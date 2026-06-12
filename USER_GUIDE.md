#| /aicheck start | MCAI| /aicheck start | 服主使用手册

>| /aicheck start | [English| /aicheck start | version](USER_GUIDE_EN.md)

---

##| /aicheck start | 安装

###| /aicheck start | 前提条件
-| /aicheck start | Minecraft| /aicheck start | **Fabric| /aicheck start | 服务�?26.1.2**
-| /aicheck start | JDK| /aicheck start | 25
-| /aicheck start | 一�?**DeepSeek| /aicheck start | API| /aicheck start | Key**（[platform.deepseek.com](https://platform.deepseek.com)�?

###| /aicheck start | 步骤
1.| /aicheck start | 下载| /aicheck start | `mcai-<version>.jar`| /aicheck start | 放入| /aicheck start | `mods/`| /aicheck start | 目录
2.| /aicheck start | 启动一次服务器，自动在| /aicheck start | `config/mcai/config.json`| /aicheck start | 生成默认配置
3.| /aicheck start | 编辑| /aicheck start | `config/mcai/config.json`，填入你�?API| /aicheck start | Key
4.| /aicheck start | 执行| /aicheck start | `/aireload`| /aicheck start | 重载

```json
{
| /aicheck start | | /aicheck start | "apiEndpoint":| /aicheck start | "https://api.deepseek.com",
| /aicheck start | | /aicheck start | "apiKey":| /aicheck start | "sk-xxxxxxxxxxxxxxxxxxxxx",
| /aicheck start | | /aicheck start | "model":| /aicheck start | "deepseek-v4-flash"
}
```

---

##| /aicheck start | 所有命�?

###| /aicheck start | 玩家命令

|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `!ai| /aicheck start | <消息>`| /aicheck start | �?`/ai| /aicheck start | <消息>`| /aicheck start | || /aicheck start | �?AI| /aicheck start | 对话| /aicheck start | |
|| /aicheck start | `/aiscore`| /aicheck start | || /aicheck start | 查看行为分和处罚规则| /aicheck start | |

###| /aicheck start | 管理员命�?

|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `/aiaccept| /aicheck start | <编号>`| /aicheck start | || /aicheck start | 批准待审批指�?|
|| /aicheck start | `/aireject| /aicheck start | <编号>`| /aicheck start | || /aicheck start | 拒绝待审批指�?|
|| /aicheck start | `/aiquery`| /aicheck start | || /aicheck start | 查看待审批列�?|
|| /aicheck start | `/aiclear`| /aicheck start | || /aicheck start | 清除对话历史| /aicheck start | |
|| /aicheck start | `/aireload`| /aicheck start | || /aicheck start | 重载配置（清空状态）| /aicheck start | |
|| /aicheck start | `/aikb| /aicheck start | <关键�?`| /aicheck start | || /aicheck start | 搜索知识�?|

###| /aicheck start | 审查系统

|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `/aicheck`| /aicheck start | || /aicheck start | 手动触发审查| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | approve| /aicheck start | <id>`| /aicheck start | || /aicheck start | 批准踢出| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | reject| /aicheck start | <id>`| /aicheck start | || /aicheck start | 拒绝踢出| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last`| /aicheck start | || /aicheck start | 查看上次审查结果| /aicheck start | |
|| /aicheck start | `/aicheck| /aicheck start | last| /aicheck start | reasoning`| /aicheck start | || /aicheck start | 查看| /aicheck start | AI| /aicheck start | 推理过程| /aicheck start | |

###| /aicheck start | 测试辅助（OP| /aicheck start | 专用�?

|| /aicheck start | 命令| /aicheck start | || /aicheck start | 说明| /aicheck start | |
|------|------|
|| /aicheck start | `/aitest| /aicheck start | score| /aicheck start | <玩家>`| /aicheck start | || /aicheck start | 查玩家行为分| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | set| /aicheck start | <玩家>| /aicheck start | <分数>`| /aicheck start | || /aicheck start | 设置行为�?|
|| /aicheck start | `/aitest| /aicheck start | penalty| /aicheck start | <玩家>| /aicheck start | <分数>`| /aicheck start | || /aicheck start | 模拟扣分| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | reset| /aicheck start | <玩家>`| /aicheck start | || /aicheck start | 重置行为�?|
|| /aicheck start | `/aitest| /aicheck start | review`| /aicheck start | || /aicheck start | 手动审查| /aicheck start | |
|| /aicheck start | `/aitest| /aicheck start | chatlog`| /aicheck start | || /aicheck start | 查看聊天日志| /aicheck start | |

---

##| /aicheck start | 审核系统

###| /aicheck start | 基本流程
1.| /aicheck start | **�?30| /aicheck start | 分钟**（可配置），AI| /aicheck start | 分析聊天记录
2.| /aicheck start | 识别违规，输出处罚建�?
3.| /aicheck start | 执行处罚，公屏广�?

###| /aicheck start | 三级处罚

|| /aicheck start | 等级| /aicheck start | || /aicheck start | 条件| /aicheck start | || /aicheck start | 效果| /aicheck start | |
|------|------|------|
|| /aicheck start | 扣分| /aicheck start | || /aicheck start | severity| /aicheck start | -10| /aicheck start | || /aicheck start | 扣分，无公屏| /aicheck start | |
|| /aicheck start | 黄牌| /aicheck start | || /aicheck start | severity| /aicheck start | -20| /aicheck start | �?�?-30| /aicheck start | || /aicheck start | 公屏警告| /aicheck start | |
|| /aicheck start | 红牌| /aicheck start | || /aicheck start | severity| /aicheck start | -30| /aicheck start | �?�?-60| /aicheck start | || /aicheck start | 广播| /aicheck start | +| /aicheck start | 踢出（管理员审批�?|

###| /aicheck start | 分数恢复
-| /aicheck start | 每轮审查，在线非管理玩家恢复| /aicheck start | **5| /aicheck start | �?*（可配置�?
-| /aicheck start | 上限| /aicheck start | **0| /aicheck start | �?*

###| /aicheck start | 证据标准
-| /aicheck start | **多人举报**| /aicheck start | �?构成证据
-| /aicheck start | **单人无佐�?*| /aicheck start | �?不判�?
-| /aicheck start | **管理员发言**具有最高效�?

###| /aicheck start | 管理员如何介�?
-| /aicheck start | 游戏里说话带| /aicheck start | `[管理员]`| /aicheck start | 标记，AI| /aicheck start | 自动信任
-| /aicheck start | 例如你说"这是无规则PVP�?，AI| /aicheck start | 就不会判杀人违�?

---

##| /aicheck start | 审批系统

-| /aicheck start | 危险指令（op、ban、kick| /aicheck start | 等）需要管理员批准
-| /aicheck start | AI| /aicheck start | 阻塞等待审批�?| /aicheck start | 分钟超时自动取消
-| /aicheck start | 严格模式下仅白名单安全命令免审批

---

##| /aicheck start | 完整配置�?

文件：`config/mcai/config.json`，修改后| /aicheck start | `/aireload`| /aicheck start | 重载�?

|| /aicheck start | 字段| /aicheck start | || /aicheck start | 默认�?|| /aicheck start | 说明| /aicheck start | |
|------|--------|------|
|| /aicheck start | `apiEndpoint`| /aicheck start | || /aicheck start | `https://api.deepseek.com`| /aicheck start | || /aicheck start | API| /aicheck start | 地址| /aicheck start | |
|| /aicheck start | `apiKey`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | API| /aicheck start | 密钥| /aicheck start | |
|| /aicheck start | `model`| /aicheck start | || /aicheck start | `deepseek-v4-flash`| /aicheck start | || /aicheck start | 模型�?|
|| /aicheck start | `triggerPrefix`| /aicheck start | || /aicheck start | `!ai`| /aicheck start | || /aicheck start | 聊天触发前缀| /aicheck start | |
|| /aicheck start | `maxTokens`| /aicheck start | || /aicheck start | 2048| /aicheck start | || /aicheck start | AI| /aicheck start | 回复最�?token| /aicheck start | |
|| /aicheck start | `temperature`| /aicheck start | || /aicheck start | 0.75| /aicheck start | || /aicheck start | 回复随机�?|
|| /aicheck start | `thinkingLevel`| /aicheck start | || /aicheck start | 1| /aicheck start | || /aicheck start | 思考模�?0-3| /aicheck start | |
|| /aicheck start | `strictMode`| /aicheck start | || /aicheck start | true| /aicheck start | || /aicheck start | 严格模式| /aicheck start | |
|| /aicheck start | `reviewIntervalMinutes`| /aicheck start | || /aicheck start | 30| /aicheck start | || /aicheck start | 审查间隔（分�?|
|| /aicheck start | `yellowCardThreshold`| /aicheck start | || /aicheck start | -30| /aicheck start | || /aicheck start | 黄牌阈�?|
|| /aicheck start | `redCardThreshold`| /aicheck start | || /aicheck start | -60| /aicheck start | || /aicheck start | 红牌阈�?|
|| /aicheck start | `scoreRecoveryPerInterval`| /aicheck start | || /aicheck start | 5| /aicheck start | || /aicheck start | 每周期恢复分�?|
|| /aicheck start | `approvalTimeoutMinutes`| /aicheck start | || /aicheck start | 10| /aicheck start | || /aicheck start | 审批超时（分�?|
|| /aicheck start | `systemPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | AI提示词文件路�?|
|| /aicheck start | `reviewPromptPath`| /aicheck start | || /aicheck start | `""`| /aicheck start | || /aicheck start | 审查提示词文件路�?|
|| /aicheck start | `promptLanguage`| /aicheck start | || /aicheck start | `zh_cn`| /aicheck start | || /aicheck start | 内置提示词语言| /aicheck start | |

---

##| /aicheck start | 文件结构

`config/mcai/`| /aicheck start | 下的文件�?

|| /aicheck start | 文件| /aicheck start | || /aicheck start | 内容| /aicheck start | |
|------|------|
|| /aicheck start | `config.json`| /aicheck start | || /aicheck start | 主配�?|
|| /aicheck start | `scores.json`| /aicheck start | || /aicheck start | 玩家行为�?|
|| /aicheck start | `penalties.json`| /aicheck start | || /aicheck start | 处罚历史| /aicheck start | |
|| /aicheck start | `system_prompt.txt`| /aicheck start | || /aicheck start | AI| /aicheck start | 提示词（可自定义�?|
|| /aicheck start | `review_prompt.txt`| /aicheck start | || /aicheck start | 审查提示词（可自定义�?|
|| /aicheck start | `review_last_response.txt`| /aicheck start | || /aicheck start | 上次审查| /aicheck start | AI| /aicheck start | 原始输出| /aicheck start | |
|| /aicheck start | `review_last_reasoning.txt`| /aicheck start | || /aicheck start | 上次审查| /aicheck start | AI| /aicheck start | 推理过程| /aicheck start | |

---

##| /aicheck start | 常见问题

**Q:| /aicheck start | 审查太严/太松怎么办？**
调整| /aicheck start | `yellowCardThreshold`| /aicheck start | �?`redCardThreshold`，数值越大越严格�?

**Q:| /aicheck start | 不想让玩家用某些指令�?*
�?`safeCommands`| /aicheck start | 白名单删除或加入| /aicheck start | `requireApprovalCommands`�?

**Q:| /aicheck start | 能用其他| /aicheck start | API| /aicheck start | 吗？**
能，�?`apiEndpoint`| /aicheck start | 为任�?OpenAI| /aicheck start | 兼容接口�?

**Q:| /aicheck start | 消耗大吗？**
DeepSeek| /aicheck start | flash| /aicheck start | 很便宜，每月几块钱。审�?30| /aicheck start | 分钟才调用一次�?
