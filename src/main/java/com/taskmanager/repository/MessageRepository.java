package com.taskmanager.repository;

import com.taskmanager.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    @Query("""
            select m from Message m
            where m.channel.id = :channelId
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findLatestPage(@Param("channelId") UUID channelId, Pageable limit);

    @Query("""
            select m from Message m
            where m.channel.id = :channelId
              and (m.createdAt < :cursorCreated or (m.createdAt = :cursorCreated and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findBefore(@Param("channelId") UUID channelId,
                             @Param("cursorCreated") LocalDateTime cursorCreated,
                             @Param("cursorId") UUID cursorId,
                             Pageable limit);
}
