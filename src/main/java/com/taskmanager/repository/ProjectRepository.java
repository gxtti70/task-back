package com.taskmanager.repository;

import com.taskmanager.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Query("select p from Project p join fetch p.owner order by p.createdAt desc")
    List<Project> findAllWithOwner();
    long countByOwner_Id(java.util.UUID ownerId);
    @org.springframework.data.jpa.repository.Query(value = "SELECT COALESCE(MAX(member_count), 0) FROM (SELECT COUNT(*) AS member_count FROM project_members pm JOIN projects p ON p.id = pm.project_id WHERE p.owner_id = :ownerId GROUP BY pm.project_id) counts", nativeQuery = true)
    long maxMembersByOwner(@org.springframework.data.repository.query.Param("ownerId") java.util.UUID ownerId);
}
