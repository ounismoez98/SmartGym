package com.smartgym.membership.api;

import java.util.List;
import java.util.UUID;

import com.smartgym.membership.profile.CoachAssignmentService;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/membership/employees")
public class EmployeeController {
    private final EmployeeService employeeService;
    private final CoachAssignmentService assignmentService;

    public EmployeeController(EmployeeService employeeService, CoachAssignmentService assignmentService) {
        this.employeeService = employeeService;
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeCreateRequest request, Authentication authentication) {
        return employeeService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeePageResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Employee.Status status,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return employeeService.list(query, status, jobTitle, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isEmployeeOwner(#p0, authentication)")
    public EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeUpdateRequest request,
            Authentication authentication) {
        return employeeService.update(id, request, authentication);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse deactivate(@PathVariable UUID id, Authentication authentication) {
        return employeeService.deactivate(id, authentication);
    }

    @PostMapping("/{coachId}/members/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachAssignmentResponse assign(
            @PathVariable UUID coachId,
            @PathVariable UUID memberId,
            Authentication authentication) {
        return assignmentService.assign(coachId, memberId, authentication);
    }

    @PostMapping("/{coachId}/members/{memberId}/unassign")
    @PreAuthorize("hasRole('ADMIN')")
    public CoachAssignmentResponse unassign(
            @PathVariable UUID coachId,
            @PathVariable UUID memberId,
            Authentication authentication) {
        return assignmentService.unassign(coachId, memberId, authentication);
    }

    @GetMapping("/{coachId}/members")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isCoachOwner(#p0, authentication)")
    public List<MemberResponse> assignedMembers(@PathVariable UUID coachId) {
        return assignmentService.assignedMembers(coachId);
    }
}
