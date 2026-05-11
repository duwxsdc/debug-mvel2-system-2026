// ============ 配置模型（支持 YAML/JSON 序列化） ============

package com.mvel.sendbox.config;

import java.util.*;

/**
 * MVEL 安全沙箱完整配置
 */
public class MvelSandboxConfig {

    // ========== 编译期配置 ==========
    private CompileConfig compile = new CompileConfig();

    // ========== 运行时配置 ==========
    private RuntimeConfig runtime = new RuntimeConfig();

    // ========== SQL 集成配置 ==========
    private SqlConfig sql = new SqlConfig();

    // ========== 审计配置 ==========
    private AuditConfig audit = new AuditConfig();

    // Getters & Setters
    public CompileConfig getCompile() { return compile; }
    public void setCompile(CompileConfig compile) { this.compile = compile; }
    public RuntimeConfig getRuntime() { return runtime; }
    public void setRuntime(RuntimeConfig runtime) { this.runtime = runtime; }
    public SqlConfig getSql() { return sql; }
    public void setSql(SqlConfig sql) { this.sql = sql; }
    public AuditConfig getAudit() { return audit; }
    public void setAudit(AuditConfig audit) { this.audit = audit; }

    // ========== 嵌套配置类 ==========

    public static class CompileConfig {
        // 类白名单（空表示允许所有，非空则只允许列表内）
        private Set<String> classWhitelist = new HashSet<>();

        // 类黑名单（优先级高于白名单）
        private Set<String> classBlacklist = new HashSet<>(Arrays.asList(
                "java.lang.Runtime",
                "java.lang.ProcessBuilder",
                "java.lang.System",
                "java.lang.Thread",
                "java.lang.ClassLoader",
                "java.lang.reflect.Method",
                "java.lang.reflect.Field",
                "java.lang.reflect.Constructor",
                "java.io.FileInputStream",
                "java.io.FileOutputStream",
                "java.io.RandomAccessFile",
                "java.nio.file.Files",
                "java.net.URL",
                "java.net.Socket",
                "java.net.ServerSocket",
                "sun.misc.Unsafe",
                "java.beans.Introspector"
        ));

        // 方法黑名单（格式: 类名.方法名 或 类名.*）
        private Set<String> methodBlacklist = new HashSet<>(Arrays.asList(
                "java.lang.System.exit",
                "java.lang.System.load",
                "java.lang.System.loadLibrary",
                "java.lang.System.setProperty",
                "java.lang.System.setSecurityManager",
                "java.lang.System.getSecurityManager",
                "java.lang.Runtime.exec",
                "java.lang.Runtime.addShutdownHook",
                "java.lang.Runtime.load",
                "java.lang.Runtime.loadLibrary",
                "java.lang.ProcessBuilder.*",
                "java.lang.Thread.start",
                "java.lang.Thread.stop",
                "java.lang.Thread.suspend",
                "java.lang.Thread.resume",
                "java.lang.Thread.setContextClassLoader",
                "java.lang.Class.forName",
                "java.lang.Class.getDeclaredField",
                "java.lang.Class.getDeclaredMethod",
                "java.lang.Class.getDeclaredConstructor",
                "java.lang.reflect.Method.invoke",
                "java.lang.reflect.Field.set",
                "java.lang.reflect.Field.setAccessible",
                "java.lang.reflect.Constructor.newInstance",
                "java.lang.Object.getClass",
                "java.lang.Object.wait",
                "java.lang.Object.notify",
                "java.lang.Object.notifyAll"
        ));

        // 属性黑名单（禁止访问的属性名）
        private Set<String> propertyBlacklist = new HashSet<>(Arrays.asList(
                "class",
                "classLoader",
                "declaredFields",
                "declaredMethods",
                "declaredConstructors",
                "superclass",
                "interfaces",
                "protectionDomain",
                "signers"
        ));

        // 是否启用严格模式（只允许显式导入的类）
        private boolean strictMode = false;

        // 是否允许裸方法调用（不加对象前缀）
        private boolean allowNakedMethodCall = false;

        // 是否允许 new 关键字
        private boolean allowNew = false;

        // 允许导入的包（仅 strictMode=true 时有效）
        private Set<String> allowedPackages = new HashSet<>(Arrays.asList(
                "java.lang",
                "java.util",
                "java.math",
                "java.time"
        ));

