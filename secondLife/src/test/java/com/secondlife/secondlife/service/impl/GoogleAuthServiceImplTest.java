package com.secondlife.secondlife.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.secondlife.secondlife.dto.GoogleUserInfo;
import com.secondlife.secondlife.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    private GoogleAuthServiceImpl googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthServiceImpl();
        ReflectionTestUtils.setField(googleAuthService, "googleClientId", "test-client-id");
        googleAuthService.setVerifier(verifier);
    }

    @Test
    void verifyToken_WhenBlank_ShouldThrowUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> googleAuthService.verifyToken(""));
        assertThrows(UnauthorizedException.class, () -> googleAuthService.verifyToken(null));
    }

    @Test
    void verifyToken_WhenVerifierReturnsNull_ShouldThrowUnauthorized() throws Exception {
        when(verifier.verify("invalid-token")).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> googleAuthService.verifyToken("invalid-token"));
    }

    @Test
    void verifyToken_WhenValid_ShouldReturnGoogleUserInfo() throws Exception {
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@gmail.com");
        payload.setEmailVerified(true);
        payload.setSubject("google-sub-12345");
        payload.set("name", "Test User");
        payload.set("picture", "https://avatar.google.com/pic.jpg");

        when(mockToken.getPayload()).thenReturn(payload);
        when(verifier.verify("valid-token")).thenReturn(mockToken);

        GoogleUserInfo userInfo = googleAuthService.verifyToken("valid-token");

        assertNotNull(userInfo);
        assertEquals("test@gmail.com", userInfo.email());
        assertEquals("Test User", userInfo.name());
        assertEquals("google-sub-12345", userInfo.sub());
        assertEquals("https://avatar.google.com/pic.jpg", userInfo.pictureUrl());
        assertTrue(userInfo.emailVerified());
    }

    @Test
    void verifyToken_WhenThrowsException_ShouldThrowUnauthorized() throws Exception {
        when(verifier.verify("error-token")).thenThrow(new GeneralSecurityException("Crypto error"));

        assertThrows(UnauthorizedException.class, () -> googleAuthService.verifyToken("error-token"));
    }
}
