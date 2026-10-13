package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.TokenResponse;
import com.secondlife.secondlife.entity.Permission;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import com.secondlife.secondlife.service.GoogleAuthService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.TokenService;
import com.secondlife.secondlife.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecureLoginApiTest {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String EMAIL = "be02.user@example.com";
    private static final String PASSWORD = "Unique-BE02-ApiPassword#2026";
    private static final UUID USER_ID = UUID.fromString("fd6dbdb9-4624-48f2-98fa-6f4b987a2e36");

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TokenService tokenService;
    @Mock private NotificationService notificationService;
    @Mock private GoogleAuthService googleAuthService;

    private final JsonMapper json = JsonMapper.builder().build();
    private BCryptPasswordEncoder passwordEncoder;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new CustomUserDetailsService(userRepository));
        provider.setPasswordEncoder(passwordEncoder);
        AuthServiceImpl authService = new AuthServiceImpl(
                userRepository, roleRepository, passwordEncoder, new ProviderManager(provider), tokenService,
                notificationService, new UserMapper(), googleAuthService);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, null, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    static Stream<Arguments> deniedAccounts() {
        return Stream.of(
                Arguments.of("unknown email", null, PASSWORD),
                Arguments.of("wrong password", AccountStatus.ACTIVE, "Incorrect-BE02-Password#2026"),
                Arguments.of("locked account", AccountStatus.LOCKED, PASSWORD),
                Arguments.of("disabled account", AccountStatus.DISABLED, PASSWORD),
                Arguments.of("pending account", AccountStatus.PENDING_VERIFICATION, PASSWORD));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deniedAccounts")
    void deniedLoginReturnsTheSameGeneric401(String scenario, AccountStatus accountStatus, String submittedPassword)
            throws Exception {
        User user = accountStatus == null ? null : userWithPassword(PASSWORD, accountStatus);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase(EMAIL)).thenReturn(Optional.ofNullable(user));

        MvcResult result = assertGenericUnauthorized(login(EMAIL, submittedPassword));

        assertNoCredentialLeak(result, user);
        assertFalse(result.getResponse().getContentAsString().contains(EMAIL));
        assertNoLoginSideEffects();
    }

    @Test
    void missingAccountAfterAuthenticationStillReturnsGeneric401() throws Exception {
        User user = userWithPassword(PASSWORD, AccountStatus.ACTIVE);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user), Optional.empty());

        assertGenericUnauthorized(login(EMAIL, PASSWORD));

        verify(userRepository, times(2)).findByEmailWithAuthoritiesIgnoreCase(EMAIL);
        assertNoLoginSideEffects();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"LOCKED", "DISABLED", "PENDING_VERIFICATION"})
    void accountBecomingInactiveAfterAuthenticationStillReturnsGeneric401(AccountStatus status) throws Exception {
        User authenticatedUser = userWithPassword(PASSWORD, AccountStatus.ACTIVE);
        User reloadedUser = userWithPassword(PASSWORD, status);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase(EMAIL))
                .thenReturn(Optional.of(authenticatedUser), Optional.of(reloadedUser));

        assertGenericUnauthorized(login(EMAIL, PASSWORD));

        assertNoLoginSideEffects();
        assertEquals(null, reloadedUser.getLastLoginAt());
    }

    @Test
    void successfulLoginReturnsOnlyDtoFieldsAndNeverPasswordOrHash() throws Exception {
        User user = prepareSuccessfulLogin(PASSWORD);

        MvcResult result = assertSuccessfulDto(login(EMAIL, PASSWORD));

        assertNoCredentialLeak(result, user);
        verify(userRepository).save(user);
        verify(tokenService).generateTokenPair(user);
        assertNotNull(user.getLastLoginAt());
        verifyNoInteractions(roleRepository, notificationService, googleAuthService);
    }

    @Test
    void uppercasePaddedEmailIsNormalizedAndPasswordEdgeWhitespaceIsPreserved() throws Exception {
        String passwordWithSpaces = " " + PASSWORD + " ";
        User user = prepareSuccessfulLogin(passwordWithSpaces);

        MvcResult result = assertSuccessfulDto(login(" \tBE02.USER@EXAMPLE.COM \t", passwordWithSpaces));

        assertNoCredentialLeak(result, user);
        assertFalse(result.getResponse().getContentAsString().contains(passwordWithSpaces));
        verify(userRepository, times(2)).findByEmailWithAuthoritiesIgnoreCase(EMAIL);
        verify(tokenService).generateTokenPair(user);
    }

    @Test
    void addingSpacesToAnExistingPasswordDoesNotAuthenticate() throws Exception {
        User user = userWithPassword(PASSWORD, AccountStatus.ACTIVE);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertGenericUnauthorized(login(EMAIL, " " + PASSWORD + " "));

        assertNoLoginSideEffects();
    }

    @Test
    void asciiPasswordAtThe72CharacterBoundaryCanAuthenticate() throws Exception {
        String boundaryPassword = "p".repeat(72);
        User user = prepareSuccessfulLogin(boundaryPassword);

        MvcResult result = assertSuccessfulDto(login(EMAIL, boundaryPassword));

        assertNoCredentialLeak(result, user);
        assertFalse(result.getResponse().getContentAsString().contains(boundaryPassword));
        verify(tokenService).generateTokenPair(user);
    }

    @Test
    void passwordBelow72CharactersButAbove72Utf8BytesReturnsGeneric401BeforeAuthentication() throws Exception {
        String multibytePassword = "\u00e9".repeat(37);
        assertTrue(multibytePassword.length() <= 72);
        assertTrue(multibytePassword.getBytes(StandardCharsets.UTF_8).length > 72);

        MvcResult result = assertGenericUnauthorized(login(EMAIL, multibytePassword));

        assertFalse(result.getResponse().getContentAsString().contains(multibytePassword));
        verifyNoInteractions(userRepository, roleRepository, tokenService, notificationService, googleAuthService);
    }

    static Stream<Arguments> invalidCredentials() {
        String oversizedEmail = "u@" + "a".repeat(63) + "." + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(62);
        return Stream.of(
                Arguments.of("null email", null, PASSWORD, "email"),
                Arguments.of("empty email", "", PASSWORD, "email"),
                Arguments.of("blank email", " \t\n", PASSWORD, "email"),
                Arguments.of("malformed email", "not-an-email", PASSWORD, "email"),
                Arguments.of("oversized email", oversizedEmail, PASSWORD, "email"),
                Arguments.of("null password", EMAIL, null, "password"),
                Arguments.of("empty password", EMAIL, "", "password"),
                Arguments.of("blank password", EMAIL, " \t\n", "password"),
                Arguments.of("oversized ASCII password", EMAIL, "p".repeat(73), "password"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCredentials")
    void invalidInputReturns400WithFieldErrorsBeforeAuthentication(
            String scenario, String email, String password, String field) throws Exception {
        login(email, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));

        verifyNoInteractions(userRepository, roleRepository, tokenService, notificationService, googleAuthService);
    }

    static Stream<Arguments> nonStringCredentials() {
        return Stream.of(
                Arguments.of("numeric email", 123, PASSWORD, "email"),
                Arguments.of("boolean email", true, PASSWORD, "email"),
                Arguments.of("array email", List.of(EMAIL), PASSWORD, "email"),
                Arguments.of("object email", Map.of("value", EMAIL), PASSWORD, "email"),
                Arguments.of("numeric password", EMAIL, 123456, "password"),
                Arguments.of("boolean password", EMAIL, false, "password"),
                Arguments.of("array password", EMAIL, List.of(PASSWORD), "password"),
                Arguments.of("object password", EMAIL, Map.of("value", PASSWORD), "password"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonStringCredentials")
    void nonStringJsonCredentialsReturn400WithFieldErrorsBeforeAuthentication(
            String scenario, Object email, Object password, String field) throws Exception {
        login(email, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));

        verifyNoInteractions(userRepository, roleRepository, tokenService, notificationService, googleAuthService);
    }

    @Test
    void omittedCredentialsReturn400WithBothFieldErrors() throws Exception {
        mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));

        verifyNoInteractions(userRepository, roleRepository, tokenService, notificationService, googleAuthService);
    }

    private ResultActions login(Object email, Object password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private MvcResult assertGenericUnauthorized(ResultActions request) throws Exception {
        MvcResult result = request
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value(LOGIN_PATH))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        assertEquals(Set.of("success", "message", "path", "errors", "timestamp"),
                new HashSet<>(json.readTree(result.getResponse().getContentAsString()).propertyNames()));
        return result;
    }

    private MvcResult assertSuccessfulDto(ResultActions request) throws Exception {
        MvcResult result = request
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.accessToken").value("be02-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("be02-refresh-token"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInMs").value(900000))
                .andExpect(jsonPath("$.data.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.fullName").value("BE02 Test User"))
                .andExpect(jsonPath("$.data.user.phone").value("0912345678"))
                .andExpect(jsonPath("$.data.user.avatarUrl").value("https://example.com/be02-avatar.png"))
                .andExpect(jsonPath("$.data.user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.roles").value(hasItem("BUYER")))
                .andExpect(jsonPath("$.data.permissions").value(hasItem("PROFILE_READ_SELF")))
                .andReturn();
        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertEquals(Set.of("accessToken", "refreshToken", "accessTokenExpiresInMs", "user", "roles", "permissions"),
                new HashSet<>(data.propertyNames()));
        assertEquals(Set.of("id", "email", "fullName", "phone", "avatarUrl", "accountStatus", "emailVerified", "gender"),
                new HashSet<>(data.path("user").propertyNames()));
        return result;
    }

    private User prepareSuccessfulLogin(String rawPassword) {
        User user = userWithPassword(rawPassword, AccountStatus.ACTIVE);
        when(userRepository.findByEmailWithAuthoritiesIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.generateTokenPair(user))
                .thenReturn(new TokenResponse("be02-access-token", "be02-refresh-token", 900000));
        return user;
    }

    private User userWithPassword(String rawPassword, AccountStatus status) {
        User user = new User(EMAIL, passwordEncoder.encode(rawPassword), status);
        user.setId(USER_ID);
        user.setEmailVerified(true);
        user.setProfile(new UserProfile(user, "BE02 Test User", "0912345678", "https://example.com/be02-avatar.png"));
        Role buyer = new Role("BUYER", "Buyer", "Test buyer role");
        buyer.addPermission(new Permission("PROFILE_READ_SELF", "Read own profile", "Test profile permission"));
        user.addRole(buyer);
        return user;
    }

    private void assertNoLoginSideEffects() {
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(tokenService, roleRepository, notificationService, googleAuthService);
    }

    private void assertNoCredentialLeak(MvcResult result, User user) throws Exception {
        String response = result.getResponse().getContentAsString();
        assertFalse(response.contains(PASSWORD));
        if (user != null) {
            assertFalse(response.contains(user.getPasswordHash()));
        }
        assertNoSensitiveKeys(json.readTree(response));
    }

    private void assertNoSensitiveKeys(JsonNode node) {
        for (String property : node.propertyNames()) {
            String lowerCaseProperty = property.toLowerCase(Locale.ROOT);
            assertFalse(lowerCaseProperty.contains("password") || lowerCaseProperty.contains("hash"),
                    "Credential field leaked in response: " + property);
        }
        for (JsonNode child : node) {
            assertNoSensitiveKeys(child);
        }
    }
}
