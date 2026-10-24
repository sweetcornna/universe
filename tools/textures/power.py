"""发电和输电设备的贴图（ADR-0031）。

蓄电器、能量枢纽、地热站、微型聚变电站是一格的设备，用共用的钢机壳；火电厂、风机、太阳能板、
电力塔、人造恒星是多元素的模型，每张图只有模型用到的那几块要画好（见各函数的说明）。
电的颜色：电力塔和能量是青色，无线输电塔是绿色，卫星配电站是金色；热是橙色。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import register as palette_register
from palette import (AMBER_GLOW, BASALT, BRASS, CONCRETE, COPPER, CRIMSON, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN,
                     GREEN_GLOW, HAZARD, IRON, ORANGE, ORANGE_GLOW, RED, RUBBER, SCREEN, STEEL, TEAL, WHITE,
                     WHITE_GLOW, YELLOW_GLOW)
from parts import bolts, casing, status_lights
from recipe import texture


# ---- 蓄电器：正面一根竖着的电量表，四格从下往上亮 ----

def accumulator_front(rng: random.Random, level: int) -> Canvas:
    c = casing(rng)
    # 电量表：凹进去的深色玻璃窗，四格电池单元，格子之间是暗缝
    c.recess(4, 2, 11, 14, STEEL, 4)
    c.rect(5, 3, 10, 13, SCREEN, 1)
    for i in range(4):
        y0 = 12 - i * 3          # 第 0 格在最下面
        if i < level:
            # 亮着：上面一行亮、下面一行稍暗，左上角一点高光
            c.rect(5, y0, 10, y0, GREEN_GLOW, 4)
            c.rect(5, y0 + 1, 10, y0 + 1, GREEN_GLOW, 3)
            c.set(5, y0, GREEN_GLOW, 5)
        else:
            # 没电：熄灭的灯格，发暗的绿玻璃，看得出格子在那儿
            c.rect(5, y0, 10, y0, GREEN, 1)
            c.rect(5, y0 + 1, 10, y0 + 1, GREEN, 0)
    # 窗外右侧的刻度：每格一道
    for i in range(4):
        c.set(12, 12 - i * 3, STEEL, 6, glint=True)
    # 顶上的标牌：正负极的颜色
    c.set(6, 1, RED, 4, glint=True)
    c.set(9, 1, GRAPHITE, 4, glint=True)
    bolts(c, 1)
    return c


for _lv in range(5):
    def _acc(rng, _lv=_lv):
        return accumulator_front(rng, _lv)
    texture(f"block/accumulator_front_{_lv}", refs=("block/redstone_lamp_on",) if _lv == 4 else ())(_acc)


@texture("block/accumulator_side")
def accumulator_side(rng):
    c = casing(rng)
    # 散热鳍片：竖条，每片左边受光、右边是深缝
    c.recess(3, 3, 12, 12, STEEL, 3)
    for x in range(4, 12, 2):
        c.vline(x, 4, 11, STEEL, 5)
        c.vline(x + 1, 4, 11, STEEL, 2)
    c.hline(4, 11, 4, STEEL, 6)
    # 下面一条警示带：高压
    c.stripes(3, 13, 12, 13, HAZARD, 4, GRAPHITE, 1, period=4, width=2)
    bolts(c, 1)
    return c


# 接线柱（4×4）：数字是绝缘圈的级数，a–c 是黄铜柱头（4–6 级），左上受光
TERMINAL = [
    ".43.",
    "4cb2",
    "3ba1",
    ".21.",
]


@texture("block/accumulator_top")
def accumulator_top(rng):
    c = casing(rng)
    # 两个接线柱：红色绝缘圈的正极、深色的负极，柱头是黄铜；下面标正负号，再下面一排三个排气阀
    c.draw(TERMINAL, {"01234": RED, "wxyzabc": BRASS}, 3, 3)
    c.draw(TERMINAL, {"01234": STEEL, "wxyzabc": BRASS}, 9, 3)
    for x, y in ((5, 8), (4, 9), (5, 9), (6, 9), (5, 10)):
        c.set(x, y, RED, 4, glint=True)
    c.hline(10, 12, 9, STEEL, 1)
    for x in (4, 7, 10):
        c.bolt(x, 11, STEEL, 3)
    bolts(c, 1)
    return c


# ---- 一格的发电设备：观察窗里是发电的东西 ----

# 地热井（8×7，窗内左上角起）：上面是黑的井腔，中间一根钻杆插进熔岩；下面是熔岩池，深色的壳浮在亮的岩浆上。
# 0–2 玄武岩，S–Z 钢（0–7 级），a–f 橙色的光（0–5），k、l 黄色的光（4、5），m–o 暗红（0–2）
GEO_ON = [
    "100YV001",
    "10aXUa01",
    "1abXUba1",
    "bcdkldcb",
    "dd1dkdd1",
    "d12dde1d",
    "cdd1cdd1",
]
GEO_OFF = [
    "000XU000",
    "000XU000",
    "100XU001",
    "mnnWTnnm",
    "n1mnn01m",
    "m11mn1mm",
    "mmm1mmm0",
]
GEO_LEGEND = {"012": GRAPHITE, "STUVWXYZ": STEEL, "abcdef": ORANGE_GLOW, "ghijkl": YELLOW_GLOW, "mno": CRIMSON}


def geothermal_front(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    # 炉口：厚的钢框，里面是熔岩井，两根耐热的格栅竖着挡在前面
    c.recess(3, 4, 12, 12, STEEL, 4)
    c.draw(GEO_ON if on else GEO_OFF, GEO_LEGEND, 4, 5)
    # 热的颜色：下面一条橙色涂装
    c.rect(3, 13, 12, 13, ORANGE, 3)
    c.set(3, 13, ORANGE, 4)
    c.set(12, 13, ORANGE, 2)
    bolts(c, 1)
    return c


@texture("block/geothermal_power_station_front", refs=("block/blast_furnace_front_on", "block/magma"))
def geothermal_front_off(rng):
    return geothermal_front(rng, False)


@texture("block/geothermal_power_station_front_on", glow=True)
def geothermal_front_on(rng):
    return geothermal_front(rng, True)


# 压力表（5×5）：钢圈，白色表盘，红色指针指向右上
GAUGE = [
    ".WXW.",
    "W55rV",
    "X5g4U",
    "W443U",
    ".VUU.",
]


@texture("block/geothermal_power_station_side")
def geothermal_side(rng):
    c = casing(rng)
    # 换热器：两根竖着的铜管，上下两道法兰；中间一块压力表
    for x0 in (3, 10):
        c.cylinder(x0, 3, x0 + 2, 12, COPPER, 3)
        for y in (4, 11):
            c.hline(x0 - 0 , x0 + 2, y, STEEL, 5)
            c.set(x0 + 2, y, STEEL, 3)
    c.draw(GAUGE, {"STUVWXYZ": STEEL, "0123456": WHITE, "r": RED, "g": GRAPHITE}, 5, 4)
    # 下面一排通风口
    c.vent(6, 10, 9, 12, STEEL, 4)
    return c


# 手轮（7×7）：一圈轮缘、十字的轮辐、中间钢的阀杆
HANDWHEEL = [
    "..443..",
    ".4.4.3.",
    "4..4..2",
    "444Y332",
    "3..3..1",
    ".3.2.1.",
    "..221..",
]


@texture("block/geothermal_power_station_top")
def geothermal_top(rng):
    c = casing(rng)
    # 井口：一圈螺栓压着的盖板，中间是阀门和红色的手轮
    c.shade_disc(7.5, 7.5, 5.2, STEEL, 4, amp=1.5, spec=False)
    c.ring(7.5, 7.5, 5.2, STEEL, 2)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        c.set(round(7.5 + 4.2 * math.cos(a)), round(7.5 + 4.2 * math.sin(a)), STEEL, 6, glint=True)
    # 阀门的红色手轮
    c.draw(HANDWHEEL, {"0123456": RED, "STUVWXYZ": STEEL}, 4, 4)
    return c


def exchanger_front(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    # 窗里：中间一根能量柱，两边各卡着一块蓄电器
    c.recess(3, 4, 12, 12, STEEL, 4)
    c.rect(4, 5, 11, 11, SCREEN, 1)
    for x0 in (4, 10):
        c.cylinder(x0, 6, x0 + 1, 11, STEEL, 4)
        c.hline(x0, x0 + 1, 6, STEEL, 6)
        for y in (8, 10):
            c.hline(x0, x0 + 1, y, GREEN_GLOW if on else GREEN, 3 if on else 1)
    if on:
        for y in range(5, 12):
            k = abs(y - 8)
            c.set(7, y, CYAN_GLOW, 5 if k < 2 else 4)
            c.set(8, y, CYAN_GLOW, 4 if k < 2 else 3)
        # 两边的电弧
        c.set(6, 7, CYAN_GLOW, 3, glint=True)
        c.set(9, 9, CYAN_GLOW, 3, glint=True)
        c.set(6, 10, CYAN_GLOW, 2, glint=True)
    else:
        c.vline(7, 5, 11, GLASS, 1)
        c.vline(8, 5, 11, GLASS, 0)
        c.set(7, 5, GLASS, 3)
    # 上下两个电极
    c.hline(6, 9, 5, STEEL, 5)
    c.hline(6, 9, 11, STEEL, 2)
    c.rect(3, 13, 12, 13, TEAL, 3)
    c.set(3, 13, TEAL, 4)
    c.set(12, 13, TEAL, 2)
    bolts(c, 1)
    return c


@texture("block/energy_exchanger_front", refs=("block/beacon",))
def exchanger_front_off(rng):
    return exchanger_front(rng, False)


@texture("block/energy_exchanger_front_on", glow=True)
def exchanger_front_on(rng):
    return exchanger_front(rng, True)


@texture("block/energy_exchanger_side")
def exchanger_side(rng):
    c = casing(rng)
    # 一块凸起的散热格栅，下面两根粗电缆接进来
    c.panel(3, 3, 12, 9, STEEL, 4)
    c.grille(4, 4, 11, 8, STEEL, 4)
    for x0 in (4, 9):
        c.cylinder(x0, 10, x0 + 2, 14, RUBBER, 2)
        c.hline(x0, x0 + 2, 10, STEEL, 5)
    c.set(7, 11, TEAL, 4, glint=True)
    c.set(8, 11, TEAL, 3, glint=True)
    return c


@texture("block/energy_exchanger_top")
def exchanger_top(rng):
    c = casing(rng)
    # 四个插蓄电器的圆座，座底有一点青色的触点
    for cx, cy in ((4.5, 4.5), (10.5, 4.5), (4.5, 10.5), (10.5, 10.5)):
        c.shade_disc(cx, cy, 2.0, STEEL, 4, convex=False, spec=False)
        c.ring(cx, cy, 2.0, STEEL, 2)
        c.set(int(cx), int(cy), CYAN_GLOW, 3, glint=True)
    c.hline(7, 8, 7, STEEL, 2)
    c.hline(7, 8, 8, STEEL, 5)
    return c


def fusion_front(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng)
    cx = cy = 7.5
    # 环形的约束线圈：铜线一节一节，节之间有钢的夹子；环里是等离子体
    for x, y in c.cells(1, 1, 14, 14):
        d = math.hypot(x - cx, y - cy)
        if 4.0 < d < 6.4:
            a = math.atan2(y - cy, x - cx)
            seg = int((a + math.pi) / (2 * math.pi) * 12) % 3
            lit = -(x - cx) - (y - cy)          # 环的左上半边受光
            if seg == 0:
                c.set(x, y, STEEL, 5 if lit > 0 else 3)
            else:
                c.set(x, y, COPPER, (4 if d < 5.2 else 3) + (1 if lit > 2 else (-1 if lit < -2 else 0)))
        elif d <= 4.0:
            if on:
                c.set(x, y, WHITE_GLOW if d < 1.6 else CYAN_GLOW, 5 if d < 2.6 else (4 if d < 3.4 else 3))
            else:
                c.set(x, y, SCREEN, 2 if d < 2.5 else 1)
    if not on:
        c.set(6, 5, GLASS, 3)
        c.set(5, 6, GLASS, 2)
    c.ring(cx, cy, 6.4, STEEL, 2, width=0.6)
    bolts(c, 1)
    return c


@texture("block/mini_fusion_power_plant_front", refs=("block/conduit", "block/beacon"))
def fusion_front_off(rng):
    return fusion_front(rng, False)


@texture("block/mini_fusion_power_plant_front_on", glow=True)
def fusion_front_on(rng):
    return fusion_front(rng, True)


@texture("block/mini_fusion_power_plant_side")
def fusion_side(rng):
    c = casing(rng)
    # 两根横着的冷却管（青色的冷却剂），中间一块辐射警示牌
    for y0 in (3, 10):
        c.cylinder(2, y0, 13, y0 + 1, TEAL, 3, vertical=False)
        c.vline(5, y0, y0 + 1, STEEL, 5)
        c.vline(10, y0, y0 + 1, STEEL, 5)
    c.recess(5, 5, 10, 8, STEEL, 4)
    c.stripes(6, 6, 9, 7, HAZARD, 4, GRAPHITE, 1, period=4, width=2)
    return c


@texture("block/mini_fusion_power_plant_top")
def fusion_top(rng):
    c = casing(rng)
    # 检修口：一圈凸起的环，四颗螺栓压着
    c.ring(7.5, 7.5, 5.0, STEEL, 5, width=1.2)
    c.ring(7.5, 7.5, 3.8, STEEL, 2, width=0.7)
    c.shade_disc(7.5, 7.5, 2.4, GRAPHITE, 3, amp=1.0, spec=False)
    # 中间一块观察窗
    c.rect(7, 7, 8, 8, SCREEN, 2)
    c.set(7, 7, GLASS, 4, glint=True)
    for x, y in ((7, 2), (2, 7), (12, 7), (7, 12)):
        c.rivet(x, y, STEEL, 4)
    return c


# ---- 电力塔一家（电力感应塔、卫星配电站、无线输电塔）：模型是同一个 ----
# 塔杆贴图：下半截塔身用第 5–10 列（0–13 行），上半截细杆用第 6–9 列（10–15 行）。
# 线圈贴图：只用第 6–8 行、第 4–11 列。顶上的球：侧面用第 0–3 行、第 5–10 列，顶面用中间 5–10 的方块；
# 通电时那块整体自发光。

def lattice(r, lit: int, mid: int, dark: int, hollow, hollow_lv: int) -> Canvas:
    """桁架塔身：两根立柱（左亮右暗），中间是镂空（深色）；每 4 行一道横撑，横撑之间一根斜撑，
    一节向右下、一节向左下，连起来是之字形（华伦桁架）。以第 5–10 列为一节，横向按 6 列重复。"""
    c = Canvas()
    for x, y in c.cells():
        u = (x - 5) % 6          # 0 和 5 是立柱，1–4 是中间
        if u == 0:
            c.set(x, y, r, lit)
            continue
        if u == 5:
            c.set(x, y, r, dark)
            continue
        k = y % 4
        if k == 0:
            c.set(x, y, r, mid + 1)
            continue
        c.set(x, y, hollow, hollow_lv)
        a = k if (y // 4) % 2 == 0 else 5 - k - 1     # 这一行斜撑占的两列里左边那列（1–3）
        if u in (a, a + 1):
            c.set(x, y, r, mid)
    return c


def windings(r, hi: int, lo: int, gap, gap_lv: int) -> Canvas:
    """线圈：一圈圈横着绕的线，亮的一行是线、暗的一行是缝；线上斜着一串高光。"""
    c = Canvas()
    for x, y in c.cells():
        if y % 2 == 0:
            c.set(x, y, r, hi + (1 if (x + y) % 5 == 0 else 0))
        else:
            c.set(x, y, gap, gap_lv)
    # 两端的绝缘套
    c.hline(0, 15, 0, GRAPHITE, 4)
    c.hline(0, 15, 15, GRAPHITE, 1)
    c.hline(0, 15, 1, GRAPHITE, 2)
    return c


def orb(glow, on: bool, cold=None, off=(1, 0)) -> Canvas:
    """塔顶的放电球。通电时用发光色阶，中间最亮；断电时是暗的玻璃，左上一点反光。"""
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if on:
            c.set(x, y, glow, 5 if d < 1.2 else (4 if d < 2.6 else (3 if d < 4.4 else 2)))
        else:
            c.set(x, y, cold or glow, off[0] if d < 2.6 else off[1])
    # 侧面那条（第 0–3 行，第 5–10 列）：中间两列最亮，两边暗，底下一行是金属的托座
    for y in range(0, 4):
        for x in range(5, 11):
            edge = min(x - 5, 10 - x)
            if on:
                lv = (5 if edge >= 2 and y in (1, 2) else 4) if edge >= 1 else 3
                c.set(x, y, glow, lv - (1 if y == 0 and edge < 2 else 0))
            else:
                c.set(x, y, cold or glow, off[0] if edge >= 2 else off[1])
    for x in range(5, 11):
        c.set(x, 3, STEEL, 5 if x < 8 else 3)
    if not on:
        c.set(6, 1, GLASS, 4, glint=True)
        c.set(6, 6, GLASS, 4, glint=True)
        c.set(7, 6, GLASS, 2, glint=True)
    else:
        c.set(6, 1, WHITE_GLOW, 5, glint=True)
    return c


@texture("block/tesla_tower_pole", refs=("block/iron_bars",))
def tesla_pole(rng):
    return lattice(STEEL, 6, 4, 2, GRAPHITE, 0)


@texture("block/tesla_tower_coil")
def tesla_coil(rng):
    return windings(COPPER, 4, 2, COPPER, 1)


@texture("block/tesla_tower_top", refs=("block/sea_lantern",))
def tesla_top(rng):
    return orb(CYAN_GLOW, True)


@texture("block/tesla_tower_top_off")
def tesla_top_off(rng):
    return orb(CYAN_GLOW, False, SCREEN, (4, 3))


@texture("block/satellite_substation_pole")
def satellite_pole(rng):
    return lattice(WHITE, 6, 4, 2, GRAPHITE, 1)


@texture("block/satellite_substation_coil")
def satellite_dish(rng):
    """碟面：金色的镀膜，一圈圈的同心环；侧面看到的那条（6–8 行）上亮下暗。"""
    c = Canvas()
    c.fill(GOLD_FOIL, 2)
    c.disc(7.5, 7.5, 6.4, GOLD_FOIL, 3)
    c.disc(7.5, 7.5, 4.6, GOLD_FOIL, 4)
    c.disc(7.5, 7.5, 2.4, GOLD_FOIL, 3)
    c.ring(7.5, 7.5, 5.6, GOLD_FOIL, 1, width=0.7)
    c.ring(7.5, 7.5, 3.4, GOLD_FOIL, 2, width=0.7)
    c.shade_disc(7.5, 7.5, 1.0, STEEL, 4, spec=False)
    for x in range(4, 12):
        c.set(x, 6, GOLD_FOIL, 5 if x % 3 else 4)
        c.set(x, 7, GOLD_FOIL, 3)
        c.set(x, 8, GOLD_FOIL, 1)
    c.set(5, 6, WHITE_GLOW, 5, glint=True)
    return c


@texture("block/satellite_substation_top")
def satellite_top(rng):
    return orb(YELLOW_GLOW, True)


@texture("block/satellite_substation_top_off")
def satellite_top_off(rng):
    return orb(YELLOW_GLOW, False, GOLD_FOIL, (2, 1))


@texture("block/wireless_power_tower_coil")
def wireless_coil(rng):
    """感应环：钢芯上套着一圈圈青绿色的环。"""
    return windings(TEAL, 4, 1, GRAPHITE, 1)


@texture("block/wireless_power_tower_top")
def wireless_top(rng):
    return orb(GREEN_GLOW, True)


@texture("block/wireless_power_tower_top_off")
def wireless_top_off(rng):
    return orb(GREEN_GLOW, False, GREEN, (2, 1))


# ---- 火电厂：下半截是锅炉房，上半截是汽轮机和烟囱 ----
# 锅炉房的侧面和正面用第 1–14 列，最下面两行被底座挡住；顶面用 1–14 的方块。
# 运转时正面叠一块自发光的片，贴的是 front_on 的第 3–12 列、第 6–13 行，正好盖住炉膛。

def boiler_plate(rng: random.Random) -> Canvas:
    """锅炉的钢板：横向的焊缝，缝上一排铆钉，左边受光、右边背光，下部积着煤灰。"""
    c = Canvas()
    c.fill(STEEL, 4)
    c.clusters(rng, 2, 1, 13, 13, -1, 6, 3)
    for y in (4, 9):
        c.hline(0, 15, y, STEEL, 2)
        c.hline(0, 15, y + 1, STEEL, 5)
    for y in (2, 7):
        for x in range(3, 14, 4):
            c.rivet(x, y, STEEL, 4)
    c.hline(0, 15, 0, STEEL, 5)
    c.vline(1, 0, 15, STEEL, 5)
    c.vline(14, 1, 15, STEEL, 2)
    c.vline(0, 0, 15, STEEL, 2)
    c.vline(15, 0, 15, STEEL, 1)
    c.grime(rng, 2, 10, 13, 13, 0.18)
    return c


@texture("block/thermal_plant_side", refs=("block/blast_furnace_side",))
def thermal_side(rng):
    c = boiler_plate(rng)
    # 一根竖着的蒸汽管：红色的保温层，两道钢箍
    c.cylinder(10, 0, 12, 15, RED, 3)
    for y in (2, 11):
        c.hline(10, 12, y, STEEL, 5)
        c.set(12, y, STEEL, 3)
    return c


# 炉膛（10×8，第 3–12 列、第 6–13 行）：三根竖的炉条挡在前面。
# 运转时上面是往上窜的火苗，下面是烧红的煤层；熄火时是黑的炉膛和一层冷煤，零星几点余烬。
# 0–2 玄武岩，g 石墨，a–f 橙光（0–5），k、l 黄光（4、5），m–o 暗红
FIRE_ON = [
    "b0gab0gcb0",
    "ca0gcdgcab",
    "dcbgdkgdcb",
    "edcgklgedc",
    "kedglkgdke",
    "lkdgdkgk1d",
    "d1egk1gdd1",
    "1dd0d1g1d0",
]
FIRE_OFF = [
    "000g00g000",
    "000g00g000",
    "000g00g000",
    "000g00g000",
    "100g01g001",
    "1m1g12gm11",
    "21mgm1g2m1",
    "1211n1g121",
]


def thermal_front(rng: random.Random, on: bool) -> Canvas:
    c = boiler_plate(rng)
    # 炉门的框：厚钢，框在发光的片外面
    c.frame(2, 5, 13, 14, STEEL, 2)
    c.hline(2, 13, 5, STEEL, 5)
    c.vline(2, 6, 14, STEEL, 5)
    c.draw(FIRE_ON if on else FIRE_OFF,
           {"012g": GRAPHITE, "abcdef": ORANGE_GLOW, "ghijkl": YELLOW_GLOW, "mno": CRIMSON}, 3, 6)
    # 炉条用石墨：亮火前面是黑的剪影
    for x in (6, 9):
        for y in range(6, 14):
            c.set(x, y, GRAPHITE, 1 if on else 2)
    # 门上方：压力表和一块铭牌
    c.draw([".XW.", "X55V", "W5rU", ".VU."], {"STUVWXYZ": STEEL, "0123456": WHITE, "r": RED}, 3, 0)
    c.rect(9, 2, 12, 3, STEEL, 6)
    c.set(12, 3, STEEL, 5)
    c.set(10, 3, GRAPHITE, 2)
    c.set(11, 3, GRAPHITE, 2)
    c.set(10, 2, GRAPHITE, 2)
    return c


@texture("block/thermal_plant_front", refs=("block/furnace_front", "block/furnace_front_on"))
def thermal_front_off(rng):
    return thermal_front(rng, False)


@texture("block/thermal_plant_front_on")
def thermal_front_on(rng):
    return thermal_front(rng, True)


@texture("block/thermal_plant_top")
def thermal_top(rng):
    c = boiler_plate(rng)
    for y in range(1, 15):          # 顶上不要焊缝和铆钉那套，重铺一遍
        for x in range(1, 15):
            c.set(x, y, STEEL, 4)
    c.frame(1, 1, 14, 14, STEEL, 2)
    c.hline(1, 13, 1, STEEL, 5)
    c.vline(1, 2, 13, STEEL, 5)
    c.vline(8, 2, 13, STEEL, 2)
    c.vline(9, 2, 13, STEEL, 5)
    # 人孔盖：一圈螺栓
    c.shade_disc(4.5, 4.5, 2.2, STEEL, 4, amp=1.2, spec=False)
    c.ring(4.5, 4.5, 2.2, STEEL, 2, width=0.6)
    c.set(4, 4, STEEL, 6, glint=True)
    # 两根出蒸汽的管口
    for cx, cy in ((11.5, 4.5), (11.5, 10.5)):
        c.shade_disc(cx, cy, 1.6, STEEL, 5, spec=False)
        c.set(int(cx), int(cy), GRAPHITE, 0, glint=True)
        c.set(int(cx) + 1, int(cy), GRAPHITE, 1, glint=True)
    c.rivet(4, 10, STEEL, 4)
    c.rivet(4, 12, STEEL, 4)
    return c


@texture("block/thermal_plant_turbine")
def thermal_turbine(rng):
    """汽轮机的机壳（模型用第 9–15 行、第 1–14 列）：横着的圆筒，漆成青绿色，两道带螺栓的法兰，
    一块白色铭牌。上面几行没用到，铺成同样的机壳。"""
    c = Canvas()
    shade = [5, 4, 4, 3, 3, 2, 1]
    for y in range(16):
        k = (y - 9) % 7
        for x in range(16):
            c.set(x, y, TEAL, shade[k])
    for x0 in (4, 10):
        for y in range(16):
            k = (y - 9) % 7
            c.set(x0, y, STEEL, 6 if k == 0 else (5 if k < 4 else 3))
            c.set(x0 + 1, y, STEEL, 3 if k < 4 else 2)
        for y in (10, 13):
            c.set(x0, y, STEEL, 7, glint=True)
    c.vline(1, 0, 15, TEAL, 6)
    c.vline(14, 0, 15, TEAL, 1)
    c.vline(0, 0, 15, STEEL, 3)
    c.vline(15, 0, 15, STEEL, 2)
    c.rect(6, 11, 8, 12, WHITE, 5)
    c.set(8, 12, WHITE, 3)
    c.set(7, 11, GRAPHITE, 3)
    c.set(7, 12, CYAN_GLOW, 4, glint=True)
    return c


@texture("block/thermal_plant_chimney", refs=("block/bricks",))
def thermal_chimney(rng):
    """烟囱（模型用第 2–5 列和第 10–13 列，整列）：混凝土一节节砌上去，顶上红白两道航空警示色，
    最顶上一圈熏黑；每个面左边受光、右边背光。"""
    c = Canvas()
    for x, y in c.cells():
        u = x % 8 - 2                # 0–3 是一个面
        lv = 4 + (1 if u == 0 else (-1 if u == 3 else 0))
        if y % 4 == 3:
            lv -= 1
        c.set(x, y, CONCRETE, lv)
    for x in range(16):
        u = x % 8 - 2
        side = 1 if u == 0 else (-1 if u == 3 else 0)
        c.set(x, 0, GRAPHITE, 2)
        c.set(x, 1, RED, 4 + side)
        c.set(x, 2, RED, 4 + side)
        c.set(x, 3, WHITE, 5 + side)
        c.set(x, 4, WHITE, 5 + side)
        c.set(x, 5, CONCRETE, 2 + side)
    # 顶下面一截被烟熏得发暗
    for x in range(16):
        c.shift(x, 6, -1)
        if (x // 2) % 2 == 0:
            c.shift(x, 7, -1)
    return c


# ---- 风机：白色的塔筒、机舱和叶片，混凝土的基座 ----

@texture("block/wind_turbine_body", refs=("block/white_concrete", "block/quartz_block_side"))
def wind_body(rng):
    """塔筒和机舱外壳：白漆，按圆筒左亮右暗（塔筒用第 5–10 列），每 8 行一道法兰缝。
    机舱的顶面、轮毂也贴这张，所以不画门、不画标记。"""
    c = Canvas()
    shade = {5: 6, 6: 5, 7: 5, 8: 5, 9: 4, 10: 3}
    for x, y in c.cells():
        c.set(x, y, WHITE, shade.get(x, 5 if x < 5 else 4))
    for y in (4, 12):
        for x in range(16):
            c.shift(x, y, -2)
            c.shift(x, y + 1, 1)
    c.clusters(rng, 0, 0, 15, 15, -1, 5, 2)
    return c


@texture("block/wind_turbine_nacelle")
def wind_nacelle(rng):
    """机舱的侧面（第 2–7 行，西面用 3–14 列、东面用 1–12 列）：白色外壳，上沿受光、下沿背光，
    一块检修门，一组百叶散热口，一道青色的装饰线。"""
    c = Canvas()
    c.fill(WHITE, 5)
    c.hline(0, 15, 2, WHITE, 6)
    c.hline(0, 15, 6, WHITE, 4)
    c.hline(0, 15, 7, WHITE, 2)
    c.hline(0, 15, 5, TEAL, 4)
    c.hline(0, 15, 1, WHITE, 3)
    c.hline(0, 15, 8, WHITE, 3)
    # 检修门：两道竖缝，一个把手
    for x in (3, 6):
        c.vline(x, 3, 6, WHITE, 3)
    c.set(5, 4, WHITE, 2, glint=True)
    # 百叶口：深色的缝、受光的唇边
    c.hline(8, 11, 3, GRAPHITE, 2)
    c.hline(8, 11, 4, WHITE, 6)
    c.set(12, 3, WHITE, 4)
    return c


@texture("block/wind_turbine_blade")
def wind_blade(rng):
    """叶片：根部（第 7–15 行、3–12 列）和叶尖（第 0–6 行、4–11 列）都被横向压窄，只有横向的
    条纹和左右两边的明暗留得下来：叶尖两道红色警示条，根部是钢的法兰。"""
    c = Canvas()
    c.fill(WHITE, 5)
    for y in range(7, 13):
        for x in range(16):
            c.set(x, y, WHITE, 5 if y < 11 else 4)
    for y, (r, lv) in {1: (RED, 4), 2: (RED, 3), 4: (RED, 4), 5: (RED, 3)}.items():
        c.hline(0, 15, y, r, lv)
    for y in (13, 14, 15):
        c.hline(0, 15, y, STEEL, {13: 5, 14: 4, 15: 2}[y])
    for x in range(3, 13, 3):
        c.set(x, 14, STEEL, 6, glint=True)
    # 左边是迎风的前缘（亮），右边是后缘（暗）
    for x0, x1, y0, y1 in ((4, 11, 0, 6), (3, 12, 7, 12)):
        for y in range(y0, y1 + 1):
            c.shift(x0, y, 1)
            c.shift(x1, y, -1)
    return c


@texture("block/wind_turbine_base", refs=("block/smooth_stone",))
def wind_base(rng):
    """混凝土基座：顶面（3–12）中间压着塔筒的法兰，一圈地脚螺栓；侧面（第 13–15 行）上沿是倒角。"""
    c = Canvas()
    c.fill(CONCRETE, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 5, 2)
    c.clusters(rng, 0, 0, 15, 15, 1, 3, 2)
    c.ring(7.5, 7.5, 4.6, STEEL, 3, width=1.0)
    c.ring(7.5, 7.5, 3.6, STEEL, 5, width=1.0)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        c.set(round(7.5 + 4.1 * math.cos(a)), round(7.5 + 4.1 * math.sin(a)), STEEL, 6, glint=True)
    c.disc(7.5, 7.5, 2.6, WHITE, 4)
    c.hline(0, 15, 13, CONCRETE, 5)
    c.hline(0, 15, 14, CONCRETE, 4)
    c.hline(0, 15, 15, CONCRETE, 2)
    return c


# ---- 太阳能板：深蓝的单晶硅电池片，铝边框，白色背板 ----

PV = palette_register(ramp("power_pv", 262, 0.10, n=6, lo=0.16, hi=0.58, shift=8, peak=0.45))


@texture("block/solar_panel_cells", refs=("block/daylight_detector_top", "block/lapis_block"))
def solar_cells(rng):
    """3×3 片电池，每片 4×4，片之间一像素的缝；单晶片切掉的四个角在缝的交叉处露出白色的背板，
    每片中间一道主栅；玻璃上一条斜的反光。整块一圈铝边框，相邻的板拼起来是一排排的方格。"""
    c = Canvas()
    c.fill(PV, 2)
    for x, y in c.cells(1, 1, 14, 14):
        u, v = (x - 1) % 5, (y - 1) % 5
        if u == 4 or v == 4:
            c.set(x, y, PV, 4)
        elif v == 1:
            c.set(x, y, PV, 3)
    for gx in (5, 10):
        for gy in (5, 10):
            c.set(gx, gy, WHITE, 5, glint=True)
            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                c.set(gx + dx, gy + dy, PV, 4)
    # 玻璃的反光：左上到右下一条斜带
    for x, y in c.cells(1, 1, 14, 14):
        if 6 <= x + y <= 8 and c.get(x, y)[0] is PV:
            c.shift(x, y, 1)
        if x + y == 18 and c.get(x, y)[0] is PV:
            c.shift(x, y, 1)
    c.hline(0, 15, 0, IRON, 5)
    c.vline(0, 0, 15, IRON, 5)
    c.hline(1, 15, 15, IRON, 2)
    c.vline(15, 1, 15, IRON, 3)
    return c


@texture("block/solar_panel_back")
def solar_back(rng):
    """背面：白色背板，两根横的铝导轨，中间一个接线盒引出两根电缆。"""
    c = Canvas()
    c.fill(WHITE, 4)
    c.clusters(rng, 1, 1, 14, 14, -1, 6, 3)
    for y in (3, 12):
        c.hline(0, 15, y, IRON, 4)
        c.hline(0, 15, y + 1, IRON, 2)
    c.panel(6, 6, 9, 9, GRAPHITE, 3)
    c.hline(10, 13, 7, RUBBER, 2)
    c.hline(2, 5, 8, RUBBER, 2)
    c.frame(0, 0, 15, 15, IRON, 3)
    c.hline(0, 14, 0, IRON, 5)
    c.vline(0, 0, 14, IRON, 5)
    return c


@texture("block/solar_panel_frame")
def solar_frame(rng):
    """铝型材：支架、转轴、板子的侧边都贴这张；第 3 行是板子的侧边，亮一些。"""
    c = Canvas()
    c.fill(IRON, 4)
    # 拉丝：横着的细长纹
    for _ in range(12):
        x, y = rng.randint(0, 12), rng.randint(0, 15)
        d = rng.choice((1, -1, -1))
        for i in range(rng.randint(3, 5)):
            c.shift((x + i) % 16, y, d)
    c.hline(0, 15, 3, IRON, 5)
    c.hline(0, 15, 4, IRON, 3)
    return c


@texture("block/solar_panel_base")
def solar_base(rng):
    """底座：深色的钢座，顶面（5–10）中间是跟踪器的回转环；侧面（第 14–15 行）。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 2)
    c.ring(7.5, 7.5, 2.4, STEEL, 5, width=0.8)
    c.set(6, 6, STEEL, 7, glint=True)
    c.hline(0, 15, 14, GRAPHITE, 4)
    c.hline(0, 15, 15, GRAPHITE, 2)
    return c


