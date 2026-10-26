package com.smartgym.courses;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {
    private final EquipmentRepository equipmentRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;

    public EquipmentService(EquipmentRepository equipmentRepository,
                           MaintenanceTicketRepository maintenanceTicketRepository) {
        this.equipmentRepository = equipmentRepository;
        this.maintenanceTicketRepository = maintenanceTicketRepository;
    }

    @Transactional(readOnly = true)
    public Page<Equipment> listEquipment(Pageable pageable) {
        return equipmentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Equipment getEquipment(UUID equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Equipment not found."));
    }

    @Transactional
    public Equipment createEquipment(Equipment equipment) {
        validateEquipment(equipment);
        if (equipmentRepository.findByNameIgnoreCase(equipment.getName()).isPresent()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Equipment with the same name already exists.");
        }
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment updateEquipment(UUID equipmentId, EquipmentUpdateRequest request) {
        Equipment equipment = getEquipment(equipmentId);
        if (request.name() != null && !request.name().isBlank()) {
            equipment.setName(request.name().trim());
        }
        if (request.description() != null) {
            equipment.setDescription(request.description());
        }
        if (request.equipmentType() != null && !request.equipmentType().isBlank()) {
            equipment.setEquipmentType(request.equipmentType().trim());
        }
        if (request.available() != null) {
            equipment.setAvailable(request.available());
        }
        if (request.maintenanceRequired() != null) {
            equipment.setMaintenanceRequired(request.maintenanceRequired());
        }
        validateEquipment(equipment);
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public void deleteEquipment(UUID equipmentId) {
        Equipment equipment = getEquipment(equipmentId);
        List<MaintenanceTicket> tickets = maintenanceTicketRepository.findByEquipmentEquipmentId(equipmentId, Pageable.unpaged()).getContent();
        if (!tickets.isEmpty()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Equipment cannot be deleted while maintenance tickets remain.");
        }
        equipmentRepository.delete(equipment);
    }

    private void validateEquipment(Equipment equipment) {
        if (equipment == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Equipment is required.");
        }
        if (equipment.getName() == null || equipment.getName().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Equipment name is required.");
        }
        if (equipment.getEquipmentType() == null || equipment.getEquipmentType().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Equipment type is required.");
        }
    }

    public record EquipmentUpdateRequest(
            String name,
            String description,
            String equipmentType,
            Boolean available,
            Boolean maintenanceRequired) {
    }
}
