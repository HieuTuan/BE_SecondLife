package com.secondlife.secondlife.security;

import com.secondlife.secondlife.config.SecurityConfig;
import com.secondlife.secondlife.controller.CategoryController;
import com.secondlife.secondlife.controller.ItemController;
import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.authentication.AccessTokenAuthenticator;
import com.secondlife.secondlife.security.filter.InspectionSealRateLimitingFilter;
import com.secondlife.secondlife.security.handler.RestAccessDeniedHandler;
import com.secondlife.secondlife.security.handler.RestAuthenticationEntryPoint;
import com.secondlife.secondlife.security.jwt.JwtAuthenticationFilter;
import com.secondlife.secondlife.service.CatalogService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicCatalogAuthorizationTest {
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final CategoryResponse CATEGORY = new CategoryResponse(CATEGORY_ID, "Appliances", "Category");
    private static final ItemResponse ITEM = new ItemResponse(ITEM_ID, "Rice cooker", CATEGORY_ID, CATEGORY);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private CatalogService catalog;

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean CatalogService catalog() { return mock(CatalogService.class); }
        @Bean CategoryController categories(CatalogService catalog) { return new CategoryController(catalog); }
        @Bean ItemController items(CatalogService catalog) { return new ItemController(catalog); }
        @Bean JwtAuthenticationFilter jwt() { return new JwtAuthenticationFilter(mock(AccessTokenAuthenticator.class)); }
        @Bean InspectionSealRateLimitingFilter seals() { return new InspectionSealRateLimitingFilter(); }
        @Bean RestAuthenticationEntryPoint unauthorized() { return new RestAuthenticationEntryPoint(new ObjectMapper()); }
        @Bean RestAccessDeniedHandler forbidden() { return new RestAccessDeniedHandler(new ObjectMapper()); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean UserDetailsService users() { return mock(UserDetailsService.class); }
    }

    @BeforeAll
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-cors", Map.of(
                "app.cors.allowed-origins", "http://localhost:3000",
                "app.cors.allowed-methods", "GET,POST,PUT,DELETE,OPTIONS",
                "app.cors.allowed-headers", "*",
                "app.cors.allow-credentials", "true",
                "app.cors.max-age-seconds", "3600")));
        context.register(Config.class);
        context.refresh();
        catalog = context.getBean(CatalogService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll
    void close() { context.close(); }

    @BeforeEach
    void resetCatalog() {
        reset(catalog);
        when(catalog.categories()).thenReturn(List.of(CATEGORY));
        when(catalog.category(CATEGORY_ID)).thenReturn(CATEGORY);
        when(catalog.items(null)).thenReturn(List.of(ITEM));
        when(catalog.items(CATEGORY_ID)).thenReturn(List.of(ITEM));
        when(catalog.item(ITEM_ID)).thenReturn(ITEM);
        when(catalog.createCategory(any(CategoryRequest.class))).thenReturn(CATEGORY);
        when(catalog.updateCategory(eq(CATEGORY_ID), any(CategoryRequest.class))).thenReturn(CATEGORY);
        when(catalog.createItem(any(ItemRequest.class))).thenReturn(ITEM);
        when(catalog.updateItem(eq(ITEM_ID), any(ItemRequest.class))).thenReturn(ITEM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"categories", "category-detail", "items", "item-detail", "category-items", "filtered-items"})
    void anonymousUsersCanReadCatalog(String endpoint) throws Exception {
        String path = switch (endpoint) {
            case "categories" -> "/api/v1/categories";
            case "category-detail" -> "/api/v1/categories/" + CATEGORY_ID;
            case "items" -> "/api/v1/items";
            case "item-detail" -> "/api/v1/items/" + ITEM_ID;
            case "category-items" -> "/api/v1/items/category/" + CATEGORY_ID;
            case "filtered-items" -> "/api/v1/items?categoryId=" + CATEGORY_ID;
            default -> throw new IllegalArgumentException(endpoint);
        };
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void anonymousUsersCannotWriteCatalog(String method, String resource) throws Exception {
        mvc.perform(mutation(method, resource)).andExpect(status().isUnauthorized());
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void roleAndPermissionAreBothRequired(String method, String resource) throws Exception {
        mvc.perform(mutation(method, resource).with(user("buyer").roles("BUYER")))
                .andExpect(status().isForbidden());
        mvc.perform(mutation(method, resource).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(mutation(method, resource).with(user("permission-only")
                        .authorities(new SimpleGrantedAuthority("ADMIN_CATALOG_MANAGE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void authorizedAdminCanWriteCatalog(String method, String resource) throws Exception {
        mvc.perform(mutation(method, resource).with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ADMIN_CATALOG_MANAGE"))))
                .andExpect(status().is(method.equals("POST") ? 201 : method.equals("DELETE") ? 204 : 200));
        switch (method + " " + resource) {
            case "POST categories" -> verify(catalog).createCategory(any(CategoryRequest.class));
            case "PUT categories" -> verify(catalog).updateCategory(eq(CATEGORY_ID), any(CategoryRequest.class));
            case "DELETE categories" -> verify(catalog).deleteCategory(CATEGORY_ID);
            case "POST items" -> verify(catalog).createItem(any(ItemRequest.class));
            case "PUT items" -> verify(catalog).updateItem(eq(ITEM_ID), any(ItemRequest.class));
            case "DELETE items" -> verify(catalog).deleteItem(ITEM_ID);
            default -> throw new IllegalArgumentException(method + " " + resource);
        }
    }

    @Test
    void unrelatedPrivateReadsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/orders/seller")).andExpect(status().isUnauthorized());
        verifyNoInteractions(catalog);
    }

    private MockHttpServletRequestBuilder mutation(String method, String resource) {
        UUID id = resource.equals("categories") ? CATEGORY_ID : ITEM_ID;
        String path = "/api/v1/" + resource + (method.equals("POST") ? "" : "/" + id);
        String body = resource.equals("categories") ? "{\"name\":\"Appliances\",\"description\":\"Category\"}"
                : "{\"categoryId\":\"" + CATEGORY_ID + "\",\"name\":\"Rice cooker\"}";
        return request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static Stream<Arguments> mutations() {
        return Stream.of("categories", "items")
                .flatMap(resource -> Stream.of("POST", "PUT", "DELETE").map(method -> Arguments.of(method, resource)));
    }
}
