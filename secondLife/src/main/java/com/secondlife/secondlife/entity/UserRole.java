package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
        if (user != null && role != null && user.getId() != null && role.getId() != null) {
            this.id = new UserRoleId(user.getId(), role.getId());
        } else {
            this.id = new UserRoleId();
        }
        this.assignedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole userRole)) return false;
        if (user != null && role != null && userRole.user != null && userRole.role != null) {
            return Objects.equals(user.getEmail(), userRole.user.getEmail()) &&
                   Objects.equals(role.getCode(), userRole.role.getCode());
        }
        return Objects.equals(id, userRole.id);
    }

    @Override
    public int hashCode() {
        if (user != null && role != null) {
            return Objects.hash(user.getEmail(), role.getCode());
        }
        return Objects.hash(id);
    }
}
