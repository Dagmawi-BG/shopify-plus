package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RoleRequest(
        @NotBlank @Pattern(regexp = "user|admin") String role
) {
}
