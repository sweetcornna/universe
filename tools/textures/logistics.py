"""物流设备的贴图（ADR-0031）：传送带、分拣器、分流器、仓库和储液罐、物流站、轨道采集器、物流无人机和运输船，
M11 串在传送带上的几台小设备，M12 的物流配送器和机器人。

传送带和分拣器按档位上色：Mk.I 琥珀、Mk.II 绿、Mk.III 蓝，集装分拣器紫。很多贴图贴在多元素模型的小块上，
只有模型取到的那几行（列）看得见，函数的注释里写了是哪几行。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from palette import (AMBER, BLUE, BLUE_GLOW, BRASS, BREAD, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GRAY, GREEN_GLOW, HAZARD,
                     LEAF, ORANGE_GLOW, RED_GLOW, RUBBER, SAND, SCREEN, STEEL, VIOLET, WHITE, YELLOW_GLOW)
from parts import TIER, bolts, casing
from recipe import texture

PILE = VIOLET  # 集装分拣器、自动集装机的颜色


def brushed(c: Canvas, rng: random.Random, x0=0, y0=0, x1=15, y1=15, count=8):
    """拉丝钢板的纹理：两三个像素的横向细纹，大多暗一级、少数亮一级。"""
    for _ in range(count):
        x, y = rng.randint(x0, max(x0, x1 - 2)), rng.randint(y0, y1)
        d = rng.choice((-1, -1, 1))
        for i in range(rng.randint(2, 3)):
            if x + i <= x1:
                c.shift(x + i, y, d)


def hazard_band(c: Canvas, x0, y0, x1, y1, period=4):
    """黄黑相间的警示条，斜纹从左下往右上；黄条上边受光。"""
    for x, y in c.cells(x0, y0, x1, y1):
        if (x + y) % period < period // 2:
            c.set(x, y, HAZARD, 4 if y == y0 else 3)
        else:
            c.set(x, y, GRAPHITE, 1)


# ---- 传送带 ----

BELT = {"": (TIER[1], 1, 8), "_mk2": (TIER[2], 2, 8), "_mk3": (TIER[3], 5, 16)}


def belt_tile(accent, period: int) -> Canvas:
    """一格带面（贴图上方是前进方向）：深色橡胶带，左边一道受光的压边、右边背光；中间一排人字形的挡条，
    每 period 像素一道，挡条顶面亮、朝后的一面暗、后面的带面上落一道影子。上下无缝，往上滚动就是动画。"""
    c = Canvas()
    c.fill(RUBBER, 2)
    rng = random.Random(40 + period)
    # 橡胶上的磨痕：两三个像素的细纹
    for _ in range(7):
        x, y = rng.randint(4, 10), rng.randint(0, 15)
        for i in range(rng.randint(2, 3)):
            c.set(x + i, y, RUBBER, 3)
    c.vline(0, 0, 15, RUBBER, 0)
    c.vline(1, 0, 15, RUBBER, 1)
    c.vline(2, 0, 15, RUBBER, 3)
    c.vline(13, 0, 15, RUBBER, 1)
    c.vline(14, 0, 15, RUBBER, 0)
    c.vline(15, 0, 15, RUBBER, 0)
    thick = 2 if period > 8 else 1
    for x in range(3, 13):
        tip = int(abs(x - 7.5) // 2)
        for y0 in range(0, 16, period):
            y = y0 + tip
            for k in range(thick):
                # 左半边朝着光，顶面亮一级
                c.set(x, (y + k) % 16, accent, (5 if x <= 7 else 4) - k)
            c.set(x, (y + thick) % 16, accent, 2)
            c.set(x, (y + thick + 1) % 16, RUBBER, 1)
    return c


def belt_surface(accent, step: int, period: int) -> Canvas:
    """动画：每帧把带面往上挪 step 个像素（Mk.I 每秒 20 像素，和物品一样快；Mk.II 两倍，Mk.III 五倍）。"""
    tile = belt_tile(accent, period)
    frames = 16 // math.gcd(16, step)
    c = Canvas(16, 16 * frames)
    for k in range(frames):
        for y in range(16):
            src = (y + k * step) % 16
            for x in range(16):
                c.p[k * 16 + y][x] = tile.p[src][x]
                if (x, src) in tile.glints:
                    c.glints.add((x, k * 16 + y))
    return c


def belt_curve(accent, period: int, right: bool = False) -> Canvas:
    """弯道：从西边进来、绕西北角拐向北的四分之一圆环（圆心在左上角）；挡条沿半径方向，尖朝前进的方向。
    圆环外面（东南角）是机架的钢甲板。右弯是左弯的镜像。"""
    c = Canvas()
    cleats = (22.5, 67.5) if period <= 8 else (45.0,)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5, y + 0.5)
            if d > 15.3:
                c.set(x, y, STEEL, 5 if d < 16.4 else 3)
                continue
            if d < 1.3:
                c.set(x, y, STEEL, 3)
                continue
            lv = 2
            if d < 2.3:
                lv = 1
            elif d < 3.2:
                lv = 3
            elif d > 13.6:
                lv = 1
            c.set(x, y, RUBBER, lv)
            if 3.0 <= d <= 13.6:
                ang = math.degrees(math.atan2(y + 0.5, x + 0.5))
                for a in cleats:
                    # 前进方向是角度变小的方向：挡条朝前的一面（角度小的一边）亮，后面一面暗，再后面落一道影子
                    off = (ang - a) * math.pi / 180 * d      # 沿弧长离挡条多远（像素）
                    if -0.75 <= off < 0.7:
                        c.set(x, y, accent, 5 if ang > 45 else 4)
                    elif 0.7 <= off < 1.6:
                        c.set(x, y, accent, 2)
                    elif 1.6 <= off < 2.5:
                        c.set(x, y, RUBBER, 1)
    # 甲板上一颗螺栓
    c.rivet(13, 13, STEEL, 3)
    return c.flip_h() if right else c


def belt_rail(accent) -> Canvas:
    """侧栏。模型只取三处：第 12–15 行是平放时侧栏的侧面，第 5–9 行是斜坡上的侧面（错开半个像素取），
    第 6 列是侧栏的顶。侧面：上沿受光的唇边、档位色的色带、一排螺栓、下沿的影子；第 6 列那里是一个卡箍。
    其余部分只在破坏粒子里看得到，画成同样的钢板。"""
    c = Canvas()
    c.fill(STEEL, 4)
    brushed(c, random.Random(120), 0, 0, 15, 4, 5)
    brushed(c, random.Random(121), 0, 10, 15, 11, 2)
    for lip, band, dark, shadow in ((12, (13,), 14, 15), (5, (6, 7), 8, 9)):
        c.hline(0, 15, lip, STEEL, 6)
        for y in band:
            c.hline(0, 15, y, accent, 4)
        c.hline(0, 15, dark, accent, 2)
        c.hline(0, 15, shadow, STEEL, 1)
        for x in range(2, 16, 4):
            if x != 6:
                c.set(x, band[0], STEEL, 6, glint=True)
                c.set(x, dark, accent, 1, glint=True)
        # 卡箍
        for y in band + (dark,):
            c.set(6, y, STEEL, 5)
        c.set(7, dark, STEEL, 3)
    # 侧栏的顶：亮的钢条，卡箍处稍暗
    for y in range(16):
        if y not in (5, 6, 7, 8, 9, 12, 13, 14, 15):
            c.set(6, y, STEEL, 6)
    c.set(6, 0, STEEL, 5)
    return c


@texture("block/conveyor_belt_frame")
def conveyor_belt_frame(rng):
    """底架。第 13 行是带子下面那层的侧面（橡胶边），第 14–15 行是机架侧面的一排滚筒；斜坡错开半格取第 6–9 行，
    同样画一遍。整面是机架的底面：两根纵梁、几道横撑。"""
    c = Canvas()
    c.fill(STEEL, 2)
    brushed(c, rng, 0, 0, 15, 12, 6)
    for x in (3, 12):
        c.vline(x, 0, 15, STEEL, 4)
        c.vline(x + 1, 0, 15, STEEL, 1)
    for y in (2, 10):
        c.hline(0, 15, y, STEEL, 3)
        c.hline(0, 15, y + 1, STEEL, 1)
    for rub, lip, r0 in ((13, None, 14), (6, 7, 8)):
        c.hline(0, 15, rub, RUBBER, 2)
        if lip is not None:
            c.hline(0, 15, lip, STEEL, 4)
        for x in range(16):
            k = x % 4
            if k in (1, 2):
                c.set(x, r0, STEEL, 6 if k == 1 else 5, glint=True)
                c.set(x, r0 + 1, STEEL, 4 if k == 1 else 2, glint=True)
            else:
                c.set(x, r0, STEEL, 1)
                c.set(x, r0 + 1, STEEL, 1 if k == 3 else 2)
    return c


for _suffix, (_accent, _step, _period) in BELT.items():
    texture(f"block/conveyor_belt{_suffix}_surface", frames=16 // math.gcd(16, _step), tile=False)(
        lambda rng, a=_accent, s=_step, p=_period: belt_surface(a, s, p))
    texture(f"block/conveyor_belt{_suffix}_curve_left", tile=False)(
        lambda rng, a=_accent, p=_period: belt_curve(a, p))
    texture(f"block/conveyor_belt{_suffix}_curve_right", tile=False)(
        lambda rng, a=_accent, p=_period: belt_curve(a, p, right=True))
    texture(f"block/conveyor_belt{_suffix}_rail")(lambda rng, a=_accent: belt_rail(a))


# ---- 分拣器 ----

def sorter_top(accent) -> Canvas:
    """底座顶面（模型取第 2–13 行、列；中间 5–10 被立柱挡住）：钢板，一圈档位色的边，前后各一个箭头，
    前面（贴图上方，出料）亮、后面暗。"""
    c = casing(random.Random(130), base=4)
    c.frame(2, 2, 13, 13, accent, 3)
    c.hline(2, 12, 2, accent, 4)
    c.vline(2, 3, 12, accent, 4)
    c.hline(3, 13, 13, accent, 2)
    c.vline(13, 3, 12, accent, 2)
    c.hline(3, 12, 3, STEEL, 3)
    c.vline(3, 4, 12, STEEL, 3)
    # 出料的箭头亮，进料的暗
    c.draw(["..55..", ".5445."], {"0123456": accent}, 5, 3)
    c.draw(["..33..", ".3223."], {"0123456": accent}, 5, 11)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        c.set(x, y, STEEL, 6, glint=True)
    return c


def sorter_arm(accent) -> Canvas:
    """手臂（模型的小块各取一点）：第 3–6 行是手臂侧面（上沿亮、下沿暗），第 7–9 行是钢的夹爪，
    第 0–2、10–12 行是手臂和转轴的顶面，第 13–15 行是底面。整体刷档位色，一眼认得出是哪一档。"""
    c = Canvas()
    c.fill(accent, 4)
    for x in range(16):
        c.set(x, 3, accent, 5)
        c.set(x, 4, accent, 4)
        c.set(x, 5, accent, 3)
        c.set(x, 6, accent, 2)
        c.set(x, 7, STEEL, 6)
        c.set(x, 8, STEEL, 4)
        c.set(x, 9, STEEL, 2)
        for y in (13, 14, 15):
            c.set(x, y, accent, 2)
    # 顶面中间一道受光的棱，转轴顶上一颗螺栓
    c.vline(7, 0, 2, accent, 5)
    c.vline(9, 0, 2, accent, 3)
    c.vline(7, 10, 12, accent, 5)
    c.vline(9, 10, 12, accent, 3)
    c.set(8, 11, STEEL, 6, glint=True)
    c.set(8, 12, STEEL, 3, glint=True)
    # 侧面每隔一段一道接缝
    for x in (2, 10):
        c.set(x, 4, accent, 2)
        c.set(x, 5, accent, 2)
    return c


for _suffix, _accent in (("", TIER[1]), ("_mk2", TIER[2]), ("_mk3", TIER[3]), ("_pile", PILE)):
    texture(f"block/sorter{_suffix}_top")(lambda rng, a=_accent: sorter_top(a))
    texture(f"block/sorter_arm{_suffix}")(lambda rng, a=_accent: sorter_arm(a))


@texture("block/sorter_side")
def sorter_side(rng):
    """底座侧面（模型取第 12–15 行）：上沿受光，下面两行黄黑警示条，最下一行影子。整张也是破坏粒子。"""
    c = casing(rng, base=4)
    c.hline(0, 15, 11, STEEL, 2)
    c.hline(0, 15, 12, STEEL, 6)
    hazard_band(c, 0, 13, 15, 14)
    c.hline(0, 15, 15, STEEL, 1)
    return c


@texture("block/sorter_pillar")
def sorter_pillar(rng):
    """立柱（模型取第 6–11 行、第 5–10 列）：方钢柱，左边受光、右边背光，中间一盏青色的状态灯。"""
    c = Canvas()
    c.fill(STEEL, 4)
    brushed(c, rng, 0, 0, 15, 15, 6)
    c.vline(5, 0, 15, STEEL, 6)
    c.vline(6, 0, 15, STEEL, 5)
    c.vline(10, 0, 15, STEEL, 2)
    c.vline(9, 0, 15, STEEL, 3)
    c.hline(5, 10, 6, STEEL, 5)
    c.hline(5, 10, 11, STEEL, 2)
    c.rect(7, 8, 8, 9, GRAPHITE, 1)
    c.set(7, 8, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 4, glint=True)
    c.set(7, 9, CYAN_GLOW, 4, glint=True)
    c.set(8, 9, CYAN_GLOW, 3, glint=True)
    return c


# ---- 分流器 ----

@texture("block/splitter_top")
def splitter_top(rng):
    """底座顶面（模型取第 1–14 行、列；中间 4–11 被转盘盖住）：四边各一个接传送带的口，口沿是琥珀色。"""
    c = casing(rng, base=4)
    acc = TIER[1]
    for i in range(5, 11):
        c.set(i, 1, RUBBER, 1)
        c.set(i, 14, RUBBER, 1)
        c.set(1, i, RUBBER, 1)
        c.set(14, i, RUBBER, 1)
        c.set(i, 2, acc, 4)
        c.set(2, i, acc, 4)
        c.set(i, 13, acc, 2)
        c.set(13, i, acc, 2)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, STEEL, 4)
    return c


@texture("block/splitter_side")
def splitter_side(rng):
    """底座侧面（模型取第 10–15 行）：上沿受光，中间一个开口，口里看得到穿过去的传送带；两边钢板上各一颗螺栓。"""
    c = casing(rng, base=4)
    c.hline(1, 14, 9, STEEL, 2)
    c.hline(1, 14, 10, STEEL, 6)
    c.recess(4, 11, 11, 15, RUBBER, 2)
    c.hline(5, 11, 12, RUBBER, 0)
    c.hline(5, 11, 13, RUBBER, 2)
    for x in range(5, 12, 3):
        c.set(x, 13, TIER[1], 4)
    c.hline(5, 11, 14, STEEL, 2)
    c.hline(5, 11, 15, STEEL, 1)
    c.rivet(2, 12, STEEL, 4)
    c.rivet(12, 12, STEEL, 4)
    c.hline(0, 3, 15, STEEL, 2)
    c.hline(12, 15, 15, STEEL, 2)
    return c


@texture("block/splitter_hub")
def splitter_hub(rng):
    """转盘：钢的外圈（按法线打光），深色的盘面，四道琥珀色的分隔筋，中间青色的指示灯。侧面取第 0–1 行。"""
    c = Canvas()
    c.fill(STEEL, 2)
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 7.4):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r > 6.0:
            v = -(dx * 0.7 + dy * 0.7) / max(r, 1e-6)
            c.set(x, y, STEEL, 4 + round(v * 1.6))
        else:
            c.set(x, y, GRAPHITE, 2 if r > 2.5 else 3)
    c.ring(cx, cy, 2.2, STEEL, 4)
    for x, y in c.disc_cells(cx, cy, 5.8):
        dx, dy = x - cx, y - cy
        if (abs(dx - dy) < 1.1 or abs(dx + dy) < 1.1) and math.hypot(dx, dy) > 2.3:
            # 筋是凸起的：朝左上的一边亮
            c.set(x, y, TIER[1], 4 if (dx + dy) < 0 or abs(dx - dy) < 0.1 else 3)
    c.rect(7, 7, 8, 8, CYAN_GLOW, 4)
    c.set(7, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 3, glint=True)
    return c


@texture("block/splitter_priority")
def splitter_priority(rng):
    """优先出口的标记，贴在自发光的小块上：亮黄的箭头朝北。顶面取第 1–3 行、第 5–10 列，侧面取第 9 行。"""
    c = Canvas()
    c.fill(YELLOW_GLOW, 3)
    for y0 in (1, 12):
        for x in range(5, 11):
            y = y0 + min(2, int(abs(x - 7.5)))
            c.set(x, y, YELLOW_GLOW, 5)
            if y + 1 <= y0 + 2:
                c.set(x, y + 1, YELLOW_GLOW, 4)
    c.hline(0, 15, 9, YELLOW_GLOW, 4)
    c.hline(7, 8, 9, YELLOW_GLOW, 5)
    return c


# ---- 仓库、储液罐 ----

@texture("block/storage_depot_side", refs=("block/barrel_side",))
def storage_depot_side(rng):
    """仓库侧面：加厚的钢框，框里竖着三道压筋，腰上一道琥珀色的 Mk.I 色带，四角螺栓。"""
    c = casing(rng, base=4)
    for x in (5, 8, 11):
        c.vline(x - 1, 3, 12, STEEL, 6)
        c.vline(x, 3, 12, STEEL, 3)
        c.set(x - 1, 3, STEEL, 5)
        c.set(x, 12, STEEL, 2)
    c.hline(2, 13, 7, AMBER, 4)
    c.hline(2, 13, 8, AMBER, 2)
    for x in (4, 7, 10):
        c.set(x, 7, AMBER, 5)
        c.set(x + 1, 8, AMBER, 1)
    bolts(c, 1)
    return c


@texture("block/storage_depot_top", refs=("block/barrel_top",))
def storage_depot_top(rng):
    """仓库顶盖：凹进去的舱口里一片格栅，一角青色的指示灯，对角一道琥珀色的标记。"""
    c = casing(rng, base=4)
    c.recess(3, 3, 12, 12, STEEL, 4)
    for y in range(4, 12):
        for x in range(4, 12):
            if y % 2 == 0:
                c.set(x, y, STEEL, 5 if x < 11 else 4)
            else:
                c.set(x, y, GRAPHITE, 1)
    c.vline(7, 4, 11, STEEL, 3)
    c.vline(8, 4, 11, STEEL, 2)
    c.set(13, 2, CYAN_GLOW, 5, glint=True)
    c.set(2, 13, AMBER, 4, glint=True)
    c.set(2, 12, AMBER, 3, glint=True)
    c.set(3, 13, AMBER, 3, glint=True)
    return c


@texture("block/storage_tank_side", refs=("block/glass",))
def storage_tank_side(rng):
    """储液罐侧面：四角立柱、上下钢箍，中间是玻璃（全透明，几道斜的反光；只用全透明和不透明的像素，模型才走镂空）。
    右边立柱内侧有几道刻度。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if x < 2 or x > 13 or y < 2 or y > 13:
                c.set(x, y, STEEL, 4)
    c.vline(0, 0, 15, STEEL, 5)
    c.vline(1, 2, 13, STEEL, 4)
    c.vline(14, 2, 13, STEEL, 3)
    c.vline(15, 0, 15, STEEL, 2)
    c.hline(0, 15, 0, STEEL, 6)
    c.hline(1, 14, 1, STEEL, 4)
    c.hline(1, 14, 14, STEEL, 3)
    c.hline(0, 15, 15, STEEL, 1)
    c.set(0, 0, STEEL, 6)
    c.set(15, 0, STEEL, 4)
    c.set(0, 15, STEEL, 3)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, STEEL, 4)
    for y in range(3, 13, 3):
        c.set(13, y, STEEL, 2)
    # 玻璃上的反光：左上角一短一长两道（背面的玻璃从里面看是镜像，反光落在右上角）
    for x, y, lv in ((3, 6, 4), (4, 5, 5), (5, 4, 5), (6, 3, 4), (3, 9, 3), (4, 8, 4)):
        c.set(x, y, GLASS, lv, glint=True)
    return c


