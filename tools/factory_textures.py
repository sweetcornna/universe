"""生成工业设备和中间产品的贴图（16×16 像素画）。

用法：python tools/factory_textures.py [方块贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block 和 textures/item。

设备的配色：深灰金属机壳、青色指示灯（和界面一个风格），风力涡轮机是白色涂装。
发光的部分在模型里单独设了自发光，贴图只管颜色。随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
BLOCK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "block")
ITEM = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "item")
N = 16

DARK = (54, 61, 69)
MID = (92, 102, 112)
LIGHT = (148, 158, 166)
CYAN = (96, 224, 255)
CYAN_DIM = (36, 84, 100)
COPPER = (196, 112, 58)
WHITE = (226, 229, 232)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(color=(0, 0, 0), alpha=255):
    return Image.new("RGBA", (N, N), tuple(color) + (alpha,))


def clear():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def put(img, x, y, color, a=255):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), tuple(color[:3]) + (a,))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def noise(img, rng, amount):
    for y in range(N):
        for x in range(N):
            px = img.getpixel((x, y))
            if px[3] == 0:
                continue
            f = 1 + rng.uniform(-amount, amount)
            put(img, x, y, shade(px, f), px[3])


def frame(img, light, dark):
    for i in range(N):
        put(img, i, 0, light)
        put(img, 0, i, light)
        put(img, i, N - 1, dark)
        put(img, N - 1, i, dark)


def rivet(img, x, y, base):
    put(img, x, y, shade(base, 1.4))
    put(img, x + 1, y + 1, shade(base, 0.6))


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, color)


def outline(img, color):
    """给透明背景上的物品图案描一圈深色边。"""
    src = img.copy()
    for y in range(N):
        for x in range(N):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < N and 0 <= ny < N and src.getpixel((nx, ny))[3] != 0:
                    put(img, x, y, color)
                    break


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


def casing(base, rng, seam=True):
    img = new(base)
    noise(img, rng, 0.035)
    if seam:
        for x in range(1, N - 1):
            put(img, x, 7, shade(base, 0.74))
            put(img, x, 8, shade(base, 1.1))
    frame(img, shade(base, 1.25), shade(base, 0.6))
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y, base)
    return img


# ---- 设备

def machine_casing():
    rng = random.Random(101)
    save(casing(DARK, rng), BLOCK, "machine_casing")
    top = casing(MID, rng, seam=False)
    # 中间的散热格栅
    for y in range(4, 12):
        for x in range(4, 12):
            put(top, x, y, shade(MID, 0.55) if y % 2 == 0 else shade(MID, 0.85))
    save(top, BLOCK, "machine_casing_top")


def tesla_tower():
    rng = random.Random(102)
    # 塔身：竖直的桁架
    pole = new(MID)
    noise(pole, rng, 0.04)
    for y in range(N):
        put(pole, 0, y, shade(MID, 1.3))
        put(pole, 15, y, shade(MID, 0.6))
        for x in range(1, 15):
            # 斜撑
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                put(pole, x, y, shade(MID, 0.6))
    for y in (0, 8):
        for x in range(N):
            put(pole, x, y, shade(MID, 1.15))
    save(pole, BLOCK, "tesla_tower_pole")

    # 线圈：铜线一圈圈绕着
    coil = new(COPPER)
    for y in range(N):
        f = 1.15 if y % 2 == 0 else 0.72
        for x in range(N):
            put(coil, x, y, shade(COPPER, f * (1 + rng.uniform(-0.04, 0.04))))
    for x in range(N):
        put(coil, x, 0, (70, 76, 84))
        put(coil, x, 15, (50, 54, 60))
    save(coil, BLOCK, "tesla_tower_coil")

    # 塔顶的放电球：亮的和暗的两张
    for name, core, edge in (("tesla_tower_top", (220, 255, 255), CYAN), ("tesla_tower_top_off", (70, 110, 124), CYAN_DIM)):
        img = new(edge)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5) / 8
                c = mix(core, edge, min(1, d * 1.2))
                put(img, x, y, shade(c, 1 + rng.uniform(-0.03, 0.03)))
        save(img, BLOCK, name)


def wind_turbine():
    rng = random.Random(103)
    body = new(WHITE)
    noise(body, rng, 0.02)
    for x in range(N):
        put(body, x, 4, shade(WHITE, 0.86))
        put(body, x, 12, shade(WHITE, 0.86))
    frame(body, shade(WHITE, 1.02), shade(WHITE, 0.8))
    save(body, BLOCK, "wind_turbine_body")

    # 机舱侧面：通风口和一条青色的指示灯带
    nacelle = body.copy()
    for x in range(3, 13):
        put(nacelle, x, 7, CYAN)
    for y in (9, 10):
        for x in range(4, 12, 2):
            put(nacelle, x, y, shade(WHITE, 0.6))
    save(nacelle, BLOCK, "wind_turbine_nacelle")

    blade = new(WHITE)
    noise(blade, rng, 0.015)
    for y in range(N):
        put(blade, 0, y, shade(WHITE, 0.8))
        put(blade, 15, y, shade(WHITE, 0.88))
    # 叶尖两道红色警示条
    for y in (1, 2, 5, 6):
        for x in range(N):
            put(blade, x, y, (204, 52, 44))
    save(blade, BLOCK, "wind_turbine_blade")

    base = casing((120, 126, 132), rng, seam=False)
    save(base, BLOCK, "wind_turbine_base")


def thermal_plant():
    rng = random.Random(104)
    steel = (110, 104, 100)
    side = new(steel)
    noise(side, rng, 0.04)
    # 锅炉钢板：横向的焊缝和铆钉
    for y in (4, 10):
        for x in range(N):
            put(side, x, y, shade(steel, 0.7))
            put(side, x, y + 1, shade(steel, 1.12))
    for x in range(1, N, 3):
        put(side, x, 2, shade(steel, 1.35))
        put(side, x, 13, shade(steel, 1.35))
    # 一根竖着的管子
    for y in range(N):
        put(side, 12, y, (150, 72, 52))
        put(side, 13, y, (112, 52, 38))
    frame(side, shade(steel, 1.2), shade(steel, 0.6))
    save(side, BLOCK, "thermal_plant_side")

    top = casing(shade(steel, 0.9), rng, seam=False)
    save(top, BLOCK, "thermal_plant_top")

    for name, glow in (("thermal_plant_front", False), ("thermal_plant_front_on", True)):
        img = side.copy()
        # 炉门：厚边框加格栅，烧着时格栅后面透火光
        rect(img, 3, 6, 12, 14, (46, 44, 44))
        rect(img, 4, 7, 11, 13, (30, 26, 24))
        for y in range(8, 13):
            for x in range(4, 12):
                if glow:
                    t = (y - 8) / 4
                    c = mix((255, 220, 120), (230, 90, 30), t)
                    put(img, x, y, shade(c, 1 + rng.uniform(-0.1, 0.05)))
                else:
                    put(img, x, y, (40, 34, 30))
        for x in (5, 7, 9, 11):
            for y in range(7, 14):
                put(img, x, y, (64, 60, 58))
        # 压力表
        rect(img, 6, 2, 9, 4, (220, 220, 210))
        put(img, 8, 3, (200, 40, 30))
        save(img, BLOCK, name)

    chimney = new((150, 146, 140))
    noise(chimney, rng, 0.06)
    for y in range(N):
        for x in range(N):
            # 砖缝
            if y % 4 == 0 or (x + (y // 4) * 4) % 8 == 0:
                put(chimney, x, y, (110, 106, 100))
    # 顶上一圈红白条纹
    for y in range(0, 4):
        for x in range(N):
            put(chimney, x, y, (196, 56, 46) if y < 2 else (230, 230, 226))
    save(chimney, BLOCK, "thermal_plant_chimney")

    turbine = casing((84, 96, 108), rng)
    for x in range(3, 13):
        put(turbine, x, 4, CYAN)
    save(turbine, BLOCK, "thermal_plant_turbine")


def accumulator():
    rng = random.Random(105)
    side = casing(DARK, rng, seam=False)
    # 竖直的散热鳍片
    for x in range(3, 13, 2):
        for y in range(3, 13):
            put(side, x, y, shade(DARK, 0.7))
    save(side, BLOCK, "accumulator_side")
    top = casing(MID, rng, seam=False)
    # 两个接线柱
    for (x, y, c) in ((4, 4, (200, 60, 50)), (10, 10, (60, 70, 80))):
        rect(top, x, y, x + 1, y + 1, c)
    save(top, BLOCK, "accumulator_top")
    for level in range(5):
        img = casing(DARK, rng, seam=False)
        rect(img, 4, 2, 11, 13, (24, 28, 32))
        # 四格电量，从下往上亮
        for i in range(4):
            y0 = 11 - i * 3
            lit = i < level
            color = (90, 230, 120) if lit else (40, 60, 48)
            rect(img, 5, y0, 10, y0 + 1, color)
        save(img, BLOCK, "accumulator_front_" + str(level))


# ---- 物品

def gear():
    img = clear()
    base = (170, 176, 182)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            tooth = math.cos(a * 8) > 0.2
            if (d < 5.2 or (d < 7.0 and tooth)) and d > 1.8:
                f = 1.15 - 0.06 * d + (0.12 if x + y < 15 else 0)
                put(img, x, y, shade(base, f))
    outline(img, (40, 44, 50))
    save(img, ITEM, "gear")


def magnetic_coil():
    img = clear()
    # 铁芯两头是灰色，中间绕着铜线
    for y in range(3, 13):
        for x in range(2, 14):
            if x < 4 or x > 11:
                put(img, x, y, shade(LIGHT, 1.0 - 0.03 * (y - 3)))
            elif 4 <= y <= 11:
                put(img, x, y, shade(COPPER, 1.2 if x % 2 == 0 else 0.8))
    for x in range(2, 14):
        put(img, x, 2, (230, 80, 70) if x < 8 else (80, 120, 230))
    outline(img, (40, 34, 30))
    save(img, ITEM, "magnetic_coil")


def circuit_board():
    rng = random.Random(201)
    img = clear()
    rect(img, 1, 3, 14, 12, (36, 120, 64))
    # 走线和芯片
    for x in range(2, 14):
        if x % 3 != 0:
            put(img, x, 5, (210, 180, 70))
            put(img, x, 10, (210, 180, 70))
    for y in range(5, 11):
        put(img, 4, y, (210, 180, 70))
        put(img, 11, y, (210, 180, 70))
    rect(img, 6, 6, 9, 9, (30, 30, 36))
    put(img, 7, 7, (90, 90, 100))
    noise(img, rng, 0.05)
    outline(img, (20, 50, 30))
    save(img, ITEM, "circuit_board")


def electric_motor():
    img = clear()
    rect(img, 3, 4, 12, 12, (90, 110, 150))
    for x in range(3, 13):
        if x % 2 == 1:
            for y in range(4, 13):
                put(img, x, y, (70, 86, 122))
    rect(img, 12, 7, 14, 9, (190, 196, 200))
    rect(img, 1, 6, 2, 10, (60, 64, 70))
    rect(img, 4, 2, 6, 3, (200, 120, 60))
    outline(img, (24, 28, 40))
    save(img, ITEM, "electric_motor")


def steel():
    img = clear()
    base = (120, 128, 140)
    for y in range(5, 12):
        for x in range(1 + (11 - y) // 2, 15 - (y - 5) // 3):
            f = 1.25 if y == 5 else 1.0 - 0.04 * (y - 5)
            put(img, x, y, shade(base, f))
    outline(img, (36, 40, 48))
    save(img, ITEM, "steel")


def prism():
    img = clear()
    for y in range(2, 14):
        half = (y - 2) // 2 + 1
        for x in range(8 - half, 8 + half):
            t = (x - (8 - half)) / max(1, 2 * half - 1)
            c = mix((210, 240, 255), (130, 180, 240), t)
            put(img, x, y, c, 220)
    for x in range(3, 13):
        put(img, x, 13, (150, 190, 240))
    outline(img, (50, 70, 110))
    save(img, ITEM, "prism")


def plasma_exciter():
    img = clear()
    rect(img, 4, 2, 11, 13, (70, 76, 86))
    rect(img, 5, 4, 10, 11, (40, 20, 60))
    for y in range(4, 12):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.8:
                put(img, x, y, mix((255, 230, 255), (200, 90, 255), d / 2.8))
    rect(img, 4, 2, 11, 2, (190, 196, 200))
    rect(img, 4, 13, 11, 13, (190, 196, 200))
    outline(img, (24, 20, 30))
    save(img, ITEM, "plasma_exciter")


def energetic_graphite():
    rng = random.Random(202)
    img = clear()
    for y in range(3, 13):
        for x in range(3, 13):
            if abs(x - 7.5) + abs(y - 7.5) < 6.5:
                put(img, x, y, shade((56, 58, 66), 1 + rng.uniform(-0.15, 0.25)))
    for (x, y) in ((6, 5), (9, 7), (7, 10)):
        put(img, x, y, (140, 210, 255))
    outline(img, (16, 16, 20))
    save(img, ITEM, "energetic_graphite")


def droplet(name, top, bottom, alpha=255):
    img = clear()
    for y in range(1, 15):
        for x in range(N):
            # 水滴：上尖下圆
            cy = 9.5
            r = 5.2
            if y < cy:
                w = (y - 1) / (cy - 1) * r
            else:
                w = math.sqrt(max(0, r * r - (y - cy) ** 2))
            if abs(x - 7.5) <= w:
                t = (y - 1) / 13
                c = mix(top, bottom, t)
                put(img, x, y, c, alpha)
    put(img, 6, 8, (255, 255, 255), 230)
    put(img, 6, 9, mix(top, (255, 255, 255), 0.6), 230)
    outline(img, shade(bottom, 0.45))
    save(img, ITEM, name)


def hydrogen():
    img = clear()
    # 气瓶：白色瓶身、蓝色瓶肩
    rect(img, 5, 4, 10, 14, (232, 236, 240))
    rect(img, 5, 4, 10, 6, (80, 140, 230))
    rect(img, 7, 2, 8, 3, (150, 156, 164))
    for y in range(7, 14):
        put(img, 6, y, (255, 255, 255))
        put(img, 10, y, (190, 196, 204))
    outline(img, (40, 50, 70))
    save(img, ITEM, "hydrogen")


def matrix(name, color):
    img = clear()
    # 斜着看的玻璃立方体：顶面亮、左面中、右面暗，棱是浅色，里面一颗发光的核心
    light, mid, dark = shade(color, 1.3), color, shade(color, 0.68)
    slope = 0.54
    for y in range(N):
        for x in range(N):
            dx = abs(x - 7.5)
            if dx > 6.6 or y < 1 + dx * slope or y > 14.6 - dx * slope:
                continue
            if y <= 8 - dx * slope:
                c = light
            elif x < 7.5:
                c = mid
            else:
                c = dark
            put(img, x, y, c, 235)
    # 三条正面的棱
    edge = mix(color, (255, 255, 255), 0.65)
    for y in range(8, 15):
        put(img, 7, y, edge)
    for i in range(7):
        put(img, 7 - i, int(round(8 - i * slope)), edge)
        put(img, 8 + i, int(round(8 - i * slope)), edge)
    for y in range(6, 12):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, (y - 8.8) * 1.2)
            if d < 2.4:
                put(img, x, y, mix((255, 255, 255), mix(color, (255, 255, 255), 0.3), d / 2.4))
    outline(img, shade(color, 0.32))
    save(img, ITEM, name)


def main():
    machine_casing()
    tesla_tower()
    wind_turbine()
    thermal_plant()
    accumulator()
    gear()
    magnetic_coil()
    circuit_board()
    electric_motor()
    steel()
    prism()
    plasma_exciter()
    energetic_graphite()
    droplet("water", (140, 200, 255), (40, 110, 220), 230)
    droplet("crude_oil", (70, 60, 56), (16, 12, 12))
    droplet("refined_oil", (255, 210, 110), (200, 110, 30))
    hydrogen()
    matrix("electromagnetic_matrix", (60, 110, 230))
    matrix("energy_matrix", (220, 60, 50))


if __name__ == "__main__":
    main()
