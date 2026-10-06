package com.ecommerce.management.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    interface PollingCandidate {
        Long getId();
        String getTransactionId();
        String getIdempotencyKey();
        PaymentStatus getStatus();
    }

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.id = :orderId order by p.id")
    List<Payment> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.transactionId = :transactionId")
    Optional<Payment> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    @Query("""
            select p.id as id,
                   p.transactionId as transactionId,
                   p.idempotencyKey as idempotencyKey,
                   p.status as status
            from Payment p
            where p.status in :statuses and p.id > :afterId
            order by p.id
            """)
    List<PollingCandidate> findStatusPollingCandidates(
            @Param("statuses") Collection<PaymentStatus> statuses,
            @Param("afterId") Long afterId,
            Pageable pageable);
}
