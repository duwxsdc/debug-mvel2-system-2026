// ============ 安全属性处理器 ============

package com.mvel.sendbox.security;

import com.mvel.sendbox.config.MvelSandboxConfig;
import org.mvel2.integration.PropertyHandler;
import org.mvel2.integration.VariableResolverFactory;

import java.util.*;

/**
 * 可配置的安全属性处理器
 */
public class SecurePropertyHandler implements PropertyHandler {

    private final MvelSandboxConfig.CompileConfig config;
    private final AuditLogger auditLogger;

    public SecurePropertyHandler(MvelSandboxConfig.CompileConfig config, AuditLogger auditLogger) {
        this.config = config;
        this.auditLogger = auditLogger;
    }

    @Override
    public Object getProperty(String name, Object contextObj, VariableResolverFactory vars) {
        // 1. 属性黑名单检查
        if (config.getPropertyBlacklist().contains(name)) {
            auditLogger.logPropertyDenied(name, contextObj.getClass().getName());
            throw new SecurityException("Property access denied: " + name);
        }

        // 2. 反射相关属性拦截
        if (name.contains("Class") || name.contains("classLoader") ||
                name.contains("declared") || name.contains("reflect")) {
            auditLogger.logPropertyDenied(name, contextObj.getClass().getName());
            throw new SecurityException("Reflection property access denied: " + name);
        }

        // 3. 返回 null 让默认处理器继续
        auditLogger.logPropertyAccess(name, contextObj.getClass().getName());
        return null;
    }

    @Override
    public Object setProperty(String name, Object contextObj, VariableResolverFactory vars, Object value) {
        // 默认不允许设置属性
        throw new SecurityException("Property modification not allowed: " + name);
    }
}