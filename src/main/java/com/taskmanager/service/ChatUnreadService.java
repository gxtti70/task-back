package com.taskmanager.service;

import com.taskmanager.entity.ChannelMember;
import com.taskmanager.entity.Message;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ChannelMemberRepository;
import com.taskmanager.repository.MessageRepository;
import com.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatUnreadService {
    private static final long DELIVERY_THROTTLE_SECONDS = 30;

    private final ChannelMemberRepository channelMembers;
    private final MessageRepository messages;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public long unreadCount(UUID channelId, String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var member = channelMembers.findByChannel_IdAndUser_Id(channelId, actor.getId());
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a este canal");
        }
        var lastRead = member.getLastReadAt();
        return messages.countByChannel_IdAndCreatedAtAfter(channelId, lastRead == null ? LocalDateTime.MIN : lastRead);
    }

    @Transactional
    public void markAsRead(UUID channelId, String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var member = channelMembers.findByChannel_IdAndUser_Id(channelId, actor.getId());
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a este canal");
        }
        var now = LocalDateTime.now();
        member.setLastReadAt(now);
        if (member.getLastDeliveredAt() == null || member.getLastDeliveredAt().isBefore(now)) {
            member.setLastDeliveredAt(now);
        }
        channelMembers.save(member);
    }

    /** Marca como entregados todos los canales del usuario (máximo una vez cada 30 s para no reescribir filas). */
    @Transactional
    public void markAllDelivered(String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var now = LocalDateTime.now();
        var threshold = now.minusSeconds(DELIVERY_THROTTLE_SECONDS);
        channelMembers.markAllDelivered(actor.getId(), now, threshold);
    }

    /** Marca como entregado un canal concreto del usuario. */
    @Transactional
    public void markDeliveredForChannel(UUID channelId, String actorEmail) {
        var actor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var member = channelMembers.findByChannel_IdAndUser_Id(channelId, actor.getId());
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a este canal");
        }
        var now = LocalDateTime.now();
        if (member.getLastDeliveredAt() == null || member.getLastDeliveredAt().isBefore(now)) {
            member.setLastDeliveredAt(now);
            channelMembers.save(member);
        }
    }

    @Transactional(readOnly = true)
    public List<MemberWatermark> memberWatermarks(UUID channelId) {
        return channelMembers.findByChannel_Id(channelId).stream()
                .map(m -> new MemberWatermark(m.getUser().getId(), m.getLastDeliveredAt(), m.getLastReadAt()))
                .toList();
    }

    public record MemberWatermark(UUID userId, LocalDateTime lastDeliveredAt, LocalDateTime lastReadAt) {
    }
}
