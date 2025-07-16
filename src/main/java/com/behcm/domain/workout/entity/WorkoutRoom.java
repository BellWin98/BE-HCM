package com.behcm.domain.workout.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutRoom extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private Integer minWeeklyWorkouts;

    @Column(nullable = false)
    private Long penaltyPerMiss;

    @Column(nullable = false)
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(nullable = false)
    private Integer maxMembers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @OneToMany(mappedBy = "workoutRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkoutRoomMember> workoutRoomMembers = new ArrayList<>();

    @Column(nullable = false)
    private Boolean isActive = true;

    @Builder
    public WorkoutRoom(String name, Integer minWeeklyWorkouts, Long penaltyPerMiss,
                       LocalDate startDate, LocalDate endDate, Integer maxMembers, Member owner) {
        this.name = name;
        this.minWeeklyWorkouts = minWeeklyWorkouts;
        this.penaltyPerMiss = penaltyPerMiss;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxMembers = maxMembers;
        this.owner = owner;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void updateRoomSettings(String name, Integer minWeeklyWorkouts, Long penaltyPerMiss,
                                   LocalDate startDate, LocalDate endDate, Integer maxMembers) {
        this.name = name;
        this.minWeeklyWorkouts = minWeeklyWorkouts;
        this.penaltyPerMiss = penaltyPerMiss;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxMembers = maxMembers;
    }

    public boolean canJoin() {
        return isActive && workoutRoomMembers.size() < maxMembers &&
                (LocalDate.now().isBefore(endDate) || LocalDate.now().isEqual(startDate));
    }

    public boolean isOwner(Member member) {
        return owner.getId().equals(member.getId());
    }

    public int getCurrentMemberCount() {
        return workoutRoomMembers.size();
    }
}
