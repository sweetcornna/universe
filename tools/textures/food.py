"""食物、动物产物、采集物和刷怪蛋的物品贴图（ADR-0031）。

画法照原版的面包、蛋糕、南瓜派、炖菜：暖色，烤过的东西边缘焦一点，顶上一两个高光，最外一圈用材质自己
最深的颜色描边。圆的东西（蛋、果子、面团）按椭球的法线打光，左上亮、右下暗；别的一个像素一个像素地画。

几种器皿各画一次、按菜换色阶：果酱和酱是玻璃罐，汁是一杯，汤是一只木碗，卡士达是白瓷烤盅，果冻是盘子上的
一块方冻，馅饼是圆派（格子皮或者整张皮），挞是小挞，蛋糕是切下来的一角，烤肉是一块浇了酱的肉，防护凝胶是一支
软管。生的和熟的一对用同一个形状，熟的换成烤过的颜色、加烤痕。
"""
from __future__ import annotations

import math

from canvas import LIGHT, Canvas
from color import ramp
from palette import (AMBER, BERRY, BLUE, BONE, BRASS, BREAD, CREAM, CYAN_GLOW, GLASS, GRAPHITE, GREEN,
                     GREEN_GLOW, ICE, LEAF, MAGENTA_GLOW, MOSS, ORANGE, PINK, RED, SAND, TEAL, VIOLET, WHITE, WOOD,
                     YELLOW_GLOW, register)
from recipe import texture

# ---- 这组自己用的色阶：吃的东西要比机器的颜色饱和，亮部少褪一点色
CRUST = register(ramp("food_crust", 70, 0.130, n=7, lo=0.30, hi=0.92, shift=18, peak=0.55, fade=0.35))
STAR = register(ramp("food_star", 82, 0.150, n=7, lo=0.34, hi=0.95, shift=14, peak=0.6, fade=0.3))
SOLAR = register(ramp("food_solar", 36, 0.185, n=7, lo=0.28, hi=0.88, shift=14, peak=0.55, fade=0.3))
GRAVITINO = register(ramp("food_gravitino", 338, 0.190, n=7, lo=0.28, hi=0.90, shift=12, peak=0.55, fade=0.35))
CACTUS = register(ramp("food_cactus", 122, 0.130, n=7, lo=0.30, hi=0.93, shift=16, peak=0.5, fade=0.35))
FROST = register(ramp("food_frost", 232, 0.075, n=7, lo=0.42, hi=0.97, shift=10, peak=0.5, fade=0.4))
MEAT = register(ramp("food_meat", 354, 0.150, n=7, lo=0.26, hi=0.85, shift=12, peak=0.5, fade=0.4))
ROAST = register(ramp("food_roast", 46, 0.105, n=7, lo=0.24, hi=0.80, shift=16, peak=0.5, fade=0.35))
FISH = register(ramp("food_fish", 214, 0.060, n=7, lo=0.30, hi=0.95, shift=10, peak=0.35, fade=0.6))
MOULD = register(ramp("food_mould", 318, 0.055, n=7, lo=0.28, hi=0.82, shift=12, peak=0.5, fade=0.4))
GAMMA = register(ramp("food_gamma", 116, 0.150, n=7, lo=0.30, hi=0.92, shift=14, peak=0.55, fade=0.35))
HIDE = register(ramp("food_hide", 150, 0.045, n=7, lo=0.28, hi=0.80, shift=14, peak=0.5, fade=0.4))
SALMON = register(ramp("food_salmon", 28, 0.120, n=6, lo=0.46, hi=0.93, shift=10, peak=0.5, fade=0.4))
BUTTER = register(ramp("food_butter", 94, 0.115, n=7, lo=0.46, hi=0.97, shift=12, peak=0.55, fade=0.3))


# ---- 成形、打光、手绘

def ell(cx, cy, rx, ry, rz=None, angle=0.0):
    """椭球（俯看是椭圆）：返回 (x, y) -> 高度或 None。angle 是长轴转的角度（度，顺时针）。"""
    rz = min(rx, ry) if rz is None else rz
    ca, sa = math.cos(math.radians(angle)), math.sin(math.radians(angle))

    def h(x, y):
        px, py = x - cx, y - cy
        u = (px * ca + py * sa) / rx
        v = (-px * sa + py * ca) / ry
        d = u * u + v * v
        return rz * math.sqrt(1 - d) if d <= 1 else None
    return h


class Form:
    """几块拼起来的立体（取最高的那块）。法线在连续的高度函数上算，明暗带干净，不出噪点。"""

    def __init__(self, *parts, w=16, h=16):
        self.parts = parts
        self.cells = {(x, y) for y in range(h) for x in range(w) if self.height(x + 0.5, y + 0.5) is not None}

    def height(self, x, y):
        vals = [v for v in (p(x, y) for p in self.parts) if v is not None]
        return max(vals) if vals else None

    def facing(self, x: int, y: int) -> float:
        """这一格的面朝光的程度：平面是 0.53，正对着光是 1。"""
        e = 0.35
        cx, cy = x + 0.5, y + 0.5

        def hh(px, py):
            v = self.height(px, py)
            return 0.0 if v is None else v
        nx = (hh(cx - e, cy) - hh(cx + e, cy)) / (2 * e)
        ny = (hh(cx, cy - e) - hh(cx, cy + e)) / (2 * e)
        n = math.sqrt(nx * nx + ny * ny + 1)
        return (nx * LIGHT[0] + ny * LIGHT[1] + LIGHT[2]) / n

    def __contains__(self, xy):
        return xy in self.cells

    def __iter__(self):
        return iter(self.cells)


def shade(c: Canvas, f: Form, r, base: int, amp: float = 2.0, cells=None, alpha=None, lo: int = 0):
    """按法线打光，一格一格换成色阶的级数；lo 是最暗只到第几级（更暗的留给描边）。"""
    for x, y in (cells if cells is not None else f):
        lv = base + round(amp * (f.facing(x, y) - 0.53) * 2.2)
        c.set(x, y, r, max(lo, lv), alpha=alpha)


def paint(c: Canvas, rows, legend: dict, ox: int = 0, oy: int = 0):
    """手绘：legend 里 "0123456": 色阶 表示这些字符依次是第 0–6 级，"*": (色阶, 级数) 是单个字符。
    "." 和空格不画，"_" 擦掉。手绘的像素都是故意的，检查时不算零碎像素。"""
    lut = {}
    for k, v in legend.items():
        if isinstance(v, tuple):
            lut[k] = v
        else:
            for i, ch in enumerate(k):
                lut[ch] = (v, i)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            if ch == "_":
                c.clear(x + ox, y + oy)
                continue
            r, lv = lut[ch]
            c.set(x + ox, y + oy, r, lv, glint=True)


def steam(c: Canvas, pts, lv: int = 5):
    """几缕半透明的热气。"""
    for x, y in pts:
        c.set(x, y, WHITE, lv, glint=True, alpha=140)


# ---- 生肉和熟肉：一块斜放的肉排，上沿一层脂肪，左下伸出一截骨头
# 数字是肉，a–g 是脂肪，A–G 是骨头

MEAT_GRID = [
    "................",
    ".........11111..",
    ".......1dff5441.",
    "......1dfe555430",
    ".....1dfe5654330",
    "....1dfe55544320",
    "....2fe55444320.",
    "...1fe544433320.",
    "...2e4443332210.",
    "...14433332210..",
    "...1333222210...",
    "...FC122110.....",
    "..FEB0000.......",
    "DFDB............",
    "EEC.............",
    ".DCB............",
]


