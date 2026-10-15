package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

public record TaskAttachmentResponse(UUID id, String filename, String contentType, long sizeBytes,
                                     String downloadUrl, String uploadUrl, Map<String, String> uploadFields,
                                     LocalDateTime createdAt) { }
