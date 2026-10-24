"""中间产品和材料的物品贴图（ADR-0031）。

物品照原版的画法：光从左上来，四到六级明暗，最外一圈用材质自己最深的颜色描边（不用纯黑），
受光的一侧描边浅一级；金属有一两个高光点，晶体有锐利的切面。小东西一个像素一个像素地画（Canvas.draw）。
"""
from __future__ import annotations

import math

from canvas import Canvas
from palette import BRASS, COPPER, CYAN_GLOW, GLASS, GRAPHITE, GREEN, STEEL, TEAL, TITANIUM
from recipe import texture


@texture("item/titanium_ingot", refs=("item/iron_ingot", "item/netherite_ingot", "item/gold_ingot"))
def titanium_ingot(rng):
    c = Canvas()
    # 锭：顶面最亮、有一道高光，正面往下渐暗，左端受光，右端背光
    c.draw([
        "................",
        "................",
        "................",
        "................",
        "....1111111111..",
        "...156666655541.",
        "..15666665554440",
        ".155555554444320",
        ".143333333333210",
        ".143333333332210",
        ".132222222222110",
        "..000000000000..",
    ], {"0123456": TITANIUM}, 0, 1)
    return c


@texture("item/gear", refs=("item/clock_00", "item/compass_00"))
def gear(rng):
    c = Canvas()
    # 齿轮：平的金属面，八个齿；外沿左上亮、右下暗，轮毂凹下去一圈，中间是轴孔
    cx = cy = 7.5
    mask = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx) + math.pi / 8
            if r <= 5.6 or (math.cos(a * 8) > -0.15 and r <= 7.4):
                mask.add((x, y))
    for x, y in mask:
        c.set(x, y, STEEL, 5)
    for x, y in mask:
        lit = (x - 1, y) not in mask or (x, y - 1) not in mask
        dark = (x + 1, y) not in mask or (x, y + 1) not in mask
        if lit and not dark:
            c.set(x, y, STEEL, 7)
        elif dark:
            c.set(x, y, STEEL, 3)
    # 轮毂：一圈凹槽，上左是影子，下右受光
    for x, y in c.disc_cells(cx, cy, 3.2):
        r = math.hypot(x - cx, y - cy)
        if r > 2.2:
            c.set(x, y, STEEL, 3 if x + y < 15 else 6)
        else:
            c.set(x, y, STEEL, 5)
    for x, y in c.disc_cells(cx, cy, 0.9):
        c.clear(x, y)
    c.set(7, 6, STEEL, 2)
    c.set(6, 7, STEEL, 2)
    c.outline()
    c.set(4, 3, STEEL, 7, glint=True)
    return c


@texture("item/circuit_board", refs=("item/redstone",))
def circuit_board(rng):
    c = Canvas()
    # 电路板：绿色基板有厚度（下边、右边一条暗色），铜走线，中间一块带引脚的芯片，
    # 右上一颗电容，左上一个安装孔，下边一排金手指
    c.rect(1, 2, 14, 13, GREEN, 3)
    c.rect(2, 3, 13, 11, GREEN, 4)
    c.hline(2, 13, 12, GREEN, 2)                        # 板子的厚度
    c.vline(14, 3, 13, GREEN, 2)
    # 走线
    for y in (4, 10):
        c.hline(3, 12, y, COPPER, 3)
    c.vline(3, 4, 10, COPPER, 3)
    c.hline(10, 12, 6, COPPER, 3)
    c.hline(10, 12, 8, COPPER, 3)
    c.hline(3, 5, 7, COPPER, 3)
    for x, y in ((3, 4), (12, 4), (3, 10), (12, 10), (12, 6), (12, 8)):
        c.set(x, y, COPPER, 5, glint=True)              # 焊盘
    # 芯片
    c.rect(6, 5, 9, 9, GRAPHITE, 2)
    c.hline(6, 9, 5, GRAPHITE, 4)
    c.vline(6, 6, 9, GRAPHITE, 3)
    c.hline(7, 9, 9, GRAPHITE, 1)
    c.set(7, 6, GRAPHITE, 5, glint=True)
    for y in (6, 8):
        c.set(5, y, STEEL, 6, glint=True)
        c.set(10, y, STEEL, 4, glint=True)
    # 电容：小圆柱，顶面亮
    c.cylinder(11, 3, 12, 4, TEAL, 4)
    c.set(11, 3, TEAL, 6, glint=True)
    # 安装孔
    c.set(2, 3, GREEN, 1)
    c.set(3, 3, GREEN, 5)
    # 金手指
    for x in range(3, 13, 2):
        c.set(x, 11, BRASS, 5, glint=True)
        c.set(x, 12, BRASS, 3, glint=True)
    c.outline()
    return c


@texture("item/casimir_crystal", refs=("item/diamond", "item/amethyst_shard", "item/echo_shard"))
def casimir_crystal(rng):
    c = Canvas()
    # 六棱柱的晶体立着：左面受光，中间一面带一道高光，右面背光；里面透出一点青光
    c.draw([
        "................",
        ".......11.......",
        "......1560......",
        ".....155420.....",
        "....15554320....",
        "....15564320....",
        "....15564320....",
        "....15564320....",
        "....15544320....",
        "....15544320....",
        "....15544220....",
        "....15544220....",
        ".....154320.....",
        "......1420......",
        ".......00.......",
        "................",
    ], {"0123456": TEAL})
    c.set(8, 6, CYAN_GLOW, 4, glint=True)
    c.set(8, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 4, glint=True)
    c.set(5, 4, GLASS, GLASS.top, glint=True)
    return c
