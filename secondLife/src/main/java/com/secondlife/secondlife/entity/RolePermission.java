package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    public RolePermission(Role role, Permission permission) {
        this.role = role;
        this.permission = permission;
        if (role != null && permission != null && role.getId() != null && permission.getId() != null) {
            this.id = new RolePermissionId(role.getId(), permission.getId());
        } else {
            this.id = new RolePermissionId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermission that)) return false;
        if (role != null && permission != null && that.role != null && that.permission != null) {
            return Objects.equals(role.getCode(), that.role.getCode()) &&
                   Objects.equals(permission.getCode(), that.permission.getCode());
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        if (role != null && permission != null) {
            return Objects.hash(role.getCode(), permission.getCode());
        }
        return Objects.hash(id);
    }
}
