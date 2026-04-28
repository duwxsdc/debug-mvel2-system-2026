# 项目复用手册

## 一、快速集成指南

### 1.1 最小集成（仅安全沙箱）

**只需复制1个文件**: `MvelSafeExecutor.java`

```
your-project/
└── src/main/java/com/yourpackage/util/
    └── MvelSafeExecutor.java    ← 复制此文件
```

**使用方法**:
```java
import com.yourpackage.util.MvelSafeExecutor;

// 创建执行器
MvelSafeExecutor executor = new MvelSafeExecutor();

// 执行表达式
Map<String, Object> context = new HashMap<>();
context.put("price", 100);
context.put("discount", 0.2);

Object result = executor.execute("price * (1 - discount)", context);
// result = 80.0
```

### 1.2 完整集成（全部功能）

**需要复制的文件**:

```
your-project/
└── src/main/java/com/debug/system/
    ├── util/
    │   └── MvelSafeExecutor.java        ← 安全沙箱
    ├── manager/
    │   └── DebugSessionManager.java     ← 会话管理
    ├── executor/
    │   └── StepExecutor.java            ← 步骤执行器
    ├── handler/
    │   └── DebugWebSocketHandler.java   ← WebSocket处理
    ├── config/
    │   └── WebSocketConfig.java         ← WebSocket配置
    ├── dto/
    │   └── DebugCommand.java            ← 命令DTO
    └── vo/
        └── DebugSnapshot.java           ← 快照VO
```

**前端组件**:
```
your-frontend/
└── src/
    ├── components/
    │   └── DebugPanel.vue    ← 调试面板组件
    └── utils/
        └── websocket.js      ← WebSocket工具
```

---

## 二、依赖配置

### 2.1 Maven (pom.xml)

```xml
<dependencies>
    <!-- MVEL2 -->
    <dependency>
        <groupId>org.mvel</groupId>
        <artifactId>mvel2</artifactId>
        <version>2.4.7.Final</version>
    </dependency>

    <!-- WebSocket (Spring Boot 3.x) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>

    <!-- Jackson (日期时间支持) -->
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

### 2.2 Gradle (build.gradle)

```groovy
dependencies {
    implementation 'org.mvel:mvel2:2.4.7.Final'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    compileOnly 'org.projectlombok:lombok:1.18.30'
    annotationProcessor 'org.projectlombok:lombok:1.18.30'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
}
```

### 2.3 前端依赖 (package.json)

```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "element-plus": "^2.5.0"
  }
}
```

---

## 三、配置说明

### 3.1 后端配置 (application.yml)

```yaml
debug:
  session:
    timeout: 3600000        # 会话超时时间(ms)，默认1小时
    cleanup-interval: 300000 # 清理间隔(ms)，默认5分钟
  executor:
    timeout: 500            # 表达式执行超时(ms)，默认500ms
    max-steps: 100          # 最大步骤数，默认100
```

### 3.2 超时时间调整

```java
// 方式1: 构造函数
MvelSafeExecutor executor = new MvelSafeExecutor(1000); // 1秒超时

// 方式2: 配置文件 + @Value
@Value("${debug.executor.timeout:500}")
private long timeout;

@Bean
public MvelSafeExecutor mvelSafeExecutor() {
    return new MvelSafeExecutor(timeout);
}
```

### 3.3 白名单扩展

```java
// 修改 MvelSafeExecutor.isAllowedNewObject 方法
private boolean isAllowedNewObject(String expression) {
    Set<String> allowedTypes = Set.of(
        // 默认允许
        "ArrayList", "HashMap", "HashSet", "LinkedList",
        "Arrays", "Math", "String", "Integer", "Long",
        "Double", "Float", "Boolean", "Character", "Byte", "Short",
        
        // 添加您的自定义类
        "com.yourpackage.YourClass"
    );
    // ...
}
```

---

## 四、API 使用示例

### 4.1 安全表达式执行

```java
// 基础使用
MvelSafeExecutor executor = new MvelSafeExecutor();

// 无上下文
Object result1 = executor.execute("1 + 2 * 3");  // 7

// 带上下文
Map<String, Object> context = new HashMap<>();
context.put("a", 10);
context.put("b", 20);
Object result2 = executor.execute("a + b", context);  // 30

// 预编译复用
Serializable compiled = executor.compile("a * b");
Object result3 = executor.executeCompiled(compiled, context);  // 200
```

### 4.2 步骤执行器使用

```java
@Autowired
private StepExecutor stepExecutor;

// 设置步骤
List<DebugCommand.StepDefinition> steps = Arrays.asList(
    DebugCommand.StepDefinition.builder()
        .key("price")
        .expression("100")
        .desc("原价")
        .build(),
    DebugCommand.StepDefinition.builder()
        .key("finalPrice")
        .expression("price * 0.8")
        .desc("折后价")
        .build()
);
stepExecutor.setSteps("session-001", steps);

// 执行所有步骤
stepExecutor.executeAll("session-001");

