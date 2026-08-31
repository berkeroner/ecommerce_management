package com.ecommerce.management.entity;

import com.ecommerce.management.entity.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, columnDefinition = "char(36)")
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private OutboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
