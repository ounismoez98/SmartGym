package com.smartgym.courses;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.smartgym.membership.profile.Employee;
import com.smartgym.membership.profile.EmployeeRepository;
import com.smartgym.membership.profile.Member;
import com.smartgym.membership.profile.MemberRepository;
import org.springframework.stereotype.Component;

@Component
public class CourseIdentityDisplayNames {
    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;

    public CourseIdentityDisplayNames(MemberRepository memberRepository, EmployeeRepository employeeRepository) {
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
    }

    public void populateCoachUsernames(Collection<CourseSession> sessions) {
        Map<String, String> usernames = employeeUsernamesFor(
                sessions.stream().map(CourseSession::getCoachSubject).toList());
        sessions.forEach(session -> session.setCoachUsername(
                usernames.get(session.getCoachSubject())));
    }

    public void populateReservationUsernames(Collection<Reservation> reservations) {
        Set<String> subjects = new LinkedHashSet<>();
        for (Reservation reservation : reservations) {
            subjects.add(reservation.getMemberSubject());
            subjects.add(reservation.getSession().getCoachSubject());
        }
        Map<String, String> memberUsernames = memberUsernamesFor(subjects);
        Map<String, String> coachUsernames = employeeUsernamesFor(subjects);
        for (Reservation reservation : reservations) {
            reservation.setMemberUsername(memberUsernames.get(reservation.getMemberSubject()));
            reservation.getSession().setCoachUsername(
                    coachUsernames.get(reservation.getSession().getCoachSubject()));
        }
    }

    public String usernameFor(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Coach assignment required";
        }
        return employeeRepository.findByKeycloakUserId(subject)
                .map(Employee::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .orElse("Assigned coach");
    }

    private Map<String, String> memberUsernamesFor(Collection<String> subjects) {
        Set<String> nonBlankSubjects = subjects.stream()
                .filter(subject -> subject != null && !subject.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return memberRepository.findAllByKeycloakUserIdIn(nonBlankSubjects).stream()
                .filter(member -> member.getUsername() != null && !member.getUsername().isBlank())
                .collect(Collectors.toMap(
                        Member::getKeycloakUserId,
                        Member::getUsername,
                        (first, ignored) -> first));
    }

    private Map<String, String> employeeUsernamesFor(Collection<String> subjects) {
        Set<String> nonBlankSubjects = subjects.stream()
                .filter(subject -> subject != null && !subject.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return employeeRepository.findAllByKeycloakUserIdIn(nonBlankSubjects).stream()
                .filter(employee -> employee.getUsername() != null && !employee.getUsername().isBlank())
                .collect(Collectors.toMap(
                        Employee::getKeycloakUserId,
                        Employee::getUsername,
                        (first, ignored) -> first));
    }
}
