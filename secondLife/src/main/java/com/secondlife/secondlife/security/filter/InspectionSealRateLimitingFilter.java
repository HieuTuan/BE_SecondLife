package com.secondlife.secondlife.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class    InspectionSealRateLimitingFilter extends OncePerRequestFilter {

    public static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentMap<String, Deque<Long>> ipRequestTimestamps = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isSealPath = uri.startsWith("/api/v1/inspection/seal/") || uri.startsWith("/api/v1/inspections/seal/");
        boolean isGet = "GET".equalsIgnoreCase(request.getMethod());
        return !isSealPath || !isGet;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();

        boolean allowed = checkAndRecord(clientIp, now);
        if (!allowed) {
            log.warn("Rate limit exceeded for IP [{}] on public seal endpoint [{}]", clientIp, request.getRequestURI());
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                {"code":429,"status":429,"message":"Quá nhiều yêu cầu tra cứu tem niêm phong. Giới hạn tối đa 30 lượt/phút. Vui lòng thử lại sau."}
            """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean checkAndRecord(String ip, long now) {
        Deque<Long> timestamps = ipRequestTimestamps.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long threshold = now - WINDOW_MS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    public void resetForIp(String ip) {
        ipRequestTimestamps.remove(ip);
    }
}