@texture("block/storage_tank_top")
def storage_tank_top(rng):
    """顶盖（底面也用它）：圆形的检修口，口沿一圈螺栓，一角青色的指示灯。"""
    c = casing(rng, base=4)
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 5.2):
        r = math.hypot(x - cx, y - cy)
        if r > 4.2:
            c.set(x, y, STEEL, 2 if (x - cx) + (y - cy) < 0 else 5)
        else:
            c.set(x, y, STEEL, 4)
    c.shade_disc(cx, cy, 3.2, STEEL, 4, amp=1.4)
    c.rect(6, 7, 9, 8, STEEL, 2)
    c.hline(6, 9, 7, STEEL, 6)
    for a in range(8):
        t = a * math.pi / 4 + math.pi / 8
        x, y = int(round(cx + math.cos(t) * 6.3)), int(round(cy + math.sin(t) * 6.3))
        c.set(x, y, STEEL, 6, glint=True)
    c.set(13, 2, CYAN_GLOW, 5, glint=True)
    return c


@texture("entity/storage_tank_fluid", tinted=True)
def storage_tank_fluid(rng):
    """液柱：灰度，渲染时按流体的颜色乘上去、半透明。斜的水波纹，几串上升的气泡；上下无缝（罐子叠起来连成一根）。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            v = math.sin((x * 0.9 + y * 0.45) * 2 * math.pi / 7.2)
            c.set(x, y, GRAY, 5 + (1 if v > 0.55 else (-1 if v < -0.6 else 0)))
    for x, y in ((3, 12), (4, 8), (3, 4), (10, 14), (11, 9), (10, 3), (7, 6), (6, 1)):
        c.set(x, y, GRAY, 6, glint=True)
        c.set(x + 1, (y + 1) % 16, GRAY, 3, glint=True)
    return c


# ---- 物流站 ----

def station_lamp(on: bool) -> Canvas:
    """指示灯的材质：模型在很多地方各取一小块（塔顶四角、无人机和机器人的灯带、运输船的航灯），所以整张都是灯面，
    不画成一个圆。每 4×4 一块透镜，左上角一点高光、右下角暗一点；亮的贴在自发光的小块上。"""
    c = Canvas()
    base, hi, lo = (4, 5, 3) if on else (1, 2, 0)
    c.fill(CYAN_GLOW, base)
    for y in range(16):
        for x in range(16):
            k = (x % 4, y % 4)
            if k == (1, 1) or (on and k == (0, 1)):
                c.set(x, y, CYAN_GLOW, hi, glint=True)
            elif k == (3, 3):
                c.set(x, y, CYAN_GLOW, lo, glint=True)
    return c


texture("block/logistics_station_light")(lambda rng: station_lamp(False))
texture("block/logistics_station_light_on")(lambda rng: station_lamp(True))


@texture("block/logistics_station_pad_side")
def logistics_station_pad_side(rng):
    """停机坪的边沿（模型取第 4–6 行）：上沿受光的唇边，下面两行黄黑警示条。"""
    c = casing(rng, base=4)
    c.hline(0, 15, 3, STEEL, 2)
    c.hline(0, 15, 4, STEEL, 6)
    hazard_band(c, 0, 5, 15, 6)
    c.hline(0, 15, 7, STEEL, 1)
    return c


@texture("block/logistics_station_trim")
def logistics_station_trim(rng):
    """立柱和细杆的钢料：四角的立柱取第 0–1、14–15 列（左边受光、右边背光，每 4 行一道箍），
    无人机的机臂、采集器的撑杆取中间第 7–8 行、第 7–8 列，画成一根亮面朝上的细杆。"""
    c = Canvas()
    c.fill(STEEL, 4)
    brushed(c, rng, 2, 0, 13, 15, 3)
    for y in range(16):
        c.set(0, y, STEEL, 6)
        c.set(1, y, STEEL, 4)
        c.set(14, y, STEEL, 3)
        c.set(15, y, STEEL, 2)
        if y % 4 == 3:
            c.hline(0, 1, y, STEEL, 2)
            c.hline(14, 15, y, STEEL, 1)
        elif y % 4 == 0:
            c.hline(0, 1, y, STEEL, 6)
            c.hline(14, 15, y, STEEL, 4)
    c.hline(2, 13, 7, STEEL, 6)
    c.hline(2, 13, 8, STEEL, 3)
    c.vline(7, 1, 14, STEEL, 6)
    c.vline(8, 1, 14, STEEL, 3)
    c.set(7, 7, STEEL, 6)
    c.set(8, 8, STEEL, 2)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.set(x, y, STEEL, 6, glint=True)
        c.set(x + 1, y + 1, STEEL, 2, glint=True)
    return c


def station_side(skin, accent, trim, ils: bool, rng) -> Canvas:
    """塔身侧面。行星站取第 2–13 列：下半截第 0–12 行、上半截第 7–15 行（两段接起来是一根塔身）；
    星际站取第 1–14 列，四面中间第 7–8 列被一道竖鳍挡住。
    面板受光的左边亮、右边暗，上下两道装饰线，竖着的灯带（行星站一道在中间，星际站两道分在鳍的两边）。"""
    c = Canvas()
    lo, hi = (2, 13) if not ils else (1, 14)
    c.fill(skin, 4)
    brushed(c, rng, lo, 0, hi, 15, 7)
    c.vline(lo, 0, 15, skin, 5)
    c.vline(lo + 1, 0, 15, skin, 5)
    c.vline(hi, 0, 15, skin, 2)
    c.vline(hi - 1, 0, 15, skin, 3)
    for y in (3, 11):
        c.hline(lo, hi, y, trim, 3)
        c.hline(lo, hi, y + 1, trim, 1)
        c.set(lo, y, trim, 4)
    strips = (6,) if not ils else (3, 9)
    for x0 in strips:
        c.vline(x0, 0, 15, skin, 2)
        c.vline(x0 + 1, 0, 15, accent, 4)
        c.vline(x0 + 2, 0, 15, accent, 3)
        c.vline(x0 + 3, 0, 15, skin, 2)
        for y in (3, 4, 11, 12):
            c.set(x0 + 1, y, accent, 5)
            c.set(x0 + 2, y, accent, 4)
    # 塔身上的检修口
    for y in (7, 14):
        c.set(lo + 2, y, skin, 6, glint=True)
        c.set(hi - 2, y, skin, 2, glint=True)
    return c


def station_front(skin, accent, trim, ils: bool, rng) -> Canvas:
    """正面（下半截，模型取第 0–12 行）：一扇装卸舱门，门框上沿是黄黑警示条，门是一层层的百叶，
    门上方一排灯，门槛一道蓝色。"""
    c = station_side(skin, accent, trim, ils, rng)
    lo, hi = (2, 13) if not ils else (1, 14)
    c.rect(lo + 2, 0, hi - 2, 12, skin, 4)
    brushed(c, rng, lo + 2, 0, hi - 2, 12, 5)
    c.hline(lo, hi, 1, trim, 3)
    c.hline(lo, hi, 2, trim, 1)
    x0, x1 = lo + 2, hi - 2
    c.recess(x0, 4, x1, 12, GRAPHITE, 3)
    hazard_band(c, x0, 4, x1, 4, period=4)
    for y in range(5, 12):
        c.hline(x0 + 1, x1 - 1, y, STEEL, 5 if y % 2 == 1 else 3)
        c.set(x1 - 1, y, STEEL, 2)
    c.hline(x0, x1, 12, accent, 3)
    c.set(x0, 12, accent, 4)
    for x in range(x0 + 1, x1, 2):
        c.set(x, 3, CYAN_GLOW, 4, glint=True)
    return c


def station_pad(deck, ring, rng) -> Canvas:
    """停机坪顶面：深色的甲板，一圈降落环，十字标记的四条短线，中心一个亮点；四角螺栓。"""
    c = Canvas()
    c.fill(deck, 3)
    brushed(c, rng, 1, 1, 14, 14, 10)
    c.hline(0, 15, 0, deck, 5)
    c.vline(0, 1, 15, deck, 5)
    c.hline(1, 15, 15, deck, 1)
    c.vline(15, 1, 14, deck, 1)
    c.hline(1, 14, 1, deck, 4)
    for x, y in c.cells(1, 1, 14, 14):
        d = math.hypot(x - 7.5, y - 7.5)
        if 5.0 <= d < 6.3:
            c.set(x, y, ring, 4 if (x - 7.5) + (y - 7.5) < 0 else 3)
        elif 6.3 <= d < 6.9:
            c.set(x, y, deck, 2)
    for i in range(3, 13):
        if abs(i - 7.5) > 2.2:
            c.set(i, 7, ring, 3)
            c.set(i, 8, ring, 2)
            c.set(7, i, ring, 3)
            c.set(8, i, ring, 2)
    c.rect(7, 7, 8, 8, ring, 5)
    c.set(8, 8, ring, 3)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, deck, 3)
    return c


@texture("block/planetary_logistics_station_side")
def planetary_logistics_station_side(rng):
    return station_side(WHITE, BLUE, BLUE, False, rng)


@texture("block/planetary_logistics_station_front")
def planetary_logistics_station_front(rng):
    return station_front(WHITE, BLUE, BLUE, False, rng)


@texture("block/planetary_logistics_station_pad")
def planetary_logistics_station_pad(rng):
    return station_pad(GRAPHITE, HAZARD, rng)


@texture("block/interstellar_logistics_station_side")
def interstellar_logistics_station_side(rng):
    return station_side(STEEL, BLUE, BRASS, True, rng)


@texture("block/interstellar_logistics_station_front")
def interstellar_logistics_station_front(rng):
    return station_front(STEEL, BLUE, BRASS, True, rng)


@texture("block/interstellar_logistics_station_pad")
def interstellar_logistics_station_pad(rng):
    return station_pad(GRAPHITE, GOLD_FOIL, rng)


@texture("block/interstellar_logistics_station_trim")
def interstellar_logistics_station_trim(rng):
    """星际站的金色构件：竖鳍取第 7–8 列和两边各一列，停机坪的立柱取第 0–1、14–15 列，第 7 行是塔顶的一圈腰线；
    运输船的翼板也用它。每 4 行一道接缝，接缝下面受光。"""
    c = Canvas()
    c.fill(BRASS, 3)
    for y in range(16):
        c.set(0, y, BRASS, 5)
        c.set(1, y, BRASS, 4)
        c.set(7, y, BRASS, 5)
        c.set(8, y, BRASS, 2)
        c.set(14, y, BRASS, 3)
        c.set(15, y, BRASS, 1)
    for y in (3, 11, 15):
        c.hline(0, 15, y, BRASS, 1)
        if y < 15:
            c.hline(0, 15, y + 1, BRASS, 4)
    c.hline(0, 15, 6, BRASS, 5)
    c.hline(0, 15, 7, BRASS, 4)
    c.hline(0, 15, 8, BRASS, 1)
    for x in (3, 11):
        c.set(x, 7, BRASS, 6, glint=True)
    return c


# ---- 轨道采集器 ----

@texture("block/orbital_collector_core")
def orbital_collector_core(rng):
    """罐体（模型取中间第 3–12 行、列，十字形的圆罐）：透明的罐壁里是一层层的气体，色带边缘带一点涡旋，
    上面亮、下面暗，左上一道玻璃的反光。"""
    c = Canvas()
    bands = ((SAND, 5), (BREAD, 4), (SAND, 4), (BREAD, 3), (SAND, 6), (BREAD, 4))
    for y in range(16):
        for x in range(16):
            wobble = 1 if math.sin(x * 0.9 + y * 0.6) > 0.55 else 0
            r, lv = bands[((y + wobble) // 3) % len(bands)]
            if y < 5:
                lv += 1
            elif y > 11:
                lv -= 1
            c.set(x, y, r, lv)
    c.set(9, 8, BREAD, 2, glint=True)
    c.set(10, 8, BREAD, 3, glint=True)
    c.set(5, 6, GLASS, 5, glint=True)
    c.set(4, 7, GLASS, 4, glint=True)
    c.set(6, 5, GLASS, 4, glint=True)
    c.vline(0, 0, 15, STEEL, 4)
    c.vline(15, 0, 15, STEEL, 2)
    return c


@texture("block/orbital_collector_ring")
def orbital_collector_ring(rng):
    """导环：一圈方钢。侧面取第 7–8 行（每 4 像素一盏青灯），顶面取第 0–1、14–15 行和第 0–1、14–15 列。"""
    c = Canvas()
    c.fill(STEEL, 4)
    brushed(c, rng, 2, 2, 13, 13, 6)
    for i in range(16):
        c.set(i, 0, STEEL, 6)
        c.set(i, 1, STEEL, 5)
        c.set(i, 14, STEEL, 3)
        c.set(i, 15, STEEL, 2)
    for i in range(2, 14):
        c.set(0, i, STEEL, 6)
        c.set(1, i, STEEL, 5)
        c.set(14, i, STEEL, 3)
        c.set(15, i, STEEL, 2)
    c.hline(0, 15, 6, STEEL, 2)
    c.hline(0, 15, 7, STEEL, 5)
    c.hline(0, 15, 8, STEEL, 3)
    c.hline(0, 15, 9, STEEL, 2)
    for x in range(1, 16, 4):
        c.set(x, 7, CYAN_GLOW, 5, glint=True)
        c.set(x, 8, CYAN_GLOW, 3, glint=True)
    return c


@texture("block/orbital_collector_intake")
def orbital_collector_intake(rng):
    """进气口：底面（第 6–9 行、列）是方孔格栅，侧面（第 13–15 行）是一圈竖缝。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    for x, y in c.cells(0, 0, 15, 11):
        if x % 3 == 0 or y % 3 == 0:
            c.set(x, y, STEEL, 4 if (x % 3 == 0 and y % 3 != 0) else 5)
        else:
            c.set(x, y, GRAPHITE, 1 if (x % 3, y % 3) == (1, 1) else 2)
    c.hline(0, 15, 12, STEEL, 6)
    for x in range(16):
        for y in (13, 14):
            c.set(x, y, GRAPHITE, 1 if x % 2 == 0 else 3)
        c.set(x, 15, STEEL, 2)
    return c


