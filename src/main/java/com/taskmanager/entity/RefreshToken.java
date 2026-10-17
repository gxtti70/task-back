package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "refresh_tokens_family_idx", columnList = "family_id"),
        @Index(name = "refresh_tokens_user_idx", columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {
    @Id
    private UUID id;
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private boolean revoked;
    @Column(name = "replaced_by")
    private UUID replacedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
