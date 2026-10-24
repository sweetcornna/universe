"""高级设备、戴森球和传送的贴图（ADR-0031）。

终局的设备比基础设备讲究：白漆面板、金箔的包边、紫色和蓝色的能量光，但机壳还是 parts.py 那一套
（深色的外框、上左亮下右暗的倒角、稍凹的面板），和别的设备摆在一起是一家人。
戴森球那几样（电磁弹射器、垂直发射井、射线接收站、运载火箭、太阳帆）是多元素的模型，
每张贴图只有模型用到的那几块要画好，注释里写着用的是哪块。
"""
from __future__ import annotations

import math
import random

from canvas import LIGHT, Canvas
from color import glow, ramp
from palette import (BLUE_GLOW, CONCRETE, COPPER, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN_GLOW, HAZARD,
                     ORANGE_GLOW, RED, RED_GLOW, SCREEN, STEEL, TEAL, TITANIUM, VIOLET, VIOLET_GLOW, WHITE,
                     WHITE_GLOW, YELLOW_GLOW, register)
from parts import bolts, casing, status_lights
from recipe import texture

# 负熵熔炉、粒子对撞机的机身：发紫的深色金属
VOID = register(ramp("advanced_void", 292, 0.045, n=8, lo=0.18, hi=0.66, shift=12, peak=0.5))
# 量子化工厂的溶液：青绿色的光，比界面的青色偏绿
TEAL_GLOW = register(glow("advanced_teal_glow", 178, 0.135))


# ---- 共用的小部件 ----

def recolor_all(c: Canvas, r, cells=None, d: int = 0):
    """把一片像素换到另一条色阶上，明暗关系不变（只换钢的像素，灯和别的颜色不动）。"""
    for x, y in cells if cells is not None else c.cells():
        px = c.get(x, y)
        if px and px[0] is STEEL:
            c.recolor(x, y, r, d)


def body(rng: random.Random, frame=STEEL, panel=STEEL, base: int = 4, edge=None) -> Canvas:
    """机壳：外框和面板可以换成别的色阶（白漆、深紫），结构照 parts.casing。

    edge 是最外一圈的色阶：白漆的机器最外一圈留深色的钢，和相邻的方块才分得开。
    """
    c = casing(rng, base)
    inner = set(c.cells(2, 2, 13, 13))
    outer = {(x, y) for x, y in c.cells() if x in (0, 15) or y in (0, 15)}
    if panel is not STEEL:
        recolor_all(c, panel, inner)
    if frame is not STEEL:
        recolor_all(c, frame, [p for p in c.cells() if p not in inner and p not in outer])
    edge = edge or frame
    if edge is not STEEL:
        recolor_all(c, edge, outer)
    return c


def torus(c: Canvas, cx: float, cy: float, rad: float, width: float, r, base: int, amp: float = 1.6,
          cells=None) -> list[tuple[int, int]]:
    """圆环（管道、包边）：截面分外沿、顶、内沿三圈，外沿朝左上的一段亮、内沿朝左上的一段亮
    （也就是环的右下内侧），顶上一圈亮一级。分圈打光，像素不会碎。返回画到的格子。"""
    out = []
    lx, ly = LIGHT[0], LIGHT[1]
    ln = math.hypot(lx, ly)
    lx, ly = lx / ln, ly / ln
    for x, y in cells if cells is not None else c.cells():
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        k = (d - rad) / (width / 2)
        if abs(k) > 1 or d < 1e-6:
            continue
        band = 1 if k > 0.34 else (-1 if k < -0.34 else 0)
        facing = (dx * lx + dy * ly) / d          # 这一点的径向朝着光的程度
        if band == 0:
            lv = base + 1
        else:
            lv = base + round(amp * band * facing)
        c.set(x, y, r, lv, glint=True)
        out.append((x, y))
    return out


def corner_bolts(c: Canvas, r=STEEL, inset: int = 1, base: int = 4):
    """四角的螺栓，颜色跟着机壳走（parts.bolts 只画钢的）。"""
    for x, y in ((inset, inset), (14 - inset, inset), (inset, 14 - inset), (14 - inset, 14 - inset)):
        c.rivet(x, y, r, base)


def ring_cells(c: Canvas, cx, cy, r0, r1):
    return [(x, y) for x, y in c.cells() if r0 <= math.hypot(x - cx, y - cy) < r1]


def trim(c: Canvas, y: int, r=GOLD_FOIL, x0: int = 3, x1: int = 12, lv: int = 3):
    """下面一条包边（金箔或涂装），左端受光、右端背光。"""
    c.rect(x0, y, x1, y, r, lv)
    c.set(x0, y, r, lv + 1)
    c.set(x1, y, r, lv - 1)


def window(c: Canvas, x0, y0, x1, y1, frame=STEEL, base: int = 4):
    """凹进去的观察窗：窗框上左是影子、下右受光，里面先铺暗玻璃。"""
    c.recess(x0, y0, x1, y1, frame, base)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SCREEN, 1)


# ---- 负熵熔炉：深紫的机身，金箔包边，圆舷窗里一团旋转的紫光 ----

def vortex(c: Canvas, cx, cy, rad, on: bool):
    """舷窗里的旋涡：两条螺旋臂亮、臂之间暗，中心白热。不亮时只有暗玻璃里一点紫色的残影。"""
    for x, y in c.disc_cells(cx, cy, rad):
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        arm = (math.cos(2 * a - d * 1.5) + 1) / 2
        t = d / (rad + 0.5)
        if on:
            lv = 1 + round(arm * arm * 3.2 + (1 - t) * 1.2)
            c.set(x, y, VIOLET_GLOW, min(lv, 4), glint=True)
        elif arm > 0.72 and t < 0.95:
            c.set(x, y, VIOLET, 1, glint=True)
        else:
            c.set(x, y, VOID, 1 if t < 0.6 else 0, glint=True)
    ix, iy = math.floor(cx), math.floor(cy)
    if on:
        c.set(ix, iy, WHITE_GLOW, 5, glint=True)
        c.set(ix + 1, iy, VIOLET_GLOW, 5, glint=True)
        c.set(ix, iy + 1, VIOLET_GLOW, 5, glint=True)
        c.set(ix + 1, iy + 1, VIOLET_GLOW, 4, glint=True)
    else:
        c.set(ix, iy, VIOLET, 2, glint=True)


