package com.ecommerce.management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.management.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query("""
            select distinct c from Cart c
            left join fetch c.items i
            left join fetch i.product p
            left join fetch p.category
            where c.user.id = :userId
            """)
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}
