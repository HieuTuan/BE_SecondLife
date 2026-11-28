package com.secondlife.secondlife.security.websocket;

import com.secondlife.secondlife.security.authentication.AccessTokenAuthenticator;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final java.util.Set<String> PERSONAL_QUEUES = java.util.Set.of(
            "/user/queue/notifications", "/user/queue/messages",
            "/user/queue/wallet", "/user/queue/wallet-updates", "/user/queue/updates");

    private final AccessTokenAuthenticator authenticator;

    public WebSocketAuthChannelInterceptor(AccessTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    private SocketExpiry expiry;
    private com.secondlife.secondlife.security.jwt.JwtTokenProvider jwt;

    @org.springframework.beans.factory.annotation.Autowired
    void configureExpiry(SocketExpiry expiry, com.secondlife.secondlife.security.jwt.JwtTokenProvider jwt) {
        this.expiry = expiry;
        this.jwt = jwt;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !PERSONAL_QUEUES.contains(destination)) {
                throw new AccessDeniedException("Subscription is not permitted");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new AccessDeniedException("Client messages must use the REST API");
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new AccessDeniedException("Authentication required");
        }
        try {
            CustomUserDetails userDetails = authenticator.authenticate(bearerToken.substring(7));
            if (expiry != null) expiry.expireAt(accessor.getSessionId(), jwt.verifiedExpiration(bearerToken.substring(7)));
            // The UUID principal name routes convertAndSendToUser messages to this session.
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails.getId().toString(), null, userDetails.getAuthorities()
            ));
        } catch (Exception ex) {
            accessor.setUser(null);
            throw new AccessDeniedException("Invalid WebSocket credentials");
        }
    }
}
