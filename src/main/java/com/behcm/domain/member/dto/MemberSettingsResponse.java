package com.behcm.domain.member.dto;

import com.behcm.domain.member.entity.MemberSettings;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberSettingsResponse {

    private NotificationSettings notifications;
    private PrivacySettings privacy;

    @Getter
    @Builder
    public static class NotificationSettings {
        private Boolean workoutReminder;
        private Boolean penaltyAlert;
        private Boolean roomUpdates;
        private Boolean weeklyReport;
    }

    @Getter
    @Builder
    public static class PrivacySettings {
        private Boolean showProfile;
        private Boolean showWorkouts;
        private Boolean showStats;
    }

    public static MemberSettingsResponse from(MemberSettings settings) {
        return MemberSettingsResponse.builder()
                .notifications(NotificationSettings.builder()
                        .workoutReminder(settings.getWorkoutReminder())
                        .penaltyAlert(settings.getPenaltyAlert())
                        .roomUpdates(settings.getRoomUpdates())
                        .weeklyReport(settings.getWeeklyReport())
                        .build())
                .privacy(PrivacySettings.builder()
                        .showProfile(settings.getShowProfile())
                        .showWorkouts(settings.getShowWorkouts())
                        .showStats(settings.getShowStats())
                        .build())
                .build();
    }
}