        // Getters & Setters
        public Set<String> getClassWhitelist() { return classWhitelist; }
        public void setClassWhitelist(Set<String> classWhitelist) { this.classWhitelist = classWhitelist; }
        public Set<String> getClassBlacklist() { return classBlacklist; }
        public void setClassBlacklist(Set<String> classBlacklist) { this.classBlacklist = classBlacklist; }
        public Set<String> getMethodBlacklist() { return methodBlacklist; }
        public void setMethodBlacklist(Set<String> methodBlacklist) { this.methodBlacklist = methodBlacklist; }
        public Set<String> getPropertyBlacklist() { return propertyBlacklist; }
        public void setPropertyBlacklist(Set<String> propertyBlacklist) { this.propertyBlacklist = propertyBlacklist; }
        public boolean isStrictMode() { return strictMode; }
        public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }
        public boolean isAllowNakedMethodCall() { return allowNakedMethodCall; }
        public void setAllowNakedMethodCall(boolean allowNakedMethodCall) { this.allowNakedMethodCall = allowNakedMethodCall; }
        public boolean isAllowNew() { return allowNew; }
        public void setAllowNew(boolean allowNew) { this.allowNew = allowNew; }
        public Set<String> getAllowedPackages() { return allowedPackages; }
        public void setAllowedPackages(Set<String> allowedPackages) { this.allowedPackages = allowedPackages; }
    }

    public static class RuntimeConfig {

        private int maxVariableDepth = 5;

        private int maxVariableCount = 50;

        private int maxIterationCount = 1000;

        private boolean readOnly = true;

        private Set<String> variableWhitelist = new HashSet<>();

        private Set<String> variableBlacklist = new HashSet<>(Arrays.asList(
                "System",
                "Runtime",
                "Thread",
                "ClassLoader",
                "ProcessBuilder"
        ));

        private int maxStringLength = 10000;

        private int maxCollectionSize = 1000;

        private long executionTimeoutMs = 5000;

        private long maxMemoryBytes = 10 * 1024 * 1024;

        private boolean logValues = false;

        // Getters & Setters
        public int getMaxVariableDepth() { return maxVariableDepth; }
        public void setMaxVariableDepth(int maxVariableDepth) { this.maxVariableDepth = maxVariableDepth; }
        public int getMaxVariableCount() { return maxVariableCount; }
        public void setMaxVariableCount(int maxVariableCount) { this.maxVariableCount = maxVariableCount; }
        public int getMaxIterationCount() { return maxIterationCount; }
        public void setMaxIterationCount(int maxIterationCount) { this.maxIterationCount = maxIterationCount; }
        public boolean isReadOnly() { return readOnly; }
        public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
        public Set<String> getVariableWhitelist() { return variableWhitelist; }
        public void setVariableWhitelist(Set<String> variableWhitelist) { this.variableWhitelist = variableWhitelist; }
        public Set<String> getVariableBlacklist() { return variableBlacklist; }
        public void setVariableBlacklist(Set<String> variableBlacklist) { this.variableBlacklist = variableBlacklist; }
        public int getMaxStringLength() { return maxStringLength; }
        public void setMaxStringLength(int maxStringLength) { this.maxStringLength = maxStringLength; }
        public int getMaxCollectionSize() { return maxCollectionSize; }
        public void setMaxCollectionSize(int maxCollectionSize) { this.maxCollectionSize = maxCollectionSize; }
        public long getExecutionTimeoutMs() { return executionTimeoutMs; }
        public void setExecutionTimeoutMs(long executionTimeoutMs) { this.executionTimeoutMs = executionTimeoutMs; }
        public long getMaxMemoryBytes() { return maxMemoryBytes; }
        public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }
        public boolean isLogValues() { return logValues; }
        public void setLogValues(boolean logValues) { this.logValues = logValues; }
    }

    public static class SqlConfig {
        // 是否启用 SQL 集成
        private boolean enabled = false;

        // SQL 关键字黑名单
        private Set<String> forbiddenKeywords = new HashSet<>(Arrays.asList(
                "DROP", "DELETE", "TRUNCATE", "ALTER", "GRANT", "REVOKE",
                "EXEC", "EXECUTE", "INTO OUTFILE", "LOAD_FILE", "UNION",
                "INSERT", "UPDATE", "MERGE", "CALL"
        ));

        // 只允许的操作（空表示允许所有）
        private Set<String> allowedOperations = new HashSet<>(Collections.singletonList("SELECT"));

        // 最大返回行数
        private int maxRows = 1000;

        // 查询超时（秒）
        private int queryTimeoutSeconds = 10;

        // 允许的表（空表示允许所有）
        private Set<String> allowedTables = new HashSet<>();

        // 表黑名单
        private Set<String> forbiddenTables = new HashSet<>();

        // 允许的字段前缀（用于字段级控制）
        private Map<String, Set<String>> allowedFields = new HashMap<>();

        // 是否要求参数化查询
        private boolean requireParameterized = true;

        // Getters & Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Set<String> getForbiddenKeywords() { return forbiddenKeywords; }
        public void setForbiddenKeywords(Set<String> forbiddenKeywords) { this.forbiddenKeywords = forbiddenKeywords; }
        public Set<String> getAllowedOperations() { return allowedOperations; }
        public void setAllowedOperations(Set<String> allowedOperations) { this.allowedOperations = allowedOperations; }
        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
        public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
        public Set<String> getAllowedTables() { return allowedTables; }
        public void setAllowedTables(Set<String> allowedTables) { this.allowedTables = allowedTables; }
        public Set<String> getForbiddenTables() { return forbiddenTables; }
        public void setForbiddenTables(Set<String> forbiddenTables) { this.forbiddenTables = forbiddenTables; }
        public Map<String, Set<String>> getAllowedFields() { return allowedFields; }
        public void setAllowedFields(Map<String, Set<String>> allowedFields) { this.allowedFields = allowedFields; }
        public boolean isRequireParameterized() { return requireParameterized; }
        public void setRequireParameterized(boolean requireParameterized) { this.requireParameterized = requireParameterized; }
    }

    public static class AuditConfig {
        // 是否启用审计
        private boolean enabled = true;

        // 审计日志存储路径
        private String logPath = "/var/log/mvel-audit";

        // 是否记录变量值（可能敏感）
        private boolean logValues = false;

        // 最大日志保留天数
        private int retentionDays = 30;

        // 是否输出到控制台
        private boolean consoleOutput = true;

        // 异步写入缓冲区大小
        private int bufferSize = 1000;

        // Getters & Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getLogPath() { return logPath; }
        public void setLogPath(String logPath) { this.logPath = logPath; }
        public boolean isLogValues() { return logValues; }
        public void setLogValues(boolean logValues) { this.logValues = logValues; }
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public boolean isConsoleOutput() { return consoleOutput; }
        public void setConsoleOutput(boolean consoleOutput) { this.consoleOutput = consoleOutput; }
        public int getBufferSize() { return bufferSize; }
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
    }
}