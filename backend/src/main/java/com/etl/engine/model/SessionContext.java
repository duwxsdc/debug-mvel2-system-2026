package com.etl.engine.model;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class SessionContext {
    private final String sessionId;
    private final ConcurrentHashMap<String, Object> variables;
    private final LocalDateTime createdTime;
    private volatile LocalDateTime lastAccessTime;

    public SessionContext(String sessionId) {
        this.sessionId = sessionId;
        this.variables = new ConcurrentHashMap<>();
        this.createdTime = LocalDateTime.now();
        this.lastAccessTime = LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public ConcurrentHashMap<String, Object> getVariables() {
        return variables;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getLastAccessTime() {
        return lastAccessTime;
    }

    public void updateLastAccessTime() {
        this.lastAccessTime = LocalDateTime.now();
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
        updateLastAccessTime();
    }

    public Object getVariable(String name) {
        updateLastAccessTime();
        return variables.get(name);
    }

    public boolean containsVariable(String name) {
        return variables.containsKey(name);
    }

    public void removeVariable(String name) {
        variables.remove(name);
    }

    public void clear() {
        variables.clear();
    }
}
