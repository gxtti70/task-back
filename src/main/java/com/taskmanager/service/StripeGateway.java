package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.entity.SubscriptionTier;
import com.taskmanager.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service @RequiredArgsConstructor
public class StripeGateway {
    private final PlanCatalog catalog;
    private final HttpClient client = HttpClient.newHttpClient();

    public PaymentIntentSession createSubscriptionPaymentIntent(User user, SubscriptionTier tier) {
        TaskManagerProperties.Billing billing = catalog.billing();
        String price = catalog.config(tier).getStripePriceId();
        if (tier == SubscriptionTier.FREE || billing.getStripeSecretKey() == null || billing.getStripeSecretKey().isBlank() || price == null || price.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe no está configurado para este plan");
        try {
            var customerFields = new LinkedHashMap<String, String>();
            customerFields.put("email", user.getEmail());
            customerFields.put("name", user.getFullName());
            customerFields.put("metadata[userId]", user.getId().toString());
            var customer = post("https://api.stripe.com/v1/customers", customerFields, billing.getStripeSecretKey());
            String customerId = customer.path("id").asText("");
            if (customerId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe no pudo crear el cliente");

            var fields = new LinkedHashMap<String, String>();
            fields.put("customer", customerId);
            fields.put("items[0][price]", price);
            fields.put("payment_behavior", "default_incomplete");
            fields.put("payment_settings[save_default_payment_method]", "on_subscription");
            fields.put("payment_settings[payment_method_types][0]", "card");
            fields.put("expand[0]", "latest_invoice.payment_intent");
            fields.put("metadata[userId]", user.getId().toString());
            fields.put("metadata[tier]", tier.name());
            var subscription = post("https://api.stripe.com/v1/subscriptions", fields, billing.getStripeSecretKey());
            String subscriptionId = subscription.path("id").asText("");
            String clientSecret = subscription.path("latest_invoice").path("payment_intent").path("client_secret").asText("");
            if (subscriptionId.isBlank() || clientSecret.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe no devolvió los datos de pago de la suscripción");
            return new PaymentIntentSession(subscriptionId, clientSecret);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Stripe"); }
    }

    private com.fasterxml.jackson.databind.JsonNode post(String url, Map<String, String> fields, String secret) throws Exception {
        String form = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((a,b) -> a + "&" + b).orElse("");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + secret).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe rechazó la creación de la suscripción");
        return json;
    }

    public record PaymentIntentSession(String subscriptionId, String clientSecret) {}

    public String checkoutUrl(User user, SubscriptionTier tier) {
        TaskManagerProperties.Billing billing = catalog.billing();
        String price = catalog.config(tier).getStripePriceId();
        if (billing.getStripeSecretKey() == null || billing.getStripeSecretKey().isBlank() || price == null || price.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe no está configurado para este plan");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mode", "subscription"); fields.put("success_url", billing.getFrontendUrl() + "/dashboard/subscription?checkout=success");
        fields.put("cancel_url", billing.getFrontendUrl() + "/dashboard/subscription?checkout=cancelled");
        fields.put("customer_email", user.getEmail()); fields.put("client_reference_id", user.getId().toString());
        fields.put("line_items[0][price]", price); fields.put("line_items[0][quantity]", "1");
        fields.put("metadata[userId]", user.getId().toString()); fields.put("metadata[tier]", tier.name()); fields.put("subscription_data[metadata][userId]", user.getId().toString()); fields.put("subscription_data[metadata][tier]", tier.name());
        try {
            String form = fields.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((a,b) -> a + "&" + b).orElse("");
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Bearer " + billing.getStripeSecretKey()).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            if (json.hasNonNull("url")) return json.get("url").asText();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe no pudo crear la sesión de pago");
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Stripe"); }
    }

    public void cancelAtPeriodEnd(String subscriptionId) {
        String key = catalog.billing().getStripeSecretKey();
        try {
            String form = "cancel_at_period_end=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/subscriptions/" + encodePath(subscriptionId)))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/x-www-form-urlencoded")
                    .method("POST", HttpRequest.BodyPublishers.ofString(form)).build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe no pudo programar la cancelación"); }
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String encodePath(String value) { return value.replaceAll("[^A-Za-z0-9_\\-]", ""); }
}
