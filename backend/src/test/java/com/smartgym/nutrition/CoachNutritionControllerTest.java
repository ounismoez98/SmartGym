package com.smartgym.nutrition;

import com.smartgym.membership.MembershipApiApplication;
import com.smartgym.membership.profile.CoachAssignment;
import com.smartgym.membership.profile.CoachAssignmentRepository;
import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MembershipApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachNutritionControllerTest {
    private static final String MEMBER_A_SUBJECT = "coach-nutrition-member-a";
    private static final String MEMBER_B_SUBJECT = "coach-nutrition-member-b";
    private static final String COACH_SUBJECT = "coach-nutrition-coach";
    private UUID memberAId;
    private UUID memberBId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private FoodJournalRepository foodJournalRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Autowired
    private NutritionPlanRepository nutritionPlanRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private MealPlanGenerationContextRepository generationContextRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private CoachAssignmentRepository assignmentRepository;

    @BeforeEach
    void setUp() {
        nutritionPlanRepository.deleteAll();
        mealPlanRepository.deleteAll();
        generationContextRepository.deleteAll();
        foodJournalRepository.deleteAll();
        nutritionProfileRepository.deleteAll();
        foodRepository.deleteAll();
        assignmentRepository.deleteAll();
        memberRepository.deleteAll();
        employeeRepository.deleteAll();

        Member memberA = new Member(UUID.randomUUID(), MEMBER_A_SUBJECT, "coach-member-a", "Member A");
        memberAId = memberA.getId();
        Member memberB = new Member(UUID.randomUUID(), MEMBER_B_SUBJECT, "coach-member-b", "Member B");
        memberBId = memberB.getId();
        memberRepository.saveAll(List.of(memberA, memberB));
        employeeRepository.save(new Employee(UUID.randomUUID(), COACH_SUBJECT, "coach", "Coach"));
        assignmentRepository.save(new CoachAssignment(COACH_SUBJECT, MEMBER_A_SUBJECT));
    }

    @Test
    void coachCanSeeOnlyAssignedMembers() throws Exception {
        mockMvc.perform(get("/api/membership/coach/assigned-members").with(coach()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberProfileId").value(memberAId.toString()))
                .andExpect(jsonPath("$[0].displayName").value("Member A"));
    }

    @Test
    void coachCanReviewAssignedMemberFoodsButCannotGenerateOrCreateMeals() throws Exception {
        mockMvc.perform(get("/api/nutrition/coach/members/{memberProfileId}/foods", memberBId)
                        .with(coach()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/nutrition/coach/members/{memberProfileId}/assistant/plan", memberAId)
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/nutrition/coach/members/{memberProfileId}/foods", memberAId)
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/nutrition/coach/members/{memberProfileId}/foods", memberAId)
                        .with(member(MEMBER_A_SUBJECT)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/meal-plans/{mealPlanId}",
                        memberAId, 1L)
                        .with(member(MEMBER_A_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachCanReviewAndEditMealPlansOnlyForAssignedMembers() throws Exception {
        Food assignedFood = foodRepository.save(new Food(MEMBER_A_SUBJECT, "Oat bowl", null,
                350, 420, 20, 55, 12, 8, 300));
        Food unassignedFood = foodRepository.save(new Food(MEMBER_B_SUBJECT, "Private food", null,
                100, 100, 5, 10, 2, 1, 50));
        MealPlanGenerationContext context = generationContextRepository.save(
                new MealPlanGenerationContext(MEMBER_A_SUBJECT, List.of("peanuts")));
        MealPlan assignedPlan = mealPlanRepository.save(new MealPlan(MEMBER_A_SUBJECT,
                "Breakfast suggestion", "Oats and fruit. Cook until soft.",
                MealPlan.MealType.BREAKFAST, 420,
                List.of(new MealPlanItem(assignedFood.getId(), 1.0, assignedFood.getName())), context));
        MealPlan unassignedPlan = mealPlanRepository.save(new MealPlan(MEMBER_B_SUBJECT,
                "Private plan", "Private instructions",
                MealPlan.MealType.LUNCH, 600,
                List.of(new MealPlanItem(unassignedFood.getId(), 1.0, unassignedFood.getName()))));

        mockMvc.perform(get("/api/nutrition/coach/members/{memberProfileId}/meal-plans", memberAId)
                        .with(coach()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Breakfast suggestion"))
                .andExpect(jsonPath("$[0].description").value("Oats and fruit. Cook until soft."))
                .andExpect(jsonPath("$[0].foods[0].foodName").value("Oat bowl"));

        String updateRequest = """
                {
                  "name": "Reviewed oat bowl",
                  "description": "Oats, berries, and yogurt. Mix and serve chilled.",
                  "mealType": "SNACK",
                  "targetCalories": 390,
                  "foods": [{"foodId": %d, "servings": 0.9}]
                }
                """.formatted(assignedFood.getId());
        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/meal-plans/{mealPlanId}",
                        memberAId, assignedPlan.getId())
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reviewed oat bowl"))
                .andExpect(jsonPath("$.description").value("Oats, berries, and yogurt. Mix and serve chilled."))
                .andExpect(jsonPath("$.mealType").value("SNACK"))
                .andExpect(jsonPath("$.targetCalories").value(390.0))
                .andExpect(jsonPath("$.foods[0].servings").value(0.9));

        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/meal-plans/{mealPlanId}",
                        memberAId, assignedPlan.getId())
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest.replace("Oats, berries, and yogurt.", "Peanut butter, berries, and yogurt.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("restriction")));

        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/foods/{foodId}",
                        memberAId, assignedFood.getId())
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Peanut butter bowl",
                                  "servingSizeGrams": 350,
                                  "calories": 420,
                                  "proteinGrams": 20,
                                  "carbohydrateGrams": 55,
                                  "fatGrams": 12,
                                  "fiberGrams": 8,
                                  "sodiumMg": 300
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("restriction")));

        mockMvc.perform(get("/api/nutrition/coach/members/{memberProfileId}/meal-plans", memberBId)
                        .with(coach()))
                .andExpect(status().isForbidden());
        String unassignedUpdateRequest = """
                {
                  "name": "Reviewed private plan",
                  "description": "Private food and instructions.",
                  "mealType": "LUNCH",
                  "targetCalories": 390,
                  "foods": [{"foodId": %d, "servings": 1}]
                }
                """.formatted(unassignedFood.getId());
        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/meal-plans/{mealPlanId}",
                        memberBId, unassignedPlan.getId())
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unassignedUpdateRequest))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/nutrition/coach/members/{memberProfileId}/meal-plans/{mealPlanId}",
                        memberAId, unassignedPlan.getId())
                        .with(coach())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound());

        assertEquals("Private plan", mealPlanRepository.findById(unassignedPlan.getId()).orElseThrow().getName());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor coach() {
        return jwt().jwt(token -> token.subject(COACH_SUBJECT)
                        .claim("realm_access", Map.of("roles", List.of("COACH"))))
                .authorities(new SimpleGrantedAuthority("ROLE_COACH"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor member(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }
}
