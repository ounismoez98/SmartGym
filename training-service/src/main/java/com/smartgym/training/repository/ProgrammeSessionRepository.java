package com.smartgym.training.repository;

import java.util.Optional;
import java.util.UUID;

import com.smartgym.training.domain.ProgrammeSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgrammeSessionRepository extends JpaRepository<ProgrammeSession, UUID> {
    Optional<ProgrammeSession> findByIdAndProgrammeId(UUID id, UUID programmeId);
}
