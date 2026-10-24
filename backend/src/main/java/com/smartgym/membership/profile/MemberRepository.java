package com.smartgym.membership.profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID>, JpaSpecificationExecutor<Member> {
    @Query("select member from Member member "
            + "where lower(member.keycloakUserId) = lower(:keycloakUserId)")
    Optional<Member> findByKeycloakUserId(@Param("keycloakUserId") String keycloakUserId);

    @Query("select member from Member member "
            + "where lower(member.keycloakUserId) in :keycloakUserIds")
    List<Member> findAllByKeycloakUserIdIn(@Param("keycloakUserIds") Collection<String> keycloakUserIds);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
