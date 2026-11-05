package com.taskmanager.repository;

import com.taskmanager.entity.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {
    List<ChannelMember> findByChannel_IdOrderByJoinedAtAsc(UUID channelId);
    boolean existsByChannel_IdAndUser_Id(UUID channelId, UUID userId);
    void deleteByChannel_IdAndUser_Id(UUID channelId, UUID userId);
}
