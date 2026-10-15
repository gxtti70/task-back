package com.taskmanager.repository;
import com.taskmanager.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AnnouncementRepository extends JpaRepository<Announcement, String> {
    List<Announcement> findByActiveTrueOrderByCreatedAtDesc();
}
