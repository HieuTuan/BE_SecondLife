package com.secondlife.secondlife.security.authentication;

import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider.AccessToken;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import com.secondlife.secondlife.security.userdetails.UserPrincipalCache;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AccessTokenAuthenticator {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserPrincipalCache userPrincipalCache;

    public AccessTokenAuthenticator(JwtTokenProvider tokenProvider, CustomUserDetailsService userDetailsService) {
        this(tokenProvider, userDetailsService, new UserPrincipalCache());
    }

    @Autowired
    public AccessTokenAuthenticator(JwtTokenProvider tokenProvider,
                                    CustomUserDetailsService userDetailsService,
                                    UserPrincipalCache userPrincipalCache) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.userPrincipalCache = userPrincipalCache != null ? userPrincipalCache : new UserPrincipalCache();
    }

    public CustomUserDetails authenticate(String token) {
        AccessToken accessToken;
        try {
            accessToken = tokenProvider.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid access token", ex);
        }

        UUID userId = accessToken.userId();
        CustomUserDetails userDetails = userPrincipalCache.get(userId);

        if (userDetails == null || userDetails.getTokenVersion() != accessToken.tokenVersion()) {
            userDetails = userDetailsService.loadUserById(userId);
            if (userDetails.isEnabled() && userDetails.getTokenVersion() == accessToken.tokenVersion()) {
                userPrincipalCache.put(userId, userDetails);
            }
        }

        if (accessToken.tokenVersion() != userDetails.getTokenVersion()) {
            userPrincipalCache.evict(userId);
            throw new BadCredentialsException("Access token has been revoked");
        }
        if (!userDetails.isEnabled()) {
            userPrincipalCache.evict(userId);
            throw new DisabledException("Account is not active");
        }
        return userDetails;
    }

    public void evict(UUID userId) {
        userPrincipalCache.evict(userId);
    }

    public void clearCache() {
        userPrincipalCache.clear();
    }
}