def meat_chop(cooked: bool) -> Canvas:
    c = Canvas()
    flesh, fat = (ROAST, BREAD) if cooked else (MEAT, CREAM)
    paint(c, MEAT_GRID, {"0123456": flesh, "abcdefg": fat, "ABCDEFG": BONE})
    # 一道细的大理石纹
    for x, y, lv in ((13, 5, 3), (12, 6, 3), (11, 6, 3), (10, 7, 2)):
        c.set(x, y, fat, lv + (0 if cooked else 1), glint=True)
    if cooked:
        # 烤网的两道焦痕，和肉排的长轴垂直
        for x0, y0 in ((6, 4), (9, 2)):
            for i in range(7):
                x, y = x0 + i, y0 + i
                px = c.get(x, y)
                if px and px[0] is not BONE and px[1] > 0:
                    c.set(x, y, ROAST, 1, glint=True)
        c.set(10, 4, ROAST, 5, glint=True)
    return c


@texture("item/alien_meat", refs=("item/beef", "item/porkchop", "item/mutton"))
def alien_meat(rng):
    return meat_chop(False)


@texture("item/cooked_alien_meat", refs=("item/cooked_beef", "item/cooked_porkchop"))
def cooked_alien_meat(rng):
    return meat_chop(True)


# ---- 鱼：头在右上、尾在左下（和原版鳕鱼一个朝向）

# 数字是鱼身（背深腹浅），s 是背上的深色横纹，g 是鳃盖，a–g 是鳍，x 是侧线上发光的点，o 和 w 是眼睛
FISH_GRID = [
    "................",
    "...........1110.",
    ".........112230.",
    "......cd1223wo40",
    ".....bc12232g550",
    "......1s23xg550.",
    ".....12s344360..",
    ".....1s2x44550..",
    "....12s344560...",
    "....1s2x4550....",
    "...12s34550b....",
    "d..1s23450ba....",
    "edc12340........",
    ".dc130..........",
    "..cb............",
    "...b............",
]


def fish(cooked: bool) -> Canvas:
    c = Canvas()
    if not cooked:
        paint(c, FISH_GRID, {"0123456": FISH, "abcdefg": TEAL, "s": (FISH, 1), "g": (FISH, 2),
                             "x": (CYAN_GLOW, 4), "o": (GRAPHITE, 0), "w": (WHITE, 6)})
        c.set(10, 2, FISH, 5, glint=True)
        return c
    # 烤熟的：鱼身烤成金黄，背上的横纹烤没了，两道和身体垂直的烤痕
    paint(c, FISH_GRID, {"0123456": CRUST, "abcdefg": ROAST, "s": (CRUST, 3), "g": (CRUST, 2),
                         "x": (CRUST, 4), "o": (BONE, 5), "w": (CRUST, 4)})
    for a0 in (-3, 3):
        for y in range(16):
            x = y + a0
            px = c.get(x, y)
            left, right = c.get(x - 1, y + 1), c.get(x + 1, y - 1)
            if px and px[0] is CRUST and px[1] > 1 and left and right:
                c.set(x, y, ROAST, 2, glint=True)
    c.set(10, 2, CRUST, 6, glint=True)
    return c


@texture("item/alien_fish", refs=("item/cod", "item/salmon", "item/tropical_fish"))
def alien_fish(rng):
    return fish(False)


@texture("item/cooked_alien_fish", refs=("item/cooked_cod", "item/cooked_salmon"))
def cooked_alien_fish(rng):
    return fish(True)


# ---- 蛋

def egg_form(cx=8.0, cy=9.0, rx=4.9, ry=6.2, rz=3.4) -> Form:
    """上窄下宽的蛋形。"""
    def h(x, y):
        py = (y - cy) / ry
        r = rx * (1 - 0.2 * max(0.0, -py))
        u = (x - cx) / r
        d = u * u + py * py
        return rz * math.sqrt(1 - d) if d <= 1 else None
    return Form(h)


def spots(c: Canvas, f: Form, r, pts, d: int = 0):
    """蛋壳上的斑：给圆心和半径，保留壳本身的明暗换色阶。"""
    for x, y in f:
        for sx, sy, sr in pts:
            if (x + 0.5 - sx) ** 2 + (y + 0.5 - sy) ** 2 < sr * sr:
                c.recolor(x, y, r, d)
                c.glints.add((x, y))
                break


@texture("item/alien_egg", refs=("item/egg", "item/turtle_egg", "item/blue_egg"))
def alien_egg(rng):
    c = Canvas()
    f = egg_form()
    shade(c, f, VIOLET, 5, 1.5, lo=2)
    spots(c, f, TEAL, ((6.2, 11.4, 1.3), (10.6, 9.4, 1.0), (9.4, 13.4, 0.9), (11.2, 12.6, 0.7), (7.4, 7.6, 0.7)))
    c.outline(lit=2, shadow=1)
    c.set(6, 5, WHITE, 6, glint=True)
    c.set(5, 6, VIOLET, 6, glint=True)
    return c


@texture("item/cooked_alien_egg", refs=("item/baked_potato",))
def cooked_alien_egg(rng):
    c = Canvas()
    f = egg_form(cy=9.6, ry=5.8)
    shade(c, f, CRUST, 4, 1.6, lo=2)
    spots(c, f, ROAST, ((6.2, 11.4, 1.2), (10.6, 10.0, 1.0), (9.4, 13.6, 0.9), (11.2, 12.6, 0.7), (7.4, 8.2, 0.7)), d=-1)
    # 壳烤裂了一道
    for x, y in ((9, 5), (10, 6), (9, 7), (10, 8)):
        c.set(x, y, ROAST, 1, glint=True)
    c.outline()
    c.set(6, 6, CRUST, 6, glint=True)
    steam(c, ((7, 1), (6, 2), (7, 3), (10, 0), (10, 2), (11, 1)))
    return c


# ---- 刷怪蛋：蛋上带着这种生物的特征（26.x 原版的刷怪蛋也是各画各的）

def spawn_egg(base, spot, pts, lv=4) -> Canvas:
    c = Canvas()
    f = egg_form(cy=9.2, rx=5.2, ry=6.4)
    shade(c, f, base, lv, 1.6, lo=2)
    spots(c, f, spot, pts)
    c.outline()
    return c


@texture("item/alien_creature_spawn_egg", refs=("item/frog_spawn_egg", "item/axolotl_spawn_egg"))
def alien_creature_spawn_egg(rng):
    c = spawn_egg(TEAL, VIOLET, ((5.4, 10.6, 1.6), (10.6, 7.6, 1.3), (9.4, 12.4, 1.4), (11.4, 13.0, 0.9), (6.6, 6.6, 0.8)))
    # 头顶两根带眼的触角
    paint(c, [
        "...fd....df.....",
        "...dc....cd.....",
        ".....b..b.......",
    ], {"abcdefg": VIOLET})
    c.set(6, 5, TEAL, 6, glint=True)
    return c


@texture("item/alien_flyer_spawn_egg", refs=("item/bat_spawn_egg", "item/parrot_spawn_egg"))
def alien_flyer_spawn_egg(rng):
    c = spawn_egg(ORANGE, BLUE, ((8.4, 13.4, 1.5), (11.4, 9.4, 1.1), (6.4, 10.4, 1.0), (9.4, 7.4, 0.8)))
    wing = [
        "e...............",
        "fc..............",
        "edb.............",
        "dcb.............",
        ".cb.............",
        "..a.............",
    ]
    paint(c, wing, {"abcdefg": BLUE}, 0, 5)
    paint(c, [row[::-1] for row in wing], {"abcdefg": BLUE}, 0, 5)
    c.set(6, 5, ORANGE, 6, glint=True)
    return c


