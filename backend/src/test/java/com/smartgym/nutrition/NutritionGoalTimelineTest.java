package com.smartgym.nutrition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionGoalTimelineTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Test
    void estimatesConservativeWeightLossRangeAndComparesRequestedDate() {
        NutritionService.GoalTimelineResult tooSoon = estimate(80.0, 70.0, "weight loss",
                TODAY.plusWeeks(10));
        assertTrue(tooSoon.available());
        assertEquals(20, tooSoon.minimumWeeks());
        assertEquals(40, tooSoon.maximumWeeks());
        assertEquals(TODAY.plusWeeks(20), tooSoon.earliestEstimatedDate());
        assertEquals(TODAY.plusWeeks(40), tooSoon.latestEstimatedDate());
        assertEquals("EARLIER_THAN_ESTIMATE", tooSoon.requestedDateStatus());
        assertTrue(tooSoon.note().contains("may be unrealistic"));
        assertTrue(tooSoon.assumptions().contains("0.25–0.5 kg/week"));

        assertEquals("WITHIN_ESTIMATE", estimate(80.0, 70.0, "weight loss",
                TODAY.plusWeeks(30)).requestedDateStatus());
        assertEquals("LATER_THAN_ESTIMATE", estimate(80.0, 70.0, "weight loss",
                TODAY.plusWeeks(50)).requestedDateStatus());
    }

    @Test
    void doesNotApplyWeightLossRateToGainMaintenanceOrUnsupportedGoals() {
        assertFalse(estimate(70.0, 80.0, "muscle gain", null).available());
        assertFalse(estimate(70.0, 70.0, "maintenance", null).available());
        assertFalse(estimate(80.0, 70.0, "recomposition", null).available());
    }

    @Test
    void returnsUnavailableForMinorsMissingInputsAndNonLossTargets() {
        assertFalse(NutritionService.estimateGoalTimeline(17, 170.0, 80.0, 70.0,
                null, "weight loss", TODAY).available());
        assertFalse(NutritionService.estimateGoalTimeline(30, null, 80.0, 70.0,
                null, "weight loss", TODAY).available());
        assertFalse(estimate(80.0, 80.0, "weight loss", null).available());
        assertFalse(NutritionService.estimateGoalTimeline(30, 170.0, Double.NaN, 70.0,
                null, "weight loss", TODAY).available());
    }

    private NutritionService.GoalTimelineResult estimate(
            double currentWeight, double targetWeight, String goal, LocalDate requestedDate) {
        return NutritionService.estimateGoalTimeline(30, 180.0, currentWeight, targetWeight,
                requestedDate, goal, TODAY);
    }
}
