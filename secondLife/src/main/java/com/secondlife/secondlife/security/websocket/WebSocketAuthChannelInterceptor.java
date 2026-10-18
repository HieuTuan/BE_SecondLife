package com.secondlife.secondlife.security.websocket;

import com.secondlife.secondlife.security.authentication.AccessTokenAuthenticator;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final AccessTokenAuthenticator authenticator;

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
            if (destination != null
                    && (destination.startsWith("/user/") || destination.startsWith("/queue/")
                    || destination.contains("notifications"))
                    && accessor.getUser() == null) {
                log.warn("Chặn truy cập trái phép: Người dùng chưa xác thực cố gắng subscribe vào {}", destination);
                throw new AccessDeniedException("Cần đăng nhập để đăng ký nhận thông báo từ " + destination);
            }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return;
        }
        try {
            CustomUserDetails userDetails = authenticator.authenticate(bearerToken.substring(7));
            // The UUID principal name routes convertAndSendToUser messages to this session.
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails.getId().toString(), null, userDetails.getAuthorities()
            ));
        } catch (Exception ex) {
            accessor.setUser(null);
            log.warn("WebSocket authentication failed ({})", ex.getClass().getSimpleName());
        }
    }
}
