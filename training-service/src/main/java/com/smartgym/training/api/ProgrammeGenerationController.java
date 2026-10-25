package com.smartgym.training.api;

import java.util.UUID;

import com.smartgym.training.application.AiJobService;
import com.smartgym.training.dto.AiJobResponse;
import com.smartgym.training.dto.ProgrammeGenerationAccepted;
import com.smartgym.training.dto.ProgrammeGenerationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/training")
public class ProgrammeGenerationController {
    private final AiJobService service;

    public ProgrammeGenerationController(AiJobService service) {
        this.service = service;
    }

    @PostMapping("/programme-generations")
    @PreAuthorize("hasRole('COACH')")
    public ResponseEntity<ProgrammeGenerationAccepted> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProgrammeGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(jwt, request));
    }

    @GetMapping("/ai-jobs/{id}")
    @PreAuthorize("hasRole('COACH')")
    public AiJobResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(jwt, id);
    }
}
