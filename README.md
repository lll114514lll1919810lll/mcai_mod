<div align="center">

<img src="src/main/resources/assets/mcai/icon.png" width="128" alt="MCAI Logo">

# MCAI - Minecraft AI 助手 / AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.1.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.java.com/)

</div>

**中文：** MCAI 是一个 Minecraft Fabric 模组，让 AI 自动管理你的服务器。纯 AI 开发。

**English:** MCAI is a Fabric mod that lets AI manage your Minecraft server. This entire project is AI-developed.

**简单来说 / TL;DR:** 玩家打 `/ai 帮我查附魔`，AI 回答；有人骂人，AI 自动警告或踢出；危险操作管理员批准才能执行。

---

## 一分钟了解 / Quick Start

| 你想做什么 / What | 怎么用 / How |
|------------------|-------------|
| 和 AI 聊天 / Chat with AI | `/ai <问题>` 或 `!ai <问题>` |
| 让 AI 执行命令 / AI executes commands | `/ai 给我一把钻石剑`（管理员审批） |
| 查看行为分 / Check score | `/aiscore` |
| 搜索知识库 / Search wiki | `/aikb 附魔` |
| 管理员审批 / Admin approve | `/aiaccept 1` 批准 / `/aireject 1` 拒绝 |

---

## 核心功能 / Features

### AI 对话 / AI Chat
- 玩家用 `!ai` 或 `/ai` 和 AI 聊天
- AI 知道服务器里发生了什么（聊天记录、天气、时间等）
- 支持多轮对话，记住上下文
- Players can chat with AI; AI has full server context awareness

### 自动行为审查 / Auto Behavior Review
- AI 每 30 分钟自动检查聊天记录
- 三级处罚：扣分 → 黄牌警告 → 红牌踢出
- Three-tier penalty: score deduction → yellow card → red card (kick)

### 安全审批 / Admin Approval
- 危险命令需要管理员手动批准，3 分钟超时自动取消
- 严格模式下仅白名单安全命令免审批
- Dangerous commands require admin approval; 3-min timeout auto-cancels

### 游戏知识库 / Game Knowledge Base
- 内置中文 Minecraft Wiki 核心条目
- 优先在线搜索，失败时回退到本地知识库
- Built-in Chinese Minecraft Wiki; online search with local fallback

---

## 安装 / Installation

### 你需要准备 / Requirements
- Minecraft **Fabric 服务端 26.1.2**
- [Java](https://www.java.com/) 25
- 一个 [DeepSeek API Key](https://platform.deepseek.com)（或其他 OpenAI 兼容 API）

### 安装步骤 / Steps
1. 从 [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) 下载最新版 JAR
2. 放入 `mods/` 文件夹
3. 启动服务端，自动生成配置文件
4. 编辑 `config/mcai/config.json`，填入 API Key
5. 执行 `/aireload` 重载配置

---

## 命令一览 / Commands

### 玩家命令 / Player Commands
| 命令 | 说明 | Description |
|------|------|-------------|
| `!ai <消息>` `/ai <消息>` | 和 AI 聊天 | Chat with AI |
| `/aiscore` | 查看行为分 | Check behavior score |

### 管理员命令 / Admin Commands
| 命令 | 说明 | Description |
|------|------|-------------|
| `/aiaccept <编号>` | 批准待审批操作 | Approve pending action |
| `/aireject <编号>` | 拒绝待审批操作 | Reject pending action |
| `/aiquery` | 查看待审批列表 | List pending approvals |
| `/aiclear` | 清除 AI 对话历史 | Clear AI chat history |
| `/aireload` | 重载配置 | Reload config |
| `/aikb <关键词>` | 搜索知识库 | Search knowledge base |

### 审查管理 / Review Management
| 命令 | 说明 | Description |
|------|------|-------------|
| `/aicheck` | 手动触发审查 | Trigger review manually |
| `/aicheck approve <id>` | 批准踢出 | Approve kick |
| `/aicheck reject <id>` | 拒绝踢出 | Reject kick |
| `/aicheck last` | 查看上次审查结果 | View last review result |
| `/aicheck last reasoning` | 查看 AI 推理过程 | View AI reasoning |

---

## 配置 / Configuration

文件位置 / File: `config/mcai/config.json`，修改后用 `/aireload` 重载。

| 配置项 | 默认值 | 说明 / Description |
|--------|--------|-------------------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 / API endpoint |
| `apiKey` | `""` | API 密钥 / API key |
| `model` | `deepseek-v4-flash` | 模型名称 / Model name |
| `strictMode` | `true` | 严格模式 / Strict mode |
| `reviewIntervalMinutes` | `30` | 审查间隔（分钟） / Review interval |
| `yellowCardThreshold` | `-30` | 黄牌阈值 / Yellow card threshold |
| `redCardThreshold` | `-60` | 红牌阈值 / Red card threshold |
| `systemPromptPath` | `""` | AI提示词文件 / System prompt file |
| `reviewPromptPath` | `""` | 审查提示词文件 / Review prompt file |
| `promptLanguage` | `zh_cn` | 内置提示词语 / Prompt language |

提示词文件默认可通过 `config/mcai/system_prompt.txt` 和 `review_prompt.txt` 覆盖，首次启动自动创建。
Prompts can be customized via txt files in `config/mcai/`, auto-created on first start.

---

## 构建 / Build

```bash
git clone https://github.com/lll114514lll1919810lll/mcai_mod.git
cd mcai_mod
.\gradlew.bat build
# 产物 / Output: build/libs/mcai-<version>.jar
```

需要 JDK 25。

---

## 链接 / Links

- [服主使用手册 / User Guide (中文)](USER_GUIDE.md)
- [开发总结 / Developer Notes (中文)](MCAI_MOD_SUMMARY.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## 许可证 / License

[MIT License](LICENSE)
