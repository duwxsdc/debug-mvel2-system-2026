# 多步骤调试系统 - Spring Boot 3.4 + Vue3 + MVEL2 安全沙箱

一套**生产级别**的多步骤调度流程参数实时 Debug 系统，集成 MVEL2 严格安全沙箱隔离、跨步骤参数引用、WebSocket 长连接实时推送、前端悬浮调试面板、会话级上下文隔离。

## ✨ 核心特性

| 特性 | 说明 |
|------|------|
| 🔒 **安全沙箱** | 20+危险操作拦截，防RCE/注入/反射攻击 |
| 📊 **多步骤执行** | 支持串行执行、单步调试、暂停/继续 |
| 🔗 **参数引用** | 后步骤可引用前步骤计算结果 |
| 🔌 **WebSocket** | 长连接实时推送，支持心跳/重连 |
| 💡 **调试面板** | 悬浮可拖拽面板，实时查看参数/日志 |
| 🗂️ **会话隔离** | 线程安全，多会话互不干扰 |
| 📦 **即用即走** | 核心组件可独立复用 |

## 🚀 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.8+

### 后端启动

```bash
cd debug-system/backend

# 编译项目
mvn clean install -DskipTests

# 运行测试
mvn test

# 启动服务
mvn spring-boot:run
```

**启动成功标志**:
```
Started DebugSystemApplication in 2.xxx seconds
Tomcat started on port 8080 (http)
```

### 前端启动

```bash
cd debug-system/frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

**访问地址**: http://localhost:3000

### 验证服务

```bash
# 健康检查
curl http://localhost:8080/api/debug/health

# 预期返回
{"status":"UP","sessionCount":0}
```

---

## 📁 项目结构

```
debug-system/
├── backend/                          # 后端项目
│   ├── pom.xml                       # Maven 配置
│   └── src/main/java/com/debug/system/
│       ├── config/
│       │   └── WebSocketConfig.java          # WebSocket 配置
│       ├── controller/
│       │   └── DebugController.java          # REST 接口
│       ├── dto/
│       │   └── DebugCommand.java             # 命令 DTO
│       ├── executor/
│       │   └── StepExecutor.java             # 步骤执行器
│       ├── handler/
│       │   └── DebugWebSocketHandler.java    # WebSocket 处理器
│       ├── manager/
│       │   └── DebugSessionManager.java      # 会话管理器
│       ├── util/
│       │   └── MvelSafeExecutor.java         # MVEL 安全沙箱 ⭐
│       ├── vo/
│       │   └── DebugSnapshot.java            # 快照 VO
│       └── DebugSystemApplication.java       # 启动类
│
├── frontend/                         # 前端项目
│   ├── package.json                  # 依赖配置
│   └── src/
│       ├── components/
│       │   └── DebugPanel.vue        # 调试面板组件 ⭐
│       └── utils/
│           └── websocket.js          # WebSocket 工具
│
├── README.md                         # 项目说明
├── TEST_REPORT.md                    # 测试报告
├── SECURITY_DESIGN.md                # 安全设计文档
└── REUSE_MANUAL.md                   # 复用手册
```

---

## 🔧 功能演示

### 1. 打开调试面板

访问 http://localhost:3000，点击右下角悬浮按钮 🔧

### 2. 配置步骤

```
Step 1: key=a, expression=10, desc=基础数值
Step 2: key=b, expression=a*2, desc=引用参数
Step 3: key=c, expression=a+b+5, desc=多参数引用
Step 4: key=result, expression=c>20?'large':'small', desc=条件判断
```

### 3. 执行调试

- 点击 **Execute All** 执行全部步骤
- 点击 **Step** 单步执行
- 点击 **Reset** 重置会话
- 在 Params 标签手动修改参数

### 4. 查看结果

- **Steps**: 查看每步执行状态、结果、耗时
- **Params**: 查看全局参数上下文
- **Logs**: 查看执行日志

---

## 🔒 安全特性

### 危险操作拦截

| 攻击类型 | 测试表达式 | 拦截结果 |
|---------|-----------|---------|
| RCE | `Runtime.getRuntime().exec("cmd")` | ✅ BLOCKED |
| 反射 | `Class.forName("java.lang.Runtime")` | ✅ BLOCKED |
| 文件操作 | `new File("/etc/passwd")` | ✅ BLOCKED |
| 网络操作 | `new Socket("evil.com", 8080)` | ✅ BLOCKED |
| 代码注入 | `ScriptEngine.eval("...")` | ✅ BLOCKED |
| 资源耗尽 | `while(true){}` | ✅ TIMEOUT |

### 安全机制

```
第一层: 字符串预检查 (关键字/方法黑名单)
    ↓
第二层: AST 语法树检查 (节点分析)
    ↓
第三层: 编译时配置 (setAllowJavaAccess=false)
    ↓
第四层: 运行时超时保护 (500ms熔断)
```

---

## 📊 测试报告

详见: [TEST_REPORT.md](TEST_REPORT.md)

| 测试类别 | 用例数 | 通过率 |
|---------|-------|-------|
| 功能测试 | 25 | 100% |
| 安全测试 | 20 | 100% |
| 超时测试 | 3 | 100% |
| 白名单测试 | 7 | 100% |
| 边界测试 | 5 | 100% |
| **总计** | **60** | **100%** |

---

## 🔌 API 接口

### WebSocket 端点

```
ws://localhost:8080/api/ws/debug?sessionId={sessionId}
```

### 命令示例

```json
{
  "sessionId": "session-001",
  "command": "START",
  "steps": [
    {"key": "a", "expression": "10", "desc": "Step 1"},
    {"key": "b", "expression": "a * 2", "desc": "Step 2"}
  ]
}
```

### 命令类型

| 命令 | 说明 |
|------|------|
| START | 开始执行所有步骤 |
| STEP | 单步执行 |
| PAUSE | 暂停执行 |
| RESUME | 恢复执行 |
| RESET | 重置会话 |
| TERMINATE | 终止执行 |
| UPDATE_PARAM | 更新参数 |

### REST 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /debug/health | 健康检查 |
| GET | /debug/snapshot/{sessionId} | 获取快照 |
| DELETE | /debug/session/{sessionId} | 删除会话 |

---

## 📦 项目复用

### 最小集成 (仅安全沙箱)

**只需复制 1 个文件**: `MvelSafeExecutor.java`

```java
MvelSafeExecutor executor = new MvelSafeExecutor();
Map<String, Object> context = new HashMap<>();
context.put("price", 100);
context.put("discount", 0.2);

Object result = executor.execute("price * (1 - discount)", context);
// result = 80.0
```

详见: [REUSE_MANUAL.md](REUSE_MANUAL.md)

---

## ⚙️ 配置说明

### application.yml

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

debug:
  session:
    timeout: 3600000        # 会话超时(ms)
    cleanup-interval: 300000 # 清理间隔(ms)
  executor:
    timeout: 500            # 执行超时(ms)
    max-steps: 100          # 最大步骤数
```

---

## 📚 文档索引

- [测试报告](TEST_REPORT.md) - 完整测试用例和结果
- [安全设计文档](SECURITY_DESIGN.md) - 安全架构和防护机制
- [复用手册](REUSE_MANUAL.md) - 集成指南和API使用

---

## 🛠️ 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.4.0 |
| Java | 17 |
| MVEL2 | 2.4.7.Final |
| Vue | 3.4.0 |
| Element Plus | 2.5.0 |

---

## 📝 License

MIT License - 可自由用于商业项目

---

**文档版本**: 1.0  
**最后更新**: 2026-04-27
"# debug-mvel2-system-2026" 
