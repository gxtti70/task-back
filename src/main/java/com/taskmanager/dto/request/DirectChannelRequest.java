package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Inicio de un DM global entre el usuario autenticado y otro usuario. Idempotente por par. */
public record DirectChannelRequest(@NotNull UUID memberId) { }
