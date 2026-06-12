# MCAI 服主使用手册

> [English version](USER_GUIDE_EN.md)

---

## 安装

### 前提条件
- Minecraft **Fabric 服务端 26.1.2**
- JDK 25
- 一个 **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com)）

### 步骤
1. 下载 `mcai-<version>.jar` 放入 `mods/` 目录
2. 启动一次服务器，自动在 `config/mcai/config.json` 生成默认配置
3. 编辑 `config/mcai/config.json`，填入你的 API Key
4. 执行 `/aireload` 重载

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
| `/aiscore` | 查看行为分和处罚规则 |

### 管理员命令

| 命令 | 说明 |
|------|------|
| `/aiaccept <编号>` | 批准待审批指令 |
| `/aireject <编号>` | 拒绝待审批指令 |
| `/aiquery` | 查看待审批列表 |
| `/aiclear` | 清除对话历史 |
| `/aireload` | 重载配置（清空状态） |
| `/aikb <关键词>` | 搜索知识库 |

### 审查系统

| 命令 | 说明 |
|------|------|
| `/aicheck start` | 手动触发审查 |
| `/aicheck approve <id>` | 批准踢出 |
| `/aicheck reject <id>` | 拒绝踢出 |
| `/aicheck last` | 查看上次审查结果 |
| `/aicheck last reasoning` | 查看 AI 推理过程 |

### 测试辅助（OP 专用）

| 命令 | 说明 |
|------|------|
| `/aitest score <玩家>` | 查玩家行为分 |
| `/aitest set <玩家> <分数>` | 设置行为分 |
| `/aitest penalty <玩家> <分数>` | 模拟扣分 |
| `/aitest reset <玩家>` | 重置行为分 |
| `/aitest review` | 手动审查 |
| `/aitest chatlog` | 查看聊天日志 |

---

## 审核系统

### 基本流程
1. **每 30 分钟**（可配置），AI 分析聊天记录
2. 识别违规，输出处罚建议
3. 执行处罚，公屏广播

### 三级处罚

| 等级 | 条件 | 效果 |
|------|------|------|
| 扣分 | severity -10 | 扣分，无公屏 |
| 黄牌 | severity -20 或 ≤ -30 | 公屏警告 |
| 红牌 | severity -30 或 ≤ -60 | 广播 + 踢出（管理员审批） |

### 分数恢复
- 每轮审查，在线非管理玩家恢复 **5 分**（可配置）
- 上限 **0 分**

### 证据标准
- **多人举报** → 构成证据
- **单人无佐证** → 不判罚
- **管理员发言**具有最高效力

### 管理员如何介入
- 游戏里说话带 `[管理员]` 标记，AI 自动信任
- 例如你说"这是无规则PVP服"，AI 就不会判杀人违规

---

## 审批系统

- 危险指令（op、ban、kick 等）需要管理员批准
- AI 阻塞等待审批，3 分钟超时自动取消
- 严格模式下仅白名单安全命令免审批

---

## 完整配置项

文件：`config/mcai/config.json`，修改后 `/aireload` 重载。

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名 |
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | 2048 | AI 回复最大 token |
| `temperature` | 0.75 | 回复随机性 |
| `thinkingLevel` | 1 | 思考模式 0-3 |
| `strictMode` | true | 严格模式 |
| `reviewIntervalMinutes` | 30 | 审查间隔（分） |
| `yellowCardThreshold` | -30 | 黄牌阈值 |
| `redCardThreshold` | -60 | 红牌阈值 |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 审批超时（分） |
| `systemPromptPath` | `""` | AI提示词文件路径 |
| `reviewPromptPath` | `""` | 审查提示词文件路径 |
| `promptLanguage` | `zh_cn` | 内置提示词语言 |

---

## 文件结构

`config/mcai/` 下的文件：

| 文件 | 内容 |
|------|------|
| `config.json` | 主配置 |
| `scores.json` | 玩家行为分 |
| `penalties.json` | 处罚历史 |
| `system_prompt.txt` | AI 提示词（可自定义） |
| `review_prompt.txt` | 审查提示词（可自定义） |
| `review_last_response.txt` | 上次审查 AI 原始输出 |
| `review_last_reasoning.txt` | 上次审查 AI 推理过程 |

---

## 常见问题

**Q: 审查太严/太松怎么办？**
调整 `yellowCardThreshold` 和 `redCardThreshold`，数值越大越严格。

**Q: 不想让玩家用某些指令？**
从 `safeCommands` 白名单删除或加入 `requireApprovalCommands`。

**Q: 能用其他 API 吗？**
能，改 `apiEndpoint` 为任何 OpenAI 兼容接口。

**Q: 消耗大吗？**
DeepSeek flash 很便宜，每月几块钱。审查 30 分钟才调用一次。
