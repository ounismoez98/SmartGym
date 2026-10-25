package com.smartgym.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {
    List<BodyMeasurement> findBySubjectOrderByMeasurementDateDesc(String subject);
    Optional<BodyMeasurement> findByIdAndSubject(Long id, String subject);
}
