package com.taskmanager.controller;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.*;
import com.taskmanager.entity.*;
import com.taskmanager.repository.*;
import com.taskmanager.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RestController @RequestMapping("/api/subscriptions") @RequiredArgsConstructor
public class SubscriptionController {
    private final UserRepository users;
    private final UserSubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final PlanCatalog plans;
    private final SubscriptionService service;
    private final StripeGateway stripe;
    private final TaskManagerProperties properties;
    private final ObjectMapper mapper;

    @GetMapping("/plans") public java.util.List<PlanResponse> plans() { return plans.all(); }
    @GetMapping("/me") public SubscriptionResponse mine(Authentication auth) { return service.getMine(currentUser(auth).getId()); }

    @PostMapping("/checkout")
    @Transactional
    public CheckoutResponse checkout(@RequestBody CheckoutRequest request, Authentication auth) {
        User user = currentUser(auth);
        if (request.tier() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan no válido");
        if (plans.simulate()) return new CheckoutResponse(null, service.activateForDevelopment(user.getId(), request.tier()));
        if (request.tier() == SubscriptionTier.FREE) { service.activate(user.getId(), request.tier(), "STRIPE", null); return new CheckoutResponse(null, service.getMine(user.getId())); }
        return new CheckoutResponse(stripe.checkoutUrl(user, request.tier()), null);
    }

    @PostMapping("/create-payment-intent")
    @Transactional
    public PaymentIntentResponse createPaymentIntent(@RequestBody CheckoutRequest request, Authentication auth) {
        if (request == null || request.tier() == null || request.tier() == SubscriptionTier.FREE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan de pago no válido");
        if (plans.simulate()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los pagos Stripe están desactivados en este entorno");
        User user = currentUser(auth);
        StripeGateway.PaymentIntentSession session = stripe.createSubscriptionPaymentIntent(user, request.tier());
        UserSubscription subscription = service.getOrCreate(user.getId());
        subscription.setTier(request.tier());
        subscription.setStatus("INCOMPLETE");
        subscription.setProvider("STRIPE");
        subscription.setProviderSubscriptionId(session.subscriptionId());
        subscription.setCancelAtPeriodEnd(false);
        subscriptions.save(subscription);
        return new PaymentIntentResponse(session.clientSecret());
    }

    @PostMapping("/dev-activate/{tier}")
    public SubscriptionResponse activate(@PathVariable SubscriptionTier tier, Authentication auth) {
        return service.activateForDevelopment(currentUser(auth).getId(), tier);
    }

    @PostMapping("/cancel")
    @Transactional
    public SubscriptionResponse cancel(Authentication auth) {
        UserSubscription sub = service.getOrCreate(currentUser(auth).getId());
        if (sub.getTier() == SubscriptionTier.FREE) return service.getMine(sub.getUser().getId());
        if (!plans.simulate() && sub.getProviderSubscriptionId() != null) stripe.cancelAtPeriodEnd(sub.getProviderSubscriptionId());
        sub.setCancelAtPeriodEnd(true); subscriptions.save(sub);
        return service.getMine(sub.getUser().getId());
    }

    @PostMapping("/webhook")
    @Transactional
    public void webhook(@RequestBody String payload, @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!validSignature(payload, signature, properties.getBilling().getStripeWebhookSecret()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma de webhook no válida");
        try {
            JsonNode event = mapper.readTree(payload);
            String type = event.path("type").asText();
            JsonNode object = event.path("data").path("object");
            if (type.equals("checkout.session.completed")) completeCheckout(object);
            if (type.equals("customer.subscription.updated") || type.equals("customer.subscription.deleted")) updateStripeSubscription(object, type.endsWith("deleted"));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento Stripe inválido"); }
    }

    private void completeCheckout(JsonNode session) {
        String userId = session.path("metadata").path("userId").asText(session.path("client_reference_id").asText(""));
        String tierName = session.path("metadata").path("tier").asText("");
        if (userId.isBlank() || tierName.isBlank()) return;
        UUID id = UUID.fromString(userId); SubscriptionTier tier = SubscriptionTier.valueOf(tierName);
        String providerSub = session.path("subscription").asText(null);
        UserSubscription sub = service.activate(id, tier, "STRIPE", providerSub);
        String paymentId = session.path("payment_intent").asText(session.path("id").asText(null));
        if (paymentId != null && !payments.existsByProviderPaymentId(paymentId)) {
            String currency = session.path("currency").asText(plans.get(tier).currency()).toUpperCase();
            java.math.BigDecimal amount = java.math.BigDecimal.valueOf(session.path("amount_total").asLong(0), 2);
            payments.save(Payment.builder().user(sub.getUser()).tier(tier).amount(amount).currency(currency).provider("STRIPE").status("SUCCEEDED").providerPaymentId(paymentId).build());
        }
    }

    private void updateStripeSubscription(JsonNode object, boolean deleted) {
        String id = object.path("id").asText("");
        subscriptions.findByProviderSubscriptionId(id).ifPresent(sub -> {
            sub.setCancelAtPeriodEnd(object.path("cancel_at_period_end").asBoolean(false));
            long end = object.path("current_period_end").asLong(0);
            if (end > 0) sub.setCurrentPeriodEnd(LocalDateTime.ofInstant(Instant.ofEpochSecond(end), ZoneOffset.UTC));
            if (deleted) { sub.setStatus("CANCELED"); sub.setTier(SubscriptionTier.FREE); }
            else sub.setStatus(object.path("status").asText("ACTIVE").toUpperCase());
            subscriptions.save(sub);
        });
    }

    private boolean validSignature(String payload, String header, String secret) {
        if (secret == null || secret.isBlank() || header == null) return false;
        try {
            String timestamp = null, expected = null;
            for (String part : header.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && pair[0].equals("t")) timestamp = pair[1];
                if (pair.length == 2 && pair[0].equals("v1")) expected = pair[1];
            }
            if (timestamp == null || expected == null || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) > 300) return false;
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String actual = java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }
    private User currentUser(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public record CheckoutRequest(SubscriptionTier tier) {}
    public record PaymentIntentResponse(String clientSecret) {}
}
