package com.taskmanager.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Almacén en memoria de las videollamadas WebRTC y su señalización.
 *
 * <p>El estado de las llamadas es efímero: no hay tabla en Flyway y cada llamada vive
 * como una sesión con una cola de eventos con cursor {@code seq}, que el cliente sondea
 * (mismo patrón de sondeo que chat y presencia). El audio/vídeo nunca pasa por aquí:
 * viaja P2P con WebRTC; solo se reenvían SDP, candidatos ICE y cambios de estado.
 *
 * <p>Al reiniciar el proceso todas las llamadas desaparecen; el cliente lo detecta
 * porque los eventos devuelven 404 y debe tratarlo como "llamada finalizada".
 */
@Service
public class CallService {
    public static final String STATE_RINGING = "RINGING";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_ENDED = "ENDED";

    public static final String EVENT_OFFER = "OFFER";
    public static final String EVENT_ANSWER = "ANSWER";
    public static final String EVENT_ICE = "ICE";
    public static final String EVENT_STATE = "STATE";

    public static final String END_DECLINED = "DECLINED";
    public static final String END_CANCELLED = "CANCELLED";
    public static final String END_MISSED = "MISSED";
    public static final String END_FINISHED = "FINISHED";

    private static final Set<String> SIGNAL_TYPES = Set.of(EVENT_OFFER, EVENT_ANSWER, EVENT_ICE);
    /** Sin respuesta en este tiempo la llamada se da por perdida. */
    private static final long RING_TIMEOUT_SECONDS = 60;
    /** Las llamadas finalizadas se conservan este tiempo para que los dos lados lean el desenlace. */
    private static final long ENDED_RETENTION_SECONDS = 600;
    private static final int MAX_SIGNAL_PAYLOAD_CHARS = 64_000;

    private final Map<UUID, CallSession> sessions = new LinkedHashMap<>();
    private Clock clock = Clock.system(ZoneOffset.UTC);

    /** Evento de señalización o transición de estado, con cursor incremental por llamada. */
    public record CallEvent(long seq, String type, UUID fromUserId, String payload,
                            String state, String reason, LocalDateTime createdAt) { }

    /** Sesión de llamada: solo se muta desde los métodos sincronizados de este servicio. */
    public static final class CallSession {
        private final UUID id;
        private final UUID channelId;
        private final UUID callerId;
        private final UUID calleeId;
        private final LocalDateTime createdAt;
        private String state;
        private String endReason;
        private long seq;
        private LocalDateTime ringingSince;
        private LocalDateTime endedAt;
        private final List<CallEvent> events = new ArrayList<>();

        private CallSession(UUID channelId, UUID callerId, UUID calleeId, LocalDateTime now) {
            this.id = UUID.randomUUID();
            this.channelId = channelId;
            this.callerId = callerId;
            this.calleeId = calleeId;
            this.createdAt = now;
            this.state = STATE_RINGING;
            this.ringingSince = now;
        }

        public UUID getId() { return id; }
        public UUID getChannelId() { return channelId; }
        public UUID getCallerId() { return callerId; }
        public UUID getCalleeId() { return calleeId; }
        public String getState() { return state; }
        public String getEndReason() { return endReason; }
        public long getSeq() { return seq; }
        public LocalDateTime getCreatedAt() { return createdAt; }

        private boolean involves(UUID userId) {
            return callerId.equals(userId) || calleeId.equals(userId);
        }
    }

