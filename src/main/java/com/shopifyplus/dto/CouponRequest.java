package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CouponRequest(
        @NotBlank String code,
        @NotBlank @Pattern(regexp = "percentage|fixed") String discountType,
        @NotNull @Positive Double amount,
        @NotNull LocalDate expiresAt, // e.g. "2099-01-01"
        Boolean active
) {
}
