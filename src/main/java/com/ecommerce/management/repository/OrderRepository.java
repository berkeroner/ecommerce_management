package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select o.id from Order o
            where o.createdAt <= :cutoff
              and o.status in :orderStatuses
              and exists (
                  select p.id from Payment p
                  where p.order = o and p.status = :pendingStatus
              )
              and not exists (
                  select p.id from Payment p
                  where p.order = o and p.status in :protectedPaymentStatuses
              )
            order by o.createdAt, o.id
            """)
    List<Long> findExpiredPendingOrderIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("pendingStatus") PaymentStatus pendingStatus,
            @Param("protectedPaymentStatuses") Collection<PaymentStatus> protectedPaymentStatuses,
            Pageable pageable);
}
