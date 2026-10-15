package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.entity.SubscriptionTier;
import com.taskmanager.entity.User;
import com.taskmanager.entity.UserSubscription;
import com.taskmanager.repository.PaymentRepository;
import com.taskmanager.repository.ProjectMemberRepository;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.UserSubscriptionRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.dto.response.PlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureGateTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserSubscriptionRepository subscriptionRepository = mock(UserSubscriptionRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final PlanCatalog plans = mock(PlanCatalog.class);
    private final SubscriptionService subscriptions = new SubscriptionService(
            subscriptionRepository, payments, projects, members, users, plans);
    private final FeatureGate gate = new FeatureGate(users, subscriptions, plans);

    @Test
    void freePlanGetsPaymentRequiredForAttachments() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("free@example.test").build();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        configurePlans();
        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> {
            UserSubscription saved = invocation.getArgument(0);
            when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(saved));
            return saved;
        });
        subscriptions.activateForDevelopment(userId, SubscriptionTier.FREE);
        TaskManagerProperties.Plan freePlan = new TaskManagerProperties.Plan();
        freePlan.setCapabilities(Map.of("attachments", false));
        when(plans.config(SubscriptionTier.FREE)).thenReturn(freePlan);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gate.check(userId, Capability.ATTACHMENTS));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
        verify(users).findByIdForUpdate(userId);
    }

    @Test
    void premiumPlanCanUseAttachments() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("premium@example.test").build();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        configurePlans();
        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(invocation -> {
            UserSubscription saved = invocation.getArgument(0);
            when(subscriptionRepository.findByUser_Id(userId)).thenReturn(Optional.of(saved));
            return saved;
        });
        subscriptions.activateForDevelopment(userId, SubscriptionTier.PREMIUM);
        TaskManagerProperties.Plan premiumPlan = new TaskManagerProperties.Plan();
        premiumPlan.setCapabilities(Map.of("attachments", true));
        when(plans.config(SubscriptionTier.PREMIUM)).thenReturn(premiumPlan);

        assertDoesNotThrow(() -> gate.check(userId, Capability.ATTACHMENTS));
    }

    private void configurePlans() {
        when(plans.simulate()).thenReturn(true);
        when(plans.price(any())).thenReturn(BigDecimal.ZERO);
        when(plans.get(any())).thenAnswer(invocation -> {
            SubscriptionTier tier = invocation.getArgument(0);
            return new PlanResponse(tier, tier.name(), 3, 5, BigDecimal.ZERO, "USD", java.util.List.of(), Map.of(), 0, 0);
        });
        when(projects.countByOwner_Id(any())).thenReturn(0L);
        when(projects.maxMembersByOwner(any())).thenReturn(0L);
    }
}
