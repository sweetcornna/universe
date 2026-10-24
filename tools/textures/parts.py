"""几组贴图共用的部件（ADR-0031 §三）：机壳、螺栓、状态灯、风扇。

各组的配方模块从这里拿，保证设备之间的机壳、灯、等级色是一个样子。只有一组用到的部件写在那组的模块里。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from palette import AMBER, BLUE, CYAN_GLOW, GRAPHITE, GREEN, SCREEN, STEEL

# 等级色：Mk.I 琥珀、Mk.II 绿、Mk.III 蓝
TIER = {1: AMBER, 2: GREEN, 3: BLUE}


def casing(rng: random.Random, base: int = 4, edge: bool = True) -> Canvas:
    """机壳的一面：凸起的钢框（最外一圈暗、里面一圈上左亮下右暗），框里的面板稍微凹进去，
    拉丝的纹理成团出现，框边偶尔掉漆，面板下部积一点灰。"""
    c = Canvas()
    c.fill(STEEL, base)
    # 面板受光：左上亮一级、右下暗一级，交界处错开几个像素，不拉出一条直线
    for x, y in c.cells(2, 2, 13, 13):
        k = x + y + ((x * 7 + y * 3) % 3) - 1
        if k < 9:
            c.shift(x, y, 1)
        elif k > 19:
            c.shift(x, y, -1)
    # 拉丝：横向的细长纹理
    for _ in range(10):
        x, y = rng.randint(2, 11), rng.randint(3, 12)
        n = rng.randint(2, 4)
        d = rng.choice((-1, -1, 1))
        for i in range(n):
            c.shift(x + i, y, d)
    if edge:
        c.frame(0, 0, 15, 15, STEEL, 2)
        c.rect(1, 1, 14, 1, STEEL, base + 1)
        c.rect(1, 1, 1, 14, STEEL, base + 1)
        c.rect(2, 14, 14, 14, STEEL, base - 1)
        c.rect(14, 2, 14, 14, STEEL, base - 1)
        # 面板凹进去：框的内侧上、左一圈影子
        c.hline(2, 13, 2, STEEL, base - 1)
        c.vline(2, 3, 13, STEEL, base - 1)
        c.edge_wear(rng, 1, 1, 14, 14, 0.22)
    c.grime(rng, 3, 9, 13, 13, 0.05)
    return c


def bolts(c: Canvas, inset: int = 2, base: int = 4):
    """四个角上的螺栓。"""
    for x, y in ((inset, inset), (15 - inset - 1, inset), (inset, 15 - inset - 1), (15 - inset - 1, 15 - inset - 1)):
        c.rivet(x, y, STEEL, base)


def status_lights(c: Canvas, on: bool, y: int = 2, xs=(6, 8, 10)):
    """一条状态灯：嵌在深色的槽里，运转时青色发光。"""
    c.rect(xs[0] - 1, y, xs[-1] + 1, y, GRAPHITE, 1)
    for x in xs:
        if on:
            c.set(x, y, CYAN_GLOW, 5, glint=True)
        else:
            c.set(x, y, SCREEN, 2, glint=True)


def fan(c: Canvas, cx: float, cy: float, rad: float, blades: int = 4):
    """凹进去的风扇：外圈是凹槽（上左暗、下右亮），扇叶顺时针扫，前缘受光，叶片之间是暗的缝，中间轴帽。"""
    for x, y in c.disc_cells(cx, cy, rad):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        if r > rad - 0.6:
            c.set(x, y, STEEL, 2 if dx + dy < 0 else 5)
            continue
        a = (math.atan2(dy, dx) + r * 0.32) % (2 * math.pi / blades)
        f = a / (2 * math.pi / blades)
        if f < 0.62:
            c.set(x, y, STEEL, 5 if f < 0.18 else (4 if f < 0.42 else 3))
        else:
            c.set(x, y, GRAPHITE, 1)
    c.shade_disc(cx, cy, 1.3, STEEL, 4)
