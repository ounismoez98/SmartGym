package com.smartgym.membership.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProvisioningServiceTest {
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final CoachAssignmentRepository assignmentRepository = mock(CoachAssignmentRepository.class);
    private final ProvisioningService service =
            new ProvisioningService(memberRepository, employeeRepository, assignmentRepository);

    @Test
    void returnsResolvedIdentifiersForEveryProvisionedProfile() {
        UUID memberId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(memberRepository.findByKeycloakUserId("member-subject")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    return new Member(memberId, member.getKeycloakUserId(), member.getUsername(), member.getDisplayName());
                });
        when(employeeRepository.findByKeycloakUserId("coach-subject")).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> {
                    Employee employee = invocation.getArgument(0);
                    return new Employee(employeeId, employee.getKeycloakUserId(), employee.getUsername(),
                            employee.getDisplayName());
                });

        ProvisioningService.ProvisioningResult result = service.provision(new ProvisioningRequest(
                List.of(
                        new ProvisioningRequest.Profile("member-subject", "demo-member", "Demo Member", "MEMBER"),
                        new ProvisioningRequest.Profile("coach-subject", "demo-coach", "Demo Coach", "EMPLOYEE")),
                List.of()));

        assertThat(result.profiles())
                .containsExactly(
                        new ProvisioningService.ProvisionedProfile("member-subject", "demo-member", memberId, null),
                        new ProvisioningService.ProvisionedProfile("coach-subject", "demo-coach", null, employeeId));
    }
}
