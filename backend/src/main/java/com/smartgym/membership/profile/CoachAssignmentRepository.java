package com.smartgym.membership.profile;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachAssignmentRepository extends JpaRepository<CoachAssignment, UUID> {
    boolean existsByCoachSubjectIgnoreCaseAndMemberSubjectIgnoreCaseAndEndedAtIsNull(
            String coachSubject, String memberSubject);

    List<CoachAssignment> findAllByCoachSubjectIgnoreCaseAndEndedAtIsNull(String coachSubject);

    boolean existsByCoachSubjectIgnoreCaseAndEndedAtIsNull(String coachSubject);

    @Query("select count(assignment) > 0 from CoachAssignment assignment "
            + "join Member member on lower(member.keycloakUserId) = lower(assignment.memberSubject) "
            + "where lower(assignment.coachSubject) = lower(:coachSubject) "
            + "and assignment.endedAt is null and member.status = com.smartgym.membership.profile.Member.Status.ACTIVE")
    boolean hasActiveAssignedMembers(@Param("coachSubject") String coachSubject);

    @Query("select assignment from CoachAssignment assignment "
            + "where lower(assignment.coachSubject) = lower(:coachSubject) "
            + "and lower(assignment.memberSubject) = lower(:memberSubject) "
            + "and assignment.endedAt is null")
    java.util.Optional<CoachAssignment> findActiveAssignment(
            @Param("coachSubject") String coachSubject,
            @Param("memberSubject") String memberSubject);

    @Query("select assignment from CoachAssignment assignment "
            + "where lower(assignment.coachSubject) = lower(:coachSubject) "
            + "and assignment.endedAt is null")
    List<CoachAssignment> findActiveAssignments(@Param("coachSubject") String coachSubject);
}
