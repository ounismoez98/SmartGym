package com.smartgym.courses;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = com.smartgym.membership.MembershipApiApplication.class)
@ActiveProfiles("test")
@Transactional
class Module2BusinessRulesTest {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSessionRepository courseSessionRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseSessionService courseSessionService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private MaintenanceTicketService maintenanceTicketService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        courseSessionRepository.deleteAll();
        maintenanceTicketRepository.deleteAll();
        equipmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void deleteCourseFailsWhenSessionExists() {
        Course course = courseRepository.save(Course.create("Strength Basics", "Intro session", "BEGINNER", 45, "Mat"));
        CourseSession session = CourseSession.create(course, LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0), "coach-1", "Room A", 12);
        courseSessionRepository.save(session);

        assertThatThrownBy(() -> courseService.deleteCourse(course.getCourseId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("scheduled sessions");
    }

    @Test
    void onlyPredefinedCourseTypesCanBeCreated() {
        for (String name : java.util.List.of(
                "Strength Training",
                "HIIT (High-Intensity Interval Training)",
                "Indoor Cycling",
                "Yoga",
                "Pilates",
                "Boxing Fitness",
                "Zumba / Dance Fitness",
                "Functional Training")) {
            assertThat(courseService.createCourse(Course.create(name, "Course offering", "BEGINNER", 45, null)).getName())
                    .isEqualTo(name);
        }
        assertThatThrownBy(() -> courseService.createCourse(
                Course.create("Mobility", "Not in the catalog", "BEGINNER", 45, null)))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("eight predefined course types");
    }

    @Test
    void sessionAndReservationResponsesShowUsernamesInsteadOfSubjects() {
            String coachSubject = UUID.randomUUID().toString();
            String memberSubject = UUID.randomUUID().toString();
            BusinessProfile coach = new BusinessProfile(coachSubject, "coach.jane", "Jane Coach");
            coach.ensureEmployeeProfile();
            businessProfileRepository.save(coach);
            BusinessProfile member = new BusinessProfile(memberSubject, "member.alex", "Alex Member");
            member.ensureMemberProfile();
            businessProfileRepository.save(member);

        Course course = courseRepository.save(
                Course.create("Yoga", "Flow session", "BEGINNER", 45, "Yoga mat"));
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course,
                LocalDate.now().plusDays(4),
                LocalTime.of(9, 0),
                LocalTime.of(9, 45),
                coachSubject,
                "Room A",
                12));
        reservationRepository.save(Reservation.create(memberSubject, session));

        CourseSession displayedSession = courseSessionService.getSession(session.getSessionId());
        Reservation displayedReservation = reservationService
                .getReservationsForMember(memberSubject, PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertThat(displayedSession.getCoachUsername()).isEqualTo("coach.jane");
        assertThat(displayedReservation.getMemberUsername()).isEqualTo("member.alex");
        assertThat(displayedReservation.getSession().getCoachUsername()).isEqualTo("coach.jane");
    }

    @Test
    void eachCourseTypeHasDefaultRequiredEquipmentRecommendations() {
        assertThat(Course.defaultRequiredEquipment("Strength Training"))
                .contains("Barbells", "weight plates", "dumbbells", "benches", "squat racks");
        assertThat(Course.defaultRequiredEquipment("HIIT (High-Intensity Interval Training)"))
                .contains("Exercise mats", "dumbbells", "kettlebells", "resistance bands", "timers");
        assertThat(Course.defaultRequiredEquipment("Indoor Cycling"))
                .contains("Stationary bikes", "water bottles", "towels");
        assertThat(Course.defaultRequiredEquipment("Yoga"))
                .contains("Yoga mats", "yoga blocks", "straps");
        assertThat(Course.defaultRequiredEquipment("Pilates"))
                .contains("Exercise mats", "Pilates balls", "resistance bands", "rings");
        assertThat(Course.defaultRequiredEquipment("Boxing Fitness"))
                .contains("Boxing gloves", "punching bags", "hand wraps", "skipping ropes");
        assertThat(Course.defaultRequiredEquipment("Zumba / Dance Fitness"))
                .contains("Open workout space", "sound system", "optional exercise mats");
        assertThat(Course.defaultRequiredEquipment("Functional Training"))
                .contains("Kettlebells", "medicine balls", "resistance bands", "dumbbells", "exercise mats");
    }

