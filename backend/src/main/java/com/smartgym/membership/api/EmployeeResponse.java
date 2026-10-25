package com.smartgym.membership.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.smartgym.membership.profile.Employee;

public record EmployeeResponse(
        UUID id,
        String keycloakUserId,
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        LocalDate hireDate,
        Employee.Status status,
        Instant createdAt,
        Instant updatedAt) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getKeycloakUserId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getJobTitle(),
                employee.getHireDate(),
                employee.getStatus(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