# ---- 物流无人机、运输船 ----

@texture("block/logistics_drone_body")
def logistics_drone_body(rng):
    """无人机机身（模型取第 5–10 行、列）：白色机壳，腰上一道蓝色涂装，顶面左上亮。"""
    c = Canvas()
    c.fill(WHITE, 5)
    c.hline(5, 10, 5, WHITE, 6)
    c.vline(5, 5, 10, WHITE, 6)
    c.hline(6, 10, 10, WHITE, 3)
    c.vline(10, 6, 10, WHITE, 3)
    c.hline(0, 15, 7, BLUE, 4)
    c.hline(0, 15, 8, BLUE, 3)
    c.set(5, 7, BLUE, 5)
    c.hline(0, 15, 9, WHITE, 3)
    return c


@texture("block/logistics_drone_rotor")
def logistics_drone_rotor(rng):
    """旋翼（贴在一片薄板上，透明的地方不画）：两片桨叶，桨尖一圈护环，中间轴帽。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            blade = math.cos(2 * (a - 0.35))
            if d < 1.8:
                c.set(x, y, STEEL, 5 if dx + dy < 0 else 3)
            elif 6.9 <= d < 7.8:
                c.set(x, y, STEEL, 5 if dx + dy < 0 else 3)
            elif d < 6.9 and blade > 0.55:
                # 桨叶：前缘亮、后缘暗，桨尖亮一点
                lv = 4 if math.sin(2 * (a - 0.35)) > 0 else 2
                c.set(x, y, GRAPHITE, lv + (1 if d > 5 else 0))
    return c


@texture("block/logistics_vessel_hull")
def logistics_vessel_hull(rng):
    """运输船船体：深灰的船壳，两道金色的涂装（第 4、11 行，顶面看是横过船身的两道），
    侧面（第 6–9 行）一排舷窗，顶面中间一道受光的脊。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    brushed(c, rng, 0, 0, 15, 15, 8)
    c.hline(0, 15, 0, GRAPHITE, 4)
    c.hline(0, 15, 15, GRAPHITE, 1)
    c.vline(7, 0, 15, GRAPHITE, 5)
    c.vline(8, 0, 15, GRAPHITE, 4)
    c.vline(9, 0, 15, GRAPHITE, 2)
    for y in (4, 11):
        c.hline(0, 15, y, GOLD_FOIL, 3)
        c.hline(0, 15, y + 1, GOLD_FOIL, 1)
    c.hline(0, 15, 6, GRAPHITE, 5)
    c.hline(0, 15, 9, GRAPHITE, 1)
    for x in range(1, 16, 3):
        c.set(x, 7, GLASS, 5, glint=True)
        c.set(x, 8, GLASS, 3, glint=True)
    return c


