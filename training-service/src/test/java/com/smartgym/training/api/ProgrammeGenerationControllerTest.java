package com.smartgym.training.api;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.application.AiJobService;
import com.smartgym.training.domain.AiJob;
import com.smartgym.training.domain.AiJobStatus;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.ExperienceLevel;
import com.smartgym.training.domain.MuscleGroup;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.repository.AiJobRepository;
import com.smartgym.training.repository.ExerciseRepository;
import com.smartgym.training.repository.TrainingOutboxRepository;
import com.smartgym.training.repository.TrainingProgrammeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProgrammeGenerationControllerTest {
    private static final String COACH_SUBJECT = "coach-subject";
    private static final String COACH_TOKEN = "coach-token";
    private static final UUID COACH_ID = UUID.fromString("28d55e3e-c6f0-4ba6-b7ac-97b176b7e762");
    private static final UUID MEMBER_ID = UUID.fromString("c6b12091-c34d-4d09-8e99-944b0f5cb8d0");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiJobRepository jobs;
    @Autowired private ExerciseRepository exercises;
    @Autowired private TrainingOutboxRepository outbox;
    @Autowired private TrainingProgrammeRepository programmes;
    @Autowired private AiJobService jobService;

    @MockitoBean
    private MembershipClient membership;

    private UUID dumbbellExerciseId;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        outbox.deleteAll();
        programmes.deleteAll();
        exercises.deleteAll();
        dumbbellExerciseId = exercises.save(new Exercise(
                "Dumbbell row",
                null,
                "Pull the dumbbell toward your hip.",
                MuscleGroup.BACK,
                ExerciseDifficulty.BEGINNER,
                EquipmentCategory.DUMBBELL)).getId();
        when(membership.identity(COACH_TOKEN)).thenReturn(new MembershipClient.Identity(
                COACH_SUBJECT, "Coach", List.of("COACH"), null, COACH_ID, "COMPLETE", false));
        when(membership.assignedMembers(COACH_TOKEN)).thenReturn(List.of(
                new MembershipClient.AssignedMember(MEMBER_ID, "Member")));
    }

    @Test
    void generationRequestCreatesAuthorizedQueuedJobAndTransactionalOutbox() throws Exception {
        String response = mvc.perform(post("/api/training/programme-generations")
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(MEMBER_ID, "Build general strength")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(response).get("jobId").asText());
        AiJob job = jobs.findById(jobId).orElseThrow();
        assertEquals(AiJobStatus.QUEUED, job.getStatus());
        assertEquals(1, outbox.count());
        var message = outbox.findAll().get(0);
        assertEquals("training.programme-generation.requested.v1", message.getRoutingKey());
        assertFalse(message.getPayloadJson().contains(MEMBER_ID.toString()));
        assertTrue(message.getPayloadJson().contains(dumbbellExerciseId.toString()));

        mvc.perform(get("/api/training/ai-jobs/{id}", jobId).with(coach()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void coachCannotGenerateForUnassignedMember() throws Exception {
        mvc.perform(post("/api/training/programme-generations")
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(UUID.randomUUID(), "Build general strength")))
                .andExpect(status().isForbidden());
        assertEquals(0, jobs.count());
        assertEquals(0, outbox.count());
    }

    @Test
    void medicalRequestsAreRejectedAndMalformedBoundsFailValidation() throws Exception {
        mvc.perform(post("/api/training/programme-generations")
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(MEMBER_ID, "Rehabilitation after knee injury")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not provide medical")));

        mvc.perform(post("/api/training/programme-generations")
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","goal":"Strength","experienceLevel":"BEGINNER",
                                 "trainingDaysPerWeek":8,"maxSessionDurationMinutes":10,
                                 "availableEquipment":["DUMBBELL"],"exercisePreferences":null,
                                 "nonMedicalConstraints":null}
                                """.formatted(MEMBER_ID)))
                .andExpect(status().isBadRequest());
        assertEquals(0, jobs.count());
    }

    @Test
    void resultCreatesExactlyOneUnpublishedAiDraftAndIsIdempotent() throws Exception {
        UUID jobId = createJob();
        String generated = generatedOutput(dumbbellExerciseId);

        jobService.handleResult(jobId, generated, null);
        AiJob completed = jobs.findById(jobId).orElseThrow();
        UUID programmeId = completed.getProgrammeId();
        assertEquals(AiJobStatus.COMPLETED, completed.getStatus());
        assertNotNull(programmeId);
        var programme = programmes.findById(programmeId).orElseThrow();
        assertEquals("AI", programme.getOrigin().name());
        assertEquals(jobId, programme.getSourceAiJobId());
        assertEquals("DRAFT", programme.getStatus().name());

        jobService.handleResult(jobId, generated.replace("Strength A", "Changed"), null);
        assertEquals(1, programmes.count());
        assertEquals(programmeId, jobs.findById(jobId).orElseThrow().getProgrammeId());
    }

    @Test
    void invalidJsonAndUnknownExercisesFailWithoutCreatingProgrammes() throws Exception {
        UUID malformedJob = createJob();
        jobService.handleResult(malformedJob, "{", null);
        assertEquals(AiJobStatus.FAILED, jobs.findById(malformedJob).orElseThrow().getStatus());

        UUID unknownExerciseJob = createJob();
        jobService.handleResult(
                unknownExerciseJob,
                generatedOutput(UUID.randomUUID()),
                null);
        assertEquals(AiJobStatus.FAILED, jobs.findById(unknownExerciseJob).orElseThrow().getStatus());
        assertEquals(0, programmes.count());
    }

    @Test
    void memberCannotReadAiJob() throws Exception {
        UUID jobId = createJob();
        mvc.perform(get("/api/training/ai-jobs/{id}", jobId)
                        .with(principal("member-subject", "member-token", "MEMBER")))
                .andExpect(status().isForbidden());
    }

    private UUID createJob() throws Exception {
        String response = mvc.perform(post("/api/training/programme-generations")
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(MEMBER_ID, "Build general strength")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("jobId").asText());
    }

    private static String requestBody(UUID memberId, String goal) {
        return """
                {"memberId":"%s","goal":"%s","experienceLevel":"BEGINNER",
                 "trainingDaysPerWeek":1,"maxSessionDurationMinutes":60,
                 "availableEquipment":["DUMBBELL"],"exercisePreferences":null,
                 "nonMedicalConstraints":null}
                """.formatted(memberId, goal);
    }

    private static String generatedOutput(UUID exerciseId) {
        return """
                {"title":"Strength A","goal":"Build general strength","durationWeeks":4,
                 "sessions":[{"dayOrder":1,"title":"Workout A","notes":null,"exercises":[
                   {"orderIndex":1,"exerciseId":"%s","sets":3,"repetitions":8,
                    "durationSeconds":null,"restSeconds":60,"suggestedLoadKg":null,"notes":null}
                 ]}]}
                """.formatted(exerciseId);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor coach() {
        return principal(COACH_SUBJECT, COACH_TOKEN, "COACH");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(
            String subject, String tokenValue, String role) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .tokenValue(tokenValue)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }
}
