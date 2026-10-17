package com.taskmanager.repository;

import com.taskmanager.entity.ConsentDocument;
import com.taskmanager.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    List<UserConsent> findByUserId(UUID userId);

    boolean existsByUserIdAndDocument(UUID userId, ConsentDocument document);
}