def porthole(c: Canvas, cx, cy, rad, on: bool, bezel=GOLD_FOIL, dark=VOID):
    """包边的圆舷窗：一圈暗缝，金箔的环，里面是旋涡，玻璃左上一点反光。"""
    for x, y in ring_cells(c, cx, cy, rad + 0.3, rad + 2.2):
        c.set(x, y, dark, 1)
    torus(c, cx, cy, rad + 1.0, 1.9, bezel, 3)
    vortex(c, cx, cy, rad, on)
    if not on:
        c.set(math.floor(cx) - 2, math.floor(cy) - 2, GLASS, 3, glint=True)


def negentropy_face(rng, on: bool) -> Canvas:
    c = body(rng, VOID, VOID)
    status_lights(c, on)
    porthole(c, 7.5, 8.0, 2.9, on)
    trim(c, 13)
    corner_bolts(c, VOID)
    return c


@texture("block/negentropy_smelter_front", refs=("block/blast_furnace_front_on", "block/respawn_anchor_side4"))
def negentropy_smelter_front(rng):
    return negentropy_face(rng, False)


@texture("block/negentropy_smelter_front_on", glow=True)
def negentropy_smelter_front_on(rng):
    return negentropy_face(rng, True)


def negentropy_top_face(rng, on: bool) -> Canvas:
    c = body(rng, VOID, VOID)
    # 顶上的坩埚口：一圈金箔，往下看是旋涡；四角各一个散热孔
    porthole(c, 7.5, 7.5, 3.4, on)
    for x0, y0 in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.set(x0, y0, VOID, 1)
        c.set(x0 + 1, y0, VOID, 2)
        c.set(x0, y0 + 1, VOID, 2)
        c.set(x0 + 1, y0 + 1, VOID, 5)
    return c


@texture("block/negentropy_smelter_top", refs=("block/respawn_anchor_top_off",))
def negentropy_smelter_top(rng):
    return negentropy_top_face(rng, False)


@texture("block/negentropy_smelter_top_on", glow=True, refs=("block/respawn_anchor_top",))
def negentropy_smelter_top_on(rng):
    return negentropy_top_face(rng, True)


@texture("block/negentropy_smelter_side")
def negentropy_smelter_side(rng):
    c = body(rng, VOID, VOID)
    # 侧面：上下两条金箔包边，中间一排竖的散热鳍
    trim(c, 3, x0=2, x1=13)
    c.hline(2, 13, 4, VOID, 2)
    c.vent(3, 5, 12, 11, VOID, 4, horizontal=False)
    trim(c, 13, x0=2, x1=13)
    c.hline(2, 13, 12, VOID, 2)
    return c


# ---- 量子化工厂：白漆的实验设备，窗里一只大烧瓶，青绿色的溶液 ----

FLASK = [
    "..gHg...",
    "..g.g...",
    ".gg.gg..",
    "g.....g.",
    "gLLLLLg.",
    "gLLLLLg.",
    ".gLLLg..",
]


def quantum_face(rng, on: bool) -> Canvas:
    c = body(rng, STEEL, WHITE)
    status_lights(c, on)
    window(c, 3, 4, 12, 12)
    # 窗里的烧瓶：玻璃的轮廓亮，液面以下是溶液，运转时溶液发光、冒泡
    if on:
        c.rect(4, 5, 11, 11, TEAL_GLOW, 1)
    for dy, row in enumerate(FLASK):
        for dx, ch in enumerate(row):
            x, y = 4 + dx, 5 + dy
            if ch == "g":
                c.set(x, y, GLASS, 2 if dx < 3 else 1, glint=True)
            elif ch == "H":
                c.set(x, y, STEEL, 6, glint=True)
            elif ch == "L":
                if on:
                    c.set(x, y, TEAL_GLOW, 4 if dy == 4 else 3, glint=True)
                else:
                    c.set(x, y, TEAL, 3 if dy == 4 else 2, glint=True)
    c.set(5, 8, GLASS, 4, glint=True)                       # 玻璃的反光
    if on:
        c.set(7, 9, TEAL_GLOW, 5, glint=True)               # 气泡
        c.set(8, 7, TEAL_GLOW, 4, glint=True)
    # 右边一根竖的冷凝管
    c.vline(11, 5, 11, GLASS, 2)
    c.set(11, 6, TEAL_GLOW if on else TEAL, 3, glint=True)
    trim(c, 13, TEAL, lv=3)
    bolts(c, 1)
    return c


@texture("block/quantum_chemical_plant_front", refs=("block/brewing_stand",))
def quantum_chemical_plant_front(rng):
    return quantum_face(rng, False)


@texture("block/quantum_chemical_plant_front_on", glow=True)
def quantum_chemical_plant_front_on(rng):
    return quantum_face(rng, True)


@texture("block/quantum_chemical_plant_top")
def quantum_chemical_plant_top(rng):
    c = body(rng, STEEL, WHITE)
    # 顶上三个球形储液罐：钢的箍，青绿的玻璃罐身；罐之间连着管子
    c.hline(5, 10, 4, STEEL, 3)
    c.vline(7, 5, 9, STEEL, 3)
    c.vline(8, 5, 9, STEEL, 5)
    for cx, cy in ((4.5, 4.5), (10.5, 4.5), (7.5, 10.5)):
        c.disc(cx, cy, 2.5, STEEL, 2)
        c.shade_disc(cx, cy, 2.0, TEAL, 3)
    return c


