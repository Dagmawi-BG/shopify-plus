package com.shopifyplus.dto;

import java.util.List;

// Envelope for paginated list endpoints: { data: [...], pagination: {...} }
public record PagedResponse<T>(List<T> data, PageMeta pagination) {

    public record PageMeta(
            int page,
            int limit,
            long totalItems,
            int totalPages,
            boolean hasNext,
            boolean hasPrev
    ) {
    }
}
