# MCAI - Minecraft AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.1.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.java.com/)

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
| Search wiki | `/aikb enchantment` |
| Admin approve | `/aiaccept 1` approve / `/aireject 1` reject |

---

## Features

### AI Chat
- Players use `!ai` or `/ai` to chat with AI
- AI has full server context (chat logs, weather, time, players)
- Multi-turn conversation with history

### Auto Behavior Review
- AI analyzes chat every 30 minutes
- Three-tier penalty: score deduction → yellow card → red card (kick)
- Admin messages are trusted; admins can declare server rules

### Admin Approval
- Dangerous commands (op, ban, kick) require manual admin approval
- 3-minute timeout auto-cancels pending approvals
- Strict mode: only whitelisted safe commands skip approval

### Game Knowledge Base
- Built-in Chinese Minecraft Wiki entries
- Online search with local fallback

---

## Installation

### Requirements
- Minecraft **Fabric server 26.1.2**
- [Java](https://www.java.com/) 25
- A [DeepSeek API Key](https://platform.deepseek.com) (or any OpenAI-compatible API)

### Steps
1. Download the latest JAR from [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)
2. Place it in `mods/` folder
3. Start the server to auto-generate config
4. Edit `config/mcai/config.json`, fill in your API Key
5. Run `/aireload` to reload config

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
| `/aiaccept <n>` | Approve pending action |
| `/aireject <n>` | Reject pending action |
| `/aiquery` | List pending approvals |
| `/aiclear` | Clear AI chat history |
| `/aireload` | Reload config |
| `/aikb <keyword>` | Search knowledge base |

### Review Management
| Command | Description |
|---------|-------------|
| `/aicheck` | Trigger manual review |
| `/aicheck approve <id>` | Approve kick |
| `/aicheck reject <id>` | Reject kick |
| `/aicheck last` | View last review result |
| `/aicheck last reasoning` | View AI reasoning |

---

## Configuration

File: `config/mcai/config.json`, reload with `/aireload`.

| Key | Default | Description |
|-----|---------|-------------|
| `apiEndpoint` | `https://api.deepseek.com` | API endpoint |
| `apiKey` | `""` | API key |
| `model` | `deepseek-v4-flash` | Model name |
| `strictMode` | `true` | Strict mode |
| `reviewIntervalMinutes` | `30` | Review interval (min) |
| `yellowCardThreshold` | `-30` | Yellow card threshold |
| `redCardThreshold` | `-60` | Red card threshold |
| `systemPromptPath` | `""` | System prompt file (under config/mcai/) |
| `reviewPromptPath` | `""` | Review prompt file |
| `promptLanguage` | `zh_cn` | Built-in prompt language |

Prompt files `system_prompt.txt` / `review_prompt.txt` are auto-created on first start.

---

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
- [开发总结 / Developer Notes (中文)](MCAI_MOD_SUMMARY.md)
- [English User Guide](USER_GUIDE_EN.md)
- [English Developer Notes](MCAI_MOD_SUMMARY_EN.md)
- [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

## License

[MIT License](LICENSE)
