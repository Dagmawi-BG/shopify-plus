package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AddItemRequest(
        @NotBlank String productId,
        @Positive Integer quantity // optional; defaults to 1 in the service
) {
}
