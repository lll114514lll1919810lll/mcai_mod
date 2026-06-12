# MCAI - Developer Notes (English)

> [中文版本](MCAI_MOD_SUMMARY.md)

---

## Overview

MCAI is a Fabric server-side mod that connects to OpenAI-compatible APIs. It reads player chat, executes commands, searches the Minecraft Wiki, and reviews player behavior automatically.

### Supported Version

| MC Version | Mappings | Artifact |
|-----------|----------|----------|
| 26.1.2 | Mojang | `mcai-<version>.jar` |

---

## Repository Structure

Single branch `main`, all source in `src/main/java/`.

```
handler/ (7)     — Chat orchestration
behavior/ (8)    — Review & penalty system
api/ (3)         — API clients & result types
kb/ (1)          — Knowledge base
client/ (2)      — Mod Menu config screen
config/ (1)      — Config & prompt loader
root (1)         — MCAIMod (111 lines)
```

### Build Config

| Param | Value |
|-------|-------|
| `minecraft_version` | 26.1.2 |
| `loader_version` | 0.19.2 |
| `fabric_version` | 0.149.1+26.1.2 |
| `loom_version` | 1.14.1 |
| `Java` | 25 |
| `Gradle` | 9.5.1 |

```bash
.\gradlew.bat build
# Output: build/libs/mcai-<version>.jar
```

---

## Mojang ↔ Yarn Mapping Reference

| Mojang (26.1.2) | Yarn (1.21.x) | Category |
|-----------------|---------------|----------|
| `CommandSourceStack` | `ServerCommandSource` | Class |
| `Commands.literal()` | `CommandManager.literal()` | Factory |
| `Component.literal()` | `Text.literal()` | Text |
| `ServerPlayer` | `ServerPlayerEntity` | Player |
| `ServerLevel` | `ServerWorld` | World |
| `player.level()` | `player.getEntityWorld()` | World access |
| `server.getPlayerList()` | `server.getPlayerManager()` | Player mgmt |
| `player.sendSystemMessage()` | `player.sendMessage()` | Messaging |
| `src.sendFailure()` | `src.sendError()` | Error feedback |
| `src.sendSuccess()` | `src.sendFeedback()` | Success feedback |
| `player.isRemoved()` | `player.isDisconnected()` | Disconnect check |
| `player.blockPosition()` | `player.getBlockPos()` | Position |
| `LevelBasedPermissionSet.OWNER` | `LeveledPermissionPredicate.OWNERS` | Permission |

---

## Key Implementation Details

### Approval Blocking

```java
CompletableFuture<String> future = new CompletableFuture<>();
pendingFutures.put(key, future);
String result = future.get(3, TimeUnit.MINUTES);
```

Four touch points: `executeCommand` (put+get), `approveCommand` (complete), `rejectCommand` (complete), `onPlayerDisconnect` (removeIf).

### Behavior Review

- AI analyzes chat every 30 min
- Three-tier: score → yellow card → red card
- Admin messages marked `[管理员]` are unconditionally trusted
- Recovers 5 pts per cycle, caps at 0

---

## Multi-Version History (Archived)

After v1.2.0 (2026-06-12), only MC 26.1.2 is supported.
Old branches `mc-1.21.1` and old `main` (1.21.11) have been deleted.
Full backup at `mc-backup-20260612-143644`.

---

## Known Pitfalls

### Mojang ↔ Yarn Incompatibility
Compiles but crashes at runtime with `NoClassDefFoundError`. Solved by dropping multi-version support.

### ModMenu Version
MC 26.1.2 requires `modmenu:20.0.0-alpha.1` (Mojang build).

### Gradle Version
Gradle 9.5.1 + Java 25, no compatibility issues.

---

## Build Checklist

- [ ] Clean working tree (`git status`)
- [ ] `gradle.properties` correct
- [ ] Run `gradle clean build`
- [ ] `build/libs/mcai-<version>.jar` exists
