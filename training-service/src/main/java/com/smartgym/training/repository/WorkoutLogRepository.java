package com.smartgym.training.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.training.domain.WorkoutLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {
    Page<WorkoutLog> findAllByMemberIdOrderByPerformedAtDesc(UUID memberId, Pageable pageable);

    Page<WorkoutLog> findAllByMemberIdInOrderByPerformedAtDesc(List<UUID> memberIds, Pageable pageable);

    Optional<WorkoutLog> findByIdAndMemberId(UUID id, UUID memberId);

    List<WorkoutLog> findAllByMemberIdOrderByPerformedAtDesc(UUID memberId);

    @EntityGraph(attributePaths = "sets")
    List<WorkoutLog> findAllByMemberIdAndPerformedAtGreaterThanEqualOrderByPerformedAtDesc(
            UUID memberId,
            java.time.OffsetDateTime performedAt);
}
