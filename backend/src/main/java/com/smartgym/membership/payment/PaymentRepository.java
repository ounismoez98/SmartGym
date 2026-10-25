package com.smartgym.membership.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    List<Payment> findAllByCorrectsPaymentIdOrderByCreatedAtAsc(UUID correctsPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(payment.amountCents), 0) from Payment payment "
            + "where payment.memberId = :memberId and payment.status = :status")
    long sumAmountCentsByMemberIdAndStatus(
            @Param("memberId") UUID memberId, @Param("status") Payment.Status status);
}
