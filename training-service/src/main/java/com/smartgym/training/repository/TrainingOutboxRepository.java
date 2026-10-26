package com.smartgym.training.repository;

import java.util.List;
import java.util.UUID;

import com.smartgym.training.domain.TrainingOutboxMessage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface TrainingOutboxRepository extends JpaRepository<TrainingOutboxMessage, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from TrainingOutboxMessage message where message.publishedAt is null order by message.createdAt")
    List<TrainingOutboxMessage> lockUnpublished(Pageable pageable);
}
