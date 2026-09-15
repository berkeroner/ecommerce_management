package com.ecommerce.management.service;

import java.time.LocalDateTime;
import com.ecommerce.management.dto.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.customer.CustomerRequest;
import com.ecommerce.management.dto.customer.CustomerResponse;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(int page, int limit, String status, String search) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.valueOf(422),
                    "page must be at least 1 and limit must be between 1 and 100");
        }
        RecordStatus recordStatus = null;
        if (status != null) {
            recordStatus = switch (status) {
                case "active" -> RecordStatus.ACTIVE;
                case "passive" -> RecordStatus.PASSIVE;
                default -> throw new ResponseStatusException(HttpStatus.valueOf(422),
                        "status must be active or passive");
            };
        }
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var pageable = PageRequest.of(page - 1, limit, Sort.by("id"));
        org.springframework.data.domain.Page<Customer> customers;
        if (!term.isEmpty()) {
            customers = customerRepository.search(recordStatus, term, pageable);
        } else if (recordStatus != null) {
            customers = customerRepository.findAllByStatus(recordStatus, pageable);
        } else {
            customers = customerRepository.findAll(pageable);
        }
        return new PageResponse<>(customers.getContent().stream().map(this::toResponse).toList(),
                page, limit, customers.getTotalElements(), customers.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return toResponse(getCustomer(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailIsAvailable(email, null);

        LocalDateTime now = LocalDateTime.now();
        Customer customer = new Customer();
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        String email = normalizeEmail(request.email());
        ensureEmailIsAvailable(email, id);

        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setUpdatedAt(LocalDateTime.now());

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse updateStatus(Long id, RecordStatus status) {
        Customer customer = getCustomer(id);
        customer.setStatus(status);
        customer.setUpdatedAt(LocalDateTime.now());

        return toResponse(customerRepository.save(customer));
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Customer not found: " + id));
    }

    private void ensureEmailIsAvailable(String email, Long currentCustomerId) {
        boolean exists = currentCustomerId == null
                ? customerRepository.existsByEmail(email)
                : customerRepository.existsByEmailAndIdNot(email, currentCustomerId);

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Customer email already exists: " + email);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
