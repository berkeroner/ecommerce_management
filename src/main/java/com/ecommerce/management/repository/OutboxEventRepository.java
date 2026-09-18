package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.management.entity.OutboxEvent;

import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
}
