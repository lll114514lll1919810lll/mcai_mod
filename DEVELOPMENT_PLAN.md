# MCAI 未来开发计划清单

> 基于 2026-07-26 工程现状评估制定

## 一、现状概览

| 维度 | 评价 | 关键指标 |
|------|------|----------|
| 模块边界 | 良好 | chat/command/review/kb/config 五层分层 |
| 安全意识 | 较强 | 审批流、注入清洗、工具调用约束 |
| 可运营性 | 较好 | 热重载、调试日志、管理员命令 |
| 测试覆盖 | 缺失 | 无 `src/test/`，零回归保障 |
| 代码复杂度 | 偏高 | `CommandExecutionService` 750 行、`OpenAIClient` 519 行 |
| 并发健壮性 | 待验证 | 线程池 4-8 线程 + 32 队列，超时 5min/60s 双层 |

---

## 二、风险点与应对计划

### P0 — 测试体系从零搭建

**现状**：无测试目录，无测试框架，无 CI，任何修改都靠人工回归。

| 任务 | 说明 | 预估 |
|------|------|------|
| 引入 JUnit 5 + Mockito | `build.gradle` 添加 `testImplementation`，配置 `test` task | 0.5d |
| 为 `OpenAIClient` 写单元测试 | Mock `HttpClient`，验证请求构建、响应解析、错误处理、工具调用循环 | 1d |
| 为 `CommandExecutionService` 写单元测试 | Mock `MinecraftServer`，验证审批流 put/get/complete/removeIf 四触点 | 1d |
| 为 `KnowledgeBase` 写单元测试 | 验证 bigram 分词、CJK 搜索、JSON 加载、边界情况 | 0.5d |
| 为 `ChatHandler` 写集成测试 | Mock `OpenAIClient`，验证消息流、并发限流、历史截断 | 1d |
| 为 `SearchRouter` 写单元测试 | 验证 online-first 策略、8s 超时、fallback 逻辑 | 0.5d |
| 设置 GitHub Actions CI | `push`/`PR` 触发 `./gradlew test`，JDK 25 | 0.5d |

**目标**：核心模块测试覆盖率 > 60%，CI 阻塞合入。

---

### P1 — 重构高复杂度模块

**现状**：`OpenAIClient`（519 行）和 `CommandExecutionService`（750 行）承担过多职责。

#### OpenAIClient 拆分

```
OpenAIClient (519行) 拆分为:
├── ApiClient              — HTTP 传输层（连接池、超时、重试）
├── ChatMessageCodec       — 消息序列化/反序列化
├── ToolCallProcessor      — 工具调用循环逻辑
├── ResponseParser         — JSON 响应解析 + 错误提取
└── OpenAIClient           — 门面类，组装上述组件
```

| 任务 | 说明 | 预估 |
|------|------|------|
| 提取 `ResponseParser` | `parseChoices()`、`extractError()` 等独立类 | 0.5d |
| 提取 `ToolCallProcessor` | 工具调用循环、结果收集、超时控制独立类 | 1d |
| 提取 `ChatMessageCodec` | `toJson()`/`fromJson()` 独立类 | 0.5d |
| `OpenAIClient` 瘦身为门面 | 仅组装组件，不包含业务逻辑 | 0.5d |

#### CommandExecutionService 拆分

```
CommandExecutionService (750行) 拆分为:
├── CommandApprovalManager  — 审批队列管理（put/get/complete/removeIf）
├── CommandChainExecutor    — 命令链执行（间隔、顺序）
├── CommandSafetyChecker    — 白名单/黑名单检查、注入清洗
├── ApprovalNotifier        — 通知广播（管理员 + 请求者 + 可取消提示）
└── CommandExecutionService — 门面类
```

