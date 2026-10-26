package com.smartgym.courses;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseSessionService {
    private final CourseSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final ReservationRepository reservationRepository;
    private final CourseIdentityDisplayNames displayNames;

    public CourseSessionService(CourseSessionRepository sessionRepository,
                               CourseRepository courseRepository,
                               CourseService courseService,
                               ReservationRepository reservationRepository,
                               CourseIdentityDisplayNames displayNames) {
        this.sessionRepository = sessionRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.reservationRepository = reservationRepository;
        this.displayNames = displayNames;
    }

    @Transactional(readOnly = true)
    public CourseSession getSession(UUID sessionId) {
        return decorate(sessionRepository.findById(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found.")));
    }

    @Transactional(readOnly = true)
    public CourseSession getSession(UUID sessionId, Jwt jwt) {
        CourseSession session = getSession(sessionId);
        if (hasRole(jwt, "COACH") && !hasRole(jwt, "ADMIN")
                && !session.getCoachSubject().equals(jwt.getSubject())) {
            throw new Module2Exception(HttpStatus.FORBIDDEN, "This session is not assigned to your account.");
        }
        if (hasRole(jwt, "MEMBER") && !hasRole(jwt, "ADMIN") && !hasRole(jwt, "COACH")
                && (!"SCHEDULED".equalsIgnoreCase(session.getStatus()) || !session.getCourse().isActive())) {
            throw new Module2Exception(HttpStatus.NOT_FOUND, "Session not found.");
        }
        return session;
    }

    @Transactional(readOnly = true)
    public Page<CourseSession> listSessionsByCourse(UUID courseId, Pageable pageable) {
        if (!courseRepository.existsById(courseId)) {
            throw new Module2Exception(HttpStatus.NOT_FOUND, "Course not found.");
        }
        return decorate(sessionRepository.findByCourseCourseIdOrderBySessionDateAscStartTimeAsc(courseId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<CourseSession> listSessions(Pageable pageable) {
        return decorate(sessionRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Page<CourseSession> listSessions(Pageable pageable, LocalDate date, Jwt jwt) {
        Page<CourseSession> sessions;
        if (hasRole(jwt, "ADMIN")) {
            sessions = date == null
                    ? sessionRepository.findBySessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(
                            LocalDate.now(), pageable)
                    : sessionRepository.findBySessionDateOrderByStartTimeAsc(date, pageable);
        } else if (hasRole(jwt, "COACH")) {
            sessions = date == null
                    ? sessionRepository.findByCoachSubjectOrderBySessionDateAscStartTimeAsc(jwt.getSubject(), pageable)
                    : sessionRepository.findByCoachSubjectAndSessionDateOrderByStartTimeAsc(jwt.getSubject(), date, pageable);
        } else {
            sessions = date == null
                    ? sessionRepository.findBySessionDateGreaterThanEqualAndStatusAndCourseActiveTrueOrderBySessionDateAscStartTimeAsc(
                            LocalDate.now(), "SCHEDULED", pageable)
                    : sessionRepository.findBySessionDateAndStatusAndCourseActiveTrueOrderByStartTimeAsc(
                            date, "SCHEDULED", pageable);
        }
        return decorate(sessions);
    }

    @Transactional(readOnly = true)
    public Page<CourseSession> listSessionsByCourse(UUID courseId, Pageable pageable, Jwt jwt) {
        if (!courseRepository.existsById(courseId)) {
            throw new Module2Exception(HttpStatus.NOT_FOUND, "Course not found.");
        }
        Page<CourseSession> sessions = hasRole(jwt, "ADMIN")
                ? sessionRepository.findByCourseCourseIdOrderBySessionDateAscStartTimeAsc(courseId, pageable)
                : sessionRepository.findByCourseCourseIdAndCoachSubjectOrderBySessionDateAscStartTimeAsc(
                        courseId, jwt.getSubject(), pageable);
        return decorate(sessions);
    }

    @Transactional
    public CourseSession createSession(UUID courseId, CreateSessionRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Course not found."));
        return scheduleSession(course, request);
    }

    @Transactional
    public CourseSession createCourseAndSession(CreateCourseAndSessionRequest request) {
        if (request == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Course and session payload is required.");
        }
        Course course = Course.create(
                request.courseName(),
                request.courseDescription(),
                request.difficultyLevel(),
                request.durationMinutes(),
                request.requiredEquipment());
        course.setActive(request.active() == null || request.active());
        Course offering = courseService.getOrCreateCourse(course);
        CreateSessionRequest sessionRequest = new CreateSessionRequest(
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                request.coachSubject(),
                request.roomName(),
                request.maxCapacity());
        return scheduleSession(offering, sessionRequest);
    }

    private CourseSession scheduleSession(Course course, CreateSessionRequest request) {
        if (!course.isActive()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Inactive courses cannot be scheduled.");
        }
        validateSessionRequest(course, request);
        ensureNoCoachConflict(request.sessionDate(), request.startTime(), request.endTime(), request.coachSubject(), null);
        ensureNoRoomConflict(request.sessionDate(), request.startTime(), request.endTime(), request.roomName(), null);
        CourseSession session = CourseSession.create(
                course,
                request.sessionDate(),
                request.startTime(),
                request.endTime(),
                request.coachSubject(),
                request.roomName(),
                request.maxCapacity());
        return decorate(sessionRepository.save(session));
    }

    @Transactional
    public CourseSession updateSession(UUID sessionId, UpdateSessionRequest request) {
        if (request == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session update payload is required.");
        }
        CourseSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found."));
        if (request.sessionDate() != null) {
            session.setSessionDate(request.sessionDate());
        }
        if (request.startTime() != null) {
            session.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            session.setEndTime(request.endTime());
        }
        if (request.coachSubject() != null && !request.coachSubject().isBlank()) {
            session.setCoachSubject(request.coachSubject());
        }
        if (request.roomName() != null && !request.roomName().isBlank()) {
            session.setRoomName(request.roomName());
        }
        if (request.maxCapacity() != null) {
            session.setMaxCapacity(request.maxCapacity());
        }
        if (request.status() != null && !request.status().isBlank()) {
            if (!session.getStatus().equalsIgnoreCase(request.status().trim())) {
                throw new Module2Exception(HttpStatus.BAD_REQUEST, "Use the session cancellation operation to change session status.");
            }
        }
        validateSession(session);
        ensureNoCoachConflict(session.getSessionDate(), session.getStartTime(), session.getEndTime(), session.getCoachSubject(), sessionId);
        ensureNoRoomConflict(session.getSessionDate(), session.getStartTime(), session.getEndTime(), session.getRoomName(), sessionId);
        if (session.getMaxCapacity() < reservationRepository.countConfirmedBySession(sessionId)) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Capacity cannot be lower than the number of confirmed reservations.");
        }
        return decorate(sessionRepository.save(session));
    }

    @Transactional
    public void cancelSession(UUID sessionId) {
        CourseSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found."));
        if (!"SCHEDULED".equalsIgnoreCase(session.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Only scheduled sessions can be cancelled.");
        }
        session.setStatus("CANCELLED");
        sessionRepository.save(session);
        List<Reservation> confirmedReservations = reservationRepository.findConfirmedBySession(sessionId);
        confirmedReservations.forEach(reservation -> reservation.setStatus("CANCELLED"));
        reservationRepository.saveAll(confirmedReservations);
    }

    private void validateSessionRequest(Course course, CreateSessionRequest request) {
        if (request == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session payload is required.");
        }
        if (request.sessionDate() == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session date is required.");
        }
        if (request.sessionDate().isBefore(LocalDate.now())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session date cannot be in the past.");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Start and end times are required.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
        if (!LocalDateTime.of(request.sessionDate(), request.startTime()).isAfter(LocalDateTime.now())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session start time must be in the future.");
        }
        if (request.coachSubject() == null || request.coachSubject().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Coach subject is required.");
        }
        if (request.roomName() == null || request.roomName().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Room name is required.");
        }
        if (request.maxCapacity() == null || request.maxCapacity() <= 0) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session capacity must be greater than zero.");
        }
        if (request.maxCapacity() > 200) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session capacity cannot exceed 200.");
        }
        if (course.getDurationMinutes() != null && request.startTime() != null && request.endTime() != null) {
            long durationMinutes = java.time.Duration.between(request.startTime(), request.endTime()).toMinutes();
            if (durationMinutes > course.getDurationMinutes() + 30) {
                throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session duration exceeds the course's expected length.");
            }
        }
    }

    private void validateSession(CourseSession session) {
        if (session.getSessionDate() == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session date is required.");
        }
        if (session.getSessionDate().isBefore(LocalDate.now())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session date cannot be in the past.");
        }
        if (session.getStartTime() == null || session.getEndTime() == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Start and end times are required.");
        }
        if (!session.getEndTime().isAfter(session.getStartTime())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        }
        if (!LocalDateTime.of(session.getSessionDate(), session.getStartTime()).isAfter(LocalDateTime.now())) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session start time must be in the future.");
        }
        if (session.getCoachSubject() == null || session.getCoachSubject().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Coach subject is required.");
        }
        if (session.getRoomName() == null || session.getRoomName().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Room name is required.");
        }
        if (session.getMaxCapacity() == null || session.getMaxCapacity() <= 0) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session capacity must be greater than zero.");
        }
    }

    private void ensureNoCoachConflict(LocalDate date, LocalTime startTime, LocalTime endTime, String coachSubject,
                                      UUID excludeSessionId) {
        List<CourseSession> conflicts = sessionRepository.findConflictingCoachSessions(
                date, startTime, endTime, coachSubject, excludeSessionId);
        if (!conflicts.isEmpty()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Coach is already assigned to another session at this time.");
        }
    }

    private void ensureNoRoomConflict(LocalDate date, LocalTime startTime, LocalTime endTime, String roomName,
                                      UUID excludeSessionId) {
        List<CourseSession> conflicts = sessionRepository.findConflictingRoomSessions(
                date, startTime, endTime, roomName, excludeSessionId);
        if (!conflicts.isEmpty()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Room is not available for the requested time slot.");
        }
    }

    private Page<CourseSession> decorate(Page<CourseSession> sessions) {
        displayNames.populateCoachUsernames(sessions.getContent());
        sessions.getContent().forEach(this::withAvailability);
        return sessions;
    }

    private CourseSession decorate(CourseSession session) {
        displayNames.populateCoachUsernames(List.of(session));
        return withAvailability(session);
    }

    private CourseSession withAvailability(CourseSession session) {
        long confirmed = reservationRepository.countConfirmedBySession(session.getSessionId());
        session.setRemainingPlaces(Math.max(0, session.getMaxCapacity() - (int) confirmed));
        LocalDateTime bookingCutoff = LocalDateTime.of(session.getSessionDate(), session.getStartTime()).minusHours(24);
        session.setBookingOpen("SCHEDULED".equalsIgnoreCase(session.getStatus())
                && LocalDateTime.now().isBefore(bookingCutoff)
                && session.getCourse().isActive()
                && confirmed < session.getMaxCapacity());
        return session;
    }

    private boolean hasRole(Jwt jwt, String role) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        return realmAccess instanceof java.util.Map<?, ?> map
                && map.get("roles") instanceof java.util.Collection<?> roles
                && roles.contains(role);
    }

    public record CreateSessionRequest(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String coachSubject,
            String roomName,
            Integer maxCapacity) {
    }

    public record UpdateSessionRequest(
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String coachSubject,
            String roomName,
            Integer maxCapacity,
            String status) {
    }

    public record CreateCourseAndSessionRequest(
            String courseName,
            String courseDescription,
            String difficultyLevel,
            Integer durationMinutes,
            String requiredEquipment,
            Boolean active,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String coachSubject,
            String roomName,
            Integer maxCapacity) {
    }
}
