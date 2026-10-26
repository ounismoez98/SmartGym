package com.smartgym.membership.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.api.EmployeeCreateRequest;
import com.smartgym.membership.api.EmployeePageResponse;
import com.smartgym.membership.api.EmployeeResponse;
import com.smartgym.membership.api.EmployeeUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.events.MembershipEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EmployeeService {
    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeRepository employeeRepository;
    private final CoachAssignmentRepository assignmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final MembershipEventPublisher eventPublisher;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            CoachAssignmentRepository assignmentRepository,
            AuditLogRepository auditLogRepository,
            MembershipEventPublisher eventPublisher) {
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public EmployeePageResponse list(
            String query, Employee.Status status, String jobTitle, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination values.");
        }
        Specification<Employee> specification = (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern),
                        builder.like(builder.lower(root.get("displayName")), pattern)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (jobTitle != null && !jobTitle.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("jobTitle")),
                        "%" + jobTitle.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(page, size);
        Page<EmployeeResponse> employees = employeeRepository.findAll(specification, pageable)
                .map(EmployeeResponse::from);
        return EmployeePageResponse.from(employees);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        return EmployeeResponse.from(findEmployee(id));
    }

    public EmployeeResponse create(EmployeeCreateRequest request, Authentication authentication) {
        String keycloakUserId = request.keycloakUserId().toString().toLowerCase(Locale.ROOT);
        return create(request, authentication, keycloakUserId);
    }

    public EmployeeResponse create(
            EmployeeCreateRequest request, Authentication authentication, String username) {
        String subject = request.keycloakUserId().toString().toLowerCase(Locale.ROOT);
        if (employeeRepository.findByKeycloakUserId(subject).isPresent()) {
            throw conflict("The Keycloak user is already linked to an employee.");
        }
        if (employeeRepository.existsByEmailIgnoreCase(request.email())) {
            throw conflict("The email address is already in use.");
        }
        Employee employee = new Employee(
                UUID.randomUUID(),
                subject,
                username,
                displayName(request.firstName(), request.lastName()),
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().trim(),
                request.jobTitle().trim(),
                request.hireDate());
        employeeRepository.save(employee);
        audit(authentication, "EMPLOYEE_CREATED", "EMPLOYEE", employee.getId(),
                List.of("keycloakUserId", "firstName", "lastName", "email", "jobTitle", "hireDate"));
        eventPublisher.publish("employee.created", Map.of(
                "employeeId", employee.getId().toString(), "status", employee.getStatus().name()));
        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse update(UUID id, EmployeeUpdateRequest request, Authentication authentication) {
        Employee employee = findEmployee(id);
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw conflict("The email address is already in use.");
        }
        employee.updateDetails(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().trim(),
                request.jobTitle().trim(),
                request.hireDate());
        employeeRepository.save(employee);
        audit(authentication, "EMPLOYEE_UPDATED", "EMPLOYEE", id,
                List.of("firstName", "lastName", "email", "jobTitle", "hireDate"));
        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse deactivate(UUID id, Authentication authentication) {
        Employee employee = findEmployee(id);
        if (employee.getStatus() == Employee.Status.ACTIVE
                && assignmentRepository.hasActiveAssignedMembers(employee.getKeycloakUserId())) {
            throw conflict("The employee has active assigned members.");
        }
        employee.deactivate();
        employeeRepository.save(employee);
        audit(authentication, "EMPLOYEE_DEACTIVATED", "EMPLOYEE", id, List.of("status"));
        eventPublisher.publish("employee.deactivated", Map.of("employeeId", id.toString(), "status", employee.getStatus().name()));
        return EmployeeResponse.from(employee);
    }

    @Transactional(readOnly = true)
    public Employee findEmployee(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));
    }

    private void audit(
            Authentication authentication, String action, String entityType, UUID entityId, List<String> fields) {
        String actor = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getSubject()
                : authentication == null ? "unknown" : authentication.getName();
        auditLogRepository.save(new AuditLog(
                actor, action, entityType, entityId, Map.of("fields", fields)));
    }

    private static String displayName(String firstName, String lastName) {
        return (firstName.trim() + " " + lastName.trim()).trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
