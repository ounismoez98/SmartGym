package com.smartgym.training.api;

import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
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
class ProgrammeControllerTest {
    private static final String COACH_SUBJECT = "coach-subject";
    private static final String MEMBER_SUBJECT = "member-subject";
    private static final UUID COACH_ID = UUID.fromString("28d55e3e-c6f0-4ba6-b7ac-97b176b7e762");
    private static final UUID MEMBER_ID = UUID.fromString("c6b12091-c34d-4d09-8e99-944b0f5cb8d0");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private TrainingProgrammeRepository programmeRepository;

    @MockitoBean
    private MembershipClient membershipClient;

    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        programmeRepository.deleteAll();
        exerciseRepository.deleteAll();
        Exercise exercise = exerciseRepository.save(new Exercise(
                "Dumbbell row",
                null,
                "Pull the dumbbell toward your hip.",
                MuscleGroup.BACK,
                ExerciseDifficulty.BEGINNER,
                EquipmentCategory.DUMBBELL));
        exerciseId = exercise.getId();
        when(membershipClient.identity("coach-token")).thenReturn(new MembershipClient.Identity(
                COACH_SUBJECT, "Coach", List.of("COACH"), null, COACH_ID, "COMPLETE", false));
        when(membershipClient.assignedMembers("coach-token")).thenReturn(List.of(
                new MembershipClient.AssignedMember(MEMBER_ID, "Member")));
        when(membershipClient.identity("member-token")).thenReturn(new MembershipClient.Identity(
                MEMBER_SUBJECT, "Member", List.of("MEMBER"), MEMBER_ID, null, "COMPLETE", false));
    }

    @Test
    void coachCanCreateReviewAndPublishProgrammeAndMemberSeesOnlyPublished() throws Exception {
        String programmeId = createProgramme();
        var coach = principal(COACH_SUBJECT, "coach-token", "COACH");

        mvc.perform(post("/api/training/programmes/{id}/submit", programmeId).with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(post("/api/training/programmes/{id}/reviews", programmeId)
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","comment":"Reviewed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedRevision").value(1));

        mvc.perform(post("/api/training/programmes/{id}/publish", programmeId).with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/training/programmes").with(
                        principal(MEMBER_SUBJECT, "member-token", "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(programmeId));
    }

    @Test
    void coachCannotCreateForUnassignedMember() throws Exception {
        var coach = principal(COACH_SUBJECT, "coach-token", "COACH");
        String body = programmeRequest(UUID.randomUUID());

        mvc.perform(post("/api/training/programmes")
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotSeeCoachDraftOrAnotherMembersProgramme() throws Exception {
        String programmeId = createProgramme();

        mvc.perform(get("/api/training/programmes/{id}", programmeId)
                        .with(principal(MEMBER_SUBJECT, "member-token", "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleProgrammeUpdateReturnsConflict() throws Exception {
        String programmeId = createProgramme();
        var coach = principal(COACH_SUBJECT, "coach-token", "COACH");

        mvc.perform(patch("/api/training/programmes/{id}", programmeId)
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programmeRequestWithVersion(999L)))
                .andExpect(status().isConflict());
    }

    @Test
    void itemCannotHaveBothRepetitionsAndDuration() throws Exception {
        String body = """
                {"memberId":"%s","title":"Plan","goal":"Strength","experienceLevel":"BEGINNER",
                 "durationWeeks":4,"version":null,"sessions":[{"title":"Day 1","notes":null,
                 "items":[{"exerciseId":"%s","sets":3,"repetitions":8,"durationSeconds":30,
                 "restSeconds":60,"suggestedLoadKg":null,"notes":null}]}]}
                """.formatted(MEMBER_ID, exerciseId);

        mvc.perform(post("/api/training/programmes")
                        .with(principal(COACH_SUBJECT, "coach-token", "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newRevisionPreservesPublishedProgrammeAndStartsAsDraft() throws Exception {
        String publishedId = createProgramme();
        var coach = principal(COACH_SUBJECT, "coach-token", "COACH");
        mvc.perform(post("/api/training/programmes/{id}/submit", publishedId).with(coach));
        mvc.perform(post("/api/training/programmes/{id}/reviews", publishedId)
                .with(coach)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"comment\":\"ok\"}"));
        mvc.perform(post("/api/training/programmes/{id}/publish", publishedId).with(coach));

        mvc.perform(post("/api/training/programmes/{id}/revisions", publishedId).with(coach))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.supersedesProgrammeId").value(publishedId));

        mvc.perform(get("/api/training/programmes/{id}", publishedId).with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void editingApprovedContentInvalidatesApproval() throws Exception {
        String programmeId = createProgramme();
        var coach = principal(COACH_SUBJECT, "coach-token", "COACH");
        mvc.perform(post("/api/training/programmes/{id}/submit", programmeId).with(coach));
        mvc.perform(post("/api/training/programmes/{id}/reviews", programmeId)
                .with(coach)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\",\"comment\":\"ok\"}"));

        String current = mvc.perform(get("/api/training/programmes/{id}", programmeId).with(coach))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long version = objectMapper.readTree(current).get("version").asLong();
        mvc.perform(patch("/api/training/programmes/{id}", programmeId)
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programmeRequestWithVersion(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvedRevision").value(nullValue()));

        mvc.perform(post("/api/training/programmes/{id}/publish", programmeId).with(coach))
                .andExpect(status().isConflict());
    }

    @Test
    void administratorAloneCannotReadPrivateProgrammeLists() throws Exception {
        mvc.perform(get("/api/training/programmes")
                        .with(principal("admin-subject", "admin-token", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    private String createProgramme() throws Exception {
        String response = mvc.perform(post("/api/training/programmes")
                        .with(principal(COACH_SUBJECT, "coach-token", "COACH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programmeRequestWithVersion(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String programmeRequestWithVersion(Long version) throws Exception {
        return """
                {"memberId":"%s","title":"Strength basics","goal":"Build strength",
                 "experienceLevel":"BEGINNER","durationWeeks":4,"version":%s,
                 "sessions":[{"title":"Day 1","notes":null,"items":[
                   {"exerciseId":"%s","sets":3,"repetitions":8,"durationSeconds":null,
                    "restSeconds":60,"suggestedLoadKg":12.5,"notes":null}
                 ]}]}
                """.formatted(MEMBER_ID, version == null ? "null" : version.toString(), exerciseId);
    }

    private String programmeRequest(UUID memberId) {
        return """
                {"memberId":"%s","title":"Strength basics","goal":"Build strength",
                 "experienceLevel":"BEGINNER","durationWeeks":4,"version":null,
                 "sessions":[{"title":"Day 1","notes":null,"items":[
                   {"exerciseId":"%s","sets":3,"repetitions":8,"durationSeconds":null,
                    "restSeconds":60,"suggestedLoadKg":12.5,"notes":null}
                 ]}]}
                """.formatted(memberId, exerciseId);
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
