package com.secondlife.secondlife.security.jwt;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 60000; // 1 min

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);

        testUserId = UUID.randomUUID();
        testUser = new User("buyer@secondlife.com", "hashedPass", AccountStatus.ACTIVE);

        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, testUserId);
    }

    @Test
    void generateAccessToken_ShouldProduceValidToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(testUserId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("buyer@secondlife.com", jwtTokenProvider.getEmailFromToken(token));
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, -1000L);
        String token = shortLivedProvider.generateAccessToken(testUser);

        assertFalse(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_WithTamperedToken_ShouldReturnFalse() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        String tamperedToken = token + "xyz";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    void validateToken_WithInvalidSignature_ShouldReturnFalse() {
        String differentSecret = "994E635266556A586E3272357538782F413F4428472B4B6250645367566B5999";
        JwtTokenProvider otherProvider = new JwtTokenProvider(differentSecret, EXPIRATION_MS);
        String token = otherProvider.generateAccessToken(testUser);

        assertFalse(jwtTokenProvider.validateToken(token));
    }
}
