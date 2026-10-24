package com.smartgym.membership.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartgym.membership.api.MemberCreateRequest;
import com.smartgym.membership.api.MemberUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.security.AccessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CoachAssignmentRepository assignmentRepository;

    @Mock
    private MemberSubscriptionRepository subscriptionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AccessChecker accessChecker;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(
                memberRepository,
                assignmentRepository,
                subscriptionRepository,
                auditLogRepository,
                accessChecker);
    }

    @Test
    void createRejectsDuplicateKeycloakUserId() {
        UUID keycloakId = UUID.randomUUID();
        when(memberRepository.findByKeycloakUserId(keycloakId.toString()))
                .thenReturn(Optional.of(member(keycloakId)));

        assertThatThrownBy(() -> memberService.create(
                new MemberCreateRequest(keycloakId, null, null, null, null, null),
                authentication("admin")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any(Member.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void createRejectsDuplicateEmailCaseInsensitively() {
        UUID keycloakId = UUID.randomUUID();
        when(memberRepository.findByKeycloakUserId(keycloakId.toString())).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("person@example.test")).thenReturn(true);

        assertThatThrownBy(() -> memberService.create(
                new MemberCreateRequest(keycloakId, null, null, "person@example.test", null, null),
                authentication("admin")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        verify(memberRepository, never()).save(any(Member.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void deactivationIsRefusedWhileMemberHasActiveSubscription() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.existsByMemberIdAndStatus(
                memberId, MemberSubscription.Status.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> memberService.deactivate(memberId, authentication("admin")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        assertThat(member.getStatus()).isEqualTo(Member.Status.ACTIVE);
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void deactivationChangesStatusAndWritesAuditEntry() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(subscriptionRepository.existsByMemberIdAndStatus(
                memberId, MemberSubscription.Status.ACTIVE)).thenReturn(false);
        var result = memberService.deactivate(memberId, authentication("admin"));

        assertThat(result.status()).isEqualTo(Member.Status.INACTIVE);
        verify(memberRepository).save(member);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(audit ->
                audit.getAction().equals("MEMBER_DEACTIVATED")
                        && audit.getActorUserId().equals("admin")
                        && audit.getEntityId().equals(memberId)));
    }

    @Test
    void memberCannotChangeFieldsOtherThanContactDetails() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(accessChecker.isAdministrator(any())).thenReturn(false);
        MemberUpdateRequest request = new MemberUpdateRequest();
        request.setFirstName("Changed");

        assertThatThrownBy(() -> memberService.update(memberId, request, authentication("member")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);

        verify(memberRepository, never()).save(any(Member.class));
    }

    private static Member member(UUID keycloakId) {
        return new Member(UUID.randomUUID(), keycloakId.toString(), keycloakId.toString(), "Member");
    }

    private static JwtAuthenticationToken authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
