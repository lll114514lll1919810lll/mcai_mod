# MCAI - Minecraft AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/technologies/downloads/)

> [中文版本](README.md)

MCAI is a Fabric mod that lets AI manage your Minecraft server. This entire project is AI-developed.

**TL;DR:** Players type `/ai how do I enchant a diamond sword?`, AI answers. Someone swears? AI warns or kicks. Dangerous ops need admin approval.

---

## Quick Start

| What | How |
|------|-----|
| Chat with AI | `/ai <question>` or `!ai <question>` |
| AI executes commands | `/ai give me a diamond sword` (admin approval) |
| AI batch commands | `/ai build a redstone circuit` (command chain, one approval) |
| Check behavior score | `/aiscore` |
| Search wiki | `/aikb enchantment` (online Wiki available) |
| Admin approve | `/aiaccept <id>` approve / `/aireject <id>` reject |
| Cancel pending | `/aicancel` cancel latest / `/aicancel all` cancel all |

---

## Features

### AI Chat
- Players use `!ai` or `/ai` to chat with AI
- AI has full server context (chat logs, weather, time, players)
- Multi-turn conversation with history
- **Persona mode**: switch AI persona with `/aipersona` — built-in villager, piglin, ender dragon, creeper themes, bilingual (ZH/EN), admins can add custom personas in `config/mcai/personas/`

### Auto Behavior Review
- AI analyzes chat every 30 minutes
- Three-tier penalty: score deduction -> yellow card -> red card (kick)
- Admin messages are trusted; admins can declare server rules

### Admin Approval
- Dangerous commands (op, ban, kick) require manual admin approval
- 3-minute timeout auto-cancels pending approvals
- Strict mode: only whitelisted safe commands skip approval
- AI text starting with `/` is blocked; all commands must go through the Tool system and the unified approval flow
- **Command Chain**: AI can batch multiple commands into a single approval unit (`execute_command_chain`), admin approves once to execute all, with configurable intervals between commands
- **Player Cancel**: Players can use `/aicancel` to cancel their own pending commands; AI receives cancellation notice and won't retry the same command

### Game Knowledge Base
- AI searches minecraft.wiki / zh.minecraft.wiki online for up-to-date game knowledge (language via `wikiLanguage`)
- Supports Chinese and English search, returns summaries and full page links

---

## Installation

