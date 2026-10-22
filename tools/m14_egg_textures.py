"""生成 M14（ADR-0026 §九）生物卵和卵序列仪的方块贴图。

用法：python tools/m14_egg_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

生物卵是一颗奶白色带斑点的卵，模型每个面都按卵的颜色染色，所以底色要浅，斑点和裂缝靠明暗区分。
侧面三张：完好、裂一道、裂两道（第二道裂到顶上，旁边掉了一小块壳）。模型只用侧面中间八格宽、下面十行，
顶面只用中间八格。
卵序列仪是一台白色外壳的小孵化箱：正面一个圆顶玻璃罩，里面一颗卵，干活时罩里亮青色的扫描光；
顶上一块玻璃观察窗；侧面两根营养液管。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, N, CYAN, CYAN_DIM, shade, mix, new, put, rect, save, noise, frame, rivet  # noqa: E402

SHELL = (240, 236, 224)
SPECKLE = (196, 186, 166)
CRACK = (82, 72, 62)
INSIDE = (52, 44, 38)
CASING = (214, 220, 224)
GLASS = (64, 92, 104)


# ---- 生物卵

def shell(rng):
    img = new(SHELL)
    # 两边稍暗，像圆的
    for y in range(N):
        for x in range(N):
            d = abs(x - 7.5) / 8
            put(img, x, y, shade(SHELL, 1.02 - 0.12 * d * d))
    noise(img, rng, 0.02)
    for _ in range(14):
        x, y = rng.randint(4, 11), rng.randint(5, 15)
        put(img, x, y, SPECKLE)
        if rng.random() < 0.3:
            put(img, x + 1, y, mix(SPECKLE, SHELL, 0.5))
    return img


def crack(img, points):
    """沿着折线画一道裂缝，一格一格连起来。"""
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            t = i / steps if steps else 0
            put(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), CRACK)


FIRST = [(4, 11), (6, 10), (7, 12), (9, 10), (11, 11)]
SECOND = [(5, 7), (7, 8), (8, 6), (10, 8), (11, 7)]


def egg_textures():
    for stage in range(3):
        img = shell(random.Random(1460))
        if stage >= 1:
            crack(img, FIRST)
        if stage >= 2:
            crack(img, SECOND)
            # 第二道裂缝旁边掉了一小块壳，露出里面
            for x, y in ((8, 9), (9, 9), (8, 8)):
                put(img, x, y, INSIDE)
        save(img, BLOCK, "creature_egg_side_" + str(stage))
    top = shell(random.Random(1461))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5) / 6
            if d < 1:
                put(top, x, y, mix(top.getpixel((x, y))[:3], (255, 255, 250), 0.25 * (1 - d)))
    save(top, BLOCK, "creature_egg_top")


# ---- 卵序列仪

def casing(rng):
    img = new(CASING)
    noise(img, rng, 0.025)
    frame(img, shade(CASING, 1.08), shade(CASING, 0.68))
    return img


def sequencer_textures():
    for suffix, lit in (("", False), ("_on", True)):
        front = casing(random.Random(1462))
        # 圆顶玻璃罩：下面一道金属底座，上面半圆的玻璃
        rect(front, 2, 12, 13, 13, shade(CASING, 0.6))
        for y in range(2, 12):
            for x in range(2, 14):
                # 指数大于 2 的超椭圆：顶上圆一些，不像尖拱
                d = (abs((x - 7.5) / 6.0) ** 2.6 + abs((y - 11.5) / 8.8) ** 2.6) ** (1 / 2.6)
                if d < 1:
                    glow = mix(GLASS, CYAN, 0.55 - d * 0.3) if lit else shade(GLASS, 0.9 + 0.2 * (1 - d))
                    put(front, x, y, glow)
                elif d < 1.12:
                    put(front, x, y, shade(CASING, 0.55))
        # 罩里一颗卵：上窄下宽，右边暗一点
        for y, (x0, x1) in zip(range(6, 12), ((7, 8), (6, 9), (6, 9), (5, 10), (5, 10), (6, 9))):
            for x in range(x0, x1 + 1):
                put(front, x, y, shade(SHELL, 1.0 - 0.06 * max(0, x - 7)))
        if lit:
            # 一道横着扫过的扫描光
            for x in range(4, 12):
                put(front, x, 8, mix(front.getpixel((x, 8))[:3], CYAN, 0.7))
        # 玻璃反光
        put(front, 5, 4, mix(GLASS, (255, 255, 255), 0.6))
        put(front, 4, 5, mix(GLASS, (255, 255, 255), 0.4))
        # 底座上的指示灯
        rect(front, 10, 14, 12, 14, CYAN if lit else CYAN_DIM)
        put(front, 3, 14, (120, 200, 140) if lit else (60, 80, 66))
        save(front, BLOCK, "egg_sequencer_front" + suffix)
    side = casing(random.Random(1463))
    for x, color in ((5, (150, 210, 120)), (10, (220, 160, 200))):
        for y in range(2, 14):
            put(side, x, y, shade(color, 0.9 if y % 4 else 1.15))
            put(side, x + 1, y, shade(color, 0.7))
        put(side, x, 1, shade(CASING, 0.5))
        put(side, x + 1, 1, shade(CASING, 0.5))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(side, x, y, CASING)
    save(side, BLOCK, "egg_sequencer_side")
    top = casing(random.Random(1464))
    rect(top, 3, 3, 12, 12, shade(CASING, 0.6))
    for y in range(4, 12):
        for x in range(4, 12):
            put(top, x, y, shade(GLASS, 0.9 + 0.03 * (x + y - 8)))
    put(top, 5, 5, mix(GLASS, (255, 255, 255), 0.5))
    save(top, BLOCK, "egg_sequencer_top")


if __name__ == "__main__":
    egg_textures()
    sequencer_textures()
