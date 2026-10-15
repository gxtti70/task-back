package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.entity.SubscriptionTier;
import com.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeatureGate {
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final PlanCatalog plans;

    @Transactional
    public void check(UUID userId, Capability capability) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        TaskManagerProperties.Plan plan = currentPlan(userId);
        if (!Boolean.TRUE.equals(plan.getCapabilities().get(capability.key()))) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, capability.upgradeMessage());
        }
    }

    @Transactional
    public TaskManagerProperties.Plan planFor(UUID userId) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return currentPlan(userId);
    }

    private TaskManagerProperties.Plan currentPlan(UUID userId) {
        SubscriptionTier tier = subscriptions.resolveTier(userId);
        return plans.config(tier);
    }
}
