package com.smartgym.membership.profile;

import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoachAssignmentServiceTest {
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final CoachAssignmentRepository assignmentRepository = mock(CoachAssignmentRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final EmployeeService employeeService =
            new EmployeeService(employeeRepository, assignmentRepository, auditLogRepository);
    private final CoachAssignmentService service = new CoachAssignmentService(
            employeeService, memberRepository, assignmentRepository, auditLogRepository);

    @Test
    void assignRejectsDuplicateActiveAssignment() {
        UUID coachId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String coachSubject = UUID.randomUUID().toString();
        String memberSubject = UUID.randomUUID().toString();
        when(employeeRepository.findById(coachId))
                .thenReturn(Optional.of(employee(coachId, coachSubject)));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(memberId, memberSubject)));
        when(assignmentRepository.existsByCoachSubjectIgnoreCaseAndMemberSubjectIgnoreCaseAndEndedAtIsNull(
                        coachSubject, memberSubject))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assign(coachId, memberId, authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(assignmentRepository, never()).save(any(CoachAssignment.class));
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void assignRejectsInactiveCoach() {
        UUID coachId = UUID.randomUUID();
        Employee coach = employee(coachId, UUID.randomUUID().toString());
        coach.deactivate();
        when(employeeRepository.findById(coachId)).thenReturn(Optional.of(coach));

        assertThatThrownBy(() -> service.assign(coachId, UUID.randomUUID(), authentication()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(memberRepository, never()).findById(any());
    }

    private static Employee employee(UUID id, String subject) {
        return new Employee(id, subject, subject, "Coach One", "Coach", "One", "coach@example.test", "Coach", null);
    }

    private static Member member(UUID id, String subject) {
        return new Member(id, subject, subject, "Member One", "Member", "One", "member@example.test", null, null);
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
