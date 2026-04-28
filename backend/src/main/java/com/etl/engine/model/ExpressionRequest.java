package com.etl.engine.model;

import java.util.Map;

public record ExpressionRequest(
    String sessionId,
    String expression,
    Map<String, Object> variables
) {}
