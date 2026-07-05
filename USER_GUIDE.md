# MCAI 服主使用手册

> [English version](USER_GUIDE_EN.md)

---

## 安装

### 前提条件
- Minecraft **Fabric 服务端 26.2**
- JDK 25
- 一个 **DeepSeek API Key**（[platform.deepseek.com](https://platform.deepseek.com)）

### 步骤
1. 下载 `mcai-<version>.jar` 放入 `mods/` 目录
2. 启动一次服务器，自动在 `config/mcai/config.json` 生成默认配置
3. 编辑 `config/mcai/config.json`，填入你的 API Key
4. 配置自动热重载生效（也可手动 `/aireload`）
### 单人游戏

模组同样支持**单人游戏**，无需专用服务端：

1. 安装 **Fabric 客户端**（与服务端同一版本 26.2）
2. 将 JAR 放入 `.minecraft/mods/` 文件夹
3. 启动游戏，进入单人存档
4. 配置自动生成于 `config/mcai/config.json`（游戏根目录）
5. 所有命令均可使用，你是 owner 拥有最高权限
6. （可选）安装 [Mod Menu](https://modrinth.com/mod/modmenu) 可在游戏内直接修改配置

> 注意：单机模式下行为审查系统自动关闭。`/aireview` 命令不可用。

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
| `/aiaccept <id>` | 批准待审批指令 |
| `/aireject <id>` | 拒绝待审批指令 |
| `/aiquery` | 查看待审批列表（显示全局唯一 id） |
| `/aiclear` | 清除对话历史 |
| `/aireload` | 手动重载配置（文件修改后自动重载，此命令会清空状态） |
| `/aikb <关键词>` | 搜索知识库 |
| `/aicontrol [chat/review] [on/off]` | 开关 AI 聊天/审查 |
| `/aikill` | 销毁所有 AI 线程 |
| `/aidebug start/stop` | 调试日志 |

### 审查系统

| 命令 | 说明 |
|------|------|
| `/aireview start` | 手动触发审查 |
| `/aireview approve <id>` | 批准踢出 |
| `/aireview reject <id>` | 拒绝踢出 |
| `/aireview last` | 查看上次审查结果 |
| `/aireview last reasoning` | 查看 AI 推理过程 |

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
| 黄牌 | severity -20 或 <= -30 | 公屏警告 |
| 红牌 | severity -30 或 <= -60 | 广播 + 踢出（管理员审批） |

### 分数恢复
- 每轮审查，在线非管理玩家恢复 **5 分**（可配置）
- 上限 **0 分**

### 证据标准
- **多人举报** -> 构成证据
- **单人无佐证** -> 不判罚
- **管理员发言**具有最高效力

### 管理员如何介入
- 游戏里说话带 `[管理员]` 标记，AI 自动信任
- 例如你说"这是无规则PVP服"，AI 就不会判杀人违规

---

## 审批系统

- 危险指令（op、ban、kick 等）需要管理员批准
- AI 阻塞等待审批，3 分钟超时自动取消
- 严格模式下仅白名单安全命令免审批
- AI 直接在聊天文本中输出以 `/` 开头的命令会被自动拦截，不会执行。所有命令执行必须通过 AI 的 Tool 工具发起，确保走统一的审批流程

---

## 完整配置项

文件：`config/mcai/config.json`，修改后自动热重载（也可手动 `/aireload`）。

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
| `aiCooldownSeconds` | 60 | 非管理员调用冷却（秒） |
| `aiMaxConcurrent` | 3 | 最大并发非管理员调用 |
| `reviewIntervalMinutes` | 30 | 审查间隔（分） |
| `yellowCardThreshold` | -30 | 黄牌阈值 |
| `redCardThreshold` | -60 | 红牌阈值 |
| `scoreRecoveryPerInterval` | 5 | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 审批超时（分） |
| `enableOnlineWiki` | `false` | 是否启用 Minecraft Wiki 在线搜索 |
| `wikiLanguage` | `"zh_cn"` | 在线 Wiki 语言：`zh_cn` 中文，`en_us` 英文 |
| `systemPromptPath` | `""` | AI提示词文件路径 |
| `reviewPromptPath` | `""` | 审查提示词文件路径 |

---

## 在线 Wiki 搜索

默认情况下知识库使用内置的本地 JSON。开启在线 Wiki 后，AI 会优先搜索 minecraft.wiki / zh.minecraft.wiki 上的最新原版内容，超时或失败时自动回退到本地知识库。

在 `config/mcai/config.json` 中修改：

```json
{
  "enableOnlineWiki": true,
  "wikiLanguage": "zh_cn"
}
```

- `enableOnlineWiki`：`true` 开启在线 Wiki，`false` 只用本地知识库
- `wikiLanguage`：`zh_cn` 使用中文 Wiki，`en_us` 使用英文 Wiki

> 注意：开启后会向 minecraft.wiki 发送搜索请求，请确保服务器可以访问互联网，并遵守 Wiki 的使用政策。

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
