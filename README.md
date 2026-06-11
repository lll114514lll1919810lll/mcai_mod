# MCAI 模组开发总结

## 概述

MCAI 是一个 Minecraft Fabric 服务端模组，接入 OpenAI 兼容 API（默认 DeepSeek），让 AI 能够：
- 读取玩家聊天和服务器广播
- 执行 Minecraft 指令（需审批的自动走审批流程）
- 搜索本地 Wiki 知识库
- 跨重启持久记忆

---

## 技术架构

```
客户端                                   服务端
─────────────────────────────────────────────────
Mod Menu ──→ MCAIConfigScreen          MCAIMod (ModInitializer)
(配置界面)                               ├── OpenAIClient (API 调用)
                                         ├── ChatHandler (核心逻辑)
                                         ├── KnowledgeBase (本地 Wiki)
                                         ├── MemoryFile (持久记忆)
                                         └── ModConfig (JSON 配置)
```

### 核心文件

| 文件 | 作用 | 行数 |
|------|------|------|
| `MCAIMod.java` | 主入口、初始化、线程安全 server 引用 | ~80 |
| `ChatHandler.java` | 聊天拦截、AI 查询、指令审批、玩家上下文 | ~520 |
| `OpenAIClient.java` | OpenAI API 调用、工具调用循环、thinking 模式 | ~300 |
| `ModConfig.java` | 配置加载/保存、自动更新提示词 | ~135 |
| `MCAIConfigScreen.java` | Mod Menu 配置界面 | ~240 |
| `KnowledgeBase.java` | 本地知识库搜索与读取 | ~115 |
| `MemoryFile.java` | 跨重启持久记忆 | ~100 |
| `ModMenuIntegration.java` | Mod Menu 接入 | ~20 |
| `download_zh_wiki.py` | 中文 Wiki 下载脚本 | ~170 |

---

## 功能清单

### AI 对话
- `/ai <消息>` 或 `!ai` 前缀触发
- 多轮对话上下文记忆（按字符数截断，默认 20000 字）
- 捕获全部聊天记录 + 服务器广播（进度、死亡、加入/离开）
- 同步异步：独立线程池 `aiExecutor`，避免 Java 25 ForkJoinPool 问题

### 工具调用（Function Calling）
| 工具 | 说明 |
|------|------|
| `search_knowledge_base` | 搜索本地中文 Wiki 知识库 |
| `read_knowledge_base` | 读取条目完整内容 |
| `execute_minecraft_command` | 执行指令（全服广播结果） |
| `recall` | 读取持久记忆 |
| `remember` | 存储持久记忆 |

### 审批系统
- 危险指令（op、ban、kick 等）自动进入审批队列
- `/aiquery` 查看待审批列表
- `/aiaccept <编号>` 批准执行
- `/aireject <编号>` 拒绝移除
- 每个玩家独立审批列表

### 知识库
- 内置 2.1MB 中文 Wiki 数据（JAR 内部 `assets/mcai/kb/zh_wiki.json`）
- 双层检索：搜索返回摘要，读取返回全文
- 约 300+ 中文页面（工具、方块、生物、附魔、指令等）
- 可通过 Python 脚本扩充

### 持久记忆
- 文件 `config/mcai_memory.json`，跨重启保留
- AI 自动在对话开始时读取记忆
- 可通过 `remember`/`recall` 工具管理

### 配置 (`config/mcai.json`)
- 自动更新提示词（系统提示词只保留在代码中，不保存到 JSON）
- 支持通过 Mod Menu 可视化编辑
- 命令行 `/aireload` 重载

### DeepSeek 专有支持
- `thinking` 模式（0=关闭, 1=开启, 3=max effort）
- `reasoning_content` 回传（思考模式下 tool_calls 必须回传思维链）
- `enable_search` 联网搜索（需 DeepSeek 账户开通）

---

## 版本兼容性

### 已支持版本

| MC 版本 | JAR 文件名 | 构建配置 |
|---------|-----------|---------|
| 1.21/1.21.1 | `mcai-1.21.jar` | `gradle-1.21.properties` |
| 1.21.11 | `mcai-1.21.11.jar` | `gradle-1.21.11.properties` |

### 1.21 vs 1.21.11 Yarn API 差异

| 项目 | 1.21 Yarn | 1.21.11 Yarn |
|------|----------|-------------|
| 客户端发送指令 | `sendCommand()` | `sendChatCommand()` |
| 执行指令 | `executeWithPrefix()` | `getDispatcher().execute()` + catch CommandSyntaxException |
| 权限参数类型 | `int` (数字) | `Predicate<ServerCommandSource>` (lambda) |
| Entity 获取世界 | `getWorld()` | `getEntityWorld()` |
| GameMode 名称 | `getName()` | `asString()` |
| 线程池 | `CompletableFuture.runAsync()` → ForkJoinPool | `aiExecutor.execute()` → cached thread pool |

### 26.1.2

Yarn 映射 **未发布**。`class_2561`（Text 类的中继名）在 26.1.2 中不存在，无法构建。需等 Fabric Yarn 发布。
26.1 开始取消了混淆映射表，可能需要重新考虑。

---

## 关键问题解决记录

### 1. `hasPermissionLevel` 崩溃（1.21.11）
- 症状：`NoSuchMethodError: method_9259`
- 原因：1.21.11 移除了 `ServerCommandSource.hasPermissionLevel(int)`
- 修复：移除所有 `.requires(s -> s.hasPermissionLevel(...))` 调用

### 2. `reasoning_content` 未回传
- 症状：`The 'reasoning_content' in the thinking mode must be passed back to the API`
- 原因：DeepSeek 思考模式下，tool_calls 消息必须回传 `reasoning_content`
- 修复：捕获响应中的 `reasoning_content`，附加到 assistant 消息

### 3. tool_calls 格式错误
- 症状：`Messages with role 'tool' must be a response to a preceding message with 'tool_calls'`
- 原因：tool_calls 消息的 `content` 字段设为 `""` 而非不传
- 修复：tool_calls 消息不包含 `content` 字段

### 4. ForkJoinPool 静默失败（Java 25）
- 症状：只显示"思考中"，没有后续响应
- 原因：`CompletableFuture.runAsync()` 使用 ForkJoinPool，Java 25 下线程可能被阻塞
- 修复：独立 `ExecutorService` + `volatile` 确保线程可见性

---

## 构建方法

```bash
# 1. 选择版本
copy gradle-1.21.properties gradle.properties        # 1.21
copy gradle-1.21.11.properties gradle.properties      # 1.21.11

# 2. 编译
.\gradlew.bat remapJar --no-daemon

# 3. 产物
# build/libs/mcai-1.0.0.jar
```

需预装：JDK 21（1.21）或 JDK 25（1.21.11）、Git、Python 3（知识库下载）

---

## 开发者备注

- 系统提示词（system prompt）保存在 `ModConfig.java` 中，不存储到 `mcai.json`
- 每次 `load()` 会覆盖为代码中的最新版本，确保更新后提示词自动生效
- 知识库 JSON 文件嵌入 JAR 内部，路径 `assets/mcai/kb/`
- 线程安全：`aiExecutor` 使用独立线程池 + `volatile` server 字段
- 配置文件不保存 `systemPrompt`，使用 `ExclusionStrategy` 序列化跳过
