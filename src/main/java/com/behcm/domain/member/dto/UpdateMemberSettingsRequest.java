package com.behcm.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateMemberSettingsRequest {

    private NotificationSettings notifications;
    private PrivacySettings privacy;

    @Getter
    @NoArgsConstructor
    public static class NotificationSettings {
        private Boolean workoutReminder;
        private Boolean penaltyAlert;
        private Boolean roomUpdates;
        private Boolean weeklyReport;
    }

    @Getter
    @NoArgsConstructor
    public static class PrivacySettings {
        private Boolean showProfile;
        private Boolean showWorkouts;
        private Boolean showStats;
    }
}
