package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.management.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
    
}
