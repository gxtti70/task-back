package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(UUID id, String body, UUID authorId, String authorName,
                                  String authorEmail, LocalDateTime createdAt,
                                  String messageType,
                                  String fileName, String fileContentType, Long fileSizeBytes, String downloadUrl) { }
