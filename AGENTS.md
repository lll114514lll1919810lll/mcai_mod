# AGENTS.md

## Project

MCAI — Minecraft Fabric mod (MC 26.3-snapshot-5, Java 25, Mojang mappings) that integrates DeepSeek or other AI into the game for chat, command execution, knowledge base search, and automated player behavior review.

**Branch**: `main` is the active and only branch. Previous multi-version branches (master, mc-1.21.11, mc-26.1.2) were merged into `main` and deleted. Do not recreate them.

## Build

```bash
.\gradlew.bat build    # Produces build/libs/mcai-26.1.2-1.6.0.jar
```

Requires **JDK 25** (see `gradle.properties` — `java.toolchain.languageVersion = 25`).

There are no tests — the project has no `src/test/` directory, no test framework configured, and no CI workflows.

## Architecture

```
src/main/java/com/example/mcai/
├── MCAIMod.java              — Mod entry point (ModInitializer)
├── api/
│   ├── OpenAIClient.java     — DeepSeek API calls (tool-calling, streaming)
│   └── ApiResult.java        — API response record
├── handler/
│   ├── ChatHandler.java      — Main chat processing (orchestrator)
│   ├── ChatLog.java          — Server-wide chat log storage
│   ├── ThinkingAnimation.java — "Thinking..." indicator
│   ├── PlayerContextBuilder.java — Builds player context (weather, time, etc.)
│   ├── CommandExecutionService.java — Executes Minecraft commands via API
│   ├── ToolDispatcher.java   — Routes AI tool calls to handlers
│   └── CommandRegistry.java  — Registers all /ai* commands
├── behavior/
│   ├── ChatReviewSystem.java — Auto review orchestrator (30-min cycle)
│   ├── ReviewEngine.java     — AI review processing
│   ├── ReviewCommandRegistry.java — /aireview commands
│   ├── PlayerBehaviorTracker.java — Per-player score persistence
│   ├── PenaltyEvent.java     — Penalty record
│   ├── PenaltyHistory.java   — Penalty history
│   ├── AdminApprovalQueue.java — Pending kick approvals
│   └── PlayerViolation.java  — Violation record
├── config/
│   └── ModConfig.java        — JSON config (config/mcai/config.json)
├── client/
│   ├── ModMenuIntegration.java — Mod Menu integration
│   └── config/
│       └── MCAIConfigScreen.java — Client-side config GUI
└── kb/
    └── KnowledgeBase.java    — Bigram CJK search over JSON wiki dumps
```

## Key Conventions

- **Mojang mappings only** — never use Yarn mappings (class/method names differ)
- **i18n mandatory** — all user-facing strings use `Component.translatable("mcai.xxx")`, never hardcoded text. Language files: `src/main/resources/assets/mcai/lang/{en_us,zh_cn}.json`
- **Documentation is bilingual, not mixed** — separate files: `README.md` (Chinese) + `README_EN.md` (English), same pattern for `USER_GUIDE`
- **Knowledge base files** (`kb/*.json`) are NOT bundled in the JAR — they live in `kb/` with license files and are deployed separately by server admins to `config/mcai/kb/`
- **Config lives at runtime** in `config/mcai/` — never commit files from that directory

## Mojang 26.3 API Gotchas

- `ResourceLocation`/`Identifier` class location changed — not at `net.minecraft.util` or `net.minecraft.resources`
- `CustomPacketPayload.type()` not `writeId()`
- `Screenshot.captureScreenshot` requires `File` parameter
- `JsonNull` from Xiaomi API can cause `ClassCastException` — guard with `isJsonNull()` before casting tool_calls
- Thread scheduling: do not call `animation.shutdown()` — daemon threads self-clean; killing the scheduler causes permanent crash on world switch

## Files That Need Care

- `tools/wiki_to_kb.py` — wiki scraper, depends on `requests` and `beautifulsoup4`

## Mojang ↔ Yarn Mapping Reference

供跨版本移植参考：

| Mojang (26.2) | Yarn (1.21.x) | Category |
|----------------|---------------|----------|
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
| `KeyEvent` | N/A (new in 26.2) | Input |

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

## Known Pitfalls

- **Mojang ↔ Yarn Incompatibility**: Compiles but crashes at runtime with `NoClassDefFoundError`. Solved by dropping multi-version support.
- **ModMenu Version**: MC 26.3 requires `modmenu:20.0.1` or newer (Mojang build).
- **Gradle Version**: Gradle 9.5.1 + Java 25, no compatibility issues.
- **MC 26.3 SDL3 Migration**: 26.3 Snapshot 4 switched from GLFW to SDL3 for window management and input. `KeyEvent` and `Screen.keyPressed()` still work, but key codes may differ from GLFW scancodes.
- **MC 26.2 API Change**: `Minecraft.setScreen()` removed, use `Minecraft.setScreenAndShow()` instead. Config screen must override `canInterruptWithAnotherScreen()` to return true.

## Build Checklist

- [ ] Clean working tree (`git status`)
- [ ] `gradle.properties` correct
- [ ] Run `.\gradlew.bat build`
- [ ] `build/libs/mcai-<version>.jar` exists
