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
1.7.0            # Release
1.7.0-beta.1     # Public beta
1.7.0-alpha.1    # Early internal alpha
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
| `alpha.N` | Each code change + successful build | Local/internal only, not published | None |
| `beta.N` | User says "publish" | Public testing | Created, marked as pre-release |
| No suffix | Breaking changes + stable after testing | All players | Created, marked as Latest |

**Daily workflow**:
1. Modify code → set `mod_version` to `X.Y.Z-alpha.N` → build → Git commit (increment alpha N, starting from 1)
2. User says "publish" → change `mod_version` to `X.Y.Z-beta.1` → build → create GitHub Release (pre-release)
3. Continue fixing → `beta.2`, `beta.3`... increment beta N each release
4. Breaking changes done and stable → remove suffix → `X.Y.Z` release → create GitHub Release (Latest)

**Version progression example**:
```
1.7.0-alpha.1   # First code change + build
1.7.0-alpha.2   # Second code change + build
1.7.0-beta.1    # User says "publish", public beta
1.7.0-beta.2    # Fix feedback, re-publish
1.7.0           # Testing stable, promoted to release
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
mod_version=1.7.0-alpha.1   # In development
# mod_version=1.7.0-beta.1  # When user says "publish"
# mod_version=1.7.0         # Release
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

Two directories store build outputs, with different naming conventions:

| Directory | Purpose | Subdirectory naming | Example |
|---|---|---|---|
| `builds/` | Local development builds (alpha/beta) | `<MOD_VERSION>` (no `v` prefix) | `builds/alpha/1.7.0-alpha.3/` |
| `releases/` | Published releases (mirror GitHub tags) | `<MC_VERSION>/v<MOD_VERSION>/` | `releases/26.1.2/v1.6.1/` |

**Rules**:
- `builds/` subdirectories use plain version strings: `1.7.0-alpha.3`, `1.7.0-beta.1` — no `v` prefix
- `releases/` subdirectories use `v` prefix to match GitHub tag convention: `v1.6.1`, `v1.7.0-beta.1`
- JAR files inside always follow `mcai-<MC_VERSION>-<MOD_VERSION>.jar` naming (no `v` prefix in filename)
- `-sources.jar` files are optional and may be omitted for alpha builds

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