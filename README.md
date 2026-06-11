# MCAI - Minecraft AI 助手

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-1.21+-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2F25-orange.svg)](https://www.java.com/)

MCAI 是一个 Minecraft Fabric 模组，让 AI 自动管理你的服务器/在单人客户端提供帮助。纯 AI 开发。

**简单来说：** 玩家在聊天里打 `/ai 帮我查一下怎么附魔钻石剑`，AI 就会回答；有人骂人，AI 会自动警告或踢出；危险操作（比如删档）需要你（管理员）手动批准。

---

## 一分钟了解

| 你想做什么 | 怎么用 |
|-----------|--------|
| 和 AI 聊天 | 游戏里打 `/ai <你的问题>` 或`!ai <你的问题>`|
| 让 AI 执行命令 | `/ai 给我一把钻石剑`（可能需管理员审批） |
| 查看自己的行为分 | `/aiscore` |
| 搜索游戏知识库 | `/aikb 附魔` |
| 管理员审批命令 | `/aiaccept 1` 批准，`/aireject 1` 拒绝 |

---

## 核心功能

### AI 对话
- 玩家用 `!ai` 或 `/ai` 和 AI 聊天
- AI 知道服务器里发生了什么（聊天记录、天气、时间等）
- 支持多轮对话，记住上下文

### 自动行为审查
- AI 每 30 分钟自动检查聊天记录
- 发现骂人、捣乱等行为自动处罚
- 三级处罚：扣分 → 黄牌警告 → 红牌踢出
- 管理员说话 AI 会特别重视（比如你说"这是 PVP 服"，AI 就不会判杀人违规）

### 安全审批
- 危险命令（op、ban、kick 等）不会直接执行
- 需要管理员手动批准，3 分钟超时自动取消
- 默认开启严格模式，只有安全命令能直接执行

### 游戏知识库
- 内置中文 Minecraft Wiki 核心条目（目前数据截至2026/6/10）
- 玩家可以问 AI 游戏问题，AI 自动搜索知识库回答

---

## 安装教程

### 你需要准备
- Minecraft **Fabric 客户/服务端**（支持 26.1.2、1.21.11、1.21/1.21.1）
- [Java](https://www.java.com/) 25（26.1.2、1.21.11）或 Java 21（1.21/1.21.1）
- 一个 [DeepSeek API Key](https://platform.deepseek.com)

### 安装步骤
1. 下载最新版 `mcai-x.x.x.jar`（在 [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) 页面，提供客户端与服务端双版本）
2. 把 JAR 文件放到客户端/服务器的 `mods/` 文件夹里
3. 启动客户端/服务器，会自动生成配置文件
4. 打开 `config/mcai/config.json`，填入你的 API Key：
   ```json
   {
     "apiEndpoint": "https://api.deepseek.com",
     "apiKey": "sk-你的密钥",
     "model": "deepseek-v4-flash"
   }
   ```
   - 对于客户端，如果安装了 [Mod Menu](https://modrinth.com/mod/modmenu) ，还可以直接在模组菜单完成大部分配置。
5. 重启客户端/服务器或输入 `/aireload` ，完成！

---

## 命令一览

### 玩家命令
| 命令 | 说明 |
|------|------|
| `!ai <消息>` 或 `/ai <消息>` | 和 AI 聊天 |
| `/aiscore` | 查看自己的行为分 |

### 管理员命令
| 命令 | 说明 |
|------|------|
| `/aiaccept <编号>` | 批准待审批的操作 |
| `/aireject <编号>` | 拒绝待审批的操作 |
| `/aiquery` | 查看待审批列表 |
| `/aiclear` | 清除 AI 对话历史 |
| `/aireload` | 重载配置 |
| `/aikb <关键词>` | 搜索知识库 |

### 审查管理
| 命令 | 说明 |
|------|------|
| `/aicheck` | 手动触发一次审查 |
| `/aicheck approve <id>` | 批准踢出 |
| `/aicheck reject <id>` | 拒绝踢出 |
| `/aicheck last` | 查看上次审查结果 |
| `/aicheck last reasoning` | 查看 AI 推理过程 |

---

## 配置说明

配置文件：`config/mcai/config.json`，修改后用 `/aireload` 重载。

### 常用配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名称 |
| `strictMode` | `true` | 严格模式（推荐开启） |
| `reviewIntervalMinutes` | `30` | 审查间隔（分钟） |
| `yellowCardThreshold` | `-30` | 黄牌阈值（越高越严） |
| `redCardThreshold` | `-60` | 红牌阈值（越高越严） |

<details>
<summary>完整配置项</summary>

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | `2048` | AI 回复最大长度 |
| `temperature` | `0.75` | 回复随机性（0=严谨，1=创意） |
| `thinkingLevel` | `1` | DeepSeek 思考模式：0=关，1=开，3=最强 |
| `maxToolCalls` | `15` | 单次对话最多调用工具次数 |
| `contextMaxChars` | `20000` | 对话上下文最大字符数 |
| `safeCommands` | `["locate","seed",...]` | 严格模式白名单 |
| `requireApprovalCommands` | `["op","ban",...]` | 必须审批的命令 |
| `enableChatInterception` | `true` | 启用聊天拦截 |
| `enableCommandExecution` | `true` | 允许 AI 执行命令 |
| `scoreRecoveryPerInterval` | `5` | 每周期恢复分数 |
| `approvalTimeoutMinutes` | `10` | 踢出审批超时（分钟） |
| `enableAutoReview` | `true` | 启用自动审查 |
| `maxReviewCycles` | `4` | 处罚记录保留轮次 |

</details>

---

## 常见问题

**Q: AI 会不会乱执行命令搞坏服务器？**
不会。默认开启严格模式，只有白名单里的安全命令能直接执行，危险操作需要你手动批准。

**Q: 审查太严/太松怎么办？**
调整 `yellowCardThreshold` 和 `redCardThreshold`，数值越大越严格。

**Q: 能用其他 AI 服务吗？**
能。修改 `apiEndpoint` 为任何 OpenAI 兼容接口（OpenAI、通义千问、GLM 等）。

**Q: 费用高吗？**
DeepSeek 非常便宜，日常使用每月几块钱。审查 30 分钟才调用一次也可以修改频率。

---

## 技术文档

- [服主使用手册](USER_GUIDE.md) - 详细的配置和使用说明
- [开发总结](MCAI_MOD_SUMMARY.md) - 多版本维护经验和踩坑记录（AI踩过的坑哦~）

## 许可证

[MIT License](LICENSE)
