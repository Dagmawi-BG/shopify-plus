package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Create-product request (replaces the Zod create schema).
public record ProductRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero Double price, // wrapper so a missing price fails @NotNull
        @NotBlank String category,
        @PositiveOrZero Integer stock, // optional; defaults to 0 in the service
        String description
) {
}
