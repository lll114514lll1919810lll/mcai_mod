# MCAI 服主使用手册

> 本文档面向服务器管理员，介绍如何安装、配置和使用 MCAI 模组。开发者请参考 [开发总结与经验教训](MCAI_MOD_SUMMARY.md)。

## 这是什么

MCAI 是一个 Fabric 服务端模组，接入大语言模型（默认 DeepSeek），为你的 Minecraft 服务器提供：

- **AI 助手** — 玩家可以通过聊天 `!ai` 或 `/ai` 让 AI 查 Wiki、看状态、执行指令
- **自动行为审查** — 定期分析聊天记录，自动识别违规行为，该警告警告、该踢踢
- **审批制** — 危险指令（如 op、ban、kick）需要管理员手动批准才执行，超时自动取消

---

## 安装

### 前提条件
- Minecraft **Fabric 服务端**（支持 26.1.2、1.21.11、1.21/1.21.1）
- JDK 25（26.1.2、1.21.11）或 JDK 21（1.21/1.21.1）
- 一个 **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com) 注册获取，余额即可用，很便宜）

### 步骤
1. 将 `mcai-1.0.0.jar` 放入服务器的 `mods/` 目录
2. 启动一次服务器，会自动在 `config/mcai/config.json` 生成默认配置
3. 编辑 `config/mcai/config.json`，填入你的 API Key
4. 重启服务器

```json
{
  "apiEndpoint": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxxxxxxxxxxxxxxx",
  "model": "deepseek-v4-flash"
}
```

---

## 所有命令

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
| `/aicheck approve <id>` | 批准踢出（红牌执行的踢出需要审批） |
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

---

## 审核系统如何运作

### 基本流程
1. **每 30 分钟**（可配置），AI 分析这段时间的聊天记录
2. 识别违规行为，输出处罚建议
3. 执行处罚，结果公屏广播

### 三级处罚

| 等级 | 条件 | 效果 |
|------|------|------|
| 扣分 | severity -10 | 扣 10 分，无公屏提示 |
| 黄牌 | severity -20 或 累计分数 ≤ -30 | 公屏警告 |
| 红牌 | severity -30 或 累计分数 ≤ -60 | 公屏广播 + 踢出（需管理员 `/aicheck approve` 批准，10 分钟超时自动批准） |

### 分数恢复
- 每轮审查周期，在线非管理玩家自动恢复 **5 分**（可配置）
- 上限恢复到 **0 分**，不会变成正分

### 证据标准
AI 审查采用"优势证据"原则：
- **多人举报同一人** → 构成优势证据，应予判罚
- **单人举报无佐证** → 证据不足，不判罚
- **被举报人沉默/不承认** → 不影响判罚
- **管理员发言具有最高效力** — 你说"这是无规则 PVP 服"，AI 就不会判 PVP 行为违规

### 管理员如何介入
- 直接在游戏里说话 — AI 审查时会看到你的发言带有 `[管理员]` 标记
- AI 把你的发言当作权威声明，例如你说"这个玩家我允许的"，AI 就不会判他违规
- 不需要 `/aicheck reject` 去一个一个处理

---

## 审批系统如何运作

### 危险指令审批
配置中 `requireApprovalCommands` 列表里的指令（默认：op、deop、ban、ban-ip、pardon、pardon-ip、kick、stop、whitelist、save-all、reload），AI 执行前需要管理员批准：

1. 玩家让 AI 执行危险指令
2. AI **暂停对话，等待审批**（全服广播请求）
3. 管理员 `/aiaccept 1` 批准 → 指令执行 → AI 继续对话
4. 管理员 `/aireject 1` 拒绝 → AI 收到拒绝信息
5. **3 分钟没人理 → 自动取消**，AI 收到超时信息

### 严格模式 (`strictMode: true`，默认开启)
开启后，**只有白名单内的安全命令**可以直接执行，其余全部走审批。白名单默认包含：`locate`、`seed`、`list`、`help`、`say`、`title`、`tell`、`msg`、`w`、`fetchprofile`、`scoreboard`、`version`、`data get`。

支持部分命令放行，例如 `data get` 在白名单中，`data get entity @s` 可以直接执行，但 `data merge` 需要审批。

---

## 完整配置项

