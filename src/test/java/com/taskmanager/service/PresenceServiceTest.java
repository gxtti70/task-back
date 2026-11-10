package com.taskmanager.service;

import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PresenceService service = new PresenceService(users);

    private UUID id() { return UUID.randomUUID(); }

    private User user(PresenceStatus status, LocalDateTime lastSeen) {
        User u = new User();
        u.setId(id());
        u.setEmail("usuario@test.test");
        u.setFullName("Usuario Prueba");
        u.setRole("DEVELOPER");
        u.setPassword("hash");
        u.setPresenceStatus(status);
        u.setLastSeenAt(lastSeen);
        return u;
    }

    @Test
    void setPresenceGuardaEstadoYMarcaTiempo() {
        UUID userId = id();
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now());
        when(users.findById(userId)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setPresence(userId, PresenceStatus.BUSY);

        assertEquals(PresenceStatus.BUSY, u.getPresenceStatus());
        assertNotNull(u.getPresenceUpdatedAt());
        assertNotNull(u.getLastSeenAt());
        verify(users).save(u);
    }

    @Test
    void setPresenceRechazaOffline() {
        UUID userId = id();
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now());
        when(users.findById(userId)).thenReturn(Optional.of(u));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.setPresence(userId, PresenceStatus.OFFLINE));
        assertTrue(ex.getMessage().contains("OFFLINE"));
        verify(users, never()).save(any());
    }

    @Test
    void effectiveStatusFueraDeOficinaSeRespetaSiempre() {
        User u = user(PresenceStatus.OUT_OF_OFFICE, null);
        assertEquals(PresenceStatus.OUT_OF_OFFICE, service.effectiveStatus(u));
    }

    @Test
    void effectiveStatusAppearOfflineSeMuestraComoOffline() {
        User u = user(PresenceStatus.APPEAR_OFFLINE, LocalDateTime.now());
        assertEquals(PresenceStatus.OFFLINE, service.effectiveStatus(u));
    }

    @Test
    void effectiveStatusSinHeartbeatRecienteEsOffline() {
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now().minusMinutes(10));
        assertEquals(PresenceStatus.OFFLINE, service.effectiveStatus(u));
    }

    @Test
    void effectiveStatusDisponibleConHeartbeatRecientePermaneceDisponible() {
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now());
        assertEquals(PresenceStatus.AVAILABLE, service.effectiveStatus(u));
    }

    @Test
    void effectiveStatusUsuarioNuloEsOffline() {
        assertEquals(PresenceStatus.OFFLINE, service.effectiveStatus(null));
    }

    @Test
    void heartbeatInactivoPasaDisponibleAEsteAusente() {
        UUID userId = id();
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now().minusMinutes(5));
        when(users.findById(userId)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.heartbeat(userId, true);

        assertEquals(PresenceStatus.AWAY, u.getPresenceStatus());
        assertNotNull(u.getLastSeenAt());
        verify(users).save(u);
    }

    @Test
    void heartbeatActivoMantieneDisponible() {
        UUID userId = id();
        User u = user(PresenceStatus.AVAILABLE, LocalDateTime.now().minusMinutes(5));
        when(users.findById(userId)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.heartbeat(userId, false);

        assertEquals(PresenceStatus.AVAILABLE, u.getPresenceStatus());
        verify(users).save(u);
    }

    @Test
    void presenceMapDevuelveEntradaPorUsuarioActivo() {
        UUID userId = id();
        User u = new User();
        u.setId(userId);
        u.setEmail("usuario@test.test");
        u.setFullName("Usuario Prueba");
        u.setRole("DEVELOPER");
        u.setPassword("hash");
        u.setPresenceStatus(PresenceStatus.BUSY);
        u.setLastSeenAt(LocalDateTime.now());
        when(users.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(u));

        Map<UUID, PresenceService.PresenceEntry> map = service.presenceMap();

        assertEquals(1, map.size());
        PresenceService.PresenceEntry entry = map.get(userId);
        assertNotNull(entry);
        assertEquals(userId, entry.userId());
        assertEquals(PresenceStatus.BUSY, entry.status());
        assertNotNull(entry.lastSeenAt());
    }

    @Test
    void appearOfflineOcultaLaMarcaDeUltimaConexion() {
        User u = user(PresenceStatus.APPEAR_OFFLINE, LocalDateTime.now());

        assertNull(service.visibleLastSeenAt(u));
    }

    @Test
    void visibleLastSeenAtConservaLaMarcaParaElRestoDeEstados() {
        LocalDateTime lastSeen = LocalDateTime.now().minusMinutes(5);

        assertEquals(lastSeen, service.visibleLastSeenAt(user(PresenceStatus.OFFLINE, lastSeen)));
        assertEquals(lastSeen, service.visibleLastSeenAt(user(PresenceStatus.OUT_OF_OFFICE, lastSeen)));
        assertEquals(lastSeen, service.visibleLastSeenAt(user(PresenceStatus.AWAY, lastSeen)));
    }

    @Test
    void presenceMapNoExponeLaMarcaDeQuienPareceDesconectado() {
        User u = user(PresenceStatus.APPEAR_OFFLINE, LocalDateTime.now());
        when(users.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(u));

        Map<UUID, PresenceService.PresenceEntry> map = service.presenceMap();

        PresenceService.PresenceEntry entry = map.get(u.getId());
        assertNotNull(entry);
        assertEquals(PresenceStatus.OFFLINE, entry.status());
        assertNull(entry.lastSeenAt());
    }
}
