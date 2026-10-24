"""装备和载具的贴图（ADR-0031）：宇航服、多功能工具和武器、技术模块、载具（车身、发光层、图标），
空间站的多功能工具柜和载具召唤台。

配色是一个系列，照无人深空的探索装备：
- 宇航服和载具是米白的壳，安全橙的条纹，深石墨色的关节和底盘，黑橡胶的轮胎，深青色的玻璃。
- 运转的灯、推力垫、炮口是青色；大灯暖白；炉火、喷口橙色；航行灯和信标红色。
- 多功能工具一种型号一套颜色：标准型白壳橙条青光，步枪枪灰红条琥珀光，实验型白壳紫条品红光，
  异星型长出来的绿色躯干、黄斑、青绿光，守卫机型深色装甲、琥珀灯带、红橙光。

实体贴图的方块展开和 client/exocraft/*Model.java 一一对应（texOffs 和尺寸）；面的叫法沿用旧脚本：
top 是 (u+d, v) 那块（游戏里朝上），front 是 (u+d, v+d)，right 在它左边，left 和 back 在它右边。
发光层单独成图：车身上画发光色阶的像素在发光层里照原色点亮，不透明度表示亮度；潜艇灯照出去的光柱
只在发光层里有。
"""
from __future__ import annotations

import math
import random
from functools import lru_cache

from canvas import Canvas
from color import Ramp, ramp
from palette import (AMBER, AMBER_GLOW, BASALT, BLUE, BLUE_GLOW, BRASS, COPPER, CYAN_GLOW, GLASS, GRAPHITE, GRAY,
                     GREEN_GLOW, HAZARD, LEAF, MAGENTA_GLOW, MOSS, ORANGE, ORANGE_GLOW, RED, RED_GLOW, RUBBER, RUST,
                     SCREEN, STEEL, TEAL, VIOLET, VIOLET_GLOW, WHITE, WHITE_GLOW, WOOD, YELLOW_GLOW, register)
from parts import bolts, casing
from recipe import texture

LEATHER = register(ramp("gear_leather", 42, 0.050, n=6, lo=0.22, hi=0.58, shift=12))     # 座椅的皮面
NAVY = register(ramp("gear_navy", 244, 0.040, n=7, lo=0.22, hi=0.66, shift=10))        # 潜艇下半截的深灰蓝
GUNMETAL = register(ramp("gear_gunmetal", 255, 0.018, n=7, lo=0.24, hi=0.74, shift=14))  # 步枪、守卫机的枪灰


# ---------------------------------------------------------------- 小工具

def lit_outline(c: Canvas, keep_emissive: bool = True):
    """物品描边：最外一圈压到这块材质自己最深的一两级，受光的左、上边浅一级；发光的像素不压。"""
    src = [row[:] for row in c.p]

    def opaque(x, y):
        return 0 <= x < c.w and 0 <= y < c.h and src[y][x] is not None

    for y in range(c.h):
        for x in range(c.w):
            px = src[y][x]
            if px is None or (keep_emissive and px[0].emissive):
                continue
            r = px[0]
            if not opaque(x + 1, y) or not opaque(x, y + 1):
                c.p[y][x] = (r, min(px[1], 0))
            elif not opaque(x - 1, y) or not opaque(x, y - 1):
                c.p[y][x] = (r, min(px[1], 1))


def limit_colors(c: Canvas, most: int = 16) -> Canvas:
    """物品图标最多 most 种颜色（发光色不算，ADR-0031 §四）：用得最少的那一级并进同一条色阶里最近的、
    还在用的一级，直到够数。手绘时多用了几级明暗，这里收一收。"""
    while True:
        count: dict = {}
        for y in range(c.h):
            for x in range(c.w):
                px = c.p[y][x]
                if px and not px[0].emissive:
                    count[(px[0].name, px[1])] = count.get((px[0].name, px[1]), 0) + 1
        if len(count) <= most:
            return c
        levels: dict = {}
        for name, lv in count:
            levels.setdefault(name, []).append(lv)
        for (name, lv), n in sorted(count.items(), key=lambda kv: kv[1]):
            others = [k for k in levels[name] if k != lv]
            if others:
                to = min(others, key=lambda k: (abs(k - lv), -count[(name, k)]))
                for y in range(c.h):
                    for x in range(c.w):
                        px = c.p[y][x]
                        if px and px[0].name == name and px[1] == lv:
                            c.p[y][x] = (px[0], to)
                break
        else:
            return c


def diag(x: int, y: int, axis: float = 15) -> tuple[int, int]:
    """斜放的工具的坐标：along 沿着从左下到右上的轴（往右上变大），across 垂直于轴（往右下变大）。"""
    return x - y, x + y - axis


# ---------------------------------------------------------------- 多功能工具

def paint(c: Canvas, grid: list[str], mats: dict, ox: int = 0, oy: int = 0):
    """按剪影上色：grid 里一个字符一种材质，mats[字符] = (色阶, 受光边的级数, 背光边的级数)。

    斜放的东西（工具、武器）光从左上来，所以每个格子的明暗看它往左上、往右下各还有几格才出剪影：
    靠左上的亮，靠右下的暗。最外一圈之后由 lit_outline 压暗。"""
    filled = {(x + ox, y + oy) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch not in ". "}
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            r, hi, lo = mats[ch]
            X, Y = x + ox, y + oy
            d1 = 0
            while (X - d1 - 1, Y - d1 - 1) in filled:
                d1 += 1
            d2 = 0
            while (X + d2 + 1, Y + d2 + 1) in filled:
                d2 += 1
            # 最外一圈留给描边；往里第一圈受光的一侧是高光、背光的一侧是阴影，中间是本色
            if d1 <= 1 and d2 > 1:
                lv = hi
            elif d2 <= 1 and d1 > 1:
                lv = lo
            else:
                lv = round(hi + (lo - hi) * (d1 / (d1 + d2) if d1 + d2 else 0.5))
            # 斜放的东西明暗是一条条 45° 的线，同色的格子只在角上相接：这是画法，不算零碎像素
            c.set(X, Y, r, lv, glint=True)


def glow_core(c: Canvas, cells, r: Ramp, cx: float, cy: float, top: int | None = None):
    """发光的芯：离中心越近越亮。"""
    top = r.top if top is None else top
    for x, y in cells:
        d = math.hypot(x - cx, y - cy)
        c.set(x, y, r, max(2, round(top - d * 1.1)), glint=True)


# 多功能工具的剪影：H 发射口（外圈是金属框、里面是光）、C 发射口后面的箍、B 机身、A 涂装、
# G 握把、T 扳机护圈、R 机身顶上瞄具的脊
MULTITOOL = [
    "................",
    "...........HHH..",
    "..........HHHHH.",
    ".........CHHHHH.",
    "........RBCHHH..",
    ".......RBBBCH...",
    "......RBBBBB....",
    ".....RBBBBB.....",
    "....AABBBBT.....",
    "...BAABBBTT.....",
    "...GBAABTT......",
    "..GGG.AA........",
    ".GGGG...........",
    ".GGG............",
    "..G.............",
    "................",
]


