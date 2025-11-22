package com.behcm.domain.member.entity;

import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    // Notification settings
    @Column(nullable = false)
    private Boolean workoutReminder = true;

    @Column(nullable = false)
    private Boolean penaltyAlert = true;

    @Column(nullable = false)
    private Boolean roomUpdates = true;

    @Column(nullable = false)
    private Boolean weeklyReport = true;

    // Privacy settings
    @Column(nullable = false)
    private Boolean showProfile = true;

    @Column(nullable = false)
    private Boolean showWorkouts = true;

    @Column(nullable = false)
    private Boolean showStats = true;

    @Builder
    public MemberSettings(Member member) {
        this.member = member;
    }

    public void updateNotificationSettings(Boolean workoutReminder, Boolean penaltyAlert,
                                          Boolean roomUpdates, Boolean weeklyReport) {
        if (workoutReminder != null) this.workoutReminder = workoutReminder;
        if (penaltyAlert != null) this.penaltyAlert = penaltyAlert;
        if (roomUpdates != null) this.roomUpdates = roomUpdates;
        if (weeklyReport != null) this.weeklyReport = weeklyReport;
    }

    public void updatePrivacySettings(Boolean showProfile, Boolean showWorkouts, Boolean showStats) {
        if (showProfile != null) this.showProfile = showProfile;
        if (showWorkouts != null) this.showWorkouts = showWorkouts;
        if (showStats != null) this.showStats = showStats;
    }
}
