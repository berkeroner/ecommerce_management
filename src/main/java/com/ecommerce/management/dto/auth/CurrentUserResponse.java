package com.ecommerce.management.dto.auth;

import com.ecommerce.management.entity.enums.UserRole;

public record CurrentUserResponse(
        String name,
        Long userId,
        Long customerId,
        String email,
        UserRole role) {
}
