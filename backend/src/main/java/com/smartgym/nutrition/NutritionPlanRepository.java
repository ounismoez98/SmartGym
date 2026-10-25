package com.smartgym.nutrition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Long> {
    List<NutritionPlan> findBySubjectOrderByStartDateDescCreatedAtDesc(String subject);

    Optional<NutritionPlan> findByIdAndSubject(Long id, String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from NutritionPlan plan where plan.id = :id and plan.subject = :subject")
    Optional<NutritionPlan> findOwnedForUpdate(@Param("id") Long id, @Param("subject") String subject);
}