# ---- 人造恒星：钢的底座带金色包边，线圈箱，四根立柱托着顶框，中间悬着恒星的核心和日冕 ----
# 底座侧面用第 12–15 行；底座顶面整张（中间 3–12 被线圈箱的顶面盖住，线圈箱顶面也用这块）；
# 线圈箱的侧面用 coil 的第 3–12 列、第 1–11 行；立柱用第 0–2、13–15 列；顶框是一圈梁加一道横梁。
# 核心和日冕是渲染器另画的：核心侧面用第 5–10 列、第 6–11 行，顶面中间 5–10；日冕侧面 4–11 列、
# 5–12 行，顶面中间 4–11，半透明。

@texture("block/artificial_star_base")
def star_base(rng):
    c = casing(rng, base=3)
    for x in range(16):
        lit = 1 if x < 8 else 0
        c.set(x, 12, GOLD_FOIL, 3 + lit)
        c.set(x, 13, STEEL, 4)
        c.set(x, 14, GRAPHITE, 1 if x % 2 == 0 else 3)
        c.set(x, 15, STEEL, 2)
    for x in (3, 7, 11):
        c.set(x, 13, STEEL, 6, glint=True)
    c.hline(0, 15, 11, GOLD_FOIL, 1)
    return c


@texture("block/artificial_star_top")
def star_top(rng):
    c = Canvas()
    c.fill(STEEL, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 3)
    # 外圈的甲板：内侧一道金线
    c.frame(2, 2, 13, 13, GOLD_FOIL, 3)
    c.hline(2, 12, 2, GOLD_FOIL, 4)
    c.vline(2, 3, 12, GOLD_FOIL, 4)
    c.hline(0, 15, 0, STEEL, 5)
    c.vline(0, 0, 15, STEEL, 5)
    # 线圈箱的顶面：同心的铜线圈围着中间的发射透镜
    c.rect(3, 3, 12, 12, GRAPHITE, 2)
    c.ring(7.5, 7.5, 4.6, COPPER, 3, width=1.0)
    c.ring(7.5, 7.5, 3.3, COPPER, 4, width=0.8)
    c.shade_disc(7.5, 7.5, 2.2, GLASS, 1, amp=1.0, spec=False)
    c.set(6, 6, GLASS, 4, glint=True)
    c.set(8, 8, ORANGE, 3, glint=True)
    return c