@texture("block/quantum_chemical_plant_side")
def quantum_chemical_plant_side(rng):
    c = body(rng, STEEL, WHITE)
    # 侧面：两根竖的输液管（钛，带法兰），中间一条液位窗看得见里面的溶液
    for x in (3, 11):
        c.cylinder(x, 3, x + 1, 12, TITANIUM, 4)
        for y in (4, 11):
            c.hline(x, x + 1, y, TITANIUM, 2)
    c.recess(6, 3, 9, 12, WHITE, 3)
    c.rect(7, 4, 8, 11, SCREEN, 1)
    c.rect(7, 7, 8, 11, TEAL, 2)
    c.hline(7, 8, 7, TEAL, 4)
    c.set(7, 4, GLASS, 3, glint=True)
    for y in (5, 8, 10):
        c.set(9, y, WHITE, 1)                               # 刻度
    bolts(c, 1)
    return c


# ---- 重组式制造台：深色机身，金色的两条机械臂，窗里是暖白的工作灯 ----

# 窗内 8×7：两条臂从左上、右上的底座伸下来，在中间的工件上方合拢
ARMS_OFF = [
    "ab....ba",
    ".a....a.",
    ".ba..ab.",
    "..a..a..",
    "..bccb..",
    "...ee...",
    "..dddd..",
]
ARMS_ON = [
    "AB....BA",
    ".A....A.",
    ".BA..AB.",
    "..A..A..",
    "..BccB..",
    "...pp...",
    "..dddd..",
]


def recomposing_face(rng, on: bool) -> Canvas:
    c = body(rng, STEEL, GRAPHITE)
    status_lights(c, on)
    window(c, 3, 4, 12, 12)
    if on:
        # 工作灯（青色，和别的设备一样）从上往下照，越往下越暗；金色的臂在灯前
        c.rect(4, 5, 11, 7, CYAN_GLOW, 3)
        c.rect(4, 8, 11, 11, CYAN_GLOW, 2)
    # draw 的图例里字符在串里的位置就是级数，前面用不会出现的字符占位
    c.draw(ARMS_ON if on else ARMS_OFF,
           {"%%%ab": GOLD_FOIL, "%%%%c": GOLD_FOIL, "%%%d": STEEL, "%e": SCREEN, "%%%%p": CYAN_GLOW,
            "%%AB": GOLD_FOIL},
           4, 5)
    if on:
        c.set(7, 10, WHITE_GLOW, 5, glint=True)
        c.set(8, 10, CYAN_GLOW, 5, glint=True)
        for x, y in ((4, 5), (11, 5)):
            c.set(x, y, GOLD_FOIL, 5, glint=True)
    else:
        c.set(11, 8, GLASS, 2, glint=True)
    trim(c, 13)
    bolts(c, 1)
    return c


@texture("block/recomposing_assembler_front", refs=("block/crafter_north",))
def recomposing_assembler_front(rng):
    return recomposing_face(rng, False)


@texture("block/recomposing_assembler_front_on", glow=True)
def recomposing_assembler_front_on(rng):
    return recomposing_face(rng, True)


@texture("block/recomposing_assembler_top")
def recomposing_assembler_top(rng):
    c = body(rng, STEEL, GRAPHITE)
    # 顶上的转台：凹进去的圆槽，一圈金箔的滚道，中间轴毂伸出两根臂
    for x, y in c.disc_cells(7.5, 7.5, 5.6):
        c.set(x, y, GRAPHITE, 1)
    torus(c, 7.5, 7.5, 4.5, 1.9, GOLD_FOIL, 3)
    c.hline(4, 11, 7, GRAPHITE, 4)
    c.hline(4, 11, 8, GRAPHITE, 2)
    c.shade_disc(7.5, 7.5, 1.4, STEEL, 4)
    return c


@texture("block/recomposing_assembler_side")
def recomposing_assembler_side(rng):
    c = body(rng, STEEL, GRAPHITE)
    # 侧面：上面一条金箔腰线，下面一扇双开的检修门，两个金色的把手
    trim(c, 3, x0=2, x1=13)
    c.hline(2, 13, 4, GRAPHITE, 1)
    c.panel(4, 6, 11, 12, GRAPHITE, 3)
    c.vline(7, 7, 11, GRAPHITE, 1)
    c.vline(8, 7, 11, GRAPHITE, 4)
    for y in (8, 10):
        c.set(6, y, GOLD_FOIL, 4, glint=True)
        c.set(9, y, GOLD_FOIL, 3, glint=True)
    bolts(c, 1)
    return c


# ---- 微型粒子对撞机：一圈加速管，四块偏转磁铁，运转时管里是蓝光，中心对撞点白热 ----

def collider_ring(c: Canvas, on: bool, top: bool = False):
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 6.3):
        c.set(x, y, VOID, 1)
    tube = torus(c, cx, cy, 4.6, 2.1, STEEL, 4)
    if on:
        # 管顶开一道窗缝：束流是一条蓝线，两团粒子束对着走
        for x, y in tube:
            d = math.hypot(x - cx, y - cy)
            if abs(d - 4.6) < 0.45:
                a = math.atan2(y - cy, x - cx)
                bunch = max(math.cos(a - 0.8), math.cos(a - 0.8 - math.pi))
                c.set(x, y, BLUE_GLOW, 5 if bunch > 0.93 else 3, glint=True)
    # 四块偏转磁铁（铜线圈的方块），骑在四个斜角的管子上
    for mx, my in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.set(mx, my, COPPER, 5, glint=True)
        c.set(mx + 1, my, COPPER, 4, glint=True)
        c.set(mx, my + 1, COPPER, 3, glint=True)
        c.set(mx + 1, my + 1, COPPER, 2, glint=True)
    # 中心的对撞室
    if top:
        c.shade_disc(cx, cy, 1.5, STEEL, 4)
    elif on:
        c.disc(cx, cy, 1.5, BLUE_GLOW, 3)
        c.set(7, 7, WHITE_GLOW, 5, glint=True)
        c.set(8, 7, WHITE_GLOW, 4, glint=True)
        c.set(7, 8, WHITE_GLOW, 4, glint=True)
        c.set(8, 8, BLUE_GLOW, 4, glint=True)
    else:
        c.disc(cx, cy, 1.5, SCREEN, 1)
        c.set(7, 7, GLASS, 2, glint=True)


