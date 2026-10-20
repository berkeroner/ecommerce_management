package com.ecommerce.management.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.auth.AuthResponse;
import com.ecommerce.management.dto.auth.CurrentUserResponse;
import com.ecommerce.management.dto.auth.LoginRequest;
import com.ecommerce.management.dto.auth.RegisterRequest;
import com.ecommerce.management.entity.AppUser;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.entity.enums.UserRole;
import com.ecommerce.management.repository.AppUserRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.security.JwtService;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AppUserRepository userRepository, CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email) || customerRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
        Customer customer = new Customer();
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.CUSTOMER);
        user.setCustomer(customer);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        return response(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        ensureCustomerIsActive(user);
        return response(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        Long customerId = user.getCustomer() == null ? null : user.getCustomer().getId();
        String name = user.getCustomer() == null ? null : user.getCustomer().getName();
        return new CurrentUserResponse(name, user.getId(), customerId,
                user.getEmail(), user.getRole());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        AppUser user = refreshTokenService.consume(refreshToken);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        ensureCustomerIsActive(user);
        return response(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse response(AppUser user) {
        JwtService.IssuedToken accessToken = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
        Long customerId = user.getCustomer() == null ? null : user.getCustomer().getId();
        return new AuthResponse(accessToken.value(), refreshToken.value(), "Bearer",
                accessToken.expiresInSeconds(), refreshToken.expiresInSeconds(), customerId,
                user.getRole());
    }

    private void ensureCustomerIsActive(AppUser user) {
        if (user.getCustomer() != null && user.getCustomer().getStatus() != RecordStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is not active");
        }
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
