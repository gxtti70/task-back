package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "announcement_dismissals", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "announcement_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnnouncementDismissal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "announcement_key", nullable = false) private Announcement announcement;
    @Column(name = "dismissed_at", nullable = false) private LocalDateTime dismissedAt;
    @PrePersist void created() { if (dismissedAt == null) dismissedAt = LocalDateTime.now(); }
}
