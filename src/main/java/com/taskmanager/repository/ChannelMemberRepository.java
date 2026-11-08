package com.taskmanager.repository;

import com.taskmanager.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {

    @Modifying
    @Query("""
            update ChannelMember m
            set m.lastDeliveredAt = :now
            where m.user.id = :userId
              and (m.lastDeliveredAt is null or m.lastDeliveredAt < :threshold)
            """)
    int markAllDelivered(@Param("userId") UUID userId,
                         @Param("now") LocalDateTime now,
                         @Param("threshold") LocalDateTime threshold);

    List<ChannelMember> findByChannel_Id(UUID channelId);
    List<ChannelMember> findByChannel_IdOrderByJoinedAtAsc(UUID channelId);
    boolean existsByChannel_IdAndUser_Id(UUID channelId, UUID userId);
    ChannelMember findByChannel_IdAndUser_Id(UUID channelId, UUID userId);
    List<ChannelMember> findByUser_Id(UUID userId);
    void deleteByChannel_IdAndUser_Id(UUID channelId, UUID userId);
}
