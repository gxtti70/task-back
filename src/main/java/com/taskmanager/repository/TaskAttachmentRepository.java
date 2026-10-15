package com.taskmanager.repository;

import com.taskmanager.entity.TaskAttachment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {
    @Query("select a from TaskAttachment a where a.task.id = :taskId and a.uploaded = true order by a.createdAt desc")
    List<TaskAttachment> findUploadedByTaskId(@Param("taskId") UUID taskId);

    @Query("select count(a) from TaskAttachment a where a.task.id = :taskId and (a.uploaded = true or a.uploadExpiresAt > :now)")
    long countActiveByTaskId(@Param("taskId") UUID taskId, @Param("now") LocalDateTime now);

    Optional<TaskAttachment> findByIdAndTask_Id(UUID id, UUID taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TaskAttachment a where a.id = :id")
    Optional<TaskAttachment> findByIdForUpdate(@Param("id") UUID id);
}
