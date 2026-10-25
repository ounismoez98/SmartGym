package com.smartgym.membership.feedback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.feedback.dto.AiAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GroqAiService {

    private static final Logger log = LoggerFactory.getLogger(GroqAiService.class);

    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqAiService(
            @Value("${app.groq.api-key:}") String apiKey,
            @Value("${app.groq.model:qwen-2.5-32b}") String model,
            @Value("${app.groq.api-url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "qwen-2.5-32b";
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.groq.com/openai/v1/chat/completions";
        this.objectMapper = objectMapper;

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(6))
                        .withReadTimeout(Duration.ofSeconds(12))
        );

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public AiAnalysisResult analyzeFeedback(int rating, FeedbackCategory category, String comment) {
        if (apiKey.isBlank() || apiKey.startsWith("change-") || apiKey.contains("your-groq")) {
            log.info("Groq API key is not configured. Utilizing heuristic satisfaction analysis fallback.");
            return createFallbackAnalysis(rating, category, comment);
        }

        try {
            String systemPrompt = """
                    You are an expert customer feedback analyzer for Smart Gym.
                    Analyze the member's feedback comment, 1-5 star rating, and category.
                    You MUST respond with a valid JSON object matching this schema:
                    {
                      "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
                      "summary": "1-2 sentence executive summary of the feedback",
                      "themes": ["Theme 1", "Theme 2", ...]
                    }
                    Rules:
                    - "sentiment" must strictly be one of: "POSITIVE", "NEUTRAL", "NEGATIVE".
                    - "summary" must be an objective, professional 1-2 sentence summary.
                    - "themes" must be 1 to 4 concise theme tags (e.g. "Equipment Maintenance", "Clean Locker Rooms", "Staff Helpfulness").
                    - Return ONLY valid JSON.
                    """;

            String userPrompt = String.format("Category: %s\nRating: %d/5\nFeedback comment: %s",
                    category != null ? category.name() : "GENERAL",
                    rating,
                    comment != null ? comment : "");

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2,
                    "max_tokens", 400
            );

            String responseString = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseString == null || responseString.isBlank()) {
                log.warn("Empty response received from Groq API. Falling back to heuristic analysis.");
                return createFallbackAnalysis(rating, category, comment);
            }

            return parseAndValidateGroqResponse(responseString, rating, category, comment);

        } catch (Exception ex) {
            log.warn("Failed to complete Groq AI analysis for feedback due to: {}. Falling back gracefully.", ex.getMessage());
            return createFallbackAnalysis(rating, category, comment);
        }
    }

    private AiAnalysisResult parseAndValidateGroqResponse(String responseJson, int rating, FeedbackCategory category, String comment) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("Groq response choices array is missing or empty.");
                return createFallbackAnalysis(rating, category, comment);
            }

            JsonNode contentNode = choices.get(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                log.warn("Groq response message content is missing.");
                return createFallbackAnalysis(rating, category, comment);
            }

            String content = contentNode.asText().trim();
            JsonNode parsedAnalysis = objectMapper.readTree(content);

            // 1. Sentiment validation
            FeedbackSentiment sentiment;
            String rawSentiment = parsedAnalysis.path("sentiment").asText("");
            if (rawSentiment.isBlank()) {
                sentiment = deriveSentimentFromRating(rating);
            } else {
                sentiment = FeedbackSentiment.fromString(rawSentiment);
            }

            // 2. Summary validation
            String summary = parsedAnalysis.path("summary").asText("");
            if (summary.isBlank()) {
                summary = createFallbackSummary(comment);
            }

            // 3. Themes validation
            List<String> themes = new ArrayList<>();
            JsonNode themesNode = parsedAnalysis.path("themes");
            if (themesNode.isArray() && !themesNode.isEmpty()) {
                for (JsonNode t : themesNode) {
                    String themeText = t.asText("").trim();
                    if (!themeText.isBlank() && !themes.contains(themeText)) {
                        themes.add(themeText);
                    }
                }
            }
            if (themes.isEmpty()) {
                themes = deriveDefaultThemes(category, sentiment);
            }

            return new AiAnalysisResult(sentiment, summary, themes);

        } catch (Exception e) {
            log.warn("Could not parse structured JSON from Groq response content: {}. Falling back.", e.getMessage());
            return createFallbackAnalysis(rating, category, comment);
        }
    }

    public AiAnalysisResult createFallbackAnalysis(int rating, FeedbackCategory category, String comment) {
        FeedbackSentiment sentiment = deriveSentimentFromRating(rating);
        String summary = createFallbackSummary(comment);
        List<String> themes = deriveDefaultThemes(category, sentiment);
        return new AiAnalysisResult(sentiment, summary, themes);
    }

    private FeedbackSentiment deriveSentimentFromRating(int rating) {
        if (rating >= 4) {
            return FeedbackSentiment.POSITIVE;
        } else if (rating == 3) {
            return FeedbackSentiment.NEUTRAL;
        } else {
            return FeedbackSentiment.NEGATIVE;
        }
    }

    private String createFallbackSummary(String comment) {
        if (comment == null || comment.isBlank()) {
            return "Member submitted rating with no extended comments.";
        }
        String trimmed = comment.trim();
        if (trimmed.length() <= 160) {
            return trimmed;
        }
        int periodIndex = trimmed.indexOf('.', 60);
        if (periodIndex != -1 && periodIndex <= 180) {
            return trimmed.substring(0, periodIndex + 1);
        }
        return trimmed.substring(0, 157) + "...";
    }

    private List<String> deriveDefaultThemes(FeedbackCategory category, FeedbackSentiment sentiment) {
        List<String> themes = new ArrayList<>();
        if (category != null) {
            switch (category) {
                case CLEANLINESS -> themes.add("Cleanliness Standards");
                case EQUIPMENT -> themes.add("Gym Equipment");
                case CLASSES -> themes.add("Class Schedules");
                case STAFF -> themes.add("Staff & Coaching");
                case SERVICE -> themes.add("Customer Care");
            }
        } else {
            themes.add("General Gym Experience");
        }

        if (sentiment == FeedbackSentiment.POSITIVE) {
            themes.add("High Satisfaction");
        } else if (sentiment == FeedbackSentiment.NEGATIVE) {
            themes.add("Needs Attention");
        } else {
            themes.add("General Feedback");
        }
        return themes;
    }
}
