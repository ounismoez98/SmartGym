package com.smartgym.membership.feedback.dto;

import java.util.List;
import com.smartgym.membership.feedback.FeedbackSentiment;

public record AiAnalysisResult(
        FeedbackSentiment sentiment,
        String summary,
        List<String> themes
) {}
