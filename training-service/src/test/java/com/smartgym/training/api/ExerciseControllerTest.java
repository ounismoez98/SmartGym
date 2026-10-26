package com.smartgym.training.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private com.smartgym.training.repository.ExerciseRepository exercises;

    @BeforeEach
    void cleanDatabase() {
        exercises.deleteAll();
    }

    @Test
    void unauthenticatedCatalogueRequestReturns401() throws Exception {
        mvc.perform(get("/api/training/exercises"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentIsAvailableAtTrainingPrefix() throws Exception {
        mvc.perform(get("/api/training/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void memberCanReadCatalogueButCannotCreateExercise() throws Exception {
        var member = jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));

        mvc.perform(get("/api/training/exercises").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(post("/api/training/exercises")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachCanCreateFilterAndArchiveExercise() throws Exception {
        var coach = jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH"));
        String response = mvc.perform(post("/api/training/exercises")
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(response)
                .get("id")
                .asText());

        mvc.perform(get("/api/training/exercises?muscleGroup=CHEST").with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/training/exercises/{id}/archive", id).with(coach))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/training/exercises").with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/training/exercises/{id}", id).with(coach))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void invalidInputReturnsStructuredValidationErrors() throws Exception {
        mvc.perform(post("/api/training/exercises")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","instructions":"",
                                 "primaryMuscleGroup":"CHEST","difficulty":"BEGINNER",
                                 "equipmentCategory":"BODYWEIGHT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void staleVersionIsRejectedAsConflict() throws Exception {
        var coach = jwt().authorities(new SimpleGrantedAuthority("ROLE_COACH"));
        String response = mvc.perform(post("/api/training/exercises")
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(response)
                .get("id")
                .asText());

        mvc.perform(patch("/api/training/exercises/{id}", id)
                        .with(coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated exercise","version":4}
                                """))
                .andExpect(status().isConflict());
    }

    private static String validRequest() {
        return """
                {"name":"Push up","description":"Bodyweight press",
                 "instructions":"Keep a straight line from head to heels.",
                 "primaryMuscleGroup":"CHEST","difficulty":"BEGINNER",
                 "equipmentCategory":"BODYWEIGHT"}
                """;
    }
}
