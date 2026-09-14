package com.ecommerce.management.dto.common;

import java.util.List;

public record PageResponse<T>(List<T> data, int page, int limit, long totalElements, int totalPages) {
}