@texture("item/aquatic_creature_spawn_egg", refs=("item/cod_spawn_egg", "item/glow_squid_spawn_egg"))
def aquatic_creature_spawn_egg(rng):
    c = spawn_egg(BLUE, CYAN_GLOW, ((6.4, 9.4, 0.9), (10.4, 7.4, 0.8), (9.4, 11.4, 0.9), (7.4, 12.4, 0.7), (11.4, 10.4, 0.7)), lv=3)
    # 两侧的鳍，下半截泡在水里：一道浪花，水里透出蛋的影子
    paint(c, [
        ".c............c.",
        ".db..........bd.",
        ".cb..........bc.",
    ], {"abcdefg": TEAL}, 0, 8)
    paint(c, [
        "..defeedefeeed..",
        "..PRSRRSRRRSQO..",
        "...PQRQQRQQQO...",
        "....OOOOOOOO....",
    ], {"abcdefg": GLASS, "OPQRS": TEAL}, 0, 12)
    c.set(6, 5, BLUE, 6, glint=True)
    return c


# ---- 罐子：果酱、酱、糊。玻璃罐，盖子按内容换颜色，中间一圈纸标签
# 数字是罐里的东西，A–G 是盖子，a–g 是玻璃，k/l/m 是标签，* 是标签上的小图

JAR = [
    ".....BDDDDB.....",
    "....BDFDDDBA....",
    "....ABBBBBAA....",
    "....bc5443ca....",
    "...bg6554432a...",
    "...bgkllllk2a...",
    "...bgkl**lk2a...",
    "...bekkkkk22a...",
    "...be5443322a...",
    "...be4433222a...",
    "...be4332222a...",
    "...bc3322222a...",
    "....baaaaaaa....",
]


def jar(content, lid) -> Canvas:
    c = Canvas()
    paint(c, JAR, {"0123456": content, "ABCDEFG": lid, "abcdefg": GLASS, "k": (CREAM, 2), "l": (CREAM, 4),
                   "*": (content, 3)}, 0, 2)
    return c


@texture("item/star_jam", refs=("item/honey_bottle",))
def star_jam(rng):
    return jar(STAR, BRASS)


@texture("item/solar_sauce", refs=("item/honey_bottle",))
def solar_sauce(rng):
    return jar(SOLAR, RED)


@texture("item/mould_paste", refs=("item/honey_bottle",))
def mould_paste(rng):
    return jar(MOULD, WOOD)


# ---- 一杯仙人掌汁：玻璃杯、吸管，汁里有几颗果肉
# 数字是汁，a–g 是玻璃，r/s/t 是吸管

CUP = [
    "..........ts....",
    ".........rs.....",
    ".........r......",
    "....bfffrffb....",
    "....bg66s65a....",
    "....bg55443a....",
    "....bf54643a....",
    "....bf44332a....",
    "....bf46332a....",
    "....be43322a....",
    "....be33222a....",
    "....bd33221a....",
    "....bd32211a....",
    "....bcdddcba....",
    ".....aaaaaa.....",
]


@texture("item/cactus_juice", refs=("item/honey_bottle", "item/potion"))
def cactus_juice(rng):
    c = Canvas()
    paint(c, CUP, {"0123456": CACTUS, "abcdefg": GLASS, "r": (RED, 4), "s": (RED, 2), "t": (RED, 5)}, 0, 1)
    return c


# ---- 防护凝胶：一支小玻璃管，白色的螺帽上一圈危害色，标签中间一道同样的颜色
# 数字是凝胶，A–G 是螺帽，a–g 是玻璃，l/m 是标签

VIAL = [
    "......BDDB......",
    ".....BDEDBA.....",
    ".....344321.....",
    ".....ABBBAA.....",
    ".....geeeca.....",
    ".....g6543a.....",
    ".....g5432a.....",
    ".....elllma.....",
    ".....e2222a.....",
    ".....elllma.....",
    ".....e4321a.....",
    ".....e3221a.....",
    ".....c2211a.....",
    "......caaa......",
]


def vial(gel) -> Canvas:
    c = Canvas()
    paint(c, VIAL, {"0123456": gel, "ABCDEFG": WHITE, "abcdefg": GLASS, "l": (WHITE, 5), "m": (WHITE, 3)}, 0, 1)
    return c


@texture("item/cold_gel", refs=("item/potion", "item/glass_bottle"))
def cold_gel(rng):
    return vial(BLUE)


@texture("item/heat_gel")
def heat_gel(rng):
    return vial(ORANGE)


@texture("item/radiation_gel")
def radiation_gel(rng):
    return vial(GAMMA)


@texture("item/toxin_gel")
def toxin_gel(rng):
    return vial(VIOLET)


# ---- 汤：原版炖菜那样的木碗，碗里一层汤，漂着几块料，冒热气
# 数字是汤，A–G 是木碗，p/q/r 是汤里的料

BOWL = [
    "....BCDDDDCB....",
    "..BC45566554CB..",
    ".BC3445555443CA.",
    ".BD2334444332DA.",
    ".BFFEEEDDDCCCBA.",
    "..BEDDDCCCBBBA..",
    "..BDDCCCCBBBAA..",
    "...BDCCCBBBAA...",
    "....BCCBBBAA....",
    ".....ACCBBA.....",
]


def bowl(soup, bits) -> Canvas:
    """bits: [(x, y, 色阶, 级数)]，漂在汤面上的料。"""
    c = Canvas()
    paint(c, BOWL, {"0123456": soup, "ABCDEFG": WOOD}, 0, 5)
    for x, y, r, lv in bits:
        c.set(x, y, r, lv, glint=True)
    steam(c, ((6, 1), (5, 2), (6, 3), (9, 0), (10, 1), (9, 2)))
    return c


@texture("item/root_soup", refs=("item/mushroom_stew", "item/beetroot_soup", "item/rabbit_stew"))
def root_soup(rng):
    # 伽马根切成的段，几点发光的根须
    return bowl(GAMMA, ((4, 7, GAMMA, 2), (5, 7, GAMMA, 3), (9, 6, GAMMA, 2), (10, 6, GAMMA, 3), (7, 8, GAMMA, 2),
                        (6, 6, GREEN_GLOW, 4), (11, 7, GREEN_GLOW, 3)))


@texture("item/marrow_soup", refs=("item/mushroom_stew", "item/rabbit_stew"))
def marrow_soup(rng):
    # 奶白的浓汤，几块烤过的髓球，一点绿叶
    return bowl(CREAM, ((4, 7, BREAD, 3), (5, 7, BREAD, 4), (9, 6, BREAD, 3), (10, 7, BREAD, 2), (7, 8, LEAF, 3),
                        (8, 8, LEAF, 4), (6, 6, BREAD, 4)))


# ---- 卡士达：白瓷烤盅，盅壁一道道竖的褶，上面一层亮黄的卡士达
# 数字是卡士达，A–G 是瓷

RAMEKIN = [
    "....BDDDDDDB....",
    "..BD45566554DB..",
    "..BC45566654CA..",
    "..BC34455443CA..",
    "..BFFFFFFEEEDA..",
    "..BGEGEFDFDECA..",
    "..BFEFEEDEDDCA..",
    "..AEDEDDCDCCBA..",
    "...AAAAAAAAAA...",
]


