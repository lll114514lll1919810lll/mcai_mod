<div align="center">

<img src="src/main/resources/assets/mcai/icon.png" width="128" alt="MCAI Logo">

# MCAI - Minecraft AI 助手

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/technologies/downloads/)

</div>

> [English version](README_EN.md) 

MCAI 是一个 Minecraft Fabric 模组，让 AI 自动管理你的服务器。纯 AI 开发。

**简单来说：** 玩家打 `/ai 帮我查附魔`，AI 回答；有人骂人，AI 自动警告或踢出；危险操作管理员批准才能执行。

---

## 一分钟了解

| 你想做什么 | 怎么用 |
|-----------|--------|
| 和 AI 聊天 | `/ai <问题>` 或 `!ai <问题>` |
| 让 AI 执行命令 | `/ai 给我一把钻石剑`（管理员审批） |
| 让 AI 批量执行命令 | `/ai 帮我建个红石电路`（命令链，一次审批） |
| 查看行为分 | `/aiscore` |
| 搜索知识库 | `/aikb 附魔`（可启用在线 Wiki） |
| 管理员审批 | `/aiaccept <id>` 批准 / `/aireject <id>` 拒绝 |
| 取消待审批命令 | `/aicancel` 取消最近一条 / `/aicancel all` 取消全部 |

---

## 核心功能

### AI 对话
- 玩家用 `!ai` 或 `/ai` 和 AI 聊天
- AI 知道服务器里发生了什么（聊天记录、天气、时间等）
- 支持多轮对话，记住上下文
- **人格模式**：`/aipersona` 可切换 AI 人格，内置村民、猪灵、末影龙、苦力怕等主题人格，支持中英双语，服主可在 `config/mcai/personas/` 添加自定义人格

### 自动行为审查
- AI 每 30 分钟自动检查聊天记录
- 三级处罚：扣分 -> 黄牌警告 -> 红牌踢出

### 安全审批
- 危险命令需要管理员手动批准，3 分钟超时自动取消
- 严格模式下仅白名单安全命令免审批
- AI 直接输出以 `/` 开头的文本命令会被拦截，所有命令必须通过 Tool 执行，走统一审批流程
- **命令链**：AI 可将多条命令打包为一个审批单元（`execute_command_chain`），管理员一次批准即可全部执行，支持设置命令间执行间隔
- **玩家取消**：玩家可用 `/aicancel` 主动取消自己发起的待审批命令，AI 会收到取消通知并不再尝试相同命令

### 游戏知识库
- 内置中文 Minecraft Wiki 核心条目
- AI 优先在线搜索 minecraft.wiki / zh.minecraft.wiki（`wikiLanguage` 控制语言），失败或超时自动回退本地知识库
- 自定义知识库：将 JSON 文件放入 `config/mcai/kb/` 即可自动加载

---

## 安装

