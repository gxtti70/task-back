package com.taskmanager.service;

import com.taskmanager.dto.response.PlanResponse;
import com.taskmanager.entity.*;
import com.taskmanager.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlanCatalog plans = mock(PlanCatalog.class);
    private final SubscriptionService service = new SubscriptionService(subscriptions, payments, projects, members, users, plans);

    @Test void blocksProjectCreationAtTheConfiguredPlanLimitWith402() {
        UUID userId = UUID.randomUUID();
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(subscriptions.findByUser_Id(userId)).thenReturn(Optional.of(UserSubscription.builder().tier(SubscriptionTier.FREE).status("ACTIVE").build()));
        when(plans.get(SubscriptionTier.FREE)).thenReturn(plan(SubscriptionTier.FREE, 3, 5));
        when(projects.countByOwner_Id(userId)).thenReturn(3L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.checkCanCreateProject(userId));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
    }

    @Test void blocksTheSixthMemberOnFreePlan() {
        UUID ownerId = UUID.randomUUID(); UUID projectId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).build();
        Project project = Project.builder().id(projectId).owner(owner).build();
        when(users.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(subscriptions.findByUser_Id(ownerId)).thenReturn(Optional.of(UserSubscription.builder().tier(SubscriptionTier.FREE).status("ACTIVE").build()));
        when(plans.get(SubscriptionTier.FREE)).thenReturn(plan(SubscriptionTier.FREE, 3, 5));
        when(members.countByProject_Id(projectId)).thenReturn(5L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.checkCanAddMember(project));
        assertEquals(HttpStatus.PAYMENT_REQUIRED, error.getStatusCode());
    }

    @Test void resolvesExpiredPaidSubscriptionAsFree() {
        UUID userId = UUID.randomUUID();
        var expired = UserSubscription.builder().tier(SubscriptionTier.PREMIUM).status("ACTIVE")
                .currentPeriodEnd(java.time.LocalDateTime.now().minusDays(1)).build();
        when(subscriptions.findByUser_Id(userId)).thenReturn(Optional.of(expired));
        assertEquals(SubscriptionTier.FREE, service.resolveTier(userId));
    }

    private PlanResponse plan(SubscriptionTier tier, int projects, int members) {
        return new PlanResponse(tier, "Free", projects, members, BigDecimal.ZERO, "USD", List.of(), Map.of(), 0, 0);
    }
}
