package com.shopifyplus.dto;

import jakarta.validation.constraints.PositiveOrZero;

// Update-product request: every field optional, but any present value must be valid.
public record ProductUpdateRequest(
        String name,
        @PositiveOrZero Double price,
        String category,
        @PositiveOrZero Integer stock,
        String description
) {
}
