package com.smartgym.membership.profile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.api.EmployeeCreateRequest;
import com.smartgym.membership.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeServiceTest {
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final CoachAssignmentRepository assignmentRepository = mock(CoachAssignmentRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final EmployeeService service =
            new EmployeeService(employeeRepository, assignmentRepository, auditLogRepository);

    @Test
    void createRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmailIgnoreCase("taken@example.test")).thenReturn(true);
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                UUID.randomUUID(), "Coach", "One", "taken@example.test", "Coach", LocalDate.now());

        assertThatThrownBy(() -> service.create(request, authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createRejectsDuplicateKeycloakUserIdCaseInsensitively() {
        UUID keycloakUserId = UUID.randomUUID();
        when(employeeRepository.findByKeycloakUserId(keycloakUserId.toString()))
                .thenReturn(Optional.of(employee(UUID.randomUUID(), keycloakUserId.toString())));
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                keycloakUserId, "Coach", "One", "coach@example.test", "Coach", null);

        assertThatThrownBy(() -> service.create(request, authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void deactivateRejectsEmployeeWithActiveAssignedMembers() {
        UUID id = UUID.randomUUID();
        Employee employee = employee(id, UUID.randomUUID().toString());
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
        when(assignmentRepository.hasActiveAssignedMembers(employee.getKeycloakUserId())).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(id, authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(employee.getStatus()).isEqualTo(Employee.Status.ACTIVE);
        verify(employeeRepository, never()).save(employee);
    }

    private static Employee employee(UUID id, String subject) {
        return new Employee(
                id, subject, subject, "Coach One", "Coach", "One", "coach@example.test", "Coach", null);
    }

    private static org.springframework.security.core.Authentication authentication() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("actor-subject")
                .build();
        return new UsernamePasswordAuthenticationToken(
                jwt, "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
