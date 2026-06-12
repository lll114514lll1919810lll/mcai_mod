<div| /aicheck start | align="center">

<img| /aicheck start | src="src/main/resources/assets/mcai/icon.png"| /aicheck start | width="128"| /aicheck start | alt="MCAI| /aicheck start | Logo">

#| /aicheck start | MCAI| /aicheck start | -| /aicheck start | Minecraft| /aicheck start | AI| /aicheck start | 助手

[![License:| /aicheck start | MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Fabric](https://img.shields.io/badge/Fabric-26.1.2-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.java.com/)

</div>

>| /aicheck start | [English| /aicheck start | version](README_EN.md)| /aicheck start | 

MCAI| /aicheck start | 是一�?Minecraft| /aicheck start | Fabric| /aicheck start | 模组，让| /aicheck start | AI| /aicheck start | 自动管理你的服务器。纯| /aicheck start | AI| /aicheck start | 开发�?

**简单来说：**| /aicheck start | 玩家�?`/ai| /aicheck start | 帮我查附魔`，AI| /aicheck start | 回答；有人骂人，AI| /aicheck start | 自动警告或踢出；危险操作管理员批准才能执行�?

---

##| /aicheck start | 一分钟了解

|| /aicheck start | 你想做什�?|| /aicheck start | 怎么�?|
|-----------|--------|
|| /aicheck start | �?AI| /aicheck start | 聊天| /aicheck start | || /aicheck start | `/ai| /aicheck start | <问题>`| /aicheck start | �?`!ai| /aicheck start | <问题>`| /aicheck start | |
|| /aicheck start | �?AI| /aicheck start | 执行命令| /aicheck start | || /aicheck start | `/ai| /aicheck start | 给我一把钻石剑`（管理员审批�?|
|| /aicheck start | 查看行为�?|| /aicheck start | `/aiscore`| /aicheck start | |
|| /aicheck start | 搜索知识�?|| /aicheck start | `/aikb| /aicheck start | 附魔`| /aicheck start | |
|| /aicheck start | 管理员审�?|| /aicheck start | `/aiaccept| /aicheck start | 1`| /aicheck start | 批准| /aicheck start | /| /aicheck start | `/aireject| /aicheck start | 1`| /aicheck start | 拒绝| /aicheck start | |

---

##| /aicheck start | 核心功能

###| /aicheck start | AI| /aicheck start | 对话
-| /aicheck start | 玩家�?`!ai`| /aicheck start | �?`/ai`| /aicheck start | �?AI| /aicheck start | 聊天
-| /aicheck start | AI| /aicheck start | 知道服务器里发生了什么（聊天记录、天气、时间等�?
-| /aicheck start | 支持多轮对话，记住上下文

###| /aicheck start | 自动行为审查
-| /aicheck start | AI| /aicheck start | �?30| /aicheck start | 分钟自动检查聊天记�?
-| /aicheck start | 三级处罚：扣�?�?黄牌警告| /aicheck start | �?红牌踢出

###| /aicheck start | 安全审批
-| /aicheck start | 危险命令需要管理员手动批准�?| /aicheck start | 分钟超时自动取消
-| /aicheck start | 严格模式下仅白名单安全命令免审批

###| /aicheck start | 游戏知识�?
-| /aicheck start | 内置中文| /aicheck start | Minecraft| /aicheck start | Wiki| /aicheck start | 核心条目
-| /aicheck start | 优先在线搜索，失败时回退到本地知识库

---

##| /aicheck start | 安装

###| /aicheck start | 你需要准�?
-| /aicheck start | Minecraft| /aicheck start | **Fabric| /aicheck start | 服务�?26.1.2**
-| /aicheck start | [Java](https://www.java.com/)| /aicheck start | 25
-| /aicheck start | 一�?[DeepSeek| /aicheck start | API| /aicheck start | Key](https://platform.deepseek.com)

###| /aicheck start | 安装步骤
1.| /aicheck start | �?[Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)| /aicheck start | 下载最新版| /aicheck start | JAR
2.| /aicheck start | 放入| /aicheck start | `mods/`| /aicheck start | 文件�?
3.| /aicheck start | 启动服务端，自动生成配置
4.| /aicheck start | 编辑| /aicheck start | `config/mcai/config.json`，填�?API| /aicheck start | Key
5.| /aicheck start | 执行| /aicheck start | `/aireload`| /aicheck start | 重载

---

##| /aicheck start | 命令一�?

###| /aicheck start | 玩家命令
|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `!ai| /aicheck start | <消息>`| /aicheck start | `/ai| /aicheck start | <消息>`| /aicheck start | || /aicheck start | �?AI| /aicheck start | 聊天| /aicheck start | |
|| /aicheck start | `/aiscore`| /aicheck start | || /aicheck start | 查看行为�?|

###| /aicheck start | 管理员命�?
|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `/aiaccept| /aicheck start | <编号>`| /aicheck start | || /aicheck start | 批准待审批操�?|
|| /aicheck start | `/aireject| /aicheck start | <编号>`| /aicheck start | || /aicheck start | 拒绝待审批操�?|
|| /aicheck start | `/aiquery`| /aicheck start | || /aicheck start | 查看待审批列�?|
|| /aicheck start | `/aiclear`| /aicheck start | || /aicheck start | 清除| /aicheck start | AI| /aicheck start | 对话历史| /aicheck start | |
|| /aicheck start | `/aireload`| /aicheck start | || /aicheck start | 重载配置| /aicheck start | |
|| /aicheck start | `/aikb| /aicheck start | <关键�?`| /aicheck start | || /aicheck start | 搜索知识�?|

###| /aicheck start | 审查管理
|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `/aicheck`| /aicheck start | || /aicheck start | 手动触发审查| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | approve| /aicheck start | <id>`| /aicheck start | || /aicheck start | 批准踢出| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | reject| /aicheck start | <id>`| /aicheck start | || /aicheck start | 拒绝踢出| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last`| /aicheck start | || /aicheck start | 查看上次审查结果| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last| /aicheck start | reasoning`| /aicheck start | || /aicheck start | 查看| /aicheck start | AI| /aicheck start | 推理过程| /aicheck start | |

---

##| /aicheck start | 配置

文件位置：`config/mcai/config.json`，修改后�?`/aireload`| /aicheck start | 重载�?

|| /aicheck start | 配置�?|| /aicheck start | 默认�?|| /aicheck start | 说明| /aicheck start | |
|--------|--------|------|
|| /aicheck start | `apiEndpoint`| /aicheck start | || /aicheck start | `https://api.deepseek.com`| /aicheck start | || /aicheck start | API| /aicheck start | 地址| /aicheck start | |
|| /aicheck start | `apiKey`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | API| /aicheck start | 密钥| /aicheck start | |
|| /aicheck start | `model`| /aicheck start | || /aicheck start | `deepseek-v4-flash`| /aicheck start | || /aicheck start | 模型名称| /aicheck start | |
|| /aicheck start | `strictMode`| /aicheck start | || /aicheck start | `true`| /aicheck start | || /aicheck start | 严格模式| /aicheck start | |
|| /aicheck start | `reviewIntervalMinutes`| /aicheck start | || /aicheck start | `30`| /aicheck start | || /aicheck start | 审查间隔（分钟）| /aicheck start | |
|| /aicheck start | `yellowCardThreshold`| /aicheck start | || /aicheck start | `-30`| /aicheck start | || /aicheck start | 黄牌阈�?|
|| /aicheck start | `redCardThreshold`| /aicheck start | || /aicheck start | `-60`| /aicheck start | || /aicheck start | 红牌阈�?|
|| /aicheck start | `systemPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | AI提示词文件（config/mcai/下）| /aicheck start | |
|| /aicheck start | `reviewPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | 审查提示词文�?|
|| /aicheck start | `promptLanguage`| /aicheck start | || /aicheck start | `zh_cn`| /aicheck start | || /aicheck start | 内置提示词语言| /aicheck start | |

提示词文�?`system_prompt.txt`| /aicheck start | /| /aicheck start | `review_prompt.txt`| /aicheck start | 首次启动自动创建�?

---

##| /aicheck start | 构建

```bash
git| /aicheck start | clone| /aicheck start | https://github.com/lll114514lll1919810lll/mcai_mod.git
cd| /aicheck start | mcai_mod
.\gradlew.bat| /aicheck start | build
#| /aicheck start | 产物:| /aicheck start | build/libs/mcai-<version>.jar
```

需�?JDK| /aicheck start | 25�?

---

##| /aicheck start | 链接

-| /aicheck start | [服主使用手册](USER_GUIDE.md)
-| /aicheck start | [开发总结](MCAI_MOD_SUMMARY.md)
-| /aicheck start | [English| /aicheck start | version](README_EN.md)
-| /aicheck start | [Releases](https://github.com/lll114514lll1919810lll/mcai_mod/releases)

##| /aicheck start | 许可�?

[MIT| /aicheck start | License](LICENSE)
