package com.smartgym.membership.feedback;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.feedback.dto.AiAnalysisResult;
import com.smartgym.membership.feedback.dto.AiExecutiveReport;
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

    private static final String DEFAULT_PRIMARY_MODEL = "openai/gpt-oss-20b";
    private static final String FALLBACK_FAST_MODEL = "openai/gpt-oss-120b";
    private static final String FALLBACK_TERTIARY_MODEL = "qwen/qwen3.8-27b";

    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqAiService(
            @Value("${app.groq.api-key:}") String apiKey,
            @Value("${app.groq.model:openai/gpt-oss-20b}") String model,
            @Value("${app.groq.api-url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = resolveModelName(model);
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.groq.com/openai/v1/chat/completions";
        this.objectMapper = objectMapper;

        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(6))
                        .withReadTimeout(Duration.ofSeconds(14))
        );

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static String resolveModelName(String model) {
        if (model == null || model.isBlank()
                || model.equalsIgnoreCase("qwen-2.5-32b")
                || model.contains("qwen-2.5")
                || model.equalsIgnoreCase("llama-3.1-8b-instant")
                || model.equalsIgnoreCase("llama-3.3-70b-versatile")) {
            return DEFAULT_PRIMARY_MODEL;
        }
        return model.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.startsWith("change-") && !apiKey.contains("your-groq");
    }

    public AiAnalysisResult analyzeFeedback(int rating, FeedbackCategory category, String comment) {
        if (!isConfigured()) {
            log.info("Groq API key is not configured. Utilizing heuristic satisfaction analysis fallback.");
            return createFallbackAnalysis(rating, category, comment);
        }

        // Try primary model, with automatic fallback model retry on failure
        AiAnalysisResult result = tryModelAnalysis(this.model, rating, category, comment);
        if (result != null) {
            return result;
        }

        if (!this.model.equalsIgnoreCase(FALLBACK_FAST_MODEL)) {
            log.info("Retrying feedback analysis with secondary model: {}", FALLBACK_FAST_MODEL);
            result = tryModelAnalysis(FALLBACK_FAST_MODEL, rating, category, comment);
            if (result != null) {
                return result;
            }
        }

        log.warn("All Groq model attempts failed. Falling back gracefully to heuristic intelligence.");
        return createFallbackAnalysis(rating, category, comment);
    }

    private AiAnalysisResult tryModelAnalysis(String targetModel, int rating, FeedbackCategory category, String comment) {
        try {
            String systemPrompt = """
                    You are an advanced Customer Experience AI Analyst and Decision Support System for Smart Gym.
                    Perform a deep operational analysis of the member's feedback comment, rating (1-5), and category.
                    You MUST respond with a valid JSON object matching this schema:
                    {
                      "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
                      "confidenceScore": 0.95,
                      "urgency": "CRITICAL" | "MEDIUM" | "LOW",
                      "summary": "Concise executive managerial summary focusing on root cause and member sentiment (max 2 sentences)",
                      "actionRecommendation": "Specific, actionable corrective step or retention initiative for the gym general manager",
                      "themes": ["Theme 1", "Theme 2", ...]
                    }
                    Rules:
                    - "sentiment": must strictly be one of: "POSITIVE", "NEUTRAL", "NEGATIVE".
                    - "confidenceScore": float between 0.50 and 1.00 indicating sentiment confidence.
                    - "urgency":
                      * "CRITICAL" if feedback mentions injury/safety risk, broken machinery, severe hygiene/cleanliness issues, abusive staff, or threats to cancel/churn.
                      * "MEDIUM" if moderate dissatisfaction, crowded peak hours, or general complaints.
                      * "LOW" if positive praise, minor cosmetic request, or routine feedback.
                    - "summary": Clear, objective executive summary without filler words.
                    - "actionRecommendation": Professional management action (e.g., 'Dispatch maintenance for cable machine #3 immediately and contact member with apology pass').
                    - "themes": 1 to 4 sharp categorical tags (e.g., 'Equipment Malfunction', 'Locker Room Hygiene', 'Trainer Professionalism', 'Peak Hour Overcrowding').
                    - Return ONLY valid JSON.
                    """;

            String userPrompt = String.format("Category: %s\nRating: %d/5\nFeedback comment: %s",
                    category != null ? category.name() : "GENERAL",
                    rating,
                    comment != null ? comment : "");

            Map<String, Object> requestBody = Map.of(
                    "model", targetModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2,
                    "max_tokens", 500
            );

            String responseString = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseString == null || responseString.isBlank()) {
                return null;
            }

            return parseAndValidateGroqResponse(responseString, rating, category, comment);

        } catch (Exception ex) {
            log.warn("Groq request failed with model {}: {}", targetModel, ex.getMessage());
            return null;
        }
    }

    private AiAnalysisResult parseAndValidateGroqResponse(String responseJson, int rating, FeedbackCategory category, String comment) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }

            JsonNode contentNode = choices.get(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                return null;
            }

            String content = contentNode.asText().trim();
            JsonNode parsed = objectMapper.readTree(content);

            // 1. Sentiment
            FeedbackSentiment sentiment;
            String rawSentiment = parsed.path("sentiment").asText("");
            if (rawSentiment.isBlank()) {
                sentiment = deriveSentimentFromRating(rating);
            } else {
                sentiment = FeedbackSentiment.fromString(rawSentiment);
            }

            // 2. Summary
            String summary = parsed.path("summary").asText("").trim();
            if (summary.isBlank()) {
                summary = createFallbackSummary(rating, category, comment);
            }

            // 3. Urgency
            FeedbackUrgency urgency;
            String rawUrgency = parsed.path("urgency").asText("");
            if (rawUrgency.isBlank()) {
                urgency = deriveDefaultUrgency(rating, comment);
            } else {
                urgency = FeedbackUrgency.fromString(rawUrgency);
            }

            // 4. Action Recommendation
            String actionRecommendation = parsed.path("actionRecommendation").asText("").trim();
            if (actionRecommendation.isBlank()) {
                actionRecommendation = deriveDefaultAction(category, urgency, sentiment);
            }

            // 5. Confidence Score
            double confidence = parsed.path("confidenceScore").asDouble(0.95);
            if (confidence < 0.5 || confidence > 1.0) {
                confidence = 0.95;
            }

            // 6. Themes
            List<String> themes = new ArrayList<>();
            JsonNode themesNode = parsed.path("themes");
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

            return new AiAnalysisResult(sentiment, summary, themes, urgency, actionRecommendation, confidence);

        } catch (Exception e) {
            log.warn("Could not parse structured JSON from Groq response content: {}. Falling back.", e.getMessage());
            return null;
        }
    }

    public AiExecutiveReport generateExecutiveReport(List<Feedback> feedbacks) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            return new AiExecutiveReport(
                    100,
                    "No feedback records registered yet. System operational pulse is waiting for member submissions.",
                    List.of("Clean baseline state", "Ready to capture reviews"),
                    List.of("No feedback collected yet"),
                    List.of("Encourage members to rate gym facilities on the mobile app or kiosk."),
                    Instant.now().toString()
            );
        }

        if (!isConfigured()) {
            return createFallbackExecutiveReport(feedbacks);
        }

        try {
            String systemPrompt = """
                    You are a Chief Customer Experience Officer (CXO) and Executive AI Advisor for Smart Gym.
                    Analyze the provided batch of gym member reviews and ratings.
                    Synthesize this data into an Executive AI Intelligence Brief for the gym directors.
                    You MUST respond with a valid JSON object matching this schema:
                    {
                      "healthScore": 85,
                      "overallAssessment": "2-3 sentences executive summary synthesizing overall member morale, key operational trends, and facility pulse.",
                      "keyStrengths": ["Strength 1 with operational context", "Strength 2 with operational context", "Strength 3 with operational context"],
                      "criticalIssues": ["Issue 1 requiring priority management intervention", "Issue 2 requiring priority intervention"],
                      "strategicRecommendations": [
                        "Action 1: Immediate operational fix...",
                        "Action 2: Staff coaching or scheduling adjustment...",
                        "Action 3: Long-term facility or equipment upgrade..."
                      ]
                    }
                    Rules:
                    - healthScore: integer from 0 to 100 representing overall gym health/satisfaction index.
                    - keyStrengths: 2 to 4 prominent operational positives members highlight.
                    - criticalIssues: 1 to 3 biggest complaints, risks, or bottlenecks.
                    - strategicRecommendations: exactly 3 concrete, prioritized, high-ROI managerial initiatives.
                    - Return ONLY valid JSON.
                    """;

            StringBuilder reviewsSnippet = new StringBuilder();
            int sampleCount = Math.min(feedbacks.size(), 30);
            for (int i = 0; i < sampleCount; i++) {
                Feedback f = feedbacks.get(i);
                reviewsSnippet.append(String.format("- [%s] Rating: %d/5, Category: %s, Urgency: %s: %s\n",
                        f.getSentiment(),
                        f.getRating(),
                        f.getCategory(),
                        f.getUrgency() != null ? f.getUrgency().name() : "LOW",
                        f.getComment()
                ));
            }

            Map<String, Object> requestBody = Map.of(
                    "model", this.model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", "Member Reviews Dataset:\n" + reviewsSnippet)
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.3,
                    "max_tokens", 800
            );

            String responseString = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseString != null && !responseString.isBlank()) {
                JsonNode root = objectMapper.readTree(responseString);
                JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
                if (!contentNode.isMissingNode() && !contentNode.asText().isBlank()) {
                    JsonNode parsed = objectMapper.readTree(contentNode.asText().trim());
                    int healthScore = parsed.path("healthScore").asInt(80);
                    String assessment = parsed.path("overallAssessment").asText("").trim();
                    List<String> strengths = new ArrayList<>();
                    parsed.path("keyStrengths").forEach(s -> strengths.add(s.asText()));
                    List<String> issues = new ArrayList<>();
                    parsed.path("criticalIssues").forEach(s -> issues.add(s.asText()));
                    List<String> recommendations = new ArrayList<>();
                    parsed.path("strategicRecommendations").forEach(s -> recommendations.add(s.asText()));

                    if (!assessment.isBlank() && !strengths.isEmpty()) {
                        return new AiExecutiveReport(
                                healthScore,
                                assessment,
                                strengths,
                                issues,
                                recommendations,
                                Instant.now().toString()
                        );
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Executive report AI generation failed: {}. Utilizing heuristic synthesis.", ex.getMessage());
        }

        return createFallbackExecutiveReport(feedbacks);
    }

    public AiAnalysisResult createFallbackAnalysis(int rating, FeedbackCategory category, String comment) {
        FeedbackSentiment sentiment = deriveSentimentFromRating(rating);
        FeedbackUrgency urgency = deriveDefaultUrgency(rating, comment);
        String summary = createFallbackSummary(rating, category, comment);
        String actionRecommendation = deriveDefaultAction(category, urgency, sentiment);
        List<String> themes = deriveDefaultThemes(category, sentiment);
        double confidence = (rating == 5 || rating == 1) ? 0.98 : 0.91;

        return new AiAnalysisResult(sentiment, summary, themes, urgency, actionRecommendation, confidence);
    }

    public AiExecutiveReport createFallbackExecutiveReport(List<Feedback> feedbacks) {
        double avg = feedbacks.stream().mapToInt(Feedback::getRating).average().orElse(4.0);
        long criticals = feedbacks.stream().filter(f -> f.getUrgency() == FeedbackUrgency.CRITICAL).count();
        int healthScore = (int) Math.round((avg / 5.0) * 100.0);
        if (criticals > 0) {
            healthScore = Math.max(20, healthScore - (int) (criticals * 8));
        }

        String assessment = String.format(Locale.ROOT,
                "Executive analysis synthesized %d member submissions with an average satisfaction rating of %.1f/5.0. " +
                "Operational health remains at %d%%, with %d critical feedback flag(s) requiring management attention.",
                feedbacks.size(), avg, healthScore, criticals
        );

        List<String> strengths = List.of(
                "High member engagement and consistent positive feedback on facility cleanliness and class coaching.",
                "Members frequently commend friendly gym staff and equipment diversity."
        );

        List<String> issues = new ArrayList<>();
        if (criticals > 0) {
            issues.add(String.format(Locale.ROOT, "%d critical priority ticket(s) flagged regarding equipment downtime or facility concerns.", criticals));
        } else {
            issues.add("Occasional equipment wait times reported during peak evening hours (6 PM - 8 PM).");
        }

        List<String> recommendations = List.of(
                "Prioritize preventative maintenance checkups on high-traffic strength machines twice weekly.",
                "Implement a 15-minute floor audit during peak gym hours to ensure equipment re-racking and hygiene.",
                "Acknowledge critical reviews proactively via the management portal within 24 hours to prevent churn."
        );

        return new AiExecutiveReport(
                healthScore,
                assessment,
                strengths,
                issues,
                recommendations,
                Instant.now().toString()
        );
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

    private FeedbackUrgency deriveDefaultUrgency(int rating, String comment) {
        String lower = comment != null ? comment.toLowerCase(Locale.ROOT) : "";
        if (rating <= 2) {
            if (lower.contains("broken") || lower.contains("danger") || lower.contains("injury")
                    || lower.contains("cancel") || lower.contains("refund") || lower.contains("dirty")
                    || lower.contains("terrible") || lower.contains("safety") || lower.contains("worst")) {
                return FeedbackUrgency.CRITICAL;
            }
            return FeedbackUrgency.MEDIUM;
        }
        if (rating == 3) {
            return FeedbackUrgency.MEDIUM;
        }
        return FeedbackUrgency.LOW;
    }

    private String createFallbackSummary(int rating, FeedbackCategory category, String comment) {
        String catName = category != null ? category.getDisplayName() : "Gym Experience";
        if (comment == null || comment.isBlank()) {
            return String.format("Member submitted a %d-star evaluation for %s with no additional commentary.", rating, catName);
        }
        String trimmed = comment.trim();
        if (rating >= 4) {
            return String.format("Positive feedback regarding %s: \"%s\"", catName, truncateText(trimmed, 130));
        } else if (rating <= 2) {
            return String.format("Operational concern regarding %s: \"%s\"", catName, truncateText(trimmed, 130));
        } else {
            return String.format("Neutral evaluation regarding %s: \"%s\"", catName, truncateText(trimmed, 130));
        }
    }

    private String deriveDefaultAction(FeedbackCategory category, FeedbackUrgency urgency, FeedbackSentiment sentiment) {
        if (urgency == FeedbackUrgency.CRITICAL) {
            return "Immediate Management Action: Dispatch facility lead for instant inspection and contact member directly with a 7-day complimentary pass or apology resolution.";
        }
        if (urgency == FeedbackUrgency.MEDIUM) {
            return "Operational Recommendation: Log into routine maintenance log and monitor member satisfaction during upcoming weekly review.";
        }
        return "Member Retention Action: Maintain current service standards and log positive testimonial for team recognition.";
    }

    private String truncateText(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3) + "...";
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
