package com.etl.engine.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class SqlExecutionEngine {

    private static final Set<String> DANGEROUS_SQL_COMMANDS = Set.of(
        "UPDATE", "DELETE", "INSERT", "DROP", "ALTER", "TRUNCATE",
        "CREATE", "REPLACE", "MERGE", "CALL", "EXEC"
    );

    private static final Pattern SQL_INJECTION_PATTERNS = Pattern.compile(
        "('\\s*OR\\s*\\d+\\s*=\\s*\\d+)|" +
        "('\\s*AND\\s*\\d+\\s*=\\s*\\d+)|" +
        "(UNION\\s+SELECT)|" +
        "(DROP\\s+TABLE)|" +
        "(--.*)|" +
        "(;.*)"
    );

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> executeQuery(String sql) {
        validateSql(sql);

        String trimmedSql = sql.trim().toUpperCase();
        
        if (trimmedSql.startsWith("SELECT") || trimmedSql.startsWith("SHOW") || 
            trimmedSql.startsWith("DESCRIBE") || trimmedSql.startsWith("EXPLAIN")) {
            return jdbcTemplate.query(sql, new MapRowMapper());
        } else {
            throw new SqlSecurityException("不支持的SQL语句类型，仅允许只读查询");
        }
    }

    private void validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SqlSecurityException("SQL语句不能为空");
        }

        String upperSql = sql.toUpperCase().trim();

        for (String command : DANGEROUS_SQL_COMMANDS) {
            if (upperSql.startsWith(command)) {
                throw new SqlSecurityException("禁止执行危险SQL命令: " + command);
            }
        }

        if (SQL_INJECTION_PATTERNS.matcher(upperSql).find()) {
            throw new SqlSecurityException("检测到潜在的SQL注入攻击");
        }
    }

    public static class MapRowMapper implements RowMapper<Map<String, Object>> {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> row = new LinkedHashMap<>();
            var metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            
            return row;
        }
    }

    public static class SqlSecurityException extends RuntimeException {
        public SqlSecurityException(String message) {
            super(message);
        }
    }
}
