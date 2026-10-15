package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.dto.response.PlanResponse;
import com.taskmanager.dto.response.SubscriptionResponse;
import com.taskmanager.entity.*;
import com.taskmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SubscriptionService {
    private final UserSubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final PlanCatalog plans;

    @Transactional
    public UserSubscription getOrCreate(UUID userId) {
        return subscriptions.findByUser_Id(userId).orElseGet(() -> {
            User user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            return subscriptions.save(UserSubscription.builder().user(user).tier(SubscriptionTier.FREE).status("ACTIVE").provider("DEV").build());
        });
    }

    @Transactional(readOnly = true)
    public SubscriptionTier resolveTier(UUID userId) {
        return subscriptions.findByUser_Id(userId).filter(s -> isEntitled(s, LocalDateTime.now()))
                .map(UserSubscription::getTier).orElse(SubscriptionTier.FREE);
    }

    public SubscriptionResponse getMine(UUID userId) {
        UserSubscription sub = getOrCreate(userId);
        SubscriptionTier tier = isEntitled(sub, LocalDateTime.now()) ? sub.getTier() : SubscriptionTier.FREE;
        PlanResponse plan = plans.get(tier);
        long projectCount = projects.countByOwner_Id(userId);
        long maxMembers = projects.maxMembersByOwner(userId);
        return new SubscriptionResponse(tier, sub.getStatus(), sub.getCurrentPeriodEnd(), sub.isCancelAtPeriodEnd(), sub.getProvider(),
                new SubscriptionResponse.Usage(projectCount, plan.maxProjects(), maxMembers, plan.maxMembersPerProject()),
                plan.features(), plan.capabilities());
    }

    @Transactional
    public UserSubscription activate(UUID userId, SubscriptionTier tier, String provider, String providerSubscriptionId) {
        if (tier == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan no válido");
        UserSubscription sub = getOrCreate(userId);
        LocalDateTime now = LocalDateTime.now();
        sub.setTier(tier); sub.setStatus("ACTIVE"); sub.setProvider(provider); sub.setProviderSubscriptionId(providerSubscriptionId);
        sub.setCurrentPeriodStart(now); sub.setCurrentPeriodEnd(tier == SubscriptionTier.FREE ? null : now.plusMonths(1));
        sub.setCancelAtPeriodEnd(false);
        return subscriptions.save(sub);
    }

    @Transactional
    public SubscriptionResponse activateForDevelopment(UUID userId, SubscriptionTier tier) {
        if (!plans.simulate()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La activación de desarrollo no está disponible");
        UserSubscription sub = activate(userId, tier, "DEV", null);
        payments.save(Payment.builder().user(sub.getUser()).tier(tier).amount(plans.price(tier)).currency(plans.get(tier).currency())
                .provider("DEV").status("SUCCEEDED").build());
        return getMine(userId);
    }

    @Transactional
    public void checkCanCreateProject(UUID userId) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        SubscriptionTier tier = resolveTier(userId);
        int max = plans.get(tier).maxProjects();
        if (max >= 0 && projects.countByOwner_Id(userId) >= max)
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Alcanzaste el límite de " + max + " proyectos de tu plan. Mejora tu plan para crear más.");
    }

    @Transactional
    public void checkCanAddMember(Project project) {
        UUID ownerId = project.getOwner().getId();
        users.findByIdForUpdate(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        int max = plans.get(resolveTier(ownerId)).maxMembersPerProject();
        long current = members.countByProject_Id(project.getId());
        if (max >= 0 && current >= max)
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Este plan permite hasta " + max + " miembros por proyecto. Mejora el plan para añadir más.");
    }

    public boolean isSimulation() { return plans.simulate(); }
    private boolean isEntitled(UserSubscription sub, LocalDateTime now) {
        return sub.getTier() != SubscriptionTier.FREE && (sub.getStatus().equals("ACTIVE") || sub.isCancelAtPeriodEnd())
                && (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isAfter(now));
    }
}