@texture("block/logistics_vessel_engine")
def logistics_vessel_engine(rng):
    """尾喷口：深色的喷管里是蓝白色的光（模型在中间偏两边各取 2×2 的一小块）。"""
    c = Canvas()
    c.fill(GRAPHITE, 2)
    for x, y in c.disc_cells(7.5, 7.5, 7.0):
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 2.5:
            c.set(x, y, BLUE_GLOW, 5)
        elif d < 4.6:
            c.set(x, y, BLUE_GLOW, 4)
        elif d < 6.0:
            c.set(x, y, BLUE_GLOW, 3)
        else:
            c.set(x, y, GRAPHITE, 4 if (x - 7.5) + (y - 7.5) < 0 else 1)
    return c


# ---- M11：串在传送带上的设备 ----

@texture("block/belt_pass_top")
def belt_pass_top(rng):
    """底座顶面：中间穿过一段传送带（前进方向朝上，Mk.I 的人字挡条），两边是钢的台面，台面内侧一道唇边。"""
    c = casing(rng, base=4)
    tile = belt_tile(TIER[1], 8)
    for y in range(16):
        for x in range(3, 13):
            c.p[y][x] = tile.p[y][x + 1] if x < 12 else tile.p[y][13]
    c.vline(0, 0, 15, STEEL, 5)
    c.vline(1, 0, 15, STEEL, 4)
    c.vline(2, 0, 15, STEEL, 6)
    c.vline(13, 0, 15, STEEL, 2)
    c.vline(14, 0, 15, STEEL, 3)
    c.vline(15, 0, 15, STEEL, 2)
    for y in (2, 10):
        c.rivet(0, y, STEEL, 4)
        c.rivet(14, y, STEEL, 3)
    return c


