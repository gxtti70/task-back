package com.taskmanager.repository;
import com.taskmanager.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    Optional<UserSubscription> findByUser_Id(UUID userId);
    Optional<UserSubscription> findByProviderSubscriptionId(String id);
}
