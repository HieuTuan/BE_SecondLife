package com.secondlife.secondlife.seeder;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.PermissionCode;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.repository.PermissionRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true", matchIfMissing = true)
public class RbacDataSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seeder.admin.enabled}")
    private boolean adminSeederEnabled;

    @Value("${app.seeder.admin.email}")
    private String adminEmail;

    @Value("${app.seeder.admin.password}")
    private String adminPassword;

    @Value("${app.seeder.admin.full-name}")
    private String adminFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting RBAC and Initial Data Seeding...");

        Map<PermissionCode, Permission> permissions = seedPermissions();
        Map<RoleCode, Role> roles = seedRoles();
        seedRolePermissions(roles, permissions);
        seedAdminUser(roles.get(RoleCode.ADMIN));

        log.info("RBAC and Initial Data Seeding completed successfully.");
    }

    private Map<PermissionCode, Permission> seedPermissions() {
        Map<PermissionCode, Permission> map = new EnumMap<>(PermissionCode.class);

        Map<PermissionCode, String[]> metadata = Map.ofEntries(
                Map.entry(PermissionCode.PROFILE_READ_SELF, new String[]{"Read Own Profile", "Allows reading personal profile and account data"}),
                Map.entry(PermissionCode.PROFILE_UPDATE_SELF, new String[]{"Update Own Profile", "Allows updating safe personal profile data"}),
                Map.entry(PermissionCode.PASSWORD_CHANGE_SELF, new String[]{"Change Own Password", "Allows changing own account password"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_SUBMIT, new String[]{"Submit Seller Verification", "Allows submitting identity verification for seller role"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_READ_SELF, new String[]{"Read Own Seller Verification", "Allows checking personal seller verification status"}),
                Map.entry(PermissionCode.USER_READ_ANY, new String[]{"Read Any User", "Allows viewing details of any user in the system"}),
                Map.entry(PermissionCode.USER_STATUS_UPDATE, new String[]{"Update User Status", "Allows activating, locking, or disabling user accounts"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_READ_ANY, new String[]{"Read Any Seller Verification", "Allows viewing all seller verification requests"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_REVIEW, new String[]{"Review Seller Verification", "Allows approving or rejecting seller verification requests"}),
                Map.entry(PermissionCode.INSPECTION_CENTER_ACCOUNT_MANAGE, new String[]{"Manage Inspection Center Accounts", "Allows provisioning Inspection Center partner accounts"}),
                Map.entry(PermissionCode.ROLE_READ, new String[]{"Read Roles", "Allows viewing available roles and permissions in the system"}),
                Map.entry(PermissionCode.POST_CREATE, new String[]{"Create Post", "Allows creating new marketplace posts"}),
                Map.entry(PermissionCode.POST_UPDATE, new String[]{"Update Post", "Allows updating own marketplace posts"}),
                Map.entry(PermissionCode.POST_DELETE, new String[]{"Delete Post", "Allows deleting own marketplace posts"})
        );

        for (Map.Entry<PermissionCode, String[]> entry : metadata.entrySet()) {
            PermissionCode code = entry.getKey();
            String name = entry.getValue()[0];
            String description = entry.getValue()[1];

            Permission permission = permissionRepository.findByCode(code.name())
                    .orElseGet(() -> permissionRepository.save(new Permission(code.name(), name, description)));
            map.put(code, permission);
        }

        return map;
    }

    private Map<RoleCode, Role> seedRoles() {
        Map<RoleCode, Role> map = new EnumMap<>(RoleCode.class);

        Map<RoleCode, String[]> roleMeta = Map.of(
                RoleCode.BUYER, new String[]{"Buyer", "Standard marketplace buyer role"},
                RoleCode.SELLER, new String[]{"Seller", "Verified seller role capable of listing items"},
                RoleCode.INSPECTION_CENTER, new String[]{"Inspection Center", "Inspection & authentication partner account"},
                RoleCode.ADMIN, new String[]{"Administrator", "Full system administrator"}
        );

        for (Map.Entry<RoleCode, String[]> entry : roleMeta.entrySet()) {
            RoleCode code = entry.getKey();
            String name = entry.getValue()[0];
            String desc = entry.getValue()[1];

            Role role = roleRepository.findByCodeWithPermissions(code.name())
                    .orElseGet(() -> roleRepository.save(new Role(code.name(), name, desc)));
            map.put(code, role);
        }

        return map;
    }

    private void seedRolePermissions(Map<RoleCode, Role> roles, Map<PermissionCode, Permission> permissions) {
        // BUYER
        assignPermissionsIfMissing(roles.get(RoleCode.BUYER), List.of(
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF),
                permissions.get(PermissionCode.SELLER_VERIFICATION_SUBMIT),
                permissions.get(PermissionCode.SELLER_VERIFICATION_READ_SELF)
        ));

        // SELLER
        assignPermissionsIfMissing(roles.get(RoleCode.SELLER), List.of(
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF),
                permissions.get(PermissionCode.SELLER_VERIFICATION_READ_SELF),
                permissions.get(PermissionCode.POST_CREATE),
                permissions.get(PermissionCode.POST_UPDATE),
                permissions.get(PermissionCode.POST_DELETE)
        ));

        // INSPECTION_CENTER
        assignPermissionsIfMissing(roles.get(RoleCode.INSPECTION_CENTER), List.of(
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF)
        ));

        // ADMIN (All permissions)
        assignPermissionsIfMissing(roles.get(RoleCode.ADMIN), new ArrayList<>(permissions.values()));
    }

    private void assignPermissionsIfMissing(Role role, List<Permission> requiredPermissions) {
        Set<String> existingPermCodes = new HashSet<>();
        for (RolePermission rp : role.getRolePermissions()) {
            existingPermCodes.add(rp.getPermission().getCode());
        }

        for (Permission perm : requiredPermissions) {
            if (!existingPermCodes.contains(perm.getCode())) {
                role.addPermission(perm);
            }
        }
        roleRepository.save(role);
    }

    private void seedAdminUser(Role adminRole) {
        if (!adminSeederEnabled) {
            log.info("Admin seeder is disabled by configuration (SEED_ADMIN_ENABLED=false).");
            return;
        }

        String normalizedAdminEmail = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedAdminEmail)) {
            log.info("Admin user [{}] already exists, skipping initial creation.", normalizedAdminEmail);
            return;
        }

        User adminUser = new User(normalizedAdminEmail, passwordEncoder.encode(adminPassword), AccountStatus.ACTIVE);
        adminUser.setEmailVerified(true);

        UserProfile profile = new UserProfile(adminUser, adminFullName, null, null);
        adminUser.setProfile(profile);
        adminUser.addRole(adminRole);

        userRepository.save(adminUser);
        log.info("Successfully seeded default Admin user [{}] from environment variables.", normalizedAdminEmail);
    }
}
