package com.etl.engine;

import com.etl.engine.engine.SqlExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlExecutionEngineTest {

    private SqlExecutionEngine engine;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbcTemplate = new JdbcTemplate(dataSource);
        engine = new SqlExecutionEngine(jdbcTemplate);

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
        jdbcTemplate.execute("INSERT INTO users VALUES (1, 'Alice', 25)");
        jdbcTemplate.execute("INSERT INTO users VALUES (2, 'Bob', 30)");
    }

    @Test
    @DisplayName("测试SELECT查询")
    void testSelectQuery() {
        List<Map<String, Object>> result = engine.executeQuery("SELECT * FROM users");
        
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("测试带条件的SELECT查询")
    void testSelectWithCondition() {
        List<Map<String, Object>> result = engine.executeQuery("SELECT * FROM users WHERE age > 25");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).get("NAME"));
    }

    @Test
    @DisplayName("测试SHOW命令")
    void testShowCommand() {
        assertDoesNotThrow(() -> {
            engine.executeQuery("SHOW TABLES");
        });
    }

    @Test
    @DisplayName("测试UPDATE被拦截")
    void testUpdateBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("UPDATE users SET age = 35 WHERE id = 1");
        });
    }

    @Test
    @DisplayName("测试DELETE被拦截")
    void testDeleteBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("DELETE FROM users WHERE id = 1");
        });
    }

    @Test
    @DisplayName("测试INSERT被拦截")
    void testInsertBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("INSERT INTO users VALUES (3, 'Charlie', 35)");
        });
    }

    @Test
    @DisplayName("测试DROP被拦截")
    void testDropBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("DROP TABLE users");
        });
    }

    @Test
    @DisplayName("测试TRUNCATE被拦截")
    void testTruncateBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("TRUNCATE TABLE users");
        });
    }

    @Test
    @DisplayName("测试SQL注入攻击被拦截")
    void testSqlInjectionBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("SELECT * FROM users WHERE id = 1' OR '1'='1");
        });
    }

    @Test
    @DisplayName("测试注释注入被拦截")
    void testCommentInjectionBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("SELECT * FROM users -- malicious comment");
        });
    }

    @Test
    @DisplayName("测试空SQL被拦截")
    void testEmptySqlBlocked() {
        assertThrows(SqlExecutionEngine.SqlSecurityException.class, () -> {
            engine.executeQuery("");
        });
    }
}
