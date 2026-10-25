package com.smartgym.membership.api;

import java.util.UUID;

import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/membership/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse create(
            @Valid @RequestBody MemberCreateRequest request,
            Authentication authentication) {
        return memberService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COACH')")
    public MemberPageResponse list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Member.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Page<MemberResponse> results = memberService.list(query, status, page, size, authentication);
        return MemberPageResponse.from(results);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isOwner(#p0, authentication) "
            + "or @accessChecker.isAssignedCoach(#p0, authentication)")
    public MemberResponse get(@PathVariable UUID id) {
        return memberService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @accessChecker.isOwner(#p0, authentication)")
    public MemberResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody MemberUpdateRequest request,
            Authentication authentication) {
        return memberService.update(id, request, authentication);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse deactivate(@PathVariable UUID id, Authentication authentication) {
        return memberService.deactivate(id, authentication);
    }
}
