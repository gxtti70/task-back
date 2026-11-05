package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChannelResponse(UUID id, String type, String name, UUID projectId,
                              UUID otherUserId, String otherUserName, LocalDateTime createdAt) { }
