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
- JDK 21 或 25
- Git
- IDE（推荐 IntelliJ IDEA）

### 构建
```bash
# 克隆仓库
git clone https://github.com/YOUR_USERNAME/mc.git
cd mc

# 选择版本
copy gradle-1.21.properties gradle.properties      # 1.21/1.21.1
copy gradle-1.21.11.properties gradle.properties    # 1.21.11

# 构建
.\gradlew.bat jar
```

### 项目结构
```
src/main/java/com/example/mcai/
├── MCAIMod.java              - 模组入口
├── api/
│   ├── OpenAIClient.java     - API 调用
│   └── WikiSearchClient.java - Wiki 搜索
├── client/
│   ├── ModMenuIntegration.java - Mod Menu 集成
│   └── config/
│       └── MCAIConfigScreen.java - 配置界面
├── config/
│   └── ModConfig.java        - 配置管理
├── handler/
│   └── ChatHandler.java      - 聊天处理
└── kb/
    └── KnowledgeBase.java    - 知识库
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
