package com.smartgym.membership.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record EmployeePageResponse(
        List<EmployeeResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static EmployeePageResponse from(Page<EmployeeResponse> page) {
        return new EmployeePageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
