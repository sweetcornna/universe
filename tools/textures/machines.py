"""工业设备的贴图（ADR-0031）。

设备的样子：冷灰的钢机壳，最外一圈深色的边让相邻的方块分得开，里面一圈倒角；正面是功能区
（观察窗、屏幕、炉口），上面一条状态灯，运转时亮青色。Mk.I/II/III 用琥珀、绿、蓝做等级色。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import (BLUE, BLUE_GLOW, BONE, BRASS, CONCRETE, COPPER, CYAN_GLOW, GLASS, GRAPHITE, GREEN, GREEN_GLOW,
                     HAZARD, IRON, MAGENTA_GLOW, OIL, ORANGE, ORANGE_GLOW, PINK, RED, RED_GLOW, RUBBER, RUST, SAND, SCREEN,
                     SOIL, STEEL, STONE, TEAL, VIOLET, VIOLET_GLOW, WHITE, WHITE_GLOW, register)
from parts import TIER, bolts, casing, fan, status_lights
from recipe import texture

# 这组自己用的色阶
PLUM = register(ramp("machines_plum", 305, 0.032, n=8, lo=0.25, hi=0.88, shift=14))       # 位面熔炉的紫灰机壳
HYDROGEN = register(ramp("machines_hydrogen", 138, 0.070, n=6, lo=0.45, hi=0.93, shift=10))  # 分馏塔里淡绿的氢
NAVY = register(ramp("machines_navy", 248, 0.045, n=8, lo=0.24, hi=0.86, shift=12))        # 抽水机、高级采矿机的蓝灰机壳
WATER = register(ramp("machines_water", 232, 0.085, n=6, lo=0.26, hi=0.82, shift=12))       # 管子里、液位计里的水


# ---- 这组共用的小部件

def shell(rng: random.Random, r=STEEL, base: int = 4, edge: bool = True) -> Canvas:
    """和 parts.casing 一样的机壳，换一条色阶就是涂了漆的外壳（采矿机的黄、化工厂的白）。"""
    if r is STEEL:
        return casing(rng, base, edge)
    c = Canvas()
    c.fill(r, base)
    for x, y in c.cells(2, 2, 13, 13):
        k = x + y + ((x * 7 + y * 3) % 3) - 1
        if k < 9:
            c.shift(x, y, 1)
        elif k > 19:
            c.shift(x, y, -1)
    for _ in range(10):
        x, y = rng.randint(2, 11), rng.randint(3, 12)
        n = rng.randint(2, 4)
        d = rng.choice((-1, -1, 1))
        for i in range(n):
            c.shift(x + i, y, d)
    if edge:
        c.frame(0, 0, 15, 15, r, base - 2)
        c.rect(1, 1, 14, 1, r, base + 1)
        c.rect(1, 1, 1, 14, r, base + 1)
        c.rect(2, 14, 14, 14, r, base - 1)
        c.rect(14, 2, 14, 14, r, base - 1)
        c.hline(2, 13, 2, r, base - 1)
        c.vline(2, 3, 13, r, base - 1)
        c.edge_wear(rng, 1, 1, 14, 14, 0.22)
    c.grime(rng, 3, 9, 13, 13, 0.05)
    return c


def hazard(c: Canvas, x0, y0, x1, y1, a=HAZARD, la: int = 3, b=GRAPHITE, lb: int = 1):
    """斜着的警示条：两种颜色，不加倒角（一条细带上再分明暗，颜色就太碎了）。"""
    c.stripes(x0, y0, x1, y1, a, la, b, lb, period=4, width=2)


def lamp(c: Canvas, x: int, y: int, on: bool, r=GREEN_GLOW):
    """一盏指示灯：亮时是发光色，不亮时是同色的暗玻璃（不进发光层）。"""
    c.set(x, y, r, 5 if on else 1, glint=True)


def screen(c: Canvas, x0, y0, x1, y1, on: bool, r=CYAN_GLOW, lines=None):
    """嵌在深色边框里的小屏幕。亮时一行行的读数，不亮时只有一点玻璃反光。"""
    c.frame(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GRAPHITE, 1)
    c.rect(x0, y0, x1, y1, SCREEN, 1)
    if on:
        lines = lines or [x1 - x0 + 1 - (i * 3) % 4 for i in range((y1 - y0) // 2 + 1)]
        for i, n in enumerate(lines):
            y = y0 + i * 2
            if y > y1:
                break
            for x in range(x0, min(x1, x0 + n - 1) + 1):
                c.set(x, y, r, 4 if i == 0 else 3, glint=True)
    else:
        c.set(x0, y0, SCREEN, 3, glint=True)


def flange(c: Canvas, x0, x1, y, r=STEEL, base: int = 4):
    """管子上的一道箍：上半亮、下半暗。"""
    c.hline(x0, x1, y, r, base + 1)
    c.set(x1, y, r, base - 1)


def lid(c: Canvas, cx: float, cy: float, rad: float, r=STEEL, base: int = 4, hatch: float = 0.0):
    """俯视的圆盖（罐顶、釜盖）：一圈凸起的边，受光的一侧亮、背光的一侧暗，盖面平的；hatch 给了就在中间加一个小人孔。"""
    inner = set(c.disc_cells(cx, cy, rad - 1))
    for x, y in c.disc_cells(cx, cy, rad):
        if (x, y) in inner:
            c.set(x, y, r, base)
            continue
        toward = ((x - cx) * -0.58 + (y - cy) * -0.62) / max(0.01, math.hypot(x - cx, y - cy))
        c.set(x, y, r, base + 1 if toward > 0.35 else (base - 2 if toward < -0.35 else base - 1))
    if hatch:
        for x, y in c.disc_cells(cx, cy, hatch):
            toward = (x - cx) * -0.58 + (y - cy) * -0.62
            c.set(x, y, r, base + 2 if toward > 0.3 else (base - 1 if toward < -0.3 else base + 1))


def crucible(c: Canvas, cx: float, cy: float, rin: float, rout: float, rim=CONCRETE, base: int = 3):
    """俯视的一圈凸起的炉沿：外坡左上亮、右下暗；内壁反过来（左上那面背光，右下那面受光），砖缝错开。"""
    for x, y in c.disc_cells(cx, cy, rout):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r <= rin:
            continue
        toward = (dx * -0.58 + dy * -0.62) / max(r, 0.01)  # 朝光的一侧为正
        t = (r - rin) / (rout - rin)
        lv = base
        if t > 0.62:
            lv += 1 if toward > 0.3 else (-1 if toward < -0.3 else 0)
        elif t < 0.38:
            lv += -1 if toward > 0.3 else (1 if toward < -0.3 else 0)
        a = math.degrees(math.atan2(dy, dx)) % 360
        if 0.3 < t < 0.7 and int(a + (20 if r > (rin + rout) / 2 else 0)) % 60 < 9:
            lv -= 1
        c.set(x, y, rim, lv)


def chute(c: Canvas, x0, y0, x1, y1, on: bool, ores=()):
    """出料口：凹进去的深色口子，里面几根横的钢滚筒；运转时顶上一条工作灯亮着，滚筒上躺着刚采出来的矿石。"""
    c.recess(x0, y0, x1, y1, STEEL, 4)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GRAPHITE, 0)
    for y in range(y0 + 3, y1, 2):
        c.hline(x0 + 1, x1 - 1, y, STEEL, 3)
        c.set(x0 + 1, y, STEEL, 4)
    if on:
        c.hline(x0 + 1, x1 - 1, y0 + 1, CYAN_GLOW, 3)
        for ox, oy, vein in ores:
            # 一块矿石：左上受光，右下背光，带一点矿脉的颜色
            c.set(ox, oy, STONE, 5, glint=True)
            c.set(ox + 1, oy, STONE, 4, glint=True)
            c.set(ox, oy + 1, STONE, 4, glint=True)
            c.set(ox + 1, oy + 1, vein, 4, glint=True)
            c.set(ox + 2, oy + 1, STONE, 2, glint=True)


def motor(c: Canvas, cx: float, cy: float, rad: float, fins: int = 6, r=STEEL, hub=STEEL):
    """俯视的电机：一圈放射状的散热片，片间是暗缝，中间是轴。"""
    for x, y in c.disc_cells(cx, cy, rad):
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        if d < 2.2:
            continue
        a = math.atan2(dy, dx)
        fin = math.cos(a * fins) > -0.1
        toward = (dx * -0.58 + dy * -0.62) / max(d, 0.01)
        if fin:
            c.set(x, y, r, 5 if toward > 0.3 else (3 if toward < -0.3 else 4))
        else:
            c.set(x, y, GRAPHITE, 1)
    c.ring(cx, cy, 2.2, r, 3)
    c.shade_disc(cx, cy, 1.4, hub, 4)


def band_panel(rng, x0, y0, x1, y1, r=STEEL, base: int = 4) -> Canvas:
    """只用贴图一部分的模型（采油机、精炼厂的下半截）：在用得到的那块里画一圈倒角，里面是拉丝面板。"""
    c = Canvas()
    c.fill(r, base)
    for _ in range(8):
        x, y = rng.randint(x0 + 1, x1 - 3), rng.randint(y0 + 1, y1 - 1)
        for i in range(rng.randint(2, 3)):
            c.shift(x + i, y, rng.choice((-1, -1, 1)))
    c.hline(x0, x1, y0, r, base + 1)
    c.vline(x0, y0, y1, r, base + 1)
    c.hline(x0 + 1, x1, y1, r, base - 2)
    c.vline(x1, y0 + 1, y1, r, base - 2)
    c.edge_wear(rng, x0, y0, x1, y1, 0.2)
    return c


# ---- 装配机：观察窗里的机械臂。Mk.I 一条臂；Mk.II 两条臂、Mk.III 龙门架加更亮的工作灯，窗框、灯槽、
#      百叶、风扇外圈都涂等级色，远看也分得出档位

# 观察窗里的机械臂（8×7，窗内左上角起）：深色的立柱、横梁和夹爪，钢的关节；亮底上的暗剪影最好认。
# 数字是石墨色的级数，a–g 是黄铜（工件），S–Z 是钢，p–u 是青色光。
ARM_OFF = [
    "........",
    "..1111X.",
    "..1...0.",
    "..1..101",
    "..1..1.1",
    ".W1W....",
    ".VUV....",
]
ARM_ON = [
    "........",
    "..1111X.",
    "..1...0.",
    "..1...0.",
    "..1..101",
    ".W1W.rur",
    ".VUVWXXW",
]
# Mk.II：左右两条臂从底座立起来，一起夹住中间的工件；运转时工件两边冒火花
ARM2_OFF = [
    "........",
    ".X1..1X.",
    ".1.11.1.",
    ".1.00.1.",
    ".1.ed.1.",
    "W1W..W1W",
    "VUV..VUV",
]
ARM2_ON = [
    "........",
    ".X1..1X.",
    ".1.11.1.",
    ".1.00.1.",
    ".1uedu1.",
    "W1W..W1W",
    "VUV..VUV",
]
# Mk.III：顶上一根横的导轨，滑车吊着加工头往下，底下是工作台；运转时加工头下面一点白光
GANTRY_OFF = [
    "XXXXXXXX",
    "UUU11UUU",
    "...11...",
    "....1...",
    "....0...",
    "..edde..",
    "WWWWWWWW",
]
GANTRY_ON = [
    "XXXXXXXX",
    "UUU11UUU",
    "...11...",
    "....1...",
    "....0...",
    "..edue..",
    "WWWWWWWW",
]
ARMS = {1: (ARM_OFF, ARM_ON), 2: (ARM2_OFF, ARM2_ON), 3: (GANTRY_OFF, GANTRY_ON)}


def assembler_face(rng: random.Random, on: bool, tier: int = 1) -> Canvas:
    c = casing(rng)
    accent = TIER[tier]
    status_lights(c, on)
    if tier > 1:
        # 灯槽涂等级色
        for x in (5, 7, 9, 11):
            c.set(x, 2, accent, 1)
    # 观察窗：凹进去，高等级的窗框是等级色。停机时里面是暗淡的玻璃色；运转时工作灯把里面照成一片青色，
    # 越往下越亮，Mk.III 的灯更亮
    c.recess(3, 4, 12, 12, STEEL if tier == 1 else accent, 4)
    if on:
        lit = (2, 3, 4) if tier < 3 else (3, 4, 5)
        c.rect(4, 5, 11, 6, CYAN_GLOW, lit[0])
        c.rect(4, 7, 11, 9, CYAN_GLOW, lit[1])
        c.rect(4, 10, 11, 11, CYAN_GLOW, lit[2])
    else:
        c.rect(4, 5, 11, 7, GLASS, 0)
        c.rect(4, 8, 11, 11, GLASS, 1)
    c.draw(ARMS[tier][1 if on else 0],
           {"0123456": GRAPHITE, "abcdefg": BRASS, "pqrstu": CYAN_GLOW, "STUVWXYZ": STEEL}, 4, 5)
    # 玻璃上一道斜的反光（只落在玻璃上，不盖住机械）
    if not on:
        for x, y, lv in ((10, 5, 3), (11, 6, 2)):
            px = c.get(x, y)
            if tier == 1 or (px and px[0] is GLASS):
                c.set(x, y, GLASS, lv)
        if tier == 3:
            c.set(10, 7, GLASS, 3)
            c.set(11, 8, GLASS, 2)
    # 下面一条等级色的涂装，左边受光
    c.rect(3, 13, 12, 13, accent, 3)
    c.set(3, 13, accent, 4)
    c.set(12, 13, accent, 2)
    bolts(c, 1)
    return c


@texture("block/assembler_front", refs=("block/crafter_north", "block/blast_furnace_front_on"))
def assembler_front(rng):
    return assembler_face(rng, False)


@texture("block/assembler_front_on", glow=True)
def assembler_front_on(rng):
    return assembler_face(rng, True)


def assembler_side_face(rng: random.Random, tier: int = 1) -> Canvas:
    c = casing(rng)
    # 侧板：一块凸起的检修板，上面一组百叶通风口，下面两根管线；Mk.I 是钢百叶、黄铜管，
    # Mk.II/III 的百叶和导管都是等级色，Mk.III 的百叶多一片
    c.panel(3, 3, 12, 12, STEEL, 4)
    if tier == 1:
        c.vent(5, 5, 10, 8, STEEL, 4)
    else:
        c.vent(5, 4 if tier == 3 else 5, 10, 8, TIER[tier], 4)
    c.cylinder(3, 10, 12, 11, BRASS if tier == 1 else TIER[tier], 3, vertical=False)
    c.vline(7, 10, 11, GRAPHITE, 2)
    bolts(c, 3)
    return c


def assembler_top_face(rng: random.Random, tier: int = 1) -> Canvas:
    c = casing(rng)
    # 风扇：Mk.I 四片叶，Mk.II 五片、Mk.III 六片；高等级的风扇外圈和轴帽涂等级色
    fan(c, 7.5, 7.5, 5.0, 3 + tier)
    if tier > 1:
        for x, y in c.disc_cells(7.5, 7.5, 5.0):
            if math.hypot(x - 7.5, y - 7.5) > 4.4:
                c.recolor(x, y, TIER[tier])
        c.shade_disc(7.5, 7.5, 1.3, TIER[tier], 3)
    bolts(c, 1)
    return c


@texture("block/assembler_side", refs=("block/crafter_south",))
def assembler_side(rng):
    return assembler_side_face(rng)


@texture("block/assembler_top", refs=("block/crafter_top",))
def assembler_top(rng):
    return assembler_top_face(rng)


@texture("block/assembler_mk2_front")
def assembler_mk2_front(rng):
    return assembler_face(rng, False, 2)


@texture("block/assembler_mk2_front_on", glow=True)
def assembler_mk2_front_on(rng):
    return assembler_face(rng, True, 2)


@texture("block/assembler_mk2_side")
def assembler_mk2_side(rng):
    return assembler_side_face(rng, 2)


@texture("block/assembler_mk2_top")
def assembler_mk2_top(rng):
    return assembler_top_face(rng, 2)


@texture("block/assembler_mk3_front")
def assembler_mk3_front(rng):
    return assembler_face(rng, False, 3)


@texture("block/assembler_mk3_front_on", glow=True)
def assembler_mk3_front_on(rng):
    return assembler_face(rng, True, 3)


@texture("block/assembler_mk3_side")
def assembler_mk3_side(rng):
    return assembler_side_face(rng, 3)


@texture("block/assembler_mk3_top")
def assembler_mk3_top(rng):
    return assembler_top_face(rng, 3)


# ---- 电弧熔炉：观察窗里两根石墨电极，通电时尖端白热、熔池发亮；顶上是耐火砖砌的坩埚，正中一根电极

def arc_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    c.recess(3, 3, 12, 11, STEEL, 4)
    # 炉膛：上面暗，越往下越被熔池照亮；停炉时只有一点余温的暗红
    if on:
        c.rect(4, 4, 11, 5, GRAPHITE, 1)
        c.hline(4, 11, 6, ORANGE_GLOW, 1)
        c.hline(4, 11, 7, ORANGE_GLOW, 2)
        c.hline(4, 11, 8, ORANGE_GLOW, 2)
        c.rect(4, 9, 11, 10, ORANGE_GLOW, 3)
    else:
        c.rect(4, 4, 11, 8, GRAPHITE, 0)
        c.rect(4, 9, 11, 10, GRAPHITE, 1)
        c.hline(5, 10, 9, GRAPHITE, 2)
    # 两根粗石墨电极从炉顶插下来，钢夹头；通电时尖端白热，正下方的熔池最亮
    for x0 in (5, 9):
        c.hline(x0, x0 + 1, 4, STEEL, 6)
        c.vline(x0, 5, 7, GRAPHITE, 3 if on else 4)
        c.vline(x0 + 1, 5, 7, GRAPHITE, 1 if on else 2)
        if on:
            c.hline(x0, x0 + 1, 8, WHITE_GLOW, 5)
            c.hline(x0 - 1, x0 + 2, 9, ORANGE_GLOW, 4)
            c.set(x0, 9, ORANGE_GLOW, 5)
        else:
            c.hline(x0, x0 + 1, 8, GRAPHITE, 3)
    hazard(c, 3, 13, 12, 13)
    bolts(c, 1)
    return c


@texture("block/arc_smelter_front", refs=("block/blast_furnace_front", "block/furnace_front"))
def arc_smelter_front(rng):
    return arc_face(rng, False)


@texture("block/arc_smelter_front_on", glow=True, refs=("block/blast_furnace_front_on",))
def arc_smelter_front_on(rng):
    return arc_face(rng, True)


@texture("block/arc_smelter_side", refs=("block/blast_furnace_side",))
def arc_smelter_side(rng):
    c = casing(rng)
    # 两组散热百叶，下面一根粗铜母排给电极供电，两头绝缘夹
    c.vent(3, 4, 12, 7, STEEL, 4)
    c.cylinder(2, 10, 13, 11, COPPER, 3, vertical=False)
    for x in (4, 11):
        c.vline(x, 9, 12, GRAPHITE, 2)
        c.set(x, 9, GRAPHITE, 3)
    bolts(c, 1)
    return c


def arc_top_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    crucible(c, 7.5, 7.5, 3.4, 5.6)
    pool = c.disc_cells(7.5, 7.5, 3.4)
    for x, y in pool:
        r = math.hypot(x - 7.5, y - 7.5)
        if on:
            c.set(x, y, ORANGE_GLOW, 4 if r < 2.0 else (3 if r < 2.9 else 2))
        else:
            c.set(x, y, GRAPHITE, 1 if r < 2.9 else 0)
    # 熔池上漂着几块暗的浮渣；停炉时是结了壳的炉渣
    for x, y in ((5, 9), (10, 6), (9, 10)):
        c.set(x, y, ORANGE_GLOW if on else GRAPHITE, 1 if on else 2)
    if not on:
        c.set(6, 5, GRAPHITE, 2)
        c.set(6, 6, GRAPHITE, 2)
    # 正中一根石墨电极插进熔池
    c.rect(7, 7, 8, 8, GRAPHITE, 2 if on else 3)
    c.set(7, 7, GRAPHITE, 4 if on else 5, glint=True)
    if on:
        c.set(6, 6, ORANGE_GLOW, 5, glint=True)
    bolts(c, 1)
    return c


@texture("block/arc_smelter_top", refs=("block/blast_furnace_top",))
def arc_smelter_top(rng):
    return arc_top_face(rng, False)


@texture("block/arc_smelter_top_on", glow=True)
def arc_smelter_top_on(rng):
    return arc_top_face(rng, True)


# ---- 位面熔炉：紫灰的机壳，观察窗两边是铜线圈，中间翻滚的紫色等离子；顶上一圈约束环

def plane_face(rng, on: bool) -> Canvas:
    c = shell(rng, PLUM)
    status_lights(c, on)
    c.recess(3, 3, 12, 12, PLUM, 4)
    for x, y in c.cells(4, 4, 11, 11):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        arm = math.sin(math.atan2(dy, dx) * 2 - d * 1.2)
        if on:
            if d < 1.2:
                lv = 5
            elif d < 2.3:
                lv = 4
            elif d < 4.1:
                lv = 3 if arm > 0.2 else 2
            else:
                lv = 2 if arm > 0.5 else -1
            if lv >= 0:
                c.set(x, y, VIOLET_GLOW, lv)
            else:
                c.set(x, y, PLUM, 1)
        else:
            c.set(x, y, PLUM, 2 if d < 3.6 and arm > 0.4 else (1 if d < 4.4 else 0))
    # 两侧的约束线圈：竖着的铜绕组，一圈一圈明暗相间
    for x in (3, 12):
        for y in range(4, 12):
            c.set(x, y, COPPER, (3 if x == 3 else 2) + (y % 2), glint=True)
    c.rect(3, 13, 12, 13, VIOLET, 3)
    c.set(3, 13, VIOLET, 4)
    bolts(c, 1)
    return c


@texture("block/plane_smelter_front", refs=("block/blast_furnace_front_on",))
def plane_smelter_front(rng):
    return plane_face(rng, False)


@texture("block/plane_smelter_front_on", glow=True)
def plane_smelter_front_on(rng):
    return plane_face(rng, True)


@texture("block/plane_smelter_side")
def plane_smelter_side(rng):
    c = shell(rng, PLUM)
    # 一组百叶，叶片之间透出一点里面的紫光；下面一根横绕的线圈
    c.panel(3, 3, 12, 9, PLUM, 4)
    for y in (4, 6, 8):
        c.hline(4, 11, y, VIOLET, 2)
        c.hline(4, 11, y + 1, PLUM, 5)
    c.cylinder(2, 11, 13, 12, COPPER, 3, vertical=False)
    for x in range(3, 13, 2):
        c.shift_rect(x, 11, x, 12, -1)
    bolts(c, 1)
    return c


def plane_top_face(rng, on: bool) -> Canvas:
    c = shell(rng, PLUM)
    crucible(c, 7.5, 7.5, 3.6, 5.6, STEEL, 5)
    for x, y in c.disc_cells(7.5, 7.5, 3.6):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        arm = math.sin(math.atan2(dy, dx) * 2 - d * 1.2)
        if on:
            c.set(x, y, VIOLET_GLOW, 5 if d < 1.0 else (4 if d < 1.9 else (3 if arm > 0 else 2)))
        else:
            c.set(x, y, PLUM, 2 if arm > 0.4 else 1)
    # 约束环上下左右四个发射极
    for x, y in ((7, 2), (2, 8), (13, 7), (8, 13)):
        c.set(x, y, VIOLET_GLOW if on else VIOLET, 5 if on else 2, glint=True)
    return c


@texture("block/plane_smelter_top")
def plane_smelter_top(rng):
    return plane_top_face(rng, False)


@texture("block/plane_smelter_top_on", glow=True)
def plane_smelter_top_on(rng):
    return plane_top_face(rng, True)


# ---- 化工厂：白色涂装，正面是反应釜的圆形观察窗（绿色的溶液），顶上一个大釜盖、一个计量罐和排气管

def chem_face(rng, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    status_lights(c, on)
    # 反应釜的圆形观察窗：钢的窗框，下面六成是溶液，上面是蒸汽
    cx, cy = 7.5, 8.0
    win = c.disc_cells(cx, cy, 4.4)
    glass = set(c.disc_cells(cx, cy, 3.4))
    for x, y in win:
        if (x, y) in glass:
            continue
        toward = ((x - cx) * -0.58 + (y - cy) * -0.62)
        c.set(x, y, STEEL, 5 if toward > 0.8 else (2 if toward < -0.8 else 3))
    for x, y in glass:
        if y >= 8:
            if on:
                c.set(x, y, GREEN_GLOW, 4 if y == 8 else 3)
            else:
                c.set(x, y, GREEN, 3 if y == 8 else 2)
        else:
            c.set(x, y, GLASS, 1 if on else 0)
    if on:
        # 气泡往上冒，不对称地散着
        for x, y in ((6, 10), (9, 9), (8, 11), (5, 9), (9, 6), (8, 7)):
            c.set(x, y, GREEN_GLOW, 5, glint=True)
    else:
        c.set(5, 6, GLASS, 3, glint=True)
        c.set(6, 5, GLASS, 3, glint=True)
    hazard(c, 2, 13, 13, 14)
    return c


@texture("block/chemical_plant_front", refs=("block/brewing_stand", "block/cauldron_side"))
def chemical_plant_front(rng):
    return chem_face(rng, False)


@texture("block/chemical_plant_front_on", glow=True)
def chemical_plant_front_on(rng):
    return chem_face(rng, True)


@texture("block/chemical_plant_side")
def chemical_plant_side(rng):
    c = shell(rng, WHITE)
    # 一根横穿的物料管，一块检修板，一个红色的阀门手轮，下面警示带
    c.cylinder(1, 3, 14, 4, STEEL, 4, vertical=False)
    for x in (4, 11):
        c.vline(x, 3, 4, STEEL, 2)
    c.panel(3, 6, 8, 11, WHITE, 4)
    c.hline(4, 7, 8, WHITE, 2)
    c.hline(4, 7, 9, WHITE, 5)
    c.ring(10.5, 8.5, 1.6, RED, 3)
    c.set(10, 7, RED, 5, glint=True)
    c.set(11, 9, RED, 1, glint=True)
    c.set(10, 8, STEEL, 5, glint=True)
    c.set(11, 8, STEEL, 3, glint=True)
    hazard(c, 2, 13, 13, 14)
    return c


@texture("block/chemical_plant_top")
def chemical_plant_top(rng):
    c = shell(rng, WHITE)
    # 一个大釜的盖（中间一个人孔），右上一个小计量罐，一根管子连过去；右下一根排气管
    c.cylinder(9, 6, 10, 9, STEEL, 4)
    lid(c, 5.5, 9.0, 3.9, STEEL, 4, hatch=1.3)
    lid(c, 10.5, 4.5, 2.4, STEEL, 4)
    c.ring(11.5, 11.5, 1.5, STEEL, 3)
    c.set(11, 11, GRAPHITE, 0)
    c.set(12, 12, STEEL, 5)
    return c


# ---- 分馏塔：一根竖着的玻璃管，里面是淡绿的氢；工作时管芯透出重氢的青光

def frac_tube(c: Canvas, lit: bool):
    c.cylinder(5, 1, 10, 14, HYDROGEN, 3)
    if lit:
        c.rect(7, 1, 8, 14, CYAN_GLOW, 5)
        c.vline(6, 1, 14, CYAN_GLOW, 4)
        c.vline(9, 1, 14, CYAN_GLOW, 3)
    c.vline(4, 1, 14, STEEL, 6)
    c.vline(11, 1, 14, STEEL, 2)
    for y in (2, 7, 12):
        c.hline(4, 11, y, STEEL, 5)
        c.hline(4, 11, y + 1, STEEL, 3)
        c.set(11, y, STEEL, 3)
    if not lit:
        # 玻璃上的竖向反光
        for y in (4, 5, 9, 10):
            c.set(6, y, HYDROGEN, HYDROGEN.top, glint=True)


def frac_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    frac_tube(c, on)
    # 右边一列竖着的状态灯
    c.vline(13, 4, 10, GRAPHITE, 1)
    for y in (5, 7, 9):
        c.set(13, y, CYAN_GLOW if on else SCREEN, 5 if on else 2, glint=True)
    return c


@texture("block/fractionator_front")
def fractionator_front(rng):
    return frac_face(rng, False)


@texture("block/fractionator_front_on", glow=True)
def fractionator_front_on(rng):
    return frac_face(rng, True)


@texture("block/fractionator_side")
def fractionator_side(rng):
    c = casing(rng)
    frac_tube(c, False)
    # 两边接进来的进料管
    c.cylinder(1, 9, 3, 10, STEEL, 4, vertical=False)
    c.cylinder(12, 4, 14, 5, STEEL, 4, vertical=False)
    return c


@texture("block/fractionator_top")
def fractionator_top(rng):
    c = casing(rng)
    c.ring(7.5, 7.5, 4.6, STEEL, 2)
    crucible(c, 7.5, 7.5, 3.0, 4.2, STEEL, 5)
    for x, y in c.disc_cells(7.5, 7.5, 3.0):
        r = math.hypot(x - 7.5, y - 7.5)
        c.set(x, y, HYDROGEN, 3 if r < 1.6 else 2)
    c.set(6, 6, HYDROGEN, HYDROGEN.top, glint=True)
    bolts(c, 1)
    return c


# ---- 采矿机：黄色涂装（高级采矿机是蓝灰），正面出料口，顶上电机，底下钻头

def mining_face(rng, on: bool, adv: bool) -> Canvas:
    paint, base = (NAVY, 4) if adv else (HAZARD, 4)
    c = shell(rng, paint, base)
    status_lights(c, on)
    if adv:
        chute(c, 2, 4, 13, 11, on, ((4, 7, COPPER), (9, 8, BLUE)))
        hazard(c, 1, 13, 14, 13, BLUE, 3, GRAPHITE, 1)
    else:
        chute(c, 3, 4, 12, 11, on, ((5, 7, COPPER), (8, 9, IRON)))
        hazard(c, 1, 13, 14, 13, HAZARD, 3, GRAPHITE, 1)
    return c


@texture("block/mining_machine_front", refs=("block/dispenser_front", "block/dropper_front"))
def mining_machine_front(rng):
    return mining_face(rng, False, False)


@texture("block/mining_machine_front_on", glow=True)
def mining_machine_front_on(rng):
    return mining_face(rng, True, False)


@texture("block/mining_machine_side")
def mining_machine_side(rng):
    c = shell(rng, HAZARD, 4)
    # 一块深色的检修板，上面一组百叶；下沿警示带
    c.panel(3, 3, 12, 10, STEEL, 3)
    c.vent(4, 4, 11, 8, STEEL, 3)
    hazard(c, 1, 12, 14, 13, HAZARD, 3, GRAPHITE, 1)
    return c


@texture("block/mining_machine_top")
def mining_machine_top(rng):
    c = shell(rng, HAZARD, 4)
    motor(c, 7.5, 7.5, 5.4, 6)
    bolts(c, 1, 4)
    return c


@texture("block/mining_machine_bottom")
def mining_machine_bottom(rng):
    c = casing(rng, 3)
    # 钻头朝下：三片螺旋的刃，刃口受光
    c.disc(7.5, 7.5, 6.2, GRAPHITE, 1)
    for x, y in c.disc_cells(7.5, 7.5, 6.2):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        s = math.sin(a * 3 + d * 0.85)
        if s > 0.25:
            c.set(x, y, STEEL, 6 if s > 0.85 else (5 if s > 0.55 else 4))
        elif s > -0.1:
            c.set(x, y, STEEL, 2)
    c.shade_disc(7.5, 7.5, 1.3, STEEL, 5)
    return c


@texture("block/advanced_mining_machine_front")
def advanced_mining_machine_front(rng):
    return mining_face(rng, False, True)


@texture("block/advanced_mining_machine_front_on", glow=True)
def advanced_mining_machine_front_on(rng):
    return mining_face(rng, True, True)


@texture("block/advanced_mining_machine_side")
def advanced_mining_machine_side(rng):
    c = shell(rng, NAVY, 4)
    c.panel(2, 3, 13, 10, STEEL, 3)
    c.vent(3, 4, 12, 8, STEEL, 3)
    # 两根液压管从检修板下面伸出来
    c.cylinder(4, 10, 5, 12, STEEL, 4)
    c.cylinder(10, 10, 11, 12, STEEL, 4)
    hazard(c, 1, 13, 14, 13, BLUE, 3, GRAPHITE, 1)
    return c


@texture("block/advanced_mining_machine_top")
def advanced_mining_machine_top(rng):
    c = shell(rng, NAVY, 4)
    motor(c, 7.5, 7.5, 5.8, 8)
    c.set(7, 7, BLUE, 5, glint=True)
    c.set(8, 8, BLUE, 2, glint=True)
    bolts(c, 1)
    return c


# ---- 抽水机：蓝灰的机壳，侧面一根竖的进水管；正面左边是液位计，右边是看叶轮的流量窗

def pump_pipe(c: Canvas):
    c.cylinder(5, 1, 9, 14, BLUE, 3)
    for y in (3, 11):
        c.hline(4, 10, y, STEEL, 5)
        c.hline(4, 10, y + 1, STEEL, 3)
        c.set(10, y, STEEL, 3)


def pump_face(rng, on: bool) -> Canvas:
    c = shell(rng, NAVY, 4)
    status_lights(c, on)
    # 液位计：竖着的玻璃管，运转时水满
    c.recess(3, 4, 6, 12, NAVY, 4)
    level = 6 if on else 10
    for y in range(5, 12):
        for x in (4, 5):
            if y >= level:
                c.set(x, y, WATER, 4 if x == 4 else 3)
            else:
                c.set(x, y, GLASS, 0)
    c.hline(4, 5, level, WATER, 5)
    for y in (6, 8, 10):
        c.set(6, y, NAVY, 6)
    # 流量窗：圆的观察窗里一个叶轮，运转时窗里是流动的水
    for x, y in c.disc_cells(10.0, 8.0, 2.6):
        toward = (x - 10.0) * -0.58 + (y - 8.0) * -0.62
        c.set(x, y, STEEL, 5 if toward > 0.9 else (2 if toward < -0.9 else 3))
    for x, y in c.cells(9, 7, 11, 9):
        c.set(x, y, WATER if on else GRAPHITE, 4 if on else 0)
    for x, y in ((10, 7), (9, 8), (10, 8), (11, 8), (10, 9)):
        c.set(x, y, STEEL, 5 if (x, y) == (10, 8) else 4, glint=True)
    if on:
        c.set(11, 7, WATER, 5, glint=True)
    lamp(c, 9, 12, on, CYAN_GLOW)
    lamp(c, 11, 12, on, GREEN_GLOW)
    bolts(c, 1)
    return c


@texture("block/water_pump_front")
def water_pump_front(rng):
    return pump_face(rng, False)


@texture("block/water_pump_front_on", glow=True)
def water_pump_front_on(rng):
    return pump_face(rng, True)


@texture("block/water_pump_side")
def water_pump_side(rng):
    c = shell(rng, NAVY, 4)
    pump_pipe(c)
    bolts(c, 1)
    return c


@texture("block/water_pump_top")
def water_pump_top(rng):
    c = shell(rng, NAVY, 4)
    fan(c, 7.5, 7.5, 5.0, 5)
    bolts(c, 1)
    return c


@texture("block/water_pump_bottom")
def water_pump_bottom(rng):
    c = casing(rng, 3)
    # 进水口的格栅，每个格子里看得到暗的水
    c.rect(3, 3, 12, 12, STEEL, 4)
    for x, y in c.cells(3, 3, 12, 12):
        if (x - 3) % 3 and (y - 3) % 3:
            c.set(x, y, WATER, 2 if (x - 3) % 3 == 1 and (y - 3) % 3 == 1 else 1)
    c.hline(3, 12, 3, STEEL, 5)
    c.vline(3, 3, 12, STEEL, 5)
    c.hline(4, 12, 12, STEEL, 2)
    c.vline(12, 4, 12, STEEL, 2)
    return c


# ---- 采油机：贴在井口上的泵房，正面一块压力表和一排灯；上面是红色的钢井架和抽油管

@texture("block/oil_extractor_side")
def oil_extractor_side(rng):
    # 模型只用 x 1–14、y 4–13 这一块（后面的小箱子用 y 8–13）
    c = band_panel(rng, 1, 4, 14, 13)
    hazard(c, 2, 5, 13, 6, ORANGE, 3, GRAPHITE, 1)
    for y in (9, 12):
        c.hline(2, 13, y - 1, STEEL, 3)
        c.hline(2, 13, y, STEEL, 5)
    c.rivet(3, 10, STEEL, 4)
    c.rivet(11, 10, STEEL, 4)
    return c


def extractor_front(rng) -> Canvas:
    c = band_panel(rng, 2, 4, 13, 13)
    # 压力表：白的表盘，红的指针
    c.disc(7.5, 8.0, 3.0, STEEL, 2)
    for x, y in c.disc_cells(7.5, 8.0, 2.2):
        c.set(x, y, WHITE, 5 if x + y < 15 else 4)
    c.set(7, 7, RED, 3, glint=True)
    c.set(6, 6, RED, 4, glint=True)
    c.set(8, 8, GRAPHITE, 2, glint=True)
    for x, y in ((5, 8), (7, 5), (10, 8)):
        c.set(x, y, GRAPHITE, 3, glint=True)
    # 一排指示灯（运转时由前面的发光层点亮）
    c.hline(4, 10, 12, GRAPHITE, 1)
    for x in (5, 7, 9):
        lamp(c, x, 12, False)
    return c


@texture("block/oil_extractor_front")
def oil_extractor_front(rng):
    return extractor_front(rng)


@texture("block/oil_extractor_front_on")
def oil_extractor_front_on(rng):
    # 贴在正面前面的自发光层（模型 uv 4–12、4–14）：只画亮的灯，别的透明
    c = Canvas()
    for x in (5, 7, 9):
        lamp(c, x, 12, True)
    return c


@texture("block/oil_extractor_beam", tile=False)
def oil_extractor_beam(rng):
    # 井架：两边的立柱（模型的四根柱子用最左边两列），中间交叉的斜撑和横撑，空的地方透明
    c = Canvas()
    for y0 in (0, 8):
        c.line(2, y0, 13, y0 + 7, RED, 3)
        c.line(13, y0, 2, y0 + 7, RED, 2)
    for y in (0, 8):
        c.hline(2, 13, y, RED, 4)
    c.hline(2, 13, 15, RED, 1)
    for y in range(16):
        c.set(0, y, RED, 4)
        c.set(1, y, RED, 2)
        c.set(14, y, RED, 3)
        c.set(15, y, RED, 1)
    return c


@texture("block/oil_extractor_pipe", tile=False)
def oil_extractor_pipe(rng):
    # 竖着的钢管：模型用中间几列（6–9、7–8），明暗每 4 列重复一次，任何一截看着都是圆的；两道法兰
    c = Canvas()
    lv = {2: 5, 3: 4, 0: 3, 1: 2}
    for x in range(16):
        c.vline(x, 0, 15, STEEL, lv[x % 4])
    for y in (3, 11):
        for x in range(16):
            c.set(x, y, STEEL, 6 if x % 4 in (2, 3) else 4)
            c.set(x, y + 1, STEEL, 3 if x % 4 in (2, 3) else 1)
    return c


# ---- 原油精炼厂：下半截是加热炉（正面一扇有炉栅的观察窗），上面两根白色保温层的分馏塔

@texture("block/oil_refinery_side")
def oil_refinery_side(rng):
    # 模型用 x 1–14、y 3–15
    c = band_panel(rng, 1, 3, 14, 15)
    for y in (6, 10):
        c.cylinder(2, y, 13, y + 1, RUST, 4, vertical=False)
        for x in (4, 10):
            c.vline(x, y, y + 1, STEEL, 3)
    c.ring(11.5, 13.0, 1.3, RED, 3)
    c.set(11, 13, STEEL, 5)
    return c


@texture("block/oil_refinery_top")
def oil_refinery_top(rng):
    c = casing(rng)
    # 炉顶：两块检修盖板，中间一道缝
    c.panel(3, 3, 7, 6, STEEL, 4)
    c.panel(9, 3, 12, 6, STEEL, 4)
    bolts(c, 2)
    return c


def refinery_front(rng) -> Canvas:
    c = band_panel(rng, 1, 3, 14, 15)
    # 加热炉的观察窗（模型的发光层盖在 x 4–10、y 3–10 上），竖的炉栅
    c.recess(3, 4, 11, 11, STEEL, 4)
    c.rect(4, 5, 10, 10, GRAPHITE, 0)
    c.hline(4, 10, 10, GRAPHITE, 1)
    for x in (5, 7, 9):
        c.vline(x, 5, 10, STEEL, 3)
    hazard(c, 3, 13, 12, 13, ORANGE, 3, GRAPHITE, 1)
    return c


@texture("block/oil_refinery_front")
def oil_refinery_front(rng):
    return refinery_front(rng)


@texture("block/oil_refinery_front_on")
def oil_refinery_front_on(rng):
    # 自发光层：只画炉栅之间的火，越往下越亮
    c = Canvas()
    for y in range(5, 11):
        for x in (4, 6, 8, 10):
            c.set(x, y, ORANGE_GLOW, 2 if y < 7 else (3 if y < 9 else 4))
    c.set(6, 10, ORANGE_GLOW, 5)
    c.set(8, 9, ORANGE_GLOW, 5)
    return c


@texture("block/oil_refinery_column", tile=False)
def oil_refinery_column(rng):
    # 两根塔身（模型用 x 2–6 和 9–13）：白色保温层，每四格一道钢箍，各自按圆柱打光
    c = Canvas()
    c.fill(WHITE, 4)
    for x0 in (2, 9):
        c.cylinder(x0, 0, x0 + 4, 15, WHITE, 4)
    c.vline(7, 0, 15, WHITE, 3)
    c.vline(8, 0, 15, WHITE, 2)
    for y in (0, 4, 8, 12):
        for x in range(16):
            c.set(x, y, STEEL, c.level(x, y) - 0)
            c.shift(x, y, 0)
        for x0 in (2, 9):
            c.hline(x0, x0 + 4, y, STEEL, 5)
            c.set(x0, y, STEEL, 6)
            c.set(x0 + 4, y, STEEL, 3)
        c.hline(0, 1, y, STEEL, 4)
        c.hline(7, 8, y, STEEL, 3)
        c.hline(14, 15, y, STEEL, 4)
    return c


# ---- 原油渗口：一汪黑油，油面上有彩虹色的油膜和气泡；侧面是往下渗油的泥土

@texture("block/crude_oil_seep_top", rough=True, refs=("block/dirt", "block/mud"))
def crude_oil_seep_top(rng):
    c = Canvas()
    c.fill(SOIL, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 14, 2)
    c.clusters(rng, 0, 0, 15, 15, -1, 14, 2)
    pool = c.disc_cells(7.5, 7.5, 5.6)
    edge = set(pool) - set(c.disc_cells(7.5, 7.5, 4.6))
    for x, y in pool:
        c.set(x, y, OIL, 1)
    for x, y in edge:
        # 油渗进泥里的一圈：湿泥更暗
        c.set(x, y, SOIL, 1)
    for x, y in c.disc_cells(7.5, 7.5, 4.6):
        if (x * 2 + y) % 7 == 0:
            c.set(x, y, OIL, 2)
    # 油膜：一道斜的彩虹色
    for x, y in ((5, 9), (6, 8), (7, 8), (8, 7), (9, 6)):
        c.set(x, y, VIOLET, 2, glint=True)
    for x, y in ((6, 9), (8, 8), (9, 7), (10, 6)):
        c.set(x, y, TEAL, 2, glint=True)
    # 气泡
    for x, y in ((5, 5), (10, 10), (4, 8)):
        c.set(x, y, OIL, 5, glint=True)
        c.set(x + 1, y + 1, OIL, 0, glint=True)
    return c


@texture("block/crude_oil_seep_side", rough=True, refs=("block/dirt",))
def crude_oil_seep_side(rng):
    c = Canvas()
    c.fill(SOIL, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 16, 2)
    c.clusters(rng, 0, 0, 15, 15, -1, 16, 2)
    for x in range(16):
        depth = 2 + int(abs(math.sin(x * 1.7 + 0.4)) * 5) + rng.randint(0, 2)
        for y in range(depth):
            c.set(x, y, OIL, 1 if y < depth - 1 else 2)
        c.set(x, depth, SOIL, 1)
    # 几道往下流的油线，尖端有一滴
    for x in (3, 9, 13):
        d = 9 + (x * 5) % 4
        c.vline(x, 4, d, OIL, 1)
        c.set(x, d + 1, OIL, 3, glint=True)
    c.set(2, 0, OIL, 3, glint=True)
    c.set(11, 1, OIL, 3, glint=True)
    return c


# ---- 工厂锚：厚重的深色基座，四面各一道青色的光槽，顶上发光的锚环

@texture("block/factory_anchor_side", refs=("block/lodestone_side",))
def factory_anchor_side(rng):
    c = casing(rng, 3)
    c.recess(5, 2, 10, 12, GRAPHITE, 3)
    c.vline(6, 3, 11, CYAN_GLOW, 3)
    c.vline(7, 3, 11, CYAN_GLOW, 5)
    c.vline(8, 3, 11, CYAN_GLOW, 4)
    c.vline(9, 3, 11, CYAN_GLOW, 2)
    # 下面一道加厚的底座
    c.hline(1, 14, 13, STEEL, 5)
    c.hline(2, 14, 14, STEEL, 2)
    bolts(c, 2, 3)
    return c


@texture("block/factory_anchor_top", refs=("block/lodestone_top",))
def factory_anchor_top(rng):
    c = casing(rng, 3)
    c.ring(7.5, 7.5, 5.4, STEEL, 2, 2.2)
    c.ring(7.5, 7.5, 4.6, CYAN_GLOW, 3, 1.2)
    for x, y in c.disc_cells(7.5, 7.5, 4.6):
        r = math.hypot(x - 7.5, y - 7.5)
        if 3.6 < r <= 4.4 and (x + y) < 15:
            c.set(x, y, CYAN_GLOW, 4)
    c.shade_disc(7.5, 7.5, 2.0, STEEL, 4)
    c.rect(7, 7, 8, 8, CYAN_GLOW, 5)
    bolts(c, 1, 3)
    return c


# ---- 通用机壳：结构件、底座用的深色钢板。好些多方块设备只用它最下面三行当底座的侧面、四角当脚；顶面是四块盖板

@texture("block/machine_casing", refs=("block/iron_block", "block/smooth_stone"))
def machine_casing(rng):
    c = casing(rng, 3)
    # 中间一道横缝，把板子分成上下两块
    c.hline(2, 13, 7, STEEL, 1)
    c.hline(2, 13, 8, STEEL, 4)
    # 底下三行是底座的侧面：上沿背光、中间受光的棱、最下面暗
    c.hline(1, 14, 13, STEEL, 2)
    c.hline(1, 14, 14, STEEL, 4)
    c.hline(0, 15, 15, STEEL, 1)
    c.set(0, 14, STEEL, 2)
    c.set(15, 14, STEEL, 2)
    for x, y in ((3, 3), (11, 3), (3, 10), (11, 10)):
        c.rivet(x, y, STEEL, 3)
    return c


@texture("block/machine_casing_top", refs=("block/iron_block",))
def machine_casing_top(rng):
    c = casing(rng)
    # 四块拼起来的盖板：十字缝，每块角上一颗螺栓
    c.hline(1, 14, 7, STEEL, 2)
    c.hline(1, 14, 8, STEEL, 5)
    c.vline(7, 1, 14, STEEL, 2)
    c.vline(8, 1, 14, STEEL, 5)
    c.set(7, 7, STEEL, 1)
    c.set(8, 8, STEEL, 5)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.rivet(x, y, STEEL, 4)
    return c


# ---- 矩阵研究站：五格高的底座（侧面只露出最下面五行），上面玻璃罩、四根立柱、顶盖，罩里悬着发光的矩阵

def lab_base(rng) -> Canvas:
    c = casing(rng)
    # 模型的底座只露出 y 11–15：上沿一道受光的棱，下面一排青色的灯
    c.hline(0, 15, 11, STEEL, 6)
    c.rect(1, 12, 14, 14, STEEL, 3)
    c.hline(0, 15, 15, STEEL, 1)
    c.vline(0, 12, 14, STEEL, 2)
    c.vline(15, 12, 14, STEEL, 2)
    return c


@texture("block/matrix_lab_side")
def matrix_lab_side(rng):
    c = lab_base(rng)
    for x in range(2, 14, 3):
        c.set(x, 13, CYAN_GLOW, 4, glint=True)
        c.set(x + 1, 13, CYAN_GLOW, 1, glint=True)
    return c


@texture("block/matrix_lab_front")
def matrix_lab_front(rng):
    c = lab_base(rng)
    # 正面一块小屏：两行读数和一个红点（两种矩阵的颜色）
    c.rect(4, 12, 11, 14, SCREEN, 1)
    c.hline(5, 9, 12, CYAN_GLOW, 4)
    c.hline(5, 7, 14, CYAN_GLOW, 3)
    c.set(10, 14, RED_GLOW, 4, glint=True)
    c.set(2, 13, CYAN_GLOW, 4, glint=True)
    c.set(13, 13, CYAN_GLOW, 4, glint=True)
    return c


@texture("block/matrix_lab_top")
def matrix_lab_top(rng):
    c = casing(rng)
    # 底座顶面（罩在玻璃里）：格栅台面，正中一圈朝上的发射环
    c.recess(2, 2, 13, 13, STEEL, 3)
    for x, y in c.cells(3, 3, 12, 12):
        if (x - 3) % 3 == 0 or (y - 3) % 3 == 0:
            c.set(x, y, STEEL, 2)
    c.ring(7.5, 7.5, 2.6, CYAN_GLOW, 3)
    c.shade_disc(7.5, 7.5, 1.2, STEEL, 4)
    return c


@texture("block/matrix_lab_frame", tile=False)
def matrix_lab_frame(rng):
    # 一张图兼几用：x 1–2、13–14 是立柱，y 0–1 是顶盖的侧面，1–14 整块是顶盖的上面，中间 6–9 是托座的顶
    c = Canvas()
    c.fill(STEEL, 5)
    c.clusters(rng, 3, 3, 12, 12, -1, 6, 2)
    c.hline(0, 15, 0, STEEL, 6)
    c.hline(0, 15, 1, STEEL, 4)
    for y in range(2, 16):
        c.set(1, y, STEEL, 6)
        c.set(2, y, CYAN_GLOW, 2)
        c.set(13, y, CYAN_GLOW, 2)
        c.set(14, y, STEEL, 3)
    c.hline(3, 12, 13, STEEL, 4)
    c.hline(1, 14, 14, STEEL, 2)
    # 托座和顶盖正中的发射器
    c.ring(7.5, 7.5, 2.4, STEEL, 3)
    c.rect(6, 6, 9, 9, CYAN_GLOW, 3)
    c.rect(7, 7, 8, 8, CYAN_GLOW, 5)
    return c


@texture("block/matrix_lab_glass", tile=False)
def matrix_lab_glass(rng):
    # 淡青的半透明玻璃（模型用 x 1.5–14.5、y 1.5–11）：上沿亮一些，斜着两道反光
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, GLASS, 4, alpha=48)
    c.hline(0, 15, 1, GLASS, 5)
    c.hline(0, 15, 2, GLASS, 5)
    for x in range(16):
        c.opacity(x, 1, 150)
        c.opacity(x, 2, 90)
    for i in range(5):
        c.set(3 + i, 9 - i, GLASS, 5, alpha=120)
        c.set(4 + i, 9 - i, GLASS, 5, alpha=80)
    c.set(9, 9, GLASS, 5, alpha=90)
    c.set(10, 8, GLASS, 5, alpha=90)
    return c


@texture("block/matrix_lab_core", tile=False)
def matrix_lab_core(rng):
    # 罩里悬着的矩阵：自发光的小方块（模型的元素 light_emission，顶盖挡住了上面，主要看四个侧面，
    # 侧面用 uv x 5.5–10.5、y 3.5–8.5）。以侧面的中心 (8, 6) 画同心的方框：最外一圈蓝色的棱，
    # 里面一圈青光，正中 2×2 白热的核；棱的四个角最亮，像方块的顶点。底面用 y 5.5–10.5，往下接着画两圈。
    c = Canvas()
    for x, y in c.cells():
        dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 6)
        d = max(dx, dy)
        if d < 1:
            c.set(x, y, WHITE_GLOW, 5)
        elif d < 2:
            c.set(x, y, CYAN_GLOW, 3 if dx > 1 and dy > 1 else 4)
        elif d < 3:
            c.set(x, y, BLUE_GLOW, 4 if dx > 2 and dy > 2 else 3)
        elif d < 4:
            c.set(x, y, CYAN_GLOW, 3)
        else:
            c.set(x, y, BLUE_GLOW, 3 if d < 5 else 2)
    return c


# ---- 卵序列机：白色的机壳，正面一个玻璃圆罩，罩里一颗卵；运转时罩里亮起青色，一道扫描光横着扫过

def egg_face(rng, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    # 底座
    c.rect(2, 12, 13, 13, STEEL, 3)
    c.hline(2, 13, 12, STEEL, 5)
    # 圆罩：超椭圆，顶上圆、两边直一些
    for y in range(2, 12):
        for x in range(2, 14):
            d = (abs((x - 7.5) / 5.6) ** 2.6 + abs((y - 11.5) / 9.0) ** 2.6) ** (1 / 2.6)
            if d < 0.86:
                if on:
                    c.set(x, y, CYAN_GLOW, 2 if d > 0.6 else 3)
                else:
                    c.set(x, y, GLASS, 1 if d > 0.6 else 2)
            elif d < 1.0:
                c.set(x, y, STEEL, 5 if x < 8 and y < 8 else 2)
    # 卵：上窄下宽，左上受光，右下的影子
    egg = [
        "..443..",
        ".45443.",
        ".54432.",
        "5544332",
        "5443322",
        ".43322.",
    ]
    c.draw(egg, {"0123456": BONE}, 4, 5)
    if on:
        for x in range(4, 12):
            px = c.get(x, 8)
            if px and px[0] is not BONE:
                c.set(x, 8, CYAN_GLOW, 4)
            elif px:
                c.shift(x, 8, 1)
    else:
        c.set(5, 4, GLASS, 4, glint=True)
        c.set(4, 5, GLASS, 4, glint=True)
    lamp(c, 3, 14, on, GREEN_GLOW)
    c.hline(10, 12, 14, CYAN_GLOW if on else SCREEN, 4 if on else 2)
    return c


@texture("block/egg_sequencer_front", refs=("block/turtle_egg",))
def egg_sequencer_front(rng):
    return egg_face(rng, False)


@texture("block/egg_sequencer_front_on", glow=True)
def egg_sequencer_front_on(rng):
    return egg_face(rng, True)


@texture("block/egg_sequencer_side")
def egg_sequencer_side(rng):
    c = shell(rng, WHITE)
    # 两根营养液管：绿的和粉的，一节一节的
    for x, r in ((5, GREEN), (10, PINK)):
        c.cylinder(x, 2, x + 1, 13, r, 3)
        for y in range(3, 14, 4):
            c.hline(x, x + 1, y, STEEL, 5)
        c.hline(x, x + 1, 1, STEEL, 2)
    bolts(c, 2)
    return c


@texture("block/egg_sequencer_top")
def egg_sequencer_top(rng):
    c = shell(rng, WHITE)
    c.recess(3, 3, 12, 12, WHITE, 3)
    for x, y in c.cells(4, 4, 11, 11):
        c.set(x, y, GLASS, 1 if x + y > 16 else 2)
    c.set(5, 5, GLASS, 4, glint=True)
    c.set(6, 5, GLASS, 3, glint=True)
    c.set(5, 6, GLASS, 3, glint=True)
    return c


# ---- 水培槽：十格高的白色塑料槽（侧面只露出下面十行），正面一条补光灯；顶上是营养液和定植杯

def tray_base(rng) -> Canvas:
    c = Canvas()
    c.fill(WHITE, 4)
    c.clusters(rng, 1, 8, 14, 14, -1, 4, 2)
    c.hline(0, 15, 6, WHITE, 6)
    c.hline(0, 15, 7, WHITE, 3)
    c.hline(0, 15, 15, WHITE, 2)
    c.vline(0, 7, 14, WHITE, 5)
    c.vline(15, 7, 15, WHITE, 2)
    return c


@texture("block/hydroponic_tray_side", tile=False)
def hydroponic_tray_side(rng):
    c = tray_base(rng)
    # 一扇小窗看营养液的液位
    c.recess(3, 9, 12, 13, WHITE, 3)
    c.rect(4, 10, 11, 12, WATER, 2)
    c.hline(4, 11, 10, WATER, 4)
    c.set(5, 11, WATER, 3)
    c.set(9, 12, WATER, 3)
    return c


def tray_face(rng, on: bool) -> Canvas:
    c = tray_base(rng)
    # 补光灯条：通电时品红色（红光加蓝光）
    c.hline(2, 13, 9, MAGENTA_GLOW, 3 if on else 1)
    c.hline(2, 13, 10, MAGENTA_GLOW, 2 if on else 0)
    # 控制面板：一块小屏和一盏状态灯
    screen(c, 3, 12, 7, 13, on, GREEN_GLOW, [5, 3])
    lamp(c, 11, 13, on, GREEN_GLOW)
    lamp(c, 12, 13, on, RED_GLOW if not on else GREEN_GLOW)
    return c


@texture("block/hydroponic_tray_front", tile=False)
def hydroponic_tray_front(rng):
    return tray_face(rng, False)


@texture("block/hydroponic_tray_front_on", tile=False)
def hydroponic_tray_front_on(rng):
    return tray_face(rng, True)


@texture("block/hydroponic_tray_top")
def hydroponic_tray_top(rng):
    c = Canvas()
    c.fill(WATER, 2)
    c.clusters(rng, 2, 2, 13, 13, 1, 8, 2)
    for x, y in ((4, 11), (11, 4), (12, 10), (3, 5)):
        c.set(x, y, WATER, 4, glint=True)
    # 两格宽的槽沿
    c.frame(0, 0, 15, 15, WHITE, 5)
    c.frame(1, 1, 14, 14, WHITE, 4)
    c.hline(0, 15, 15, WHITE, 3)
    c.vline(15, 0, 15, WHITE, 3)
    c.hline(1, 14, 1, WHITE, 6)
    # 定植杯和岩棉
    c.ring(7.5, 7.5, 3.2, GRAPHITE, 2)
    for x, y in c.disc_cells(7.5, 7.5, 2.2):
        c.set(x, y, SAND, 4 if x + y < 15 else 3)
    c.set(7, 7, SAND, 5, glint=True)
    return c


# ---- 营养处理器：白色的小烤箱，正面玻璃门里是加热管，工作时烧红；顶上一大一小两个电热盘

@texture("block/nutrient_processor_side", refs=("block/smoker_side",))
def nutrient_processor_side(rng):
    c = shell(rng, WHITE)
    c.vent(4, 5, 11, 10, WHITE, 4)
    return c


def processor_face(rng, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    # 控制条：两个旋钮和一块小屏
    c.panel(2, 2, 13, 3, WHITE, 3)
    for x in (3, 5):
        c.set(x, 2, GRAPHITE, 3, glint=True)
        c.set(x, 3, GRAPHITE, 1, glint=True)
    c.hline(8, 12, 2, GREEN_GLOW if on else SCREEN, 4 if on else 2)
    c.hline(8, 12, 3, SCREEN, 1)
    if on:
        c.hline(8, 10, 3, GREEN_GLOW, 3)
    # 门：深色门框，玻璃后面是炉腔和上下两根加热管
    c.frame(2, 5, 13, 13, GRAPHITE, 2)
    c.hline(2, 13, 5, GRAPHITE, 3)
    for y in range(6, 13):
        for x in range(3, 13):
            if on:
                c.set(x, y, ORANGE_GLOW, 2 if y in (8, 9, 10) else 1)
            else:
                c.set(x, y, GRAPHITE, 0 if y < 9 else 1)
    for y in (7, 11):
        c.hline(3, 12, y, ORANGE_GLOW if on else STEEL, 4 if on else 2)
    if on:
        c.hline(4, 11, 9, ORANGE_GLOW, 3)
    c.set(4, 6, GLASS, 4, glint=True)
    c.set(5, 6, GLASS, 3, glint=True)
    # 门把手
    c.hline(4, 11, 14, STEEL, 5)
    c.set(11, 14, STEEL, 3)
    return c


@texture("block/nutrient_processor_front", refs=("block/smoker_front",))
def nutrient_processor_front(rng):
    return processor_face(rng, False)


@texture("block/nutrient_processor_front_on", glow=True, refs=("block/smoker_front_on",))
def nutrient_processor_front_on(rng):
    return processor_face(rng, True)


def processor_top_face(rng, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    # 一大一小两个电热盘错开摆，右上角一组竖的散热孔
    for cx, cy, rad in ((5.5, 9.5, 3.4), (11.0, 4.5, 2.2)):
        for x, y in c.disc_cells(cx, cy, rad):
            d = math.hypot(x - cx, y - cy)
            if d > rad - 0.8:
                toward = (x - cx) * -0.58 + (y - cy) * -0.62
                c.set(x, y, STEEL, 5 if toward > 0.6 else (3 if toward < -0.6 else 4))
            elif rad * 0.3 <= d < rad - 1.2:
                c.set(x, y, ORANGE_GLOW if on else GRAPHITE, 3 if on else 2)
            else:
                c.set(x, y, GRAPHITE, 1)
    for x in (3, 5, 7):
        c.vline(x, 3, 5, WHITE, 1)
        c.vline(x + 1, 3, 5, WHITE, 5)
    return c


@texture("block/nutrient_processor_top", refs=("block/smoker_top",))
def nutrient_processor_top(rng):
    return processor_top_face(rng, False)


@texture("block/nutrient_processor_top_on", glow=True)
def nutrient_processor_top_on(rng):
    return processor_top_face(rng, True)


# ---- 便携精炼机：十二格见方的深灰金属箱（模型只用中间那块），正面圆的观察窗里是熔融物，顶上一个漏斗口

def refiner_box(rng, x0, y0, x1, y1) -> Canvas:
    c = Canvas()
    c.fill(STEEL, 3)
    c.clusters(rng, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 1, 6, 2)
    c.hline(x0, x1, y0, STEEL, 5)
    c.vline(x0, y0, y1, STEEL, 4)
    c.vline(x1, y0 + 1, y1, STEEL, 1)
    c.hline(x0 + 1, x1, y1, STEEL, 1)
    return c


def refiner_side_base(rng) -> Canvas:
    c = refiner_box(rng, 2, 4, 13, 15)
    hazard(c, 3, 13, 12, 14)
    c.rivet(3, 5, STEEL, 3)
    c.rivet(11, 5, STEEL, 3)
    return c


@texture("block/portable_refiner_side", tile=False)
def portable_refiner_side(rng):
    c = refiner_side_base(rng)
    c.vent(5, 7, 10, 11, STEEL, 3)
    return c


def refiner_face(rng, on: bool) -> Canvas:
    c = refiner_side_base(rng)
    for x, y in c.disc_cells(7.5, 9.0, 3.4):
        d = math.hypot(x - 7.5, y - 9.0)
        if d > 2.5:
            toward = (x - 7.5) * -0.58 + (y - 9.0) * -0.62
            c.set(x, y, STEEL, 6 if toward > 0.8 else (2 if toward < -0.8 else 4))
        elif on:
            c.set(x, y, ORANGE_GLOW, 4 if d < 1.2 else 3)
        else:
            c.set(x, y, GRAPHITE, 1 if y > 8 else 0)
    if on:
        c.set(7, 10, ORANGE_GLOW, 5)
    else:
        c.set(6, 8, GLASS, 3, glint=True)
    lamp(c, 12, 6, on)
    return c


@texture("block/portable_refiner_front", tile=False)
def portable_refiner_front(rng):
    return refiner_face(rng, False)


@texture("block/portable_refiner_front_on", tile=False)
def portable_refiner_front_on(rng):
    return refiner_face(rng, True)


@texture("block/portable_refiner_top", tile=False)
def portable_refiner_top(rng):
    c = refiner_box(rng, 2, 2, 13, 13)
    # 漏斗口：一圈往里收的斜面，越往里越暗
    for x, y in c.disc_cells(7.5, 7.5, 3.8):
        d = math.hypot(x - 7.5, y - 7.5)
        toward = (x - 7.5) * -0.58 + (y - 7.5) * -0.62
        lv = 1 + int(d * 0.9)
        c.set(x, y, STEEL, min(5, lv + (-1 if toward > 1 else (1 if toward < -1 else 0))))
    c.rect(7, 7, 8, 8, GRAPHITE, 0)
    return c


@texture("block/portable_refiner_bottom", tile=False)
def portable_refiner_bottom(rng):
    c = refiner_box(rng, 2, 2, 13, 13)
    c.shift_rect(3, 3, 12, 12, -1)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.set(x, y, RUBBER, 2)
        c.set(x + 1, y, RUBBER, 1)
        c.set(x, y + 1, RUBBER, 1)
        c.set(x + 1, y + 1, RUBBER, 0)
    return c


# ---- 电网接口：一格的设备，正面窗里三个铜端子，工作时端子之间亮起电流；顶面四角是接线柱

@texture("block/grid_interface_side")
def grid_interface_side(rng):
    c = casing(rng)
    c.hline(2, 13, 3, TEAL, 4)
    c.hline(2, 13, 4, TEAL, 2)
    c.panel(4, 7, 11, 12, STEEL, 4)
    c.hline(5, 10, 9, STEEL, 2)
    c.hline(5, 10, 10, STEEL, 5)
    return c


def grid_face(rng, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    c.recess(3, 5, 12, 13, STEEL, 4)
    c.rect(4, 6, 11, 12, GRAPHITE, 0)
    for x0 in (4, 7, 10):
        c.rect(x0, 8, x0 + 1, 11, COPPER, 3)
        c.set(x0, 8, COPPER, 5, glint=True)
        c.vline(x0, 9, 11, COPPER, 4)
        c.set(x0 + 1, 11, COPPER, 1, glint=True)
        c.hline(x0, x0 + 1, 7, STEEL, 4)
    for x in (6, 9):
        for y in range(8, 12):
            if on:
                c.set(x + (y % 2) * 0, y, CYAN_GLOW, 5 if y in (9, 10) else 4, glint=True)
            elif y % 2:
                c.set(x, y, CYAN_GLOW, 1, glint=True)
    return c


@texture("block/grid_interface_front")
def grid_interface_front(rng):
    return grid_face(rng, False)


@texture("block/grid_interface_front_on", glow=True)
def grid_interface_front_on(rng):
    return grid_face(rng, True)


@texture("block/grid_interface_top")
def grid_interface_top(rng):
    c = casing(rng)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.rect(x, y, x + 1, y + 1, COPPER, 3)
        c.set(x, y, COPPER, 5, glint=True)
        c.set(x + 1, y + 1, COPPER, 1, glint=True)
    c.recess(4, 6, 11, 9, STEEL, 4)
    c.hline(5, 10, 7, TEAL, 4)
    c.hline(5, 10, 8, TEAL, 2)
    return c
