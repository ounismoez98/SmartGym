package com.smartgym.courses;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {
    Page<MaintenanceTicket> findByEquipmentEquipmentId(UUID equipmentId, Pageable pageable);

    List<MaintenanceTicket> findByStatusOrderByCreatedAtDesc(String status);

    Page<MaintenanceTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