    @Test
    void creatingCourseAndSessionTogetherPersistsBothAndReusesExistingCourseType() {
        LocalDate date = LocalDate.now().plusDays(5);
        CourseSessionService.CreateCourseAndSessionRequest request =
                new CourseSessionService.CreateCourseAndSessionRequest(
                        "Indoor Cycling",
                        "Ride together",
                        "INTERMEDIATE",
                        45,
                        null,
                        true,
                        date,
                        LocalTime.of(18, 0),
                        LocalTime.of(18, 45),
                        "coach-cycle",
                        "Spin Room",
                        16);

        CourseSession first = courseSessionService.createCourseAndSession(request);
        CourseSession second = courseSessionService.createCourseAndSession(
                new CourseSessionService.CreateCourseAndSessionRequest(
                        "Indoor Cycling",
                        "A different description should not create a duplicate",
                        "BEGINNER",
                        60,
                        null,
                        true,
                        date.plusDays(1),
                        LocalTime.of(18, 0),
                        LocalTime.of(18, 45),
                        "coach-cycle",
                        "Spin Room",
                        16));

        assertThat(first.getCourse().getCourseId()).isEqualTo(second.getCourse().getCourseId());
        assertThat(first.getCourse().getName()).isEqualTo("Indoor Cycling");
        assertThat(first.getSessionDate()).isEqualTo(date);
        assertThat(first.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(first.getEndTime()).isEqualTo(LocalTime.of(18, 45));
        assertThat(first.getCoachSubject()).isEqualTo("coach-cycle");
        assertThat(first.getMaxCapacity()).isEqualTo(16);
        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(courseSessionRepository.count()).isEqualTo(2);
        assertThat(courseSessionRepository.findBySessionDateOrderByStartTimeAsc(date, org.springframework.data.domain.Pageable.unpaged())
                .getContent()).extracting(CourseSession::getSessionId).contains(first.getSessionId());
    }

    @Test
    void upcomingSessionsAreListedAndSerializableForAdminsMembersAndAssignedCoaches() throws Exception {
        Course course = courseRepository.save(Course.create("Yoga", "Mindful movement", "BEGINNER", 45, null));
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course, LocalDate.now().plusDays(3), LocalTime.of(18, 0), LocalTime.of(18, 45),
                "coach-upcoming", "Yoga Room", 16));
        Jwt admin = jwtWithRole("admin-subject", "ADMIN");
        Jwt member = jwtWithRole("member-subject", "MEMBER");
        Jwt coach = jwtWithRole("coach-upcoming", "COACH");

