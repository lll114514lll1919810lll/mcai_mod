import json, re, sys, os, time
from urllib.request import Request, urlopen
from urllib.parse import urlencode
from collections import Counter

API = "https://zh.minecraft.wiki/api.php"
UA = "MCAIKB/1.0"
OUT = "..\\src\\main\\resources\\assets\\mcai\\kb"

PAGES = [
    "合成", "烧炼", "酿造", "附魔", "铁砧", "砂轮",
    "状态效果", "经验", "交易", "繁殖", "驯服",
    "生命", "饥饿", "氧气", "伤害", "盔甲机制",
    "难度", "游戏模式",
    "镐", "斧", "锹", "锄", "剑",
    "弓", "弩", "三叉戟", "盾牌", "钓鱼竿",
    "钻石镐", "下界合金镐",
    "鞘翅", "打火石", "剪刀", "火焰弹",
    "盔甲", "头盔", "胸甲", "护腿", "靴子",
    "钻石盔甲", "下界合金盔甲", "锁链盔甲",
    "马铠", "乌龟壳",
    "木头", "原木", "石头", "圆石", "深板岩",
    "黑曜石", "基岩", "命令方块", "结构方块",
    "草方块", "泥土", "沙子", "沙砾", "粘土",
    "玻璃", "书架", "海绵", "TNT",
    "红石粉", "红石火把", "红石中继器", "红石比较器",
    "活塞", "粘性活塞", "侦测器", "观察者",
    "漏斗", "投掷器", "发射器", "音符盒", "唱片机",
    "红石灯", "陷阱箱", "铁轨", "激活铁轨", "探测铁轨",
    "熔炉", "高炉", "烟熏炉", "酿造台",
    "附魔台", "锻造台", "切石机",
    "织布机", "制图台", "制箭台", "讲台",
    "堆肥桶", "炼药锅",
    "箱子", "木桶", "潜影盒", "末影箱",
    "工作台", "信标", "潮涌核心", "重生锚",
    "床", "钟",
    "钻石", "下界合金锭", "下界合金碎片",
    "铁锭", "金锭", "铜锭", "绿宝石",
    "煤炭", "红石粉", "青金石", "石英",
    "不死图腾", "末影珍珠", "末影之眼",
    "紫颂果", "爆裂紫颂果",
    "金苹果", "附魔金苹果",
    "书与笔", "成书", "地图", "空地图",
    "指南针", "时钟", "拴绳", "鞍", "命名牌",
    "烟花火箭", "烟火之星",
    "药水", "喷溅药水", "滞留药水", "药箭",
    "附魔书", "刷怪蛋",
    "面包", "牛排", "熟猪排", "熟鸡肉",
    "胡萝卜", "马铃薯", "甜菜根", "南瓜派",
    "曲奇", "蛋糕", "蘑菇煲", "兔肉煲",
    "干海带", "紫颂果",
    "村民", "铁傀儡", "雪傀儡", "流浪商人",
    "苦力怕", "僵尸", "骷髅", "蜘蛛", "洞穴蜘蛛",
    "末影人", "末影螨", "幻翼",
    "凋灵", "末影龙", "劫掠兽", "恼鬼",
    "卫道士", "掠夺者", "唤魔者",
    "猪灵", "猪灵蛮兵", "疣猪兽", "炽足兽",
    "蜜蜂", "海豚", "鱿鱼", "发光鱿鱼",
    "马", "驴", "骡", "骷髅马", "僵尸马",
    "狼", "猫", "豹猫", "狐狸", "熊猫",
    "北极熊", "兔子", "羊驼", "行商羊驼",
    "鸡", "猪", "牛", "羊", "哞菇",
    "蝙蝠", "鹦鹉", "青蛙", "美西螈", "山羊",
    "下界", "末地", "主世界",
    "村庄", "要塞", "末地城", "堡垒遗迹",
    "废弃矿井", "沙漠神殿", "丛林神庙",
    "沼泽小屋", "雪屋", "掠夺者前哨站",
    "海底遗迹", "沉船", "海底废墟",
    "下界要塞", "堡垒遗迹", "远古城市",
    "林地府邸",
    "生物群系", "平原", "森林", "沙漠", "海洋",
    "山地", "沼泽", "针叶林", "雪原",
    "丛林", "恶地", "蘑菇岛",
    "下界荒地", "灵魂沙峡谷", "绯红森林", "诡异森林",
    "末地荒地", "末地高地", "末地内陆",
    "世界生成", "矿脉", "洞穴",
    "成就", "进度", "统计信息",
    "村民职业", "村庄英雄",
    "袭击", "巡逻队",
    "命令", "give", "effect", "gamemode",
    "teleport", "summon", "fill", "clone",
    "data", "execute", "item", "scoreboard",
    "team", "tag", "attribute", "bossbar",
    "title", "tellraw", "playsound",
    "锋利", "亡灵杀手", "节肢杀手",
    "保护", "火焰保护", "爆炸保护", "弹射物保护",
    "时运", "精准采集", "经验修补", "耐久",
    "效率", "力量", "冲击", "火矢", "穿透",
    "多重射击", "快速装填",
    "火焰附加", "无限", "抢夺", "击退",
    "饵钓", "海之眷顾",
    "冰霜行者", "深海探索者", "灵魂疾行",
    "荆棘", "横扫之刃",
    "速度", "缓慢", "急迫", "挖掘疲劳",
    "力量", "瞬间伤害", "瞬间治疗",
    "跳跃提升", "反胃", "生命恢复",
    "抗性提升", "防火", "水下呼吸",
    "隐身", "夜视", "饥饿",
    "虚弱", "中毒", "凋零",
    "发光", "漂浮", "幸运", "霉运",
]

def api(p):
    p["format"] = "json"
    qs = urlencode(p, doseq=True)
    req = Request(f"{API}?{qs}", headers={"User-Agent": UA})
    with urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))

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

def main():
    pages = list(dict.fromkeys(sys.argv[1:] if len(sys.argv) > 1 else PAGES))
    os.makedirs(OUT, exist_ok=True)
    entries, errors = [], []

    for i, raw in enumerate(pages, 1):
        title = resolve(raw)
        print(f"[{i}/{len(pages)}] {title}")
        text = get_text(title)
        if not text or len(text) < 50:
            print(f"  跳过")
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
        print(f"  {len(text)} 字符")
        time.sleep(0.5)

    out = os.path.join(OUT, "zh_wiki.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)

    print(f"\n完成！{len(entries)} 个条目 → {out}")
    print(f"复制 '{OUT}' 文件夹到服务器 config/mcai_kb/")

if __name__ == "__main__":
    main()