### Requirements
- Minecraft **Fabric server 26.2**
- [Java](https://www.oracle.com/java/technologies/downloads/) 25
- A [DeepSeek API Key](https://platform.deepseek.com) (or any OpenAI-compatible API)

### Steps
1. Download the latest JAR from [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)
2. Place it in `mods/` folder
3. Start the server to auto-generate config
4. Edit `config/mcai/config.json`, fill in your API Key
5. Config auto-reloads; or run `/aireload` manually
### Single-Player Usage

The mod works in **single-player** too (no dedicated server required):

1. Install **Fabric client** (same version 26.2 as the server)
2. Place the JAR in `.minecraft/mods/` folder
3. Launch the game and enter a single-player world
4. Config and commands are identical to the server setup
5. You are the world owner with full admin permissions by default
6. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu) for in-game config editing

> Tip: The behavior review system is auto-disabled in single-player (no need to review yourself).

---

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `!ai <msg>` `/ai <msg>` | Chat with AI |
| `/aiscore` | Check behavior score |
| `/aicancel [id/all]` | Cancel your own pending commands |

### Admin Commands
| Command | Description |
|---------|-------------|
| `/aiaccept <id>` | Approve pending action |
| `/aireject <id>` | Reject pending action |
| `/aiquery` | List pending approvals (shows unique ids) |
| `/aiclear` | Clear AI chat history |
| `/aireload` | Manually reload config (auto-reloads on file change) |
| `/airesetprompts` | Reset prompt files to current built-in defaults |
| `/aikb <keyword>` | Search Wiki knowledge base |
| `/aicontrol [chat/review] [on/off]` | Toggle AI chat/review |
| `/aikill` | Destroy all AI threads |
| `/aidebug start/stop/show/list/clear` | Debug logging |
| `/aipersona [list/set/current/view/reload]` | Switch/view/reload AI persona |

### Review Management
| Command | Description |
|---------|-------------|
| `/aireview start` | Trigger manual review |
| `/aireview approve <id>` | Approve kick |
| `/aireview reject <id>` | Reject kick |
| `/aireview last` | View last review result |
| `/aireview last reasoning` | View AI reasoning |

### Test Commands (OP only)
| Command | Description |
|---------|-------------|
| `/aitest score <player>` | Check player behavior score |
| `/aitest set <player> <score>` | Set player behavior score |
| `/aitest penalty <player> <points>` | Simulate penalty |
| `/aitest reset <player>` | Reset player behavior score |
| `/aitest review` | Trigger manual review |
| `/aitest chatlog` | View chat log |

---

## Configuration

File: `config/mcai/config.json`. Auto-reloads on change (or use `/aireload`).

| Key | Default | Description |
|-----|---------|-------------|
| `apiEndpoint` | `https://api.deepseek.com` | API endpoint |
| `apiKey` | `""` | API key |
| `model` | `deepseek-v4-flash` | Model name |
| `triggerPrefix` | `!ai` | Chat trigger prefix |
| `maxTokens` | 2048 | Max response tokens |
| `temperature` | 0.75 | Response randomness (0-1) |
| `thinkingLevel` | 1 | Thinking level 0-3 |
| `strictMode` | `true` | Strict mode |
| `aiCooldownSeconds` | `60` | Non-admin AI cooldown (seconds) |
| `aiMaxConcurrent` | `3` | Max concurrent non-admin AI calls |
| `compatibilityMode` | `false` | Compatibility mode: send only basic fields for local APIs like LM Studio |
| `reviewIntervalMinutes` | `30` | Review interval (min) |
| `yellowCardThreshold` | `-30` | Yellow card threshold |
| `redCardThreshold` | `-60` | Red card threshold |
| `scoreRecoveryPerInterval` | `5` | Score recovery per cycle |
| `approvalTimeoutMinutes` | `10` | Approval timeout (min) |
| `wikiLanguage` | `"zh_cn"` | AI online search language: `zh_cn` Chinese, `en_us` English (online search enabled by default) |
| `apiConnectTimeoutSeconds` | 10 | API connect timeout (seconds) |
| `apiRequestTimeoutSeconds` | 60 | API per-request timeout (seconds) |
| `apiLoopTimeoutSeconds` | 300 | Tool-call loop total timeout (seconds) |
| `commandExecTimeoutSeconds` | 30 | Per-command execution timeout (seconds) |
| `maxChainCommands` | 10 | Max commands per chain |
| `contextMaxChars` | 20000 | Max context chars for AI (auto-truncated) |
| `maxToolCalls` | 15 | Max tool calls per conversation turn |
| `activePersona` | `"default"` | Current persona ID |
| `personaLanguage` | `""` | Persona language override, empty=follow client language |
| `promptLanguage` | `"zh_cn"` | Built-in prompt language |
| `enableChatInterception` | `true` | Intercept chat and forward to AI |
| `enableCommandExecution` | `true` | Allow AI to execute commands |
| `enableAutoReview` | `true` | Enable auto behavior review |
| `maxReviewCycles` | 4 | Max review analysis cycles |
| `systemPromptPath` | `""` | System prompt file (under config/mcai/) |
| `reviewPromptPath` | `""` | Review prompt file |
| `reviewApiEndpoint` | `""` | Review system API endpoint, empty=follow chat config |
| `reviewApiKey` | `""` | Review system API key, empty=follow chat config |
| `reviewModel` | `""` | Review system model, empty=follow chat config |

Prompt files `system_prompt.txt` / `review_prompt.txt` are auto-created on first start. Run `/airesetprompts` to force-sync them to the current built-in versions.

---


### Knowledge Base

AI knowledge search is done via online Wiki — no manual data import needed. The `kb/` directory still contains legacy knowledge base files, but the current version does not automatically load local KB.

## Build

```bash
git clone https://github.com/lll114514lll1919810lll/mcai_mod.git
cd mcai_mod
.\gradlew.bat build
# Output: build/libs/mcai-<version>.jar
```

Requires JDK 25.

---

## Download

### Stable (Recommended)

Download tested stable releases from GitHub Releases:

[![Releases](https://img.shields.io/github/v/release/lll114514lll1919810lll/mcai_mod?label=Latest&logo=github)](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

### Beta

Download Pre-release versions from GitHub Releases (newer features, may have bugs):

[![Pre-release](https://img.shields.io/github/v/release/lll114514lll1919810lll/mcai_mod?include_prereleases&label=Beta&logo=github)](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

### Nightly (Development)

Download the latest development builds from the `nightly-builds/` directory in the repository (not fully tested, for early adopters):

```
nightly-builds/<MC_VERSION>/<MOD_VERSION>/mcai-<MC_VERSION>-<MOD_VERSION>.jar
```

Example: `nightly-builds/26.1.2/1.7.0-beta.4-alpha.1/mcai-26.1.2-1.7.0-beta.4-alpha.1.jar`

> ⚠️ Nightly builds may contain unfinished features and bugs. Only recommended for developers or users willing to report issues.

---

## Links

- [用户手册 / User Guide (中文)](USER_GUIDE.md)
- [English User Guide](USER_GUIDE_EN.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## License

[MIT License](LICENSE)
