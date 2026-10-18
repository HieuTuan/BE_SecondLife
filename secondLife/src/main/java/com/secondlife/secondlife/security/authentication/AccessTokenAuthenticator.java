package com.secondlife.secondlife.security.authentication;

import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider.AccessToken;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticator {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public CustomUserDetails authenticate(String token) {
        AccessToken accessToken;
        try {
            accessToken = tokenProvider.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid access token", ex);
        }

        CustomUserDetails userDetails = userDetailsService.loadUserById(accessToken.userId());
        if (accessToken.tokenVersion() != userDetails.getTokenVersion()) {
            throw new BadCredentialsException("Access token has been revoked");
        }
        if (!userDetails.isEnabled()) {
            throw new DisabledException("Account is not active");
        }
        return userDetails;
    }
}
