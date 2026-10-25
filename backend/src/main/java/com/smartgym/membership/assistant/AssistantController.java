package com.smartgym.membership.assistant;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/membership/assistant")
@PreAuthorize("hasRole('ADMIN')")
public class AssistantController {
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/explain")
    public AssistantTextResponse explain(@Valid @RequestBody AssistantExplainRequest request) {
        return assistantService.explain(request);
    }

    @GetMapping("/renewals")
    public AssistantTextResponse renewals(@RequestParam @Min(1) @Max(365) int days) {
        return assistantService.renewals(days);
    }

    @PostMapping("/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse createReminder(
            @Valid @RequestBody ReminderCreateRequest request,
            Authentication authentication) {
        return assistantService.createReminder(request, authentication);
    }

    @PutMapping("/reminders/{id}")
    public ReminderResponse updateReminder(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderUpdateRequest request,
            Authentication authentication) {
        return assistantService.updateReminder(id, request, authentication);
    }

    @PostMapping("/reminders/{id}/confirm")
    public ReminderResponse confirm(@PathVariable UUID id, Authentication authentication) {
        return assistantService.confirm(id, authentication);
    }

    @PostMapping("/reminders/{id}/reject")
    public ReminderResponse reject(@PathVariable UUID id, Authentication authentication) {
        return assistantService.reject(id, authentication);
    }
}
