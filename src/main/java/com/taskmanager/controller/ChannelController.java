package com.taskmanager.controller;

import com.taskmanager.dto.request.ChannelRequest;
import com.taskmanager.dto.request.ChatMessageRequest;
import com.taskmanager.dto.request.DirectChannelRequest;
import com.taskmanager.dto.response.ChannelResponse;
import com.taskmanager.dto.response.ChatMessageResponse;
import com.taskmanager.entity.Channel;
import com.taskmanager.entity.ChannelMember;
import com.taskmanager.entity.Message;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ChannelMemberRepository;
import com.taskmanager.repository.ChannelRepository;
import com.taskmanager.repository.MessageRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.RoleNames;
import com.taskmanager.service.ChatFileService;
import com.taskmanager.service.ChatUnreadService;
import com.taskmanager.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ChatFileService chatFileService;
    private final ChatUnreadService chatUnreadService;
    private final PushNotificationService pushNotificationService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<ChannelResponse> getChannels(Authentication authentication) {
        User actor = currentUser(authentication);
        List<UUID> projectIds = projectMemberRepository.findByUser_Id(actor.getId()).stream()
                .map(member -> member.getProject().getId()).toList();
        List<Channel> visible = projectIds.isEmpty()
                ? channelRepository.findByDmMembership(actor.getId())
                : mergeChannels(channelRepository.findByProjectIds(projectIds),
                        channelRepository.findByDmMembership(actor.getId()));
        return visible.stream().map(channel -> toResponse(channel, actor)).toList();
    }

    /** Solo el propietario o un ADMIN puede crear salas dentro del proyecto. */
    @PostMapping
    @Transactional
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody ChannelRequest request,
                                                         Authentication authentication) {
        User actor = currentUser(authentication);
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        requireProjectManager(project, actor);
        String type = validateChannelType(request.type());
        String name = request.name().trim();
        if (channelRepository.existsByProject_IdAndName(project.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una sala con ese nombre");
        }
        Channel channel = channelRepository.save(Channel.builder()
                .type(type).name(name).project(project).createdBy(actor).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(channel, actor));
    }

    /** DM global: idempotente por par de usuarios (devuelve el existente si ya lo hay). */
    @PostMapping("/direct")
    @Transactional
    public ResponseEntity<ChannelResponse> createDirect(@Valid @RequestBody DirectChannelRequest request,
                                                        Authentication authentication) {
        User actor = currentUser(authentication);
        User target = userRepository.findById(request.memberId()).filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no disponible"));
        if (actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes abrir un DM contigo mismo");
        }
        boolean actorFirst = actor.getId().compareTo(target.getId()) < 0;
        UUID dmUserLow = actorFirst ? actor.getId() : target.getId();
        UUID dmUserHigh = actorFirst ? target.getId() : actor.getId();

        return channelRepository.findByDmUserLowAndDmUserHigh(dmUserLow, dmUserHigh)
                .<ResponseEntity<ChannelResponse>>map(channel -> ResponseEntity.ok(toResponse(channel, actor)))
                .orElseGet(() -> {
                    Channel channel = channelRepository.save(Channel.builder().type(Channel.TYPE_TEXT)
                            .dmUserLow(dmUserLow).dmUserHigh(dmUserHigh).createdBy(actor).build());
                    channelMemberRepository.save(ChannelMember.builder().channel(channel).user(actor).build());
                    channelMemberRepository.save(ChannelMember.builder().channel(channel).user(target).build());
                    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(channel, actor));
                });
    }

    /** Paginación por cursor: devuelve hasta `limit` mensajes anteriores a `before`, en orden ascendente. */
    @GetMapping("/{id}/messages")
    @Transactional
    public List<ChatMessageResponse> getMessages(@PathVariable UUID id,
                                                 @RequestParam(name = "before", required = false) UUID before,
                                                 @RequestParam(name = "limit", defaultValue = "50") int limit,
                                                 @RequestParam(name = "deliver", defaultValue = "false") boolean deliver,
                                                 Authentication authentication) {
        User actor = currentUser(authentication);
        Channel channel = requireChannelAccess(id, actor);
        if (deliver && before == null) {
            chatUnreadService.markDeliveredForChannel(id, actor.getEmail());
        }
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_MESSAGE_PAGE_SIZE));
        List<Message> raw;
        if (before != null) {
            Message cursor = messageRepository.findById(before)
                    .filter(message -> message.getChannel().getId().equals(channel.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cursor de paginación no válido"));
            raw = messageRepository.findBefore(channel.getId(), cursor.getCreatedAt(), cursor.getId(), page);
        } else {
            raw = messageRepository.findLatestPage(channel.getId(), page);
        }
        List<Message> ascending = new ArrayList<>(raw);
        java.util.Collections.reverse(ascending);
        List<ChatUnreadService.MemberWatermark> watermarks = chatUnreadService.memberWatermarks(channel.getId());
        return ascending.stream().map(m -> toMessageResponse(m, watermarks)).toList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ChatMessageResponse createMessage(@PathVariable UUID id, @Valid @RequestBody ChatMessageRequest request,
                                             Authentication authentication) {
        User sender = currentUser(authentication);
        Channel channel = requireChannelAccess(id, sender);
        Message message = messageRepository.save(Message.builder()
                .channel(channel).sender(sender).body(request.body().trim()).build());
        // Notificación push a los miembros del canal (excepto el remitente)
        var memberIds = channelMemberRepository.findByChannel_IdOrderByJoinedAtAsc(channel.getId()).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        Map<String, String> data = Map.of(
                "type", "message",
                "messageId", message.getId().toString(),
                "senderId", sender.getId().toString(),
                "senderName", sender.getFullName()
        );
        pushNotificationService.sendPushToChannelMembers(memberIds, sender.getId(),
                "Nuevo mensaje en " + (channel.getName() != null ? channel.getName() : "conversación"),
                request.body().trim(), channel.getId().toString(), data);
        List<ChatUnreadService.MemberWatermark> watermarks = chatUnreadService.memberWatermarks(channel.getId());
        return toMessageResponse(message, watermarks);
    }

    @PostMapping("/{id}/messages/file")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ChatMessageResponse uploadFile(@PathVariable UUID id,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(name = "body", required = false) String body,
                                          Authentication authentication) {
        User sender = currentUser(authentication);
        Channel channel = requireChannelAccess(id, sender);
        ChatMessageResponse response = chatFileService.saveFileMessage(id, file, body, authentication.getName());
        // Notificación push a los miembros del canal (excepto el remitente)
        var memberIds = channelMemberRepository.findByChannel_IdOrderByJoinedAtAsc(channel.getId()).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());
        String fileLabel = (file.getContentType() != null && file.getContentType().startsWith("image/")) ? "una imagen"
                : (file.getContentType() != null && file.getContentType().startsWith("audio/")) ? "un audio"
                : "un archivo";
        Map<String, String> data = Map.of(
                "type", "file",
                "messageId", response.id() != null ? response.id().toString() : "",
                "senderId", sender.getId().toString(),
                "senderName", sender.getFullName()
        );
        pushNotificationService.sendPushToChannelMembers(memberIds, sender.getId(),
                "Nuevo archivo en " + (channel.getName() != null ? channel.getName() : "conversación"),
                "Te enviaron " + fileLabel, channel.getId().toString(), data);
        return response;
    }

    @GetMapping("/{id}/messages/{messageId}/file")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id,
                                                 @PathVariable UUID messageId,
                                                 Authentication authentication) {
        return chatFileService.downloadFile(id, messageId, authentication.getName());
    }

    @DeleteMapping("/{id}/messages/{messageId}/file")
    @Transactional
    public ResponseEntity<Void> deleteFile(@PathVariable UUID id,
                                           @PathVariable UUID messageId,
                                           Authentication authentication) {
        chatFileService.deleteFile(id, messageId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/unread-count")
    @Transactional(readOnly = true)
    public Long unreadCount(@PathVariable UUID id, Authentication authentication) {
        return chatUnreadService.unreadCount(id, authentication.getName());
    }

    @GetMapping("/unread-counts")
    @Transactional(readOnly = true)
    public Map<UUID, Long> unreadCounts(Authentication authentication) {
        var actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var members = channelMemberRepository.findByUser_Id(actor.getId());
        var counts = new java.util.LinkedHashMap<UUID, Long>();
        for (var member : members) {
            var channelId = member.getChannel().getId();
            var lastRead = member.getLastReadAt();
            var count = messageRepository.countByChannel_IdAndCreatedAtAfter(
                    channelId, lastRead == null ? LocalDateTime.MIN : lastRead);
            if (count > 0) counts.put(channelId, count);
        }
        return counts;
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id, Authentication authentication) {
        chatUnreadService.markAsRead(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Marca como entregado este canal para el usuario autenticado. */
    @PostMapping("/{id}/deliver")
    @Transactional
    public ResponseEntity<Void> markChannelDelivered(@PathVariable UUID id, Authentication authentication) {
        chatUnreadService.markDeliveredForChannel(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Marca como entregados todos los canales del usuario autenticado (con throttle de 30 s). */
    @PostMapping("/deliver")
    @Transactional
    public ResponseEntity<Void> markAllDelivered(Authentication authentication) {
        chatUnreadService.markAllDelivered(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private Channel requireChannelAccess(UUID channelId, User actor) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal no encontrado"));
        boolean allowed;
        if (channel.getProject() != null) {
            allowed = RoleNames.normalize(actor.getRole()).equals("ADMIN")
                    || projectMemberRepository.existsByProject_IdAndUser_Id(channel.getProject().getId(), actor.getId());
        } else {
            allowed = channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), actor.getId());
        }
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debes pertenecer al canal para verlo o escribir en él");
        }
        return channel;
    }

    private String validateChannelType(@Nullable String type) {
        String normalized = (type == null || type.isBlank()) ? Channel.TYPE_TEXT : type.toUpperCase();
        if (!Channel.TYPE_TEXT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de canal no disponible todavía; solo se permiten salas de texto");
        }
        return normalized;
    }

    private void requireProjectManager(Project project, User actor) {
        if (!project.getOwner().getId().equals(actor.getId()) && !RoleNames.normalize(actor.getRole()).equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el propietario o un administrador puede crear salas en el proyecto");
        }
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private List<Channel> mergeChannels(List<Channel> direct, List<Channel> rooms) {
        Map<UUID, Channel> byId = new LinkedHashMap<>();
        for (Channel channel : direct) byId.put(channel.getId(), channel);
        for (Channel channel : rooms) byId.putIfAbsent(channel.getId(), channel);
        return byId.values().stream()
                .sorted(Comparator.comparing(Channel::getCreatedAt).reversed())
                .toList();
    }

    private ChannelResponse toResponse(Channel channel, User actor) {
        if (channel.getProject() != null) {
            return new ChannelResponse(channel.getId(), channel.getType(), channel.getName(),
                    channel.getProject().getId(), null, null, channel.getCreatedAt());
        }
        return channelMemberRepository.findByChannel_IdOrderByJoinedAtAsc(channel.getId()).stream()
                .filter(member -> !member.getUser().getId().equals(actor.getId()))
                .findFirst()
                .map(other -> new ChannelResponse(channel.getId(), channel.getType(), null, null,
                        other.getUser().getId(), other.getUser().getFullName(), channel.getCreatedAt()))
                .orElseGet(() -> new ChannelResponse(channel.getId(), channel.getType(), null, null,
                        null, null, channel.getCreatedAt()));
    }

    private ChatMessageResponse toMessageResponse(Message message, List<ChatUnreadService.MemberWatermark> watermarks) {
        User author = message.getSender();
        String name = author.getFullName() == null || author.getFullName().isBlank()
                ? author.getEmail() : author.getFullName();
        String downloadUrl = message.getFileStorageKey() != null
                ? "/api/channels/" + message.getChannel().getId() + "/messages/" + message.getId() + "/file"
                : null;
        var createdAt = message.getCreatedAt();
        int recipientCount = 0;
        int deliveredCount = 0;
        int readCount = 0;
        for (var w : watermarks) {
            if (w.userId().equals(author.getId())) continue;
            recipientCount++;
            if (w.lastDeliveredAt() != null && !w.lastDeliveredAt().isBefore(createdAt)) deliveredCount++;
            if (w.lastReadAt() != null && !w.lastReadAt().isBefore(createdAt)) readCount++;
        }
        return new ChatMessageResponse(message.getId(), message.getBody(), author.getId(), name,
                author.getEmail(), createdAt, message.getMessageType(),
                message.getFileName(), message.getFileContentType(), message.getFileSizeBytes(),
                downloadUrl, deliveredCount, readCount, recipientCount);
    }
}
