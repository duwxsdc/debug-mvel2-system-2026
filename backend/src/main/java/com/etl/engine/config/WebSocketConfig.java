package com.etl.engine.config;

import com.etl.engine.handler.ExpressionWebSocketHandler;
import com.etl.engine.manager.SessionContextManager;
import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.engine.SqlExecutionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionContextManager sessionManager;
    private final MvelSandboxEngine mvelEngine;
    private final SqlExecutionEngine sqlEngine;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(SessionContextManager sessionManager,
                           MvelSandboxEngine mvelEngine,
                           SqlExecutionEngine sqlEngine,
                           ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.mvelEngine = mvelEngine;
        this.sqlEngine = sqlEngine;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(expressionWebSocketHandler(), "/ws/expression")
                .setAllowedOrigins("*");
    }

    @Bean
    public ExpressionWebSocketHandler expressionWebSocketHandler() {
        return new ExpressionWebSocketHandler(sessionManager, mvelEngine, sqlEngine, objectMapper);
    }
}
