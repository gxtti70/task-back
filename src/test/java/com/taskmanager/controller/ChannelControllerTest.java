package com.taskmanager.controller;

import com.taskmanager.dto.request.ChannelRequest;
import com.taskmanager.dto.request.ChatMessageRequest;
import com.taskmanager.dto.request.DirectChannelRequest;
import com.taskmanager.dto.response.ChannelResponse;
import com.taskmanager.dto.response.ChatMessageResponse;
import com.taskmanager.entity.Channel;
import com.taskmanager.entity.Message;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.User;
import com.taskmanager.repository.*;
import com.taskmanager.service.ChatFileService;
import com.taskmanager.service.ChatUnreadService;
import com.taskmanager.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChannelControllerTest {

    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ChannelMemberRepository channelMembers = mock(ChannelMemberRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository projectMembers = mock(ProjectMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatFileService chatFileService = mock(ChatFileService.class);
    private final ChatUnreadService chatUnreadService = mock(ChatUnreadService.class);
    private final PushNotificationService pushNotificationService = mock(PushNotificationService.class);
    private final ChannelController controller = new ChannelController(channels, channelMembers, messages,
            projects, projectMembers, users, chatFileService, chatUnreadService, pushNotificationService);

    private static User user(UUID id, String email) {
        return User.builder().id(id).email(email).fullName("User " + id).role("DEVELOPER").active(true).build();
    }

    private static UsernamePasswordAuthenticationToken auth(String email, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(email, "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void dmChannelIsCreatedOnceIdempotentlyForTheSamePairRegardlessOfRequestOrder() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        User target = user(UUID.randomUUID(), "target@example.test");
        UUID lower = target.getId().compareTo(actor.getId()) < 0 ? target.getId() : actor.getId();
        UUID higher = target.getId().compareTo(actor.getId()) < 0 ? actor.getId() : target.getId();
        UUID savedId = UUID.randomUUID();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findByEmail(target.getEmail())).thenReturn(Optional.of(target));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(channels.save(any())).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return Channel.builder().id(savedId).type(c.getType()).dmUserLow(c.getDmUserLow())
                    .dmUserHigh(c.getDmUserHigh()).createdAt(LocalDateTime.now()).build();
        });

        var actorAuth = auth(actor.getEmail(), "DEVELOPER");
        var first = controller.createDirect(new DirectChannelRequest(target.getId()), actorAuth);
        var targetAuth = auth(target.getEmail(), "DEVELOPER");
        when(channels.findByDmUserLowAndDmUserHigh(lower, higher))
                .thenReturn(Optional.of(Channel.builder().id(savedId).type("TEXT")
                        .dmUserLow(lower).dmUserHigh(higher).createdAt(LocalDateTime.now()).build()));
        var second = controller.createDirect(new DirectChannelRequest(actor.getId()), targetAuth);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(savedId, first.getBody().id());
        assertEquals(savedId, second.getBody().id());
        verify(channels, times(1)).save(any());
        var captor = ArgumentCaptor.forClass(Channel.class);
        verify(channels).save(captor.capture());
        assertEquals(lower, captor.getValue().getDmUserLow());
        assertEquals(higher, captor.getValue().getDmUserHigh());
    }

    @Test
    void rejectsDmToYourself() {
        User actor = user(UUID.randomUUID(), "actor@example.test");
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.createDirect(new DirectChannelRequest(actor.getId()), auth(actor.getEmail(), "DEVELOPER")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(channels, never()).save(any());
    }

    @Test
    void onlyOwnerOrAdminCanCreateProjectRoomsAndTheNameIsNormalized() {
        UUID projectId = UUID.randomUUID();
        User owner = user(UUID.randomUUID(), "owner@example.test");
        User outsider = user(UUID.randomUUID(), "outsider@example.test");
        Project project = Project.builder().id(projectId).owner(owner).name("P").build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(users.findByEmail("owner@example.test")).thenReturn(Optional.of(owner));
        when(users.findByEmail("outsider@example.test")).thenReturn(Optional.of(outsider));

        var outsiderError = assertThrows(ResponseStatusException.class,
                () -> controller.createChannel(new ChannelRequest(projectId, "Sprint 1", null),
                        auth("outsider@example.test", "DEVELOPER")));
        assertEquals(HttpStatus.FORBIDDEN, outsiderError.getStatusCode());
        verify(channels, never()).save(any());

        when(channels.save(any())).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return Channel.builder().id(UUID.randomUUID()).type(c.getType()).name(c.getName())
                    .project(c.getProject()).createdAt(LocalDateTime.now()).build();
        });
        var created = controller.createChannel(new ChannelRequest(projectId, "  Sprint 1  ", null),
                auth("owner@example.test", "MANAGER"));

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("Sprint 1", created.getBody().name());
        assertEquals(projectId, created.getBody().projectId());
    }

    @Test
    void voiceChannelTypeIsRejectedUntilVoiceShips() {
        UUID projectId = UUID.randomUUID();
        User owner = user(UUID.randomUUID(), "owner@example.test");
        when(projects.findById(projectId)).thenReturn(Optional.of(
                Project.builder().id(projectId).owner(owner).name("P").build()));
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.createChannel(new ChannelRequest(projectId, "Voz", "VOICE"),
                        auth(owner.getEmail(), "MANAGER")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(channels, never()).save(any());
    }

    @Test
    void nonMemberCannotReadNorWriteRoomMessages() {
        UUID projectId = UUID.randomUUID();
        User outsider = user(UUID.randomUUID(), "outsider@example.test");
        Project project = Project.builder().id(projectId).owner(user(UUID.randomUUID(), "owner@x.test")).build();
        Channel channel = Channel.builder().id(UUID.randomUUID()).type("TEXT").name("General").project(project).build();
        when(users.findByEmail("outsider@example.test")).thenReturn(Optional.of(outsider));
        when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(projectMembers.existsByProject_IdAndUser_Id(projectId, outsider.getId())).thenReturn(false);

        var readError = assertThrows(ResponseStatusException.class,
                () -> controller.getMessages(channel.getId(), null, 50, false,
                        auth("outsider@example.test", "DEVELOPER")));
        assertEquals(HttpStatus.FORBIDDEN, readError.getStatusCode());
        verify(messages, never()).findLatestPage(any(), any(Pageable.class));

        var writeError = assertThrows(ResponseStatusException.class,
                () -> controller.createMessage(channel.getId(), new ChatMessageRequest("hola"),
                        auth("outsider@example.test", "DEVELOPER")));
        assertEquals(HttpStatus.FORBIDDEN, writeError.getStatusCode());
        verify(messages, never()).save(any());
    }

    @Test
    void nonMemberCannotReadDmMessages() {
        User outsider = user(UUID.randomUUID(), "outsider@example.test");
        Channel dm = Channel.builder().id(UUID.randomUUID()).type("TEXT").build();
        when(users.findByEmail("outsider@example.test")).thenReturn(Optional.of(outsider));
        when(channels.findById(dm.getId())).thenReturn(Optional.of(dm));
        when(channelMembers.existsByChannel_IdAndUser_Id(dm.getId(), outsider.getId())).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getMessages(dm.getId(), null, 50, false,
                        auth("outsider@example.test", "DEVELOPER")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(messages, never()).findLatestPage(any(), any(Pageable.class));
    }

    @Test
    void messagesPageComesBackInAscendingOrder() {
        UUID projectId = UUID.randomUUID();
        User actor = user(UUID.randomUUID(), "actor@example.test");
        Project project = Project.builder().id(projectId).owner(actor).build();
        Channel channel = Channel.builder().id(UUID.randomUUID()).type("TEXT").name("General").project(project).build();
        LocalDateTime newer = LocalDateTime.now();
        Message m1 = Message.builder().id(UUID.randomUUID()).body("primero").createdAt(newer.minusMinutes(1)).sender(actor).build();
        Message m2 = Message.builder().id(UUID.randomUUID()).body("segundo").createdAt(newer).sender(actor).build();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(projectMembers.existsByProject_IdAndUser_Id(projectId, actor.getId())).thenReturn(true);
        when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(messages.findLatestPage(eq(channel.getId()), any(Pageable.class))).thenReturn(List.of(m2, m1));

        List<ChatMessageResponse> page = controller.getMessages(channel.getId(), null, 50, false,
                auth(actor.getEmail(), "MANAGER"));

        assertEquals(2, page.size());
        assertEquals("primero", page.get(0).body());
        assertEquals("segundo", page.get(1).body());
    }

    @Test
    void cursorPageUsesTheBeforeMessageAndItsChannelMustMatch() {
        UUID projectId = UUID.randomUUID();
        User actor = user(UUID.randomUUID(), "actor@example.test");
        Project project = Project.builder().id(projectId).owner(actor).build();
        Channel channel = Channel.builder().id(UUID.randomUUID()).type("TEXT").name("General").project(project).build();
        Channel otherChannel = Channel.builder().id(UUID.randomUUID()).type("TEXT").project(null).build();
        UUID cursorId = UUID.randomUUID();
        Message cursor = Message.builder().id(cursorId).channel(otherChannel).body("x")
                .createdAt(LocalDateTime.now()).sender(actor).build();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(projectMembers.existsByProject_IdAndUser_Id(projectId, actor.getId())).thenReturn(true);
        when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(messages.findById(cursorId)).thenReturn(Optional.of(cursor));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getMessages(channel.getId(), cursorId, 50, false,
                        auth(actor.getEmail(), "MANAGER")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(messages, never()).findBefore(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void memberCanPostMessageAndBodyIsTrimmed() {
        UUID projectId = UUID.randomUUID();
        User actor = user(UUID.randomUUID(), "actor@example.test");
        Project project = Project.builder().id(projectId).owner(actor).build();
        Channel channel = Channel.builder().id(UUID.randomUUID()).type("TEXT").name("General").project(project).build();
        when(users.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(projectMembers.existsByProject_IdAndUser_Id(projectId, actor.getId())).thenReturn(true);
        when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(messages.save(any())).thenAnswer(inv -> Message.builder().id(UUID.randomUUID())
                .channel(channel).sender(actor)
                .body(((Message) inv.getArgument(0)).getBody())
                .createdAt(LocalDateTime.now()).build());

        ChatMessageResponse response = controller.createMessage(channel.getId(), new ChatMessageRequest("  hola  "),
                auth(actor.getEmail(), "MANAGER"));

        assertEquals("hola", response.body());
        assertEquals(actor.getId(), response.authorId());
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(captor.capture());
        assertEquals("hola", captor.getValue().getBody());
    }
}
