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
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
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

    @Value("${app.seeder.seller.enabled}")
    private boolean sellerSeederEnabled;

    @Value("${app.seeder.seller.email}")
    private String sellerEmail;

    @Value("${app.seeder.seller.password}")
    private String sellerPassword;

    @Value("${app.seeder.seller.full-name}")
    private String sellerFullName;

    @Value("${app.seeder.buyer.enabled:false}")
    private boolean buyerSeederEnabled;

    @Value("${app.seeder.buyer.email:}")
    private String buyerEmail;

    @Value("${app.seeder.buyer.password:}")
    private String buyerPassword;

    @Value("${app.seeder.buyer.full-name:}")
    private String buyerFullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting RBAC and Initial Data Seeding...");

        Map<PermissionCode, Permission> permissions = seedPermissions();
        Set<RoleCode> newRoleCodes = EnumSet.allOf(RoleCode.class);
        roleRepository.findAll().forEach(role -> {
            try {
                newRoleCodes.remove(RoleCode.valueOf(role.getCode()));
            } catch (IllegalArgumentException ignored) {
                // Historical/custom roles are outside the built-in role seed.
            }
        });
        Map<RoleCode, Role> roles = seedRoles();
        Map<RoleCode, Role> newRoles = new EnumMap<>(RoleCode.class);
        newRoleCodes.forEach(code -> newRoles.put(code, roles.get(code)));
        seedRolePermissions(newRoles, permissions);
        seedAdminUser(roles.get(RoleCode.ADMIN));
        seedSellerUser(roles.get(RoleCode.SELLER), roles.get(RoleCode.BUYER));
        seedBuyerUser(roles.get(RoleCode.BUYER));

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
                Map.entry(PermissionCode.LISTING_CREATE_SELF, new String[]{"Create Own Listing", "Allows creating own listings"}),
                Map.entry(PermissionCode.LISTING_PUBLISH_SELF, new String[]{"Publish Own Listing", "Allows publishing own listings"}),
                Map.entry(PermissionCode.CREDIT_READ_SELF, new String[]{"Read Own Credits", "Allows reading own credit balances and pricing"}),
                Map.entry(PermissionCode.CREDIT_PURCHASE_SELF, new String[]{"Purchase Own Credits", "Allows purchasing listing and valuation credits"}),
                Map.entry(PermissionCode.INSPECTION_REPORT_SUBMIT, new String[]{"Submit Inspection Report", "Allows submitting inspection reports"}),
                Map.entry(PermissionCode.STAFF_LISTING_REVIEW, new String[]{"Review Listings", "Allows staff listing review"}),
                Map.entry(PermissionCode.STAFF_FRAUD_REVIEW, new String[]{"Review Fraud", "Allows staff fraud review"}),
                Map.entry(PermissionCode.STAFF_DISPUTE_REVIEW, new String[]{"Review Disputes", "Allows staff dispute review"}),
                Map.entry(PermissionCode.STAFF_PAYOUT_REVIEW, new String[]{"Review Payouts", "Allows staff payout review"}),
                Map.entry(PermissionCode.STAFF_PAYOUT_HOLD, new String[]{"Hold Payouts", "Allows staff payout holds"}),
                Map.entry(PermissionCode.STAFF_USER_WARN, new String[]{"Warn Users", "Allows staff user warnings"}),
                Map.entry(PermissionCode.STAFF_USER_TEMP_RESTRICT, new String[]{"Temporarily Restrict Users", "Allows temporary user restrictions"}),
                Map.entry(PermissionCode.ADMIN_STAFF_MANAGE, new String[]{"Manage Staff", "Allows admin staff management"}),
                Map.entry(PermissionCode.ADMIN_RBAC_MANAGE, new String[]{"Manage RBAC", "Allows admin role and permission management"}),
                Map.entry(PermissionCode.ADMIN_PRICING_MANAGE, new String[]{"Manage Pricing", "Allows admin pricing management"}),
                Map.entry(PermissionCode.ADMIN_COMMISSION_MANAGE, new String[]{"Manage Commission", "Allows admin commission management"}),
                Map.entry(PermissionCode.ADMIN_CONFIG_MANAGE, new String[]{"Manage Configuration", "Allows admin configuration management"}),
                Map.entry(PermissionCode.ADMIN_PERMANENT_BAN, new String[]{"Permanently Ban Users", "Allows admin permanent bans"}),
                Map.entry(PermissionCode.ADMIN_HIGH_VALUE_PAYOUT, new String[]{"Approve High Value Payouts", "Allows admin high value payout approval"}),
                Map.entry(PermissionCode.USER_READ_ANY, new String[]{"Read Any User", "Allows viewing details of any user in the system"}),
                Map.entry(PermissionCode.USER_STATUS_UPDATE, new String[]{"Update User Status", "Allows activating, locking, or disabling user accounts"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_READ_ANY, new String[]{"Read Any Seller Verification", "Allows viewing all seller verification requests"}),
                Map.entry(PermissionCode.SELLER_VERIFICATION_REVIEW, new String[]{"Review Seller Verification", "Allows approving or rejecting seller verification requests"}),
                Map.entry(PermissionCode.POST_REVIEW, new String[]{"Review Posts", "Allows approving or rejecting submitted posts"}),
                Map.entry(PermissionCode.INSPECTION_CENTER_ACCOUNT_MANAGE, new String[]{"Manage Inspection Center Accounts", "Allows provisioning Inspection Center partner accounts"}),
                Map.entry(PermissionCode.ROLE_READ, new String[]{"Read Roles", "Allows viewing available roles and permissions in the system"}),
                Map.entry(PermissionCode.INSPECTION_ORDER_READ_SELF, new String[]{"Read Assigned Inspections", "Read own assigned inspection orders"}),
                Map.entry(PermissionCode.INSPECTION_ORDER_READ_ANY, new String[]{"Read All Inspections", "Read and filter all inspection orders"}),
                Map.entry(PermissionCode.INSPECTION_ORDER_ASSIGN, new String[]{"Assign Inspection Orders", "Assign orders to active inspectors"}),
                Map.entry(PermissionCode.INSPECTION_STAFF_MANAGE, new String[]{"Manage Inspectors", "Create and list inspectors"}),
                Map.entry(PermissionCode.AI_CHAT_SELF, new String[]{"Use Own AI Chat", "Use own chat sessions"}),
                Map.entry(PermissionCode.MEDIA_UPLOAD_SELF, new String[]{"Upload Media", "Upload images"})
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
                RoleCode.INSPECTOR, new String[]{"Inspector", "Inspection and authentication specialist"},
                RoleCode.STAFF, new String[]{"Staff", "Operations and moderation staff"},
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
        // Only newly created roles are passed here. Restarting must not undo an admin's revocations.
        roles.values().forEach(role -> assignPermissionsIfMissing(role, List.of(
                permissions.get(PermissionCode.AI_CHAT_SELF), permissions.get(PermissionCode.MEDIA_UPLOAD_SELF))));
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
                permissions.get(PermissionCode.LISTING_CREATE_SELF),
                permissions.get(PermissionCode.LISTING_PUBLISH_SELF),
                permissions.get(PermissionCode.CREDIT_READ_SELF),
                permissions.get(PermissionCode.CREDIT_PURCHASE_SELF)
        ));

        // STAFF
        assignPermissionsIfMissing(roles.get(RoleCode.STAFF), List.of(
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF),
                permissions.get(PermissionCode.USER_READ_ANY),
                permissions.get(PermissionCode.SELLER_VERIFICATION_READ_ANY),
                permissions.get(PermissionCode.ROLE_READ),
                permissions.get(PermissionCode.STAFF_LISTING_REVIEW),
                permissions.get(PermissionCode.STAFF_FRAUD_REVIEW),
                permissions.get(PermissionCode.STAFF_DISPUTE_REVIEW),
                permissions.get(PermissionCode.STAFF_PAYOUT_REVIEW),
                permissions.get(PermissionCode.STAFF_PAYOUT_HOLD),
                permissions.get(PermissionCode.STAFF_USER_WARN),
                permissions.get(PermissionCode.STAFF_USER_TEMP_RESTRICT)
        ));

        // INSPECTOR
        assignPermissionsIfMissing(roles.get(RoleCode.INSPECTOR), List.of(
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF),
                permissions.get(PermissionCode.INSPECTION_REPORT_SUBMIT),
                permissions.get(PermissionCode.INSPECTION_ORDER_READ_SELF)
        ));

        // INSPECTION_CENTER
        assignPermissionsIfMissing(roles.get(RoleCode.INSPECTION_CENTER), List.of(
                permissions.get(PermissionCode.INSPECTION_ORDER_READ_ANY),
                permissions.get(PermissionCode.INSPECTION_ORDER_ASSIGN),
                permissions.get(PermissionCode.INSPECTION_STAFF_MANAGE),
                permissions.get(PermissionCode.PROFILE_READ_SELF),
                permissions.get(PermissionCode.PROFILE_UPDATE_SELF),
                permissions.get(PermissionCode.PASSWORD_CHANGE_SELF)
        ));

        // ADMIN (All permissions)
        assignPermissionsIfMissing(roles.get(RoleCode.ADMIN), new ArrayList<>(permissions.values()));
    }

    private void assignPermissionsIfMissing(Role role, List<Permission> requiredPermissions) {
        if (role == null) {
            return;
        }
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
        User adminUser = userRepository.findByEmailIgnoreCase(normalizedAdminEmail).orElse(null);

        if (adminUser != null) {
            log.warn("Admin seed account already exists; skipping changes to credentials and roles");
            return;
        }

        adminUser = new User(normalizedAdminEmail, passwordEncoder.encode(adminPassword), AccountStatus.ACTIVE);
        adminUser.setEmailVerified(true);

        UserProfile profile = new UserProfile(adminUser, adminFullName, null, null);
        adminUser.setProfile(profile);
        if (adminRole != null) {
            adminUser.addRole(adminRole);
        }

        userRepository.save(adminUser);
        log.info("Successfully seeded default Admin user [{}] from environment variables.", normalizedAdminEmail);
    }

    private void seedSellerUser(Role sellerRole, Role buyerRole) {
        if (!sellerSeederEnabled) {
            log.info("Seller seeder is disabled by configuration (SEED_SELLER_ENABLED=false).");
            return;
        }

        String normalizedSellerEmail = sellerEmail.trim().toLowerCase();
        User sellerUser = userRepository.findByEmailIgnoreCase(normalizedSellerEmail).orElse(null);

        if (sellerUser != null) {
            log.warn("Seller seed account already exists; skipping changes to credentials and roles");
            return;
        }

        sellerUser = new User(normalizedSellerEmail, passwordEncoder.encode(sellerPassword), AccountStatus.ACTIVE);
        sellerUser.setEmailVerified(true);

        UserProfile profile = new UserProfile(sellerUser, sellerFullName, null, null);
        sellerUser.setProfile(profile);
        if (buyerRole != null) {
            sellerUser.addRole(buyerRole);
        }
        if (sellerRole != null) {
            sellerUser.addRole(sellerRole);
        }

        userRepository.save(sellerUser);
        log.info("Successfully seeded default Seller user [{}] from environment variables.", normalizedSellerEmail);
    }

    private void seedBuyerUser(Role buyerRole) {
        if (!buyerSeederEnabled) {
            return;
        }
        if (buyerEmail == null || buyerEmail.isBlank()
                || buyerPassword == null || buyerPassword.isBlank()
                || buyerFullName == null || buyerFullName.isBlank()) {
            throw new IllegalStateException("SEED_BUYER_EMAIL, SEED_BUYER_PASSWORD and SEED_BUYER_FULL_NAME are required when SEED_BUYER_ENABLED=true");
        }
        if (buyerRole == null) {
            throw new IllegalStateException("BUYER role is not initialized");
        }

        String normalizedBuyerEmail = buyerEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedBuyerEmail)) {
            log.info("Buyer seed account [{}] already exists; skipping", normalizedBuyerEmail);
            return;
        }

        User buyerUser = new User(normalizedBuyerEmail, passwordEncoder.encode(buyerPassword), AccountStatus.ACTIVE);
        buyerUser.setEmailVerified(true);
        buyerUser.setProfile(new UserProfile(buyerUser, buyerFullName.trim(), null, null));
        buyerUser.addRole(buyerRole);
        userRepository.save(buyerUser);
        log.info("Successfully seeded Buyer user [{}] from environment variables.", normalizedBuyerEmail);
    }
}
