# MCAI 服主使用手册 / Server Admin Guide

> **中文：** 本文档面向服务器管理员，介绍如何安装、配置和使用 MCAI 模组。
> **English:** This guide covers installation, configuration, and usage for server admins.

---

## 安装 / Installation

### 前提条件 / Requirements
- Minecraft **Fabric 服务端 26.1.2**
- JDK 25
- 一个 **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com)）

### 步骤 / Steps
1. 下载 `mcai-<version>.jar` 放入 `mods/` 目录 / Place the JAR in `mods/`
2. 启动一次服务器，自动生成配置 / Start server to auto-generate config
3. 编辑 `config/mcai/config.json`，填入 API Key / Fill in your API Key
4. 执行 `/aireload` 重载 / Reload config

```json
{
  "apiEndpoint": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxxxxxxxxxxxxxxx",
  "model": "deepseek-v4-flash"
}
```

---

## 所有命令 / Commands

### 玩家命令 / Player Commands

| 命令 / Command | 中文说明 | English |
|----------------|---------|---------|
| `!ai <消息>` `/ai <消息>` | 与 AI 对话 | Chat with AI |
| `/aiscore` | 查看行为分和处罚规则 | Check behavior score |

### 管理员命令 / Admin Commands

| 命令 / Command | 中文说明 | English |
|----------------|---------|---------|
| `/aiaccept <编号>` | 批准待审批指令 | Approve pending command |
| `/aireject <编号>` | 拒绝待审批指令 | Reject pending command |
| `/aiquery` | 查看待审批列表 | List pending approvals |
| `/aiclear` | 清除对话历史 | Clear chat history |
| `/aireload` | 重载配置（清空状态） | Reload config |
| `/aikb <关键词>` | 搜索知识库 | Search knowledge base |

### 审查系统 / Review System

| 命令 / Command | 中文说明 | English |
|----------------|---------|---------|
| `/aicheck` | 手动触发审查 | Trigger manual review |
| `/aicheck approve <id>` | 批准踢出 | Approve kick |
| `/aicheck reject <id>` | 拒绝踢出 | Reject kick |
| `/aicheck last` | 查看上次审查结果 | View last review |
| `/aicheck last reasoning` | 查看 AI 推理过程 | View AI reasoning |

### 测试辅助（OP 专用）/ Test Commands (OP only)

| 命令 / Command | 中文说明 | English |
|----------------|---------|---------|
| `/aitest score <玩家>` | 查玩家行为分 | Check player score |
| `/aitest set <玩家> <分数>` | 设置行为分 | Set player score |
| `/aitest penalty <玩家> <分数>` | 模拟扣分 | Simulate penalty |
| `/aitest reset <玩家>` | 重置行为分 | Reset player score |
| `/aitest review` | 手动审查 | Trigger review |
| `/aitest chatlog` | 查看聊天日志 | View chat log |

---

## 审核系统 / Review System

### 基本流程 / Flow
1. **每 30 分钟**（可配置），AI 分析聊天记录 / AI analyzes chat every N minutes
2. 识别违规，输出处罚建议 / Detects violations, suggests penalties
3. 执行处罚，公屏广播 / Penalty executed and broadcast

### 三级处罚 / Three-Tier Penalty

| 等级 / Tier | 条件 / Condition | 效果 / Effect |
|-------------|-----------------|--------------|
| 扣分 / Score | severity -10 | 扣分，无公屏 / Score only, no broadcast |
| 黄牌 / Yellow | severity -20 或 ≤ -30 | 公屏警告 / Broadcast warning |
| 红牌 / Red | severity -30 或 ≤ -60 | 广播 + 踢出（管理员审批）/ Broadcast + kick (admin approval) |

### 分数恢复 / Score Recovery
- 每轮审查，在线非管理玩家恢复 **5 分**（可配置）/ Recovers 5 points per cycle
- 上限 **0 分** / Caps at 0