| 任务 | 说明 | 预估 |
|------|------|------|
| 提取 `CommandSafetyChecker` | 白名单/黑名单/归一化逻辑独立类 | 0.5d |
| 提取 `ApprovalNotifier` | 通知广播 + 可点击按钮 + cancel hint | 0.5d |
| 提取 `CommandChainExecutor` | 命令链间隔执行独立类 | 0.5d |
| `CommandExecutionService` 瘦身 | 仅协调组件 | 0.5d |

**原则**：拆分后每个类 < 200 行，单一职责，可独立测试。

---

### P2 — 并发与超时策略强化

**现状**：
- 线程池：4 核心 / 8 最大 / 32 队列，拒绝策略为静默丢弃
- HTTP 超时：单请求 60s，工具调用循环总计 5min
- 并发限流：`concurrentNonAdminCalls` AtomicInteger，无队列排队

| 任务 | 说明 | 优先级 |
|------|------|--------|
| **超时策略可配置化** | 单请求超时、循环总超时、连接超时写入 `ModConfig`，而非硬编码 | 高 |
| **拒绝策略改进** | 队列满时返回友好错误给玩家，而非静默丢弃 | 高 |
| **并发限流增强** | 非管理员并发上限可配置；超限时排队等待而非直接拒绝 | 中 |
| **API 重试机制** | 5xx / 网络错误自动重试 1-2 次，指数退避 | 中 |
| **熔断器** | 连续 N 次失败后短路 30s，避免雪崩 | 低 |
| **压测脚本** | 模拟 10/20/50 并发玩家的 API 调用，记录 P95/P99 延迟 | 中 |
| **线程池监控** | 暴露活跃线程数、队列深度、拒绝次数到 `/aistats` 命令 | 低 |

**目标配置示例**：
```json
{
  "apiConnectTimeoutSec": 10,
  "apiRequestTimeoutSec": 60,
  "apiLoopTimeoutSec": 300,
  "apiMaxRetries": 2,
  "apiRetryBackoffMs": 1000,
  "executorCoreSize": 4,
  "executorMaxSize": 8,
  "executorQueueSize": 32,
  "maxConcurrentNonAdmin": 3
}
```

---

## 三、功能演进计划

### P1 — 上下文管理增强

| 功能 | 说明 | 预估 |
|------|------|------|
| 对话历史持久化 | 玩家下线后历史保留，上线恢复（按 UUID 存 JSON） | 1d |
| 历史摘要压缩 | 超过 `contextMaxChars` 时用 AI 生成摘要，而非简单截断 | 1d |
| 多玩家共享上下文 | 可选的"全局频道"模式，多个玩家共享 AI 对话上下文 | 2d |
| 上下文窗口可视化 | `/aicontext` 命令显示当前 token 使用量、历史条数 | 0.5d |

### P2 — 审查系统增强

| 功能 | 说明 | 预估 |
|------|------|------|
| 行为画像 | 按玩家累积违规类型分布，生成雷达图数据 | 1d |
| 申诉机制 | 被处罚玩家可 `/aiappeal` 提交申诉，管理员审批 | 1.5d |
| 审查白名单 | 指定玩家免审查，或降低审查频率 | 0.5d |
| 审查报告导出 | `/aireview export` 导出 CSV/JSON 审查记录 | 0.5d |
| 自适应审查间隔 | 高活跃服务器动态调整审查周期（基于聊天量） | 1d |

### P3 — 知识库增强

| 功能 | 说明 | 预估 |
|------|------|------|
| 多知识库源 | 支持同时加载多个 `kb/*.json`，按优先级搜索 | 0.5d |
| 知识库热更新 | 监听 `kb/` 目录变化，自动重新加载索引 | 0.5d |
| 向量搜索 | 可选的 embedding 向量搜索，提升语义匹配准确率 | 3d |
| 知识库管理命令 | `/aikb list`/`/aikb reload`/`/aikb stats` | 0.5d |
| Wiki 自动同步 | 定时从 Minecraft Wiki 拉取更新，重新生成 KB | 2d |

### P4 — 多模型支持

