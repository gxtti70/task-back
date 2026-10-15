package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTaskAttachmentRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 150) String contentType,
        @Positive long sizeBytes) { }
