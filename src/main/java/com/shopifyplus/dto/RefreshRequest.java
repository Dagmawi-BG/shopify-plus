package com.shopifyplus.dto;

import jakarta.validation.constraints.NotBlank;

// Used by both /refresh and /logout — the client presents the opaque refresh token.
public record RefreshRequest(@NotBlank String refreshToken) {
}
