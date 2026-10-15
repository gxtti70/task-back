package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSubscription {
    @Id @GeneratedValue(generator = "UUID") @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) @Builder.Default private SubscriptionTier tier = SubscriptionTier.FREE;
    @Column(nullable = false, length = 24) @Builder.Default private String status = "ACTIVE";
    @Column(name = "current_period_start") private LocalDateTime currentPeriodStart;
    @Column(name = "current_period_end") private LocalDateTime currentPeriodEnd;
    @Column(nullable = false, length = 16) @Builder.Default private String provider = "DEV";
    @Column(name = "provider_subscription_id", unique = true) private String providerSubscriptionId;
    @Column(name = "cancel_at_period_end", nullable = false) @Builder.Default private boolean cancelAtPeriodEnd = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
