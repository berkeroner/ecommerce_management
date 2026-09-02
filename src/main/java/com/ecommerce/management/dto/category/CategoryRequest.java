package com.ecommerce.management.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 190) String slug,
        @NotNull Boolean isActive) {
}
