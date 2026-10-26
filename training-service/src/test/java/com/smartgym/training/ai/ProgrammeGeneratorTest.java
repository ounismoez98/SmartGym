package com.smartgym.training.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExperienceLevel;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
import org.junit.jupiter.api.Test;

class ProgrammeGeneratorTest {
    @Test
    void deterministicFakeCreatesSchemaShapedOutputFromApprovedCandidates() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        ProgrammeGenerationPayload payload = new ProgrammeGenerationPayload(
                new GenerationParameters(
                        "Improve general strength",
                        ExperienceLevel.BEGINNER,
                        2,
                        60,
                        List.of(EquipmentCategory.DUMBBELL),
                        null,
                        null),
                List.of(new CandidateExercise(
                        exerciseId,
                        "Dumbbell row",
                        "Pull with control.",
                        MuscleGroup.BACK,
                        ExerciseDifficulty.BEGINNER,
                        EquipmentCategory.DUMBBELL)));

        String output = new DeterministicFakeProgrammeGenerator(new ObjectMapper()).generate(payload, null);
        GeneratedProgramme generated = new ObjectMapper().readValue(output, GeneratedProgramme.class);

        assertEquals("Deterministic training draft", generated.title());
        assertEquals(2, generated.sessions().size());
        assertEquals(exerciseId, generated.sessions().get(0).exercises().get(0).exerciseId());
        assertEquals(2, generated.sessions().get(1).dayOrder());
    }
}
