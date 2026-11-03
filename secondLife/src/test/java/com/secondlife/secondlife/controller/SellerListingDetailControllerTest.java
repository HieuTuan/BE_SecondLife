package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.PostDto;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.MarketplaceListingService;
import com.secondlife.secondlife.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerListingDetailControllerTest {
    private static final String PATH = "/api/v1/posts/my-posts/{postId}";
    private final UUID sellerId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private PostService service;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean PostService posts() { return mock(PostService.class); }
        @Bean CurrentUserProvider currentUser() { return new CurrentUserProvider(); }
        @Bean PostController controller(PostService posts, CurrentUserProvider currentUser) {
            return new PostController(posts, currentUser, mock(MarketplaceListingService.class));
        }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(
                            (request, response, error) -> response.sendError(401))).build();
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(PostService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void ownerReceivesListingDetailsAndPrincipalIdCannotBeOverridden() throws Exception {
        when(service.getMyPostDetail(sellerId, postId)).thenReturn(PostDto.builder()
                .id(postId).title("Clothes drying rack").status("SOLD")
                .shippingWeight(2000).shippingLength(160).shippingWidth(43).shippingHeight(18)
                .imageUrls(List.of("https://example.test/front.jpg")).build());

        mvc.perform(get(PATH, postId).param("userId", UUID.randomUUID().toString())
                        .with(user(principal("PROFILE_READ_SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.shippingWeight").value(2000))
                .andExpect(jsonPath("$.shippingLength").value(160))
                .andExpect(jsonPath("$.imageUrls[0]").value("https://example.test/front.jpg"));
        verify(service).getMyPostDetail(sellerId, postId);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get(PATH, postId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void sellerRoleWithoutRequiredPermissionIsRejected() throws Exception {
        mvc.perform(get(PATH, postId).with(user(principal("ROLE_SELLER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anotherOwnersListingReturns403() throws Exception {
        when(service.getMyPostDetail(sellerId, postId))
                .thenThrow(new ForbiddenException("This post belongs to another user"));
        mvc.perform(get(PATH, postId).with(user(principal("PROFILE_READ_SELF"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingListingReturns404() throws Exception {
        when(service.getMyPostDetail(sellerId, postId)).thenThrow(new NotFoundException("Post not found"));
        mvc.perform(get(PATH, postId).with(user(principal("PROFILE_READ_SELF"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Post not found"));
    }

    @Test
    void invalidUuidReturns400BeforeServiceIsCalled() throws Exception {
        mvc.perform(get(PATH, "not-a-uuid").with(user(principal("PROFILE_READ_SELF"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private CustomUserDetails principal(String authority) {
        var principal = mock(CustomUserDetails.class);
        when(principal.getId()).thenReturn(sellerId);
        when(principal.getUsername()).thenReturn("seller@example.test");
        doReturn(List.of(new SimpleGrantedAuthority(authority))).when(principal).getAuthorities();
        return principal;
    }
}
