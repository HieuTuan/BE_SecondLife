package com.secondlife.secondlife.config;

import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");
            if (authorization != null && !authorization.isEmpty()) {
                String bearerToken = authorization.get(0);
                if (bearerToken.startsWith("Bearer ")) {
                    String jwt = bearerToken.substring(7);
                    if (jwtTokenProvider.validateToken(jwt)) {
                        UUID userId = jwtTokenProvider.getUserIdFromToken(jwt);
                        try {
                            UserDetails userDetails = userDetailsService.loadUserById(userId);
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                            // Cài đặt Principal cho WebSocket session.
                            // Quan trọng: userId.toString() sẽ làm "tên" của principal để convertAndSendToUser route tới đúng user ID.
                            accessor.setUser(() -> userId.toString());
                        } catch (Exception e) {
                            log.error("WebSocket Authentication Failed: {}", e.getMessage());
                        }
                    }
                }
            }
        }
        return message;
    }
}