def star_coil(on: bool) -> Canvas:
    c = Canvas()
    for x, y in c.cells():
        if y % 2 == 1:
            c.set(x, y, COPPER, 4 + (1 if (x + y) % 5 == 0 else 0))
        else:
            c.set(x, y, COPPER, 2)
    # 上下两道钢箍，两边的立筋
    for y in (1, 11):
        c.hline(0, 15, y, STEEL, 5 if y == 1 else 3)
    c.vline(3, 1, 11, STEEL, 5)
    c.vline(12, 1, 11, STEEL, 2)
    # 中间一道观察缝：运转时透出橙黄的光
    c.vline(6, 2, 10, STEEL, 4)
    c.vline(9, 2, 10, STEEL, 2)
    for y in range(2, 11):
        if on:
            k = abs(y - 6)
            c.set(7, y, YELLOW_GLOW, 5 if k < 2 else 4)
            c.set(8, y, ORANGE_GLOW, 3 if k < 3 else 2)
        else:
            c.set(7, y, GRAPHITE, 1)
            c.set(8, y, GRAPHITE, 0)
    if not on:
        c.set(7, 3, CRIMSON, 1)
        c.set(7, 7, CRIMSON, 1)
    return c


@texture("block/artificial_star_coil")
def star_coil_off(rng):
    return star_coil(False)


