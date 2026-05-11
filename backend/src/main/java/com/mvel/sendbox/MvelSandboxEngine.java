// ============ 主入口：安全沙箱引擎 ============

package com.mvel.sendbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mvel.sendbox.config.MvelSandboxConfig;
import com.mvel.sendbox.security.AuditLogger;
import com.mvel.sendbox.security.SecureClassResolver;
import com.mvel.sendbox.security.SecureMethodResolver;
import com.mvel.sendbox.security.SecurePropertyHandler;
import com.mvel.sendbox.sql.SecureSqlExecutor;
import com.mvel.sendbox.runtime.SecureVariableResolverFactory;
import org.mvel2.CompileException;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;
import org.mvel2.compiler.ExpressionCompiler;

import javax.sql.DataSource;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * MVEL2 安全沙箱引擎（完全可配置）
 */
public class MvelSandboxEngine implements AutoCloseable {

    private final MvelSandboxConfig config;
    private final AuditLogger auditLogger;
    private final SecureClassResolver classResolver;
    private final SecureMethodResolver methodResolver;
    private final SecurePropertyHandler propertyHandler;
    private SecureSqlExecutor sqlExecutor;

    // 表达式缓存（编译后缓存）
    private final ConcurrentHashMap<String, Object> expressionCache =
            new ConcurrentHashMap<>();

    // 执行线程池（隔离）
    private final ExecutorService executor;

    public MvelSandboxEngine(MvelSandboxConfig config) {
        this.config = config;
        this.auditLogger = new AuditLogger(config.getAudit());
        this.classResolver = new SecureClassResolver(config.getCompile(), auditLogger);
        this.methodResolver = new SecureMethodResolver(config.getCompile(), auditLogger);
        this.propertyHandler = new SecurePropertyHandler(config.getCompile(), auditLogger);

        // 创建隔离线程池
        this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "mvel-sandbox-" + counter.incrementAndGet());
                        t.setUncaughtExceptionHandler((th, ex) -> {
                            System.err.println("MVEL thread error: " + ex.getMessage());
                        });
                        return t;
                    }
                });
    }

    /**
     * 从配置文件创建引擎
     */
    public static MvelSandboxEngine fromYaml(String yamlPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        MvelSandboxConfig config = mapper.readValue(new File(yamlPath), MvelSandboxConfig.class);
        return new MvelSandboxEngine(config);
    }

    public static MvelSandboxEngine fromJson(String jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        MvelSandboxConfig config = mapper.readValue(new File(jsonPath), MvelSandboxConfig.class);
        return new MvelSandboxEngine(config);
    }

    /**
     * 设置数据源（启用 SQL 功能）
     */
    public void setDataSource(DataSource dataSource) {
        if (!config.getSql().isEnabled()) {
            throw new IllegalStateException("SQL not enabled in config");
        }
        this.sqlExecutor = new SecureSqlExecutor(
                dataSource, config.getSql(), config.getRuntime(), auditLogger);
    }

    // ========== 表达式编译 ==========

    /**
     * 编译表达式（安全沙箱环境）
     */
    public Object compile(String expression) throws CompileException {
        // 检查缓存
        Object cached = expressionCache.get(expression);
        if (cached != null) return cached;

        long start = System.currentTimeMillis();

        try {
            // 在编译前进行安全检查
            validateExpression(expression);
            
            // 使用 ExpressionCompiler 进行编译
            ExpressionCompiler compiler = new ExpressionCompiler(expression);
            CompiledExpression compiled = compiler.compile();

            // 缓存
            if (expressionCache.size() < 10000) { // 防止内存泄漏
                expressionCache.put(expression, compiled);
            }

            long elapsed = System.currentTimeMillis() - start;
            auditLogger.logExpressionCompiled(expression, elapsed);

            return compiled;

        } catch (Exception e) {
            auditLogger.logExpressionCompiled(expression, -1);
            if (e instanceof CompileException) {
                throw (CompileException) e;
            }
            throw new CompileException("Compilation failed: " + e.getMessage(), 
                    expression.toCharArray(), 0, e);
        }
    }
    
    /**
     * 验证表达式安全性
     */
    private void validateExpression(String expression) {
        // 检查黑名单类 - 使用单词边界匹配
        for (String blackClass : config.getCompile().getClassBlacklist()) {
            String simpleName = blackClass.replace(".*", "");
            // 使用单词边界匹配，避免部分匹配
            String regex = "\\b" + Pattern.quote(simpleName) + "\\b";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(expression).find()) {
                auditLogger.logClassDenied(simpleName, "blacklist");
                throw new SecurityException("Expression contains blacklisted class: " + simpleName);
            }
        }
        
        // 检查黑名单方法 - 使用单词边界匹配
        for (String blackMethod : config.getCompile().getMethodBlacklist()) {
            String methodName = blackMethod.substring(blackMethod.lastIndexOf('.') + 1).replace("*", "");
            if (methodName.isEmpty()) continue;
            
            // 使用单词边界匹配
            String regex = "\\b" + Pattern.quote(methodName) + "\\b";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(expression).find()) {
                auditLogger.logMethodDenied(blackMethod);
                throw new SecurityException("Expression contains blacklisted method: " + blackMethod);
            }
        }
    }

    // ========== 表达式执行 ==========

    /**
     * 执行表达式（完整安全控制）
     */
    public Object execute(String expression, Map<String, Object> variables)
            throws Exception {
        return execute(expression, variables, config.getRuntime().getExecutionTimeoutMs());
    }

    /**
     * 执行表达式（指定超时）
     */
    public Object execute(String expression, Map<String, Object> variables,
                          long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        boolean success = false;

        try {
            // 编译
            Object compiled = compile(expression);

            // 创建安全变量工厂
            SecureVariableResolverFactory factory = new SecureVariableResolverFactory(
                    variables, config.getRuntime(), auditLogger);

            // 带超时的执行
            Future<Object> future = executor.submit(() ->
                    MVEL.executeExpression(compiled, factory));

            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            success = true;
            return result;

        } catch (TimeoutException e) {
            throw new SecurityException("Expression execution timeout: " + timeoutMs + "ms");
        } catch (Exception e) {
            throw new SecurityException("Execution failed: " + e.getMessage(), e);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            auditLogger.logExpressionExecuted(expression, elapsed, success);
        }
    }

    /**
     * 执行表达式（简化版，无超时控制）
     */
    public Object executeSimple(String expression, Map<String, Object> variables) {
        Object compiled = compile(expression);
        SecureVariableResolverFactory factory = new SecureVariableResolverFactory(
                variables, config.getRuntime(), auditLogger);
        return MVEL.executeExpression(compiled, factory);
    }

    // ========== SQL 执行 ==========

    /**
     * 执行安全 SQL 查询
     */
    public SecureSqlExecutor.SqlResult executeSql(String baseSql,
                                                  String mvelCondition,
                                                  Map<String, Object> context)
            throws Exception {
        if (sqlExecutor == null) {
            throw new IllegalStateException("DataSource not configured");
        }
        return sqlExecutor.executeQuery(baseSql, mvelCondition, context);
    }

    // ========== 管理接口 ==========

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public Map<String, Long> getStatistics() {
        return auditLogger.getStatistics();
    }

    public void clearCache() {
        expressionCache.clear();
    }

    @Override
    public void close() {
        executor.shutdown();
        auditLogger.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}