package com.ecommerce.management.dto.common;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PageResponse<T>(
        List<T> data,
        int page,
        int limit,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages) {
}
