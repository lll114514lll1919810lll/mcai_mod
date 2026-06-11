import json, re, sys, os, time, math
from urllib.request import Request, urlopen
from urllib.parse import urlencode
from collections import Counter

API = "https://zh.minecraft.wiki/api.php"
UA = "MCAIKB/2.0"
# 自动检测输出路径：从 tools/ 或项目根目录运行均可
_OUT_BASE = os.path.dirname(os.path.abspath(__file__))  # tools/
if os.path.basename(_OUT_BASE) == "tools":
    OUT = os.path.join(_OUT_BASE, "..", "src", "main", "resources", "assets", "mcai", "kb")
else:
    OUT = os.path.join(_OUT_BASE, "src", "main", "resources", "assets", "mcai", "kb")

# ── 基础页面 (手工精选核心机制页面) ──
CORE = [
    # 游戏机制
    "合成", "烧炼", "酿造", "附魔", "铁砧", "砂轮", "锻造",
    "状态效果", "经验", "交易", "繁殖", "驯服", "难度", "游戏模式",
    "生命", "饥饿", "氧气", "伤害", "盔甲机制", "战斗",
    "红石电路", "红石元件", "运输", "命令", "命令/execute",
    "村民", "袭击", "巡逻队", "村庄英雄", "村民职业",
    "进度", "成就", "统计信息",
    # 世界
    "主世界", "下界", "末地", "世界生成", "生物群系",
    "洞穴", "矿脉", "结构", "要塞", "村庄",
    # 方块大全
    "石头", "圆石", "深板岩", "花岗岩", "闪长岩", "安山岩",
    "黑曜石", "基岩", "命令方块", "结构方块", "拼图方块", "光源方块",
    "草方块", "泥土", "砂土", "灰化土", "菌丝",
    "沙子", "红沙", "沙砾", "粘土", "粘土块",
    "玻璃", "玻璃板", "遮光玻璃", "染色玻璃",
    "书架", "海绵", "湿海绵", "TNT",
    "木头", "原木", "去皮原木", "木板", "木台阶", "木楼梯",
    "金合欢原木", "白桦原木", "深色橡木原木", "丛林原木", "橡木原木", "云杉原木",
    "樱花原木", "红树原木", "竹块", "绯红菌柄", "诡异菌柄",
    "树叶", "树苗", "竹子", "甘蔗", "仙人掌", "藤蔓", "海带",
    # 红石
    "红石粉", "红石火把", "红石中继器", "红石比较器",
    "活塞", "粘性活塞", "侦测器", "观察者",
    "漏斗", "投掷器", "发射器", "音符盒", "唱片机",
    "红石灯", "陷阱箱", "压力板", "按钮", "拉杆",
    "铁轨", "激活铁轨", "探测铁轨", "动力铁轨",
    "阳光探测器", "标靶", "避雷针",
    # 功能方块
    "熔炉", "高炉", "烟熏炉", "酿造台", "营火", "灵魂营火",
    "附魔台", "锻造台", "切石机",
    "织布机", "制图台", "制箭台", "讲台",
    "堆肥桶", "炼药锅", "砂轮",
    "箱子", "木桶", "潜影盒", "末影箱",
    "工作台", "信标", "潮涌核心", "重生锚",
    "床", "钟", "磁石", "蛋糕", "蜂蜜块", "黏液块",
    # 矿物/材料
    "钻石", "下界合金锭", "下界合金碎片", "远古残骸",
    "铁锭", "金锭", "铜锭", "绿宝石",
    "煤炭", "木炭", "红石粉", "青金石", "下界石英",
    "紫水晶碎片", "回响碎片", "海晶碎片", "海晶砂粒",
    "烈焰棒", "烈焰粉", "恶魂之泪", "岩浆膏",
    "发酵蛛眼", "闪烁的西瓜片", "金胡萝卜", "兔子脚",
    "幻翼膜", "龙息", "海龟壳", "鹦鹉螺壳",
    # 工具
    "镐", "斧", "锹", "锄", "剑",
    "木镐", "石镐", "铁镐", "金镐", "钻石镐", "下界合金镐",
    "木斧", "石斧", "铁斧", "金斧", "钻石斧", "下界合金斧",
    "木锹", "石锹", "铁锹", "金锹", "钻石锹", "下界合金锹",
    "木锄", "石锄", "铁锄", "金锄", "钻石锄", "下界合金锄",
    "木剑", "石剑", "铁剑", "金剑", "钻石剑", "下界合金剑",
    # 武器/装备
    "弓", "弩", "三叉戟", "盾牌", "钓鱼竿",
    "打火石", "剪刀", "火焰弹", "雪球", "鸡蛋",
    "鞘翅", "马鞍", "拴绳", "命名牌",
    "烟花火箭", "烟火之星",
    "指南针", "追溯指针", "时钟", "空地图", "地图",
    "书与笔", "成书", "附魔书", "知识之书",
    # 盔甲
    "头盔", "胸甲", "护腿", "靴子",
    "皮革头盔", "皮革胸甲", "皮革护腿", "皮革靴子",
    "锁链头盔", "锁链胸甲", "锁链护腿", "锁链靴子",
    "铁头盔", "铁胸甲", "铁护腿", "铁靴子",
    "金头盔", "金胸甲", "金护腿", "金靴子",
    "钻石头盔", "钻石胸甲", "钻石护腿", "钻石靴子",
    "下界合金头盔", "下界合金胸甲", "下界合金护腿", "下界合金靴子",
    "海龟壳", "马铠", "狼铠",
    # 食物
    "苹果", "金苹果", "附魔金苹果",
    "面包", "牛排", "熟猪排", "熟鸡肉", "熟羊肉",
    "熟兔肉", "熟鲑鱼", "熟鳕鱼",
    "生牛肉", "生猪排", "生鸡肉", "生羊肉", "生兔肉",
    "生鲑鱼", "生鳕鱼", "热带鱼", "河豚",
    "胡萝卜", "金胡萝卜", "马铃薯", "烤马铃薯", "毒马铃薯",
    "甜菜根", "甜菜汤", "蘑菇煲", "兔肉煲", "甜浆果", "发光浆果",
    "曲奇", "蛋糕", "南瓜派",
    "干海带", "紫颂果", "爆裂紫颂果",
    "蜂蜜瓶", "迷之炖菜",
    "西瓜片", "蜘蛛眼",
    # 药水 (完整)
    "药水", "喷溅药水", "滞留药水", "药箭",
    "粗制的药水", "平凡的药水", "浓稠的药水", "平凡的药水",
    "治疗药水", "瞬间治疗", "再生药水", "生命恢复",
    "力量药水", "迅捷药水", "速度", "缓慢药水",
    "跳跃药水", "跳跃提升", "抗火药水", "防火",
    "水肺药水", "水下呼吸", "夜视药水",
    "隐身药水", "剧毒药水", "中毒",
    "虚弱药水", "伤害药水", "瞬间伤害",
    "缓降药水", "缓慢 falling", "幸运药水",
    "衰变药水", "凋零", "神龟药水",
    # 生物 (按类别)
    "苦力怕", "僵尸", "尸壳", "溺尸", "骷髅", "流浪者", "蜘蛛", "洞穴蜘蛛",
    "史莱姆", "岩浆怪", "蠹虫", "末影螨",
    "末影人", "末影龙", "潜影贝",
    "幻翼", "幻术师",
    "凋灵", "凋灵骷髅", "凋灵玫瑰",
    "卫道士", "掠夺者", "唤魔者", "恼鬼", "劫掠兽", "女巫",
    "猪灵", "猪灵蛮兵", "疣猪兽", "僵尸猪灵", "僵尸疣猪兽",
    "炽足兽", "烈焰人", "恶魂", "岩浆怪",
    "蜜蜂", "海豚", "鱿鱼", "发光鱿鱼", "美西螈",
    "蝌蚪", "青蛙", "悦灵",
    "马", "驴", "骡", "骷髅马", "僵尸马",
    "狼", "猫", "豹猫", "狐狸", "熊猫",
    "北极熊", "兔子", "羊驼", "行商羊驼", "山羊",
    "鸡", "猪", "牛", "羊", "哞菇", "嗅探兽", "骆驼",
    "蝙蝠", "鹦鹉", "海龟", "鳕鱼", "鲑鱼", "热带鱼", "河豚",
    "铁傀儡", "雪傀儡", "流浪商人",
    # 结构
    "要塞", "末地城", "末地船", "堡垒遗迹",
    "废弃矿井", "沙漠神殿", "丛林神庙",
    "沼泽小屋", "雪屋", "掠夺者前哨站",
    "海底遗迹", "海底神殿", "沉船", "海底废墟",
    "下界要塞", "远古城市", "古城",
    "林地府邸", "埋藏的宝藏", "试炼密室",
    "化石", "下界化石", "紫晶洞",
    "废弃传送门", "下界金矿", "玄武岩柱",
    # 生物群系
    "平原", "森林", "沙漠", "海洋", "山地",
    "沼泽", "针叶林", "雪原", "冰原",
    "丛林", "恶地", "蘑菇岛", "热带草原",
    "樱花树林", "红树林沼泽", "深暗之域", "溶洞",
    "下界荒地", "灵魂沙峡谷", "绯红森林", "诡异森林",
    "玄武岩三角洲", "末地荒地", "末地高地", "末地内陆",
    "繁花森林", "黑森林", "桦木森林", "原始桦木森林",
    "原始松木针叶林", "原始云杉针叶林", "积雪针叶林",
    "风袭丘陵", "风袭森林", "风袭沙砾丘陵", "风袭热带草原",
    "石岸", "冻洋", "冷水海洋", "温水海洋", "暖水海洋",
    # 附魔
    "锋利", "亡灵杀手", "节肢杀手", "击退",
    "火焰附加", "抢夺", "横扫之刃",
    "保护", "火焰保护", "爆炸保护", "弹射物保护", "摔落保护",
    "水下呼吸", "水下速掘", "深海探索者",
    "荆棘", "冰霜行者", "灵魂疾行", "迅捷潜行",
    "时运", "精准采集", "经验修补", "耐久",
    "效率", "力量", "冲击", "火矢", "穿透",
    "多重射击", "快速装填", "无限",
    "饵钓", "海之眷顾",
    "消失诅咒", "绑定诅咒",
    # 状态效果
    "速度", "缓慢", "急迫", "挖掘疲劳",
    "力量", "瞬间伤害", "瞬间治疗",
    "跳跃提升", "反胃", "生命恢复",
    "抗性提升", "防火", "水下呼吸",
    "隐身", "夜视", "饥饿",
    "虚弱", "中毒", "凋零",
    "发光", "漂浮", "幸运", "霉运",
    "缓降", "潮涌能量", "不祥之兆", "村庄英雄",
    "黑暗", "黑暗效果",
    # 命令
    "命令/give", "命令/effect", "命令/gamemode", "命令/defaultgamemode",
    "命令/tp", "命令/teleport", "命令/summon", "命令/kill",
    "命令/fill", "命令/clone", "命令/setblock",
    "命令/data", "命令/execute", "命令/item",
    "命令/scoreboard", "命令/team", "命令/tag",
    "命令/title", "命令/tellraw", "命令/playsound",
    "命令/particle", "命令/weather", "命令/time",
    "命令/gamerule", "命令/difficulty", "命令/spawnpoint",
    "命令/setworldspawn", "命令/worldborder",
    "命令/enchant", "命令/xp", "命令/experience",
    "命令/clear", "命令/replaceitem",
    "命令/locate", "命令/loot", "命令/recipe",
    "命令/ban", "命令/ban-ip", "命令/pardon", "命令/pardon-ip",
    "命令/banlist", "命令/list", "命令/op", "命令/deop",
    "命令/kick", "命令/stop", "命令/save-all",
    "命令/whitelist", "命令/seed", "命令/reload",
    "命令/forceload", "命令/spectate", "命令/place",
    "命令/return", "命令/random", "命令/ride",
    "命令/damage", "命令/transfer",
    # 游戏规则 (核心)
    "游戏规则", "游戏规则/keepInventory", "游戏规则/doFireTick",
    "游戏规则/mobGriefing", "游戏规则/doDaylightCycle",
    "游戏规则/doWeatherCycle", "游戏规则/commandBlockOutput",
]

