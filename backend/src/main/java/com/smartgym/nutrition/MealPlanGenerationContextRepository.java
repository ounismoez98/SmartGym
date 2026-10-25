package com.smartgym.nutrition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MealPlanGenerationContextRepository
        extends JpaRepository<MealPlanGenerationContext, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select context from MealPlanGenerationContext context
            where context.id = :id and context.subject = :subject and context.consumed = false
            """)
    Optional<MealPlanGenerationContext> findAvailableForUpdate(
            @Param("id") UUID id,
            @Param("subject") String subject);
}
