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

    @Test
    void shouldReturnOrderedPagesAndAccurateTotals() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(customerRepository.saveAndFlush(customer("page" + i + "@example.com")).getId());
        }
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by("id"));
        var first = customerRepository.findAll(pageable);
        var second = customerRepository.findAll(pageable.next());
        org.junit.jupiter.api.Assertions.assertEquals(ids.subList(0, 2),
                first.getContent().stream().map(Customer::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(ids.subList(2, 3),
                second.getContent().stream().map(Customer::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(3, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
        assertTrue(customerRepository.findAll(pageable.next().next()).isEmpty());
    }

    @Test
    void shouldSearchNameOrEmailAndFilterBeforePagination() {
        java.util.List<Long> activeIds = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Customer customer = customer(i == 0 ? "name-match@example.com" : "ada" + i + "@example.com");
            customer.setName(i == 0 ? "Ada Lovelace" : "Grace Hopper");
            customer.setStatus(i == 4 ? RecordStatus.PASSIVE : RecordStatus.ACTIVE);
            customerRepository.saveAndFlush(customer);
            if (i != 4) activeIds.add(customer.getId());
        }
        var pageable = org.springframework.data.domain.PageRequest.of(0, 2,
                org.springframework.data.domain.Sort.by("id"));
        org.junit.jupiter.api.Assertions.assertEquals(5,
                customerRepository.search(null, "ada", pageable).getTotalElements());
        var first = customerRepository.search(RecordStatus.ACTIVE, "ada", pageable);
        var second = customerRepository.search(RecordStatus.ACTIVE, "ada", pageable.next());
        org.junit.jupiter.api.Assertions.assertEquals(activeIds.subList(0, 2),
                first.getContent().stream().map(Customer::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(activeIds.subList(2, 4),
                second.getContent().stream().map(Customer::getId).toList());
        org.junit.jupiter.api.Assertions.assertEquals(4, first.getTotalElements());
        org.junit.jupiter.api.Assertions.assertEquals(2, first.getTotalPages());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                customerRepository.findAllByStatus(RecordStatus.PASSIVE, pageable).getTotalElements());
        assertTrue(customerRepository.search(null, "missing", pageable).isEmpty());
        assertTrue(customerRepository.search(null, "%_", pageable).isEmpty());
        Customer literal = customer("literal@example.com");
        literal.setName("Test %_ Name");
        customerRepository.saveAndFlush(literal);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                customerRepository.search(null, "%_", pageable).getTotalElements());
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
