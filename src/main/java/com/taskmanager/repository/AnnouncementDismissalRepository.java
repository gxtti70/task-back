package com.taskmanager.repository;
import com.taskmanager.entity.AnnouncementDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AnnouncementDismissalRepository extends JpaRepository<AnnouncementDismissal, Long> {
    boolean existsByUser_IdAndAnnouncement_Key(java.util.UUID userId, String key);
    List<AnnouncementDismissal> findByUser_Id(java.util.UUID userId);
}
