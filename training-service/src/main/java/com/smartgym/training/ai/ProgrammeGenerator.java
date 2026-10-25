package com.smartgym.training.ai;

public interface ProgrammeGenerator {
    String generate(ProgrammeGenerationPayload payload, String repairFeedback);
}
