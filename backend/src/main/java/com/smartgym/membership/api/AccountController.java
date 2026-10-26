package com.smartgym.membership.api;

import com.smartgym.membership.profile.AccountProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/membership/accounts")
public class AccountController {
    private final AccountProvisioningService accountProvisioningService;

    public AccountController(AccountProvisioningService accountProvisioningService) {
        this.accountProvisioningService = accountProvisioningService;
    }

    @PostMapping("/members")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedMemberAccountResponse createMember(
            @Valid @RequestBody MemberAccountCreateRequest request,
            Authentication authentication) {
        return accountProvisioningService.createMember(request, authentication);
    }

    @PostMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedEmployeeAccountResponse createEmployee(
            @Valid @RequestBody EmployeeAccountCreateRequest request,
            Authentication authentication) {
        return accountProvisioningService.createEmployee(request, authentication);
    }
}
