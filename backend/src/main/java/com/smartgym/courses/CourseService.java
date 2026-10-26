package com.smartgym.courses;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
    private static final Map<String, String> COURSE_TYPES = Map.of(
            "strength training", "Strength Training",
            "hiit (high-intensity interval training)", "HIIT (High-Intensity Interval Training)",
            "indoor cycling", "Indoor Cycling",
            "yoga", "Yoga",
            "pilates", "Pilates",
            "boxing fitness", "Boxing Fitness",
            "zumba / dance fitness", "Zumba / Dance Fitness",
            "functional training", "Functional Training");
    private static final Set<String> COURSE_TYPE_KEYS = COURSE_TYPES.keySet();

    private final CourseRepository courseRepository;
    private final CourseSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;

    public CourseService(CourseRepository courseRepository,
                        CourseSessionRepository sessionRepository,
                        ReservationRepository reservationRepository) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public Page<Course> listCourses(Pageable pageable) {
        return courseRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Course> listActiveCourses(Pageable pageable) {
        return courseRepository.findAllByActiveTrueOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Course> searchCourses(String query, Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return listCourses(pageable);
        }
        return courseRepository.search(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Course> searchActiveCourses(String query, Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return listActiveCourses(pageable);
        }
        return courseRepository.searchActive(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public Course getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new Module2Exception(HttpStatus.NOT_FOUND, "Course not found."));
    }

    @Transactional
    public Course createCourse(Course course) {
        validateCourse(course);
        if (courseRepository.findByNameIgnoreCase(course.getName()).isPresent()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "A course with this name already exists.");
        }
        return courseRepository.save(course);
    }

    @Transactional
    public Course getOrCreateCourse(Course course) {
        validateCourse(course);
        return courseRepository.findByNameIgnoreCase(course.getName())
                .map(existing -> {
                    if (!existing.isActive() && course.isActive()) {
                        existing.setActive(true);
                        return courseRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> courseRepository.save(course));
    }

    @Transactional
    public Course updateCourse(UUID courseId, CourseUpdateRequest request) {
        Course course = getCourse(courseId);
        if (request.name() != null && !request.name().isBlank()) {
            course.setName(request.name().trim());
        }
        if (request.description() != null) {
            course.setDescription(request.description());
        }
        if (request.difficultyLevel() != null && !request.difficultyLevel().isBlank()) {
            course.setDifficultyLevel(request.difficultyLevel().trim());
        }
        if (request.durationMinutes() != null) {
            course.setDurationMinutes(request.durationMinutes());
        }
        if (request.requiredEquipment() != null) {
            course.setRequiredEquipment(request.requiredEquipment());
        }
        if (request.active() != null) {
            course.setActive(request.active());
        }
        validateCourse(course);
        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(UUID courseId) {
        Course course = getCourse(courseId);
        List<CourseSession> courseSessions = sessionRepository.findByCourseCourseIdOrderBySessionDateAscStartTimeAsc(courseId, Pageable.unpaged()).getContent();
        if (!courseSessions.isEmpty()) {
            throw new Module2Exception(HttpStatus.CONFLICT, "Course cannot be deleted because it has scheduled sessions.");
        }
        courseRepository.delete(course);
    }

    private void validateCourse(Course course) {
        if (course == null) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Course payload is required.");
        }
        if (course.getName() == null || course.getName().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Course name is required.");
        }
        String courseTypeKey = course.getName().trim().toLowerCase(Locale.ROOT);
        if (!COURSE_TYPE_KEYS.contains(courseTypeKey)) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Course name must be one of the eight predefined course types.");
        }
        if (course.getDifficultyLevel() == null || course.getDifficultyLevel().isBlank()) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Difficulty level is required.");
        }
        if (course.getDurationMinutes() == null || course.getDurationMinutes() <= 0) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Duration must be greater than zero.");
        }
        if (!List.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(course.getDifficultyLevel().trim().toUpperCase(Locale.ROOT))) {
            throw new Module2Exception(HttpStatus.BAD_REQUEST, "Difficulty level must be BEGINNER, INTERMEDIATE, or ADVANCED.");
        }
        course.setName(COURSE_TYPES.get(courseTypeKey));
        course.setDifficultyLevel(course.getDifficultyLevel().trim().toUpperCase(Locale.ROOT));
    }

    public record CourseUpdateRequest(
            String name,
            String description,
            String difficultyLevel,
            Integer durationMinutes,
            String requiredEquipment,
            Boolean active) {
    }
}
