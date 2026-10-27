package com.secondlife.secondlife.security.authentication;

import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.UserSecurityState;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider.AccessToken;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import com.secondlife.secondlife.security.userdetails.UserPrincipalCache;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
        for (int attempt = 0; attempt < 2; attempt++) {
            long generation = userPrincipalCache.generation();
            UserSecurityState state;
            try {
                state = userDetailsService.loadSecurityState(userId);
            } catch (UsernameNotFoundException missingUser) {
                userPrincipalCache.evict(userId);
                throw missingUser;
            }
            if (accessToken.tokenVersion() != state.getTokenVersion()) {
                userPrincipalCache.evict(userId);
                throw new BadCredentialsException("Access token has been revoked");
            }
            if (state.getAccountStatus() != AccountStatus.ACTIVE) {
                userPrincipalCache.evict(userId);
                throw new DisabledException("Account is not active");
            }

            CustomUserDetails details = userPrincipalCache.get(userId);
            if (details != null && details.matches(state)) {
                return details;
            }
            details = userDetailsService.loadUserById(userId);
            if (details.matches(state)) {
                userPrincipalCache.putIfUnchanged(userId, details, generation);
                return details;
            }
            // A committed security mutation occurred between the two reads.
            userPrincipalCache.evict(userId);
        }
        throw new BadCredentialsException("Account security state changed; retry authentication");
    }

    public void evict(UUID userId) {
        userPrincipalCache.evict(userId);
    }

    public void clearCache() {
        userPrincipalCache.clear();
    }
}
