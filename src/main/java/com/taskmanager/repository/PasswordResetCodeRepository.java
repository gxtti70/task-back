package com.taskmanager.repository;

import com.taskmanager.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {
    List<PasswordResetCode> findByUserIdAndUsedFalse(UUID userId);

    Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
