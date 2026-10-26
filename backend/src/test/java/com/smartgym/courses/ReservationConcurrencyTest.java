package com.smartgym.courses;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = com.smartgym.membership.MembershipApiApplication.class)
@ActiveProfiles("test")
class ReservationConcurrencyTest {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSessionRepository sessionRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Test
    void concurrentReservationsCannotExceedSessionCapacity() throws Exception {
        Course course = courseRepository.save(Course.create(
                "Functional Training", "Concurrency check", "BEGINNER", 45, null));
        LocalDateTime start = LocalDateTime.now().plusHours(48);
        CourseSession session = sessionRepository.save(CourseSession.create(
                course, start.toLocalDate(), start.toLocalTime(), start.toLocalTime().plusMinutes(45),
                "coach-concurrency", "Concurrency Room", 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> reserveAfterSignal(ready, startTogether, "member-concurrent-a", session.getSessionId())),
                    executor.submit(() -> reserveAfterSignal(ready, startTogether, "member-concurrent-b", session.getSessionId())));
            ready.await();
            startTogether.countDown();

            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(reservationRepository.countConfirmedBySession(session.getSessionId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            reservationRepository.deleteAll(reservationRepository
                    .findBySessionSessionIdOrderByCreatedAtDesc(session.getSessionId(),
                            org.springframework.data.domain.Pageable.unpaged()).getContent());
            sessionRepository.deleteById(session.getSessionId());
            courseRepository.deleteById(course.getCourseId());
        }
    }

    private boolean reserveAfterSignal(CountDownLatch ready, CountDownLatch startTogether,
                                       String memberSubject, java.util.UUID sessionId) throws InterruptedException {
        ready.countDown();
        startTogether.await();
        try {
            reservationService.createReservation(memberSubject, sessionId);
            return true;
        } catch (Module2Exception exception) {
            return false;
        }
    }
}
