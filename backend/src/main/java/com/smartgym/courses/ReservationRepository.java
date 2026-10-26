package com.smartgym.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Override
    @EntityGraph(attributePaths = {"session", "session.course"})
    Optional<Reservation> findById(UUID reservationId);

    @EntityGraph(attributePaths = {"session", "session.course"})
    Optional<Reservation> findByMemberSubjectAndSessionSessionId(String memberSubject, UUID sessionId);

    @EntityGraph(attributePaths = {"session", "session.course"})
    Page<Reservation> findByMemberSubjectOrderByCreatedAtDesc(String memberSubject, Pageable pageable);

    @EntityGraph(attributePaths = {"session", "session.course"})
    Page<Reservation> findBySessionSessionIdOrderByCreatedAtDesc(UUID sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"session", "session.course"})
    @Query("select r from Reservation r where r.session.coachSubject = :coachSubject order by r.createdAt desc")
    Page<Reservation> findByCoachSubjectOrderByCreatedAtDesc(
            @Param("coachSubject") String coachSubject, Pageable pageable);

    @EntityGraph(attributePaths = {"session", "session.course"})
    Page<Reservation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"session", "session.course"})
    @Query("select r from Reservation r where r.session.sessionId = :sessionId and r.status = 'CONFIRMED'")
    List<Reservation> findConfirmedBySession(@Param("sessionId") UUID sessionId);

    @Query("select count(r) from Reservation r where r.session.sessionId = :sessionId and r.status = 'CONFIRMED'")
    long countConfirmedBySession(@Param("sessionId") UUID sessionId);
}
