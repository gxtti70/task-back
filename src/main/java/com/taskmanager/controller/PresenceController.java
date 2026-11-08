package com.taskmanager.controller;

import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.PresenceService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final UserRepository userRepository;
    private final PresenceService presenceService;

    /**
     * Devuelve el estado de presencia efectivo de todos los usuarios activos.
     * Respuesta: mapa userId -> { status, lastSeenAt }.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public Map<UUID, PresenceService.PresenceEntry> presence() {
        return presenceService.presenceMap();
    }

    /**
     * Establece el estado de presencia manual del usuario autenticado.
     * Estados vÃ¡lidos: DISPONIBLE, OCUPADO, NO_MOLestar, VUELVO_ENSEGUIDA, AUSENTE,
     * FUERA_OFICINA, APARECER_DESCONECTADO. OFFLINE no se admite (es derivado).
     */
    @PutMapping("/me")
    @Transactional
    public PresenceResponse setMyPresence(Authentication authentication,
                                          @RequestBody @NotNull SetPresenceRequest request) {
        User actor = currentUser(authentication);
        try {
            presenceService.setPresence(actor.getId(), request.status());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return new PresenceResponse(actor.getId(), request.status());
    }

    /**
     * Heartbeat periÃ³dico del frontend. Actualiza last_seen_at y gestiona el paso
     * automÃ¡tico a Ausente cuando el usuario estÃ¡ inactivo.
     */
    @PostMapping("/heartbeat")
    @Transactional
    public ResponseEntity<Void> heartbeat(Authentication authentication,
                                          @RequestBody @NotNull HeartbeatRequest request) {
        User actor = currentUser(authentication);
        presenceService.heartbeat(actor.getId(), request.idle());
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    public record SetPresenceRequest(@NotNull PresenceStatus status) {}
    public record HeartbeatRequest(boolean idle) {}
    public record PresenceResponse(UUID userId, PresenceStatus status) {}
}