def belt_pass_wall(rng, port: bool) -> Canvas:
    """底座的侧面（模型取第 7–15 行）：上沿受光，钢板上两颗螺栓，下面两行警示条。
    前后两面（port）中间开口，看得到穿过去的传送带和里面的暗处。"""
    c = casing(rng, base=4)
    c.hline(0, 15, 6, STEEL, 2)
    c.hline(0, 15, 7, STEEL, 6)
    c.hline(0, 15, 12, STEEL, 2)
    hazard_band(c, 0, 13, 15, 14)
    c.hline(0, 15, 15, STEEL, 1)
    c.rivet(1, 9, STEEL, 4)
    c.rivet(13, 9, STEEL, 4)
    if port:
        c.rect(3, 8, 12, 15, GRAPHITE, 0)
        c.hline(3, 12, 8, RUBBER, 3)
        for x in range(3, 13):
            if x % 3 == 1:
                c.set(x, 9, TIER[1], 3)
            else:
                c.set(x, 9, RUBBER, 1)
        c.vline(3, 10, 15, GRAPHITE, 1)
        c.vline(12, 10, 15, STEEL, 3)
        c.hline(4, 11, 10, GRAPHITE, 1)
    return c


texture("block/belt_pass_side")(lambda rng: belt_pass_wall(rng, False))
texture("block/belt_pass_port")(lambda rng: belt_pass_wall(rng, True))


