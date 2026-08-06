# MCAI Code Wiki

> Minecraft Fabric Mod —— 将 AI 助手深度集成到 Minecraft 服务器中，实现智能对话、命令执行、知识库搜索与自动行为审查。

---

## 目录

1. [项目总览](#1-项目总览)
2. [架构设计](#2-架构设计)
3. [模块详解](#3-模块详解)
4. [核心类与函数说明](#4-核心类与函数说明)
5. [依赖关系图](#5-依赖关系图)
6. [事件生命周期](#6-事件生命周期)
7. [运行与构建](#7-运行与构建)
8. [配置与资源](#8-配置与资源)

---

## 1. 项目总览

### 1.1 基本信息

| 属性 | 值 |
|---|---|
| 项目名称 | MCAI（AI Assistant for Minecraft） |
| Mod ID | `mcai` |
| Minecraft 版本 | 26.1.2（快照 26.3 兼容） |
| Java 版本 | 25 |
| 构建系统 | Gradle 9.5.1 + Fabric Loom 1.14.1 |
| Fabric Loader | 0.19.2 |
| Fabric API | 0.149.1+26.1.2 |
| 映射 | Mojang Mappings |
| 许可证 | MIT |
| 目标环境 | 专用服务器 + 客户端（`environment: *`） |

### 1.2 核心功能

| 功能 | 说明 | 触发方式 |
|---|---|---|
| AI 对话 | 玩家通过前缀（默认 `!ai`）与 AI 助手对话 | `!ai <消息>` 或 `/ai <消息>` |
| 命令执行 | AI 通过工具调用执行 Minecraft 命令（危险命令走审批） | AI 自动触发 `execute_minecraft_command` |
| 命令链 | 多条命令打包为一个审批单元，支持执行间隔 | AI 自动触发 `execute_command_chain` |
| 知识库搜索 | 搜索 Minecraft Wiki（zh_cn / en_us）获取游戏知识 | AI 自动触发 `search_knowledge_base` 或 `/aikb` |
| 行为审查 | 定时（默认 30 分钟）AI 自动分析聊天记录并处罚违规玩家 | 自动周期运行 |
| 人格系统 | 切换 AI 人格（傲娇、海盗、中二病、村民、猪灵等） | `/aipersona set <index>` |
| Mod Menu 集成 | 客户端 GUI 配置界面 | Mod Menu → MCAI → Config |
| 调试日志 | 记录 AI 交互全流程（思考、工具调用、回复） | `/aidebug start` |

### 1.3 目录结构

```
mc/
├── src/main/java/com/example/mcai/
│   ├── MCAIMod.java                  # Mod 主入口
│   ├── api/                          # AI API 客户端层
│   │   ├── OpenAIClient.java
│   │   └── ApiResult.java
│   ├── handler/                      # 核心处理层（Orchestrator）
│   │   ├── ChatHandler.java
│   │   ├── ChatLog.java
│   │   ├── CommandExecutionService.java
│   │   ├── CommandRegistry.java
│   │   ├── ToolDispatcher.java
│   │   ├── PlayerContextBuilder.java
│   │   ├── ThinkingAnimation.java
│   │   ├── PersonaManager.java
│   │   └── AIDebugLogger.java
│   ├── behavior/                     # 行为审查子系统
│   │   ├── ChatReviewSystem.java
│   │   ├── ReviewEngine.java
│   │   ├── ReviewCommandRegistry.java
│   │   ├── AdminApprovalQueue.java
│   │   ├── PlayerBehaviorTracker.java
│   │   ├── PenaltyHistory.java
│   │   ├── PenaltyEvent.java
│   │   └── PlayerViolation.java
│   ├── config/                       # 配置管理
│   │   ├── ModConfig.java
│   │   └── PromptLoader.java
│   ├── kb/                           # 知识库搜索子系统
│   │   ├── SearchProvider.java       # 抽象接口
│   │   ├── SearchResult.java
│   │   ├── SearchRouter.java
│   │   ├── WikiSearchProvider.java
│   │   └── KnowledgeBase.java
│   └── client/                       # 客户端专用
│       ├── ModMenuIntegration.java
│       └── config/
│           └── MCAIConfigScreen.java
├── src/main/resources/
│   ├── assets/mcai/lang/             # i18n 语言文件
│   │   ├── en_us.json
│   │   └── zh_cn.json
│   ├── mcai/                         # 内置人设文件（JSON）
│   │   ├── personas/                 # 通用人格（tsundere, pirate, chuuni, gentle）
│   │   └── mc_personas/              # 游戏角色人格（villager, piglin, ender_dragon, creeper）
│   └── fabric.mod.json
├── kb/                               # 知识库数据（不打入 JAR）
│   ├── zh_wiki.json
│   ├── biomesoplenty.json
│   ├── create_mod.json
│   └── LICENSE*.txt
├── tools/
│   └── wiki_to_kb.py                 # Wiki 抓取脚本
├── gradle.properties                 # 版本号、依赖版本
├── build.gradle                      # Gradle 构建脚本
└── settings.gradle
```

---

## 2. 架构设计

### 2.1 分层架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      Minecraft Server                        │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                  CommandRegistry                       │ │
│  │   /ai  /aikb  /aiaccept  /aireject  /aicancel  ...    │ │
│  └────────────┬────────────────────────┬──────────────────┘ │
│               │                        │                    │
│  ┌────────────▼─────────┐   ┌─────────▼───────────────────┐ │
│  │     ChatHandler       │   │  ReviewCommandRegistry      │ │
│  │  (AI 对话编排)        │   │  (审查系统命令)              │ │
│  └────┬──────────┬──────┘   └─────────┬───────────────────┘ │
│       │          │                     │                     │
│  ┌────▼──┐ ┌─────▼──────────────┐  ┌───▼───────────────────┐ │
│  │  ToolDispatcher     │  ChatReviewSystem                 │ │
│  │  (工具调用路由)      │  (审查编排)                       │ │
│  └──┬───┘ └────────┬────────────┘  └───┬───────────────────┘ │
│     │               │                   │                    │
│  ┌──▼──┐  ┌────────▼─────────┐   ┌──────▼───────┐            │
│  │OpenAIClient│ │CommandExecutionService│ │ ReviewEngine │    │
│  │(AI API 调用)│ │(审批 + 命令执行)       │ │(AI 审查调用) │    │
│  └──┬──┘  └────────┬─────────┘   └──────┬───────┘            │
│     │               │                    │                    │
│     ▼               ▼                    ▼                    │
│  ┌─────────────────────────────────────────────┐              │
│  │              SearchRouter (Wiki)              │              │
│  └─────────────────────────────────────────────┘              │
│                                                                │
│  ┌─────────────────────────────────────────────┐              │
│  │          PlayerBehaviorTracker +             │              │
│  │          PenaltyHistory (持久化)              │              │
│  └─────────────────────────────────────────────┘              │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
                   ┌─────────────────────┐
                   │   ModConfig (config  │
                   │   .json 热重载)      │
                   └─────────────────────┘
```

### 2.2 核心设计理念

#### 服务定位器模式
MCAIMod 单例持有所有子系统实例，通过 `MCAIMod.getInstance()` + getter 方法实现服务定位。各子模块通过构造函数或 setter 注入依赖。

#### 事件驱动 + 生命周期钩子
- `ModInitializer.onInitialize()` — Mod 加载时初始化
- `ServerLifecycleEvents.SERVER_STARTED` — 进入世界时重建线程池（退出世界时线程池已关闭）
- `ServerLifecycleEvents.SERVER_STOPPING` — 退出世界时清理资源
- `ServerPlayConnectionEvents.DISCONNECT` — 玩家断开时清理上下文
- `ServerMessageEvents.CHAT_MESSAGE` / `ALLOW_CHAT_MESSAGE` / `GAME_MESSAGE` — 聊天拦截

#### 异步线程池策略

| 线程池 | 创建位置 | 用途 | 关闭时机 |
|---|---|---|---|
| `MCAI-Worker` (4~8 线程) | ChatHandler | AI 对话请求处理 | 不关闭（daemon），`killAIThreads()` 重建 |
| `MCAI-SearchRouter` (CachedThreadPool) | SearchRouter | Wiki 在线搜索 | `SERVER_STOPPING` |
| `MCAI-UI` (单线程 Scheduler) | ThinkingAnimation | "思考中" 动画 | 不关闭（daemon） |
| `MCAI-Review` (单线程 Scheduler) | ChatReviewSystem | 定时审查任务 | `SERVER_STOPPING` |
| `MCAI-ConfigWatcher` (单线程 Scheduler) | MCAIMod | 配置文件热重载 | `SERVER_STOPPING` |
| `MCAI-Chain-{id}` (临时线程) | CommandExecutionService | 命令链执行 | 执行完毕自动结束 |

#### 审批机制

命令执行的核心安全机制：

```
AI 调用 execute_minecraft_command
        │
        ▼
CommandExecutionService.needsApproval(command)
        │
   ┌────┴────┐
   │Yes      │No
   ▼         ▼
PendingCommand  server.execute() 直接执行
.future 等待    广播 + 记录
管理员审批
   │
   ┌─/aiaccept <id>────────────────────┐
   └─/aireject <id>────────────────────┘
   完成 future.complete(result)
```

---

## 3. 模块详解

### 3.1 入口模块：MCAIMod.java

**文件**: [MCAIMod.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/MCAIMod.java)

职责：Mod 主入口，负责所有子系统的初始化、事件注册与生命周期管理。

#### 3.1.1 初始化流程（`onInitialize()`）

```
1. config = ModConfig.load()
2. 触发 PromptLoader 创建默认 system_prompt.txt / review_prompt.txt
3. searchRouter = new SearchRouter(config, new WikiSearchProvider(...))
4. aiClient = new OpenAIClient(config)
5. reviewClient = new OpenAIClient(config, reviewEndpoint, reviewKey, reviewModel)
6. personaManager = new PersonaManager()   // 提取内置人设文件
7. chatLog = new ChatLog()
8. animation = new ThinkingAnimation()
9. contextBuilder = new PlayerContextBuilder()
10. cmdExec = new CommandExecutionService(this)
11. toolDispatcher = new ToolDispatcher(searchRouter, cmdExec, this)
12. chatHandler = new ChatHandler(this, chatLog, animation, contextBuilder, cmdExec, toolDispatcher)
13. cmdReg = new CommandRegistry(chatHandler, cmdExec)
14. 注册所有 /ai* 命令（15 个命令组）
15. 注册 ServerPlayConnectionEvents.DISCONNECT
16. 注册 ServerLifecycleEvents.SERVER_STARTED / SERVER_STOPPING
17. chatHandler.registerChatInterceptor()
18. startConfigWatcher()
19. 创建 PlayerBehaviorTracker
```

#### 3.1.2 SERVER_STARTED 事件处理

关键逻辑：
- 清空聊天记录（防止跨世界数据泄漏）
- **重新初始化 SearchRouter** —— 进入新世界时旧线程池已在退出时关闭
- **重新初始化 ToolDispatcher** 并同步 ChatHandler 中的引用
- 仅专用服务器启用 ChatReviewSystem（单人世界环境跳过）

#### 3.1.3 SERVER_STOPPING 事件处理

清理顺序（**严格有序**）：
1. `chatReviewSystem.stop()` — 先停止审查调度
2. `behaviorTracker.saveImmediate()` — 持久化玩家分数
3. `debugLogger.stop()` — 关闭调试文件（重置 enabled 标志）
4. `watcherScheduler.shutdownNow()` — 先关闭调度器
5. `configWatcher.close()` — 再关闭 WatchService（顺序不能反）
6. `searchRouter.shutdown()` — 关闭搜索线程池

#### 3.1.4 配置热重载

实现双重保障机制：

| 机制 | 说明 |
|---|---|
| WatchService + 2 秒防抖 | 文件变更时触发 `reloadConfig()` |
| 兜底轮询（5 秒） | 检查文件修改时间戳变化 |
| 服务器线程执行 | 通过 `server.execute()` 在主线程安全执行重载 |

`reloadConfig()` 完整刷新链：
- 重新加载 ModConfig
- 清除并重新加载 system/review prompt
- 重建 aiClient 和 reviewClient
- 关闭旧 SearchRouter 并重建
- 重建 ToolDispatcher，更新 ChatHandler 引用
- 刷新 persona 列表
- 校验激活的 persona 是否仍有效，无效则自动回退 `default`

---

### 3.2 API 模块：api/

#### 3.2.1 OpenAIClient.java

**文件**: [OpenAIClient.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/api/OpenAIClient.java)

AI API 客户端，封装 OpenAI 兼容协议的 HTTP 调用，支持工具调用（Function Calling）与思考模式（Reasoning）。

**两种构造方式**：
- `OpenAIClient(ModConfig config)` — 使用主聊天系统配置
- `OpenAIClient(ModConfig config, endpoint, apiKey, model)` — 用于审查系统独立模型

**核心数据类**：

| 类 | 说明 |
|---|---|
| `ToolCall` | AI 返回的工具调用请求（id, name, arguments） |
| `ChatMessage` | 聊天消息（role, content, toolCalls, toolCallId, reasoningContent） |
| `ChatSimpleResult` | 简单响应结果（content, reasoningContent） |

**方法**：

| 方法 | 用途 |
|---|---|
| `chat(messages, toolExecutor)` | 完整的多轮工具调用循环，最多 `maxToolCalls` 轮，5 分钟总超时 |
| `chatSimpleFull(messages)` | 无工具的单轮调用，用于行为审查 |
| `buildToolDefinitions()` | 构建 AI 可用的工具定义 JSON（10 个工具） |
| `addThinkingParams(body, level)` | 根据模型类型（DeepSeek 风格 / agnes 风格）注入思考参数 |
| `buildBaseRequestBody()` | 构建请求体，兼容模式只传 model/messages |

**工具定义清单**（AI 可调用的 10 个工具）：

| 工具名 | 参数 | 说明 |
|---|---|---|
| `search_knowledge_base` | query (string) | 搜索 Minecraft Wiki（最多 10 条） |
| `execute_minecraft_command` | command (string) | 执行单条 Minecraft 命令 |
| `execute_command_chain` | commands (string[]), interval (int, 0-10) | 命令链，最多 10 条 |
| `get_server_status` | 无 | 服务器状态（时间/天气/生物群系/TPS） |
| `get_game_rules` | 无 | 游戏规则状态 |
| `get_debug_info` | 无 | F3 调试信息 |
| `get_installed_mods` | 无 | 已安装 Mod 列表 |
| `get_player_effects` | 无 | 玩家药水效果 |
| `get_player_advancements` | 无 | 玩家进度 |
| `get_player_inventory` | 无 | 玩家物品栏 |

**`chat()` 方法超时保护**：
- 总时长 5 分钟超时后，强制追加一条 user 消息让 AI 收敛："本轮工具调用次数已用完。请基于已有信息给出最终回答"
- 最终调用不带 tools 定义
- `reasoning_content` 在每轮循环中自动捕获并传递

**sendAndParseMessage() 内部方法**：
- HTTP 连接超时 10 秒
- 单次请求超时 60 秒
- 支持 DeepSeek 兼容模式（Bearer token 认证）
- 错误处理：JSON error message 优先返回，否则返回 HTTP 状态码

#### 3.2.2 ApiResult.java

**文件**: [ApiResult.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/api/ApiResult.java)

轻量级结果封装 record：`ApiResult<T>(value, error, success)`。

```java
ApiResult.ok(value)    // 成功
ApiResult.err(message) // 失败
```

---

### 3.3 核心处理层：handler/

#### 3.3.1 ChatHandler.java

**文件**: [ChatHandler.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatHandler.java)

**职责**：AI 对话编排中心，负责聊天拦截、上下文构建、API 调用调度、历史管理、安全防护。

**关键字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `toolDispatcher` | `volatile ToolDispatcher` | **volatile** —— 退出再进世界后引用需要更新 |
| `aiExecutor` | `ExecutorService` | 4~8 线程的线程池，队列容量 32 |
| `history` | `ConcurrentHashMap<UUID, LinkedList<ChatMessage>>` | 每玩家的对话历史 |
| `lastAICallTime` | `ConcurrentMap<UUID, Long>` | 冷却时间戳 |
| `concurrentNonAdminCalls` | `AtomicInteger` | 非管理员并发计数器 |
| `chatEnabled` | `volatile boolean` | 全局对话开关 |

**线程池配置**：
```java
new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32),
    r -> { Thread t = new Thread(r, "MCAI-Worker"); t.setDaemon(true); return t; },
    (r, executor) -> LOGGER.warn("AI executor queue full, task rejected"));
```

**killAIThreads()**：销毁所有 AI 工作线程并重建线程池，同时重置并发计数器。被 `/aikill` 命令调用。

##### `handleAIQuery(player, query)` — 玩家调用链路

```
1. debugLogger.logQuery() — 记录查询
2. 限频检查（冷却 + 并发数）—— 管理员不受限
3. history.computeIfAbsent() — 获取玩家对话历史
4. animation.start() —— 发送"思考中"动画
5. 提交到 aiExecutor 线程池
6. 在线程池中：
   a. contextBuilder.build() —— 构建玩家上下文（版本/在线玩家/坐标/时间/HP...）
   b. 组装消息列表（按顺序）：
      [1] system prompt
      [2] persona prompt（非 default 时注入）
      [3] 最近聊天记录（chatLog.peek() + sanitizeChatLogForPrompt）
      [4] 处罚历史摘要（PenaltyHistory.getSummary()）
      [5] 历史对话（按 contextMaxChars 截断）
      [6] user message（玩家上下文 + sanitizeForPrompt）
   c. mod.getAiClient().chat(messages, toolDispatcher::dispatch) —— AI 调用
7. 结果回 server.execute() 主线程处理：
   a. animation.done()
   b. handleResponse() —— 安全检查（不允许 / 开头）
   c. broadcastSystemMessage() —— 广播回复
   d. 更新对话历史（trimHistoryByChars 自动截断）
8. 回滚并发计数器（finally 块）
9. RejectedExecutionException 时回滚冷却时间和计数器
```

##### `handleConsoleAIQuery(src, query)` — 控制台调用链路

类似但使用 `toolDispatcher.dispatchConsole()`（直接 OP 执行命令），玩家上下文替换为服务器概况。

##### `handleResponse(player, response)` — 响应安全处理

**安全策略**：AI 禁止在文本中输出以 `/` 开头的命令。检测到则：
- 广播警告（`mcai.chat.blocked_text_command`）
- 拒绝执行
- 记录 `[blocked]` 到聊天日志

##### `sanitizeForPrompt(playerName, message)` — Prompt Injection 防护

```java
// 1. 去除控制字符（保留 \n \t）
// 2. 截断到 500 字符
// 3. 用 [PLAYER:xxx] 结构化分隔符包裹
return "[PLAYER:" + playerName + "] " + clean;
```

##### `sanitizeChatLogForPrompt(chatLog)` — 聊天记录清洗

逐行过滤，去除所有控制字符（0x00-0x1F, 0x7F），保留可打印内容。

##### `registerChatInterceptor()` — 聊天事件监听

监听 3 个 Fabric 事件：

| 事件 | 作用 |
|---|---|
| `CHAT_MESSAGE` | 记录所有聊天到 ChatLog |
| `ALLOW_CHAT_MESSAGE` | 拦截以 `triggerPrefix` 开头的消息，转发给 AI 处理并阻断广播 |
| `GAME_MESSAGE` | 捕获系统消息（格式 `[来源] 内容`）到 ChatLog |

#### 3.3.2 CommandExecutionService.java

**文件**: [CommandExecutionService.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/CommandExecutionService.java)

**职责**：命令执行与审批管理子系统。这是整个项目中逻辑最复杂的类（约 830 行）。

**待审批状态管理**：

| 数据结构 | 说明 |
|---|---|
| `pendingById` | `ConcurrentMap<Long, PendingCommand>` — 按 ID 索引单命令 |
| `pendingChains` | `ConcurrentMap<Long, PendingChain>` — 按 ID 索引命令链 |
| `pendingByPlayer` | `ConcurrentMap<UUID, Set<Long>>` — 玩家 → 待审批 ID 集合 |
| `idGenerator` | `AtomicLong` — 全局唯一递增 ID（从 1 开始） |

**FORBIDDEN_COMMANDS**：AI 禁止调用 MCAI 内部命令（`ai`, `aiwiki`, `aiquery`, `aiaccept`, `aireject`, `aicancel`, `aiclear`, `aireload`, `aitest`, `aicheck`）。

**常量**：
- `MAX_CHAIN_COMMANDS = 10` — 命令链最大命令数
- `MAX_CHAIN_INTERVAL = 10` — 命令链最大间隔秒数

**PendingCommand 内部类**：
- `id` — 全局唯一
- `future` — `CompletableFuture<String>`，等待审批后 complete
- **requesterLevel / requesterPos / requesterRot** — 执行时使用**请求者的上下文**而非管理员上下文

**PendingChain 内部类**：
- `executing` — `volatile boolean`，标记是否正在执行
- `executionThread` — `volatile Thread`，用于中断执行
- `intervalSeconds` — 命令间等待秒数

**executeCommand(command, player)** 完整流程：

```
1. normalizeCommand(command) — 去除开头所有 /
2. 检查 FORBIDDEN_COMMANDS
3. needsApproval(normalizedCommand)?
   ├─ Yes → addPendingCommand → notifyAdminsPending → future.get(3分钟)
   │        超时 → removePending + 返回"审批超时"
   │        完成 → 返回执行结果
   └─ No → server.execute() 在主线程执行
            executeAsOp(OWNER 权限, 玩家上下文)
            广播执行结果
            future.get(10秒) 等待结果
```

**approveCommand/admin, id**：执行时使用**请求者的上下文**（requesterLevel, requesterPos, requesterRot），而非管理员的位置。

**needsApproval(command) 判断逻辑**：
```
1. 若命令 root 在 requireApprovalCommands 列表 → 需要审批
2. 若 strictMode=true：
   a. 检查 safeCommands 中带空格的条目（如 "data get"）→ startsWith 匹配
   b. 检查 safeCommands 中不带空格的条目 → root.equals 匹配
   c. 都不匹配 → 需要审批
3. 否则不需要审批
```

**玩家取消机制**：
- `/aicancel <id>` — 取消指定命令/链（只能取消自己的）
- `/aicancel` — 取消最近一条（按 createdAt 时间戳排序）
- `/aicancel all` — 取消全部
- 执行中命令链被取消时会 interrupt 执行线程

**cleanupPlayer(uuid)**：玩家断开时清理其所有待审批项。执行中命令链会 interrupt 执行线程。

**executeAsOp() 权限注入**：
```java
new CommandSourceStack(
    ...,
    LevelBasedPermissionSet.OWNER,  // OP 5 级权限
    ...
)
```

#### 3.3.3 ToolDispatcher.java

**文件**: [ToolDispatcher.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ToolDispatcher.java)

**职责**：AI 工具调用的分发路由器。约 310 行。

**两种分发模式**：

| 方法 | 调用者 | 命令执行方式 |
|---|---|---|
| `dispatch(toolCalls, player)` | 玩家触发的 AI 对话 | 走审批流程（cmdExec.executeCommand） |
| `dispatchConsole(toolCalls)` | 控制台触发的 AI 对话 | 直接 OP 执行（cmdExec.executeAsOp） |

**工具分发 switch-case**：

```java
switch (tc.name) {
    case "search_knowledge_base"    → searchProvider.search(query, 10)
    case "execute_minecraft_command" → cmdExec.executeCommand(normalizeCommand(command), player)
    case "execute_command_chain"   → cmdExec.submitChain(commands, interval, player)
    case "get_server_status"       → getServerStatus(player)
    case "get_game_rules"          → getGameRules(player)
    case "get_debug_info"          → getDebugInfo(player)
    case "get_installed_mods"      → FabricLoader.getAllMods()
    case "get_player_effects"      → player.getActiveEffects()
    case "get_player_advancements" → player.getAdvancements()
    case "get_player_inventory"    → player.getInventory()
    default → "未知工具: " + tc.name
}
```

**dispatchConsole 特殊处理**：控制台无法获取玩家相关信息（药水效果、进度、物品栏），返回友好提示。

**formatSearchResult(SearchResult result)**：将搜索结果格式化为 AI 可读文本，错误信息原样返回（如 `[wiki] 搜索失败: HTTP 503`）。

**参数解析**：使用 Gson 从 JSON 字符串中解析 `query` / `command` / `commands[]` / `interval` 等字段，解析失败返回空值并记录 warn 日志。

#### 3.3.4 CommandRegistry.java

**文件**: [CommandRegistry.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/CommandRegistry.java)

**职责**：注册所有玩家可调用的 `/ai*` 子命令。约 570 行。

**完整命令列表**：

| 命令 | 权限 | 说明 |
|---|---|---|
| `/ai <消息>` | 所有人 | AI 对话（等价于前缀触发） |
| `/aikb <query>` | 管理员/控制台 | 直接搜索 Wiki（返回 7 条，异步执行） |
| `/aiquery` | 管理员/控制台 | 查看待审批命令/命令链列表 |
| `/aiaccept <id>` | 管理员（仅玩家） | 批准命令/命令链（先尝试单命令，再尝试链） |
| `/aireject <id>` | 管理员（仅玩家） | 拒绝命令/命令链（先尝试单命令，再尝试链） |
| `/aicancel [id/all]` | 所有人 | 取消自己的待审批命令（支持三种模式） |
| `/aiclear` | 管理员/控制台 | 清除自己的对话历史 + 取消所有待审批 |
| `/aireload` | 管理员/控制台 | 热重载配置（等同于 `chatHandler.reloadAll()`） |
| `/airesetprompts` | 管理员/控制台 | 重置 system/review prompt 为默认 + 自动 reload |
| `/aikill` | 管理员/控制台 | 杀掉所有 AI 工作线程并重建 |
| `/aicontrol [chat/review] [on/off]` | 管理员/控制台 | 对话/审查开关（无参数显示当前状态） |
| `/aidebug [start/stop/show/list/clear]` | 管理员/控制台 | AI 交互调试 |
| `/aiscore` | 所有人 | 查看自己的行为分及阈值说明 |
| `/aitest <子命令>` | OP | 测试命令（score/penalty/reset/set/review/chatlog） |
| `/aipersona [list/set/view/reload/current]` | 管理员/控制台 | 人格管理 |

**审批 ID 自动补全**：`pendingIdSuggestions` 为 `/aiaccept` `/aireject` `/aicancel` 提供 Tab 补全，显示命令名/命令链信息（管理员看全部，普通玩家只看自己的）。

**`/aikb` 异步实现**：通过 `SearchRouter.getExecutor()` 线程池异步执行，避免阻塞主线程。

#### 3.3.5 ChatLog.java

**文件**: [ChatLog.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ChatLog.java)

轻量级聊天记录存储，最多 50 条，带脏标记缓存。

```
数据结构：LinkedList<String> log  (最多 50 条)
         volatile cachedPeek / dirty  (脏标记缓存)

add(name, message) → 同步写入，加时间戳，管理员消息加 [管理员] 前缀
peek()             → 返回完整聊天记录字符串（脏标记机制避免重复 join）
clear()            → 清空（进入新世界时调用 / 审查后调用）
size()             → 当前记录数
```

#### 3.3.6 ThinkingAnimation.java

**文件**: [ThinkingAnimation.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/ThinkingAnimation.java)

"思考中"动画发送器。使用 `ClientboundSetActionBarTextPacket` 在玩家 Action Bar 显示动态效果。

```
动画帧（每 400ms 切换）:
  ▌▌▌▌ → ▌▌▌▌ → ▌▌▌▌ → ▌▌▌▌
  颜色深浅交替（§7/§8）营造动态感

start(player, server) → 启动定时任务（保存 playerId → task 映射）
stop(playerId)        → 取消定时任务
done(player)         → 清除 Action Bar（发送空的 ActionBar 包）
```

#### 3.3.7 PlayerContextBuilder.java

**文件**: [PlayerContextBuilder.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/PlayerContextBuilder.java)

构建 AI 调用时的玩家上下文信息（纯字符串拼接）。

输出格式：
```
版本: <MC 版本> | 在线(x/y): [玩家列表] | <游戏时间> | <难度>
说话者: <玩家名> | 坐标: [x y z] | 朝向: <方向> | 维度: <namespace> | HP: x.x | 饱食度: x | 模式: <gamemode> | 等级: x | 进度: done/total
```

**`formatGameTime(ticks)`** — 将 Minecraft ticks 转换为可读时间：`第N天 H:MM AM/PM (tick=N)`。

#### 3.3.8 PersonaManager.java

**文件**: [PersonaManager.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/PersonaManager.java)

**职责**：AI 人格管理子系统，从 JSON 文件加载人格定义，支持多语言翻译。约 390 行。

**内置人格**：

| 类别 | 人格 ID | 名称 |
|---|---|---|
| 通用人格 | `tsundere`, `pirate`, `chuuni`, `gentle` | 傲娇、海盗、中二病、温柔 |
| 游戏人格 | `villager`, `piglin`, `ender_dragon`, `creeper` | 村民、猪灵、末影龙、苦力怕 |

**DEFAULT_PERSONA**：特殊人格 record，content 为 null（i18n 类型），注入时跳过。

**数据结构**：
- `PersonaRecord` — 人格记录（id, name, summary, content, i18n, translations）
- `PersonaTranslation` — 单语言翻译条目（name, summary, content）

**人格 JSON 格式**：
```json
{
  "id": "tsundere",
  "name": "傲娇",
  "summary": "表面冷漠实则关心",
  "content": "你是一个傲娇角色...",
  "translations": {
    "en_us": {
      "name": "Tsundere",
      "summary": "Cold outside, warm inside",
      "content": "You are a tsundere character..."
    }
  }
}
```

**加载流程**：
1. 构造时从 JAR 资源目录提取内置 JSON 到 `config/mcai/personas/`（不覆盖已有文件）
2. 清理旧版 `.txt` 人格文件（迁移）
3. `refreshPersonaList()` 扫描目录下所有 `.json` 文件（按文件名字母序排序）
4. 校验：必填字段 `id`, `name`, `content`；id 包含 `/` `\` `..` 拒绝加载
5. 重复 ID（按文件名字母序先到先得），后续跳过并记录警告

**`refreshPersonaList()` 输出统计**：
- 总文件数、成功加载数、失败数
- 重复 ID 列表（`id (in filename.json)`）
- 失败文件列表

**多语言解析**：
- `resolveEffectiveLanguage()` — 优先使用 config 的 `personaLanguage`，否则客户端跟随游戏语言（反射调用 `Minecraft.getInstance().getLanguageManager().getSelected()`）
- 回退链：翻译字段 → 顶层默认字段

**`getPersonaContent(id)`**：返回指定人格在生效语言下的 content，`default` 或不存在返回 null。

#### 3.3.9 AIDebugLogger.java

**文件**: [AIDebugLogger.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/handler/AIDebugLogger.java)

AI 交互全流程调试记录器。写入文件 + 内存会话追踪。

**文件输出**：`config/mcai/debug/ai_debug_YYYY-MM-DD_HH-mm-ss.log`

**内存会话**：最多保留 5 个 `DebugSession`（sessionId, playerName, query, thinking, toolCalls[], response）。

**API**：

| 方法 | 说明 |
|---|---|
| `start()` / `stop()` | 开启/关闭调试日志；stop 重置 enabled 标志 + 关闭 BufferedWriter |
| `startSession(name, query)` / `endSession()` | 标记一轮 AI 对话 |
| `logQuery(pname, query)` | 记录玩家查询 |
| `logThinking(reasoning)` | 记录 AI 思考过程 |
| `logToolCall(name, args)` / `logToolResult(name, result)` | 记录工具调用 |
| `logAIResponse(content)` | 记录最终回复 |
| `logError(context, error)` | 记录错误 |
| `logInfo(msg)` | 记录信息 |
| `getLastSession()` / `getSession(id)` | 查询单个历史会话 |
| `getLastSessions(n)` | 查询最近 n 个会话 |
| `getCurrentLogFile()` | 当前日志文件路径 |

**DebugSession 内部类**：
- `List<ToolCallRecord> toolCalls` — 工具调用记录（name, arguments, result）

---

### 3.4 行为审查系统：behavior/

#### 3.4.1 ChatReviewSystem.java

**文件**: [ChatReviewSystem.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/ChatReviewSystem.java)

**职责**：审查子系统的调度与编排器。仅专用服务器上启用。约 147 行。

**调度逻辑**：
```java
// 默认 30 分钟周期
reviewScheduler.scheduleAtFixedRate(this::runReview, interval, interval, TimeUnit.MINUTES);
```

**runReview() 完整流程**：
```
1. reviewEnabled 开关检查
2. reviewInProgress.compareAndSet(false, true) —— 防重复执行
3. reviewEngine.run() —— 执行 AI 审查（返回 Component 状态消息）
4. 捕获异常 → 设置 lastReviewStatus
5. 最终 reviewInProgress.set(false)
```

**reloadConfig(ModConfig)**：热重载配置引用 + 间隔变化时重新调度。

**triggerManualReview(notifier)**：手动触发审查，可指定通知者。

**executeApprovedAction(item)**：在 `server.execute()` 主线程执行踢出操作，同时记录 PenaltyEvent。

**构造链**：
```java
this.penaltyHistory = new PenaltyHistory(config);
this.approvalQueue = new AdminApprovalQueue(reviewScheduler, this::executeApprovedAction);
this.reviewEngine = new ReviewEngine(mod, tracker, penaltyHistory, approvalQueue);
this.cmdReg = new ReviewCommandRegistry(this, approvalQueue, reviewEngine);
```

#### 3.4.2 ReviewEngine.java

**文件**: [ReviewEngine.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/ReviewEngine.java)

**职责**：AI 审查引擎 —— 读取聊天记录、调用审查模型、解析违规、执行处罚。约 167 行。

**run() 方法核心逻辑**：

```
1. penaltyHistory.advanceCycle() —— 进入新审查周期
2. chatLog.peek() —— 获取聊天记录快照，空则返回
3. 构建 roster：
   - 在线玩家列表（标记管理员）
   - === CHAT LOG START/END === 包裹的聊天记录
   - penaltyHistory.getJson() 历史处罚
4. reviewClient.chatSimpleFull(messages) —— AI 审查调用（单轮，无工具）
5. saveReviewFiles() —— 保存原始响应和思考过程到磁盘（>100KB 自动分片）
6. parseViolations(response) —— 解析违规 JSON
7. 若 AI 返回非 JSON，自动 retry 一次（加严格提示）
8. 无违规 → chatLog.clear() + recoverScores() + penaltyHistory.save/purgeOld()
9. 有违规：
   a. 遍历每个 PlayerViolation
   b. 跳过不在线玩家和管理员
   c. 检查 cumulative severity（单玩家多违规累积不超过红牌阈值）
   d. tracker.addScore(uuid, severity) —— 扣分
   e. 根据分数阈值分级执行：
      - newScore <= 红牌阈值 → AdminApprovalQueue.addItem()（超时自动批准）
      - newScore <= 黄牌阈值 → 广播警告
      - 否则 → 仅记录扣分
   f. chatLog.clear() + recoverScores() + penaltyHistory.save/purgeOld()
```

**PlayerViolation JSON 格式**：
```json
{
  "violations": [
    {
      "player_name": "Notch",
      "description": "在聊天中发送垃圾信息",
      "severity": -20,
      "suggested_action": "warn"
    }
  ]
}
```

**parseViolations 严格校验**：
- 自动剥离 ```json ... ``` 代码块包裹
- player_name 正则 `[a-zA-Z0-9_]{3,16}`（合法 Minecraft 用户名）
- severity 强制分档：-10(轻微) / -20(中度) / -30(严重)
- suggested_action 严格白名单：none / warn / kick

**recoverScores()**：仅对非管理员在线玩家执行，调用 `tracker.tryRecover()`。

**saveReviewFiles()**：保存 `review_last_response.txt` 和 `review_last_reasoning.txt`，超过 100KB 自动分片为 `.1`, `.2`...

#### 3.4.3 PlayerBehaviorTracker.java

**文件**: [PlayerBehaviorTracker.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/PlayerBehaviorTracker.java)

**职责**：玩家行为分数持久化存储。

**数据文件**：`config/mcai/scores.json`

**JSON 格式**：
```json
{
  "scores": {
    "uuid-string": -30
  },
  "lastRecoveryTime": {
    "uuid-string": 1700000000000
  }
}
```

**核心操作**：

| 方法 | 说明 |
|---|---|
| `getScore(uuid)` | 查询分数（默认 0） |
| `addScore(uuid, delta)` | 加/扣分（使用 `ConcurrentHashMap.compute` 原子操作），自动持久化 |
| `setScore(uuid, score)` | 直接设置分数 |
| `resetScore(uuid)` | 重置为 0 + 清除恢复时间 |
| `tryRecover(uuid)` | 检查是否到恢复周期，恢复分数（cap at 0，不会从负数恢复到正数） |
| `save()` / `saveImmediate()` | 持久化到磁盘 |

**并发性**：`ConcurrentHashMap<UUID, Integer>` scores + `ConcurrentHashMap<UUID, Long>` lastRecoveryTime。

**tryRecover() 恢复逻辑**：
```java
if (current >= 0) return; // 分数 >= 0 无需恢复
long intervalMs = config.getReviewIntervalMinutes() * 60_000L;
if (now - last >= intervalMs) {
    scores.compute(uuid, (id, existing) ->
        Math.min(existing + recoverAmount, 0)); // cap at 0
}
```

#### 3.4.4 AdminApprovalQueue.java

**文件**: [AdminApprovalQueue.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/AdminApprovalQueue.java)

**职责**：红牌踢出的管理员审批队列。约 110 行。

**ApprovalItem 数据结构**：
- `id` — 自增 int（AtomicInteger nextId）
- `targetPlayerId`, `targetPlayerName` — 被踢出目标
- `action` — 目前固定 `"kick"`
- `reason` — 踢出原因（来自 AI 审查 description）
- `resolved` — `volatile boolean`，防止重复处理

**审批机制**：
- `addItem()` —— 创建审批条目，`scheduler.schedule()` 超时自动批准（调用 `onApproved` Consumer）
- `tryApprove(id)` / `tryReject(id)` —— 管理员手动批准/拒绝
- `tryResolve(item, approved)` —— synchronized(item) 确保每条目只 resolve 一次
- resolve 后取消对应的 timeout ScheduledFuture

**与 CommandExecutionService 审批的区别**：

| 维度 | AdminApprovalQueue | CommandExecutionService |
|---|---|---|
| 触发来源 | AI 审查系统 | AI 工具调用 |
| 审批内容 | 踢出玩家 | 执行命令 |
| 审批者 | 管理员 | 管理员 |
| 超时行为 | **自动批准执行**（10 分钟默认） | 超时取消（3 分钟） |
| ID 类型 | int（AtomicInteger） | long（AtomicLong） |
| 存储 | ConcurrentMap<Integer, ApprovalItem> | pendingById + pendingChains + pendingByPlayer |
| 执行方式 | Consumer<ApprovalItem> 回调 | future.complete(result) |

#### 3.4.5 PenaltyHistory.java

**文件**: [PenaltyHistory.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/PenaltyHistory.java)

**职责**：处罚历史记录，供 AI 和管理员查看。

**数据文件**：`config/mcai/penalties.json`

**advanceCycle()**：每次审查前进一个周期计数器，用于处罚事件的 cycle 字段。

**getSummary()** — 给 AI 的中文文本摘要（注入到下一轮 system prompt）：
```
===== 最近行为审查处罚记录 =====
[5分钟前] Notch | 垃圾信息 | 扣20分 | 当前-30 | 黄牌
[30分钟前] Steve | 骂人 | 扣30分 | 当前-60 | 红牌(待审批)(审批ID:1)
```

**getJson()** — 给 AI 的 JSON 格式（供审查引擎避免重复处罚）。

**purgeOld()** — 清理超过 `maxReviewCycles`（默认 4 个周期）的旧记录。

**addEvent(PenaltyEvent)** — 添加处罚事件，自动触发 save()。

**reloadConfig(ModConfig)** — 热重载配置引用。

#### 3.4.6 ReviewCommandRegistry.java

**文件**: [ReviewCommandRegistry.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/ReviewCommandRegistry.java)

`/aireview` 命令注册器：

| 子命令 | 权限 | 说明 |
|---|---|---|
| `/aireview` | 所有人 | 帮助信息 |
| `/aireview start` | 管理员/控制台 | 手动触发审查 |
| `/aireview approve <id>` | 管理员（仅玩家） | 批准红牌踢出 |
| `/aireview reject <id>` | 管理员（仅玩家） | 拒绝红牌踢出 |
| `/aireview last` | 管理员/控制台 | 查看上次审查的 AI 输出 |
| `/aireview last reasoning` | 管理员/控制台 | 查看上次审查的 AI 思考过程 |

**审批 ID 自动补全**：`getUnresolvedIds()` 返回所有未解决的审批条目。

#### 3.4.7 PenaltyEvent.java

**文件**: [PenaltyEvent.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/PenaltyEvent.java)

处罚事件记录类：
```java
public enum PenaltyAction {
    SCORE_ONLY,    // 仅扣分
    WARN,          // 黄牌警告
    KICK,          // 红牌（审批中）
    KICK_EXECUTED  // 已踢出
}
```

**字段**：playerName, reason, severity, scoreAfter, action, approvalId, cycle, timestamp。

#### 3.4.8 PlayerViolation.java

**文件**: [PlayerViolation.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/behavior/PlayerViolation.java)

AI 审查解析后的违规记录：`(playerName, description, severity, suggestedAction)`。

---

### 3.5 配置模块：config/

#### 3.5.1 ModConfig.java

**文件**: [ModConfig.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/config/ModConfig.java)

**职责**：JSON 配置的加载、持久化、校验。约 553 行。

**配置文件路径**：`config/mcai/config.json`

**Gson 序列化策略**：使用 `GSON_SAVE` 排除 transient 提示词字段（`cachedSystemPrompt`, `cachedReviewPrompt`, `defaultSystemPrompt*`, `defaultReviewPrompt*`），避免提示词文件内容混入 config.json。

**配置项分组**：

##### AI 连接配置
| 字段 | 默认值 | 说明 |
|---|---|---|
| `apiEndpoint` | `https://api.deepseek.com` | AI API 端点 |
| `apiKey` | 空 | API Key |
| `model` | `deepseek-v4-flash` | 模型名 |
| `compatibilityMode` | false | 兼容模式（只传基础字段，适配 LM Studio 等） |

##### AI 参数
| 字段 | 默认值 | 范围 | 说明 |
|---|---|---|---|
| `triggerPrefix` | `!ai` | - | 聊天触发前缀 |
| `maxTokens` | 2048 | 256-8192 | 最大输出 token |
| `temperature` | 0.75 | 0.0-2.0 | 随机性 |
| `thinkingLevel` | 1 | 0-3 | 思考深度 |
| `contextMaxChars` | 20000 | 2000-100000 | 历史对话最大字符数 |
| `maxToolCalls` | 15 | 1-50 | 单次对话最大工具调用轮数 |

##### 行为控制
| 字段 | 默认值 | 说明 |
|---|---|---|
| `enableChatInterception` | true | 聊天拦截开关 |
| `enableCommandExecution` | true | 命令执行开关 |
| `strictMode` | true | 严格审批模式 |
| `requireApprovalCommands` | op, deop, ban, kick... | 需要审批的命令白名单 |
| `safeCommands` | locate, help, list... | 免审批的安全命令（只读） |
| `aiCooldownSeconds` | 60 | 非管理员冷却时间（秒） |
| `aiMaxConcurrent` | 3 | 非管理员最大并发数 |

##### 审查系统
| 字段 | 默认值 | 说明 |
|---|---|---|
| `enableAutoReview` | true | 自动审查开关 |
| `reviewIntervalMinutes` | 30 | 审查周期 |
| `yellowCardThreshold` | -30 | 黄牌分数阈值 |
| `redCardThreshold` | -60 | 红牌分数阈值（必须 < 黄牌阈值） |
| `scoreRecoveryPerInterval` | 5 | 每周期分数恢复量 |
| `approvalTimeoutMinutes` | 10 | 红牌审批超时 |
| `reviewApiEndpoint` | 空 → 跟随聊天系统 | 审查系统独立端点 |
| `reviewApiKey` | 空 → 跟随聊天系统 | 审查系统独立 Key |
| `reviewModel` | 空 → 跟随聊天系统 | 审查系统独立模型 |

##### 知识库
| 字段 | 默认值 | 说明 |
|---|---|---|
| `wikiLanguage` | `zh_cn` | Wiki 搜索语言（zh_cn / en_us） |

##### 人格系统
| 字段 | 默认值 | 说明 |
|---|---|---|
| `activePersona` | `default` | 当前激活的人格 |
| `personaLanguage` | 空 | 人格语言（空 = 跟随文件默认） |

##### 提示词
| 字段 | 默认值 | 说明 |
|---|---|---|
| `promptLanguage` | `zh_cn` | 内置提示词语言（zh_cn / en_us） |
| `systemPromptPath` | 空 | 自定义 system prompt 文件路径 |
| `reviewPromptPath` | 空 | 自定义 review prompt 文件路径 |

**校验规则（`validate()`）**：
- URL 格式检查、HTTP 警告、SSRF 风险警告
- 数值边界强制修正（temperature 0-2, maxTokens 256-8192 等）
- 列表默认值填充（requireApprovalCommands, safeCommands）
- 提示词语言必须是 zh_cn 或 en_us
- 红牌阈值必须严格小于黄牌阈值（`redCardThreshold = yellowCardThreshold - 30`）
- `activePersona` 和 `personaLanguage` null 回退

**提示词加载策略**：
- 优先使用外部文件（`systemPromptPath` 非空时）
- 否则使用文件名 `system_prompt.txt` / `review_prompt.txt`
- 文件不存在时自动写入内置默认内容
- `cachedSystemPrompt` / `cachedReviewPrompt` transient 缓存，reloadConfig 时 clearPromptCache()

**安全警告**：
- API 端点使用 HTTP 时警告（明文传输）
- API 端点指向 localhost/127.0.0.1/169.254.169.254 等时警告 SSRF 风险

**getReviewApiEndpoint() / getReviewApiKey() / getReviewModel()**：Effective getter，未配置时自动回退到聊天系统对应的配置。

**hasSeparateReviewModel()**：仅检查 reviewModel 是否非空，用于日志提示。

#### 3.5.2 PromptLoader.java

**文件**: [PromptLoader.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/config/PromptLoader.java)

极简工具类，负责提示词文件的加载与重置。

```java
load(fileName, defaultContent)
  → 若文件存在且非空，返回文件内容
  → 否则写入默认内容到文件（供用户后续编辑），返回默认内容

reset(fileName, defaultContent)
  → 强制覆盖为默认内容（/airesetprompts 命令调用）
```

文件位于 `config/mcai/{fileName}`（相对 FabricLoader configDir）。

---

### 3.6 知识库模块：kb/

#### 3.6.1 SearchProvider.java

**文件**: [SearchProvider.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/kb/SearchProvider.java)

搜索提供器抽象接口。所有搜索源（Wiki 在线、本地 JSON）实现此接口。

```java
public interface SearchProvider {
    String name();                    // 提供器名称
    boolean isAvailable();            // 当前是否可用
    SearchResult search(String query, int maxResults);
    default String read(String title); // 读取完整条目（可选）
}
```

#### 3.6.2 SearchResult.java

**文件**: [SearchResult.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/kb/SearchResult.java)

统一搜索结果格式：
```java
public class SearchResult {
    String provider;    // "wiki" 或 "local"
    List<Item> items;   // 搜索结果列表
    boolean offline;    // 是否离线搜索
    String error;       // 错误信息（非空表示搜索失败）
    
    public static class Item {
        String title, summary, url;
        double score;   // 相关度评分（1.0 起始，每排名 -0.1）
    }
}
```

工厂方法：`SearchResult.error(provider, error)`, `SearchResult.empty(provider, offline)`。

#### 3.6.3 SearchRouter.java

**文件**: [SearchRouter.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/kb/SearchRouter.java)

**职责**：搜索路由器，异步调用 WikiSearchProvider，带超时保护。约 89 行。

```
search(query, maxResults)
  → executor.submit(wikiProvider.search)
  → future.get(8秒超时)  // WIKI_TIMEOUT_MS = 8000
  → 返回 Wiki 结果（错误不丢弃）或空结果
```

**线程池**：`CachedThreadPool`，每次搜索创建新线程，daemon 模式，线程名 `MCAI-SearchRouter`。

**⚠️ 重要**：线程池在 `SERVER_STOPPING` 时 `shutdownNow()`，进入新世界时必须重建。`MCAIMod.reloadConfig()` 和 `SERVER_STARTED` 事件中都执行了重建。

**read(title)**：单条目读取，同样走线程池 + 8 秒超时。

#### 3.6.4 WikiSearchProvider.java

**文件**: [WikiSearchProvider.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/kb/WikiSearchProvider.java)

通过 MediaWiki API 实现 Minecraft Wiki 在线搜索。约 183 行。

**API 端点**：
- 中文：`https://zh.minecraft.wiki/api.php`
- 英文：`https://minecraft.wiki/api.php`

**两步搜索流程**：
1. `action=query&list=search&srsearch=...&srlimit=N` — 全文搜索，获取标题和摘要片段
2. `action=query&prop=extracts&exintro=true&explaintext=true&titles=...` — 获取前 3 个条目的完整简介（enrich 结果）

**HTTP User-Agent**：`MCAI-Minecraft-Mod/1.5.1 (https://github.com/lll114514lll1919810lll/mcai_mod)`

**HTTP 超时**：连接 5 秒，请求 8 秒。

**文本清洗**：
```java
// 1. strip HTML tag: <[^>]+>
// 2. 去除 Wiki 标记: [[]]{}|
// 3. HTML entity decode: &quot; &amp; &lt; &gt;
// 4. 压缩空白: \s+ → 空格
```

**fullTextSearch** 中的 score 算法：`1.0 - (items.size() * 0.1)` —— 按排名递减。

#### 3.6.5 KnowledgeBase.java

**文件**: [KnowledgeBase.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/kb/KnowledgeBase.java)

本地 JSON 知识库实现（**已弃用，保留兼容**）。从 `config/mcai/kb/*.json` 加载。

**搜索算法**：加权评分 + CJK Bigram 分词。

**Entry 数据结构**：
```java
public record Entry(String title, List<String> keywords, String summary, String content) {}
```

**加载限制**：
- 最大条目数：50000
- 单文件最大：50MB
- 标题去重（大小写不敏感）

**⚠️ 注意**：当前 SearchRouter 只使用 WikiSearchProvider，KnowledgeBase 未在主流程中使用。保留此实现供未来扩展或离线环境使用。

---

### 3.7 客户端模块：client/

#### 3.7.1 ModMenuIntegration.java

**文件**: [ModMenuIntegration.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/client/ModMenuIntegration.java)

Mod Menu API 实现，提供配置屏幕工厂。标注 `@Environment(EnvType.CLIENT)` —— 仅客户端加载。

```java
implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new MCAIConfigScreen(parent);
    }
}
```

#### 3.7.2 MCAIConfigScreen.java

**文件**: [MCAIConfigScreen.java](file:///c:/Users/Lecoo/mc/src/main/java/com/example/mcai/client/config/MCAIConfigScreen.java)

**职责**：客户端 GUI 配置界面，直接读写 `config/mcai/config.json` 原始 JSON。

**UI 分组**：
1. **AI 连接** — endpoint, apiKey, model
2. **AI 参数** — triggerPrefix, maxTokens, temperature, context, thinkingLevel, maxToolCalls, 兼容模式, Wiki 语言
3. **行为控制** — 聊天拦截, 命令执行, 严格模式, 非管理员冷却/并发
4. **审查系统** — 自动审查, 审查参数（周期/阈值/恢复量/超时）, 独立模型配置
5. **人格系统** — 当前人格, 人格语言
6. **提示词** — system/review prompt 文件路径, 提示词语言

**保存后自动执行 `/aireload`** — 通过 `minecraft.player.connection.sendCommand("aireload")` 通知服务器重载。

**MC 26.3 适配**：
- `canInterruptWithAnotherScreen() → true` — 允许被其他屏幕覆盖
- `Minecraft.setScreenAndShow()` — 替代已移除的 `Minecraft.setScreen()`

---

## 4. 核心类与函数说明

### 4.1 关键数据流向

#### 4.1.1 AI 对话请求 → 响应全链路

```
玩家发送 "!ai 给我一把钻石剑"
    │
    ▼
ChatHandler.registerChatInterceptor()
    ServerMessageEvents.ALLOW_CHAT_MESSAGE 拦截
    ├─ 检查 chatEnabled 开关
    ├─ checkPlayerCanUseAI() —— 限频检查（冷却 + 并发数）
    ├─ broadcastSystemMessage() —— 广播 AI 请求
    └─ chatLog.add() —— 记录到聊天日志
    │
    ▼
ChatHandler.handleAIQuery(player, "给我一把钻石剑")
    ├─ debugLogger.logQuery()
    ├─ 非管理员限频检查 + lastAICallTime/并发计数器更新
    ├─ history.computeIfAbsent() —— 获取玩家对话历史
    ├─ animation.start() —— 发送"思考中"动画
    └─ aiExecutor.submit(() → ...)
        │
        ▼
    在线程池中:
        ├─ contextBuilder.build() —— 构建玩家上下文
        │   (版本/在线玩家/坐标/朝向/维度/HP/饱食度/模式/等级/进度)
        ├─ 组装 messages 列表（按顺序）：
        │   [1] system prompt (ModConfig.getSystemPrompt)
        │   [2] persona prompt (PersonaManager.getPersonaContent, 非 null 时注入)
        │   [3] 最近聊天记录 (chatLog.peek() + sanitizeChatLogForPrompt)
        │   [4] 处罚历史摘要 (PenaltyHistory.getSummary)
        │   [5] 历史对话 (按 contextMaxChars 截断)
        │   [6] user message (context + sanitizeForPrompt)
        │
        ▼
    OpenAIClient.chat(messages, toolExecutor)
        ├─ buildBaseRequestBody() —— 构建请求体
        │   (model/max_tokens/temperature/thinking/... 兼容模式只传 model)
        ├─ HTTP POST → AI API (/chat/completions)
        ├─ 若返回 tool_calls:
        │   ├─ parseToolCalls()
        │   ├─ 只保留 tool_call_request 消息 + reasoning_content
        │   ├─ toolExecutor.apply(toolCalls) → ToolDispatcher.dispatch()
        │   │   ├─ "execute_minecraft_command" → cmdExec.executeCommand()
        │   │   ├─ "search_knowledge_base" → searchRouter.search()
        │   │   └─ ...其他工具
        │   ├─ 工具结果（最多10条）追加到 messages
        │   └─ continue 循环
        ├─ 达到 maxToolCalls → 追加收敛消息 → 最终调用不带 tools
        └─ 返回最终文本响应 ApiResult.ok(content)
        │
        ▼
    server.execute() 主线程:
        ├─ animation.done()
        ├─ handleResponse() —— 安全检查（AI 回复不能以 / 开头）
        ├─ broadcastSystemMessage() —— 广播 AI 回复
        ├─ chatLog.add() —— 记录 AI 回复
        └─ 更新对话历史（trimHistoryByChars 自动截断）
```

#### 4.1.2 命令审批流程

```
AI: execute_minecraft_command("op Notch")
    │
    ▼
ToolDispatcher.dispatch() → CommandExecutionService.executeCommand()
    ├─ normalizeCommand() → "op Notch"
    ├─ FORBIDDEN_COMMANDS 检查 → 通过
    │
    ▼
needsApproval("op Notch")
    ├─ root = "op"
    ├─ requireApprovalCommands.contains("op") → true
    └─ return true
    │
    ▼
addPendingCommand(player, "op Notch")
    ├─ id = idGenerator.getAndIncrement()
    ├─ 创建 PendingCommand（含 requester 上下文）
    ├─ pendingById.put(id, pending)
    ├─ pendingByPlayer.computeIfAbsent().add(id)
    └─ return pending
    │
    ▼
notifyAdminsPending(pending, server)
    ├─ broadcastSystemMessage() —— 广播审批请求
    ├─ 记录聊天日志
    ├─ 给请求者发送 [取消] 点击按钮
    └─ 给所有管理员发送 [批准] [拒绝] [取消] 点击按钮
    │
    ▼
future.get(3, TimeUnit.MINUTES) 等待
    │
    ├─ 3分钟内管理员 /aiaccept <id>
    │   └─ approveCommand() → executeAsOp(OWNER 权限, **请求者上下文**)
    │       └─ future.complete(result)
    │
    ├─ 管理员 /aireject <id>
    │   └─ rejectCommand() → future.complete("[Approval rejected] ...")
    │
    ├─ 玩家 /aicancel <id>
    │   └─ cancelByPlayer() → future.complete("[玩家取消] ...")
    │
    ├─ 玩家断开
    │   └─ cleanupPlayer() → future.complete("[Approval cancelled] ...")
    │
    └─ 超时
        └─ future.get() 抛 TimeoutException → 返回 "审批超时"
    │
    ▼
原始请求线程从 future.get() 返回 → 继续 AI 对话循环
```

#### 4.1.3 行为审查周期

```
ChatReviewSystem (MCAI-Review 线程)
    scheduleAtFixedRate(30min)
    │
    ▼
runReview()
    ├─ reviewEnabled 检查
    ├─ reviewInProgress.compareAndSet(false, true)
    │
    ▼
ReviewEngine.run()
    ├─ penaltyHistory.advanceCycle() —— 周期 +1
    ├─ chatLog.peek() —— 获取聊天记录快照，空则跳过
    ├─ 构建 roster（在线玩家 + 标记管理员 + 聊天记录 + 历史处罚 JSON）
    ├─ reviewClient.chatSimpleFull(messages) —— 单轮调用（无工具）
    │   ├─ 审查模型独立配置（空则跟随聊天系统）
    │   └─ 返回 content + reasoningContent
    ├─ saveReviewFiles() —— 保存原始响应 + 思考过程（>100KB 分片）
    │
    ├─ AI 返回 {"violations": [...]}
    │   ├─ parseViolations() ——
    │   │   a. 剥离 ```json ``` 代码块包裹
    │   │   b. player_name 正则 [a-zA-Z0-9_]{3,16} 校验
    │   │   c. severity 强制分档：-10/-20/-30
    │   │   d. suggested_action 白名单：none/warn/kick
    │   │
    │   ├─ AI 返回非 JSON → retry 一次（加严格提示）
    │   │
    │   ├─ 无违规：
    │   │   ├─ chatLog.clear() + recoverScores()
    │   │   ├─ penaltyHistory.save() + purgeOld()
    │   │   └─ 返回 "无违规" 状态消息
    │   │
    │   └─ 有违规：
    │       ├─ 遍历 PlayerViolation
    │       │   ├─ 跳过不在线玩家和管理员
    │       │   ├─ 累积同一玩家 severity（不超过红牌阈值）
    │       │   └─ tracker.addScore(uuid, severity)
    │       │
    │       ├─ newScore ≤ 红牌阈值
    │       │   └─ approvalQueue.addItem() → AdminApprovalQueue
    │       │       ├─ 超时自动批准（默认 10 分钟）
    │       │       ├─ /aireview approve <id> 手动批准
    │       │       └─ /aireview reject <id> 手动拒绝
    │       │
    │       ├─ newScore ≤ 黄牌阈值
    │       │   └─ 广播警告 + PenaltyHistory 记录
    │       │
    │       ├─ chatLog.clear() + recoverScores()
    │       └─ penaltyHistory.save() + purgeOld()
```

#### 4.1.4 玩家取消命令流程

```
玩家: /aicancel              (无参数 → 取消最近一条)
  └─ CommandRegistry.createCancelCommand()
      └─ cmdExec.cancelLatestByPlayer(player)
          ├─ 从 pendingByPlayer.get(uuid) 获取该玩家所有待审批 ID
          ├─ 遍历 pendingById / pendingChains 找 createdAt 最大的
          └─ cancelByPlayer(player, id)

玩家: /aicancel 123          (指定 ID)
  └─ cmdExec.cancelByPlayer(player, 123)
      ├─ pendingById.get(id) → 单命令
      │   ├─ 校验 requesterId.equals(player.getUUID())
      │   ├─ pendingById.remove(id) + pendingByPlayer cleanup
      │   └─ future.complete("[玩家取消] ...")
      │
      └─ pendingChains.get(id) → 命令链
          ├─ 同上权限 + 中断执行线程 chain.executionThread.interrupt()
          └─ future.complete("[玩家取消] ...")

玩家: /aicancel all           (取消全部)
  └─ cmdExec.cancelAllByPlayer(player)
      └─ 遍历该玩家所有待审批 ID，逐个清理并 future.complete()
```

#### 4.1.5 配置热重载链路

```
启动阶段: MCAIMod.startConfigWatcher()
  ├─ WatchService 监听 config/mcai/ 目录
  ├─ 2 秒防抖 + 500ms 延迟 doReload
  └─ 兜底：每 5 秒轮询文件修改时间戳

触发: config.json 被外部编辑器保存
  ├─ WatchService.take() → 检测到 ENTRY_MODIFY
  ├─ 500ms 后 schedule(doReload)
  ├─ 2 秒防抖判断 doReload
  └─ server.execute() → 主线程安全执行 reloadConfig()

reloadConfig() 执行链:
  1. config = ModConfig.load()          — 重新解析 config.json + validate()
  2. config.clearPromptCache()           — 清除 cachedSystem/cachedReviewPrompt
  3. config.getSystemPrompt()            — 触发 PromptLoader.load (可能重建文件)
  4. config.getReviewPrompt()
  5. aiClient = new OpenAIClient(config) — 重建聊天系统 AI 客户端
  6. reviewClient = new OpenAIClient(config, reviewEndpoint, reviewKey, reviewModel)
  7. searchRouter.shutdown() + 重建      — 关闭旧线程池，创建新 CachedThreadPool
  8. toolDispatcher = new ToolDispatcher(searchRouter, cmdExec, this)
  9. chatHandler.setToolDispatcher(td)    — 更新 volatile 引用（避免 ChatHandler 持有旧 ToolDispatcher）
  10. chatReviewSystem.reloadConfig()    — 间隔变化时重建调度器
  11. personaManager.refreshPersonaList() — 重新扫描 personas/*.json
  12. 校验 activePersona 是否仍有效
      └─ 无效 → config.setActivePersona("default") + config.save()
```

---

## 5. 依赖关系图

### 5.1 模块依赖矩阵

```
                    api.OpenAIClient
                    api.ApiResult
                        ▲
                        │
    ┌───────────────────┼────────────────────┐
    │                   │                    │
handler.ChatHandler  behavior.ReviewEngine  handler.ChatHandler.handleConsoleAIQuery
    │                   │                    │
    │            handler.ToolDispatcher ◄─────┘
    │                   │
    │       ┌──────────┼──────────┐
    │       ▼          ▼          ▼
    │  CommandExec  kb.SearchRouter  (其他 8 个工具)
    │  utionService
    │       │
    │       ▼
    │  behavior.AdminApprovalQueue (审批独立于此)
    │
    ├─ behavior.ChatReviewSystem ──► behavior.ReviewEngine
    │         │                          │
    │         ├─ behavior.PlayerBehaviorTracker
    │         ├─ behavior.PenaltyHistory
    │         └─ behavior.AdminApprovalQueue
    │
    └─ config.ModConfig ◄────────── 所有模块
          │
          ▼
    config.PromptLoader
```

### 5.2 关键类引用关系

```java
MCAIMod (单例服务定位器)
├── ModConfig config (volatile)
│   └── PromptLoader (static)
├── OpenAIClient aiClient (volatile)
│   └── 依赖 ModConfig 的 endpoint/key/model
├── OpenAIClient reviewClient (volatile)
├── SearchRouter searchRouter (volatile)
│   └── WikiSearchProvider (按 wikiLanguage 创建)
├── CommandExecutionService cmdExec
├── ToolDispatcher toolDispatcher (volatile)
│   ├── searchRouter (SearchProvider 接口)
│   ├── cmdExec
│   └── MCAIMod
├── ChatHandler chatHandler
│   ├── chatLog
│   ├── animation (ThinkingAnimation)
│   ├── contextBuilder (PlayerContextBuilder)
│   ├── cmdExec
│   ├── toolDispatcher (volatile, setToolDispatcher 可更新)
│   └── aiExecutor (ThreadPoolExecutor, 4~8 线程)
├── CommandRegistry cmdReg
│   ├── chatHandler
│   └── cmdExec
├── PersonaManager personaManager
├── ChatLog chatLog
├── ThinkingAnimation animation
├── PlayerContextBuilder contextBuilder
├── PlayerBehaviorTracker behaviorTracker (volatile)
├── ChatReviewSystem chatReviewSystem (volatile)
│   ├── PenaltyHistory penaltyHistory
│   ├── AdminApprovalQueue approvalQueue
│   └── ReviewEngine reviewEngine
├── AIDebugLogger debugLogger (final)
└── WatchService + ScheduledExecutorService (配置热重载)
```

### 5.3 线程池依赖关系

```
主线程 (server.execute)
  │
  ├─── MCAI-Worker (4~8 线程, daemon) ← ChatHandler.aiExecutor
  │     └── 内部调用 aiClient.chat() → HTTP 请求
  │          └── 调用 toolExecutor → ToolDispatcher → 同步执行
  │
  ├─── MCAI-SearchRouter (CachedThreadPool, daemon) ← SearchRouter.executor
  │     └── WikiSearchProvider.search() → HTTP 请求 (8s 超时)
  │
  ├─── MCAI-UI (单线程 Scheduler, daemon) ← ThinkingAnimation.scheduler
  │     └── 定时发送 ActionBar 动画
  │
  ├─── MCAI-Review (单线程 Scheduler, daemon) ← ChatReviewSystem.reviewScheduler
  │     ├── scheduleAtFixedRate(runReview, interval) → ReviewEngine.run()
  │     └── AdminApprovalQueue timeout.schedule()
  │
  ├─── MCAI-ConfigWatcher (单线程 Scheduler, daemon) ← MCAIMod.watcherScheduler
  │     ├── WatchService.take() 循环
  │     └── 兜底轮询 scheduleAtFixedRate(5s)
  │
  └─── MCAI-Chain-{id} (临时线程, daemon) ← CommandExecutionService.approveChain()
        └── 命令链执行, 可被 interrupt() 取消
```

---

## 6. 事件生命周期

### 6.1 完整生命周期时序图

```
游戏启动 / 进入世界
  │
  ├─ Fabric Mod Loader 加载
  │   └─ MCAIMod.onInitialize() [◆ 只调用一次]
  │       ├─ ModConfig.load() → config/mcai/config.json
  │       ├─ PromptLoader.load() → system_prompt.txt / review_prompt.txt
  │       ├─ new SearchRouter(config, wikiProvider) → 创建 CachedThreadPool
  │       ├─ new OpenAIClient(config)
  │       ├─ new OpenAIClient(config, reviewEndpoint, reviewKey, reviewModel)
  │       ├─ new PersonaManager() → 提取内置 JSON + refreshPersonaList()
  │       ├─ new ChatLog() / ThinkingAnimation() / PlayerContextBuilder()
  │       ├─ new CommandExecutionService(this)
  │       ├─ new ToolDispatcher(searchRouter, cmdExec, this)
  │       ├─ new ChatHandler(...)
  │       ├─ new CommandRegistry(chatHandler, cmdExec)
  │       ├─ CommandRegistrationCallback → 注册 15 个 /ai* 命令
  │       ├─ ServerPlayConnectionEvents.DISCONNECT → chatHandler.onPlayerDisconnect
  │       ├─ ServerLifecycleEvents.SERVER_STARTED → (待触发)
  │       ├─ ServerLifecycleEvents.SERVER_STOPPING → (待触发)
  │       ├─ chatHandler.registerChatInterceptor()
  │       ├─ startConfigWatcher() → WatchService + 兜底轮询
  │       └─ new PlayerBehaviorTracker(config) → scores.json load()
  │
  ├─ SERVER_STARTED 事件 [◆ 每次进入世界触发]
  │   ├─ chatLog.clear() — 防止跨世界消息泄漏
  │   ├─ searchRouter.shutdown() + new SearchRouter(...) — 重建线程池
  │   ├─ new ToolDispatcher(searchRouter, cmdExec, this)
  │   ├─ chatHandler.setToolDispatcher(td)
  │   └─ 仅专用服务器:
  │       ├─ new ChatReviewSystem(this, behaviorTracker)
  │       ├─ commandDispatcher.register(/aireview 命令)
  │       └─ config.isEnableAutoReview() → chatReviewSystem.start()
  │
  │  ┌─────────────────── 游戏运行中 ───────────────────┐
  │  │                                                   │
  │  │  玩家输入 "!ai 给我一把钻石剑"                    │
  │  │    → ALLOW_CHAT_MESSAGE → chatHandler.handleAIQuery│
  │  │                                                   │
  │  │  定时: ChatReviewSystem.runReview() (30min)       │
  │  │                                                   │
  │  │  配置文件变更 → WatchService → reloadConfig()     │
  │  │                                                   │
  │  └───────────────────────────────────────────────────┘
  │
  └─ SERVER_STOPPING 事件 [◆ 每次退出世界触发]
      ├─ chatReviewSystem.stop() → reviewScheduler.shutdown()
      ├─ behaviorTracker.saveImmediate() → scores.json 持久化
      ├─ debugLogger.stop() → 重置 enabled + 关闭 BufferedWriter
      ├─ watcherScheduler.shutdownNow() — 先关闭调度器
      ├─ configWatcher.close() — 再关闭 WatchService (顺序不能反!)
      └─ searchRouter.shutdown() → shutdownNow() 线程池
```

### 6.2 关键生命周期 Gotcha

| 问题 | 根因 | 解决方案 |
|---|---|---|
| 退出世界后再次进入，AI 搜索失败 | `onInitialize()` 只调用一次，SearchRouter 线程池在 SERVER_STOPPING 时被 shutdownNow() | **SERVER_STARTED 中重建 SearchRouter 和 ToolDispatcher** |
| ChatHandler 仍持有旧 ToolDispatcher | ChatHandler 构造时注入的 ToolDispatcher 引用在 reloadConfig 后未更新 | ToolDispatcher 字段改为 **volatile**，提供 `setToolDispatcher()` |
| Config Watcher 报 ClosedWatchServiceException | WatchService 在 Scheduler 之前关闭，take() 循环还在运行 | **先 shutdownNow() Scheduler，再 close() WatchService** |
| ChatLog 跨世界泄漏 | ChatLog 是长生命周期单例，退出世界时未清空 | SERVER_STARTED 中 chatLog.clear() |
| AIDebugLogger 静默失败 | enabled 标志未在 SERVER_STOPPING 重置，BufferedWriter 泄漏 | SERVER_STOPPING 中 debugLogger.stop() |
| Reload 后 /aikb 还是旧 SearchRouter | CommandRegistry 直接引用 ChatHandler，而 ChatHandler 需要同步 | reloadConfig 同时重建 ToolDispatcher + setToolDispatcher |

---

## 7. 运行与构建

### 7.1 构建环境

| 要求 | 版本 |
|---|---|
| JDK | 25（必须，`java.toolchain.languageVersion = 25`） |
| Gradle | 9.5.1（内置 wrapper） |
| Fabric Loader | 0.19.2 |
| Fabric API | 0.149.1+26.1.2 |
| Minecraft | 26.1.2 |
| 映射 | Mojang Mappings |

### 7.2 构建命令

```powershell
# 完整构建（含 sources jar）
.\gradlew.bat build

# 只编译
.\gradlew.bat compileJava

# 清理后构建
.\gradlew.bat clean build

# 运行 Minecraft 客户端（开发调试）
.\gradlew.bat runClient
```

构建产物：`build/libs/mcai-26.1.2-<mod_version>.jar`

### 7.3 依赖清单

| 依赖 | 版本 | scope | 说明 |
|---|---|---|---|
| Fabric Loader | 0.19.2 | implementation | Fabric Mod 加载器 |
| Fabric API | 0.149.1+26.1.2 | implementation | 事件 API（聊天/生命周期/连接） |
| Mod Menu | 20.0.1 | compileOnly | Mod 配置 GUI 入口 |
| Gson | (Maven Central latest) | transitive | JSON 解析（ToolDispatcher, ModConfig 等） |
| slf4j | (Fabric API 传递) | transitive | 日志 |
| Java HttpClient | JDK 25 内置 | - | OpenAIClient + WikiSearchProvider |

### 7.4 部署步骤

```bash
# 1. 将 JAR 放入 mods/ 目录
cp build/libs/mcai-26.1.2-1.7.0-beta.2.jar minecraft_server/mods/

# 2. 首次启动后配置文件自动生成
#    config/mcai/config.json       —— 主配置
#    config/mcai/system_prompt.txt —— 系统提示词（默认自动写入）
#    config/mcai/review_prompt.txt —— 审查提示词（默认自动写入）
#    config/mcai/personas/         —— 人格目录（内置自动提取）

# 3. 编辑 config.json 填入 API Key
#    "apiEndpoint": "https://api.deepseek.com",
#    "apiKey": "sk-xxx",
#    "model": "deepseek-v4-flash"

# 4. 可选：部署知识库（不打入 JAR）
#    cp kb/*.json minecraft_server/config/mcai/kb/
#    （当前 Wiki 在线搜索已弃用本地知识库，保留兼容）

# 5. 启动服务器
java -Xmx4G -jar server.jar

# 6. 可选：服务器安装资源包强制翻译
#    客户端未装 Mod 时，需要 mcai-lang-pack.zip 作为服务器资源包
```

---

## 8. 配置与资源

### 8.1 运行时目录结构

```
config/mcai/
├── config.json                  # 主配置（GSON_SAVE 排除了提示词缓存字段）
├── system_prompt.txt            # 系统提示词（默认自动创建，可编辑）
├── review_prompt.txt            # 审查提示词（默认自动创建，可编辑）
├── scores.json                  # 玩家行为分持久化（运行时生成）
├── penalties.json               # 处罚历史持久化（运行时生成）
├── debug/
│   └── ai_debug_YYYY-MM-DD_HH-mm-ss.log  # AIDebugLogger 输出
└── personas/
    ├── tsundere.json            # 内置（首次启动自动提取）
    ├── pirate.json
    ├── chuuni.json
    ├── gentle.json
    ├── villager.json
    ├── piglin.json
    ├── ender_dragon.json
    └── creeper.json
```

### 8.2 配置字段速查

**AI 连接**

| 字段 | 默认 | 类型 | 说明 |
|---|---|---|---|
| `apiEndpoint` | `https://api.deepseek.com` | URL | AI API 端点 |
| `apiKey` | 空 | String | API Key（HTTP 模式会警告） |
| `model` | `deepseek-v4-flash` | String | 模型名 |
| `compatibilityMode` | false | boolean | 兼容模式（只传 model，适配 LM Studio） |

**AI 参数**

| 字段 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `triggerPrefix` | `!ai` | - | 聊天触发前缀 |
| `maxTokens` | 2048 | 256–8192 | AI 最大输出 token |
| `temperature` | 0.75 | 0.0–2.0 | 随机性 |
| `thinkingLevel` | 1 | 0–3 | 思考深度（0=关闭） |
| `contextMaxChars` | 20000 | 2000–100000 | 历史对话最大字符 |
| `maxToolCalls` | 15 | 1–50 | 单次对话最大工具轮数 |

**命令审批**

| 字段 | 默认 | 说明 |
|---|---|---|
| `strictMode` | true | 严格模式（非 safeCommands 的命令全部需要审批） |
| `requireApprovalCommands` | op, deop, ban, kick... | 必须审批的命令白名单 |
| `safeCommands` | locate, help, list, data get... | 免审批安全命令 |
| `aiCooldownSeconds` | 60 | 非管理员冷却（秒） |
| `aiMaxConcurrent` | 3 | 非管理员最大并发数 |

**审查系统**

| 字段 | 默认 | 说明 |
|---|---|---|
| `enableAutoReview` | true | 自动审查开关 |
| `reviewIntervalMinutes` | 30 | 审查周期 |
| `yellowCardThreshold` | -30 | 黄牌分数阈值（警告） |
| `redCardThreshold` | -60 | 红牌分数阈值（踢出审批） |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复量（cap at 0） |
| `approvalTimeoutMinutes` | 10 | 红牌审批超时（超时自动批准） |
| `reviewApiEndpoint` / `reviewApiKey` / `reviewModel` | 空 | 独立审查模型配置（空=跟随聊天系统） |

**知识库 / 人格 / 提示词**

| 字段 | 默认 | 说明 |
|---|---|---|
| `wikiLanguage` | `zh_cn` | Wiki 搜索语言（zh_cn / en_us） |
| `activePersona` | `default` | 当前激活的人格 |
| `personaLanguage` | 空 | 人格语言（空=跟随客户端游戏语言） |
| `promptLanguage` | `zh_cn` | 内置提示词语言 |
| `systemPromptPath` / `reviewPromptPath` | 空 | 自定义提示词文件路径 |

### 8.3 内置人格文件格式

```json
{
  "id": "villager",
  "name": "村民",
  "summary": "Minecraft 经典 NPC",
  "content": "你是一个 Minecraft 村民...",
  "translations": {
    "en_us": {
      "name": "Villager",
      "summary": "Classic Minecraft NPC",
      "content": "You are a Minecraft villager..."
    }
  }
}
```

必填字段：`id`, `name`, `content`。可选：`summary`, `translations`。`id` 禁止包含 `/` `\` `..`。

### 8.4 i18n 语言文件

位置：`src/main/resources/assets/mcai/lang/{zh_cn,en_us}.json`

所有用户可见字符串通过 `Component.translatable("mcai.xxx")` 引用，禁止硬编码文本。

服务器端资源包：`mcai-lang-pack/` — 用于客户端未安装 Mod 时的翻译回退。

---

## 附录 A：AI 工具定义清单

AI 通过 `buildToolDefinitions()` 注册 10 个工具：

| # | 工具名 | 参数 | 说明 | 触发条件 |
|---|---|---|---|---|
| 1 | `search_knowledge_base` | `query: string` | 搜索 Minecraft Wiki（中文/英文站） | 玩家询问游戏知识时 |
| 2 | `execute_minecraft_command` | `command: string` | 执行单条 MC 命令（危险命令走审批） | 需要执行游戏操作时 |
| 3 | `execute_command_chain` | `commands: string[]`, `interval: int` | 多条命令打包为一个审批单元，支持 0–10 秒间隔 | 多步骤任务（给物品+传送+附魔） |
| 4 | `get_server_status` | 无 | 时间/天气/生物群系/TPS | 玩家询问服务器状态 |
| 5 | `get_game_rules` | 无 | 18 项游戏规则状态 | 玩家询问规则 |
| 6 | `get_debug_info` | 无 | F3 调试信息（光照/区块/注视目标） | 需要精确环境数据 |
| 7 | `get_installed_mods` | 无 | 已安装 Mod 列表+版本 | 识别 Mod 物品命名空间 |
| 8 | `get_player_effects` | 无 | 药水效果 | 玩家询问自己的 buff/debuff |
| 9 | `get_player_advancements` | 无 | 进度完成情况 | 玩家询问进度 |
| 10 | `get_player_inventory` | 无 | 物品栏详情 | 玩家询问物品 |

**循环终止条件**：`maxToolCalls` 轮用完后，自动追加 user 收敛消息，最终调用不带 tools 的 API 获取收敛回复。

---

## 附录 B：审批超时与取消对照表

| 审批类型 | 超时 | 超时行为 | 取消方式 |
|---|---|---|---|
| 命令审批（execute_minecraft_command） | 3 分钟 | **取消**（返回 "审批超时"） | `/aicancel <id>` 或断开连接 |
| 命令链审批（execute_command_chain） | 3 分钟 | **取消** | `/aicancel <id>` 或断开连接 |
| 红牌踢出（AdminApprovalQueue） | 10 分钟（可配置） | **自动批准执行** | `/aireview reject <id>` |

**关键区别**：AI 工具调用的审批超时是「温和取消」（不再执行），而行为审查的红牌审批超时是「自动批准」（默认管理员不在时也执行踢出）。

---

## 附录 C：命令安全矩阵

### C.1 FORBIDDEN_COMMANDS（AI 禁止调用）

```
ai, aiwiki, aiquery, aiaccept, aireject, aicancel, aiclear, aireload, aitest, aicheck
```

### C.2 requireApprovalCommands（必须审批）

```
op, deop, ban, ban-ip, pardon, pardon-ip, kick, kill, damage, execute, stop, whitelist, save-all, reload
```

### C.3 safeCommands（严格模式下免审批）

```
locate, seed, list, help, say, title, tell, msg, w, fetchprofile, scoreboard, version, data get
```

### C.4 严格模式判断逻辑

```
needsApproval(command):
  1. root ∈ requireApprovalCommands → true
  2. strictMode = false → false
  3. 对每个 safeCommand:
       - 含空格: command.toLowerCase().startsWith(safe) → false
       - 不含空格: root.equals(safe) → false
  4. 否则 → true
```

---

## 附录 D：线程安全与并发模型

### D.1 并发集合使用

| 数据结构 | 类型 | 用途 |
|---|---|---|
| `pendingById` | `ConcurrentMap<Long, PendingCommand>` | 待审批单命令索引 |
| `pendingChains` | `ConcurrentMap<Long, PendingChain>` | 待审批命令链索引 |
| `pendingByPlayer` | `ConcurrentMap<UUID, Set<Long>>` | 玩家 → 待审批 ID 集合 |
| `history` | `ConcurrentHashMap<UUID, LinkedList<ChatMessage>>` | 每玩家对话历史（synchronized 块保护内部 LinkedList） |
| `lastAICallTime` | `ConcurrentMap<UUID, Long>` | 非管理员冷却时间戳 |
| `scores` / `lastRecoveryTime` | `ConcurrentHashMap<UUID, Integer/Long>` | 行为分数持久化 |
| `items` / `timeouts` | `ConcurrentMap<Integer, ApprovalItem/ScheduledFuture>` | 红牌审批队列 |

### D.2 volatile 关键字使用

| 字段 | 类 | 说明 |
|---|---|---|
| `toolDispatcher` | ChatHandler | 退出/重载后引用需更新 |
| `aiExecutor` | ChatHandler | killAIThreads 后重建 |
| `chatEnabled` | ChatHandler | 运行时开关 |
| `searchRouter` | MCAIMod | reloadConfig 重建 |
| `chatReviewSystem` | MCAIMod | SERVER_STARTED 创建（仅专用服务器） |
| `config` | MCAIMod | reloadConfig 更新 |
| `aiClient` / `reviewClient` | MCAIMod | reloadConfig 重建 |
| `chatLog.cachedPeek` / `chatLog.dirty` | ChatLog | 脏标记缓存 |
| `PendingChain.executing` / `executionThread` | CommandExecutionService | 执行状态标记 |
| `ApprovalItem.resolved` | AdminApprovalQueue | synchronized(item) 保护 |

### D.3 synchronized 块保护

| 对象 | 保护范围 |
|---|---|
| `ChatLog.log` | add(), peek(), clear() |
| `playerHistory (LinkedList)` | ChatHandler.handleAIQuery 中的 trimHistory |
| `AdminApprovalQueue.ApprovalItem` | tryResolve — 每条目只 resolve 一次 |
| `PenaltyHistory.recentPenalties` | addEvent(), purgeOld(), save() |

---

## 附录 E：数据文件清单

### E.1 运行时生成的持久化文件

| 文件 | 路径 | 读写时机 | 格式 |
|---|---|---|---|
| `config.json` | `config/mcai/` | 启动加载 + `/aireload` + 配置监视器 | JSON（GSON_SAVE 排除提示词字段） |
| `system_prompt.txt` | `config/mcai/` | 首次访问自动创建 + `/airesetprompts` 重置 | 纯文本 |
| `review_prompt.txt` | `config/mcai/` | 首次访问自动创建 + `/airesetprompts` 重置 | 纯文本 |
| `scores.json` | `config/mcai/` | addScore/tryRecover/resetScore 每次变更 + SERVER_STOPPING | JSON（UUID → score + lastRecoveryTime） |
| `penalties.json` | `config/mcai/` | 每次审查后 save() | JSON（cycle + events[]） |
| `ai_debug_*.log` | `config/mcai/debug/` | `/aidebug start` 创建 + stop 关闭 | 纯文本 |
| `review_last_response.txt` | `config/mcai/` | 每次审查后 | 纯文本（>100KB 自动分片） |
| `review_last_reasoning.txt` | `config/mcai/` | 每次审查后 | 纯文本（>100KB 自动分片） |

### E.2 不打入 JAR 的外部资源

| 路径 | 说明 | 部署方式 |
|---|---|---|
| `kb/*.json` | 知识库数据（biomesoplenty, create_mod, zh_wiki） | 管理员手动复制到 `config/mcai/kb/` |
| `mcai-lang-pack/` | 服务器强制资源包（客户端未装 Mod 时的翻译回退） | 设为 `server.properties` 的 `resource-pack` |

---

## 附录 F：i18n 键命名约定

所有 `Component.translatable()` 使用以下命名空间模式：

```
mcai.{模块}.{功能}[.{子功能}]
```

| 前缀 | 对应模块 | 示例 |
|---|---|---|
| `mcai.chat.` | ChatHandler | `mcai.chat.thinking_anim`, `mcai.chat.cooldown` |
| `mcai.cmd.` | CommandRegistry | `mcai.cmd.ai.broadcast`, `mcai.cmd.accept.invalid` |
| `mcai.cmd.exec.` | CommandExecutionService | `mcai.cmd.exec.broadcast_direct`, `mcai.cmd.exec.cancel_hint` |
| `mcai.cmd.review.` | ReviewCommandRegistry | `mcai.review.started`, `mcai.review.approve.done` |
| `mcai.cmd.persona.` | PersonaManager / aipersona | `mcai.cmd.persona.list_header`, `mcai.cmd.persona.set_done` |
| `mcai.review.` | ChatReviewSystem / ReviewEngine | `mcai.review.status.empty`, `mcai.review.kick_broadcast` |
| `mcai.persona.` | 内置人格 | `mcai.persona.default.name`（DEFAULT_PERSONA 的 i18n key） |

---

## 附录 G：Mojang 26.3 API Gotchas 速查

| 项目 | Mojang (26.2+) | 说明 |
|---|---|---|
| 包名 | `CommandSourceStack` | 不是 ServerCommandSource |
| 命令注册 | `Commands.literal()` | 不是 `CommandManager.literal()` |
| 纯文本组件 | `Component.literal()` | 不是 `Text.literal()` |
| 玩家 | `ServerPlayer` | 不是 ServerPlayerEntity |
| 世界访问 | `player.level()` | 不是 player.getEntityWorld() |
| 权限 | `LevelBasedPermissionSet.OWNER` | 不是 LeveledPermissionPredicate.OWNERS |
| KeyEvent | 新增 | MC 26.3 Snapshot 4 从 GLFW 切到 SDL3 |
| 屏幕 | `Minecraft.setScreenAndShow()` | `setScreen()` 已移除 |
| 屏幕覆盖 | `canInterruptWithAnotherScreen() → true` | 必须返回 true 才能被其他屏幕覆盖 |
| 屏幕截取 | `Screenshot.captureScreenshot(File)` | 需要 File 参数 |

---

> **文档版本**: 2026-08-07 —— 同步代码到 `main` 分支 HEAD (1.7.0-beta.2)
> **维护者**: MCAI 项目团队
> **对应 Mod 版本**: 1.7.0-beta.2 / Minecraft 26.1.2