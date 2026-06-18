# MCAI 知识库文件 / Knowledge Base Files

此目录包含第三方 Wiki 的知识库数据，**不内置于 JAR 中**，用户可自行下载并放入 `config/mcai/kb/` 使用。

These knowledge base files are **NOT bundled in the JAR**. Download and place them in `config/mcai/kb/` to extend AI knowledge.

---

## 使用方法 / How to Use

1. 下载所需的 `.json` 文件 / Download the required `.json` files
2. 放入 `config/mcai/kb/` 目录（首次启动会自动创建）/ Place them in `config/mcai/kb/` (auto-created on first start)
3. 自动热重载生效（或手动 `/aireload`） / Auto-reloads (or run `/aireload` manually)
4. AI 即可查询这些知识库 / The AI can now query these knowledge bases

---

## 可用的知识库文件 / Available Knowledge Base Files

| 文件 / File | 来源 / Source | 许可证 / License | 条目数 / Items |
|------|------|--------|--------|
| `zh_wiki.json` | [zh.minecraft.wiki](https://zh.minecraft.wiki) | CC BY-NC-SA 3.0 | ~5000 |
| `create_mod.json` | [create.fandom.com](https://create.fandom.com) | CC BY-SA 3.0 | ~250 |
| `biomesoplenty.json` | [biomesoplenty.wiki.gg](https://biomesoplenty.wiki.gg) | CC BY-SA 4.0 | ~200 |

## 自行爬取 / DIY

使用 `tools/wiki_to_kb.py` 可爬取任何 MediaWiki 网站：

Use `tools/wiki_to_kb.py` to scrape any MediaWiki website:

```bash
python tools/wiki_to_kb.py https://wiki-url output.json
```
