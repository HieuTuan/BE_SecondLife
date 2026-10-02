package com.secondlife.secondlife.security;

import com.secondlife.secondlife.service.PostService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class PostReviewAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private PostService postService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/approve", postId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRoleWithoutPostReviewPermissionIsRejected() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/approve", postId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postService);
    }

    @Test
    @WithMockUser(authorities = "POST_REVIEW")
    void postReviewPermissionAllowsApproval() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/posts/{postId}/approve", postId))
                .andExpect(status().isOk());

        verify(postService).approvePost(postId);
    }
}
