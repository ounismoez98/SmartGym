package com.smartgym.courses;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final CourseSessionRepository sessionRepository;
    private final CourseIdentityDisplayNames displayNames;

    public ReservationService(ReservationRepository reservationRepository,
                              CourseSessionRepository sessionRepository,
                              CourseIdentityDisplayNames displayNames) {
        this.reservationRepository = reservationRepository;
        this.sessionRepository = sessionRepository;
        this.displayNames = displayNames;
    }

    @Transactional(readOnly = true)
    public Page<Reservation> getReservationsForMember(String memberSubject, Pageable pageable) {
        return decorate(reservationRepository.findByMemberSubjectOrderByCreatedAtDesc(memberSubject, pageable));
    }

    @Transactional(readOnly = true)
    public Page<Reservation> listReservations(Pageable pageable) {
        return decorate(reservationRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    @Transactional(readOnly = true)
    public Page<Reservation> listReservationsForCoach(String coachSubject, Pageable pageable) {
        return decorate(reservationRepository.findByCoachSubjectOrderByCreatedAtDesc(coachSubject, pageable));
    }

    @Transactional(readOnly = true)
    public Reservation getReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Reservation not found."));
    }

    @Transactional
    public Reservation createReservation(String memberSubject, UUID sessionId) {
        if (memberSubject == null || memberSubject.isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Member subject is required.");
        }
        CourseSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found."));
        if (!"SCHEDULED".equalsIgnoreCase(session.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Only scheduled sessions can accept reservations.");
        }
        if (!session.getCourse().isActive()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "This course is not open for booking.");
        }
        ensureBookingPeriodOpen(session);
        Reservation existingReservation = reservationRepository
                .findByMemberSubjectAndSessionSessionId(memberSubject, sessionId)
                .orElse(null);
        if (existingReservation != null && "CONFIRMED".equalsIgnoreCase(existingReservation.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "A reservation for this member and session already exists.");
        }
        long confirmed = reservationRepository.countConfirmedBySession(sessionId);
        if (confirmed >= session.getMaxCapacity()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Session is at full capacity.");
        }
        if (existingReservation != null) {
            existingReservation.setStatus("CONFIRMED");
            return decorate(reservationRepository.save(existingReservation));
        }
        return decorate(reservationRepository.save(Reservation.create(memberSubject, session)));
    }

    @Transactional
    public Reservation cancelReservation(UUID reservationId, String memberSubject, boolean isAdmin) {
        Reservation reservation = getReservation(reservationId);
        boolean allowed = isAdmin || reservation.getMemberSubject().equals(memberSubject);
        if (!allowed) {
            throw new Module2Exception(HttpStatus.FORBIDDEN, "You are not allowed to cancel this reservation.");
        }
        if (!"CONFIRMED".equalsIgnoreCase(reservation.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Only confirmed reservations can be cancelled.");
        }
        reservation.setStatus("CANCELLED");
        return decorate(reservationRepository.save(reservation));
    }

    @Transactional
    public Reservation updateReservation(UUID reservationId, String memberSubject, boolean isAdmin,
                                         UUID sessionId) {
        Reservation reservation = getReservation(reservationId);
        if (!isAdmin && !reservation.getMemberSubject().equals(memberSubject)) {
            throw new Module2Exception(HttpStatus.FORBIDDEN, "You are not allowed to update this reservation.");
        }
        if (!"CONFIRMED".equalsIgnoreCase(reservation.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Only confirmed reservations can be updated.");
        }
        if (sessionId == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Session ID is required.");
        }
        CourseSession newSession = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found."));
        if (!"SCHEDULED".equalsIgnoreCase(newSession.getStatus())) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Only scheduled sessions can accept reservations.");
        }
        if (!newSession.getCourse().isActive()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "This course is not open for booking.");
        }
        ensureBookingPeriodOpen(newSession);
        if (reservation.getSession().getSessionId().equals(sessionId)) {
            return reservation;
        }
        if (reservationRepository.findByMemberSubjectAndSessionSessionId(reservation.getMemberSubject(), sessionId).isPresent()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "A reservation for this member and session already exists.");
        }
        if (reservationRepository.countConfirmedBySession(sessionId) >= newSession.getMaxCapacity()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Session is at full capacity.");
        }
        reservation.setSession(newSession);
        return decorate(reservationRepository.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<Reservation> listReservationsForSession(UUID sessionId) {
        return decorate(reservationRepository.findBySessionSessionIdOrderByCreatedAtDesc(sessionId, Pageable.unpaged()).getContent());
    }

    @Transactional(readOnly = true)
    public Page<Reservation> listReservationsForSession(UUID sessionId, Pageable pageable) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new Module2Exception(HttpStatus.NOT_FOUND, "Session not found.");
        }
        return decorate(reservationRepository.findBySessionSessionIdOrderByCreatedAtDesc(sessionId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<Reservation> listReservationsForSession(UUID sessionId, String coachSubject, Pageable pageable) {
        CourseSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Session not found."));
        if (!session.getCoachSubject().equals(coachSubject)) {
            throw new Module2Exception(HttpStatus.FORBIDDEN, "This session is not assigned to your account.");
        }
        return decorate(reservationRepository.findBySessionSessionIdOrderByCreatedAtDesc(sessionId, pageable));
    }

    private Reservation decorate(Reservation reservation) {
        displayNames.populateReservationUsernames(List.of(reservation));
        return reservation;
    }

    private Page<Reservation> decorate(Page<Reservation> reservations) {
        displayNames.populateReservationUsernames(reservations.getContent());
        return reservations;
    }

    private List<Reservation> decorate(List<Reservation> reservations) {
        displayNames.populateReservationUsernames(reservations);
        return reservations;
    }

    private void ensureBookingPeriodOpen(CourseSession session) {
        LocalDateTime cutoff = LocalDateTime.of(session.getSessionDate(), session.getStartTime()).minusHours(24);
        if (!LocalDateTime.now().isBefore(cutoff)) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Reservations close 24 hours before the session starts.");
        }
    }
}
