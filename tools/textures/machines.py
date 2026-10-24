"""工业设备的贴图（ADR-0031）。

设备的样子：冷灰的钢机壳，最外一圈深色的边让相邻的方块分得开，里面一圈倒角；正面是功能区
（观察窗、屏幕、炉口），上面一条状态灯，运转时亮青色。Mk.I/II/III 用琥珀、绿、蓝做等级色。
"""
from __future__ import annotations

import random

from canvas import Canvas
from palette import BRASS, CYAN_GLOW, GLASS, GRAPHITE, STEEL
from parts import TIER, bolts, casing, fan, status_lights
from recipe import texture


# 观察窗里的机械臂（8×7，窗内左上角起）：深色的立柱、横梁和夹爪，钢的关节；亮底上的暗剪影最好认。
# 数字是石墨色的级数，S–Z 是钢，p–u 是青色光。
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


def assembler_face(rng: random.Random, on: bool, tier: int = 1) -> Canvas:
    c = casing(rng)
    accent = TIER[tier]
    status_lights(c, on)
    # 观察窗：凹进去。停机时里面是暗淡的玻璃色；运转时工作灯把里面照成一片青色，越往下越亮
    c.recess(3, 4, 12, 12, STEEL, 4)
    if on:
        c.rect(4, 5, 11, 6, CYAN_GLOW, 2)
        c.rect(4, 7, 11, 9, CYAN_GLOW, 3)
        c.rect(4, 10, 11, 11, CYAN_GLOW, 4)
    else:
        c.rect(4, 5, 11, 7, GLASS, 0)
        c.rect(4, 8, 11, 11, GLASS, 1)
    c.draw(ARM_ON if on else ARM_OFF,
           {"0123456": GRAPHITE, "abcdefg": BRASS, "pqrstu": CYAN_GLOW, "STUVWXYZ": STEEL}, 4, 5)
    # 玻璃上一道斜的反光
    if not on:
        c.set(10, 5, GLASS, 3)
        c.set(11, 6, GLASS, 2)
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


@texture("block/assembler_side", refs=("block/crafter_south",))
def assembler_side(rng):
    c = casing(rng)
    # 侧板：一块凸起的检修板，上面一组百叶通风口，下面两根管线
    c.panel(3, 3, 12, 12, STEEL, 4)
    c.vent(5, 5, 10, 8, STEEL, 4)
    c.cylinder(3, 10, 12, 11, BRASS, 3, vertical=False)
    c.vline(7, 10, 11, GRAPHITE, 2)
    bolts(c, 3)
    return c


@texture("block/assembler_top", refs=("block/crafter_top",))
def assembler_top(rng):
    c = casing(rng)
    fan(c, 7.5, 7.5, 5.0)
    bolts(c, 1)
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
