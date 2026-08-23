package com.qlda.authservice.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenStoreService {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String token, Long userId, String username, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_refresh_token
                    (token_hash, user_id, username, expires_at, revoked, created_at, updated_at)
                VALUES (?, ?, ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (token_hash) DO UPDATE SET
                    user_id = EXCLUDED.user_id,
                    username = EXCLUDED.username,
                    expires_at = EXCLUDED.expires_at,
                    revoked = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                """, tokenHash(token), userId, username, Timestamp.from(expiresAt));
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM auth_refresh_token
                WHERE token_hash = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP
                """, Integer.class, tokenHash(token));
        return count != null && count > 0;
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE auth_refresh_token
                SET revoked = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                """, tokenHash(token));
    }

    @Transactional
    public void replace(String oldToken, String newToken, Long userId, String username, Instant expiresAt) {
        revoke(oldToken);
        save(newToken, userId, username, expiresAt);
    }

    private String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
