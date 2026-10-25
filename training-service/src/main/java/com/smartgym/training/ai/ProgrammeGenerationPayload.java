package com.smartgym.training.ai;

import java.util.List;

public record ProgrammeGenerationPayload(
        GenerationParameters parameters,
        List<CandidateExercise> candidates) {
    public ProgrammeGenerationPayload {
        candidates = List.copyOf(candidates);
    }
}
