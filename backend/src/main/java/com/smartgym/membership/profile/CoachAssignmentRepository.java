package com.smartgym.membership.profile;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachAssignmentRepository extends JpaRepository<CoachAssignment, UUID> {
    boolean existsByCoachSubjectAndMemberSubject(String coachSubject, String memberSubject);

    List<CoachAssignment> findAllByCoachSubject(String coachSubject);
}
