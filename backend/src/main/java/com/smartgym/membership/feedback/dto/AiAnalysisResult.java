package com.smartgym.membership.feedback.dto;

import java.util.List;
import com.smartgym.membership.feedback.FeedbackSentiment;
import com.smartgym.membership.feedback.FeedbackUrgency;

public record AiAnalysisResult(
        FeedbackSentiment sentiment,
        String summary,
        List<String> themes,
        FeedbackUrgency urgency,
        String actionRecommendation,
        double confidenceScore
) {
    public AiAnalysisResult(FeedbackSentiment sentiment, String summary, List<String> themes) {
        this(sentiment, summary, themes, FeedbackUrgency.LOW, "", 0.95);
    }
}