编辑 `config/mcai/config.json`，修改后 `/aireload` 重载（或重启服务器）。

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址，也支持其他 OpenAI 兼容接口 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名 |
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | 2048 | AI 回复最大 token |
| `temperature` | 0.75 | 回复随机性（0=严谨，1=创意） |
| `thinkingLevel` | 1 | DeepSeek 思考模式：0=关，1=开，3=最大努力 |
| `maxToolCalls` | 15 | AI 单次对话最多调用工具次数 |
| `contextMaxChars` | 20000 | 对话上下文最大字符数 |
| `strictMode` | true | 严格模式：只允许白名单命令免审批 |
| `safeCommands` | `["locate","seed",...]` | 严格模式白名单（支持 `data get` 这种多词模式） |
| `requireApprovalCommands` | `["op","deop",...]` | 必须审批的指令列表 |
| `enableChatInterception` | true | 启用聊天拦截（关了审查系统也废了） |
| `enableCommandExecution` | true | 允许 AI 执行指令 |
| `reviewIntervalMinutes` | 30 | 审查间隔（分钟） |
| `yellowCardThreshold` | -30 | 黄牌阈值 |
| `redCardThreshold` | -60 | 红牌阈值 |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 踢出审批超时（分钟） |
| `enableAutoReview` | true | 启用自动审查 |
| `maxReviewCycles` | 4 | 处罚记录保留轮次 |

---

## 常见使用场景

### 场景一：玩家要来把钻石剑
```
玩家: !ai 给我一把钻石剑
AI: §e已发送审批  /give Steve diamond_sword 1  §7(待审批)
→ 管理员 /aiaccept 1
AI: §a已给
```

### 场景二：玩家骂人
```
A: 艹你妈 B！
B: 举报 A 骂人
C: A 你过分了
→ 审查 AI 检测到多人指证 + 辱骂记录 → 黄牌警告，公屏广播
```

### 场景三：管理员声明规则
```
[管理员] 服主: 这是无规则PVP服，别来举报杀人
A: 服主，B 乱杀人！！
→ 审查 AI 看到管理员声明 → 不会判 B 违规
```

### 场景四：控制台操作
```
控制台(通过 /ai): 帮我查一下他们的行为分
AI → 控制台: Steve 当前 -20, Alex 0, B -50(已触发红牌)
→ 聊天日志完整记录，审查 AI 能看到服主的操作
```

### 场景五：踢出审批
```
→ 审查 AI 判定 B 触发红牌（分数 -65）
→ 公屏广播 + 管理员收到私信：/aicheck approve 1 批准
→ 管理员 /aicheck approve 1 → B 被踢出
→ 10 分钟无人审批 → 自动执行踢出
```

---

## 文件结构

所有数据存放在 `config/mcai/`：

| 文件 | 内容 |
|------|------|
| `config.json` | 主配置 |
| `scores.json` | 玩家行为分 |
| `penalties.json` | 处罚历史 |
| `memory.json` | AI 记忆 |
| `kb/` | 知识库缓存 |
| `review_last_response.txt` | 上次审查 AI 原始输出 |
| `review_last_reasoning.txt` | 上次审查 AI 推理过程 |

---

## 常见问题

**Q: 审查太严/太松怎么办？**
调整 `yellowCardThreshold` 和 `redCardThreshold`，数值越大越严格（默认 -30/-60 比较宽松）。

**Q: 不想让玩家用某些指令？**
从 `safeCommands` 白名单里删掉，或者把它们加入 `requireApprovalCommands`。

**Q: DeepSeek 之外能用别的 API 吗？**
能，修改 `apiEndpoint` 为任何 OpenAI 兼容接口（如 OpenAI、通义千问、GLM 等），修改 `model` 为对应模型名。

**Q: AI 会不会乱执行命令破坏服务器？**
不会。默认开启严格模式，只有白名单里的安全命令能直接执行，其余必须管理员审批。

**Q: /say 命令的输出审查 AI 能看到吗？**
部分版本可能收不到。但 AI 帮玩家执行的指令本身（`/say`、`/setblock` 等）会被记录到审查日志。

**Q: 消耗大吗？**
DeepSeek flash 模型非常便宜，日常使用每月几块钱。审查通常 30 分钟才调用一次 AI，对话部分按需。
