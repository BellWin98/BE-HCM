package com.behcm.performance;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource(properties = "jasypt.encryptor.password=slxmthvmxm24@$")
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IndexPerformanceComparisonTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkoutRoomRepository workoutRoomRepository;

    @Autowired
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Autowired
    private WorkoutRecordRepository workoutRecordRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int MEMBER_COUNT = 100;
    private static final int WORKOUT_RECORD_COUNT = 50000;
    private static final int PERFORMANCE_TEST_ITERATIONS = 100;

    private List<Member> testMembers;
    private List<WorkoutRoom> testWorkoutRooms;
    private List<WorkoutRoomMember> testWorkoutRoomMembers;

    @BeforeEach
    void setUp() {
        setupLargeDataset();
    }

    @Test
    @Order(1)
    @DisplayName("인덱스 추가 전 성능 측정")
    @Transactional
    void testPerformanceBeforeIndexes() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("인덱스 추가 전 성능 측정");
        System.out.println("=".repeat(80));

        // 인덱스 제거 (테스트용)
//        dropTestIndexes();

        runPerformanceTests("BEFORE_INDEXES");
    }

    @Test
    @Order(2)
    @DisplayName("인덱스 추가 후 성능 측정")
    @Transactional
    void testPerformanceAfterIndexes() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("인덱스 추가 후 성능 측정");
        System.out.println("=".repeat(80));

        // 인덱스 생성
