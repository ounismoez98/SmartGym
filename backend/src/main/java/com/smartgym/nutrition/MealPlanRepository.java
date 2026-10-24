package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findBySubjectOrderByCreatedAtDesc(String subject);
    Optional<MealPlan> findByIdAndSubject(Long id, String subject);

    @Query(value = "select count(*) from nutrition_meal_plan_items where food_id = :foodId", nativeQuery = true)
    long countItemsByFoodId(@Param("foodId") Long foodId);
}
