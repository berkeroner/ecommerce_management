package com.ecommerce.management.dto.auth;

import com.ecommerce.management.entity.enums.UserRole;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        Long customerId,
        UserRole role) {

    @Override
    public String toString() {
        return "AuthResponse[accessToken=[REDACTED], refreshToken=[REDACTED], tokenType="
                + tokenType + ", expiresIn=" + expiresIn + ", refreshExpiresIn="
                + refreshExpiresIn + ", customerId=" + customerId + ", role=" + role + "]";
    }
}
