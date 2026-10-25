package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {
    List<Food> findBySubjectOrderByNameAsc(String subject);
    Optional<Food> findByIdAndSubject(Long id, String subject);
}
