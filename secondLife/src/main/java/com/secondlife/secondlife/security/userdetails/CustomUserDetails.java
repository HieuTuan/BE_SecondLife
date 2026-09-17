package com.secondlife.secondlife.security.userdetails;

import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final AccountStatus accountStatus;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.accountStatus = user.getAccountStatus();
        this.emailVerified = user.isEmailVerified();

        Set<GrantedAuthority> auths = new HashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                // Role authority with ROLE_ prefix
                auths.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));

                // Permission authorities
                if (role.getPermissions() != null) {
                    role.getPermissions().forEach(permission ->
                            auths.add(new SimpleGrantedAuthority(permission.getCode()))
                    );
                }
            }
        }
        this.authorities = auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
