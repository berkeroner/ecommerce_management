package com.ecommerce.management.controller;

import java.net.URI;
import com.ecommerce.management.dto.common.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.management.dto.customer.CustomerRequest;
import com.ecommerce.management.dto.customer.CustomerResponse;
import com.ecommerce.management.dto.customer.CustomerStatusRequest;
import com.ecommerce.management.service.CustomerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CustomerResponse> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return customerService.findAll(page, limit, status, search);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canAccessCustomer(authentication, #id)")
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/customers/" + customer.id()))
                .body(customer);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canAccessCustomer(authentication, #id)")
    public CustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CustomerStatusRequest request) {
        return customerService.updateStatus(id, request.status());
    }
}
