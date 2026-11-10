package com.taskmanager.service;

import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de presencia de usuarios (estilo Teams).
 *
 * Reglas de derivaciÃ³n del estado efectivo que ven los demÃ¡s:
 *   1. OUT_OF_OFFICE se respeta tal cual (estado manual persistente).
 *   2. APPEAR_OFFLINE se muestra como OFFLINE para los demÃ¡s.
 *   3. Sin heartbeat en mÃ¡s de 3 minutos -> OFFLINE.
 *   4. Estado manual AVAILABLE + usuario inactivo mÃ¡s de 10 minutos -> AWAY;
 *      al recuperar actividad vuelve a AVAILABLE automÃ¡ticamente.
 *
 * OFFLINE nunca se acepta como entrada del cliente: sÃ³lo lo calcula este servicio.
 */
@Service
public class PresenceService {

    private static final long OFFLINE_AFTER_SECONDS = 180; // 3 min sin heartbeat
    private static final long IDLE_TO_AWAY_SECONDS = 600;  // 10 min inactivo -> Ausente

    private final UserRepository userRepository;

    public PresenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Establece el estado de presencia manual del usuario autenticado.
     * @throws IllegalArgumentException si se intenta establecer OFFLINE (no settable).
     */
    @Transactional
    public void setPresence(UUID userId, PresenceStatus status) {
        if (status == PresenceStatus.OFFLINE) {
            throw new IllegalArgumentException("El estado OFFLINE no se puede establecer manualmente.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        user.setPresenceStatus(status);
        user.setPresenceUpdatedAt(LocalDateTime.now());
        // Si el usuario pasa de Ausente a cualquier estado manual, recupera la marca de actividad.
        user.setLastSeenAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * Heartbeat periÃ³dico enviado por el frontend mientras la pestaÃ±a estÃ¡ visible.
     * Actualiza last_seen_at y, si el usuario estÃ¡ inactivo y su estado es Disponible,
     * lo pasa automÃ¡ticamente a Ausente.
     */
    @Transactional
    public void heartbeat(UUID userId, boolean idle) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        LocalDateTime now = LocalDateTime.now();
        user.setLastSeenAt(now);

        if (idle && user.getPresenceStatus() == PresenceStatus.AVAILABLE) {
            user.setPresenceStatus(PresenceStatus.AWAY);
            user.setPresenceUpdatedAt(now);
        }
        userRepository.save(user);
    }

    /**
     * Devuelve el estado efectivo que deben ver los demÃ¡s usuarios de un usuario dado,
     * aplicando las reglas de derivaciÃ³n.
     */
    public PresenceStatus effectiveStatus(User user) {
        if (user == null) return PresenceStatus.OFFLINE;
        PresenceStatus stored = user.getPresenceStatus() != null ? user.getPresenceStatus() : PresenceStatus.AVAILABLE;

        // Fuera de la oficina se respeta siempre, aunque no haya heartbeat reciente.
        if (stored == PresenceStatus.OUT_OF_OFFICE) return PresenceStatus.OUT_OF_OFFICE;

        // Parecer desconectado se muestra como desconectado para el resto.
        if (stored == PresenceStatus.APPEAR_OFFLINE) return PresenceStatus.OFFLINE;

        // Sin heartbeat reciente -> desconectado.
        LocalDateTime lastSeen = user.getLastSeenAt();
        if (lastSeen == null || lastSeen.isBefore(LocalDateTime.now().minusSeconds(OFFLINE_AFTER_SECONDS))) {
            return PresenceStatus.OFFLINE;
        }

        return stored;
    }

    /**
     * Marca de última conexión que puede ver el resto de usuarios.
     * Si el usuario eligió "Parecer desconectado" la marca se oculta: su
     * heartbeat seguiría actualizándola y delataría que está conectado.
     */
    public LocalDateTime visibleLastSeenAt(User user) {
        if (user == null) return null;
        PresenceStatus stored = user.getPresenceStatus() != null ? user.getPresenceStatus() : PresenceStatus.AVAILABLE;
        return stored == PresenceStatus.APPEAR_OFFLINE ? null : user.getLastSeenAt();
    }

    /**
     * Mapa userId -> entrada de presencia para todos los usuarios activos,
     * listo para consumir por el frontend en una sola llamada.
     */
    @Transactional(readOnly = true)
    public Map<UUID, PresenceEntry> presenceMap() {
        List<User> users = userRepository.findAllByActiveTrueOrderByFullNameAsc();
        Map<UUID, PresenceEntry> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), new PresenceEntry(user.getId(), effectiveStatus(user), visibleLastSeenAt(user)));
        }
        return map;
    }

    public record PresenceEntry(UUID userId, PresenceStatus status, LocalDateTime lastSeenAt) {}
}
