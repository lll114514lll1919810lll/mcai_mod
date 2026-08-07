# AGENTS.md

## Project

MCAI — Minecraft Fabric mod (MC 26.3-snapshot-5, Java 25, Mojang mappings) that integrates DeepSeek or other AI into the game for chat, command execution, knowledge base search, and automated player behavior review.

**Branch**: `main` is the active and only branch. Previous multi-version branches (master, mc-1.21.11, mc-26.1.2) were merged into `main` and deleted. Do not recreate them.

## Build

```bash
.\gradlew.bat build    # Produces build/libs/mcai-26.1.2-1.6.1.jar
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

## Versioning

This project uses **Semantic Versioning**, effective from v1.7.0. The version is composed of two parts: `mod_version` (semantic) + `minecraft_version` (MC target).

### mod_version format

```
<MAJOR>.<MINOR>.<PATCH>[-<PRERELEASE>]

Examples:
1.7.0                 # Release
1.7.0-beta.3          # Public beta (3rd published beta)
1.7.0-beta.3-alpha.1  # Local development build on top of beta.3 (not published)
1.7.0-alpha.2         # Early development before first beta
```

### Version increment rules

| Change type | MAJOR | MINOR | PATCH | Example |
|---|---|---|---|---|
| Breaking API/config/save format change | +1 | 0 | 0 | 1.x.x → 2.0.0 |
| New feature, backward compatible | . | +1 | 0 | 1.6.x → 1.7.0 |
| Bug fix, backward compatible | . | . | +1 | 1.6.0 → 1.6.1 |
| Pre-release (testing) | . | . | . + suffix | 1.7.0-beta.1 |

### Release channels and workflow

| Channel | Trigger | Visibility | GitHub Release |
|---|---|---|---|
| `beta.N-alpha.M` | After beta.N published, each code change + local build | Local/internal only, not published | None |
| `alpha.N` (standalone) | Pre-first-beta development cycle | Local/internal only, not published | None |
| `beta.N` | User says "publish" | Public testing | Created, marked as pre-release |
| No suffix | Breaking changes + stable after testing | All players | Created, marked as Latest |

**Daily workflow**:
1. New feature/bugfix cycle starts, version at `X.Y.Z-alpha.1` → each commit increments alpha N (`alpha.2`, `alpha.3`...)
2. User says "publish beta" → change `mod_version` to `X.Y.Z-beta.1` → build → create GitHub Release (pre-release)
3. After beta.N is published, **continue development locally**: version stays at `X.Y.Z-beta.N-alpha.M` (M starts from 1, each commit increments) — this is a *beta.N-internal development build*, not a new beta channel
4. When accumulated changes are ready for public beta again → drop `-alpha.M` suffix → `X.Y.Z-beta.(N+1)` → build → create GitHub Release (pre-release)
5. Breaking changes done and stable → remove all suffix → `X.Y.Z` release → create GitHub Release (Latest)

**Version progression example**:
```
1.7.0-alpha.1        # Feature A implemented
1.7.0-alpha.2        # Feature B implemented
1.7.0-beta.1         # User says "publish", public beta
1.7.0-beta.1-alpha.1 # Bug fix #1 on top of beta.1 (local only)
1.7.0-beta.1-alpha.2 # Bug fix #2 on top of beta.1 (local only)
1.7.0-beta.2         # User says "publish" again → promoted
1.7.0-beta.2-alpha.1 # Polish work on top of beta.2 (local only)
1.7.0-beta.3         # User says "publish" again → promoted
1.7.0                # Breaking changes done + stable → full release
```

### GitHub Tag rules

```
v<MOD_VERSION>           # Release and beta
v<MOD_VERSION>-<MC_TAG>  # Only add MC tag when multiple MC versions coexist

Examples:
v1.7.0
v1.7.0-beta.1
```

Alpha versions do not create GitHub Tags — they are recorded in Git commit history only.

### JAR naming

Keep `mcai-<MC_VERSION>-<MOD_VERSION>.jar`, e.g. `mcai-26.1.2-1.7.0-alpha.1.jar`, `mcai-26.1.2-1.7.0-beta.1.jar`, `mcai-26.1.2-1.7.0.jar`. The filename encodes both MC compatibility and release channel info.

### gradle.properties config

```properties
minecraft_version=26.1.2
mod_version=1.7.0-beta.3   # Released beta (publish state)
# mod_version=1.7.0-beta.3-alpha.1  # After beta.3 published, local dev build
# mod_version=1.7.0-alpha.1        # Early development before first beta
# mod_version=1.7.0                # Release
```

### Git Commit message format

```
<type>: <description>

