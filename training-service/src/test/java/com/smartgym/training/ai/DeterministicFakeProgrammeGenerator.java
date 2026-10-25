package com.smartgym.training.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DeterministicFakeProgrammeGenerator implements ProgrammeGenerator {
    private final ObjectMapper objectMapper;

    public DeterministicFakeProgrammeGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(ProgrammeGenerationPayload payload, String repairFeedback) {
        CandidateExercise exercise = payload.candidates().get(0);
        List<GeneratedProgramme.GeneratedSession> sessions = new ArrayList<>();
        for (int day = 1; day <= payload.parameters().trainingDaysPerWeek(); day++) {
            sessions.add(new GeneratedProgramme.GeneratedSession(
                    day,
                    "Workout " + day,
                    null,
                    List.of(new GeneratedProgramme.GeneratedItem(
                            1,
                            exercise.id(),
                            3,
                            8,
                            null,
                            60,
                            null,
                            null))));
        }
        try {
            return objectMapper.writeValueAsString(new GeneratedProgramme(
                    "Deterministic training draft",
                    payload.parameters().goal(),
                    4,
                    sessions));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize deterministic test output.", exception);
        }
    }
}
