#| /aicheck start | MCAI| /aicheck start | -| /aicheck start | Minecraft| /aicheck start | AI| /aicheck start | Assistant

[![License:| /aicheck start | MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.1.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.java.com/)

>| /aicheck start | [中文版本](README.md)

MCAI| /aicheck start | is| /aicheck start | a| /aicheck start | Fabric| /aicheck start | mod| /aicheck start | that| /aicheck start | lets| /aicheck start | AI| /aicheck start | manage| /aicheck start | your| /aicheck start | Minecraft| /aicheck start | server.| /aicheck start | This| /aicheck start | entire| /aicheck start | project| /aicheck start | is| /aicheck start | AI-developed.

**TL;DR:**| /aicheck start | Players| /aicheck start | type| /aicheck start | `/ai| /aicheck start | how| /aicheck start | do| /aicheck start | I| /aicheck start | enchant| /aicheck start | a| /aicheck start | diamond| /aicheck start | sword?`,| /aicheck start | AI| /aicheck start | answers.| /aicheck start | Someone| /aicheck start | swears?| /aicheck start | AI| /aicheck start | warns| /aicheck start | or| /aicheck start | kicks.| /aicheck start | Dangerous| /aicheck start | ops| /aicheck start | need| /aicheck start | admin| /aicheck start | approval.

---

##| /aicheck start | Quick| /aicheck start | Start

|| /aicheck start | What| /aicheck start | || /aicheck start | How| /aicheck start | |
|------|-----|
|| /aicheck start | Chat| /aicheck start | with| /aicheck start | AI| /aicheck start | || /aicheck start | `/ai| /aicheck start | <question>`| /aicheck start | or| /aicheck start | `!ai| /aicheck start | <question>`| /aicheck start | |
|| /aicheck start | AI| /aicheck start | executes| /aicheck start | commands| /aicheck start | || /aicheck start | `/ai| /aicheck start | give| /aicheck start | me| /aicheck start | a| /aicheck start | diamond| /aicheck start | sword`| /aicheck start | (admin| /aicheck start | approval)| /aicheck start | |
|| /aicheck start | Check| /aicheck start | behavior| /aicheck start | score| /aicheck start | || /aicheck start | `/aiscore`| /aicheck start | |
|| /aicheck start | Search| /aicheck start | wiki| /aicheck start | || /aicheck start | `/aikb| /aicheck start | enchantment`| /aicheck start | |
|| /aicheck start | Admin| /aicheck start | approve| /aicheck start | || /aicheck start | `/aiaccept| /aicheck start | 1`| /aicheck start | approve| /aicheck start | /| /aicheck start | `/aireject| /aicheck start | 1`| /aicheck start | reject| /aicheck start | |

---

##| /aicheck start | Features

###| /aicheck start | AI| /aicheck start | Chat
-| /aicheck start | Players| /aicheck start | use| /aicheck start | `!ai`| /aicheck start | or| /aicheck start | `/ai`| /aicheck start | to| /aicheck start | chat| /aicheck start | with| /aicheck start | AI
-| /aicheck start | AI| /aicheck start | has| /aicheck start | full| /aicheck start | server| /aicheck start | context| /aicheck start | (chat| /aicheck start | logs,| /aicheck start | weather,| /aicheck start | time,| /aicheck start | players)
-| /aicheck start | Multi-turn| /aicheck start | conversation| /aicheck start | with| /aicheck start | history

###| /aicheck start | Auto| /aicheck start | Behavior| /aicheck start | Review
-| /aicheck start | AI| /aicheck start | analyzes| /aicheck start | chat| /aicheck start | every| /aicheck start | 30| /aicheck start | minutes
-| /aicheck start | Three-tier| /aicheck start | penalty:| /aicheck start | score| /aicheck start | deduction| /aicheck start | �?yellow| /aicheck start | card| /aicheck start | �?red| /aicheck start | card| /aicheck start | (kick)
-| /aicheck start | Admin| /aicheck start | messages| /aicheck start | are| /aicheck start | trusted;| /aicheck start | admins| /aicheck start | can| /aicheck start | declare| /aicheck start | server| /aicheck start | rules

###| /aicheck start | Admin| /aicheck start | Approval
-| /aicheck start | Dangerous| /aicheck start | commands| /aicheck start | (op,| /aicheck start | ban,| /aicheck start | kick)| /aicheck start | require| /aicheck start | manual| /aicheck start | admin| /aicheck start | approval
-| /aicheck start | 3-minute| /aicheck start | timeout| /aicheck start | auto-cancels| /aicheck start | pending| /aicheck start | approvals
-| /aicheck start | Strict| /aicheck start | mode:| /aicheck start | only| /aicheck start | whitelisted| /aicheck start | safe| /aicheck start | commands| /aicheck start | skip| /aicheck start | approval

###| /aicheck start | Game| /aicheck start | Knowledge| /aicheck start | Base
-| /aicheck start | Built-in| /aicheck start | Chinese| /aicheck start | Minecraft| /aicheck start | Wiki| /aicheck start | entries
-| /aicheck start | Online| /aicheck start | search| /aicheck start | with| /aicheck start | local| /aicheck start | fallback

---

##| /aicheck start | Installation

###| /aicheck start | Requirements
-| /aicheck start | Minecraft| /aicheck start | **Fabric| /aicheck start | server| /aicheck start | 26.1.2**
-| /aicheck start | [Java](https://www.java.com/)| /aicheck start | 25
-| /aicheck start | A| /aicheck start | [DeepSeek| /aicheck start | API| /aicheck start | Key](https://platform.deepseek.com)| /aicheck start | (or| /aicheck start | any| /aicheck start | OpenAI-compatible| /aicheck start | API)

###| /aicheck start | Steps
1.| /aicheck start | Download| /aicheck start | the| /aicheck start | latest| /aicheck start | JAR| /aicheck start | from| /aicheck start | [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)
2.| /aicheck start | Place| /aicheck start | it| /aicheck start | in| /aicheck start | `mods/`| /aicheck start | folder
3.| /aicheck start | Start| /aicheck start | the| /aicheck start | server| /aicheck start | to| /aicheck start | auto-generate| /aicheck start | config
4.| /aicheck start | Edit| /aicheck start | `config/mcai/config.json`,| /aicheck start | fill| /aicheck start | in| /aicheck start | your| /aicheck start | API| /aicheck start | Key
5.| /aicheck start | Run| /aicheck start | `/aireload`| /aicheck start | to| /aicheck start | reload| /aicheck start | config

---

##| /aicheck start | Commands

###| /aicheck start | Player| /aicheck start | Commands
|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `!ai| /aicheck start | <msg>`| /aicheck start | `/ai| /aicheck start | <msg>`| /aicheck start | || /aicheck start | Chat| /aicheck start | with| /aicheck start | AI| /aicheck start | |
|| /aicheck start | `/aiscore`| /aicheck start | || /aicheck start | Check| /aicheck start | behavior| /aicheck start | score| /aicheck start | |

###| /aicheck start | Admin| /aicheck start | Commands
|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `/aiaccept| /aicheck start | <n>`| /aicheck start | || /aicheck start | Approve| /aicheck start | pending| /aicheck start | action| /aicheck start | |
|| /aicheck start | `/aireject| /aicheck start | <n>`| /aicheck start | || /aicheck start | Reject| /aicheck start | pending| /aicheck start | action| /aicheck start | |
|| /aicheck start | `/aiquery`| /aicheck start | || /aicheck start | List| /aicheck start | pending| /aicheck start | approvals| /aicheck start | |
|| /aicheck start | `/aiclear`| /aicheck start | || /aicheck start | Clear| /aicheck start | AI| /aicheck start | chat| /aicheck start | history| /aicheck start | |
|| /aicheck start | `/aireload`| /aicheck start | || /aicheck start | Reload| /aicheck start | config| /aicheck start | |
|| /aicheck start | `/aikb| /aicheck start | <keyword>`| /aicheck start | || /aicheck start | Search| /aicheck start | knowledge| /aicheck start | base| /aicheck start | |

###| /aicheck start | Review| /aicheck start | Management
|| /aicheck start | Command| /aicheck start | || /aicheck start | Description| /aicheck start | |
|---------|-------------|
|| /aicheck start | `/aicheck`| /aicheck start | || /aicheck start | Trigger| /aicheck start | manual| /aicheck start | review| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | approve| /aicheck start | <id>`| /aicheck start | || /aicheck start | Approve| /aicheck start | kick| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | reject| /aicheck start | <id>`| /aicheck start | || /aicheck start | Reject| /aicheck start | kick| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last`| /aicheck start | || /aicheck start | View| /aicheck start | last| /aicheck start | review| /aicheck start | result| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last| /aicheck start | reasoning`| /aicheck start | || /aicheck start | View| /aicheck start | AI| /aicheck start | reasoning| /aicheck start | |

---

##| /aicheck start | Configuration

File:| /aicheck start | `config/mcai/config.json`,| /aicheck start | reload| /aicheck start | with| /aicheck start | `/aireload`.

|| /aicheck start | Key| /aicheck start | || /aicheck start | Default| /aicheck start | || /aicheck start | Description| /aicheck start | |
|-----|---------|-------------|
|| /aicheck start | `apiEndpoint`| /aicheck start | || /aicheck start | `https://api.deepseek.com`| /aicheck start | || /aicheck start | API| /aicheck start | endpoint| /aicheck start | |
|| /aicheck start | `apiKey`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | API| /aicheck start | key| /aicheck start | |
|| /aicheck start | `model`| /aicheck start | || /aicheck start | `deepseek-v4-flash`| /aicheck start | || /aicheck start | Model| /aicheck start | name| /aicheck start | |
|| /aicheck start | `strictMode`| /aicheck start | || /aicheck start | `true`| /aicheck start | || /aicheck start | Strict| /aicheck start | mode| /aicheck start | |
|| /aicheck start | `reviewIntervalMinutes`| /aicheck start | || /aicheck start | `30`| /aicheck start | || /aicheck start | Review| /aicheck start | interval| /aicheck start | (min)| /aicheck start | |
|| /aicheck start | `yellowCardThreshold`| /aicheck start | || /aicheck start | `-30`| /aicheck start | || /aicheck start | Yellow| /aicheck start | card| /aicheck start | threshold| /aicheck start | |
|| /aicheck start | `redCardThreshold`| /aicheck start | || /aicheck start | `-60`| /aicheck start | || /aicheck start | Red| /aicheck start | card| /aicheck start | threshold| /aicheck start | |
|| /aicheck start | `systemPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | System| /aicheck start | prompt| /aicheck start | file| /aicheck start | (under| /aicheck start | config/mcai/)| /aicheck start | |
|| /aicheck start | `reviewPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | Review| /aicheck start | prompt| /aicheck start | file| /aicheck start | |
|| /aicheck start | `promptLanguage`| /aicheck start | || /aicheck start | `zh_cn`| /aicheck start | || /aicheck start | Built-in| /aicheck start | prompt| /aicheck start | language| /aicheck start | |

Prompt| /aicheck start | files| /aicheck start | `system_prompt.txt`| /aicheck start | /| /aicheck start | `review_prompt.txt`| /aicheck start | are| /aicheck start | auto-created| /aicheck start | on| /aicheck start | first| /aicheck start | start.

---

##| /aicheck start | Build

```bash
git| /aicheck start | clone| /aicheck start | https://github.com/lll114514lll1919810lll/mcai_mod.git
cd| /aicheck start | mcai_mod
.\gradlew.bat| /aicheck start | build
#| /aicheck start | Output:| /aicheck start | build/libs/mcai-<version>.jar
```

Requires| /aicheck start | JDK| /aicheck start | 25.

---

##| /aicheck start | Links

-| /aicheck start | [用户手册| /aicheck start | /| /aicheck start | User| /aicheck start | Guide| /aicheck start | (中文)](USER_GUIDE.md)
-| /aicheck start | [开发总结| /aicheck start | /| /aicheck start | Developer| /aicheck start | Notes| /aicheck start | (中文)](MCAI_MOD_SUMMARY.md)
-| /aicheck start | [English| /aicheck start | User| /aicheck start | Guide](USER_GUIDE_EN.md)
-| /aicheck start | [English| /aicheck start | Developer| /aicheck start | Notes](MCAI_MOD_SUMMARY_EN.md)
-| /aicheck start | [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

##| /aicheck start | License

[MIT| /aicheck start | License](LICENSE)
