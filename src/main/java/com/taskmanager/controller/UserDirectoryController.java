package com.taskmanager.controller;

import com.taskmanager.dto.response.UserSummaryResponse;
import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserDirectoryController {
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getActiveUsers() {
        return userRepository.findAllByActiveTrueOrderByFullNameAsc().stream().map(user -> {
            String name = user.getFullName() == null ? user.getEmail() : user.getFullName();
            String avatar = "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "&background=27272a&color=a1a1aa&bold=true";
            PresenceStatus status = presenceService.effectiveStatus(user);
            return new UserSummaryResponse(user.getId(), user.getEmail(), name, user.getRole(), avatar,
                    status, user.getLastSeenAt());
        }).toList();
    }
}
