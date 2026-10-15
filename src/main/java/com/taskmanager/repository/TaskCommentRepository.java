package com.taskmanager.repository;

import com.taskmanager.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    @Query("select c from TaskComment c join fetch c.author where c.task.id = :taskId order by c.createdAt asc")
    List<TaskComment> findAllByTaskIdWithAuthor(UUID taskId);
}