@texture("block/spray_coater_side")
def spray_coater_side(rng):
    """喷涂机的白色罩子（模型取第 5–10 行）：上沿受光，中间一扇观察窗，窗里是增产剂喷出的绿雾。"""
    c = Canvas()
    c.fill(WHITE, 4)
    brushed(c, rng, 0, 0, 15, 15, 6)
    c.vline(2, 0, 15, WHITE, 5)
    c.vline(13, 0, 15, WHITE, 2)
    c.hline(0, 15, 4, WHITE, 2)
    c.hline(0, 15, 5, WHITE, 6)
    c.hline(0, 15, 10, WHITE, 2)
    c.hline(0, 15, 11, WHITE, 1)
    c.recess(4, 6, 11, 9, LEAF, 1)
    for x, y, lv in ((6, 8, 4), (7, 7, 5), (8, 8, 4), (9, 7, 3), (7, 8, 3), (10, 8, 2), (5, 8, 2)):
        c.set(x, y, LEAF, lv, glint=True)
    c.set(5, 7, GLASS, 4, glint=True)
    c.hline(3, 12, 3, LEAF, 4)
    c.hline(3, 12, 12, LEAF, 4)
    return c


@texture("block/spray_coater_top")
def spray_coater_top(rng):
    """罩子顶面（模型取第 3–12 行、第 2–13 列）：一个圆的玻璃罐盖，里面是绿色的增产剂，中间是加注口。"""
    c = Canvas()
    c.fill(WHITE, 4)
    brushed(c, rng, 0, 0, 15, 15, 5)
    c.hline(2, 13, 3, WHITE, 6)
    c.vline(2, 3, 12, WHITE, 5)
    c.hline(2, 13, 12, WHITE, 2)
    c.vline(13, 3, 12, WHITE, 2)
    cx, cy = 7.5, 7.5
    c.ring(cx, cy, 4.3, WHITE, 2)
    for x, y in c.disc_cells(cx, cy, 3.3):
        d = math.hypot(x - cx, y - cy)
        c.set(x, y, LEAF, 4 if (x - cx) + (y - cy) < -1 else (3 if d < 2.5 else 2))
    c.rect(7, 7, 8, 8, WHITE, 3)
    c.set(7, 7, WHITE, 5)
    c.set(5, 5, GLASS, 5, glint=True)
    c.set(6, 5, GLASS, 4, glint=True)
    return c


