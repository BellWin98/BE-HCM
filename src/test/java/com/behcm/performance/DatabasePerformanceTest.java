package com.behcm.performance;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DatabasePerformanceTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkoutRoomRepository workoutRoomRepository;

    @Autowired
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Autowired
    private WorkoutRecordRepository workoutRecordRepository;

    @Autowired
    private PenaltyRepository penaltyRepository;

    private List<Member> testMembers;
    private List<WorkoutRoom> testWorkoutRooms;
    private List<WorkoutRoomMember> testWorkoutRoomMembers;

    @BeforeEach
    void setUp() {
        setupTestData();
    }

    private void setupTestData() {
        // 테스트용 멤버 생성 (1000명)
        testMembers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            Member member = Member.builder()
                    .email("test" + i + "@example.com")
                    .password("password")
                    .nickname("TestUser" + i)
                    .profileUrl("")
                    .role(MemberRole.USER)
                    .build();
            testMembers.add(member);
        }
        memberRepository.saveAll(testMembers);

        // 테스트용 운동방 생성 (100개)
        testWorkoutRooms = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            WorkoutRoom room = WorkoutRoom.builder()
                    .name("TestRoom" + i)
                    .minWeeklyWorkouts(3)
                    .penaltyPerMiss(10000L)
                    .startDate(LocalDate.now().minusDays(30))
                    .endDate(LocalDate.now().plusDays(30))
                    .maxMembers(20)
                    .entryCode("CODE" + String.format("%03d", i))
                    .owner(testMembers.get(i % testMembers.size()))
                    .build();
            testWorkoutRooms.add(room);
        }
        workoutRoomRepository.saveAll(testWorkoutRooms);

        // 테스트용 운동방 멤버 관계 생성
        testWorkoutRoomMembers = new ArrayList<>();
        for (int i = 0; i < testMembers.size(); i++) {
            WorkoutRoomMember wrm = WorkoutRoomMember.builder()
                    .member(testMembers.get(i))
                    .workoutRoom(testWorkoutRooms.get(i % testWorkoutRooms.size()))
                    .build();
            testWorkoutRoomMembers.add(wrm);
        }
        workoutRoomMemberRepository.saveAll(testWorkoutRoomMembers);

        // 테스트용 운동 기록 생성 (10,000개)
        List<WorkoutRecord> workoutRecords = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(90);
        for (int i = 0; i < 10000; i++) {
            WorkoutRecord record = WorkoutRecord.builder()
                    .member(testMembers.get(i % testMembers.size()))
                    .workoutRoom(testWorkoutRooms.get(i % testWorkoutRooms.size()))
                    .workoutDate(startDate.plusDays(i % 90))
                    .workoutType("Running")
                    .duration(30 + (i % 60))
                    .imageUrl("test-image-url")
                    .build();
            workoutRecords.add(record);
        }
        workoutRecordRepository.saveAll(workoutRecords);

        // 테스트용 벌금 데이터 생성 (5,000개)
        List<Penalty> penalties = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            Penalty penalty = Penalty.builder()
                    .workoutRoomMember(testWorkoutRoomMembers.get(i % testWorkoutRoomMembers.size()))
                    .penaltyAmount(10000L)
                    .requiredWorkouts(3)
                    .actualWorkouts(i % 3)
                    .weekStartDate(startDate.plusWeeks(i % 12))
                    .weekEndDate(startDate.plusWeeks(i % 12).plusDays(6))
                    .build();
            penalties.add(penalty);
        }
        penaltyRepository.saveAll(penalties);
    }

    @Test
    void testWorkoutRecordQueryPerformance() {
        System.out.println("=== WorkoutRecord Query Performance Test ===");

        // 1. 멤버별 운동 기록 조회 (전체)
        measureQueryPerformance("findAllByMember", () -> {
            Member testMember = testMembers.get(0);
            return workoutRecordRepository.findAllByMember(testMember);
        });

        // 2. 특정 날짜 운동 기록 조회
        measureQueryPerformance("findByMemberAndWorkoutRoomAndWorkoutDate", () -> {
            Member testMember = testMembers.get(0);
            WorkoutRoom testRoom = testWorkoutRooms.get(0);
            return workoutRecordRepository.findByMemberAndWorkoutRoomAndWorkoutDate(
                testMember, testRoom, LocalDate.now().minusDays(1));
        });

        // 3. 중복 운동 기록 확인
        measureQueryPerformance("existsByMemberAndWorkoutRoomAndWorkoutDate", () -> {
            Member testMember = testMembers.get(0);
            WorkoutRoom testRoom = testWorkoutRooms.get(0);
            return workoutRecordRepository.existsByMemberAndWorkoutRoomAndWorkoutDate(
                testMember, testRoom, LocalDate.now().minusDays(1));
        });

        // 4. 주간 운동 횟수 계산 (가장 중요한 쿼리)
        measureQueryPerformance("countByMemberAndWorkoutRoomAndWorkoutDateBetween", () -> {
            Member testMember = testMembers.get(0);
            WorkoutRoom testRoom = testWorkoutRooms.get(0);
            LocalDate weekStart = LocalDate.now().minusDays(7);
            LocalDate weekEnd = LocalDate.now();
            return workoutRecordRepository.countByMemberAndWorkoutRoomAndWorkoutDateBetween(
                testMember, testRoom, weekStart, weekEnd);
        });
    }

    @Test
    void testPenaltyQueryPerformance() {
        System.out.println("\n=== Penalty Query Performance Test ===");

        // 1. 방별 미납 벌금 조회
        measureQueryPerformance("findUnpaidByRoomId", () -> {
            return penaltyRepository.findUnpaidByRoomId(testWorkoutRooms.get(0).getId());
        });

        // 2. 전체 미납 벌금 조회
        measureQueryPerformance("findAllUnpaid", () -> {
            return penaltyRepository.findAllUnpaid();
        });

        // 3. 방별 전체 벌금 조회
        measureQueryPerformance("findAllByWorkoutRoomId", () -> {
            return penaltyRepository.findAllByWorkoutRoomId(testWorkoutRooms.get(0).getId());
        });
    }

    @Test
    void testWorkoutRoomQueryPerformance() {
        System.out.println("\n=== WorkoutRoom Query Performance Test ===");

        // 1. 활성 운동방 조회
        measureQueryPerformance("findByIsActiveTrue", () -> {
            return workoutRoomRepository.findByIsActiveTrue();
        });

        // 2. 멤버별 활성 운동방 조회
        measureQueryPerformance("findActiveWorkoutRoomByMember", () -> {
            return workoutRoomRepository.findActiveWorkoutRoomByMember(testMembers.get(0));
        });

        // 3. 활성 방 조회 (종료일 고려)
        measureQueryPerformance("findActiveRooms", () -> {
            return workoutRoomRepository.findActiveRooms();
        });

        // 4. 입장 코드로 방 찾기
        measureQueryPerformance("findByEntryCode", () -> {
            return workoutRoomRepository.findByEntryCode("CODE001");
        });
    }

    @Test
    void testWorkoutRoomMemberQueryPerformance() {
        System.out.println("\n=== WorkoutRoomMember Query Performance Test ===");

        // 1. 멤버별 참여 방 조회
        measureQueryPerformance("findWorkoutRoomMembersByMember", () -> {
            return workoutRoomMemberRepository.findWorkoutRoomMembersByMember(testMembers.get(0));
        });

        // 2. 방별 멤버 조회 (가입일 순)
        measureQueryPerformance("findByWorkoutRoomOrderByJoinedAt", () -> {
            return workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(testWorkoutRooms.get(0));
        });

        // 3. 멤버-방 관계 존재 확인
        measureQueryPerformance("existsByMemberAndWorkoutRoom", () -> {
            return workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(
                testMembers.get(0), testWorkoutRooms.get(0));
        });
    }

    private <T> T measureQueryPerformance(String queryName, QueryExecutor<T> executor) {
        long startTime = System.nanoTime();
        T result = executor.execute();
        long endTime = System.nanoTime();

        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        System.out.printf("%-50s: %.2f ms%n", queryName, executionTimeMs);

        return result;
    }

    @FunctionalInterface
    private interface QueryExecutor<T> {
        T execute();
    }
}