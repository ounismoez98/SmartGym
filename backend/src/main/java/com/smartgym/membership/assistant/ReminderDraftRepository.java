package com.smartgym.membership.assistant;

import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderDraftRepository extends JpaRepository<ReminderDraft, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from ReminderDraft draft where draft.id = :id")
    java.util.Optional<ReminderDraft> findByIdForUpdate(@Param("id") UUID id);
}
