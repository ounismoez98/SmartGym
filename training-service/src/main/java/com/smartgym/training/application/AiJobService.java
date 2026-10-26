package com.smartgym.training.application;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.ai.CandidateExercise;
import com.smartgym.training.ai.GeneratedProgramme;
import com.smartgym.training.ai.GenerationParameters;
import com.smartgym.training.ai.ProgrammeGenerationPayload;
import com.smartgym.training.ai.ProgrammeTextPolicy;
import com.smartgym.training.domain.AiJob;
import com.smartgym.training.domain.AiJobStatus;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ProgrammeItem;
import com.smartgym.training.domain.ProgrammeSession;
import com.smartgym.training.domain.TrainingOutboxMessage;
import com.smartgym.training.domain.TrainingProgramme;
import com.smartgym.training.dto.AiJobResponse;
import com.smartgym.training.dto.ProgrammeGenerationAccepted;
import com.smartgym.training.dto.ProgrammeGenerationRequest;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.messaging.MessageEnvelope;
import com.smartgym.training.repository.AiJobRepository;
import com.smartgym.training.repository.ExerciseRepository;
import com.smartgym.training.repository.TrainingOutboxRepository;
import com.smartgym.training.repository.TrainingProgrammeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AiJobService {
    public static final String EXCHANGE = "smartgym.training";
    public static final String REQUESTED_ROUTING_KEY = "training.programme-generation.requested.v1";
    public static final String COMPLETED_ROUTING_KEY = "training.programme-generation.completed.v1";
    public static final String FAILED_ROUTING_KEY = "training.programme-generation.failed.v1";
    public static final String COMMAND_MESSAGE_TYPE = "training.programme-generation.command";
    public static final String RESULT_MESSAGE_TYPE = "training.programme-generation.result";
    private static final int MAX_CANDIDATES = 50;
    private static final TypeReference<List<CandidateExercise>> CANDIDATES_TYPE = new TypeReference<>() {};

    private final AiJobRepository jobs;
    private final ExerciseRepository exercises;
    private final TrainingProgrammeRepository programmes;
    private final TrainingOutboxRepository outbox;
    private final MembershipClient membership;
    private final ObjectMapper objectMapper;

    public AiJobService(
            AiJobRepository jobs,
            ExerciseRepository exercises,
            TrainingProgrammeRepository programmes,
            TrainingOutboxRepository outbox,
            MembershipClient membership,
            ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.exercises = exercises;
        this.programmes = programmes;
        this.outbox = outbox;
        this.membership = membership;
        this.objectMapper = objectMapper;
    }

    public ProgrammeGenerationAccepted create(Jwt jwt, ProgrammeGenerationRequest request) {
        requireRole(jwt, "COACH");
        MembershipClient.Identity identity = verifiedIdentity(jwt);
        UUID coachId = requireProfile(identity.employeeProfileId(), "coach");
        requireAssignment(jwt, request.memberId());
        ProgrammeTextPolicy.requireNonMedical(request.goal());
        ProgrammeTextPolicy.requireNonMedical(request.exercisePreferences());
        ProgrammeTextPolicy.requireNonMedical(request.nonMedicalConstraints());

        GenerationParameters parameters = new GenerationParameters(
                request.goal().trim(),
                request.experienceLevel(),
                request.trainingDaysPerWeek(),
                request.maxSessionDurationMinutes(),
                request.availableEquipment(),
                trimToNull(request.exercisePreferences()),
                trimToNull(request.nonMedicalConstraints()));
        List<CandidateExercise> candidates = candidateExercises(request.availableEquipment());
        if (candidates.isEmpty()) {
            throw invalid("No active exercises match the available equipment. Add catalogue exercises or choose different equipment.");
        }

        try {
            AiJob job = jobs.saveAndFlush(new AiJob(
                    request.memberId(),
                    coachId,
                    jwt.getSubject(),
                    objectMapper.writeValueAsString(parameters),
                    objectMapper.writeValueAsString(candidates)));
            ProgrammeGenerationPayload payload = new ProgrammeGenerationPayload(parameters, candidates);
            MessageEnvelope<ProgrammeGenerationPayload> envelope = new MessageEnvelope<>(
                    UUID.randomUUID(),
                    job.getId(),
                    job.getId(),
                    COMMAND_MESSAGE_TYPE,
                    1,
                    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),
                    payload);
            outbox.save(new TrainingOutboxMessage(
                    envelope.messageId(),
                    REQUESTED_ROUTING_KEY,
                    objectMapper.writeValueAsString(envelope)));
            return new ProgrammeGenerationAccepted(job.getId(), job.getStatus(), job.getCreatedAt());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize the validated training-generation request.", exception);
        }
    }

    @Transactional(readOnly = true)
    public AiJobResponse get(Jwt jwt, UUID id) {
        requireRole(jwt, "COACH");
        AiJob job = find(id);
        requireAssignment(jwt, job.getMemberId());
        return AiJobResponse.from(job);
    }

    public void handleResult(UUID jobId, String outputJson, String failureReason) {
        AiJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != AiJobStatus.QUEUED) return;
        if (failureReason != null || outputJson == null) {
            job.fail(safeFailure(failureReason));
            jobs.save(job);
            return;
        }
        try {
            GenerationParameters parameters = objectMapper.readValue(job.getRequestJson(), GenerationParameters.class);
            List<CandidateExercise> candidates = objectMapper.readValue(job.getCandidateExercisesJson(), CANDIDATES_TYPE);
            GeneratedProgramme generated = objectMapper.readValue(outputJson, GeneratedProgramme.class);
            validateGenerated(parameters, candidates, generated);
            TrainingProgramme programme = createDraft(job, parameters, generated);
            programmes.saveAndFlush(programme);
            job.complete(programme.getId());
            jobs.save(job);
        } catch (IOException | IllegalArgumentException | ResponseStatusException exception) {
            job.fail("The model returned a programme that did not meet the required JSON schema and training rules.");
            jobs.save(job);
        }
    }

    private TrainingProgramme createDraft(
            AiJob job, GenerationParameters parameters, GeneratedProgramme generated) {
        TrainingProgramme programme = new TrainingProgramme(
                job.getMemberId(),
                job.getCoachId(),
                generated.title().trim(),
                generated.goal().trim(),
                parameters.experienceLevel(),
                generated.durationWeeks(),
                job.getCreatedBySubject());
        programme.markAiGenerated(job.getId());
        List<ProgrammeSession> sessions = new ArrayList<>();
        for (GeneratedProgramme.GeneratedSession generatedSession : generated.sessions()) {
            List<ProgrammeItem> items = new ArrayList<>();
            for (GeneratedProgramme.GeneratedItem generatedItem : generatedSession.exercises()) {
                items.add(new ProgrammeItem(
                        generatedItem.exerciseId(),
                        generatedItem.orderIndex(),
                        generatedItem.sets(),
                        generatedItem.repetitions(),
                        generatedItem.durationSeconds(),
                        generatedItem.restSeconds(),
                        generatedItem.suggestedLoadKg(),
                        trimToNull(generatedItem.notes())));
            }
            sessions.add(new ProgrammeSession(
                    generatedSession.title().trim(),
                    generatedSession.dayOrder(),
                    trimToNull(generatedSession.notes()),
                    items));
        }
        programme.replaceSessions(sessions);
        return programme;
    }

    private void validateGenerated(
            GenerationParameters parameters,
            List<CandidateExercise> candidates,
            GeneratedProgramme generated) {
        requireText(generated.title(), 120, "title");
        requireText(generated.goal(), 500, "goal");
        ProgrammeTextPolicy.requireNonMedical(generated.title());
        ProgrammeTextPolicy.requireNonMedical(generated.goal());
        if (generated.durationWeeks() < 1 || generated.durationWeeks() > 52) {
            throw new IllegalArgumentException("Programme duration is outside the permitted range.");
        }
        if (generated.sessions() == null
                || generated.sessions().size() != parameters.trainingDaysPerWeek()) {
            throw new IllegalArgumentException("The generated session count does not match the request.");
        }
        Set<UUID> allowed = candidates.stream().map(CandidateExercise::id).collect(java.util.stream.Collectors.toSet());
        Set<EquipmentCategory> allowedEquipment = new HashSet<>(parameters.availableEquipment());
        allowedEquipment.add(EquipmentCategory.BODYWEIGHT);
        int expectedDayOrder = 1;
        for (GeneratedProgramme.GeneratedSession session : generated.sessions()) {
            if (session == null || session.dayOrder() != expectedDayOrder++) {
                throw new IllegalArgumentException("Session order must be contiguous and start at one.");
            }
            requireText(session.title(), 120, "session title");
            ProgrammeTextPolicy.requireNonMedical(session.title());
            if (session.notes() != null && session.notes().length() > 1000) {
                throw new IllegalArgumentException("Session notes are too long.");
            }
            if (session.exercises() == null || session.exercises().isEmpty() || session.exercises().size() > 20) {
                throw new IllegalArgumentException("Every generated session must contain 1 to 20 exercises.");
            }
            int expectedOrder = 1;
            int estimatedSeconds = 0;
            Set<UUID> usedExercises = new HashSet<>();
            for (GeneratedProgramme.GeneratedItem item : session.exercises()) {
                if (item == null || item.orderIndex() != expectedOrder++) {
                    throw new IllegalArgumentException("Exercise order must be contiguous and start at one.");
                }
                if (!allowed.contains(item.exerciseId()) || !usedExercises.add(item.exerciseId())) {
                    throw new IllegalArgumentException("An exercise is unknown, unavailable, or duplicated.");
                }
                CandidateExercise candidate = candidates.stream()
                        .filter(exercise -> exercise.id().equals(item.exerciseId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown exercise."));
                Exercise currentExercise = exercises.findById(item.exerciseId())
                        .orElseThrow(() -> new IllegalArgumentException("The selected exercise no longer exists."));
                if (!currentExercise.isActive()) {
                    throw new IllegalArgumentException("An archived exercise cannot be used in a generated programme.");
                }
                if (!allowedEquipment.contains(candidate.equipmentCategory())) {
                    throw new IllegalArgumentException("The exercise requires unavailable equipment.");
                }
                if (currentExercise.getEquipmentCategory() != candidate.equipmentCategory()) {
                    throw new IllegalArgumentException("The exercise equipment changed while the job was running.");
                }
                if (item.sets() < 1 || item.sets() > 20 || item.restSeconds() < 0 || item.restSeconds() > 3600) {
                    throw new IllegalArgumentException("Exercise set or rest values are outside the permitted range.");
                }
                boolean repetitions = item.repetitions() != null && item.durationSeconds() == null;
                boolean timed = item.repetitions() == null && item.durationSeconds() != null;
                if (!repetitions && !timed) {
                    throw new IllegalArgumentException("An exercise must specify repetitions or duration, not both.");
                }
                if (repetitions && (item.repetitions() < 1 || item.repetitions() > 100)) {
                    throw new IllegalArgumentException("Repetitions are outside the permitted range.");
                }
                if (timed && (item.durationSeconds() < 1 || item.durationSeconds() > 7200)) {
                    throw new IllegalArgumentException("Duration is outside the permitted range.");
                }
                if (item.suggestedLoadKg() != null
                        && (item.suggestedLoadKg().compareTo(BigDecimal.ZERO) <= 0
                                || item.suggestedLoadKg().compareTo(new BigDecimal("99999.99")) > 0
                                || candidate.equipmentCategory() == EquipmentCategory.BODYWEIGHT
                                || timed)) {
                    throw new IllegalArgumentException("Suggested load is invalid for this exercise.");
                }
                if (item.notes() != null && item.notes().length() > 500) {
                    throw new IllegalArgumentException("Exercise notes are too long.");
                }
                ProgrammeTextPolicy.requireNonMedical(item.notes());
                ProgrammeTextPolicy.requireNonMedical(session.notes());
                int workSecondsPerSet = timed
                        ? item.durationSeconds()
                        : Math.multiplyExact(item.repetitions(), 4);
                estimatedSeconds += item.sets() * workSecondsPerSet
                        + Math.max(0, item.sets() - 1) * item.restSeconds();
            }
            if (estimatedSeconds > parameters.maxSessionDurationMinutes() * 60) {
                throw new IllegalArgumentException("Estimated session duration exceeds the requested limit.");
            }
        }
    }

    private List<CandidateExercise> candidateExercises(List<EquipmentCategory> availableEquipment) {
        Set<EquipmentCategory> allowedEquipment = new HashSet<>(availableEquipment);
        allowedEquipment.add(EquipmentCategory.BODYWEIGHT);
        Specification<Exercise> specification = (root, query, builder) -> builder.and(
                builder.isTrue(root.get("active")),
                root.get("equipmentCategory").in(allowedEquipment));
        return exercises.findAll(
                        specification,
                        PageRequest.of(0, MAX_CANDIDATES, Sort.by(Sort.Direction.ASC, "name")))
                .getContent()
                .stream()
                .map(exercise -> new CandidateExercise(
                        exercise.getId(),
                        exercise.getName(),
                        exercise.getInstructions(),
                        exercise.getPrimaryMuscleGroup(),
                        exercise.getDifficulty(),
                        exercise.getEquipmentCategory()))
                .toList();
    }

    private AiJob find(UUID id) {
        return jobs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI job not found."));
    }

    private void requireAssignment(Jwt jwt, UUID memberId) {
        boolean assigned = membership.assignedMembers(jwt.getTokenValue()).stream()
                .anyMatch(member -> member.memberProfileId().equals(memberId));
        if (!assigned) throw forbidden("The member is not currently assigned to this coach.");
    }

    private MembershipClient.Identity verifiedIdentity(Jwt jwt) {
        MembershipClient.Identity identity = membership.identity(jwt.getTokenValue());
        if (identity == null || !jwt.getSubject().equals(identity.subject())) {
            throw forbidden("The membership identity could not be verified.");
        }
        return identity;
    }

    private static void requireRole(Jwt jwt, String role) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof java.util.Map<?, ?> access)
                || !(access.get("roles") instanceof List<?> roles)
                || !roles.contains(role)) {
            throw forbidden("This operation requires the " + role + " role.");
        }
    }

    private static UUID requireProfile(UUID profileId, String type) {
        if (profileId == null) throw forbidden("Your " + type + " profile is not provisioned.");
        return profileId;
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Generated " + field + " is missing or too long.");
        }
    }

    private static String safeFailure(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Programme generation failed. Please retry or adjust the request.";
        }
        return failureReason.substring(0, Math.min(1000, failureReason.length()));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
