// ============ 安全变量解析工厂（完整可配置版） ============

package com.mvel.sendbox.runtime;

import com.mvel.sendbox.config.MvelSandboxConfig;
import com.mvel.sendbox.security.AuditLogger;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级安全变量解析工厂
 */
public class SecureVariableResolverFactory implements VariableResolverFactory {

    private final MvelSandboxConfig.RuntimeConfig config;
    private final AuditLogger auditLogger;

    // 底层委托
    private final VariableResolverFactory delegate;

    // 线程安全的深度追踪
    private final ThreadLocal<DepthTracker> depthTracker = ThreadLocal.withInitial(DepthTracker::new);

    // 变量计数器
    private final AtomicInteger variableCount = new AtomicInteger(0);

    // 已创建的变量名（用于重复检测）
    private final Set<String> createdVariables = ConcurrentHashMap.newKeySet();

    // 父工厂引用
    private VariableResolverFactory parentFactory;

    // 内存追踪（估算）
    private final ThreadLocal<Long> memoryUsed = ThreadLocal.withInitial(() -> 0L);

    private boolean tiltFlag = false;

    public SecureVariableResolverFactory(Map<String, Object> variables,
                                         MvelSandboxConfig.RuntimeConfig config,
                                         AuditLogger auditLogger) {
        this.config = config;
        this.auditLogger = auditLogger;
        this.delegate = new MapVariableResolverFactory(variables);

        // 初始化变量计数
        variableCount.set(variables.size());
    }

    // 私有构造器用于子工厂
    private SecureVariableResolverFactory(VariableResolverFactory delegate,
                                          MvelSandboxConfig.RuntimeConfig config,
                                          AuditLogger auditLogger,
                                          VariableResolverFactory parent) {
        this.config = config;
        this.auditLogger = auditLogger;
        this.delegate = delegate;
        this.parentFactory = parent;
    }

    // ========== 核心：变量解析 ==========
    @Override
    public VariableResolver getVariableResolver(String name) {
        DepthTracker tracker = depthTracker.get();

        // 1. 深度检查
        int currentDepth = tracker.increment();
        try {
            if (currentDepth > config.getMaxVariableDepth()) {
                auditLogger.logDepthExceeded(name, currentDepth, config.getMaxVariableDepth());
                throw new SecurityException(String.format(
                        "Variable resolution depth exceeded: %d > %d (variable: %s)",
                        currentDepth, config.getMaxVariableDepth(), name));
            }

            // 2. 变量名安全检查
            validateVariableName(name);

            // 3. 黑名单检查
            if (config.getVariableBlacklist().contains(name)) {
                auditLogger.logVariableDenied(name, "blacklist");
                throw new SecurityException("Variable access denied (blacklist): " + name);
            }

            // 4. 白名单检查
            if (!config.getVariableWhitelist().isEmpty() &&
                    !config.getVariableWhitelist().contains(name)) {
                auditLogger.logVariableDenied(name, "whitelist");
                throw new SecurityException("Variable not in whitelist: " + name);
            }

            // 5. 获取解析器
            VariableResolver resolver = delegate.getVariableResolver(name);

            if (resolver == null) {
                return null;
            }

            // 6. 包装为安全解析器
            SecureVariableResolver secureResolver = new SecureVariableResolver(
                    name, resolver, config, tracker, auditLogger);

            // 7. 审计
            auditLogger.logVariableRead(name, currentDepth,
                    config.isLogValues() ? resolver.getValue() : null);

            return secureResolver;

        } finally {
            tracker.decrement();
        }
    }

    // ========== 变量创建 ==========
    @Override
    public VariableResolver createVariable(String name, Object value) {
        // 1. 只读检查
        if (config.isReadOnly()) {
            throw new SecurityException("Context is read-only, cannot create: " + name);
        }

        // 2. 变量名验证
        validateVariableName(name);

        // 3. 数量限制
        if (variableCount.incrementAndGet() > config.getMaxVariableCount()) {
            variableCount.decrementAndGet();
            throw new SecurityException("Variable count exceeded: " + config.getMaxVariableCount());
        }

        // 4. 黑名单
        if (config.getVariableBlacklist().contains(name)) {
            variableCount.decrementAndGet();
            throw new SecurityException("Variable creation denied: " + name);
        }

        // 5. 值大小检查
        validateValueSize(value);

        // 6. 创建
        createdVariables.add(name);
        VariableResolver resolver = delegate.createVariable(name, value);

        auditLogger.logVariableCreated(name, config.isLogValues() ? value : null);

        return new SecureVariableResolver(name, resolver, config,
                depthTracker.get(), auditLogger);
    }

    @Override
    public VariableResolver createVariable(String name, Object value, Class<?> type) {
        // 类型安全检查
        if (type != null && isDangerousType(type)) {
            throw new SecurityException("Dangerous type not allowed: " + type.getName());
        }
        return createVariable(name, value);
    }

    // ========== 子工厂创建（深度继承）==========
    @Override
    public VariableResolverFactory getNextFactory() {
        return delegate.getNextFactory();
    }

    @Override
    public VariableResolverFactory setNextFactory(VariableResolverFactory factory) {
        return delegate.setNextFactory(factory);
    }

