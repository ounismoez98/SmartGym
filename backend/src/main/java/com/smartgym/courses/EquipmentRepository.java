package com.smartgym.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    Optional<Equipment> findByNameIgnoreCase(String name);

    List<Equipment> findByNameIgnoreCaseIn(List<String> names);

    Page<Equipment> findByAvailableTrue(Pageable pageable);

    Page<Equipment> findByMaintenanceRequiredTrue(Pageable pageable);
}
