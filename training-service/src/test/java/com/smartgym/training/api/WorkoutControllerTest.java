package com.smartgym.training.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.Exercise;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(
            String subject, String tokenValue, String role) {
        return jwt()
                .jwt(token -> token.subject(subject)
                        .tokenValue(tokenValue)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
