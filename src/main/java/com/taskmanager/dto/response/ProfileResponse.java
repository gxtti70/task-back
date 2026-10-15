package com.taskmanager.dto.response;

import com.taskmanager.entity.User;
import com.taskmanager.entity.SubscriptionTier;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String fullName, String role, String avatarData,
                              String timezone, Integer weekStartsOn, String jobTitle, String bio,
                              String accentColor) {
    public static ProfileResponse from(User user, SubscriptionTier tier) {
        boolean premium = tier != SubscriptionTier.FREE;
        boolean premiumPlus = tier == SubscriptionTier.PREMIUM_PLUS;
        return new ProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                premium ? user.getAvatarData() : null, premium ? user.getProfileTimezone() : null,
                premium ? user.getWeekStartsOn() : null, premiumPlus ? user.getProfileJobTitle() : null,
                premiumPlus ? user.getProfileBio() : null, premiumPlus ? user.getProfileAccentColor() : null);
    }
}
