package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIntentRequest(
        @NotBlank String orderId
) {
}
