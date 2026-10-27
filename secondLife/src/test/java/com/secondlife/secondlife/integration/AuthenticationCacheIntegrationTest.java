package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.AdminStatusUpdateRequest;
import com.secondlife.secondlife.dto.request.ChangePasswordRequest;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.PermissionRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.authentication.AccessTokenAuthenticator;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import com.secondlife.secondlife.security.userdetails.UserPrincipalCache;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.UserService;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.secondlife.secondlife.integration.AuthenticationCacheIntegrationTest$SqlCapture"
})
class AuthenticationCacheIntegrationTest {
    private static final String PASSWORD = "Password@123456";
    private static final GhnTestDatabase DATABASE = new GhnTestDatabase("AUTH_CACHE_TEST_JDBC_URL");

    static boolean databaseAvailable() {
        return GhnTestDatabase.available("AUTH_CACHE_TEST_JDBC_URL");
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @AfterAll
    static void closeDatabase() {
        DATABASE.close();
    }

    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private UserService userService;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AccessTokenAuthenticator authenticator;
    @Autowired private CustomUserDetailsService detailsService;
    @Autowired private UserPrincipalCache cache;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private NotificationService notifications;

    private TransactionTemplate transaction;

    @BeforeEach
    void clearCache() {
        cache.clear();
        transaction = new TransactionTemplate(transactions);
        SqlCapture.clear();
    }

    @Test
    void warmAuthenticationUsesOneSmallQueryInsteadOfLoadingProfilesAndPermissions() {
        Fixture fixture = fixture();
        SqlCapture.clear();
        authenticator.authenticate(fixture.token());
        assertEquals(2, SqlCapture.statements().size(), "Cold authentication: state + full details");

        SqlCapture.clear();
        CustomUserDetails principal = authenticator.authenticate(fixture.token());
        List<String> sql = SqlCapture.statements();
        assertEquals(1, sql.size(), "Warm authentication still checks current security state");
        String query = sql.getFirst().toLowerCase(Locale.ROOT);
        assertTrue(query.contains("permissions_version"));
        assertFalse(query.contains("user_profiles"));
        assertFalse(query.contains("role_permissions"));
        assertEquals(fixture.userId(), principal.getId());
        assertTrue(hasPermission(principal, fixture.permissionCode()));
    }

    @Test
    void lockingAnAccountRejectsWarmTokensInIndependentCacheInstances() {
        Fixture fixture = fixture();
        AccessTokenAuthenticator secondInstance = secondInstance();
        authenticator.authenticate(fixture.token());
        secondInstance.authenticate(fixture.token());

        userService.updateAdminUserStatus(UUID.randomUUID(), fixture.userId(),
                new AdminStatusUpdateRequest(AccountStatus.LOCKED));

        assertThrows(DisabledException.class, () -> authenticator.authenticate(fixture.token()));
        assertThrows(DisabledException.class, () -> secondInstance.authenticate(fixture.token()));
    }

    @Test
    void passwordChangesRevokeWarmAccessTokensInIndependentCacheInstances() {
        Fixture fixture = fixture();
        AccessTokenAuthenticator secondInstance = secondInstance();
        authenticator.authenticate(fixture.token());
        secondInstance.authenticate(fixture.token());

        userService.changePassword(fixture.userId(),
                new ChangePasswordRequest(PASSWORD, "Changed@123456", "Changed@123456"));

        assertThrows(BadCredentialsException.class, () -> authenticator.authenticate(fixture.token()));
        assertThrows(BadCredentialsException.class, () -> secondInstance.authenticate(fixture.token()));
    }

    @Test
    void persistedPermissionRevisionRefreshesOtherInstancesWithoutRevokingTheJwt() {
        Fixture fixture = fixture();
        AccessTokenAuthenticator secondInstance = secondInstance();
        assertTrue(hasPermission(authenticator.authenticate(fixture.token()), fixture.permissionCode()));
        assertTrue(hasPermission(secondInstance.authenticate(fixture.token()), fixture.permissionCode()));

        removePermission(fixture, false);

        assertFalse(hasPermission(authenticator.authenticate(fixture.token()), fixture.permissionCode()));
        assertFalse(hasPermission(secondInstance.authenticate(fixture.token()), fixture.permissionCode()));
        assertEquals(0, users.findSecurityStateById(fixture.userId()).orElseThrow().getTokenVersion());
    }

    @Test
    void rolledBackPermissionChangesPreserveCommittedAuthoritiesAndCacheEntries() {
        Fixture fixture = fixture();
        CustomUserDetails before = authenticator.authenticate(fixture.token());
        removePermission(fixture, true);
        SqlCapture.clear();

        CustomUserDetails after = authenticator.authenticate(fixture.token());

        assertSame(before, after);
        assertTrue(hasPermission(after, fixture.permissionCode()));
        assertEquals(1, SqlCapture.statements().size());
    }

    @Test
    void authenticationProjectionHandlesUsersWithNoAssignedRoles() {
        User user = users.saveAndFlush(new User(UUID.randomUUID() + "@example.test", "hash", AccountStatus.ACTIVE));
        assertEquals(0, users.findSecurityStateById(user.getId()).orElseThrow().getPermissionsVersion());
        assertTrue(authenticator.authenticate(tokens.generateAccessToken(user)).getAuthorities().isEmpty());
    }

    private void removePermission(Fixture fixture, boolean rollback) {
        transaction.executeWithoutResult(status -> {
            Role role = roles.findByCodeForUpdate(fixture.roleCode()).orElseThrow();
            Permission permission = permissions.findByCode(fixture.permissionCode()).orElseThrow();
            role.removePermission(permission);
            roles.saveAndFlush(role);
            if (rollback) {
                cache.clearAfterCommit();
                status.setRollbackOnly();
            }
        });
    }

    private AccessTokenAuthenticator secondInstance() {
        return new AccessTokenAuthenticator(tokens, detailsService, new UserPrincipalCache());
    }

    private Fixture fixture() {
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Permission permission = permissions.saveAndFlush(new Permission("CACHE_ACCESS_" + suffix, "Cache access", null));
            Role role = roles.saveAndFlush(new Role("CACHE_" + suffix, "Cache role", null));
            role.addPermission(permission);
            User user = new User(suffix + "@example.test", passwords.encode(PASSWORD), AccountStatus.ACTIVE);
            user.addRole(role);
            users.saveAndFlush(user);
            return new Fixture(user.getId(), role.getCode(), permission.getCode(), tokens.generateAccessToken(user));
        });
    }

    private boolean hasPermission(CustomUserDetails details, String permission) {
        return details.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    private record Fixture(UUID userId, String roleCode, String permissionCode, String token) { }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            STATEMENTS.get().add(sql);
            return sql;
        }

        static void clear() { STATEMENTS.get().clear(); }
        static List<String> statements() { return List.copyOf(STATEMENTS.get()); }
    }
}
