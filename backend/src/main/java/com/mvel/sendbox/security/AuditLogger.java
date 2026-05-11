// ============ 审计日志系统 ============

package com.mvel.sendbox.security;

import com.mvel.sendbox.config.MvelSandboxConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步审计日志器
 */
public class AuditLogger {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogger.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final MvelSandboxConfig.AuditConfig config;
    private final BlockingQueue<AuditEvent> eventQueue;
    private final ExecutorService writerExecutor;
    private final AtomicLong eventId = new AtomicLong(0);

    // 统计计数器
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public AuditLogger(MvelSandboxConfig.AuditConfig config) {
        this.config = config;
        this.eventQueue = new LinkedBlockingQueue<>(config.getBufferSize());
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mvel-audit-writer");
            t.setDaemon(true);
            return t;
        });

        if (config.isEnabled()) {
            startWriter();
        }

        // 初始化计数器
        Arrays.asList("class_allowed", "class_denied", "method_allowed", "method_denied",
                "property_access", "property_denied",
                "variable_read", "variable_write", "variable_created", "variable_denied", "depth_exceeded",
                "expression_compiled", "expression_executed",
                "child_factory",
                "sql_executed", "sql_denied").forEach(k -> counters.put(k, new AtomicLong(0)));
    }

    private void startWriter() {
        writerExecutor.submit(() -> {
            List<AuditEvent> batch = new ArrayList<>();
            while (!Thread.interrupted()) {
                try {
                    AuditEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        batch.add(event);
                        eventQueue.drainTo(batch, 100); // 批量获取
                        writeBatch(batch);
                        batch.clear();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void writeBatch(List<AuditEvent> events) {
        if (events.isEmpty()) return;

        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path logFile = Paths.get(config.getLogPath(), "mvel-audit-" + date + ".log");

        try {
            Files.createDirectories(logFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (AuditEvent event : events) {
                    writer.write(event.toJson());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            logger.error("Failed to write audit log", e);
        }

        if (config.isConsoleOutput()) {
            events.forEach(e -> logger.info("[AUDIT] {}", e.toJson()));
        }
    }

    // ========== 事件记录方法 ==========

    public void logClassAllowed(String className) {
        record("class_allowed", new AuditEvent("CLASS_ALLOWED",
                Map.of("className", className)));
    }

    public void logClassDenied(String className, String reason) {
        record("class_denied", new AuditEvent("CLASS_DENIED",
                Map.of("className", className, "reason", reason)));
    }

    public void logMethodAllowed(String methodName) {
        record("method_allowed", new AuditEvent("METHOD_ALLOWED",
                Map.of("methodName", methodName)));
    }

    public void logMethodDenied(String methodName) {
        record("method_denied", new AuditEvent("METHOD_DENIED",
                Map.of("methodName", methodName)));
    }

    public void logPropertyAccess(String property, String className) {
        record("property_access", new AuditEvent("PROPERTY_ACCESS",
                Map.of("property", property, "className", className)));
    }

    public void logPropertyDenied(String property, String className) {
        record("property_denied", new AuditEvent("PROPERTY_DENIED",
                Map.of("property", property, "className", className)));
    }

    public void logVariableRead(String name, int depth, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("depth", depth);
        if (config.isLogValues() && value != null) {
            data.put("valueType", value.getClass().getName());
            data.put("value", truncate(value.toString()));
        }
        record("variable_read", new AuditEvent("VARIABLE_READ", data));
    }

    public void logVariableWrite(String name, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        if (config.isLogValues() && value != null) {
            data.put("valueType", value.getClass().getName());
            data.put("value", truncate(value.toString()));
        }
        record("variable_write", new AuditEvent("VARIABLE_WRITE", data));
    }

    public void logVariableCreated(String name, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        if (config.isLogValues() && value != null) {
            data.put("valueType", value.getClass().getName());
        }
        record("variable_created", new AuditEvent("VARIABLE_CREATED", data));
    }

    public void logVariableDenied(String name, String reason) {
        record("variable_denied", new AuditEvent("VARIABLE_DENIED",
                Map.of("name", name, "reason", reason)));
    }

    public void logDepthExceeded(String name, int current, int max) {
        record("depth_exceeded", new AuditEvent("DEPTH_EXCEEDED",
                Map.of("variable", name, "currentDepth", current, "maxDepth", max)));
    }

    public void logChildFactoryCreated() {
        record("child_factory", new AuditEvent("CHILD_FACTORY_CREATED", Map.of()));
    }

    public void logSqlExecuted(String sql, int params, int rows) {
        record("sql_executed", new AuditEvent("SQL_EXECUTED",
                Map.of("sql", truncate(sql, 500), "paramCount", params, "rowCount", rows)));
    }

    public void logSqlDenied(String sql, String reason) {
        record("sql_denied", new AuditEvent("SQL_DENIED",
                Map.of("sql", truncate(sql, 500), "reason", reason)));
    }

    public void logExpressionCompiled(String expression, long timeMs) {
        record("expression_compiled", new AuditEvent("EXPRESSION_COMPILED",
                Map.of("expression", truncate(expression, 200), "compileTimeMs", timeMs)));
    }

    public void logExpressionExecuted(String expression, long timeMs, boolean success) {
        record("expression_executed", new AuditEvent("EXPRESSION_EXECUTED",
                Map.of("expression", truncate(expression, 200), "executeTimeMs", timeMs,
                        "success", success)));
    }

    // ========== 内部方法 ==========

    private void record(String counterKey, AuditEvent event) {
        AtomicLong counter = counters.get(counterKey);
        if (counter != null) {
            counter.incrementAndGet();
        } else {
            // 如果计数器不存在，记录警告并创建它
            logger.warn("Counter key not found: {}, creating new counter", counterKey);
            counters.putIfAbsent(counterKey, new AtomicLong(1));
        }
        if (config.isEnabled()) {
            eventQueue.offer(event);
        }
    }

    private String truncate(String s) {
        return truncate(s, 100);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...[truncated]";
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        counters.forEach((k, v) -> stats.put(k, v.get()));
        return stats;
    }

    public void shutdown() {
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
        }
    }

    // ========== 审计事件 ==========
    private class AuditEvent {
        final long id = eventId.incrementAndGet();
        final String timestamp = LocalDateTime.now().format(DTF);
        final String type;
        final Map<String, Object> data;
        final String thread = Thread.currentThread().getName();

        AuditEvent(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
        }

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":").append(id).append(",");
            sb.append("\"timestamp\":\"").append(timestamp).append("\",");
            sb.append("\"type\":\"").append(type).append("\",");
            sb.append("\"thread\":\"").append(thread).append("\",");
            sb.append("\"data\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val instanceof Number || val instanceof Boolean) {
                    sb.append(val);
                } else {
                    sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
                }
            }
            sb.append("}}");
            return sb.toString();
        }
    }
}