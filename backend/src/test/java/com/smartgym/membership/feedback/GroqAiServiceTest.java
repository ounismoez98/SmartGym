package com.smartgym.membership.feedback;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.feedback.dto.AiAnalysisResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroqAiServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fallbackAnalysisProducesExpectedPositiveResult() {
        GroqAiService service = new GroqAiService("", "qwen-2.5-32b", "https://api.groq.com/openai/v1/chat/completions", objectMapper);

        AiAnalysisResult result = service.analyzeFeedback(5, FeedbackCategory.EQUIPMENT, "The dumbbells and benches are top quality!");
        assertThat(result.sentiment()).isEqualTo(FeedbackSentiment.POSITIVE);
        assertThat(result.summary()).contains("dumbbells and benches");
        assertThat(result.themes()).contains("Gym Equipment", "High Satisfaction");
    }

    @Test
    void fallbackAnalysisProducesExpectedNegativeResult() {
        GroqAiService service = new GroqAiService("", "qwen-2.5-32b", "https://api.groq.com/openai/v1/chat/completions", objectMapper);

        AiAnalysisResult result = service.analyzeFeedback(1, FeedbackCategory.CLEANLINESS, "Locker rooms were not cleaned today.");
        assertThat(result.sentiment()).isEqualTo(FeedbackSentiment.NEGATIVE);
        assertThat(result.summary()).contains("Locker rooms were not cleaned");
        assertThat(result.themes()).contains("Cleanliness Standards", "Needs Attention");
    }

    @Test
    void fallbackAnalysisProducesNeutralResultFor3Stars() {
        GroqAiService service = new GroqAiService("", "qwen-2.5-32b", "https://api.groq.com/openai/v1/chat/completions", objectMapper);

        AiAnalysisResult result = service.analyzeFeedback(3, FeedbackCategory.SERVICE, "It was okay, nothing special.");
        assertThat(result.sentiment()).isEqualTo(FeedbackSentiment.NEUTRAL);
        assertThat(result.themes()).contains("Customer Care", "General Feedback");
    }
}
