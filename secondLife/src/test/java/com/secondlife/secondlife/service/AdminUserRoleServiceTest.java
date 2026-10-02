package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.rbac.ReplaceUserRolesRequest;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.AdminUserRoleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserRoleServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock UserRoleAuditRepository audits;
    @Mock SellerVerificationRepository verifications;
    @Mock TokenService tokens;
    @InjectMocks AdminUserRoleServiceImpl service;

    @Test void locksAdminGuardBeforeUserAndProtectsLastActiveAdmin() {
        User target = lockedUser("ADMIN");
        when(users.countActiveAdmins()).thenReturn(1L);
        assertThrows(ConflictException.class, () -> service.replaceUserRoles(UUID.randomUUID(), target.getId(),
                new ReplaceUserRolesRequest(Set.of("ADMIN"), Set.of())));
        var sequence = inOrder(roles, users);
        sequence.verify(roles).findByCodeForUpdate("ADMIN");
        sequence.verify(users).findByIdForRoleUpdate(target.getId());
        sequence.verify(users).countActiveAdmins();
        verifyNoInteractions(audits, tokens);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void canRemoveAdminFromInactiveUserWithoutAffectingLastActiveAdmin() {
        User target = lockedUser("ADMIN"); target.setAccountStatus(AccountStatus.DISABLED);
        service.replaceUserRoles(UUID.randomUUID(), target.getId(), new ReplaceUserRolesRequest(Set.of("ADMIN"), Set.of()));
        assertTrue(target.getRoles().isEmpty());
        verify(users, never()).countActiveAdmins();
        verify(tokens).revokeAllUserRefreshTokens(target.getId());
    }

    @Test void approvedSellerGrantIsAuditedAndInvalidatesSessions() {
        User target = lockedUser("BUYER");
        Role seller = role("SELLER");
        when(verifications.existsByUserIdAndStatus(target.getId(), SellerVerificationStatus.APPROVED)).thenReturn(true);
        when(roles.findByCode("SELLER")).thenReturn(Optional.of(seller));
        when(roles.findByCode("BUYER")).thenReturn(Optional.of(target.getRoles().iterator().next()));
        service.replaceUserRoles(UUID.randomUUID(), target.getId(),
                new ReplaceUserRolesRequest(Set.of("BUYER"), Set.of("BUYER", "SELLER")));
        assertTrue(target.hasRole("SELLER")); assertEquals(1, target.getTokenVersion());
        ArgumentCaptor<UserRoleAudit> record = ArgumentCaptor.forClass(UserRoleAudit.class);
        verify(audits).save(record.capture());
        assertEquals("SELLER", record.getValue().getRoleCode());
        assertEquals("GRANT", record.getValue().getAction());
        verify(tokens).revokeAllUserRefreshTokens(target.getId());
    }

    private User lockedUser(String code) {
        User user = new User("user@test.local", "hash", AccountStatus.ACTIVE); user.setId(UUID.randomUUID());
        user.addRole(role(code));
        when(roles.findByCodeForUpdate("ADMIN")).thenReturn(Optional.of(role("ADMIN")));
        when(users.findByIdForRoleUpdate(user.getId())).thenReturn(Optional.of(user));
        return user;
    }
    private Role role(String code) { Role role = new Role(code, code, null); role.setId(UUID.randomUUID()); return role; }
}
