# AGENTS.md

## Project

MCAI — Minecraft Fabric mod (MC 26.1.2, Java 25, Mojang mappings) that integrates DeepSeek or other AI into the game for chat, command execution, knowledge base search, and automated player behavior review.

**Branch**: `main` is the active and only branch. Previous multi-version branches (master, mc-1.21.11, mc-26.1.2) were merged into `main` and deleted. Do not recreate them.

## Build

```bash
.\gradlew.bat build    # Produces build/libs/mcai-26.1.2.jar
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
│   ├── ReviewCommandRegistry.java — /aicheck commands
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
- **Documentation is bilingual, not mixed** — separate files: `README.md` (Chinese) + `README_EN.md` (English), same pattern for `USER_GUIDE` and `MCAI_MOD_SUMMARY`
- **Knowledge base files** (`kb/*.json`) are NOT bundled in the JAR — they live in `kb/` with license files and are deployed separately by server admins to `config/mcai/kb/`
- **Config lives at runtime** in `config/mcai/` — never commit files from that directory

## Mojang 26.1.2 API Gotchas

- `ResourceLocation`/`Identifier` class location changed — not at `net.minecraft.util` or `net.minecraft.resources`
- `CustomPacketPayload.type()` not `writeId()`
- `Screenshot.captureScreenshot` requires `File` parameter
- `JsonNull` from Xiaomi API can cause `ClassCastException` — guard with `isJsonNull()` before casting tool_calls
- Thread scheduling: do not call `animation.shutdown()` — daemon threads self-clean; killing the scheduler causes permanent crash on world switch

## Files That Need Care

- `build.bat` — multi-version build script, still references branch switching (stale, but harmless)
- `tools/wiki_to_kb.py` — wiki scraper, depends on `requests` and `beautifulsoup4`
- `CONTRIBUTING.md` — references multi-version setup instructions that are outdated (single branch now)
