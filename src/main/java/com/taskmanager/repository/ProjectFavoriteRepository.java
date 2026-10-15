package com.taskmanager.repository;

import com.taskmanager.entity.ProjectFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectFavoriteRepository extends JpaRepository<ProjectFavorite, UUID> {
    boolean existsByUser_IdAndProject_Id(UUID userId, UUID projectId);
    void deleteByUser_IdAndProject_Id(UUID userId, UUID projectId);

    @Query("select f from ProjectFavorite f join fetch f.project where f.user.id = :userId order by f.createdAt desc")
    List<ProjectFavorite> findAllByUserIdWithProject(@Param("userId") UUID userId);
}
