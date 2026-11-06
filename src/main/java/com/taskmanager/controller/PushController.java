package com.taskmanager.controller;

import com.taskmanager.entity.User;
import com.taskmanager.repository.PushSubscriptionRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.PushNotificationService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    @GetMapping("/vapid-public-key")
    @Transactional(readOnly = true)
    public Map<String, String> getVapidPublicKey() {
        return Map.of("publicKey", pushNotificationService.getVapidPublicKey());
    }

    @PostMapping("/subscribe")
    @Transactional
    public ResponseEntity<Void> subscribe(Authentication authentication,
                                          @RequestBody PushSubscriptionRequest request) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
        pushNotificationService.subscribe(actor.getId(), request.endpoint(), request.p256dhKey(), request.authKey());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unsubscribe")
    @Transactional
    public ResponseEntity<Void> unsubscribe(Authentication authentication,
                                            @RequestBody PushSubscriptionRequest request) {
        User actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
        pushNotificationService.unsubscribe(actor.getId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    public record PushSubscriptionRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dhKey,
            @NotBlank String authKey
    ) {}
}
