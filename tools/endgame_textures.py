"""生成终局的物品和设备贴图（ADR-0018），16×16 像素画。

用法：python tools/endgame_textures.py [方块贴图目录] [物品贴图目录]
和 factory_textures.py 共用画图的函数和配色；随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_textures as ft  # noqa: E402
from factory_textures import N, casing, clear, mix, new, outline, put, rect, rivet, save, shade  # noqa: E402

BLOCK = ft.BLOCK
ITEM = ft.ITEM

VIOLET = (170, 90, 255)
PINK = (255, 120, 220)
STAR_WHITE = (255, 252, 236)
STAR_GOLD = (255, 196, 84)
STAR_ORANGE = (255, 132, 48)
STEEL = (64, 70, 82)
GOLD_TRIM = (206, 164, 72)


# ---- 物品

def antimatter():
    """反物质：磁约束的玻璃小球，里面一团黑紫色的东西，边上一圈粉色的光晕。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.6:
                if d < 3.2:
                    c = mix((10, 0, 20), (90, 30, 140), d / 3.2)
                elif d < 4.6:
                    c = mix(PINK, VIOLET, (d - 3.2) / 1.4)
                else:
                    c = mix((150, 170, 210), (80, 90, 130), (d - 4.6) / 2)
                put(img, x, y, c, 255 if d < 4.6 else 170)
    put(img, 5, 4, (240, 250, 255))
    put(img, 4, 5, (220, 236, 255))
    outline(img, (30, 16, 50))
    save(img, ITEM, "antimatter")


