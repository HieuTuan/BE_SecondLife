package com.secondlife.secondlife.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "permissions_version", nullable = false)
    private long permissionsVersion;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RolePermission> rolePermissions = new HashSet<>();

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public Set<Permission> getPermissions() {
        return rolePermissions.stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toSet());
    }

    public void addPermission(Permission permission) {
        RolePermission rolePermission = new RolePermission(this, permission);
        if (this.rolePermissions.add(rolePermission)) {
            permissionsVersion = Math.addExact(permissionsVersion, 1);
        }
    }

    public void removePermission(Permission permission) {
        if (this.rolePermissions.removeIf(rp -> Objects.equals(rp.getPermission().getCode(), permission.getCode()))) {
            permissionsVersion = Math.addExact(permissionsVersion, 1);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role role)) return false;
        return Objects.equals(code, role.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
