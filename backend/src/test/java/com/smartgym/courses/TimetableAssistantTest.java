package com.smartgym.courses;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(classes = com.smartgym.membership.MembershipApiApplication.class)
@ActiveProfiles("test")
@Transactional
class TimetableAssistantTest {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSessionRepository courseSessionRepository;
    @Autowired
    private TimetableAssistantService timetableAssistantService;

    @Test
    void generatesDeterministicSuggestionsForAvailableSlots() {
        Course course = courseRepository.save(Course.create("Functional Training", "Strength and conditioning", "BEGINNER", 45, null));

        TimetableAssistantService.TimetableAssistantResponse response = timetableAssistantService.generateRecommendations(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                3);

        assertThat(response.summary()).contains("Deterministic timetable suggestions", "drafts");
        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations()).allSatisfy(slot -> {
            assertThat(slot.courseId()).isEqualTo(course.getCourseId());
            assertThat(slot.roomName()).isNotBlank();
            assertThat(slot.startTime()).isNotNull();
            assertThat(slot.endTime()).isAfter(slot.startTime());
        });
    }

    @Test
    void rejectsInvalidDateRange() {
        assertThatThrownBy(() -> timetableAssistantService.generateRecommendations(
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(2),
                2))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("To-date must be on or after the from-date");
    }

    @Test
    void recommendationsDoNotDependOnEquipmentInventory() {
        courseRepository.save(Course.create("Strength Training", "Strength class", "BEGINNER", 45, null));

        TimetableAssistantService.TimetableAssistantResponse response = timetableAssistantService.generateRecommendations(
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                5);

        assertThat(response.recommendations()).isNotEmpty();
    }

    @Test
    void excludesSlotsThatConflictWithTheAssignedCoach() {
        Course course = courseRepository.save(Course.create("Yoga", "Beginner class", "BEGINNER", 45, null));
        LocalDate date = LocalDate.now().plusDays(1);
        courseSessionRepository.save(CourseSession.create(
                course, date, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0),
                "coach-1", "Room A", 20));

        TimetableAssistantService.TimetableAssistantResponse response =
                timetableAssistantService.generateRecommendations(date, date, 10, "coach-1");

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.summary()).contains("No viable timetable slots");
    }
}
