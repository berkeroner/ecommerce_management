package com.ecommerce.management.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.enums.RecordStatus;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findAllByStatus(RecordStatus status, Pageable pageable);

    @Query("""
            select c from Customer c
            where (:status is null or c.status = :status)
              and (locate(:term, lower(c.name)) > 0 or locate(:term, lower(c.email)) > 0)
            """)
    Page<Customer> search(@Param("status") RecordStatus status, @Param("term") String term, Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
    
}
