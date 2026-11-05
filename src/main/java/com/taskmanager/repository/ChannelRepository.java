package com.taskmanager.repository;

import com.taskmanager.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    List<Channel> findByProject_IdOrderByNameAsc(UUID projectId);
    boolean existsByProject_IdAndName(UUID projectId, String name);
    Optional<Channel> findByDmUserLowAndDmUserHigh(UUID dmUserLow, UUID dmUserHigh);

    @Query("select c from Channel c where c.project.id in :projectIds order by c.createdAt desc")
    List<Channel> findByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            select c from Channel c
            where exists (select m from ChannelMember m
                          where m.channel.id = c.id and m.user.id = :userId)
            order by c.createdAt desc
            """)
    List<Channel> findByDmMembership(@Param("userId") UUID userId);
}
