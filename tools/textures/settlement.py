"""聚落（ADR-0034 §二）：广场和地基前的聚落终端、炮台建筑顶上的那门炮（ADR-0031）。

聚落终端是民用的：比设施终端暖，钢灰的机身包一圈黄铜边，屏幕是青色的聚落平面图（中间广场，周围八块地基，
有一块选中）；侧面是散热格和一条青色的指示灯；顶上一个黄铜的标志，八个点围着一个方块，就是聚落的样子。
炮台是戍卫用的钢板：一块块的装甲板，板缝深、铆钉亮；正面一只红色的传感眼；炮管是石墨色的圆管，炮口黑。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from palette import BRASS, CYAN_GLOW, GRAPHITE, RED_GLOW, SCREEN, STEEL, TEAL
from recipe import texture


def casing(rng: random.Random) -> Canvas:
    """机身：钢灰的板，外圈一道黄铜边（左上亮、右下暗），四角铜铆钉，板面一点成团的明暗和磨痕。"""
    c = Canvas()
    c.fill(STEEL, 3)
    c.clusters(rng, 2, 2, 13, 13, -1, 5, 2)
    c.hline(0, 15, 0, BRASS, 4)
    c.vline(0, 1, 15, BRASS, 4)
    c.hline(1, 15, 15, BRASS, 1)
    c.vline(15, 1, 14, BRASS, 2)
    c.hline(1, 14, 1, STEEL, 4)
    c.vline(1, 2, 14, STEEL, 4)
    c.hline(2, 14, 14, STEEL, 1)
    c.vline(14, 2, 13, STEEL, 2)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.rivet(x, y, BRASS, 4)
    c.edge_wear(rng, 1, 1, 14, 14, 0.12)
    c.grime(rng, 2, 11, 13, 14, 0.10)
    return c


@texture("block/settlement_terminal_front", refs=("block/crafter_north", "block/lectern_front"))
def settlement_terminal_front(rng):
    """屏幕是聚落的平面图：深色底，中间的广场，四周八块地基，右上那块选中、亮着；下面一排键，右边的亮青。"""
    c = casing(random.Random(3401))
    # 屏幕：凹进去，上沿一道深影，下沿受光
    c.rect(3, 3, 12, 10, SCREEN, 1)
    c.hline(3, 12, 3, SCREEN, 0)
    c.hline(3, 12, 11, STEEL, 5)
    c.vline(2, 3, 11, STEEL, 1)
    c.vline(13, 3, 11, STEEL, 5)
    # 环路
    c.frame(4, 4, 11, 10, TEAL, 1)
    # 广场
    c.rect(7, 6, 8, 8, CYAN_GLOW, 2)
    c.set(7, 6, CYAN_GLOW, 5, glint=True)
    # 地基：四角和四边的中点
    plots = [(4, 4), (11, 4), (4, 10), (11, 10), (4, 7), (11, 7), (7, 4), (8, 10)]
    for i, (x, y) in enumerate(plots):
        chosen = i == 1
        c.set(x, y, CYAN_GLOW, 5 if chosen else 2, glint=True)
    c.set(10, 4, CYAN_GLOW, 2, glint=True)
    c.set(11, 5, CYAN_GLOW, 2, glint=True)
    # 键
    for x in (4, 6, 8):
        c.set(x, 12, STEEL, 5, glint=True)
        c.set(x, 13, STEEL, 1)
    c.set(10, 12, CYAN_GLOW, 5, glint=True)
    c.set(11, 12, CYAN_GLOW, 2, glint=True)
    c.set(10, 13, CYAN_GLOW, 2, glint=True)
    c.set(11, 13, CYAN_GLOW, 2, glint=True)
    return c


@texture("block/settlement_terminal_side")
def settlement_terminal_side(rng):
    """侧面：一片散热格，靠后一条竖的青色指示灯，隔几格亮一点。"""
    c = casing(random.Random(3402))
    c.vent(3, 3, 11, 12, STEEL, 2)
    for y in range(3, 13):
        c.set(13, y, CYAN_GLOW, 4 if y % 4 == 1 else 2, glint=True)
    c.vline(12, 3, 12, STEEL, 1)
    return c


@texture("block/settlement_terminal_top")
def settlement_terminal_top(rng):
    """顶上的标志：八个黄铜的点围着一个方块，左上受光；外面一圈细的凹槽。"""
    c = casing(random.Random(3403))
    cx = cy = 7.5
    for x, y in c.cells(3, 3, 12, 12):
        d = math.hypot(x - cx, y - cy)
        if 4.2 <= d <= 4.9:
            c.set(x, y, STEEL, 1)
    c.panel(6, 6, 9, 9, BRASS, 3)
    for k in range(8):
        a = k * math.pi / 4
        x = round(cx + 3.2 * math.cos(a) - 0.01)
        y = round(cy + 3.2 * math.sin(a) - 0.01)
        lit = (x - cx) + (y - cy) < 0
        c.set(x, y, BRASS, 5 if lit else 3)
    return c


def armor(rng: random.Random) -> Canvas:
    """装甲板：横竖两道深的板缝把一块分成四块，每块左上亮、右下暗，缝边上铆钉，边上一点磨亮和锈迹似的暗斑。"""
    c = Canvas()
    c.fill(STEEL, 3)
    for x0, y0, x1, y1 in ((0, 0, 6, 6), (8, 0, 15, 6), (0, 8, 6, 15), (8, 8, 15, 15)):
        c.panel(x0, y0, x1, y1, STEEL, 3)
    c.hline(0, 15, 7, GRAPHITE, 1)
    c.vline(7, 0, 15, GRAPHITE, 1)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12), (5, 10), (10, 5)):
        c.rivet(x, y, STEEL, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 4, 2)
    c.edge_wear(rng, 0, 0, 15, 15, 0.10)
    c.grime(rng, 0, 10, 15, 15, 0.08)
    return c


@texture("block/settlement_turret", refs=("block/iron_block", "block/heavy_core"))
def settlement_turret(rng):
    return armor(random.Random(3411))


@texture("block/settlement_turret_front")
def settlement_turret_front(rng):
    """炮塔正面：装甲板中间一道黑的观察缝，缝里一只红色的传感眼，眼的左上一点亮。"""
    c = armor(random.Random(3412))
    c.recess(3, 5, 12, 9, GRAPHITE, 1)
    c.hline(3, 12, 10, STEEL, 5)
    c.disc(7.5, 7, 1.6, RED_GLOW, 3)
    c.set(7, 6, RED_GLOW, 5, glint=True)
    c.set(8, 7, RED_GLOW, 4, glint=True)
    return c


@texture("block/settlement_turret_barrel")
def settlement_turret_barrel(rng):
    """炮管：左上 2×2 是炮口（黑洞一圈亮边）；4–5 行是管身的侧面，横着上亮下暗；8–14 行的两列是管身的顶和底。"""
    c = Canvas()
    c.fill(GRAPHITE, 2)
    # 炮口
    c.set(0, 0, GRAPHITE, 4)
    c.set(1, 0, GRAPHITE, 3)
    c.set(0, 1, GRAPHITE, 3)
    c.set(1, 1, GRAPHITE, 0)
    # 侧面：7 格长，前端一道散热环
    for x in range(0, 7):
        ring = x in (1, 4)
        c.set(x, 4, STEEL if ring else GRAPHITE, 5 if ring else 4)
        c.set(x, 5, STEEL if ring else GRAPHITE, 2 if ring else 1)
    # 顶和底：竖的 2 格宽，左亮右暗
    for y in range(8, 15):
        ring = y in (9, 12)
        c.set(0, y, STEEL if ring else GRAPHITE, 5 if ring else 4)
        c.set(1, y, STEEL if ring else GRAPHITE, 3 if ring else 2)
    # 其余的地方不显示，画成和管身一样的深色，免得取色时跳
    for x, y in c.cells(3, 0, 15, 15):
        if not (y in (4, 5) and x < 7):
            c.set(x, y, GRAPHITE, 2)
    return c
