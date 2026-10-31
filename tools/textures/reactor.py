"""化学反应釜的贴图（ADR-0036 §四，画法照 ADR-0031）。

一台立着的压力容器：钢机壳中间嵌着釜体，上面拱起的封头、下面一道法兰。正面一个圆视镜，工作时透出反应气的橙光
（下面热、上面暗一些）；釜体两边各一块压力表，表下面一根引压管。侧面是白漆的保温夹套、横穿的进料管和红色的阀门手轮。
顶上是封头的一圈螺栓、中间的人孔，右上角一个安全阀（方块工作时偶尔从那里泄一口白汽）。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from palette import BRASS, GLASS, GRAPHITE, ORANGE_GLOW, RED, STEEL, WHITE
from parts import bolts, casing, status_lights
from recipe import texture

# 正面：0–7 是钢的级数，g 是玻璃（不工作）或橙光（工作），G 是视镜的黑框，w 是表盘，n 是表的指针
FRONT = [
    "................",
    "................",
    "................",
    "......7765......",
    "...ww666554ww...",
    "...wn665543nw...",
    "....56GGGG33....",
    "....5GggggG3....",
    "....5GggggG3....",
    "....5GggggG3....",
    "....5GggggG3....",
    "....56GGGG33....",
    "....56655433....",
    "...5666655432...",
    "................",
    "................",
]


def reactor_face(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on, y=2, xs=(6, 8, 10))
    # 先画钢的部分，别的字符留给下面按工作状态上色
    c.draw(["".join(ch if ch.isdigit() else "." for ch in row) for row in FRONT], {"01234567": STEEL})
    for y, row in enumerate(FRONT):
        for x, ch in enumerate(row):
            if ch == "G":
                # 视镜的框：左上受光
                c.set(x, y, GRAPHITE, 3 if x + y < 14 else 1, glint=True)
            elif ch == "w":
                c.set(x, y, WHITE, 4, glint=True)
            elif ch == "n":
                c.set(x, y, RED, 4, glint=True)
            elif ch == "g":
                depth = y - 7
                if on:
                    c.set(x, y, ORANGE_GLOW, (2, 3, 4, 4)[depth], glint=True)
                else:
                    c.set(x, y, GLASS, 0 if depth >= 2 else 1, glint=True)
    if not on:
        # 玻璃上一点反光，用表盘的白，不多出颜色
        c.set(6, 7, WHITE, 4, glint=True)
    return c


@texture("block/chemical_reactor_front", refs=("block/blast_furnace_front", "block/smoker_front"))
def chemical_reactor_front(rng):
    return reactor_face(rng, False)


@texture("block/chemical_reactor_front_on", glow=True, refs=("block/blast_furnace_front_on",))
def chemical_reactor_front_on(rng):
    return reactor_face(rng, True)


@texture("block/chemical_reactor_side")
def chemical_reactor_side(rng):
    c = casing(rng)
    # 保温夹套：白漆的板，竖着两道箍
    c.panel(3, 3, 12, 12, WHITE, 3)
    for x in (6, 10):
        c.vline(x, 4, 11, WHITE, 2)
        c.set(x, 3, WHITE, 4)
    # 横穿的进料管，接一个红手轮的阀门
    c.cylinder(1, 7, 14, 8, STEEL, 4, vertical=False)
    for x in (3, 13):
        c.vline(x, 7, 8, STEEL, 2)
    c.ring(8.5, 7.5, 1.7, RED, 3)
    c.set(8, 6, RED, 5, glint=True)
    c.set(8, 7, STEEL, 6, glint=True)
    c.set(9, 8, STEEL, 3, glint=True)
    bolts(c)
    return c


@texture("block/chemical_reactor_top")
def chemical_reactor_top(rng):
    c = casing(rng)
    # 封头：凸起的圆盖，一圈螺栓
    cx, cy = 7.0, 8.0
    for x, y in c.disc_cells(cx, cy, 5.0):
        r = math.hypot(x - cx, y - cy)
        toward = ((x - cx) * -0.58 + (y - cy) * -0.62) / max(0.01, r)
        if r > 4.2:
            c.set(x, y, STEEL, 6 if toward > 0.3 else (2 if toward < -0.3 else 4))
        else:
            c.set(x, y, STEEL, 5 if toward > 0.5 else (3 if toward < -0.5 else 4))
    for i in range(6):
        a = i * math.pi / 3 + 0.5
        c.set(round(cx + 3.6 * math.cos(a)), round(cy + 3.6 * math.sin(a)), STEEL, 7, glint=True)
    # 人孔：中间一个小圆盖，深色的缝
    c.ring(cx, cy, 1.6, GRAPHITE, 2)
    c.set(6, 7, STEEL, 6, glint=True)
    c.set(7, 8, STEEL, 5, glint=True)
    # 安全阀：右上角一个短管，顶上黄铜的阀帽
    c.set(11, 3, BRASS, 5, glint=True)
    c.set(12, 3, BRASS, 3, glint=True)
    c.set(11, 4, BRASS, 3, glint=True)
    c.set(12, 4, BRASS, 2, glint=True)
    c.set(10, 5, STEEL, 2, glint=True)
    return c
