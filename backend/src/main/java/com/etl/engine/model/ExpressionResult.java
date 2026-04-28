package com.etl.engine.model;

import java.time.LocalDateTime;
import java.util.Map;

public record ExpressionResult(
    String sessionId,
    String expression,
    Object result,
    boolean success,
    String errorMessage,
    Map<String, Object> assignedVariables,
    LocalDateTime timestamp
) {
    public static ExpressionResult success(String sessionId, String expression, Object result, Map<String, Object> variables) {
        return new ExpressionResult(sessionId, expression, result, true, null, variables, LocalDateTime.now());
    }

    public static ExpressionResult error(String sessionId, String expression, String errorMessage) {
        return new ExpressionResult(sessionId, expression, null, false, errorMessage, Map.of(), LocalDateTime.now());
    }
}
