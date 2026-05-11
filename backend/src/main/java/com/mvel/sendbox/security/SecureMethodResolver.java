// ============ 安全方法解析器 ============

package com.mvel.sendbox.security;

import com.mvel.sendbox.config.MvelSandboxConfig;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 可配置的安全方法解析器
 * 注意：MVEL2 2.5.0.Final 中没有 MethodResolver 接口
 * 这个类作为工具类使用，在编译和运行时进行方法调用检查
 */
public class SecureMethodResolver {

    private final MvelSandboxConfig.CompileConfig config;
    private final AuditLogger auditLogger;

    // 编译模式缓存
    private final Map<String, Boolean> methodCache = new ConcurrentHashMap<>();
    private final List<Pattern> blackPatterns = new ArrayList<>();

    public SecureMethodResolver(MvelSandboxConfig.CompileConfig config, AuditLogger auditLogger) {
        this.config = config;
        this.auditLogger = auditLogger;

        // 预编译黑名单模式
        for (String black : config.getMethodBlacklist()) {
            String regex = black.replace(".", "\\.")
                    .replace("*", ".*");
            blackPatterns.add(Pattern.compile("^" + regex + "$"));
        }
    }

    public Method resolveMethod(Class<?> cls, String methodName, Class<?>[] args) {
        String fullName = cls.getName() + "." + methodName;

        // 1. 缓存检查
        Boolean cached = methodCache.get(fullName);
        if (Boolean.FALSE.equals(cached)) {
            throw new SecurityException("Method denied (cached): " + fullName);
        }

        // 2. 黑名单检查
        for (Pattern pattern : blackPatterns) {
            if (pattern.matcher(fullName).matches()) {
                methodCache.put(fullName, false);
                auditLogger.logMethodDenied(fullName);
                throw new SecurityException("Method blacklisted: " + fullName);
            }
        }

        // 3. 类级安全检查
        if (!config.getClassBlacklist().isEmpty()) {
            for (String black : config.getClassBlacklist()) {
                if (cls.getName().startsWith(black.replace(".*", ""))) {
                    throw new SecurityException("Class of method is blacklisted: " + cls.getName());
                }
            }
        }

        // 4. 解析方法
        try {
            Method method = cls.getMethod(methodName, args);
            methodCache.put(fullName, true);
            auditLogger.logMethodAllowed(fullName);
            return method;
        } catch (NoSuchMethodException e) {
            // 尝试查找声明的方法
            try {
                return cls.getDeclaredMethod(methodName, args);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException("Method not found: " + fullName);
            }
        }
    }
}