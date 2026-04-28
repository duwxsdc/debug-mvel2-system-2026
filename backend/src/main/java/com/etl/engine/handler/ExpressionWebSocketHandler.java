package com.etl.engine.handler;

import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.engine.SqlExecutionEngine;
import com.etl.engine.manager.SessionContextManager;
import com.etl.engine.model.ExpressionResult;
import com.etl.engine.model.SessionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExpressionWebSocketHandler extends TextWebSocketHandler {

    private final SessionContextManager sessionManager;
    private final MvelSandboxEngine mvelEngine;
    private final SqlExecutionEngine sqlEngine;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;

    public ExpressionWebSocketHandler(SessionContextManager sessionManager, 
                                      MvelSandboxEngine mvelEngine,
                                      SqlExecutionEngine sqlEngine,
                                      ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.mvelEngine = mvelEngine;
        this.sqlEngine = sqlEngine;
        this.objectMapper = objectMapper;
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = sessionManager.createSession();
        session.getAttributes().put("sessionId", sessionId);
        sessionMap.put(sessionId, session);

        ExpressionResult result = ExpressionResult.success(sessionId, "CONNECT", "会话已建立", Map.of());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        
        executorService.submit(() -> {
            try {
                String payload = message.getPayload();
                ExpressionResult result = processExpression(sessionId, payload);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
            } catch (Exception e) {
                try {
                    ExpressionResult errorResult = ExpressionResult.error(sessionId, message.getPayload(), e.getMessage());
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResult)));
                } catch (IOException ioEx) {
                    // ignore
                }
            }
        });
    }

    private ExpressionResult processExpression(String sessionId, String expression) {
        try {
            SessionContext context = sessionManager.getSession(sessionId);
            if (context == null) {
                return ExpressionResult.error(sessionId, expression, "会话不存在");
            }

            if (expression.trim().toUpperCase().startsWith("SELECT") || 
                expression.trim().toUpperCase().startsWith("SHOW")) {
                var results = sqlEngine.executeQuery(expression);
                return ExpressionResult.success(sessionId, expression, results, context.getVariables());
            }

            Object result = mvelEngine.execute(expression, context.getVariables());
            return ExpressionResult.success(sessionId, expression, result, context.getVariables());

        } catch (MvelSandboxEngine.ExpressionTimeoutException e) {
            return ExpressionResult.error(sessionId, expression, "执行超时: " + e.getMessage());
        } catch (SqlExecutionEngine.SqlSecurityException e) {
            return ExpressionResult.error(sessionId, expression, "SQL安全错误: " + e.getMessage());
        } catch (SecurityException e) {
            return ExpressionResult.error(sessionId, expression, "安全限制: " + e.getMessage());
        } catch (Exception e) {
            return ExpressionResult.error(sessionId, expression, "执行错误: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            sessionMap.remove(sessionId);
            sessionManager.removeSession(sessionId);
        }
    }
}
