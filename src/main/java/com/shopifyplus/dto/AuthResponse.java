package com.shopifyplus.dto;

import com.shopifyplus.model.User;

public record AuthResponse(String token, String refreshToken, PublicUser user) {

    // Curated user view — never leaks the password hash.
    public record PublicUser(String id, String name, String username, String email, String role) {
        public static PublicUser from(User u) {
            return new PublicUser(u.getId(), u.getName(), u.getUsername(), u.getEmail(), u.getRole());
        }
    }
}
