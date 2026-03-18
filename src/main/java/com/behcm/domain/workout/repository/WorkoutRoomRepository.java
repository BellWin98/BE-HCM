package com.behcm.domain.workout.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;

@Repository
public interface WorkoutRoomRepository extends JpaRepository<WorkoutRoom, Long> {

    @Query("SELECT wr FROM WorkoutRoom wr WHERE wr.isActive = true")
    List<WorkoutRoom> findActiveRooms();

    List<WorkoutRoom> findByIsActiveTrue();

    boolean existsByEntryCode(String entryCode);

    Optional<WorkoutRoom> findByEntryCodeAndIsActiveTrue(String entryCode);

    Optional<WorkoutRoom> findByIdAndIsActiveTrue(Long roomId);

    @Query("""
            SELECT wr
            FROM WorkoutRoom wr
            JOIN wr.owner o
            WHERE (:active IS NULL OR wr.isActive = :active)
            AND (
                :query IS NULL
                OR LOWER(wr.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(o.nickname) LIKE LOWER(CONCAT('%', :query, '%'))
            )
            """)
    Page<WorkoutRoom> searchAdminRooms(@Param("query") String query,
            @Param("active") Boolean active,
            Pageable pageable);

    List<WorkoutRoom> findByOwner(Member owner);

    @Query("""
            SELECT COUNT(wr)
            FROM WorkoutRoom wr
            WHERE wr.isActive = true
            """)
    long countActiveRooms();
}
