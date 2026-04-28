package com.etl.engine.manager;

import com.etl.engine.model.SessionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SessionContextManager {

    private static final ThreadLocal<SessionContext> CURRENT_SESSION = new ThreadLocal<>();

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newScheduledThreadPool(1);
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    public SessionContextManager() {
        cleaner.scheduleAtFixedRate(this::cleanExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(sessionId);
        sessions.put(sessionId, context);
        return sessionId;
    }

    public SessionContext getSession(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.updateLastAccessTime();
        }
        return context;
    }

    public void removeSession(String sessionId) {
        SessionContext context = sessions.remove(sessionId);
        if (context != null) {
            context.clear();
        }
    }

    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public <T> T executeInSession(String sessionId, java.util.function.Supplier<T> action) {
        SessionContext context = getSession(sessionId);
        if (context == null) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }
        try {
            CURRENT_SESSION.set(context);
            return action.get();
        } finally {
            CURRENT_SESSION.remove();
        }
    }

    public void executeInSession(String sessionId, Runnable action) {
        SessionContext context = getSession(sessionId);
        if (context == null) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }
        try {
            CURRENT_SESSION.set(context);
            action.run();
        } finally {
            CURRENT_SESSION.remove();
        }
    }

    public static SessionContext currentSession() {
        return CURRENT_SESSION.get();
    }

    private void cleanExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> {
            SessionContext context = entry.getValue();
            long minutesSinceLastAccess = java.time.Duration.between(context.getLastAccessTime(), now).toMinutes();
            return minutesSinceLastAccess > SESSION_TIMEOUT_MINUTES;
        });
    }

    public void shutdown() {
        cleaner.shutdown();
        sessions.values().forEach(SessionContext::clear);
        sessions.clear();
    }
}
