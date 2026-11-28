package com.secondlife.secondlife.security;

import com.secondlife.secondlife.security.websocket.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeChannelPolicyTest {
    private final WebSocketAuthChannelInterceptor policy = new WebSocketAuthChannelInterceptor(null);

    private void check(StompCommand command, String destination, boolean authenticated) {
        var headers = StompHeaderAccessor.create(command);
        if (destination != null) headers.setDestination(destination);
        if (authenticated) headers.setUser(() -> "account");
        headers.setLeaveMutable(true);
        policy.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }

    @Test void personalQueuesRequireAuthentication() {
        assertThrows(AccessDeniedException.class, () -> check(StompCommand.CONNECT, null, false));
        assertThrows(AccessDeniedException.class, () -> check(StompCommand.SUBSCRIBE, "/user/queue/messages", false));
        assertDoesNotThrow(() -> check(StompCommand.SUBSCRIBE, "/user/queue/messages", true));
    }

    @Test void privateDeliveryIsServerOnly() {
        assertThrows(AccessDeniedException.class, () -> check(StompCommand.SUBSCRIBE, "/unlisted", true));
        assertThrows(AccessDeniedException.class, () -> check(StompCommand.SEND, "/unlisted", true));
    }
}
