package com.ecommerce.management.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.enums.RecordStatus;

@DataJpaTest
@ActiveProfiles("test")
class CustomerRepositoryTest {

    @Autowired CustomerRepository customerRepository;

    @Test
    void shouldCheckEmailExistence() {
        Customer saved = customerRepository.saveAndFlush(customer("ada@example.com"));

        assertTrue(customerRepository.existsByEmail("ada@example.com"));
        assertFalse(customerRepository.existsByEmail("grace@example.com"));
        assertFalse(customerRepository.existsByEmailAndIdNot("ada@example.com", saved.getId()));
        assertTrue(customerRepository.existsByEmailAndIdNot("ada@example.com", saved.getId() + 1));
    }

    private Customer customer(String email) {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = new Customer();
        customer.setName("Ada Lovelace");
        customer.setEmail(email);
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return customer;
    }
}
