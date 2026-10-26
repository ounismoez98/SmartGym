package com.smartgym.training.repository;

import java.util.UUID;

import com.smartgym.training.domain.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {}
