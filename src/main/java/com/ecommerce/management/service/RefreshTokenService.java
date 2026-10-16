package com.ecommerce.management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecommerce.management.entity.AppUser;
import com.ecommerce.management.entity.RefreshToken;
import com.ecommerce.management.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokenRepository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokenRepository,
            @Value("${app.security.jwt.refresh-token-ttl:30d}") Duration refreshTokenTtl) {
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        this.tokenRepository = tokenRepository;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public IssuedRefreshToken issue(AppUser user) {
        String value = generateToken();
        Instant now = Instant.now();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(value));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(refreshTokenTtl));
        tokenRepository.save(token);

        return new IssuedRefreshToken(value, refreshTokenTtl.toSeconds());
    }

    public AppUser consume(String value) {
        Instant now = Instant.now();
        RefreshToken token = tokenRepository.findByTokenHash(hash(value)).orElse(null);
        if (token == null || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return null;
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    public void revoke(String value) {
        RefreshToken token = tokenRepository.findByTokenHash(hash(value)).orElse(null);
        if (token != null && token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
        }
    }

    String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }
}
