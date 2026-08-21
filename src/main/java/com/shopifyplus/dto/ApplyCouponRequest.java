package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;

// Only the code — the subtotal is computed server-side from the real cart (Option B).
public record ApplyCouponRequest(
        @NotBlank String code
) {
}