def ramekin() -> Canvas:
    c = Canvas()
    paint(c, RAMEKIN, {"0123456": STAR, "ABCDEFG": WHITE}, 0, 4)
    return c


@texture("item/custard", refs=("item/pumpkin_pie",))
def custard(rng):
    c = ramekin()
    # 表面烤出的几块焦糖色
    for x, y, lv in ((5, 7, 3), (6, 7, 3), (10, 6, 3), (9, 7, 2)):
        c.set(x, y, BREAD, lv, glint=True)
    c.set(5, 6, STAR, 6, glint=True)
    return c


@texture("item/star_custard")
def star_custard(rng):
    c = ramekin()
    # 正中一勺星莓果酱
    paint(c, [
        "......fe........",
        ".....fedc.......",
        "......cb........",
    ], {"abcdefg": ORANGE}, 1, 3)
    c.set(5, 6, STAR, 6, glint=True)
    return c


# ---- 馅饼：一只整的圆派，边上捏出花边。格子皮的透出馅，整张皮的开几道气孔
# 数字是馅，A–G 是派皮

LATTICE_PIE = [
    "....BEDEDEDB....",
    "..BEE55E54E4EB..",
    ".BEFFFFFFFFFFDA.",
    ".BD5E45E44E33CA.",
    ".BEFFFFEEEDDDCA.",
    ".BD4D34D33D2DBA.",
    "..BEDEDEDEDCBA..",
    "..ACDCDCCBCBAA..",
    "...ABBBBBBBBA...",
]

CRUST_PIE = [
    "....BEFFFEDB....",
    "..BEFFGFFEEEDB..",
    ".BEFFFF2E2EDDCA.",
    ".BEFFFEE1EEDDCA.",
    ".BEEFFE2E2EDDCA.",
    ".BDEEEEEDDDDCCA.",
    "..BEDEDEDEDCBA..",
    "..ACDCDCCBCBAA..",
    "...ABBBBBBBBA...",
]


def pie(filling, lattice: bool, deep: int = 0) -> Canvas:
    """deep：馅的颜色压暗几级，和格子皮拉开（馅和皮同一个色系的时候）。"""
    c = Canvas()
    paint(c, LATTICE_PIE if lattice else CRUST_PIE, {"0123456": filling, "ABCDEFG": CRUST}, 0, 5)
    if deep:
        for y in range(16):
            for x in range(16):
                px = c.get(x, y)
                if px and px[0] is filling:
                    c.shift(x, y, -deep)
    return c


@texture("item/star_pie", refs=("item/pumpkin_pie",))
def star_pie(rng):
    c = pie(STAR, True, deep=2)
    return c


@texture("item/solar_pie", refs=("item/pumpkin_pie",))
def solar_pie(rng):
    c = pie(SOLAR, True)
    c.set(5, 6, SOLAR, 6, glint=True)
    return c


@texture("item/meat_pie", refs=("item/pumpkin_pie",))
def meat_pie(rng):
    c = pie(ROAST, False)
    steam(c, ((8, 4), (7, 3), (8, 2), (9, 1)))
    return c


@texture("item/fish_pie", refs=("item/pumpkin_pie",))
def fish_pie(rng):
    # 派皮上躺着一条烤过的小银鱼
    c = pie(CREAM, False)
    for y in range(5, 10):
        for x in range(5, 12):
            px = c.get(x, y)
            if px and px[0] is CREAM:
                c.set(x, y, CRUST, 4, glint=True)
    paint(c, [
        "....o...ooo.....",
        "....oo.o5665p...",
        ".....oo55446ep..",
        "....oo.p3332p...",
        "....p...ppp.....",
    ], {"0123456": FISH, "o": (FISH, 2), "p": (FISH, 1), "e": (GRAPHITE, 0)}, 0, 6)
    return c


# ---- 挞：小圆挞，挞皮一圈竖褶，挞心亮亮的
# 数字是挞心，A–G 是挞皮

TART = [
    "...BEDEDEDEDB...",
    "..BE45566554DB..",
    ".BE3456666543DB.",
    ".BD3455665543CA.",
    ".BD2344554432CA.",
    ".BEDEDEDEDEDCBA.",
    "..BCBCBCBCBCBA..",
    "...AAAAAAAAAA...",
]


def tart(filling) -> Canvas:
    c = Canvas()
    paint(c, TART, {"0123456": filling, "ABCDEFG": CRUST}, 0, 5)
    return c


@texture("item/egg_tart", refs=("item/pumpkin_pie", "item/cookie"))
def egg_tart(rng):
    c = tart(STAR)
    # 葡式蛋挞那样烤出的几块焦斑
    for x, y, lv in ((5, 8, 2), (6, 8, 3), (10, 7, 2), (11, 8, 2), (8, 9, 2)):
        c.set(x, y, BREAD, lv, glint=True)
    c.set(5, 7, STAR, 6, glint=True)
    return c


@texture("item/frost_tart")
def frost_tart(rng):
    c = tart(FROST)
    for x, y in ((5, 8), (10, 8), (8, 9), (11, 7)):
        c.set(x, y, WHITE, 6, glint=True)
    c.set(6, 7, FROST, 6, glint=True)
    return c


# ---- 蛋糕：切下来的一角，前面的切面露出海绵和果酱的夹层，右边是抹了奶油的外侧
# 数字是海绵，A–G 是奶油，j/k 是夹层的果酱

CAKE = [
    "...........BDDA.",
    "........BDEEFDA.",
    ".....BDEEFFFEDA.",
    "..BDEEFFFFFFECA.",
    ".BEFFFFFFFFFEDA.",
    ".B5555555544DCA.",
    ".BkkkkkkkkkjDCA.",
    ".B4444444443DCA.",
    ".BjjjjjjjjjjDCA.",
    ".B3333333332CBA.",
    "..AAAAAAAAAAAA..",
]


def cake(jam) -> Canvas:
    c = Canvas()
    paint(c, CAKE, {"0123456": BREAD, "ABCDEFG": CREAM, "j": (jam, 2), "k": (jam, 4)}, 0, 3)
    return c


@texture("item/alien_cake", refs=("item/cake",))
def alien_cake(rng):
    c = cake(BERRY)
    # 奶油顶上一颗浆果
    paint(c, [
        "........ed......",
        ".......edc......",
        "........b.......",
    ], {"abcdefg": BERRY}, 2, 2)
    return c


@texture("item/star_cake", refs=("item/cake",))
def star_cake(rng):
    c = cake(STAR)
    # 奶油顶上一排星莓
    for x, y in ((4, 5), (8, 4), (11, 3)):
        c.set(x, y, STAR, 6, glint=True)
        c.set(x + 1, y, STAR, 4, glint=True)
        c.set(x, y + 1, STAR, 4, glint=True)
        c.set(x + 1, y + 1, STAR, 2, glint=True)
    return c


@texture("item/cake_base", refs=("item/bread", "item/cake"))
def cake_base(rng):
    # 还没抹奶油的圆海绵坯：顶上烤得金黄、有气孔，侧面颜色深一些
    c = Canvas()
    paint(c, [
        "....BCDDDDCB....",
        "..BD45566554DB..",
        ".BD4556665543CA.",
        ".BC3445555443CA.",
        ".BEDDDDDDCCCCBA.",
        ".BDCDCCCCBCBBBA.",
        ".BDCCCCCBBBBBAA.",
        "..ABBBBBBBBAA...",
    ], {"0123456": BREAD, "ABCDEFG": CRUST}, 0, 6)
    for x, y in ((5, 7), (9, 8), (11, 7), (7, 9), (4, 8)):
        c.shift(x, y, -1)
    c.set(5, 8, BREAD, 6, glint=True)
    return c


