package com.smartgym.membership.feedback;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findAllBySubjectOrderByCreatedAtDesc(String subject);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    @Query("SELECT f FROM Feedback f WHERE " +
           "(:category IS NULL OR f.category = :category) AND " +
           "(:sentiment IS NULL OR f.sentiment = :sentiment) AND " +
           "(:rating IS NULL OR f.rating = :rating) " +
           "ORDER BY f.createdAt DESC")
    List<Feedback> findWithFilters(
            @Param("category") FeedbackCategory category,
            @Param("sentiment") FeedbackSentiment sentiment,
            @Param("rating") Integer rating);
}