# ── 版本历史 ──
JAVA_VERSIONS = [
    # 经典 / pre-classic (可选)
    # "Java版pre-Classic",
    # 正式版 1.0 → 1.21
    "Java版1.0.0", "Java版1.1", "Java版1.2.1",
    "Java版1.3.1", "Java版1.4.2", "Java版1.4.6", "Java版1.4.7",
    "Java版1.5", "Java版1.5.1", "Java版1.5.2",
    "Java版1.6.1", "Java版1.6.2", "Java版1.6.4",
    "Java版1.7.2", "Java版1.7.4", "Java版1.7.5",
    "Java版1.7.6", "Java版1.7.7", "Java版1.7.8", "Java版1.7.9", "Java版1.7.10",
    "Java版1.8", "Java版1.8.1", "Java版1.8.2", "Java版1.8.3",
    "Java版1.8.4", "Java版1.8.5", "Java版1.8.6", "Java版1.8.7", "Java版1.8.8", "Java版1.8.9",
    "Java版1.9", "Java版1.9.1", "Java版1.9.2", "Java版1.9.3", "Java版1.9.4",
    "Java版1.10", "Java版1.10.1", "Java版1.10.2",
    "Java版1.11", "Java版1.11.1", "Java版1.11.2",
    "Java版1.12", "Java版1.12.1", "Java版1.12.2",
    "Java版1.13", "Java版1.13.1", "Java版1.13.2",
    "Java版1.14", "Java版1.14.1", "Java版1.14.2", "Java版1.14.3", "Java版1.14.4",
    "Java版1.15", "Java版1.15.1", "Java版1.15.2",
    "Java版1.16", "Java版1.16.1", "Java版1.16.2", "Java版1.16.3", "Java版1.16.4", "Java版1.16.5",
    "Java版1.17", "Java版1.17.1",
    "Java版1.18", "Java版1.18.1", "Java版1.18.2",
    "Java版1.19", "Java版1.19.1", "Java版1.19.2", "Java版1.19.3", "Java版1.19.4",
    "Java版1.20", "Java版1.20.1", "Java版1.20.2", "Java版1.20.3", "Java版1.20.4", "Java版1.20.5", "Java版1.20.6",
    "Java版1.21", "Java版1.21.1", "Java版1.21.2", "Java版1.21.3", "Java版1.21.4", "Java版1.21.5",
    # 重大更新的主题页面
    "Java版1.0.0", "冒险更新", "红石更新", "马匹更新",
    "改变世界的更新", "缤纷更新", "霜炙更新",
    "探险更新", "世界之色更新", "水域更新",
    "村庄与掠夺", "嗡嗡蜂群", "下界更新",
    "洞穴与山崖", "荒野更新", "足迹与故事",
    "棘巧试炼", "花园觉醒",
    # 版本命名对照
    "1.0.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9",
    "1.10", "1.11", "1.12", "1.13", "1.14", "1.15", "1.16", "1.17", "1.18",
    "1.19", "1.20", "1.21",
]

