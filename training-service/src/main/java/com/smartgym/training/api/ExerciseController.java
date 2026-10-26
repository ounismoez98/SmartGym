package com.smartgym.training.api;

import java.net.URI;
import java.util.UUID;

import com.smartgym.training.application.ExerciseService;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.MuscleGroup;
import com.smartgym.training.dto.ExercisePageResponse;
import com.smartgym.training.dto.ExercisePatchRequest;
import com.smartgym.training.dto.ExerciseRequest;
import com.smartgym.training.dto.ExerciseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/training/exercises")
public class ExerciseController {
    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public ExercisePageResponse list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) MuscleGroup muscleGroup,
            @RequestParam(required = false) ExerciseDifficulty difficulty,
            @RequestParam(required = false) EquipmentCategory equipmentCategory,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return service.list(name, muscleGroup, difficulty, equipmentCategory, page, size);
    }

    @GetMapping("/{id}")
    public ExerciseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COACH', 'ADMIN')")
    public ResponseEntity<ExerciseResponse> create(@Valid @RequestBody ExerciseRequest request) {
        ExerciseResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COACH', 'ADMIN')")
    public ExerciseResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ExercisePatchRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('COACH', 'ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        service.archive(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
