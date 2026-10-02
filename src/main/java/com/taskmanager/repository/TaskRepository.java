package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    @Query("select distinct t from Task t join fetch t.assignee left join fetch t.project left join fetch t.tags order by t.createdAt desc")
    List<Task> findAllWithAssigneeAndProject();

    long countByProject_Id(UUID projectId);
}