type values:
feat:     New feature
fix:      Bug fix
refactor: Refactoring (no functional change)
docs:     Documentation change
chore:    Build/config/misc
release:  Release commit
```

| Scenario | Example |
|---|---|
| Feature commit | `feat: add command chain cancellation` |
| Fix commit | `fix: approval command uses wrong execution context` |
| Release commit | `release: v1.7.0` |
| Pre-release commit | `release: v1.7.0-beta.1` |

### GitHub Release rules

- Release: mark as `Latest`, include full release notes
- Pre-release (alpha/beta): must check `Set as a pre-release`, note known issues in release notes
- After pre-release stabilizes, promote to release (remove suffix), create new Tag and Release

### Build Artifacts Directory

Two directories store build outputs:

| Directory | Purpose | Structure | Example |
|---|---|---|---|
| `nightly-builds/` | Local development builds (not published) | `nightly-builds/<MC_VERSION>/<MOD_VERSION>/` | `nightly-builds/26.1.2/1.7.0-beta.3-alpha.1/` |
| `releases/` | Published releases (mirror GitHub tags) | `releases/<MC_VERSION>/<MOD_VERSION>/` | `releases/26.1.2/1.6.1/` |

**Rules**:
- Both directories nest by `<MC_VERSION>` first, then `<MOD_VERSION>` — flat version strings, no `v` prefix
- No channel subdirectory (no `alpha/`, `beta/`) — version suffix itself (`alpha.N`, `beta.N-alpha.M`, `beta.N`) carries the channel info
- JAR files inside always follow `mcai-<MC_VERSION>-<MOD_VERSION>.jar` naming (no `v` prefix in filename)
- `-sources.jar` files are optional and may be omitted for local dev builds

## Mojang 26.3 API Gotchas

- `ResourceLocation`/`Identifier` class location changed — not at `net.minecraft.util` or `net.minecraft.resources`
- `CustomPacketPayload.type()` not `writeId()`
- `Screenshot.captureScreenshot` requires `File` parameter
- `JsonNull` from Xiaomi API can cause `ClassCastException` — guard with `isJsonNull()` before casting tool_calls
- Thread scheduling: do not call `animation.shutdown()` — daemon threads self-clean; killing the scheduler causes permanent crash on world switch

## Files That Need Care

- `tools/wiki_to_kb.py` — wiki scraper, depends on `requests` and `beautifulsoup4`

## Mojang ↔ Yarn Mapping Reference

Cross-version porting reference:

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

## Development Roadmap

> Merged from DEVELOPMENT_PLAN.md (based on engineering assessment 2026-07-26). Rolling document, updated with each release.

### Current Status Assessment

| Dimension | Rating | Key metric |
|---|---|---|
| Module boundaries | Good | 5-layer separation (chat/command/review/kb/config) |
| Security awareness | Strong | Approval flow, injection sanitization, tool call constraints |
| Operability | Good | Hot reload, debug logging, admin commands |
| Test coverage | Missing | No `src/test/`, zero regression protection |
| Code complexity | High | `CommandExecutionService` 750 lines, `OpenAIClient` 519 lines |
| Concurrency robustness | TBD | 4-8 thread pool + 32 queue, dual timeout 5min/60s |

### Priority Backlog

#### P0 — Testing infrastructure (target v1.7.0)
- Add JUnit 5 + Mockito (`testImplementation` in `build.gradle`)
- Unit tests: `OpenAIClient` (request building, response parsing, tool-call loop), `CommandExecutionService` (4 approval touch points), `KnowledgeBase` (bigram/CJK search), `SearchRouter` (online-first, 8s timeout)
- Integration test: `ChatHandler` (message flow, concurrency limiting, history truncation)
- GitHub Actions CI: `push`/`PR` triggers `./gradlew test` on JDK 25
- Goal: core module coverage > 60%, CI gates merges

#### P1 — Refactor high-complexity modules
- Split `OpenAIClient` (519 lines) into: `ApiClient` (HTTP transport), `ChatMessageCodec`, `ToolCallProcessor`, `ResponseParser`, and a thin facade
- Split `CommandExecutionService` (750 lines) into: `CommandApprovalManager`, `CommandChainExecutor`, `CommandSafetyChecker`, `ApprovalNotifier`, and a facade
- Principle: each class < 200 lines, single responsibility, independently testable

#### P2 — Concurrency & timeout hardening
- Make all timeouts configurable in `ModConfig` (connect/request/loop/retry/executor size/maxConcurrent) instead of hardcoded
- Return friendly error on queue-full instead of silent drop
- Configurable non-admin concurrency cap; queue instead of reject when over limit
- API retry 1-2x with exponential backoff on 5xx/network errors
- Circuit breaker: short-circuit 30s after N consecutive failures
- Load test with 10/20/50 concurrent players (P95/P99 latency); expose thread pool metrics in `/aistats`

### Feature Evolution

| Area | Planned features |
|---|---|
| Context management | History persistence per UUID (JSON), AI-summary compression past `contextMaxChars`, optional shared global channel, `/aicontext` usage visualization |
| Review system | Per-player behavior profiles (violation-type distribution), `/aiappeal` appeal workflow, review whitelist, `/aireview export` (CSV/JSON), adaptive review interval based on chat volume |
| Knowledge base | Multiple `kb/*.json` sources with priority, hot reload on `kb/` changes, optional embedding vector search, `/aikb list/reload/stats`, scheduled wiki sync |
| Multi-model | Task-based model routing (chat/review/summary), endpoint health checks with failover, local Ollama/llama.cpp support, token cost tracking per model |

### Quality & Security

- **Code quality**: SpotBugs + Checkstyle in CI, eliminate `@SuppressWarnings`, Javadoc on all public APIs, dead code cleanup
- **Observability**: structured JSON logs (ELK-ready), metrics exposure (call count/latency/error rate), separate audit log for commands/approvals/penalties, `AIDebugLogger` levels
- **Security**: API key encryption at rest, second-pass command injection pattern check, tool output size limits (context-bomb guard), per-player/IP/global rate limiting

### Version Roadmap

| Version | Target | Content |
|---|---|---|
| v1.7.0 | 2026 Q4 | P0 testing, `OpenAIClient` split, configurable timeouts, rejection policy, CI pipeline |
| v1.8.0 | 2027 Q1 | `CommandExecutionService` split, concurrency limiting, API retry, history persistence, behavior profiles |
| v1.9.0 | 2027 Q2 | Multi-KB sources, KB hot reload, model routing, health checks, vector search (experimental) |
| v2.0.0 | 2027 Q3 | Circuit breaker, structured logs + metrics, API key encryption, rate limiting, load test pass |

### Technical Debt Registry

| ID | Debt | Impact | Interest | Target version |
|---|---|---|---|---|
| TD-001 | `CommandExecutionService` 750 lines | Maintenance | High | v1.8.0 |
| TD-002 | `OpenAIClient` 519 lines | Maintenance | High | v1.7.0 |
| TD-003 | No tests | Regression risk | Critical | v1.7.0 |
| TD-004 | Hardcoded timeouts | Ops | Medium | v1.7.0 |
| TD-005 | Silent drop on full queue | UX | Medium | v1.7.0 |
| TD-006 | `ModConfig` 453 lines | Config bloat | Medium | v1.8.0 |
| TD-007 | In-memory history only | Lost on restart | Medium | v1.8.0 |
| TD-008 | No audit log | Compliance | Low | v2.0.0 |

## Build Checklist

- [ ] Clean working tree (`git status`)
- [ ] `gradle.properties` correct
- [ ] Run `.\gradlew.bat build`
- [ ] `build/libs/mcai-<version>.jar` exists

## Hard Constraints
- All Minecraft commands must be executed through the `execute_minecraft_command` tool, not directly from AI text responses
- Pending approval commands must use globally unique incrementing IDs instead of list indices
- Configuration watcher must first shutdown the scheduler before closing the WatchService to prevent loop errors
- Command chains (via `execute_command_chain` tool) can contain up to 10 commands with 0-10 second execution intervals
- Players can only cancel their own pending approval commands/chains using `/aicancel`
- Non-admin players in cooldown period must be directly rejected without message broadcasting
- Server must use a forced resource pack (`mcai-lang-pack.zip`) for translation when client mod is not installed
- Client and server should both install the MCAI mod for optimal translation and UI experience
- Review system can be configured with separate model settings; if not configured, it defaults to the chat system's model configuration
- `/airesetprompts` command is restricted to admin/console use only
- AIDebugLogger must be stopped during `SERVER_STOPPING` event to reset enabled flag and close file handles
- Chat logs must be cleared when entering a new world to prevent cross-world data leakage
- `/aireload` command must refresh persona list by rescanning `config/mcai/personas/` directory for JSON files
- If active persona file is deleted or invalid, system must auto-fallback to `default` persona and persist change to `config.json`

# Project Memory
## Engineering Conventions
- **CODE_WIKI.md Real-time Synchronization**: After every code modification (adding classes, modifying function signatures, changing behavior/flow, adjusting configuration fields, adding/removing commands, etc.), must synchronously update the corresponding sections in `CODE_WIKI.md` to keep documentation consistent with code; documentation includes sections for architecture, module responsibilities, key classes and functions, dependencies, operation methods, etc.; after modifications, must check and update affected sections
- Language files must use `%s` format specifier for approval IDs (changed from `%d`)
- Command execution requires admin approval for dangerous commands, determined by strict whitelist/blacklist checks
- Search functionality uses a provider-based architecture with `SearchProvider`/`SearchResult` abstractions
- `/aikb` command returns 7 search results (increased from 5)
- Approval notifications include clickable buttons for `[批准]`, `[拒绝]`, and `[取消]` actions
- Minecraft color codes must be re-applied after `%s`/`%d` placeholders to maintain consistent text styling
- Approval/rejection methods (`approveCommand`/`rejectCommand`/`approveChain`/`rejectChain`) must not send error messages; error handling centralized in `CommandRegistry`
- `/aikb` command displays "§7[AI] 搜索中..." instead of "§7[AI] 思考中..." during search execution
- PromptLoader handles system and review prompts, with `reset()` method to force overwrite prompt files
- ModConfig contains `resetPromptFiles()` method to reset both prompt files and clear cache
- ReviewEngine uses `ModConfig.getReviewPrompt()` instead of inline `REVIEW_PROMPT` constant
- Search failure handling: AI must abandon search attempts, not reveal error codes/HTTP status, and respond with "搜索功能暂时不可用，请稍后再试。" before continuing other tasks
- Persona files must be structured JSON with required fields: `id`, `name`, `content`; `summary` is optional
- PersonaManager must validate JSON structure, check for duplicate `id`s, and perform path traversal security checks on `id`
- Duplicate persona IDs are handled by alphabetical file order: first encountered file is kept, subsequent duplicates are skipped and logged as warnings
- Persona list refresh includes detailed logging: total files, loaded count, failed count, and lists available persona IDs
- Version numbering must follow strict channel progression: after beta.N is published, local development builds must use beta.N-alpha.M suffix (increment M) and cannot revert to standalone alpha.N
- Build artifacts must be organized in a two-level directory structure: `nightly-builds/<MC_VERSION>/<MOD_VERSION>/` for local development builds and `releases/<MC_VERSION>/<MOD_VERSION>/` for published versions; no channel subdirectories or 'v' prefixes

## Lessons Learned
- Using list indices for approval IDs caused number drift and potential approval of wrong commands
- Closing WatchService before scheduler shutdown led to repeated `ClosedWatchServiceException` on server stop
- Synchronous Wiki API calls in `/aikb` command blocked the server main thread, causing game tick pauses ("回弹")
- `koa-connect` wrapper caused ctx leaks when migrating Express middleware to Koa
- `approveCommand` sending immediate error messages led to false "invalid ID" prompts even when `approveChain` succeeded
- `CommandRegistry` caching old `SearchRouter` references caused `/aikb` failure after `/aireload`; must fetch current `SearchRouter` from `MCAIMod` on each execution
- Exiting single-player world without `/aidebug stop` caused silent failure on subsequent `/aidebug start` due to unreset `enabled` flag and unclosed file handles
- `onInitialize()` only runs once; `SearchRouter` (with thread pool) must be reinitialized in `SERVER_STARTED` event to avoid dead thread pool errors when re-entering single-player worlds
- `ChatHandler` holding a final `ToolDispatcher` reference caused search failures in AI conversations after server restart; `toolDispatcher` must be made volatile with a `setToolDispatcher()` method to update references