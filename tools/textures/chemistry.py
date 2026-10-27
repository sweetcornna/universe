"""M20 化学的贴图（ADR-0031、ADR-0036）：气体、溶液、催化剂、镍锭、钒渣、甲烷液氧，大气提取器和电解槽。

气体和溶液接着用中间产品那套流体罐（items.canister）：液体有液面，气体充满整只罐。气体罐中间那道环涂成
工业气瓶的识别色（EN 1089-3）：氮黑、二氧化碳灰、氩深绿、氦棕，可燃的红，有毒、腐蚀的黄，一氧化碳又毒又可燃，
半红半黄。罐里的颜色是为了一眼分得开，现实里这些气体大多无色，只有氯气是黄绿的。
"""
from __future__ import annotations

import math

from canvas import Canvas
from color import ramp
from items import canister, flecks, ingot, lump, tidy
from machines import screen
from palette import (AMBER, BASALT, BLUE, COPPER, CREAM, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN, HAZARD, ICE,
                     MOSS, ORANGE, PINK, RED, RUST, STEEL, TITANIUM, VIOLET, WHITE, WHITE_GLOW, WOOD, register)
from parts import casing, fan, status_lights
from recipe import texture

# 这组自己用的色阶
NITROGEN = register(ramp("chem_nitrogen", 285, 0.045, n=6, lo=0.50, hi=0.93, shift=8))      # 氮：发灰的淡紫
CARBON_DIOXIDE = register(ramp("chem_co2", 85, 0.012, n=6, lo=0.48, hi=0.92, shift=8))      # 二氧化碳：浅灰
CARBON_MONOXIDE = register(ramp("chem_co", 250, 0.022, n=6, lo=0.40, hi=0.84, shift=8))     # 一氧化碳：深一点的蓝灰
AMMONIA = register(ramp("chem_ammonia", 172, 0.055, n=6, lo=0.50, hi=0.93, shift=8))        # 氨：淡薄荷绿
CHLORINE = register(ramp("chem_chlorine", 118, 0.120, n=6, lo=0.42, hi=0.92, shift=10))     # 氯气：真的黄绿色
NITRIC = register(ramp("chem_nitric", 92, 0.070, n=6, lo=0.52, hi=0.95, shift=8))           # 硝酸：放久了发黄
CUPRIC = register(ramp("chem_cupric", 228, 0.125, n=6, lo=0.30, hi=0.86, shift=10))         # 硫酸铜溶液的蓝
CYANIDE = register(ramp("chem_cyanide", 240, 0.055, n=6, lo=0.50, hi=0.93, shift=8))      # 氰化氢：淡的普鲁士蓝
NICKEL = register(ramp("chem_nickel", 92, 0.018, n=7, lo=0.30, hi=0.93, shift=16))          # 镍：比钢暖一点的银白


# ---------------------------------------------------------------- 气体

def gas(content, base: int, band, band_base: int, band2=None) -> Canvas:
    """一只气体罐，中间的环涂识别色；band2 给了就是左右两半两种色（一氧化碳）。"""
    c = canister(content, base, gas=True)
    c.cylinder(4, 8, 11, 8, band, band_base)
    if band2 is not None:
        for x in range(8, 12):
            c.recolor(x, 8, band2)
    return c


@texture("item/nitrogen", refs=("item/potion", "item/glass_bottle"))
def nitrogen(rng):
    return gas(NITROGEN, 3, GRAPHITE, 2)


@texture("item/carbon_dioxide")
def carbon_dioxide(rng):
    return gas(CARBON_DIOXIDE, 3, STEEL, 6)


@texture("item/carbon_monoxide")
def carbon_monoxide(rng):
    return gas(CARBON_MONOXIDE, 3, HAZARD, 3, band2=RED)


@texture("item/methane")
def methane(rng):
    return gas(ORANGE, 4, RED, 3)


@texture("item/argon")
def argon(rng):
    return gas(VIOLET, 4, GREEN, 2)


@texture("item/helium")
def helium(rng):
    return gas(PINK, 4, WOOD, 2)


@texture("item/sulfur_dioxide")
def sulfur_dioxide(rng):
    return gas(HAZARD, 4, HAZARD, 2)


@texture("item/ammonia")
def ammonia(rng):
    return gas(AMMONIA, 3, HAZARD, 3)


@texture("item/hydrogen_cyanide")
def hydrogen_cyanide(rng):
    # 又毒又可燃：半黄半红，和一氧化碳一样
    return gas(CYANIDE, 3, HAZARD, 3, band2=RED)


@texture("item/chlorine")
def chlorine(rng):
    return gas(CHLORINE, 3, HAZARD, 3)


# ---------------------------------------------------------------- 溶液

