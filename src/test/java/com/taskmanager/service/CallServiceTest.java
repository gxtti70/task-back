package com.taskmanager.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallServiceTest {

    private final CallService service = new CallService();
    private final MutableClock clock = new MutableClock();
    private final UUID channel = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final UUID callee = UUID.randomUUID();

    /** Reloj controlable para ejercer los tiempos de sonido y retención sin esperar. */
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-19T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private CallService.CallSession start() {
        service.setClock(clock);
        return service.start(channel, caller, callee);
    }

    @Test
    void startCreatesRingingSessionWithASingleStateEvent() {
        CallService.CallSession session = start();

        assertEquals(CallService.STATE_RINGING, session.getState());
        assertEquals(1, session.getSeq());

        List<CallService.CallEvent> events = service.events(session.getId(), callee, 0);
        assertEquals(1, events.size());
        assertEquals(CallService.EVENT_STATE, events.get(0).type());
        assertEquals(CallService.STATE_RINGING, events.get(0).state());
    }

    @Test
    void secondCallOnTheSameChannelOrBusyUserIsRejected() {
        start();

        ResponseStatusException sameChannel = assertThrows(ResponseStatusException.class,
                () -> service.start(channel, UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(HttpStatus.CONFLICT, sameChannel.getStatusCode());

        ResponseStatusException busyUser = assertThrows(ResponseStatusException.class,
                () -> service.start(UUID.randomUUID(), caller, UUID.randomUUID()));
        assertEquals(HttpStatus.CONFLICT, busyUser.getStatusCode());
    }

    @Test
    void selfCallIsRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.start(channel, caller, caller));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void onlyTheCalleeCanAnswerAndAnsweringActivatesTheCall() {
        CallService.CallSession session = start();

        ResponseStatusException byCaller = assertThrows(ResponseStatusException.class,
                () -> service.answer(session.getId(), caller));
        assertEquals(HttpStatus.FORBIDDEN, byCaller.getStatusCode());

        CallService.CallSession answered = service.answer(session.getId(), callee);
        assertEquals(CallService.STATE_ACTIVE, answered.getState());
        assertEquals(2, answered.getSeq());
    }

    @Test
    void declineEndsTheCallAndFurtherSignalsAreRejected() {
        CallService.CallSession session = start();

        CallService.CallSession declined = service.decline(session.getId(), callee);

        assertEquals(CallService.STATE_ENDED, declined.getState());
        assertEquals(CallService.END_DECLINED, declined.getEndReason());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.signal(session.getId(), caller, CallService.EVENT_OFFER, "v=0"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void callerCanCancelWhileRingingAndTheChannelIsFreeAgain() {
        CallService.CallSession session = start();

        CallService.CallSession cancelled = service.end(session.getId(), caller);

        assertEquals(CallService.STATE_ENDED, cancelled.getState());
        assertEquals(CallService.END_CANCELLED, cancelled.getEndReason());

        // La llamada anterior sigue residente (para que el otro lado lea el desenlace)
        // pero ya no bloquea el canal.
        assertEquals(1, service.sessionCount());
        CallService.CallSession second = service.start(channel, caller, callee);
        assertEquals(CallService.STATE_RINGING, second.getState());
    }

    @Test
    void endingAnActiveCallIsIdempotentAndFinishedForEitherSide() {
        CallService.CallSession session = start();
        service.answer(session.getId(), callee);

        CallService.CallSession firstEnd = service.end(session.getId(), callee);
        CallService.CallSession secondEnd = service.end(session.getId(), caller);

        assertEquals(CallService.END_FINISHED, firstEnd.getEndReason());
        assertEquals(firstEnd.getSeq(), secondEnd.getSeq());
        assertEquals(1, service.events(session.getId(), caller, 0).stream()
                .filter(event -> CallService.EVENT_STATE.equals(event.type())
                        && CallService.STATE_ENDED.equals(event.state()))
                .count());
    }

    @Test
    void signalsAreNormalizedOrderedAndFilteredByCursor() {
        CallService.CallSession session = start();
        service.signal(session.getId(), caller, "offer", "v=0-offer");
        service.signal(session.getId(), callee, CallService.EVENT_ICE, "{\"candidate\":\"c1\"}");

        List<CallService.CallEvent> all = service.events(session.getId(), callee, 0);
        assertEquals(3, all.size());
        assertEquals(CallService.EVENT_STATE, all.get(0).type());
        assertEquals(CallService.EVENT_OFFER, all.get(1).type());
        assertEquals("v=0-offer", all.get(1).payload());
        assertEquals(caller, all.get(1).fromUserId());
        assertEquals(CallService.EVENT_ICE, all.get(2).type());

        List<CallService.CallEvent> tail = service.events(session.getId(), caller, 2);
        assertEquals(1, tail.size());
        assertEquals(CallService.EVENT_ICE, tail.get(0).type());
    }

    @Test
    void invalidSignalTypeOrEmptyPayloadIsRejected() {
        CallService.CallSession session = start();

        ResponseStatusException badType = assertThrows(ResponseStatusException.class,
                () -> service.signal(session.getId(), caller, "REVOKE", "x"));
        assertEquals(HttpStatus.BAD_REQUEST, badType.getStatusCode());

        ResponseStatusException emptyPayload = assertThrows(ResponseStatusException.class,
                () -> service.signal(session.getId(), caller, CallService.EVENT_OFFER, "  "));
        assertEquals(HttpStatus.BAD_REQUEST, emptyPayload.getStatusCode());
    }

    @Test
    void outsiderNeitherReadsNorSignalsTheCall() {
        CallService.CallSession session = start();
        UUID outsider = UUID.randomUUID();

        ResponseStatusException readError = assertThrows(ResponseStatusException.class,
                () -> service.events(session.getId(), outsider, 0));
        assertEquals(HttpStatus.FORBIDDEN, readError.getStatusCode());

        ResponseStatusException signalError = assertThrows(ResponseStatusException.class,
                () -> service.signal(session.getId(), outsider, CallService.EVENT_ICE, "{}"));
        assertEquals(HttpStatus.FORBIDDEN, signalError.getStatusCode());
    }

    @Test
    void ringTimeoutMarksTheCallMissedAndEmitsTheEvent() {
        CallService.CallSession session = start();

        clock.advance(Duration.ofSeconds(61));

        assertTrue(service.activeFor(caller).isEmpty());
        List<CallService.CallEvent> events = service.events(session.getId(), caller, 0);
        CallService.CallEvent last = events.get(events.size() - 1);
        assertEquals(CallService.EVENT_STATE, last.type());
        assertEquals(CallService.STATE_ENDED, last.state());
        assertEquals(CallService.END_MISSED, last.reason());
    }

    @Test
    void endedSessionsArePurgedAfterRetention() {
        CallService.CallSession session = start();
        service.end(session.getId(), caller);
        assertEquals(1, service.sessionCount());

        clock.advance(Duration.ofSeconds(601));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.events(session.getId(), caller, 0));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(0, service.sessionCount());
        assertFalse(service.activeFor(caller).stream()
                .anyMatch(item -> item.getId().equals(session.getId())));
    }

    @Test
    void activeForOnlyReturnsCallsTheUserParticipatesIn() {
        CallService.CallSession session = start();
        service.answer(session.getId(), callee);
        UUID outsider = UUID.randomUUID();

        assertEquals(1, service.activeFor(caller).size());
        assertEquals(1, service.activeFor(callee).size());
        assertTrue(service.activeFor(outsider).isEmpty());
        assertEquals(session.getId(), service.activeFor(callee).get(0).getId());
    }
}
