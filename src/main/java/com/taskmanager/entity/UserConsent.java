package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro inmutable de la autorización otorgada por el titular para un
 * documento y versión concretos. Es la prueba exigible por la SIC.
 */
@Entity
@Table(name = "user_consents", indexes = {
        @Index(name = "user_consents_user_idx", columnList = "user_id"),
        @Index(name = "user_consents_doc_idx", columnList = "user_id,document")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserConsent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document", nullable = false, length = 20)
    private ConsentDocument document;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;
}
