package com.behcm.domain.workout.entity;

import com.behcm.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_id", nullable = false)
    private WorkoutRoom workoutRoom;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "workout_type", joinColumns = @JoinColumn(name = "workout_record_id"))
    @Column(name = "workout_type", nullable = false)
    private List<String> workoutTypes = new ArrayList<>();

    @Column(nullable = false)
    private LocalDate workoutDate;

    @Column(nullable = false)
    private Integer duration; // minutes

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "workout_image", joinColumns = @JoinColumn(name = "workout_record_id"))
    @Column(name = "image_url", nullable = false, length = 512)
    private List<String> imageUrls = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkoutRecord(Member member, WorkoutRoom workoutRoom, LocalDate workoutDate,
                         List<String> workoutTypes, Integer duration, List<String> imageUrls) {
        this.member = member;
        this.workoutRoom = workoutRoom;
        this.workoutDate = workoutDate;
        this.workoutTypes = workoutTypes != null ? workoutTypes : new ArrayList<>();
        this.duration = duration;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
    }

    public boolean canDelete() {
        return workoutDate.equals(LocalDate.now());
    }
}
