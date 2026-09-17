package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 50)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserProfile profile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserRole> userRoles = new HashSet<>();

    public User(String email, String passwordHash, AccountStatus accountStatus) {
        this.email = email != null ? email.trim().toLowerCase() : null;
        this.passwordHash = passwordHash;
        this.accountStatus = accountStatus != null ? accountStatus : AccountStatus.ACTIVE;
    }

    public void setProfile(UserProfile profile) {
        this.profile = profile;
        if (profile != null) {
            profile.setUser(this);
        }
    }

    public Set<Role> getRoles() {
        return userRoles.stream()
                .map(UserRole::getRole)
                .collect(Collectors.toSet());
    }

    public void addRole(Role role) {
        UserRole userRole = new UserRole(this, role);
        this.userRoles.add(userRole);
    }

    public void removeRole(Role role) {
        this.userRoles.removeIf(ur -> Objects.equals(ur.getRole().getCode(), role.getCode()));
    }

    public boolean hasRole(String roleCode) {
        return userRoles.stream()
                .anyMatch(ur -> ur.getRole().getCode().equalsIgnoreCase(roleCode));
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return email != null && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }
}