        assertThat(courseSessionService.listSessions(PageRequest.of(0, 100), null, admin).getContent())
                .extracting(CourseSession::getSessionId)
                .contains(session.getSessionId());
        var memberSessions = courseSessionService.listSessions(PageRequest.of(0, 100), null, member);
        assertThat(memberSessions.getContent())
                .extracting(CourseSession::getSessionId)
                .contains(session.getSessionId());
        assertThat(courseSessionService.listSessions(PageRequest.of(0, 100), null, coach).getContent())
                .extracting(CourseSession::getSessionId)
                .contains(session.getSessionId());
        assertThat(objectMapper.writeValueAsString(memberSessions)).contains("Yoga");
    }

    @Test
    void reservationsCloseTwentyFourHoursBeforeSession() {
        Course course = courseRepository.save(Course.create("Yoga", "Mindful movement", "BEGINNER", 45, null));
        LocalDateTime start = LocalDateTime.now().plusHours(23);
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course, start.toLocalDate(), start.toLocalTime(),
                start.toLocalTime().plusMinutes(45), "coach-cutoff", "Room A", 10));

        assertThatThrownBy(() -> reservationService.createReservation("member-cutoff", session.getSessionId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("24 hours before");
    }

    private Jwt jwtWithRole(String subject, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }

    @Test
    void duplicateReservationIsRejected() {
        Course course = courseRepository.save(Course.create("Pilates", "Core strength", "INTERMEDIATE", 60, "Mat"));
        CourseSession session = courseSessionRepository.save(CourseSession.create(course, LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "coach-2", "Room B", 20));

        reservationService.createReservation("member-1", session.getSessionId());

        assertThatThrownBy(() -> reservationService.createReservation("member-1", session.getSessionId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void reservationsUpdateAvailabilityAndCanBeRebookedAfterCancellation() {
        Course course = courseRepository.save(Course.create("Yoga", "Mindful movement", "BEGINNER", 45, null));
        LocalDateTime start = LocalDateTime.now().plusHours(48);
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course, start.toLocalDate(), start.toLocalTime(), start.toLocalTime().plusMinutes(45),
                "coach-yoga", "Yoga Room", 2));

        Reservation reservation = reservationService.createReservation("member-rebook", session.getSessionId());
        assertThat(reservationRepository.countConfirmedBySession(session.getSessionId())).isEqualTo(1);
        assertThat(courseSessionService.listSessions(PageRequest.of(0, 10), session.getSessionDate(),
                jwtWithRole("member-rebook", "MEMBER")).getContent().get(0).getRemainingPlaces()).isEqualTo(1);

        reservationService.cancelReservation(reservation.getReservationId(), "member-rebook", false);
        Reservation rebooked = reservationService.createReservation("member-rebook", session.getSessionId());

        assertThat(rebooked.getReservationId()).isEqualTo(reservation.getReservationId());
        assertThat(rebooked.getStatus()).isEqualTo("CONFIRMED");
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelledAndPastSessionsRejectReservations() {
        Course course = courseRepository.save(Course.create("Pilates", "Core strength", "BEGINNER", 45, null));
        CourseSession cancelledSession = courseSessionRepository.save(CourseSession.create(
                course, LocalDate.now().plusDays(3), LocalTime.of(18, 0), LocalTime.of(18, 45),
                "coach-pilates", "Studio C", 10));
        cancelledSession.setStatus("CANCELLED");
        courseSessionRepository.save(cancelledSession);

        LocalDateTime pastStart = LocalDateTime.now().minusHours(2);
        CourseSession pastSession = courseSessionRepository.save(CourseSession.create(
                course, pastStart.toLocalDate(), pastStart.toLocalTime(),
                pastStart.toLocalTime().plusMinutes(45), "coach-past", "Studio D", 10));

        assertThatThrownBy(() -> reservationService.createReservation("member-cancelled", cancelledSession.getSessionId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("Only scheduled sessions");
        assertThatThrownBy(() -> reservationService.createReservation("member-past", pastSession.getSessionId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("24 hours before");
    }

    @Test
    void reservationExceedsCapacityIsRejected() {
        Course course = courseRepository.save(Course.create("HIIT", "Fat burn", "ADVANCED", 40, "Treadmill"));
        CourseSession session = courseSessionRepository.save(CourseSession.create(course, LocalDate.now().plusDays(3), LocalTime.of(15, 0), LocalTime.of(16, 0), "coach-3", "Room C", 1));

        reservationService.createReservation("member-1", session.getSessionId());

        assertThatThrownBy(() -> reservationService.createReservation("member-2", session.getSessionId()))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("full capacity");
    }

    @Test
    void creatingMaintenanceTicketMarksEquipmentUnavailable() {
        Equipment equipment = equipmentRepository.save(Equipment.create("Bench Press", "Heavy bench", "STRENGTH", true, false));

        MaintenanceTicket ticket = maintenanceTicketService.createTicket(equipment.getEquipmentId(), new MaintenanceTicketService.CreateTicketRequest("Loose handle", "operator-1"));

        assertThat(ticket.getStatus()).isEqualTo("OPEN");
        Equipment updated = equipmentRepository.findById(equipment.getEquipmentId()).orElseThrow();
        assertThat(updated.isAvailable()).isFalse();
        assertThat(updated.isMaintenanceRequired()).isTrue();
    }

    @Test
    void courseEquipmentRecommendationsDoNotRequireInventoryItemsToSchedule() {
        Course course = courseRepository.save(Course.create("Strength Training", "Strength class", "BEGINNER", 45, null));
        CourseSession session = courseSessionService.createSession(course.getCourseId(),
                new CourseSessionService.CreateSessionRequest(
                        LocalDate.now().plusDays(4),
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 45),
                        "coach-4",
                        "Room D",
                        12));

        assertThat(session.getCourse().getRequiredEquipment()).contains("Barbells", "squat racks");
    }

    @Test
    void coachAndRoomSchedulingConflictsAreRejected() {
        Equipment equipment = equipmentRepository.save(Equipment.create("Foam Roller", "Recovery tool", "RECOVERY", true, false));
        Course course = courseRepository.save(Course.create("Mobility", "Movement flow", "BEGINNER", 45, equipment.getName()));
        CourseSession existing = courseSessionRepository.save(CourseSession.create(
                course,
                LocalDate.now().plusDays(5),
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                "coach-5",
                "Room E",
                10));

        assertThatThrownBy(() -> courseSessionService.createSession(course.getCourseId(),
                new CourseSessionService.CreateSessionRequest(
                        existing.getSessionDate(),
                        LocalTime.of(11, 30),
                        LocalTime.of(12, 30),
                        "coach-9",
                        "Room E",
                        8)))
                .isInstanceOf(Module2Exception.class)
                .hasMessageContaining("Room is not available");
    }

    @Test
    void sessionCanBeRescheduledWithoutConflictingWithItself() {
        Course course = courseRepository.save(Course.create("Stretch", "Flexibility", "BEGINNER", 45, null));
        CourseSession existing = courseSessionRepository.save(CourseSession.create(
                course, LocalDate.now().plusDays(5), LocalTime.of(10, 0), LocalTime.of(10, 45),
                "coach-reschedule", "Room A", 12));

        CourseSession updated = courseSessionService.updateSession(existing.getSessionId(),
                new CourseSessionService.UpdateSessionRequest(
                        existing.getSessionDate(),
                        LocalTime.of(10, 30),
                        LocalTime.of(11, 15),
                        existing.getCoachSubject(),
                        existing.getRoomName(),
                        existing.getMaxCapacity(),
                        null));

        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(11, 15));
    }

    @Test
    void cancellingSessionAlsoCancelsConfirmedReservations() {
        Course course = courseRepository.save(Course.create("Pilates", "Core strength", "BEGINNER", 45, null));
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course, LocalDate.now().plusDays(3), LocalTime.of(9, 0), LocalTime.of(9, 45),
                "coach-cancel", "Room B", 12));
        Reservation reservation = reservationService.createReservation("member-cancel", session.getSessionId());

        courseSessionService.cancelSession(session.getSessionId());

        assertThat(reservationRepository.findById(reservation.getReservationId()).orElseThrow().getStatus())
                .isEqualTo("CANCELLED");
    }

    @Test
    void reservationEndpointsEnforceMemberAndCoachPermissionsAndSerializeCourseDetails() throws Exception {
        Course course = courseRepository.save(Course.create("Yoga", "Mindful movement", "BEGINNER", 45, null));
        LocalDateTime start = LocalDateTime.now().plusHours(48);
        CourseSession session = courseSessionRepository.save(CourseSession.create(
                course, start.toLocalDate(), start.toLocalTime(), start.toLocalTime().plusMinutes(45),
                "coach-visible", "Yoga Room", 3));
        CourseSession otherCoachSession = courseSessionRepository.save(CourseSession.create(
                course, start.toLocalDate(), start.toLocalTime(), start.toLocalTime().plusMinutes(45),
                "coach-other", "Other Yoga Room", 3));
        String sessionPath = "/api/courses/sessions/" + session.getSessionId() + "/reservations";

        mockMvc.perform(post(sessionPath).with(jwt().jwt(token -> token.subject("member-api"))
                        .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yoga")));
        mockMvc.perform(post("/api/courses/sessions/" + otherCoachSession.getSessionId() + "/reservations")
                        .with(jwt().jwt(token -> token.subject("member-other"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/courses/sessions/" + session.getSessionId())
                        .with(jwt().jwt(token -> token.subject("coach-visible")
                                        .claim("realm_access", Map.of("roles", List.of("COACH"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yoga mats")));
        mockMvc.perform(get("/api/courses/sessions/" + session.getSessionId())
                        .with(jwt().jwt(token -> token.subject("coach-other")
                                        .claim("realm_access", Map.of("roles", List.of("COACH"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(sessionPath).with(jwt().jwt(token -> token.subject("member-api"))
                        .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")));
        mockMvc.perform(post(sessionPath).with(jwt().jwt(token -> token.subject("admin-api"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(sessionPath).with(jwt().jwt(token -> token.subject("coach-visible"))
                        .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/courses/reservations/me").with(jwt().jwt(token -> token.subject("member-api"))
                        .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Yoga")));
        mockMvc.perform(get("/api/courses/reservations").with(jwt().jwt(token -> token.subject("coach-visible"))
                        .authorities(new SimpleGrantedAuthority("ROLE_COACH"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member-api")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("member-other"))));
        mockMvc.perform(get("/api/courses/reservations").with(jwt().jwt(token -> token.subject("admin-api")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member-api")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member-other")));
        mockMvc.perform(get("/api/courses/reservations/me").with(jwt().jwt(token -> token.subject("admin-api"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        UUID reservationId = reservationRepository
                .findByMemberSubjectAndSessionSessionId("member-api", session.getSessionId()).orElseThrow()
                .getReservationId();
        UUID otherReservationId = reservationRepository
                .findByMemberSubjectAndSessionSessionId("member-other", otherCoachSession.getSessionId()).orElseThrow()
                .getReservationId();
        mockMvc.perform(put("/api/courses/reservations/" + otherReservationId + "/cancel")
                        .with(jwt().jwt(token -> token.subject("admin-api")
                                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CANCELLED")));
        mockMvc.perform(put("/api/courses/reservations/" + reservationId + "/cancel")
                        .with(jwt().jwt(token -> token.subject("other-member"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/courses/reservations/" + reservationId + "/cancel")
                        .with(jwt().jwt(token -> token.subject("member-api"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CANCELLED")));
    }
}
