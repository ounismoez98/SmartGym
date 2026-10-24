package com.smartgym.training.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import com.smartgym.training.domain.CoachReview;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ProgrammeItem;
import com.smartgym.training.domain.ProgrammeSession;
import com.smartgym.training.domain.ProgrammeStatus;
import com.smartgym.training.domain.ReviewDecision;
import com.smartgym.training.domain.TrainingProgramme;
import com.smartgym.training.dto.ProgrammeItemRequest;
import com.smartgym.training.dto.ProgrammeRequest;
import com.smartgym.training.dto.ProgrammeResponse;
import com.smartgym.training.dto.ProgrammeReviewRequest;
import com.smartgym.training.dto.ProgrammeSessionRequest;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.repository.ExerciseRepository;
import com.smartgym.training.repository.TrainingProgrammeRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProgrammeService {
    private final TrainingProgrammeRepository programmes;
    private final ExerciseRepository exercises;
    private final MembershipClient membership;
    private final EntityManager entityManager;

    public ProgrammeService(
            TrainingProgrammeRepository programmes,
            ExerciseRepository exercises,
            MembershipClient membership,
            EntityManager entityManager) {
        this.programmes = programmes;
        this.exercises = exercises;
        this.membership = membership;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeResponse> list(Jwt jwt, ProgrammeStatus filter) {
        requireAnyRole(jwt, "COACH", "MEMBER");
        if (hasRole(jwt, "COACH")) {
            MembershipClient.Identity identity = identity(jwt);
            requireProfile(identity.employeeProfileId(), "coach");
            List<UUID> assigned = assignedMemberIds(jwt);
            if (assigned.isEmpty()) return List.of();
            List<TrainingProgramme> programmesForCoach = programmes
                    .findAllByMemberIdInOrderByUpdatedAtDesc(assigned);
            return programmesForCoach.stream()
                    .filter(programme -> filter == null || programme.getStatus() == filter)
                    .map(programme -> ProgrammeResponse.from(programme, true))
                    .toList();
        }
        MembershipClient.Identity identity = identity(jwt);
        UUID memberId = requireProfile(identity.memberProfileId(), "member");
        return programmes.findAllByMemberIdAndStatusOrderByUpdatedAtDesc(memberId, ProgrammeStatus.PUBLISHED)
                .stream()
                .map(programme -> ProgrammeResponse.from(programme, false))
                .toList();
    }

    public ProgrammeResponse create(Jwt jwt, ProgrammeRequest request) {
        requireRole(jwt, "COACH");
        MembershipClient.Identity identity = identity(jwt);
        UUID coachId = requireProfile(identity.employeeProfileId(), "coach");
        requireAssignment(jwt, request.memberId());
        validateExercisesAndItems(request.sessions());
        TrainingProgramme programme = new TrainingProgramme(
                request.memberId(),
                coachId,
                request.title().trim(),
                request.goal().trim(),
                request.experienceLevel(),
                request.durationWeeks(),
                jwt.getSubject());
        programme.replaceSessions(createSessions(request.sessions()));
        return ProgrammeResponse.from(programmes.save(programme), true);
    }

    @Transactional(readOnly = true)
    public ProgrammeResponse get(Jwt jwt, UUID id) {
        TrainingProgramme programme = find(id);
        if (hasRole(jwt, "COACH")) {
            requireAssignment(jwt, programme.getMemberId());
            return ProgrammeResponse.from(programme, true);
        }
        if (hasRole(jwt, "MEMBER")) {
            UUID memberId = requireProfile(identity(jwt).memberProfileId(), "member");
            if (!programme.getMemberId().equals(memberId)
                    || programme.getStatus() != ProgrammeStatus.PUBLISHED) {
                throw forbidden("Members may only view their own published programmes.");
            }
            return ProgrammeResponse.from(programme, false);
        }
        throw forbidden("This programme is not available to your role.");
    }

    public ProgrammeResponse update(Jwt jwt, UUID id, ProgrammeRequest request) {
        requireRole(jwt, "COACH");
        TrainingProgramme programme = find(id);
        requireAssignment(jwt, programme.getMemberId());
        if (programme.getStatus() == ProgrammeStatus.PUBLISHED
                || programme.getStatus() == ProgrammeStatus.ARCHIVED
                || programme.getStatus() == ProgrammeStatus.PENDING_REVIEW) {
            throw conflict("This programme cannot be edited in its current state.");
        }
        if (!programme.getMemberId().equals(request.memberId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A programme's member cannot be changed.");
        }
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme version is required for updates.");
        }
        if (request.version() != programme.getVersion()) {
            throw conflict("This programme changed. Reload it before saving.");
        }
        validateExercisesAndItems(request.sessions());
        programme.updateDetails(
                request.title().trim(),
                request.goal().trim(),
                request.experienceLevel(),
                request.durationWeeks());
        programme.clearSessions();
        entityManager.flush();
        programme.addSessions(createSessions(request.sessions()));
        return save(programme);
    }

    public ProgrammeResponse submit(Jwt jwt, UUID id) {
        requireRole(jwt, "COACH");
        TrainingProgramme programme = find(id);
        requireAssignment(jwt, programme.getMemberId());
        if (programme.getStatus() != ProgrammeStatus.DRAFT
                && programme.getStatus() != ProgrammeStatus.CHANGES_REQUESTED) {
            throw conflict("Only draft programmes can be submitted for review.");
        }
        if (programme.getSessions().isEmpty()
                || programme.getSessions().stream().anyMatch(session -> session.getItems().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every submitted programme needs sessions and exercise items.");
        }
        boolean containsArchivedExercise = programme.getSessions().stream()
                .flatMap(session -> session.getItems().stream())
                .map(ProgrammeItem::getExerciseId)
                .map(exercises::findById)
                .anyMatch(exercise -> exercise.isEmpty() || !exercise.get().isActive());
        if (containsArchivedExercise) {
            throw invalid("Replace archived exercises before submitting this programme revision.");
        }
        programme.submitForReview();
        return save(programme);
    }

    public ProgrammeResponse review(Jwt jwt, UUID id, ProgrammeReviewRequest request) {
        requireRole(jwt, "COACH");
        TrainingProgramme programme = find(id);
        MembershipClient.Identity identity = identity(jwt);
        UUID coachId = requireProfile(identity.employeeProfileId(), "coach");
        requireAssignment(jwt, programme.getMemberId());
        if (programme.getStatus() != ProgrammeStatus.PENDING_REVIEW) {
            throw conflict("Only programmes pending review can be reviewed.");
        }
        programme.recordReview(new CoachReview(
                programme.getRevision(),
                coachId,
                request.decision(),
                trimToNull(request.comment())));
        return save(programme);
    }

    public ProgrammeResponse publish(Jwt jwt, UUID id) {
        requireRole(jwt, "COACH");
        TrainingProgramme programme = find(id);
        requireAssignment(jwt, programme.getMemberId());
        if (programme.getStatus() != ProgrammeStatus.APPROVED
                || programme.getApprovedRevision() == null
                || programme.getApprovedRevision() != programme.getRevision()) {
            throw conflict("Publication requires approval of the current revision.");
        }
        programme.publish();
        return save(programme);
    }

    public ProgrammeResponse archive(Jwt jwt, UUID id) {
        requireRole(jwt, "COACH");
        TrainingProgramme programme = find(id);
        requireAssignment(jwt, programme.getMemberId());
        if (programme.getStatus() == ProgrammeStatus.ARCHIVED) return ProgrammeResponse.from(programme, true);
        programme.archive();
        return save(programme);
    }

    public ProgrammeResponse createRevision(Jwt jwt, UUID id) {
        requireRole(jwt, "COACH");
        TrainingProgramme published = find(id);
        requireAssignment(jwt, published.getMemberId());
        if (published.getStatus() != ProgrammeStatus.PUBLISHED) {
            throw conflict("A new revision can only be created from a published programme.");
        }
        List<ProgrammeSessionRequest> sessionRequests = published.getSessions().stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.smartgym.training.domain.ProgrammeSession::getDayOrder))
                .map(session -> new ProgrammeSessionRequest(
                        session.getTitle(),
                        session.getNotes(),
                        session.getItems().stream()
                                .sorted(java.util.Comparator.comparingInt(
                                        com.smartgym.training.domain.ProgrammeItem::getOrderIndex))
                                .map(item -> new ProgrammeItemRequest(
                                        item.getExerciseId(),
                                        item.getSets(),
                                        item.getRepetitions(),
                                        item.getDurationSeconds(),
                                        item.getRestSeconds(),
                                        item.getSuggestedLoadKg(),
                                        item.getNotes()))
                                .toList()))
                .toList();
        TrainingProgramme draft = new TrainingProgramme(
                published.getMemberId(),
                published.getCoachId(),
                published.getTitle(),
                published.getGoal(),
                published.getExperienceLevel(),
                published.getDurationWeeks(),
                jwt.getSubject(),
                published.getRevision() + 1,
                published.getId());
        draft.replaceSessions(createSessions(sessionRequests));
        return ProgrammeResponse.from(programmes.save(draft), true);
    }

    private ProgrammeResponse save(TrainingProgramme programme) {
        try {
            return ProgrammeResponse.from(programmes.saveAndFlush(programme), true);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict("This programme changed. Reload it before saving.");
        }
    }

    private void validateExercisesAndItems(List<ProgrammeSessionRequest> sessions) {
        if (sessions.size() > 14) throw invalid("A programme may contain at most 14 sessions.");
        for (ProgrammeSessionRequest session : sessions) {
            for (ProgrammeItemRequest item : session.items()) {
                boolean repetitionsBased = item.repetitions() != null && item.durationSeconds() == null;
                boolean timeBased = item.repetitions() == null && item.durationSeconds() != null;
                if (!repetitionsBased && !timeBased) {
                    throw invalid("Each exercise prescription must use either repetitions or duration, not both.");
                }
                Exercise exercise = exercises.findById(item.exerciseId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "A programme references an unknown exercise."));
                if (!exercise.isActive()) {
                    throw invalid("Archived exercises cannot be added to new programme content.");
                }
                if (item.suggestedLoadKg() != null
                        && exercise.getEquipmentCategory() == EquipmentCategory.BODYWEIGHT) {
                    throw invalid("Bodyweight exercises cannot have a suggested external load.");
                }
            }
        }
    }

    private static List<ProgrammeSession> createSessions(List<ProgrammeSessionRequest> requests) {
        List<ProgrammeSession> result = new ArrayList<>();
        for (int sessionIndex = 0; sessionIndex < requests.size(); sessionIndex++) {
            ProgrammeSessionRequest sessionRequest = requests.get(sessionIndex);
            List<ProgrammeItem> items = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < sessionRequest.items().size(); itemIndex++) {
                ProgrammeItemRequest item = sessionRequest.items().get(itemIndex);
                items.add(new ProgrammeItem(
                        item.exerciseId(),
                        itemIndex + 1,
                        item.sets(),
                        item.repetitions(),
                        item.durationSeconds(),
                        item.restSeconds(),
                        item.suggestedLoadKg(),
                        trimToNull(item.notes())));
            }
            result.add(new ProgrammeSession(
                    sessionRequest.title().trim(),
                    sessionIndex + 1,
                    trimToNull(sessionRequest.notes()),
                    items));
        }
        return result;
    }

    private void requireAssignment(Jwt jwt, UUID memberId) {
        boolean assigned = assignedMemberIds(jwt).contains(memberId);
        if (!assigned) throw forbidden("The member is not currently assigned to this coach.");
    }

    private List<UUID> assignedMemberIds(Jwt jwt) {
        return membership.assignedMembers(jwt.getTokenValue()).stream()
                .map(MembershipClient.AssignedMember::memberProfileId)
                .toList();
    }

    private MembershipClient.Identity identity(Jwt jwt) {
        MembershipClient.Identity result = membership.identity(jwt.getTokenValue());
        if (result == null || !jwt.getSubject().equals(result.subject())) {
            throw forbidden("The membership identity could not be verified.");
        }
        return result;
    }

    private TrainingProgramme find(UUID id) {
        return programmes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme not found."));
    }

    private static UUID requireProfile(UUID profileId, String type) {
        if (profileId == null) throw forbidden("Your " + type + " profile is not provisioned.");
        return profileId;
    }

    private static void requireRole(Jwt jwt, String role) {
        if (!hasRole(jwt, role)) throw forbidden("This operation requires the " + role + " role.");
    }

    private static void requireAnyRole(Jwt jwt, String... roles) {
        for (String role : roles) if (hasRole(jwt, role)) return;
        throw forbidden("This operation is not available to your role.");
    }

    private static boolean hasRole(Jwt jwt, String role) {
        Object realmAccessClaim = jwt.getClaims().get("realm_access");
        if (!(realmAccessClaim instanceof java.util.Map<?, ?> realmAccess)) return false;
        Object roles = realmAccess.get("roles");
        return roles instanceof List<?> values && values.contains(role);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
