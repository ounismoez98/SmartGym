package com.smartgym.membership.profile;

import java.util.UUID;

import com.smartgym.membership.api.CreatedEmployeeAccountResponse;
import com.smartgym.membership.api.CreatedMemberAccountResponse;
import com.smartgym.membership.api.EmployeeAccountCreateRequest;
import com.smartgym.membership.api.EmployeeCreateRequest;
import com.smartgym.membership.api.EmployeeResponse;
import com.smartgym.membership.api.MemberAccountCreateRequest;
import com.smartgym.membership.api.MemberCreateRequest;
import com.smartgym.membership.api.MemberResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccountProvisioningService {
    private final KeycloakAccountService keycloakAccountService;
    private final MemberService memberService;
    private final EmployeeService employeeService;

    public AccountProvisioningService(
            KeycloakAccountService keycloakAccountService,
            MemberService memberService,
            EmployeeService employeeService) {
        this.keycloakAccountService = keycloakAccountService;
        this.memberService = memberService;
        this.employeeService = employeeService;
    }

    public CreatedMemberAccountResponse createMember(
            MemberAccountCreateRequest request, Authentication authentication) {
        String keycloakUserId = keycloakAccountService.createAccount(
                request.username(),
                request.temporaryPassword(),
                request.firstName(),
                request.lastName(),
                request.email(),
                "MEMBER");
        try {
            MemberResponse profile = memberService.create(new MemberCreateRequest(
                    UUID.fromString(keycloakUserId),
                    request.firstName(),
                    request.lastName(),
                    request.email(),
                    request.phone(),
                    request.birthDate()), authentication, request.username());
            return new CreatedMemberAccountResponse(request.username(), profile);
        } catch (RuntimeException exception) {
            keycloakAccountService.deleteAccount(keycloakUserId, exception);
            throw exception;
        }
    }

    public CreatedEmployeeAccountResponse createEmployee(
            EmployeeAccountCreateRequest request, Authentication authentication) {
        String keycloakUserId = keycloakAccountService.createAccount(
                request.username(),
                request.temporaryPassword(),
                request.firstName(),
                request.lastName(),
                request.email(),
                "COACH");
        try {
            EmployeeResponse profile = employeeService.create(new EmployeeCreateRequest(
                    UUID.fromString(keycloakUserId),
                    request.firstName(),
                    request.lastName(),
                    request.email(),
                    request.jobTitle(),
                    request.hireDate()), authentication, request.username());
            return new CreatedEmployeeAccountResponse(request.username(), profile);
        } catch (RuntimeException exception) {
            keycloakAccountService.deleteAccount(keycloakUserId, exception);
            throw exception;
        }
    }
}