@texture("block/artificial_star_coil_on")
def star_coil_on(rng):
    return star_coil(True)


@texture("block/artificial_star_pillar")
def star_pillar(rng):
    c = Canvas()
    c.fill(STEEL, 3)
    for x0 in (0, 13):
        c.vline(x0, 0, 15, STEEL, 5)
        c.vline(x0 + 1, 0, 15, STEEL, 4)
        c.vline(x0 + 2, 0, 15, STEEL, 2)
        for y in (2, 7, 12):
            c.set(x0, y, GOLD_FOIL, 4)
            c.set(x0 + 1, y, GOLD_FOIL, 3)
            c.set(x0 + 2, y, GOLD_FOIL, 2)
            c.set(x0 + 1, y + 1, STEEL, 2)
        # 柱头和柱脚（顶面、底面用到的 2.5×2.5）
        for y0 in (0, 13):
            c.rect(x0, y0, x0 + 2, y0 + 2, STEEL, 4)
            c.set(x0, y0, STEEL, 6)
            c.set(x0 + 2, y0 + 2, STEEL, 2)
    return c


@texture("block/artificial_star_frame")
def star_frame(rng):
    c = Canvas()
    c.fill(GRAPHITE, 2)
    # 一圈梁的顶面
    for x, y in c.cells():
        if x <= 2 or x >= 13 or y <= 2 or y >= 13:
            c.set(x, y, STEEL, 4)
    c.hline(0, 15, 0, STEEL, 6)
    c.hline(0, 15, 1, STEEL, 4)
    c.vline(0, 0, 15, STEEL, 5)
    c.hline(3, 12, 2, STEEL, 2)
    c.vline(2, 3, 12, STEEL, 2)
    c.hline(0, 15, 15, STEEL, 2)
    c.vline(15, 0, 15, STEEL, 2)
    # 梁侧面（第 0–1 行）每隔四格一颗金色的螺栓
    for x in range(1, 16, 4):
        c.set(x, 1, GOLD_FOIL, 4, glint=True)
    # 横梁（第 7–8 行）
    c.hline(2, 13, 7, STEEL, 5)
    c.hline(2, 13, 8, STEEL, 3)
    # 中间的吊挂：金色的环
    c.ring(7.5, 7.5, 2.2, GOLD_FOIL, 3, width=0.8)
    c.set(6, 6, GOLD_FOIL, 5, glint=True)
    c.rect(7, 7, 8, 8, GRAPHITE, 1)
    return c


