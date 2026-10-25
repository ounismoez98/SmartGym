package com.smartgym.membership.assistant;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GymPolicyRepository extends JpaRepository<GymPolicy, UUID> {
    List<GymPolicy> findAllByValidatedTrueOrderByTitleAsc();
}
