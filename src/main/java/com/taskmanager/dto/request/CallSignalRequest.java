package com.taskmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Señal WebRTC reenviada sin interpretar: SDP (OFFER/ANSWER) o candidato ICE
 * serializado (ICE). El backend solo la encola para la otra parte.
 */
public record CallSignalRequest(@NotBlank String type,
                                @NotBlank @Size(max = 64000) String payload) { }
