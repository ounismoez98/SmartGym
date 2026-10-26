package com.smartgym.membership.feedback;

import java.util.List;
import java.util.UUID;

import com.smartgym.membership.feedback.dto.AiExecutiveReport;
import com.smartgym.membership.feedback.dto.FeedbackDashboardStats;
import com.smartgym.membership.feedback.dto.FeedbackResponse;
import com.smartgym.membership.feedback.dto.FeedbackSubmitRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/feedback", "/api/feedback/"})
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submitFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FeedbackSubmitRequest request) {
        return feedbackService.submitFeedback(jwt.getSubject(), displayName(jwt), request);
    }

    @GetMapping("/my")
    public List<FeedbackResponse> getMyFeedback(@AuthenticationPrincipal Jwt jwt) {
        return feedbackService.getMyFeedback(jwt.getSubject());
    }

    @GetMapping("/admin/all")
    public List<FeedbackResponse> getAllFeedback(
            @RequestParam(required = false) FeedbackCategory category,
            @RequestParam(required = false) FeedbackSentiment sentiment,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) FeedbackUrgency urgency,
            @RequestParam(required = false) String search) {
        return feedbackService.getAllFeedback(category, sentiment, rating, urgency, search);
    }

    @GetMapping("/admin/stats")
    public FeedbackDashboardStats getDashboardStats() {
        return feedbackService.getDashboardStats();
    }

    @GetMapping("/admin/executive-report")
    public AiExecutiveReport getExecutiveReport() {
        return feedbackService.getExecutiveReport();
    }

    @PostMapping("/admin/reanalyze-all")
    public List<FeedbackResponse> reanalyzeAllFeedback() {
        return feedbackService.reanalyzeAllFeedbacks();
    }

    @PostMapping("/admin/{id}/reanalyze")
    public FeedbackResponse reanalyzeFeedback(@PathVariable UUID id) {
        return feedbackService.reanalyzeFeedback(id);
    }

    @DeleteMapping("/admin/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@PathVariable UUID id) {
        feedbackService.deleteFeedback(id);
    }

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String username = jwt.getClaimAsString("preferred_username");
        return username == null || username.isBlank() ? jwt.getSubject() : username;
    }
}
