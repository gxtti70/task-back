package com.taskmanager.controller;

import com.taskmanager.dto.request.CallSignalRequest;
import com.taskmanager.dto.request.CallStartRequest;
import com.taskmanager.dto.response.CallEventResponse;
import com.taskmanager.dto.response.CallResponse;
import com.taskmanager.entity.Channel;
import com.taskmanager.entity.ChannelMember;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ChannelMemberRepository;
import com.taskmanager.repository.ChannelRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.CallService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallControllerTest {

    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ChannelMemberRepository channelMembers = mock(ChannelMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CallService callService = new CallService();
    private final CallController controller = new CallController(channels, channelMembers, users, callService);

    private static User user(UUID id, String email) {
        return User.builder().id(id).email(email).fullName("User " + id).role("DEVELOPER").active(true).build();
    }

    private static UsernamePasswordAuthenticationToken auth(String email) {
        return UsernamePasswordAuthenticationToken.authenticated(email, "",
                List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
    }

    /** Prepara un DM entre actor y peer con el actor como miembro. */
    private Channel dmChannel(UUID id, User actor, User peer) {
        Channel channel = Channel.builder().id(id).type("TEXT").build();
        when(channels.findById(id)).thenReturn(Optional.of(channel));
        when(channelMembers.existsByChannel_IdAndUser_Id(id, actor.getId())).thenReturn(true);
        when(channelMembers.findByChannel_IdOrderByJoinedAtAsc(id)).thenReturn(List.of(
                ChannelMember.builder().channel(channel).user(actor).build(),
                ChannelMember.builder().channel(channel).user(peer).build()));
        return channel;
    }

    @Test
    void startCallOnDmReturnsCreatedRingingSessionWithPeerAsCallee() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User peer = user(UUID.randomUUID(), "peer@example.test");
        UUID channelId = UUID.randomUUID();
        dmChannel(channelId, actor, peer);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));

        ResponseEntity<CallResponse> response =
                controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail()));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(actor.getId(), response.getBody().callerId());
        assertEquals(peer.getId(), response.getBody().calleeId());
        assertEquals(CallService.STATE_RINGING, response.getBody().state());
        assertEquals(channelId, response.getBody().channelId());
        assertEquals(1, response.getBody().seq());
    }

    @Test
    void projectRoomsCannotBeCalled() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        UUID channelId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID())
                .owner(user(UUID.randomUUID(), "owner@example.test")).name("P").build();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(channels.findById(channelId)).thenReturn(Optional.of(
                Channel.builder().id(channelId).type("TEXT").project(project).build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(channelMembers, never()).existsByChannel_IdAndUser_Id(channelId, actor.getId());
    }

    @Test
    void nonMemberCannotStartACall() {
        User outsider = user(UUID.randomUUID(), "outsider@example.test");
        UUID channelId = UUID.randomUUID();
        when(users.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));
        when(channels.findById(channelId)).thenReturn(Optional.of(
                Channel.builder().id(channelId).type("TEXT").build()));
        when(channelMembers.existsByChannel_IdAndUser_Id(channelId, outsider.getId())).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.startCall(new CallStartRequest(channelId), auth(outsider.getEmail())));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void unknownChannelIsNotFound() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        UUID channelId = UUID.randomUUID();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(channels.findById(channelId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void dmWithoutPeerIsRejected() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        UUID channelId = UUID.randomUUID();
        Channel channel = Channel.builder().id(channelId).type("TEXT").build();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(channels.findById(channelId)).thenReturn(Optional.of(channel));
        when(channelMembers.existsByChannel_IdAndUser_Id(channelId, actor.getId())).thenReturn(true);
        when(channelMembers.findByChannel_IdOrderByJoinedAtAsc(channelId)).thenReturn(List.of(
                ChannelMember.builder().channel(channel).user(actor).build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void calleeAnswersAndCallerCannot() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User peer = user(UUID.randomUUID(), "peer@example.test");
        UUID channelId = UUID.randomUUID();
        dmChannel(channelId, actor, peer);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmail(peer.getEmail())).thenReturn(Optional.of(peer));
        CallResponse call = controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())).getBody();
        assertNotNull(call);

        ResponseStatusException byCaller = assertThrows(ResponseStatusException.class,
                () -> controller.answer(call.id(), auth(actor.getEmail())));
        assertEquals(HttpStatus.FORBIDDEN, byCaller.getStatusCode());

        CallResponse answered = controller.answer(call.id(), auth(peer.getEmail()));
        assertEquals(CallService.STATE_ACTIVE, answered.state());
        assertEquals(2, answered.seq());
    }

    @Test
    void eventsAreOnlyVisibleToParticipants() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User peer = user(UUID.randomUUID(), "peer@example.test");
        User outsider = user(UUID.randomUUID(), "outsider@example.test");
        UUID channelId = UUID.randomUUID();
        dmChannel(channelId, actor, peer);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmail(peer.getEmail())).thenReturn(Optional.of(peer));
        when(users.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));
        CallResponse call = controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())).getBody();
        assertNotNull(call);

        List<CallEventResponse> peerEvents = controller.events(call.id(), 0, auth(peer.getEmail()));
        assertEquals(1, peerEvents.size());
        assertEquals(CallService.EVENT_STATE, peerEvents.get(0).type());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.events(call.id(), 0, auth(outsider.getEmail())));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void signalReachesThePeerThroughTheCursor() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User peer = user(UUID.randomUUID(), "peer@example.test");
        UUID channelId = UUID.randomUUID();
        dmChannel(channelId, actor, peer);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmail(peer.getEmail())).thenReturn(Optional.of(peer));
        CallResponse call = controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail())).getBody();
        assertNotNull(call);

        controller.signal(call.id(), new CallSignalRequest(CallService.EVENT_OFFER, "v=0"),
                auth(actor.getEmail()));

        List<CallEventResponse> peerEvents = controller.events(call.id(), 1, auth(peer.getEmail()));
        assertEquals(1, peerEvents.size());
        assertEquals(CallService.EVENT_OFFER, peerEvents.get(0).type());
        assertEquals("v=0", peerEvents.get(0).payload());
        assertEquals(actor.getId(), peerEvents.get(0).fromUserId());
    }

    @Test
    void activeListsOnlyTheCallsOfTheAuthenticatedUser() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User peer = user(UUID.randomUUID(), "peer@example.test");
        UUID channelId = UUID.randomUUID();
        dmChannel(channelId, actor, peer);
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmail(peer.getEmail())).thenReturn(Optional.of(peer));
        controller.startCall(new CallStartRequest(channelId), auth(actor.getEmail()));
        when(users.findByEmail("stranger@example.test")).thenReturn(
                Optional.of(user(UUID.randomUUID(), "stranger@example.test")));

        assertEquals(1, controller.activeCalls(auth(actor.getEmail())).size());
        assertEquals(1, controller.activeCalls(auth(peer.getEmail())).size());
        assertEquals(0, controller.activeCalls(auth("stranger@example.test")).size());
    }
}
