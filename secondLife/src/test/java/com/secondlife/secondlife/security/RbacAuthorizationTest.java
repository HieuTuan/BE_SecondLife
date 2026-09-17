package com.secondlife.secondlife.security;

import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.PermissionCode;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RbacAuthorizationTest {

    @Test
    void buyerAuthorities_ShouldIncludeBuyerPermissionsAndRole_ButExcludeAdminPermissions() {
        Role buyerRole = new Role(RoleCode.BUYER.name(), "Buyer", "Buyer role");
        buyerRole.addPermission(new Permission(PermissionCode.PROFILE_READ_SELF.name(), "Read", ""));
        buyerRole.addPermission(new Permission(PermissionCode.SELLER_VERIFICATION_SUBMIT.name(), "Submit", ""));

        User buyer = new User("buyer@example.com", "pass", AccountStatus.ACTIVE);
        buyer.addRole(buyerRole);

        CustomUserDetails userDetails = new CustomUserDetails(buyer);
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_BUYER"));
        assertTrue(authorities.contains("PROFILE_READ_SELF"));
        assertTrue(authorities.contains("SELLER_VERIFICATION_SUBMIT"));

        // Must NOT have admin privileges
        assertFalse(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("USER_READ_ANY"));
        assertFalse(authorities.contains("SELLER_VERIFICATION_REVIEW"));
        assertFalse(authorities.contains("INSPECTION_CENTER_ACCOUNT_MANAGE"));
    }

    @Test
    void adminAuthorities_ShouldIncludeAdminRoleAndAllAdminPermissions() {
        Role adminRole = new Role(RoleCode.ADMIN.name(), "Admin", "Admin role");
        adminRole.addPermission(new Permission(PermissionCode.USER_READ_ANY.name(), "Read Any", ""));
        adminRole.addPermission(new Permission(PermissionCode.USER_STATUS_UPDATE.name(), "Status Update", ""));
        adminRole.addPermission(new Permission(PermissionCode.SELLER_VERIFICATION_REVIEW.name(), "Review", ""));
        adminRole.addPermission(new Permission(PermissionCode.INSPECTION_CENTER_ACCOUNT_MANAGE.name(), "Manage", ""));

        User admin = new User("admin@example.com", "pass", AccountStatus.ACTIVE);
        admin.addRole(adminRole);

        CustomUserDetails userDetails = new CustomUserDetails(admin);
        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("USER_READ_ANY"));
        assertTrue(authorities.contains("USER_STATUS_UPDATE"));
        assertTrue(authorities.contains("SELLER_VERIFICATION_REVIEW"));
        assertTrue(authorities.contains("INSPECTION_CENTER_ACCOUNT_MANAGE"));
    }

    @Test
    void userStatus_WhenLocked_ShouldReportLocked() {
        User lockedUser = new User("locked@example.com", "pass", AccountStatus.LOCKED);
        CustomUserDetails userDetails = new CustomUserDetails(lockedUser);

        assertFalse(userDetails.isAccountNonLocked());
        assertFalse(userDetails.isEnabled());
    }

    @Test
    void userStatus_WhenDisabled_ShouldReportDisabled() {
        User disabledUser = new User("disabled@example.com", "pass", AccountStatus.DISABLED);
        CustomUserDetails userDetails = new CustomUserDetails(disabledUser);

        assertFalse(userDetails.isEnabled());
    }

    @Test
    void userStatus_WhenActive_ShouldReportActiveAndUnlocked() {
        User activeUser = new User("active@example.com", "pass", AccountStatus.ACTIVE);
        CustomUserDetails userDetails = new CustomUserDetails(activeUser);

        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
    }
}
