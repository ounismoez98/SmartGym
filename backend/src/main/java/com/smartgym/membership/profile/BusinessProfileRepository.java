package com.smartgym.membership.profile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, String> {
    Optional<BusinessProfile> findByMemberProfileId(UUID memberProfileId);
}
