package com.smartgym.courses;

import java.time.Instant;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "courses", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Course {
    private static final Map<String, String> DEFAULT_REQUIRED_EQUIPMENT = Map.of(
            "strength training", "Barbells, weight plates, dumbbells, benches, squat racks",
            "hiit (high-intensity interval training)", "Exercise mats, dumbbells, kettlebells, resistance bands, timers",
            "indoor cycling", "Stationary bikes, water bottles, towels",
            "yoga", "Yoga mats, yoga blocks, straps",
            "pilates", "Exercise mats, Pilates balls, resistance bands, rings",
            "boxing fitness", "Boxing gloves, punching bags, hand wraps, skipping ropes",
            "zumba / dance fitness", "Open workout space, sound system, optional exercise mats",
            "functional training", "Kettlebells, medicine balls, resistance bands, dumbbells, exercise mats");

    @Id
    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "difficulty_level", nullable = false, length = 30)
    private String difficultyLevel;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "required_equipment", length = 1000)
    private String requiredEquipment;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<CourseSession> sessions = new ArrayList<>();

    protected Course() {
    }

    public Course(UUID courseId, String name, String description, String difficultyLevel,
                 Integer durationMinutes, String requiredEquipment) {
        this.courseId = courseId;
        this.name = name;
        this.description = description;
        this.difficultyLevel = difficultyLevel;
        this.durationMinutes = durationMinutes;
        this.requiredEquipment = requiredEquipment == null || requiredEquipment.isBlank()
                ? defaultRequiredEquipment(name)
                : requiredEquipment.trim();
    }

    public static Course create(String name, String description, String difficultyLevel,
                                Integer durationMinutes, String requiredEquipment) {
        return new Course(UUID.randomUUID(), name, description, difficultyLevel, durationMinutes, requiredEquipment);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.courseId == null) {
            this.courseId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getRequiredEquipment() {
        if (requiredEquipment != null && !requiredEquipment.isBlank()) {
            return requiredEquipment;
        }
        return defaultRequiredEquipment(name);
    }

    public void setRequiredEquipment(String requiredEquipment) {
        this.requiredEquipment = requiredEquipment == null || requiredEquipment.isBlank()
                ? defaultRequiredEquipment(name)
                : requiredEquipment.trim();
    }

    public static String defaultRequiredEquipment(String courseName) {
        if (courseName == null) {
            return "";
        }
        return DEFAULT_REQUIRED_EQUIPMENT.getOrDefault(courseName.trim().toLowerCase(Locale.ROOT), "");
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CourseSession> getSessions() {
        return sessions;
    }

}
