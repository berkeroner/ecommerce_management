package com.ecommerce.management.entity;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payments_payment_no", columnNames = "payment_no"))
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "payment_no", nullable = false, length = 50)
    private String paymentNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentMethod method;
    @Column(nullable = false, length = 50)
    private String provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentStatus status;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(name = "transaction_id", length = 190)
    private String transactionId;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
