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
| Check behavior score | `/aiscore` |
| Search wiki | `/aikb enchantment` (online Wiki available) |
| Admin approve | `/aiaccept <id>` approve / `/aireject <id>` reject |

---

## Features

### AI Chat
- Players use `!ai` or `/ai` to chat with AI
- AI has full server context (chat logs, weather, time, players)
- Multi-turn conversation with history

### Auto Behavior Review
- AI analyzes chat every 30 minutes
- Three-tier penalty: score deduction -> yellow card -> red card (kick)
- Admin messages are trusted; admins can declare server rules

### Admin Approval
- Dangerous commands (op, ban, kick) require manual admin approval
- 3-minute timeout auto-cancels pending approvals
- Strict mode: only whitelisted safe commands skip approval
- AI text starting with `/` is blocked; all commands must go through the Tool system and the unified approval flow

### Game Knowledge Base
- Built-in Chinese Minecraft Wiki entries
- Optional `enableOnlineWiki` to search minecraft.wiki / zh.minecraft.wiki
- Online-first; falls back to local KB when disabled or offline

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

### Admin Commands
| Command | Description |
|---------|-------------|
| `/aiaccept <id>` | Approve pending action |
| `/aireject <id>` | Reject pending action |
| `/aiquery` | List pending approvals (shows unique ids) |
| `/aiclear` | Clear AI chat history |
| `/aireload` | Manually reload config (auto-reloads on file change) |
| `/aikb <keyword>` | Search knowledge base |
| `/aicontrol [chat/review] [on/off]` | Toggle AI chat/review |
| `/aikill` | Destroy all AI threads |
| `/aidebug start/stop` | Debug logging |

### Review Management
| Command | Description |
|---------|-------------|
| `/aireview start` | Trigger manual review |
| `/aireview approve <id>` | Approve kick |
| `/aireview reject <id>` | Reject kick |
| `/aireview last` | View last review result |
| `/aireview last reasoning` | View AI reasoning |

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
| `reviewIntervalMinutes` | `30` | Review interval (min) |
| `yellowCardThreshold` | `-30` | Yellow card threshold |
| `redCardThreshold` | `-60` | Red card threshold |
| `scoreRecoveryPerInterval` | `5` | Score recovery per cycle |
| `approvalTimeoutMinutes` | `10` | Approval timeout (min) |
| `enableOnlineWiki` | `false` | Enable Minecraft Wiki online search |
| `wikiLanguage` | `"zh_cn"` | Wiki language: `zh_cn` Chinese, `en_us` English |
| `systemPromptPath` | `""` | System prompt file (under config/mcai/) |
| `reviewPromptPath` | `""` | Review prompt file |

Prompt files `system_prompt.txt` / `review_prompt.txt` are auto-created on first start.

---


### Importing Knowledge Bases

Place .json files in config/mcai/kb/ to extend AI knowledge:

1. Download from [kb/](kb/README.md) directory
2. Put them in config/mcai/kb/ (auto-created on first start)
3. Auto-reloads (or run /aireload manually)

See [kb/README.md](kb/README.md) for available files and licenses.
DIY scraper: [tools/wiki_to_kb.py](tools/wiki_to_kb.py).

## Build

```bash
git clone https://github.com/lll114514lll1919810lll/mcai_mod.git
cd mcai_mod
.\gradlew.bat build
# Output: build/libs/mcai-<version>.jar
```

Requires JDK 25.

---

## Links

- [用户手册 / User Guide (中文)](USER_GUIDE.md)
- [English User Guide](USER_GUIDE_EN.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## License

[MIT License](LICENSE)
