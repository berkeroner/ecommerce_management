package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.management.entity.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

}
