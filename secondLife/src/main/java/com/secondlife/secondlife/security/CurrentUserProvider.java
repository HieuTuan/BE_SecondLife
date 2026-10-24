package com.secondlife.secondlife.security;

import com.secondlife.secondlife.exception.UnauthorizedException;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the active user ID from the authenticated principal. */
@Component
public class CurrentUserProvider {

    public UUID resolveUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        throw new UnauthorizedException("Authentication is required");
    }
}
