package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class AdminUserRolesIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwt;
    @Autowired TokenService tokens;
    @MockitoBean NotificationService notifications;
    // Domain repositories are mocked only for ownership tests; RBAC/auth use the actual PostgreSQL schema.
    @MockitoBean PostRepository posts;
    @MockitoBean AiChatSessionRepository sessions;
    @MockitoBean InspectionOrderRepository orders;

    @Test void onlyAuthorizedAdminCanReadAndReplaceUserRoles() throws Exception {
        User admin = user("ADMIN"), buyer = user("BUYER"), staff = user("STAFF");
        String path = "/api/v1/admin/users/" + buyer.getId() + "/roles";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (User unauthorized : List.of(buyer, staff)) {
            mvc.perform(get(path).header("Authorization", bearer(unauthorized))).andExpect(status().isForbidden());
            mvc.perform(put(path).header("Authorization", bearer(unauthorized)).contentType(MediaType.APPLICATION_JSON)
                    .content(roleBody(Set.of("BUYER"), Set.of("ADMIN")))).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/roles/STAFF/permissions").header("Authorization", bearer(unauthorized))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedPermissionCodes\":[],\"permissionCodes\":[]}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCodes[0]").value("BUYER"));
        mvc.perform(get("/api/v1/admin/users/{id}/role-changes", buyer.getId()).header("Authorization", bearer(buyer)))
                .andExpect(status().isForbidden());
    }

    @Test void roleChangesRevokeSessionsAndResolveUnionOfPermissionsWithAudit() throws Exception {
        User admin = user("ADMIN"), buyer = user("BUYER");
        String adminBearer = bearer(admin), oldBearer = bearer(buyer);
        String refresh = tokens.generateTokenPair(buyer).refreshToken();
        String path = "/api/v1/admin/users/" + buyer.getId() + "/roles";
        mvc.perform(get("/api/v1/admin/users").header("Authorization", oldBearer)).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", adminBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(Set.of("BUYER"), Set.of("BUYER", "STAFF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roleCodes.length()").value(2));
        mvc.perform(get("/api/users/me").header("Authorization", oldBearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refresh)))).andExpect(status().isUnauthorized());
        String newBearer = login(buyer);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", newBearer)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", newBearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'SELLER_VERIFICATION_SUBMIT')]").exists())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'USER_READ_ANY')]").exists());
        mvc.perform(put(path).header("Authorization", adminBearer).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of("BUYER", "STAFF"), Set.of("BUYER")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", newBearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", login(buyer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/{id}/role-changes", buyer.getId()).header("Authorization", adminBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].changedBy").value(admin.getId().toString()));
    }

    @Test void validatesRoleUpdatesWithoutEscalationOrStaleWrites() throws Exception {
        User admin = user("ADMIN"), buyer = user("BUYER");
        String auth = bearer(admin), path = "/api/v1/admin/users/" + buyer.getId() + "/roles";
        mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of("BUYER"), Set.of("UNKNOWN")))).andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"STAFF\"]}")).andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of(), Set.of("STAFF")))).andExpect(status().isConflict());
        mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of("BUYER"), Set.of("BUYER", "SELLER")))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/admin/users/{id}/roles", admin.getId()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(roleBody(Set.of("ADMIN"), Set.of())))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/users/{id}/roles", UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of("BUYER"), Set.of("BUYER")))).andExpect(status().isOk());
        assertEquals(0, users.findById(buyer.getId()).orElseThrow().getTokenVersion());
        mvc.perform(get("/api/v1/admin/users/{id}/role-changes", buyer.getId()).header("Authorization", auth))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test void concurrentRoleReplacementsDoNotLoseUpdates() throws Exception {
        User admin = user("ADMIN"), buyer = user("BUYER");
        String auth = bearer(admin), path = "/api/v1/admin/users/" + buyer.getId() + "/roles";
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> replaceAfter(start, path, auth, Set.of("BUYER", "STAFF")));
            var second = pool.submit(() -> replaceAfter(start, path, auth, Set.of("BUYER", "INSPECTOR")));
            start.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        }
    }

    @Test void pendingAccountsCannotUsePreviouslyIssuedAccessTokens() throws Exception {
        User buyer = user("BUYER");
        String auth = bearer(buyer);
        buyer.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        users.saveAndFlush(buyer);
        mvc.perform(get("/api/users/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
    }

    @Test void adminCanGrantAdminRoleAndOnlyNewlyAuthenticatedSessionCanUseIt() throws Exception {
        User admin = user("ADMIN"), buyer = user("BUYER");
        String old = bearer(buyer);
        mvc.perform(put("/api/v1/admin/users/{id}/roles", buyer.getId()).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(roleBody(Set.of("BUYER"), Set.of("BUYER", "ADMIN"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/permissions").header("Authorization", old)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/permissions").header("Authorization", login(buyer))).andExpect(status().isOk());
    }

    @Test void chatRejectsOtherUsersSessionAndForgedPostAssociationBeforeReadingHistoryOrCallingAi() throws Exception {
        User buyer = user("BUYER"), victim = user("BUYER");
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID()); session.setUser(victim);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        mvc.perform(multipart("/api/v1/ai/chat").param("message", "hello").param("sessionId", session.getId().toString())
                .header("Authorization", bearer(buyer))).andExpect(status().isForbidden());
        Post victimPost = new Post(); victimPost.setId(UUID.randomUUID()); victimPost.setUser(victim);
        when(posts.findById(victimPost.getId())).thenReturn(Optional.of(victimPost));
        mvc.perform(multipart("/api/v1/ai/chat").param("message", "hello").param("postId", victimPost.getId().toString())
                .header("Authorization", bearer(buyer))).andExpect(status().isForbidden());
        verify(sessions, never()).save(any());
        verify(posts, never()).save(any());
    }

    @Test void sellerCannotFinalizeOrSubmitAnotherUsersPostDespiteHavingListingPermissions() throws Exception {
        User seller = user("SELLER"), victim = user("SELLER");
        Post post = new Post(); post.setId(UUID.randomUUID()); post.setUser(victim);
        AiChatSession forged = new AiChatSession(); forged.setId(UUID.randomUUID()); forged.setUser(seller); forged.setPostId(post.getId());
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(posts.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(sessions.findById(forged.getId())).thenReturn(Optional.of(forged));
        mvc.perform(post("/api/v1/posts/finalize-chat/{id}", forged.getId()).header("Authorization", bearer(seller)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/posts/submit/{id}", post.getId()).header("Authorization", bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Title\",\"description\":\"Description\",\"price\":100000}"))
                .andExpect(status().isForbidden());
        verify(posts, never()).save(any());
        verify(sessions, never()).save(any());
    }

    @Test void inspectorAndCenterResolveDistinctPermissionsAndEnforceAssignedOrderOwnership() throws Exception {
        User inspector = user("INSPECTOR"), center = user("INSPECTION_CENTER"), other = user("INSPECTOR");
        when(orders.findByInspectorId(inspector.getId())).thenReturn(List.of());
        mvc.perform(get("/api/v1/inspector/orders").header("Authorization", bearer(inspector))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/inspector/orders/all").header("Authorization", bearer(inspector))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inspector/orders").header("Authorization", bearer(center))).andExpect(status().isForbidden());
        InspectionOrder order = new InspectionOrder(); order.setId(UUID.randomUUID()); order.setInspector(other);
        when(orders.findOwnerId(order.getId())).thenReturn(Optional.of(other.getId()));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        mvc.perform(post("/api/v1/inspector/orders/{id}/result", order.getId()).header("Authorization", bearer(inspector))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FAILED\",\"note\":\"invalid\"}"))
                .andExpect(status().isForbidden());
        verify(orders, never()).save(any());
    }

    private int replaceAfter(CountDownLatch start, String path, String auth, Set<String> desired) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return mvc.perform(put(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(roleBody(Set.of("BUYER"), desired))).andReturn().getResponse().getStatus();
    }
    private String roleBody(Set<String> expected, Set<String> desired) throws Exception {
        return json.writeValueAsString(Map.of("expectedRoleCodes", expected, "roleCodes", desired));
    }
    private User user(String code) {
        User user = new User("roles-" + UUID.randomUUID() + "@example.test", encoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.setEmailVerified(true); user.addRole(roles.findByCode(code).orElseThrow());
        return users.saveAndFlush(user);
    }
    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
    private String login(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", "Password@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("accessToken").asText();
    }
}
