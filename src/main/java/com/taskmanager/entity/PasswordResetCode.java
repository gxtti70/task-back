package com.taskmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Código de verificación de 6 dígitos usado para restablecer la contraseña.
 * <p>
 * Nunca se persiste el código en texto plano: solo su HMAC-SHA256 con el pepper
 * del servidor, lo que permite localizar el registro por código y a la vez impide
 * recuperar los códigos a partir de una filtración de la base de datos.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetCode {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
