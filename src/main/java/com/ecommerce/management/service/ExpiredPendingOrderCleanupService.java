package com.ecommerce.management.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.entity.enums.AttachableType;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.ProductRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpiredPendingOrderCleanupService {

    private static final int BATCH_SIZE = 100;
    private static final Set<OrderStatus> DELETABLE_ORDER_STATUSES = EnumSet.of(
            OrderStatus.PENDING, OrderStatus.PROCESSING);
    private static final Set<PaymentStatus> PROTECTED_PAYMENT_STATUSES = EnumSet.of(
            PaymentStatus.PROCESSING, PaymentStatus.COMPLETED, PaymentStatus.REFUNDED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    @Value("${order.pending-payment-timeout:10m}")
    private Duration pendingPaymentTimeout;

    @Transactional
    public CleanupResult cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minus(pendingPaymentTimeout);
        int deletedCount = 0;

        while (true) {
            List<Long> orderIds = orderRepository.findExpiredPendingOrderIds(
                    cutoff,
                    DELETABLE_ORDER_STATUSES,
                    PaymentStatus.PENDING,
                    PROTECTED_PAYMENT_STATUSES,
                    PageRequest.of(0, BATCH_SIZE));

            if (orderIds.isEmpty()) {
                return new CleanupResult(deletedCount, cutoff);
            }

            int deletedInBatch = 0;
            for (Long orderId : orderIds) {
                if (deleteIfStillEligible(orderId, cutoff)) {
                    deletedCount++;
                    deletedInBatch++;
                }
            }

            // Every skipped candidate changed concurrently and will disappear from
            // the next query. This guard prevents an endless loop for inconsistent data.
            if (deletedInBatch == 0) {
                return new CleanupResult(deletedCount, cutoff);
            }
        }
    }

    private boolean deleteIfStillEligible(Long orderId, LocalDateTime cutoff) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || order.getCreatedAt().isAfter(cutoff)
                || !DELETABLE_ORDER_STATUSES.contains(order.getStatus())) {
            return false;
        }

        List<Payment> payments = paymentRepository.findAllByOrderIdForUpdate(orderId);
        boolean hasPendingPayment = payments.stream()
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.PENDING);
        boolean hasProtectedPayment = payments.stream()
                .anyMatch(payment -> PROTECTED_PAYMENT_STATUSES.contains(payment.getStatus()));
        if (!hasPendingPayment || hasProtectedPayment) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        for (OrderItem item : items) {
            int updated = productRepository.releaseStock(
                    item.getProduct().getId(), item.getQuantity(), now);
            if (updated != 1) {
                throw new IllegalStateException(
                        "Stock could not be restored for product: " + item.getProduct().getId());
            }
        }

        List<Long> paymentIds = payments.stream().map(Payment::getId).toList();
        deleteDependentRecords(orderId, paymentIds);
        entityManager.flush();
        entityManager.clear();
        return true;
    }

    private void deleteDependentRecords(Long orderId, List<Long> paymentIds) {
        if (!paymentIds.isEmpty()) {
            entityManager.createQuery("""
                            delete from Attachment a
                            where a.attachableType = :type and a.attachableId in :ids
                            """)
                    .setParameter("type", AttachableType.PAYMENT)
                    .setParameter("ids", paymentIds)
                    .executeUpdate();
            entityManager.createQuery("""
                            delete from OutboxEvent e
                            where e.aggregateType = :type and e.aggregateId in :ids
                            """)
                    .setParameter("type", "payment")
                    .setParameter("ids", paymentIds)
                    .executeUpdate();
        }

        entityManager.createQuery("""
                        delete from Attachment a
                        where a.attachableType = :type and a.attachableId = :orderId
                        """)
                .setParameter("type", AttachableType.ORDER)
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("""
                        delete from Address a
                        where a.addressableType = :type and a.addressableId = :orderId
                        """)
                .setParameter("type", AddressableType.ORDER)
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("delete from Shipment s where s.order.id = :orderId")
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("delete from OrderStatusHistory h where h.order.id = :orderId")
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("delete from Payment p where p.order.id = :orderId")
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("delete from OrderItem i where i.order.id = :orderId")
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("""
                        delete from OutboxEvent e
                        where e.aggregateType = :type and e.aggregateId = :orderId
                        """)
                .setParameter("type", "order")
                .setParameter("orderId", orderId)
                .executeUpdate();
        entityManager.createQuery("delete from Order o where o.id = :orderId")
                .setParameter("orderId", orderId)
                .executeUpdate();
    }

    public record CleanupResult(int deletedOrderCount, LocalDateTime cutoff) {
    }
}
