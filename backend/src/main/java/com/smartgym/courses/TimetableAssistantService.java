package com.smartgym.courses;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimetableAssistantService {
    private static final List<String> ROOM_CANDIDATES = List.of("Room A", "Room B", "Room C", "Room D", "Room E");

    private final CourseRepository courseRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseIdentityDisplayNames displayNames;

    public TimetableAssistantService(CourseRepository courseRepository,
                                    CourseSessionRepository sessionRepository,
                                    CourseIdentityDisplayNames displayNames) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.displayNames = displayNames;
    }

    @Transactional(readOnly = true)
    public TimetableAssistantResponse generateRecommendations(LocalDate fromDate, LocalDate toDate, int limit) {
        return generateRecommendations(fromDate, toDate, limit, null);
    }

    @Transactional(readOnly = true)
    public TimetableAssistantResponse generateRecommendations(LocalDate fromDate, LocalDate toDate, int limit,
                                                               String coachSubject) {
        LocalDate effectiveFrom = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate effectiveTo = toDate == null ? effectiveFrom.plusDays(7) : toDate;
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "To-date must be on or after the from-date.");
        }

        int effectiveLimit = limit > 0 ? limit : 3;
        String assignedCoach = coachSubject == null || coachSubject.isBlank() ? null : coachSubject.trim();
        List<Course> courses = courseRepository.findAllByOrderByCreatedAtDesc(Pageable.unpaged()).getContent();
        List<CourseSession> existingSessions = sessionRepository.findScheduledSessionsInRange(effectiveFrom, effectiveTo);

        List<RecommendedSlot> recommendations = new ArrayList<>();
        for (Course course : courses) {
            if (course == null || !course.isActive()) {
                continue;
            }
            for (LocalDate currentDate = effectiveFrom; !currentDate.isAfter(effectiveTo); currentDate = currentDate.plusDays(1)) {
                for (String roomName : ROOM_CANDIDATES) {
                    LocalTime slotStart = LocalTime.of(8, 0);
                    while (!slotStart.plusMinutes(resolveDurationMinutes(course)).isAfter(LocalTime.of(20, 0))) {
                        LocalTime slotEnd = slotStart.plusMinutes(resolveDurationMinutes(course));
                        if (hasRoomConflict(existingSessions, currentDate, roomName, slotStart, slotEnd)
                                || (assignedCoach != null && hasCoachConflict(existingSessions, currentDate,
                                assignedCoach, slotStart, slotEnd))) {
                            slotStart = slotStart.plusMinutes(30);
                            continue;
                        }
                        int score = scoreSlot(currentDate, slotStart, roomName, existingSessions);
                        recommendations.add(new RecommendedSlot(
                                course.getCourseId(),
                                course.getName(),
                                currentDate,
                                slotStart,
                                slotEnd,
                                roomName,
                                assignedCoach == null ? "Coach assignment required" : assignedCoach,
                                displayNames.usernameFor(assignedCoach),
                                score,
                                recommendationReason(currentDate, slotStart, roomName, score)
                        ));
                        slotStart = slotStart.plusMinutes(30);
                    }
                }
            }
        }

        recommendations = recommendations.stream()
                .sorted(Comparator.comparingInt(RecommendedSlot::score).reversed()
                        .thenComparing(RecommendedSlot::date)
                        .thenComparing(RecommendedSlot::startTime)
                        .thenComparing(RecommendedSlot::roomName))
                .limit(effectiveLimit)
                .toList();

        return new TimetableAssistantResponse(
                recommendations,
                recommendations.isEmpty()
                        ? noRecommendationsSummary(courses)
                        : "Deterministic timetable suggestions generated as drafts and checked against scheduled room conflicts. Review and approve each session before it is created.");
    }

    private String noRecommendationsSummary(List<Course> courses) {
        List<Course> activeCourses = courses.stream()
                .filter(course -> course != null && course.isActive())
                .toList();
        if (activeCourses.isEmpty()) {
            return "No active courses are available. Add or activate a course to generate timetable drafts.";
        }
        return "No viable timetable slots were found. Check coach/room conflicts and the selected date window.";
    }

    private boolean hasRoomConflict(List<CourseSession> sessions, LocalDate date, String roomName,
                                   LocalTime startTime, LocalTime endTime) {
        for (CourseSession session : sessions) {
            if (!roomName.equals(session.getRoomName())) {
                continue;
            }
            if (!date.equals(session.getSessionDate())) {
                continue;
            }
            if (session.getStatus() == null || !"SCHEDULED".equalsIgnoreCase(session.getStatus())) {
                continue;
            }
            if (startTime.isBefore(session.getEndTime()) && endTime.isAfter(session.getStartTime())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCoachConflict(List<CourseSession> sessions, LocalDate date, String coachSubject,
                                     LocalTime startTime, LocalTime endTime) {
        for (CourseSession session : sessions) {
            if (!coachSubject.equals(session.getCoachSubject()) || !date.equals(session.getSessionDate())) {
                continue;
            }
            if (session.getStatus() == null || !"SCHEDULED".equalsIgnoreCase(session.getStatus())) {
                continue;
            }
            if (startTime.isBefore(session.getEndTime()) && endTime.isAfter(session.getStartTime())) {
                return true;
            }
        }
        return false;
    }

    private int resolveDurationMinutes(Course course) {
        Integer durationMinutes = course.getDurationMinutes();
        return durationMinutes != null && durationMinutes > 0 ? durationMinutes : 60;
    }

    private int scoreSlot(LocalDate date, LocalTime startTime, String roomName, List<CourseSession> existingSessions) {
        int score = 100;
        int hourValue = startTime.getHour();
        if (hourValue >= 9 && hourValue <= 11) {
            score += 20;
        } else if (hourValue >= 12 && hourValue <= 14) {
            score += 5;
        } else if (hourValue >= 17 && hourValue <= 19) {
            score += 15;
        }

        long roomLoad = existingSessions.stream()
                .filter(session -> roomName.equals(session.getRoomName())
                        && date.equals(session.getSessionDate())
                        && "SCHEDULED".equalsIgnoreCase(session.getStatus()))
                .count();
        score -= roomLoad * 8;

        if (hourValue >= 18) {
            score -= 10;
        }

        return score;
    }

    private String recommendationReason(LocalDate date, LocalTime startTime, String roomName, int score) {
        String timeLabel = startTime.getHour() < 12 ? "Morning slot" : (startTime.getHour() < 17 ? "Afternoon slot" : "Evening slot");
        return score >= 115 ? "Best fit: low crowding and strong demand window for " + roomName + " on " + date + "."
                : timeLabel + " in " + roomName + " is available and consistent with expected class timing.";
    }

    public record TimetableAssistantResponse(List<RecommendedSlot> recommendations, String summary) {
    }

    public record RecommendedSlot(UUID courseId,
                                 String courseName,
                                 LocalDate date,
                                 LocalTime startTime,
                                 LocalTime endTime,
                                 String roomName,
                                 String coachSubject,
                                 String coachUsername,
                                 int score,
                                 String reason) {
    }
}
