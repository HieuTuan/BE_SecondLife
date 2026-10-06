package com.secondlife.secondlife.security.jwt;

import tools.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.common.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastLogTime = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");

        String logKey = request.getMethod() + ":" + request.getRequestURI() + ":" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        Long lastTime = lastLogTime.get(logKey);
        if (lastTime == null || (now - lastTime) > 5000) {
            lastLogTime.put(logKey, now);
            log.warn("Unauthorized {} request at {} [RemoteAddr={}, Origin={}, Referer={}, User-Agent={}]",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), origin, referer, userAgent);
        } else {
            log.debug("Repeated unauthorized {} request at {}", request.getMethod(), request.getRequestURI());
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ErrorResponse errorResponse = ErrorResponse.of(
                "Full authentication is required to access this resource",
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
