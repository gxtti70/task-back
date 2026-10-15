package com.taskmanager.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_data", columnDefinition = "TEXT")
    private String avatarData;

    @Column(name = "profile_timezone", length = 64)
    private String profileTimezone;

    @Column(name = "week_starts_on")
    private Integer weekStartsOn;

    @Column(name = "profile_job_title", length = 100)
    private String profileJobTitle;

    @Column(name = "profile_bio", length = 500)
    private String profileBio;

    @Column(name = "profile_accent_color", length = 7)
    private String profileAccentColor;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String password;

    @NotBlank
    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