def collider_face(rng, on: bool) -> Canvas:
    c = body(rng, STEEL, VOID)
    collider_ring(c, on)
    bolts(c, 1)
    return c


@texture("block/miniature_particle_collider_front", refs=("block/conduit", "block/lodestone_top"))
def miniature_particle_collider_front(rng):
    return collider_face(rng, False)


@texture("block/miniature_particle_collider_front_on", glow=True)
def miniature_particle_collider_front_on(rng):
    return collider_face(rng, True)


@texture("block/miniature_particle_collider_top")
def miniature_particle_collider_top(rng):
    c = body(rng, STEEL, VOID)
    collider_ring(c, False, top=True)
    return c


@texture("block/miniature_particle_collider_side")
def miniature_particle_collider_side(rng):
    c = body(rng, STEEL, VOID)
    # 侧面：两根横的冷却管（钛，带管箍），下面一块警示牌
    for y in (3, 6):
        c.cylinder(2, y, 13, y + 1, TITANIUM, 4, vertical=False)
        for x in (4, 11):
            c.vline(x, y, y + 1, TITANIUM, 2)
    c.hline(2, 13, 5, VOID, 1)
    c.hline(2, 13, 8, VOID, 1)
    c.panel(3, 9, 12, 12, VOID, 4)
    c.stripes(4, 10, 11, 11, HAZARD, 3, GRAPHITE, 1, period=4, width=2)
    bolts(c, 1)
    return c


# ---- 传送器：深色机壳，窗里一柱线圈，顶上是传送台；空间站那台是白漆 ----

def coil_window(c: Canvas, on: bool, frame):
    window(c, 3, 4, 12, 13, frame, 4)
    if on:
        c.rect(4, 5, 11, 12, CYAN_GLOW, 1)
    # 一柱横着绕的线圈：每圈是一根横的铜管，运转时线圈发青光，圈之间透光
    for y in range(5, 13, 2):
        if on:
            c.hline(5, 10, y, CYAN_GLOW, 4)
            c.set(5, y, CYAN_GLOW, 5, glint=True)
            c.set(10, y, CYAN_GLOW, 3)
        else:
            c.hline(5, 10, y, COPPER, 3)
            c.set(5, y, COPPER, 5, glint=True)
            c.set(10, y, COPPER, 1)


def teleporter_body(rng, white: bool) -> Canvas:
    if white:
        return body(rng, WHITE, WHITE, base=4, edge=STEEL)
    return body(rng, base=3)


def teleporter_face(rng, on: bool, white: bool) -> Canvas:
    frame = WHITE if white else STEEL
    c = teleporter_body(rng, white)
    status_lights(c, on)
    coil_window(c, on, frame)
    corner_bolts(c, WHITE if white else STEEL, base=4 if white else 3)
    return c


def teleporter_pad(rng, on: bool, white: bool) -> Canvas:
    frame = WHITE if white else STEEL
    c = teleporter_body(rng, white)
    cx = cy = 7.5
    # 俯视的传送台：外圈金属包边，里面暗玻璃，两道同心的发射环，中间一个发射头
    torus(c, cx, cy, 6.0, 1.8, frame, 3)
    for x, y in c.disc_cells(cx, cy, 4.9):
        d = math.hypot(x - cx, y - cy)
        ring = 3.9 <= d or 1.9 <= d < 2.8
        if ring:
            c.set(x, y, CYAN_GLOW if on else SCREEN, (4 if d < 4.4 else 3) if on else 3)
        else:
            c.set(x, y, CYAN_GLOW if on else SCREEN, 1)
    if on:
        c.disc(cx, cy, 1.0, WHITE_GLOW, 5)
    else:
        c.disc(cx, cy, 1.0, SCREEN, 2)
        c.set(7, 7, GLASS, 3, glint=True)
    return c


def teleporter_side_face(rng, white: bool) -> Canvas:
    frame = WHITE if white else STEEL
    base = 4 if white else 3
    c = teleporter_body(rng, white)
    # 侧面：一条青色的腰线，三道竖的散热槽
    c.hline(2, 13, 3, TEAL, 2)
    c.hline(2, 13, 4, frame, base - 1)
    for x in (4, 7, 10):
        c.vline(x, 6, 12, frame, base - 2)
        c.vline(x + 1, 6, 12, frame, base + 1)
        c.set(x + 1, 6, frame, base - 1)
    corner_bolts(c, frame, base=base)
    return c


@texture("block/teleporter_front", refs=("block/respawn_anchor_side0", "block/end_portal_frame_side"))
def teleporter_front(rng):
    return teleporter_face(rng, False, False)


@texture("block/teleporter_front_on", glow=True)
def teleporter_front_on(rng):
    return teleporter_face(rng, True, False)


@texture("block/teleporter_top", refs=("block/end_portal_frame_top",))
def teleporter_top(rng):
    return teleporter_pad(rng, False, False)


@texture("block/teleporter_top_on", glow=True)
def teleporter_top_on(rng):
    return teleporter_pad(rng, True, False)


@texture("block/teleporter_side")
def teleporter_side(rng):
    return teleporter_side_face(rng, False)


@texture("block/station_teleporter_front")
def station_teleporter_front(rng):
    return teleporter_face(rng, False, True)


@texture("block/station_teleporter_front_on", glow=True)
def station_teleporter_front_on(rng):
    return teleporter_face(rng, True, True)


@texture("block/station_teleporter_top")
def station_teleporter_top(rng):
    return teleporter_pad(rng, False, True)


@texture("block/station_teleporter_top_on", glow=True)
def station_teleporter_top_on(rng):
    return teleporter_pad(rng, True, True)