def multitool_shape(c: Canvas, body: Ramp, accent: Ramp, glow: Ramp, grip: Ramp = GRAPHITE, metal: Ramp = STEEL,
                    grid: list[str] = MULTITOOL, extra: dict | None = None):
    """多功能工具（斜放）。发射口 H 的外圈压成描边当金属框，里面是一团光，左上最亮。
    extra 给型号自己的材质（字符 -> (色阶, 亮, 暗)）。"""
    mats = {"B": (body, 6, 3), "R": (body, 6, 4), "A": (accent, 4, 1), "G": (grip, 4, 1), "T": (grip, 3, 1),
            "C": (grip, 3, 0), "H": (metal, 5, 2), "S": (body, 4, 2)}
    mats.update(extra or {})
    paint(c, grid, mats)
    head = {(x, y) for y, row in enumerate(grid) for x, ch in enumerate(row) if ch == "H"}
    core = [(x, y) for x, y in head if all((x + dx, y + dy) in head for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    lit_outline(c)
    if core:
        x0 = min(x + y for x, y in core)
        for x, y in core:
            d = (x + y - x0)
            c.set(x, y, glow, max(2, glow.top - round(d * 1.2)), glint=True)
        # 光芯最左上那一格是白的高光
        hx, hy = min(core, key=lambda p: (p[0] + p[1], p[1]))
        c.set(hx, hy, glow, glow.top, glint=True)
    return limit_colors(c)


@texture("item/multitool", refs=("item/iron_sword", "item/spyglass", "item/brush"))
def multitool(rng):
    # 标准型（手枪）：白色机身，橙色涂装，青色的发射口，深色握把，机身顶上一道瞄具的脊
    c = multitool_shape(Canvas(), WHITE, ORANGE, CYAN_GLOW, metal=STEEL)
    c.set(8, 7, GRAPHITE, 2, glint=True)          # 机身上的散热孔
    c.set(9, 6, GRAPHITE, 2, glint=True)
    return limit_colors(c)


MULTITOOL_RIFLE = [
    ".............HH.",
    "............HHHH",
    "...........CHHHH",
    "..........RBCHH.",
    ".........RBBBC..",
    "........RBBBB...",
    ".......RBBBB....",
    "......RBBBB.....",
    ".....AABBBB.....",
    "....BAABBGG.....",
    "...SBBAABGG.....",
    "..SSSB..GGG.....",
    ".SSSS...GG......",
    "SSSS............",
    "SSS.............",
    ".S..............",
]


@texture("item/multitool_rifle")
def multitool_rifle(rng):
    # 步枪：枪灰机身、红色涂装、琥珀色枪口；机身更长，后面接一截枪托，手枪式握把挂在机身下面
    c = multitool_shape(Canvas(), GUNMETAL, RED, AMBER_GLOW, grip=GRAPHITE, metal=GRAPHITE, grid=MULTITOOL_RIFLE,
                        extra={"S": (GRAPHITE, 4, 2)})
    c.set(10, 5, GRAPHITE, 1, glint=True)
    c.set(9, 6, GRAPHITE, 1, glint=True)
    return limit_colors(c)


MULTITOOL_EXPERIMENTAL = [
    "..........HHH...",
    ".........HHHHH..",
    "........KHHHHH..",
    "........RKHHHH..",
    ".......RBBKHH...",
    "......RBKBBK....",
    "......BBBKBB....",
    ".....AABBBKB....",
    "....BAABBBB.....",
    "....BBAABBT.....",
    "...GGBBAATT.....",
    "..GGGG..T.......",
    ".GGGG...........",
    ".GGG............",
    "..G.............",
    "................",
]


@texture("item/multitool_experimental")
def multitool_experimental(rng):
    # 实验型：白色机身，紫色涂装，枪管上两圈紫色的线圈，发射口是一团更大的品红色的光
    return multitool_shape(Canvas(), WHITE, VIOLET, MAGENTA_GLOW, metal=VIOLET, grid=MULTITOOL_EXPERIMENTAL,
                           extra={"K": (VIOLET, 5, 2)})


MULTITOOL_ALIEN = [
    "...........HH...",
    "..........HHHH..",
    ".........HHHHHH.",
    "........BCHHHH..",
    ".......BBBCHH...",
    "......BBBBBC....",
    ".....BBBBBBB....",
    "....BBYBBBB.....",
    "....BBBBBYB.....",
    "...BBBYBBB......",
    "...GBBBBB.......",
    "..GGGBB.........",
    ".GGGG...........",
    ".GGG............",
    "..G.............",
    "................",
]


@texture("item/multitool_alien")
def multitool_alien(rng):
    # 异星型：像长出来的绿色躯干，鼓起来的地方不规则，黄色斑点，青绿色的芽苞当发射口，木头一样的握把
    c = multitool_shape(Canvas(), LEAF, HAZARD, GREEN_GLOW, grip=WOOD, metal=MOSS, grid=MULTITOOL_ALIEN,
                        extra={"Y": (HAZARD, 4, 3), "C": (MOSS, 3, 1)})
    return c


MULTITOOL_SENTINEL = [
    "..........HHH...",
    ".........HHHHH..",
    "......F.CHHHHH..",
    ".....F.RCHHHH...",
    "....F.RBBCHH....",
    "......RLBBBC....",
    ".....RBBLBB.....",
    ".....RBBBLB.....",
    "....AABBBBL.....",
    "...BAABBBTT.....",
    "...GBAABTT......",
    "..GGG.AA........",
    ".GGGG...........",
    ".GGG............",
    "..G.............",
    "................",
]


@texture("item/multitool_sentinel")
def multitool_sentinel(rng):
    # 守卫机型：深色的棱角装甲，左上一排尖鳍，机身上一道斜的琥珀色灯带，红橙色的发射口
    return multitool_shape(Canvas(), GUNMETAL, AMBER, RED_GLOW, grip=BASALT, metal=GRAPHITE, grid=MULTITOOL_SENTINEL,
                           extra={"F": (GUNMETAL, 4, 3), "L": (AMBER_GLOW, 4, 3)})


@texture("item/damaged_multitool")
def damaged_multitool(rng):
    # 坏掉的多功能工具：发射口熄了、裂开，涂装褪色，机身上一道裂纹和一块烧黑的地方
    grid = [row for row in MULTITOOL]
    c = multitool_shape(Canvas(), WHITE, AMBER, SCREEN, metal=GRAPHITE, grid=grid)
    for x, y, lv in ((9, 5, 1), (8, 6, 2), (8, 7, 1), (7, 8, 2), (6, 9, 1)):
        c.set(x, y, GRAPHITE, lv, glint=True)      # 斜着一道裂纹
    for x, y in ((10, 6), (9, 7), (10, 7)):
        c.set(x, y, GRAPHITE, 3, glint=True)       # 烧黑的一块
    c.clear(14, 3)
    c.set(13, 2, SCREEN, 1, glint=True)            # 发射口裂开的缺口
    return limit_colors(c)


# ---------------------------------------------------------------- 武器（装进多功能工具的枪管组件）

def weapon(grid: list[str], mats: dict, glow: Ramp | None = None, glow_cells=(), hot=None) -> Canvas:
    """枪管组件：和多功能工具一样斜放，左下是插进工具里的接口。glow_cells 是发光的格子，hot 是最亮的那格。"""
    c = Canvas()
    paint(c, grid, mats)
    lit_outline(c)
    if glow:
        for x, y in glow_cells:
            d = 0 if hot is None else abs(x - hot[0]) + abs(y - hot[1])
            c.set(x, y, glow, max(2, glow.top - d), glint=True)
    return c


def cells_of(grid: list[str], ch: str) -> list[tuple[int, int]]:
    return [(x, y) for y, row in enumerate(grid) for x, k in enumerate(row) if k == ch]


SCATTER_BLASTER = [
    "................",
    "................",
    "................",
    "................",
    "........BBB.....",
    ".......BBBB.....",
    "......BBBBB.....",
    ".....BBBBB......",
    "....BBBBB.......",
    "...PBBBB........",
    "..PPPBB.........",
    ".PPPP...........",
    ".PPP............",
    "..P.............",
    "................",
    "................",
]


@texture("item/scatter_blaster", refs=("item/iron_sword",))
def scatter_blaster(rng):
    # 散射爆能枪：短粗的枪管，枪口张开成一个喇叭（钢的圆环，左上受光），喇叭里三个黑孔，喇叭根上一道橙色的箍
    c = Canvas()
    paint(c, SCATTER_BLASTER, {"B": (STEEL, 6, 3), "P": (GRAPHITE, 4, 1)})
    cx, cy = 12.0, 3.0
    for x, y in list(c.cells()):
        px = c.get(x, y)
        if px and px[0] is STEEL and 3.0 < math.hypot(x - cx, y - cy) <= 4.3:
            c.set(x, y, ORANGE, 4 if (x - cx) + (y - cy) < 4.5 else 2, glint=True)
    for x, y in c.disc_cells(cx, cy, 3.0):
        r = math.hypot(x - cx, y - cy)
        v = (x - cx) + (y - cy)
        if r > 1.9:
            c.set(x, y, STEEL, 6 if v < -1.5 else 5 if v < 0.5 else 3, glint=True)
        else:
            c.set(x, y, GRAPHITE, 3 if v > 0 else 2, glint=True)
    for x, y in ((11, 3), (12, 2), (13, 3)):
        c.set(x, y, GRAPHITE, 0, glint=True)
    lit_outline(c)
    return limit_colors(c)


PULSE_SPITTER = [
    "..............EE",
    ".............EEE",
    "............MEEE",
    "...........MMME.",
    "..........RRMM..",
    ".........RRRR...",
    "........BBBR....",
    ".......RBBB.....",
    "......RRRB......",
    ".....BRRR.......",
    "....BBBR........",
    "...RBBB.........",
    "..PPPB..........",
    ".PPPP...........",
    ".PPP............",
    "..P.............",
]


@texture("item/pulse_spitter")
def pulse_spitter(rng):
    # 脉冲喷射器：细长的枪管，管身套着三道散热环，枪口一点黄绿色的光
    grid = PULSE_SPITTER
    return weapon(grid, {"B": (STEEL, 6, 3), "R": (GRAPHITE, 5, 2), "M": (STEEL, 5, 2), "P": (GRAPHITE, 4, 1),
                         "E": (GREEN_GLOW, 4, 3)}, GREEN_GLOW, cells_of(grid, "E"), hot=(14, 1))


BLAZE_JAVELIN = [
    "..............EE",
    ".............EEE",
    "............BBE.",
    "...........BBB..",
    "..........BBB...",
    ".........BBB....",
    "........KKKK....",
    ".......KKKK.....",
    "......KKKK......",
    ".....BKKK.......",
    "....BBB.........",
    "...BBB..........",
    "..PBB...........",
    ".PPP............",
    ".PPP............",
    "..P.............",
]


@texture("item/blaze_javelin")
def blaze_javelin(rng):
    # 烈焰标枪：最长最细的一根导轨，中段一圈烧得发红的线圈，枪尖烧得发白
    grid = BLAZE_JAVELIN
    c = weapon(grid, {"B": (STEEL, 6, 3), "K": (ORANGE_GLOW, 4, 2), "P": (GRAPHITE, 4, 1), "E": (WHITE_GLOW, 5, 4)},
               WHITE_GLOW, cells_of(grid, "E"), hot=(15, 0))
    # 线圈一匝一匝的：亮的是绕着的铜线，暗的是匝间的缝
    for x, y in cells_of(grid, "K"):
        c.set(x, y, ORANGE_GLOW, 2 if ((x - y) // 2) % 2 else 4 + (x + y < 14), glint=True)
    return c


PLASMA_LAUNCHER = [
    "...........EEE..",
    "..........EEEEE.",
    "..........EEEEE.",
    ".........MEEEEE.",
    "........BBMEEE..",
    ".......BBBBM....",
    "......BBBBBA....",
    ".....BBBBBA.....",
    "....BBBBBA......",
    "....BBBBB.......",
    "...PBBBB........",
    "..PPPBB.........",
    ".PPPP...........",
    ".PPP............",
    "..P.............",
    "................",
]


@texture("item/plasma_launcher")
def plasma_launcher(rng):
    # 等离子发射器：粗短的炮管，炮口顶着一团紫色的等离子球
    grid = PLASMA_LAUNCHER
    c = weapon(grid, {"B": (STEEL, 6, 3), "M": (GRAPHITE, 3, 1), "A": (VIOLET, 4, 2), "P": (GRAPHITE, 4, 1),
                      "E": (VIOLET_GLOW, 4, 3)})
    for x, y in cells_of(grid, "E"):
        d = math.hypot(x - 12.0, y - 1.6)
        c.set(x, y, VIOLET_GLOW, max(1, VIOLET_GLOW.top - round(d * 1.2)), glint=True)
    return c


# ---------------------------------------------------------------- 宇航服的物品图标

# 手绘网格的字符表：0–6 白漆，a–e 安全橙，S–W 石墨，k–n 钢（2–5 级），v–z 面罩的深色玻璃（0–4 级），
# G 玻璃的反光，p/q 青光，r 绿灯，B/b 氧气罐的蓝（亮、暗），y 是面罩里最亮的反光
SUIT = {
    **{str(i): (WHITE, i) for i in range(7)},
    **{ch: (ORANGE, i) for i, ch in enumerate("abcde")},
    **{ch: (GRAPHITE, i) for i, ch in enumerate("STUVW")},
    **{ch: (STEEL, i + 2) for i, ch in enumerate("klmn")},
    **{ch: (SCREEN, i) for i, ch in enumerate("vwxyz")},
    "G": (GLASS, 5), "p": (CYAN_GLOW, 4), "q": (CYAN_GLOW, 5), "r": (GREEN_GLOW, 4),
}


def suit_icon(rows: list[str]) -> Canvas:
    c = Canvas()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in SUIT:
                r, lv = SUIT[ch]
                c.set(x, y, r, lv, glint=True)
    return limit_colors(c)


@texture("item/exosuit_helmet", refs=("item/iron_helmet", "item/turtle_helmet"))
def exosuit_helmet(rng):
    # 头盔：白色的圆壳，顶上一道橙色，一整块深色的面罩，左上有天空的反光，下巴一盏绿灯，颈圈是石墨色
    return suit_icon([
        "................",
        ".....11dc11.....",
        "...1566dc5440...",
        "..15665dc54430..",
        "..155444444430..",
        "..14zGGyyxxw20..",
        "..14zGyyxxwv20..",
        "..14yyxxwwvv20..",
        "..13xxwwwvvv20..",
        "..13wwwvvvvv20..",
        "..134444433320..",
        "..133r43333220..",
        "...UWWVVVUUTS...",
        "....SSSSSSSS....",
        "................",
        "................",
    ])


@texture("item/exosuit_chestplate", refs=("item/iron_chestplate",))
def exosuit_chestplate(rng):
    # 胸甲：白色的躯干和护肩，领口、胸前一块控制面板（绿灯、青灯），一道橙色的腰线，下摆是石墨色的腰带
    return suit_icon([
        "................",
        ".1111......1111.",
        "166551SSSS154430",
        "1655666dc5554320",
        "1554566dc5544320",
        "..1556TUUT5441..",
        "..1556TrpT5431..",
        "..1555TUUT4431..",
        "..155565544431..",
        "..1dddccccbbb1..",
        "..155565444331..",
        "..1VWWVVVVUUT1..",
        "...SSSSSSSSSS...",
        "................",
        "................",
        "................",
    ])


@texture("item/exosuit_leggings", refs=("item/iron_leggings",))
def exosuit_leggings(rng):
    # 护腿：石墨色的腰带扣，白色的大腿，膝盖上灰色的护垫，外侧一道橙条
    return suit_icon([
        "................",
        "..111111111111..",
        "..1VWWVddVVUT1..",
        "..156665554431..",
        "..156654344431..",
        "..1d6654344c31..",
        "..1d66541544c1..",
        "..1d661..144c1..",
        "..1mnl1..1lmk1..",
        "..1lmk1..1lkk1..",
        "..1d551..144b1..",
        "..1d551..143b1..",
        "..145410.14320..",
        "..UUUUT..TTTTS..",
        "................",
        "................",
    ])


@texture("item/exosuit_boots", refs=("item/iron_boots",))
def exosuit_boots(rng):
    # 靴子：白色的靴筒，一道橙色，石墨色的厚鞋底，鞋尖朝外
    return suit_icon([
        "................",
        "................",
        "................",
        "................",
        "................",
        "..1111....1111..",
        "..1650....1540..",
        "..1dc0....1cb0..",
        "..1650....1540..",
        ".156540..154440.",
        ".1dddcb..1ccbba.",
        ".1nmmlk..1mllkk.",
        ".VWWWVU..VWVVUT.",
        ".SSSSSS..SSSSSS.",
        "................",
        "................",
    ])


@texture("item/oxygen_capsule", refs=("item/potion", "item/honey_bottle"))
def oxygen_capsule(rng):
    # 氧气胶囊：蓝色的耐压罐，左边一道高光，中间一圈白色的标签，顶上钢的阀门
    c = Canvas()
    c.draw([
        "................",
        "......1221......",
        "......2442......",
        ".....125531.....",
        "....11111110....",
    ], {"0123456": STEEL})
    body = ["....aBDDDCCa....", "....aBDDCCBa....", "....aBDDCCBa....", "....aw6665wa....", "....aw4433wa....",
            "....aBDDCCBa....", "....aBDDCBBa....", "....aBDCCBBa....", "....aABBBBAa....", "....aaaaaaaa...."]
    blue = {"a": (BLUE, 1), "A": (BLUE, 2), "B": (BLUE, 3), "C": (BLUE, 4), "D": (BLUE, 5),
            "w": (WHITE, 2), "6": (WHITE, 6), "5": (WHITE, 5), "4": (WHITE, 4), "3": (WHITE, 3)}
    c.ascii(body, {k: (r, lv, True) for k, (r, lv) in blue.items()}, 0, 4)
    c.set(5, 5, GLASS, 5, glint=True)
    c.set(5, 6, BLUE, 6, glint=True)
    return limit_colors(c)


@texture("item/hazard_cell", refs=("item/potion",))
def hazard_cell(rng):
    # 防护电池：黄色的电池芯，黑色的警示斜纹，顶上石墨色的接头，左边一道高光
    c = Canvas()
    c.draw([
        "................",
        "................",
        ".......12.......",
        "......1340......",
        "....11111110....",
    ], {"0123456": STEEL})
    c.rect(4, 5, 11, 12, HAZARD, 3)
    for x, y in c.cells(5, 5, 10, 12):
        if (x + y) % 4 < 2:
            c.set(x, y, GRAPHITE, 1 if x > 7 else 2)
        else:
            c.set(x, y, HAZARD, 4 if x < 7 else 3)
    c.vline(5, 5, 12, HAZARD, 5)
    for y in range(5, 13):
        if (5 + y) % 4 < 2:
            c.set(5, y, GRAPHITE, 3)
    c.rect(4, 13, 11, 13, HAZARD, 1)
    lit_outline(c)
    c.set(5, 6, HAZARD, HAZARD.top, glint=True)
    return c


# ---------------------------------------------------------------- 载具的物品图标

# 0–6 白壳，a–e 橙，S–W 石墨，k–n 钢（2–5 级），f–j 轮胎的橡胶（0–4 级），v–y 深色玻璃，G 玻璃反光，
# p/q 青光，L 大灯，F 炉火，r 红灯，H 警示黄，z/Z 皮座椅，N/O/P 潜艇的深灰蓝（1、3、5 级）
VEHICLE = {
    **{str(i): (WHITE, i) for i in range(7)},
    **{ch: (ORANGE, i) for i, ch in enumerate("abcde")},
    **{ch: (GRAPHITE, i) for i, ch in enumerate("STUVW")},
    **{ch: (STEEL, i + 2) for i, ch in enumerate("klmn")},
    **{ch: (RUBBER, i) for i, ch in enumerate("fghij")},
    **{ch: (SCREEN, i) for i, ch in enumerate("vwxy")},
    "G": (GLASS, 5), "p": (CYAN_GLOW, 4), "q": (CYAN_GLOW, 5), "o": (CYAN_GLOW, 2), "L": (WHITE_GLOW, 5),
    "F": (ORANGE_GLOW, 4), "r": (RED_GLOW, 4), "H": (HAZARD, 4), "z": (LEATHER, 1), "Z": (LEATHER, 3),
    "N": (NAVY, 1), "O": (NAVY, 3), "P": (NAVY, 5), "u": (COPPER, 3), "U": (GRAPHITE, 3),
}


def vehicle_icon(rows: list[str]) -> Canvas:
    c = Canvas()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in VEHICLE:
                r, lv = VEHICLE[ch]
                c.set(x, y, r, lv, glint=True)
    return limit_colors(c)


@texture("item/exocraft", refs=("item/minecart",))
def exocraft_icon(rng):
    # 探索车（侧面，车头朝左）：米白的车身、一道橙条，防滚架和座椅，车尾橙色的货箱，车头一门激光炮，两个大轮子
    return vehicle_icon([
        "................",
        "................",
        "................",
        "......VWWV......",
        "......U..U......",
        "......U.ZUeeeee.",
        "pkkk..UzZUddddc.",
        ".56666UVVUdddcb.",
        "L666666655554430",
        "1eeeedddddddccb0",
        "1444443333333320",
        "SVghhgVVVVghhgVS",
        ".ghnmgf..ghnmgf.",
        ".gglkff..gglkff.",
        "..gff.....gff...",
        "................",
    ])


@texture("item/heavy_rover", refs=("item/minecart",))
def heavy_rover_icon(rng):
    # 重型车（侧面，车头朝左）：白色驾驶室和风挡，橙色的瓦楞货舱，灰色的精炼机（顶上警示条、观察窗里的炉火）和烟囱，八个轮子
    return vehicle_icon([
        "................",
        ".L.L.........S..",
        "UVVVU........T..",
        "15664........U..",
        "1Gyx3edededHSHSH",
        "1yxw3dcdcdcnmmml",
        "15553dcdcdcmllkl",
        "1eeedcbcbcbmFFkl",
        "L4443cbcbcblmmlk",
        "13332bababalllkk",
        "SUUUUUUUUUUUUUUS",
        ".hg..hg..hg..hg.",
        "hnlggnlggnlggnlg",
        "glkfglkfglkfglkf",
        ".ff..ff..ff..ff.",
        "................",
    ])


@texture("item/hover_car", refs=("item/minecart",))
def hover_car_icon(rng):
    # 悬浮车（侧面，车头朝左）：扁平的米白车身、橙色的腰线、斜的挡风玻璃和靠背，车底两块推力垫发青光，没有轮子
    return vehicle_icon([
        "................",
        "................",
        "................",
        "................",
        "................",
        "........vG..eZ..",
        ".......vwG..dZ..",
        "Sk....vwwy..cz..",
        "pk566666666665..",
        "L666666665555440",
        "1eeeeddddddccbb0",
        "1444333333332220",
        ".TUUVVVVVVVVUUT.",
        "..opqpo..opqpo..",
        "...opo....opo...",
        "................",
    ])


@texture("item/exo_mech", refs=("item/armor_stand",))
def exo_mech_icon(rng):
    # 机甲（正面）：白色的胸甲、深色的驾驶舱玻璃、青色的面罩；右臂（画面左边）一门炮，左臂一台扫描仪，两条腿
    return vehicle_icon([
        "......1561......",
        "......pqqp......",
        "..dc15666651cb..",
        "..ec1GyxxwV1cb..",
        ".156yxxwwvV1450.",
        ".155xwwvvvV1440.",
        ".1551dc11dc14 40",
        ".1442333333143k0",
        ".133.VWWWWV.mnk.",
        ".SUS..15..15.p..",
        "..F...14..14....",
        ".....dc..dc.....",
        ".....UV..UV.....",
        ".....TU..TU.....",
        "....VWWVVWWV....",
        "....SSSSSSSS....",
    ])


@texture("item/submarine", refs=("item/oak_boat",))
def submarine_icon(rng):
    # 潜艇（侧面，艇头朝左）：上半截白、腰上一道安全橙、下半截深灰蓝、最底下黑色防污漆；顶上一个淡青色的玻璃罩，
    # 艇头一盏灯，艇尾橙色的导流罩和铜色的螺旋桨
    return vehicle_icon([
        "................",
        "................",
        "................",
        ".....GGy........",
        "....Gwxyy1......",
        "...1566666661...",
        "..156666665554b.",
        ".L66666655544dcu",
        ".eeeedddddddccbu",
        ".OPPPOOOOOOONNbu",
        "..OOONNNNNNNNN..",
        "...fgggggggff...",
        "................",
        "................",
        "................",
        "................",
    ])


# ---------------------------------------------------------------- 技术模块

# 每种模块两张：module_<种类> 是芯片和图案，module_<种类>_overlay 是边框和指示灯（灰度），
# 游戏里按品级染色（client/tech/ModuleGradeTint：C 灰白、B 绿、A 蓝、S 金）。
# 图案 8×8，左上角对齐到 (4, 4)：'#' 主色、'+' 亮、'-' 暗。宇航服的模块青色，多功能工具的橙色，飞船的紫色。
MODULE_GLYPHS = {
    # 氧气泡
    "life_support": (CYAN_GLOW, [
        "........",
        "..++....",
        ".+##+...",
        ".#--#.+.",
        ".+##+.#.",
        "..##....",
        "....++..",
        "....##..",
    ]),
    # 雪花
    "cold_lining": (CYAN_GLOW, [
        "...+....",
        ".#.#.#..",
        "..###...",
        "++#+#++.",
        "..###...",
        ".#.#.#..",
        "...#....",
        "........",
    ]),
    # 火焰
    "heat_lining": (CYAN_GLOW, [
        "...+....",
        "...##...",
        "..+##...",
        "..###+..",
        ".##-##..",
        ".#---#..",
        "..###...",
        "........",
    ]),
    # 过滤网格
    "toxin_filter": (CYAN_GLOW, [
        "........",
        ".######.",
        ".#-#-#+.",
        ".######.",
        ".#-#-#-.",
        ".+#####.",
        "........",
        "........",
    ]),
    # 三叶标志
    "radiation_shield": (CYAN_GLOW, [
        "........",
        ".##..##.",
        ".###+##.",
        "...++...",
        "...##...",
        "..####..",
        "..####..",
        "........",
    ]),
    # 向上的箭头和尾焰
    "jet_booster": (CYAN_GLOW, [
        "...++...",
        "..####..",
        ".######.",
        "...##...",
        "...##...",
        "..-##-..",
        "...--...",
        "........",
    ]),
    # 双箭头
    "servo": (CYAN_GLOW, [
        "........",
        ".#..#...",
        "..#+.#..",
        "...#+.#.",
        "..#+.#..",
        ".#..#...",
        "........",
        "........",
    ]),
    # 盾牌
    "shield": (CYAN_GLOW, [
        "........",
        ".######.",
        ".#++++#.",
        ".#+##+#.",
        ".#####-.",
        "..###-..",
        "...#-...",
        "........",
    ]),
    # 斜着的光束
    "beam_focus": (AMBER_GLOW, [
        "......++",
        ".....+#.",
        "....+#..",
        "...+#...",
        "..##....",
        ".###....",
        "-##.....",
        "........",
    ]),
    # 散热片
    "heat_sink": (AMBER_GLOW, [
        "........",
        ".#.#.#..",
        ".#.#.#..",
        ".+.+.+..",
        ".#.#.#..",
        "-######.",
        "........",
        "........",
    ]),
    # 扫描波纹
    "scanner_array": (AMBER_GLOW, [
        "........",
        ".####...",
        "#....#..",
        "..##..#.",
        ".#..#.#.",
        ".#.+#.#.",
        "........",
        "........",
    ]),
    # 眼睛
    "analyzer": (AMBER_GLOW, [
        "........",
        "..####..",
        ".#....#.",
        "#..++..#",
        "#..++..#",
        ".#....#.",
        "..####..",
        "........",
    ]),
    # 准星
    "weapon_calibrator": (AMBER_GLOW, [
        "...#....",
        "..###...",
        ".#.#.#..",
        "####+###",
        ".#.#.#..",
        "..###...",
        "...#....",
        "........",
    ]),
    # ---- 飞船（ADR-0024）
    # 喷口和火焰
    "thrust_efficiency": (VIOLET_GLOW, [
        "..####..",
        "..#--#..",
        ".######.",
        "..+..+..",
        "..+..+..",
        "...++...",
        "...+....",
        "........",
    ]),
    # 往右的箭头加速度线
    "pulse_tuning": (VIOLET_GLOW, [
        "........",
        "-...##..",
        "--..###.",
        "+++#####",
        "--..###.",
        "-...##..",
        "........",
        "........",
    ]),
    # 星和跃迁的弧线
    "hyperdrive_booster": (VIOLET_GLOW, [
        "......+.",
        ".....+#+",
        "..-...+.",
        ".-......",
        "-...#...",
        "-..###..",
        ".-..#...",
        "..--....",
    ]),
    # 护盾的弧面
    "deflector": (VIOLET_GLOW, [
        "..####..",
        ".#++++#.",
        "#+....+#",
        "#......#",
        "#......#",
        ".#....#.",
        "..#..#..",
        "...##...",
    ]),
    # 铆钉钢板
    "hull_reinforcement": (VIOLET_GLOW, [
        "########",
        "#+....+#",
        "#.----.#",
        "#.----.#",
        "#.----.#",
        "#.----.#",
        "#+....+#",
        "########",
    ]),
    # 炮管和瞄准点
    "fire_control": (VIOLET_GLOW, [
        "......+.",
        ".....+++",
        "....#.+.",
        "...#....",
        "..#.....",
        ".##.....",
        "###.....",
        "-#......",
    ]),
}


def module_chip(glow: Ramp, glyph: list[str]) -> Canvas:
    """芯片：石墨色的封装，左右各三根钢的针脚，中间凹进去一块深色的窗，图案在窗里发光。
    最外一圈会被品级色的边框盖住。"""
    c = Canvas()
    c.panel(2, 2, 13, 13, GRAPHITE, 3)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.clear(x, y)
    for y in (5, 8, 11):
        c.set(1, y, STEEL, 5, glint=True)
        c.set(14, y, STEEL, 3, glint=True)
    c.recess(4, 4, 11, 11, SCREEN, 2)
    filled = {(x, y) for y, row in enumerate(glyph) for x, ch in enumerate(row) if ch != "."}
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "+":
                lv = 5
            elif ch == "-":
                lv = 1
            else:
                # 主色带一点体积：左上没东西的亮一级，右下没东西的暗一级
                lv = 3
                if (x - 1, y) not in filled and (x, y - 1) not in filled:
                    lv = 4
                elif (x + 1, y) not in filled and (x, y + 1) not in filled:
                    lv = 2
            c.set(x + 4, y + 4, glow, lv, glint=True)
    # 窗里的光把四周的玻璃映亮一点
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                X, Y = x + 4 + dx, y + 4 + dy
                px = c.get(X, Y)
                if px and px[0] is SCREEN and 4 <= X <= 11 and 4 <= Y <= 11:
                    c.set(X, Y, SCREEN, 3, glint=True)
    return c


def module_overlay() -> Canvas:
    """边框和指示灯，灰度：游戏里乘上品级的颜色。上、左边受光，下、右边背光，左上角一盏指示灯。"""
    c = Canvas()
    c.hline(3, 12, 2, GRAY, 6)
    c.vline(2, 3, 12, GRAY, 5)
    c.hline(3, 12, 13, GRAY, 2)
    c.vline(13, 3, 12, GRAY, 3)
    c.set(3, 3, GRAY, 6, glint=True)          # 指示灯
    c.set(4, 3, GRAY, 4, glint=True)
    c.set(3, 4, GRAY, 4, glint=True)
    c.set(12, 12, GRAY, 1, glint=True)        # 右下角的定位缺口
    return c


def _module(name: str, glow: Ramp, glyph: list[str]):
    @texture(f"item/module_{name}", refs=("item/disc_fragment_5",) if name == "analyzer" else ())
    def base(rng):
        return module_chip(glow, glyph)

    @texture(f"item/module_{name}_overlay", tinted=True)
    def overlay(rng):
        return module_overlay()


for _name, (_glow, _glyph) in MODULE_GLYPHS.items():
    _module(_name, _glow, _glyph)


# ---------------------------------------------------------------- 空间站的多功能工具柜、载具召唤台

def kiosk_screen(c: Canvas, x0: int = 2, y0: int = 3, x1: int = 13, y1: int = 10):
    """嵌在机壳里的一块屏幕：凹进去，深色的玻璃，左上角一道反光。"""
    c.recess(x0 - 1, y0 - 1, x1 + 1, y1 + 1, STEEL, 4)
    c.rect(x0, y0, x1, y1, SCREEN, 1)
    c.hline(x0, x1, y0, SCREEN, 2)
    c.set(x0, y0, GLASS, 2, glint=True)
    c.set(x0 + 1, y0, GLASS, 1, glint=True)


@texture("block/multitool_cabinet_front", refs=("block/smithing_table_front",))
def multitool_cabinet_front(rng):
    # 正面一块展示屏，里面斜挂着三把小工具（白、橙、青），下面一排琥珀色的价签
    c = casing(rng)
    kiosk_screen(c, 2, 2, 13, 10)
    mini = ["..gg", ".Bg.", "BBb.", "Bb..", "S...", "S..."]
    for x0, body, glow in ((2, WHITE, CYAN_GLOW), (6, GUNMETAL, AMBER_GLOW), (10, LEAF, GREEN_GLOW)):
        # 一把小工具：握把在左下，两格宽的机身，枪口两格光
        hi = 6 if body is WHITE else 5
        legend = {"g": (glow, 5, True), "B": (body, hi, True), "b": (body, hi - 2, True), "S": (GRAPHITE, 3, True)}
        c.ascii(mini, legend, x0 + 1, 3)
    # 价签
    for x in range(3, 13):
        c.set(x, 12, AMBER_GLOW, 4 if x % 4 != 2 else 2, glint=True)
    c.hline(2, 13, 13, STEEL, 2)
    return c


@texture("block/multitool_cabinet_side")
def multitool_cabinet_side(rng):
    # 侧面：一组散热百叶，靠边一条竖着的橙色灯带
    c = casing(rng)
    c.panel(3, 3, 12, 12, STEEL, 4)
    c.vent(4, 4, 10, 11, STEEL, 4)
    c.vline(12, 4, 11, ORANGE, 3)
    c.set(12, 4, ORANGE, 5, glint=True)
    bolts(c, 1)
    return c


@texture("block/multitool_cabinet_top")
def multitool_cabinet_top(rng):
    # 顶面：一块圆的徽记，橙色的环里一道横杠（多功能工具的标志）
    c = casing(rng)
    c.recess(3, 3, 12, 12, STEEL, 4)
    c.ring(7.5, 7.5, 4.0, ORANGE, 3, 1.2)
    for x, y in c.disc_cells(7.5, 7.5, 4.0):
        if (x + y) < 13 and c.get(x, y)[0] is ORANGE:
            c.set(x, y, ORANGE, 4)
    c.hline(5, 10, 7, ORANGE, 4)
    c.hline(5, 10, 8, ORANGE, 2)
    c.set(5, 7, ORANGE, 5, glint=True)
    bolts(c, 1)
    return c


@texture("block/vehicle_summoner_front", refs=("block/lodestone_side",))
def vehicle_summoner_front(rng):
    # 正面一块屏幕：一辆车的轮廓（车头朝左）和右上一圈橙色的定位环，下面一个琥珀色的按钮
    c = casing(rng)
    kiosk_screen(c, 2, 2, 13, 10)
    # 车：车身、车顶、两个轮子，都是屏幕上的青色线条
    c.hline(4, 10, 7, CYAN_GLOW, 4)
    c.hline(5, 8, 6, CYAN_GLOW, 3)
    c.set(4, 7, CYAN_GLOW, 5, glint=True)
    for x in (5, 9):
        c.set(x, 8, CYAN_GLOW, 3, glint=True)
    c.set(7, 5, CYAN_GLOW, 2, glint=True)
    # 定位环
    for a in range(0, 360, 45):
        x = 11 + round(1.5 * math.cos(math.radians(a)))
        y = 4 + round(1.5 * math.sin(math.radians(a)))
        c.set(x, y, ORANGE_GLOW, 4 if a in (180, 225, 270) else 3, glint=True)
    c.set(11, 4, ORANGE_GLOW, 5, glint=True)
    # 按钮
    c.rect(6, 12, 9, 12, GRAPHITE, 1)
    c.set(7, 12, AMBER_GLOW, 5, glint=True)
    c.set(8, 12, AMBER_GLOW, 4, glint=True)
    return c


@texture("block/vehicle_summoner_side")
def vehicle_summoner_side(rng):
    # 侧面：散热百叶，靠边一条橙色的状态灯
    c = casing(rng)
    c.panel(3, 3, 12, 12, STEEL, 4)
    c.vent(4, 4, 10, 11, STEEL, 4)
    c.vline(12, 4, 11, AMBER, 3)
    c.set(12, 4, AMBER_GLOW, 4, glint=True)
    bolts(c, 1)
    return c


@texture("block/vehicle_summoner_top")
def vehicle_summoner_top(rng):
    # 顶面：落点标记，深色的底板上一圈橙色的菱形，四个角黄色的三角，中间一个橙点
    c = casing(rng)
    c.recess(2, 2, 13, 13, GRAPHITE, 3)
    for x, y in c.disc_cells(7.5, 7.5, 4.6):
        r = math.hypot(x - 7.5, y - 7.5)
        if r > 3.4:
            c.set(x, y, ORANGE, 4 if x + y < 15 else 2)
        elif r < 1.2:
            c.set(x, y, ORANGE, 4 if x + y < 15 else 3)
    # 四个方向的黄色箭头指着中间
    for x, y in ((7, 3), (8, 3), (3, 7), (3, 8), (12, 7), (12, 8), (7, 12), (8, 12)):
        c.set(x, y, HAZARD, 4 if x + y < 15 else 3, glint=True)
    bolts(c, 1)
    return c


# ---------------------------------------------------------------- 实体贴图的画法

def faces(u: int, v: int, w: int, h: int, d: int) -> dict:
    """模型方块的贴图展开：每个面在贴图上的矩形 (x, y, 宽, 高)。"""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


SIDES = ("front", "back", "left", "right")


class Skin:
    """实体贴图：c 是车身，g 是发光层（RenderTypes.eyes，按原色全亮画上去，不透明度就是亮度）。"""

    def __init__(self, w: int, h: int, seed: int):
        self.c = Canvas(w, h)
        self.g = Canvas(w, h)
        self.rng = random.Random(seed)

    # ---- 面
    def panel(self, rect, r: Ramp, lv: int, bevel: bool = True, brushed: int = 0):
        """一块面板：底色，四边倒角（上左亮、下右暗），brushed 给几团拉丝纹理。"""
        x0, y0, w, h = rect
        if w <= 0 or h <= 0:
            return
        self.c.rect(x0, y0, x0 + w - 1, y0 + h - 1, r, lv)
        if bevel and w > 2 and h > 2:
            self.c.bevel(x0, y0, x0 + w - 1, y0 + h - 1)
        for _ in range(brushed):
            x, y = self.rng.randint(x0, x0 + w - 1), self.rng.randint(y0, y0 + h - 1)
            for i in range(self.rng.randint(2, 3)):
                if x + i < x0 + w:
                    self.c.shift(x + i, y, self.rng.choice((-1, 1)))

    def box(self, u, v, w, h, d, r: Ramp, lv: int, bevel: bool = True, brushed: int = 0) -> dict:
        f = faces(u, v, w, h, d)
        for key, rect in f.items():
            # 顶面亮一级、底面暗一级：光从上面来
            self.panel(rect, r, lv + (1 if key == "top" else -1 if key == "bottom" else 0), bevel,
                       brushed if rect[2] * rect[3] > 30 else 0)
        return f

    def fill(self, rect, r: Ramp, lv: int):
        x0, y0, w, h = rect
        if w > 0 and h > 0:
            self.c.rect(x0, y0, x0 + w - 1, y0 + h - 1, r, lv)

    def row(self, rect, i: int, r: Ramp, lv: int):
        x0, y0, w, h = rect
        if 0 <= i < h:
            self.c.hline(x0, x0 + w - 1, y0 + i, r, lv)

    def col(self, rect, i: int, r: Ramp, lv: int):
        x0, y0, w, h = rect
        if 0 <= i < w:
            self.c.vline(x0 + i, y0, y0 + h - 1, r, lv)

    def stripe(self, rect, i: int, r: Ramp = ORANGE, lv: int = 4):
        """一道两行的涂装条：上面一行亮，下面一行暗一级。"""
        self.row(rect, i, r, lv)
        self.row(rect, i + 1, r, lv - 1)

    def seam(self, rect, i: int, r: Ramp, lv: int, vertical: bool = False):
        """面板接缝：一道暗线，紧挨着一道亮线（接缝的另一边受光）。"""
        if vertical:
            self.col(rect, i, r, lv - 2)
            self.col(rect, i + 1, r, lv + 1)
        else:
            self.row(rect, i, r, lv - 2)
            self.row(rect, i + 1, r, lv + 1)

    def glass(self, rect, frame: Ramp | None = GRAPHITE):
        """深色的玻璃：一圈框，左上浅、右下深，斜着两道反光。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                if edge and frame and w > 2 and h > 2:
                    self.c.set(x, y, frame, 2 if (x == x0 or y == y0) else 1)
                    continue
                t = ((x - x0) / max(1, w - 1) + (y - y0) / max(1, h - 1)) / 2
                lv = 3 if t < 0.3 else 2 if t < 0.65 else 1
                k = (x - x0) - (y - y0)
                if k % 7 in (2, 3) and t < 0.8:
                    self.c.set(x, y, GLASS, 2 if k % 7 == 2 else 1)
                else:
                    self.c.set(x, y, SCREEN, lv)

    def grille(self, rect, r: Ramp = GRAPHITE, lv: int = 3):
        """横的百叶：暗的缝和受光的唇边交替。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            self.c.hline(x0, x0 + w - 1, y, r, lv - 2 if (y - y0) % 2 == 0 else lv + 1)

    def hazard(self, rect):
        """黄黑相间的警示斜条。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if ((x + y) // 2) % 2 == 0:
                    self.c.set(x, y, HAZARD, 4 if y == y0 else 3)
                else:
                    self.c.set(x, y, GRAPHITE, 1)

    def tread(self, rect):
        """轮胎的胎面：横纹。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                k = (y - y0) % 3 if w <= h else (x - x0) % 3
                self.c.set(x, y, RUBBER, 3 if k == 0 else 1 if k == 1 else 2)

    def tire(self, rect, rim: Ramp = STEEL):
        """轮子的侧面：黑橡胶的胎，钢的轮辋（上左亮），中间一颗轴盖，轮辐四颗螺栓。"""
        x0, y0, w, h = rect
        cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
        r = min(w, h) / 2
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                dx, dy = x - cx, y - cy
                d = math.hypot(dx, dy)
                if d <= r * 0.22:
                    self.c.set(x, y, rim, 2)
                elif d <= r * 0.62:
                    self.c.set(x, y, rim, 5 if dx + dy < -0.5 else 3 if dx + dy > 0.5 else 4)
                elif d <= r * 0.75:
                    self.c.set(x, y, rim, 2)
                else:
                    self.c.set(x, y, RUBBER, 2 if dx + dy < -1 else 1 if d < r * 0.95 else 0)

    def wheel(self, u, v, w, h, d, rim: Ramp = STEEL):
        f = faces(u, v, w, h, d)
        for key in ("front", "back", "top", "bottom"):
            self.tread(f[key])
        for key in ("left", "right"):
            self.tire(f[key], rim)
        return f

    # ---- 发光
    def light(self, x: int, y: int, alpha: int = 255, r: Ramp | None = None, lv: int | None = None):
        """发光层上点亮一格：默认照抄车身上这一格的颜色。"""
        px = self.c.get(x, y)
        if r is None:
            if not px:
                return
            r, lv = px[0], px[1]
        self.g.set(x, y, r, lv, alpha=alpha)

    def glow(self, rect, r: Ramp, lv: int, alpha: int = 255):
        """一块整个发光：车身上画发光色，发光层同一块点亮。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.c.set(x, y, r, lv)
                self.light(x, y, alpha)

    def lamp(self, rect, r: Ramp = WHITE_GLOW, alpha: int = 255):
        """一盏灯：中间最亮，边上暗一级。"""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = (x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)) and w > 2 and h > 1
                self.c.set(x, y, r, r.top - 1 if edge else r.top)
                self.light(x, y, alpha)


@lru_cache(maxsize=None)
def skin(name: str) -> Skin:
    """同一张车身和它的发光层只画一次（两个配方各取一半）。"""
    return SKINS[name]()


SKINS: dict = {}


def skin_recipes(name: str, refs: tuple = ()):
    """登记 entity/<name> 和 entity/<name>_glow 两个配方，都从同一张 Skin 里取。"""
    def wrap(fn):
        SKINS[name] = fn

        @texture(f"entity/{name}", refs=refs)
        def base(rng):
            return skin(name).c

        @texture(f"entity/{name}_glow")
        def glow(rng):
            return skin(name).g
        return fn
    return wrap


# ---------------------------------------------------------------- 探索车（client/exocraft/ExocraftModel.java，128×128）

@skin_recipes("exocraft", refs=("entity/minecart",))
def exocraft_skin() -> Skin:
    s = Skin(128, 128, 83)
    # 底盘：侧面米白，一道橙条，底下两行石墨色；两道车门缝；顶上是花纹钢板；前后保险杠上警示条
    chassis = s.box(0, 0, 20, 8, 36, WHITE, 5, brushed=6)
    for key in ("left", "right"):
        x0, y0, w, h = chassis[key]
        s.stripe(chassis[key], 2)
        s.fill((x0, y0 + h - 2, w, 2), GRAPHITE, 3)
        s.row(chassis[key], h - 1, GRAPHITE, 2)
        for dx in (w // 2 - 4, w // 2 + 6):
            s.c.vline(x0 + dx, y0, y0 + 1, WHITE, 3)
            s.c.vline(x0 + dx, y0 + 4, y0 + h - 3, WHITE, 3)
            s.c.vline(x0 + dx + 1, y0 + 4, y0 + h - 3, WHITE, 6)
    x0, y0, w, h = chassis["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            k = (x + y) % 4 == 0 or (x - y) % 4 == 0
            s.c.set(x, y, GRAPHITE, 4 if k and (x + y) % 4 == 0 else 3 if k else 2)
    s.fill(chassis["bottom"], GRAPHITE, 1)
    for key in ("front", "back"):
        x0, y0, w, h = chassis[key]
        s.fill((x0, y0 + h - 3, w, 3), GRAPHITE, 3)
        s.row(chassis[key], h - 1, GRAPHITE, 2)
        s.hazard((x0, y0 + h - 3, w, 1))
    # 发动机罩：米白，中间一道橙色宽条，前面是散热格栅
    hood = s.box(32, 44, 18, 3, 12, WHITE, 5)
    x0, y0, w, h = hood["top"]
    s.c.rect(x0 + w // 2 - 3, y0, x0 + w // 2 + 2, y0 + h - 1, ORANGE, 3)
    s.c.vline(x0 + w // 2 - 3, y0, y0 + h - 1, ORANGE, 4)
    x0, y0, w, h = hood["front"]
    for x in range(x0, x0 + w):
        s.c.vline(x, y0, y0 + h - 1, GRAPHITE, 1 if x % 2 == 0 else 3)
    # 货箱：橙色，箱板的边框和缝，侧面印着编号样的白条
    crate = s.box(0, 68, 18, 6, 10, ORANGE, 3)
    for key, rect in crate.items():
        x0, y0, w, h = rect
        s.c.frame(x0, y0, x0 + w - 1, y0 + h - 1, ORANGE, 1)
        s.c.hline(x0 + 1, x0 + w - 2, y0 + 1, ORANGE, 4) if h > 3 else None
        if key in ("left", "right", "back") and h >= 6:
            s.c.hline(x0 + 2, x0 + min(w - 2, 7), y0 + 3, WHITE, 6)
    x0, y0, w, h = crate["top"]
    for x in range(x0 + 1, x0 + w - 1, 4):
        s.c.vline(x, y0 + 1, y0 + h - 2, ORANGE, 2)
    # 座椅和靠背：深棕的皮面，一道道缝线
    seat = s.box(56, 68, 8, 5, 8, LEATHER, 2)
    x0, y0, w, h = seat["top"]
    for y in range(y0 + 1, y0 + h - 1, 3):
        s.c.hline(x0 + 1, x0 + w - 2, y, LEATHER, 4)
    back = s.box(88, 68, 8, 11, 2, LEATHER, 2)
    for key in ("front", "back"):
        x0, y0, w, h = back[key]
        for y in range(y0 + 2, y0 + h - 1, 3):
            s.c.hline(x0 + 1, x0 + w - 2, y, LEATHER, 4)
    # 防滚架：石墨色的管子，朝上的一面亮
    for (u, v, w, h, d) in ((92, 44, 2, 15, 2), (0, 86, 2, 2, 13), (32, 86, 18, 2, 2)):
        for rect in s.box(u, v, w, h, d, GRAPHITE, 3, bevel=False).values():
            s.row(rect, 0, GRAPHITE, 5)
    # 挡泥板：米白，外沿一圈橙
    fender = s.box(72, 86, 5, 2, 12, WHITE, 5)
    for key in SIDES:
        s.row(fender[key], fender[key][3] - 1, ORANGE, 3)
    # 轮子
    s.wheel(0, 44, 4, 12, 12)
    # 激光炮塔：石墨色的底座，顶上两格橙；炮管上一圈圈散热片，炮口青光
    turret = s.box(0, 104, 4, 3, 4, GRAPHITE, 3)
    x0, y0, w, h = turret["top"]
    s.c.hline(x0 + 1, x0 + 2, y0 + 1, ORANGE, 4)
    barrel = s.box(16, 104, 2, 2, 6, GRAPHITE, 2, bevel=False)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = barrel[key]
        for x in range(x0, x0 + w, 2):
            s.c.vline(x, y0, y0 + h - 1, STEEL, 4)
    s.glow(barrel["front"], CYAN_GLOW, 5)
    # 大灯、天线顶上的红色信号灯
    light = s.box(32, 104, 3, 2, 1, GRAPHITE, 2, bevel=False)
    s.lamp(light["front"])
    antenna = s.box(40, 104, 1, 10, 1, STEEL, 4, bevel=False)
    for key in SIDES:
        x0, y0, w, h = antenna[key]
        s.c.set(x0, y0, RED_GLOW, 4)
        s.light(x0, y0, 230)
    s.c.set(antenna["top"][0], antenna["top"][1], RED_GLOW, 5)
    s.light(antenna["top"][0], antenna["top"][1])
    return s


# ---------------------------------------------------------------- 悬浮车（HoverCarModel.java，128×128）

@skin_recipes("hover_car", refs=("entity/minecart",))
def hover_car_skin() -> Skin:
    s = Skin(128, 128, 2611)
    # 车身：侧面米白，一道橙条，最下面一行石墨；前面一道格栅；顶上两道橙色赛车条
    hull = s.box(0, 0, 24, 6, 32, WHITE, 5, brushed=8)
    for key in SIDES:
        s.stripe(hull[key], 2)
        s.row(hull[key], 5, GRAPHITE, 3)
    s.grille((hull["front"][0] + 6, hull["front"][1] + 1, 12, 1), GRAPHITE, 3)
    for c0 in (6, 16):
        s.col(hull["top"], c0, ORANGE, 4)
        s.col(hull["top"], c0 + 1, ORANGE, 3)
    s.seam(hull["top"], 10, WHITE, 5)
    # 车底的裙板：石墨色，侧面最下面一行是青色的灯带（一段一段）
    skirt = s.box(0, 38, 26, 3, 28, GRAPHITE, 3)
    for key in SIDES:
        x0, y0, w, h = skirt[key]
        for x in range(x0, x0 + w):
            if (x - x0) % 4 != 3:
                s.c.set(x, y0 + h - 1, CYAN_GLOW, 4)
                s.light(x, y0 + h - 1, 190)
    s.fill(skirt["bottom"], GRAPHITE, 1)
    # 车头：米白，正面一道格栅和橙色的边
    nose = s.box(0, 69, 20, 5, 6, WHITE, 5)
    s.grille((nose["front"][0] + 4, nose["front"][1] + 1, 12, 2), GRAPHITE, 3)
    for key in ("front", "left", "right"):
        s.row(nose[key], 4, ORANGE, 3)
    # 座椅、靠背（靠背顶上一行橙色头枕）
    seat = s.box(52, 69, 8, 2, 7, LEATHER, 2)
    x0, y0, w, h = seat["top"]
    for y in range(y0 + 1, y0 + h, 3):
        s.c.hline(x0 + 1, x0 + w - 2, y, LEATHER, 4)
    back = s.box(82, 69, 8, 7, 2, LEATHER, 2)
    for key in SIDES:
        s.row(back[key], 0, ORANGE, 4)
    s.row(back["top"], 0, ORANGE, 4)
    s.row(back["top"], 1, ORANGE, 3)
    # 挡风玻璃
    ws = faces(0, 80, 18, 6, 1)
    for rect in ws.values():
        s.fill(rect, GRAPHITE, 2)
    s.glass(ws["front"])
    s.glass(ws["back"])
    # 翼舱：橙色，前面一个进气口，后面一个小喷口发青光
    pod = s.box(38, 80, 3, 3, 12, ORANGE, 3)
    s.fill(pod["front"], GRAPHITE, 1)
    s.glow(pod["back"], CYAN_GLOW, 4, 220)
    for key in ("left", "right"):
        s.row(pod[key], 2, ORANGE, 1)
    # 车尾的喷口罩：石墨色，后面一圈深色
    housing = s.box(68, 80, 14, 6, 4, GRAPHITE, 3)
    x0, y0, w, h = housing["back"]
    s.c.rect(x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2, GRAPHITE, 0)
    # 两个喷口：后面亮青色，中间最亮
    nozzle = s.box(0, 95, 4, 4, 1, GRAPHITE, 3)
    x0, y0, w, h = nozzle["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            s.c.set(x, y, CYAN_GLOW, 3 if edge else 5)
            s.light(x, y, 200 if edge else 255)
    # 推力垫：底面亮青色，中间最亮
    padf = s.box(10, 95, 6, 1, 6, GRAPHITE, 3)
    x0, y0, w, h = padf["bottom"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - (x0 + (w - 1) / 2), y - (y0 + (h - 1) / 2)) / (w / 2)
            s.c.set(x, y, CYAN_GLOW, 5 if d < 0.45 else 4 if d < 0.85 else 3)
            s.light(x, y, max(130, int(255 * (1 - d * 0.45))))
    # 采矿激光：底座、炮管，炮口青光
    s.box(34, 95, 4, 3, 4, GRAPHITE, 3)
    barrel = s.box(50, 95, 2, 2, 6, STEEL, 3, bevel=False)
    s.glow(barrel["front"], CYAN_GLOW, 5)
    # 大灯
    lamp = s.box(66, 95, 3, 2, 1, GRAPHITE, 2, bevel=False)
    s.lamp(lamp["front"])
    return s


# ---------------------------------------------------------------- 重型车（HeavyRoverModel.java，256×128）

@skin_recipes("heavy_rover", refs=("entity/minecart",))
def heavy_rover_skin() -> Skin:
    s = Skin(256, 128, 2612)
    # 底盘：石墨色，侧面一排螺栓，顶上花纹钢板，前面警示条
    chassis = s.box(0, 0, 26, 6, 52, GRAPHITE, 3)
    for key in ("left", "right"):
        x0, y0, w, h = chassis[key]
        for x in range(x0 + 2, x0 + w - 1, 4):
            s.c.set(x, y0 + 1, STEEL, 5)
            s.c.set(x + 1, y0 + 2, GRAPHITE, 1)
        s.row(chassis[key], h - 1, GRAPHITE, 1)
    x0, y0, w, h = chassis["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x + 2 * y) % 5 == 0:
                s.c.set(x, y, GRAPHITE, 5)
            elif (x + 2 * y) % 5 == 1:
                s.c.set(x, y, GRAPHITE, 2)
    s.hazard((chassis["front"][0], chassis["front"][1] + 1, chassis["front"][2], 2))
    # 驾驶室：米白；正面一整块风挡、下面格栅；侧面一扇窗、一道橙条、车门缝
    cab = s.box(156, 0, 24, 27, 16, WHITE, 5, brushed=10)
    front = cab["front"]
    s.glass((front[0] + 2, front[1] + 3, 20, 9))
    s.grille((front[0] + 5, front[1] + 18, 14, 4), GRAPHITE, 3)
    s.stripe(front, 14)
    # 风挡底下的仪表灯（隔着玻璃看得见）
    for x in range(front[0] + 4, front[0] + 20, 3):
        s.c.set(x, front[1] + 10, GREEN_GLOW, 4)
        s.light(x, front[1] + 10, 200)
    # 车头两盏雾灯
    for x in (front[0] + 2, front[0] + 21):
        s.c.set(x, front[1] + 23, WHITE_GLOW, 5)
        s.light(x, front[1] + 23)
    for key in ("left", "right"):
        x0, y0, w, h = cab[key]
        # "right" 面的右边是车头，"left" 面的左边是车头：窗户靠车头那一边
        wx = x0 + w - 11 if key == "right" else x0 + 2
        s.glass((wx, y0 + 3, 9, 8))
        s.stripe(cab[key], 14)
        dx = x0 + w - 12 if key == "right" else x0 + 11
        s.c.vline(dx, y0 + 2, y0 + 13, WHITE, 3)
        s.c.vline(dx, y0 + 16, y0 + 23, WHITE, 3)
        s.c.set(dx + (-2 if key == "right" else 2), y0 + 17, GRAPHITE, 2)    # 门把手
    for c0 in range(3, 21, 4):
        s.col(cab["top"], c0, WHITE, 3)
    # 货舱：橙色集装箱，侧面竖着的瓦楞，后面两扇门
    cargo = s.box(0, 58, 24, 18, 18, ORANGE, 3)
    for key in SIDES:
        x0, y0, w, h = cargo[key]
        for x in range(x0 + 1, x0 + w - 2, 2):
            s.c.vline(x, y0 + 1, y0 + h - 2, ORANGE, 2)
            s.c.vline(x + 1, y0 + 1, y0 + h - 2, ORANGE, 4)
        s.row(cargo[key], 0, ORANGE, 5)
        s.row(cargo[key], h - 1, ORANGE, 1)
    x0, y0, w, h = cargo["back"]
    s.c.vline(x0 + w // 2, y0 + 1, y0 + h - 2, ORANGE, 0)
    for x in (x0 + w // 2 - 2, x0 + w // 2 + 2):
        s.c.vline(x, y0 + 5, y0 + h - 5, STEEL, 4)
    s.fill(cargo["top"], ORANGE, 3)
    # 精炼机：钢，顶上一圈警示条；侧面散热格栅和一根管子；背面一扇观察窗
    refiner = s.box(84, 58, 22, 20, 15, STEEL, 4, brushed=8)
    for key in SIDES:
        x0, y0, w, h = refiner[key]
        s.hazard((x0, y0, w, 2))
    for key in ("left", "right"):
        x0, y0, w, h = refiner[key]
        s.grille((x0 + 3, y0 + 5, w - 6, 8), STEEL, 4)
        s.row(refiner[key], 15, STEEL, 2)
        s.row(refiner[key], 16, STEEL, 6)
    x0, y0, w, h = refiner["back"]
    s.c.recess(x0 + 6, y0 + 6, x0 + 15, y0 + 11, GRAPHITE, 2)
    s.c.rect(x0 + 7, y0 + 7, x0 + 14, y0 + 10, RUST, 1)
    x0, y0, w, h = refiner["top"]
    s.c.recess(x0 + 4, y0 + 3, x0 + 11, y0 + h - 4, STEEL, 4)
    # 观察窗里的炉火：发光
    furnace = s.box(158, 58, 10, 6, 1, GRAPHITE, 3)
    x0, y0, w, h = furnace["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = (y - y0) / max(1, h - 1)
            lv = 2 + round(t * 2.4 + s.rng.uniform(-0.6, 0.6))
            s.c.set(x, y, ORANGE_GLOW, max(2, min(5, lv)))
            s.light(x, y, int(200 + 55 * t))
    # 烟囱：顶上一圈黑
    chimney = s.box(180, 58, 3, 8, 3, GRAPHITE, 3)
    s.fill(chimney["top"], GRAPHITE, 0)
    # 灯排：正面一排大灯
    bar = s.box(192, 58, 20, 2, 3, GRAPHITE, 3)
    x0, y0, w, h = bar["front"]
    for x in range(x0, x0 + w):
        if (x - x0) % 4 in (1, 2):
            for y in range(y0, y0 + h):
                s.c.set(x, y, WHITE_GLOW, 5)
                s.light(x, y)
    # 八个轮子共用一块
    s.wheel(0, 94, 5, 10, 10)
    # 采矿激光
    s.box(30, 94, 5, 3, 5, GRAPHITE, 3)
    barrel = s.box(50, 94, 2, 2, 7, STEEL, 3, bevel=False)
    s.glow(barrel["front"], CYAN_GLOW, 5)
    # 大灯、天线（顶上一盏红灯）、前保险杠（警示条）
    lamp = s.box(68, 94, 4, 3, 1, GRAPHITE, 2, bevel=False)
    s.lamp(lamp["front"])
    antenna = s.box(78, 94, 1, 10, 1, STEEL, 4, bevel=False)
    s.glow(antenna["top"], RED_GLOW, 5)
    for key in SIDES:
        x0, y0, w, h = antenna[key]
        s.c.set(x0, y0, RED_GLOW, 4)
        s.light(x0, y0)
    bumper = s.box(82, 94, 24, 3, 2, GRAPHITE, 3)
    s.hazard(bumper["front"])
    return s


# ---------------------------------------------------------------- 机甲（ExoMechModel.java，128×128）

@skin_recipes("exo_mech", refs=("entity/iron_golem/iron_golem",))
def exo_mech_skin() -> Skin:
    s = Skin(128, 128, 2613)
    # 胸口：米白装甲板，正面玻璃下面两道橙色 V 形条，侧面一道橙条和散热口；背面是石墨色的机械
    torso = s.box(0, 0, 20, 23, 18, WHITE, 5, brushed=10)
    x0, y0, w, h = torso["front"]
    for i in range(6):
        for dy in (0, 1):
            lv = 4 if dy == 0 else 3
            s.c.set(x0 + 4 + i, y0 + 15 + i // 2 + dy, ORANGE, lv)
            s.c.set(x0 + w - 5 - i, y0 + 15 + i // 2 + dy, ORANGE, lv)
    s.seam(torso["front"], 20, WHITE, 5)
    for key in ("left", "right"):
        s.stripe(torso[key], 4)
        x0, y0, w, h = torso[key]
        s.c.recess(x0 + 4, y0 + 9, x0 + w - 5, y0 + 16, WHITE, 4)
        s.grille((x0 + 5, y0 + 10, w - 10, 6), GRAPHITE, 3)
    s.fill(torso["back"], GRAPHITE, 3)
    x0, y0, w, h = torso["back"]
    for y in range(y0 + 2, y0 + h - 2, 3):
        s.c.hline(x0 + 2, x0 + w - 3, y, GRAPHITE, 1)
        s.c.hline(x0 + 2, x0 + w - 3, y + 1, GRAPHITE, 4)
    for c0 in (4, 15):
        s.col(torso["top"], c0, WHITE, 3)
    # 驾驶舱玻璃：深色，发光层上是淡淡的平视仪表（一个十字）
    canopy = faces(76, 0, 14, 10, 1)
    for rect in canopy.values():
        s.fill(rect, GRAPHITE, 2)
    s.glass(canopy["front"])
    x0, y0, w, h = canopy["front"]
    for x in range(x0 + 3, x0 + w - 3):
        s.light(x, y0 + h // 2, 70, CYAN_GLOW, 4)
    for y in range(y0 + 2, y0 + h - 2):
        s.light(x0 + w // 2, y, 70, CYAN_GLOW, 4)
    # 头：米白，正面一道青色的面罩，中间两格最亮
    head = s.box(76, 11, 8, 4, 9, WHITE, 5)
    x0, y0, w, h = head["front"]
    for x in range(x0, x0 + w):
        mid = x in (x0 + 3, x0 + 4)
        for y in (y0 + 1, y0 + 2):
            s.c.set(x, y, CYAN_GLOW, 5 if mid else 4)
            s.light(x, y, 255 if mid else 200)
    for key in ("left", "right"):
        s.row(head[key], 3, ORANGE, 3)
    # 骨盆：石墨色
    pelvis = s.box(0, 41, 16, 4, 12, GRAPHITE, 3)
    s.row(pelvis["front"], 1, GRAPHITE, 5)
    # 喷气包：钢，顶上橙色的盖，一圈圈加强筋
    pack = s.box(56, 41, 5, 15, 5, STEEL, 4)
    for key in SIDES:
        s.row(pack[key], 0, ORANGE, 4)
        s.row(pack[key], 1, ORANGE, 3)
        x0, y0, w, h = pack[key]
        for y in range(y0 + 4, y0 + h - 2, 3):
            s.c.hline(x0, x0 + w - 1, y, STEEL, 2)
    s.fill(pack["top"], ORANGE, 4)
    # 喷口：底面发橙光
    nozzle = s.box(76, 41, 3, 2, 3, GRAPHITE, 2)
    s.glow(nozzle["bottom"], ORANGE_GLOW, 4)
    # 大腿：米白装甲，上下两头石墨色的关节，中间一道橙
    thigh = s.box(0, 61, 5, 12, 5, WHITE, 5)
    for key in SIDES:
        s.row(thigh[key], 0, GRAPHITE, 3)
        s.row(thigh[key], 11, GRAPHITE, 2)
        s.row(thigh[key], 5, ORANGE, 3)
    # 小腿：石墨色的液压杆，钢的活塞杆
    shin = s.box(20, 61, 4, 10, 4, GRAPHITE, 3)
    for key in SIDES:
        s.col(shin[key], 1, STEEL, 5)
        s.col(shin[key], 2, STEEL, 3)
    # 脚：石墨色，底面是防滑纹，脚尖一道警示条
    foot = s.box(36, 61, 7, 4, 9, GRAPHITE, 3)
    s.tread(foot["bottom"])
    s.hazard((foot["front"][0], foot["front"][1] + 2, foot["front"][2], 2))
    # 护膝：橙色
    s.box(68, 61, 5, 4, 2, ORANGE, 3)
    # 肩甲：橙色，下沿一道深色
    shoulder = s.box(0, 78, 7, 5, 7, ORANGE, 3)
    for key in SIDES:
        s.row(shoulder[key], 4, ORANGE, 1)
    # 上臂：石墨色
    s.box(28, 78, 5, 10, 5, GRAPHITE, 3)
    # 炮身：米白，一道橙条，前面深色
    cannon = s.box(48, 78, 7, 7, 14, WHITE, 5)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = cannon[key]
        if w > h:
            s.c.hline(x0, x0 + w - 1, y0 + h // 2, ORANGE, 3)
        else:
            s.c.vline(x0 + w // 2, y0, y0 + h - 1, ORANGE, 3)
    s.fill(cannon["front"], GRAPHITE, 1)
    # 炮管：炮口发橙光
    barrel = s.box(90, 78, 3, 3, 8, GRAPHITE, 4, bevel=False)
    s.glow(barrel["front"], ORANGE_GLOW, 5)
    # 扫描仪：米白，正面一块青色的镜头；扫描盘发青光的格子
    scanner = s.box(0, 99, 5, 5, 10, WHITE, 5)
    s.glow((scanner["front"][0] + 1, scanner["front"][1] + 1, 3, 3), CYAN_GLOW, 4, 230)
    s.c.set(scanner["front"][0] + 1, scanner["front"][1] + 1, CYAN_GLOW, 5)
    s.light(scanner["front"][0] + 1, scanner["front"][1] + 1)
    dish = s.box(30, 99, 6, 6, 1, STEEL, 4)
    x0, y0, w, h = dish["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x - x0) % 2 == 0 or (y - y0) % 2 == 0:
                s.c.set(x, y, CYAN_GLOW, 3)
                s.light(x, y, 140)
    # 天线：顶上一盏红灯
    antenna = s.box(44, 99, 1, 5, 1, GRAPHITE, 3, bevel=False)
    s.glow(antenna["top"], RED_GLOW, 5)
    return s


# ---------------------------------------------------------------- 潜艇和鱼雷（SubmarineModel.java 256×128、TorpedoRenderer.java 32×32）

def hull_side(s: Skin, rect, stripe_at: int):
    """潜艇的侧面：上白、腰线橙、下深灰蓝、最底下两行黑色防污漆；一道道竖的舱板缝，顶上一排铆钉。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        i = y - y0
        if i < stripe_at:
            r, lv = WHITE, 5 if i > 0 else 6
        elif i < stripe_at + 2:
            r, lv = ORANGE, 4 if i == stripe_at else 3
        elif i < h - 2:
            r, lv = NAVY, 4 if i == stripe_at + 2 else 3
        else:
            r, lv = RUBBER, 1
        s.c.hline(x0, x0 + w - 1, y, r, lv)
    for x in range(x0 + 6, x0 + w - 2, 10):
        for y in range(y0 + 1, y0 + h - 2):
            s.c.shift(x, y, -2)
            s.c.shift(x + 1, y, 1)
    for x in range(x0 + 2, x0 + w - 1, 3):
        if h > 2:
            s.c.set(x, y0 + 1, WHITE, 3)


def anti_slip(s: Skin, rect):
    """白色甲板上的防滑纹：错开排的两格短横。"""
    x0, y0, w, h = rect
    s.fill(rect, WHITE, 5)
    for y in range(y0, y0 + h):
        if (y - y0) % 3:
            continue
        off = 2 * (((y - y0) // 3) % 2)
        for x in range(x0, x0 + w):
            if (x - x0 + off) % 4 in (0, 1):
                s.c.set(x, y, WHITE, 4)


def glass_canopy(s: Skin, rect):
    """驾驶舱的玻璃：淡青、半透明，斜着几道亮的反光。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            k = (x - y) % 11
            if k in (0, 1):
                s.c.set(x, y, GLASS, 5 if k == 0 else 4, alpha=150)
            else:
                s.c.set(x, y, GLASS, 3, alpha=100)


@skin_recipes("submarine", refs=("entity/boat/oak",))
def submarine_skin() -> Skin:
    s = Skin(256, 128, 2026)
    # ---- 艇身主体 (0,0) 20×14×40
    hull = faces(0, 0, 20, 14, 40)
    for rect in hull.values():
        s.panel(rect, WHITE, 5)
    for key in ("left", "right"):
        hull_side(s, hull[key], 6)
        # 侧面靠前一个舷窗，靠后一块编号牌。"right" 面（-x）右边是艇头，"left" 面（+x）左边是艇头
        x0, y0, w, h = hull[key]
        front = x0 + w - 6 if key == "right" else x0 + 5
        back = x0 + 6 if key == "right" else x0 + w - 14
        s.c.hline(back, back + 7, y0 + 3, GRAPHITE, 2)
        s.c.hline(back + 1, back + 3, y0 + 3, WHITE, 3)
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                s.c.set(front + dx, y0 + 3 + dy, STEEL, 3 if dx + dy > 0 else 5)
        s.c.set(front, y0 + 3, GLASS, 4)
        s.c.set(front + 1, y0 + 4, SCREEN, 2)
    for key in ("front", "back"):
        hull_side(s, hull[key], 6)
    # 顶面：白色甲板和防滑纹（前半截被驾驶舱底座盖住）
    anti_slip(s, hull["top"])
    s.fill(hull["bottom"], RUBBER, 1)
    # ---- 上甲板 (0,54) 16×3×20：白色，防滑纹，中间一个舱口盖
    deck = s.box(0, 54, 16, 3, 20, WHITE, 5)
    for key in SIDES:
        s.row(deck[key], deck[key][3] - 1, WHITE, 3)
    anti_slip(s, deck["top"])
    x0, y0, w, h = deck["top"]
    s.c.panel(x0 + 5, y0 + 8, x0 + 10, y0 + 12, STEEL, 4)
    s.c.set(x0 + 7, y0 + 10, STEEL, 2)
    s.c.set(x0 + 8, y0 + 10, STEEL, 2)
    # ---- 艇头 (72,54) 16×10×4 和尖头 (72,68) 10×6×2：白色，中间一圈橙；尖头正面一块深色的声呐罩
    nose = s.box(72, 54, 16, 10, 4, WHITE, 5)
    for key in ("left", "right", "front"):
        hull_side(s, nose[key], 2)
    tip = s.box(72, 68, 10, 6, 2, WHITE, 5)
    x0, y0, w, h = tip["front"]
    s.c.panel(x0, y0, x0 + w - 1, y0 + h - 1, STEEL, 4)
    s.c.rect(x0 + 2, y0 + 1, x0 + w - 3, y0 + h - 2, NAVY, 1)
    s.c.hline(x0 + 2, x0 + w - 3, y0 + 1, NAVY, 2)
    # ---- 尾锥 (0,77) 14×10×5
    tail = s.box(0, 77, 14, 10, 5, WHITE, 5)
    for key in SIDES:
        hull_side(s, tail[key], 2)
    # ---- 螺旋桨：桨毂 (96,68) 钢，桨叶 (110,68)、(38,77) 铜色，前缘受光
    s.box(96, 68, 3, 3, 4, STEEL, 4)
    for (u, v, w, h, d) in ((110, 68, 1, 12, 1), (38, 77, 12, 1, 1)):
        for rect in s.box(u, v, w, h, d, COPPER, 4, bevel=False).values():
            s.c.set(rect[0], rect[1], COPPER, 6)
    # ---- 导流罩：上下 (38,79)、两侧 (78,79)，橙色，朝前的一行亮
    for (u, v, w, h, d) in ((38, 79, 16, 1, 4), (78, 79, 1, 12, 4)):
        for rect in s.box(u, v, w, h, d, ORANGE, 3, bevel=False).values():
            s.c.hline(rect[0], rect[0] + rect[2] - 1, rect[1], ORANGE, 4)
    # ---- 尾翼：竖鳍、下鳍、水平鳍，白色，后缘橙色
    for (u, v, w, h, d) in ((88, 81, 1, 6, 10), (110, 81, 1, 2, 7), (38, 84, 5, 1, 7)):
        for key, rect in s.box(u, v, w, h, d, WHITE, 5, bevel=False).items():
            x0, y0, rw, rh = rect
            s.c.vline(x0 + rw - 1, y0, y0 + rh - 1, ORANGE, 3)
    # 竖鳍顶上一盏红色航行灯
    fin = faces(88, 81, 1, 6, 10)
    for key in ("left", "right"):
        x0, y0, w, h = fin[key]
        s.c.set(x0 + w - 2, y0, RED_GLOW, 5)
        s.light(x0 + w - 2, y0)
    # ---- 侧推舱 (0,92) 4×6×14：深灰蓝，一道橙线，尾部喷口发青光，前面进水口
    pod = s.box(0, 92, 4, 6, 14, NAVY, 3)
    for key in ("left", "right", "top"):
        x0, y0, w, h = pod[key]
        if key == "top":
            s.c.vline(x0 + w // 2, y0, y0 + h - 1, ORANGE, 3)
        else:
            s.c.hline(x0, x0 + w - 1, y0 + 1, ORANGE, 3)
    x0, y0, w, h = pod["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if edge:
                s.c.set(x, y, GRAPHITE, 2)
            else:
                s.c.set(x, y, CYAN_GLOW, 4)
                s.light(x, y, 220)
    x0, y0, w, h = pod["front"]
    s.c.rect(x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2, GRAPHITE, 0)
    # ---- 大灯 (36,92) 3×3×1
    lamp = s.box(36, 92, 3, 3, 1, GRAPHITE, 2)
    s.lamp(lamp["front"])
    # ---- 机械臂 (44,92) 2×2×7：钢，一节一节的关节，爪子是黄铜色
    arm = s.box(44, 92, 2, 2, 7, STEEL, 4, bevel=False)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = arm[key]
        if w > h:
            for x in range(x0 + 2, x0 + w, 3):
                s.c.vline(x, y0, y0 + h - 1, STEEL, 2)
        else:
            for y in range(y0 + 2, y0 + h, 3):
                s.c.hline(x0, x0 + w - 1, y, STEEL, 2)
    s.fill(arm["front"], BRASS, 4)
    # ---- 驾驶舱玻璃 (128,0)：所有玻璃块共用这一片
    for rect in faces(128, 0, 16, 5, 20).values():
        glass_canopy(s, rect)
    # ---- 光柱：车身上全透明，只在发光层里亮，越远越淡；两头的面不画
    for (u, v, w, h, d, strength) in ((192, 68, 8, 6, 12, 0.34), (128, 68, 14, 10, 18, 0.22), (128, 26, 22, 16, 26, 0.13)):
        for key, rect in faces(u, v, w, h, d).items():
            x0, y0, rw, rh = rect
            for y in range(y0, y0 + rh):
                for x in range(x0, x0 + rw):
                    s.c.clear(x, y)
            if key in ("front", "back"):
                continue
            for y in range(y0, y0 + rh):
                for x in range(x0, x0 + rw):
                    a = int(255 * strength * (1 + s.rng.uniform(-0.08, 0.08)))
                    s.g.set(x, y, WHITE_GLOW, 5, alpha=a)
    # ---- 驾驶舱底座 (128,96) 17×1×21：顶面隔着玻璃看得见舱里。第一行是舱尾，越往下越靠前：
    # 一圈框、深色的地板、中间的座椅、最前面的仪表台（一排屏幕和指示灯）
    collar = s.box(128, 96, 17, 1, 21, GRAPHITE, 3)
    x0, y0, w, h = collar["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            s.c.set(x, y, GRAPHITE, 4 if edge else (2 if (x + y) % 2 else 1))
    s.c.rect(x0 + 2, y0 + 16, x0 + w - 3, y0 + 19, SCREEN, 1)
    for x in range(x0 + 3, x0 + w - 3, 3):
        s.c.set(x, y0 + 18, CYAN_GLOW, 4)
        s.light(x, y0 + 18, 230)
        s.c.set(x + 1, y0 + 17, AMBER_GLOW, 4)
        s.light(x + 1, y0 + 17, 180)
    for y in range(y0 + 6, y0 + 12):
        for x in range(x0 + 5, x0 + 12):
            s.c.set(x, y, LEATHER, 3 if y > y0 + 6 else 1)
    return s


@skin_recipes("torpedo")
def torpedo_skin() -> Skin:
    """鱼雷 32×32：钢的弹体 (0,0) 3×3×10，一道黄色、一道深色的环带；红色弹头 (0,13)；尾部喷口 (6,13) 发青光；
    十字尾翼 (0,17)、(18,17)。"""
    s = Skin(32, 32, 7)
    body = s.box(0, 0, 3, 3, 10, STEEL, 5, bevel=False)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = body[key]
        if w > h:
            s.c.vline(x0 + 2, y0, y0 + h - 1, HAZARD, 4)
            s.c.vline(x0 + 3, y0, y0 + h - 1, GRAPHITE, 1)
        s.c.hline(x0, x0 + w - 1, y0, STEEL, 6)
    s.box(0, 13, 2, 2, 1, RED, 4, bevel=False)
    nozzle = s.box(6, 13, 2, 2, 2, GRAPHITE, 2, bevel=False)
    s.glow(nozzle["back"], CYAN_GLOW, 5)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = nozzle[key]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                s.light(x, y, 110, CYAN_GLOW, 4)
    s.box(0, 17, 7, 1, 2, STEEL, 3, bevel=False)
    s.box(18, 17, 1, 7, 2, STEEL, 3, bevel=False)
    return s


# ---------------------------------------------------------------- 宇航服的盔甲层（原版 64×32 人形盔甲布局）

def suit_panel(c: Canvas, rect, lv: int = 5):
    """宇航服的一块白色面板：上面一行最亮，下面一行和右边一列暗一级（接缝）。"""
    x0, y0, w, h = rect
    c.rect(x0, y0, x0 + w - 1, y0 + h - 1, WHITE, lv)
    c.hline(x0, x0 + w - 1, y0, WHITE, lv + 1)
    c.hline(x0, x0 + w - 1, y0 + h - 1, WHITE, lv - 2)
    c.vline(x0 + w - 1, y0 + 1, y0 + h - 2, WHITE, lv - 1)


def suit_box(c: Canvas, u, v, w, h, d, painters: dict):
    """按原版的长方体展开填六个面；painters[面](c, x, y, 宽, 高)，缺的面画成白色面板，None 是不画。"""
    for name, rect in faces(u, v, w, h, d).items():
        fn = painters.get(name, "panel")
        if fn == "panel":
            suit_panel(c, rect)
        elif fn is not None:
            fn(c, *rect)


def helmet_front(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    # 面罩：第 2–5 行，左右各留一格；左上是天空的反光，往右下越来越深
    for j in range(4):
        for i in range(w - 2):
            t = (i / (w - 3) + j / 3) / 2
            c.set(x + 1 + i, y + 2 + j, SCREEN, 4 if t < 0.2 else 3 if t < 0.45 else 2 if t < 0.75 else 1)
    c.set(x + 2, y + 2, GLASS, 5)
    c.set(x + 3, y + 2, GLASS, 4)
    c.set(x + 2, y + 3, GLASS, 4)
    c.hline(x + 1, x + w - 2, y + 6, WHITE, 3)
    c.set(x + w // 2 - 1, y, ORANGE, 4)
    c.set(x + w // 2, y, ORANGE, 3)


def helmet_side(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    # 耳侧的通讯模块：一块凸起的灰盒子，中间一盏绿灯
    c.panel(x + 2, y + 3, x + 4, y + 5, STEEL, 4)
    c.set(x + 3, y + 4, GREEN_GLOW, 4)


def helmet_top(c, x, y, w, h):
    suit_panel(c, (x, y, w, h), 5)
    c.vline(x + w // 2 - 1, y, y + h - 1, ORANGE, 4)
    c.vline(x + w // 2, y, y + h - 1, ORANGE, 3)


def helmet_back(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    c.panel(x + 2, y + 4, x + w - 3, y + 6, STEEL, 4)
    c.set(x + 3, y + 5, GRAPHITE, 2)
    c.set(x + w - 4, y + 5, GRAPHITE, 2)


def helmet_bottom(c, x, y, w, h):
    c.rect(x, y, x + w - 1, y + h - 1, GRAPHITE, 2)
    c.frame(x, y, x + w - 1, y + h - 1, GRAPHITE, 3)


def chest_front(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    # 领口、胸前的控制面板（绿灯、青灯）、右胸一道竖的橙色、腰上一道橙
    c.hline(x + 2, x + w - 3, y, WHITE, 3)
    c.recess(x + 1, y + 2, x + 5, y + 6, STEEL, 4)
    c.rect(x + 2, y + 3, x + 4, y + 5, GRAPHITE, 2)
    c.set(x + 2, y + 4, GREEN_GLOW, 4)
    c.set(x + 3, y + 4, CYAN_GLOW, 4)
    c.set(x + 4, y + 3, AMBER_GLOW, 3)
    c.hline(x, x + w - 1, y + 8, ORANGE, 4)
    c.hline(x, x + w - 1, y + 9, ORANGE, 2)
    for j in range(y, y + 8):
        c.set(x + w - 2, j, ORANGE, 4 if (j - y) % 3 else 2)
    c.hline(x, x + w - 1, y + h - 1, GRAPHITE, 3)


def chest_back(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    # 生命维持背包：钢的箱体，两只橙色的气罐（圆柱，左亮右暗），中间一盏绿灯
    c.panel(x + 1, y + 1, x + w - 2, y + 8, STEEL, 4)
    for tx in (x + 2, x + w - 4):
        c.cylinder(tx, y + 2, tx + 1, y + 7, ORANGE, 3)
        c.set(tx, y + 2, ORANGE, 5)
    c.set(x + w // 2, y + 3, GREEN_GLOW, 4)
    c.set(x + w // 2 - 1, y + 5, GRAPHITE, 2)
    c.hline(x + 1, x + w - 2, y + 9, GRAPHITE, 2)
    c.hline(x, x + w - 1, y + h - 1, GRAPHITE, 3)


def chest_side(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    c.hline(x, x + w - 1, y + 8, ORANGE, 4)
    c.hline(x, x + w - 1, y + 9, ORANGE, 2)
    c.hline(x, x + w - 1, y + h - 1, GRAPHITE, 3)


def arm_face(c, x, y, w, h):
    suit_panel(c, (x, y, w, h))
    # 上臂的橙色臂章，手肘的关节缝，手腕钢的护腕
    c.hline(x, x + w - 1, y + 2, ORANGE, 4)
    c.hline(x, x + w - 1, y + 5, WHITE, 3)
    c.hline(x, x + w - 1, y + 6, WHITE, 6)
    c.rect(x, y + h - 3, x + w - 1, y + h - 1, STEEL, 4)
    c.hline(x, x + w - 1, y + h - 3, STEEL, 5)
    c.hline(x, x + w - 1, y + h - 1, GRAPHITE, 2)


def boots_face(c, x, y, w, h):
    # 靴子只画腿最下面 5 行，上面透明
    c.rect(x, y + h - 5, x + w - 1, y + h - 1, GRAPHITE, 3)
    c.hline(x, x + w - 1, y + h - 5, WHITE, 6)
    c.hline(x, x + w - 1, y + h - 4, WHITE, 4)
    c.hline(x, x + w - 1, y + h - 3, ORANGE, 4)
    c.hline(x, x + w - 1, y + h - 1, GRAPHITE, 1)


def boots_bottom(c, x, y, w, h):
    c.rect(x, y, x + w - 1, y + h - 1, RUBBER, 1)
    c.hline(x, x + w - 1, y + 1, RUBBER, 2)


def legs_face(c, x, y, w, h, side=False):
    # 护腿画上面 9 行：大腿白色、膝盖钢的护垫，外侧一道橙色
    suit_panel(c, (x, y, w, 9))
    c.rect(x, y + 5, x + w - 1, y + 6, STEEL, 4)
    c.hline(x, x + w - 1, y + 5, STEEL, 5)
    c.hline(x, x + w - 1, y + 6, STEEL, 3)
    if side:
        c.vline(x + w // 2, y, y + 4, ORANGE, 4)
        c.vline(x + w // 2, y + 7, y + 8, ORANGE, 3)


def belt(c, x, y, w, h):
    # 身体的下半截是腰带：钢的带子，中间橙色的扣
    c.rect(x, y + h - 5, x + w - 1, y + h - 1, WHITE, 5)
    c.hline(x, x + w - 1, y + h - 5, STEEL, 5)
    c.hline(x, x + w - 1, y + h - 4, STEEL, 3)
    c.hline(x, x + w - 1, y + h - 1, WHITE, 3)
    c.set(x + w // 2 - 1, y + h - 5, ORANGE, 4)
    c.set(x + w // 2, y + h - 5, ORANGE, 3)


@texture("entity/equipment/humanoid/exosuit", refs=("entity/equipment/humanoid/iron",))
def exosuit_layer(rng):
    # 头盔（头）、胸甲（身体、手臂）、靴子（腿的下半截）
    c = Canvas(64, 32)
    suit_box(c, 0, 0, 8, 8, 8, {"front": helmet_front, "right": helmet_side, "left": helmet_side,
                                "top": helmet_top, "back": helmet_back, "bottom": helmet_bottom})
    suit_box(c, 16, 16, 8, 12, 4, {"front": chest_front, "back": chest_back, "right": chest_side,
                                   "left": chest_side})
    suit_box(c, 40, 16, 4, 12, 4, {k: arm_face for k in SIDES})
    suit_box(c, 0, 16, 4, 12, 4, {**{k: boots_face for k in SIDES}, "top": None, "bottom": boots_bottom})
    return c


@texture("entity/equipment/humanoid_leggings/exosuit", refs=("entity/equipment/humanoid_leggings/iron",))
def exosuit_leggings_layer(rng):
    # 护腿：腰带（身体的下半截）、腿的上半截
    c = Canvas(64, 32)
    suit_box(c, 16, 16, 8, 12, 4, {**{k: belt for k in SIDES}, "top": None, "bottom": None})
    side = lambda c_, x, y, w, h: legs_face(c_, x, y, w, h, True)  # noqa: E731
    suit_box(c, 0, 16, 4, 12, 4, {"front": legs_face, "back": legs_face, "left": side, "right": side,
                                  "top": lambda c_, x, y, w, h: c_.rect(x, y, x + w - 1, y + h - 1, WHITE, 5),
                                  "bottom": None})
    return c
