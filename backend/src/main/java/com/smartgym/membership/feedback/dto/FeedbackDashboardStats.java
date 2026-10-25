package com.smartgym.membership.feedback.dto;

import java.util.List;
import java.util.Map;

public record FeedbackDashboardStats(
        long totalFeedbacks,
        double averageRating,
        long positiveCount,
        long neutralCount,
        long negativeCount,
        double positivePercentage,
        double neutralPercentage,
        double negativePercentage,
        Map<Integer, Long> ratingDistribution,
        List<CategoryStat> categoryBreakdown,
        List<ThemeStat> commonThemes
) {
    public record CategoryStat(
            String category,
            String displayName,
            long count,
            double averageRating,
            long positiveCount,
            long neutralCount,
            long negativeCount
    ) {}

    public record ThemeStat(
            String theme,
            long count
    ) {}
}
