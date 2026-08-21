package com.shopifyplus.dto;

import jakarta.validation.constraints.NotNull;

// quantity <= 0 removes the line (matches the Node behavior), so no @Positive here.
public record UpdateItemRequest(
        @NotNull Integer quantity
) {
}
