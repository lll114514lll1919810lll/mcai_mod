# MCAI 模组开发总结与经验教训

> 本文档面向开发者，详细记录了多版本维护的经验和踩坑记录。普通用户请参考 [服主使用手册](USER_GUIDE.md)。

## 目录

1. [项目概述](#1-项目概述)
2. [分支与版本矩阵](#2-分支与版本矩阵)
3. [构建配置清单](#3-构建配置清单)
4. [API 差异速查表](#4-api-差异速查表)
5. [关键实现细节](#5-关键实现细节)
6. [踩坑记录](#6-踩坑记录)
7. [工作流程建议](#7-工作流程建议)

---

## 1. 项目概述

MCAI 是一个 Fabric 服务端模组，接入 OpenAI 兼容 API（DeepSeek），让 AI 能够读取玩家聊天、执行指令、搜索 Wiki 知识库、审查玩家行为。

### 支持版本

| MC 版本 | 服务端 | 客户端 |
|---------|--------|--------|
| 26.1.2 (deobfuscated) | ✅ | ✅ |
| 1.21.11 (Yarn) | ✅ | ✅ |
| 1.21 / 1.21.1 (Yarn) | ✅ | ✅ |

### 核心功能

- AI 对话 (`/ai`, `!ai` 前缀)
- Function Calling 工具（知识库搜索、指令执行、服务器状态、调试信息）
- 指令审批系统（`pendingFutures` 阻塞等待 + 超时自动取消）
- 北京时间戳聊天记录
- 管理员身份检查（`isAdminOrConsole`/`isAdminPlayer`）
- 控制台 AI 查询支持（`handleConsoleAIQuery`）
- 管理员通知（`notifyAdminsPending`）
- 行为审查系统（服务端，自动 AI 审查聊天记录、扣分、黄牌/红牌）
- Mod Menu 配置界面（客户端）
- 思考动画（ActionBar）

---

## 2. 仓库结构（单分支 + 版本目录）

### 当前结构

```
main (唯一分支)
├── src/                    ← 主源码 (MC 1.21.11, Yarn 映射)
├── versions/
│   └── mc-26.1.2/         ← MC 26.1.2 版本特定文件
│       ├── build.gradle
│       ├── gradle.properties
│       ├── fabric.mod.json
│       └── src/            ← 26.1.2 的 Java 源码 (Mojang 映射)
├── build-version.bat       ← 多版本构建脚本
├── build.gradle            ← 主构建配置 (1.21.11)
└── gradle.properties       ← 主版本属性 (1.21.11)
```

### 构建命令

```bash
# 构建 MC 1.21.11（默认，直接用当前源码）
.\gradlew.bat build

# 构建 MC 26.1.2（脚本自动替换文件再构建）
.\build-version.bat 26.1.2
```

### 版本差异

| 差异项 | MC 1.21.11 (main) | MC 26.1.2 (versions/) |
|--------|-------------------|----------------------|
| 映射类型 | Yarn v2 | Mojang (deobfuscated) |
| Java | 21+ | 25 |
| Loom | 1.15.5 | 1.14.1 |
| 玩家类 | `ServerPlayerEntity` | `ServerPlayer` |
| 命令源 | `ServerCommandSource` | `CommandSourceStack` |
| 文本 API | `Text.literal()` | `Component.literal()` |
| TPS | 不支持 | `getCurrentSmoothedTickTime()` |
| F3 信息 | 不支持 | 完整实现 |
| 游戏规则 | 不支持 | 完整实现 |

---

## 3. 构建配置清单

### 版本-工具链映射表

| MC 版本 | Loom | Gradle | Java | 构建任务 | 映射类型 |
|---------|------|--------|------|----------|---------|
| 26.1.2 | 1.14.1 | 9.5.1+ | 25 | `build` | 无（deobfuscated） |
| 1.21.11 | 1.15.5 | 9.5.1+ | 21 | `build` 或 `remapJar` | Yarn v2 |
| 1.21 / 1.21.1 | 1.7.2 | 8.8 | 21 | `build` 或 `remapJar` | Yarn v2 |

### build.gradle 关键差异

**26.1.2（无混淆映射）：**
```gradle
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
// 无 mappings 依赖！
// Java 25
```

**1.21.x / 1.21.1（Yarn 映射）：**
```gradle
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"  // 必须有
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}
// Java 21，需要 TerraformersMC Maven 仓库放 Mod Menu
repositories {
    maven { url = 'https://maven.terraformersmc.com/' }
}
```

### Loom 版本陷阱

| Loom 版本 | Gradle 兼容性 | 说明 |
|-----------|--------------|------|
| 1.7.x | 8.8 | 旧 API，`remapJar` 可用 |
| 1.14.x | 9.5.1+ | 26.1.2 专用，`remapJar` 不可用（用 `build`） |
| 1.15.x | 9.5.1+ | 需 `plugins { id 'fabric-loom' }` 格式 |

**Loom 1.7.x 的 build.gradle 不能用 `implementation` 代替 `modImplementation`，否则会有 `Access widener namespace` 错误。**

### gradle-wrapper.properties 必须在每个分支上正确设置

`gradle-wrapper.properties` 是 Git 跟踪的，每个分支需要不同的 Gradle 版本。切换分支后必须检查：

```bash
# 1.21.11（Loom 1.15.5）
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip

# 1.21/1.21.1（Loom 1.7.2）
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
```

---

## 4. API 差异速查表

### 26.1.2 (deobfuscated) ↔ 1.21.x (Yarn) 对照

| 26.1.2 名称 | 1.21.x / 1.21.1 Yarn | 备注 |
|-------------|----------------------|------|
| `CommandSourceStack` | `ServerCommandSource` | 类名不同 |
| `Commands.literal()` | `CommandManager.literal()` | 工厂类不同 |
| `Component.literal()` | `Text.literal()` | 文本组件不同 |
| `ServerPlayer` | `ServerPlayerEntity` | 实体类不同 |
| `ServerLevel` | `ServerWorld` | 世界类不同 |
| `player.getScoreboardName()` | `player.getNameForScoreboard()` | 方法名不同 |
| `player.level()` | `player.getEntityWorld()` | 获取世界方法不同 |
| `server.getPlayerList()` | `server.getPlayerManager()` | 玩家管理器不同 |
| `broadcastSystemMessage()` | `broadcast()` | 广播方法不同 |
| `sendSystemMessage()` | `sendMessage()` | 发送消息方法不同 |
| `player.isRemoved()` | `player.isDisconnected()` | 断线检测方法不同 |
| `.level().dimension()` | `.getEntityWorld().getRegistryKey()` | 维度标识不同 |
| `getBrightness()` | `getLightLevel()` | 光照方法不同 |
| `.pick()` | `.raycast()` | 射线检测方法不同 |
| `getSkyDarken()` | `getSkyDarken()`(可能不存在) | 天空亮度方法 |
| `getCurrentSmoothedTickTime()` | 需用其他方式获取 | TPS 计算方法 |
| `getCurrentDifficultyAt()` | `getLocalDifficulty()` | 难度方法不同 |
| `GameRules.ADVANCE_TIME` | `GameRules.DO_DAYLIGHT_CYCLE` | 规则名不同 |
| `LevelBasedPermissionSet.OWNER` | `LeveledPermissionPredicate.OWNERS`(1.21.11) 或 `int 4`(1.21) | 权限谓词完全不同 |

### 1.21.11 ↔ 1.21/1.21.1 Yarn 差异

| 特性 | 1.21.11 | 1.21 / 1.21.1 |
|------|---------|---------------|
| `ServerCommandSource` 构造函数 | `(..., Predicate<ServerCommandSource>, ...)` | `(..., int permissionLevel, ...)` |
| 权限检查 | `LeveledPermissionPredicate.OWNERS` | `4` (整数权限级别) |
| OP 检查 | `isOperator(PlayerConfigEntry)` | `isOperator(GameProfile)` |
| `PlayerConfigEntry` 类 | 存在 | **不存在** |
| `LeveledPermissionPredicate` 类 | 存在 `net.minecraft.command.permission` | **不存在** |
| `sendFeedback` / `sendError` | 同 1.21 | 同 1.21 |

**判断当前版本的 API 技巧：**
```java
// 检查 PlayerConfigEntry 是否存在
try { Class.forName("net.minecraft.server.PlayerConfigEntry"); } catch (...) {}
```

---

## 5. 关键实现细节

### 审批阻塞系统 (pendingFutures)

```java
// ChatHandler.executeCommand() 中的审批阻塞
if (needsApproval(command)) {
    int num = addPendingCommand(player.getUuid(), command);
    String key = player.getUuid() + ":" + num;
    CompletableFuture<String> future = new CompletableFuture<>();
    pendingFutures.put(key, future);
    notifyAdminsPending(player, command, num);
    try {
        String result = future.get(3, TimeUnit.MINUTES);  // ⚠️ 阻塞 AI 线程
        return result != null ? result : "指令已执行";
    } catch (TimeoutException e) {
        pendingFutures.remove(key);
        return "[审批超时] 3分钟内无人批准，指令已自动取消";
    }
}
```

**所有涉及 pendingFutures 的地方：**
1. `executeCommand()` — `future.put` + `future.get(3min)` ⚡
2. `approveCommand()` — `future.remove(key).complete(result)` ✅
3. `rejectCommand()` — `future.remove(key).complete("[审批拒绝]...")` ❌
4. `onPlayerDisconnect()` — `pendingFutures.keySet().removeIf(...)` 🚪

**常见错误：只定义了字段但不使用它（死代码）。必须检查 `pendingFutures.put` 是否真的被调用。**

### 北京时间戳

```java
// addToChatLog(String, String, boolean) 的三个参数版本
private void addToChatLog(String name, String message, boolean isAdmin) {
    String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")));
    String prefix = isAdmin ? "[管理员] " : "";
    chatLog.add("[" + time + "] " + prefix + name + ": " + message);
}
```

### handleResponse 的双路径问题

`handleResponse` 方法在 AI 文本返回时调用。如果 AI 的响应以 `/` 开头，它**直接执行命令**，绕过了审批系统。这会导致：

1. AI 通过 `execute_minecraft_command` 工具调用 → 走 `executeCommand` → 正确走审批
2. AI 在回复文本中写入命令 → 走 `handleResponse` → ⚠️ 绕过审批

**解决方案：** AI 被提示必须使用工具，不能以文本输出命令。`handleResponse` 只作为后备。

---

## 6. 踩坑记录

### 🔥 坑 1: pendingFutures 定义但未使用

**症状：** 审批系统提示"已提交审批"但不阻塞，命令立即执行。

**原因：** `executeCommand` 中的 `needsApproval` 块只返回提示消息，没有 `pendingFutures.put/future.get`。

**修复：** 确保 `executeCommand` 中的 `needsApproval` 分支包含完整的阻塞逻辑。

### 🔥 坑 2: `build/libs/` 被 `clean` 清空

**症状：** 构建后保存的 JAR 文件消失。

**原因：** 所有分支共享 `build/libs/` 目录，`gradle clean` 会删除它。

**修复：** 将 JAR 保存到项目根目录或独立目录，如 `C:\Users\Lecoo\mc\mcai-xxx.jar`。

### 🔥 坑 3: 分支间 `gradle.properties` 不一致

**症状：** 切换分支后构建失败，报 Loom/Gradle 版本不兼容错误。

**原因：** `gradle.properties` 和 `gradle-wrapper.properties` 在每个分支上需要不同的值，但切换分支时未正确恢复。

**修复：** 每个分支的 `gradle.properties` 和 `gradle-wrapper.properties` 必须独立维护。使用 `git checkout` 后检查这些文件。

### 🔥 坑 4: Loom 版本与 Gradle 版本不匹配

| Loom 版本 | 最低 Gradle |
|-----------|------------|
| 1.7.x | 8.8 |
| 1.14.x | 9.5+ |
| 1.15.x | 9.5+ |

**症状：** `Failed to apply plugin 'fabric-loom'` 或 `UnsupportedOperationException: Unsupported unpick version`。

### 🔥 坑 5: 1.21 Fabric API 版本错误

**症状：** `Failed to process jar when running jar processor: fabric-loom:access-widener - Expected official namespace for access widener entry, found: intermediary`

**原因：** Loom 1.15+ 要求 Fabric API 使用 official 命名空间的 access widener，但旧版 Fabric API 使用 intermediary。

**修复：** 使用正确版本的 Fabric API：
- Loom 1.7.x + Yarn 1.21.11 → `fabric-api 0.140.2+1.21.11`
- Loom 1.15.x + Yarn 1.21.11 → 需要更新版的 Fabric API

### 🔥 坑 6: Python 脚本中的中文编码损坏

**症状：** 源文件中的中文字符变成乱码（如 `系统` 变成 `绯荤粺`）。

**原因：** `git show` 输出通过 PowerShell 管道时编码转换错误，或 Python `replace()` 操作中使用了错误的编码。

**修复：** 始终使用 `encoding='utf-8'` 打开文件，避免通过 PowerShell 管道传递文件内容。使用 `subprocess.run(['git', 'show', ...], capture_output=True)` 直接读取。

### 🔥 坑 7: PowerShell 内嵌 Python 单引号冲突

**症状：** Python 代码中的 `'''` 或 `"""` 三引号与 PowerShell 的字符串解析冲突，出现语法错误。

**修复：** 将 Python 脚本保存为 `.py` 文件，然后执行 `python script.py`，不要用 `python -c "..."` 内联。

### 🔥 坑 8: 新增功能后忘记更新旧分支

**症状：** 旧版本分支缺失新功能（如 pendingFutures、北京时间等）。

**原因：** 只在 26.1.2-server 上开发，未将变更同步到 1.21.x 和 1.21.1 分支。

**修复：** 从最新的全功能分支（26.1.2-server）派生所有版本分支，而不是从旧分支派生。

### 🔥 坑 9: `CommandSourceStack` 替换不完整

**症状：** 编译错误 `找不到符号: CommandSourceStack`。

**原因：** 只替换了 `import` 中的完全限定名，未替换方法签名和泛型参数中的裸类名。

**修复：** 替换所有出现位置，包括：
- `import net.minecraft.commands.CommandSourceStack` → `import net.minecraft.server.command.ServerCommandSource`
- 方法签名 `LiteralArgumentBuilder<CommandSourceStack>` → `LiteralArgumentBuilder<ServerCommandSource>`
- 字段类型 `SuggestionProvider<CommandSourceStack>` → `SuggestionProvider<ServerCommandSource>`

### 🔥 坑 10: `sendSuccess` / `sendFailure` 在不同版本不同

| 方法 | 26.1.2 | 1.21.x Yarn |
|------|--------|-------------|
| 成功反馈 | `sendSuccess(Supplier<Component>, boolean)` | `sendFeedback(Supplier<Text>, boolean)` |
| 错误反馈 | `sendFailure(Component)` | `sendError(Text)` |

---

## 7. 工作流程建议

### 多版本维护流程

```bash
# 1. 在 26.1.2-server 上开发新功能
git checkout mc-26.1.2-server
# ... 编码、测试、提交 ...

# 2. 移植到 1.21.11
git checkout 1.21.11-server
git merge mc-26.1.2-server --squash  # 或手动 cherry-pick
# 修复 Yarn API 差异

# 3. 移植到 1.21.1
git checkout 1.21.1-server
git merge 1.21.11-server --squash
# 修复 1.21 API 差异（int/Predicate、GameProfile/PlayerConfigEntry）
```

### 推荐：差异最小化策略

将版本特定代码隔离到单独的方法中，用同一套核心逻辑：

```java
// 版本无关的核心逻辑
private void doApproval(UUID playerId, String command) { ... }

// 版本特定的 API 包装（每个版本不同）
// 1.21.11: server.getPlayerManager().isOperator(new PlayerConfigEntry(profile))
// 1.21:    server.getPlayerManager().isOperator(profile)
// 26.1.2:  server.getPlayerList().isOp(new NameAndId(profile))
```

### 构建清单

每次构建前检查：
- [ ] `gradle.properties` 版本正确
- [ ] `gradle-wrapper.properties` Gradle 版本与 Loom 匹配
- [ ] `build.gradle` 的 `loom_version` 与 `gradle.properties` 一致
- [ ] Java 版本正确（21 或 25）
- [ ] 清理 loom 缓存：`Remove-Item -Recurse $env:USERPROFILE\.gradle\caches\fabric-loom`
- [ ] 保存 JAR 到独立目录（不要用 `build/libs/`）

### 配置文件路径

所有配置文件存放在 `config/mcai/` 目录下：
- `config.json` — 主配置（API、模型、阈值等）
- `scores.json` — 玩家行为分
- `penalties.json` — 处罚历史
- `memory.json` — AI 持久记忆
- `kb/` — 知识库缓存
- `review_last_response.txt` — 上次审查 AI 原始输出
- `review_last_reasoning.txt` — 上次审查 AI 推理过程
