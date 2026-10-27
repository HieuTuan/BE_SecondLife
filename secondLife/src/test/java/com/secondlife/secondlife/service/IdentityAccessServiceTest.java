package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.IdentityAccessServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAccessServiceTest {
    @Mock
    private UserRepository users;

    @InjectMocks
    private IdentityAccessServiceImpl identityAccess;

    @ParameterizedTest
    @ValueSource(strings = {"seller", "admin"})
    void roleLookupKeepsCaseInsensitiveMatchingAndUsesCurrentAuthorities(String roleCode) {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.addRole(new Role(roleCode, roleCode, null));
        when(users.findByIdWithAuthorities(userId)).thenReturn(Optional.of(user));

        assertTrue(identityAccess.hasAnyRole(userId, Set.of("SELLER", "ADMIN")));
        assertFalse(identityAccess.hasAnyRole(userId, Set.of("BUYER")));

        verify(users, times(2)).findByIdWithAuthorities(userId);
        verifyNoMoreInteractions(users);
    }

    @Test
    void missingUserHasNoMatchingRole() {
        UUID userId = UUID.randomUUID();
        when(users.findByIdWithAuthorities(userId)).thenReturn(Optional.empty());

        assertFalse(identityAccess.hasAnyRole(userId, Set.of("SELLER", "ADMIN")));
    }
}