    /**
     * Crea la llamada en RINGING. Falla con 409 si el canal ya tiene una llamada en
     * curso o si cualquiera de los dos participantes está en otra llamada.
     */
    public synchronized CallSession start(UUID channelId, UUID callerId, UUID calleeId) {
        if (callerId.equals(calleeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes llamarte a ti mismo");
        }
        purgeExpired();
        boolean channelBusy = sessions.values().stream()
                .anyMatch(s -> isActive(s) && s.channelId.equals(channelId));
        if (channelBusy) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una llamada en curso en esta conversación");
        }
        boolean someoneBusy = sessions.values().stream()
                .anyMatch(s -> isActive(s) && (s.involves(callerId) || s.involves(calleeId)));
        if (someoneBusy) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Uno de los dos está en otra llamada");
        }
        CallSession session = new CallSession(channelId, callerId, calleeId, now());
        appendState(session, null);
        sessions.put(session.id, session);
        return session;
    }

    /** Acepta la llamada: RINGING → ACTIVE. Solo la persona llamada. */
    public synchronized CallSession answer(UUID callId, UUID userId) {
        purgeExpired();
        CallSession session = require(callId);
        requireParticipant(session, userId);
        if (!STATE_RINGING.equals(session.state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La llamada ya no está sonando");
        }
        if (!session.calleeId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la persona llamada puede aceptar la llamada");
        }
        session.state = STATE_ACTIVE;
        appendState(session, null);
        return session;
    }

    /** Rechaza la llamada entrante: RINGING → ENDED(DECLINED). Solo la persona llamada. */
    public synchronized CallSession decline(UUID callId, UUID userId) {
        purgeExpired();
        CallSession session = require(callId);
        requireParticipant(session, userId);
        if (!STATE_RINGING.equals(session.state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La llamada ya no está sonando");
        }
        if (!session.calleeId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la persona llamada puede rechazar la llamada");
        }
        return finish(session, END_DECLINED);
    }

    /**
     * Finaliza la llamada desde cualquiera de los dos lados. Es idempotente: si ya
     * estaba ENDED se devuelve sin error. Colgar mientras suena cancela (CANCELLED)
     * o rechaza (DECLINED) según quién lo haga.
     */
    public synchronized CallSession end(UUID callId, UUID userId) {
        purgeExpired();
        CallSession session = require(callId);
        requireParticipant(session, userId);
        if (STATE_ENDED.equals(session.state)) return session;
        String reason;
        if (STATE_RINGING.equals(session.state)) {
            reason = session.callerId.equals(userId) ? END_CANCELLED : END_DECLINED;
        } else {
            reason = END_FINISHED;
        }
        return finish(session, reason);
    }

    /** Encola una señal WebRTC (OFFER/ANSWER/ICE) para la otra parte. */
    public synchronized CallSession signal(UUID callId, UUID userId, String rawType, String payload) {
        purgeExpired();
        CallSession session = require(callId);
        requireParticipant(session, userId);
        if (STATE_ENDED.equals(session.state)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La llamada ya finalizó");
        }
        String type = rawType == null ? "" : rawType.trim().toUpperCase();
        if (!SIGNAL_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de señal no válido: " + rawType);
        }
        if (payload == null || payload.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La señal no puede ir vacía");
        }
        if (payload.length() > MAX_SIGNAL_PAYLOAD_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Señal demasiado grande");
        }
        session.seq++;
        session.events.add(new CallEvent(session.seq, type, userId, payload, session.state, session.endReason, now()));
        return session;
    }

    /** Eventos posteriores al cursor {@code after}: así el cliente solo recibe novedades. */
    public synchronized List<CallEvent> events(UUID callId, UUID userId, long after) {
        purgeExpired();
        CallSession session = require(callId);
        requireParticipant(session, userId);
        long cursor = Math.max(after, 0);
        return session.events.stream().filter(event -> event.seq() > cursor).toList();
    }

    /** Llamadas en las que participo que aún no han terminado (descubrimiento de entrantes). */
    public synchronized List<CallSession> activeFor(UUID userId) {
        purgeExpired();
        return sessions.values().stream()
                .filter(s -> isActive(s) && s.involves(userId))
                .toList();
    }

    /** Visible para pruebas: permite avanzar el reloj y comprobar expiraciones. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Visible para pruebas: número de sesiones residentes en memoria. */
    synchronized int sessionCount() {
        return sessions.size();
    }

    private boolean isActive(CallSession session) {
        return STATE_RINGING.equals(session.state) || STATE_ACTIVE.equals(session.state);
    }

    private CallSession require(UUID callId) {
        CallSession session = sessions.get(callId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Llamada no encontrada");
        }
        return session;
    }

    private void requireParticipant(CallSession session, UUID userId) {
        if (!session.involves(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No participas en esta llamada");
        }
    }

    private CallSession finish(CallSession session, String reason) {
        session.state = STATE_ENDED;
        session.endReason = reason;
        session.endedAt = now();
        appendState(session, reason);
        return session;
    }

    private void appendState(CallSession session, String reason) {
        session.seq++;
        session.events.add(new CallEvent(session.seq, EVENT_STATE, null, null, session.state, reason, now()));
    }

    /**
     * Expiración perezosa: convierte en MISSED las llamadas que llevan demasiado
     * tiempo sonando y purga las finalizadas antiguas. Se ejecuta al entrar en
     * cualquier método público, de modo que no hace falta un programador de tareas.
     */
    private void purgeExpired() {
        LocalDateTime now = now();
        for (CallSession session : sessions.values()) {
            if (STATE_RINGING.equals(session.state)
                    && session.ringingSince.plusSeconds(RING_TIMEOUT_SECONDS).isBefore(now)) {
                finish(session, END_MISSED);
            }
        }
        sessions.values().removeIf(session -> STATE_ENDED.equals(session.state)
                && session.endedAt != null
                && session.endedAt.plusSeconds(ENDED_RETENTION_SECONDS).isBefore(now));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