@texture("block/station_teleporter_side")
def station_teleporter_side(rng):
    return teleporter_side_face(rng, True)


# ---- 电磁弹射器：深色的底座，白漆的转台，铜线圈绕着导轨，导轨中间一条发光的轨道 ----

@texture("block/em_rail_ejector_base", refs=("block/smithing_table_side",))
def em_rail_ejector_base(rng):
    # 底座是 16×4 的扁块：侧面只用最下面四行，底面用整张
    c = body(rng, base=3)
    c.hline(1, 14, 12, STEEL, 5)
    c.stripes(1, 13, 14, 14, HAZARD, 3, GRAPHITE, 1, period=4, width=2)
    c.set(1, 13, HAZARD, 4)
    c.hline(0, 15, 15, STEEL, 2)
    c.vline(0, 12, 15, STEEL, 2)
    c.vline(15, 12, 15, STEEL, 1)
    return c


@texture("block/em_rail_ejector_top")
def em_rail_ejector_top(rng):
    # 底座的顶面：大半被转台盖住，露出来的是一圈回转轨道和四个指示点
    c = body(rng, base=3)
    c.ring(7.5, 7.5, 6.3, GRAPHITE, 1, width=1.2)
    c.ring(7.5, 7.5, 5.1, STEEL, 5, width=0.9)
    for x, y in ((7, 1), (14, 7), (8, 14), (1, 8)):
        c.set(x, y, TEAL, 3, glint=True)
    return c


@texture("block/em_rail_ejector_ring", refs=("block/quartz_block_top",))
def em_rail_ejector_ring(rng):
    # 转台叠了三层白漆的板：14×14、10×10、7×7，正好落在 1–14、3–12、4–11 这几圈上，
    # 每一层的边上一道倒角；侧面是第 11、10、8 行
    c = Canvas()
    c.fill(WHITE, 4)
    c.clusters(rng, 1, 1, 14, 14, -1, 6, 2)
    for x0, x1 in ((1, 14), (3, 12)):
        c.bevel(x0, x0, x1, x1, 2, -2)
    c.bevel(5, 5, 10, 10, 1, -1, raised=False)
    for x, y in c.disc_cells(7.5, 7.5, 1.6):
        c.set(x, y, STEEL, 3)
    c.shade_disc(7.5, 7.5, 1.0, STEEL, 4)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, STEEL, 3, glint=True)
    c.hline(1, 14, 11, WHITE, 3)
    c.frame(0, 0, 15, 15, WHITE, 2)
    return c


@texture("block/em_rail_ejector_rail", refs=("block/iron_block",))
def em_rail_ejector_rail(rng):
    # 结构钢：导轨、支臂、射线接收站的立柱、发射井的立柱都用它，任何一小块拿出来都得像钢材。
    # 横向每四行一道接缝（上暗下亮），左边受光、右边背光，偶尔一颗铆钉
    c = Canvas()
    c.fill(STEEL, 3)
    for y in range(16):
        if y % 4 == 3:
            c.hline(0, 15, y, STEEL, 1)
        elif y % 4 == 0:
            c.hline(0, 15, y, STEEL, 4)
    c.vline(0, 0, 15, STEEL, 4)
    c.vline(15, 0, 15, STEEL, 2)
    c.clusters(rng, 1, 1, 14, 14, 1, 7, 3)
    for x, y in ((3, 1), (11, 5), (6, 9), (13, 13)):
        c.rivet(x, y, STEEL, 3)
    return c


@texture("block/em_rail_ejector_coil", refs=("block/copper_block",))
def em_rail_ejector_coil(rng):
    # 铜线圈：一圈一圈横着绕，每圈上沿受光、圈和圈之间是暗缝。运载火箭的喷管也用这张（再生冷却的铜管）
    c = Canvas()
    for y in range(16):
        c.hline(0, 15, y, COPPER, 4 if y % 2 == 0 else 2)
    for _ in range(9):
        x, y = rng.randint(0, 14), rng.randrange(0, 16, 2)
        c.set(x, y, COPPER, 5, glint=True)
        c.set(x + 1, y, COPPER, 5, glint=True)
    c.vline(0, 0, 15, COPPER, 1)
    c.vline(15, 0, 15, COPPER, 1)
    return c


@texture("block/em_rail_ejector_glow")
def em_rail_ejector_glow(rng):
    # 导轨中间的加速轨道（自发光）：中间两列最亮，往两边暗；每四行一个亮的脉冲
    c = Canvas()
    for x in range(16):
        d = abs(x - 7.5)
        base = 5 if d < 1 else (4 if d < 2 else (3 if d < 4 else 2))
        for y in range(16):
            c.set(x, y, CYAN_GLOW, base + (1 if y % 4 == 1 and d < 3 else 0))
    return c


@texture("block/solar_sail_flight", kind="block", tile=False, refs=("item/phantom_membrane",))
def solar_sail_flight(rng):
    # 飞出去的太阳帆（自发光的薄片）：金色的六边形薄膜，一格一格的太阳能电池，中间十字骨架
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if dx * 0.5 + dy * 0.866 <= 7.0 and dx <= 7.2:
                lv = 4 if (x % 3 != 0 and y % 3 != 0) else 3
                if (x + y) < 13:
                    lv += 1                                  # 朝着光的一半反光强
                c.set(x, y, GOLD_FOIL, lv)
    c.outline(lit=2, shadow=1)
    for i in range(1, 15):
        c.set(i, 7, WHITE, 5)
        c.set(7, i, WHITE, 5)
        c.set(i, 8, WHITE, 3)
        c.set(8, i, WHITE, 3)
    c.rect(7, 7, 8, 8, WHITE, 6)
    for x, y in list(c.cells()):
        if c.get(x, y) and not (abs(x - 7.5) * 0.5 + abs(y - 7.5) * 0.866 <= 7.0 and abs(x - 7.5) <= 7.2):
            c.clear(x, y)
    return c


