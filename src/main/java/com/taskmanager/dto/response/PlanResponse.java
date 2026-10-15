package com.taskmanager.dto.response;
import com.taskmanager.entity.SubscriptionTier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
public record PlanResponse(SubscriptionTier tier, String name, int maxProjects, int maxMembersPerProject,
                           BigDecimal price, String currency, List<String> features,
                           Map<String, Boolean> capabilities, int maxAttachmentMB, int maxAttachmentsPerTask) { }
