package com.shopifyplus.service;

import com.shopifyplus.exception.UnauthorizedException;
import com.shopifyplus.model.RefreshToken;
import com.shopifyplus.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final long refreshExpirationMs;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens,
                               @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.tokens = tokens;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    // Mint and persist a fresh refresh token for a user.
    public RefreshToken issue(String userId) {
        Instant now = Instant.now();
        return tokens.save(RefreshToken.builder()
                .token(randomToken())
                .userId(userId)
                .expiresAt(now.plus(refreshExpirationMs, ChronoUnit.MILLIS))
                .revoked(false)
                .createdAt(now)
                .build());
    }

    // Validate the presented token, revoke it, and hand back a brand-new one (rotation).
    // Reusing an already-rotated (revoked) token fails — that's the reuse-detection signal.
    public RefreshToken verifyAndRotate(String tokenValue) {
        RefreshToken existing = tokens.findByToken(tokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        existing.setRevoked(true);
        tokens.save(existing);
        return issue(existing.getUserId());
    }

    // Idempotent logout — revoking an unknown/already-revoked token is a no-op.
    public void revoke(String tokenValue) {
        tokens.findByToken(tokenValue).ifPresent(rt -> {
            rt.setRevoked(true);
            tokens.save(rt);
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
