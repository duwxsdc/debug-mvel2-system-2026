package com.mvel.sendbox.demo;

import com.mvel.sendbox.MvelSandboxEngine;
import com.mvel.sendbox.sql.SecureSqlExecutor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.*;

public class SandboxDemo {

    public static void main(String[] args) throws Exception {

        // ========== 1. 从配置文件创建引擎 ==========
        MvelSandboxEngine engine = MvelSandboxEngine.fromYaml("E:\\java\\AI\\20260427\\debug-system\\backend\\src\\main\\resources\\mvel-sandbox.yml");

        // ========== 2. 配置数据源（启用 SQL）==========
//        HikariConfig hikari = new HikariConfig();
//        hikari.setJdbcUrl("jdbc:mysql://localhost:3306/test");
//        hikari.setUsername("app_user");
//        hikari.setPassword("app_pass");
//        hikari.setMaximumPoolSize(5);
//        DataSource ds = new HikariDataSource(hikari);
//        engine.setDataSource(ds);

        // ========== 3. 安全执行 MVEL 表达式 ==========
        Map<String, Object> context = new HashMap<>();
        context.put("price", 100);
        context.put("quantity", 5);
        context.put("discount", 0.9);

        // 安全的数学计算
        String expr = "price * quantity * discount";
        Object result = engine.execute(expr, context);
        System.out.println("计算结果: " + result); // 450.0

        // ========== 4. 尝试危险操作（会被拦截）==========
        try {
            String dangerous = "System.exit(0)";
            engine.execute(dangerous, context);
        } catch (Exception e) {
            System.out.println("危险操作被拦截: " + e.getMessage());
        }

        // ========== 5. 深度限制测试 ==========
        try {
            Map<String, Object> nested = new HashMap<>();
            nested.put("a", Map.of("b", Map.of("c", Map.of("d", Map.of("e", "deep")))));
            context.put("nested", nested);

            String deepExpr = "nested.a.b.c.d.e.f.g"; // 深度超过 5
            engine.execute(deepExpr, context);
        } catch (Exception e) {
            System.out.println("深度超限被拦截: " + e.getMessage());
        }

        // ========== 6. 安全 SQL 执行 ==========
//        String baseSql = "SELECT id, name, age FROM users WHERE status = :status AND age >= :min_age";
//        String mvelParams = "['status': 'ACTIVE', 'min_age': 18]";
//
//        SecureSqlExecutor.SqlResult sqlResult = engine.executeSql(baseSql, mvelParams, context);
//        System.out.println("SQL 返回 " + sqlResult.getRowCount() + " 行，耗时 " +
//                sqlResult.getElapsedMs() + "ms");

        // ========== 7. 查看审计统计 ==========
        System.out.println("\n=== 审计统计 ===");
        engine.getStatistics().forEach((k, v) ->
                System.out.println(k + ": " + v));

        // ========== 8. 清理 ==========
        engine.close();
    }
}