package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.PlanResponse;
import com.taskmanager.entity.SubscriptionTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class PlanCatalog {
    private final TaskManagerProperties properties;
    public List<PlanResponse> all() { return Arrays.stream(SubscriptionTier.values()).map(this::get).toList(); }
    public PlanResponse get(SubscriptionTier tier) {
        String key = switch (tier) { case FREE -> "free"; case PREMIUM -> "premium"; case PREMIUM_PLUS -> "premiumPlus"; };
        TaskManagerProperties.Plan plan = properties.getPlans().get(key);
        if (plan == null) throw new IllegalStateException("Falta el plan " + key + " en taskmanager.plans");
        String name = tier == SubscriptionTier.PREMIUM_PLUS ? "Premium Plus" : tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
        return new PlanResponse(tier, name, plan.getMaxProjects(), plan.getMaxMembersPerProject(), plan.getPrice(),
                plan.getCurrency(), List.copyOf(plan.getFeatures()), Map.copyOf(plan.getCapabilities()),
                plan.getMaxAttachmentMB(), plan.getMaxAttachmentsPerTask());
    }
    public TaskManagerProperties.Plan config(SubscriptionTier tier) {
        String key = switch (tier) { case FREE -> "free"; case PREMIUM -> "premium"; case PREMIUM_PLUS -> "premiumPlus"; };
        return properties.getPlans().get(key);
    }
    public boolean simulate() { return properties.getBilling().isSimulate(); }
    public TaskManagerProperties.Billing billing() { return properties.getBilling(); }
    public BigDecimal price(SubscriptionTier tier) { return get(tier).price(); }
}