# ---- 垂直发射井：白漆的井壁，一条红色的涂装，井口一圈警示条纹，井底是导流槽 ----

@texture("block/vertical_launching_silo_side", refs=("block/white_concrete",))
def vertical_launching_silo_side(rng):
    # 上半截的井壁：外侧面用第 6–15 行（井口唇边、红色涂装带、两块板）；下半截的底面用整张
    c = Canvas()
    c.fill(WHITE, 4)
    c.clusters(rng, 1, 0, 14, 15, -1, 8, 2)
    c.hline(0, 15, 6, WHITE, 6)
    c.hline(0, 15, 7, WHITE, 2)
    c.hline(0, 15, 9, RED, 3)
    c.hline(0, 15, 10, RED, 2)
    c.hline(0, 15, 8, WHITE, 5)
    c.hline(0, 15, 11, WHITE, 3)
    for x in (5, 10):
        c.vline(x, 12, 14, WHITE, 2)
        c.vline(x + 1, 12, 14, WHITE, 5)
    for x in (2, 13):
        c.rivet(x, 12, WHITE, 3)
    c.hline(0, 15, 15, STEEL, 2)
    c.hline(0, 15, 0, WHITE, 5)
    c.hline(0, 15, 3, WHITE, 2)
    return c


@texture("block/vertical_launching_silo_lower")
def vertical_launching_silo_lower(rng):
    # 下半截的四面：白漆的机壳，中间一扇检修门，下面一条警示带，门边一盏红色的示廓灯
    c = body(rng, WHITE, WHITE, edge=STEEL)
    c.panel(5, 3, 10, 11, WHITE, 4)
    c.vline(7, 4, 10, WHITE, 2)
    c.vline(8, 4, 10, WHITE, 5)
    c.set(6, 7, STEEL, 3, glint=True)
    c.set(9, 7, STEEL, 3, glint=True)
    c.stripes(2, 12, 13, 13, HAZARD, 3, GRAPHITE, 1, period=4, width=2)
    c.set(3, 4, RED, 4, glint=True)
    c.set(3, 5, RED, 2, glint=True)
    bolts(c, 1)
    return c


@texture("block/vertical_launching_silo_rim", refs=("block/yellow_concrete",))
def vertical_launching_silo_rim(rng):
    # 井口一圈 3 格宽的警示条纹（俯视用外圈三格，侧面用第 5 行）：外沿上左亮、下右暗；
    # 内沿往井里掉下去，左边、上边的内沿背光，右边、下边的内沿朝着光
    c = Canvas()
    c.stripes(0, 0, 15, 15, HAZARD, 3, GRAPHITE, 1, period=4, width=2)
    for i in range(16):
        c.shift(i, 0, 1)
        c.shift(0, i, 1)
        c.shift(i, 15, -1)
        c.shift(15, i, -1)
    for i in range(3, 13):
        c.shift(i, 2, -1)
        c.shift(2, i, -1)
        c.shift(i, 13, 1)
        c.shift(13, i, 1)
    return c


@texture("block/vertical_launching_silo_pad", refs=("block/iron_trapdoor",))
def vertical_launching_silo_pad(rng):
    # 井底：钢格栅，中间一个圆形的导流口，周围被尾焰熏黑
    c = Canvas()
    c.fill(GRAPHITE, 3)
    for i in range(16):
        if i % 4 == 0:
            c.hline(0, 15, i, GRAPHITE, 4)
            c.vline(i, 0, 15, GRAPHITE, 4)
        if i % 4 == 3:
            c.hline(0, 15, i, GRAPHITE, 2)
    for x, y in c.disc_cells(7.5, 7.5, 4.6):
        c.shift(x, y, -1)
    for x, y in c.disc_cells(7.5, 7.5, 3.0):
        c.set(x, y, GRAPHITE, 1)
    for x, y in c.disc_cells(7.5, 7.5, 2.0):
        c.set(x, y, GRAPHITE, 0)
    torus(c, 7.5, 7.5, 3.4, 1.0, STEEL, 3)
    return c


def silo_lamp(on: bool) -> Canvas:
    # 井口四角的示廓灯：模型只取 (1,1)、(14,1)、(1,14)、(14,14) 这几个像素，整张画成一块灯罩
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if on:
            c.set(x, y, RED_GLOW, 4 if d < 2.5 else 3)
        else:
            c.set(x, y, RED, 3 if d < 2.5 else (2 if d < 5 else 1))
    c.frame(0, 0, 15, 15, STEEL, 2)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, RED_GLOW if on else RED, 3 if on else 2, glint=True)
        c.set(x + (1 if x < 8 else -1), y + (1 if y < 8 else -1), RED_GLOW if on else RED, 4 if on else 3, glint=True)
    return c


@texture("block/vertical_launching_silo_light", tile=False, refs=("block/redstone_lamp",))
def vertical_launching_silo_light(rng):
    return silo_lamp(False)


@texture("block/vertical_launching_silo_light_on", tile=False, refs=("block/redstone_lamp_on",))
def vertical_launching_silo_light_on(rng):
    return silo_lamp(True)


# ---- 小型运载火箭：白色的箭体，黑白的滚转标记（照土星五号），红色的整流罩和尾翼 ----

