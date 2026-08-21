package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderStatusRequest(
        @NotBlank @Pattern(regexp = "pending|paid|shipped|cancelled") String status
) {
}