# ---- 果冻：盘子上一块模子扣出来的果冻，半透明，有一道亮的反光
# 数字是果冻，A–G 是盘子

JELLY = [
    "......2332......",
    ".....256652.....",
    "....25w66542....",
    "....2w566541....",
    "...2455565431...",
    "...2445554431...",
    "...2344444321...",
    "..233444333211..",
    ".BDEEEEEEEDDCCA.",
    "..ACCCCCCCBBAA..",
]


def jelly(r) -> Canvas:
    c = Canvas()
    paint(c, JELLY, {"0123456": r, "ABCDEFG": WHITE, "w": (WHITE, 6)}, 0, 4)
    # 模子压出的竖棱：一道亮一道暗
    for y in range(6, 12):
        for x in (6, 9):
            px = c.get(x, y)
            if px and px[0] is r:
                c.shift(x, y, 1 if x == 6 else -1)
    for (x, y), px in [((x, y), c.get(x, y)) for y in range(16) for x in range(16)]:
        if px and px[0] is r:
            c.opacity(x, y, 220)
    return c


@texture("item/gravitino_jelly", refs=("item/slime_ball",))
def gravitino_jelly(rng):
    return jelly(GRAVITINO)


@texture("item/cactus_jelly", refs=("item/slime_ball",))
def cactus_jelly(rng):
    return jelly(CACTUS)


# ---- 霜冻冰淇淋：华夫蛋卷筒上一球淡蓝的冰淇淋，几点冰晶
# 数字是冰淇淋，A–G 是蛋卷

ICE_CREAM = [
    ".....234432.....",
    "....24566542....",
    "...2w56666542...",
    "...2456665443...",
    "...2345554432...",
    "...1233443221...",
    "....BEDEDEDB....",
    "....BDCEDCDA....",
    ".....BEDCDA.....",
    ".....BCEDBA.....",
    "......BDCA......",
    "......BCBA......",
    ".......BA.......",
]


@texture("item/frost_ice_cream", refs=("item/cookie", "item/snowball"))
def frost_ice_cream(rng):
    c = Canvas()
    paint(c, ICE_CREAM, {"0123456": FROST, "ABCDEFG": CRUST, "w": (WHITE, 6)}, 0, 2)
    # 化了一点，顺着卷筒往下滴
    c.set(5, 8, FROST, 3, glint=True)
    c.set(10, 8, FROST, 2, glint=True)
    c.set(10, 9, FROST, 1, glint=True)
    for x, y in ((6, 3), (10, 4), (8, 6)):
        c.set(x, y, WHITE, 6, glint=True)
    return c


# ---- 绒毛糖：插在小棍上的一团粉色棉花糖

@texture("item/fluff_candy", refs=("item/sugar",))
def fluff_candy(rng):
    c = Canvas()
    f = Form(ell(8.0, 6.0, 4.6, 4.0, 3.6), ell(5.0, 7.4, 3.0, 2.6, 2.6), ell(11.0, 7.2, 3.0, 2.8, 2.6),
             ell(8.0, 3.4, 3.0, 2.4, 2.4))
    shade(c, f, PINK, 4, 1.8, lo=2)
    # 一簇一簇的糖丝：亮的一侧成团地更亮
    for x, y in ((6, 3), (7, 3), (4, 6), (5, 6), (9, 5), (10, 5), (7, 7)):
        if (x, y) in f:
            c.shift(x, y, 1)
            c.glints.add((x, y))
    for x, y in ((10, 8), (11, 8), (8, 9), (6, 9), (12, 6)):
        if (x, y) in f:
            c.shift(x, y, -1)
            c.glints.add((x, y))
    c.outline(lit=2, shadow=1)
    paint(c, [
        "........e.......",
        "........d.......",
        "........d.......",
        "........c.......",
        "........c.......",
        "........b.......",
        "........b.......",
    ], {"abcdefg": BONE}, 0, 9)
    return c


# ---- 面粉、面团、黄油、酥皮

@texture("item/flour", refs=("item/sugar", "item/wheat"))
def flour(rng):
    # 一小堆面粉，堆尖压出一个小坑，旁边撒了几粒
    c = Canvas()

    def heap(x, y):
        dx, dy = (x - 8.0) / 6.4, (y - 12.0) / 2.6
        r = math.sqrt(dx * dx + max(0.0, dy) ** 2)
        top = 12.0 - 7.2 * (1 - min(1.0, abs(x - 8.0) / 6.4)) ** 0.8
        if y < top or r > 1:
            return None
        return 4.0 * (1 - abs(x - 8.0) / 6.4) + (y - top) * 0.15
    f = Form(heap)
    shade(c, f, CREAM, 3, 1.8, lo=1)
    c.outline(lit=1, shadow=0)
    for x, y in ((7, 6), (8, 6)):
        c.shift(x, y, -2)
    c.set(6, 7, CREAM, 5, glint=True)
    for x, y in ((2, 14), (14, 13), (13, 15)):
        c.set(x, y, CREAM, 3, glint=True)
    return c


@texture("item/dough", refs=("item/bread",))
def dough(rng):
    # 一团揉好的面团，有点塌，顶上一道折痕，撒了点干粉
    c = Canvas()
    f = Form(ell(8.0, 9.4, 6.0, 4.6, 3.6))
    shade(c, f, BREAD, 5, 1.6, lo=3)
    c.outline(lit=3, shadow=2)
    for x, y in ((5, 8), (6, 7), (7, 7), (8, 7), (9, 8)):
        c.shift(x, y, -1)
        c.glints.add((x, y))
    for x, y in ((4, 7), (10, 6), (6, 10)):
        c.set(x, y, WHITE, 6, glint=True)
    return c


@texture("item/butter", refs=("item/gold_ingot",))
def butter(rng):
    # 一块切开的黄油搁在油纸上：顶面最亮，正面次之，右侧面最暗
    c = Canvas()
    paint(c, [
        ".....25555552...",
        "....2566666652..",
        "...255555555432.",
        "...244444444322.",
        "...244444444322.",
        "...244444444321.",
        "...234444444321.",
        "...123333333211.",
        ".BDEEEEEEEEEEEDB",
        "..ACCCCCCCCCCBA.",
    ], {"0123456": BUTTER, "ABCDEFG": WHITE}, 0, 4)
    c.set(5, 5, BUTTER, 6, glint=True)
    return c


# ---- 可颂和引力果丹麦酥

@texture("item/pastry", refs=("item/bread", "item/cookie"))
def pastry(rng):
    # 可颂：五节弯成月牙，每一节自己打光，外面一节压着里面一节的地方是一道暗缝
    segs = [ell(8.0, 8.0, 3.0, 3.9, 3.6), ell(4.8, 8.9, 2.6, 3.2, 3.0), ell(11.2, 8.9, 2.6, 3.2, 3.0),
            ell(2.3, 10.9, 1.8, 2.1, 2.0), ell(13.7, 10.9, 1.8, 2.1, 2.0)]
    inner = {1: 0, 2: 0, 3: 1, 4: 2}  # 每一节挨着的、更靠中间的那一节
    c = Canvas()
    f = Form(*segs)
    owner = {(x, y): max(range(len(segs)), key=lambda i: segs[i](x + 0.5, y + 0.5) or -1) for x, y in f}
    shade(c, f, CRUST, 4, 1.8, lo=2)
    for (x, y), i in owner.items():
        if i in inner and inner[i] in (owner.get((x - 1, y)), owner.get((x + 1, y))):
            c.shift(x, y, -2)
            c.glints.add((x, y))
    c.outline()
    c.set(7, 5, CRUST, 6, glint=True)
    c.set(4, 7, CRUST, 6, glint=True)
    return c


