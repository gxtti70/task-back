package com.taskmanager.dto.response;

import java.util.UUID;

public record FavoriteProjectResponse(UUID id, String name, String status) { }
