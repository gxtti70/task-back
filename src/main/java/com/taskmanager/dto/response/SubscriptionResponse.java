package com.taskmanager.dto.response;
import com.taskmanager.entity.SubscriptionTier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public record SubscriptionResponse(SubscriptionTier tier, String status, LocalDateTime currentPeriodEnd,
                                   boolean cancelAtPeriodEnd, String provider, Usage usage, List<String> features,
                                   Map<String, Boolean> capabilities) {
    public record Usage(long projects, int maxProjects, long membersPerProject, int maxMembersPerProject) {}
}
