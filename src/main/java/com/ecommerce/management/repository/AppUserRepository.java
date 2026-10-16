package com.ecommerce.management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.management.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByCustomerId(Long customerId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
