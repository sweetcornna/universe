"""生物反应器的贴图（ADR-0036 §五，画法照 ADR-0031）。

照实验室和中试车间的光生物反应器画：白漆的机身，正面一整块玻璃罐壁，看得见里面的菌液（藻液是墨绿的，长满了更深）、
上面一段顶空，一根搅拌轴从顶上插下来。工作时补光灯打开，菌液透出绿光，底下的气体分布器往上冒泡。
侧面是一根竖的液位计、横穿的进气管和一块温度表，背后两根夹套的冷却水管。顶上是罐的封头，正中一台搅拌电机，
右上一个排气口、左下一个取样口；工作时封头上一圈补光灯的指示灯亮。
"""
from __future__ import annotations

import random

from canvas import Canvas
from machines import WATER, lid, motor, shell
from palette import GLASS, GRAPHITE, GREEN, GREEN_GLOW, RED, STEEL, WHITE
from parts import status_lights
from recipe import texture

# 罐里往上冒的气泡，不对称地散着
BUBBLES = ((5, 10), (6, 8), (9, 9), (10, 11), (8, 7), (5, 7), (10, 6))


def bio_face(rng: random.Random, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    status_lights(c, on)
    # 罐壁的钢框：上左受光、下右背光
    c.hline(3, 12, 3, STEEL, 5)
    c.vline(3, 4, 12, STEEL, 4)
    c.vline(12, 4, 12, STEEL, 2)
    c.hline(4, 11, 12, STEEL, 2)
    for x in range(4, 12):
        for y in range(4, 12):
            if y <= 5:
                # 顶空：一点雾气的玻璃
                c.set(x, y, GLASS, 1 if on else 0)
            elif on:
                c.set(x, y, GREEN_GLOW, 4 if y == 6 else (3 if y < 10 else 2))
            else:
                c.set(x, y, GREEN, 3 if y == 6 or (y == 7 and x % 3 == 0) else 2)
    # 搅拌轴从顶上插进液面
    c.set(7, 4, STEEL, 5, glint=True)
    c.set(7, 5, STEEL, 4, glint=True)
    if on:
        for x, y in BUBBLES:
            c.set(x, y, GREEN_GLOW, 5, glint=True)
    else:
        # 玻璃上的一点反光，用白漆的白，不多出颜色
        c.set(4, 4, WHITE, 5, glint=True)
        c.set(5, 4, WHITE, 4, glint=True)
    # 罐底一道法兰，正中一个红色的放料阀
    c.hline(3, 12, 13, STEEL, 4)
    c.set(12, 13, STEEL, 2)
    c.set(7, 14, RED, 4, glint=True)
    c.set(8, 14, STEEL, 2, glint=True)
    return c


@texture("block/bioreactor_front", refs=("block/brewing_stand", "block/glass"))
def bioreactor_front(rng):
    return bio_face(rng, False)


@texture("block/bioreactor_front_on", glow=True)
def bioreactor_front_on(rng):
    return bio_face(rng, True)


@texture("block/bioreactor_side")
def bioreactor_side(rng):
    c = shell(rng, WHITE)
    # 液位计：一根竖的玻璃管，钢的上下帽，下面七成是菌液
    c.set(3, 2, STEEL, 5)
    c.set(4, 2, STEEL, 3)
    for y in range(3, 12):
        c.set(3, y, GREEN if y >= 6 else GLASS, 3 if y >= 6 else 1)
        c.set(4, y, GREEN if y >= 6 else GLASS, 2 if y >= 6 else 1)
    c.hline(3, 4, 12, STEEL, 3)
    # 温度表：白的表盘，黑框，红指针
    c.ring(9.0, 5.0, 2.2, GRAPHITE, 2)
    for x, y in c.disc_cells(9.0, 5.0, 1.5):
        c.set(x, y, WHITE, 5)
    c.set(9, 5, RED, 4, glint=True)
    c.set(10, 4, RED, 4, glint=True)
    # 进气管：横穿下半，一道箍
    c.hline(6, 14, 9, STEEL, 5)
    c.hline(6, 14, 10, STEEL, 3)
    c.vline(9, 9, 10, STEEL, 2)
    # 夹套的冷却水管：两根短管接到机身，管口里是水
    for y in (12, 13):
        c.hline(7, 13, y, STEEL, 3 if y == 12 else 2)
    c.set(13, 12, WATER, 3, glint=True)
    c.set(13, 13, GRAPHITE, 2, glint=True)
    return c


def bio_top(rng: random.Random, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    # 封头和正中的搅拌电机
    lid(c, 7.5, 7.5, 5.6, STEEL, 4)
    motor(c, 7.5, 7.5, 3.3, fins=8)
    # 轴帽：电机自带的轴帽明暗级数太多，这里按受光方向重画成三级
    for x, y in c.disc_cells(7.5, 7.5, 1.4):
        toward = (x - 7.5) * -0.58 + (y - 7.5) * -0.62
        c.set(x, y, STEEL, 5 if toward > 0.5 else (3 if toward < -0.5 else 4))
    c.set(7, 7, STEEL, 6, glint=True)
    # 右上排气口、左下取样口
    c.ring(12.5, 2.5, 1.2, STEEL, 3)
    c.set(12, 2, GRAPHITE, 1)
    c.ring(2.5, 12.5, 1.2, STEEL, 3)
    c.set(2, 12, GRAPHITE, 1)
    # 封头一圈的补光灯指示灯
    for x, y in ((7, 3), (12, 7), (8, 12), (3, 8)):
        c.set(x, y, GREEN_GLOW if on else GREEN, 3 if on else 1, glint=True)
    return c


@texture("block/bioreactor_top")
def bioreactor_top(rng):
    return bio_top(rng, False)


@texture("block/bioreactor_top_on", glow=True)
def bioreactor_top_on(rng):
    return bio_top(rng, True)


# ---------------------------------------------------------------- 产物

@texture("item/algal_biomass", refs=("item/dried_kelp", "item/slime_ball"))
def algal_biomass(rng):
    # 从藻液里滤出来的一团藻泥（螺旋藻那样的墨绿）：压实的湿块，左上受光、右下背光，表面几点水光，旁边掉了两小块
    c = Canvas()
    cells = set()
    for cx, cy, rad in ((7.5, 9.5, 4.4), (4.8, 8.6, 3.0), (10.6, 10.0, 2.8), (8.2, 6.6, 2.8)):
        cells.update(c.disc_cells(cx, cy, rad))
    for x, y in sorted(cells):
        d = (x - 4) + (y - 5)
        lv = 3 if d < 3 else 2 if d < 9 else 1
        # 压实的块表面不平：零星低一级
        if lv > 1 and rng.random() < 0.22:
            lv -= 1
        c.set(x, y, GREEN, lv)
    # 湿的水光，只在受光的一侧
    for x, y in ((5, 7), (6, 6), (8, 5), (4, 9)):
        if (x, y) in cells:
            c.set(x, y, GREEN, 4, glint=True)
    # 掉下来的两小块
    c.set(2, 13, GREEN, 2)
    c.set(13, 13, GREEN, 1)
    c.set(13, 12, GREEN, 2)
    c.outline()
    return c