@texture("item/gravitino_pastry", refs=("item/cookie",))
def gravitino_pastry(rng):
    # 圆的丹麦酥：一圈起酥的边，中间一窝亮紫红的引力果酱
    c = Canvas()
    f = Form(ell(8.0, 9.0, 6.4, 5.0, 2.8))
    shade(c, f, CRUST, 4, 1.8, lo=2)
    # 起酥的层：几道弧形的暗纹
    for x, y in f:
        r = math.hypot((x + 0.5 - 8.0) / 6.4, (y + 0.5 - 9.0) / 5.0)
        if 0.78 < r < 0.86:
            c.shift(x, y, -1)
            c.glints.add((x, y))
    c.outline()
    j = Form(ell(8.0, 8.4, 3.4, 2.6, 1.2))
    shade(c, j, GRAVITINO, 3, 2.0, lo=1)
    for x, y in j:
        c.glints.add((x, y))
    c.set(6, 7, GRAVITINO, 6, glint=True)
    c.set(7, 7, MAGENTA_GLOW, 5, glint=True)
    return c


# ---- 辣烤肉串、红烧肉

@texture("item/spicy_roast", refs=("item/cooked_beef", "item/cooked_porkchop"))
def spicy_roast(rng):
    # 一串烤肉：木签斜穿过三块肉，肉上刷了红亮的辣酱，撒着辣椒碎和葱花
    c = Canvas()
    paint(c, [
        "..............c.",
        ".............cb.",
    ], {"abcdefg": WOOD}, 0, 0)
    paint(c, [
        ".c..............",
        "cb..............",
        "b...............",
    ], {"abcdefg": WOOD}, 0, 13)
    for cx, cy in ((4.6, 11.4), (8.0, 8.0), (11.4, 4.6)):
        f = Form(ell(cx, cy, 3.0, 2.6, 2.6, angle=-45))
        shade(c, f, ROAST, 3, 1.8, lo=1)
        for x, y in f:
            if (x + 0.5 - cx) + (y + 0.5 - cy) < 0.5:
                c.recolor(x, y, SOLAR, 0)
            c.glints.add((x, y))
    c.outline()
    for x, y in ((3, 10), (8, 7), (10, 3), (5, 12), (12, 5)):
        c.set(x, y, RED, 1, glint=True)
    for x, y in ((7, 6), (11, 4), (4, 9)):
        c.set(x, y, LEAF, 5, glint=True)
    for x, y in ((3, 9), (7, 5)):
        c.set(x, y, WHITE, 6, glint=True)
    return c


# 红烧肉的一块：顶上是油亮的肉皮，切面一层肥一层瘦
PORK = [
    ".3443.",
    "345542",
    "ffffg1",
    "322210",
    "gggff0",
    "211100",
]


@texture("item/braised_meat", refs=("item/cooked_beef", "item/rabbit_stew"))
def braised_meat(rng):
    # 盘子里三块红烧肉，浇着深色的汤汁
    c = Canvas()
    paint(c, [
        "..BEEEEEEEEEEB..",
        ".BEFFFFFFFFFFEA.",
        ".AEEEEEEEEEEEBA.",
        "..ABBBBBBBBBBA..",
    ], {"ABCDEFG": WHITE}, 0, 11)
    for x, y, lv in ((2, 12, 1), (3, 13, 2), (12, 12, 1), (13, 12, 2), (11, 13, 1)):
        c.set(x, y, ROAST, lv, glint=True)
    for ox, oy in ((2, 4), (8, 3), (5, 8)):
        paint(c, PORK, {"0123456": ROAST, "f": (BREAD, 5), "g": (BREAD, 3)}, ox, oy)
        c.set(ox + 2, oy + 1, WHITE, 6, glint=True)
    c.set(10, 2, LEAF, 4, glint=True)
    c.set(11, 2, LEAF, 3, glint=True)
    steam(c, ((5, 2), (4, 1), (5, 0), (12, 1), (13, 0)))
    return c


@texture("item/roasted_marrow", refs=("item/baked_potato",))
def roasted_marrow(rng):
    # 烤过的髓球：一大一小两个球，烤得金黄，有几块焦斑，冒热气
    c = Canvas()
    f = Form(ell(7.2, 10.0, 4.8, 4.4, 4.0), ell(11.4, 6.6, 2.6, 2.4, 2.4))
    shade(c, f, BREAD, 4, 1.8, lo=2)
    for x, y in ((8, 11), (9, 11), (5, 12), (10, 9), (12, 7), (6, 8)):
        if (x, y) in f:
            c.recolor(x, y, ROAST, -1)
            c.glints.add((x, y))
    c.outline()
    c.set(5, 8, BREAD, 6, glint=True)
    c.set(10, 5, BREAD, 6, glint=True)
    steam(c, ((5, 3), (4, 4), (5, 5), (8, 2), (9, 3)))
    return c


# ---- 海带脆片、海味卷、海带囊

@texture("item/kelp_crisps", refs=("item/dried_kelp", "item/kelp"))
def kelp_crisps(rng):
    # 叠在一起的三片烤干的海带，边缘不齐，撒了盐
    c = Canvas()
    paint(c, [
        "......12221.....",
        "....1234443210..",
        "...123455443210.",
        "..1234544332210.",
        "..13443332210...",
        "..123322110.....",
    ], {"0123456": MOSS}, 2, 3)
    paint(c, [
        ".1222211........",
        "123444332110....",
        "1345554432210...",
        "1234443322110...",
        ".12332211100....",
    ], {"0123456": MOSS}, 2, 8)
    for x, y in ((6, 4), (9, 5), (4, 10), (8, 10), (11, 6), (6, 11)):
        c.set(x, y, WHITE, 6, glint=True)
    return c


@texture("item/seafood_roll", refs=("item/dried_kelp", "item/cooked_salmon"))
def seafood_roll(rng):
    # 切开的两块海味卷：外面一圈海带，里面一圈米饭似的白色籽，正中粉色的鱼肉；后面还剩一截整卷
    c = Canvas()
    paint(c, [
        ".....12333321...",
        "....1234444321..",
        "....1233333321..",
        ".....11111111...",
    ], {"0123456": MOSS}, 2, 2)
    for cx, cy in ((5.0, 10.0), (11.0, 10.4)):
        for x, y in c.disc_cells(cx, cy, 3.0):
            r = math.hypot(x - cx, y - cy)
            if r > 2.4:
                c.set(x, y, MOSS, 1 if x + y > cx + cy else 2)
            elif r > 1.3:
                c.set(x, y, WHITE, 5 if x + y < cx + cy else 4)
            else:
                c.set(x, y, SALMON, 3 if x + y < cx + cy else 2)
            c.glints.add((x, y))
        c.set(round(cx) - 1, round(cy) - 1, SALMON, 5, glint=True)
    c.outline()
    return c


@texture("item/kelp_pod", refs=("item/glow_berries",))
def kelp_pod(rng):
    # 巨藻的气囊：半透明的琥珀色圆囊，里面透出一点光，连着一截柄和叶片根
    c = Canvas()
    paint(c, [
        "....12..........",
        "..123...........",
        ".1232...........",
        "2232............",
        ".21.............",
    ], {"0123456": MOSS}, 2, 10)
    f = Form(ell(9.4, 7.2, 4.6, 4.8, 4.2))
    shade(c, f, AMBER, 4, 1.8, lo=2, alpha=225)
    for x, y in Form(ell(9.0, 7.0, 2.0, 2.2)):
        c.recolor(x, y, AMBER, 1)
    c.outline()
    for x, y in f:
        c.opacity(x, y, 225 if c.level(x, y) > 1 else 255)
    c.set(7, 4, WHITE, 6, glint=True)
    c.set(8, 5, AMBER, 6, glint=True)
    return c


