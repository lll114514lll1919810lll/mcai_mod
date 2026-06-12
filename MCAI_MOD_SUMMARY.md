# MCAI 模组开发总结与经验教训

> 本文档面向开发者。现已放弃多版本维护，仅支持 MC 26.1.2。历史多版本记录保留供参考。

## 目录

1. [项目概述](#1-项目概述)
2. [仓库结构](#2-仓库结构)
3. [关键实现细节](#3-关键实现细节)
4. [多版本历史参考](#4-多版本历史参考)
5. [踩坑记录](#5-踩坑记录)

---

## 1. 项目概述

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（DeepSeek），让 AI 能够读取玩家聊天、执行指令、搜索 Wiki 知识库、审查玩家行为。

### 支持版本

| MC 版本 | 映射 | 产物 |
|---------|------|------|
| 26.1.2 | Mojang (deobfuscated) | `mcai-<version>.jar` |

JAR 同时支持服务端和客户端（含 Mod Menu 配置界面）。

---

## 2. 仓库结构

```
main           ← MC 26.1.2, Mojang 映射, Java 25
```

单分支，所有源码在 `src/main/java/` 下。

### 源码文件（23 个 Java 文件）

```
handler/ (7)     — ChatHandler, ChatLog, ThinkingAnimation, PlayerContextBuilder,
                   CommandExecutionService, ToolDispatcher, CommandRegistry
behavior/ (8)    — ChatReviewSystem, PenaltyEvent, PenaltyHistory, ReviewEngine,
                   ReviewCommandRegistry, AdminApprovalQueue,
                   PlayerBehaviorTracker, PlayerViolation
api/ (3)         — OpenAIClient, WikiSearchClient, ApiResult
kb/ (1)          — KnowledgeBase
client/ (2)      — ModMenuIntegration, MCAIConfigScreen
config/ (1)      — ModConfig
root (1)         — MCAIMod (111行)
```

### 构建参数

| 参数 | 值 |
|------|-----|
| `minecraft_version` | 26.1.2 |
| `yarn_mappings` | 无（Mojang） |
| `loader_version` | 0.19.2 |
| `fabric_version` | 0.149.1+26.1.2 |
| `loom_version` | 1.14.1 |
| `Java` | 25 |
| `Gradle` | 9.5.1 |
| 插件 ID | `net.fabricmc.fabric-loom` |
| 依赖写法 | `implementation` |
| ModMenu | `compileOnly 20.0.0-alpha.1` |

### 构建命令

```bash
.\gradlew.bat build
# 产物: build/libs/mcai-<version>.jar
```

---

## 3. 关键实现细节

| Mojang (26.1.2) | Yarn (1.21.x) | 类别 |
|-----------------|---------------|------|
| `CommandSourceStack` | `ServerCommandSource` | 类名 |
| `Commands.literal()` | `CommandManager.literal()` | 工厂类 |
| `Component.literal()` | `Text.literal()` | 文本 |
| `ServerPlayer` | `ServerPlayerEntity` | 玩家实体 |
| `ServerLevel` | `ServerWorld` | 世界 |
| `player.getScoreboardName()` | `player.getNameForScoreboard()` | 方法 |
| `player.level()` | `player.getEntityWorld()` | 世界 |
| `server.getPlayerList()` | `server.getPlayerManager()` | 玩家管理 |
| `player.sendSystemMessage()` | `player.sendMessage()` | 消息 |
| `src.sendFailure()` | `src.sendError()` | 错误反馈 |
| `src.sendSuccess()` | `src.sendFeedback()` | 成功反馈 |
| `player.isRemoved()` | `player.isDisconnected()` | 离线检测 |
| `player.getUUID()` | `player.getUuid()` | UUID |
| `player.blockPosition()` | `player.getBlockPos()` | 坐标 |
| `player.getYRot()` | `player.getYaw()` | 朝向 |
| `player.getFoodData()` | `player.getHungerManager()` | 饱食度 |
| `player.gameMode` | `player.interactionManager` | 游戏模式 |
| `server.getCommands()` | `server.getCommandManager()` | 命令系统 |
| `server.createCommandSourceStack()` | `server.getCommandSource()` | 命令源 |
| `LevelBasedPermissionSet.OWNER` | `LeveledPermissionPredicate.OWNERS` | 权限 |

## 多版本历史参考

> 旧版多分支结构（main=1.21.11、mc-26.1.2、mc-1.21.1）已废弃。
> 2026-06-12 发布 1.2.0 后统一为仅支持 26.1.2 的单分支。
> 备份含完整仓库历史和所有分支，需要时从 `mc-backup-20260612-143644` 恢复。

---

## 5. 关键实现细节

### 审批阻塞系统 (pendingFutures)

```java
if (needsApproval(command)) {
    CompletableFuture<String> future = new CompletableFuture<>();
    pendingFutures.put(key, future);
    notifyAdminsPending(...);
    try {
        String result = future.get(3, TimeUnit.MINUTES);  // 阻塞 AI 线程
        return result != null ? result : "指令已执行";
    } catch (TimeoutException e) {
        return "[审批超时] 3分钟内无人批准，指令已自动取消";
    }
}
```

四个操作点：`executeCommand`（put+get）、`approveCommand`（complete）、`rejectCommand`（complete）、`onPlayerDisconnect`（removeIf）。

### 行为审查

- 每 30 分钟 AI 分析聊天记录
- 三级处罚：扣分 (-10) → 黄牌 (-30/阈值) → 红牌 (-60/阈值)
- 管理员发言带 `[管理员]` 标记，AI 无条件信任
- 每周期自动恢复 5 分，最多恢复到 0

---

## 踩坑记录

> 以下踩坑记录主要为多版本维护时期的历史，现已统一为单分支（26.1.2），部分问题不再适用。

### 坑 1: Mojang 和 Yarn 映射不能混
编译通过的 JAR 在运行时崩溃 `NoClassDefFoundError`。已通过放弃多版本解决。

### 坑 2: PowerShell `Out-File` 默认加 BOM
用 `Write` 工具或 `Set-Content -Encoding UTF8` 代替。

### 坑 3: Gradle 版本与 Java 版本兼容性
当前使用 Gradle 9.5.1 + Java 25，无兼容问题。

### 坑 4: 26.1.2 客户端必须用 ModMenu 20.0.0-alpha.1
`build.gradle` 已配置 `compileOnly "com.terraformersmc:modmenu:20.0.0-alpha.1"`。

### 坑 5: `build/libs/` 跨分支共享
已统一为单分支，无此问题。

---

## 构建验证清单

- [ ] `git status` 干净
- [ ] `gradle.properties` 配置正确
- [ ] 运行 `gradle clean build`
- [ ] 检查 `build/libs/mcai-<version>.jar` 存在

---

## 2026-06-11 仓库整理血泪史

> 从多分支合并到单分支再到三分支最终回单分支。旧历史见备份。

### 最终状态（2026-06-12）

```
main → mcai-26.1.2-<version>.jar (Java 25, Mojang, 单分支)
```

1.21.x 版本以 v1.2.0 为最终版发布，源码在备份中存档。
