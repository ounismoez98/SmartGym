package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HydrationLogRepository extends JpaRepository<HydrationLog, Long> {
    List<HydrationLog> findBySubjectAndConsumedAtGreaterThanEqualAndConsumedAtLessThanOrderByConsumedAtAsc(
            String subject, Instant from, Instant to);

    List<HydrationLog> findBySubjectAndConsumedAtGreaterThanEqualAndConsumedAtLessThanOrderByConsumedAtDesc(
            String subject, Instant from, Instant to);

    Optional<HydrationLog> findByIdAndSubject(Long id, String subject);

}