def api(p, retries=3):
    p["format"] = "json"
    qs = urlencode(p, doseq=True)
    for attempt in range(retries):
        try:
            req = Request(f"{API}?{qs}", headers={"User-Agent": UA})
            with urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            if attempt < retries - 1:
                wait = (attempt + 1) * 3
                print(f"  API重试 {attempt+1}/{retries} ({wait}s)...", end="", flush=True)
                time.sleep(wait)
            else:
                raise e

def get_text(title):
    d = api({"action": "query", "titles": title, "prop": "extracts",
             "explaintext": True, "exsectionformat": "plain", "exlimit": 1})
    for pid, info in d.get("query", {}).get("pages", {}).items():
        if pid != "-1":
            t = info.get("extract", "")
            if t:
                t = re.sub(r"\n{4,}", "\n\n", t)
                t = re.sub(r"\[edit[^\]]*\]", "", t)
                return t.strip()
    return None

def resolve(title):
    d = api({"action": "query", "titles": title, "redirects": 1})
    for pid, info in d.get("query", {}).get("pages", {}).items():
        if pid != "-1":
            return info.get("title", title)
    return title

def extract_keywords(title, text):
    combined = (title + " " + text[:2000]).lower()
    tokens = re.findall(r"[\u4e00-\u9fff\w]+", combined)
    kw = [w for w in tokens if len(w) > 1]
    top = [w for w, _ in Counter(kw).most_common(30)]
    return top