// 获取结果
DebugSnapshot snapshot = stepExecutor.getSnapshot("session-001");
System.out.println(snapshot.getContext());  // {price=100, finalPrice=80.0}
```

### 4.3 会话管理

```java
@Autowired
private DebugSessionManager sessionManager;

// 创建会话
DebugSession session = sessionManager.createSession("session-001");

// 设置参数
session.setContextValue("userId", 12345);
session.setContextValue("userName", "John");

// 获取参数
Object userId = session.getContextValue("userId");

// 清理会话
sessionManager.removeSession("session-001");
```

### 4.4 WebSocket 前端集成

```javascript
import { createDebugWebSocket } from './utils/websocket'

// 创建连接
const ws = createDebugWebSocket('my-session-id')

// 监听快照更新
ws.on('onSnapshot', (snapshot) => {
  console.log('Context:', snapshot.context)
  console.log('Steps:', snapshot.stepResults)
})

// 连接
ws.connect()

// 发送命令
ws.start('my-session-id', [
  { key: 'a', expression: '10', desc: 'Step 1' },
  { key: 'b', expression: 'a * 2', desc: 'Step 2' }
])

// 更新参数
ws.updateParam('my-session-id', 'a', 20)

// 重置
ws.reset('my-session-id')
```

---

## 五、前端组件集成

### 5.1 Vue3 组件引入

```vue
<template>
  <div id="app">
    <!-- 您的业务页面 -->
    <YourBusinessPage />
    
    <!-- 调试面板（悬浮按钮） -->
    <DebugPanel />
  </div>
</template>

<script setup>
import DebugPanel from './components/DebugPanel.vue'
</script>
```

### 5.2 自定义配置

```vue
<template>
  <DebugPanel 
    :auto-connect="true"
    :default-steps="defaultSteps"
  />
</template>

<script setup>
const defaultSteps = [
  { key: 'a', expression: '100', desc: 'Base' },
  { key: 'b', expression: 'a * 2', desc: 'Double' }
]
</script>
```

---

## 六、常见问题排查

### 6.1 编译问题

**问题**: 找不到 Lombok 生成的方法
```
解决: 
1. 确保 IDE 安装 Lombok 插件
2. 启用 annotation processor
3. mvn clean compile
```

**问题**: Spring Boot 3 兼容性
```
解决:
1. 确保 Java 17+
2. 使用 spring-boot-starter-parent 3.x
3. WebSocket 配置无需特殊修改
```

### 6.2 运行问题

**问题**: WebSocket 连接失败
```
排查:
1. 检查后端是否启动 (http://localhost:8080/api/debug/health)
2. 检查端口是否正确
3. 检查防火墙设置
4. 查看浏览器控制台错误
```

**问题**: 表达式执行报安全错误
```
排查:
1. 检查表达式是否包含危险关键字
2. 查看错误信息确认具体原因
3. 如需使用特定类，添加到白名单
```

**问题**: 会话数据丢失
```
排查:
1. 检查会话超时配置
2. 检查 WebSocket 连接是否断开
3. 确认没有手动清理会话
```

### 6.3 性能问题

**问题**: 表达式执行慢
```
优化:
1. 使用预编译: compile() + executeCompiled()
2. 增加超时时间
3. 简化复杂表达式
```

**问题**: 内存占用高
```
优化:
1. 调小会话超时时间
2. 调小清理间隔
3. 减少上下文参数数量
```

---

## 七、扩展开发

### 7.1 添加新的安全检查

```java
// 在 MvelSafeExecutor.validateExpression 中添加
if (normalized.contains("yourDangerousKeyword")) {
    throw new MvelSecurityException("Dangerous keyword detected: yourDangerousKeyword");
}
```

### 7.2 自定义步骤类型

```java
// 扩展 DebugCommand.StepDefinition
@Data
public static class StepDefinition {
    private String key;
    private String expression;
    private String desc;
    private StepType type;  // 新增: CALCULATION, VALIDATION, TRANSFORM
}
```

### 7.3 添加持久化支持

```java
// 扩展 DebugSessionManager
public void persistSession(String sessionId) {
    DebugSession session = sessions.get(sessionId);
    // 保存到数据库
    sessionRepository.save(session);
}

public DebugSession restoreSession(String sessionId) {
    // 从数据库恢复
    DebugSession session = sessionRepository.findById(sessionId);
    sessions.put(sessionId, session);
    return session;
}
```

---

## 八、版本兼容性

| 组件 | 最低版本 | 推荐版本 |
|------|---------|---------|
| Java | 17 | 17+ |
| Spring Boot | 3.0 | 3.4+ |
| MVEL2 | 2.4.0 | 2.4.7.Final |
| Vue | 3.0 | 3.4+ |
| Element Plus | 2.0 | 2.5+ |

---

## 九、技术支持

- **GitHub Issues**: [项目地址]/issues
- **文档**: README.md, SECURITY_DESIGN.md
- **测试报告**: TEST_REPORT.md

---

**手册版本**: 1.0  
**最后更新**: 2026-04-27
