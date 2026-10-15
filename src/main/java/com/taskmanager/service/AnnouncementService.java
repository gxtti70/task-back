package com.taskmanager.service;

import com.taskmanager.dto.response.AnnouncementResponse;
import com.taskmanager.entity.*;
import com.taskmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AnnouncementService {
    private final AnnouncementRepository announcements;
    private final AnnouncementDismissalRepository dismissals;
    private final UserRepository users;
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> pending(UUID userId) {
        var dismissed = dismissals.findByUser_Id(userId).stream().map(d -> d.getAnnouncement().getKey()).collect(java.util.stream.Collectors.toSet());
        return announcements.findByActiveTrueOrderByCreatedAtDesc().stream().filter(a -> !dismissed.contains(a.getKey()))
                .map(a -> new AnnouncementResponse(a.getKey(), a.getTitle(), a.getBody())).toList();
    }
    @Transactional
    public void dismiss(UUID userId, String key) {
        if (dismissals.existsByUser_IdAndAnnouncement_Key(userId, key)) return;
        Announcement announcement = announcements.findById(key).orElse(null);
        if (announcement == null || !announcement.isActive()) return;
        User user = users.findById(userId).orElseThrow();
        dismissals.save(AnnouncementDismissal.builder().user(user).announcement(announcement).build());
    }
    @Transactional
    public void seedWelcome() {
        announcements.findById("welcome-v1").orElseGet(() -> announcements.save(Announcement.builder().key("welcome-v1")
                .title("Bienvenido a TaskManager").body("Organiza tus proyectos, prioriza tareas y mantén a tu equipo alineado desde un solo lugar.").active(true).build()));
    }
}
