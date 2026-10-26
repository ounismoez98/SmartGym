package com.smartgym.courses;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;
    private final CourseSessionService courseSessionService;
    private final TimetableAssistantService timetableAssistantService;

    public CourseController(CourseService courseService, CourseSessionService courseSessionService,
                            TimetableAssistantService timetableAssistantService) {
        this.courseService = courseService;
        this.courseSessionService = courseSessionService;
        this.timetableAssistantService = timetableAssistantService;
    }

    @GetMapping
    public Page<Course> listCourses(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String query,
                                   @AuthenticationPrincipal Jwt jwt) {
        Pageable pageable = PageRequest.of(page, size);
        if (!isAdmin(jwt)) {
            return query == null || query.isBlank()
                    ? courseService.listActiveCourses(pageable)
                    : courseService.searchActiveCourses(query, pageable);
        }
        return query == null || query.isBlank() ? courseService.listCourses(pageable) : courseService.searchCourses(query, pageable);
    }

    @GetMapping("/{courseId}")
    public Course getCourse(@PathVariable UUID courseId, @AuthenticationPrincipal Jwt jwt) {
        Course course = courseService.getCourse(courseId);
        if (!course.isActive() && !isAdmin(jwt)) {
            throw new com.smartgym.courses.Module2Exception(HttpStatus.NOT_FOUND, "Course not found.");
        }
        return course;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@RequestBody Course course) {
        return courseService.createCourse(course);
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseSession createCourseAndSchedule(
            @RequestBody CourseSessionService.CreateCourseAndSessionRequest request) {
        return courseSessionService.createCourseAndSession(request);
    }

    @PutMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Course updateCourse(@PathVariable UUID courseId, @RequestBody CourseService.CourseUpdateRequest request) {
        return courseService.updateCourse(courseId, request);
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable UUID courseId) {
        courseService.deleteCourse(courseId);
    }

    @GetMapping("/assistant/timetable")
    @PreAuthorize("hasRole('ADMIN')")
    public TimetableAssistantService.TimetableAssistantResponse suggestTimetable(
            @RequestParam(required = false) java.time.LocalDate fromDate,
            @RequestParam(required = false) java.time.LocalDate toDate,
            @RequestParam(defaultValue = "3") int limit,
            @RequestParam(required = false) String coachSubject) {
        return timetableAssistantService.generateRecommendations(fromDate, toDate, limit, coachSubject);
    }

    private boolean isAdmin(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        return realmAccess instanceof java.util.Map<?, ?> map
                && map.get("roles") instanceof java.util.Collection<?> roles
                && roles.contains("ADMIN");
    }
}
