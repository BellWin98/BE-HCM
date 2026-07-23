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
@Table(
        name = "workout_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_workout_record_member_room_date",
                        columnNames = {"member_id", "workout_room_id", "workout_date"}
                )
        },
        indexes = {
                // 회원별 일자별 최신 기록 조회(findAllByMemberPerWorkoutDate 계열)의 상관 서브쿼리
                // max(created_at) where member = ? and workout_date = ? 를 index-only 로 만든다.
                // 위 UK 는 workout_room_id 가 중간에 있어 workout_date 를 탈 수 없다.
                @Index(
                        name = "idx_workout_record_member_date_created",
                        columnList = "member_id, workout_date, created_at"
                ),
                // 주간 벌금 정산 배치(countByWorkoutRoomAndWorkoutDateBetweenGroupByMember)용 커버링 인덱스.
                @Index(
                        name = "idx_workout_record_room_date_member",
                        columnList = "workout_room_id, workout_date, member_id"
                )
        }
)
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "workout_type", joinColumns = @JoinColumn(name = "workout_record_id"))
    @Column(name = "workout_type", nullable = false)
    private List<String> workoutTypes = new ArrayList<>();

    @Column(nullable = false)
    private LocalDate workoutDate;

    @Column(nullable = false)
    private Integer duration; // minutes

    @ElementCollection(fetch = FetchType.EAGER)
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
        this.workoutTypes = workoutTypes;
        this.duration = duration;
        this.imageUrls = imageUrls;
    }

    public boolean canDelete() {
        return workoutDate.equals(LocalDate.now());
    }
}
