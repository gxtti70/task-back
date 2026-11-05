package com.taskmanager.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "channels")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Channel {
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_VOICE = "VOICE";

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Builder.Default
    @Column(nullable = false, updatable = false, length = 16)
    private String type = TYPE_TEXT;

    @Size(max = 100)
    @Column(length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    // Par normalizado (id menor / mayor) que identifica el DM; null para salas de proyecto.
    @Column(name = "dm_user_low", updatable = false)
    private UUID dmUserLow;

    @Column(name = "dm_user_high", updatable = false)
    private UUID dmUserHigh;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
