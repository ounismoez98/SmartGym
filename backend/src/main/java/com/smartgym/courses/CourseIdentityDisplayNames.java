package com.smartgym.courses;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.smartgym.membership.profile.BusinessProfile;
import com.smartgym.membership.profile.BusinessProfileRepository;
import org.springframework.stereotype.Component;

@Component
public class CourseIdentityDisplayNames {
    private final BusinessProfileRepository profileRepository;

    public CourseIdentityDisplayNames(BusinessProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public void populateCoachUsernames(Collection<CourseSession> sessions) {
        Map<String, String> usernames = usernamesFor(
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
        Map<String, String> usernames = usernamesFor(subjects);
        for (Reservation reservation : reservations) {
            reservation.setMemberUsername(usernames.get(reservation.getMemberSubject()));
            reservation.getSession().setCoachUsername(
                    usernames.get(reservation.getSession().getCoachSubject()));
        }
    }

    public String usernameFor(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Coach assignment required";
        }
        return profileRepository.findById(subject)
                .map(BusinessProfile::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .orElse("Assigned coach");
    }

    private Map<String, String> usernamesFor(Collection<String> subjects) {
        Set<String> nonBlankSubjects = subjects.stream()
                .filter(subject -> subject != null && !subject.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return profileRepository.findAllById(nonBlankSubjects).stream()
                .filter(profile -> profile.getUsername() != null && !profile.getUsername().isBlank())
                .collect(Collectors.toMap(
                        BusinessProfile::getSubject,
                        BusinessProfile::getUsername,
                        (first, ignored) -> first));
    }
}
