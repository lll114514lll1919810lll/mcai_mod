#| /aicheck start | MCAI| /aicheck start | -| /aicheck start | Server| /aicheck start | Admin| /aicheck start | Guide| /aicheck start | (English)

>| /aicheck start | [ä¸­æ–‡ç‰ˆæœ¬](USER_GUIDE.md)

---

##| /aicheck start | Installation

###| /aicheck start | Requirements
-| /aicheck start | Minecraft| /aicheck start | **Fabric| /aicheck start | server| /aicheck start | 26.1.2**
-| /aicheck start | JDK| /aicheck start | 25
-| /aicheck start | A| /aicheck start | **DeepSeek| /aicheck start | API| /aicheck start | Key**| /aicheck start | ([platform.deepseek.com](https://platform.deepseek.com))

###| /aicheck start | Steps
1.| /aicheck start | Download| /aicheck start | `mcai-<version>.jar`| /aicheck start | and| /aicheck start | place| /aicheck start | in| /aicheck start | `mods/`
2.| /aicheck start | Start| /aicheck start | server| /aicheck start | once| /aicheck start | to| /aicheck start | auto-generate| /aicheck start | config
3.| /aicheck start | Edit| /aicheck start | `config/mcai/config.json`,| /aicheck start | fill| /aicheck start | in| /aicheck start | your| /aicheck start | API| /aicheck start | Key
4.| /aicheck start | Run| /aicheck start | `/aireload`| /aicheck start | to| /aicheck start | reload

```json
{
| /aicheck start | | /aicheck start | "apiEndpoint":| /aicheck start | "https://api.deepseek.com",
| /aicheck start | | /aicheck start | "apiKey":| /aicheck start | "sk-xxxxxxxxxxxxxxxxxxxxx",
| /aicheck start | | /aicheck start | "model":| /aicheck start | "deepseek-v4-flash"
}
```

---

##| /aicheck start | Commands

###| /aicheck start | Player| /aicheck start | Commands

|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `!ai| /aicheck start | <msg>`| /aicheck start | /| /aicheck start | `/ai| /aicheck start | <msg>`| /aicheck start | || /aicheck start | Chat| /aicheck start | with| /aicheck start | AI| /aicheck start | |
|| /aicheck start | `/aiscore`| /aicheck start | || /aicheck start | Check| /aicheck start | behavior| /aicheck start | score| /aicheck start | &| /aicheck start | rules| /aicheck start | |

###| /aicheck start | Admin| /aicheck start | Commands

|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `/aiaccept| /aicheck start | <n>`| /aicheck start | || /aicheck start | Approve| /aicheck start | pending| /aicheck start | command| /aicheck start | |
|| /aicheck start | `/aireject| /aicheck start | <n>`| /aicheck start | || /aicheck start | Reject| /aicheck start | pending| /aicheck start | command| /aicheck start | |
|| /aicheck start | `/aiquery`| /aicheck start | || /aicheck start | List| /aicheck start | pending| /aicheck start | approvals| /aicheck start | |
|| /aicheck start | `/aiclear`| /aicheck start | || /aicheck start | Clear| /aicheck start | chat| /aicheck start | history| /aicheck start | |
|| /aicheck start | `/aireload`| /aicheck start | || /aicheck start | Reload| /aicheck start | config| /aicheck start | |
|| /aicheck start | `/aikb| /aicheck start | <keyword>`| /aicheck start | || /aicheck start | Search| /aicheck start | knowledge| /aicheck start | base| /aicheck start | |

###| /aicheck start | Review| /aicheck start | System

|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `/aicheck`| /aicheck start | || /aicheck start | Trigger| /aicheck start | manual| /aicheck start | review| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | approve| /aicheck start | <id>`| /aicheck start | || /aicheck start | Approve| /aicheck start | kick| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | reject| /aicheck start | <id>`| /aicheck start | || /aicheck start | Reject| /aicheck start | kick| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last`| /aicheck start | || /aicheck start | View| /aicheck start | last| /aicheck start | review| /aicheck start | result| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last| /aicheck start | reasoning`| /aicheck start | || /aicheck start | View| /aicheck start | AI| /aicheck start | reasoning| /aicheck start | |

###| /aicheck start | Test| /aicheck start | Commands| /aicheck start | (OP| /aicheck start | only)

|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `/aitest| /aicheck start | score| /aicheck start | <player>`| /aicheck start | || /aicheck start | Check| /aicheck start | player| /aicheck start | score| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | set| /aicheck start | <player>| /aicheck start | <score>`| /aicheck start | || /aicheck start | Set| /aicheck start | player| /aicheck start | score| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | penalty| /aicheck start | <player>| /aicheck start | <points>`| /aicheck start | || /aicheck start | Simulate| /aicheck start | penalty| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | reset| /aicheck start | <player>`| /aicheck start | || /aicheck start | Reset| /aicheck start | player| /aicheck start | score| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | review`| /aicheck start | || /aicheck start | Trigger| /aicheck start | review| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | chatlog`| /aicheck start | || /aicheck start | View| /aicheck start | chat| /aicheck start | log| /aicheck start | |

---

##| /aicheck start | Review| /aicheck start | System

###| /aicheck start | Flow
1.| /aicheck start | AI| /aicheck start | analyzes| /aicheck start | chat| /aicheck start | every| /aicheck start | N| /aicheck start | minutes| /aicheck start | (configurable,| /aicheck start | default| /aicheck start | 30)
2.| /aicheck start | Detects| /aicheck start | violations,| /aicheck start | suggests| /aicheck start | penalties
3.| /aicheck start | Penalties| /aicheck start | are| /aicheck start | executed| /aicheck start | and| /aicheck start | broadcast

###| /aicheck start | Three-Tier| /aicheck start | Penalty

|| /aicheck start | Tier| /aicheck start | || /aicheck start | Condition| /aicheck start | || /aicheck start | Effect| /aicheck start | |
|------|----------|--------|
|| /aicheck start | Score| /aicheck start | || /aicheck start | severity| /aicheck start | -10| /aicheck start | || /aicheck start | Score| /aicheck start | only,| /aicheck start | no| /aicheck start | broadcast| /aicheck start | |
|| /aicheck start | Yellow| /aicheck start | || /aicheck start | severity| /aicheck start | -20| /aicheck start | or| /aicheck start | â‰?-30| /aicheck start | || /aicheck start | Broadcast| /aicheck start | warning| /aicheck start | |
|| /aicheck start | Red| /aicheck start | || /aicheck start | severity| /aicheck start | -30| /aicheck start | or| /aicheck start | â‰?-60| /aicheck start | || /aicheck start | Broadcast| /aicheck start | +| /aicheck start | kick| /aicheck start | (admin| /aicheck start | approval)| /aicheck start | |

###| /aicheck start | Score| /aicheck start | Recovery
-| /aicheck start | Online| /aicheck start | non-admin| /aicheck start | players| /aicheck start | recover| /aicheck start | **5| /aicheck start | points**| /aicheck start | per| /aicheck start | review| /aicheck start | cycle
-| /aicheck start | Caps| /aicheck start | at| /aicheck start | **0**

###| /aicheck start | Evidence| /aicheck start | Standard
-| /aicheck start | Multiple| /aicheck start | reports| /aicheck start | â†?sufficient| /aicheck start | evidence
-| /aicheck start | Single| /aicheck start | report| /aicheck start | without| /aicheck start | corroboration| /aicheck start | â†?no| /aicheck start | penalty
-| /aicheck start | Admin| /aicheck start | statements| /aicheck start | override| /aicheck start | all| /aicheck start | claims

---

##| /aicheck start | Approval| /aicheck start | System

-| /aicheck start | Dangerous| /aicheck start | commands| /aicheck start | (op,| /aicheck start | ban,| /aicheck start | kick| /aicheck start | etc.)| /aicheck start | require| /aicheck start | admin| /aicheck start | approval
-| /aicheck start | AI| /aicheck start | blocks| /aicheck start | waiting| /aicheck start | for| /aicheck start | approval;| /aicheck start | 3-min| /aicheck start | timeout| /aicheck start | auto-cancels
-| /aicheck start | Strict| /aicheck start | mode:| /aicheck start | only| /aicheck start | whitelisted| /aicheck start | safe| /aicheck start | commands| /aicheck start | skip| /aicheck start | approval

---

##| /aicheck start | Configuration

File:| /aicheck start | `config/mcai/config.json`.| /aicheck start | Reload| /aicheck start | with| /aicheck start | `/aireload`.

|| /aicheck start | Key| /aicheck start | || /aicheck start | Default| /aicheck start | || /aicheck start | Description| /aicheck start | |
|-----|---------|-------------|
|| /aicheck start | `apiEndpoint`| /aicheck start | || /aicheck start | `https://api.deepseek.com`| /aicheck start | || /aicheck start | API| /aicheck start | endpoint| /aicheck start | |
|| /aicheck start | `apiKey`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | API| /aicheck start | key| /aicheck start | |
|| /aicheck start | `model`| /aicheck start | || /aicheck start | `deepseek-v4-flash`| /aicheck start | || /aicheck start | Model| /aicheck start | name| /aicheck start | |
|| /aicheck start | `triggerPrefix`| /aicheck start | || /aicheck start | `!ai`| /aicheck start | || /aicheck start | Chat| /aicheck start | trigger| /aicheck start | prefix| /aicheck start | |
|| /aicheck start | `maxTokens`| /aicheck start | || /aicheck start | `2048`| /aicheck start | || /aicheck start | Max| /aicheck start | response| /aicheck start | tokens| /aicheck start | |
|| /aicheck start | `temperature`| /aicheck start | || /aicheck start | `0.75`| /aicheck start | || /aicheck start | Response| /aicheck start | randomness| /aicheck start | |
|| /aicheck start | `thinkingLevel`| /aicheck start | || /aicheck start | `1`| /aicheck start | || /aicheck start | Thinking| /aicheck start | level| /aicheck start | 0-3| /aicheck start | |
|| /aicheck start | `strictMode`| /aicheck start | || /aicheck start | `true`| /aicheck start | || /aicheck start | Strict| /aicheck start | mode| /aicheck start | |
|| /aicheck start | `reviewIntervalMinutes`| /aicheck start | || /aicheck start | `30`| /aicheck start | || /aicheck start | Review| /aicheck start | interval| /aicheck start | (min)| /aicheck start | |
|| /aicheck start | `yellowCardThreshold`| /aicheck start | || /aicheck start | `-30`| /aicheck start | || /aicheck start | Yellow| /aicheck start | card| /aicheck start | threshold| /aicheck start | |
|| /aicheck start | `redCardThreshold`| /aicheck start | || /aicheck start | `-60`| /aicheck start | || /aicheck start | Red| /aicheck start | card| /aicheck start | threshold| /aicheck start | |
|| /aicheck start | `scoreRecoveryPerInterval`| /aicheck start | || /aicheck start | `5`| /aicheck start | || /aicheck start | Score| /aicheck start | recovery| /aicheck start | per| /aicheck start | cycle| /aicheck start | |
|| /aicheck start | `approvalTimeoutMinutes`| /aicheck start | || /aicheck start | `10`| /aicheck start | || /aicheck start | Approval| /aicheck start | timeout| /aicheck start | (min)| /aicheck start | |
|| /aicheck start | `systemPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | System| /aicheck start | prompt| /aicheck start | file| /aicheck start | path| /aicheck start | |
|| /aicheck start | `reviewPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | Review| /aicheck start | prompt| /aicheck start | file| /aicheck start | path| /aicheck start | |
|| /aicheck start | `promptLanguage`| /aicheck start | || /aicheck start | `zh_cn`| /aicheck start | || /aicheck start | Built-in| /aicheck start | prompt| /aicheck start | language| /aicheck start | |

---

##| /aicheck start | File| /aicheck start | Structure

`config/mcai/`:

|| /aicheck start | File| /aicheck start | || /aicheck start | Content| /aicheck start | |
|------|---------|
|| /aicheck start | `config.json`| /aicheck start | || /aicheck start | Main| /aicheck start | config| /aicheck start | |
|| /aicheck start | `scores.json`| /aicheck start | || /aicheck start | Player| /aicheck start | behavior| /aicheck start | scores| /aicheck start | |
|| /aicheck start | `penalties.json`| /aicheck start | || /aicheck start | Penalty| /aicheck start | history| /aicheck start | |
|| /aicheck start | `system_prompt.txt`| /aicheck start | || /aicheck start | System| /aicheck start | prompt| /aicheck start | (customizable)| /aicheck start | |
|| /aicheck start | `review_prompt.txt`| /aicheck start | || /aicheck start | Review| /aicheck start | prompt| /aicheck start | (customizable)| /aicheck start | |
|| /aicheck start | `review_last_response.txt`| /aicheck start | || /aicheck start | Last| /aicheck start | review| /aicheck start | AI| /aicheck start | raw| /aicheck start | output| /aicheck start | |
|| /aicheck start | `review_last_reasoning.txt`| /aicheck start | || /aicheck start | Last| /aicheck start | review| /aicheck start | AI| /aicheck start | reasoning| /aicheck start | |

---

##| /aicheck start | FAQ

**Q:| /aicheck start | Review| /aicheck start | too| /aicheck start | strict/lenient?**
Adjust| /aicheck start | `yellowCardThreshold`| /aicheck start | and| /aicheck start | `redCardThreshold`.| /aicheck start | Higher| /aicheck start | values| /aicheck start | =| /aicheck start | stricter.

**Q:| /aicheck start | Want| /aicheck start | to| /aicheck start | block| /aicheck start | certain| /aicheck start | commands?**
Remove| /aicheck start | from| /aicheck start | `safeCommands`| /aicheck start | whitelist| /aicheck start | or| /aicheck start | add| /aicheck start | to| /aicheck start | `requireApprovalCommands`.

**Q:| /aicheck start | Can| /aicheck start | I| /aicheck start | use| /aicheck start | other| /aicheck start | API| /aicheck start | providers?**
Yes.| /aicheck start | Change| /aicheck start | `apiEndpoint`| /aicheck start | to| /aicheck start | any| /aicheck start | OpenAI-compatible| /aicheck start | endpoint.

**Q:| /aicheck start | Cost?**
DeepSeek| /aicheck start | flash| /aicheck start | is| /aicheck start | very| /aicheck start | cheap.| /aicheck start | Review| /aicheck start | calls| /aicheck start | every| /aicheck start | 30| /aicheck start | min.| /aicheck start | Daily| /aicheck start | use| /aicheck start | costs| /aicheck start | pennies.
