package com.smartgym.membership.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.api.MemberCreateRequest;
import com.smartgym.membership.api.MemberResponse;
import com.smartgym.membership.api.MemberUpdateRequest;
import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.security.AccessChecker;
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
public class MemberService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> MEMBER_EDITABLE_FIELDS = List.of("email", "phone");

    private final MemberRepository memberRepository;
    private final CoachAssignmentRepository assignmentRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccessChecker accessChecker;

    public MemberService(
            MemberRepository memberRepository,
            CoachAssignmentRepository assignmentRepository,
            MemberSubscriptionRepository subscriptionRepository,
            AuditLogRepository auditLogRepository,
            AccessChecker accessChecker) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessChecker = accessChecker;
    }

    @Transactional(readOnly = true)
    public Page<MemberResponse> list(
            String query,
            Member.Status status,
            int page,
            int size,
            Authentication authentication) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination values.");
        }
        List<String> assignedSubjects = null;
        if (!accessChecker.isAdministrator(authentication)) {
            String coachSubject = subject(authentication);
            assignedSubjects = assignmentRepository.findAllByCoachSubjectIgnoreCaseAndEndedAtIsNull(coachSubject)
                    .stream()
                    .map(CoachAssignment::getMemberSubject)
                    .map(subject -> subject.toLowerCase(java.util.Locale.ROOT))
                    .distinct()
                    .toList();
            if (assignedSubjects.isEmpty()) {
                return Page.empty(PageRequest.of(page, size));
            }
        }

        Specification<Member> specification = filter(query, status, assignedSubjects);
        Pageable pageable = PageRequest.of(page, size);
        return memberRepository.findAll(specification, pageable).map(MemberResponse::from);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(UUID id) {
        return MemberResponse.from(findMember(id));
    }

    public MemberResponse create(MemberCreateRequest request, Authentication authentication) {
        String keycloakUserId = request.keycloakUserId().toString().toLowerCase(java.util.Locale.ROOT);
        if (memberRepository.findByKeycloakUserId(keycloakUserId).isPresent()) {
            throw conflict("The Keycloak user is already linked to a member.");
        }
        if (request.email() != null && memberRepository.existsByEmailIgnoreCase(request.email())) {
            throw conflict("The email address is already in use.");
        }

        Member member = new Member(
                UUID.randomUUID(),
                keycloakUserId,
                keycloakUserId,
                displayName(request.firstName(), request.lastName(), keycloakUserId),
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.birthDate());
        memberRepository.save(member);
        auditLogRepository.save(audit(
                authentication,
                "MEMBER_CREATED",
                member.getId(),
                List.of("keycloakUserId", "firstName", "lastName", "email", "phone", "birthDate")));
        return MemberResponse.from(member);
    }

    public MemberResponse update(UUID id, MemberUpdateRequest request, Authentication authentication) {
        Member member = findMember(id);
        List<String> suppliedFields = request.getSuppliedFields().stream().sorted().toList();
        if (suppliedFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one member field is required.");
        }
        if (!accessChecker.isAdministrator(authentication)
                && suppliedFields.stream().anyMatch(field -> !MEMBER_EDITABLE_FIELDS.contains(field))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Members may update only their own email address and phone number.");
        }
        if (request.getEmail() != null
                && !request.getEmail().equalsIgnoreCase(member.getEmail())
                && memberRepository.existsByEmailIgnoreCaseAndIdNot(request.getEmail(), id)) {
            throw conflict("The email address is already in use.");
        }

        if (accessChecker.isAdministrator(authentication)) {
            member.updateDetails(
                    suppliedFields.contains("firstName") ? request.getFirstName() : member.getFirstName(),
                    suppliedFields.contains("lastName") ? request.getLastName() : member.getLastName(),
                    suppliedFields.contains("email") ? request.getEmail() : member.getEmail(),
                    suppliedFields.contains("phone") ? request.getPhone() : member.getPhone(),
                    suppliedFields.contains("birthDate") ? request.getBirthDate() : member.getBirthDate());
        } else {
            member.updateContact(
                    suppliedFields.contains("email") ? request.getEmail() : member.getEmail(),
                    suppliedFields.contains("phone") ? request.getPhone() : member.getPhone());
        }
        memberRepository.save(member);
        auditLogRepository.save(audit(authentication, "MEMBER_UPDATED", id, suppliedFields));
        return MemberResponse.from(member);
    }

    public MemberResponse deactivate(UUID id, Authentication authentication) {
        Member member = findMember(id);
        if (subscriptionRepository.existsByMemberIdAndStatus(
                id, MemberSubscription.Status.ACTIVE)) {
            throw conflict("A member with an active subscription cannot be deactivated.");
        }
        member.deactivate();
        memberRepository.save(member);
        auditLogRepository.save(audit(authentication, "MEMBER_DEACTIVATED", id, List.of("status")));
        return MemberResponse.from(member);
    }

    private Member findMember(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
    }

    private static Specification<Member> filter(
            String query,
            Member.Status status,
            List<String> assignedSubjects) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("displayName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("keycloakUserId")), pattern)));
            }
            if (assignedSubjects != null) {
                predicates.add(root.get("keycloakUserId").in(assignedSubjects));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static AuditLog audit(
            Authentication authentication,
            String action,
            UUID memberId,
            List<String> changedFields) {
        return new AuditLog(
                subject(authentication),
                action,
                "MEMBER",
                memberId,
                Map.of("changedFields", changedFields));
    }

    private static String subject(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    private static String displayName(String firstName, String lastName, String fallback) {
        String name = ((firstName == null ? "" : firstName.trim())
                + " " + (lastName == null ? "" : lastName.trim())).trim();
        return name.isEmpty() ? fallback : name;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
