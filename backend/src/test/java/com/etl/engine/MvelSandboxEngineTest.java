package com.etl.engine;

import com.etl.engine.engine.MvelSandboxEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MvelSandboxEngineTest {

    private MvelSandboxEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MvelSandboxEngine(3000);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    @Test
    @DisplayName("测试变量赋值")
    void testVariableAssignment() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("a=100", context);
        
        assertEquals(100, result);
        assertEquals(100, context.get("a"));
    }

    @Test
    @DisplayName("测试字符串赋值")
    void testStringAssignment() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("name=\"etl\"", context);
        
        assertEquals("etl", result);
        assertEquals("etl", context.get("name"));
    }

    @Test
    @DisplayName("测试布尔值赋值")
    void testBooleanAssignment() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("flag=true", context);
        
        assertEquals(true, result);
        assertEquals(true, context.get("flag"));
    }

    @Test
    @DisplayName("测试数学运算")
    void testMathOperation() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("1+2*3", context);
        
        assertEquals(7, result);
    }

    @Test
    @DisplayName("测试变量参与运算")
    void testVariableOperation() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 5);
        
        Object result = engine.execute("a + b * 2", context);
        
        assertEquals(20, result);
    }

    @Test
    @DisplayName("测试逻辑判断")
    void testLogicalOperation() {
        Map<String, Object> context = new HashMap<>();
        context.put("x", 10);
        context.put("y", 5);
        
        Object result = engine.execute("x > y", context);
        
        assertEquals(true, result);
    }

    @Test
    @DisplayName("测试三元运算")
    void testTernaryOperation() {
        Map<String, Object> context = new HashMap<>();
        context.put("score", 85);
        
        Object result = engine.execute("score >= 60 ? \"pass\" : \"fail\"", context);
        
        assertEquals("pass", result);
    }

    @Test
    @DisplayName("测试多行表达式顺序执行")
    void testMultiLineExecution() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("a=10; b=20; a+b", context);
        
        assertEquals(30, result);
        assertEquals(10, context.get("a"));
        assertEquals(20, context.get("b"));
    }

    @Test
    @DisplayName("测试变量跨行引用")
    void testCrossLineVariableReference() {
        Map<String, Object> context = new HashMap<>();
        
        engine.execute("x=5; y=x*2", context);
        
        assertEquals(5, context.get("x"));
        assertEquals(10, context.get("y"));
    }

    @Test
    @DisplayName("测试嵌套表达式")
    void testNestedExpression() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("(a=3; b=4; Math.sqrt(a*a + b*b))", context);
        
        assertEquals(5.0, result);
    }

    @Test
    @DisplayName("测试危险类引用被拦截")
    void testDangerousClassBlocked() {
        Map<String, Object> context = new HashMap<>();
        
        assertThrows(SecurityException.class, () -> {
            engine.execute("Runtime.getRuntime().exec('ls')", context);
        });
    }

    @Test
    @DisplayName("测试System.exit被拦截")
    void testSystemExitBlocked() {
        Map<String, Object> context = new HashMap<>();
        
        assertThrows(SecurityException.class, () -> {
            engine.execute("System.exit(0)", context);
        });
    }

    @Test
    @DisplayName("测试文件操作被拦截")
    void testFileOperationBlocked() {
        Map<String, Object> context = new HashMap<>();
        
        assertThrows(SecurityException.class, () -> {
            engine.execute("new java.io.File('/etc/passwd')", context);
        });
    }

    @Test
    @DisplayName("测试执行超时")
    void testExecutionTimeout() {
        Map<String, Object> context = new HashMap<>();
        
        assertThrows(MvelSandboxEngine.ExpressionTimeoutException.class, () -> {
            engine.execute("while(true) {}", context);
        });
    }

    @Test
    @DisplayName("测试空表达式")
    void testEmptyExpression() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("", context);
        
        assertNull(result);
    }

    @Test
    @DisplayName("测试null值处理")
    void testNullValue() {
        Map<String, Object> context = new HashMap<>();
        
        Object result = engine.execute("null", context);
        
        assertNull(result);
    }

    @Test
    @DisplayName("测试复杂对象操作")
    void testComplexObject() {
        Map<String, Object> context = new HashMap<>();
        context.put("user", Map.of("name", "test", "age", 25));
        
        Object result = engine.execute("user.name", context);
        
        assertEquals("test", result);
    }
}
