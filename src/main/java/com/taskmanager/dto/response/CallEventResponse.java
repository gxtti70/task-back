package com.taskmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evento de una llamada con cursor incremental.
 *
 * <p>{@code type}: {@code OFFER} / {@code ANSWER} / {@code ICE} traen la señal en
 * {@code payload}; {@code STATE} notifica transiciones de estado (RINGING, ACTIVE,
 * ENDED) usando {@code state} y, si la llamada terminó, {@code reason}
 * (DECLINED, CANCELLED, MISSED, FINISHED).
 */
public record CallEventResponse(long seq, String type, UUID fromUserId, String payload,
                                String state, String reason, LocalDateTime createdAt) { }
