package com.smartgym.courses;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/reservations/me")
    @PreAuthorize("hasRole('MEMBER')")
    public Page<Reservation> getMyReservations(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reservationService.getReservationsForMember(jwt.getSubject(), pageable);
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyRole('ADMIN','COACH')")
    public Page<Reservation> listReservations(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return isAdmin(jwt) ? reservationService.listReservations(pageable)
                : reservationService.listReservationsForCoach(jwt.getSubject(), pageable);
    }

    @GetMapping("/sessions/{sessionId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN','COACH')")
    public Page<Reservation> getSessionReservations(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID sessionId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return isAdmin(jwt) ? reservationService.listReservationsForSession(sessionId, pageable)
                : reservationService.listReservationsForSession(sessionId, jwt.getSubject(), pageable);
    }

    @PostMapping("/sessions/{sessionId}/reservations")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation createReservation(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID sessionId) {
        return reservationService.createReservation(jwt.getSubject(), sessionId);
    }

    @PutMapping("/reservations/{reservationId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public Reservation cancelReservation(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID reservationId) {
        return reservationService.cancelReservation(reservationId, jwt.getSubject(), isAdmin(jwt));
    }

    @PutMapping("/reservations/{reservationId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER')")
    public Reservation updateReservation(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID reservationId,
                                         @RequestBody UpdateReservationRequest request) {
        return reservationService.updateReservation(reservationId, jwt.getSubject(), isAdmin(jwt),
                request == null ? null : request.sessionId());
    }

    private boolean isAdmin(Jwt jwt) {
        Object realmAccess = jwt.getClaims().getOrDefault("realm_access", Map.of());
        return realmAccess instanceof Map<?, ?> map
                && map.get("roles") instanceof java.util.Collection<?> roles
                && roles.contains("ADMIN");
    }

    public record UpdateReservationRequest(UUID sessionId) {
    }
}
