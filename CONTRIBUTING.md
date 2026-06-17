# 贡献指南

感谢你对 MCAI 项目的关注！

## 如何贡献

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建一个 Pull Request

## 开发环境

### 前提条件
- JDK 25
- Git
- IDE（推荐 IntelliJ IDEA）

### 构建
```bash
# 克隆仓库
git clone https://github.com/YOUR_USERNAME/mc.git
cd mc

# 构建（需要 JDK 25）
.\gradlew.bat build
```

### 项目结构
```
src/main/java/com/example/mcai/
├── MCAIMod.java              - 模组入口
├── api/
│   ├── OpenAIClient.java     - DeepSeek API 调用（工具调用）
│   └── ApiResult.java        - API 响应记录
├── config/
│   └── ModConfig.java        - JSON 配置管理
├── handler/
│   ├── ChatHandler.java      - 聊天处理（协调器）
│   ├── ChatLog.java          - 服务器聊天日志
│   ├── ThinkingAnimation.java - "思考中..." 动画
│   ├── PlayerContextBuilder.java - 玩家上下文构建
│   ├── CommandExecutionService.java - 命令执行（需审批）
│   ├── ToolDispatcher.java   - AI 工具调用路由
│   └── CommandRegistry.java  - /ai* 命令注册
├── behavior/
│   ├── ChatReviewSystem.java - 自动行为审查（30分钟周期）
│   ├── ReviewEngine.java     - AI 审查处理
│   ├── ReviewCommandRegistry.java - /aicheck 命令
│   ├── PlayerBehaviorTracker.java - 玩家行为评分
│   ├── PenaltyEvent.java     - 处罚记录
│   ├── PenaltyHistory.java   - 处罚历史
│   ├── AdminApprovalQueue.java - 踢出审批队列
│   └── PlayerViolation.java  - 违规记录
├── kb/
│   └── KnowledgeBase.java    - Bigram CJK 知识库搜索
└── client/
    ├── ModMenuIntegration.java - Mod Menu 集成
    └── config/
        └── MCAIConfigScreen.java - 配置界面
```

## 代码规范

- 遵循 Java 命名规范
- 使用 4 空格缩进
- 添加必要的注释
- 保持代码简洁

## 提交规范

- 使用清晰的提交信息
- 每个提交只做一件事
- 测试你的更改

## 问题反馈

- 使用 GitHub Issues 报告问题
- 描述清楚问题和复现步骤
- 提供相关日志

## 许可证

贡献即表示你同意你的代码在 [MIT License](LICENSE) 下发布。
