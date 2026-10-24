package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FoodJournalRepository extends JpaRepository<FoodJournal, Long> {
    List<FoodJournal> findBySubjectOrderByEntryDateDesc(String subject);
    List<FoodJournal> findBySubjectAndEntryDateOrderByMealTypeAscIdAsc(String subject, LocalDate entryDate);
    Optional<FoodJournal> findByIdAndSubject(Long id, String subject);
    boolean existsByFoodId(Long foodId);
}
