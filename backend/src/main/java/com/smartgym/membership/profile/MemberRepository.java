package com.smartgym.membership.profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByKeycloakUserId(String keycloakUserId);

    List<Member> findAllByKeycloakUserIdIn(Collection<String> keycloakUserIds);
}
