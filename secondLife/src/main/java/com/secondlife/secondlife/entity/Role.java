package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
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
        this.rolePermissions.add(rolePermission);
    }

    public void removePermission(Permission permission) {
        this.rolePermissions.removeIf(rp -> Objects.equals(rp.getPermission().getCode(), permission.getCode()));
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