### 你需要准备
- Minecraft **Fabric 服务端 26.2**
- [Java](https://www.oracle.com/java/technologies/downloads/) 25
- 一个 [DeepSeek API Key](https://platform.deepseek.com)

### 安装步骤
1. 从 [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) 下载最新版 JAR
2. 放入 `mods/` 文件夹
3. 启动服务端，自动生成配置
4. 编辑 `config/mcai/config.json`，填入 API Key
5. 配置自动热重载，或执行 `/aireload` 手动重载

### 客户端资源包（可选但推荐）

如果玩家客户端**未安装 MCAI mod**，AI 相关的翻译文本将无法显示。我们提供了专用语言资源包：

1. 从 [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) 下载 `mcai-lang-pack.zip`
2. 放入 `.minecraft/resourcepacks/` 文件夹
3. 在游戏中启用该资源包

> 服主也可在 `server.properties` 中配置强制下载（见 [服主使用手册](USER_GUIDE.md)）。

### 双端装载推荐

**强烈建议服务端和客户端都安装 MCAI mod**，原因：
- 客户端安装后自动获得所有翻译文本，无需额外资源包
- 客户端可使用 Mod Menu 在游戏内直接修改配置
- 审批通知中的可点击按钮（批准/拒绝/取消）体验更好
- 服务端负责 AI 逻辑和命令执行，客户端负责 UI 展示，职责清晰

### 单人游戏使用

模组同样支持**单人游戏**（无需专用服务端）：

1. 安装 **Fabric 客户端**（与服务端同一版本 26.2）
2. 将 JAR 放入 `.minecraft/mods/` 文件夹
3. 启动游戏，进入单人世界
4. 配置文件和命令与服务端完全一致
5. 你是世界 owner，默认拥有所有管理权限
6. （可选）安装 [Mod Menu](https://modrinth.com/mod/modmenu) 可在游戏内直接修改配置

> 提示：单人模式下行为审查系统自动关闭（单人无需审查）。

---

## 命令一览

### 玩家命令
| 命令 | 说明 |
|------|------|
| `!ai <消息>` `/ai <消息>` | 和 AI 聊天 |
| `/aiscore` | 查看行为分 |

### 管理员命令
| 命令 | 说明 |
|------|------|
| `/aiaccept <id>` | 批准待审批操作（支持单条命令和命令链） |
| `/aireject <id>` | 拒绝待审批操作（支持单条命令和命令链） |
| `/aiquery` | 查看待审批列表（显示全局唯一 id） |
| `/aicancel [id/all]` | 取消待审批命令（玩家可用，管理员也可用） |
| `/aiclear` | 清除 AI 对话历史 |
| `/aireload` | 手动重载配置（配置文件修改后自动重载） |
| `/airesetprompts` | 重置提示词文件为当前内置默认 |
| `/aikb <关键词>` | 搜索知识库 |
| `/aicontrol [chat/review] [on/off]` | 开关 AI 聊天/审查 |
| `/aikill` | 销毁所有 AI 线程 |
| `/aidebug start/stop/show/list/clear` | 调试日志 |
| `/aipersona [list/set/current/view/reload]` | 切换/查看/重载 AI 人格 |

### 审查管理
| 命令 | 说明 |
|------|------|
| `/aireview start` | 手动触发审查 |
| `/aireview approve <id>` | 批准踢出 |
| `/aireview reject <id>` | 拒绝踢出 |
| `/aireview last` | 查看上次审查结果 |
| `/aireview last reasoning` | 查看 AI 推理过程 |

---

## 配置

文件位置：`config/mcai/config.json`，修改后自动热重载（也可手动 `/aireload`）。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名称 |
| `triggerPrefix` | `!ai` | 聊天触发前缀 |
| `maxTokens` | 2048 | AI 回复最大 token |
| `temperature` | 0.75 | 回复随机性（0-1） |
| `thinkingLevel` | 1 | 思考模式 0-3 |
| `strictMode` | `true` | 严格模式 |
| `aiCooldownSeconds` | `60` | 非管理员 AI 调用冷却（秒） |
| `aiMaxConcurrent` | `3` | 最大并发非管理员 AI 调用 |
| `compatibilityMode` | `false` | 兼容模式，开启后只发送基础字段，适配 LM Studio 等本地 API |
| `reviewIntervalMinutes` | `30` | 审查间隔（分钟） |
| `yellowCardThreshold` | `-30` | 黄牌阈值 |
| `redCardThreshold` | `-60` | 红牌阈值 |
| `scoreRecoveryPerInterval` | `5` | 每周期恢复分数 |
| `approvalTimeoutMinutes` | 10 | 审批超时（分） |
| `wikiLanguage` | `"zh_cn"` | AI 在线搜索语言：`zh_cn` 中文，`en_us` 英文（在线搜索默认开启） |
| `apiConnectTimeoutSeconds` | 10 | API 连接超时（秒） |
| `apiRequestTimeoutSeconds` | 60 | API 单次请求超时（秒） |
| `apiLoopTimeoutSeconds` | 300 | 工具调用循环总超时（秒） |
| `commandExecTimeoutSeconds` | 30 | 单条命令执行超时（秒） |
| `maxChainCommands` | 10 | 命令链最大条数 |
| `contextMaxChars` | 20000 | AI 上下文最大字符数（超出自动截断） |
| `maxToolCalls` | 15 | 单轮对话最大工具调用次数 |
| `activePersona` | `"default"` | 当前人格 ID |
| `personaLanguage` | `""` | 人格语言覆盖，空=自动跟随客户端 |
| `promptLanguage` | `"zh_cn"` | 内置提示词语言 |
| `enableChatInterception` | `true` | 是否拦截聊天转交 AI |
| `enableCommandExecution` | `true` | 是否允许 AI 执行命令 |
| `enableAutoReview` | `true` | 是否启用自动行为审查 |
| `maxReviewCycles` | 4 | 审查最大分析轮数 |
| `systemPromptPath` | `""` | AI提示词文件（config/mcai/下） |
| `reviewPromptPath` | `""` | 审查提示词文件 |
| `reviewApiEndpoint` | `""` | 审查系统独立 API 地址，空=跟随聊天系统 |
| `reviewApiKey` | `""` | 审查系统独立 API 密钥，空=跟随聊天系统 |
| `reviewModel` | `""` | 审查系统独立模型，空=跟随聊天系统 |

提示词文件 `system_prompt.txt` / `review_prompt.txt` 首次启动自动创建。运行 `/airesetprompts` 可强制同步到当前内置最新版本。

---


### 知识库导入

知识库文件放置在 config/mcai/kb/ 下即可：

1. 从 [kb/](kb/README.md) 目录下载 .json 文件
2. 放入 config/mcai/kb/（首次启动自动创建）
3. 自动热重载生效（或手动 /aireload）

可用文件及许可证见 [kb/README.md](kb/README.md)。
自爬工具：[tools/wiki_to_kb.py](tools/wiki_to_kb.py)。

## 构建

```bash
git clone https://github.com/lll114514lll1919810lll/mcai_mod.git
cd mcai_mod
.\gradlew.bat build
# 产物: build/libs/mcai-<version>.jar
```

需要 JDK 25。

---

## 下载

### 稳定版（推荐）

从 GitHub Releases 下载经过测试的稳定版本：

[![Releases](https://img.shields.io/github/v/release/lll114514lll1919810lll/mcai_mod?label=Latest&logo=github)](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

### 测试版

从 GitHub Releases 下载 Pre-release 版本（功能较新，可能存在 bug）：

[![Pre-release](https://img.shields.io/github/v/release/lll114514lll1919810lll/mcai_mod?include_prereleases&label=Beta&logo=github)](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

### 开发版（Nightly）

从仓库的 `nightly-builds/` 目录下载最新开发构建（未经充分测试，仅供尝鲜）：

```
nightly-builds/<MC_VERSION>/<MOD_VERSION>/mcai-<MC_VERSION>-<MOD_VERSION>.jar
```

示例：`nightly-builds/26.1.2/1.7.0-beta.4-alpha.1/mcai-26.1.2-1.7.0-beta.4-alpha.1.jar`

> ⚠️ 开发版可能包含未完成的功能和 bug，仅建议开发者或愿意反馈问题的用户使用。

---

## 链接

- [服主使用手册](USER_GUIDE.md)
- [English version](README_EN.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## 许可证

[MIT License](LICENSE)
