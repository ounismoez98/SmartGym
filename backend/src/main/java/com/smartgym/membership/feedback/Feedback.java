package com.smartgym.membership.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "keycloak_subject", nullable = false, length = 64)
    private String subject;

    @Column(name = "member_name", nullable = false, length = 200)
    private String memberName;

    @Column(nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeedbackCategory category;

    @Column(nullable = false, length = 4000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackSentiment sentiment;

    @Column(length = 2000)
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "feedback_themes", joinColumns = @JoinColumn(name = "feedback_id"))
    @Column(name = "theme", length = 100)
    private List<String> themes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackUrgency urgency = FeedbackUrgency.LOW;

    @Column(name = "action_recommendation", length = 2000)
    private String actionRecommendation;

    @Column(name = "confidence_score")
    private Double confidenceScore = 0.95;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Feedback() {}

    public Feedback(
            UUID id,
            String subject,
            String memberName,
            int rating,
            FeedbackCategory category,
            String comment,
            FeedbackSentiment sentiment,
            String summary,
            List<String> themes,
            FeedbackUrgency urgency,
            String actionRecommendation,
            Double confidenceScore,
            Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.subject = subject;
        this.memberName = memberName;
        this.rating = rating;
        this.category = category;
        this.comment = comment;
        this.sentiment = sentiment != null ? sentiment : FeedbackSentiment.NEUTRAL;
        this.summary = summary;
        this.themes = themes != null ? new ArrayList<>(themes) : new ArrayList<>();
        this.urgency = urgency != null ? urgency : FeedbackUrgency.LOW;
        this.actionRecommendation = actionRecommendation;
        this.confidenceScore = confidenceScore != null ? confidenceScore : 0.95;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Feedback(
            UUID id,
            String subject,
            String memberName,
            int rating,
            FeedbackCategory category,
            String comment,
            FeedbackSentiment sentiment,
            String summary,
            List<String> themes,
            Instant createdAt) {
        this(id, subject, memberName, rating, category, comment, sentiment, summary, themes,
                FeedbackUrgency.LOW, null, 0.95, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getMemberName() {
        return memberName;
    }

    public int getRating() {
        return rating;
    }

    public FeedbackCategory getCategory() {
        return category;
    }

    public String getComment() {
        return comment;
    }

    public FeedbackSentiment getSentiment() {
        return sentiment;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getThemes() {
        return themes;
    }

    public FeedbackUrgency getUrgency() {
        return urgency;
    }

    public String getActionRecommendation() {
        return actionRecommendation;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateAiAnalysis(
            FeedbackSentiment sentiment,
            String summary,
            List<String> themes,
            FeedbackUrgency urgency,
            String actionRecommendation,
            Double confidenceScore) {
        this.sentiment = sentiment != null ? sentiment : FeedbackSentiment.NEUTRAL;
        this.summary = summary;
        this.themes = themes != null ? new ArrayList<>(themes) : new ArrayList<>();
        this.urgency = urgency != null ? urgency : FeedbackUrgency.LOW;
        this.actionRecommendation = actionRecommendation;
        this.confidenceScore = confidenceScore != null ? confidenceScore : 0.95;
    }

    public void updateAiAnalysis(FeedbackSentiment sentiment, String summary, List<String> themes) {
        updateAiAnalysis(sentiment, summary, themes, FeedbackUrgency.LOW, null, 0.95);
    }
}
