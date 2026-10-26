package com.smartgym.courses;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/maintenance-tickets")
@PreAuthorize("hasRole('ADMIN')")
public class MaintenanceTicketController {
    private final MaintenanceTicketService maintenanceTicketService;

    public MaintenanceTicketController(MaintenanceTicketService maintenanceTicketService) {
        this.maintenanceTicketService = maintenanceTicketService;
    }

    @GetMapping
    public Page<MaintenanceTicket> listTickets(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "100") int size) {
        return maintenanceTicketService.listTickets(PageRequest.of(page, size));
    }

    @PostMapping("/equipment/{equipmentId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceTicket createTicket(@PathVariable UUID equipmentId,
                                          @RequestBody CreateTicketRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        return maintenanceTicketService.createTicket(equipmentId,
                new MaintenanceTicketService.CreateTicketRequest(request.issue(), jwt.getSubject()));
    }

    @PutMapping("/{ticketId}")
    public MaintenanceTicket updateTicket(@PathVariable UUID ticketId,
                                          @RequestBody MaintenanceTicketService.UpdateTicketRequest request) {
        return maintenanceTicketService.updateTicket(ticketId, request);
    }

    @PutMapping("/{ticketId}/archive")
    public MaintenanceTicket archiveTicket(@PathVariable UUID ticketId) {
        return maintenanceTicketService.archiveTicket(ticketId);
    }

    public record CreateTicketRequest(String issue) {
    }
}
