package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ecommerce.management.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            select oi from OrderItem oi
            join fetch oi.product
            where oi.order.id = :orderId
            order by oi.product.id
            """)
    List<OrderItem> findAllByOrderId(@Param("orderId") Long orderId);
}
