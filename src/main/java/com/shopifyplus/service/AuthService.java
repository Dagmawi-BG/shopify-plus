package com.shopifyplus.service;

import com.shopifyplus.dto.AuthResponse;
import com.shopifyplus.dto.LoginRequest;
import com.shopifyplus.dto.RegisterRequest;
import com.shopifyplus.exception.BadRequestException;
import com.shopifyplus.exception.UnauthorizedException;
import com.shopifyplus.model.User;
import com.shopifyplus.repository.UserRepository;
import com.shopifyplus.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final CartService carts;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens, CartService carts) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.carts = carts;
    }

    // Always creates a normal "user" role account. Login is by email.
    public AuthResponse register(RegisterRequest req, String guestCartId) {
        String email = req.email().toLowerCase();
        String username = req.username().toLowerCase();

        if (users.existsByEmail(email)) {
            throw new BadRequestException("Email already registered");
        }
        if (users.existsByUsername(username)) {
            throw new BadRequestException("Username already taken");
        }

        User user = users.save(User.builder()
                .name(req.name())
                .username(username)
                .email(email)
                .password(encoder.encode(req.password()))
                .role("user")
                .build());

        carts.mergeGuestCart(guestCartId, user.getId());
        return response(user);
    }

    public AuthResponse login(LoginRequest req, String guestCartId) {
        User user = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!encoder.matches(req.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        carts.mergeGuestCart(guestCartId, user.getId());
        return response(user);
    }

    // Exchange a valid refresh token for a new access token (and a rotated refresh token).
    public AuthResponse refresh(String refreshTokenValue) {
        var rotated = refreshTokens.verifyAndRotate(refreshTokenValue);
        User user = users.findById(rotated.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        String access = jwt.generate(user.getId(), user.getRole());
        return new AuthResponse(access, rotated.getToken(), AuthResponse.PublicUser.from(user));
    }

    public void logout(String refreshTokenValue) {
        refreshTokens.revoke(refreshTokenValue);
    }

    private AuthResponse response(User user) {
        String token = jwt.generate(user.getId(), user.getRole());
        String refresh = refreshTokens.issue(user.getId()).getToken();
        return new AuthResponse(token, refresh, AuthResponse.PublicUser.from(user));
    }
}
