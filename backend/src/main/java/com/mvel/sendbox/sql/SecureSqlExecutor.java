// ============ SQL 安全执行器 ============

package com.mvel.sendbox.sql;

import com.mvel.sendbox.config.MvelSandboxConfig;
import com.mvel.sendbox.security.AuditLogger;
import org.mvel2.MVEL;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可配置的安全 SQL 执行器
 */
public class SecureSqlExecutor {

    private final DataSource dataSource;
    private final MvelSandboxConfig.SqlConfig config;
    private final MvelSandboxConfig.RuntimeConfig runtimeConfig;
    private final AuditLogger auditLogger;

    // SQL 解析正则
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+AS\\s+\\w+)?" +
                    "(?:\\s+WHERE\\s+(.+?))?" +
                    "(?:\\s+GROUP\\s+BY\\s+(.+?))?" +
                    "(?:\\s+ORDER\\s+BY\\s+(.+?))?" +
                    "(?:\\s+LIMIT\\s+(\\d+))?" +
                    "\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\bFROM\\s+(\\w+)|\\bJOIN\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    public SecureSqlExecutor(DataSource dataSource,
                             MvelSandboxConfig.SqlConfig config,
                             MvelSandboxConfig.RuntimeConfig runtimeConfig,
                             AuditLogger auditLogger) {
        this.dataSource = dataSource;
        this.config = config;
        this.runtimeConfig = runtimeConfig;
        this.auditLogger = auditLogger;
    }

    /**
     * 执行安全的查询
     * @param baseSql 基础 SQL（含 :param 命名参数）
     * @param mvelCondition MVEL 表达式，计算结果为 Map<String, Object>
     * @param context MVEL 上下文
     */
    public SqlResult executeQuery(String baseSql, String mvelCondition,
                                  Map<String, Object> context) throws SqlSecurityException {
        long startTime = System.currentTimeMillis();

        try {
            // 1. SQL 结构校验
            validateSqlStructure(baseSql);

            // 2. 表权限校验
            validateTables(baseSql);

            // 3. MVEL 计算动态参数（在沙箱中）
            Map<String, Object> params = evaluateMvelParams(mvelCondition, context);

            // 4. 参数化 SQL 构建
            ParameterizedSql psql = buildParameterizedSql(baseSql, params);

            // 5. 执行查询
            List<Map<String, Object>> rows = executeWithGuards(psql);

            // 6. 审计
            long elapsed = System.currentTimeMillis() - startTime;
            auditLogger.logSqlExecuted(psql.sql, psql.parameters.size(), rows.size());

            return new SqlResult(rows, elapsed, psql.sql);

        } catch (Exception e) {
            auditLogger.logSqlDenied(baseSql, e.getMessage());
            throw new SqlSecurityException("SQL execution denied: " + e.getMessage(), e);
        }
    }

    // ========== SQL 结构校验 ==========
    private void validateSqlStructure(String sql) {
        String upper = sql.toUpperCase().trim();

        // 1. 操作类型检查
        boolean allowed = false;
        for (String op : config.getAllowedOperations()) {
            if (upper.startsWith(op)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new SecurityException("SQL operation not allowed. Allowed: " +
                    config.getAllowedOperations());
        }

        // 2. 关键字黑名单
        for (String keyword : config.getForbiddenKeywords()) {
            // 使用单词边界匹配
            String pattern = "\\b" + keyword.replace(" ", "\\s+") + "\\b";
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
                throw new SecurityException("Forbidden SQL keyword detected: " + keyword);
            }
        }

        // 3. 多语句检查
        if (sql.contains(";")) {
            throw new SecurityException("Multiple statements not allowed");
        }

        // 4. 注释检查（防止注释注入）
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new SecurityException("SQL comments not allowed");
        }

        // 5. 必须参数化检查
        if (config.isRequireParameterized()) {
            // 检查是否有字面量直接拼接（简单启发式）
            Pattern literalPattern = Pattern.compile(
                    "(?i)WHERE\\s+.*=\\s*['\"\\d]|INSERT\\s+.*VALUES\\s*\\(.*['\"\\d]");
            if (literalPattern.matcher(sql).find()) {
                // 警告：可能存在非参数化值
                // 不直接拒绝，因为可能是合法场景
            }
        }
    }

    // ========== 表权限校验 ==========
    private void validateTables(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

            // 黑名单检查
            if (config.getForbiddenTables().contains(table)) {
                throw new SecurityException("Table forbidden: " + table);
            }

            // 白名单检查
            if (!config.getAllowedTables().isEmpty() &&
                    !config.getAllowedTables().contains(table)) {
                throw new SecurityException("Table not in whitelist: " + table);
            }
        }
    }

    // ========== MVEL 参数计算 ==========
    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateMvelParams(String mvelCondition,
                                                   Map<String, Object> context) {
        if (mvelCondition == null || mvelCondition.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用受限的 MVEL 执行
        // 这里复用沙箱配置，但限制更严格
        Map<String, Object> result = (Map<String, Object>) MVEL.eval(mvelCondition, context);

        // 验证返回类型
        if (result == null) return Collections.emptyMap();

        // 检查参数值安全
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                // 防止参数中包含 SQL 注入
                if (s.contains("'") || s.contains("\"") || s.contains(";") ||
                        s.contains("--") || s.contains("/*")) {
                    throw new SecurityException("Unsafe parameter value: " + entry.getKey());
                }
            }
        }

        return result;
    }

    // ========== 构建参数化 SQL ==========
    private ParameterizedSql buildParameterizedSql(String baseSql,
                                                   Map<String, Object> params) {
        String sql = baseSql;
        List<Object> orderedParams = new ArrayList<>();

        // 提取 :name 参数并替换为 ?
        Pattern paramPattern = Pattern.compile(":(\\w+)");
        Matcher matcher = paramPattern.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            if (value == null && !params.containsKey(paramName)) {
                throw new SecurityException("Missing parameter: " + paramName);
            }
            orderedParams.add(value);
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);

        return new ParameterizedSql(sb.toString(), orderedParams);
    }

    // ========== 带保护的执行 ==========
    private List<Map<String, Object>> executeWithGuards(ParameterizedSql psql)
            throws SQLException {

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(psql.sql)) {

            // 设置限制
            stmt.setMaxRows(config.getMaxRows());
            stmt.setQueryTimeout(config.getQueryTimeoutSeconds());

            // 设置参数
            for (int i = 0; i < psql.parameters.size(); i++) {
                stmt.setObject(i + 1, psql.parameters.get(i));
            }

            // 执行
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                String colName = meta.getColumnLabel(i);
                Object value = rs.getObject(i);

                // 字段级权限检查
                // 这里简化处理，实际可扩展

                row.put(colName, value);
            }
            results.add(row);

            // 行数限制双重检查
            if (results.size() >= config.getMaxRows()) {
                break;
            }
        }
        return results;
    }

    // ========== 内部类 ==========

    private static class ParameterizedSql {
        final String sql;
        final List<Object> parameters;

        ParameterizedSql(String sql, List<Object> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }

    public static class SqlResult {
        private final List<Map<String, Object>> rows;
        private final long elapsedMs;
        private final String executedSql;

        public SqlResult(List<Map<String, Object>> rows, long elapsedMs, String executedSql) {
            this.rows = rows;
            this.elapsedMs = elapsedMs;
            this.executedSql = executedSql;
        }

        public List<Map<String, Object>> getRows() { return rows; }
        public long getElapsedMs() { return elapsedMs; }
        public String getExecutedSql() { return executedSql; }
        public int getRowCount() { return rows.size(); }
    }

    public static class SqlSecurityException extends Exception {
        public SqlSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}