package com.taskmanager.dto.response;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String email, String name, String role, String avatarUrl) { }
