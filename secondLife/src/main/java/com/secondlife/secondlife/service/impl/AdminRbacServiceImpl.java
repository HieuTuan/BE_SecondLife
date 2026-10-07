package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.rbac.*;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.RolePermissionAudit;
import com.secondlife.secondlife.enums.PermissionCode;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PermissionRepository;
import com.secondlife.secondlife.repository.RolePermissionAuditRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.service.AdminRbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRbacServiceImpl implements AdminRbacService {
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionAuditRepository auditRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermission(String permissionCode) {
        return toPermissionResponse(permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new NotFoundException("Permission not found")));
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String code = request.code();
        if (permissionRepository.existsByCode(code)) {
            throw new ConflictException("Permission code already exists");
        }
        PermissionCode definedCode;
        try {
            definedCode = PermissionCode.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Permission must be defined and implemented by the backend");
        }
        if (!definedCode.isImplemented()) {
            throw new BadRequestException("Permission has no implemented API operation");
        }
        Permission permission = new Permission(code, request.name().trim(), request.description());
        Set<String> roles = validateAssignableRoles(request.assignableRoles());
        if (!roles.equals(assignableRoles(permission))) {
            throw new BadRequestException("System permission assignment policy is fixed");
        }
        try {
            return toPermissionResponse(permissionRepository.saveAndFlush(permission));
        } catch (DataIntegrityViolationException ex) {
            // The unique code constraint also handles concurrent create requests.
            throw new ConflictException("Permission code already exists");
        }
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(String permissionCode, UpdatePermissionRequest request) {
        Permission permission = lockedPermission(permissionCode);
        if (request.assignableRoles() != null) {
            Set<String> roles = validateAssignableRoles(request.assignableRoles());
            if (systemPermission(permissionCode)) {
                if (!roles.equals(assignableRoles(permission))) {
                    throw new ConflictException("System permission assignment policy is fixed");
                }
            } else {
                Set<String> assignedRoles = permissionRepository.findAssignedRoleCodes(permission.getId());
                if (!roles.containsAll(assignedRoles)) {
                    throw new ConflictException("Revoke the permission from excluded roles before changing allowed roles");
                }
                permission.getAssignableRoles().clear();
                permission.getAssignableRoles().addAll(roles);
            }
        }
        permission.setName(request.name().trim());
        permission.setDescription(request.description());
        return toPermissionResponse(permissionRepository.saveAndFlush(permission));
    }

    @Override
    @Transactional
    public void deletePermission(String permissionCode) {
        Permission permission = lockedPermission(permissionCode);
        if (systemPermission(permissionCode)) {
            throw new ConflictException("System permissions cannot be deleted");
        }
        if (!permissionRepository.findAssignedRoleCodes(permission.getId()).isEmpty()) {
            throw new ConflictException("Revoke the permission from all roles before deleting it");
        }
        permissionRepository.delete(permission);
        permissionRepository.flush();
    }

    private Permission lockedPermission(String code) {
        return permissionRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NotFoundException("Permission not found"));
    }

    private Set<String> validateAssignableRoles(Set<String> roles) {
        Set<String> validated = new TreeSet<>();
        for (String roleCode : roles) {
            RoleCode role;
            try {
                role = RoleCode.valueOf(roleCode);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new BadRequestException("Unknown role code: " + roleCode);
            }
            if (role == RoleCode.ADMIN) {
                throw new BadRequestException("ADMIN role permissions are fixed");
            }
            if (!roleRepository.existsByCode(roleCode)) {
                throw new BadRequestException("Role is not initialized: " + roleCode);
            }
            validated.add(roleCode);
        }
        return validated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionsResponse> getRoles() {
        return roleRepository.findAllWithPermissions().stream()
                .sorted(Comparator.comparing(Role::getCode))
                .map(this::toRoleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionsResponse getRole(String roleCode) {
        RoleCode code = parseRoleCode(roleCode);
        Role role = roleRepository.findByCodeWithPermissions(code.name())
                .orElseThrow(() -> new NotFoundException("Role not found"));
        return toRoleResponse(role);
    }

    @Override
    @Transactional
    public RolePermissionsResponse replaceRolePermissions(UUID adminId, String roleCode,
                                                          ReplaceRolePermissionsRequest request) {
        RoleCode code = parseRoleCode(roleCode);
        if (code == RoleCode.ADMIN) {
            throw new ConflictException("ADMIN role permissions are fixed to prevent administrator lockout");
        }
        if (request == null || request.expectedPermissionCodes() == null || request.permissionCodes() == null) {
            throw new BadRequestException("Expected and desired permission codes are required");
        }
        Set<String> expected = request.expectedPermissionCodes();
        Set<String> desired = request.permissionCodes();
        Role role = roleRepository.findByCodeForUpdate(code.name())
                .orElseThrow(() -> new NotFoundException("Role not found"));
        Set<String> current = role.getPermissions().stream()
                .map(Permission::getCode).collect(Collectors.toSet());

        Set<String> requested = new HashSet<>(current);
        requested.addAll(expected);
        requested.addAll(desired);
        if (requested.contains(null)) {
            throw new BadRequestException("Permission codes must not be null");
        }
        // Catalog mutations and assignments take the same locks, in code order.
        Map<String, Permission> catalog = requested.isEmpty() ? Map.of()
                : permissionRepository.findByCodesForUpdate(requested).stream()
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));
        for (String permission : expected) {
            requireCatalogPermission(catalog, permission);
        }
        for (String permission : desired) {
            Permission entry = requireCatalogPermission(catalog, permission);
            if (!assignableRoles(entry).contains(code.name())) {
                throw new BadRequestException("Permission " + permission + " cannot be assigned to " + code);
            }
        }
        if (!current.equals(expected)) {
            throw new ConflictException("Role permissions changed; reload before saving");
        }
        for (String removed : current) {
            if (!desired.contains(removed)) {
                role.removePermission(catalog.get(removed));
                audit(adminId, role, removed, "REVOKE");
            }
        }
        for (String added : desired) {
            if (!current.contains(added)) {
                role.addPermission(catalog.get(added));
                audit(adminId, role, added, "GRANT");
            }
        }
        roleRepository.saveAndFlush(role);
        return toRoleResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RolePermissionAuditResponse> getRolePermissionAudit(String roleCode, Pageable pageable) {
        RoleCode code = parseRoleCode(roleCode);
        Role role = roleRepository.findByCode(code.name())
                .orElseThrow(() -> new NotFoundException("Role not found"));
        return PageResponse.from(auditRepository.findByRoleIdOrderByChangedAtDesc(role.getId(), pageable)
                .map(entry -> new RolePermissionAuditResponse(entry.getId(), entry.getPermissionCode(),
                        entry.getAction(), entry.getChangedBy(), entry.getChangedAt())));
    }

    private void audit(UUID adminId, Role role, String permission, String action) {
        RolePermissionAudit entry = new RolePermissionAudit();
        entry.setRoleId(role.getId());
        entry.setPermissionCode(permission);
        entry.setAction(action);
        entry.setChangedBy(adminId);
        entry.setChangedAt(Instant.now());
        auditRepository.save(entry);
    }

    private RolePermissionsResponse toRoleResponse(Role role) {
        Set<String> codes = role.getPermissions().stream().map(Permission::getCode)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        return new RolePermissionsResponse(role.getCode(), role.getName(), role.getDescription(),
                !RoleCode.ADMIN.name().equals(role.getCode()), codes);
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return new PermissionResponse(permission.getCode(), permission.getName(), permission.getDescription(),
                assignableRoles(permission), systemPermission(permission.getCode()));
    }

    private boolean systemPermission(String code) {
        try {
            PermissionCode.valueOf(code);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Set<String> assignableRoles(Permission permission) {
        PermissionCode code;
        try {
            code = PermissionCode.valueOf(permission.getCode());
        } catch (IllegalArgumentException ex) {
            return new TreeSet<>(permission.getAssignableRoles());
        }
        return EnumSet.complementOf(EnumSet.of(RoleCode.ADMIN)).stream()
                .filter(role -> assignable(role, code))
                .map(Enum::name)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private boolean assignable(RoleCode role, PermissionCode permission) {
        if (role == RoleCode.ADMIN) return false;
        return switch (permission) {
            case PROFILE_READ_SELF, PROFILE_UPDATE_SELF, PASSWORD_CHANGE_SELF, AI_CHAT_SELF, MEDIA_UPLOAD_SELF -> true;
            case SELLER_VERIFICATION_SUBMIT -> role == RoleCode.BUYER;
            case SELLER_VERIFICATION_READ_SELF -> role == RoleCode.BUYER || role == RoleCode.SELLER;
            case LISTING_CREATE_SELF, LISTING_PUBLISH_SELF, LISTING_VALUATION_SELF, CREDIT_READ_SELF, CREDIT_PURCHASE_SELF ->
                    role == RoleCode.SELLER;
            case INSPECTION_REPORT_SUBMIT, INSPECTION_ORDER_READ_SELF -> role == RoleCode.INSPECTOR;
            case INSPECTION_ORDER_READ_ANY, INSPECTION_ORDER_ASSIGN, INSPECTION_STAFF_MANAGE ->
                    role == RoleCode.INSPECTION_CENTER;
            case USER_READ_ANY, SELLER_VERIFICATION_READ_ANY, ROLE_READ,
                 STAFF_LISTING_REVIEW, STAFF_FRAUD_REVIEW, STAFF_DISPUTE_REVIEW,
                 STAFF_PAYOUT_REVIEW, STAFF_PAYOUT_HOLD, STAFF_USER_WARN,
                 STAFF_USER_TEMP_RESTRICT -> role == RoleCode.STAFF;
            case ADMIN_STAFF_MANAGE, ADMIN_RBAC_MANAGE, ADMIN_PRICING_MANAGE,
                 ADMIN_COMMISSION_MANAGE, ADMIN_CONFIG_MANAGE, ADMIN_CATALOG_MANAGE, ADMIN_PERMANENT_BAN,
                 ADMIN_HIGH_VALUE_PAYOUT, USER_STATUS_UPDATE, SELLER_VERIFICATION_REVIEW,
                 POST_REVIEW, INSPECTION_CENTER_ACCOUNT_MANAGE, POST_CREATE, POST_UPDATE, POST_DELETE -> false;
        };
    }

    private RoleCode parseRoleCode(String code) {
        try {
            return RoleCode.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new NotFoundException("Role not found");
        }
    }

    private Permission requireCatalogPermission(Map<String, Permission> catalog, String code) {
        Permission permission = catalog.get(code);
        if (permission == null) {
            throw new BadRequestException("Unknown permission code: " + code);
        }
        return permission;
    }
}
