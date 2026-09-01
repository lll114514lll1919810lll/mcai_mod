# MCAI - Server Admin Guide (English)

> [中文版本](USER_GUIDE.md)

---

## Installation

### Requirements
- Minecraft **Fabric server 26.2**
- JDK 25
- A **DeepSeek API Key** ([platform.deepseek.com](https://platform.deepseek.com))

### Download JAR

| Channel | Description | How to Download |
|---------|-------------|-----------------|
| **Stable** (Recommended) | Thoroughly tested, suitable for production | [GitHub Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases) |
| **Beta** | Newer features, may have bugs | Pre-release on GitHub Releases |
| **Nightly** | Latest features, not fully tested | `nightly-builds/` directory in repository |

> Nightly path example: `nightly-builds/26.3-pre-1/1.7.1-beta.1-alpha.2/mcai-26.3-pre-1-1.7.1-beta.1-alpha.2.jar`

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
| `/aireload` | Manually reload config (auto-reloads on file change) |
| `/airesetprompts` | Reset prompt files to current built-in defaults |
| `/aikb <keyword>` | Search knowledge base |
| `/aicontrol [chat/review] [on/off]` | Toggle AI chat/review |
| `/aikill` | Destroy all AI threads |
| `/aidebug start/stop/show/list/clear` | Debug logging |
| `/aipersona [list/set/current/view/reload]` | Switch/view/reload AI persona |

### Persona Mode

`/aipersona` subcommands:
- `list` — list all personas (clickable to select)
- `set <id|index>` — switch persona
- `current` — show current persona
- `view <id|index>` — view persona content
- `reload` — rescan `config/mcai/personas/` directory

Built-in personas: `default`, villager, piglin, ender dragon, creeper. Admins can add custom persona JSON in `config/mcai/personas/` (required fields `id`/`name`/`content`; optional `summary` and `translations` for multi-language). Duplicate IDs keep the alphabetically-first file; later duplicates are skipped with a warning.

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
- AI text output starting with `/` is automatically blocked and will not execute. All commands must go through the AI Tool system, ensuring the unified approval flow

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
| `approvalTimeoutMinutes` | 10 | Approval timeout (min) |
| `wikiLanguage` | `"zh_cn"` | AI online search language: `zh_cn` Chinese, `en_us` English (online search enabled by default) |
| `apiConnectTimeoutSeconds` | 10 | API connect timeout (seconds) |
| `apiRequestTimeoutSeconds` | 60 | API per-request timeout (seconds) |
| `apiLoopTimeoutSeconds` | 300 | Tool-call loop total timeout (seconds) |
| `commandExecTimeoutSeconds` | 30 | Per-command execution timeout (seconds) |
| `wikiConnectTimeoutSeconds` | 5 | Wiki search connect timeout (seconds) |
| `wikiRequestTimeoutSeconds` | 8 | Wiki search request timeout (seconds) |
| `maxChainCommands` | 10 | Max commands per chain |
| `contextMaxChars` | 20000 | Max context chars for AI |
| `maxToolCalls` | 15 | Max tool calls per turn |
| `activePersona` | `"default"` | Current persona ID |
| `personaLanguage` | `""` | Persona language override, empty=follow client language |
| `promptLanguage` | `"zh_cn"` | Built-in prompt language |
| `enableChatInterception` | `true` | Intercept chat and forward to AI |
| `enableCommandExecution` | `true` | Allow AI to execute commands |
| `enableAutoReview` | `true` | Enable auto behavior review |
| `maxReviewCycles` | 4 | Max review analysis cycles |
| `systemPromptPath` | `""` | System prompt file path |
| `reviewPromptPath` | `""` | Review prompt file path |
| `reviewApiEndpoint` | `""` | Review system API endpoint, empty=follow chat config |
| `reviewApiKey` | `""` | Review system API key, empty=follow chat config |
| `reviewModel` | `""` | Review system model, empty=follow chat config |
| `compatibilityMode` | `false` | Compatibility mode: send only basic fields for local APIs like LM Studio |
| `enableStream` | `false` | Streaming output (SSE), some APIs only support streaming mode |

---

## Wiki Knowledge Search

AI searches minecraft.wiki / zh.minecraft.wiki online for up-to-date game knowledge.

Edit `config/mcai/config.json` to set the language:

```json
{
  "wikiLanguage": "zh_cn"
}
```

- `wikiLanguage`: `zh_cn` for Chinese Wiki, `en_us` for English Wiki

> Note: online search sends requests to minecraft.wiki. Make sure your server has internet access and follows the wiki usage policy.

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
| `personas/*.json` | Custom personas (optional; effective after `/aipersona reload`) |

---

## FAQ

**Q: Review too strict/lenient?**
Adjust `yellowCardThreshold` and `redCardThreshold`. Higher values = stricter.

**Q: Want to block certain commands?**
Remove from `safeCommands` whitelist or add to `requireApprovalCommands`.

**Q: Can I use other API providers?**
Yes. Change `apiEndpoint` to any OpenAI-compatible endpoint.

**Q: Prompt files differ from the built-in prompts?**
Default prompt files are created on first start. After updating the mod, run `/airesetprompts` to sync them to the current built-in versions.

**Q: Getting 400 when connecting to LM Studio?**
LM Studio is strict about some OpenAI extension fields (`max_tokens`, `temperature`, `thinking`). Enable `compatibilityMode: true` to send only basic fields.

**Q: Use a different model for review?**
Set `reviewApiEndpoint` / `reviewApiKey` / `reviewModel`. Leave empty to follow the chat system config.

**Q: Cost?**
DeepSeek flash is very cheap. Review calls every 30 min. Daily use costs pennies.
