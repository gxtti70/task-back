package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {
    @Id @Column(length = 100) private String key;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 4000) private String body;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
