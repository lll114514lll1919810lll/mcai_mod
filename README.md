# MCAI 模组

一个 Minecraft Fabric 服务端模组，接入 OpenAI 兼容 API（默认 DeepSeek），为服务器提供 AI 助手和自动行为审查。

---

## 功能清单

### AI 对话
- `/ai <消息>` 或 `!ai` 前缀触发
- 多轮对话上下文记忆（按字符数截断，可配置）
- 聊天日志自动注入上下文，AI 了解当前氛围
- 所有玩家可查自己行为分：`/aiscore`

### 工具调用（Function Calling）
| 工具 | 说明 |
|------|------|
| `search_knowledge_base` | 搜索中文 Wiki 知识库 |
| `read_knowledge_base` | 读取条目完整内容 |
| `execute_minecraft_command` | 执行指令（需审批的自动挂起等待） |
| `get_server_status` | 获取服务器实时状态（时间、天气、TPS 等） |
| `get_game_rules` | 获取游戏规则状态 |
| `get_debug_info` | 获取 F3 调试信息 |

### 指令审批
- 危险指令（op、ban、kick、stop 等）自动进入审批队列
- 可配置的审批指令列表
- **AI 线程挂起等待**：AI 执行需审批的指令时，AI 线程阻塞等待管理员审批结果，最长 3 分钟超时自动取消
- 管理员收到通知，可直接用 `/aiaccept` / `/aireject` 处理
- 超时和拒绝时 AI 获得对应反馈信息

### 严格模式安全命令白名单
- 开启后只有白名单内的只读命令可免审批
- 支持多词匹配（如 `data get` 只放行查看，不放行修改）
- 可配置命令列表
- 所有 AI 执行的命令自动写入审查日志

### 行为审查系统
- 周期性 AI 审查所有聊天记录（可配置间隔）
- 三级处罚：
  - **扣分**（severity -10）：仅扣分，无公屏警告
  - **黄牌**（severity -20 或分数 ≤ 阈值）：公屏警告
  - **红牌**（severity -30 或分数 ≤ 阈值）：踢出 + 管理员审批
- 管理员发言带 `[管理员]` 标记，审查 AI 信任管理员声明
- 每周期分数恢复（可配置恢复量，上限恢复至 0）
- 处罚记录跨轮次保留（可配置最大轮次），共享给对话 AI 和审查 AI
- 手动审查：`/aicheck`

### 审批队列（踢出审批）
- 红牌踢出需要管理员批准（`/aicheck approve <id>`）
- 10 分钟超时自动批准
- `/aicheck last` 查看上次审查原始输出和推理过程

### 知识库
- 内置中文 Wiki 数据（JAR 内部 `assets/mcai/kb/zh_wiki.json`）
- 双层检索：搜索返回摘要，读取返回全文
- 可通过 Python 脚本扩充

### 持久化存储
所有配置文件统一存放在 `config/mcai/` 目录下：
| 文件 | 内容 |
|------|------|
| `config/mcai/config.json` | 主配置 |
| `config/mcai/scores.json` | 玩家行为分（每次变更即时写盘） |
| `config/mcai/penalties.json` | 处罚记录（跨重启保留） |
| `config/mcai/memory.json` | AI 持久记忆 |
| `config/mcai/kb/` | 知识库数据 |
| `config/mcai/review_last_response.txt` | 上次审查 AI 原始输出 |
| `config/mcai/review_last_reasoning.txt` | 上次审查 AI 推理过程 |

### 安全措施
- 聊天记录用 `=== CHAT LOG START/END ===` 定界符包裹，防注入
- 审查输出严格校验：玩家名正则、severity 钳位、action 白名单
- 每玩家每轮累计扣分上限 -60
- AI 禁止执行模组内部指令（ai、aiaccept、aireload 等）
- 系统提示词自动覆盖，不保存到 JSON，确保更新后生效

### 测试指令（OP 专用）
- `/aitest score <玩家>` — 查询行为分
- `/aitest penalty <玩家> <分数>` — 模拟扣分
- `/aitest reset <玩家>` — 重置行为分
- `/aitest set <玩家> <分数>` — 设置行为分
- `/aitest review` — 手动触发审查
- `/aitest chatlog` — 查看聊天日志

---

## 配置项 (`config/mcai.json`)

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名 |
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | 2048 | 最大 token 数 |
| `temperature` | 0.75 | 温度 |
| `thinkingLevel` | 1 | DeepSeek 思考模式（0/1/3） |
| `maxToolCalls` | 15 | 最大工具调用轮次 |
| `strictMode` | true | 严格模式（仅白名单免审批） |
| `safeCommands` | `["locate","seed","list","help","say","title","tell","msg","w","fetchprofile","scoreboard","version","data get"]` | 严格模式白名单 |
| `requireApprovalCommands` | `["op","deop","ban","ban-ip","pardon","pardon-ip","kick","stop","whitelist","save-all","reload"]` | 需审批指令 |
| `reviewIntervalMinutes` | 30 | 审查间隔（分钟） |
| `yellowCardThreshold` | -30 | 黄牌阈值 |
| `redCardThreshold` | -60 | 红牌阈值 |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 踢出审批超时（分钟） |
| `enableAutoReview` | true | 启用自动审查 |
| `maxReviewCycles` | 4 | 处罚记录保留轮次 |

---

## 构建方法

```bash
# 选择版本
copy gradle-1.21.properties gradle.properties        # 1.21
copy gradle-1.21.11.properties gradle.properties      # 1.21.11

# 当前分支 mc-26.1.2 (26.1.2，直接使用 gradle.properties)
.\gradlew.bat jar
# 产物: build/libs/mcai-1.0.0.jar
```

需预装：JDK 21（1.21）或 JDK 25（1.21.11+）、Git

---

## 版本兼容性

| MC 版本 | JAR 文件名 | 构建配置 |
|---------|-----------|---------|
| 1.21/1.21.1 | `mcai-1.21.jar` | `gradle-1.21.properties` |
| 1.21.11 | `mcai-1.21.11.jar` | `gradle-1.21.11.properties` |
| 26.1.2 | `mcai-26.1.2.jar` | `gradle.properties`（当前分支） |

---

## 架构

```
MCAIMod (ModInitializer)
├── OpenAIClient (API 调用、工具循环)
├── ChatHandler (AI 对话、指令审批、聊天拦截、审查日志注入)
├── ChatReviewSystem (行为审查、周期调度、处罚记录)
│   ├── AdminApprovalQueue (踢出审批队列)
│   └── PlayerBehaviorTracker (行为分追踪、持久化)
├── KnowledgeBase (本地 Wiki 搜索与读取)
├── MemoryFile (AI 持久记忆)
├── ModConfig (JSON 配置)
└── MCAIConfigScreen (Mod Menu 配置界面)
```
