// ============ 安全类解析器 ============

package com.mvel.sendbox.security;

import com.mvel.sendbox.config.MvelSandboxConfig;
import org.mvel2.CompileException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 可配置的安全类解析器
 * 注意：MVEL2 2.5.0.Final 中没有 ClassResolver 接口
 * 这个类作为工具类使用，在编译前进行类访问检查
 */
public class SecureClassResolver {

    private final MvelSandboxConfig.CompileConfig config;
    private final AuditLogger auditLogger;

    // 缓存已解析的类
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final Set<String> deniedCache = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 包匹配缓存
    private final List<Pattern> allowedPackagePatterns = new ArrayList<>();

    public SecureClassResolver(MvelSandboxConfig.CompileConfig config, AuditLogger auditLogger) {
        this.config = config;
        this.auditLogger = auditLogger;

        // 预编译包模式
        for (String pkg : config.getAllowedPackages()) {
            allowedPackagePatterns.add(Pattern.compile(
                    "^" + pkg.replace(".", "\\.") + "\\..*$"));
        }
    }

    public Class<?> resolveClass(String className) {
        // 1. 检查缓存
        if (deniedCache.contains(className)) {
            throw new SecurityException("Class access denied (cached): " + className);
        }
        Class<?> cached = classCache.get(className);
        if (cached != null) return cached;

        // 2. 黑名单检查（优先级最高）
        for (String black : config.getClassBlacklist()) {
            if (matchesPattern(className, black)) {
                deniedCache.add(className);
                auditLogger.logClassDenied(className, "blacklist");
                throw new SecurityException("Class blacklisted: " + className);
            }
        }

        // 3. 白名单检查
        if (!config.getClassWhitelist().isEmpty()) {
            boolean allowed = false;
            for (String white : config.getClassWhitelist()) {
                if (matchesPattern(className, white)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                deniedCache.add(className);
                auditLogger.logClassDenied(className, "whitelist");
                throw new SecurityException("Class not in whitelist: " + className);
            }
        }

        // 4. 严格模式：包检查
        if (config.isStrictMode()) {
            boolean inAllowedPackage = false;
            for (Pattern pattern : allowedPackagePatterns) {
                if (pattern.matcher(className).matches()) {
                    inAllowedPackage = true;
                    break;
                }
            }
            if (!inAllowedPackage) {
                deniedCache.add(className);
                auditLogger.logClassDenied(className, "strict-package");
                throw new SecurityException("Class not in allowed packages: " + className);
            }
        }

        // 5. 解析类
        try {
            Class<?> clazz = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            classCache.put(className, clazz);
            auditLogger.logClassAllowed(className);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new CompileException("Class not found: " + className, className.toCharArray(), 0);
        }
    }

    private boolean matchesPattern(String className, String pattern) {
        if (pattern.endsWith(".*")) {
            return className.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return className.equals(pattern);
    }
}