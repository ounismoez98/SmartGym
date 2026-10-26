package com.smartgym.courses;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CourseSessionRepository extends JpaRepository<CourseSession, UUID> {
    @Override
    @EntityGraph(attributePaths = "course")
    Optional<CourseSession> findById(UUID sessionId);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findByCourseCourseIdOrderBySessionDateAscStartTimeAsc(UUID courseId, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findByCoachSubjectOrderBySessionDateAscStartTimeAsc(String coachSubject, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findByCoachSubjectAndSessionDateOrderByStartTimeAsc(String coachSubject, LocalDate sessionDate, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findBySessionDateOrderByStartTimeAsc(LocalDate sessionDate, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findBySessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
            LocalDate sessionDate, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findBySessionDateAndStatusAndCourseActiveTrueOrderByStartTimeAsc(
            LocalDate sessionDate, String status, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findBySessionDateGreaterThanEqualAndStatusAndCourseActiveTrueOrderBySessionDateAscStartTimeAsc(
            LocalDate sessionDate, String status, Pageable pageable);

    @EntityGraph(attributePaths = "course")
    Page<CourseSession> findByCourseCourseIdAndCoachSubjectOrderBySessionDateAscStartTimeAsc(
            UUID courseId, String coachSubject, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "course")
    @Query("select s from CourseSession s where s.sessionId = :sessionId")
    Optional<CourseSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Query("select s from CourseSession s where s.status = 'SCHEDULED' and s.sessionDate = :date and s.roomName = :roomName and " +
            "(:excludeSessionId is null or s.sessionId <> :excludeSessionId) and " +
            "((s.startTime < :endTime and s.endTime > :startTime))")
    List<CourseSession> findConflictingRoomSessions(@Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime,
                                                  @Param("endTime") LocalTime endTime,
                                                  @Param("roomName") String roomName,
                                                  @Param("excludeSessionId") UUID excludeSessionId);

    @Query("select s from CourseSession s where s.status = 'SCHEDULED' and s.sessionDate = :date and s.coachSubject = :coachSubject and " +
            "(:excludeSessionId is null or s.sessionId <> :excludeSessionId) and " +
            "((s.startTime < :endTime and s.endTime > :startTime))")
    List<CourseSession> findConflictingCoachSessions(@Param("date") LocalDate date,
                                                    @Param("startTime") LocalTime startTime,
                                                    @Param("endTime") LocalTime endTime,
                                                    @Param("coachSubject") String coachSubject,
                                                    @Param("excludeSessionId") UUID excludeSessionId);

    @Query("select s from CourseSession s where s.status = 'SCHEDULED' and s.sessionDate >= :fromDate and s.sessionDate <= :toDate")
    List<CourseSession> findScheduledSessionsInRange(@Param("fromDate") LocalDate fromDate,
                                                    @Param("toDate") LocalDate toDate);
}
