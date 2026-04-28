# ETL流程编排专用表达式参数引擎

## 项目简介

基于 SpringBoot3.4.6、JDK21、MVEL2、WebSocket、JDBC 技术栈开发的ETL流程编排专用表达式参数引擎，用于动态计算、变量管理和SQL查询。

## 核心特性

- **表达式引擎**: 兼容MVEL2语法，支持变量赋值、数学运算、逻辑判断、三元运算
- **SQL执行**: 支持只读SQL查询，内置安全黑名单过滤
- **会话隔离**: 每个WebSocket连接独立会话，变量完全隔离
- **安全沙箱**: 禁用危险类、系统API、文件操作、进程调用
- **虚拟线程**: 使用JDK21虚拟线程提升高并发吞吐量
- **ScopedValue**: 使用JDK21新特性实现上下文透传，替代ThreadLocal

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21 | LTS 版本，使用虚拟线程、Record、ScopedValue |
| SpringBoot | 3.4.6 | 框架基础 |
| MVEL2 | 2.5.0.Final | 表达式引擎 |
| H2 | 2.x | 嵌入式数据库 |
| WebSocket | - | 实时双向通信 |

## 项目结构

```
backend/
├── src/main/java/com/etl/engine/
│   ├── ExpressionEngineApplication.java  # 启动类
│   ├── config/                           # 配置层
│   │   ├── EngineConfig.java             # 引擎配置
│   │   └── WebSocketConfig.java          # WebSocket配置
│   ├── engine/                           # 引擎层
│   │   ├── MvelSandboxEngine.java        # MVEL2安全沙箱引擎
│   │   └── SqlExecutionEngine.java       # SQL执行引擎
│   ├── handler/                          # 处理器层
│   │   └── ExpressionWebSocketHandler.java # WebSocket处理器
│   ├── manager/                          # 管理层
│   │   └── SessionContextManager.java    # 会话上下文管理器
│   └── model/                            # 模型层
│       ├── ExpressionRequest.java         # 请求DTO
│       ├── ExpressionResult.java          # 响应DTO
│       └── SessionContext.java            # 会话上下文实体
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   └── static/index.html                 # 前端Console页面
├── src/test/java/com/etl/engine/         # 单元测试
└── pom.xml                               # Maven依赖
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+

### 构建运行

```bash
cd backend
mvn clean package
mvn spring-boot:run
```

### 访问地址

- **前端Console**: http://localhost:8080/
- **H2控制台**: http://localhost:8080/h2-console

## WebSocket接口

### 连接地址

```
ws://localhost:8080/ws/expression
```

### 消息格式

#### 发送消息

直接发送表达式字符串，无需封装：

```
a=100
```

#### 响应消息

```json
{
    "sessionId": "uuid-string",
    "expression": "a=100",
    "result": 100,
    "success": true,
    "errorMessage": null,
    "assignedVariables": {
        "a": 100
    },
    "timestamp": "2024-01-01T12:00:00"
}
```

## 表达式语法手册

### 1. 变量赋值

```
a=100
name="etl"
flag=true
pi=3.14159
```

### 2. 数学运算

```
1 + 2 * 3           # 结果: 7
(1 + 2) * 3         # 结果: 9
a + b * c           # 使用变量
```

### 3. 逻辑判断

```
x > y
flag && (x < 10)
a == b || c != d
```

### 4. 三元运算

```
score >= 60 ? "pass" : "fail"
value != null ? value : "default"
```

### 5. 多行执行

使用分号分隔多个表达式，中间变量赋值全部生效：

```
a=10; b=20; a + b   # 结果: 30
```

### 6. SQL查询

仅支持只读查询：

```sql
SELECT * FROM users
SELECT name, age FROM users WHERE age > 25
SHOW TABLES
```

### 7. 对象操作

```
user.name           # 获取对象属性
list[0]             # 获取列表元素
map.key             # 获取Map值
```

## 安全限制

### 禁止的Java类

- `java.lang.Runtime`
- `java.lang.ProcessBuilder`
- `java.lang.System`
- `java.io.File`
- `java.net.Socket`
- `java.lang.ClassLoader`
- `java.lang.Thread`

### 禁止的SQL命令

- UPDATE
- DELETE
- INSERT
- DROP
- ALTER
- TRUNCATE
- CREATE
- REPLACE

### 执行限制

- 表达式执行超时时间：5秒
- 会话超时时间：30分钟

## 配置说明

### application.yml

```yaml
server:
  port: 8080                    # 服务端口

spring:
  datasource:
    url: jdbc:h2:mem:testdb     # H2内存数据库
    username: sa
    password: 

logging:
  level:
    com.etl.engine: DEBUG       # 日志级别
```

## 使用示例

### 示例1: 简单变量赋值

```
a=100
```

响应：
```json
{
    "result": 100,
    "success": true,
    "assignedVariables": {"a": 100}
}
```

### 示例2: 多行表达式

```
x=5; y=x*2; x+y
```

响应：
```json
{
    "result": 15,
    "success": true,
    "assignedVariables": {"x": 5, "y": 10}
}
```

### 示例3: SQL查询

```sql
SELECT * FROM users
```

响应：
```json
{
    "result": [
        {"ID": 1, "NAME": "Alice", "AGE": 25},
        {"ID": 2, "NAME": "Bob", "AGE": 30}
    ],
    "success": true
}
```

### 示例4: 错误处理

```
undefinedVariable
```

响应：
```json
{
    "result": null,
    "success": false,
    "errorMessage": "执行错误: ..."
}
```

## API说明

### SessionContextManager

| 方法 | 说明 |
|------|------|
| `createSession()` | 创建新会话 |
| `getSession(sessionId)` | 获取会话上下文 |
| `removeSession(sessionId)` | 删除会话 |
| `executeInSession(sessionId, action)` | 在会话上下文中执行操作 |

### MvelSandboxEngine

| 方法 | 说明 |
|------|------|
| `execute(expression, context)` | 执行表达式 |

### SqlExecutionEngine

| 方法 | 说明 |
|------|------|
| `executeQuery(sql)` | 执行SQL查询 |

## 测试说明

单元测试覆盖以下场景：

- 变量赋值（字符串、数字、布尔值）
- 数学运算和逻辑判断
- 多行表达式执行
- 变量跨行引用
- SQL查询和安全限制
- 危险类拦截
- 执行超时
- 会话隔离

运行测试：

```bash
mvn test
```

## 许可证

MIT License
