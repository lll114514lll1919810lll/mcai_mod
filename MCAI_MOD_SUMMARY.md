# MCAI 模组开发总结 / Developer Notes

> **中文：** 本文档面向开发者。现已放弃多版本维护，仅支持 MC 26.1.2。
> **English:** Developer documentation. Multi-version support dropped; now only MC 26.1.2.

---

## 项目概述 / Overview

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（DeepSeek），让 AI 能够读取玩家聊天、执行指令、搜索 Wiki 知识库、审查玩家行为。

MCAI is a Fabric server-side mod that connects to OpenAI-compatible APIs to enable AI-powered chat reading, command execution, wiki search, and behavior review.

### 支持版本 / Supported Version

| MC 版本 | 映射 / Mappings | 产物 / Artifact |
|---------|----------------|-----------------|
| 26.1.2 | Mojang | `mcai-<version>.jar` |

---

## 仓库结构 / Repository Structure

单分支 `main`，所有源码在 `src/main/java/` 下。

```
handler/ (7)     — Chat orchestration
behavior/ (8)    — Review & penalty system
api/ (3)         — API clients & result types
kb/ (1)          — Knowledge base
client/ (2)      — Mod Menu config screen
config/ (1)      — Config & prompt loader
root (1)         — MCAIMod (111 lines)
```

### 构建参数 / Build Config

| 参数 | 值 |
|------|-----|
| `minecraft_version` | 26.1.2 |
| `loader_version` | 0.19.2 |
| `fabric_version` | 0.149.1+26.1.2 |
| `loom_version` | 1.14.1 |
| `Java` | 25 |
| `Gradle` | 9.5.1 |

```bash
.\gradlew.bat build
# 产物 / Output: build/libs/mcai-<version>.jar
```

---

## Mojang ↔ Yarn 映射对照 / Mapping Reference

供跨版本移植参考 / For cross-version porting reference:

| Mojang (26.1.2) | Yarn (1.21.x) | 类别 / Category |
|-----------------|---------------|----------------|
| `CommandSourceStack` | `ServerCommandSource` | 类名 / Class |
| `Commands.literal()` | `CommandManager.literal()` | 工厂 / Factory |
| `Component.literal()` | `Text.literal()` | 文本 / Text |
| `ServerPlayer` | `ServerPlayerEntity` | 玩家 / Player |
| `ServerLevel` | `ServerWorld` | 世界 / World |
| `player.level()` | `player.getEntityWorld()` | 世界 / World access |
| `server.getPlayerList()` | `server.getPlayerManager()` | 玩家管理 / Player mgmt |
| `player.sendSystemMessage()` | `player.sendMessage()` | 消息 / Messaging |
| `src.sendFailure()` | `src.sendError()` | 错误反馈 / Error feedback |
| `src.sendSuccess()` | `src.sendFeedback()` | 成功反馈 / Success feedback |
| `player.isRemoved()` | `player.isDisconnected()` | 离线检测 / Disconnect check |
| `player.blockPosition()` | `player.getBlockPos()` | 坐标 / Position |
| `LevelBasedPermissionSet.OWNER` | `LeveledPermissionPredicate.OWNERS` | 权限 / Permission |

---

## 关键实现细节 / Key Implementation Details

### 审批阻塞系统 / Approval Blocking

```java
CompletableFuture<String> future = new CompletableFuture<>();
pendingFutures.put(key, future);
String result = future.get(3, TimeUnit.MINUTES);  // 阻塞等待审批
```

四个操作点 / Four touch points: `executeCommand` (put+get), `approveCommand` (complete), `rejectCommand` (complete), `onPlayerDisconnect` (removeIf).

### 行为审查 / Behavior Review

- 每 30 分钟 AI 分析聊天记录 / AI analyzes chat every 30 min
- 三级处罚：扣分 → 黄牌 → 红牌 / Three-tier: score → yellow → red
- 管理员发言带 `[管理员]` 标记，AI 无条件信任 / Admin messages marked `[管理员]` are trusted
- 每周期恢复 5 分，上限 0 / Recovers 5 pts per cycle, caps at 0

---

## 多版本历史 / Multi-Version History (Archived)

> 2026-06-12 发布 1.2.0 后统一为仅支持 26.1.2。
> After v1.2.0 (2026-06-12), only 26.1.2 is supported.

旧分支 `mc-1.21.1` 和旧 `main`（1.21.11）已删除。备份在 `mc-backup-20260612-143644`。

---

## 踩坑记录 / Pitfalls

### Mojang ↔ Yarn 映射不兼容 / Mapping Incompatibility
编译通过但运行时崩溃 `NoClassDefFoundError`。放弃多版本解决。
Compiles but crashes at runtime. Solved by dropping multi-version.

### ModMenu 版本要求 / ModMenu Version
26.1.2 必须用 `modmenu:20.0.0-alpha.1`（Mojang 编译版本）。

### Gradle 版本 / Gradle Version
Gradle 9.5.1 + Java 25，无兼容问题。

---

## 构建验证清单 / Build Checklist

- [ ] `git status` 干净 / Clean working tree
- [ ] `gradle.properties` 配置正确 / Config correct
- [ ] 运行 `gradle clean build` / Run clean build
- [ ] `build/libs/mcai-<version>.jar` 存在 / Artifact exists
