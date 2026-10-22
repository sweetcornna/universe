"""生成 M16（ADR-0028 §五）信号塔、干扰塔和投掷物、干扰胶囊的贴图。

用法：python tools/m16_support_textures.py
信号塔是红白相间的桁架，顶上一盏警示灯；干扰塔是深灰机壳上绕着紫色的发射线圈。黑雾矩阵的贴图如果还没有，也在这里补一张。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import (BLOCK, ITEM, N, DARK, MID, LIGHT, shade, mix, clear, new, put, rect, outline, save, noise, frame,  # noqa: E402
                              matrix)

SIGNAL_RED = (196, 58, 46)
SIGNAL_WHITE = (222, 226, 230)
BEACON = (255, 96, 64)
VIOLET = (168, 120, 255)
VIOLET_DIM = (70, 52, 110)


def signal_tower():
    rng = random.Random(1601)
    # 塔身：红白相间的桁架，两边是立柱，中间斜撑，后面是深色的芯
    pole = new(shade(DARK, 0.75))
    for y in range(N):
        band = SIGNAL_RED if (y // 4) % 2 == 0 else SIGNAL_WHITE
        for x in (0, 1, 14, 15):
            put(pole, x, y, shade(band, 1.15 if x in (0, 14) else 0.85))
        # 斜撑：每 8 格一个 X
        d = y % 8
        for x in (2 + d, 13 - d):
            if 2 <= x <= 13:
                put(pole, x, y, shade(band, 0.95))
    noise(pole, rng, 0.04)
    save(pole, BLOCK, "signal_tower_pole")
    # 天线阵：横梁上一排竖着的天线，中间的发射头
    dish = new(shade(MID, 0.8))
    noise(dish, rng, 0.04)
    frame(dish, shade(MID, 1.1), shade(MID, 0.55))
    for x in range(2, 14, 3):
        rect(dish, x, 2, x + 1, 13, shade(LIGHT, 1.05))
        put(dish, x, 2, (255, 255, 255))
    for x in range(1, 15):
        put(dish, x, 7, shade(SIGNAL_RED, 0.9))
        put(dish, x, 8, shade(SIGNAL_RED, 0.7))
    save(dish, BLOCK, "signal_tower_dish")
    # 警示灯：亮着是橙红，灭了是暗红
    for name, lit in (("signal_tower_top", True), ("signal_tower_top_off", False)):
        top = clear()
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if lit:
                    c = mix((255, 236, 200), BEACON, min(1, d / 7))
                else:
                    c = mix((120, 48, 40), (70, 30, 26), min(1, d / 7))
                put(top, x, y, c)
        frame(top, shade(SIGNAL_RED, 1.2 if lit else 0.8), shade(SIGNAL_RED, 0.6 if lit else 0.4))
        save(top, BLOCK, name)


def jammer_tower():
    rng = random.Random(1602)
    base = (62, 64, 78)
    # 机壳：深灰，正中一道紫色的窗
    body = new(base)
    noise(body, rng, 0.04)
    frame(body, shade(base, 1.3), shade(base, 0.6))
    for y in range(3, 13):
        put(body, 7, y, VIOLET_DIM)
        put(body, 8, y, shade(VIOLET_DIM, 1.2))
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(body, x, y, shade(base, 1.4))
        put(body, x + 1, y + 1, shade(base, 0.6))
    save(body, BLOCK, "jammer_tower_body")
    # 发射线圈：一圈圈紫铜色的线，中间一道紫光
    coil = new((92, 60, 46))
    for y in range(N):
        f = 1.25 if y % 2 == 0 else 0.75
        for x in range(N):
            put(coil, x, y, shade((150, 96, 70), f))
    for y in range(N):
        put(coil, 7, y, VIOLET)
        put(coil, 8, y, shade(VIOLET, 0.8))
    noise(coil, rng, 0.05)
    save(coil, BLOCK, "jammer_tower_coil")
    # 顶上的发射头：亮着是紫光，灭了是暗紫
    for name, lit in (("jammer_tower_top", True), ("jammer_tower_top_off", False)):
        top = clear()
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                ring = abs(d - 5) < 0.8
                if lit:
                    c = (240, 220, 255) if d < 2 else VIOLET if ring or d < 3.5 else shade(VIOLET, 0.6)
                else:
                    c = shade(VIOLET_DIM, 1.1) if ring else shade(VIOLET_DIM, 0.7)
                put(top, x, y, c)
        save(top, BLOCK, name)


def explosive_unit():
    """爆破单元：灰色的扁盒子，红色的警示条，中间一个黄色的引信灯。"""
    rng = random.Random(1603)
    img = clear()
    for y in range(4, 13):
        for x in range(2, 14):
            put(img, x, y, shade((112, 116, 120), 1 + rng.uniform(-0.08, 0.08)))
    for x in range(2, 14):
        put(img, x, 4, (160, 166, 170))
        put(img, x, 12, (70, 72, 76))
    for x in range(2, 14):
        c = (200, 52, 40) if (x // 2) % 2 == 0 else (40, 40, 44)
        put(img, x, 7, c)
        put(img, x, 8, c)
    rect(img, 7, 5, 8, 6, (255, 214, 80))
    put(img, 7, 5, (255, 250, 200))
    rect(img, 6, 2, 9, 3, (90, 92, 96))
    outline(img, (24, 24, 28))
    save(img, ITEM, "explosive_unit")


def crystal_explosive_unit():
    """晶石爆破单元：爆破单元的盒子上嵌着一块发光的青色晶石。"""
    rng = random.Random(1604)
    img = clear()
    for y in range(5, 14):
        for x in range(2, 14):
            put(img, x, y, shade((96, 100, 112), 1 + rng.uniform(-0.08, 0.08)))
    for x in range(2, 14):
        put(img, x, 5, (150, 156, 166))
        put(img, x, 13, (60, 62, 70))
        c = (200, 52, 40) if (x // 2) % 2 == 0 else (40, 40, 44)
        put(img, x, 11, c)
    # 晶石：菱形，从盒子里冒出来
    for y in range(1, 10):
        half = 3 - abs(y - 5) * 0.6
        for x in range(N):
            if abs(x - 7.5) <= half:
                put(img, x, y, mix((200, 255, 250), (60, 200, 220), abs(x - 7.5) / 3 + (y - 1) / 18))
    put(img, 7, 3, (255, 255, 255))
    outline(img, (16, 30, 36))
    save(img, ITEM, "crystal_explosive_unit")


def jamming_capsule():
    """干扰胶囊：两头银色的胶囊，中间是透明的紫色。"""
    img = clear()
    for i in range(3, 13):
        # 斜着放，从左下到右上
        for w in range(-2, 3):
            x, y = i + w // 2, 15 - i + (w - w // 2)
            if not (0 <= x < N and 0 <= y < N):
                continue
            if i < 6 or i > 10:
                c = shade((190, 196, 204), 1.15 if w < 0 else 0.8)
            else:
                c = mix((236, 220, 255), VIOLET, 0.3 + 0.15 * abs(w))
            put(img, x, y, c)
    outline(img, (30, 22, 46))
    save(img, ITEM, "jamming_capsule")


def dark_fog_matrix():
    """黑雾矩阵：别的矩阵一样的玻璃立方体，暗红色的核心。只在别处还没画时补一张。"""
    if os.path.exists(os.path.join(ITEM, "dark_fog_matrix.png")):
        return
    matrix("dark_fog_matrix", (150, 36, 52))


def main():
    signal_tower()
    jammer_tower()
    explosive_unit()
    crystal_explosive_unit()
    jamming_capsule()
    dark_fog_matrix()


if __name__ == "__main__":
    main()
