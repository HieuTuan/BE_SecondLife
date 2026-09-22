package com.secondlife.secondlife.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.secondlife.secondlife.dto.GoogleUserInfo;
import com.secondlife.secondlife.exception.UnauthorizedException;
import com.secondlife.secondlife.service.GoogleAuthService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    @Value("${app.google.client-id:}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        if (this.verifier == null) {
            GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            );

            if (googleClientId != null && !googleClientId.trim().isEmpty()) {
                builder.setAudience(Collections.singletonList(googleClientId.trim()));
                log.info("Google ID token verifier initialized with client ID: {}", googleClientId.trim());
            } else {
                log.warn("GOOGLE_CLIENT_ID is not configured. Token audience check skipped (Google signature & expiry still verified).");
            }

            this.verifier = builder.build();
        }
    }

    public void setVerifier(GoogleIdTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public GoogleUserInfo verifyToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Google ID token is required");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token verification failed: token is invalid or expired");
                throw new UnauthorizedException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            String sub = payload.getSubject();

            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Google account has no email associated");
            }

            return new GoogleUserInfo(sub, email, emailVerified, name, picture);
        } catch (GeneralSecurityException | IOException e) {
            log.error("Error verifying Google ID token: {}", e.getMessage());
            throw new UnauthorizedException("Failed to verify Google ID token: " + e.getMessage());
        }
    }
}