@texture("item/sodium_hydroxide", refs=("item/milk_bucket",))
def sodium_hydroxide(rng):
    # 氯碱槽出来的是三成多的碱液，乳白
    return canister(WHITE, 4, 7)


@texture("item/nitric_acid")
def nitric_acid(rng):
    c = canister(NITRIC, 3, 7)
    # 液面上方一缕红棕色的二氧化氮
    for x, y in ((6, 5), (7, 5), (8, 6)):
        c.set(x, y, RUST, 4)
    return c


@texture("item/titanium_tetrachloride")
def titanium_tetrachloride(rng):
    # 无色透明、碰到空气就冒白烟的液体
    c = canister(TITANIUM, 3, 8)
    for x, y in ((6, 6), (7, 5), (8, 6), (9, 5), (7, 7)):
        c.set(x, y, WHITE, 5 if y == 5 else 4)
    return c


@texture("item/copper_leach_solution")
def copper_leach_solution(rng):
    return canister(CUPRIC, 4, 6)


@texture("item/gold_leach_solution")
def gold_leach_solution(rng):
    c = canister(GOLD_FOIL, 3, 7)
    c.set(9, 10, GOLD_FOIL, GOLD_FOIL.top, glint=True)
    return c


# ---------------------------------------------------------------- 固体

@texture("item/nickel_ingot", refs=("item/iron_ingot", "item/netherite_ingot"))
def nickel_ingot(rng):
    return ingot(NICKEL)


@texture("item/vanadium_slag", rough=True, refs=("item/raw_iron",))
def vanadium_slag(rng):
    # 玻璃质的黑渣，里面夹着橙色的钒
    c = lump(1, BASALT, 3)
    flecks(c, rng, ORANGE, 4, 3)
    return c


@texture("item/ammonium_nitrate")
def ammonium_nitrate(rng):
    # 一袋化肥：扎口的编织袋，中间一道绿色的标签，标签上三颗白色的颗粒
    c = Canvas()
    c.cylinder(3, 5, 12, 14, CREAM, 3)
    c.cylinder(4, 4, 11, 4, CREAM, 3)
    c.cylinder(5, 3, 10, 3, CREAM, 3)
    # 扎口：一道绳子，上面翘出两个袋角
    c.hline(6, 9, 2, WOOD, 2)
    c.set(6, 2, WOOD, 3)
    for x in (5, 6, 9, 10):
        c.set(x, 1, CREAM, 4 if x < 8 else 2)
    c.cylinder(3, 8, 12, 10, GREEN, 3)
    for x, y in ((6, 9), (8, 9), (10, 9)):
        c.set(x, y, WHITE, 5, glint=True)
    for x, y in ((3, 14), (12, 14)):
        c.clear(x, y)
    c.outline()
    return c


@texture("item/methalox_fuel", refs=("item/fire_charge",))
def methalox_fuel(rng):
    # 两只并排的白罐：左边甲烷（橙环），右边液氧（蓝环，下半截结霜），顶上一根连管和阀门
    c = Canvas()
    c.cylinder(2, 4, 7, 13, WHITE, 4)
    c.cylinder(8, 4, 13, 13, WHITE, 4)
    c.cylinder(3, 3, 6, 3, WHITE, 4)
    c.cylinder(9, 3, 12, 3, WHITE, 4)
    c.cylinder(2, 6, 7, 7, ORANGE, 3, amp=1)
    c.cylinder(8, 6, 13, 7, BLUE, 3, amp=1)
    for x, y in ((9, 11), (10, 12), (12, 11), (11, 10), (9, 13), (12, 13)):
        c.set(x, y, WHITE, WHITE.top)
    c.hline(4, 11, 1, STEEL, 4)
    c.vline(4, 1, 2, STEEL, 5)
    c.vline(11, 1, 2, STEEL, 3)
    c.set(7, 0, STEEL, 5)
    c.set(8, 0, STEEL, 3)
    c.cylinder(2, 14, 13, 14, STEEL, 3)
    for x, y in ((2, 4), (7, 4), (8, 4), (13, 4)):
        c.clear(x, y)
    c.outline()
    return c


# ---------------------------------------------------------------- 催化剂：一小堆颗粒
#
# 熔铁催化剂是大小不一的黑色碎粒，五氧化二钒做成中间有孔的环（气流阻力小），镍催化剂压成小圆片。
# 和粗矿一样一颗一颗按球面打光，前面的压在后面的上面，交界处后面那颗暗一级；每颗一个高光点，看得出是一粒一粒的。

# 堆成一小堆：(圆心 x, 圆心 y, 半径)，先画后面的
HEAP = [(8.0, 4.4, 2.3), (5.4, 7.4, 2.3), (10.6, 7.4, 2.3), (3.2, 10.8, 2.2), (8.0, 10.2, 2.4), (12.8, 10.8, 2.2),
        (5.6, 13.0, 2.0), (10.4, 13.0, 2.0)]


