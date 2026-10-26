package com.smartgym.training.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.ai.WorkoutInsightContext;
import com.smartgym.training.ai.WorkoutInsightGenerator;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.ExperienceLevel;
import com.smartgym.training.domain.MuscleGroup;
import com.smartgym.training.domain.ProgrammeItem;
import com.smartgym.training.domain.ProgrammeSession;
import com.smartgym.training.domain.TrainingProgramme;
import com.smartgym.training.domain.WorkoutLog;
import com.smartgym.training.domain.WorkoutSet;
import com.smartgym.training.integration.MembershipClient;
import com.smartgym.training.repository.ExerciseRepository;
import com.smartgym.training.repository.TrainingProgrammeRepository;
import com.smartgym.training.repository.WorkoutLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutControllerTest {
    private static final String MEMBER_SUBJECT = "member-subject";
    private static final UUID MEMBER_ID = UUID.fromString("c6b12091-c34d-4d09-8e99-944b0f5cb8d0");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("bb7fdd43-67e3-4c4e-a750-835b48857f2a");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private TrainingProgrammeRepository programmeRepository;

    @Autowired
    private WorkoutLogRepository workoutLogRepository;

    @MockitoBean
    private MembershipClient membershipClient;

    @MockitoBean
    private WorkoutInsightGenerator insightGenerator;

    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        workoutLogRepository.deleteAll();
        programmeRepository.deleteAll();
        exerciseRepository.deleteAll();
        exerciseId = exerciseRepository.save(new Exercise(
                "Squat",
                null,
                "Lower under control.",
                MuscleGroup.QUADRICEPS,
                ExerciseDifficulty.BEGINNER,
                EquipmentCategory.BARBELL)).getId();
        when(membershipClient.identity("member-token")).thenReturn(new MembershipClient.Identity(
                MEMBER_SUBJECT, "Member", List.of("MEMBER"), MEMBER_ID, null, "COMPLETE", false));
        when(membershipClient.identity("other-member-token")).thenReturn(new MembershipClient.Identity(
                "other-member", "Other", List.of("MEMBER"), OTHER_MEMBER_ID, null, "COMPLETE", false));
        when(membershipClient.identity("coach-token")).thenReturn(new MembershipClient.Identity(
                "coach-subject", "Coach", List.of("COACH"), null, UUID.randomUUID(), "COMPLETE", false));
        when(membershipClient.assignedMembers("coach-token")).thenReturn(List.of());
    }

    @Test
    void memberCanLogAndProgressUsesOnlyExplicitLoads() throws Exception {
        String created = mvc.perform(post("/api/training/workout-logs")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programmeId":null,"programmeSessionId":null,
                                 "performedAt":"2026-10-22T10:00:00Z","notes":"Good session",
                                 "sets":[
                                   {"exerciseId":"%s","repetitions":10,"durationSeconds":null,
                                    "loadKg":12.5,"notes":null},
                                   {"exerciseId":"%s","repetitions":null,"durationSeconds":30,
                                    "loadKg":null,"notes":"Timed hold"}
                                 ]}
                                """.formatted(exerciseId, exerciseId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String logId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(get("/api/training/me/progress")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedWorkouts").value(1))
                .andExpect(jsonPath("$.totalRepetitions").value(10))
                .andExpect(jsonPath("$.trainingVolumeKgRepetitions").value(125.0))
                .andExpect(jsonPath("$.maximumRecordedLoadKgByExercise").exists());

        mvc.perform(patch("/api/training/workout-logs/{id}", logId)
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performedAt":"2026-10-22T10:05:00Z","notes":"Edited",
                                 "version":0,"sets":[
                                   {"exerciseId":"%s","repetitions":12,"durationSeconds":null,
                                    "loadKg":15.0,"notes":null}
                                 ]}
                                """.formatted(exerciseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets.length()").value(1));

        mvc.perform(get("/api/training/workout-logs/{id}", logId)
                        .with(principal("other-member", "other-member-token", "MEMBER")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/training/workout-logs/{id}", logId)
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER")))
                .andExpect(status().isNoContent());
    }

    @Test
    void memberCanLogOnlyExercisesFromTheirPublishedProgrammeSession() throws Exception {
        TrainingProgramme programme = new TrainingProgramme(
                MEMBER_ID,
                UUID.randomUUID(),
                "Strength plan",
                "Build strength",
                ExperienceLevel.BEGINNER,
                4,
                "coach-subject");
        programme.replaceSessions(List.of(new ProgrammeSession(
                "Workout A",
                1,
                null,
                List.of(new ProgrammeItem(
                        exerciseId,
                        1,
                        3,
                        8,
                        null,
                        60,
                        null,
                        null)))));
        programme.publish();
        programme = programmeRepository.saveAndFlush(programme);
        UUID programmeId = programme.getId();
        UUID programmeSessionId = programme.getSessions().get(0).getId();
        UUID unrelatedExerciseId = exerciseRepository.save(new Exercise(
                "Row",
                null,
                "Pull with control.",
                MuscleGroup.BACK,
                ExerciseDifficulty.BEGINNER,
                EquipmentCategory.DUMBBELL)).getId();

        mvc.perform(post("/api/training/workout-logs")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programmeId":"%s","programmeSessionId":"%s",
                                 "performedAt":"2026-10-22T10:00:00Z","notes":null,
                                 "sets":[{"exerciseId":"%s","repetitions":8,"durationSeconds":null,
                                 "loadKg":12.5,"notes":null}]}
                                """.formatted(programmeId, programmeSessionId, exerciseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programmeSessionId").value(programmeSessionId.toString()));

        mvc.perform(post("/api/training/workout-logs")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programmeId":"%s","programmeSessionId":"%s",
                                 "performedAt":"2026-10-22T10:00:00Z","notes":null,
                                 "sets":[{"exerciseId":"%s","repetitions":8,"durationSeconds":null,
                                 "loadKg":12.5,"notes":null}]}
                                """.formatted(programmeId, programmeSessionId, unrelatedExerciseId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coachCannotReadUnassignedMemberHistory() throws Exception {
        mvc.perform(get("/api/training/workout-logs")
                        .param("memberId", MEMBER_ID.toString())
                        .with(principal("coach-subject", "coach-token", "COACH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidActualSetPrescriptionIsRejected() throws Exception {
        mvc.perform(post("/api/training/workout-logs")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programmeId":null,"programmeSessionId":null,
                                 "performedAt":"2026-10-22T10:00:00Z","notes":null,
                                 "sets":[{"exerciseId":"%s","repetitions":8,"durationSeconds":30,
                                 "loadKg":null,"notes":null}]}
                                """.formatted(exerciseId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInventedLoadsForBodyweightAndTimedSets() throws Exception {
        UUID bodyweightId = exerciseRepository.save(new Exercise(
                "Push-up",
                null,
                "Lower and push up.",
                MuscleGroup.CHEST,
                ExerciseDifficulty.BEGINNER,
                EquipmentCategory.BODYWEIGHT)).getId();
        var member = principal(MEMBER_SUBJECT, "member-token", "MEMBER");

        mvc.perform(post("/api/training/workout-logs")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workoutRequest(bodyweightId, 10, null, 5.0)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/training/workout-logs")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workoutRequest(exerciseId, null, 30, 5.0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberInsightUsesOnlyTheirRecentLogsAndCalculatedFacts() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        workoutLogRepository.save(new WorkoutLog(
                MEMBER_ID,
                null,
                null,
                now,
                null,
                List.of(
                        new WorkoutSet(exerciseId, 1, 10, null, BigDecimal.valueOf(12.5), null),
                        new WorkoutSet(exerciseId, 2, null, 30, null, null))));
        workoutLogRepository.save(new WorkoutLog(
                OTHER_MEMBER_ID,
                null,
                null,
                now,
                null,
                List.of(new WorkoutSet(exerciseId, 1, 100, null, BigDecimal.valueOf(100), null))));
        workoutLogRepository.save(new WorkoutLog(
                MEMBER_ID,
                null,
                null,
                now.minusDays(40),
                null,
                List.of(new WorkoutSet(exerciseId, 1, 50, null, BigDecimal.valueOf(50), null))));
        when(insightGenerator.answer(anyString(), any(WorkoutInsightContext.class)))
                .thenReturn("You recorded one workout in this period.");

        mvc.perform(post("/api/training/me/workout-insights")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Summarize my recent training\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedWorkouts").value(1))
                .andExpect(jsonPath("$.recordedSets").value(2))
                .andExpect(jsonPath("$.repetitions").value(10))
                .andExpect(jsonPath("$.loadedVolumeKgRepetitions").value(125.0))
                .andExpect(jsonPath("$.answer").value("You recorded one workout in this period."))
                .andExpect(jsonPath("$.encouragement").isNotEmpty());
        ArgumentCaptor<WorkoutInsightContext> facts = ArgumentCaptor.forClass(WorkoutInsightContext.class);
        verify(insightGenerator).answer(eq("Summarize my recent training"), facts.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, facts.getValue().completedWorkouts());
        org.junit.jupiter.api.Assertions.assertEquals(2, facts.getValue().recordedSets());
        org.junit.jupiter.api.Assertions.assertEquals(0, facts.getValue().loadedVolumeKgRepetitions()
                .compareTo(BigDecimal.valueOf(125)));

        mvc.perform(post("/api/training/me/workout-insights")
                        .with(principal("coach-subject", "coach-token", "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Summarize my recent training\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workoutAssistantDoesNotSendInjuryQuestionsToTheModel() throws Exception {
        mvc.perform(post("/api/training/me/workout-insights")
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Could this pain mean I am injured?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(
                        org.hamcrest.Matchers.containsString("cannot assess or advise")));
        org.mockito.Mockito.verifyNoInteractions(insightGenerator);
    }

    private static String workoutRequest(UUID exerciseId, Integer repetitions, Integer duration, Double load) {
        return """
                {"programmeId":null,"programmeSessionId":null,
                 "performedAt":"2026-10-22T10:00:00Z","notes":null,
                 "sets":[{"exerciseId":"%s","repetitions":%s,"durationSeconds":%s,
                 "loadKg":%s,"notes":null}]}
                """.formatted(
                exerciseId,
                repetitions == null ? "null" : repetitions,
                duration == null ? "null" : duration,
                load == null ? "null" : load);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(
            String subject, String tokenValue, String role) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .tokenValue(tokenValue)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
