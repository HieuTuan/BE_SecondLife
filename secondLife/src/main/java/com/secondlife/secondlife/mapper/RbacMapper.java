package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.PermissionResponse;
import com.secondlife.secondlife.dto.response.RolePermissionAuditResponse;
import com.secondlife.secondlife.dto.response.RolePermissionsResponse;
import com.secondlife.secondlife.dto.response.UserRoleAuditResponse;
import com.secondlife.secondlife.dto.response.UserRolesResponse;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.RolePermissionAudit;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserRoleAudit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class RbacMapper {
    private final Delegate delegate;

    public RbacMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public RolePermissionsResponse toRoleResponse(Role role) {
        return delegate.toRoleResponse(role);
    }

    public PermissionResponse toPermissionResponse(Permission permission, Set<String> assignableRoles, boolean systemPermission) {
        return delegate.toPermissionResponse(permission, assignableRoles, systemPermission);
    }

    public UserRolesResponse toUserRolesResponse(User user, Set<String> roleCodes, Set<String> permissionCodes) {
        return delegate.toUserRolesResponse(user, roleCodes, permissionCodes);
    }

    public UserRoleAuditResponse toUserRoleAuditResponse(UserRoleAudit audit) {
        return delegate.toUserRoleAuditResponse(audit);
    }

    public RolePermissionAuditResponse toRolePermissionAuditResponse(RolePermissionAudit audit) {
        return delegate.toRolePermissionAuditResponse(audit);
    }

    @Mapper(config = MapStructConfiguration.class, imports = {Permission.class, Collectors.class, TreeSet.class})
    public interface Delegate {
        @Mapping(target = "editable", expression = "java(!\"ADMIN\".equals(role.getCode()))")
        @Mapping(target = "permissionCodes", expression = "java(role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new)))")
        RolePermissionsResponse toRoleResponse(Role role);

        @Mapping(target = "code", source = "permission.code")
        @Mapping(target = "name", source = "permission.name")
        @Mapping(target = "description", source = "permission.description")
        @Mapping(target = "assignableRoles", source = "assignableRoles")
        @Mapping(target = "systemPermission", source = "systemPermission")
        PermissionResponse toPermissionResponse(Permission permission, Set<String> assignableRoles, boolean systemPermission);

        @Mapping(target = "userId", source = "user.id")
        @Mapping(target = "roleCodes", source = "roleCodes")
        @Mapping(target = "permissionCodes", source = "permissionCodes")
        UserRolesResponse toUserRolesResponse(User user, Set<String> roleCodes, Set<String> permissionCodes);

        UserRoleAuditResponse toUserRoleAuditResponse(UserRoleAudit audit);

        RolePermissionAuditResponse toRolePermissionAuditResponse(RolePermissionAudit audit);
    }
}
