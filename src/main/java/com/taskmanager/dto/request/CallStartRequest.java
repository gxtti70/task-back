package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Inicio de una videollamada: el canal debe ser un DM entre dos usuarios. */
public record CallStartRequest(@NotNull UUID channelId) { }
