package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String fullName,
        @Size(max = 700_000, message = "La imagen supera el tamaño permitido") String avatarData,
        @Size(max = 64, message = "La zona horaria no es válida") String timezone,
        Integer weekStartsOn,
        @Size(max = 100, message = "El cargo no puede superar 100 caracteres") String jobTitle,
        @Size(max = 500, message = "La biografía no puede superar 500 caracteres") String bio,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "El color debe tener formato hexadecimal #RRGGBB")
        String accentColor
) { }
