"""防御设施的贴图（ADR-0031）：六种炮塔、行星护盾发生器、信号塔、干扰塔、战场分析基地，弹药和投掷物。

炮塔是军用的涂装：橄榄灰的装甲板，接缝、铆钉、掉漆，底座一圈黄黑警示条，炮口烧得发黑。各型号一眼分得开：
高斯炮塔两侧是铜线圈，激光炮塔是白色的壳子和青色的透镜，聚爆炮是厚重的橄榄绿装甲，导弹炮塔是一个六联装的发射箱，
等离子炮塔是深蓝灰、磁环上一道青光，近程等离子炮塔是紫灰、导轨中间夹着发紫光的线圈。

炮塔的模型是很多小方块拼的，UV 按方块在格子里的位置投影（tools/factory_models.py 的 uv_for），
一张贴图的不同区域贴在不同的小方块上。所以壳子、炮管这几张画成"材质"：哪一小块切出来都像装甲板；
只有炮口、发射箱正面这种固定看得到的区域按位置画（炮口的端面在中间几列、第 3–7 行）。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import (AMBER, AMBER_GLOW, BLUE_GLOW, BRASS, COPPER, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN_GLOW, HAZARD,
                     IRON, MAGENTA_GLOW, ORANGE, ORANGE_GLOW, RED, RED_GLOW, SCREEN, STEEL, TEAL, TITANIUM, VIOLET,
                     VIOLET_GLOW, WHITE, register)
from parts import bolts, casing, status_lights
from recipe import texture

# 军用的橄榄灰：炮塔底座、高斯和导弹炮塔的壳子、战场分析基地
GUN = register(ramp("defense_gunmetal", 110, 0.016, n=8, lo=0.21, hi=0.76, shift=16))
# 聚爆炮的橄榄绿装甲
OLIVE = register(ramp("defense_olive", 116, 0.052, n=7, lo=0.22, hi=0.76, shift=18))
# 等离子炮塔的深蓝灰
NAVY = register(ramp("defense_navy", 250, 0.042, n=7, lo=0.20, hi=0.74, shift=12))
# 近程等离子炮塔的紫灰
PLUM = register(ramp("defense_plum", 305, 0.036, n=7, lo=0.20, hi=0.74, shift=12))


# ---------------------------------------------------------------- 共用的画法

def armor(rng: random.Random, r, base: int, xs=(8,), ys=(5, 11), rivets: bool = True) -> Canvas:
    """装甲板的材质：接缝（暗两级的槽，xs 列、ys 行，贯通整张图）把它分成几块板子，每块板子上、左边受光，
    下、右边背光，角上一颗铆钉，板面有少量成团的明暗和磨损。哪一块切出来贴在小方块上都像装甲。"""
    c = Canvas()
    c.fill(r, base)
    c.clusters(rng, 0, 0, 15, 15, -1, 2, 2)
    bx = [-1] + list(xs) + [16]
    by = [-1] + list(ys) + [16]
    for i in range(len(bx) - 1):
        for j in range(len(by) - 1):
            x0, x1 = bx[i] + 1, bx[i + 1] - 1
            y0, y1 = by[j] + 1, by[j + 1] - 1
            if x1 - x0 < 1 or y1 - y0 < 1:
                continue
            c.bevel(x0, y0, x1, y1)
            c.edge_wear(rng, x0, y0, x1, y1, 0.06)
            if rivets and x1 - x0 >= 5 and y1 - y0 >= 3:
                c.rivet(x0 + 1, y0 + 1, r, base)
                c.rivet(x1 - 2, y0 + 1, r, base)
    for x in xs:
        c.vline(x, 0, 15, r, base - 2)
    for y in ys:
        c.hline(0, 15, y, r, base - 2)
    return c


def hazard_band(c: Canvas, y0: int, y1: int, x0: int = 0, x1: int = 15, lv: int = 4):
    """黄黑警示条，斜着往右上，黑的用石墨色（不用纯黑）。"""
    c.stripes(x0, y0, x1, y1, HAZARD, lv, GRAPHITE, 1, period=4, width=2)
    # 上沿受光、下沿背光
    for x in range(x0, x1 + 1):
        c.shift(x, y0, 1)
        c.shift(x, y1, -1)


def tube(c: Canvas, r, base: int, x0: int, x1: int, y0: int = 0, y1: int = 15, vertical: bool = False):
    """炮管的材质：横截面的明暗（受光一侧亮带、背光一侧最暗）。"""
    if vertical:
        c.cylinder(x0, y0, x1, y1, r, base, vertical=True)
    else:
        c.cylinder(x0, y0, x1, y1, r, base, vertical=False)


def soot(c: Canvas, rng: random.Random, x0, y0, x1, y1, amount: float = 0.5):
    """炮口附近的烟熏：往石墨色压暗，越靠近炮口越重。"""
    for x, y in c.cells(x0, y0, x1, y1):
        if rng.random() < amount:
            px = c.get(x, y)
            if px:
                c.set(x, y, GRAPHITE, min(px[1], 2 if rng.random() < 0.6 else 1))


# ---------------------------------------------------------------- 底座

@texture("block/turret_base", refs=("block/smooth_stone_slab_side", "block/blast_furnace_side"))
def turret_base(rng):
    # 侧面只露出最下面四行（底座 4 像素高）：上沿一道受光的唇边，两行警示条，最下一行背光。
    # 上面十二行在底面（down）上看得到：一块带接缝的钢板
    c = armor(rng, GUN, 4, xs=(8,), ys=(6,), rivets=False)
    for x, y in ((2, 2), (12, 2), (2, 9), (12, 9)):
        c.rivet(x, y, GUN, 4)
    c.hline(0, 15, 11, GUN, 3)
    c.hline(0, 15, 12, GUN, 6)
    hazard_band(c, 13, 14, lv=4)
    c.hline(0, 15, 15, GUN, 1)
    c.set(0, 12, GUN, 7)
    return c


@texture("block/turret_base_top", refs=("block/smooth_stone",))
def turret_base_top(rng):
    # 转盘压在中间（1.5–14.5），只露出外面一圈：受光的上、左边，背光的下、右边，四角各一颗螺栓
    c = Canvas()
    c.fill(GUN, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 8, 2)
    c.clusters(rng, 0, 0, 15, 15, 1, 5, 2)
    c.hline(0, 15, 0, GUN, 6)
    c.vline(0, 0, 15, GUN, 6)
    c.hline(1, 15, 15, GUN, 2)
    c.vline(15, 1, 15, GUN, 2)
    c.set(15, 0, GUN, 4)
    c.set(0, 15, GUN, 4)
    # 转盘落进去的一圈槽
    c.frame(2, 2, 13, 13, GUN, 1)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.bolt(x, y, GUN, 4)
    # 警示角：四个角上一小段黄黑条
    for x0, y0 in ((3, 0), (10, 0), (3, 15), (10, 15)):
        for i in range(3):
            c.set(x0 + i, y0, HAZARD if (x0 + i + y0) % 4 < 2 else GRAPHITE, 4 if (x0 + i) % 4 < 2 else 1)
    return c


@texture("block/turret_ring", refs=("block/iron_block",))
def turret_ring(rng):
    # 转盘：深色金属。侧面露出第 9–11 行：一道受光的边、一排刻度、一道背光的边。
    # 顶面露出 1.5–4.5 那一圈：外沿一道亮边，里面一圈滚珠轴承的刻度
    c = Canvas()
    c.fill(GRAPHITE, 2)
    for x, y in c.cells():
        d = max(abs(x - 7.5), abs(y - 7.5))
        if d > 6.5:
            c.set(x, y, GRAPHITE, 3)
        elif d > 5.5:
            c.set(x, y, GRAPHITE, 4 if x + y < 15 else 1)
        elif d > 4.5:
            c.set(x, y, GRAPHITE, 1)
            if (x + y) % 3 == 0:
                c.set(x, y, GRAPHITE, 4, glint=True)
    for x in range(16):
        c.set(x, 9, GRAPHITE, 4)
        c.set(x, 10, STEEL, 4, glint=True) if x % 2 == 0 else c.set(x, 10, GRAPHITE, 1)
        c.set(x, 11, GRAPHITE, 1)
    return c


# ---------------------------------------------------------------- 高斯炮塔

@texture("block/gauss_turret_housing")
def gauss_housing(rng):
    c = armor(rng, GUN, 4, xs=(), ys=(2, 12))
    # 一道小的警示标记，给人看出是军用的
    c.hline(3, 5, 8, HAZARD, 4)
    c.hline(3, 5, 9, HAZARD, 2)
    return c


@texture("block/gauss_turret_barrel")
def gauss_barrel(rng):
    # 两根并排的电磁轨炮管：侧面露出第 4–5 行，顶面露出第 5–7、8–10 列（沿炮管拉长）
    c = Canvas()
    c.fill(GRAPHITE, 3)
    for x0 in (0, 4, 8, 12):
        tube(c, GRAPHITE, 3, x0, x0 + 3, vertical=True)
    # 侧面那两行：上面一道受光的棱，下面背光
    c.hline(0, 15, 4, GRAPHITE, 5)
    c.hline(0, 15, 5, GRAPHITE, 2)
    # 一节一节的加速线圈卡箍（铜色），侧面顺着炮管方向排在第 11–15 列，顶面在第 11–15 行
    for k in (12, 14):
        c.vline(k, 3, 6, COPPER, 3)
        c.hline(4, 11, k, COPPER, 3)
        c.set(k, 4, COPPER, 5, glint=True)
    return c


@texture("block/gauss_turret_accent", refs=("block/copper_block", "block/exposed_cut_copper"))
def gauss_accent(rng):
    # 线圈舱：一圈圈铜线（横纹），两头钢的端盖；炮口制退器的端面在第 5–7、8–10 列，第 4–6 行：两个膛口
    c = Canvas()
    for y in range(16):
        c.hline(0, 15, y, COPPER, 4 if y % 2 == 0 else 2)
    for y in range(0, 16, 2):
        for x in range(16):
            if (x * 5 + y * 3) % 7 == 0:
                c.set(x, y, COPPER, 5, glint=True)
    c.frame(0, 0, 15, 15, STEEL, 3)
    c.hline(1, 14, 0, STEEL, 5)
    c.vline(0, 1, 14, STEEL, 5)
    # 膛口只画一个像素：这块地方也贴在线圈舱的侧面上，画大了线圈上会多出两个黑洞
    for cx in (6, 9):
        c.set(cx, 5, GRAPHITE, 0, glint=True)
    # 顶上的散热片（第 6–10 列、第 1–2 行）
    c.hline(6, 9, 1, STEEL, 6)
    c.hline(6, 9, 2, STEEL, 2)
    return c


# ---------------------------------------------------------------- 激光炮塔

@texture("block/laser_turret_housing", refs=("block/white_concrete",))
def laser_housing(rng):
    c = armor(rng, WHITE, 4, xs=(), ys=(2, 10))
    # 侧面一道青色的发光条（工作灯），嵌在深色的槽里
    c.hline(3, 12, 12, GRAPHITE, 2)
    c.hline(4, 11, 12, CYAN_GLOW, 4)
    c.set(4, 12, CYAN_GLOW, 5, glint=True)
    return c


@texture("block/laser_turret_barrel")
def laser_barrel(rng):
    # 发射管：白色的管子（侧面第 4–5 行，顶面第 7–8 列），沿管子一道深色的导光槽
    c = Canvas()
    c.fill(WHITE, 3)
    tube(c, WHITE, 4, 6, 9, vertical=True)
    c.vline(10, 0, 15, WHITE, 1)
    c.vline(5, 0, 15, WHITE, 2)
    c.hline(0, 15, 3, WHITE, 6)
    c.hline(0, 15, 4, WHITE, 5)
    c.hline(0, 15, 5, WHITE, 3)
    c.hline(0, 15, 6, WHITE, 1)
    for x in range(0, 16, 4):
        c.set(x, 4, GRAPHITE, 2)
        c.set(x, 5, GRAPHITE, 1)
    for y in range(0, 16, 4):
        c.set(7, y, GRAPHITE, 2)
        c.set(8, y, GRAPHITE, 1)
    return c


@texture("block/laser_turret_accent")
def laser_accent(rng):
    # 散热片和顶上的散热鳍：深色金属，一道道竖鳍
    c = Canvas()
    c.fill(GRAPHITE, 3)
    for x in range(16):
        c.vline(x, 0, 15, GRAPHITE, 4 if x % 2 == 0 else 1)
    c.hline(0, 15, 2, GRAPHITE, 5)
    c.hline(0, 15, 7, GRAPHITE, 1)
    # 透镜座的端面（第 6–9 列，第 3–6 行）：一圈亮的金属环
    c.frame(6, 3, 9, 6, STEEL, 5)
    c.rect(7, 4, 8, 5, GRAPHITE, 0)
    c.set(6, 3, STEEL, 7, glint=True)
    return c


@texture("block/laser_turret_lens", refs=("block/sea_lantern",))
def laser_lens(rng):
    # 透镜：自发光的元素，只露出中间 2×2（第 7–8 列，第 4–5 行）。中间白，往外青色，外圈一道暗环
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 4.5)
        lv = 5 if d < 0.8 else 4 if d < 1.8 else 3 if d < 3.2 else 2
        c.set(x, y, CYAN_GLOW, lv)
    c.set(7, 4, WHITE, 6, glint=True)
    return c


# ---------------------------------------------------------------- 聚爆炮

@texture("block/implosion_cannon_housing", refs=("block/moss_block",))
def implosion_housing(rng):
    # 厚重的橄榄绿装甲：大块的板子，斜的接缝，粗铆钉
    c = armor(rng, OLIVE, 3, xs=(10,), ys=(9,), rivets=False)
    for x, y in ((2, 2), (13, 2), (7, 6), (2, 12), (8, 12), (13, 12)):
        c.bolt(x, y, OLIVE, 3)
    return c


@texture("block/implosion_cannon_barrel")
def implosion_barrel(rng):
    # 粗炮管和上面的复进机：深橄榄色，侧面第 3–6 行是炮管的截面，第 1–2 行是复进机
    c = Canvas()
    c.fill(OLIVE, 2)
    tube(c, OLIVE, 2, 0, 15, 3, 6)
    tube(c, OLIVE, 2, 0, 15, 1, 2)
    tube(c, OLIVE, 2, 6, 10, vertical=True, y0=7, y1=15)
    tube(c, OLIVE, 3, 6, 9, vertical=True, y0=0, y1=6)
    # 炮管上一圈圈加强箍（侧面在第 12–15 列，顶面在第 12–15 行）
    for k in (12, 15):
        c.vline(k, 3, 6, GRAPHITE, 2)
        c.hline(5, 10, k, GRAPHITE, 2)
    soot(c, rng, 13, 3, 15, 6, 0.4)
    soot(c, rng, 5, 13, 10, 15, 0.4)
    return c


@texture("block/implosion_cannon_accent")
def implosion_accent(rng):
    # 炮尾和炮口的加强环：深色钢，两道警示条；炮口的端面在第 5–10 列、第 3–7 行：大口径的膛口
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 2)
    c.clusters(rng, 0, 0, 15, 15, 1, 5, 2)
    c.frame(0, 0, 15, 15, GRAPHITE, 4)
    for y in (1, 2):
        for x in range(16):
            c.set(x, y, HAZARD if (x + y) % 4 < 2 else GRAPHITE, 4 if (x + y) % 4 < 2 else 1)
    for y in (8, 9):
        for x in range(16):
            c.set(x, y, HAZARD if (x + y) % 4 < 2 else GRAPHITE, 4 if (x + y) % 4 < 2 else 1)
    # 膛口
    c.rect(5, 3, 10, 7, GRAPHITE, 2)
    c.rect(6, 4, 9, 6, GRAPHITE, 0)
    c.hline(6, 9, 4, GRAPHITE, 0)
    c.hline(7, 9, 6, GRAPHITE, 1)
    c.set(5, 3, GRAPHITE, 4)
    return c


# ---------------------------------------------------------------- 导弹炮塔

@texture("block/missile_turret_housing")
def missile_housing(rng):
    return armor(rng, GUN, 4, xs=(), ys=(2, 12))


@texture("block/missile_turret_barrel")
def missile_barrel(rng):
    # 发射箱的侧面和顶面：一整块的箱体，横的加强筋，下面一道警示条，侧面喷着白色的编号块
    c = armor(rng, GUN, 3, xs=(), ys=(4, 10), rivets=False)
    for x, y in ((3, 1), (12, 1), (3, 12), (12, 12)):
        c.rivet(x, y, GUN, 3)
    # 喷涂的编号：两个白色的短条
    c.hline(5, 6, 6, WHITE, 4)
    c.hline(9, 10, 6, WHITE, 4)
    c.hline(5, 6, 7, WHITE, 3)
    c.hline(9, 10, 7, WHITE, 3)
    hazard_band(c, 12, 13, 2, 13)
    return c


@texture("block/missile_turret_accent")
def missile_accent(rng):
    # 发射箱口的加强框：深色钢，四边倒角
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 2)
    c.clusters(rng, 0, 0, 15, 15, 1, 6, 2)
    for y in (1, 8):
        c.hline(0, 15, y, GRAPHITE, 5)
    for y in (2, 7):
        c.hline(0, 15, y, GRAPHITE, 1)
    c.hline(0, 15, 13, GRAPHITE, 5)
    c.hline(0, 15, 14, GRAPHITE, 1)
    return c


@texture("block/missile_turret_pod_front", refs=("block/dispenser_front",))
def missile_pod_front(rng):
    # 发射箱正面（只露出第 3–12 列、第 2–7 行）：两排三个发射管，管里看得到红色的弹头
    c = Canvas()
    c.fill(GUN, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 2)
    c.frame(2, 1, 13, 8, GUN, 2)
    c.hline(3, 12, 2, GUN, 6)
    for y0 in (3, 6):
        for x0 in (4, 7, 10):
            c.set(x0, y0, GRAPHITE, 0)
            c.set(x0 + 1, y0, GRAPHITE, 1)
            c.set(x0, y0 + 1, RED, 2)
            c.set(x0 + 1, y0 + 1, RED, 4, glint=True)
    c.hline(3, 12, 5, GUN, 5)
    return c


@texture("block/missile_turret_lamp", refs=("block/redstone_lamp_on",))
def missile_lamp(rng):
    # 指示灯：自发光的小方块，露出第 6–10 列、第 1 行和顶面中间：红色的灯罩，中间最亮
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, (y - 3) * 0.6)
        c.set(x, y, RED_GLOW, 4 if d < 1.2 else 3 if d < 3 else 2)
    c.hline(0, 15, 0, RED_GLOW, 2)
    c.set(7, 6, RED_GLOW, 5, glint=True)
    return c


# ---------------------------------------------------------------- 等离子炮塔

@texture("block/plasma_turret_housing")
def plasma_housing(rng):
    c = armor(rng, NAVY, 4, xs=(), ys=(2, 12))
    c.hline(3, 12, 9, GRAPHITE, 1)
    c.hline(4, 11, 9, CYAN_GLOW, 3)
    c.set(4, 9, CYAN_GLOW, 4, glint=True)
    return c


@texture("block/plasma_turret_barrel")
def plasma_barrel(rng):
    # 粗炮管：深色钢，侧面第 3–6 行，顶面第 6–9 列；沿管子一道道冷却槽
    c = Canvas()
    c.fill(GRAPHITE, 3)
    tube(c, GRAPHITE, 3, 0, 15, 3, 6)
    tube(c, GRAPHITE, 3, 6, 9, vertical=True, y0=7, y1=15)
    tube(c, GRAPHITE, 3, 6, 9, vertical=True, y0=0, y1=2)
    for x in range(0, 16, 3):
        c.vline(x, 3, 6, GRAPHITE, 1)
    for y in range(9, 16, 3):
        c.hline(6, 9, y, GRAPHITE, 1)
    return c


@texture("block/plasma_turret_accent")
def plasma_accent(rng):
    # 磁环、散热片：深色钢上一道青色的光带（侧面第 4–5 行）
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 6, 2)
    c.hline(0, 15, 2, GRAPHITE, 5)
    c.hline(0, 15, 3, GRAPHITE, 1)
    c.hline(0, 15, 4, CYAN_GLOW, 4)
    c.hline(0, 15, 5, CYAN_GLOW, 3)
    c.hline(0, 15, 6, GRAPHITE, 1)
    c.hline(0, 15, 7, GRAPHITE, 4)
    # 顶面（第 12–16 行）：环的上沿，中间也是青色
    c.hline(0, 15, 12, GRAPHITE, 5)
    c.hline(5, 10, 13, CYAN_GLOW, 3)
    c.hline(0, 15, 15, CYAN_GLOW, 3)
    return c


@texture("block/plasma_turret_glow", refs=("block/beacon",))
def plasma_glow(rng):
    # 自发光的约束腔窗口和炮口的约束环：中间白，往外青色
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, (y - 4.5) * 1.3)
        lv = 5 if d < 1.0 else 4 if d < 2.2 else 3 if d < 3.6 else 2
        c.set(x, y, CYAN_GLOW, lv)
    for x in range(16):
        if x % 3 == 0:
            c.shift(x, 3, -1)
            c.shift(x, 6, -1)
    return c


# ---------------------------------------------------------------- 近程等离子炮塔

@texture("block/sr_plasma_turret_housing")
def sr_plasma_housing(rng):
    c = armor(rng, PLUM, 4, xs=(), ys=(2, 12))
    hazard_band(c, 13, 14, 2, 13)
    return c


@texture("block/sr_plasma_turret_barrel")
def sr_plasma_barrel(rng):
    # 上下两条导轨：侧面第 3 行（上导轨）、第 6 行（下导轨），顶面第 6–10 列；导轨上一排散热孔
    c = Canvas()
    c.fill(PLUM, 3)
    c.hline(0, 15, 3, PLUM, 5)
    c.hline(0, 15, 6, PLUM, 2)
    for x in range(0, 16, 2):
        c.set(x, 3, PLUM, 3)
        c.set(x, 6, PLUM, 1)
    tube(c, PLUM, 3, 5, 10, vertical=True, y0=7, y1=15)
    for y in range(9, 16, 2):
        c.set(7, y, GRAPHITE, 1)
        c.set(8, y, GRAPHITE, 2)
    return c


@texture("block/sr_plasma_turret_accent")
def sr_plasma_accent(rng):
    # 导轨端头的卡箍：深色钢，第 2、7 行露出来，各一道紫光
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 6, 2)
    for y in (2, 7):
        c.hline(0, 15, y, GRAPHITE, 4)
        for x in range(3, 13, 3):
            c.set(x, y, VIOLET_GLOW, 4, glint=True)
    return c


@texture("block/sr_plasma_turret_glow", refs=("block/amethyst_block",))
def sr_plasma_glow(rng):
    # 导轨中间夹着的线圈（自发光）：一道道紫色的绕线，中间行最亮
    c = Canvas()
    for x, y in c.cells():
        lv = 5 if y in (4, 5) and x % 3 != 0 else 4 if y in (3, 6) else 3
        if x % 3 == 0:
            lv -= 1
        c.set(x, y, VIOLET_GLOW, lv)
    return c


# ---------------------------------------------------------------- 行星护盾发生器

@texture("block/planetary_shield_generator_bank", refs=("block/respawn_anchor_side4",))
def shield_bank(rng):
    # 下半截一圈储能电容（四面都露出第 2–13 列、第 2–12 行）。每三列一格：一根竖的电容管，
    # 管里青色的电量从下往上亮到不同高度；格之间是深色的隔条。隔条在第 2、5、8、11 列，转角处正好接上
    c = Canvas()
    c.fill(GRAPHITE, 2)
    c.hline(0, 15, 1, GRAPHITE, 4)
    c.hline(0, 15, 13, GRAPHITE, 1)
    levels = {3: 4, 6: 7, 9: 3, 12: 5}   # 每根管从下往上亮几格
    for x0, lit in levels.items():
        for y in range(3, 12):
            filled = y >= 12 - lit
            c.set(x0, y, CYAN_GLOW if filled else SCREEN, 4 if filled else 2)
            c.set(x0 + 1, y, CYAN_GLOW if filled else SCREEN, 3 if filled else 1)
        top = 12 - lit
        c.set(x0, top, CYAN_GLOW, 5, glint=True)
        # 管子上下的金属帽
        c.set(x0, 2, STEEL, 6)
        c.set(x0 + 1, 2, STEEL, 4)
        c.set(x0, 12, STEEL, 4)
        c.set(x0 + 1, 12, STEEL, 2)
    for x in (2, 5, 8, 11, 14):
        c.vline(x, 2, 12, GRAPHITE, 3)
        c.set(x, 2, GRAPHITE, 5)
        c.set(x, 7, STEEL, 5, glint=True)
    c.frame(0, 0, 15, 15, GRAPHITE, 1)
    return c


@texture("block/planetary_shield_generator_core")
def shield_core(rng):
    # 导能柱和四根支架：深色的柱子，正中一道青色的导能管（第 7–8 列），每隔四行一道钢箍
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.cylinder(0, 0, 15, 15, GRAPHITE, 3, vertical=True, amp=1)
    for y in range(16):
        c.set(7, y, CYAN_GLOW, 4)
        c.set(8, y, CYAN_GLOW, 3)
        c.set(6, y, GRAPHITE, 1)
        c.set(9, y, GRAPHITE, 1)
    for y in (1, 5, 9, 13):
        c.hline(0, 15, y, STEEL, 5)
        c.hline(0, 15, y + 1, STEEL, 2)
        c.set(2, y, STEEL, 7, glint=True)
        c.set(12, y, STEEL, 7, glint=True)
    return c


@texture("block/planetary_shield_generator_dish", refs=("block/smooth_quartz",))
def shield_dish(rng):
    # 抛物面的盘：白色的金属，一圈圈同心的接缝，中间亮、往外暗一点；侧面（第 7–10 行）是盘的厚边
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        lv = 5 if d < 3 else 4 if d < 5.5 else 3
        if (x - 7.5) + (y - 7.5) < -4:
            lv += 1
        c.set(x, y, WHITE, lv)
        if abs(d - 3.2) < 0.5 or abs(d - 6.2) < 0.5:
            c.set(x, y, WHITE, 1 if x + y > 15 else 2)
    c.frame(0, 0, 15, 15, WHITE, 2)
    c.hline(1, 14, 0, WHITE, 5)
    c.vline(0, 1, 14, WHITE, 5)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, WHITE, 3)
    return c


def emitter(lit: bool) -> Canvas:
    """护盾发射头：侧面露出第 6–9 列、第 0–6 行，顶面露出中间 4×4。亮着是从白到青的光，灭着是暗的玻璃。"""
    c = Canvas()
    c.fill(STEEL, 3)
    glow, lo = (CYAN_GLOW, 2) if lit else (SCREEN, 1)
    for x, y in c.cells(5, 0, 10, 6):
        d = abs(x - 7.5) + abs(y - 3) * 0.6
        if lit:
            c.set(x, y, CYAN_GLOW, 5 if d < 1.2 else 4 if d < 2.2 else 3)
        else:
            c.set(x, y, SCREEN, 3 if d < 1.2 else 2 if d < 2.2 else 1)
    for x in range(5, 11):
        c.set(x, 0, STEEL, 5)
        c.set(x, 6, STEEL, 2)
    for x, y in c.cells(5, 5, 10, 10):
        d = max(abs(x - 7.5), abs(y - 7.5))
        if d < 1:
            c.set(x, y, glow, 5 if lit else 3)
        elif d < 2:
            c.set(x, y, glow, 4 if lit else 2)
        else:
            c.set(x, y, STEEL, 4 if x + y < 15 else 2)
    if lit:
        c.set(7, 7, WHITE, 6, glint=True)
    else:
        c.set(7, 7, GLASS, 3, glint=True)
        c.set(7, 2, GLASS, 3, glint=True)
    return c


@texture("block/planetary_shield_generator_top", refs=("block/beacon",))
def shield_emitter_on(rng):
    return emitter(True)


@texture("block/planetary_shield_generator_top_off")
def shield_emitter_off(rng):
    return emitter(False)


# ---------------------------------------------------------------- 信号塔

@texture("block/signal_tower_pole", refs=("block/iron_bars",))
def signal_pole(rng):
    # 红白相间的桁架：下半截露出第 4–11 列，上半截第 5–10 列。立柱在第 5、10 列，中间 X 形斜撑，
    # 后面是深色的芯；红白每四行换一次（航空障碍的涂装）
    c = Canvas()
    c.fill(GRAPHITE, 1)
    c.vline(7, 0, 15, GRAPHITE, 2)
    c.vline(8, 0, 15, GRAPHITE, 0)
    for y in range(16):
        band = RED if (y // 4) % 2 == 0 else WHITE
        lit = 4 if band is RED else 5
        c.set(5, y, band, lit + 1)
        c.set(10, y, band, lit - 1)
        d = y % 8
        for x in (6 + d // 2, 9 - d // 2):
            if 6 <= x <= 9:
                c.set(x, y, band, lit)
    for y in range(0, 16, 4):
        c.hline(5, 10, y, STEEL, 4)
    return c


@texture("block/signal_tower_dish", refs=("block/lightning_rod",))
def signal_dish(rng):
    # 天线板：白色的整流罩，竖着几道肋，第 6–7 行一道红色涂装，上下（第 4、9 行）是深色的安装框
    c = Canvas()
    c.fill(WHITE, 4)
    for x in range(16):
        if x % 3 == 0:
            c.vline(x, 0, 15, WHITE, 2)
        elif x % 3 == 1:
            c.vline(x, 0, 15, WHITE, 5)
    c.hline(0, 15, 4, GRAPHITE, 2)
    c.hline(0, 15, 9, GRAPHITE, 1)
    c.hline(0, 15, 6, RED, 4)
    c.hline(0, 15, 7, RED, 2)
    for x in range(1, 16, 3):
        c.set(x, 5, WHITE, 6, glint=True)
    return c


def beacon(lit: bool) -> Canvas:
    """警示灯：红色的灯罩，侧面露出第 6–9 列、第 0–3 行，顶面露出中间 4×4。"""
    c = Canvas()
    r = RED_GLOW if lit else RED
    c.fill(GRAPHITE, 2)
    for x, y in c.cells(5, 0, 10, 4):
        d = abs(x - 7.5) + abs(y - 1.5) * 0.8
        c.set(x, y, r, (4 if d < 1.2 else 3 if d < 2.5 else 2) if lit else (3 if d < 1.2 else 2 if d < 2.5 else 1))
    for x in range(5, 11):
        c.set(x, 4, GRAPHITE, 3)
    for x, y in c.cells(5, 5, 10, 10):
        d = max(abs(x - 7.5), abs(y - 7.5))
        c.set(x, y, r, (4 if d < 1 else 3 if d < 2 else 2) if lit else (3 if d < 1 else 2 if d < 2 else 1))
    c.set(6, 6, r, 5 if lit else 4, glint=True)
    c.set(6, 1, r, 5 if lit else 4, glint=True)
    return c


@texture("block/signal_tower_top", refs=("block/redstone_lamp_on",))
def signal_beacon_on(rng):
    return beacon(True)


@texture("block/signal_tower_top_off", refs=("block/redstone_lamp",))
def signal_beacon_off(rng):
    return beacon(False)


# ---------------------------------------------------------------- 干扰塔

@texture("block/jammer_tower_body")
def jammer_body(rng):
    # 深灰的机壳（下半截露出第 3–12 列、第 0–13 行），正中一道竖的紫色观察窗，两侧散热格栅
    c = armor(rng, GRAPHITE, 3, xs=(), ys=(4, 13), rivets=False)
    c.rect(7, 5, 8, 12, GRAPHITE, 0)
    for y in range(6, 12):
        c.set(7, y, VIOLET_GLOW, 3 if y % 2 else 4)
        c.set(8, y, VIOLET_GLOW, 2)
    for x0 in (4, 10):
        c.vent(x0, 6, x0 + 1, 11, GRAPHITE, 3)
    for x, y in ((3, 1), (11, 1), (3, 10), (11, 10)):
        c.rivet(x, y, GRAPHITE, 3)
    return c


@texture("block/jammer_tower_coil", refs=("block/copper_block",))
def jammer_coil(rng):
    # 发射线圈：一圈圈铜线（每行一圈），正中一道紫色的光（第 7–8 列）
    c = Canvas()
    for y in range(16):
        c.hline(0, 15, y, COPPER, 4 if y % 2 == 0 else 2)
    for x, y in c.cells():
        if y % 2 == 0 and (x * 3 + y) % 5 == 0:
            c.set(x, y, COPPER, 5, glint=True)
    for y in range(16):
        c.set(7, y, VIOLET_GLOW, 4)
        c.set(8, y, VIOLET_GLOW, 3)
        c.set(6, y, COPPER, 1)
        c.set(9, y, COPPER, 1)
    return c


def jammer_emitter(lit: bool) -> Canvas:
    """干扰塔顶上的发射头：侧面露出第 5–10 列、第 0–2 行，顶面露出中间 6×6。"""
    c = Canvas()
    c.fill(GRAPHITE, 2)
    r = VIOLET_GLOW if lit else VIOLET
    for x, y in c.cells(5, 0, 10, 2):
        d = abs(x - 7.5)
        c.set(x, y, r, (5 if d < 1 else 4 if d < 2 else 3) if lit else (3 if d < 1 else 2))
    c.hline(5, 10, 2, GRAPHITE, 3)
    for x, y in c.cells(5, 5, 10, 10):
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1:
            c.set(x, y, r, 5 if lit else 3)
        elif d < 2.2:
            c.set(x, y, r, 4 if lit else 2)
        elif d < 3:
            c.set(x, y, r, 3 if lit else 1)
        else:
            c.set(x, y, GRAPHITE, 3)
    if lit:
        c.set(7, 7, WHITE, 6, glint=True)
    else:
        # 灭着的玻璃上一点反光
        c.set(6, 6, VIOLET, 5, glint=True)
        c.set(6, 0, VIOLET, 5, glint=True)
    return c


@texture("block/jammer_tower_top")
def jammer_emitter_on(rng):
    return jammer_emitter(True)


@texture("block/jammer_tower_top_off")
def jammer_emitter_off(rng):
    return jammer_emitter(False)


# ---------------------------------------------------------------- 战场分析基地

def mil_casing(rng: random.Random) -> Canvas:
    """设备机壳换成军用的橄榄灰。"""
    c = casing(rng)
    for x, y in c.cells():
        c.recolor(x, y, GUN)
    return c


def radar_face(rng: random.Random, on: bool) -> Canvas:
    c = mil_casing(rng)
    status_lights(c, on)
    # 雷达屏：凹进去的深色屏幕，两圈距离环，运转时一条扫描线扫过，后面拖着渐暗的余辉，上面几个目标点
    c.recess(3, 4, 12, 12, GUN, 4)
    cx, cy = 7.5, 8.0
    for x, y in c.cells(4, 5, 11, 11):
        d = math.hypot(x - cx, y - cy)
        ring = abs(d - 1.8) < 0.5 or abs(d - 3.4) < 0.5
        if on:
            c.set(x, y, GREEN_GLOW if ring else SCREEN, 2 if ring else 1)
        else:
            c.set(x, y, SCREEN, 1 if ring else 0)
    if on:
        for x, y in c.cells(4, 5, 11, 11):
            a = math.atan2(y - cy, x - cx)
            d = math.hypot(x - cx, y - cy)
            # 扫描线朝右上，余辉往逆时针方向拖
            da = (a - (-0.8)) % (2 * math.pi)
            if d < 3.9 and da < 0.35:
                c.set(x, y, GREEN_GLOW, 5)
            elif d < 3.9 and da < 1.1:
                c.set(x, y, GREEN_GLOW, 3)
        c.set(9, 6, AMBER_GLOW, 5, glint=True)
        c.set(5, 10, AMBER_GLOW, 4, glint=True)
        c.set(7, 8, GREEN_GLOW, 5, glint=True)
    else:
        c.set(10, 6, GLASS, 2, glint=True)
    hazard_band(c, 13, 13, 3, 12)
    bolts(c, 1)
    return c


@texture("block/battlefield_analysis_base_front", refs=("block/observer_front", "block/blast_furnace_front_on"))
def ba_front(rng):
    return radar_face(rng, False)


@texture("block/battlefield_analysis_base_front_on", glow=True)
def ba_front_on(rng):
    return radar_face(rng, True)


@texture("block/battlefield_analysis_base_side")
def ba_side(rng):
    # 侧面：一块检修板，上面一排通风百叶，下面喷着白色的编号和一盏琥珀色的小灯
    c = mil_casing(rng)
    c.panel(3, 3, 12, 12, GUN, 4)
    c.vent(4, 4, 11, 7, GUN, 4)
    c.rect(4, 9, 6, 10, WHITE, 5)
    c.set(5, 9, GUN, 4)
    c.rect(8, 9, 8, 10, WHITE, 5)
    c.set(10, 9, WHITE, 5)
    c.set(10, 10, WHITE, 4)
    c.set(11, 11, AMBER, 4, glint=True)
    bolts(c, 3)
    return c


def pad(rng: random.Random, on: bool) -> Canvas:
    """顶面是无人机的停机坪：深色的坪面，一圈琥珀色的圆环，中间白色的"H"，四角的引导灯。"""
    c = mil_casing(rng)
    c.recess(2, 2, 13, 13, GRAPHITE, 3)
    for x, y in c.cells(3, 3, 12, 12):
        d = math.hypot(x - 7.5, y - 7.5)
        if 3.6 < d < 4.7:
            c.set(x, y, AMBER, 4 if x + y < 15 else 3)
    for y in range(6, 10):
        c.set(6, y, WHITE, 5)
        c.set(9, y, WHITE, 4)
    c.set(7, 8, WHITE, 5)
    c.set(8, 8, WHITE, 4)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        if on:
            c.set(x, y, AMBER_GLOW, 5, glint=True)
        else:
            c.set(x, y, AMBER, 1, glint=True)
    return c


@texture("block/battlefield_analysis_base_top", refs=("block/smithing_table_top",))
def ba_top(rng):
    return pad(rng, False)


@texture("block/battlefield_analysis_base_top_on", glow=True)
def ba_top_on(rng):
    return pad(rng, True)


@texture("block/facility_ghost")
def facility_ghost(rng):
    # 残影的碎屑粒子（方块模型只给粒子用）：蓝色的全息格子，扫描线一行亮一行暗
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, BLUE_GLOW, 2 if y % 2 else 3)
    for i in range(0, 16, 4):
        c.hline(0, 15, i, BLUE_GLOW, 4)
        c.vline(i, 0, 15, BLUE_GLOW, 4)
    for x, y in ((0, 0), (4, 8), (8, 4), (12, 12), (8, 12), (12, 0)):
        c.set(x, y, BLUE_GLOW, 5, glint=True)
    c.clusters(rng, 0, 0, 15, 15, -1, 8, 2)
    return c


# ---------------------------------------------------------------- 弹药：弹箱

def ammo_box(rng: random.Random, body, tip, base: int = 3) -> Canvas:
    """敞开的弹箱，斜着从上往下看：箱口露出一排四发子弹的弹头和一截黄铜弹壳，前面是箱体，
    箱体上一道凹下去的提手、两边的模板喷字，四角铆钉。"""
    c = Canvas()
    # 箱口里的暗处
    c.rect(2, 3, 13, 5, body, 0)
    # 四发子弹：两个像素宽，左边受光；弹头圆顶，下面一截黄铜
    for x in (2, 5, 8, 11):
        c.draw(["42", "53", "32", "fd", "ec"], {"0123456": tip, "abcdef": BRASS}, x, 1)
    c.set(2, 2, tip, tip.top, glint=True)
    # 箱体：上沿最亮，左边受光，右边、下沿背光
    b = base
    c.hline(1, 14, 6, body, b + 2)
    c.rect(1, 7, 14, 13, body, b)
    c.vline(1, 7, 13, body, b + 1)
    c.vline(14, 7, 13, body, b - 1)
    c.hline(2, 14, 13, body, b - 2)
    c.set(1, 13, body, b - 1)
    c.set(14, 6, body, b + 1)
    # 两道压出来的加强筋
    c.hline(2, 13, 8, body, b + 1)
    c.hline(2, 13, 12, body, b - 1)
    # 提手：凹下去的横槽，下沿受光
    c.hline(6, 9, 9, body, b - 2)
    c.hline(6, 9, 10, body, b + 1)
    # 两边的模板喷字
    c.hline(3, 4, 10, HAZARD, 4)
    c.hline(11, 12, 10, HAZARD, 3)
    c.rivet(2, 7, body, b)
    c.rivet(12, 7, body, b)
    c.outline(outside=True)
    return c


@texture("item/magnum_ammo_box", refs=("item/bundle", "item/arrow"))
def magnum_ammo_box(rng):
    # 机枪弹箱：橄榄色铁皮箱，铜被甲弹头
    return ammo_box(rng, OLIVE, COPPER)


@texture("item/titanium_ammo_box")
def titanium_ammo_box(rng):
    # 钛化弹箱：钢蓝色的箱子，银色弹头
    return ammo_box(rng, TITANIUM, IRON, base=2)


@texture("item/superalloy_ammo_box")
def superalloy_ammo_box(rng):
    # 超合金弹箱：深紫灰的箱子，金色弹头
    return ammo_box(rng, PLUM, GOLD_FOIL)


# ---------------------------------------------------------------- 弹药：炮弹组

# 一发炮弹，三个像素宽：引信、弹头（圆柱往上收尖）、铜弹带、黄铜弹壳、底缘。
# 数字是弹头色阶的级数，a–f 是黄铜，p–t 是铜，B 是弹头上的色带（替换成别的色阶）
SHELL = [
    ".4.",
    "542",
    "542",
    "542",
    "BBB",
    "531",
    "rqp",
    "eca",
    "eca",
    "dca",
    "dca",
    "cba",
]


def shell_set(rng: random.Random, head, band=None, crystal: bool = False) -> Canvas:
    """三发并排立着的炮弹，中间那发高一行。"""
    c = Canvas()
    for x0, y0 in ((2, 3), (6, 2), (10, 3)):
        rows = []
        for r in SHELL:
            if r == "BBB":
                r = "542" if band is None else "DCA"
            rows.append(r)
        legend = {"012345": head, "abcdef": BRASS, "pqrst": COPPER}
        if band is not None:
            legend["ABCD"] = band
        c.draw(rows, legend, x0, y0)
        if crystal:
            c.set(x0 + 1, y0, VIOLET_GLOW, 5, glint=True)
            c.set(x0, y0 + 1, VIOLET_GLOW, 4, glint=True)
            c.set(x0, y0 + 2, VIOLET_GLOW, 3, glint=True)
        else:
            c.set(x0, y0 + 1, head, head.top, glint=True)
    c.outline(outside=True)
    return c


@texture("item/shell_set", refs=("item/firework_rocket",))
def shell_set_plain(rng):
    # 普通炮弹：灰钢弹头
    return shell_set(rng, STEEL)


@texture("item/high_explosive_shell_set")
def shell_set_he(rng):
    # 高爆炮弹：橄榄色弹头，一道红环
    return shell_set(rng, OLIVE, RED)


@texture("item/crystal_shell_set")
def shell_set_crystal(rng):
    # 晶石炮弹：紫色晶体的弹头，尖上发光
    return shell_set(rng, VIOLET, crystal=True)


# ---------------------------------------------------------------- 弹药：导弹组

def missile(c: Canvas, tx: int, ty: int, n: int, body, tip, fin, slim: bool = False, glow_tip=None):
    """一枚从左下往右上斜放的导弹，尾部在 (tx, ty)，弹体 n 格长。

    弹体是三条斜线并排：左上的一条受光，中间一条，右下的一条背光（细长的导弹只有两条）。
    最前面两格是弹头，尾部两格两侧伸出尾翼，再往后一格是喷口。"""
    for i in range(n):
        x, y = tx + i, ty - i
        nose = i >= n - 2
        r = (glow_tip or tip) if nose else body
        if glow_tip and nose:
            levels = (5, 4, 3)
        else:
            levels = (5, 4, 2)
        if i == n - 1:
            c.set(x, y, r, levels[0], glint=True)
            continue
        c.set(x, y - 1, r, levels[0], glint=True)
        c.set(x, y, r, levels[1], glint=True)
        if not slim:
            c.set(x + 1, y, r, levels[2], glint=True)
    for i in (0, 1):
        x, y = tx + i, ty - i
        c.set(x - 1, y - 1, fin, 4, glint=True)
        c.set(x + (1 if slim else 2), y + 1, fin, 2, glint=True)
    c.set(tx - 1, ty, fin, 3, glint=True)
    c.set(tx - 1, ty + 1, GRAPHITE, 2, glint=True)


def missile_set(rng, body, tip, fin, slim=False, glow_tip=None) -> Canvas:
    c = Canvas()
    missile(c, 2, 11, 10, body, tip, fin, slim, glow_tip)
    missile(c, 5, 14, 9, body, tip, fin, slim, glow_tip)
    c.outline(outside=True)
    return c


@texture("item/missile_set", refs=("item/firework_rocket",))
def missile_set_plain(rng):
    # 普通导弹：白色弹体，红色弹头，灰色尾翼
    return missile_set(rng, WHITE, RED, STEEL)


@texture("item/supersonic_missile_set")
def missile_set_supersonic(rng):
    # 超音速导弹：更细长，橙色的尾翼
    return missile_set(rng, WHITE, RED, ORANGE, slim=True)


@texture("item/gravity_missile_set")
def missile_set_gravity(rng):
    # 引力导弹：黑色弹体，弹头发紫光
    return missile_set(rng, GRAPHITE, VIOLET, PLUM, glow_tip=VIOLET_GLOW)


# ---------------------------------------------------------------- 胶囊

def capsule(rng, core, cap, band=None) -> Canvas:
    """一颗从左下往右上斜放的胶囊：两头金属帽，中间玻璃管里一团发光的等离子体。"""
    c = Canvas()
    r = 1 / math.sqrt(2)
    for x, y in c.cells():
        px, py = x + 0.5 - 8, y + 0.5 - 8
        t = (px - py) * r
        s = -(px + py) * r
        if abs(t) > 6.4 or abs(s) > 2.6:
            continue
        if abs(t) > 5.7 and abs(s) > 1.5:
            continue
        side = 5 if s > 1.0 else 2 if s < -1.0 else 4
        if abs(t) > 4.0:
            if band is not None and abs(t) < 4.8:
                c.set(x, y, band, side, glint=True)
            else:
                c.set(x, y, cap, side, glint=True)
        else:
            k = max(0.0, 1 - abs(s) / 2.4) * (1 - abs(t) / 5.5)
            lv = 5 if k > 0.55 else 4 if k > 0.32 else 3 if k > 0.15 else 2
            c.set(x, y, core, lv, glint=True)
            if s > 1.6 and abs(t) < 3.0:
                c.set(x, y, GLASS, 5, glint=True)
    c.outline(outside=True)
    return c


@texture("item/plasma_capsule", refs=("item/experience_bottle", "item/potion"))
def plasma_capsule(rng):
    return capsule(rng, CYAN_GLOW, STEEL)


@texture("item/antimatter_capsule")
def antimatter_capsule(rng):
    return capsule(rng, MAGENTA_GLOW, GRAPHITE, RED)


@texture("item/jamming_capsule")
def jamming_capsule(rng):
    return capsule(rng, VIOLET_GLOW, IRON)


# ---------------------------------------------------------------- 投掷的爆破单元

def charge_box(c: Canvas, body, y0: int):
    """斜着看的扁盒子：顶面亮，正面一道红黑警示条，两道黑色捆带，下沿背光。"""
    c.rect(2, y0, 13, y0 + 2, body, 5)
    c.hline(2, 13, y0, body, 6)
    c.set(13, y0 + 1, body, 4)
    c.set(13, y0 + 2, body, 4)
    c.rect(1, y0 + 3, 14, y0 + 8, body, 3)
    c.hline(1, 14, y0 + 3, body, 5)
    c.vline(1, y0 + 4, y0 + 8, body, 4)
    c.vline(14, y0 + 4, y0 + 8, body, 2)
    c.hline(2, 14, y0 + 8, body, 1)
    for x in range(1, 15):
        red = (x // 2) % 2 == 0
        c.set(x, y0 + 5, RED if red else GRAPHITE, 4 if red else 1)
        c.set(x, y0 + 6, RED if red else GRAPHITE, 3 if red else 1)
    for x in (4, 11):
        c.vline(x, y0, y0 + 2, GRAPHITE, 2)
        c.vline(x, y0 + 3, y0 + 8, GRAPHITE, 1)
        c.set(x, y0 + 3, GRAPHITE, 3)


@texture("item/explosive_unit", refs=("item/tnt_minecart", "block/tnt_side"))
def explosive_unit(rng):
    # 爆破单元：灰色的扁盒子，红黑警示条，顶上一个雷管，琥珀色的引信灯亮着，一根红色的引线
    c = Canvas()
    charge_box(c, STEEL, 5)
    c.rect(6, 3, 9, 5, GRAPHITE, 2)
    c.hline(6, 9, 3, GRAPHITE, 4)
    c.vline(6, 4, 5, GRAPHITE, 3)
    c.set(7, 4, AMBER_GLOW, 5, glint=True)
    c.set(8, 4, AMBER_GLOW, 3, glint=True)
    c.set(10, 4, RED, 4)
    c.set(11, 3, RED, 3)
    c.set(12, 3, RED, 2)
    c.outline(outside=True)
    return c


@texture("item/crystal_explosive_unit", refs=("item/amethyst_shard",))
def crystal_explosive_unit(rng):
    # 晶石爆破单元：同样的盒子，顶上嵌着一块发光的青色晶石，比盒子还高
    c = Canvas()
    charge_box(c, STEEL, 6)
    # 晶石：左边的切面受光、透着青光，右边的切面是暗的青绿
    c.draw([
        "..4..",
        ".4F3.",
        ".FE3.",
        "4FE32",
        "4FD32",
        "4ED32",
        ".4D2.",
        "..3..",
    ], {"0123456": TEAL, "ABCDEF": CYAN_GLOW}, 5, 0)
    c.set(6, 2, WHITE, 6, glint=True)
    c.outline(outside=True)
    return c


# ---------------------------------------------------------------- 实体：炮弹和导弹

def cube_faces(u: int, v: int, w: int, h: int, d: int) -> dict:
    """实体方块盒子的贴图展开（原版的布局）：每个面 (x, y, 宽, 高)。"""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def ring_at(c: Canvas, faces: dict, f: int, d: int, r, lv: int):
    """在离前端第 f 格的地方绕盒子画一圈（原版的展开里，顶面、底面第一行是后端，
    右面（东）第一列是前端，左面（西）第一列是后端）。"""
    for key in ("top", "bottom"):
        x0, y0, w, h = faces[key]
        c.hline(x0, x0 + w - 1, y0 + d - 1 - f, r, lv)
    x0, y0, w, h = faces["right"]
    c.vline(x0 + f, y0, y0 + h - 1, r, lv)
    x0, y0, w, h = faces["left"]
    c.vline(x0 + d - 1 - f, y0, y0 + h - 1, r, lv - 1)


def paint_box(c: Canvas, faces: dict, r, base: int):
    """每个面一块面板：上、左边亮一级，下、右边暗一级；顶面最亮，底面最暗。"""
    tone = {"top": 1, "bottom": -2, "right": -1, "left": 0, "front": 0, "back": -1}
    for key, (x0, y0, w, h) in faces.items():
        lv = base + tone[key]
        c.rect(x0, y0, x0 + w - 1, y0 + h - 1, r, lv)
        if w > 2 and h > 2:
            c.bevel(x0, y0, x0 + w - 1, y0 + h - 1)


@texture("entity/turret_shell", glow=True)
def turret_shell_entity(rng):
    """炮弹 32×32：弹体 (0,0) 3×3×4 黄铜色、靠后一道铜弹带，弹头 (0,8) 2×2×1 灰钢，
    尾部的曳光引信 (0,12) 2×2×1 发橙光（发光层）。"""
    c = Canvas(32, 32)
    body = cube_faces(0, 0, 3, 3, 4)
    paint_box(c, body, BRASS, 4)
    ring_at(c, body, 2, 4, COPPER, 3)
    head = cube_faces(0, 8, 2, 2, 1)
    paint_box(c, head, STEEL, 5)
    x0, y0, w, h = head["front"]
    c.set(x0, y0, STEEL, 7, glint=True)
    for key, (x0, y0, w, h) in cube_faces(0, 12, 2, 2, 1).items():
        c.rect(x0, y0, x0 + w - 1, y0 + h - 1, ORANGE_GLOW, 4 if key == "back" else 3)
    return c


@texture("entity/turret_missile", glow=True)
def turret_missile_entity(rng):
    """导弹 32×32：弹体 (0,0) 2×2×10 白色，前面一道红环、尾翼前面一道黑环，弹头 (0,14) 1×1×2 红色，
    喷口 (8,14) 1×1×1 后面发橙光，尾翼 (0,18) 5×1×2、(14,18) 1×5×2 灰色。"""
    c = Canvas(32, 32)
    body = cube_faces(0, 0, 2, 2, 10)
    paint_box(c, body, WHITE, 4)
    ring_at(c, body, 1, 10, RED, 4)
    ring_at(c, body, 7, 10, GRAPHITE, 2)
    paint_box(c, cube_faces(0, 14, 1, 1, 2), RED, 4)
    nozzle = cube_faces(8, 14, 1, 1, 1)
    paint_box(c, nozzle, GRAPHITE, 2)
    x0, y0, w, h = nozzle["back"]
    c.set(x0, y0, ORANGE_GLOW, 5)
    paint_box(c, cube_faces(0, 18, 5, 1, 2), STEEL, 4)
    paint_box(c, cube_faces(14, 18, 1, 5, 2), STEEL, 4)
    return c
