package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskCommentResponse(UUID id, String body, UUID authorId, String authorName,
                                 String authorEmail, LocalDateTime createdAt) { }
