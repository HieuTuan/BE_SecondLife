package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.IdentityAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityAccessServiceImpl implements IdentityAccessService {
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyRole(UUID userId, Set<String> roleCodes) {
        return users.findByIdWithAuthorities(userId)
                .map(user -> roleCodes.stream().anyMatch(user::hasRole))
                .orElse(false);
    }
}
