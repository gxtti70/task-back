package com.taskmanager.service;

import com.taskmanager.config.VapidProperties;
import com.taskmanager.entity.PushSubscription;
import com.taskmanager.entity.PresenceStatus;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PushSubscriptionRepository;
import com.taskmanager.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService implements InitializingBean {

    private static final String PUSH_ENCODING = "aes128gcm";
    private static final int SALT_SIZE = 16;
    private static final int NONCE_SIZE = 12;
    private static final int KEY_SIZE = 16;

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final VapidProperties vapidProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private PrivateKey vapidPrivateKey;
    private PublicKey vapidPublicKey;

    @Override
    public void afterPropertiesSet() {
        try {
            if (vapidProperties.getPrivateKey() != null && !vapidProperties.getPrivateKey().isBlank()
                    && vapidProperties.getPublicKey() != null && !vapidProperties.getPublicKey().isBlank()) {
                vapidPrivateKey = decodeVapidPrivateKey(vapidProperties.getPrivateKey());
                vapidPublicKey = decodeVapidPublicKey(vapidProperties.getPublicKey());
            } else {
                KeyPair keyPair = generateVapidKeyPair();
                vapidPrivateKey = keyPair.getPrivate();
                vapidPublicKey = keyPair.getPublic();
                log.warn("VAPID keys generated at startup. Public key: {}", encodeVapidPublicKey(vapidPublicKey));
                log.warn("Set vapid.public-key and vapid.private-key in application.properties for stable keys.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron inicializar las claves VAPID", e);
        }
    }

    public String getVapidPublicKey() {
        return encodeVapidPublicKey(vapidPublicKey);
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dhKey, String authKey) {
        if (userId == null || endpoint == null || p256dhKey == null || authKey == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Datos de suscripción incompletos");
        }
        var existing = subscriptionRepository.findByUserId(userId).stream()
                .filter(s -> s.getEndpoint().equals(endpoint))
                .findFirst();
        if (existing.isPresent()) {
            var sub = existing.get();
            sub.setP256dhKey(p256dhKey);
            sub.setAuthKey(authKey);
            subscriptionRepository.save(sub);
        } else {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            subscriptionRepository.save(PushSubscription.builder()
                    .userId(userId)
                    .endpoint(endpoint)
                    .p256dhKey(p256dhKey)
                    .authKey(authKey)
                    .build());
        }
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    @Async
    public void sendPushToUser(UUID userId, String title, String body, String channelId, Map<String, String> data) {
        List<PushSubscription> subs = subscriptionRepository.findByUserId(userId);
        for (PushSubscription sub : subs) {
            sendPush(sub, title, body, channelId, data);
        }
    }

    @Async
    public void sendPushToChannelMembers(Set<UUID> memberIds, UUID senderId, String title, String body, String channelId, Map<String, String> data) {
        List<User> members = userRepository.findAllById(memberIds);
        Map<UUID, User> memberById = new HashMap<>();
        for (User member : members) memberById.put(member.getId(), member);
        for (UUID memberId : memberIds) {
            if (memberId.equals(senderId)) continue;
            User member = memberById.get(memberId);
            if (member == null) continue;
            // "No molestar" suprime el push para ese destinatario.
            if (presenceService.effectiveStatus(member) == PresenceStatus.DO_NOT_DISTURB) continue;
            sendPushToUser(memberId, title, body, channelId, data);
        }
    }

    private void sendPush(PushSubscription sub, String title, String body, String channelId, Map<String, String> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("channelId", channelId);
            if (data != null) payload.putAll(data);
            payload.put("timestamp", System.currentTimeMillis());

            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

            String vapidJwt = createVapidJwt(sub.getEndpoint());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sub.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .header("TTL", "86400")
                    .header("Urgency", "high")
                    .header("Authorization", "WebPush " + vapidJwt)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        int status = response.statusCode();
                        if (status == 410 || status == 404) {
                            subscriptionRepository.deleteById(sub.getId());
                            log.debug("Suscripción eliminada (endpoint inválido): {}", sub.getEndpoint());
                        } else if (status >= 200 && status < 300) {
                            log.debug("Push enviada a {}", sub.getEndpoint());
                        } else {
                            log.warn("Push falló con estado {} para {}", status, sub.getEndpoint());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Error enviando push a {}: {}", sub.getEndpoint(), ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Error preparando push para {}: {}", sub.getEndpoint(), e.getMessage());
        }
    }

    private String createVapidJwt(String audience) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(86400);

        String jwt = Jwts.builder()
                .setIssuer("https://taskmanager.local")
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .setSubject(vapidProperties.getSubject())
                .signWith(vapidPrivateKey, SignatureAlgorithm.ES256)
                .compact();

        return jwt;
    }

    private KeyPair generateVapidKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return keyGen.generateKeyPair();
    }

    private PublicKey decodeVapidPublicKey(String b64) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(b64);
        if (bytes.length == 65 && bytes[0] == 0x04) {
            byte[] x = Arrays.copyOfRange(bytes, 1, 33);
            byte[] y = Arrays.copyOfRange(bytes, 33, 65);
            java.security.spec.EllipticCurve curve = p256Curve();
            java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                    new java.math.BigInteger(1, x), new java.math.BigInteger(1, y));
            java.security.spec.ECParameterSpec ecSpec = new java.security.spec.ECParameterSpec(curve, point,
                    n(), 1);
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePublic(new java.security.spec.ECPublicKeySpec(point, ecSpec));
        }
        throw new IllegalArgumentException("Invalid VAPID public key format");
    }

    private PrivateKey decodeVapidPrivateKey(String b64) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(b64);
        java.math.BigInteger d = new java.math.BigInteger(1, bytes);
        java.security.spec.EllipticCurve curve = p256Curve();
        java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                p(), new java.math.BigInteger("0"));
        java.security.spec.ECParameterSpec ecSpec = new java.security.spec.ECParameterSpec(curve, point, n(), 1);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(new java.security.spec.ECPrivateKeySpec(d, ecSpec));
    }

    private PublicKey decodeP256dhPublicKey(String b64) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(b64);
        if (bytes.length == 65 && bytes[0] == 0x04) {
            byte[] x = Arrays.copyOfRange(bytes, 1, 33);
            byte[] y = Arrays.copyOfRange(bytes, 33, 65);
            java.security.spec.EllipticCurve curve = p256Curve();
            java.security.spec.ECPoint point = new java.security.spec.ECPoint(
                    new java.math.BigInteger(1, x), new java.math.BigInteger(1, y));
            java.security.spec.ECParameterSpec ecSpec = new java.security.spec.ECParameterSpec(curve, point, n(), 1);
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePublic(new java.security.spec.ECPublicKeySpec(point, ecSpec));
        }
        throw new IllegalArgumentException("Invalid P-256 public key format");
    }

    private String encodeVapidPublicKey(PublicKey key) {
        try {
            java.security.interfaces.ECPublicKey ecKey = (java.security.interfaces.ECPublicKey) key;
            java.math.BigInteger x = ecKey.getW().getAffineX();
            java.math.BigInteger y = ecKey.getW().getAffineY();
            byte[] x32 = padTo32(x.toByteArray());
            byte[] y32 = padTo32(y.toByteArray());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x04);
            baos.write(x32);
            baos.write(y32);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo codificar la clave pública VAPID", e);
        }
    }

    private byte[] padTo32(byte[] input) {
        if (input.length == 32) return input;
        byte[] out = new byte[32];
        if (input.length > 32) {
            System.arraycopy(input, input.length - 32, out, 0, 32);
        } else {
            System.arraycopy(input, 0, out, 32 - input.length, input.length);
        }
        return out;
    }

    // P-256 curve parameters
    private java.security.spec.EllipticCurve p256Curve() {
        return new java.security.spec.EllipticCurve(
                new java.security.spec.ECFieldFp(p()),
                a(), b());
    }

    private java.math.BigInteger p() {
        return new java.math.BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853951");
    }

    private java.math.BigInteger n() {
        return new java.math.BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948");
    }

    private java.math.BigInteger a() {
        return new java.math.BigInteger("115792089210356248762697446949407573530086143415290314195533631308867097853948");
    }

    private java.math.BigInteger b() {
        return new java.math.BigInteger("410583637251521421293261297800472684091144410159937255548352506690938672209960");
    }
}
