"""生成 M16（ADR-0028 §八）行星护盾发生器的贴图。

用法：python tools/m16_shield_textures.py
下半截是深色机壳上一排储能电容，每格一道青色的电量灯；上半截是导能柱、浅色的抛物面盘和发射头（展开时亮青色，收起时暗）。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, N, DARK, MID, LIGHT, CYAN, shade, mix, clear, new, put, rect, save, noise, frame  # noqa: E402

SHIELD = (92, 200, 255)
SHIELD_DIM = (34, 70, 96)


def bank():
    """储能电容：四格竖着的电容，每格中间一道青色的电量灯，上下是机壳的边。"""
    rng = random.Random(1611)
    img = new(shade(DARK, 0.9))
    noise(img, rng, 0.04)
    frame(img, shade(MID, 1.1), shade(DARK, 0.6))
    for i, x0 in enumerate((2, 6, 10)):
        rect(img, x0, 2, x0 + 3, 13, shade(MID, 0.85))
        for y in range(2, 14):
            put(img, x0, y, shade(MID, 1.15))
            put(img, x0 + 3, y, shade(MID, 0.6))
        # 电量灯：从下往上亮到不同的高度
        top = 4 + i * 2
        for y in range(top, 12):
            put(img, x0 + 1, y, SHIELD if y > top else shade(SHIELD, 1.2))
            put(img, x0 + 2, y, shade(SHIELD, 0.75))
    for x in range(1, 15):
        put(img, x, 14, shade(DARK, 0.7))
    save(img, BLOCK, "planetary_shield_generator_bank")


def core():
    """导能柱、支架：深灰的柱子，正中一道青色的导能管，两边是一节一节的箍。"""
    rng = random.Random(1612)
    img = new(shade(MID, 0.75))
    noise(img, rng, 0.05)
    for y in range(N):
        put(img, 7, y, shade(SHIELD, 0.85))
        put(img, 8, y, shade(SHIELD, 0.6))
        if y % 4 == 0:
            for x in range(N):
                put(img, x, y, shade(LIGHT, 0.95))
    frame(img, shade(MID, 1.1), shade(DARK, 0.6))
    save(img, BLOCK, "planetary_shield_generator_core")


def dish():
    """抛物面的盘：浅色的金属，一圈圈同心的接缝，往外一点点变暗。"""
    rng = random.Random(1613)
    img = new(LIGHT)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            c = shade(LIGHT, 1.08 - d * 0.03)
            if abs(d - 3) < 0.5 or abs(d - 6) < 0.5:
                c = shade(LIGHT, 0.72)
            put(img, x, y, c)
    noise(img, rng, 0.03)
    frame(img, shade(LIGHT, 1.15), shade(MID, 0.8))
    save(img, BLOCK, "planetary_shield_generator_dish")


def emitter():
    """发射头：亮着是从白到青的光球，外面一圈细环；灭着是暗青。"""
    for name, lit in (("planetary_shield_generator_top", True), ("planetary_shield_generator_top_off", False)):
        img = clear()
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                ring = abs(d - 6) < 0.8
                if lit:
                    c = mix((240, 252, 255), SHIELD, min(1, d / 5.5)) if not ring else shade(CYAN, 1.1)
                else:
                    c = shade(SHIELD_DIM, 1.2) if ring else mix(shade(SHIELD_DIM, 0.9), shade(SHIELD_DIM, 0.55), min(1, d / 7))
                put(img, x, y, c)
        frame(img, shade(MID, 1.1), shade(DARK, 0.6))
        save(img, BLOCK, name)


def main():
    bank()
    core()
    dish()
    emitter()


if __name__ == "__main__":
    main()
