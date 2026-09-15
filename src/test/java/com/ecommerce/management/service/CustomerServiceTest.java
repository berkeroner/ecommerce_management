package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.customer.CustomerRequest;
import com.ecommerce.management.dto.customer.CustomerResponse;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void shouldNormalizeSearchAndCombineWithStatus() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by("id"));
        when(customerRepository.search(RecordStatus.ACTIVE, "ada", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));
        when(customerRepository.search(null, "ada", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));
        customerService.findAll(1, 5, "active", " ADA ");
        customerService.findAll(1, 5, null, "ADA");
        org.mockito.Mockito.verify(customerRepository).search(RecordStatus.ACTIVE, "ada", pageable);
        org.mockito.Mockito.verify(customerRepository).search(null, "ada", pageable);
        org.mockito.Mockito.verifyNoMoreInteractions(customerRepository);
    }

    @Test
    void shouldIgnoreBlankSearchAndFilterByStatus() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by("id"));
        when(customerRepository.findAllByStatus(RecordStatus.PASSIVE, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));
        when(customerRepository.findAll(pageable)).thenReturn(org.springframework.data.domain.Page.empty(pageable));
        customerService.findAll(1, 5, "passive", "   ");
        customerService.findAll(1, 5, null, "   ");
        org.mockito.Mockito.verify(customerRepository).findAllByStatus(RecordStatus.PASSIVE, pageable);
        org.mockito.Mockito.verify(customerRepository).findAll(pageable);
    }

    @Test
    void shouldRejectInvalidStatusBeforeQuerying() {
        for (String status : new String[] {"", "ACTIVE", "unknown", " active "}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> customerService.findAll(1, 20, status, null));
            assertEquals(422, error.getStatusCode().value());
        }
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldMapCustomersAndReturnPageTotals() {
        var pageable = org.springframework.data.domain.PageRequest.of(1, 2,
                org.springframework.data.domain.Sort.by("id"));
        Customer customer = customer(3L, "Ada", "ada@example.com", RecordStatus.ACTIVE);
        when(customerRepository.findAll(pageable)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(customer), pageable, 3));
        var result = customerService.findAll(2, 2, null, null);
        assertEquals(3L, result.data().getFirst().id());
        assertEquals("ada@example.com", result.data().getFirst().email());
        assertEquals(2, result.page());
        assertEquals(2, result.limit());
        assertEquals(3, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    void shouldReturnEmptyPageAndPreserveTotalCount() {
        var pageable = org.springframework.data.domain.PageRequest.of(2, 5,
                org.springframework.data.domain.Sort.by("id"));
        when(customerRepository.findAll(pageable)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(), pageable, 7));
        var result = customerService.findAll(3, 5, null, null);
        assertEquals(java.util.List.of(), result.data());
        assertEquals(7, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    void shouldRejectInvalidPaginationBeforeQuerying() {
        for (int[] values : new int[][] {{0, 20}, {-1, 20}, {1, 0}, {1, -1}, {1, 101}}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> customerService.findAll(values[0], values[1], null, null));
            assertEquals(422, error.getStatusCode().value());
        }
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldCreateActiveCustomerAndNormalizeEmail() {
        CustomerRequest request = new CustomerRequest(
                " Ayse Demir ", " AYSE.DEMIR@EXAMPLE.COM ");

        when(customerRepository.existsByEmail("ayse.demir@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return customer;
        });

        CustomerResponse response = customerService.create(request);

        assertEquals(1L, response.id());
        assertEquals("Ayse Demir", response.name());
        assertEquals("ayse.demir@example.com", response.email());
        assertEquals(RecordStatus.ACTIVE, response.status());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() {
        CustomerRequest request = new CustomerRequest(
                "Ayse Demir", "AYSE.DEMIR@EXAMPLE.COM");

        when(customerRepository.existsByEmail("ayse.demir@example.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void shouldReturnNotFoundWhenCustomerDoesNotExist() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerService.findById(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldUpdateCustomerAndPreserveStatus() {
        Customer customer = customer(
                1L, "Ayse Demir", "ayse.demir@example.com", RecordStatus.PASSIVE);
        CustomerRequest request = new CustomerRequest(
                " Mehmet Kaya ", " MEHMET.KAYA@EXAMPLE.COM ");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmailAndIdNot("mehmet.kaya@example.com", 1L))
                .thenReturn(false);
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = customerService.update(1L, request);

        assertEquals("Mehmet Kaya", response.name());
        assertEquals("mehmet.kaya@example.com", response.email());
        assertEquals(RecordStatus.PASSIVE, response.status());
        verify(customerRepository).save(customer);
    }

    @Test
    void shouldReturnConflictWhenUpdatingToAnotherCustomersEmail() {
        Customer customer = customer(
                1L, "Ayse Demir", "ayse.demir@example.com", RecordStatus.ACTIVE);
        CustomerRequest request = new CustomerRequest(
                "Ayse Demir", "existing.customer@example.com");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.existsByEmailAndIdNot("existing.customer@example.com", 1L))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerService.update(1L, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void shouldUpdateCustomerStatus() {
        Customer customer = customer(
                1L, "Ayse Demir", "ayse.demir@example.com", RecordStatus.ACTIVE);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = customerService.updateStatus(1L, RecordStatus.PASSIVE);

        assertEquals(RecordStatus.PASSIVE, response.status());
        verify(customerRepository).save(customer);
    }

    private Customer customer(
            Long id,
            String name,
            String email,
            RecordStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName(name);
        customer.setEmail(email);
        customer.setStatus(status);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return customer;
    }
}
