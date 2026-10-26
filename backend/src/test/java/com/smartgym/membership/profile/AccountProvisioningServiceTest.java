package com.smartgym.membership.profile;

import java.time.LocalDate;
import java.util.UUID;

import com.smartgym.membership.api.EmployeeAccountCreateRequest;
import com.smartgym.membership.api.EmployeeCreateRequest;
import com.smartgym.membership.api.EmployeeResponse;
import com.smartgym.membership.api.MemberAccountCreateRequest;
import com.smartgym.membership.api.MemberCreateRequest;
import com.smartgym.membership.api.MemberResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountProvisioningServiceTest {
    private final KeycloakAccountService keycloakAccountService = mock(KeycloakAccountService.class);
    private final MemberService memberService = mock(MemberService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final AccountProvisioningService service = new AccountProvisioningService(
            keycloakAccountService, memberService, employeeService);

    @Test
    void linksMemberProfileToKeycloakGeneratedId() {
        UUID keycloakId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        MemberResponse profile = mock(MemberResponse.class);
        MemberAccountCreateRequest request = new MemberAccountCreateRequest(
                "new-member", "temporary-password", "New", "Member", "new@example.test",
                "555-0100", LocalDate.of(1990, 1, 1));
        when(keycloakAccountService.createAccount(
                "new-member", "temporary-password", "New", "Member", "new@example.test", "MEMBER"))
                .thenReturn(keycloakId.toString());
        when(memberService.create(any(MemberCreateRequest.class), same(authentication), eq("new-member")))
                .thenReturn(profile);

        var response = service.createMember(request, authentication);

        assertThat(response.username()).isEqualTo("new-member");
        assertThat(response.profile()).isSameAs(profile);
        verify(memberService).create(
                new MemberCreateRequest(keycloakId, "New", "Member", "new@example.test",
                        "555-0100", LocalDate.of(1990, 1, 1)),
                authentication,
                "new-member");
    }

    @Test
    void linksEmployeeProfileToKeycloakGeneratedIdAndCoachRole() {
        UUID keycloakId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        EmployeeResponse profile = mock(EmployeeResponse.class);
        EmployeeAccountCreateRequest request = new EmployeeAccountCreateRequest(
                "new-coach", "temporary-password", "New", "Coach", "new-coach@example.test",
                "Personal Trainer", LocalDate.of(2025, 1, 1));
        when(keycloakAccountService.createAccount(
                "new-coach", "temporary-password", "New", "Coach", "new-coach@example.test", "COACH"))
                .thenReturn(keycloakId.toString());
        when(employeeService.create(any(EmployeeCreateRequest.class), same(authentication), eq("new-coach")))
                .thenReturn(profile);

        var response = service.createEmployee(request, authentication);

        assertThat(response.username()).isEqualTo("new-coach");
        assertThat(response.profile()).isSameAs(profile);
        verify(employeeService).create(
                new EmployeeCreateRequest(keycloakId, "New", "Coach", "new-coach@example.test",
                        "Personal Trainer", LocalDate.of(2025, 1, 1)),
                authentication,
                "new-coach");
    }

    @Test
    void deletesKeycloakAccountWhenMemberProfileCreationFails() {
        String keycloakId = UUID.randomUUID().toString();
        Authentication authentication = mock(Authentication.class);
        MemberAccountCreateRequest request = new MemberAccountCreateRequest(
                "new-member", "temporary-password", "New", "Member", "new@example.test",
                null, null);
        RuntimeException failure = new IllegalStateException("Profile creation failed");
        when(keycloakAccountService.createAccount(
                "new-member", "temporary-password", "New", "Member", "new@example.test", "MEMBER"))
                .thenReturn(keycloakId);
        doThrow(failure).when(memberService)
                .create(any(MemberCreateRequest.class), same(authentication), eq("new-member"));

        assertThatThrownBy(() -> service.createMember(request, authentication))
                .isSameAs(failure);

        verify(keycloakAccountService).deleteAccount(keycloakId, failure);
    }
}