# ---- 饲料：麻布小袋，扎口的绳子，口上冒出一把颗粒

SACK = [
    "....1........1..",
    "...14222222221..",
    "....dddddddde...",
    "....1344444321..",
    "...134555544321.",
    "...1345454443210",
    "..13454545443210",
    "..13455544433210",
    "..13445454433210",
    "..12344444332210",
    "...122333322210.",
    "....0000000000..",
]


def feed(pellet) -> Canvas:
    c = Canvas()
    paint(c, SACK, {"0123456": SAND, "d": (WOOD, 3), "e": (WOOD, 1)}, 0, 4)
    # 口上的一把颗粒
    paint(c, [
        ".....d.c.dc.....",
        "....cedcedcd....",
        "....dcbdcbdc....",
    ], {"abcdefg": pellet}, 0, 2)
    return c


@texture("item/herbivore_feed", refs=("item/wheat_seeds", "item/bundle"))
def herbivore_feed(rng):
    return feed(LEAF)


@texture("item/carnivore_feed", refs=("item/wheat_seeds",))
def carnivore_feed(rng):
    return feed(ROAST)


# ---- 异兽皮、异星绒毛、生物胶质

@texture("item/alien_hide", refs=("item/leather", "item/rabbit_hide"))
def alien_hide(rng):
    # 摊开的一张皮：四角是腿皮，边缘不齐；灰绿的底色上几道弯弯的深色条纹
    c = Canvas()
    mask = [
        "................",
        "...###....###...",
        "...##########...",
        "..############..",
        "...##########...",
        "...##########...",
        "....########....",
        "...##########...",
        "...##########...",
        "...##########...",
        "..############..",
        "..############..",
        "..####....####..",
        "..###......###..",
        "................",
    ]
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == "#":
                lv = 4 + (1 if x + y < 11 else 0) - (1 if x + y > 19 else 0)
                # 弯弯的条纹
                if math.sin(x * 0.95 + math.sin(y * 0.7) * 1.6) > 0.72 and 3 < y < 13:
                    lv -= 2
                c.set(x, y, HIDE, lv, glint=True)
    c.outline()
    c.set(5, 3, HIDE, 6, glint=True)
    return c


@texture("item/alien_fluff", refs=("item/white_wool", "item/string"))
def alien_fluff(rng):
    # 蓬松的一团毛：几团叠起来，亮的一侧泛粉，背光一侧是青色
    c = Canvas()
    lobes = [ell(8.0, 6.4, 3.8, 3.4), ell(4.8, 9.0, 3.2, 3.0), ell(11.2, 9.0, 3.2, 3.0),
             ell(6.4, 11.8, 3.0, 2.6), ell(9.8, 11.8, 3.0, 2.6), ell(8.0, 9.4, 3.4, 3.2)]
    f = Form(*lobes)
    shade(c, f, TEAL, 4, 1.8, lo=2)
    for x, y in f:
        top = max(range(len(lobes)), key=lambda i: lobes[i](x + 0.5, y + 0.5) or -1)
        if top in (0, 1):
            c.recolor(x, y, PINK, 1)
        c.glints.add((x, y))
    c.outline(lit=2, shadow=1)
    # 翘出来的几根细毛
    for x, y in ((3, 5), (2, 4), (6, 2), (12, 4), (13, 5)):
        c.set(x, y, PINK, 4, glint=True)
    return c


@texture("item/bio_gel", refs=("item/slime_ball", "item/honeycomb"))
def bio_gel(rng):
    # 半透明的一滴绿色胶质，上面尖下面圆，里面有两颗小气泡
    c = Canvas()

    def drop(x, y):
        px, py = x - 8.0, y - 9.8
        if py >= 0:
            d = (px * px + py * py) / 5.4 ** 2
        else:
            w = 5.4 * (1 + py / 8.4)
            if w <= 0:
                return None
            d = (px / w) ** 2
        return 4.0 * math.sqrt(1 - d) if d < 1 else None
    f = Form(drop)
    shade(c, f, GREEN, 4, 1.8, lo=2, alpha=215)
    c.outline()
    for x, y in f:
        if c.level(x, y) <= 1:
            c.opacity(x, y, 255)
    c.set(6, 8, WHITE, 6, glint=True)
    c.set(6, 9, GREEN, 6, glint=True)
    c.set(7, 7, GREEN, 6, glint=True)
    for x, y in ((10, 11), (9, 13)):
        c.set(x, y, GREEN, 5, glint=True, alpha=230)
    return c


# ---- 资源植物的产物

@texture("item/oxygen", refs=("item/glow_berries", "item/glass_bottle"))
def oxygen(rng):
    # 氧囊草的一串气囊：三个半透明的淡蓝圆囊，各连一小截柄，挂在一根细茎上
    c = Canvas()
    paint(c, [
        "............c...",
        "...........dc...",
        "..........dc....",
        ".........dcb....",
        "..........cb....",
        "...........cb...",
        "............b...",
    ], {"abcdefg": LEAF}, 0, 9)
    bubbles = Form(ell(5.6, 8.0, 3.9, 3.9), ell(11.2, 4.8, 2.7, 2.7), ell(9.0, 12.6, 2.2, 2.2))
    shade(c, bubbles, ICE, 3, 1.8, lo=2, alpha=210)
    for x, y in bubbles:
        out_r = (x + 1, y) not in bubbles or (x, y + 1) not in bubbles
        out_l = (x - 1, y) not in bubbles or (x, y - 1) not in bubbles
        if out_r:
            c.set(x, y, TEAL, 1)
        elif out_l:
            c.set(x, y, TEAL, 3)
        c.glints.add((x, y))
    for x, y in ((3, 6), (4, 5), (10, 3), (8, 11)):
        c.set(x, y, WHITE, 6, glint=True)
    for x, y in ((7, 10), (12, 6)):
        c.set(x, y, ICE, 6, glint=True, alpha=230)
    return c


@texture("item/sodium", refs=("item/sugar", "item/quartz"))
def sodium(rng):
    # 盐晶：一大一小两块立方的石盐，旁边一粒碎晶。顶面最亮，左面次之，右面最暗
    c = Canvas()
    big = [
        "....oo....",
        "..otttto..",
        "ottttwttto",
        "olttttttrp",
        "olllttrrrp",
        "ollllrrrrp",
        "ollllrrrrp",
        "ollllrrrrp",
        ".oollrrpp.",
        "...oopp...",
    ]
    small = [
        "...oo...",
        ".ottwto.",
        "otttttto",
        "olttttrp",
        "ollllrrp",
        "ollllrrp",
        ".oollrp.",
        "...opp..",
    ]
    legend = {"t": (WHITE, 5), "w": (WHITE, 6), "l": (WHITE, 4), "L": (WHITE, 5), "r": (WHITE, 2),
              "o": (WHITE, 1), "p": (WHITE, 0)}
    paint(c, small, legend, 8, 1)
    paint(c, big, legend, 1, 6)
    paint(c, [
        ".tw.",
        "lrrp",
        ".pp.",
    ], legend, 11, 12)
    return c


