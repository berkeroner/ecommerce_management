package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.management.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
