package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NutritionProfileRepository extends JpaRepository<NutritionProfile, Long> {
    List<NutritionProfile> findBySubjectOrderByCreatedAtDesc(String subject);
    Optional<NutritionProfile> findFirstBySubjectOrderByCreatedAtDesc(String subject);
}
