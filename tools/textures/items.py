"""中间产品和材料的物品贴图（ADR-0031）。

物品照原版的画法：光从左上来，四到六级明暗，最外一圈用材质自己最深的颜色描边（不用纯黑），
受光的一侧描边浅一级；金属有一两个高光点，晶体有锐利的切面。小东西一个像素一个像素地画（Canvas.draw）。
"""
from __future__ import annotations

import math

from canvas import Canvas
from color import ramp
from palette import (AMBER, AMBER_GLOW, BASALT, BERRY, BLUE, BLUE_GLOW, BRASS, COPPER, CREAM, CRIMSON, CYAN_GLOW,
                     FOG, GLASS, GOLD_FOIL, GRAPHITE, GREEN, GREEN_GLOW, HAZARD, ICE, LEAF, MAGENTA_GLOW, MOSS, OIL,
                     ORANGE_GLOW, RED, RED_GLOW, RUST, SCREEN, STEEL, STONE, TEAL, TITANIUM, VIOLET, VIOLET_GLOW,
                     WHITE, WHITE_GLOW, YELLOW_GLOW, register)
from parts import TIER
from recipe import texture

# 这一组自己用的色阶
TI_ALLOY = register(ramp("items_ti_alloy", 245, 0.055, n=7, lo=0.26, hi=0.90, shift=14))


@texture("item/titanium_ingot", refs=("item/iron_ingot", "item/netherite_ingot", "item/gold_ingot"))
def titanium_ingot(rng):
    c = Canvas()
    # 锭：顶面最亮、有一道高光，正面往下渐暗，左端受光，右端背光
    c.draw([
        "................",
        "................",
        "................",
        "................",
        "....1111111111..",
        "...156666655541.",
        "..15666665554440",
        ".155555554444320",
        ".143333333333210",
        ".143333333332210",
        ".132222222222110",
        "..000000000000..",
    ], {"0123456": TITANIUM}, 0, 1)
    return c


@texture("item/gear", refs=("item/clock_00", "item/compass_00"))
def gear(rng):
    c = Canvas()
    # 齿轮：平的金属面，八个齿；外沿左上亮、右下暗，轮毂凹下去一圈，中间是轴孔
    cx = cy = 7.5
    mask = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx) + math.pi / 8
            if r <= 5.6 or (math.cos(a * 8) > -0.15 and r <= 7.4):
                mask.add((x, y))
    for x, y in mask:
        c.set(x, y, STEEL, 5)
    for x, y in mask:
        lit = (x - 1, y) not in mask or (x, y - 1) not in mask
        dark = (x + 1, y) not in mask or (x, y + 1) not in mask
        if lit and not dark:
            c.set(x, y, STEEL, 7)
        elif dark:
            c.set(x, y, STEEL, 3)
    # 轮毂：一圈凹槽，上左是影子，下右受光
    for x, y in c.disc_cells(cx, cy, 3.2):
        r = math.hypot(x - cx, y - cy)
        if r > 2.2:
            c.set(x, y, STEEL, 3 if x + y < 15 else 6)
        else:
            c.set(x, y, STEEL, 5)
    for x, y in c.disc_cells(cx, cy, 0.9):
        c.clear(x, y)
    c.set(7, 6, STEEL, 2)
    c.set(6, 7, STEEL, 2)
    c.outline()
    c.set(4, 3, STEEL, 7, glint=True)
    return c


@texture("item/circuit_board", refs=("item/redstone",))
def circuit_board(rng):
    c = Canvas()
    # 电路板：绿色基板有厚度（下边、右边一条暗色），铜走线，中间一块带引脚的芯片，
    # 右上一颗电容，左上一个安装孔，下边一排金手指
    c.rect(1, 2, 14, 13, GREEN, 3)
    c.rect(2, 3, 13, 11, GREEN, 4)
    c.hline(2, 13, 12, GREEN, 2)                        # 板子的厚度
    c.vline(14, 3, 13, GREEN, 2)
    # 走线
    for y in (4, 10):
        c.hline(3, 12, y, COPPER, 3)
    c.vline(3, 4, 10, COPPER, 3)
    c.hline(10, 12, 6, COPPER, 3)
    c.hline(10, 12, 8, COPPER, 3)
    c.hline(3, 5, 7, COPPER, 3)
    for x, y in ((3, 4), (12, 4), (3, 10), (12, 10), (12, 6), (12, 8)):
        c.set(x, y, COPPER, 5, glint=True)              # 焊盘
    # 芯片
    c.rect(6, 5, 9, 9, GRAPHITE, 2)
    c.hline(6, 9, 5, GRAPHITE, 4)
    c.vline(6, 6, 9, GRAPHITE, 3)
    c.hline(7, 9, 9, GRAPHITE, 1)
    c.set(7, 6, GRAPHITE, 5, glint=True)
    for y in (6, 8):
        c.set(5, y, STEEL, 6, glint=True)
        c.set(10, y, STEEL, 4, glint=True)
    # 电容：小圆柱，顶面亮
    c.cylinder(11, 3, 12, 4, TEAL, 4)
    c.set(11, 3, TEAL, 6, glint=True)
    # 安装孔
    c.set(2, 3, GREEN, 1)
    c.set(3, 3, GREEN, 5)
    # 金手指
    for x in range(3, 13, 2):
        c.set(x, 11, BRASS, 5, glint=True)
        c.set(x, 12, BRASS, 3, glint=True)
    c.outline()
    return c


@texture("item/casimir_crystal", refs=("item/diamond", "item/amethyst_shard", "item/echo_shard"))
def casimir_crystal(rng):
    c = Canvas()
    # 六棱柱的晶体立着：左面受光，中间一面带一道高光，右面背光；里面透出一点青光
    c.draw([
        "................",
        ".......11.......",
        "......1560......",
        ".....155420.....",
        "....15554320....",
        "....15564320....",
        "....15564320....",
        "....15564320....",
        "....15544320....",
        "....15544320....",
        "....15544220....",
        "....15544220....",
        ".....154320.....",
        "......1420......",
        ".......00.......",
        "................",
    ], {"0123456": TEAL})
    c.set(8, 6, CYAN_GLOW, 4, glint=True)
    c.set(8, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 4, glint=True)
    c.set(5, 4, GLASS, GLASS.top, glint=True)
    return c


# ---------------------------------------------------------------- 系列：矩阵
#
# 六种科研矩阵（加上复合、黑雾两种）是同一个容器：斜看的立方体，钢的棱框，三个面是透着光的能量。
# 顶面最亮，中间一团亮核；左面中等，右面暗；三个面用色阶里最饱和的几级，棱是受光的亮钢。
# 数字是棱框的钢，a–f 顶面、g–l 左面、m–r 右面。
MATRIX = [
    "................",
    ".......43.......",
    ".....44dd33.....",
    "...44ddeedd33...",
    ".44ddeeffeedd33.",
    ".3j66ddeedd55o2.",
    ".3jjj66dd55ooo2.",
    ".3jkjjj65ooooo2.",
    ".3jkjjj54ooooo2.",
    ".3ikiii54nnnnn2.",
    ".3iiiii54nnnnn2.",
    ".22iiii54nnnn11.",
    "...22ii54nn11...",
    ".....225411.....",
    ".......21.......",
    "................",
]


def matrix(top, left=None, right=None, frame=STEEL, lift: int = 0) -> Canvas:
    """lift：三个面整体提亮几级（白色的宇宙矩阵用）。"""
    c = Canvas()
    c.draw(MATRIX, {"01234567": frame, "abcdef": top, "ghijkl": left or top, "mnopqr": right or top})
    if lift:
        for x, y in c.cells():
            px = c.get(x, y)
            if px and px[0] is not frame:
                c.shift(x, y, lift)
    return c


@texture("item/electromagnetic_matrix", refs=("item/lapis_lazuli", "item/ender_eye"))
def electromagnetic_matrix(rng):
    return matrix(BLUE_GLOW)


@texture("item/energy_matrix")
def energy_matrix(rng):
    return matrix(RED_GLOW)


@texture("item/structure_matrix")
def structure_matrix(rng):
    return matrix(YELLOW_GLOW)


@texture("item/information_matrix")
def information_matrix(rng):
    return matrix(VIOLET_GLOW)


@texture("item/gravity_matrix")
def gravity_matrix(rng):
    return matrix(GREEN_GLOW)


@texture("item/composite_matrix")
def composite_matrix(rng):
    # 三种矩阵拼在一起：顶面黄、左面蓝、右面红
    return matrix(YELLOW_GLOW, BLUE_GLOW, RED_GLOW)


