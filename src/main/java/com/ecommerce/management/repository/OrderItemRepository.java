package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.management.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
}
