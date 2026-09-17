package com.secondlife.secondlife.security;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.UnauthorizedException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper to resolve the active user ID from SecurityContext or CustomUserDetails.
 * When authorization is temporarily disabled during API testing via Swagger UI,
 * requests sent without a Bearer token will fallback to the seeded user or first available user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public UUID resolveUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        log.warn("No authenticated user details in request. Using fallback user for unauthenticated API testing.");
        return userRepository.findAll().stream()
                .findFirst()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException("No user found in database. Please register or seed a user first."));
    }

    public UUID resolveAdminId(CustomUserDetails currentAdmin) {
        if (currentAdmin != null && currentAdmin.getId() != null) {
            return currentAdmin.getId();
        }
        log.warn("No authenticated admin details in request. Using fallback admin for unauthenticated API testing.");
        return userRepository.findByEmailIgnoreCase("admin@secondlife.com")
                .map(User::getId)
                .or(() -> userRepository.findAll().stream().findFirst().map(User::getId))
                .orElseThrow(() -> new UnauthorizedException("No admin user found in database. Please seed initial admin data."));
    }
}
