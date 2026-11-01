package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.service.AdminTopupPackageService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminTopupPackageControllerTest {
    private static final String BASE = "/api/v1/admin/topup-packages";
    private static final UUID ID = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper();
    private AnnotationConfigWebApplicationContext context;
    private AdminTopupPackageService service;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean AdminTopupPackageService packages() { return mock(AdminTopupPackageService.class); }
        @Bean AdminTopupPackageController controller(AdminTopupPackageService packages) {
            return new AdminTopupPackageController(packages);
        }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(
                            (request, response, error) -> response.sendError(401))).build();
        }
    }

    @BeforeAll
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(AdminTopupPackageService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void resetService() { reset(service); }

    @AfterAll
    void close() { context.close(); }

    private MockHttpServletRequestBuilder call(String action) {
        String method = action.equals("GET_DETAIL") ? "GET" : action;
        String path = action.equals("POST") || action.equals("GET") ? BASE : BASE + "/" + ID + (action.equals("PATCH") ? "/status" : "");
        return request(HttpMethod.valueOf(method), path).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(validBody()));
    }

    private MockHttpServletRequestBuilder asAdmin(String action) {
        return call(action).with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ADMIN_PRICING_MANAGE")));
    }

    private static Map<String, Object> validBody() {
        return new HashMap<>(Map.of("name", "Basic Combo", "description", "Test package", "postCredits", 2,
                "chatCredits", 10, "valuationCredits", 2, "price", 59000, "active", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_DETAIL", "POST", "PUT", "DELETE", "PATCH"})
    void anonymousIsRejected(String action) throws Exception {
        mvc.perform(call(action)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_DETAIL", "POST", "PUT", "DELETE", "PATCH"})
    void adminWithoutPermissionIsRejected(String action) throws Exception {
        mvc.perform(call(action).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_DETAIL", "POST", "PUT", "DELETE", "PATCH"})
    void permissionWithoutAdminRoleIsRejected(String action) throws Exception {
        mvc.perform(call(action).with(user("seller").authorities(new SimpleGrantedAuthority("ROLE_SELLER"),
                        new SimpleGrantedAuthority("ADMIN_PRICING_MANAGE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminCanUseAllCrudEndpointsWithStandardWrapper() throws Exception {
        var dto = new TopupPackageResponse(ID, "Basic Combo", "Test package", 2, 10, 2,
                new BigDecimal("59000"), new BigDecimal("7.81"), new BigDecimal("64000"), true, true, 0);
        when(service.createPackage(any())).thenReturn(dto);
        when(service.updatePackage(any(), any())).thenReturn(dto);
        when(service.getPackages()).thenReturn(List.of(dto));
        when(service.getPackage(ID)).thenReturn(dto);
        mvc.perform(asAdmin("POST")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.discountPercentage").value(7.81));
        mvc.perform(asAdmin("PUT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(59000));
        mvc.perform(asAdmin("GET")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].postCredits").value(2));
        mvc.perform(asAdmin("GET_DETAIL")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ID.toString()));
        mvc.perform(asAdmin("DELETE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("Đã ngừng bán gói dịch vụ"));
        verify(service).deletePackage(ID);
        var captor = ArgumentCaptor.forClass(TopupPackageRequest.class);
        verify(service).createPackage(captor.capture());
        assertEquals(true, captor.getValue().active());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("name", null), Arguments.of("name", "   "), Arguments.of("name", "x".repeat(101)),
                Arguments.of("description", "x".repeat(256)), Arguments.of("postCredits", -1),
                Arguments.of("chatCredits", -1), Arguments.of("valuationCredits", -1),
                Arguments.of("price", null), Arguments.of("price", 999.99), Arguments.of("price", 1000.001),
                Arguments.of("price", "100000000000000000"),
                Arguments.of("active", null));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void validationRejectsInvalidFieldsOnCreateAndUpdate(String field, Object value) throws Exception {
        var body = validBody();
        body.put(field, value);
        for (String action : List.of("POST", "PUT")) {
            mvc.perform(asAdmin(action).content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").isNotEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void acceptsBoundaryValuesAndOptionalDescription() throws Exception {
        var body = validBody();
        body.put("name", "x".repeat(100));
        body.put("description", null);
        body.put("postCredits", 0);
        body.put("chatCredits", 0);
        body.put("price", 1000);
        for (boolean active : List.of(false, true)) {
            body.put("active", active);
            mvc.perform(asAdmin("POST").content(json.writeValueAsString(body))).andExpect(status().isCreated());
        }
    }

    @Test
    void missingPackageReturns404ForReadUpdateAndDelete() throws Exception {
        var error = new NotFoundException("Không tìm thấy gói nạp");
        when(service.getPackage(ID)).thenThrow(error);
        when(service.updatePackage(any(), any())).thenThrow(error);
        doThrow(error).when(service).deletePackage(ID);
        for (String action : List.of("GET_DETAIL", "PUT", "DELETE")) {
            mvc.perform(asAdmin(action)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy gói nạp"));
        }
    }

    @Test
    void malformedUuidOrMissingBodyIsBadRequest() throws Exception {
        mvc.perform(asAdmin("POST").content("")).andExpect(status().isBadRequest());
        mvc.perform(request(HttpMethod.GET, BASE + "/not-a-uuid").with(user("admin").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ADMIN_PRICING_MANAGE"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
