package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.repository.PermissionRepository;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import com.secondlife.secondlife.seeder.RbacDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=true"
})
class AdminRbacIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private RbacDataSeeder seeder;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private WebApplicationContext context;

    @Test
    void preservesLegacyCustomPermissionManagementButRejectsArbitraryCreation() throws Exception {
        User admin = userForRole("ADMIN");
        User buyer = userForRole("BUYER");
        String authorization = bearer(admin);
        String code = "REPORT_EXPORT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String body = objectMapper.writeValueAsString(Map.of("code", code, "name", "Export reports",
                "description", "Custom reporting", "assignableRoles", Set.of("BUYER")));

        mockMvc.perform(post("/api/admin/permissions").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        // Existing custom rows remain manageable for compatibility; only new arbitrary codes are prohibited.
        Permission legacy = new Permission(code, "Export reports", "Custom reporting");
        legacy.getAssignableRoles().add("BUYER");
        permissionRepository.saveAndFlush(legacy);
        mockMvc.perform(post("/api/admin/permissions").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/permissions/{code}", code).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignableRoles[0]").value("BUYER"));

        Set<String> sellerPermissions = rolePermissions(authorization, "SELLER");
        Set<String> invalidSellerPermissions = new HashSet<>(sellerPermissions);
        invalidSellerPermissions.add(code);
        mockMvc.perform(put("/api/admin/roles/SELLER/permissions").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(json(sellerPermissions, invalidSellerPermissions)))
                .andExpect(status().isBadRequest());

        Set<String> original = rolePermissions(authorization, "BUYER");
        Set<String> granted = new HashSet<>(original);
        granted.add(code);
        mockMvc.perform(put("/api/admin/roles/BUYER/permissions").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(json(original, granted)))
                .andExpect(status().isOk());
        assertTrue(userDetailsService.loadUserById(buyer.getId()).getAuthorities().stream()
                .anyMatch(authority -> code.equals(authority.getAuthority())));
        mockMvc.perform(delete("/api/admin/permissions/{code}", code).header("Authorization", authorization))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/permissions/{code}", code).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reports\",\"assignableRoles\":[]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/permissions/{code}", code).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated reports\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated reports"))
                .andExpect(jsonPath("$.data.assignableRoles[0]").value("BUYER"));
        mockMvc.perform(put("/api/admin/roles/BUYER/permissions").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(json(granted, original)))
                .andExpect(status().isOk());
        assertFalse(userDetailsService.loadUserById(buyer.getId()).getAuthorities().stream()
                .anyMatch(authority -> code.equals(authority.getAuthority())));

        mockMvc.perform(put("/api/admin/permissions/{code}", code).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated reports\",\"assignableRoles\":[\"STAFF\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignableRoles[0]").value("STAFF"));
        mockMvc.perform(delete("/api/admin/permissions/{code}", code).header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/permissions/{code}", code).header("Authorization", authorization))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/roles/BUYER/permission-changes").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.permissionCode == '" + code + "')]").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void validatesCatalogRequestsProtectsSystemPermissionsAndBlocksNonAdmins() throws Exception {
        String authorization = bearer(userForRole("ADMIN"));
        String buyerAuthorization = bearer(userForRole("BUYER"));
        String validBody = "{\"code\":\"REPORT_VIEW\",\"name\":\"Reports\",\"assignableRoles\":[\"STAFF\"]}";
        mockMvc.perform(post("/api/admin/permissions").contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/permissions").header("Authorization", buyerAuthorization)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/permissions/PROFILE_READ_SELF").header("Authorization", buyerAuthorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Read\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/permissions/PROFILE_READ_SELF").header("Authorization", buyerAuthorization))
                .andExpect(status().isForbidden());

        for (String invalidBody : new String[] {
                "{\"code\":\"lowercase\",\"name\":\"Reports\",\"assignableRoles\":[]}",
                "{\"code\":\"REPORT_VIEW\",\"name\":\"   \",\"assignableRoles\":[]}",
                "{\"code\":\"REPORT_VIEW\",\"name\":\"Reports\"}",
                "{\"code\":\"REPORT_VIEW\",\"name\":\"Reports\",\"assignableRoles\":[null]}",
                "{\"code\":\"ROLE_ADMIN\",\"name\":\"Reports\",\"assignableRoles\":[\"BUYER\"]}",
                "{\"code\":\"REPORT_VIEW\",\"name\":\"Reports\",\"assignableRoles\":[\"ADMIN\"]}",
                "{\"code\":\"REPORT_VIEW\",\"name\":\"Reports\",\"assignableRoles\":[\"UNKNOWN\"]}"
        }) {
            mockMvc.perform(post("/api/admin/permissions").header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/admin/permissions/ADMIN_RBAC_MANAGE").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.systemPermission").value(true));
        mockMvc.perform(delete("/api/admin/permissions/ADMIN_RBAC_MANAGE").header("Authorization", authorization))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/permissions/ADMIN_RBAC_MANAGE").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RBAC\",\"assignableRoles\":[\"STAFF\"]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/permissions/MISSING").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWithoutRbacPermissionCannotAccessCatalog() throws Exception {
        MockMvc securityMockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        securityMockMvc.perform(get("/api/admin/permissions")).andExpect(status().isForbidden());
    }

    @Test
    void concurrentAdminsCannotOverwriteEachOthersRolePermissions() throws Exception {
        String authorization = bearer(userForRole("ADMIN"));
        String roleCode = "INSPECTION_CENTER";
        Set<String> original = rolePermissions(authorization, roleCode);
        Set<String> firstDesired = new HashSet<>(original);
        firstDesired.remove("PROFILE_READ_SELF");
        Set<String> secondDesired = new HashSet<>(original);
        secondDesired.remove("PASSWORD_CHANGE_SELF");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mockMvc.perform(put("/api/admin/roles/{role}/permissions", roleCode)
                                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                                .content(json(original, firstDesired))).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mockMvc.perform(put("/api/admin/roles/{role}/permissions", roleCode)
                                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                                .content(json(original, secondDesired))).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        } finally {
            Set<String> current = rolePermissions(authorization, roleCode);
            mockMvc.perform(put("/api/admin/roles/{role}/permissions", roleCode)
                            .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                            .content(json(current, original))).andExpect(status().isOk());
        }
    }

    @Test
    void adminCanManageRolePermissionsWithAuditAndLiveAuthorization() throws Exception {
        User admin = userForRole("ADMIN");
        User staff = userForRole("STAFF");
        User buyer = userForRole("BUYER");
        String adminBearer = bearer(admin);
        String staffBearer = bearer(staff);

        mockMvc.perform(get("/api/admin/permissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/roles").header("Authorization", staffBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'ADMIN_RBAC_MANAGE')].assignableRoles").exists());
        mockMvc.perform(get("/api/admin/roles").header("Authorization", adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/roles/UNKNOWN").header("Authorization", adminBearer))
                .andExpect(status().isNotFound());

        Set<String> original = rolePermissions(adminBearer, "STAFF");
        assertTrue(original.contains("SELLER_VERIFICATION_READ_ANY"));
        Set<String> reduced = new HashSet<>(original);
        reduced.remove("SELLER_VERIFICATION_READ_ANY");

        mockMvc.perform(put("/api/admin/roles/STAFF/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(original, Set.of("ADMIN_RBAC_MANAGE"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/roles/ADMIN/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(original, original)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/roles/STAFF/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[]}"))
                .andExpect(status().isBadRequest());
        assertEquals(original, rolePermissions(adminBearer, "STAFF"));

        mockMvc.perform(get("/api/staff/seller-verifications").header("Authorization", staffBearer))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/roles/STAFF/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(original, reduced)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("STAFF"));
        assertEquals(reduced, rolePermissions(adminBearer, "STAFF"));
        mockMvc.perform(get("/api/staff/seller-verifications").header("Authorization", staffBearer))
                .andExpect(status().isForbidden());

        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(reduced, rolePermissions(adminBearer, "STAFF"));
        mockMvc.perform(put("/api/admin/roles/STAFF/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(original, original)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/roles/STAFF/permissions")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reduced, original)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/seller-verifications").header("Authorization", staffBearer))
                .andExpect(status().isOk());

        String history = mockMvc.perform(get("/api/admin/roles/STAFF/permission-changes")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(history).path("data").path("items");
        assertEquals(2, items.size());
        Set<String> actions = Set.of(items.get(0).path("action").asText(),
                items.get(1).path("action").asText());
        assertEquals(Set.of("GRANT", "REVOKE"), actions);
        assertTrue(items.get(0).path("changedBy").asText().equals(admin.getId().toString()));
        assertFalse(items.get(0).path("changedAt").asText().isBlank());
    }

    private Set<String> rolePermissions(String authorization, String roleCode) throws Exception {
        String body = mockMvc.perform(get("/api/admin/roles/{roleCode}", roleCode)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Set<String> codes = new HashSet<>();
        objectMapper.readTree(body).path("data").path("permissionCodes")
                .forEach(node -> codes.add(node.asText()));
        return codes;
    }

    private String json(Set<String> expected, Set<String> desired) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "expectedPermissionCodes", expected, "permissionCodes", desired));
    }

    private User userForRole(String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User("rbac-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user);
    }
}