def annihilation_constraint_sphere():
    """湮灭约束球：银色的金属球，两道交叉的约束环，环的缝里透出紫光。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.4:
                light = 1.25 - 0.06 * math.hypot(x - 5, y - 5)
                put(img, x, y, shade((150, 158, 172), light))
    for i in range(1, 15):
        # 一道横的环，一道斜的环
        put(img, i, 7, (210, 216, 228))
        put(img, i, 8, VIOLET)
        y = int(round(7.5 + (i - 7.5) * 0.8))
        if 1 <= y <= 14 and math.hypot(i - 7.5, y - 7.5) <= 6.4:
            put(img, i, y, PINK)
    put(img, 7, 7, (255, 230, 255))
    put(img, 8, 8, (255, 230, 255))
    outline(img, (26, 24, 40))
    save(img, ITEM, "annihilation_constraint_sphere")


def antimatter_fuel_rod():
    """反物质燃料棒：和氘核燃料棒一样的棒子，中间一段是紫红色的燃料，两端的帽是金色的。"""
    img = clear()
    rect(img, 6, 1, 9, 14, (150, 140, 180))
    rect(img, 6, 4, 9, 11, (200, 70, 220))
    for y in range(4, 12):
        put(img, 6, y, (255, 170, 255))
        put(img, 9, y, (110, 30, 140))
        if y % 3 == 0:
            put(img, 7, y, (255, 230, 255))
    rect(img, 5, 1, 10, 2, GOLD_TRIM)
    rect(img, 5, 13, 10, 14, GOLD_TRIM)
    outline(img, (30, 16, 40))
    save(img, ITEM, "antimatter_fuel_rod")


def matrix_cube(name, top, left, right, core):
    """矩阵的立方体：和 factory_textures.matrix 一样的形状，三个面分别上色。"""
    img = clear()
    slope = 0.54
    for y in range(N):
        for x in range(N):
            dx = abs(x - 7.5)
            if dx > 6.6 or y < 1 + dx * slope or y > 14.6 - dx * slope:
                continue
            if y <= 8 - dx * slope:
                c = top
            elif x < 7.5:
                c = left
            else:
                c = right
            put(img, x, y, c, 235)
    edge = (250, 250, 255)
    for y in range(8, 15):
        put(img, 7, y, edge)
    for i in range(7):
        put(img, 7 - i, int(round(8 - i * slope)), edge)
        put(img, 8 + i, int(round(8 - i * slope)), edge)
    for y in range(6, 12):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, (y - 8.8) * 1.2)
            if d < 2.4:
                put(img, x, y, mix((255, 255, 255), core, d / 2.4))
    outline(img, (30, 32, 40))
    save(img, ITEM, name)


def composite_matrix():
    """复合矩阵：顶面黄、左面蓝、右面红，三种矩阵拼在一起。"""
    matrix_cube("composite_matrix", (240, 210, 70), (70, 130, 240), (220, 70, 70), (255, 240, 220))


def universe_matrix():
    """宇宙矩阵：白色的立方体，三个面带一点点不同的冷暖，核心是彩虹色的光。"""
    img = clear()
    slope = 0.54
    for y in range(N):
        for x in range(N):
            dx = abs(x - 7.5)
            if dx > 6.6 or y < 1 + dx * slope or y > 14.6 - dx * slope:
                continue
            if y <= 8 - dx * slope:
                c = (250, 250, 252)
            elif x < 7.5:
                c = (220, 228, 240)
            else:
                c = (196, 200, 214)
            put(img, x, y, c, 240)
    for y in range(8, 15):
        put(img, 7, y, (255, 255, 255))
    for i in range(7):
        put(img, 7 - i, int(round(8 - i * slope)), (255, 255, 255))
        put(img, 8 + i, int(round(8 - i * slope)), (255, 255, 255))
    rainbow = ((255, 90, 90), (255, 200, 80), (120, 230, 120), (90, 170, 255), (190, 110, 255))
    for y in range(6, 12):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, (y - 8.8) * 1.2)
            if d < 2.6:
                a = (math.atan2(y - 8.8, x - 7.5) + math.pi) / (2 * math.pi)
                c = rainbow[int(a * len(rainbow)) % len(rainbow)]
                put(img, x, y, mix((255, 255, 255), c, d / 2.6))
    outline(img, (90, 96, 116))
    save(img, ITEM, "universe_matrix")


# ---- 微型聚变发电站

def mini_fusion_power_plant():
    """微型聚变发电站：深灰的机壳，正面一圈环形的约束线圈，工作时环里是青白色的等离子体。"""
    rng = random.Random(801)
    side = casing(STEEL, rng)
    for y in (3, 12):
        for x in range(2, 14):
            put(side, x, y, (96, 170, 200))
    save(side, BLOCK, "mini_fusion_power_plant_side")
    top = casing((78, 84, 96), rng, seam=False)
    for y in range(N):
        for x in range(N):
            if 3.5 < math.hypot(x - 7.5, y - 7.5) < 5.5:
                put(top, x, y, (120, 130, 150))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rivet(top, x, y, (78, 84, 96))
    save(top, BLOCK, "mini_fusion_power_plant_top")
    for name, lit in (("mini_fusion_power_plant_front", False), ("mini_fusion_power_plant_front_on", True)):
        img = casing(STEEL, random.Random(802), seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.0 < d < 6.4:
                    # 线圈一节一节的
                    a = math.atan2(y - 7.5, x - 7.5)
                    segment = int((a + math.pi) / (2 * math.pi) * 12) % 2
                    put(img, x, y, (184, 112, 60) if segment else (150, 90, 50))
                elif d <= 4.0:
                    if lit:
                        put(img, x, y, mix((255, 255, 255), (90, 220, 255), d / 4.0))
                    else:
                        put(img, x, y, mix((40, 50, 64), (24, 28, 36), d / 4.0))
        save(img, BLOCK, name)


# ---- 人造恒星

def artificial_star():
    """人造恒星：钢灰色的底座带金色的包边，底座上的线圈箱工作时透出橙光；四根立柱和顶框；恒星的核心和日冕。"""
    rng = random.Random(811)
    base = casing(STEEL, rng)
    for x in range(N):
        put(base, x, 0, GOLD_TRIM)
        put(base, x, 1, shade(GOLD_TRIM, 0.8))
    save(base, BLOCK, "artificial_star_base")
    top = casing((86, 92, 104), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3:
                put(top, x, y, mix((255, 220, 140), (140, 100, 60), d / 3))
            elif d < 4:
                put(top, x, y, GOLD_TRIM)
    save(top, BLOCK, "artificial_star_top")
    for name, lit in (("artificial_star_coil", False), ("artificial_star_coil_on", True)):
        img = new((60, 64, 74))
        for y in range(N):
            for x in range(N):
                # 横着绕的铜线圈，中间三道缝
                c = (176, 104, 56) if y % 3 != 0 else (120, 70, 40)
                if 5 <= x <= 10 and y % 4 == 1:
                    c = mix(STAR_ORANGE, STAR_GOLD, 0.5) if lit else (40, 30, 26)
                put(img, x, y, shade(c, 0.95 + 0.1 * ((x * 7 + y * 3) % 3) / 2))
        for y in range(N):
            put(img, 0, y, (90, 96, 108))
            put(img, 15, y, (44, 48, 56))
        save(img, BLOCK, name)
    pillar = new(STEEL)
    for y in range(N):
        put(pillar, 0, y, (100, 108, 122))
        put(pillar, 1, y, (84, 90, 102))
        if y % 5 == 2:
            for x in range(N):
                put(pillar, x, y, GOLD_TRIM)
    save(pillar, BLOCK, "artificial_star_pillar")
    frame = new((74, 80, 92))
    for x in range(N):
        put(frame, x, 0, (110, 118, 132))
        put(frame, x, 15, (44, 48, 56))
        if x % 4 == 1:
            put(frame, x, 7, GOLD_TRIM)
            put(frame, x, 8, GOLD_TRIM)
    save(frame, BLOCK, "artificial_star_frame")
    core = new(STAR_WHITE)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            # 中间白，到边上才泛黄
            c = mix(STAR_WHITE, STAR_GOLD, max(0, d - 4) / 7)
            # 表面的米粒组织
            if (x * 5 + y * 3) % 7 == 0:
                c = shade(c, 0.95)
            put(core, x, y, c)
    save(core, BLOCK, "artificial_star_core")
    corona = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            # 很淡的一层光晕，中间稍浓
            put(corona, x, y, mix(STAR_GOLD, STAR_ORANGE, min(1, d / 10)), max(24, int(80 - d * 6)))
    save(corona, BLOCK, "artificial_star_corona")


def main():
    antimatter()
    annihilation_constraint_sphere()
    antimatter_fuel_rod()
    composite_matrix()
    universe_matrix()
    mini_fusion_power_plant()
    artificial_star()


if __name__ == "__main__":
    main()
