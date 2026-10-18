package com.secondlife.secondlife.security.authentication;

import com.secondlife.secondlife.security.websocket.WebSocketAuthChannelInterceptor;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.security.jwt.JwtAuthenticationFilter;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccessTokenAuthenticationTest {

    private final JwtTokenProvider tokens = spy(new JwtTokenProvider("a".repeat(64), 60_000));
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final AccessTokenAuthenticator authenticator = new AccessTokenAuthenticator(tokens, users);
    private final JwtAuthenticationFilter http = new JwtAuthenticationFilter(authenticator);
    private final WebSocketAuthChannelInterceptor webSocket = new WebSocketAuthChannelInterceptor(authenticator);
    private final MessageChannel channel = mock(MessageChannel.class);
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        user = new User("buyer@example.test", "hash", AccountStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        user.addRole(new Role("BUYER", "Buyer", "Buyer role"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAccountAuthenticatesOnBothTransportsWithTheirExistingPrincipalTypes() throws Exception {
        String token = tokens.generateAccessToken(user);
        CustomUserDetails details = currentDetails();

        Authentication httpAuthentication = authenticateHttp(token);
        Authentication webSocketAuthentication = authenticateWebSocket(token);

        assertNotNull(httpAuthentication);
        assertSame(details, httpAuthentication.getPrincipal());
        assertNotNull(webSocketAuthentication);
        assertEquals(user.getId().toString(), webSocketAuthentication.getName());
        assertEquals(Set.copyOf(details.getAuthorities()), Set.copyOf(webSocketAuthentication.getAuthorities()));
    }

    @Test
    void revokedTokenCannotAuthenticateOnEitherTransport() throws Exception {
        String token = tokens.generateAccessToken(user);
        user.bumpTokenVersion();
        currentDetails();

        assertNull(authenticateHttp(token));
        assertNull(authenticateWebSocket(token));
        assertPrivateSubscriptionDenied();
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void nonActiveAccountCannotAuthenticateOnEitherTransport(AccountStatus status) throws Exception {
        String token = tokens.generateAccessToken(user);
        user.setAccountStatus(status);
        currentDetails();

        assertNull(authenticateHttp(token));
        assertNull(authenticateWebSocket(token));
        assertPrivateSubscriptionDenied();
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "wrong-signature"})
    void invalidTokenCannotAuthenticateOrLoadUserOnEitherTransport(String kind) throws Exception {
        String token = switch (kind) {
            case "expired" -> new JwtTokenProvider("a".repeat(64), -1000).generateAccessToken(user);
            case "wrong-signature" -> new JwtTokenProvider("b".repeat(64), 60_000).generateAccessToken(user);
            default -> "invalid.jwt.token";
        };

        assertNull(authenticateHttp(token));
        assertNull(authenticateWebSocket(token));
        verifyNoInteractions(users);
    }

    @Test
    void deletedAccountCannotAuthenticateOnEitherTransport() throws Exception {
        String token = tokens.generateAccessToken(user);
        doThrow(new UsernameNotFoundException("User not found")).when(users).loadUserById(user.getId());

        assertNull(authenticateHttp(token));
        assertNull(authenticateWebSocket(token));
    }

    @Test
    void revokedTokenClearsExistingHttpAuthentication() throws Exception {
        String token = tokens.generateAccessToken(user);
        user.bumpTokenVersion();
        currentDetails();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "existing-user", null, Set.of()));

        assertNull(authenticateHttp(token));
    }

    @Test
    void missingTokenKeepsBothTransportsAnonymous() throws Exception {
        http.doFilter(new MockHttpServletRequest("GET", "/api/v1/posts"),
                new MockHttpServletResponse(), (req, res) -> { });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertSame(message, webSocket.preSend(message, channel));
        assertNull(headers.getUser());
        verifyNoInteractions(tokens, users);
        assertPrivateSubscriptionDenied();
    }

    private CustomUserDetails currentDetails() {
        CustomUserDetails details = new CustomUserDetails(user);
        doReturn(details).when(users).loadUserById(user.getId());
        return details;
    }

    private Authentication authenticateHttp(String token) throws Exception {
        clearInvocations(tokens);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        http.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        verify(tokens).parseAccessToken(token);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Authentication authenticateWebSocket(String token) {
        clearInvocations(tokens);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer " + token);
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        Message<?> result = webSocket.preSend(message, channel);
        verify(tokens).parseAccessToken(token);
        return (Authentication) MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class).getUser();
    }

    private void assertPrivateSubscriptionDenied() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/notifications");
        headers.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () -> webSocket.preSend(message, channel));
    }
}
