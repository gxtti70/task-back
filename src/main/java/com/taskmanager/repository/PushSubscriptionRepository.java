package com.taskmanager.repository;

import com.taskmanager.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    List<PushSubscription> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM PushSubscription p WHERE p.userId = ?1 AND p.endpoint = ?2")
    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
