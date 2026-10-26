package com.smartgym.courses;

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
public class CourseSessionController {
    private final CourseSessionService sessionService;

    public CourseSessionController(CourseSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/sessions")
    public Page<CourseSession> listSessions(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "100") int size,
                                            @RequestParam(required = false) java.time.LocalDate date,
                                            @AuthenticationPrincipal Jwt jwt) {
        return sessionService.listSessions(PageRequest.of(page, size), date, jwt);
    }

    @GetMapping("/{courseId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','COACH')")
    public Page<CourseSession> listSessions(@PathVariable UUID courseId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @AuthenticationPrincipal Jwt jwt) {
        Pageable pageable = PageRequest.of(page, size);
        return sessionService.listSessionsByCourse(courseId, pageable, jwt);
    }

    @GetMapping("/sessions/{sessionId}")
    public CourseSession getSession(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.getSession(sessionId, jwt);
    }

    @PostMapping("/{courseId}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseSession createSession(@PathVariable UUID courseId,
                                      @RequestBody CourseSessionService.CreateSessionRequest request) {
        return sessionService.createSession(courseId, request);
    }

    @PutMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseSession updateSession(@PathVariable UUID sessionId,
                                      @RequestBody CourseSessionService.UpdateSessionRequest request) {
        return sessionService.updateSession(sessionId, request);
    }

    @PutMapping("/sessions/{sessionId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseSession cancelSession(@PathVariable UUID sessionId) {
        sessionService.cancelSession(sessionId);
        return sessionService.getSession(sessionId);
    }
}
