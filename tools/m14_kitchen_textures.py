"""生成 M14（ADR-0026）营养处理器和便携精炼机的方块贴图。

用法：python tools/m14_kitchen_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

营养处理器是一台白色的小烤箱：正面一扇玻璃门，里面是加热管，工作时管子烧红、门里透出橙光；
顶上两个圆形电热盘和一排散热孔；侧面是白色外壳和一道通风栅。
便携精炼机是一个十二格见方的深灰色金属箱：正面一个圆形的观察窗，工作时里面是橙黄色的熔融物；
顶上一个漏斗口；侧面底下一圈黄黑警示条。模型只用贴图中间的十二格（顶面）和下面十二行（侧面）。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, N, shade, mix, new, put, rect, save, noise, frame, rivet  # noqa: E402

ENAMEL = (222, 224, 220)
STEEL = (84, 90, 98)
GLOW = (255, 150, 52)
HOT = (255, 214, 120)


# ---- 营养处理器

def enamel(rng):
    img = new(ENAMEL)
    noise(img, rng, 0.02)
    frame(img, shade(ENAMEL, 1.08), shade(ENAMEL, 0.7))
    return img


def processor_textures():
    rng = random.Random(1402)
    side = enamel(rng)
    for y in range(5, 11):
        if y % 2 == 1:
            for x in range(4, 12):
                put(side, x, y, shade(ENAMEL, 0.55))
    save(side, BLOCK, "nutrient_processor_side")
    for suffix, lit in (("", False), ("_on", True)):
        front = enamel(random.Random(1403))
        # 控制条：一排旋钮和一块小屏
        rect(front, 1, 1, 14, 3, shade(ENAMEL, 0.84))
        for x in (3, 6):
            put(front, x, 2, (60, 64, 70))
        rect(front, 9, 2, 13, 2, (110, 220, 140) if lit else (48, 70, 58))
        # 门：外框、玻璃、里面的加热管
        rect(front, 2, 5, 13, 13, (70, 74, 80))
        inside = (120, 52, 24) if lit else (34, 36, 40)
        rect(front, 3, 6, 12, 12, inside)
        for y in (7, 11):
            for x in range(3, 13):
                put(front, x, y, GLOW if lit else (66, 60, 58))
        if lit:
            for y in range(8, 11):
                for x in range(3, 13):
                    put(front, x, y, mix(inside, GLOW, 0.35 - abs(y - 9) * 0.1))
        # 玻璃反光
        put(front, 4, 6, mix(inside, (255, 255, 255), 0.4))
        put(front, 5, 6, mix(inside, (255, 255, 255), 0.25))
        # 门把手
        rect(front, 4, 14, 11, 14, (150, 154, 160))
        save(front, BLOCK, "nutrient_processor_front" + suffix)
    for suffix, lit in (("", False), ("_on", True)):
        top = enamel(random.Random(1404))
        for cx, cy in ((4.5, 5.5), (11.5, 5.5)):
            for y in range(N):
                for x in range(N):
                    d = math.hypot(x - cx, y - cy)
                    if d < 3.3:
                        # 黑色的盘面，外圈一道金属边，中间一圈电热丝
                        coil = 0.9 <= d < 1.9
                        base = (104, 108, 114) if d >= 2.6 else (58, 60, 66) if coil else (36, 38, 42)
                        put(top, x, y, mix(base, GLOW, 0.75) if lit and coil else base)
        for x in range(3, 13, 2):
            put(top, x, 12, shade(ENAMEL, 0.5))
            put(top, x, 13, shade(ENAMEL, 0.6))
        save(top, BLOCK, "nutrient_processor_top" + suffix)


# ---- 便携精炼机

def refiner_side(rng):
    img = new(STEEL)
    noise(img, rng, 0.04)
    # 模型侧面是 x 2–13、y 4–15 这一块
    for x in range(2, 14):
        put(img, x, 4, shade(STEEL, 1.3))
        stripe = (214, 172, 46) if (x + 15) // 2 % 2 == 0 else (40, 40, 44)
        put(img, x, 14, stripe)
        put(img, x, 15, shade(stripe, 0.8))
    for y in range(4, N):
        put(img, 2, y, shade(STEEL, 1.2))
        put(img, 13, y, shade(STEEL, 0.65))
    rivet(img, 3, 5, STEEL)
    rivet(img, 11, 5, STEEL)
    return img


def refiner_textures():
    rng = random.Random(1405)
    side = refiner_side(rng)
    vents = side.copy()
    for y in (7, 9, 11):
        for x in range(5, 11):
            put(vents, x, y, shade(STEEL, 0.45))
    save(vents, BLOCK, "portable_refiner_side")
    for suffix, lit in (("", False), ("_on", True)):
        front = side.copy()
        # 圆形观察窗
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 9.0)
                if d < 3.9:
                    if d >= 3.0:
                        put(front, x, y, (150, 156, 164))
                    elif lit:
                        put(front, x, y, mix(HOT, GLOW, min(1, d / 2.6)))
                    else:
                        put(front, x, y, mix((30, 32, 36), (60, 50, 44), 1 - d / 3))
        put(front, 6, 7, mix(HOT if lit else (60, 62, 66), (255, 255, 255), 0.5))
        # 状态灯
        put(front, 12, 6, (120, 240, 120) if lit else (90, 50, 46))
        save(front, BLOCK, "portable_refiner_front" + suffix)
    top = new(STEEL)
    noise(top, rng, 0.04)
    # 顶面是 x 2–13、y 2–13 这一块，中间一个漏斗口
    for i in range(2, 14):
        put(top, i, 2, shade(STEEL, 1.3))
        put(top, 2, i, shade(STEEL, 1.2))
        put(top, i, 13, shade(STEEL, 0.65))
        put(top, 13, i, shade(STEEL, 0.65))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.6:
                put(top, x, y, shade((60, 64, 70), 0.55 + d * 0.12))
            if d < 1.4:
                put(top, x, y, (20, 20, 22))
    save(top, BLOCK, "portable_refiner_top")
    bottom = new(shade(STEEL, 0.7))
    noise(bottom, rng, 0.04)
    save(bottom, BLOCK, "portable_refiner_bottom")


def main():
    processor_textures()
    refiner_textures()


if __name__ == "__main__":
    main()
