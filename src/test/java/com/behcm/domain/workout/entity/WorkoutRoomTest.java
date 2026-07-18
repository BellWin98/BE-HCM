package com.behcm.domain.workout.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutRoomTest {

    private WorkoutRoom newRoom(boolean penaltyEnabled, Long penaltyPerMiss) {
        Member owner = Member.builder()
                .email("owner@test.com")
                .nickname("owner")
                .role(MemberRole.USER)
                .build();

        return WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(penaltyEnabled)
                .penaltyPerMiss(penaltyPerMiss)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
    }

    @Test
    @DisplayName("schedulePenaltyChange는 pending 필드들을 설정만 하고 penaltyEnabled는 즉시 바꾸지 않는다")
    void schedulePenaltyChange_onlySetsPendingFields() {
        WorkoutRoom room = newRoom(true, 5000L);
        LocalDate effectiveDate = LocalDate.of(2026, 7, 27);

        room.schedulePenaltyChange(false, null, effectiveDate);

        assertThat(room.getPenaltyEnabled()).isTrue();
        assertThat(room.getPenaltyPerMiss()).isEqualTo(5000L);
        assertThat(room.getPendingPenaltyEnabled()).isFalse();
        assertThat(room.getPendingPenaltyPerMiss()).isNull();
        assertThat(room.getPenaltyChangeEffectiveDate()).isEqualTo(effectiveDate);
    }

    @Test
    @DisplayName("schedulePenaltyChange를 다시 호출하면 기존 예약을 새 예약으로 덮어쓴다")
    void schedulePenaltyChange_overwritesExistingSchedule() {
        WorkoutRoom room = newRoom(true, 5000L);
        room.schedulePenaltyChange(false, null, LocalDate.of(2026, 7, 27));

        room.schedulePenaltyChange(true, 8000L, LocalDate.of(2026, 8, 3));

        assertThat(room.getPendingPenaltyEnabled()).isTrue();
        assertThat(room.getPendingPenaltyPerMiss()).isEqualTo(8000L);
        assertThat(room.getPenaltyChangeEffectiveDate()).isEqualTo(LocalDate.of(2026, 8, 3));
    }

    @Test
    @DisplayName("applyPendingPenaltyChangeIfDue는 effectiveDate가 도래하면 벌금 끄기를 적용하고 pending을 초기화한다")
    void applyPendingPenaltyChangeIfDue_appliesDisableWhenDue() {
        WorkoutRoom room = newRoom(true, 5000L);
        LocalDate effectiveDate = LocalDate.of(2026, 7, 27);
        room.schedulePenaltyChange(false, null, effectiveDate);

        room.applyPendingPenaltyChangeIfDue(effectiveDate);

        assertThat(room.getPenaltyEnabled()).isFalse();
        assertThat(room.getPenaltyPerMiss()).isEqualTo(5000L); // 기존 금액은 유지 (소급 삭제하지 않음)
        assertThat(room.getPendingPenaltyEnabled()).isNull();
        assertThat(room.getPendingPenaltyPerMiss()).isNull();
        assertThat(room.getPenaltyChangeEffectiveDate()).isNull();
    }

    @Test
    @DisplayName("applyPendingPenaltyChangeIfDue는 effectiveDate가 도래하면 벌금 켜기를 적용하고 새 금액을 반영한다")
    void applyPendingPenaltyChangeIfDue_appliesEnableWhenDue() {
        WorkoutRoom room = newRoom(false, null);
        LocalDate effectiveDate = LocalDate.of(2026, 7, 27);
        room.schedulePenaltyChange(true, 8000L, effectiveDate);

        room.applyPendingPenaltyChangeIfDue(effectiveDate);

        assertThat(room.getPenaltyEnabled()).isTrue();
        assertThat(room.getPenaltyPerMiss()).isEqualTo(8000L);
        assertThat(room.getPendingPenaltyEnabled()).isNull();
    }

    @Test
    @DisplayName("applyPendingPenaltyChangeIfDue는 effectiveDate가 아직 도래하지 않았으면 아무것도 바꾸지 않는다")
    void applyPendingPenaltyChangeIfDue_doesNothingWhenNotYetDue() {
        WorkoutRoom room = newRoom(true, 5000L);
        LocalDate effectiveDate = LocalDate.of(2026, 7, 27);
        room.schedulePenaltyChange(false, null, effectiveDate);

        room.applyPendingPenaltyChangeIfDue(effectiveDate.minusDays(1));

        assertThat(room.getPenaltyEnabled()).isTrue();
        assertThat(room.getPendingPenaltyEnabled()).isFalse();
        assertThat(room.getPenaltyChangeEffectiveDate()).isEqualTo(effectiveDate);
    }

    @Test
    @DisplayName("applyPendingPenaltyChangeIfDue는 예약이 없으면 예외 없이 아무 동작도 하지 않는다")
    void applyPendingPenaltyChangeIfDue_noopWhenNoPendingChange() {
        WorkoutRoom room = newRoom(true, 5000L);

        room.applyPendingPenaltyChangeIfDue(LocalDate.now());

        assertThat(room.getPenaltyEnabled()).isTrue();
        assertThat(room.getPendingPenaltyEnabled()).isNull();
    }
}
