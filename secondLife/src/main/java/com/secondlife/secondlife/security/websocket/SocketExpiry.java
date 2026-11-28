package com.secondlife.secondlife.security.websocket;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.*;

@Component
public final class SocketExpiry {
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> deadlines = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor clock = new ScheduledThreadPoolExecutor(1);

    public SocketExpiry() { clock.setRemoveOnCancelPolicy(true); }

    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                try { super.afterConnectionEstablished(session); }
                catch (Exception failure) { sessions.remove(session.getId(), session); throw failure; }
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId(), session);
                var deadline = deadlines.remove(session.getId());
                if (deadline != null) deadline.cancel(false);
                super.afterConnectionClosed(session, status);
            }
        };
    }

    public void expireAt(String id, Instant expiry) {
        var session = sessions.get(id);
        if (session == null) throw new IllegalStateException("Missing transport session");
        var deadline = clock.schedule(() -> {
            try { if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION); }
            catch (IOException ignored) { /* Transport already closed. */ }
        }, Math.max(0, expiry.toEpochMilli() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        var previous = deadlines.put(id, deadline);
        if (previous != null) previous.cancel(false);
        if (sessions.get(id) != session && deadlines.remove(id, deadline)) deadline.cancel(false);
    }

    @PreDestroy public void shutdown() { clock.shutdownNow(); }
}