| 功能 | 说明 | 预估 |
|------|------|------|
| 模型路由 | 根据任务类型（聊天/审查/摘要）自动选择模型 | 1d |
| 模型健康检查 | 定时 ping 各模型端点，故障自动切换 | 1d |
| 本地模型支持 | 兼容 Ollama / llama.cpp 本地部署的模型 | 0.5d |
| 模型成本统计 | 记录每次调用的 token 消耗，按模型汇总 | 0.5d |

---

## 四、工程质量提升

### 代码质量

| 任务 | 说明 | 预估 |
|------|------|------|
| 引入 SpotBugs | 静态分析，CI 集成 | 0.5d |
| 引入 Checkstyle | 代码规范检查 | 0.5d |
| 消除 `@SuppressWarnings` | 审查所有抑制警告，修复根因 | 1d |
| Javadoc 补充 | 所有 public API 补充文档 | 1d |
| 死代码清理 | 移除未使用的字段、方法、import | 0.5d |

### 可观测性

| 任务 | 说明 | 预估 |
|------|------|------|
| 结构化日志 | JSON 格式日志，便于 ELK 采集 | 0.5d |
| Metrics 暴露 | API 调用次数/延迟/错误率，通过命令或文件暴露 | 1d |
| 审计日志 | 所有命令执行、审批、处罚记录到独立审计文件 | 0.5d |
| 调试日志增强 | `AIDebugLogger` 支持分级（INFO/WARN/ERROR/TRACE） | 0.5d |

### 安全加固

| 任务 | 说明 | 预估 |
|------|------|------|
| API Key 加密存储 | 配置文件中的 API Key 加密，运行时解密 | 1d |
| 命令注入二次校验 | 执行前对最终命令字符串做模式匹配，拦截已知攻击模式 | 0.5d |
| 工具调用沙箱 | 限制工具返回内容大小，防止上下文炸弹 | 0.5d |
| 速率限制细化 | 按玩家/IP/全局三级限流，防止滥用 | 1d |

---

## 五、版本路线图

### v1.7.0（2026 Q4）— 稳定性与测试
- [ ] 测试体系搭建（P0 全部）
- [ ] OpenAIClient 拆分（P1 第一部分）
- [ ] 超时策略可配置化
- [ ] 拒绝策略改进
- [ ] CI 流水线

### v1.8.0（2027 Q1）— 架构优化
- [ ] CommandExecutionService 拆分（P1 第二部分）
- [ ] 并发限流增强
- [ ] API 重试机制
- [ ] 对话历史持久化
- [ ] 行为画像

### v1.9.0（2027 Q2）— 知识库与多模型
- [ ] 多知识库源
- [ ] 知识库热更新
- [ ] 模型路由
- [ ] 模型健康检查
- [ ] 向量搜索（实验性）

### v2.0.0（2027 Q3）— 企业级
- [ ] 熔断器
- [ ] 结构化日志 + Metrics
- [ ] API Key 加密
- [ ] 速率限制细化
- [ ] 压测验证通过

---

## 六、技术债务登记

| 编号 | 债务 | 影响 | 利息 | 处理版本 |
|------|------|------|------|----------|
| TD-001 | `CommandExecutionService` 750 行 | 维护困难 | 高 | v1.8.0 |
| TD-002 | `OpenAIClient` 519 行 | 维护困难 | 高 | v1.7.0 |
| TD-003 | 无测试 | 回归风险 | 极高 | v1.7.0 |
| TD-004 | 超时硬编码 | 运维困难 | 中 | v1.7.0 |
| TD-005 | 拒绝策略静默丢弃 | 用户体验差 | 中 | v1.7.0 |
| TD-006 | `ModConfig` 453 行 | 配置膨胀 | 中 | v1.8.0 |
| TD-007 | 历史仅内存存储 | 重启丢失 | 中 | v1.8.0 |
| TD-008 | 无审计日志 | 合规风险 | 低 | v2.0.0 |

---

*本计划为滚动文档，随版本迭代持续更新。*
