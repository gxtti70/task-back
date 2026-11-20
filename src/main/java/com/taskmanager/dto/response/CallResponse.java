package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Instantánea de una llamada. {@code seq} es el cursor actual de eventos: el cliente
 * conserva su propio último {@code seq} por llamada (iniciado en 0) y sondea con
 * {@code GET /api/calls/{id}/events?after=<seq>}.
 */
public record CallResponse(UUID id, UUID channelId, UUID callerId, UUID calleeId,
                           String state, String endReason, long seq, LocalDateTime createdAt) { }