    @Override
    public void setTiltFlag(boolean flag) {
        this.tiltFlag = flag;
        delegate.setTiltFlag(flag);
    }

    @Override
    public boolean tiltFlag() {
        return tiltFlag;
    }

    @Override
    public Set<String> getKnownVariables() {
        return delegate.getKnownVariables();
    }

    @Override
    public int variableIndexOf(String name) {
        return delegate.variableIndexOf(name);
    }

    @Override
    public boolean isIndexedFactory() {
        return delegate.isIndexedFactory();
    }

    @Override
    public VariableResolver getIndexedVariableResolver(int index) {
        return delegate.getIndexedVariableResolver(index);
    }

    @Override
    public VariableResolver createIndexedVariable(int index, String name, Object value) {
        validateVariableName(name);
        if (config.getVariableBlacklist().contains(name)) {
            throw new SecurityException("Variable creation denied: " + name);
        }
        return delegate.createIndexedVariable(index, name, value);
    }

    @Override
    public VariableResolver createIndexedVariable(int index, String name, Object value, Class<?> type) {
        if (type != null && isDangerousType(type)) {
            throw new SecurityException("Dangerous type not allowed: " + type.getName());
        }
        return createIndexedVariable(index, name, value);
    }

    @Override
    public VariableResolver setIndexedVariableResolver(int index, VariableResolver resolver) {
        return delegate.setIndexedVariableResolver(index, resolver);
    }

    // ========== 其他委托方法 ==========
    @Override
    public boolean isResolveable(String name) {
        if (config.getVariableBlacklist().contains(name)) return false;
        if (!config.getVariableWhitelist().isEmpty() &&
                !config.getVariableWhitelist().contains(name)) return false;
        return delegate.isResolveable(name);
    }

    @Override
    public boolean isTarget(String name) {
        return delegate.isTarget(name);
    }

    // ========== 内部工具方法 ==========

    private void validateVariableName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Variable name cannot be empty");
        }
        if (name.length() > 100) {
            throw new SecurityException("Variable name too long: " + name.length());
        }
        // 只允许字母数字下划线
        if (!name.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
            throw new SecurityException("Invalid variable name: " + name);
        }
    }

    private void validateValueSize(Object value) {
        if (value instanceof String) {
            String s = (String) value;
            if (s.length() > config.getMaxStringLength()) {
                throw new SecurityException("String value too long: " + s.length());
            }
            memoryUsed.set(memoryUsed.get() + s.length() * 2L);
        } else if (value instanceof Collection) {
            Collection<?> c = (Collection<?>) value;
            if (c.size() > config.getMaxCollectionSize()) {
                throw new SecurityException("Collection too large: " + c.size());
            }
            memoryUsed.set(memoryUsed.get() + c.size() * 100L); // 估算
        } else if (value != null && value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            if (len > config.getMaxCollectionSize()) {
                throw new SecurityException("Array too large: " + len);
            }
        }

        // 内存限制检查
        if (memoryUsed.get() > config.getMaxMemoryBytes()) {
            throw new SecurityException("Memory limit exceeded");
        }
    }

    private boolean isDangerousType(Class<?> type) {
        String name = type.getName();
        return name.contains("reflect") ||
                name.contains("ClassLoader") ||
                name.contains("Runtime") ||
                name.contains("Process");
    }

    // ========== 深度追踪器 ==========
    private static class DepthTracker {
        private int depth = 0;

        int increment() { return ++depth; }
        void decrement() { depth--; }
        int get() { return depth; }
    }

    // ========== 安全变量解析器包装 ==========
    private static class SecureVariableResolver implements VariableResolver {

        private final String name;
        private final VariableResolver delegate;
        private final MvelSandboxConfig.RuntimeConfig config;
        private final DepthTracker tracker;
        private final AuditLogger auditLogger;

        SecureVariableResolver(String name, VariableResolver delegate,
                               MvelSandboxConfig.RuntimeConfig config,
                               DepthTracker tracker,
                               AuditLogger auditLogger) {
            this.name = name;
            this.delegate = delegate;
            this.config = config;
            this.tracker = tracker;
            this.auditLogger = auditLogger;
        }

        @Override
        public String getName() { return name; }

        @Override
        public Class<?> getType() {
            return delegate != null ? delegate.getType() : Object.class;
        }

        @Override
        public void setStaticType(Class type) {
            if (delegate != null) delegate.setStaticType(type);
        }

        @Override
        public int getFlags() {
            return delegate != null ? delegate.getFlags() : 0;
        }

        @Override
        public Object getValue() {
            if (delegate == null) return null;
            return delegate.getValue();
        }

        @Override
        public void setValue(Object value) {
            if (config.isReadOnly()) {
                throw new SecurityException("Variable is read-only: " + name);
            }
            if (delegate == null) {
                throw new IllegalStateException("Cannot set null resolver");
            }

            // 值验证
            if (value instanceof String && ((String) value).length() > config.getMaxStringLength()) {
                throw new SecurityException("Value too long");
            }

            delegate.setValue(value);
            auditLogger.logVariableWrite(name, config.isLogValues() ? value : null);
        }
    }
}