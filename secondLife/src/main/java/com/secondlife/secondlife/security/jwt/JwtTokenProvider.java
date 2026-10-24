package com.secondlife.secondlife.security.jwt;

import com.secondlife.secondlife.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final long accessExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs
    ) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must be configured");
        }
        byte[] keyBytes;
        if (jwtSecret.matches("^[0-9a-fA-F]+$") && jwtSecret.length() % 2 == 0) {
            keyBytes = HexFormat.of().parseHex(jwtSecret);
        } else {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 256 bits");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.parser = Jwts.parser().verifyWith(signingKey).build();
        this.accessExpirationMs = accessExpirationMs;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("type", "ACCESS")
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public AccessToken parseAccessToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (!"ACCESS".equals(claims.get("type", String.class))) {
            throw new UnsupportedJwtException("JWT token is not an access token");
        }
        if (claims.getSubject() == null) {
            throw new MalformedJwtException("JWT subject is required");
        }
        Number version = claims.get("tokenVersion", Number.class);
        return new AccessToken(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                version == null ? 0 : version.longValue()
        );
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public record AccessToken(UUID userId, String email, long tokenVersion) {
    }
}
