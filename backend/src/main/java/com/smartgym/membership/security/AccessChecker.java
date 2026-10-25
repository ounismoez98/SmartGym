package com.smartgym.membership.security;

import java.util.UUID;
import java.util.Locale;

import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import com.smartgym.membership.profile.MemberSubscription;
import com.smartgym.membership.profile.MemberSubscriptionRepository;
import com.smartgym.membership.payment.PaymentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("accessChecker")
public class AccessChecker {
    private final MemberRepository memberRepository;
    private final CoachAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public AccessChecker(
            MemberRepository memberRepository,
            CoachAssignmentRepository assignmentRepository,
            EmployeeRepository employeeRepository,
            MemberSubscriptionRepository subscriptionRepository,
            PaymentRepository paymentRepository) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    public boolean isOwner(UUID memberId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_MEMBER") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return memberRepository.findById(memberId)
                .map(Member::getKeycloakUserId)
                .filter(subject -> normalize(subject).equals(normalize(jwt.getSubject())))
                .isPresent();
    }

    public boolean isAssignedCoach(UUID memberId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_COACH") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return memberRepository.findById(memberId)
                .map(Member::getKeycloakUserId)
                .map(memberSubject -> assignmentRepository
                        .existsByCoachSubjectIgnoreCaseAndMemberSubjectIgnoreCaseAndEndedAtIsNull(
                        normalize(jwt.getSubject()), normalize(memberSubject)))
                .orElse(false);
    }

    public boolean isEmployeeOwner(UUID employeeId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_COACH") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return employeeRepository.findById(employeeId)
                .map(Employee::getKeycloakUserId)
                .filter(subject -> normalize(subject).equals(normalize(jwt.getSubject())))
                .isPresent();
    }

    public boolean isCoachOwner(UUID employeeId, Authentication authentication) {
        return isEmployeeOwner(employeeId, authentication);
    }

    public boolean isSubscriptionOwner(UUID subscriptionId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_MEMBER") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return subscriptionRepository.findById(subscriptionId)
                .map(MemberSubscription::getMemberId)
                .map(memberId -> memberRepository.findById(memberId)
                        .map(Member::getKeycloakUserId)
                        .filter(subject -> normalize(subject).equals(normalize(jwt.getSubject())))
                        .isPresent())
                .orElse(false);
    }

    public boolean isPaymentOwner(UUID paymentId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_MEMBER") || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        return paymentRepository.findById(paymentId)
                .map(com.smartgym.membership.payment.Payment::getMemberId)
                .map(memberId -> memberRepository.findById(memberId)
                        .map(Member::getKeycloakUserId)
                        .filter(subject -> normalize(subject).equals(normalize(jwt.getSubject())))
                        .isPresent())
                .orElse(false);
    }

    public boolean isAdministrator(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    public boolean isMember(Authentication authentication) {
        return hasRole(authentication, "ROLE_MEMBER");
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private static String normalize(String subject) {
        return subject.toLowerCase(Locale.ROOT);
    }
}
