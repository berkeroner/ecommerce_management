package com.ecommerce.management.dto.auth;

import com.ecommerce.management.entity.enums.UserRole;

public record CurrentUserResponse(
        Long userId,
        Long customerId,
        String email,
        UserRole role) {
}
