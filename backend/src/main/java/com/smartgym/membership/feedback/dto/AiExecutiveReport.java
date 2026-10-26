package com.smartgym.membership.feedback.dto;

import java.util.List;

public record AiExecutiveReport(
        int healthScore,
        String overallAssessment,
        List<String> keyStrengths,
        List<String> criticalIssues,
        List<String> strategicRecommendations,
        String generatedAt
) {}
