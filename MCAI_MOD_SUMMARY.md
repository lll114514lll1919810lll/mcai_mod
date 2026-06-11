# MCAI 模组开发总结与经验教训

> 本文档面向开发者，详细记录了多版本维护的经验和踩坑记录。普通用户请参考 [服主使用手册](USER_GUIDE.md)。

## 目录

1. [项目概述](#1-项目概述)
2. [仓库结构（当前）](#2-仓库结构)
3. [构建配置清单](#3-构建配置清单)
4. [API 差异速查表](#4-api-差异速查表)
5. [关键实现细节](#5-关键实现细节)
6. [踩坑记录](#6-踩坑记录)
7. [工作流程建议](#7-工作流程建议)
8. [2026-06-11 仓库整理血泪史](#8-2026-06-11-仓库整理血泪史)

---

## 1. 项目概述

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（DeepSeek），让 AI 能够读取玩家聊天、执行指令、搜索 Wiki 知识库、审查玩家行为。

### 支持版本

| 分支 | MC 版本 | 映射 | 产物 |
|------|---------|------|------|
| `main` | 1.21.11 | Yarn v2 | `mcai-1.21.11-1.0.0.jar` |
| `mc-26.1.2` | 26.1.2 | Mojang (deobfuscated) | `mcai-26.1.2-1.0.0.jar` |
| `mc-1.21.1` | 1.21 | Yarn v2 | `mcai-1.21.1-1.0.0.jar` |

每个 JAR 同时支持服务端和客户端（含 Mod Menu 配置界面）。

---

## 2. 仓库结构

```
main           ← MC 1.21.11, Yarn 映射, Java 25
mc-26.1.2      ← MC 26.1.2, Mojang 映射, Java 25
mc-1.21.1      ← MC 1.21, Yarn 映射, Java 21
```

**每个分支都是独立完整的代码副本**，互不依赖，互不覆盖。三个分支的 `src/`、`build.gradle`、`gradle.properties` 各不相同，通过 git 分支管理。

**核心原则：**
- 同一个业务逻辑改动需要在三个分支上分别手动同步
- 绝对不能跨分支用 `git checkout <other-branch> -- src/` 混搭源码
- Mojang 和 Yarn 映射体系完全不同，不能共存在一个构建中

---

## 3. 构建配置清单

### 各分支构建参数

| 参数 | main (1.21.11) | mc-26.1.2 | mc-1.21.1 |
|------|---------------|-----------|-----------|
| `minecraft_version` | 1.21.11 | 26.1.2 | 1.21 |
| `yarn_mappings` | 1.21.11+build.1 | 无（Mojang） | 1.21+build.9 |
| `loader_version` | 0.18.4 | 0.19.2 | 0.16.9 |
| `fabric_version` | 0.140.2+1.21.11 | 0.149.1+26.1.2 | 0.100.4+1.21 |
| `loom_version` | 1.15.5 | 1.14.1 | 1.7.2 |
| `Java` | 25 | 25 | 21 |
| `Gradle` | 9.5.1 | 9.5.1 | 8.8 |
| 插件 ID | `fabric-loom` | `net.fabricmc.fabric-loom` | `fabric-loom` |
| 依赖写法 | `modImplementation` | `implementation` | `modImplementation` |
| ModMenu | `modCompileOnly 11.0.1` | `compileOnly 20.0.0-alpha.1` | 同 main |

### 构建命令

```bash
# main (1.21.11)：直接构建
git checkout main
.\gradlew.bat build

# mc-26.1.2：直接构建
git checkout mc-26.1.2
.\gradlew.bat build

# mc-1.21.1：必须设置 JAVA_HOME 为 Java 21
git checkout mc-1.21.1
$env:JAVA_HOME = "C:\Users\Lecoo\AppData\Roaming\.hmcl\java\windows-x86_64\mojang-java-runtime-delta"
.\gradlew.bat build
```

### Loom 版本陷阱

| Loom 版本 | 最低 Gradle | 说明 |
|-----------|-----------|------|
| 1.7.x | 8.8 | MC 1.21 专用，用 `fabric-loom` + Yarn |
| 1.14.x | 9.5+ | MC 26.1.2 专用，用 `net.fabricmc.fabric-loom`，无 Yarn |
| 1.15.x | 9.5+ | MC 1.21.11 专用，用 `fabric-loom` + Yarn |

---

## 4. API 差异速查表

### Mojang (26.1.2) ↔ Yarn (1.21.x) 对照

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

### 1.21.11 ↔ 1.21 Yarn 差异

| 特性 | 1.21.11 | 1.21 |
|------|---------|------|
| 权限构造函数 | `Predicate<ServerCommandSource>` | `int permissionLevel` |
| 权限值 | `LeveledPermissionPredicate.OWNERS` | `4` |
| OP 检查 | `isOperator(PlayerConfigEntry)` | `isOperator(GameProfile)` |
| `PlayerConfigEntry` | 存在 | **不存在** |
| `LeveledPermissionPredicate` | 存在 | **不存在** |

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

## 6. 踩坑记录

### 🔥 坑 1: Mojang 和 Yarn 映射不能混

**症状：** 编译通过的 JAR 在运行时崩溃 `NoClassDefFoundError: net/minecraft/class_2561`。

**原因：** MC 26.1.2 使用 Mojang 映射（无 Yarn intermediary），运行时不存在 `class_2561` 等 Yarn 中继名。从 Yarn 分支 checkout 源码到 Mojang 分支会导致编译通过但运行崩溃。

**修复：** 每个分支维护独立的完整源码，**绝不能用 `git checkout` 跨映射分支拷文件**。

### 🔥 坑 2: `git checkout <branch> -- src/` 会同时覆盖多种文件

**症状：** 只想恢复某个子目录，结果覆盖了所有 `src/` 下的文件（包括 handler、behavior、config）。

**修复：** 指定精确文件路径，如 `git checkout <branch> -- src/main/java/com/example/mcai/client/`。

### 🔥 坑 3: PowerShell `Out-File` 默认加 BOM

**症状：** 用 `git show | Out-File` 创建的文件导致 Gradle 报 `Unexpected character: ''`。

**修复：** 用 Write 工具或 `Set-Content -Encoding UTF8` 代替 `Out-File`。

### 🔥 坑 4: Gradle 版本与 Java 版本不兼容

| Gradle | 最低 Java | 说明 |
|--------|----------|------|
| 8.8 | 不支持 Java 25 | MC 1.21.1 需要 Gradle 8.8，但系统 Java 25 无法运行 |
| 9.5.1 | Java 21+ | MC 1.21.11 / 26.1.2 用这个 |

**修复：** mc-1.21.1 构建时必须设置 `$env:JAVA_HOME` 指向 Java 21。

### 🔥 坑 5: 分支间 gradle-wrapper.properties 必须匹配

**症状：** 切换分支后 Gradle 版本不对，构建失败。

**原因：** `gradle-wrapper.properties` 是 Git 跟踪的，每个分支不同。Loom 1.7.2 需要 Gradle 8.8，Loom 1.14+ 需要 Gradle 9.5+。

**修复：** 用 `git checkout <branch>` 切换时会自动更新，不要手动修改。

### 🔥 坑 6: 26.1.2 客户端必须用 ModMenu 20.0.0-alpha.1

**原因：** ModMenu 11.x 基于 Yarn 映射，26.1.2 是 Mojang 映射。只有 `20.0.0-alpha.1` 版本为 Mojang 编译。

**修复：** `mc-26.1.2` 分支的 build.gradle 用 `compileOnly "com.terraformersmc:modmenu:20.0.0-alpha.1"`。

### 🔥 坑 7: API 版本号必须精确

**症状：** 用 `fabric_version=0.106.1+1.21.1` 导致 `Could not resolve` 错误。

**原因：** Maven 仓库没有这个版本。

**修复：** 用已知存在的版本（从旧 gradle.properties 文件复制），如 `fabric_version=0.100.4+1.21`。

### 🔥 坑 8: 删除分支前必须确认 commit 在 git 历史中

**症状：** 删了分支才发现需要从中恢复代码。

**修复：** 删分支前用 `git log --all --oneline | grep <keyword>` 确认所有提交还在。

### 🔥 坑 9: 部分恢复 commit 导致代码混乱

**症状：** 从 A 提交恢复 src/，从 B 提交恢复个别文件，编译连锁报错。

**修复：** 任何恢复操作**从单个完整提交恢复所有源码**，不做拼凑。

### 🔥 坑 10: `build/libs/` 是所有分支共享的

**症状：** 在 A 分支构建后切到 B 分支，看到的是 A 分支的 JAR。

**修复：** 切分支后执行 `gradle clean build` 确保产物正确。

---

## 7. 工作流程建议

### 日常开发

```bash
# 在 main 上开发新功能
git checkout main
# ... 编码、测试 ...
git commit -m "feat: xxx"
.\gradlew.bat build    # 确认 1.21.11 构建通过

# 手动同步到其他分支
git checkout mc-26.1.2
# 手工移植代码（Yarn → Mojang 手动转换）
.\gradlew.bat build    # 确认 26.1.2 构建通过

git checkout mc-1.21.1
# 手工移植代码（注意 1.21 API 差异：PlayerConfigEntry、LeveledPermissionPredicate）
$env:JAVA_HOME = "路径\Java21"
.\gradlew.bat build    # 确认 1.21 构建通过
```

### 文档类修改

README、LICENSE、USER_GUIDE 等文档在 main 上改完后，用 `git checkout main -- <file>` 同步到其他分支。

### 发布流程

```bash
# 切到每个分支构建，产物在 build/libs/ 下：
main:       mcai-1.21.11-1.0.0.jar
mc-26.1.2:  mcai-26.1.2-1.0.0.jar
mc-1.21.1:  mcai-1.21.1-1.0.0.jar
# 用 gh release upload 上传
```

### 构建验证清单

- [ ] 切到目标分支，确认 `git status` 干净
- [ ] `gradle.properties`：minecraft_version、fabric_version、loom_version 正确
- [ ] `gradle-wrapper.properties`：Gradle 版本与 Loom 匹配
- [ ] `build.gradle`：插件 ID、依赖写法符合该分支的映射类型
- [ ] 环境变量：26.1.2/main 用系统 Java 25，1.21.1 用 `$env:JAVA_HOME` 设 Java 21
- [ ] 运行 `gradle clean build`
- [ ] 检查 `build/libs/mcai-<version>-1.0.0.jar` 存在

---

## 8. 2026-06-11 仓库整理血泪史

> 从多分支合并到单分支再到三分支，踩过的坑全部记录在此，避免后人重蹈覆辙。

### 背景

原始仓库有 6 个分支（`26.1.2-server/client`、`1.21.11-server/client`、`1.21.1-server/client`），分支间频繁互相覆盖文件导致混乱。

### 尝试一：合并所有分支到 main（失败）

**操作：** `git merge 26.1.2-server` → 冲突，`-X theirs` 解决 → 源码变成 Mojang+Yarn 混合。

**失败原因：** 26.1.2 用 Mojang 映射，1.21.11 用 Yarn 映射，同一个 `ChatHandler.java` 在两个分支是不同 API。合并后编译 1.21.11 报 100 个错误。

### 尝试二：versions/ 目录隔离（失败）

**操作：** 创建 `versions/mc-26.1.2/` 目录放 Mojang 源码，用 build-version.bat 脚本切换。

**失败原因：** 只复制了 ChatHandler/MCAIMod，但 ChatReviewSystem、MCAIConfigScreen 等 6+ 个文件都需要转换映射。而且 PowerShell `Out-File` 引入 BOM 导致 Gradle 解析错误。

### 尝试三：mc-26.1.2 客户端反复踩坑（最终成功）

**失败过程：**
1. 从 `7c38d0a` 恢复服务端代码 → 构建成功
2. 从 `458c58b` 恢复客户端代码 → ModConfig 缺方法，编译失败
3. 发现 `458c58b` 的 ModConfig 只有 2 个 getter，`7c38d0a` 有 8 个
4. 从 `458c58b` 单独拉 MCAIConfigScreen → ModMenu 版本不对（用了 11.x 而非 20.0.0-alpha.1）

**最终方案：**
```
7c38d0a (服务端全量源码) + 458c58b (仅 client/ 目录 + fabric.mod.json)
→ 不碰 ModConfig / ChatHandler / ChatReviewSystem
→ build.gradle 加 compileOnly modmenu:20.0.0-alpha.1
→ 构建通过
```

**核心教训：不同提交的代码不能混搭。要么全从 A 提交来，要么全从 B 提交来。只能在一个稳定的完整版本上做最小加法。**

### 最终仓库状态

```
main        → mcai-1.21.11-1.0.0.jar  (Java 25, Yarn, 3 文件需 1.21.1 修复)
mc-26.1.2   → mcai-26.1.2-1.0.0.jar   (Java 25, Mojang, ModMenu 20.0.0-alpha.1)
mc-1.21.1   → mcai-1.21.1-1.0.0.jar   (Java 21, Yarn, PlayerConfigEntry/LeveledPermissionPredicate 修复)
```

### 禁忌清单

| 操作 | 后果 | 正确做法 |
|------|------|---------|
| `git checkout <跨映射分支> -- src/` | Yarn/Mojang 混合，编译或运行崩溃 | 手工跨分支移植代码 |
| `git merge <跨映射分支>` | 同上 | 不合并不同映射的分支 |
| 从一个分支恢复部分源码 | ModConfig/ChatHandler 版本不一致 | 从单个完整提交恢复全部 |
| `Out-File` 写 gradle 文件 | BOM 导致解析失败 | 用 Write 工具或 `Set-Content -Encoding UTF8` |
| 删分支前不检查 git 历史 | 代码丢失 | `git log --all` 确认 |
| 编一个版本不测其他版本 | 返工 n 轮 | 改结构性内容后全量测试 |
