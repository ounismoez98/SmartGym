package com.smartgym.training.dto;

import java.util.List;

public record WorkoutLogPageResponse(
        List<WorkoutLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
