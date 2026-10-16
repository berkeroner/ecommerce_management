package com.ecommerce.management.dto.auth;

import com.ecommerce.management.entity.enums.UserRole;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long customerId,
        UserRole role) {
}