### 证据标准 / Evidence Standard
- **多人举报** → 构成证据 / Multiple reports → evidence
- **单人无佐证** → 不判罚 / Single report without corroboration → no penalty
- **管理员发言**具有最高效力 / Admin statements override all claims

### 管理员如何介入 / How Admins Intervene
- 游戏里说话带 `[管理员]` 标记，AI 自动信任 / Admin messages are marked and trusted by AI
- 例如你说"这是无规则PVP服"，AI 就不会判杀人违规 / Declare server rules and AI follows them

---

## 审批系统 / Approval System

- 危险指令（op、ban、kick 等）需要管理员批准
- Dangerous commands (op, ban, kick etc.) require admin approval
- AI 阻塞等待审批，3 分钟超时自动取消
- AI blocks waiting for approval; 3-min timeout auto-cancels
- 严格模式下仅白名单安全命令免审批
- Strict mode: only whitelisted safe commands skip approval

---

## 完整配置项 / Full Configuration

文件 / File: `config/mcai/config.json`，修改后 `/aireload` 重载。

| 字段 / Key | 默认值 / Default | 中文说明 | English |
|------------|-----------------|---------|---------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 | API endpoint |
| `apiKey` | `""` | API 密钥 | API key |
| `model` | `deepseek-v4-flash` | 模型名 | Model name |
| `triggerPrefix` | `!ai` | 聊天触发前缀 | Chat trigger prefix |
| `maxTokens` | `2048` | 回复最大 token | Max response tokens |
| `temperature` | `0.75` | 回复随机性 | Response randomness |
| `thinkingLevel` | `1` | 思考模式 0-3 | Thinking level 0-3 |
| `strictMode` | `true` | 严格模式 | Strict mode |
| `reviewIntervalMinutes` | `30` | 审查间隔（分） | Review interval (min) |
| `yellowCardThreshold` | `-30` | 黄牌阈值 | Yellow card threshold |
| `redCardThreshold` | `-60` | 红牌阈值 | Red card threshold |
| `scoreRecoveryPerInterval` | `5` | 每周期恢复分 | Score recovery per cycle |
| `approvalTimeoutMinutes` | `10` | 审批超时（分） | Approval timeout (min) |
| `systemPromptPath` | `""` | AI提示词文件路径 | System prompt file |
| `reviewPromptPath` | `""` | 审查提示词文件路径 | Review prompt file |
| `promptLanguage` | `zh_cn` | 内置提示词语言 | Built-in prompt language |

---

## 文件结构 / File Structure

`config/mcai/` 目录下的文件 / Files under `config/mcai/`:

| 文件 / File | 内容 / Content |
|-------------|---------------|
| `config.json` | 主配置 / Main config |
| `scores.json` | 玩家行为分 / Player scores |
| `penalties.json` | 处罚历史 / Penalty history |
| `system_prompt.txt` | AI 提示词（可自定义） / System prompt (customizable) |
| `review_prompt.txt` | 审查提示词（可自定义） / Review prompt (customizable) |
| `review_last_response.txt` | 上次审查 AI 原始输出 / Last review raw output |
| `review_last_reasoning.txt` | 上次审查 AI 推理过程 / Last review reasoning |

---

## 常见问题 / FAQ

**Q: 审查太严/太松？ / Review too strict/lenient?**
调整 `yellowCardThreshold` 和 `redCardThreshold`，数值越大越严格。
Adjust thresholds; higher values = stricter.

**Q: 不想让玩家用某些指令？ / Want to block commands?**
从 `safeCommands` 白名单删除或加入 `requireApprovalCommands`。
Remove from whitelist or add to approval list.

**Q: 能用其他 API 吗？ / Other API providers?**
可以。改 `apiEndpoint` 为任何 OpenAI 兼容接口。
Yes. Set any OpenAI-compatible endpoint.

**Q: 消耗大吗？ / Cost?**
DeepSeek flash 很便宜，每月几块钱。审查 30 分钟才调用一次。
Very cheap; review calls every 30 min; daily use is pennies.
