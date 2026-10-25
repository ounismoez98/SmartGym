package com.smartgym.training.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ProgrammeItem;
import com.smartgym.training.domain.ProgrammeSession;
import com.smartgym.training.domain.ProgrammeStatus;
import com.smartgym.training.domain.TrainingProgramme;
import com.smartgym.training.domain.WorkoutLog;
import com.smartgym.training.domain.WorkoutSet;
import com.smartgym.training.dto.ProgressResponse;
import com.smartgym.training.dto.WorkoutLogPageResponse;
import com.smartgym.training.dto.WorkoutLogPatchRequest;
import com.smartgym.training.dto.WorkoutLogRequest;
import com.smartgym.training.dto.WorkoutLogResponse;
import com.smartgym.training.dto.WorkoutSetRequest;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.repository.ExerciseRepository;
import com.smartgym.training.repository.ProgrammeSessionRepository;
import com.smartgym.training.repository.TrainingProgrammeRepository;
import com.smartgym.training.repository.WorkoutLogRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkoutService {
    private final WorkoutLogRepository logs;
    private final TrainingProgrammeRepository programmes;
    private final ProgrammeSessionRepository sessions;
    private final ExerciseRepository exercises;
    private final MembershipClient membership;
    private final EntityManager entityManager;

    public WorkoutService(
            WorkoutLogRepository logs,
            TrainingProgrammeRepository programmes,
            ProgrammeSessionRepository sessions,
            ExerciseRepository exercises,
            MembershipClient membership,
            EntityManager entityManager) {
        this.logs = logs;
        this.programmes = programmes;
        this.sessions = sessions;
        this.exercises = exercises;
        this.membership = membership;
        this.entityManager = entityManager;
    }

    public WorkoutLogResponse create(Jwt jwt, WorkoutLogRequest request) {
        requireRole(jwt, "MEMBER");
        UUID memberId = memberId(jwt);
        validateReferences(memberId, request.programmeId(), request.programmeSessionId(), request.sets());
        WorkoutLog log = new WorkoutLog(
                memberId,
                request.programmeId(),
                request.programmeSessionId(),
                request.performedAt(),
                trimToNull(request.notes()),
                toSets(request.sets()));
        return WorkoutLogResponse.from(logs.save(log));
    }

    @Transactional(readOnly = true)
    public WorkoutLogPageResponse list(Jwt jwt, UUID memberId, int page, int size) {
        Page<WorkoutLog> result;
        if (hasRole(jwt, "COACH")) {
            if (memberId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coaches must select an assigned member.");
            }
            requireAssignment(jwt, memberId);
            result = logs.findAllByMemberIdOrderByPerformedAtDesc(memberId, PageRequest.of(page, size));
        } else if (hasRole(jwt, "MEMBER")) {
            UUID ownMemberId = memberId(jwt);
            result = logs.findAllByMemberIdOrderByPerformedAtDesc(ownMemberId, PageRequest.of(page, size));
        } else {
            throw forbidden("Workout history is not available to your role.");
        }
        return new WorkoutLogPageResponse(
                result.getContent().stream().map(WorkoutLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public WorkoutLogResponse get(Jwt jwt, UUID id) {
        WorkoutLog log = find(id);
        authorizeRead(jwt, log);
        return WorkoutLogResponse.from(log);
    }

    public WorkoutLogResponse update(Jwt jwt, UUID id, WorkoutLogPatchRequest patch) {
        requireRole(jwt, "MEMBER");
        UUID memberId = memberId(jwt);
        WorkoutLog log = logs.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout log not found."));
        if (patch.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout log version is required for updates.");
        }
        if (patch.version() != log.getVersion()) {
            throw conflict("This workout log changed. Reload it before saving.");
        }
        List<WorkoutSetRequest> updatedSets = patch.sets() == null
                ? log.getSets().stream()
                        .map(set -> new WorkoutSetRequest(
                                set.getExerciseId(),
                                set.getRepetitions(),
                                set.getDurationSeconds(),
                                set.getLoadKg(),
                                set.getNotes()))
                        .toList()
                : patch.sets();
        validateReferences(memberId, log.getProgrammeId(), log.getProgrammeSessionId(), updatedSets);
        entityManager.lock(log, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        log.clearSets();
        entityManager.flush();
        log.update(
                patch.performedAt(),
                patch.notes() == null ? log.getNotes() : trimToNull(patch.notes()),
                List.of());
        log.addSets(toSets(updatedSets));
        try {
            return WorkoutLogResponse.from(logs.saveAndFlush(log));
        } catch (OptimisticLockingFailureException exception) {
            throw conflict("This workout log changed. Reload it before saving.");
        }
    }

    public void delete(Jwt jwt, UUID id) {
        requireRole(jwt, "MEMBER");
        WorkoutLog log = logs.findByIdAndMemberId(id, memberId(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout log not found."));
        logs.delete(log);
    }

    @Transactional(readOnly = true)
    public ProgressResponse myProgress(Jwt jwt) {
        requireRole(jwt, "MEMBER");
        return progress(memberId(jwt));
    }

    @Transactional(readOnly = true)
    public ProgressResponse memberProgress(Jwt jwt, UUID memberId) {
        requireRole(jwt, "COACH");
        requireAssignment(jwt, memberId);
        return progress(memberId);
    }

    private ProgressResponse progress(UUID memberId) {
        List<WorkoutLog> memberLogs = logs.findAllByMemberIdOrderByPerformedAtDesc(memberId);
        long repetitions = 0;
        BigDecimal volume = BigDecimal.ZERO;
        Map<UUID, BigDecimal> maximumLoad = new LinkedHashMap<>();
        Map<String, Long> workoutsByWeek = new LinkedHashMap<>();
        WeekFields iso = WeekFields.ISO;
        for (WorkoutLog log : memberLogs) {
            OffsetDateTime utc = log.getPerformedAt().withOffsetSameInstant(ZoneOffset.UTC);
            String week = "%04d-W%02d".formatted(
                    utc.get(iso.weekBasedYear()),
                    utc.get(iso.weekOfWeekBasedYear()));
            workoutsByWeek.merge(week, 1L, Long::sum);
            for (WorkoutSet set : log.getSets()) {
                if (set.getRepetitions() != null) {
                    repetitions += set.getRepetitions();
                    if (set.getLoadKg() != null) {
                        volume = volume.add(set.getLoadKg().multiply(BigDecimal.valueOf(set.getRepetitions())));
                    }
                }
                if (set.getLoadKg() != null) {
                    maximumLoad.merge(
                            set.getExerciseId(),
                            set.getLoadKg(),
                            (current, value) -> current.max(value));
                }
            }
        }
        return new ProgressResponse(
                memberId,
                memberLogs.size(),
                repetitions,
                volume,
                maximumLoad,
                workoutsByWeek);
    }

    private void validateReferences(
            UUID memberId,
            UUID programmeId,
            UUID programmeSessionId,
            List<WorkoutSetRequest> setRequests) {
        TrainingProgramme programme = null;
        List<UUID> allowedExerciseIds = null;
        if (programmeSessionId != null && programmeId == null) {
            throw invalid("A programme session requires its programme ID.");
        }
        if (programmeId != null) {
            programme = programmes.findById(programmeId)
                    .orElseThrow(() -> invalid("Programme not found."));
            if (!programme.getMemberId().equals(memberId)
                    || programme.getStatus() != ProgrammeStatus.PUBLISHED) {
                throw forbidden("Logs may only reference your own published programme.");
            }
        }
        if (programmeSessionId != null) {
            ProgrammeSession session = sessions.findByIdAndProgrammeId(programmeSessionId, programmeId)
                    .orElseThrow(() -> invalid("The selected session does not belong to this programme."));
            allowedExerciseIds = session.getItems().stream().map(ProgrammeItem::getExerciseId).toList();
        } else if (programme != null) {
            allowedExerciseIds = programme.getSessions().stream()
                    .flatMap(session -> session.getItems().stream())
                    .map(ProgrammeItem::getExerciseId)
                    .toList();
        }
        for (WorkoutSetRequest set : setRequests) {
            boolean hasRepetitions = set.repetitions() != null;
            boolean hasDuration = set.durationSeconds() != null;
            if (hasRepetitions == hasDuration) {
                throw invalid("Each logged set must record repetitions or duration, but not both.");
            }
            Exercise exercise = exercises.findById(set.exerciseId())
                    .orElseThrow(() -> invalid("A workout set references an unknown exercise."));
            if (set.loadKg() != null
                    && (set.durationSeconds() != null
                            || exercise.getEquipmentCategory() == EquipmentCategory.BODYWEIGHT)) {
                throw invalid("Loads are not recorded for timed or bodyweight exercise sets.");
            }
            if (allowedExerciseIds != null && !allowedExerciseIds.contains(set.exerciseId())) {
                throw invalid("A logged exercise is not part of the selected programme session.");
            }
        }
    }

    private WorkoutLog find(UUID id) {
        return logs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout log not found."));
    }

    private void authorizeRead(Jwt jwt, WorkoutLog log) {
        if (hasRole(jwt, "COACH")) {
            requireAssignment(jwt, log.getMemberId());
        } else if (hasRole(jwt, "MEMBER")) {
            if (!memberId(jwt).equals(log.getMemberId())) {
                throw forbidden("Members may only access their own workout logs.");
            }
        } else {
            throw forbidden("Workout history is not available to your role.");
        }
    }

    private UUID memberId(Jwt jwt) {
        MembershipClient.Identity identity = verifiedIdentity(jwt);
        if (!identity.roles().contains("MEMBER") || identity.memberProfileId() == null) {
            throw forbidden("Your member profile is not provisioned.");
        }
        return identity.memberProfileId();
    }

    private void requireAssignment(Jwt jwt, UUID memberId) {
        boolean assigned = membership.assignedMembers(jwt.getTokenValue()).stream()
                .anyMatch(assignedMember -> assignedMember.memberProfileId().equals(memberId));
        if (!assigned) throw forbidden("The member is not currently assigned to this coach.");
    }

    private MembershipClient.Identity verifiedIdentity(Jwt jwt) {
        MembershipClient.Identity identity = membership.identity(jwt.getTokenValue());
        if (identity == null || !jwt.getSubject().equals(identity.subject())) {
            throw forbidden("The membership identity could not be verified.");
        }
        return identity;
    }

    private static List<WorkoutSet> toSets(List<WorkoutSetRequest> requests) {
        List<WorkoutSet> result = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            WorkoutSetRequest request = requests.get(i);
            result.add(new WorkoutSet(
                    request.exerciseId(),
                    i + 1,
                    request.repetitions(),
                    request.durationSeconds(),
                    request.loadKg(),
                    trimToNull(request.notes())));
        }
        return result;
    }

    private static void requireRole(Jwt jwt, String role) {
        if (!hasRole(jwt, role)) throw forbidden("This operation requires the " + role + " role.");
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
