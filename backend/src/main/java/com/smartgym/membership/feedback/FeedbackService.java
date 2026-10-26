package com.smartgym.membership.feedback;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.smartgym.membership.feedback.dto.AiAnalysisResult;
import com.smartgym.membership.feedback.dto.AiExecutiveReport;
import com.smartgym.membership.feedback.dto.FeedbackDashboardStats;
import com.smartgym.membership.feedback.dto.FeedbackResponse;
import com.smartgym.membership.feedback.dto.FeedbackSubmitRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final GroqAiService groqAiService;

    public FeedbackService(FeedbackRepository feedbackRepository, GroqAiService groqAiService) {
        this.feedbackRepository = feedbackRepository;
        this.groqAiService = groqAiService;
    }

    public FeedbackResponse submitFeedback(String subject, String memberName, FeedbackSubmitRequest request) {
        AiAnalysisResult analysis = groqAiService.analyzeFeedback(
                request.rating(),
                request.category(),
                request.comment()
        );

        Feedback feedback = new Feedback(
                UUID.randomUUID(),
                subject,
                (memberName != null && !memberName.isBlank()) ? memberName : "Gym Member",
                request.rating(),
                request.category(),
                request.comment().trim(),
                analysis.sentiment(),
                analysis.summary(),
                analysis.themes(),
                analysis.urgency(),
                analysis.actionRecommendation(),
                analysis.confidenceScore(),
                Instant.now()
        );

        Feedback saved = feedbackRepository.save(feedback);
        return FeedbackResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMyFeedback(String subject) {
        return feedbackRepository.findAllBySubjectOrderByCreatedAtDesc(subject)
                .stream()
                .map(FeedbackResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getAllFeedback(
            FeedbackCategory category,
            FeedbackSentiment sentiment,
            Integer rating,
            FeedbackUrgency urgency,
            String search) {
        List<Feedback> list = feedbackRepository.findWithFilters(category, sentiment, rating, urgency);

        if (search != null && !search.isBlank()) {
            String lowerSearch = search.trim().toLowerCase(Locale.ROOT);
            list = list.stream()
                    .filter(f -> (f.getComment() != null && f.getComment().toLowerCase(Locale.ROOT).contains(lowerSearch))
                            || (f.getMemberName() != null && f.getMemberName().toLowerCase(Locale.ROOT).contains(lowerSearch))
                            || (f.getSummary() != null && f.getSummary().toLowerCase(Locale.ROOT).contains(lowerSearch))
                            || (f.getActionRecommendation() != null && f.getActionRecommendation().toLowerCase(Locale.ROOT).contains(lowerSearch))
                            || (f.getThemes() != null && f.getThemes().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(lowerSearch))))
                    .toList();
        }

        return list.stream()
                .map(FeedbackResponse::fromEntity)
                .toList();
    }

    public FeedbackResponse reanalyzeFeedback(UUID id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found with ID: " + id));

        AiAnalysisResult analysis = groqAiService.analyzeFeedback(
                feedback.getRating(),
                feedback.getCategory(),
                feedback.getComment()
        );

        feedback.updateAiAnalysis(
                analysis.sentiment(),
                analysis.summary(),
                analysis.themes(),
                analysis.urgency(),
                analysis.actionRecommendation(),
                analysis.confidenceScore()
        );
        Feedback saved = feedbackRepository.save(feedback);
        return FeedbackResponse.fromEntity(saved);
    }

    public List<FeedbackResponse> reanalyzeAllFeedbacks() {
        List<Feedback> all = feedbackRepository.findAll();
        for (Feedback feedback : all) {
            AiAnalysisResult analysis = groqAiService.analyzeFeedback(
                    feedback.getRating(),
                    feedback.getCategory(),
                    feedback.getComment()
            );
            feedback.updateAiAnalysis(
                    analysis.sentiment(),
                    analysis.summary(),
                    analysis.themes(),
                    analysis.urgency(),
                    analysis.actionRecommendation(),
                    analysis.confidenceScore()
            );
        }
        feedbackRepository.saveAll(all);
        return all.stream().map(FeedbackResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public AiExecutiveReport getExecutiveReport() {
        List<Feedback> all = feedbackRepository.findAllByOrderByCreatedAtDesc();
        return groqAiService.generateExecutiveReport(all);
    }

    public void deleteFeedback(UUID id) {
        if (!feedbackRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found with ID: " + id);
        }
        feedbackRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public FeedbackDashboardStats getDashboardStats() {
        List<Feedback> all = feedbackRepository.findAllByOrderByCreatedAtDesc();
        long total = all.size();

        if (total == 0) {
            Map<Integer, Long> emptyRatings = Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L);
            List<FeedbackDashboardStats.CategoryStat> emptyCategories = Arrays.stream(FeedbackCategory.values())
                    .map(cat -> new FeedbackDashboardStats.CategoryStat(
                            cat.name(), cat.getDisplayName(), 0, 0.0, 0, 0, 0))
                    .toList();
            AiExecutiveReport emptyReport = groqAiService.generateExecutiveReport(List.of());
            return new FeedbackDashboardStats(
                    0, 0.0, 0, 0, 0, 0.0, 0.0, 0.0,
                    0, 0, 0,
                    emptyRatings, emptyCategories, List.of(), emptyReport);
        }

        double avgRating = BigDecimal.valueOf(all.stream().mapToInt(Feedback::getRating).average().orElse(0.0))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        long positive = all.stream().filter(f -> f.getSentiment() == FeedbackSentiment.POSITIVE).count();
        long neutral = all.stream().filter(f -> f.getSentiment() == FeedbackSentiment.NEUTRAL).count();
        long negative = all.stream().filter(f -> f.getSentiment() == FeedbackSentiment.NEGATIVE).count();

        double positivePct = BigDecimal.valueOf((positive * 100.0) / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
        double neutralPct = BigDecimal.valueOf((neutral * 100.0) / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
        double negativePct = BigDecimal.valueOf((negative * 100.0) / total).setScale(1, RoundingMode.HALF_UP).doubleValue();

        long critical = all.stream().filter(f -> f.getUrgency() == FeedbackUrgency.CRITICAL).count();
        long medium = all.stream().filter(f -> f.getUrgency() == FeedbackUrgency.MEDIUM).count();
        long low = all.stream().filter(f -> f.getUrgency() == FeedbackUrgency.LOW || f.getUrgency() == null).count();

        Map<Integer, Long> ratingDistribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int star = i;
            ratingDistribution.put(star, all.stream().filter(f -> f.getRating() == star).count());
        }

        List<FeedbackDashboardStats.CategoryStat> categoryStats = Arrays.stream(FeedbackCategory.values())
                .map(cat -> {
                    List<Feedback> catFeedbacks = all.stream().filter(f -> f.getCategory() == cat).toList();
                    long catCount = catFeedbacks.size();
                    double catAvg = catCount == 0 ? 0.0 : BigDecimal.valueOf(
                            catFeedbacks.stream().mapToInt(Feedback::getRating).average().orElse(0.0)
                    ).setScale(1, RoundingMode.HALF_UP).doubleValue();
                    long catPos = catFeedbacks.stream().filter(f -> f.getSentiment() == FeedbackSentiment.POSITIVE).count();
                    long catNeu = catFeedbacks.stream().filter(f -> f.getSentiment() == FeedbackSentiment.NEUTRAL).count();
                    long catNeg = catFeedbacks.stream().filter(f -> f.getSentiment() == FeedbackSentiment.NEGATIVE).count();
                    return new FeedbackDashboardStats.CategoryStat(
                            cat.name(), cat.getDisplayName(), catCount, catAvg, catPos, catNeu, catNeg);
                })
                .toList();

        Map<String, Long> themeFrequencies = new HashMap<>();
        for (Feedback f : all) {
            if (f.getThemes() != null) {
                for (String t : f.getThemes()) {
                    String clean = t.trim();
                    if (!clean.isBlank()) {
                        themeFrequencies.merge(clean, 1L, Long::sum);
                    }
                }
            }
        }

        List<FeedbackDashboardStats.ThemeStat> topThemes = themeFrequencies.entrySet().stream()
                .map(e -> new FeedbackDashboardStats.ThemeStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(FeedbackDashboardStats.ThemeStat::count).reversed()
                        .thenComparing(FeedbackDashboardStats.ThemeStat::theme))
                .limit(15)
                .toList();

        AiExecutiveReport executiveReport = groqAiService.generateExecutiveReport(all);

        return new FeedbackDashboardStats(
                total,
                avgRating,
                positive,
                neutral,
                negative,
                positivePct,
                neutralPct,
                negativePct,
                critical,
                medium,
                low,
                ratingDistribution,
                categoryStats,
                topThemes,
                executiveReport
        );
    }
}
