package com.smartgym.membership.profile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.smartgym.membership.audit.AuditLog;
import com.smartgym.membership.audit.AuditLogRepository;
import com.smartgym.membership.api.CoachAssignmentResponse;
import com.smartgym.membership.api.MemberResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CoachAssignmentService {
    private final EmployeeService employeeService;
    private final MemberRepository memberRepository;
    private final CoachAssignmentRepository assignmentRepository;
    private final AuditLogRepository auditLogRepository;

    public CoachAssignmentService(
            EmployeeService employeeService,
            MemberRepository memberRepository,
            CoachAssignmentRepository assignmentRepository,
            AuditLogRepository auditLogRepository) {
        this.employeeService = employeeService;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public CoachAssignmentResponse assign(UUID coachId, UUID memberId, Authentication authentication) {
        Employee coach = requireActiveCoach(coachId);
        Member member = requireActiveMember(memberId);
        String coachSubject = normalize(coach.getKeycloakUserId());
        String memberSubject = normalize(member.getKeycloakUserId());
        if (assignmentRepository.existsByCoachSubjectIgnoreCaseAndMemberSubjectIgnoreCaseAndEndedAtIsNull(
                coachSubject, memberSubject)) {
            throw conflict("The coach is already assigned to this member.");
        }
        CoachAssignment assignment = assignmentRepository.save(new CoachAssignment(coachSubject, memberSubject));
        audit(authentication, "COACH_MEMBER_ASSIGNED", assignment.getId(), coachId, memberId);
        return new CoachAssignmentResponse(
                assignment.getId(), coachId, memberId, assignment.getCreatedAt(), assignment.getEndedAt());
    }

    public CoachAssignmentResponse unassign(UUID coachId, UUID memberId, Authentication authentication) {
        Employee coach = employeeService.findEmployee(coachId);
        Member member = findMember(memberId);
        CoachAssignment assignment = assignmentRepository.findActiveAssignment(
                        normalize(coach.getKeycloakUserId()), normalize(member.getKeycloakUserId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active coach assignment not found."));
        assignment.finish();
        audit(authentication, "COACH_MEMBER_UNASSIGNED", assignment.getId(), coachId, memberId);
        return new CoachAssignmentResponse(
                assignment.getId(), coachId, memberId, assignment.getCreatedAt(), assignment.getEndedAt());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> assignedMembers(UUID coachId) {
        Employee coach = employeeService.findEmployee(coachId);
        List<String> subjects = assignmentRepository.findAllByCoachSubjectIgnoreCaseAndEndedAtIsNull(
                        coach.getKeycloakUserId())
                .stream()
                .map(assignment -> normalize(assignment.getMemberSubject()))
                .distinct()
                .toList();
        return memberRepository.findAllByKeycloakUserIdIn(subjects).stream()
                .map(MemberResponse::from)
                .toList();
    }

    private Employee requireActiveCoach(UUID coachId) {
        Employee employee = employeeService.findEmployee(coachId);
        if (employee.getStatus() != Employee.Status.ACTIVE) {
            throw conflict("The coach must be active.");
        }
        return employee;
    }

    private Member requireActiveMember(UUID memberId) {
        Member member = findMember(memberId);
        if (member.getStatus() != Member.Status.ACTIVE) {
            throw conflict("The member must be active.");
        }
        return member;
    }

    private Member findMember(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
    }

    private static String normalize(String subject) {
        return subject.toLowerCase(Locale.ROOT);
    }

    private void audit(
            Authentication authentication, String action, UUID assignmentId, UUID coachId, UUID memberId) {
        String actor = authentication instanceof org.springframework.security.oauth2.server.resource.authentication
                        .JwtAuthenticationToken jwtAuthentication
                ? jwtAuthentication.getToken().getSubject()
                : authentication == null ? "unknown" : authentication.getName();
        auditLogRepository.save(new AuditLog(
                actor,
                action,
                "COACH_ASSIGNMENT",
                assignmentId,
                java.util.Map.of("coachId", coachId.toString(), "memberId", memberId.toString())));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
