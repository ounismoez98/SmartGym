package com.smartgym.nutrition;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NutritionControllerAsyncHandlingTest {
    private final NutritionService nutritionService = mock(NutritionService.class);
    private final NutritionController controller = new NutritionController(nutritionService);
    private final Jwt member = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("member-123")
            .build();

    @Test
    void executorRejectionReturnsServiceUnavailableWithoutPlan() {
        when(nutritionService.generateMealPlanSuggestion(eq("member-123"), any()))
                .thenThrow(new TaskRejectedException("executor queue is full"));

        var response = controller.generatePlan(member, new NutritionService.AiMealRequest(
                "maintenance", 2000, "balanced", List.of())).join();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().available());
        assertEquals("UNAVAILABLE", response.getBody().status());
        assertNull(response.getBody().plan());
        assertTrue(response.getBody().note().contains("assistant is busy"));
    }

    @Test
    void failedAsyncInferenceReturnsSanitizedServiceUnavailableResponse() {
        when(nutritionService.generateMealPlanSuggestion(eq("member-123"), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("private timeout detail")));

        var response = controller.generatePlan(member, new NutritionService.AiMealRequest(
                "maintenance", 2000, "balanced", List.of())).join();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().available());
        assertEquals("UNAVAILABLE", response.getBody().status());
        assertNull(response.getBody().plan());
        assertEquals("Meal-plan generation failed. No plan was accepted.", response.getBody().note());
    }
}
