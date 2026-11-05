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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatUnreadService {
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
        member.setLastReadAt(LocalDateTime.now());
        channelMembers.save(member);
    }
}
