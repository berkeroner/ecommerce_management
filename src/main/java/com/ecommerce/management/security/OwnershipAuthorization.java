package com.ecommerce.management.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.entity.enums.RecordStatus;

@Component("authz")
public class OwnershipAuthorization {
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public OwnershipAuthorization(OrderRepository orderRepository, CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
    }

    public boolean canAccessCustomer(Authentication authentication, Long customerId) {
        if (isAdmin(authentication)) {
            return true;
        }
        return customerId.equals(customerId(authentication))
                && customerRepository.existsByIdAndStatus(customerId, RecordStatus.ACTIVE);
    }

    public boolean canCreateOrder(Authentication authentication, OrderRequest request) {
        return request != null && canAccessCustomer(authentication, request.customerId());
    }

    public boolean canAccessOrder(Authentication authentication, Long orderId) {
        if (isAdmin(authentication)) {
            return true;
        }
        Long customerId = customerId(authentication);
        return customerId != null && orderRepository.existsByIdAndCustomerIdAndCustomerStatus(
                orderId, customerId, RecordStatus.ACTIVE);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private Long customerId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        Number claim = jwtAuthentication.getToken().getClaim("customer_id");
        return claim == null ? null : claim.longValue();
    }
}
