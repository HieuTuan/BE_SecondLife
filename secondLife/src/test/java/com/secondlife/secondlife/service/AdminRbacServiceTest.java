package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.rbac.CreatePermissionRequest;
import com.secondlife.secondlife.dto.rbac.ReplaceRolePermissionsRequest;
import com.secondlife.secondlife.dto.rbac.UpdatePermissionRequest;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.RolePermissionAudit;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PermissionRepository;
import com.secondlife.secondlife.repository.RolePermissionAuditRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.service.impl.AdminRbacServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRbacServiceTest {
    @Mock private PermissionRepository permissionRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionAuditRepository auditRepository;
    @InjectMocks private AdminRbacServiceImpl service;

    @Test
    void initializesOnlyImplementedDeveloperDefinedPermission() {
        when(roleRepository.existsByCode("SELLER")).thenReturn(true);
        when(permissionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.createPermission(new CreatePermissionRequest(
                "CREDIT_READ_SELF", " Export reports ", "Reporting", Set.of("SELLER")));

        assertEquals("Export reports", response.name());
        assertEquals(Set.of("SELLER"), response.assignableRoles());
        assertTrue(response.systemPermission());
    }

    @Test
    void preventsDuplicateOrReservedCodesIncludingRoleAuthoritySpoofing() {
        when(permissionRepository.existsByCode("REPORT_EXPORT")).thenReturn(true);
        when(permissionRepository.existsByCode("ADMIN_RBAC_MANAGE")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.createPermission(create("REPORT_EXPORT")));
        assertThrows(ConflictException.class, () -> service.createPermission(create("ADMIN_RBAC_MANAGE")));
        assertThrows(BadRequestException.class, () -> service.createPermission(create("ROLE_ADMIN")));
        assertThrows(BadRequestException.class, () -> service.createPermission(create("ADMIN_CUSTOM")));
        verify(permissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateCreateReturnsConflict() {
        when(roleRepository.existsByCode("SELLER")).thenReturn(true);
        when(permissionRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(ConflictException.class, () -> service.createPermission(new CreatePermissionRequest(
                "CREDIT_READ_SELF", "Credits", null, Set.of("SELLER"))));
    }

    @Test
    void rejectsUnknownAndAdminAssignableRoles() {
        for (String role : List.of("ADMIN", "UNKNOWN", "STAFF")) {
            assertThrows(BadRequestException.class, () -> service.createPermission(
                    new CreatePermissionRequest("CREDIT_READ_SELF", "Reports", null, Set.of(role))));
        }
        assertThrows(BadRequestException.class, () -> service.createPermission(create("REPORT_EXPORT")));
        assertThrows(BadRequestException.class, () -> service.createPermission(create("POST_DELETE")));
        verify(permissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesSystemMetadataButProtectsItsAssignmentPolicyAndDeletion() {
        Permission permission = permission("ADMIN_RBAC_MANAGE");
        when(permissionRepository.findByCodeForUpdate(permission.getCode())).thenReturn(Optional.of(permission));
        when(permissionRepository.saveAndFlush(permission)).thenReturn(permission);
        when(roleRepository.existsByCode("STAFF")).thenReturn(true);

        var response = service.updatePermission(permission.getCode(), new UpdatePermissionRequest("RBAC", null, null));
        assertTrue(response.systemPermission());
        assertEquals("RBAC", response.name());
        assertThrows(ConflictException.class, () -> service.updatePermission(permission.getCode(),
                new UpdatePermissionRequest("RBAC", null, Set.of("STAFF"))));
        assertThrows(ConflictException.class, () -> service.deletePermission(permission.getCode()));
        verify(permissionRepository, never()).delete(any());
    }

    @Test
    void requiresRevocationBeforeDeletingOrExcludingAssignedRoles() {
        Permission permission = permission("REPORT_EXPORT");
        permission.getAssignableRoles().add("STAFF");
        when(permissionRepository.findByCodeForUpdate(permission.getCode())).thenReturn(Optional.of(permission));
        when(permissionRepository.findAssignedRoleCodes(permission.getId())).thenReturn(Set.of("STAFF"));

        assertThrows(ConflictException.class, () -> service.deletePermission(permission.getCode()));
        assertThrows(ConflictException.class, () -> service.updatePermission(permission.getCode(),
                new UpdatePermissionRequest("Reports", null, Set.of())));
        assertEquals(Set.of("STAFF"), permission.getAssignableRoles());
        verify(permissionRepository, never()).delete(any());
        verify(permissionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletesUnassignedCustomPermissionAndReturnsNotFoundForMissingPermission() {
        Permission permission = permission("REPORT_EXPORT");
        when(permissionRepository.findByCodeForUpdate(permission.getCode())).thenReturn(Optional.of(permission));
        service.deletePermission(permission.getCode());
        verify(permissionRepository).delete(permission);
        verify(permissionRepository).flush();
        assertThrows(NotFoundException.class, () -> service.getPermission("MISSING"));
        assertThrows(NotFoundException.class, () -> service.deletePermission("MISSING"));
    }

    @Test
    void grantsAndRevokesCustomPermissionsWithAuditAndRejectsStaleWrites() {
        Role role = new Role("STAFF", "Staff", null);
        role.setId(UUID.randomUUID());
        Permission permission = permission("REPORT_EXPORT");
        permission.getAssignableRoles().add("STAFF");
        UUID admin = UUID.randomUUID();
        when(roleRepository.findByCodeForUpdate("STAFF")).thenReturn(Optional.of(role));
        when(permissionRepository.findByCodesForUpdate(any())).thenReturn(List.of(permission));

        var granted = service.replaceRolePermissions(admin, "STAFF",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of(permission.getCode())));
        assertEquals(Set.of("REPORT_EXPORT"), granted.permissionCodes());
        assertThrows(ConflictException.class, () -> service.replaceRolePermissions(admin, "STAFF",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of(permission.getCode()))));
        var revoked = service.replaceRolePermissions(admin, "STAFF",
                new ReplaceRolePermissionsRequest(Set.of(permission.getCode()), Set.of()));
        assertTrue(revoked.permissionCodes().isEmpty());

        ArgumentCaptor<RolePermissionAudit> audits = ArgumentCaptor.forClass(RolePermissionAudit.class);
        verify(auditRepository, times(2)).save(audits.capture());
        assertEquals(List.of("GRANT", "REVOKE"), audits.getAllValues().stream()
                .map(RolePermissionAudit::getAction).toList());
        assertTrue(audits.getAllValues().stream().allMatch(audit -> admin.equals(audit.getChangedBy())
                && "REPORT_EXPORT".equals(audit.getPermissionCode()) && audit.getChangedAt() != null));
    }

    @Test
    void rejectsUnknownOrDisallowedPermissionsWithoutWritingAudit() {
        Role role = new Role("BUYER", "Buyer", null);
        Permission permission = permission("REPORT_EXPORT");
        permission.getAssignableRoles().add("STAFF");
        when(roleRepository.findByCodeForUpdate("BUYER")).thenReturn(Optional.of(role));
        when(permissionRepository.findByCodesForUpdate(any())).thenReturn(List.of(permission));

        assertThrows(BadRequestException.class, () -> service.replaceRolePermissions(UUID.randomUUID(), "BUYER",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of("REPORT_EXPORT"))));
        assertThrows(BadRequestException.class, () -> service.replaceRolePermissions(UUID.randomUUID(), "BUYER",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of("UNKNOWN"))));
        verifyNoInteractions(auditRepository);
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAdminRoleChangesAndNoOpDoesNotCreateAudit() {
        assertThrows(ConflictException.class, () -> service.replaceRolePermissions(UUID.randomUUID(), "ADMIN",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of())));
        Role role = new Role("STAFF", "Staff", null);
        when(roleRepository.findByCodeForUpdate("STAFF")).thenReturn(Optional.of(role));
        service.replaceRolePermissions(UUID.randomUUID(), "STAFF",
                new ReplaceRolePermissionsRequest(Set.of(), Set.of()));
        verifyNoInteractions(auditRepository);
    }

    private CreatePermissionRequest create(String code) {
        return new CreatePermissionRequest(code, "Reports", null, Set.of("STAFF"));
    }

    private Permission permission(String code) {
        Permission permission = new Permission(code, "Permission", null);
        permission.setId(UUID.randomUUID());
        return permission;
    }
}
