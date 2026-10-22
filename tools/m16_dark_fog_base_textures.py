"""生成黑雾地面基地（ADR-0028 §二）的方块贴图（16×16 像素画）：底板、导轨、地核。

用法：python tools/m16_dark_fog_base_textures.py [方块贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block。

配色：黑雾是近黑的炮铜色金属，缝里透出暗红的光（戴森球计划里黑雾的样子）；地核是钻芯打穿地壳后露出来的熔融物质。
随机数用固定种子，每次生成的结果一样。
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import (BLOCK, N, shade, mix, new, put, get, noise, save)  # noqa: E402

PLATE = (34, 32, 38)
SEAM = (18, 17, 21)
EDGE = (58, 54, 62)
GLOW = (214, 52, 64)
GLOW_HOT = (255, 128, 112)
CRUST = (40, 26, 22)
MOLTEN = (236, 120, 40)
MOLTEN_HOT = (255, 214, 120)


def plating(rng):
    img = new(PLATE)
    noise(img, rng, 0.06)
    # 两块错开的面板，缝是暗的，面板上沿亮一点
    for i in range(N):
        put(img, i, 7, SEAM)
        put(img, i, 15, SEAM)
        put(img, i, 8, EDGE)
        put(img, i, 0, EDGE)
    for y in range(0, 7):
        put(img, 11, y, SEAM)
        put(img, 12, y, shade(EDGE, 0.9))
    for y in range(8, 15):
        put(img, 4, y, SEAM)
        put(img, 5, y, shade(EDGE, 0.9))
    # 零星的暗红光点
    for _ in range(3):
        x, y = rng.randrange(N), rng.randrange(N)
        put(img, x, y, shade(GLOW, 0.55))
    return img


def conduit(rng):
    img = plating(rng)
    # 十字形的导槽：中间一道亮红，两边暗红
    for i in range(N):
        for c, color in ((6, shade(GLOW, 0.5)), (7, GLOW), (8, GLOW), (9, shade(GLOW, 0.5))):
            put(img, i, c, color)
            put(img, c, i, color)
    for i in range(N):
        if i % 4 == 1:
            put(img, i, 7, GLOW_HOT)
            put(img, 8, i, GLOW_HOT)
    put(img, 7, 7, GLOW_HOT)
    put(img, 8, 8, GLOW_HOT)
    return img


def remnant(rng):
    img = new(MOLTEN)
    # 熔融的底色，中间更热
    for y in range(N):
        for x in range(N):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6
            put(img, x, y, mix(MOLTEN_HOT, MOLTEN, min(1, d * 1.3)))
    noise(img, rng, 0.08)
    # 一块块凝固的硬壳，壳之间是发光的裂缝
    for _ in range(7):
        cx, cy = rng.randrange(N), rng.randrange(N)
        r = rng.uniform(2.0, 3.6)
        for y in range(N):
            for x in range(N):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    put(img, x, y, shade(CRUST, rng.uniform(0.85, 1.25)))
    for y in range(N):
        for x in range(N):
            if get(img, x, y)[0] < 80 and rng.random() < 0.06:
                put(img, x, y, shade(MOLTEN, 0.8))
    return img


def main():
    rng = random.Random(28)
    save(plating(rng), BLOCK, "dark_fog_plating")
    save(conduit(rng), BLOCK, "dark_fog_conduit")
    save(remnant(rng), BLOCK, "dark_fog_core_remnant")


if __name__ == "__main__":
    main()
