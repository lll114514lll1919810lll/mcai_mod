# MCAI 模组开发总结

> [English version](MCAI_MOD_SUMMARY_EN.md)

---

## 项目概述

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（DeepSeek），让 AI 能够读取玩家聊天、执行指令、搜索 Wiki 知识库、审查玩家行为。

### 支持版本

| MC 版本 | 映射 | 产物 |
|---------|------|------|
| 26.1.2 | Mojang | `mcai-<version>.jar` |

---

## 仓库结构

单分支 `main`，所有源码在 `src/main/java/` 下。

```
handler/ (7)     — 聊天编排
behavior/ (8)    — 审查与处罚系统
api/ (3)         — API 客户端与结果类型
kb/ (1)          — 知识库
client/ (2)      — Mod Menu 配置界面
config/ (1)      — 配置与提示词加载
root (1)         — MCAIMod (111行)
```

### 构建参数

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
# 产物: build/libs/mcai-<version>.jar
```

---

## Mojang ↔ Yarn 映射对照

供跨版本移植参考：

| Mojang (26.1.2) | Yarn (1.21.x) | 类别 |
|-----------------|---------------|------|
| `CommandSourceStack` | `ServerCommandSource` | 类名 |
| `Commands.literal()` | `CommandManager.literal()` | 工厂 |
| `Component.literal()` | `Text.literal()` | 文本 |
| `ServerPlayer` | `ServerPlayerEntity` | 玩家 |
| `ServerLevel` | `ServerWorld` | 世界 |
| `player.level()` | `player.getEntityWorld()` | 世界访问 |
| `server.getPlayerList()` | `server.getPlayerManager()` | 玩家管理 |
| `player.sendSystemMessage()` | `player.sendMessage()` | 消息 |
| `src.sendFailure()` | `src.sendError()` | 错误反馈 |
| `src.sendSuccess()` | `src.sendFeedback()` | 成功反馈 |
| `player.isRemoved()` | `player.isDisconnected()` | 离线检测 |
| `player.blockPosition()` | `player.getBlockPos()` | 坐标 |
| `LevelBasedPermissionSet.OWNER` | `LeveledPermissionPredicate.OWNERS` | 权限 |

---

## 关键实现细节

### 审批阻塞系统

```java
CompletableFuture<String> future = new CompletableFuture<>();
pendingFutures.put(key, future);
String result = future.get(3, TimeUnit.MINUTES);
```

四个操作点：`executeCommand`（put+get）、`approveCommand`（complete）、`rejectCommand`（complete）、`onPlayerDisconnect`（removeIf）。

### 行为审查

- 每 30 分钟 AI 分析聊天记录
- 三级处罚：扣分 → 黄牌 → 红牌
- 管理员发言带 `[管理员]` 标记，AI 无条件信任
- 每周期恢复 5 分，上限 0

---

## 多版本历史（已归档）

2026-06-12 发布 1.2.0 后仅支持 26.1.2。
旧分支 `mc-1.21.1` 和旧 `main`（1.21.11）已删除。
完整备份在 `mc-backup-20260612-143644`。

---

## 踩坑记录

### Mojang ↔ Yarn 映射不兼容
编译通过但运行时崩溃 `NoClassDefFoundError`。已通过放弃多版本解决。

### ModMenu 版本要求
26.1.2 必须用 `modmenu:20.0.0-alpha.1`（Mojang 编译版本）。

### Gradle 版本
Gradle 9.5.1 + Java 25，无兼容问题。

---

## 构建验证清单

- [ ] `git status` 干净
- [ ] `gradle.properties` 配置正确
- [ ] 运行 `gradle clean build`
- [ ] `build/libs/mcai-<version>.jar` 存在
