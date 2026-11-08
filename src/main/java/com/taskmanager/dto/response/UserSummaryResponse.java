package com.taskmanager.dto.response;

import com.taskmanager.entity.PresenceStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserSummaryResponse(UUID id, String email, String name, String role, String avatarUrl,
                                  PresenceStatus presenceStatus, LocalDateTime lastSeenAt) { }
