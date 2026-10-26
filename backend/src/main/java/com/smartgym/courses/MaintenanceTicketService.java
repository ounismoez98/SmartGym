package com.smartgym.courses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceTicketService {
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final EquipmentRepository equipmentRepository;

    public MaintenanceTicketService(MaintenanceTicketRepository maintenanceTicketRepository,
                                   EquipmentRepository equipmentRepository) {
        this.maintenanceTicketRepository = maintenanceTicketRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceTicket> listTickets(Pageable pageable) {
        return maintenanceTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public MaintenanceTicket getTicket(UUID ticketId) {
        return maintenanceTicketRepository.findById(ticketId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Maintenance ticket not found."));
    }

    @Transactional
    public MaintenanceTicket createTicket(UUID equipmentId, CreateTicketRequest request) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Equipment not found."));
        if (request == null || request.issue() == null || request.issue().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Issue description is required.");
        }
        if (request.reportedBy() == null || request.reportedBy().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Reported-by subject is required.");
        }
        MaintenanceTicket ticket = MaintenanceTicket.create(equipment, request.issue(), request.reportedBy());
        equipment.setAvailable(false);
        equipment.setMaintenanceRequired(true);
        equipmentRepository.save(equipment);
        return maintenanceTicketRepository.save(ticket);
    }

    @Transactional
    public MaintenanceTicket updateTicket(UUID ticketId, UpdateTicketRequest request) {
        MaintenanceTicket ticket = getTicket(ticketId);
        if (request.issue() != null && !request.issue().isBlank()) {
            ticket.setIssue(request.issue().trim());
        }
        if (request.status() != null && !request.status().isBlank()) {
            ticket.setStatus(request.status().trim().toUpperCase());
        }
        if ("CLOSED".equalsIgnoreCase(ticket.getStatus())) {
            ticket.setClosedAt(Instant.now());
            ticket.getEquipment().setAvailable(true);
            ticket.getEquipment().setMaintenanceRequired(false);
        }
        return maintenanceTicketRepository.save(ticket);
    }

    @Transactional
    public MaintenanceTicket archiveTicket(UUID ticketId) {
        MaintenanceTicket ticket = getTicket(ticketId);
        ticket.setStatus("ARCHIVED");
        return maintenanceTicketRepository.save(ticket);
    }

    public record CreateTicketRequest(String issue, String reportedBy) {
    }

    public record UpdateTicketRequest(String issue, String status) {
    }
}
