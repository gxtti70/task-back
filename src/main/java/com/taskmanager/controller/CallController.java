package com.taskmanager.controller;

import com.taskmanager.dto.request.CallSignalRequest;
import com.taskmanager.dto.request.CallStartRequest;
import com.taskmanager.dto.response.CallEventResponse;
import com.taskmanager.dto.response.CallResponse;
import com.taskmanager.entity.Channel;
import com.taskmanager.entity.ChannelMember;
import com.taskmanager.entity.User;
import com.taskmanager.repository.ChannelMemberRepository;
import com.taskmanager.repository.ChannelRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.CallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Señalización de videollamadas WebRTC entre dos usuarios de un DM.
 *
 * <p>El media viaja P2P; este controlador solo crea la sesión, reenvía SDP y
 * candidatos ICE, y devuelve los cambios de estado mediante sondeo con cursor.
 */
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {
    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;
    private final CallService callService;

    /** Inicia una llamada en un DM; queda RINGING hasta que la otra parte acepte, rechace o venza el tiempo. */
    @PostMapping
    @Transactional
    public ResponseEntity<CallResponse> startCall(@Valid @RequestBody CallStartRequest request,
                                                  Authentication authentication) {
        User actor = currentUser(authentication);
        Channel channel = requireDirectChannel(request.channelId(), actor);
        UUID peer = requirePeer(channel, actor);
        CallService.CallSession session = callService.start(channel.getId(), actor.getId(), peer);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    /** Llamadas en las que participo y que aún no han finalizado: así se descubren las entrantes. */
    @GetMapping("/active")
    public List<CallResponse> activeCalls(Authentication authentication) {
        User actor = currentUser(authentication);
        return callService.activeFor(actor.getId()).stream().map(this::toResponse).toList();
    }

    /** Eventos de la llamada posteriores al cursor {@code after} (sondeo del cliente). */
    @GetMapping("/{id}/events")
    public List<CallEventResponse> events(@PathVariable UUID id,
                                          @RequestParam(name = "after", defaultValue = "0") long after,
                                          Authentication authentication) {
        User actor = currentUser(authentication);
        return callService.events(id, actor.getId(), after).stream()
                .map(event -> new CallEventResponse(event.seq(), event.type(), event.fromUserId(),
                        event.payload(), event.state(), event.reason(), event.createdAt()))
                .toList();
    }

    /** Encola una señal WebRTC (OFFER/ANSWER/ICE) para la otra parte. */
    @PostMapping("/{id}/signal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signal(@PathVariable UUID id, @Valid @RequestBody CallSignalRequest request,
                       Authentication authentication) {
        User actor = currentUser(authentication);
        callService.signal(id, actor.getId(), request.type(), request.payload());
    }

    /** Acepta la llamada entrante (solo la persona llamada). */
    @PostMapping("/{id}/answer")
    public CallResponse answer(@PathVariable UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        return toResponse(callService.answer(id, actor.getId()));
    }

    /** Rechaza la llamada entrante (solo la persona llamada). */
    @PostMapping("/{id}/decline")
    public CallResponse decline(@PathVariable UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        return toResponse(callService.decline(id, actor.getId()));
    }

    /** Cuelga desde cualquiera de los dos lados; idempotente si ya finalizó. */
    @PostMapping("/{id}/end")
    public CallResponse end(@PathVariable UUID id, Authentication authentication) {
        User actor = currentUser(authentication);
        return toResponse(callService.end(id, actor.getId()));
    }

    private Channel requireDirectChannel(UUID channelId, User actor) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal no encontrado"));
        if (channel.getProject() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las llamadas solo están disponibles en mensajes directos");
        }
        if (!channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Debes pertenecer a la conversación para llamar");
        }
        return channel;
    }

    /** La otra persona del DM: sin ella no hay llamada posible. */
    private UUID requirePeer(Channel channel, User actor) {
        return channelMemberRepository.findByChannel_IdOrderByJoinedAtAsc(channel.getId()).stream()
                .map(member -> member.getUser().getId())
                .filter(id -> !id.equals(actor.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La conversación no tiene a otra persona"));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private CallResponse toResponse(CallService.CallSession session) {
        return new CallResponse(session.getId(), session.getChannelId(), session.getCallerId(),
                session.getCalleeId(), session.getState(), session.getEndReason(),
                session.getSeq(), session.getCreatedAt());
    }
}
