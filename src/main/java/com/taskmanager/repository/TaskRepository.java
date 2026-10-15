package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    @Query("select distinct t from Task t join fetch t.assignee left join fetch t.project left join fetch t.tags order by t.createdAt desc")
    List<Task> findAllWithAssigneeAndProject();

    long countByProject_Id(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :taskId")
    java.util.Optional<Task> findByIdForUpdate(@Param("taskId") UUID taskId);
}