def heap(r, base: int, rng, kind: str) -> Canvas:
    c = Canvas()
    drawn = set()
    for cx, cy, rad in HEAP:
        if kind == "granule":
            rad += rng.random() * 0.6 - 0.3
            cx += rng.random() * 0.6 - 0.3
        cells = c.disc_cells(cx, cy, rad)
        best, best_v = None, -9
        for x, y in cells:
            nx, ny = (x + 0.5 - cx) / (rad + 0.4), (y + 0.5 - cy) / (rad + 0.4)
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            v = -0.58 * nx - 0.62 * ny + 0.53 * nz
            if kind == "tablet":
                # 圆片：上半是平的顶面，一样亮；下半是侧面，往下变暗
                lv = base + 1 if y + 0.5 < cy else base - (1 if y + 0.5 < cy + rad * 0.6 else 2)
            else:
                lv = base + (1 if v > 0.6 else 0 if v > 0.25 else -1 if v > -0.05 else -2)
            c.set(x, y, r, lv)
            if v > best_v:
                best, best_v = (x, y), v
        new = set(cells)
        for x, y in drawn - new:
            if any((x + dx, y + dy) in new for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.shift(x, y, -1)
        drawn |= new
        if kind == "ring":
            # 环中间的孔：深两级，孔的右下沿受光
            hx, hy = round(cx - 0.5), round(cy - 0.5)
            c.set(hx, hy, r, base - 3)
            c.set(hx + 1, hy + 1, r, base + 1)
        elif best:
            c.set(best[0], best[1], r, base + 2, glint=True)
    tidy(c)
    c.outline()
    return c


# 环和圆片照模板画（h 高光、t 受光、s 本色、d 背光、k 最暗、x 环孔里看到的内壁），三颗摆成一堆
RING = [
    ".tttt.",
    "thddts",
    "tdxxts",
    "sdxxsd",
    "sttsdk",
    ".ddkk.",
]
TABLET = [
    ".ttttt.",
    "thtttts",
    "sttttsd",
    "dssssdk",
    ".kkkkk.",
]
LEVELS = {"h": 2, "t": 1, "s": 0, "d": -1, "k": -2, "x": -3}


def three(rows, r, base: int, spots) -> Canvas:
    c = Canvas()
    for ox, oy in spots:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in LEVELS:
                    c.set(ox + x, oy + y, r, base + LEVELS[ch], glint=ch == "h")
    c.outline()
    return c


@texture("item/iron_catalyst")
def iron_catalyst(rng):
    return heap(GRAPHITE, 4, rng, "granule")


@texture("item/vanadium_catalyst")
def vanadium_catalyst(rng):
    return three(RING, AMBER, 4, ((5, 2), (1, 8), (9, 9)))


@texture("item/nickel_catalyst")
def nickel_catalyst(rng):
    return three(TABLET, MOSS, 4, ((5, 3), (1, 8), (8, 10)))


# ---------------------------------------------------------------- 大气提取器：压缩机加冷箱
#
# 正面左边是冷箱的液位窗，里面是分馏出来的液态空气，工作时透出青光；右边一块压力表和读数屏。
# 侧面是进气的百叶和一根结霜的冷管，顶上一台大风扇吸气，工作时四角的灯亮。

def gauge(c: Canvas, cx: float, cy: float, rad: float, on: bool):
    """一块压力表：钢圈、白表盘、红指针。开着指针打到右上，关着落回左下。"""
    for x, y in c.disc_cells(cx, cy, rad):
        r = math.hypot(x - cx, y - cy)
        if r > rad - 0.9:
            toward = (x - cx) * -0.58 + (y - cy) * -0.62
            c.set(x, y, STEEL, 5 if toward > 0.6 else (2 if toward < -0.6 else 3))
        else:
            c.set(x, y, WHITE, 5)
    a = math.radians(-45 if on else 135)
    for t in (0.0, 0.8, 1.5):
        c.set(round(cx - 0.5 + math.cos(a) * t), round(cy - 0.5 - math.sin(a) * t), RED, 3)


def extractor_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    c.recess(2, 4, 7, 13, STEEL, 4)
    for x, y in c.cells(3, 5, 6, 12):
        if y >= 9:
            if on:
                c.set(x, y, CYAN_GLOW, 4 if y == 9 else 3)
            else:
                c.set(x, y, GLASS, 3 if y == 9 else 2)
        else:
            c.set(x, y, GLASS, 1)
    if on:
        for x, y in ((4, 11), (5, 10), (3, 12)):
            c.set(x, y, CYAN_GLOW, 5, glint=True)
    else:
        c.set(3, 5, GLASS, 3, glint=True)
    gauge(c, 10.5, 6.5, 2.8, on)
    screen(c, 9, 10, 12, 12, on)
    return c


@texture("block/atmospheric_extractor_front", refs=("block/blast_furnace_front", "block/observer_front"))
def atmospheric_extractor_front(rng):
    return extractor_face(rng, False)


@texture("block/atmospheric_extractor_front_on", glow=True)
def atmospheric_extractor_front_on(rng):
    return extractor_face(rng, True)


@texture("block/atmospheric_extractor_side")
def atmospheric_extractor_side(rng):
    c = casing(rng)
    c.vent(3, 3, 12, 8, STEEL, 4)
    # 一根横穿的冷管：两道箍，中间一段结了霜
    c.cylinder(1, 10, 14, 12, STEEL, 4, vertical=False)
    for x in (3, 12):
        c.vline(x, 10, 12, STEEL, 2)
    for x, y in ((6, 10), (7, 11), (8, 10), (9, 11), (6, 12), (8, 12)):
        c.set(x, y, ICE, 4)
    return c


def extractor_top(rng, on: bool) -> Canvas:
    c = casing(rng)
    fan(c, 7.5, 7.5, 5.2, blades=5)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        if on:
            c.set(x, y, CYAN_GLOW, 5, glint=True)
        else:
            c.set(x, y, GRAPHITE, 1)
    return c


@texture("block/atmospheric_extractor_top", refs=("block/crafter_top",))
def atmospheric_extractor_top(rng):
    return extractor_top(rng, False)


@texture("block/atmospheric_extractor_top_on", glow=True)
def atmospheric_extractor_top_on(rng):
    return extractor_top(rng, True)


# ---------------------------------------------------------------- 电解槽：玻璃的电解池
#
# 正面是电解池的观察窗：左边铜的阴极，右边钛的阳极，中间一道隔膜。工作时两极冒气泡，阴极出的氢是阳极出的氧的两倍。
# 上面两个接线柱，红的是正极。侧面是铜母排和两根出气管（氢红环、氧白环），顶上两个集气罩，氢的大、氧的小。

def cell_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    c.recess(2, 4, 13, 12, STEEL, 4)
    for x, y in c.cells(3, 5, 12, 11):
        if y < 7:
            c.set(x, y, GRAPHITE, 1)
        else:
            c.set(x, y, BLUE, 3 if y == 7 else 2)
    # 电极：两格宽的板，从顶上插下来；隔膜偏右，阴极那边的格子大，氢多
    c.cylinder(4, 4, 5, 10, COPPER, 4, amp=1)
    c.cylinder(10, 4, 11, 10, STEEL, 5, amp=1)
    c.vline(8, 5, 11, STEEL, 6)
    if on:
        for x, y in ((3, 9), (6, 8), (3, 6), (6, 5), (7, 10), (3, 11)):
            c.set(x, y, WHITE_GLOW, 4, glint=True)
        for x, y in ((9, 8), (12, 6), (9, 10)):
            c.set(x, y, WHITE_GLOW, 4, glint=True)
    # 接线柱：左边负极，右边红的正极
    c.set(4, 3, GRAPHITE, 1)
    c.set(11, 3, RED, 4)
    return c


@texture("block/electrolyzer_front", refs=("block/brewing_stand", "block/cauldron_side"))
def electrolyzer_front(rng):
    return cell_face(rng, False)


@texture("block/electrolyzer_front_on", glow=True)
def electrolyzer_front_on(rng):
    return cell_face(rng, True)


@texture("block/electrolyzer_side")
def electrolyzer_side(rng):
    c = casing(rng)
    # 铜母排横穿上面，两根出气管竖着下来
    c.cylinder(1, 3, 14, 4, COPPER, 4, vertical=False)
    for x0, band in ((4, RED), (10, WHITE)):
        c.cylinder(x0, 5, x0 + 1, 13, STEEL, 4)
        c.cylinder(x0, 8, x0 + 1, 9, band, 3)
    c.panel(6, 7, 8, 11, STEEL, 3)
    return c


@texture("block/electrolyzer_top", refs=("block/cauldron_top",))
def electrolyzer_top(rng):
    c = casing(rng)
    # 两个鼓起来的集气罩，底下一圈暗缝；罩顶的出气口涂识别色（氢红、氧白）
    for cx, cy, rad, cap in ((5.5, 6.5, 3.4, RED), (11.5, 6.0, 2.3, WHITE)):
        c.ring(cx, cy, rad + 0.7, GRAPHITE, 1)
        c.shade_disc(cx, cy, rad, STEEL, 5, spec=False)
        c.set(round(cx - 0.5), round(cy - 0.5), cap, 4, glint=True)
    c.cylinder(1, 11, 14, 12, COPPER, 4, vertical=False)
    return c
