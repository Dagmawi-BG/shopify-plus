package com.shopifyplus.util;

import com.shopifyplus.dto.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// Pagination helpers shared by list endpoints.
public final class Pagination {

    private Pagination() {
    }

    // ?page (1-based) & ?limit -> Spring Pageable (0-based), with safe defaults + cap.
    public static Pageable of(Integer page, Integer limit) {
        int p = Math.max(1, page == null ? 1 : page);
        int l = Math.min(100, Math.max(1, limit == null ? 20 : limit));
        return PageRequest.of(p - 1, l);
    }

    // Spring Page -> our { data, pagination } envelope.
    public static <T> PagedResponse<T> toResponse(Page<T> result) {
        int page = result.getNumber() + 1;
        int limit = result.getSize();
        long total = result.getTotalElements();
        int totalPages = result.getTotalPages();
        return new PagedResponse<>(
                result.getContent(),
                new PagedResponse.PageMeta(page, limit, total, totalPages,
                        result.hasNext(), result.hasPrevious())
        );
    }
}
