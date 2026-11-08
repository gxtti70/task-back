package com.taskmanager.controller;

import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceControllerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PresenceService presenceService = mock(PresenceService.class);
    private final PresenceController controller = new PresenceController(users, presenceService);

    private static User user(UUID id, String email) {
        return User.builder().id(id).email(email).fullName("User " + id).role("DEVELOPER")
                .password("hash").active(true).build();
    }

    private static UsernamePasswordAuthenticationToken auth(String email) {
        return UsernamePasswordAuthenticationToken.authenticated(email, "",
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
    }

    @Test
    void presenceDevuelveMapaDeTodosLosUsuariosActivos() {
        UUID userId = UUID.randomUUID();
        when(users.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(user(userId, "a@test.test")));
        when(presenceService.presenceMap()).thenReturn(Map.of(userId,
                new PresenceService.PresenceEntry(userId, PresenceStatus.BUSY, LocalDateTime.now())));

        Map<UUID, PresenceService.PresenceEntry> result = controller.presence();

        assertEquals(1, result.size());
        assertEquals(PresenceStatus.BUSY, result.get(userId).status());
    }

    @Test
    void setMyPresenceActualizaEstadoYDevuelveRespuesta() {
        UUID userId = UUID.randomUUID();
        User actor = user(userId, "actor@test.test");
        when(users.findByEmailIgnoreCase("actor@test.test")).thenReturn(Optional.of(actor));

        var response = controller.setMyPresence(auth("actor@test.test"),
                new PresenceController.SetPresenceRequest(PresenceStatus.BUSY));

        assertEquals(PresenceStatus.BUSY, response.status());
        assertEquals(userId, response.userId());
        verify(presenceService).setPresence(userId, PresenceStatus.BUSY);
    }

    @Test
    void setMyPresenceRechazaOfflineCon400() {
        UUID userId = UUID.randomUUID();
        User actor = user(userId, "actor@test.test");
        when(users.findByEmailIgnoreCase("actor@test.test")).thenReturn(Optional.of(actor));
        doThrow(new IllegalArgumentException("El estado OFFLINE no se puede establecer manualmente."))
            .when(presenceService).setPresence(eq(userId), eq(PresenceStatus.OFFLINE));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.setMyPresence(auth("actor@test.test"),
                        new PresenceController.SetPresenceRequest(PresenceStatus.OFFLINE)));

        verify(presenceService, never()).setPresence(any(), eq(PresenceStatus.BUSY));
    }

    @Test
    void heartbeatDevuelve204() {
        UUID userId = UUID.randomUUID();
        User actor = user(userId, "actor@test.test");
        when(users.findByEmailIgnoreCase("actor@test.test")).thenReturn(Optional.of(actor));

        ResponseEntity<Void> response = controller.heartbeat(auth("actor@test.test"),
                new PresenceController.HeartbeatRequest(true));

        assertEquals(204, response.getStatusCode().value());
        verify(presenceService).heartbeat(userId, true);
    }
}
