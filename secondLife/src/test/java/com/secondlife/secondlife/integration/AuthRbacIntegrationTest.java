package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthRbacIntegrationTest.ProbeConfiguration.class)
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.seeder.enabled=false"
})
class AuthRbacIntegrationTest {

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
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockitoBean private NotificationService notificationService;

    @Test
    void registerLoginProtectedRefreshLogoutFlow() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@example.test";
        String registerJson = """
                {"email":"%s","password":"Password@123","fullName":"Day Two Buyer"}
                """.formatted(email);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"));

        String loginJson = "{\"email\":\"%s\",\"password\":\"Password@123\"}".formatted(email);
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        String loginAccessToken = field(loginBody, "accessToken");
        String oldRefreshToken = field(loginBody, "refreshToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + loginAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        String refreshBody = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(oldRefreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = field(refreshBody, "refreshToken");
        assertNotEquals(oldRefreshToken, newRefreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(newRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(newRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refreshTokenReplayRevokesNewSessionPersistently() throws Exception {
        String email = "replay-" + UUID.randomUUID() + "@example.test";
        String registerBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password@123\",\"fullName\":\"Replay Buyer\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String oldRefreshToken = field(registerBody, "refreshToken");
        String refreshBody = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(oldRefreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = field(refreshBody, "refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(oldRefreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(newRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshAllowsOnlyOneRotation() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.test";
        String registerBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Password@123\",\"fullName\":\"Concurrent Buyer\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = field(registerBody, "refreshToken");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return mockMvc.perform(post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON).content(refreshJson(refreshToken)))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertEquals(Set.of(200, 401), Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
    }

    @Test
    void buyerAndStaffCannotUseAdminConfigurationPermission() throws Exception {
        assertTrue(roleRepository.findByCode("INSPECTOR").isPresent());
        User buyer = saveUser("buyer", "BUYER", AccountStatus.ACTIVE);
        User staff = saveUser("staff", "STAFF", AccountStatus.ACTIVE);
        User admin = saveUser("admin", "ADMIN", AccountStatus.ACTIVE);

        mockMvc.perform(get("/test/rbac/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/test/rbac/staff").header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/test/rbac/staff").header("Authorization", bearer(staff)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/test/rbac/admin-config").header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/test/rbac/admin-config").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountCannotUseExistingAccessToken() throws Exception {
        User disabled = saveUser("disabled", "BUYER", AccountStatus.DISABLED);
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(disabled)))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String prefix, String roleCode, AccountStatus status) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        User user = new User(prefix + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password@123"), status);
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user);
    }

    private String field(String body, String name) throws Exception {
        return objectMapper.readTree(body).path("data").path(name).asText();
    }

    private String refreshJson(String token) {
        return "{\"refreshToken\":\"%s\"}".formatted(token);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        RbacProbeController rbacProbeController() {
            return new RbacProbeController();
        }
    }

    @RestController
    static class RbacProbeController {
        @GetMapping("/test/rbac/staff")
        @PreAuthorize("hasAuthority('STAFF_LISTING_REVIEW')")
        ApiResponse<Void> staff() {
            return ApiResponse.success("OK");
        }

        @GetMapping("/test/rbac/admin-config")
        @PreAuthorize("hasAuthority('ADMIN_CONFIG_MANAGE')")
        ApiResponse<Void> adminConfiguration() {
            return ApiResponse.success("OK");
        }
    }
}