//        createTestIndexes();

        runPerformanceTests("AFTER_INDEXES");
    }

    private void setupLargeDataset() {
        // 대용량 테스트 데이터 생성
        System.out.println("대용량 테스트 데이터 생성 중...");

        // 1. 멤버 생성
        testMembers = new ArrayList<>();
        for (int i = 1; i <= MEMBER_COUNT; i++) {
            Member member = Member.builder()
                    .email("perf_test_" + i + "@example.com")
                    .password("password")
                    .nickname("test" + i)
                    .profileUrl("")
                    .role(MemberRole.USER)
                    .build();
            testMembers.add(member);
        }
        memberRepository.saveAllAndFlush(testMembers);

        // 2. 운동방 생성
        testWorkoutRooms = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            WorkoutRoom room = WorkoutRoom.builder()
                    .name("PerfRoom" + i)
                    .minWeeklyWorkouts(3)
                    .penaltyPerMiss(10000L)
                    .startDate(LocalDate.now().minusDays(90))
                    .endDate(LocalDate.now().plusDays(90))
                    .maxMembers(50)
                    .entryCode("PERF" + String.format("%03d", i))
                    .owner(testMembers.get(i % testMembers.size()))
                    .build();
            testWorkoutRooms.add(room);
        }
        workoutRoomRepository.saveAllAndFlush(testWorkoutRooms);

        // 3. 운동방 멤버 관계 생성
        testWorkoutRoomMembers = new ArrayList<>();
        for (int i = 0; i < testMembers.size(); i++) {
            WorkoutRoomMember wrm = WorkoutRoomMember.builder()
                    .member(testMembers.get(i))
                    .workoutRoom(testWorkoutRooms.get(i % testWorkoutRooms.size()))
                    .build();
            testWorkoutRoomMembers.add(wrm);
        }
        workoutRoomMemberRepository.saveAllAndFlush(testWorkoutRoomMembers);

        // 4. 대용량 운동 기록 생성
        List<WorkoutRecord> workoutRecords = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(90);

        for (int i = 0; i < WORKOUT_RECORD_COUNT; i++) {
            WorkoutRecord record = WorkoutRecord.builder()
                    .member(testMembers.get(i % testMembers.size()))
                    .workoutRoom(testWorkoutRooms.get(i % testWorkoutRooms.size()))
                    .workoutDate(startDate.plusDays(i % 90))
                    .workoutType("Performance Test")
                    .duration(30 + (i % 60))
                    .imageUrl("perf-test-image")
                    .build();
            workoutRecords.add(record);

            // 배치 처리로 메모리 사용량 최적화
            if (i % 1000 == 0) {
                workoutRecordRepository.saveAllAndFlush(workoutRecords);
                workoutRecords.clear();
                entityManager.clear(); // 영속성 컨텍스트 클리어
            }
        }

        if (!workoutRecords.isEmpty()) {
            workoutRecordRepository.saveAllAndFlush(workoutRecords);
        }

        entityManager.clear();
        System.out.println("테스트 데이터 생성 완료: " +
                "Members=" + MEMBER_COUNT +
                ", WorkoutRecords=" + WORKOUT_RECORD_COUNT);
    }

    private void runPerformanceTests(String phase) {
        PerformanceResult result = new PerformanceResult(phase);

        // 1. 가장 중요한 쿼리: 주간 운동 횟수 계산
        result.weeklyWorkoutCountTime = measureAverageTime(() -> {
            Member testMember = testMembers.get(0);
            WorkoutRoom testRoom = testWorkoutRooms.get(0);
            LocalDate weekStart = LocalDate.now().minusDays(7);
            LocalDate weekEnd = LocalDate.now();
            return workoutRecordRepository.countByMemberAndWorkoutRoomAndWorkoutDateBetween(
                    testMember, testRoom, weekStart, weekEnd);
        });

        // 2. 멤버별 운동 기록 조회
        result.memberWorkoutRecordsTime = measureAverageTime(() -> {
            Member testMember = testMembers.get(0);
            return workoutRecordRepository.findAllByMember(testMember);
        });

        // 3. 특정 날짜 운동 기록 존재 확인
        result.workoutExistsCheckTime = measureAverageTime(() -> {
            Member testMember = testMembers.get(0);
            WorkoutRoom testRoom = testWorkoutRooms.get(0);
            return workoutRecordRepository.existsByMemberAndWorkoutRoomAndWorkoutDate(
                    testMember, testRoom, LocalDate.now().minusDays(1));
        });

        // 4. 활성 운동방 조회
        result.activeRoomsTime = measureAverageTime(() -> {
            return workoutRoomRepository.findByIsActiveTrue();
        });

        // 5. 멤버별 참여 방 조회
        result.memberRoomsTime = measureAverageTime(() -> {
            return workoutRoomMemberRepository.findWorkoutRoomMembersByMember(testMembers.get(0));
        });

        result.printResults();
    }

    private double measureAverageTime(QueryExecutor executor) {
        // Warm-up
        for (int i = 0; i < 3; i++) {
            executor.execute();
        }

        // 실제 측정
        long totalTime = 0;
        for (int i = 0; i < PERFORMANCE_TEST_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            executor.execute();
            long endTime = System.nanoTime();
            totalTime += (endTime - startTime);
        }

        return (totalTime / (double) PERFORMANCE_TEST_ITERATIONS) / 1_000_000.0; // ms로 변환
    }

    private void dropTestIndexes() {
        try {
            entityManager.createNativeQuery("DROP INDEX IF EXISTS idx_workout_record_member_room_date ON workout_record").executeUpdate();
            entityManager.createNativeQuery("DROP INDEX IF EXISTS idx_workout_record_member_created ON workout_record").executeUpdate();
            entityManager.createNativeQuery("DROP INDEX IF EXISTS idx_workout_record_date_range ON workout_record").executeUpdate();
            entityManager.createNativeQuery("DROP INDEX IF EXISTS idx_workout_room_active ON workout_room").executeUpdate();
            entityManager.createNativeQuery("DROP INDEX IF EXISTS idx_workout_room_member_member ON workout_room_member").executeUpdate();
            entityManager.flush();
            System.out.println("테스트 인덱스 제거 완료");
        } catch (Exception e) {
            System.out.println("인덱스 제거 중 오류 (무시 가능): " + e.getMessage());
        }
    }

    private void createTestIndexes() {
        try {
            entityManager.createNativeQuery(
                    "CREATE INDEX idx_workout_record_member_room_date ON workout_record (member_id, workout_room_id, workout_date)"
            ).executeUpdate();

            entityManager.createNativeQuery(
                    "CREATE INDEX idx_workout_record_member_created ON workout_record (member_id, created_at)"
            ).executeUpdate();

            entityManager.createNativeQuery(
                    "CREATE INDEX idx_workout_record_date_range ON workout_record (workout_date)"
            ).executeUpdate();

            entityManager.createNativeQuery(
                    "CREATE INDEX idx_workout_room_active ON workout_room (is_active)"
            ).executeUpdate();

            entityManager.createNativeQuery(
                    "CREATE INDEX idx_workout_room_member_member ON workout_room_member (member_id)"
            ).executeUpdate();

            entityManager.flush();
            System.out.println("테스트 인덱스 생성 완료");
        } catch (Exception e) {
            System.out.println("인덱스 생성 중 오류: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface QueryExecutor {
        Object execute();
    }

    private static class PerformanceResult {
        private final String phase;
        double weeklyWorkoutCountTime;
        double memberWorkoutRecordsTime;
        double workoutExistsCheckTime;
        double activeRoomsTime;
        double memberRoomsTime;

        public PerformanceResult(String phase) {
            this.phase = phase;
        }

        public void printResults() {
            System.out.println("\n" + phase + " 성능 측정 결과:");
            System.out.println("-".repeat(60));
            System.out.printf("주간 운동 횟수 계산: %.2f ms%n", weeklyWorkoutCountTime);
            System.out.printf("멤버별 운동 기록 조회: %.2f ms%n", memberWorkoutRecordsTime);
            System.out.printf("운동 기록 존재 확인: %.2f ms%n", workoutExistsCheckTime);
            System.out.printf("활성 운동방 조회: %.2f ms%n", activeRoomsTime);
            System.out.printf("멤버별 참여 방 조회: %.2f ms%n", memberRoomsTime);
            System.out.println("-".repeat(60));
        }
    }
}