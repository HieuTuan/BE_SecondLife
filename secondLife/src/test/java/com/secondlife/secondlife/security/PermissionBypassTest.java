package com.secondlife.secondlife.security;

import com.secondlife.secondlife.service.PostService;
import com.secondlife.secondlife.service.AdminRbacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties",
        properties = "app.security.permissions-enabled=false")
class PermissionBypassTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private AdminRbacService rbacService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void legacyDisableFlagCannotBypassMethodAuthorization() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/approve", postId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postService);
    }

    @Test
    void anonymousRequestStillRequiresAuthentication() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/approve", postId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService);
    }

    @Test
    @WithMockUser(roles = "BUYER")
    void rbacCrudRemainsAdminOnlyWhenMethodPermissionsAreDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/permissions")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/roles")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/permissions")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/permissions/REPORT_EXPORT")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/permissions/REPORT_EXPORT")).andExpect(status().isForbidden());
        verifyNoInteractions(rbacService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rbacManagementStillRequiresPermissionWhenMethodPermissionsAreDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/permissions")).andExpect(status().isForbidden());
        verifyNoInteractions(rbacService);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "ADMIN_RBAC_MANAGE"})
    void authorizedAdminCanReadCatalogWhenMethodPermissionsAreDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/permissions")).andExpect(status().isOk());
        verify(rbacService).getPermissions();
    }
}