def fetch_category(cat_title, max_pages=500):
    """获取分类下的所有页面标题"""
    titles = []
    for ns in [0]:  # 主命名空间
        cmcontinue = None
        while True:
            params = {
                "action": "query", "list": "categorymembers",
                "cmtitle": cat_title, "cmlimit": 500,
                "cmnamespace": ns, "cmtype": "page"
            }
            if cmcontinue:
                params["cmcontinue"] = cmcontinue
            d = api(params)
            for m in d.get("query", {}).get("categorymembers", []):
                titles.append(m["title"])
            if "continue" in d and len(titles) < max_pages:
                cmcontinue = d["continue"]["cmcontinue"]
            else:
                break
            time.sleep(0.3)
    return titles

def main():
    # 收集所有页面
    pages = list(dict.fromkeys(sys.argv[1:] if len(sys.argv) > 1 else CORE))

    # ── 通过分类自动获取 ──
    if "--full" in sys.argv:
        print("=== 通过分类获取额外页面 ===")
        categories = [
            "Category:物品", "Category:方块", "Category:生物",
            "Category:药水", "Category:命令", "Category:附魔",
            "Category:状态效果", "Category:结构",
        ]
        cat_pages = set()
        for cat in categories:
            print(f"  获取 {cat} ...")
            titles = fetch_category(cat, max_pages=300)
            cat_pages.update(titles)
            print(f"    找到 {len(titles)} 个页面")
            time.sleep(0.5)
        # 去重并添加到列表
        existing = set(pages)
        added = [p for p in cat_pages if p not in existing]
        print(f"  分类共新增 {len(added)} 个页面")
        pages.extend(added)

    # ── 添加版本历史 ──
    if "--noversion" not in sys.argv:
        ver_added = [v for v in JAVA_VERSIONS if v not in pages]
        print(f"添加 {len(ver_added)} 个版本页面")
        pages.extend(ver_added)

    # 去重保序
    pages = list(dict.fromkeys(pages))
    os.makedirs(OUT, exist_ok=True)
    out = os.path.join(OUT, "zh_wiki.json")

    # ── 断点续传：加载已有条目 ──
    entries, done_titles = [], set()
    if os.path.exists(out) and "--fresh" not in sys.argv:
        try:
            with open(out, "r", encoding="utf-8") as f:
                entries = json.load(f)
            done_titles = {e["title"] for e in entries}
            print(f"已加载 {len(entries)} 个已有条目，将跳过")
        except Exception:
            entries = []

    errors = []
    for i, raw in enumerate(pages, 1):
        # 先解析重定向（快速检查是否已存在）
        try:
            title = resolve(raw)
        except Exception as e:
            print(f"[{i}/{len(pages)}] {raw} → 重定向解析失败: {e}")
            errors.append(raw)
            time.sleep(1)
            continue

        if title in done_titles:
            print(f"[{i}/{len(pages)}] {title} (已缓存，跳过)")
            continue

        if any(e["title"] == title for e in entries):
            done_titles.add(title)
            print(f"[{i}/{len(pages)}] {title} (已缓存，跳过)")
            continue

        print(f"[{i}/{len(pages)}] {title}", end="", flush=True)

        if len(title) > 200:
            print(f" → 跳过（标题过长）")
            errors.append(raw)
            continue

        try:
            text = get_text(title)
        except Exception as e:
            print(f" → 下载失败: {e}")
            errors.append(title)
            time.sleep(2)
            continue

        if not text or len(text) < 50:
            print(f" → 跳过（内容不足）")
            errors.append(title)
            continue

        summary = text[:300].rsplit("。", 1)[0] + "。" if "。" in text[:300] else text[:300]
        keywords = extract_keywords(title, text)
        entries.append({
            "title": title,
            "keywords": keywords,
            "summary": summary,
            "content": text
        })
        done_titles.add(title)
        print(f" → {len(text)} 字符")

        # 每 10 条保存一次
        if len(entries) % 10 == 0:
            with open(out, "w", encoding="utf-8") as f:
                json.dump(entries, f, ensure_ascii=False, indent=1)

        time.sleep(0.5)

    # 最终保存
    with open(out, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)

    total_size = os.path.getsize(out)
    print(f"\n完成！{len(entries)} 个条目 ({total_size/1024:.0f} KB) → {out}")
    print(f"失败/跳过: {len(errors)} ({', '.join(errors[:10])}{'...' if len(errors)>10 else ''})")
    if "--full" not in sys.argv:
        print("提示: 使用 --full 从分类自动获取更多页面")
    print("使用 --noversion 跳过版本历史 | 使用 --fresh 忽略已有缓存")

if __name__ == "__main__":
    main()
