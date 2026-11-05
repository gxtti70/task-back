package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Creación de una sala de proyecto (canal tipo TEXT/VOICE dentro de un proyecto). */
public record ChannelRequest(@NotNull UUID projectId,
                             @NotBlank @Size(max = 100) String name,
                             String type) { }
