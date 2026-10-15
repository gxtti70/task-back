package com.taskmanager.repository;

import com.taskmanager.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findByUserIdAndUsedFalse(UUID userId);
}
