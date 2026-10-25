package com.smartgym.nutrition;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.smartgym.membership.MembershipApiApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = MembershipApiApplication.class)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "smartgym.postgres.it.url", matches = "jdbc:postgresql:.*")
class PostgresNutritionIntegrationTest {
    private static final String SUBJECT = "postgres-it-member";

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    @Autowired
    private FoodJournalRepository foodJournalRepository;

    @Autowired
    private BodyMeasurementRepository bodyMeasurementRepository;

    @Autowired
    private NutritionProfileRepository nutritionProfileRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredProperty("smartgym.postgres.it.url"));
        registry.add("spring.datasource.username", () -> requiredProperty("smartgym.postgres.it.username"));
        registry.add("spring.datasource.password", () -> requiredProperty("smartgym.postgres.it.password"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void clearOnlyIntegrationTestRows() {
        mealPlanRepository.deleteAll();
        foodJournalRepository.deleteAll();
        bodyMeasurementRepository.deleteAll();
        nutritionProfileRepository.deleteAll();
        foodRepository.deleteAll();
    }

    @Test
    void freshDatabaseRunsBaselineMigrationAndCreatesNutritionTables() {
        assertTrue(flyway.info().applied().length > 0);
        assertEquals("1", flyway.info().applied()[0].getVersion().getVersion());
        Integer tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('nutrition_foods', 'nutrition_meal_plans',
                      'nutrition_meal_plan_items', 'nutrition_food_journal',
                      'nutrition_body_measurements', 'nutrition_profiles')
                """, Integer.class);
        assertEquals(6, tables);
    }

    @Test
    void existingNonemptySchemaWithoutHistoryIsNotSilentlyBaselined() {
        String schema = "existing_baseline_probe";
        jdbcTemplate.execute("create schema " + schema);
        jdbcTemplate.execute("create table " + schema + ".preexisting_table (id integer primary key)");

        Flyway existingSchema = Flyway.configure()
                .dataSource(requiredProperty("smartgym.postgres.it.url"),
                        requiredProperty("smartgym.postgres.it.username"),
                        requiredProperty("smartgym.postgres.it.password"))
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();

        assertThrows(FlywayException.class, existingSchema::migrate);
    }

    @Test
    void nutritionEntitiesPersistUpdateAndDeleteAcrossRepositoryTransactions() {
        Food food = foodRepository.saveAndFlush(
                new Food(SUBJECT, "Oats", "Test brand", 40, 150, 5, 27, 3, 4, 10));
        assertNotNull(food.getId());
        food.update("Rolled oats", "Test brand", 40, 155, 5, 28, 3, 4, 9);
        foodRepository.saveAndFlush(food);
        assertEquals("Rolled oats", foodRepository.findByIdAndSubject(food.getId(), SUBJECT)
                .orElseThrow().getName());

        NutritionProfile profile = nutritionProfileRepository.saveAndFlush(
                new NutritionProfile(SUBJECT, "Profile", "maintenance", "balanced",
                        "light", 30, 170.0, 70.0, 2000));
        assertEquals(2000, nutritionProfileRepository.findFirstBySubjectOrderByCreatedAtDesc(SUBJECT)
                .orElseThrow().getTargetCalories());

        MealPlan plan = mealPlanRepository.saveAndFlush(new MealPlan(SUBJECT, "Breakfast",
                "Initial", MealPlan.MealType.BREAKFAST, 300,
                List.of(new MealPlanItem(food.getId(), 2, food.getName()))));
        MealPlan loadedPlan = mealPlanRepository.findByIdAndSubject(plan.getId(), SUBJECT).orElseThrow();
        assertEquals(food.getId(), loadedPlan.getItems().get(0).getFoodId());
        assertEquals(2, loadedPlan.getItems().get(0).getServings());
        loadedPlan.update("Updated breakfast", "Updated", MealPlan.MealType.LUNCH, 350,
                List.of(new MealPlanItem(food.getId(), 1, food.getName())));
        mealPlanRepository.saveAndFlush(loadedPlan);
        assertEquals("Updated breakfast", mealPlanRepository.findById(plan.getId()).orElseThrow().getName());

        FoodJournal journal = foodJournalRepository.saveAndFlush(new FoodJournal(SUBJECT, food.getId(),
                FoodJournal.MealType.BREAKFAST, LocalDate.of(2026, 10, 24), 1.5, "Logged"));
        assertEquals(1.5, foodJournalRepository.findByIdAndSubject(journal.getId(), SUBJECT)
                .orElseThrow().getServings());
        journal.update(food.getId(), FoodJournal.MealType.LUNCH,
                LocalDate.of(2026, 10, 25), 2, "Updated log");
        foodJournalRepository.saveAndFlush(journal);
        assertEquals(LocalDate.of(2026, 10, 25),
                foodJournalRepository.findById(journal.getId()).orElseThrow().getEntryDate());

        BodyMeasurement measurement = bodyMeasurementRepository.saveAndFlush(new BodyMeasurement(SUBJECT,
                LocalDate.of(2026, 10, 24), 70, 80, 90, 100, 30, 50, null));
        assertEquals(70, bodyMeasurementRepository.findByIdAndSubject(measurement.getId(), SUBJECT)
                .orElseThrow().getWeightKg());

        foodJournalRepository.deleteById(journal.getId());
        bodyMeasurementRepository.deleteById(measurement.getId());
        mealPlanRepository.deleteById(plan.getId());
        assertFalse(foodJournalRepository.findById(journal.getId()).isPresent());
        assertFalse(bodyMeasurementRepository.findById(measurement.getId()).isPresent());
        assertFalse(mealPlanRepository.findById(plan.getId()).isPresent());
        foodRepository.deleteById(food.getId());
        nutritionProfileRepository.deleteById(profile.getId());
        assertFalse(foodRepository.findById(food.getId()).isPresent());
        assertFalse(nutritionProfileRepository.findById(profile.getId()).isPresent());
    }

    @Test
    void mealPlanItemForeignKeyRejectsMissingParentAndFailedTransactionRollsBack() {
        Food food = foodRepository.saveAndFlush(
                new Food(SUBJECT, "Integrity test food", null, 100, 100, 10, 10, 2, 1, 5));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into nutrition_meal_plan_items (meal_plan_id, food_id, food_name, servings)
                values (?, ?, ?, ?)
                """, Long.MAX_VALUE, food.getId(), food.getName(), 1.0));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            foodRepository.saveAndFlush(
                    new Food(SUBJECT, "Rolled back food", null, 100, 100, 10, 10, 2, 1, 5));
            throw new IllegalStateException("rollback requested");
        }));
        assertTrue(foodRepository.findBySubjectOrderByNameAsc(SUBJECT).stream()
                .noneMatch(item -> item.getName().equals("Rolled back food")));
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing dedicated PostgreSQL integration property: " + name);
        }
        if (name.equals("smartgym.postgres.it.url")
                && !value.matches("jdbc:postgresql:.*?/smartgym_it(?:\\?.*)?")) {
            throw new IllegalStateException(
                    "PostgreSQL integration tests require the dedicated smartgym_it database name.");
        }
        if (name.equals("smartgym.postgres.it.username") && !value.equals("smartgym_it")) {
            throw new IllegalStateException(
                    "PostgreSQL integration tests require the dedicated smartgym_it database user.");
        }
        return value;
    }
}
