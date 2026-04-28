package com.etl.engine;

import com.etl.engine.manager.SessionContextManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextManagerTest {

    private SessionContextManager manager;

    @BeforeEach
    void setUp() {
        manager = new SessionContextManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    @DisplayName("测试创建会话")
    void testCreateSession() {
        String sessionId = manager.createSession();
        
        assertNotNull(sessionId);
        assertTrue(manager.sessionExists(sessionId));
    }

    @Test
    @DisplayName("测试获取会话")
    void testGetSession() {
        String sessionId = manager.createSession();
        
        var context = manager.getSession(sessionId);
        
        assertNotNull(context);
        assertEquals(sessionId, context.getSessionId());
    }

    @Test
    @DisplayName("测试获取不存在的会话")
    void testGetNonExistentSession() {
        var context = manager.getSession("non-existent-session");
        
        assertNull(context);
    }

    @Test
    @DisplayName("测试删除会话")
    void testRemoveSession() {
        String sessionId = manager.createSession();
        
        assertTrue(manager.sessionExists(sessionId));
        
        manager.removeSession(sessionId);
        
        assertFalse(manager.sessionExists(sessionId));
    }

    @Test
    @DisplayName("测试会话变量设置和获取")
    void testSessionVariables() {
        String sessionId = manager.createSession();
        var context = manager.getSession(sessionId);
        
        context.setVariable("testKey", "testValue");
        
        assertEquals("testValue", context.getVariable("testKey"));
    }

    @Test
    @DisplayName("测试会话变量包含判断")
    void testContainsVariable() {
        String sessionId = manager.createSession();
        var context = manager.getSession(sessionId);
        
        context.setVariable("existing", "value");
        
        assertTrue(context.containsVariable("existing"));
        assertFalse(context.containsVariable("non-existing"));
    }

    @Test
    @DisplayName("测试会话变量删除")
    void testRemoveVariable() {
        String sessionId = manager.createSession();
        var context = manager.getSession(sessionId);
        
        context.setVariable("key", "value");
        assertTrue(context.containsVariable("key"));
        
        context.removeVariable("key");
        assertFalse(context.containsVariable("key"));
    }

    @Test
    @DisplayName("测试会话计数")
    void testSessionCount() {
        assertEquals(0, manager.getSessionCount());
        
        manager.createSession();
        assertEquals(1, manager.getSessionCount());
        
        manager.createSession();
        assertEquals(2, manager.getSessionCount());
    }

    @Test
    @DisplayName("测试会话隔离")
    void testSessionIsolation() {
        String session1 = manager.createSession();
        String session2 = manager.createSession();
        
        var context1 = manager.getSession(session1);
        var context2 = manager.getSession(session2);
        
        context1.setVariable("shared", "session1");
        context2.setVariable("shared", "session2");
        
        assertEquals("session1", context1.getVariable("shared"));
        assertEquals("session2", context2.getVariable("shared"));
    }

    @Test
    @DisplayName("测试ScopedValue上下文透传")
    void testScopedValuePropagation() {
        String sessionId = manager.createSession();
        
        String result = manager.executeInSession(sessionId, () -> {
            var current = SessionContextManager.currentSession();
            return current.getSessionId();
        });
        
        assertEquals(sessionId, result);
    }

    @Test
    @DisplayName("测试会话清除")
    void testSessionClear() {
        String sessionId = manager.createSession();
        var context = manager.getSession(sessionId);
        
        context.setVariable("key1", "value1");
        context.setVariable("key2", "value2");
        
        context.clear();
        
        assertNull(context.getVariable("key1"));
        assertNull(context.getVariable("key2"));
    }
}