@texture("item/universe_matrix")
def universe_matrix(rng):
    # 白色的立方体，亮核是一圈彩色的光
    c = matrix(WHITE_GLOW, GLASS, GLASS, frame=TITANIUM)
    for x, y in c.cells():
        px = c.get(x, y)
        if px and px[0] is GLASS:
            c.shift(x, y, 2)
        elif px and px[0] is WHITE_GLOW:
            c.shift(x, y, 1)
    for (x, y), r in zip(((7, 3), (8, 3), (9, 4), (8, 5), (7, 5), (6, 4)),
                         (RED_GLOW, AMBER_GLOW, YELLOW_GLOW, GREEN_GLOW, BLUE_GLOW, VIOLET_GLOW)):
        c.set(x, y, r, 4, glint=True)
    c.set(7, 4, WHITE_GLOW, 5, glint=True)
    c.set(8, 4, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/dark_fog_matrix")
def dark_fog_matrix(rng):
    # 黑雾的配色：暗紫黑的棱框，里面是深红的光
    return matrix(MAGENTA_GLOW, RED_GLOW, RED_GLOW, frame=FOG)


# ---------------------------------------------------------------- 系列：流体罐
#
# 水、原油、精炼油、硫酸、氢、氘装在同一种罐子里：钢的阀门和底座，中间一段玻璃看得见里面。
# 液体有液面（亮一道），气体充满整个罐子、颜色浅，罐身多一道标识色的环。

def canister(content, base: int, level: int = 6, gas: bool = False) -> Canvas:
    c = Canvas()
    # 玻璃罐身 x 4–11、y 4–12，两边是钢的立柱：空的部分是透过玻璃看到的暗色
    c.cylinder(4, 4, 11, 12, GLASS, 2, amp=1)
    top = 4 if gas else level
    c.cylinder(4, top, 11, 12, content, base)
    c.vline(3, 4, 12, STEEL, 5)
    c.vline(12, 4, 12, STEEL, 2)
    if gas:
        # 气体：罐里几团浅一点的雾
        for x, y in ((6, 6), (7, 6), (9, 10), (10, 10), (6, 11)):
            c.shift(x, y, 1)
    else:
        # 液面：一道亮线，两边贴着玻璃往上翘一点（弯月面）
        c.hline(5, 10, top, content, base + 2)
        c.set(4, top - 1, content, base + 1)
        c.set(11, top - 1, content, base)
    # 玻璃的反光：左边一道竖的高光
    c.vline(5, 5, 10, GLASS, GLASS.top)
    # 上下的钢盖，四个角收圆
    c.cylinder(3, 2, 12, 3, STEEL, 4)
    c.hline(3, 12, 3, STEEL, 3)
    c.cylinder(6, 1, 9, 1, STEEL, 5)
    c.cylinder(3, 13, 12, 14, STEEL, 3)
    c.hline(3, 12, 13, STEEL, 4)
    for x, y in ((3, 2), (12, 2), (3, 14), (12, 14)):
        c.clear(x, y)
    if gas:
        # 标识环：罐身中间一道钢圈
        c.cylinder(3, 8, 12, 8, STEEL, 4)
    c.outline()
    return c


@texture("item/water", refs=("item/water_bucket", "item/potion"))
def water(rng):
    return canister(BLUE, 4, 6)


@texture("item/crude_oil")
def crude_oil(rng):
    return canister(OIL, 2, 5)


@texture("item/refined_oil")
def refined_oil(rng):
    return canister(AMBER, 4, 6)


@texture("item/sulfuric_acid")
def sulfuric_acid(rng):
    return canister(HAZARD, 3, 7)


@texture("item/hydrogen")
def hydrogen(rng):
    return canister(ICE, 3, gas=True)


@texture("item/deuterium")
def deuterium(rng):
    return canister(TEAL, 4, gas=True)


# ---------------------------------------------------------------- 系列：燃料棒
#
# 斜放的一根棒（左下到右上）：两头是金属端盖，中间一段透明的窗，里面是发光的燃料。

def rod(c: Canvas, segments, x0: float = 2.4, y0: float = 13.6):
    """斜着的圆柱。segments：[(从, 到, 半径, 色阶, 基准级), ...]，沿轴的长度单位是像素。"""
    s = math.sqrt(0.5)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            u = (px - py) * s          # 沿轴
            v = (px + py) * s          # 垂直于轴，负的一侧朝左上（受光）
            for a, b, hw, r, base in segments:
                if a <= u < b and abs(v) <= hw:
                    t = (v / hw + 1) / 2
                    d = 2 if t < 0.18 else 1 if t < 0.4 else 0 if t < 0.66 else -1 if t < 0.85 else -2
                    c.set(x, y, r, base + d, glint=True)   # 斜线上的像素本来就只斜着相邻
                    break


def tidy(c: Canvas, passes: int = 1):
    """去掉程序打光留下的孤零零的像素：四邻里有三个是同一种颜色，就随它们。登记过的高光点不动。"""
    for _ in range(passes):
        changes = []
        for x, y in c.cells():
            px = c.get(x, y)
            if not px or (x, y) in c.glints:
                continue
            around = [c.get(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(q == px for q in around):
                continue
            for q in around:
                if q and sum(1 for o in around if o == q) >= 3:
                    changes.append((x, y, q))
                    break
        for x, y, q in changes:
            c.set(x, y, q[0], q[1])


def prune(c: Canvas):
    """去掉只有一个邻居的毛刺像素（斜线的端头常冒出来一个）。"""
    for x, y in [(x, y) for x, y in c.cells() if c.get(x, y)]:
        n = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if c.get(x + dx, y + dy))
        if n <= 1:
            c.clear(x, y)


def fuel_rod(fuel, cap) -> Canvas:
    c = Canvas()
    rod(c, [(0.0, 3.0, 2.5, cap, 3), (3.0, 4.2, 2.9, STEEL, 4), (4.2, 11.4, 2.3, fuel, 3),
            (11.4, 12.6, 2.9, STEEL, 4), (12.6, 15.6, 2.5, cap, 3)])
    prune(c)
    c.outline()
    return c


@texture("item/deuteron_fuel_rod", refs=("item/blaze_rod", "item/end_rod"))
def deuteron_fuel_rod(rng):
    return fuel_rod(CYAN_GLOW, TITANIUM)


@texture("item/antimatter_fuel_rod")
def antimatter_fuel_rod(rng):
    return fuel_rod(MAGENTA_GLOW, GOLD_FOIL)


@texture("item/hydrogen_fuel_rod")
def hydrogen_fuel_rod(rng):
    return fuel_rod(BLUE_GLOW, RED)


# ---------------------------------------------------------------- 系列：增产剂
#
# 一只喷罐：罐身是档位的颜色（Mk.I 琥珀、Mk.II 绿、Mk.III 蓝），罐身上一、二、三道白环。

def proliferator(tier: int) -> Canvas:
    c = Canvas()
    body = TIER[tier]
    c.cylinder(5, 4, 10, 14, body, 3)
    # 罐肩和喷头
    c.cylinder(6, 3, 9, 3, STEEL, 5)
    c.cylinder(7, 1, 8, 2, STEEL, 4)
    c.set(9, 1, GRAPHITE, 2)
    c.set(10, 1, GRAPHITE, 1)
    c.cylinder(5, 4, 10, 4, body, 4)
    # 档位的白环
    for i in range(tier):
        y = 7 + i * 2
        c.cylinder(5, y, 10, y, WHITE, 4)
        c.glints.update((x, y) for x in range(5, 11))
    c.cylinder(5, 14, 10, 14, STEEL, 3)
    c.outline()
    return c


@texture("item/proliferator_mk1")
def proliferator_mk1(rng):
    return proliferator(1)


@texture("item/proliferator_mk2")
def proliferator_mk2(rng):
    return proliferator(2)


@texture("item/proliferator_mk3")
def proliferator_mk3(rng):
    return proliferator(3)


# ---------------------------------------------------------------- 系列：锭
#
# 和钛锭一个造型：顶面最亮、有一道高光，正面往下渐暗，左端受光，右端背光。

INGOT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "....1111111111..",
    "...156666655541.",
    "..15666665554440",
    ".155555554444320",
    ".143333333333210",
    ".143333333332210",
    ".132222222222110",
    "..000000000000..",
    "................",
    "................",
    "................",
]


def ingot(r) -> Canvas:
    c = Canvas()
    c.draw(INGOT, {"0123456": r})
    return c


@texture("item/steel", refs=("item/iron_ingot", "item/netherite_ingot"))
def steel(rng):
    return ingot(STEEL)


@texture("item/titanium_alloy")
def titanium_alloy(rng):
    # 比钛锭偏蓝、更暗一点的合金
    return ingot(TI_ALLOY)


@texture("item/uranium_ingot")
def uranium_ingot(rng):
    c = ingot(GREEN)
    c.hline(6, 11, 6, GREEN_GLOW, 4)
    return c


# ---------------------------------------------------------------- 矿物：粗矿、晶体、矿石的矿点
#
# 每种矿一套颜色，矿点（贴在按星球染色的岩石上）和掉的物品用同一套，挖之前就认得出是什么。

# 粗矿的几种造型：几个圆鼓鼓的石块挤在一起，[(圆心 x, 圆心 y, 半径)]，先画后面的
LUMPS = [
    [(10.8, 6.6, 3.2), (6.2, 6.0, 4.3), (8.6, 10.6, 3.6), (4.4, 10.4, 2.6)],
    [(5.0, 6.4, 3.1), (9.6, 5.8, 3.9), (7.2, 10.2, 4.0), (11.6, 10.4, 2.5)],
    [(11.0, 9.6, 3.0), (6.8, 5.6, 3.6), (5.0, 10.0, 3.4), (9.2, 8.8, 3.3)],
]


def lump(shape: int, rock, base: int = 3) -> Canvas:
    """一团粗矿：每个石块是一个鼓包，按球面打光（左上亮、右下暗），量化成几级；
    前面的石块压在后面的上面，交界处后面那块暗一级，看得出是几块挤在一起。"""
    c = Canvas()
    drawn = set()
    for cx, cy, r in LUMPS[shape]:
        cells = c.disc_cells(cx, cy, r)
        for x, y in cells:
            nx, ny = (x + 0.5 - cx) / (r + 0.4), (y + 0.5 - cy) / (r + 0.4)
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            v = -0.58 * nx - 0.62 * ny + 0.53 * nz
            lv = base + (2 if v > 0.78 else 1 if v > 0.55 else 0 if v > 0.25 else -1 if v > -0.05 else -2)
            c.set(x, y, rock, lv)
        new = set(cells)
        # 交界：后面的石块挨着这一块的地方压暗一级
        for x, y in drawn - new:
            if any((x + dx, y + dy) in new for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.shift(x, y, -1)
        drawn |= new
    tidy(c)
    prune(c)
    c.outline()
    return c


def flecks(c: Canvas, rng, mineral, base: int, count: int, size: int = 2):
    """嵌在石头里的矿物：2×2 的一小块（size=3 时 L 形三块），左上亮、右下暗，只放在石头里面、不压轮廓。"""
    def inner(x, y):
        return all(c.get(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
    spots = [(x, y) for x, y in c.cells(1, 1, 13, 13) if inner(x, y) and inner(x + 1, y + 1)]
    rng.shuffle(spots)
    used = []
    for x, y in spots:
        if len(used) >= count:
            break
        if any(abs(x - u) < 4 and abs(y - v) < 4 for u, v in used):
            continue
        shape = [(0, 0, 2), (1, 0, 1), (0, 1, 0), (1, 1, -1)]
        if size == 3:
            shape = [(0, 0, 2), (1, 0, 0), (0, 1, 0)]
        for dx, dy, d in shape:
            c.set(x + dx, y + dy, mineral, base + d, glint=True)
        used.append((x, y))


RAW_IRON = register(ramp("items_raw_iron", 55, 0.075, n=7, lo=0.34, hi=0.92, shift=14))      # 原版粗铁的肉色
SILICON = register(ramp("items_silicon", 255, 0.045, n=7, lo=0.22, hi=0.88, shift=12))       # 硅：带蓝的深灰
SALT = register(ramp("items_salt", 8, 0.030, n=6, lo=0.52, hi=0.97, shift=10))              # 岩盐：带一点粉的白
MAGNETITE = register(ramp("items_magnetite", 270, 0.022, n=7, lo=0.18, hi=0.70, shift=10))  # 磁铁矿：发蓝的铁黑


@texture("item/raw_titanium", refs=("item/raw_iron", "item/raw_copper", "item/raw_gold"))
def raw_titanium(rng):
    c = lump(0, STONE, 3)
    flecks(c, rng, TITANIUM, 4, 4)
    return c


@texture("item/raw_silicon")
def raw_silicon(rng):
    c = lump(1, BASALT, 3)
    flecks(c, rng, SILICON, 4, 4, 3)
    return c


@texture("item/raw_uranium")
def raw_uranium(rng):
    c = lump(2, MOSS, 2)
    flecks(c, rng, GREEN_GLOW, 3, 5)
    return c


@texture("item/raw_magnetite")
def raw_magnetite(rng):
    c = lump(0, MAGNETITE, 3).flip_h()
    flecks(c, rng, STEEL, 5, 4)
    return c


# 一簇晶体：中间一根高的，左右各一根矮的挤在前面。
# 数字是中间那根，a–g 左边那根，h–n 右边那根。
CLUSTER = [
    "........6.......",
    ".......563......",
    "......56432.....",
    "......56432.....",
    "...gd.56432.....",
    "..fedc56432.....",
    "..fedc56432mj...",
    "..fedc5643lkji..",
    "..fedc5543lkji..",
    "..fedc5543lkji..",
    "..fedc5543lkji..",
    "..edcb4432kjih..",
    "..edcb4432kjih..",
    "....cb3321ji....",
    "................",
    "................",
]


def cluster(center, left=None, right=None) -> Canvas:
    c = Canvas()
    c.draw(CLUSTER, {"0123456": center, "abcdefg": left or center, "hijklmn": right or center}, 0, 1)
    c.outline()
    return c


@texture("item/fire_ice", refs=("item/diamond", "item/amethyst_shard", "item/prismarine_crystals"))
def fire_ice(rng):
    c = cluster(ICE)
    # 燃冰会发光：中间那根晶柱里透出一点青光
    for x, y in ((8, 5), (8, 6), (8, 7), (7, 9)):
        c.set(x, y, CYAN_GLOW, 4, glint=True)
    return c


@texture("item/optical_crystal")
def optical_crystal(rng):
    # 虹彩：三根晶柱各是一种颜色
    return cluster(VIOLET, TEAL, BERRY)


@texture("item/organic_crystal")
def organic_crystal(rng):
    return cluster(LEAF)


@texture("item/titanium_crystal")
def titanium_crystal(rng):
    return cluster(TITANIUM, BLUE, BLUE)



# ---- 矿石的矿点层（贴在按星球染色的岩石上，只有全透明和不透明两种像素，走镂空渲染层）
#
# 矿点用几种小印章拼：h 比基准亮两级、m 亮一级、b 基准、d 暗一级、o 暗两级。亮的一头在左上，
# 浅色的岩石上看暗边、深色的岩石上看亮边，在什么颜色的星岩上都清楚。
NODULES = [
    [".hm.", "hmbd", "mbdo", ".do."],
    [".hmm.", "hmmbd", "mbbbd", ".bddo", "..oo."],
    ["hm.", "mbd", ".do"],
    [".hmm", "hmbd", "mbdo"],
]
SHARDS = [
    [".h.", "hmd", "hmd", "hbd", ".o."],
    [".h..", "hmh.", "hmhd", "hmbd", "hbdo", ".oo."],
    ["..h", ".hd", "hmd", "bo."],
    ["h..", "hd.", "hmd", "hbd", ".o."],
]
CUBES = [
    ["hhm", "hmd", "mdo"],
    ["hhhm", "hmmd", "hmmd", "mddo"],
    ["hhm.", "hmmd", "mmdo"],
]
STEP = {"h": 2, "m": 1, "b": 0, "d": -1, "o": -2}


def stamp(c: Canvas, rows, x0: int, y0: int, r, base: int):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch in STEP:
                c.set(x0 + dx, y0 + dy, r, base + STEP[ch], glint=True)


def ore_overlay(rng, ramps, base: int, stamps, count: int, spacing: float = 4.2, accent=None, shine=None) -> Canvas:
    """count 个印章撒在 16×16 里，彼此隔开，不贴边（相邻的矿石方块拼起来不会切掉半个）。
    ramps 可以给几条色阶轮着用（光学晶体的虹彩）；accent 是点在个别矿点上的另一种颜色（铜的铜绿）；
    shine 是深色矿物左上角的一点反光（煤、磁铁矿），深色的岩石上也看得见。"""
    c = Canvas()
    placed = []
    for _ in range(600):
        if len(placed) >= count:
            break
        rows = rng.choice(stamps)
        w, h = max(len(r) for r in rows), len(rows)
        x0, y0 = rng.randint(1, 15 - w), rng.randint(1, 15 - h)
        cx, cy = x0 + w / 2, y0 + h / 2
        if any((cx - px) ** 2 + (cy - py) ** 2 < spacing ** 2 for px, py in placed):
            continue
        r = ramps[len(placed) % len(ramps)]
        stamp(c, rows, x0, y0, r, base)
        if accent and len(placed) % 2 == 1:
            c.set(x0 + w // 2, y0 + h // 2, accent, 3, glint=True)
        if shine:
            hx, hy = next((x0 + dx, y0 + dy) for dy, row in enumerate(rows) for dx, ch in enumerate(row) if ch == "h")
            c.set(hx, hy, shine[0], shine[1], glint=True)
        placed.append((cx, cy))
    return c


@texture("block/planet_iron_ore_overlay", refs=("block/iron_ore", "block/copper_ore", "block/deepslate_iron_ore"))
def planet_iron_ore_overlay(rng):
    return ore_overlay(rng, [RAW_IRON], 3, NODULES, 6)


@texture("block/planet_copper_ore_overlay")
def planet_copper_ore_overlay(rng):
    return ore_overlay(rng, [COPPER], 3, NODULES, 6, accent=TEAL)


@texture("block/planet_coal_ore_overlay", refs=("block/coal_ore",))
def planet_coal_ore_overlay(rng):
    return ore_overlay(rng, [BASALT], 1, NODULES, 7, shine=(STEEL, 5))


@texture("block/titanium_ore_overlay")
def titanium_ore_overlay(rng):
    return ore_overlay(rng, [TITANIUM], 4, NODULES, 6)


@texture("block/silicon_ore_overlay")
def silicon_ore_overlay(rng):
    return ore_overlay(rng, [SILICON], 3, SHARDS, 6)


@texture("block/sulfur_ore_overlay")
def sulfur_ore_overlay(rng):
    return ore_overlay(rng, [HAZARD], 3, NODULES[2:], 9, spacing=3.6)


@texture("block/fire_ice_ore_overlay", refs=("block/diamond_ore",))
def fire_ice_ore_overlay(rng):
    return ore_overlay(rng, [ICE], 4, SHARDS, 6)


@texture("block/optical_crystal_ore_overlay")
def optical_crystal_ore_overlay(rng):
    return ore_overlay(rng, [VIOLET, TEAL, BERRY], 3, SHARDS, 5)


@texture("block/organic_crystal_ore_overlay")
def organic_crystal_ore_overlay(rng):
    return ore_overlay(rng, [LEAF], 3, SHARDS, 6)


@texture("block/uranium_ore_overlay")
def uranium_ore_overlay(rng):
    return ore_overlay(rng, [GREEN_GLOW], 3, NODULES, 6)


@texture("block/salt_ore_overlay")
def salt_ore_overlay(rng):
    return ore_overlay(rng, [SALT], 3, CUBES, 6)


@texture("block/magnetic_ore_overlay")
def magnetic_ore_overlay(rng):
    # 磁铁矿长成八面体的小晶粒，和煤的圆疙瘩分得开
    return ore_overlay(rng, [MAGNETITE], 2, CUBES, 6, shine=(STEEL, 6))


@texture("block/strange_matter_ore_overlay")
def strange_matter_ore_overlay(rng):
    return ore_overlay(rng, [VIOLET_GLOW, MAGENTA_GLOW], 3, SHARDS, 5)



# ---- 精炼后的矿物

@texture("item/silicon", refs=("item/iron_ingot", "item/quartz"))
def silicon(rng):
    # 高纯硅：锭的造型，表面是多晶硅的块面反光
    c = ingot(SILICON)
    for x, y in ((5, 6), (6, 6), (10, 7), (4, 9), (5, 9), (9, 10)):
        c.shift(x, y, 1)
    c.set(5, 6, SILICON, SILICON.top, glint=True)
    c.set(10, 7, SILICON, SILICON.top, glint=True)
    return c


@texture("item/crystal_silicon")
def crystal_silicon(rng):
    """晶格硅：斜着看的一片晶圆，镜面一样的深色，表面一格一格的晶粒，一道彩虹色的反光，边上一个定位缺口。"""
    c = Canvas()
    cx, cy, rx, ry = 8.0, 7.2, 7.4, 5.4
    face = set()
    for y in range(16):
        for x in range(16):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                face.add((x, y))
    # 厚度：往下错一格的边
    for x, y in face:
        if (x, y + 1) not in face:
            c.set(x, y + 1, SILICON, 1)
    for x, y in face:
        c.set(x, y, SILICON, 2)
        if (x - 2) % 3 == 0 or (y - 2) % 3 == 0:
            c.set(x, y, SILICON, 3)
    # 一道斜的反光：蓝到紫的干涉色
    for x, y in face:
        k = x - y * 1.6
        if -4.5 <= k <= -2.5:
            c.set(x, y, BLUE, 4)
        elif -2.5 < k <= -1.0:
            c.set(x, y, VIOLET, 5)
    c.clear(7, 2)
    c.clear(8, 2)
    c.outline()
    c.set(3, 5, SILICON, SILICON.top, glint=True)
    return c


@texture("item/sulfur", refs=("item/glowstone_dust", "item/gunpowder", "item/redstone"))
def sulfur(rng):
    """硫磺：一小堆黄色的晶粉，堆尖受光，颗粒成团。"""
    c = Canvas()
    cells = []
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.6, (y + 0.5 - 13.4) / 8.4
            if dy <= 0.08 and dx * dx + dy * dy < 1:
                cells.append((x, y))
    for x, y in cells:
        dx, dy = (x + 0.5 - 8) / 6.6, (y + 0.5 - 13.4) / 8.4
        nz = math.sqrt(max(0.0, 1 - dx * dx - dy * dy))
        v = -0.58 * dx - 0.62 * dy + 0.53 * nz
        c.set(x, y, HAZARD, 2 + round((v - 0.3) * 4))
    c.clusters(rng, 3, 6, 13, 13, 1, 9, 2, only=HAZARD)
    c.clusters(rng, 3, 7, 13, 13, -1, 9, 2, only=HAZARD)
    c.outline()
    for x, y in ((6, 8), (9, 10), (4, 11)):
        c.set(x, y, HAZARD, HAZARD.top, glint=True)
    return c


def cube(c: Canvas, ox: int, oy: int, r, s: int = 5):
    """斜着看的小立方体：顶面最亮，正面中间，右侧面暗。"""
    for i in range(s):
        c.set(ox + 2 + i, oy, r, r.top)
        c.set(ox + 1 + i, oy + 1, r, r.top - 1)
    c.rect(ox, oy + 2, ox + s - 1, oy + s + 1, r, r.top - 2)
    c.hline(ox, ox + s - 1, oy + 2, r, r.top - 1)
    c.vline(ox + s, oy + 1, oy + s + 1, r, 1)
    c.vline(ox + s + 1, oy, oy + s, r, 1)
    c.hline(ox, ox + s - 1, oy + s + 1, r, 1)


@texture("item/salt", refs=("item/sugar", "item/quartz"))
def salt(rng):
    # 岩盐：三颗方方正正的晶体叠着
    c = Canvas()
    cube(c, 5, 1, SALT)
    cube(c, 1, 7, SALT)
    cube(c, 8, 7, SALT)
    c.outline(lit=2, shadow=0)
    c.set(3, 9, SALT, SALT.top, glint=True)
    return c


@texture("item/magnet", refs=("item/iron_ingot",))
def magnet(rng):
    """马蹄形磁铁：红色的弯管，两头是钢的磁极，管子按圆柱打光。"""
    c = Canvas()
    cx, cy, ro, ri = 7.5, 7.0, 6.6, 2.4
    mid, half = (ro + ri) / 2, (ro - ri) / 2
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            r, t, nx, ny = None, None, 0.0, 0.0
            if dy <= 0 and ri <= d <= ro:
                t = (d - mid) / half
                nx, ny = dx / d, dy / d
            elif dy > 0 and ri <= abs(dx) <= ro and y <= 14:
                t = (abs(dx) - mid) / half
                nx, ny = (1 if dx > 0 else -1), 0.0
            if t is None:
                continue
            t = max(-1.0, min(1.0, t))
            nz = math.sqrt(max(0.0, 1 - t * t))
            v = -0.58 * nx * t - 0.62 * ny * t + 0.53 * nz
            r = STEEL if y >= 12 else RED
            base = 4 if r is STEEL else 3
            c.set(x, y, r, base + round((v - 0.45) * 4))
    c.outline()
    return c


@texture("item/strange_matter", refs=("item/ender_pearl", "item/heart_of_the_sea"))
def strange_matter(rng):
    """奇异物质：发紫光的球，按球面打光，一条亮的旋臂绕着中心的亮核，背光的边缘泛品红。"""
    c = Canvas()
    c.shade_disc(7.5, 7.5, 6.2, VIOLET_GLOW, 3, amp=1.6, spec=False)
    for x, y in c.disc_cells(7.5, 7.5, 6.2):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        if 1.2 < d < 5.4 and math.cos(a - d * 0.95) > 0.72:
            c.set(x, y, VIOLET_GLOW, 5 if d < 3.4 else 4)
        elif d > 4.8 and dx + dy > 3:
            c.set(x, y, MAGENTA_GLOW, 2)
    tidy(c)
    c.outline()
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    c.set(8, 8, VIOLET_GLOW, 5, glint=True)
    c.set(4, 4, WHITE_GLOW, 4, glint=True)
    return c


# ---- 黑雾掉落的零件（ADR-0028 §六）：等级越高颜色越亮，越不像地上能捡到的东西

@texture("item/energy_shard", refs=("item/echo_shard", "item/prismarine_shard"))
def energy_shard(rng):
    """能量碎片：两块斜着的青蓝色碎晶，一大一小，受光的一边亮，中间一道亮棱。"""
    c = Canvas()
    c.draw([
        "................",
        "...........55...",
        "..........5554..",
        ".........55543..",
        "........555432..",
        ".......555432...",
        "......555432....",
        ".....555432.....",
        "....555432......",
        "...55432...54...",
        "...5432...5543..",
        "....32...55432..",
        ".........5432...",
        "..........32....",
        "................",
        "................",
    ], {"012345": CYAN_GLOW})
    c.outline()
    for x, y in ((11, 2), (10, 3), (9, 4), (8, 5), (7, 6)):
        c.set(x, y, WHITE_GLOW, 5 if x > 9 else 4, glint=True)
    c.set(11, 10, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/core_element", refs=("item/emerald", "item/diamond"))
def core_element(rng):
    """核心素：发红光的八面体晶体，上半受光、下半背光，左右各一个面。"""
    c = Canvas()
    c.draw([
        "................",
        ".......54.......",
        "......5543......",
        ".....655432.....",
        "....6554432.....",
        "...655544321....",
        "..6555444321....",
        ".66555444322l...",
        ".ihhh3332211k...",
        "..ihh332221k....",
        "...ih33221k.....",
        "....ih321k......",
        ".....i31k.......",
        "......jk........",
        "................",
        "................",
    ], {"0123456": RED, "ghijkl": CRIMSON})
    c.outline()
    c.set(6, 3, RED_GLOW, 5, glint=True)
    c.set(7, 2, RED_GLOW, 4, glint=True)
    return c


@texture("item/silicon_neuron")
def silicon_neuron(rng):
    """硅基神经元：中间一块暗红的芯片，四周伸出发亮的突触，末端一个亮点。"""
    c = Canvas()
    c.panel(4, 4, 11, 11, CRIMSON, 2)
    c.recess(6, 6, 9, 9, CRIMSON, 2)
    c.rect(7, 7, 8, 8, RED_GLOW, 4)
    c.outline()
    c.set(7, 7, RED_GLOW, 5, glint=True)
    # 突触画在描边之后，一个像素宽的线不能被描边吃掉
    paths = [((7, 3), (7, 1)), ((8, 12), (8, 14)), ((3, 8), (1, 8)), ((12, 7), (14, 7)),
             ((3, 4), (2, 2)), ((12, 11), (13, 13)), ((12, 4), (13, 2)), ((3, 11), (2, 13))]
    for i, ((x0, y0), (x1, y1)) in enumerate(paths):
        dx, dy = (x1 > x0) - (x1 < x0), (y1 > y0) - (y1 < y0)
        x, y = x0, y0
        while True:
            c.set(x, y, RED, 4 if (dx < 0 or dy < 0) else 3, glint=True)
            if (x, y) == (x1, y1):
                break
            x, y = x + (dx if x != x1 else 0), y + (dy if y != y1 else 0)
        c.set(x1, y1, RED_GLOW, 5 if i < 4 else 4, glint=True)
    return c


@texture("item/matter_recombinator", refs=("item/ender_eye",))
def matter_recombinator(rng):
    """物质重组器：深灰的金属球，赤道上斜着一圈橙色的光环，前半圈压在球上、后半圈被球挡住。"""
    c = Canvas()
    c.shade_disc(7.5, 7.5, 4.6, GRAPHITE, 3, amp=2.2)
    ca, sa = math.cos(math.radians(-16)), math.sin(math.radians(-16))
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            u = dx * ca + dy * sa
            v = -dx * sa + dy * ca
            ring = math.hypot(u / 7.3, v / 2.4)
            if 0.62 <= ring <= 1.0:
                front = v > -0.4
                if front:
                    c.set(x, y, ORANGE_GLOW, 4 if u < -2 else 3)
                elif c.get(x, y) is None:
                    c.set(x, y, ORANGE_GLOW, 2)
    c.outline()
    return c


@texture("item/negentropy_singularity", refs=("item/ender_pearl",))
def negentropy_singularity(rng):
    """负熵奇点：紫黑色的漩涡，三条旋臂往里卷，中心一点白光。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 6.6):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        arm = (math.cos(3 * a + d * 0.95) + 1) / 2
        if arm > 0.62 and d < 6.2:
            c.set(x, y, VIOLET_GLOW, max(1, round(4.6 - d * 0.55)))
        else:
            c.set(x, y, FOG, max(0, round(2.6 - d * 0.32)))
    tidy(c)
    c.outline()
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    c.set(8, 8, WHITE_GLOW, 5, glint=True)
    c.set(8, 7, VIOLET_GLOW, 5, glint=True)
    c.set(7, 8, VIOLET_GLOW, 5, glint=True)
    return c



# ---------------------------------------------------------------- 零件和中间产品

def torus(c: Canvas, cx: float, cy: float, rx: float, ry: float, tube: float, color, base: int):
    """斜着看的圆环（椭圆环）：管子的截面按圆柱打光，外沿上半圈受光。color 可以是 (x, y) -> 色阶 的函数。"""
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = math.hypot(dx, dy)
            t = (d - 1) * min(rx, ry) / tube
            if abs(t) > 1:
                continue
            nx, ny = (dx / d, dy / d) if d else (0.0, 0.0)
            nz = math.sqrt(max(0.0, 1 - t * t))
            v = -0.58 * nx * t - 0.62 * ny * t + 0.53 * nz
            r = color(x, y) if callable(color) else color
            c.set(x, y, r, base + round((v - 0.45) * 4.2))
    tidy(c)


@texture("item/magnetic_coil", refs=("item/copper_ingot",))
def magnetic_coil(rng):
    """电磁线圈：铁芯上密密地绕着铜线，两头是钢的挡板，上面伸出两根引线。"""
    c = Canvas()
    c.cylinder(3, 5, 12, 11, COPPER, 3, vertical=False)
    for x in range(3, 13):
        if x % 2 == 0:
            for y in range(5, 12):
                c.shift(x, y, -1)
    c.cylinder(1, 4, 2, 12, STEEL, 4, vertical=False)
    c.cylinder(13, 4, 14, 12, STEEL, 3, vertical=False)
    c.vline(1, 4, 12, STEEL, 6)
    c.vline(14, 4, 12, STEEL, 2)
    c.outline()
    for x, y in ((5, 4), (5, 3), (6, 2)):
        c.set(x, y, COPPER, 5, glint=True)
    for x, y in ((10, 4), (10, 3), (11, 2)):
        c.set(x, y, COPPER, 4, glint=True)
    return c


@texture("item/electric_motor")
def electric_motor(rng):
    """电动机：横放的圆筒机身，一圈圈散热片，右边伸出转轴，上面一个接线盒，下面两只脚。"""
    c = Canvas()
    c.cylinder(2, 4, 10, 12, BLUE, 3, vertical=False)
    for x in range(3, 10, 2):
        for y in range(5, 12):
            c.shift(x, y, -2)
    c.cylinder(11, 5, 11, 11, STEEL, 4, vertical=False)
    c.vline(11, 5, 11, STEEL, 3)
    c.cylinder(12, 7, 14, 9, STEEL, 5, vertical=False)
    c.rect(4, 2, 7, 3, AMBER, 4)
    c.hline(4, 7, 2, AMBER, 5)
    c.rect(3, 13, 4, 13, GRAPHITE, 2)
    c.rect(8, 13, 9, 13, GRAPHITE, 2)
    c.outline()
    c.set(14, 7, STEEL, 7, glint=True)
    return c


@texture("item/electromagnetic_turbine")
def electromagnetic_turbine(rng):
    """电磁涡轮：正面看的转子，钛合金的叶片顺时针扫，外面一圈蓝色的定子环，中间铜的轴。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 6.6):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        r = math.hypot(dx, dy)
        if r > 5.6:
            c.set(x, y, BLUE, 4 if dx + dy < 0 else 2)
            continue
        a = (math.atan2(dy, dx) + r * 0.26) % (math.tau / 5)
        f = a / (math.tau / 5)
        if f < 0.62:
            c.set(x, y, TITANIUM, 5 if f < 0.22 else 4 if f < 0.45 else 3)
        else:
            c.set(x, y, GRAPHITE, 1)
    c.shade_disc(7.5, 7.5, 2.0, COPPER, 4)
    c.outline()
    return c


@texture("item/energetic_graphite", refs=("item/coal", "item/charcoal"))
def energetic_graphite(rng):
    # 高能石墨：一块石墨，裂缝里透出蓝色的光
    c = lump(2, GRAPHITE, 3)
    flecks(c, rng, CYAN_GLOW, 3, 4)
    return c


@texture("item/graphene")
def graphene(rng):
    """石墨烯：斜着看的一片薄膜，上面是六角形的碳原子网格（亮的是碳键），薄膜下沿有一点厚度。"""
    c = Canvas()
    centers = [(cx + 0.5 + (2 if row % 2 else 0), row * 3.5 + 0.5) for row in range(-1, 7) for cx in range(-4, 20, 4)]
    sheet = set()
    for y in range(16):
        for x in range(16):
            u = (x + 0.5 - 8) + (y + 0.5 - 8) * 0.7
            v = (y + 0.5 - 8) - (x + 0.5 - 8) * 0.35
            if abs(u) <= 7.6 and abs(v) <= 5.6:
                sheet.add((x, y))
    for x, y in sheet:
        d = sorted(math.hypot(x + 0.5 - cx, y + 0.5 - cy) for cx, cy in centers)
        edge = d[1] - d[0] < 0.8
        c.set(x, y, GRAPHITE, 5 if edge else 1)
    c.outline()
    for x, y in ((4, 5), (8, 7), (11, 9)):
        c.set(x, y, GRAPHITE, GRAPHITE.top, glint=True)
    return c


@texture("item/carbon_nanotube")
def carbon_nanotube(rng):
    """碳纳米管：斜着的一根管子，管壁是螺旋的六角网格（亮暗相间的斜纹），右上的管口是空的。"""
    c = Canvas()
    rod(c, [(0.4, 15.2, 3.1, GRAPHITE, 2)])
    for x, y in list(c.cells()):
        if c.get(x, y) and ((x + 2 * y) % 4 == 0 or (2 * x - y) % 4 == 0):
            c.shift(x, y, 2)
    prune(c)
    c.outline()
    for x, y in ((12, 3), (13, 2), (12, 2)):
        if c.get(x, y):
            c.set(x, y, GRAPHITE, 0, glint=True)
    return c


@texture("item/microcrystalline_component")
def microcrystalline_component(rng):
    """微晶元件：小芯片，深色的封装，四边一排金色的引脚，中间一块蓝色的晶片。"""
    c = Canvas()
    c.panel(4, 4, 11, 11, GRAPHITE, 2)
    c.recess(6, 6, 9, 9, BLUE, 3)
    c.set(7, 7, BLUE, 5, glint=True)
    c.outline()
    for i in range(5, 11, 2):
        for x, y in ((i, 2), (i, 3), (i, 12), (i, 13), (2, i), (3, i), (12, i), (13, i)):
            c.set(x, y, BRASS, 5 if x < 8 and y < 8 else 4, glint=True)
    return c


@texture("item/processor")
def processor(rng):
    """处理器：绿色的基板，四边金色的触点，中间一块银色的金属顶盖，顶盖上一个青色的标记。"""
    c = Canvas()
    c.panel(1, 1, 14, 14, GREEN, 3)
    for i in range(3, 13, 2):
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            c.set(x, y, BRASS, 5, glint=True)
    c.panel(4, 4, 11, 11, STEEL, 5)
    c.bevel(4, 4, 11, 11, 2, -2)
    c.rect(7, 7, 8, 8, CYAN_GLOW, 3)
    c.set(7, 7, CYAN_GLOW, 5, glint=True)
    c.outline()
    c.set(5, 5, STEEL, 7, glint=True)
    return c


def broadband_color(x, y):
    return VIOLET_GLOW if int(math.hypot(x + 0.5 - 8, y + 0.5 - 8) * 1.6) % 2 else VIOLET


@texture("item/particle_broadband")
def particle_broadband(rng):
    """粒子宽带：卷成一卷的扁平线缆，紫色的线芯一圈圈发光，中间是钢的线轴。"""
    c = Canvas()
    torus(c, 8.0, 8.0, 5.2, 5.2, 2.3, broadband_color, 3)
    c.shade_disc(7.5, 7.5, 2.0, STEEL, 4)
    c.set(7, 7, GRAPHITE, 1)
    c.set(8, 8, GRAPHITE, 2)
    c.outline()
    return c


@texture("item/particle_container")
def particle_container(rng):
    """粒子容器：上下钢盖的玻璃罐，里面悬着一团发光的粒子。"""
    c = Canvas()
    c.cylinder(4, 3, 11, 12, GLASS, 1, amp=1)
    c.shade_disc(7.5, 7.5, 2.4, ORANGE_GLOW, 4, amp=1.2, spec=False)
    c.set(7, 7, YELLOW_GLOW, 5, glint=True)
    c.set(6, 7, YELLOW_GLOW, 4, glint=True)
    c.set(7, 6, YELLOW_GLOW, 4, glint=True)
    c.vline(5, 4, 11, GLASS, GLASS.top)
    c.cylinder(3, 1, 12, 2, STEEL, 4)
    c.cylinder(3, 13, 12, 14, STEEL, 3)
    c.hline(3, 12, 2, STEEL, 3)
    c.hline(3, 12, 13, STEEL, 4)
    for x, y in ((3, 1), (12, 1), (3, 14), (12, 14)):
        c.clear(x, y)
    c.outline()
    return c


@texture("item/plasma_exciter")
def plasma_exciter(rng):
    """电浆激发器：钢的外壳，正面一个凹进去的窗，窗里一团紫色的电浆，上下两排散热片。"""
    c = Canvas()
    c.panel(2, 2, 13, 13, STEEL, 4)
    c.recess(4, 4, 11, 11, GRAPHITE, 1)
    c.shade_disc(7.5, 7.5, 2.8, VIOLET_GLOW, 3, amp=2.4)
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    for x in range(4, 12, 2):
        c.set(x, 2, STEEL, 2)
        c.set(x, 13, STEEL, 2)
    c.outline()
    c.set(3, 3, STEEL, 7, glint=True)
    return c


@texture("item/plastic")
def plastic(rng):
    """塑料：三张叠在一起的白色塑料板，斜着看，顶面亮，前沿和右沿露出三层的厚度。"""
    c = Canvas()
    top = [(x, y) for y in range(3, 9) for x in range(7 - (y - 3), 15 - (y - 3))]
    for d, lv in ((4, 1), (3, 3), (2, 2), (1, 3)):
        for x, y in top:
            c.set(x, y + d, WHITE, lv)
    for x, y in top:
        c.set(x, y, WHITE, 5)
    for x in range(7, 15):
        c.set(x, 3, WHITE, 6)
    c.outline()
    c.set(8, 4, WHITE, WHITE.top, glint=True)
    return c



@texture("item/prism", refs=("item/prismarine_crystals", "item/glass_bottle"))
def prism(rng):
    """棱镜：斜着看的三棱柱玻璃，正面是三角形，右边一条侧面；一束白光进去，右边散成三色。"""
    c = Canvas()
    c.draw([
        "................",
        "......5.........",
        ".....554........",
        ".....5543.......",
        "....55543c......",
        "....554443c.....",
        "...5544443cb....",
        "...55444333cb...",
        "..554444333ccb..",
        "..5544433332cb..",
        ".55444433332cb..",
        ".54444333322cb..",
        ".22222222222bb..",
        "..1111111111b...",
        "................",
        "................",
    ], {"012345": GLASS, "abcdef": TEAL}, 0, 1)
    c.outline()
    # 光：左边一道白光照进来，右边散成红、绿、蓝
    for x in range(0, 3):
        c.set(x, 8, WHITE_GLOW, 5, glint=True)
    for (x, y), r in (((14, 6), RED_GLOW), ((14, 8), GREEN_GLOW), ((14, 10), BLUE_GLOW),
                      ((15, 5), RED_GLOW), ((15, 8), GREEN_GLOW), ((15, 11), BLUE_GLOW)):
        c.set(x, y, r, 4, glint=True)
    c.set(6, 2, GLASS, GLASS.top, glint=True)
    return c


# 推进器：喷口朝下，上面是圆柱形的燃烧室，一道钢箍，中间收成喉部，下面喇叭口，喷着火。
# 数字是机身，s 是钢箍（换成钢的色阶），a–f 是火焰。
THRUSTER = [
    "................",
    "......3443......",
    ".....245542.....",
    ".....245542.....",
    ".....SUVUTS.....",
    ".....245542.....",
    "......2442......",
    ".....245542.....",
    "....24555432....",
    "....24554432....",
    "...2455444332...",
    "..245554443321..",
    "..111111111110..",
    "....cdeffedc....",
    ".....cdeedc.....",
    "......cddc......",
]


def thruster(body, flame, bands: int = 1) -> Canvas:
    c = Canvas()
    rows = list(THRUSTER)
    if bands > 1:
        rows[2] = ".....SUVUTS....."
    c.draw(rows, {"0123456": body, "STUVW": STEEL, "abcdef": flame})
    c.outline()
    for x, y in ((7, 13), (8, 13), (7, 14), (8, 14), (7, 15), (8, 15), (6, 13), (9, 13)):
        px = c.get(x, y)
        if px:
            c.set(x, y, px[0], max(px[1], 3), glint=True)
    c.set(6, 2, body, body.top, glint=True)
    return c


@texture("item/thruster")
def thruster_item(rng):
    return thruster(STEEL, ORANGE_GLOW)


@texture("item/reinforced_thruster")
def reinforced_thruster(rng):
    # 加力推进器：钛合金的机身多两道加强箍，喷的是蓝色的等离子火焰
    return thruster(TI_ALLOY, BLUE_GLOW, bands=2)


def magnet_ring_color(x, y):
    return RED if x + 0.5 < 8 else BLUE


@texture("item/super_magnetic_ring", refs=("item/heart_of_the_sea",))
def super_magnetic_ring(rng):
    """超级磁场环：粗粗的一圈，一半红一半蓝（两个磁极），斜着看。"""
    c = Canvas()
    torus(c, 8.0, 8.0, 5.0, 4.4, 2.2, magnet_ring_color, 3)
    c.outline()
    c.set(4, 5, RED, RED.top, glint=True)
    c.set(10, 4, BLUE, BLUE.top, glint=True)
    return c


@texture("item/synthetic_diamond", refs=("item/diamond",))
def synthetic_diamond(rng):
    """金刚石：圆钻的侧面，上面是台面和冠部的斜面，下面收成尖的亭部，切面一亮一暗。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "....56666654....",
        "...5665566543...",
        "..566556654443..",
        ".55555444444332.",
        "..5544443333322.",
        "...54443333322..",
        "....444333322...",
        ".....4433322....",
        "......43322.....",
        ".......322......",
        "........2.......",
        "................",
        "................",
        "................",
    ], {"0123456": ICE}, 0, 1)
    c.outline()
    c.set(5, 4, WHITE_GLOW, 5, glint=True)
    c.set(9, 4, WHITE_GLOW, 4, glint=True)
    return c


@texture("item/combustible_unit", refs=("item/coal", "item/fire_charge"))
def combustible_unit(rng):
    """燃烧单元：压成方块的燃料，深褐色，裂缝里透出橙红的火光。"""
    c = matrix(ORANGE_GLOW, ORANGE_GLOW, ORANGE_GLOW, frame=RUST)
    # 三个面都换成压实的燃料，只留裂缝发光
    for x, y in list(c.cells()):
        px = c.get(x, y)
        if px and px[0] is ORANGE_GLOW:
            c.set(x, y, RUST, {5: 6, 4: 5, 3: 3, 2: 2, 1: 1}.get(px[1], 2), glint=True)
    for x, y in ((6, 3), (7, 4), (8, 4), (9, 5), (3, 8), (4, 9), (4, 10), (5, 11), (11, 8), (11, 9), (12, 10)):
        c.set(x, y, ORANGE_GLOW, 4, glint=True)
    c.set(8, 4, YELLOW_GLOW, 5, glint=True)
    c.set(4, 9, ORANGE_GLOW, 5, glint=True)
    return c



# ---------------------------------------------------------------- 戴森球阶段（ADR-0016）

@texture("item/critical_photon", refs=("item/nether_star", "item/glowstone_dust"))
def critical_photon(rng):
    """临界光子：一团白金色的光球，四个方向放出光芒，斜的四道短一点。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 3.4):
        d = math.hypot(x - 7.5, y - 7.5)
        c.set(x, y, YELLOW_GLOW, 5 if d < 1.2 else 4 if d < 2.4 else 3)
    for i in range(4, 8):
        for x, y in ((7, 7 - i), (8, 7 - i), (7, 8 + i), (8, 8 + i), (7 - i, 7), (7 - i, 8), (8 + i, 7), (8 + i, 8)):
            c.set(x, y, YELLOW_GLOW, 4 if i < 6 else 3, glint=True)
    for i in range(3, 6):
        for x, y in ((7 - i, 7 - i), (8 + i, 7 - i), (7 - i, 8 + i), (8 + i, 8 + i)):
            c.set(x, y, AMBER_GLOW, 4 if i < 5 else 3, glint=True)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        c.set(x, y, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/solar_sail")
def solar_sail(rng):
    """太阳帆：一片六边形的金色薄膜，中间十字形的骨架，膜上几道斜的反光。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 8)
            if dx * 0.5 + dy * 0.866 <= 6.6 and dx <= 6.8:
                k = (x + y) % 6
                c.set(x, y, GOLD_FOIL, 4 if k in (0, 1) else 3 if y < 8 else 2)
    c.outline()
    for i in range(2, 14):
        c.set(i, 7, TI_ALLOY, 5 if i < 8 else 4, glint=True)
        c.set(7, i, TI_ALLOY, 5 if i < 8 else 4, glint=True)
    c.set(7, 7, WHITE, WHITE.top, glint=True)
    return c


@texture("item/dyson_sphere_component")
def dyson_sphere_component(rng):
    """戴森球组件：一块六边形的壳板，斜着看：钛合金的边框，里面金色的集光膜，下面露出深色的厚度，
    两个角上亮着青色的定位灯。"""
    c = Canvas()
    def hexagon(x, y, cy, r=7.0):
        dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - cy) / 0.62
        return dx * 0.5 + dy * 0.866 <= r * 0.866 and dx <= r
    top = {(x, y) for y in range(16) for x in range(16) if hexagon(x, y, 6.6)}
    for d in (3, 2, 1):
        for x, y in top:
            c.set(x, y + d, GRAPHITE, 2 if d > 1 else 3)
    inner = {(x, y) for x, y in top if hexagon(x, y, 6.6, 5.4)}
    for x, y in top:
        c.set(x, y, TI_ALLOY, 5 if y < 7 else 4)
    for x, y in inner:
        k = (x - y) % 5
        c.set(x, y, GOLD_FOIL, 4 if k == 0 else 3 if y < 7 else 2)
    c.outline()
    c.set(1, 7, CYAN_GLOW, 5, glint=True)
    c.set(14, 7, CYAN_GLOW, 5, glint=True)
    c.set(6, 3, GOLD_FOIL, GOLD_FOIL.top, glint=True)
    return c


@texture("item/frame_material")
def frame_material(rng):
    """框架材料：一段桁架，上下两根钛合金的弦杆，中间是竖杆和交叉的斜撑。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "................",
        ".66666666666665.",
        ".44444444444443.",
        ".4e....e4....e4.",
        ".4.e..e.4e..e.4.",
        ".4..ee..4.ee..4.",
        ".4..ee..4.ee..4.",
        ".4.e..e.4e..e.4.",
        ".4e....e4....e4.",
        ".4......4.....4.",
        ".55555555555554.",
        ".33333333333332.",
        "................",
        "................",
    ], {"0123456": TI_ALLOY, "abcde": TI_ALLOY}, 0, 0)
    inner = {(x, y): c.get(x, y) for x, y in c.cells(2, 5, 13, 11) if c.get(x, y)}
    c.outline(lit=2, shadow=0)
    # 一个像素宽的杆子不能被描边吃掉：中间的斜撑、竖杆描边后再画回去
    for (x, y), px in inner.items():
        c.set(x, y, px[0], px[1], glint=True)
    c.set(2, 3, TI_ALLOY, TI_ALLOY.top, glint=True)
    return c


@texture("item/graviton_lens", refs=("item/ender_eye",))
def graviton_lens(rng):
    """引力透镜：钛合金镜框里一片淡蓝的镜片，中心一个小黑洞，四周一圈被扭弯的亮光，左边亮右边暗。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 6.6):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if d > 5.6:
            c.set(x, y, TI_ALLOY, 5 if dx + dy < 0 else 2)
        elif d > 2.9:
            # 镜片里的光被拉成一圈一圈的弧
            c.set(x, y, GLASS, 3 if int(d * 1.3 + math.atan2(dy, dx) * 0.6) % 2 else 2)
        elif d > 1.9:
            c.set(x, y, AMBER_GLOW, 4 if dx - dy < 0 else 2)
        else:
            c.set(x, y, OIL, 0)
    c.outline()
    c.set(4, 4, GLASS, GLASS.top, glint=True)
    c.set(5, 3, GLASS, GLASS.top, glint=True)
    return c


@texture("item/photon_combiner")
def photon_combiner(rng):
    """光子合并器：银色的框里一块棱镜，左边三束彩色的光汇进去，右边出来一束白光。"""
    c = Canvas()
    c.panel(1, 3, 14, 12, STEEL, 4)
    c.recess(2, 4, 13, 11, SCREEN, 1)
    for i, r in enumerate((RED_GLOW, GREEN_GLOW, BLUE_GLOW)):
        y = 5 + i * 2
        for x in range(3, 7):
            c.set(x, y + (x - 3) * (1 - i) // 3, r, 4, glint=True)
    c.draw([".5.", "543", "5432"], {"012345": GLASS}, 6, 6)
    for x in range(9, 13):
        c.set(x, 8, WHITE_GLOW, 5, glint=True)
        c.set(x, 7, YELLOW_GLOW, 3, glint=True)
    c.outline()
    c.set(2, 4, STEEL, 7, glint=True)
    return c


@texture("item/plane_filter")
def plane_filter(rng):
    """位面过滤器：圆形的滤镜，钛合金的镜框，镜片里几圈紫色的干涉条纹。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 6.6):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 5.4:
            c.set(x, y, TI_ALLOY, 5 if x + y < 15 else 2)
        else:
            band = int(d * 1.5 + (x - y) * 0.25) % 3
            c.set(x, y, VIOLET, (2, 4, 5)[band])
    c.outline()
    c.set(5, 4, WHITE, WHITE.top, glint=True)
    c.set(4, 5, VIOLET_GLOW, 5, glint=True)
    return c


@texture("item/quantum_chip")
def quantum_chip(rng):
    """量子芯片：深紫色的大芯片，四边金色的引脚，中间一块发紫光的量子核心，四角青色的定位点。"""
    c = Canvas()
    c.panel(2, 2, 13, 13, VIOLET, 1)
    c.recess(5, 5, 10, 10, FOG, 1)
    c.shade_disc(7.5, 7.5, 1.8, VIOLET_GLOW, 3, amp=1.6)
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, CYAN_GLOW, 4, glint=True)
    c.outline()
    for i in range(3, 13, 2):
        for x, y in ((i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)):
            c.set(x, y, BRASS, 5 if (x < 2 or y < 2) else 4, glint=True)
    return c


@texture("item/small_carrier_rocket", refs=("item/firework_rocket",))
def small_carrier_rocket(rng):
    """小型运载火箭：斜着往右上飞，白色的箭体，红色的鼻锥和尾翼，中间一道蓝色的舱段，尾部喷火。"""
    c = Canvas()
    rod(c, [(3.4, 5.2, 2.3, RED, 3), (5.2, 10.4, 2.3, WHITE, 4), (10.4, 11.6, 2.3, BLUE, 3),
            (11.6, 13.0, 2.0, WHITE, 4), (13.0, 14.4, 1.4, RED, 4), (14.4, 15.4, 0.8, RED, 4)])
    # 尾翼：从尾部往两边斜着伸出去
    for x, y, lv in ((3, 10, 3), (2, 10, 3), (2, 9, 2), (6, 13, 2), (6, 14, 1), (5, 14, 2)):
        c.set(x, y, RED, lv)
    prune(c)
    c.outline()
    # 尾焰
    for x, y, r, lv in ((4, 12, YELLOW_GLOW, 5), (3, 12, ORANGE_GLOW, 4), (3, 13, ORANGE_GLOW, 5),
                        (4, 13, ORANGE_GLOW, 4), (2, 13, ORANGE_GLOW, 3), (3, 14, ORANGE_GLOW, 3), (2, 14, ORANGE_GLOW, 2)):
        c.set(x, y, r, lv, glint=True)
    c.set(8, 6, BLUE, 5, glint=True)
    return c


@texture("item/titanium_glass", refs=("block/glass",))
def titanium_glass(rng):
    """钛化玻璃：一块带淡蓝色调的厚玻璃板，钛合金的包边，两道斜的高光。"""
    c = Canvas()
    c.panel(1, 1, 14, 14, TI_ALLOY, 4)
    c.rect(3, 3, 12, 12, GLASS, 2)
    c.hline(3, 12, 3, GLASS, 1)
    c.vline(3, 3, 12, GLASS, 1)
    for i in range(5):
        c.set(5 + i, 9 - i, GLASS, GLASS.top - (1 if i == 4 else 0))
    for i in range(3):
        c.set(8 + i, 11 - i, GLASS, 4)
    c.outline()
    c.set(2, 2, TI_ALLOY, TI_ALLOY.top, glint=True)
    return c


# ---------------------------------------------------------------- 终局

@texture("item/antimatter")
def antimatter(rng):
    """反物质：磁约束的玻璃球，上下两只钢的约束夹，球心悬着一小团发亮的粉紫色反物质，四周一圈光晕。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 8.0, 5.6):
        d = math.hypot(x - 7.5, y - 8.0)
        if d < 1.8:
            c.set(x, y, MAGENTA_GLOW, 5 if d < 1.0 else 4)
        elif d < 3.0:
            c.set(x, y, VIOLET_GLOW, 3 if x + y < 15 else 2)
        else:
            c.set(x, y, GLASS, 2 if x + y < 14 else 1)
    c.cylinder(5, 1, 10, 2, STEEL, 4)
    c.cylinder(5, 14, 10, 14, STEEL, 3)
    c.outline()
    c.set(4, 5, GLASS, GLASS.top, glint=True)
    c.set(5, 4, GLASS, 4, glint=True)
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/annihilation_constraint_sphere")
def annihilation_constraint_sphere(rng):
    """湮灭约束球：银色的金属球，两道交叉的约束环，环缝里透出紫光。"""
    c = Canvas()
    c.shade_disc(7.5, 7.5, 6.2, STEEL, 4, amp=2.2)
    for x in range(1, 15):
        if c.get(x, 7):
            c.set(x, 7, STEEL, 6 if x < 8 else 5)
            c.set(x, 8, VIOLET_GLOW, 4 if x < 8 else 3, glint=True)
    for y in range(1, 15):
        x = round(7.5 + (y - 7.5) * 0.45)
        if c.get(x, y):
            c.set(x, y, MAGENTA_GLOW, 4, glint=True)
            c.set(x + 1, y, STEEL, 3)
    c.outline()
    c.set(7, 8, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/blueprint", refs=("item/map", "item/paper"))
def blueprint(rng):
    """蓝图：一张蓝色的图纸，浅色的网格，中间画着一台机器和一段传送带，右上角卷起来。"""
    c = Canvas()
    c.rect(2, 2, 13, 13, BLUE, 3)
    for x, y in c.cells(2, 2, 13, 13):
        if (x - 2) % 3 == 0 or (y - 2) % 3 == 0:
            c.set(x, y, BLUE, 4)
    c.frame(5, 5, 10, 9, WHITE, 6)
    c.rect(7, 7, 8, 7, AMBER, 5)
    c.hline(3, 12, 11, WHITE, 5)
    # 卷起来的角
    for i in range(3):
        for j in range(3 - i):
            c.clear(13 - j, 2 + i)
    c.draw(["ab.", "cab", ".ca"], {"abc": BLUE}, 11, 2)
    c.set(11, 2, BLUE, 6)
    c.set(12, 3, BLUE, 6)
    c.set(13, 4, BLUE, 5)
    c.outline()
    return c


@texture("item/guide", refs=("item/book", "item/writable_book"))
def guide(rng):
    """手册：深蓝封面的硬皮书，左边书脊，右边和下边露出纸页，封面中间一个青色的圆环。"""
    c = Canvas()
    c.rect(3, 1, 12, 13, BLUE, 1)
    c.vline(3, 1, 13, BLUE, 0)
    c.vline(4, 1, 13, BLUE, 3)
    c.hline(5, 12, 1, BLUE, 2)
    c.vline(13, 2, 14, CREAM, 3)
    c.hline(5, 13, 14, CREAM, 2)
    c.ring(8.5, 7.0, 2.9, CYAN_GLOW, 4)
    c.set(8, 7, CYAN_GLOW, 5, glint=True)
    c.set(9, 7, CYAN_GLOW, 3, glint=True)
    c.outline()
    c.set(5, 2, BLUE, 4, glint=True)
    return c
