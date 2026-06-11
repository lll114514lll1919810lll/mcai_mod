# MCAI - Minecraft AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-1.21+-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2F25-orange.svg)](https://adoptium.net/)

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（默认 DeepSeek），为 Minecraft 服务器提供 AI 助手和自动行为审查功能。

## 功能特性

### AI 对话
- `/ai <消息>` 或 `!ai` 前缀触发
- 多轮对话上下文记忆（按字符数截断，可配置）
- 聊天日志自动注入上下文，AI 了解当前氛围
- 玩家可查自己行为分：`/aiscore`

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
- AI 线程阻塞等待管理员审批结果，最长 3 分钟超时自动取消
- 管理员收到通知，可用 `/aiaccept` / `/aireject` 处理

### 行为审查系统
- 周期性 AI 审查所有聊天记录（可配置间隔）
- 三级处罚：扣分、黄牌警告、红牌踢出
- 管理员发言带 `[管理员]` 标记，审查 AI 信任管理员声明
- 每周期分数恢复（可配置，上限恢复至 0）

### 严格模式
- 只有白名单内的只读命令可免审批
- 支持多词匹配（如 `data get` 只放行查看，不放行修改）
- 所有 AI 执行的命令自动写入审查日志

### 知识库
- 内置中文 Wiki 数据
- 双层检索：搜索返回摘要，读取返回全文
- 可通过 Python 脚本扩充

## 安装

### 前提条件
- Minecraft **Fabric 服务端**（支持 26.1.2、1.21.11、1.21/1.21.1）
- JDK 25（26.1.2、1.21.11）或 JDK 21（1.21/1.21.1）
- 一个 **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com) 注册获取）

### 步骤
1. 下载 `mcai-1.0.0.jar`
2. 放入服务器的 `mods/` 目录
3. 启动服务器，自动生成配置文件
4. 编辑 `config/mcai/config.json`，填入你的 API Key
5. 重启服务器

```json
{
  "apiEndpoint": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxxxxxxxxxxxxxxx",
  "model": "deepseek-v4-flash"
}
```

## 命令列表

### 玩家命令
| 命令 | 说明 |
|------|------|
| `!ai <消息>` 或 `/ai <消息>` | 与 AI 对话 |
| `/aiscore` | 查看自己的行为分和处罚规则 |

### 管理员命令
| 命令 | 说明 |
|------|------|
| `/aiaccept <编号>` | 批准待审批指令 |
| `/aireject <编号>` | 拒绝待审批指令 |
| `/aiquery` | 查看自己的待审批列表 |
| `/aiclear` | 清除自己的 AI 对话历史 |
| `/aireload` | 重载配置（清理所有状态） |
| `/aikb <关键词>` | 搜索知识库 |

### 审查系统
| 命令 | 说明 |
|------|------|
| `/aicheck` | 手动触发一次审查 |
| `/aicheck approve <id>` | 批准踢出 |
| `/aicheck reject <id>` | 拒绝踢出 |
| `/aicheck last` | 查看上次审查的 AI 原始输出 |
| `/aicheck last reasoning` | 查看上次审查的 AI 推理过程 |

### 测试辅助（OP 专用）
| 命令 | 说明 |
|------|------|
| `/aitest score <玩家>` | 查玩家行为分 |
| `/aitest set <玩家> <分数>` | 设置玩家行为分 |
| `/aitest penalty <玩家> <分数>` | 模拟扣分 |
| `/aitest reset <玩家>` | 重置行为分 |
| `/aitest review` | 手动审查 |
| `/aitest chatlog` | 查看聊天日志 |

## 配置

配置文件位于 `config/mcai/config.json`，修改后使用 `/aireload` 重载。

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址（支持任何 OpenAI 兼容接口） |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名 |
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | 2048 | AI 回复最大 token |
| `temperature` | 0.75 | 回复随机性（0=严谨，1=创意） |
| `thinkingLevel` | 1 | DeepSeek 思考模式：0=关，1=开，3=最大努力 |
| `maxToolCalls` | 15 | AI 单次对话最多调用工具次数 |
| `contextMaxChars` | 20000 | 对话上下文最大字符数 |
| `strictMode` | true | 严格模式：只允许白名单命令免审批 |
| `safeCommands` | `["locate","seed",...]` | 严格模式白名单 |
| `requireApprovalCommands` | `["op","deop",...]` | 必须审批的指令列表 |
| `enableChatInterception` | true | 启用聊天拦截 |
| `enableCommandExecution` | true | 允许 AI 执行指令 |
| `reviewIntervalMinutes` | 30 | 审查间隔（分钟） |
| `yellowCardThreshold` | -30 | 黄牌阈值 |
| `redCardThreshold` | -60 | 红牌阈值 |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 踢出审批超时（分钟） |
| `enableAutoReview` | true | 启用自动审查 |
| `maxReviewCycles` | 4 | 处罚记录保留轮次 |

## 构建

```bash
# 克隆仓库
git clone https://github.com/YOUR_USERNAME/mc.git
cd mc

# 选择版本
copy gradle-1.21.properties gradle.properties      # 1.21/1.21.1
copy gradle-1.21.11.properties gradle.properties    # 1.21.11
# 或直接使用 gradle-26.1.2.properties（26.1.2）

# 构建
.\gradlew.bat jar

# 产物位于 build/libs/mcai-1.0.0.jar
```

## 版本兼容性

| MC 版本 | 服务端 | 客户端 | JDK |
|---------|--------|--------|-----|
| 26.1.2 | ✅ | ✅ | 25 |
| 1.21.11 | ✅ | ✅ | 25 |
| 1.21 / 1.21.1 | ✅ | ✅ | 21 |

## 架构

```
MCAIMod (ModInitializer)
├── OpenAIClient        - API 调用、工具循环
├── ChatHandler         - AI 对话、指令审批、聊天拦截
├── ChatReviewSystem    - 行为审查、周期调度、处罚记录
│   ├── AdminApprovalQueue   - 踢出审批队列
│   └── PlayerBehaviorTracker - 行为分追踪、持久化
├── KnowledgeBase       - 本地 Wiki 搜索与读取
├── MemoryFile          - AI 持久记忆
├── ModConfig           - JSON 配置
└── MCAIConfigScreen    - Mod Menu 配置界面
```

## 安全机制

- 聊天记录用 `=== CHAT LOG START/END ===` 定界符包裹，防注入
- 审查输出严格校验：玩家名正则、severity 钳位、action 白名单
- 每玩家每轮累计扣分上限 -60
- AI 禁止执行模组内部指令（ai、aiaccept、aireload 等）
- 系统提示词自动覆盖，不保存到 JSON，确保更新后生效

## 常见问题

**Q: 审查太严/太松怎么办？**
调整 `yellowCardThreshold` 和 `redCardThreshold`，数值越大越严格。

**Q: 能用其他 API 吗？**
能，修改 `apiEndpoint` 为任何 OpenAI 兼容接口（OpenAI、通义千问、GLM 等）。

**Q: AI 会不会乱执行命令破坏服务器？**
不会。默认开启严格模式，只有白名单里的安全命令能直接执行，其余必须管理员审批。

**Q: 消耗大吗？**
DeepSeek flash 模型非常便宜，日常使用每月几块钱。审查通常 30 分钟才调用一次。

## 相关文档

- [服主使用手册](USER_GUIDE.md) - 详细配置和使用说明
- [开发总结与经验教训](MCAI_MOD_SUMMARY.md) - 多版本维护经验

## 许可证

[MIT License](LICENSE)
