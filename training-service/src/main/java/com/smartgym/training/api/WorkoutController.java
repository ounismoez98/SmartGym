package com.smartgym.training.api;

import java.util.UUID;

import com.smartgym.training.application.WorkoutService;
import com.smartgym.training.dto.ProgressResponse;
import com.smartgym.training.dto.WorkoutLogPageResponse;
import com.smartgym.training.dto.WorkoutLogPatchRequest;
import com.smartgym.training.dto.WorkoutLogRequest;
import com.smartgym.training.dto.WorkoutLogResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/training")
public class WorkoutController {
    private final WorkoutService service;

    public WorkoutController(WorkoutService service) {
        this.service = service;
    }

    @PostMapping("/workout-logs")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<WorkoutLogResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(jwt, request));
    }

    @GetMapping("/workout-logs")
    @PreAuthorize("hasAnyRole('MEMBER', 'COACH')")
    public WorkoutLogPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(jwt, memberId, page, size);
    }

    @GetMapping("/workout-logs/{id}")
    @PreAuthorize("hasAnyRole('MEMBER', 'COACH')")
    public WorkoutLogResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(jwt, id);
    }

    @PatchMapping("/workout-logs/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public WorkoutLogResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody WorkoutLogPatchRequest request) {
        return service.update(jwt, id, request);
    }

    @DeleteMapping("/workout-logs/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(jwt, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/progress")
    @PreAuthorize("hasRole('MEMBER')")
    public ProgressResponse myProgress(@AuthenticationPrincipal Jwt jwt) {
        return service.myProgress(jwt);
    }

    @GetMapping("/members/{memberId}/progress")
    @PreAuthorize("hasRole('COACH')")
    public ProgressResponse memberProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID memberId) {
        return service.memberProgress(jwt, memberId);
    }
}