@texture("block/artificial_star_core", refs=("block/glowstone", "block/shroomlight"))
def star_core(rng):
    """恒星的表面：一颗颗亮的米粒组织，中间白，粒之间的暗道发黄，边上偏橙（临边昏暗）。"""
    c = Canvas()
    pts = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(14)]
    for x, y in c.cells():
        ds = sorted(math.hypot((x - px + 8) % 16 - 8, (y - py + 8) % 16 - 8) for px, py in pts)
        lane = ds[1] - ds[0] < 0.7
        edge = not (6 <= x <= 9 and 6 <= y <= 10)          # 看得见的那块（5–10 列）最外一圈偏橙
        if edge:
            c.set(x, y, ORANGE_GLOW if lane else AMBER_GLOW, 3 if lane else 4)
        elif lane:
            c.set(x, y, YELLOW_GLOW, 4)
        else:
            c.set(x, y, WHITE_GLOW if ds[0] < 1.0 else YELLOW_GLOW, 5)
    return c


@texture("block/artificial_star_corona")
def star_corona(rng):
    """日冕：很淡的一层橙光，中间稍浓，四条斜的冕流浓一些；越往外越透明。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 8.0
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        streamer = max(0.0, math.cos(4 * a + 0.4)) ** 3
        alpha = int(max(18, 92 - d * 13 + streamer * 40 * min(1, d / 2)))
        if d < 2.5:
            c.set(x, y, YELLOW_GLOW, 5, alpha=alpha)
        elif d < 4.2:
            c.set(x, y, YELLOW_GLOW if streamer > 0.4 else ORANGE_GLOW, 4, alpha=alpha)
        else:
            c.set(x, y, ORANGE_GLOW, 3 if streamer < 0.4 else 4, alpha=alpha)
    return c
