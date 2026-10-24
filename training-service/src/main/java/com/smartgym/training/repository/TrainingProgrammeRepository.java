package com.smartgym.training.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.training.domain.ProgrammeStatus;
import com.smartgym.training.domain.TrainingProgramme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingProgrammeRepository extends JpaRepository<TrainingProgramme, UUID> {
    List<TrainingProgramme> findAllByMemberIdAndStatusOrderByUpdatedAtDesc(UUID memberId, ProgrammeStatus status);

    List<TrainingProgramme> findAllByMemberIdInAndStatusOrderByUpdatedAtDesc(
            List<UUID> memberIds, ProgrammeStatus status);

    List<TrainingProgramme> findAllByMemberIdInOrderByUpdatedAtDesc(List<UUID> memberIds);

    Optional<TrainingProgramme> findByIdAndMemberIdAndStatus(UUID id, UUID memberId, ProgrammeStatus status);
}
