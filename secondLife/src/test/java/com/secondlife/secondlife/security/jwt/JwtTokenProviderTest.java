package com.secondlife.secondlife.security.jwt;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider.AccessToken;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 60_000;

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
        testUser = new User("buyer@secondlife.com", "hashedPass", AccountStatus.ACTIVE);
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void generateAccessToken_ShouldProduceValidToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        AccessToken parsed = jwtTokenProvider.parseAccessToken(token);

        assertNotNull(token);
        assertEquals(testUser.getId(), parsed.userId());
        assertEquals("buyer@secondlife.com", parsed.email());
        assertEquals(0, parsed.tokenVersion());
    }

    @Test
    void parseAccessToken_WithExpiredToken_ShouldRejectIt() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, -1000L);
        String token = shortLivedProvider.generateAccessToken(testUser);

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseAccessToken(token));
    }

    @Test
    void parseAccessToken_WithTamperedToken_ShouldRejectIt() {
        String token = jwtTokenProvider.generateAccessToken(testUser) + "xyz";

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseAccessToken(token));
    }

    @Test
    void parseAccessToken_WithInvalidSignature_ShouldRejectIt() {
        String differentSecret = "994E635266556A586E3272357538782F413F4428472B4B6250645367566B5999";
        JwtTokenProvider otherProvider = new JwtTokenProvider(differentSecret, EXPIRATION_MS);
        String token = otherProvider.generateAccessToken(testUser);

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseAccessToken(token));
    }

    @Test
    void parseAccessToken_WithWrongType_ShouldRejectIt() {
        String token = Jwts.builder().subject(testUser.getId().toString()).claim("type", "REFRESH")
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(SECRET))).compact();

        assertThrows(UnsupportedJwtException.class, () -> jwtTokenProvider.parseAccessToken(token));
    }

    @Test
    void parseAccessToken_WithoutVersion_ShouldKeepLegacyDefaultOfZero() {
        String token = Jwts.builder().subject(testUser.getId().toString()).claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(SECRET))).compact();

        assertEquals(0, jwtTokenProvider.parseAccessToken(token).tokenVersion());
    }

    @Test
    void parseAccessToken_ShouldReadCurrentTokenVersion() {
        testUser.bumpTokenVersion();
        String token = jwtTokenProvider.generateAccessToken(testUser);

        assertEquals(1, jwtTokenProvider.parseAccessToken(token).tokenVersion());
    }

    @Test
    void parseAccessToken_WithoutSubject_ShouldRejectIt() {
        String token = Jwts.builder().claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(HexFormat.of().parseHex(SECRET))).compact();

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseAccessToken(token));
    }

    @Test
    void weakSecretFailsInsteadOfBeingPadded() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("short", EXPIRATION_MS));
    }
}