@texture("item/star_bulb", refs=("item/glow_berries", "item/sweet_berries"))
def star_bulb(rng):
    # 星莓：淡金色的一颗圆果，上下左右四个角亮出星芒，右上一小截梗
    c = Canvas()
    f = Form(ell(7.8, 8.6, 4.4, 4.4))
    shade(c, f, STAR, 4, 1.8, lo=2)
    c.outline()
    paint(c, [
        "........e.......",
        "........c.......",
        "................",
        "................",
        "................",
        "................",
        "ec............ce",
        "................",
        "................",
        "................",
        "................",
        "........c.......",
        "........e.......",
    ], {"abcdefg": YELLOW_GLOW}, 0, 2)
    paint(c, [
        "............b.",
        "...........cb.",
        "..........c...",
    ], {"abcdefg": WOOD}, 0, 2)
    c.set(6, 6, WHITE, 6, glint=True)
    c.set(7, 6, STAR, 6, glint=True)
    c.set(6, 7, STAR, 6, glint=True)
    return c


@texture("item/frost_crystal", refs=("item/amethyst_shard", "item/diamond"))
def frost_crystal(rng):
    # 霜苔结的冰晶：三根长短不一的冰柱，左面受光，右面背光
    c = Canvas()
    paint(c, [
        "......45........",
        ".....4652.......",
        ".....56420......",
        ".....56420......",
        ".....56420....4.",
        "..4..56420...452",
        ".462.5642...4642",
        ".5642564...56420",
        ".5642564..56420.",
        "..5425642564320.",
        "..542564256420..",
        "...4256425642...",
        "...3256425420...",
        "....123332210...",
    ], {"0123456": ICE}, 0, 1)
    c.set(6, 2, WHITE, 6, glint=True)
    c.set(13, 6, WHITE, 6, glint=True)
    return c


@texture("item/solanium", refs=("item/blaze_powder", "item/glow_berries"))
def solanium(rng):
    # 日焰藤的花果：三条火舌往上蹿，边上深红，往里橙，芯是金黄的；底下几片绿萼
    c = Canvas()
    paint(c, [
        "................",
        ".......1........",
        "......121.......",
        "......2321...1..",
        "...1..34321.12..",
        "...21.34432123..",
        "..1321344c43221.",
        "..2432ccdcc4321.",
        "..243cdeedc4321.",
        "..34cdeeedc4321.",
        "..34cdeedcc4321.",
        "...34cddcc4321..",
        "...2344444321...",
        "....LM12221ML...",
        "...LM..LM..ML...",
    ], {"0123456": SOLAR, "c": (YELLOW_GLOW, 3), "d": (YELLOW_GLOW, 4), "e": (YELLOW_GLOW, 5), "L": (LEAF, 2),
        "M": (LEAF, 4)})
    return c


@texture("item/gamma_root", refs=("item/carrot", "item/beetroot"))
def gamma_root(rng):
    # 伽马根：顶上一小撮叶子，根身拧着往左下长，分出一根须，表面几个发光的小瘤
    c = Canvas()
    paint(c, [
        "..........d.e...",
        "..........edc...",
        ".........2442...",
        "........245542..",
        "........2x5531..",
        ".........24431..",
        "........24431...",
        ".......24x31....",
        "......244311....",
        ".....24431......",
        "....24331..2....",
        "...24x3112.1....",
        "...2431..1......",
        "..2431..........",
        ".231............",
        ".1..............",
    ], {"0123456": GAMMA, "abcdefg": LEAF, "x": (GREEN_GLOW, 5)})
    return c


@texture("item/fungal_mould", refs=("item/brown_mushroom", "item/nether_wart"))
def fungal_mould(rng):
    # 毒霉：一团紫灰色的霉块，几个小菌盖鼓起来，上面一层黄绿的孢子粉
    c = Canvas()
    f = Form(ell(8.0, 10.0, 6.0, 3.6, 2.6), ell(5.6, 7.6, 2.6, 2.4), ell(9.8, 6.6, 3.0, 2.8), ell(12.0, 8.8, 2.0, 1.8))
    shade(c, f, MOULD, 4, 1.8, lo=2)
    c.outline()
    for x, y in ((5, 6), (9, 5), (10, 5), (12, 8), (7, 9), (4, 10), (10, 10), (8, 12)):
        if (x, y) in f:
            c.set(x, y, GAMMA, 5 if (x + y) % 2 else 4, glint=True)
    return c


@texture("item/cactus_flesh", refs=("item/melon_slice", "item/cactus"))
def cactus_flesh(rng):
    # 切下来的一片桶形仙人掌：外面一圈带棱的绿皮和黄刺，里面是多汁的淡绿果肉
    c = Canvas()
    cx, cy, rad = 8.0, 3.6, 7.6
    cells = [(x, y) for x, y in c.disc_cells(cx, cy, rad) if y >= 4]
    for x, y in cells:
        r = math.hypot(x - cx, y - cy)
        if r > rad - 1.0:
            c.set(x, y, LEAF, 1 if x >= cx else 2)
        elif r > rad - 2.2:
            c.set(x, y, LEAF, 3 if (x + y) % 3 else 2)
        else:
            c.set(x, y, CACTUS, 6 if y == 4 else (5 if r < 3.4 and x < cx + 1 else 4 if r < 5.2 else 3))
        c.glints.add((x, y))
    # 果肉的纤维：几道从中心放射的浅纹
    for x, y in ((6, 6), (5, 7), (10, 6), (11, 7), (8, 7), (8, 8)):
        c.shift(x, y, 1)
    c.outline()
    # 黄刺：贴着皮往外扎
    for a in (160, 130, 100, 70, 40):
        ax, ay = math.cos(math.radians(a)), math.sin(math.radians(a))
        x, y = round(cx + ax * (rad + 0.6)), round(cy + ay * (rad + 0.6))
        if not c.get(x, y):
            c.set(x, y, STAR, 5, glint=True)
    return c


@texture("item/gravitino_ball", refs=("item/ender_pearl", "item/slime_ball"))
def gravitino_ball(rng):
    # 引力果：一颗亮紫红的圆果，表面一道旋涡状的光
    c = Canvas()
    f = Form(ell(8.0, 8.4, 5.4, 5.4))
    shade(c, f, GRAVITINO, 4, 1.8, lo=2)
    c.outline()
    for i in range(9):
        a = i * 0.72
        x, y = round(8.0 + math.cos(a) * i * 0.42), round(8.4 + math.sin(a) * i * 0.42)
        if (x, y) in f:
            c.set(x, y, MAGENTA_GLOW, 5 if i < 5 else 4, glint=True)
    c.set(5, 5, WHITE, 6, glint=True)
    return c


@texture("item/marrow_bulb", refs=("item/potato", "item/brown_mushroom"))
def marrow_bulb(rng):
    # 洞穴髓球：三个挨在一起的奶白色球状菌体，底下一点须根，表面几处微微发亮
    c = Canvas()
    f = Form(ell(5.6, 10.0, 3.7, 3.5), ell(10.8, 10.4, 3.3, 3.1), ell(8.2, 6.2, 3.2, 3.0))
    shade(c, f, BONE, 4, 1.8, lo=2)
    c.outline()
    paint(c, [
        "......c..c......",
        ".....c....b.....",
    ], {"abcdefg": BONE}, 0, 13)
    for x, y in ((7, 4), (4, 8), (9, 9)):
        c.set(x, y, WHITE, 6, glint=True)
    for x, y in ((8, 5), (5, 9), (10, 10)):
        c.set(x, y, YELLOW_GLOW, 5, glint=True)
    return c


