# MCAI - Server Admin Guide (English)

> [中文版本](USER_GUIDE.md)

---

## Installation

### Requirements
- Minecraft **Fabric server 26.2**
- JDK 25
- A **DeepSeek API Key** ([platform.deepseek.com](https://platform.deepseek.com))

### Steps
1. Download `mcai-<version>.jar` and place in `mods/`
2. Start server once to auto-generate config
3. Edit `config/mcai/config.json`, fill in your API Key
4. Config auto-reloads (or run `/aireload` manually)
### Single-Player

The mod works in **single-player** too - no dedicated server needed:

1. Install **Fabric client** (same version 26.2 as the server)
2. Place the JAR in `.minecraft/mods/` folder
3. Launch the game and enter a single-player world
4. Config auto-generated at `config/mcai/config.json` (game root directory)
5. All commands are available; you are the world owner with full permissions
6. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu) for in-game config editing

> Note: The behavior review system is auto-disabled in single-player, and `/aireview` no longer works.

```json
{
  "apiEndpoint": "https://api.deepseek.com",
  "apiKey": "sk-xxxxxxxxxxxxxxxxxxxxx",
  "model": "deepseek-v4-flash"
}
```

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `!ai <msg>` / `/ai <msg>` | Chat with AI |
| `/aiscore` | Check behavior score & rules |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/aiaccept <id>` | Approve pending command |
| `/aireject <id>` | Reject pending command |
| `/aiquery` | List pending approvals (shows unique ids) |
| `/aiclear` | Clear chat history |
| `/aireload` | Manually reload config (auto-reloads on file change) |
| `/aikb <keyword>` | Search knowledge base |
| `/aicontrol [chat/review] [on/off]` | Toggle AI chat/review |
| `/aikill` | Destroy all AI threads |
| `/aidebug start/stop` | Debug logging |

### Review System

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
| `/aitest score <player>` | Check player score |
| `/aitest set <player> <score>` | Set player score |
| `/aitest penalty <player> <points>` | Simulate penalty |
| `/aitest reset <player>` | Reset player score |
| `/aitest review` | Trigger review |
| `/aitest chatlog` | View chat log |

---

## Review System

### Flow
1. AI analyzes chat every N minutes (configurable, default 30)
2. Detects violations, suggests penalties
3. Penalties are executed and broadcast

### Three-Tier Penalty

| Tier | Condition | Effect |
|------|----------|--------|
| Score | severity -10 | Score only, no broadcast |
| Yellow | severity -20 or <= -30 | Broadcast warning |
| Red | severity -30 or <= -60 | Broadcast + kick (admin approval) |

### Score Recovery
- Online non-admin players recover **5 points** per review cycle
- Caps at **0**

### Evidence Standard
- Multiple reports -> sufficient evidence
- Single report without corroboration -> no penalty
- Admin statements override all claims

---

## Approval System

- Dangerous commands (op, ban, kick etc.) require admin approval
- AI blocks waiting for approval; 3-min timeout auto-cancels
- Strict mode: only whitelisted safe commands skip approval

---

## Configuration

File: `config/mcai/config.json`. Auto-reloads on change (or use `/aireload`).

| Key | Default | Description |
|-----|---------|-------------|
| `apiEndpoint` | `https://api.deepseek.com` | API endpoint |
| `apiKey` | `""` | API key |
| `model` | `deepseek-v4-flash` | Model name |
| `triggerPrefix` | `!ai` | Chat trigger prefix |
| `maxTokens` | `2048` | Max response tokens |
| `temperature` | `0.75` | Response randomness |
| `thinkingLevel` | `1` | Thinking level 0-3 |
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
| `systemPromptPath` | `""` | System prompt file path |
| `reviewPromptPath` | `""` | Review prompt file path |

---

## Online Wiki Search

By default the knowledge base uses built-in local JSON. Enabling online Wiki makes the AI search minecraft.wiki / zh.minecraft.wiki first, and automatically fall back to the local knowledge base on timeout or failure.

Edit `config/mcai/config.json`:

```json
{
  "enableOnlineWiki": true,
  "wikiLanguage": "zh_cn"
}
```

- `enableOnlineWiki`: `true` to enable online Wiki, `false` for local KB only
- `wikiLanguage`: `zh_cn` for Chinese Wiki, `en_us` for English Wiki

> Note: enabling this sends search requests to minecraft.wiki. Make sure your server has internet access and follows the wiki usage policy.

## File Structure

`config/mcai/`:

| File | Content |
|------|---------|
| `config.json` | Main config |
| `scores.json` | Player behavior scores |
| `penalties.json` | Penalty history |
| `system_prompt.txt` | System prompt (customizable) |
| `review_prompt.txt` | Review prompt (customizable) |
| `review_last_response.txt` | Last review AI raw output |
| `review_last_reasoning.txt` | Last review AI reasoning |

---

## FAQ

**Q: Review too strict/lenient?**
Adjust `yellowCardThreshold` and `redCardThreshold`. Higher values = stricter.

**Q: Want to block certain commands?**
Remove from `safeCommands` whitelist or add to `requireApprovalCommands`.

**Q: Can I use other API providers?**
Yes. Change `apiEndpoint` to any OpenAI-compatible endpoint.

**Q: Cost?**
DeepSeek flash is very cheap. Review calls every 30 min. Daily use costs pennies.
