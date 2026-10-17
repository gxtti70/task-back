package com.taskmanager.security;

import com.taskmanager.config.AuthCookieService;
import com.taskmanager.config.SecurityConfig;
import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.controller.SubscriptionController;
import com.taskmanager.repository.PaymentRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.repository.UserSubscriptionRepository;
import com.taskmanager.service.PlanCatalog;
import com.taskmanager.service.StripeGateway;
import com.taskmanager.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = SubscriptionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthCookieService.class})
class SubscriptionWebhookSecurityTest {
    private static final String SECRET = "webhook-test-secret";
    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository users;
    @MockBean UserSubscriptionRepository subscriptions;
    @MockBean PaymentRepository payments;
    @MockBean PlanCatalog plans;
    @MockBean SubscriptionService service;
    @MockBean StripeGateway stripe;
    @MockBean TaskManagerProperties properties;

    @BeforeEach
    void configureSecret() {
        TaskManagerProperties.Billing billing = new TaskManagerProperties.Billing();
        billing.setStripeWebhookSecret(SECRET);
        when(properties.getBilling()).thenReturn(billing);
    }

    @Test
    void validSignedWebhookPassesWithoutBrowserCsrfToken() throws Exception {
        String payload = "{\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}";

        mvc.perform(post("/api/subscriptions/webhook").contentType("application/json")
                        .header("Stripe-Signature", signature(payload)).content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void missingOrInvalidStripeSignatureIsRejectedByControllerRatherThanCsrf() throws Exception {
        String payload = "{\"type\":\"invoice.paid\"}";

        mvc.perform(post("/api/subscriptions/webhook").contentType("application/json").content(payload))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/subscriptions/webhook").contentType("application/json")
                        .header("Stripe-Signature", "t=1,v1=invalid").content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCsrfGetsAnExplicitErrorCode() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Error-Code", "CSRF_INVALID"));
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + digest;
    }
}