@texture("block/small_carrier_rocket_body", tile=False)
def small_carrier_rocket_body(rng):
    # 箭体只用中间 6.5–9.5 这三列（第 1–14 行），四个面一样：左列受光、右列背光
    c = Canvas()
    shade = {6: 6, 7: 5, 8: 4, 9: 3}
    for x in range(16):
        lv = shade.get(x, 5 if x < 6 else 3)
        c.vline(x, 0, 15, WHITE, lv)
    for y in (4, 8):
        for x in range(16):
            c.set(x, y, GRAPHITE, 3 if x <= 7 else 2)
    # 下面一级的滚转标记：黑白相间的竖条
    for y in range(9, 14):
        for x in range(16):
            if (x // 2) % 2 == 1:
                c.set(x, y, GRAPHITE, 2 if x % 2 == 0 else 1)
    # 上面一级的一小块黑色方标
    for y in (5, 6):
        for x in (8, 9):
            c.set(x, y, GRAPHITE, 2 if x == 8 else 1)
    c.hline(0, 15, 14, WHITE, 2)
    return c


@texture("block/small_carrier_rocket_trim", tile=False)
def small_carrier_rocket_trim(rng):
    # 红漆的零件：整流罩（第 0 行、正中那块）、中段的箍（第 7 行）、尾翼（第 11–14 行）
    c = Canvas()
    for y in range(16):
        for x in range(16):
            lv = 3
            if x in (5, 7, 9) or y == 0:
                lv = 4
            if x in (6, 10):
                lv = 2
            c.set(x, y, RED, lv)
    c.hline(0, 15, 7, RED, 4)
    c.hline(0, 15, 11, RED, 4)
    c.hline(0, 15, 14, RED, 2)
    c.rect(6, 6, 9, 9, RED, 3)
    c.set(6, 6, RED, 5, glint=True)
    return c


@texture("block/rocket_flame", tile=False, refs=("block/fire_0",))
def rocket_flame(rng):
    # 尾焰（自发光）：顶、底面取正中 3×3，侧面取最下一行；中心白热，往外黄、橙
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1.6:
            c.set(x, y, WHITE_GLOW, 5)
        elif d < 3.2:
            c.set(x, y, YELLOW_GLOW, 5)
        elif d < 5.2:
            c.set(x, y, YELLOW_GLOW, 4)
        elif d < 7.0:
            c.set(x, y, ORANGE_GLOW, 4)
        else:
            c.set(x, y, ORANGE_GLOW, 3)
    for x in range(5, 11):
        c.set(x, 15, YELLOW_GLOW, 5 if 6 < x < 9 else 4)
        c.set(x, 0, YELLOW_GLOW, 5 if 6 < x < 9 else 4)
    return c


# ---- 射线接收站：底座、反射盘、盘背的桁架、盘心发光的光子接收头 ----

@texture("block/ray_receiver_base")
def ray_receiver_base(rng):
    # 底座 14×4：侧面用第 12–15 行（上沿受光、一排散热缝、下沿暗），底面用 1–14
    c = body(rng, base=3)
    c.hline(1, 14, 12, STEEL, 5)
    c.vent(1, 13, 14, 14, STEEL, 3, horizontal=False)
    c.hline(1, 14, 15, STEEL, 2)
    return c


@texture("block/ray_receiver_top")
def ray_receiver_top(rng):
    # 底座的顶面：中间被立柱的底板盖住，露出外圈和四角的地脚螺栓
    c = body(rng, base=3)
    c.recess(3, 3, 12, 12, STEEL, 3)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.bolt(x, y, STEEL, 3)
    return c


@texture("block/ray_receiver_dish", refs=("block/glass",))
def ray_receiver_dish(rng):
    # 反射盘（俯视）：一圈一圈的反光面板，盘是凹的，所以右下的内侧亮、左上暗，正中是接收头的座
    c = Canvas()
    cx = cy = 7.5
    for x, y in c.cells():
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        facing = -(dx * LIGHT[0] + dy * LIGHT[1]) / max(d, 0.5)
        lv = 3 + (1 if facing > 0.35 else (-1 if facing < -0.35 else 0))
        if d % 2.6 < 0.75:
            lv -= 1                                      # 面板之间的缝
        c.set(x, y, GLASS, lv, glint=True)
    c.set(9, 9, GLASS, GLASS.top, glint=True)
    c.set(10, 10, GLASS, GLASS.top - 1, glint=True)
    c.rect(7, 7, 8, 8, STEEL, 3)
    c.frame(0, 0, 15, 15, STEEL, 4)
    c.hline(0, 15, 15, STEEL, 2)
    c.vline(15, 0, 15, STEEL, 2)
    return c


@texture("block/ray_receiver_back")
def ray_receiver_back(rng):
    # 盘背：深色的钢，从中心放射出去的八根肋，一圈加强环
    c = Canvas()
    c.fill(STEEL, 2)
    cx = cy = 7.5
    for x, y in c.cells():
        a = math.degrees(math.atan2(y - cy, x - cx)) % 45
        if a < 7 or a > 38:
            c.set(x, y, STEEL, 4 if (x + y) < 15 else 3)
    for x, y in ring_cells(c, cx, cy, 4.6, 5.6):
        c.set(x, y, STEEL, 4)
    for x, y in ring_cells(c, cx, cy, 5.6, 6.3):
        c.set(x, y, STEEL, 1)
    c.shade_disc(cx, cy, 1.5, STEEL, 3)
    return c


@texture("block/ray_receiver_core", refs=("block/shroomlight",))
def ray_receiver_core(rng):
    # 光子接收头（自发光）：只用正中 2×2 和第 0 行的中间两格，中心白热、往外是金黄
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1.5:
            c.set(x, y, WHITE_GLOW, 5)
        elif d < 4:
            c.set(x, y, YELLOW_GLOW, 5)
        elif d < 6.5:
            c.set(x, y, YELLOW_GLOW, 4)
        else:
            c.set(x, y, ORANGE_GLOW, 4)
    for x in (6, 7, 8, 9):
        c.set(x, 0, YELLOW_GLOW, 5)
    return c


# ---- 基地信标：深色的底座，柱身一条青色灯带，顶上的灯 ----

@texture("block/base_beacon_base", refs=("block/beacon", "block/lodestone_top"))
def base_beacon_base(rng):
    # 底座 12×3：顶面用 2–13，侧面用第 13–15 行；上面一块 8×1 的小台用 4–11 和第 0 行
    c = body(rng, base=3)
    c.frame(3, 3, 12, 12, TEAL, 2)
    c.set(3, 3, TEAL, 3)
    c.hline(4, 11, 0, STEEL, 5)
    c.hline(2, 13, 13, STEEL, 5)
    c.hline(2, 13, 14, STEEL, 3)
    for x in (4, 7, 10):
        c.set(x, 14, STEEL, 5, glint=True)
    c.hline(2, 13, 15, STEEL, 1)
    return c


@texture("block/base_beacon_body", tile=False)
def base_beacon_body(rng):
    # 柱身 6 格宽（第 5–10 列，第 3–12 行）：圆柱的明暗，中间两列是青色灯带，每四行一道箍
    c = Canvas()
    shade = {5: 2, 6: 5, 9: 3, 10: 2}
    for x in range(16):
        c.vline(x, 0, 15, STEEL, shade.get(x, 4 if x < 7 else 3))
    for y in range(16):
        c.set(7, y, CYAN_GLOW, 4)
        c.set(8, y, CYAN_GLOW, 3)
        if y % 4 == 2:
            for x in range(16):
                c.set(x, y, STEEL, 2 if x not in (6,) else 4)
    return c


@texture("block/base_beacon_cap", tile=False, refs=("block/sea_lantern",))
def base_beacon_cap(rng):
    # 顶灯（自发光）：侧面用第 7–8 行，顶面用 4.5–11.5；中间白、外面一圈青
    c = Canvas()
    for x, y in c.cells():
        d = max(abs(x - 7.5), abs(y - 7.5))
        c.set(x, y, CYAN_GLOW, 5 if d < 2 else (4 if d < 4 else 3))
    c.rect(7, 7, 8, 8, WHITE_GLOW, 5)
    for x in range(4, 12):
        c.set(x, 7, CYAN_GLOW, 5)
        c.set(x, 8, CYAN_GLOW, 4)
    return c


# ---- 自我演化研究站：矩阵研究站的造型，立柱和顶板换成金箔和白漆，核心是绿紫两色的光 ----

@texture("block/self_evolution_lab_frame", tile=False, refs=("block/gold_block",))
def self_evolution_lab_frame(rng):
    # 四根立柱用第 1–2、13–14 列的第 1–10 行；顶板俯视用 1–14 的整块、侧面用第 0–1 行；
    # 底座正中的小台用 6–9。所以：外圈两格是金箔，里面是白漆顶板，正中一个紫色的发射口
    c = Canvas()
    c.fill(WHITE, 4)
    c.clusters(rng, 3, 3, 12, 12, -1, 5, 2)
    c.bevel(3, 3, 12, 12, -1, 1)
    for x, y in c.cells():
        if x <= 2 or x >= 13 or y <= 2 or y >= 13:
            # 金箔立柱：左列受光、右列背光，每三行一道刻线
            col = x if x <= 2 else (x - 12 if x >= 13 else 1)
            lv = {0: 3, 1: 4, 2: 2}.get(col, 3)
            if y % 3 == 0 and 1 <= y <= 10 and (x <= 2 or x >= 13):
                lv -= 1
            c.set(x, y, GOLD_FOIL, lv)
    c.hline(0, 15, 0, GOLD_FOIL, 5)
    c.hline(1, 14, 1, GOLD_FOIL, 3)
    c.hline(0, 15, 15, GOLD_FOIL, 1)
    c.vline(15, 0, 15, GOLD_FOIL, 1)
    for x, y in c.disc_cells(7.5, 7.5, 2.0):
        c.set(x, y, VOID, 2)
    torus(c, 7.5, 7.5, 2.2, 1.0, GOLD_FOIL, 3)
    c.rect(7, 7, 8, 8, VIOLET, 4)
    c.set(7, 7, VIOLET, 5, glint=True)
    return c


@texture("block/self_evolution_lab_core", tile=False, refs=("block/beacon",))
def self_evolution_lab_core(rng):
    # 悬浮的核心（自发光）：侧面用第 5–10 列的第 3–8 行，顶面用 5–10；
    # 中间白热，往外是绿色的光，最外一圈转成紫色，切面上有两道亮线
    c = Canvas()
    for x, y in c.cells():
        d = max(abs(x - 7.5), abs(y - 6.5))
        if d < 1:
            c.set(x, y, WHITE_GLOW, 5)
        elif d < 1.8:
            c.set(x, y, GREEN_GLOW, 5)
        elif d < 2.6:
            c.set(x, y, GREEN_GLOW, 4)
        else:
            c.set(x, y, VIOLET_GLOW, 4 if d < 3.6 else 3)
    for x, y in ((5, 3), (10, 3), (5, 8), (10, 8), (5, 5), (10, 10)):
        c.set(x, y, VIOLET_GLOW, 5, glint=True)          # 角上的亮点：晶格的棱
    return c


# ---- 地基：混凝土的预制板，四角黄色的定位标记，中间压着防滑槽，拼起来是一格一格的地面 ----

@texture("block/foundation", rough=True, refs=("block/smooth_stone", "block/polished_andesite"))
def foundation(rng):
    c = Canvas()
    c.fill(CONCRETE, 4)
    c.clusters(rng, 1, 1, 14, 14, -1, 10, 3)
    c.clusters(rng, 1, 1, 14, 14, 1, 6, 2)
    # 板面中间一道浅的伸缩缝
    c.hline(4, 11, 8, CONCRETE, 3)
    c.hline(4, 11, 9, CONCRETE, 5)
    # 板缝：上、左受光，下、右暗，拼起来就是一道凹缝
    c.hline(0, 15, 0, CONCRETE, 5)
    c.vline(0, 0, 15, CONCRETE, 5)
    c.hline(0, 15, 15, CONCRETE, 2)
    c.vline(15, 0, 15, CONCRETE, 2)
    c.set(15, 0, CONCRETE, 4)
    c.set(0, 15, CONCRETE, 4)
    # 四角的 L 形黄色标记
    for cx, cy, sx, sy in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        for i in range(3):
            c.set(cx + sx * i, cy, HAZARD, 3 if i else 4, glint=True)
            c.set(cx, cy + sy * i, HAZARD, 3 if i else 4, glint=True)
    return c
