package com.secondlife.secondlife.security.jwt;

import com.secondlife.secondlife.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final String jwtSecret;
    private final long accessExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs
    ) {
        this.jwtSecret = jwtSecret;
        this.accessExpirationMs = accessExpirationMs;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        if (jwtSecret.matches("^[0-9a-fA-F]+$") && jwtSecret.length() >= 64) {
            keyBytes = HexFormat.of().parseHex(jwtSecret);
        } else {
            byte[] rawBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (rawBytes.length < 32) {
                keyBytes = Arrays.copyOf(rawBytes, 32);
            } else {
                keyBytes = rawBytes;
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("email", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            String type = claims.get("type", String.class);
            return "ACCESS".equals(type);
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
