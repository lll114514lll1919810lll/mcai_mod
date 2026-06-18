<div align="center">

<img src="src/main/resources/assets/mcai/icon.png" width="128" alt="MCAI Logo">

# MCAI - Minecraft AI 助手

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.1.2-blue.svg)](https://fabricmc.net/)
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
| 查看行为分 | `/aiscore` |
| 搜索知识库 | `/aikb 附魔` |
| 管理员审批 | `/aiaccept 1` 批准 / `/aireject 1` 拒绝 |

---

## 核心功能

### AI 对话
- 玩家用 `!ai` 或 `/ai` 和 AI 聊天
- AI 知道服务器里发生了什么（聊天记录、天气、时间等）
- 支持多轮对话，记住上下文

### 自动行为审查
- AI 每 30 分钟自动检查聊天记录
- 三级处罚：扣分 -> 黄牌警告 -> 红牌踢出

### 安全审批
- 危险命令需要管理员手动批准，3 分钟超时自动取消
- 严格模式下仅白名单安全命令免审批

### 游戏知识库
- 内置中文 Minecraft Wiki 核心条目
- 优先在线搜索，失败时回退到本地知识库

---

## 安装

### 你需要准备
- Minecraft **Fabric 服务端 26.1.2**
- [Java](https://www.oracle.com/java/technologies/downloads/) 25
- 一个 [DeepSeek API Key](https://platform.deepseek.com)

### 安装步骤
1. 从 [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) 下载最新版 JAR
2. 放入 `mods/` 文件夹
3. 启动服务端，自动生成配置
4. 编辑 `config/mcai/config.json`，填入 API Key
5. 配置自动热重载，或执行 `/aireload` 手动重载
### 单人游戏使用

模组同样支持**单人游戏**（无需专用服务端）：

1. 安装 **Fabric 客户端**（与服务端同一版本 26.1.2）
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
| `/aiaccept <编号>` | 批准待审批操作 |
| `/aireject <编号>` | 拒绝待审批操作 |
| `/aiquery` | 查看待审批列表 |
| `/aiclear` | 清除 AI 对话历史 |
| `/aireload` | 手动重载配置（配置文件修改后自动重载） |
| `/aikb <关键词>` | 搜索知识库 |

### 审查管理
| 命令 | 说明 |
|------|------|
| `/aicheck start` | 手动触发审查 |
| `/aicheck approve <id>` | 批准踢出 |
| `/aicheck reject <id>` | 拒绝踢出 |
| `/aicheck last` | 查看上次审查结果 |
| `/aicheck last reasoning` | 查看 AI 推理过程 |

---

## 配置

文件位置：`config/mcai/config.json`，修改后自动热重载（也可手动 `/aireload`）。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `apiEndpoint` | `https://api.deepseek.com` | API 地址 |
| `apiKey` | `""` | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名称 |
| `strictMode` | `true` | 严格模式 |
| `aiCooldownSeconds` | `60` | 非管理员 AI 调用冷却（秒） |
| `aiMaxConcurrent` | `3` | 最大并发非管理员 AI 调用 |
| `reviewIntervalMinutes` | `30` | 审查间隔（分钟） |
| `yellowCardThreshold` | `-30` | 黄牌阈值 |
| `redCardThreshold` | `-60` | 红牌阈值 |
| `systemPromptPath` | `""` | AI提示词文件（config/mcai/下） |
| `reviewPromptPath` | `""` | 审查提示词文件 |

提示词文件 `system_prompt.txt` / `review_prompt.txt` 首次启动自动创建。

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

## 链接

- [服主使用手册](USER_GUIDE.md)
- [开发总结](MCAI_MOD_SUMMARY.md)
- [English version](README_EN.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## 许可证

[MIT License](LICENSE)
