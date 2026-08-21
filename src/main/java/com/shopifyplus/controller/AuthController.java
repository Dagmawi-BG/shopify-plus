package com.shopifyplus.controller;

import com.shopifyplus.dto.AuthResponse;
import com.shopifyplus.dto.LoginRequest;
import com.shopifyplus.dto.RefreshRequest;
import com.shopifyplus.dto.RegisterRequest;
import com.shopifyplus.service.AuthService;
import com.shopifyplus.service.CartService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCartId,
            HttpServletResponse response) {
        AuthResponse body = authService.register(req, guestCartId);
        clearGuestCookie(guestCartId, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest req,
            @CookieValue(value = CartService.GUEST_COOKIE_NAME, required = false) String guestCartId,
            HttpServletResponse response) {
        AuthResponse body = authService.login(req, guestCartId);
        clearGuestCookie(guestCartId, response);
        return body;
    }

    // The guest cart has been merged into the account; expire the now-defunct cookie.
    private void clearGuestCookie(String guestCartId, HttpServletResponse response) {
        if (guestCartId == null || guestCartId.isBlank()) {
            return;
        }
        Cookie cookie = new Cookie(CartService.GUEST_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