@texture("block/automatic_piler_side")
def automatic_piler_side(rng):
    """集装机的压头外壳（模型取第 5–10 行、第 1–14 列）：深色机壳，上沿受光，一道紫色涂装，下面一排散热缝。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    brushed(c, rng, 0, 0, 15, 15, 6)
    c.vline(1, 0, 15, GRAPHITE, 5)
    c.vline(14, 0, 15, GRAPHITE, 1)
    c.hline(0, 15, 4, GRAPHITE, 1)
    c.hline(0, 15, 5, GRAPHITE, 5)
    c.hline(1, 14, 6, PILE, 4)
    c.hline(1, 14, 7, PILE, 2)
    for x in range(3, 13, 2):
        c.set(x, 8, GRAPHITE, 0)
        c.set(x, 9, GRAPHITE, 4)
    c.hline(0, 15, 10, GRAPHITE, 1)
    c.set(1, 6, PILE, 5)
    return c


@texture("block/automatic_piler_top")
def automatic_piler_top(rng):
    """压头顶面（模型取第 1–14 行、列）：紫色的框，中间凸起的压板，压板上一盏青灯。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.hline(1, 14, 1, GRAPHITE, 5)
    c.vline(1, 1, 14, GRAPHITE, 5)
    c.hline(1, 14, 14, GRAPHITE, 1)
    c.vline(14, 2, 14, GRAPHITE, 1)
    c.frame(3, 3, 12, 12, PILE, 3)
    c.hline(3, 11, 3, PILE, 4)
    c.vline(3, 4, 11, PILE, 4)
    c.panel(5, 5, 10, 10, STEEL, 4)
    c.vline(7, 6, 9, STEEL, 3)
    c.set(8, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 3, glint=True)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.set(x, y, STEEL, 5, glint=True)
    return c


@texture("block/traffic_monitor_frame")
def traffic_monitor_frame(rng):
    """监测器的钢架：两边的立柱取第 0–1、14–15 列（左亮右暗），立柱的侧面取第 7–8 列，横梁取第 0–1 行，
    柱顶和屏幕的底边取第 7–8 行。"""
    c = Canvas()
    c.fill(STEEL, 4)
    brushed(c, rng, 2, 2, 13, 13, 6)
    c.hline(0, 15, 0, STEEL, 6)
    c.hline(0, 15, 1, STEEL, 4)
    for y in range(16):
        c.set(0, y, STEEL, 6)
        c.set(1, y, STEEL, 4)
        c.set(14, y, STEEL, 3)
        c.set(15, y, STEEL, 2)
        c.set(7, y, STEEL, 5)
        c.set(8, y, STEEL, 3)
    for y in (4, 12):
        for x in (0, 1, 7, 8, 14, 15):
            c.shift(x, y, -2)
    c.hline(0, 15, 7, STEEL, 5)
    c.hline(0, 15, 8, STEEL, 3)
    return c


@texture("block/traffic_monitor_screen", refs=("block/observer_front",))
def traffic_monitor_screen(rng):
    """屏幕（模型取第 3–12 行、列）：深色的屏，钢的边框，一组流量的柱状图，最新的一根最亮。"""
    c = Canvas()
    c.fill(STEEL, 4)
    c.frame(2, 2, 13, 13, STEEL, 5)
    c.hline(3, 13, 13, STEEL, 2)
    c.vline(13, 3, 13, STEEL, 2)
    c.rect(3, 3, 12, 12, SCREEN, 1)
    c.hline(3, 12, 3, SCREEN, 0)
    c.vline(3, 3, 12, SCREEN, 0)
    for i, h in enumerate((2, 4, 3, 5, 4, 6, 5, 7)):
        x = 4 + i
        for y in range(12 - h, 12):
            c.set(x, y, CYAN_GLOW, 3 if i < 7 else 4)
        c.set(x, 12 - h, CYAN_GLOW, 5 if i == 7 else 4, glint=True)
    c.hline(4, 11, 4, SCREEN, 3)
    return c


def lamp(r) -> Canvas:
    """圆灯罩（模型取中间 4×4，贴在自发光的小块上）：中心最亮，往外一级级暗下去，左上一点高光。"""
    c = Canvas()
    c.fill(r, 3)
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1.2:
            c.set(x, y, r, 5)
        elif d < 2.6:
            c.set(x, y, r, 4)
        elif d > 6.5:
            c.set(x, y, r, 2)
    c.set(6, 6, r, 5, glint=True)
    return c


texture("block/traffic_monitor_lamp")(lambda rng: lamp(GREEN_GLOW))
texture("block/traffic_monitor_lamp_alarm")(lambda rng: lamp(RED_GLOW))


# ---- M12：物流配送器、配送机器人、建造无人机 ----

@texture("block/logistics_distributor_base")
def logistics_distributor_base(rng):
    """配送器底座的侧面（模型取第 12–15 行）：白色机壳，一道深色的槽，槽里一排青色的小灯。"""
    c = Canvas()
    c.fill(WHITE, 4)
    brushed(c, rng, 0, 0, 15, 10, 6)
    c.hline(0, 15, 11, WHITE, 2)
    c.hline(0, 15, 12, WHITE, 6)
    c.hline(0, 15, 13, GRAPHITE, 2)
    c.hline(0, 15, 14, GRAPHITE, 1)
    c.hline(0, 15, 15, WHITE, 2)
    for x in (3, 6, 9, 12):
        c.set(x, 13, CYAN_GLOW, 5, glint=True)
        c.set(x, 14, CYAN_GLOW, 2, glint=True)
    return c


@texture("block/logistics_distributor_top")
def logistics_distributor_top(rng):
    """配送器底座的顶面和停机坪的底面：白色的盖板，四边一圈倒角，中间一道接缝。"""
    c = Canvas()
    c.fill(WHITE, 4)
    brushed(c, rng, 2, 2, 13, 13, 6)
    c.frame(0, 0, 15, 15, WHITE, 2)
    c.hline(1, 14, 1, WHITE, 5)
    c.vline(1, 2, 14, WHITE, 5)
    c.hline(2, 14, 14, WHITE, 3)
    c.vline(14, 2, 13, WHITE, 3)
    c.hline(2, 13, 7, WHITE, 2)
    c.hline(2, 13, 8, WHITE, 5)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.rivet(x, y, WHITE, 3)
    return c


def distributor_column(on: bool) -> Canvas:
    """立柱（模型取第 6–11 行、第 4–11 列）：深色，竖着的散热筋，中间一道灯带；上下两圈白色的箍。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    for x in range(16):
        c.vline(x, 0, 15, GRAPHITE, 1 if x % 4 < 2 else 4)
    c.hline(0, 15, 6, WHITE, 5)
    c.hline(0, 15, 11, WHITE, 3)
    for x in range(16):
        c.set(x, 8, CYAN_GLOW, 5 if on else 2)
        c.set(x, 9, CYAN_GLOW, 4 if on else 1)
    return c


texture("block/logistics_distributor_column")(lambda rng: distributor_column(False))
texture("block/logistics_distributor_column_on")(lambda rng: distributor_column(True))


@texture("block/logistics_distributor_pad")
def logistics_distributor_pad(rng):
    """停机坪：深灰的坪面，一圈白色的降落环，中间深色的着陆点，四角黄黑的警示。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    brushed(c, rng, 1, 1, 14, 14, 8)
    c.hline(0, 15, 0, WHITE, 5)
    c.vline(0, 1, 15, WHITE, 5)
    c.hline(1, 15, 15, WHITE, 2)
    c.vline(15, 1, 14, WHITE, 2)
    for x, y in c.cells(1, 1, 14, 14):
        d = math.hypot(x - 7.5, y - 7.5)
        if 4.6 <= d < 5.8:
            c.set(x, y, WHITE, 5 if (x - 7.5) + (y - 7.5) < 0 else 4)
        elif d < 2.2:
            c.set(x, y, GRAPHITE, 1)
    c.set(7, 7, GRAPHITE, 2)
    for x0, y0, dx, dy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        for i in range(4):
            for j in range(4 - i):
                if (i + j) // 2 % 2 == 0:
                    c.set(x0 + dx * i, y0 + dy * j, HAZARD, 4)
                else:
                    c.set(x0 + dx * i, y0 + dy * j, GRAPHITE, 1)
    return c


