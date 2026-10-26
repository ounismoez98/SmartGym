package com.smartgym.training.api;

import java.util.List;
import java.util.UUID;

import com.smartgym.training.application.ProgrammeService;
import com.smartgym.training.domain.ProgrammeStatus;
import com.smartgym.training.dto.ProgrammeItemChangeRequest;
import com.smartgym.training.dto.ProgrammeOrderRequest;
import com.smartgym.training.dto.ProgrammeRequest;
import com.smartgym.training.dto.ProgrammeResponse;
import com.smartgym.training.dto.ProgrammeReviewRequest;
import com.smartgym.training.dto.ProgrammeSessionChangeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training/programmes")
public class ProgrammeController {
    private final ProgrammeService service;

    public ProgrammeController(ProgrammeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<ProgrammeResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProgrammeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(jwt, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COACH', 'MEMBER')")
    public List<ProgrammeResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ProgrammeStatus status) {
        return service.list(jwt, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COACH', 'MEMBER')")
    public ProgrammeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(jwt, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProgrammeRequest request) {
        return service.update(jwt, id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.submit(jwt, id);
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProgrammeReviewRequest request) {
        return service.review(jwt, id, request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.publish(jwt, id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.archive(jwt, id);
    }

    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<ProgrammeResponse> createRevision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRevision(jwt, id));
    }

    @PostMapping("/{id}/sessions")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse addSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProgrammeSessionChangeRequest request) {
        return service.addSession(jwt, id, request);
    }

    @PatchMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse updateSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ProgrammeSessionChangeRequest request) {
        return service.updateSession(jwt, id, sessionId, request);
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @RequestParam long version) {
        service.deleteSession(jwt, id, sessionId, version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sessions/order")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse reorderSessions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ProgrammeOrderRequest request) {
        return service.reorderSessions(jwt, id, request);
    }

    @PostMapping("/{id}/sessions/{sessionId}/items")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse addItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ProgrammeItemChangeRequest request) {
        return service.addItem(jwt, id, sessionId, request);
    }

    @PatchMapping("/{id}/sessions/{sessionId}/items/{itemId}")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse updateItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ProgrammeItemChangeRequest request) {
        return service.updateItem(jwt, id, sessionId, itemId, request);
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/items/{itemId}")
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @PathVariable UUID itemId,
            @RequestParam long version) {
        service.deleteItem(jwt, id, sessionId, itemId, version);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sessions/{sessionId}/items/order")
    @PreAuthorize("hasRole('COACH')")
    public ProgrammeResponse reorderItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ProgrammeOrderRequest request) {
        return service.reorderItems(jwt, id, sessionId, request);
    }
}
