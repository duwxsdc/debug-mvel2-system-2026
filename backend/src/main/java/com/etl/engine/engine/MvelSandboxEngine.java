package com.etl.engine.engine;

import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class MvelSandboxEngine {

    private static final Set<String> DANGEROUS_CLASSES = Set.of(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.System",
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.RandomAccessFile",
        "java.nio.file.Files",
        "java.nio.file.Path",
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.URL",
        "java.lang.ClassLoader",
        "java.lang.reflect.Method",
        "java.lang.reflect.Constructor",
        "java.lang.Thread",
        "java.util.concurrent.ExecutorService"
    );

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=");

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final long timeoutMs;

    public MvelSandboxEngine() {
        this(5000);
    }

    public MvelSandboxEngine(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Object execute(String expression, Map<String, Object> context) {
        List<String> lines = splitExpression(expression);
        Object lastResult = null;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            lastResult = executeSingleExpression(trimmedLine, context);
        }

        return lastResult;
    }

    private List<String> splitExpression(String expression) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int braceCount = 0;
        int bracketCount = 0;
        int parenCount = 0;
        boolean inString = false;
        char quoteChar = '\0';

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if (c == '\\' && inString && i + 1 < expression.length()) {
                currentLine.append(c).append(expression.charAt(i + 1));
                i++;
                continue;
            }

            if (c == quoteChar) {
                inString = false;
                currentLine.append(c);
                continue;
            }

            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                quoteChar = c;
                currentLine.append(c);
                continue;
            }

            if (!inString) {
                switch (c) {
                    case '{': braceCount++; break;
                    case '}': braceCount--; break;
                    case '[': bracketCount++; break;
                    case ']': bracketCount--; break;
                    case '(': parenCount++; break;
                    case ')': parenCount--; break;
                    case ';':
                        if (braceCount == 0 && bracketCount == 0 && parenCount == 0) {
                            lines.add(currentLine.toString());
                            currentLine = new StringBuilder();
                            continue;
                        }
                        break;
                }
            }

            currentLine.append(c);
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private Object executeSingleExpression(String expression, Map<String, Object> context) {
        validateExpression(expression);

        Future<Object> future = executor.submit(() -> {
            VariableResolverFactory factory = new MapVariableResolverFactory(context);
            
            if (isAssignment(expression)) {
                MVEL.eval(expression, context);
                String varName = extractVariableName(expression);
                return context.get(varName);
            } else {
                return MVEL.eval(expression, context);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExpressionTimeoutException("表达式执行超时，超过 " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new ExpressionExecutionException("表达式执行失败: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExpressionExecutionException("表达式执行被中断", e);
        }
    }

    private void validateExpression(String expression) {
        for (String dangerousClass : DANGEROUS_CLASSES) {
            if (expression.contains(dangerousClass)) {
                throw new SecurityException("表达式包含危险类引用: " + dangerousClass);
            }
        }

        if (expression.contains("System.exit") || expression.contains("Runtime.getRuntime")) {
            throw new SecurityException("表达式包含危险操作");
        }
    }

    private boolean isAssignment(String expression) {
        return ASSIGNMENT_PATTERN.matcher(expression).find();
    }

    private String extractVariableName(String expression) {
        java.util.regex.Matcher matcher = ASSIGNMENT_PATTERN.matcher(expression);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public static class ExpressionTimeoutException extends RuntimeException {
        public ExpressionTimeoutException(String message) {
            super(message);
        }
    }

    public static class ExpressionExecutionException extends RuntimeException {
        public ExpressionExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