@texture("block/logistics_distributor_pad_side")
def logistics_distributor_pad_side(rng):
    """停机坪的边沿（模型取第 4–5 行）：白色的唇边，下面一排深色的卡口。"""
    c = Canvas()
    c.fill(WHITE, 4)
    brushed(c, rng, 0, 0, 15, 15, 6)
    c.hline(0, 15, 3, WHITE, 2)
    c.hline(0, 15, 4, WHITE, 6)
    for x in range(16):
        if x % 4 == 0:
            c.set(x, 5, GRAPHITE, 2)
        else:
            c.set(x, 5, WHITE, 3)
    c.hline(0, 15, 6, WHITE, 1)
    return c


texture("block/logistics_distributor_light")(lambda rng: station_lamp(False))
texture("block/logistics_distributor_light_on")(lambda rng: station_lamp(True))


@texture("block/logistics_bot_body")
def logistics_bot_body(rng):
    """配送机器人的机身（模型取第 5–10 行、列）：白色的扁圆机壳，左上受光，中间一道接缝。"""
    c = Canvas()
    c.fill(WHITE, 5)
    c.hline(5, 10, 5, WHITE, 6)
    c.vline(5, 5, 10, WHITE, 6)
    c.hline(6, 10, 10, WHITE, 3)
    c.vline(10, 6, 10, WHITE, 3)
    c.hline(5, 10, 9, WHITE, 3)
    c.rect(7, 7, 8, 8, WHITE, 4)
    c.set(7, 7, WHITE, 6)
    return c


@texture("block/construction_drone_body")
def construction_drone_body(rng):
    """建造无人机的机身（模型取第 5–10 行、列）：黄白相间的斜纹，像工地上的东西。"""
    c = Canvas()
    for x, y in c.cells():
        k = x + y
        lv = 4 if k < 13 else (3 if k < 19 else 2)
        if (k // 3) % 2 == 0:
            c.set(x, y, HAZARD, lv)
        else:
            c.set(x, y, WHITE, lv + 1)
    c.frame(0, 0, 15, 15, GRAPHITE, 2)
    return c


@texture("block/construction_drone_light")
def construction_drone_light(rng):
    """焊枪的光（贴在自发光的小块上，模型取中间一带）：中间白热，往外是橙色。"""
    c = Canvas()
    c.fill(ORANGE_GLOW, 3)
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1.6:
            c.set(x, y, ORANGE_GLOW, 5)
        elif d < 3.2:
            c.set(x, y, ORANGE_GLOW, 4)
    return c

# ---- 物品图标 ----

def quad_icon(paint, eye) -> Canvas:
    """俯视的四旋翼：四个圆的旋翼（转起来是一团浅灰的影子，一道斜的桨影，中间轴帽），斜的机臂，
    中间圆角的方机身盖在旋翼上，机身中间一只镜头。描边加在外面；白色部分的描边换成深灰，在物品栏里才看得清。"""
    c = Canvas()
    for i in range(2, 14):
        c.set(i, i, GRAPHITE, 3)
        c.set(i, 15 - i, GRAPHITE, 3)
    for cx, cy in ((3.5, 3.5), (11.5, 3.5), (3.5, 11.5), (11.5, 11.5)):
        for x, y in c.disc_cells(cx, cy, 2.6):
            c.set(x, y, STEEL, 5 if (x - cx) + (y - cy) < -1.5 else 4)
        c.set(int(cx - 1.5), int(cy - 1.5), STEEL, 6, glint=True)
        c.set(int(cx), int(cy), GRAPHITE, 2, glint=True)
    for x, y in c.cells(5, 5, 10, 10):
        if x in (5, 10) and y in (5, 10):
            continue
        r, lv = paint(x, y)
        if y == 5 or x == 5:
            lv += 1
        elif y == 10 or x == 10:
            lv -= 1
        c.set(x, y, r, lv)
    c.set(7, 7, eye, 5, glint=True)
    c.set(8, 7, eye, 4, glint=True)
    c.set(7, 8, eye, 4, glint=True)
    c.set(8, 8, eye, 3, glint=True)
    c.outline(outside=True)
    # 整张是照着形状一个个摆的像素（旋翼、机臂、轴帽），检查零碎像素时不算
    c.glints = {(x, y) for x, y in c.cells() if c.get(x, y)}
    return c


@texture("item/logistics_drone", refs=("item/phantom_membrane", "item/elytra"))
def logistics_drone_icon(rng):
    """俯视的物流无人机：白色机身，两侧一道蓝色涂装，中间青色的镜头，四个旋翼。"""
    def paint(x, y):
        return (BLUE, 3) if x in (6, 9) and y in (7, 8) else (WHITE, 5)
    return quad_icon(paint, CYAN_GLOW)


@texture("item/construction_drone")
def construction_drone_icon(rng):
    """俯视的建造无人机：黄白斜纹的机身，中间一盏橙色的焊灯，四个旋翼。"""
    def paint(x, y):
        return (HAZARD, 4) if ((x + y) // 2) % 2 == 0 else (WHITE, 5)
    return quad_icon(paint, ORANGE_GLOW)


@texture("item/logistics_vessel", refs=("item/minecart",))
def logistics_vessel_icon(rng):
    """侧看的物流运输船，船头朝右：白色的船身（上亮下暗），中段蓝色的货舱，船头的舷窗，
    上下两片深色的翼，尾部蓝白色的推进光。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "................",
        "......lk........",
        ".....lkkj.......",
        "...cclkkjcccc...",
        "..c6666666666b..",
        "xk555EEDEE55onb.",
        "zk555DDCDD555n5a",
        "xk444CCCCC4444a.",
        "..b3333333333a..",
        "...aajjjjaaaa...",
        ".....kjjj.......",
        "......jj........",
    ], {"0123456": WHITE, "abcdefg": STEEL, "hijkl": GRAPHITE, "ABCDEFG": BLUE, "mno": GLASS, "xyz": BLUE_GLOW})
    return c


@texture("item/logistics_bot")
def logistics_bot_icon(rng):
    """侧看的配送机器人：扁圆的白机身，腰上一道青色灯带，两侧推进舱下面喷着青光，底下夹着一个纸箱。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "................",
        "......cccc......",
        ".....c6665b.....",
        ".eedc666555beed.",
        ".ddcrutttttsddc.",
        ".bbbc444443bbbb.",
        "..t..aaaaaa..t..",
        "......k..k......",
        ".....BBBBBB.....",
        ".....BGDDGA.....",
        ".....BFDDFA.....",
        ".....BFDDEA.....",
        ".....AAAAAA.....",
    ], {"0123456": WHITE, "abcdefg": STEEL, "hijkl": GRAPHITE, "pqrstu": CYAN_GLOW, "ABCDEFG": BREAD})
    return c
