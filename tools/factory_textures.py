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
ENTITY = os.path.join(os.path.dirname(BLOCK), "entity")
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


def arc_smelter():
    rng = random.Random(106)
    steel = (78, 72, 70)
    side = casing(steel, rng, seam=False)
    # 两排散热口，口子里透着一点炉温
    for y in (5, 10):
        for x in range(3, 13):
            put(side, x, y, (34, 28, 26))
            put(side, x, y + 1, (120, 60, 34) if x % 3 else (90, 46, 30))
    save(side, BLOCK, "arc_smelter_side")

    for name, hot in (("arc_smelter_top", False), ("arc_smelter_top_on", True)):
        top = casing(shade(steel, 1.1), rng, seam=False)
        # 坩埚：一圈耐火砖，中间是金属液
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.2 <= d < 6.2:
                    put(top, x, y, shade((150, 120, 100), 1.05 - 0.12 * (d - 4.2) + rng.uniform(-0.06, 0.06)))
                elif d < 4.2:
                    if hot:
                        put(top, x, y, mix((255, 240, 170), (240, 110, 30), d / 4.2))
                    else:
                        put(top, x, y, shade((46, 40, 38), 1 + rng.uniform(-0.1, 0.1)))
        save(top, BLOCK, name)

    for name, arc in (("arc_smelter_front", False), ("arc_smelter_front_on", True)):
        img = side.copy()
        # 观察窗：两根石墨电极，通电时中间拉出电弧
        rect(img, 3, 3, 12, 12, (40, 38, 38))
        rect(img, 4, 4, 11, 11, (22, 18, 18))
        if arc:
            # 电极尖端之间的一团光，底部映着炉膛
            for y in range(4, 12):
                for x in range(4, 12):
                    d = math.hypot(x - 7.5, (y - 9.0) * 1.3)
                    if d < 4.2:
                        put(img, x, y, mix((255, 236, 190), (150, 54, 22), d / 4.2))
        for y in range(4, 9):
            put(img, 5, y, (70, 70, 76))
            put(img, 10, y, (70, 70, 76))
        if arc:
            # 电弧的折线
            for (x, y) in ((5, 9), (6, 9), (7, 10), (8, 9), (9, 10), (10, 9)):
                put(img, x, y, (235, 248, 255))
        rect(img, 3, 13, 12, 13, (120, 116, 112))
        put(img, 11, 13, (90, 230, 120) if arc else (40, 60, 48))
        save(img, BLOCK, name)


def assembler(suffix="", base=(70, 80, 92), waist=CYAN_DIM, light=CYAN, seed=107):
    """制造台：Mk.II 绿色涂装、Mk.III 蓝色涂装，腰线和指示灯跟着换色。"""
    name = "assembler" + suffix
    rng = random.Random(seed)
    side = casing(base, rng, seam=False)
    # 一道腰线和检修面板
    for x in range(1, N - 1):
        put(side, x, 4, waist)
    rect(side, 4, 7, 11, 12, shade(base, 0.82))
    for (x, y) in ((4, 7), (11, 7), (4, 12), (11, 12)):
        put(side, x, y, shade(base, 1.3))
    save(side, BLOCK, name + "_side")

    top = casing(shade(base, 1.15), rng, seam=False)
    # 俯视的机械臂转台
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.6:
                put(top, x, y, shade((120, 128, 136), 1.15 - 0.08 * d))
    rect(top, 7, 2, 8, 7, (230, 170, 40))
    rect(top, 7, 2, 11, 3, (230, 170, 40))
    put(top, 7, 7, (40, 44, 50))
    put(top, 8, 8, (40, 44, 50))
    if suffix:
        # 转台外面一圈档位的颜色
        for y in range(N):
            for x in range(N):
                if 4.6 <= math.hypot(x - 7.5, y - 7.5) < 5.4:
                    put(top, x, y, waist)
    save(top, BLOCK, name + "_top")

    for texture, lit in ((name + "_front", False), (name + "_front_on", True)):
        img = side.copy()
        # 舱门：里面是黄色的机械臂，工作时舱内亮灯
        rect(img, 3, 5, 12, 13, (36, 40, 46))
        rect(img, 4, 6, 11, 12, (60, 90, 104) if lit else (24, 28, 32))
        arm = (240, 180, 50) if lit else (130, 100, 40)
        for (x, y) in ((5, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 8), (10, 9)):
            put(img, x, y, arm)
        put(img, 10, 10, (200, 200, 210))
        for x in range(4, 12):
            put(img, x, 12, (50, 54, 60))
        for i, x in enumerate((4, 6, 8)):
            put(img, x, 2, light if lit and i < 2 else waist)
        save(img, BLOCK, texture)


def plane_smelter():
    """位面熔炉：深紫灰的机壳，观察窗里是翻滚的紫色等离子，顶上一圈约束环。"""
    rng = random.Random(160)
    steel = (70, 62, 84)
    violet = (176, 96, 255)
    side = casing(steel, rng, seam=False)
    for y in (4, 11):
        for x in range(3, 13):
            put(side, x, y, (30, 24, 38))
            put(side, x, y + 1, shade(violet, 0.45) if x % 3 else shade(violet, 0.3))
    for y in range(2, 14):
        put(side, 1, y, shade(steel, 1.3))
        put(side, 14, y, shade(steel, 0.7))
    save(side, BLOCK, "plane_smelter_side")

    for texture, hot in (("plane_smelter_top", False), ("plane_smelter_top_on", True)):
        top = casing(shade(steel, 1.1), rng, seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.5 <= d < 6.3:
                    put(top, x, y, shade((120, 116, 132), 1.1 - 0.1 * (d - 4.5)))
                elif d < 4.5:
                    put(top, x, y, mix((255, 230, 255), violet, d / 4.5) if hot else shade((38, 30, 48), 1 + rng.uniform(-0.1, 0.1)))
        for a in range(4):
            t = a * math.pi / 2 + math.pi / 4
            put(top, int(round(7.5 + math.cos(t) * 5.4)), int(round(7.5 + math.sin(t) * 5.4)), violet if hot else shade(violet, 0.4))
        save(top, BLOCK, texture)

    for texture, hot in (("plane_smelter_front", False), ("plane_smelter_front_on", True)):
        img = side.copy()
        rect(img, 3, 3, 12, 12, (36, 32, 44))
        rect(img, 4, 4, 11, 11, (16, 12, 22))
        if hot:
            for y in range(4, 12):
                for x in range(4, 12):
                    d = math.hypot(x - 7.5, y - 7.5)
                    swirl = math.sin(math.atan2(y - 7.5, x - 7.5) * 3 + d * 1.3)
                    if d < 4.3:
                        put(img, x, y, mix((255, 236, 255), shade(violet, 0.6 + 0.25 * swirl), d / 4.3))
        rect(img, 3, 13, 12, 13, (120, 112, 132))
        put(img, 11, 13, violet if hot else (60, 40, 80))
        save(img, BLOCK, texture)


def chemical_plant():
    """化工厂：白色涂装，正面是反应釜的观察窗（绿色的溶液），顶上两个圆形的釜盖和一根排气管。"""
    rng = random.Random(170)
    white = (188, 194, 198)
    liquid = (120, 214, 96)
    side = casing(white, rng, seam=False)
    # 一根横管和一道黄色的警示条
    for x in range(1, N - 1):
        put(side, x, 3, (120, 126, 132))
        put(side, x, 4, (150, 156, 162))
    hazard(side, 13, 13)
    rect(side, 4, 6, 11, 11, shade(white, 0.85))
    for (x, y) in ((4, 6), (11, 6), (4, 11), (11, 11)):
        put(side, x, y, shade(white, 1.15))
    save(side, BLOCK, "chemical_plant_side")

    top = casing(shade(white, 1.02), rng, seam=False)
    for (cx, cy, r) in ((5.0, 9.5, 3.4), (10.5, 9.5, 3.4)):
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - cx, y - cy)
                if d < r:
                    put(top, x, y, shade((150, 158, 166), 1.1 - 0.08 * d) if d > r - 1 else shade((112, 120, 128), 1.1 - 0.05 * d))
    rect(top, 3, 2, 5, 4, (90, 94, 100))
    put(top, 4, 3, (40, 42, 46))
    save(top, BLOCK, "chemical_plant_top")

    for texture, lit in (("chemical_plant_front", False), ("chemical_plant_front_on", True)):
        img = side.copy()
        rect(img, 3, 5, 12, 12, (70, 76, 82))
        rect(img, 4, 6, 11, 11, (20, 26, 24))
        level = 8
        for y in range(level, 12):
            for x in range(4, 12):
                put(img, x, y, shade(liquid, (1.0 if lit else 0.45) - 0.06 * (y - level)))
        if lit:
            for (x, y) in ((5, 7), (8, 6), (10, 7), (6, 9), (9, 10)):
                put(img, x, y, (220, 255, 210))
        put(img, 11, 12, (90, 230, 120) if lit else (40, 60, 48))
        save(img, BLOCK, texture)


def fractionator():
    """分馏塔：一根竖着的玻璃管，里面是淡绿色的氢，工作时管芯透出重氢的青光。"""
    rng = random.Random(175)
    steel = (96, 104, 112)
    hydrogen = (170, 236, 150)
    side = casing(steel, rng, seam=False)
    for y in range(1, 15):
        for x in range(5, 11):
            put(side, x, y, shade(hydrogen, 0.55 + 0.05 * (x - 5) if x < 8 else 0.75 - 0.05 * (x - 8)))
        put(side, 4, y, shade(steel, 1.3))
        put(side, 11, y, shade(steel, 0.6))
    for y in (2, 7, 12):
        rect(side, 4, y, 11, y, shade(steel, 0.8))
    save(side, BLOCK, "fractionator_side")

    top = casing(shade(steel, 1.1), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.5:
                put(top, x, y, shade(hydrogen, 0.8 - 0.06 * d))
            elif d < 4.6:
                put(top, x, y, shade(steel, 1.3))
    save(top, BLOCK, "fractionator_top")

    for texture, lit in (("fractionator_front", False), ("fractionator_front_on", True)):
        img = side.copy()
        if lit:
            for y in range(1, 15):
                for x in range(6, 10):
                    put(img, x, y, mix((210, 255, 255), (90, 220, 236), abs(x - 7.5) / 2))
            for y in (2, 7, 12):
                rect(img, 4, y, 11, y, shade(steel, 0.8))
        put(img, 13, 13, (90, 230, 120) if lit else (40, 60, 48))
        save(img, BLOCK, texture)


def advanced_mining_machine():
    """高级采矿机：深蓝灰的机壳、蓝色的警示条，出料口更宽。"""
    rng = random.Random(180)
    blue = (64, 84, 120)
    side = casing(blue, rng, seam=False)
    rect(side, 2, 3, 13, 10, (40, 46, 58))
    for y in range(4, 10, 2):
        for x in range(3, 13):
            put(side, x, y, (28, 32, 42))
    hazard(side, 12, 13, (90, 160, 240), (32, 36, 46))
    save(side, BLOCK, "advanced_mining_machine_side")

    top = casing(shade(blue, 0.95), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.2:
                a = math.atan2(y - 7.5, x - 7.5)
                fin = math.cos(a * 8) > 0
                put(top, x, y, shade((96, 110, 136), 1.15 - 0.05 * d) if fin or d < 2.5 else (44, 50, 64))
    rect(top, 7, 7, 8, 8, CYAN)
    save(top, BLOCK, "advanced_mining_machine_top")

    for texture, lit in (("advanced_mining_machine_front", False), ("advanced_mining_machine_front_on", True)):
        img = side.copy()
        rect(img, 2, 3, 13, 10, (24, 28, 36))
        for y in (4, 6, 8):
            for x in range(3, 13):
                put(img, x, y, (66, 72, 86))
        arrow = CYAN if lit else CYAN_DIM
        for (x, y) in ((7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (4, 7), (11, 7), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7),
                       (7, 8), (8, 8), (7, 9), (8, 9)):
            put(img, x, y, arrow)
        save(img, BLOCK, texture)


def factory_anchor():
    """工厂锚：厚重的深色基座，四面各一道青色的光槽，顶上是发光的锚环。"""
    rng = random.Random(185)
    base = (48, 54, 64)
    side = casing(base, rng, seam=False)
    for y in range(3, 13):
        put(side, 7, y, CYAN)
        put(side, 8, y, mix(CYAN, (255, 255, 255), 0.4))
        put(side, 6, y, CYAN_DIM)
        put(side, 9, y, CYAN_DIM)
    for x in range(4, 12):
        put(side, x, 13, shade(base, 1.4))
    save(side, BLOCK, "factory_anchor_side")

    top = casing(shade(base, 1.1), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.6 <= d < 5.2:
                put(top, x, y, mix(CYAN, (255, 255, 255), 0.25 if 4.1 < d < 4.7 else 0))
            elif d < 2.2:
                put(top, x, y, (230, 252, 255))
    save(top, BLOCK, "factory_anchor_top")


def hazard(img, y0, y1, a=(214, 172, 46), b=(40, 40, 44)):
    """黄黑相间的警示条。"""
    for y in range(y0, y1 + 1):
        for x in range(N):
            put(img, x, y, a if ((x + y) // 2) % 2 == 0 else b)


def mining_machine():
    rng = random.Random(108)
    yellow = (200, 160, 52)
    side = casing(yellow, rng, seam=False)
    # 中间一块深色的检修面板，下沿一道警示条
    rect(side, 3, 3, 12, 10, (64, 66, 72))
    for y in range(4, 10, 2):
        for x in range(4, 12):
            put(side, x, y, (46, 48, 54))
    hazard(side, 12, 13)
    save(side, BLOCK, "mining_machine_side")

    top = casing(shade(yellow, 0.9), rng, seam=False)
    # 俯视的电机：一圈散热片围着转轴
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.5:
                a = math.atan2(y - 7.5, x - 7.5)
                fin = math.cos(a * 6) > 0
                put(top, x, y, shade((84, 88, 96), 1.15 - 0.05 * d) if fin or d < 2.5 else (52, 54, 60))
    rect(top, 7, 7, 8, 8, (180, 184, 190))
    save(top, BLOCK, "mining_machine_top")

    bottom = new((60, 60, 64))
    noise(bottom, rng, 0.05)
    # 钻头：螺旋的刃
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            if d < 7 and math.sin(a * 3 + d * 0.9) > 0.3:
                put(bottom, x, y, shade((170, 172, 178), 1.1 - 0.06 * d))
    frame(bottom, (90, 90, 96), (40, 40, 44))
    save(bottom, BLOCK, "mining_machine_bottom")

    for name, lit in (("mining_machine_front", False), ("mining_machine_front_on", True)):
        img = side.copy()
        # 出料口：滚筒和一个往外的箭头
        rect(img, 3, 3, 12, 10, (30, 32, 36))
        for y in (4, 6, 8):
            for x in range(4, 12):
                put(img, x, y, (70, 72, 78))
        arrow = CYAN if lit else CYAN_DIM
        for (x, y) in ((7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)):
            put(img, x, y, arrow)
        save(img, BLOCK, name)


def water_pump():
    rng = random.Random(109)
    base = (70, 96, 120)
    side = casing(base, rng, seam=False)
    # 一根竖直的进水管
    for y in range(1, 15):
        put(side, 6, y, (110, 150, 190))
        put(side, 7, y, (140, 180, 215))
        put(side, 8, y, (110, 150, 190))
        put(side, 9, y, (80, 110, 140))
    for y in (4, 11):
        rect(side, 5, y, 10, y, (60, 64, 70))
    save(side, BLOCK, "water_pump_side")

    top = casing(shade(base, 1.1), rng, seam=False)
    # 电机风扇的格栅
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.5:
                put(top, x, y, (40, 48, 58) if (x + y) % 2 == 0 else (90, 104, 120))
    save(top, BLOCK, "water_pump_top")

    bottom = new((50, 58, 66))
    for y in range(2, 14):
        for x in range(2, 14):
            put(bottom, x, y, (30, 36, 42) if y % 2 == 0 else (80, 110, 140))
    frame(bottom, (90, 100, 110), (30, 34, 40))
    save(bottom, BLOCK, "water_pump_bottom")

    for name, lit in (("water_pump_front", False), ("water_pump_front_on", True)):
        img = side.copy()
        # 水位计和出水口
        rect(img, 3, 3, 6, 12, (36, 40, 46))
        level = 10 if lit else 5
        for y in range(4, 12):
            if 12 - y <= level - 3:
                put(img, 4, y, (90, 170, 255) if lit else (50, 90, 130))
                put(img, 5, y, (60, 140, 230) if lit else (40, 70, 110))
        rect(img, 9, 6, 13, 10, (36, 40, 46))
        rect(img, 10, 7, 12, 9, (20, 24, 30))
        put(img, 11, 8, (120, 200, 255) if lit else (40, 70, 100))
        save(img, BLOCK, name)


def oil_extractor():
    rng = random.Random(110)
    steel = (72, 70, 74)
    side = casing(steel, rng, seam=False)
    hazard(side, 2, 2, (210, 120, 40), (40, 38, 40))
    for y in range(5, 13, 3):
        rect(side, 3, y, 12, y, shade(steel, 0.75))
    save(side, BLOCK, "oil_extractor_side")

    for name, lit in (("oil_extractor_front", False), ("oil_extractor_front_on", True)):
        img = side.copy()
        # 压力表和一排指示灯
        rect(img, 4, 4, 11, 11, (34, 32, 34))
        for y in range(5, 11):
            for x in range(5, 11):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 2.9:
                    put(img, x, y, (230, 226, 210))
        put(img, 7, 6, (200, 40, 30))
        put(img, 7, 7, (60, 60, 60))
        put(img, 8, 8, (60, 60, 60))
        for i, x in enumerate((5, 7, 9)):
            put(img, x, 13, (90, 230, 120) if lit else (40, 60, 48))
        save(img, BLOCK, name)

    beam = clear()
    # 井架的钢梁：两条边加斜撑，留空的地方透明
    for y in range(N):
        put(beam, 0, y, (150, 60, 40))
        put(beam, 1, y, (196, 84, 52))
        put(beam, 14, y, (196, 84, 52))
        put(beam, 15, y, (150, 60, 40))
        x = y if y < N else 0
        put(beam, x, y, (176, 74, 48))
        put(beam, 15 - x, y, (176, 74, 48))
    save(beam, BLOCK, "oil_extractor_beam")

    pipe = new((100, 96, 92))
    noise(pipe, rng, 0.04)
    for y in range(N):
        put(pipe, 0, y, (140, 136, 130))
        put(pipe, 15, y, (60, 58, 56))
    for y in (3, 11):
        rect(pipe, 0, y, 15, y, (60, 58, 56))
    save(pipe, BLOCK, "oil_extractor_pipe")


def oil_refinery():
    rng = random.Random(111)
    steel = (96, 92, 88)
    side = casing(steel, rng, seam=False)
    # 横着的几根油管
    for y in (4, 9):
        rect(side, 1, y, 14, y + 1, (150, 80, 50))
        rect(side, 1, y + 1, 14, y + 1, (110, 56, 36))
    save(side, BLOCK, "oil_refinery_side")

    top = casing(shade(steel, 1.1), rng, seam=False)
    save(top, BLOCK, "oil_refinery_top")

    for name, lit in (("oil_refinery_front", False), ("oil_refinery_front_on", True)):
        img = side.copy()
        # 加热炉的观察窗
        rect(img, 4, 3, 11, 11, (40, 38, 38))
        for y in range(4, 11):
            for x in range(5, 11):
                if lit:
                    t = (y - 4) / 6
                    put(img, x, y, mix((255, 200, 90), (220, 80, 30), t))
                else:
                    put(img, x, y, (30, 26, 24))
        for x in (6, 8, 10):
            for y in range(4, 11):
                put(img, x, y, (64, 60, 58))
        put(img, 12, 13, (90, 230, 120) if lit else (40, 60, 48))
        save(img, BLOCK, name)

    column = new((206, 208, 210))
    noise(column, rng, 0.03)
    # 分馏塔：保温层的环箍和竖直的接缝
    for y in range(0, N, 4):
        rect(column, 0, y, 15, y, (150, 154, 158))
    for y in range(N):
        put(column, 0, y, (230, 232, 234))
        put(column, 15, y, (160, 164, 168))
        put(column, 7, y, (186, 188, 190))
    save(column, BLOCK, "oil_refinery_column")


def crude_oil_seep():
    rng = random.Random(112)
    top = new((20, 16, 16))
    # 黑油面，带一点彩虹色的油膜和气泡
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.8:
                put(top, x, y, shade((60, 44, 30), 1 + rng.uniform(-0.15, 0.1)))
            else:
                sheen = math.sin(x * 0.9 + y * 0.6) * 0.5 + 0.5
                if rng.random() < 0.08:
                    c = mix((90, 60, 140), (40, 120, 110), sheen)
                else:
                    c = shade((18, 14, 16), 1 + rng.uniform(-0.2, 0.3))
                put(top, x, y, c)
    for (x, y) in ((5, 6), (9, 9), (10, 5)):
        put(top, x, y, (120, 110, 120))
        put(top, x + 1, y, (60, 56, 62))
    save(top, BLOCK, "crude_oil_seep_top")

    side = new((74, 54, 38))
    noise(side, rng, 0.12)
    # 往下渗的油迹
    for x in range(N):
        depth = 2 + int(abs(math.sin(x * 1.7)) * 6) + rng.randint(0, 2)
        for y in range(depth):
            put(side, x, y, shade((22, 18, 18), 1 + rng.uniform(-0.2, 0.2)))
    save(side, BLOCK, "crude_oil_seep_side")


# ---- 物流

BELT_RUBBER = (44, 44, 50)
BELT_AMBER = (214, 168, 58)
BELT_STEEL = (112, 116, 124)
# 三档的颜色和戴森球计划一样：黄、绿、蓝
BELT_GREEN = (92, 198, 104)
BELT_BLUE = (76, 148, 242)
TIERS = (("", BELT_AMBER), ("_mk2", BELT_GREEN), ("_mk3", BELT_BLUE))


def belt_tile(accent=BELT_AMBER, period=8):
    """一格带面（朝上为前进方向）：深色橡胶带，人字纹每 period 像素一道，周期能整除 16，正好平铺。"""
    tile = new(BELT_RUBBER)
    for y in range(N):
        for x in range(N):
            tip = abs(x - 7.5) // 2
            row = (y - tip) % period
            if row == 0 or (period > 8 and row == 1):
                put(tile, x, y, accent)
            elif row == 1 or (period > 8 and row == 2):
                put(tile, x, y, shade(accent, 0.6))
            elif (x + y) % 5 == 0:
                put(tile, x, y, shade(BELT_RUBBER, 1.25))
        put(tile, 0, y, shade(BELT_RUBBER, 0.7))
        put(tile, 15, y, shade(BELT_RUBBER, 0.7))
    return tile


def conveyor_belt(suffix="", accent=BELT_AMBER, step=1, period=8):
    """step 是每帧往前挪的像素：Mk.I 每秒 20 像素（1.25 格），和物品的速度一样；Mk.II 两倍，Mk.III 五倍。
    Mk.III 一帧挪 5 像素，人字纹改成 16 像素一道，每帧的位移小于半个周期，看起来才是往前走。"""
    name = "conveyor_belt" + suffix
    tile = belt_tile(accent, period)
    frames = N // math.gcd(N, step)
    sheet = Image.new("RGBA", (N, N * frames))
    for k in range(frames):
        for y in range(N):
            for x in range(N):
                sheet.putpixel((x, k * N + y), tile.getpixel((x, (y + k * step) % N)))
    save(sheet, BLOCK, name + "_surface")
    with open(os.path.join(BLOCK, name + "_surface.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "animation": {\n    "frametime": 1\n  }\n}\n')

    # 弯道：从西边进来、绕西北角拐向北的四分之一圆环，人字纹变成放射状的条纹
    curve = new(shade(BELT_STEEL, 0.8))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5, y + 0.5)
            if 1 <= d <= 15:
                angle = math.degrees(math.atan2(x + 0.5, y + 0.5))
                stripe = int(angle // 11.25) % 4
                color = accent if stripe == 0 else BELT_RUBBER
                if d < 2 or d > 14:
                    color = shade(BELT_RUBBER, 0.7)
                put(curve, x, y, color)
    save(curve, BLOCK, name + "_curve_left")
    save(curve.transpose(Image.FLIP_LEFT_RIGHT), BLOCK, name + "_curve_right")

    # 侧栏：钢条，中间一道档位的颜色，一排螺栓
    rail = new(BELT_STEEL)
    noise(rail, random.Random(120), 0.04)
    for x in range(N):
        put(rail, x, 12, shade(BELT_STEEL, 1.3))
        put(rail, x, 13, accent)
        put(rail, x, 14, shade(accent, 0.75))
        put(rail, x, 15, shade(BELT_STEEL, 0.6))
    for x in range(2, N, 4):
        put(rail, x, 13, shade(BELT_STEEL, 1.4))
    for y in range(N):
        put(rail, 6, y, shade(BELT_STEEL, 1.25))
    save(rail, BLOCK, name + "_rail")
    if suffix:
        return

    # 底架：深色钢板，侧面看得到一排滚筒
    frame_tex = new(shade(BELT_STEEL, 0.55))
    noise(frame_tex, random.Random(121), 0.05)
    for x in range(N):
        put(frame_tex, x, 14, (150, 154, 160) if x % 4 in (1, 2) else (60, 62, 68))
        put(frame_tex, x, 15, (90, 92, 98) if x % 4 in (1, 2) else (40, 42, 46))
    save(frame_tex, BLOCK, "conveyor_belt_frame")


def sorter(suffix="", accent=BELT_AMBER):
    rng = random.Random(130)
    # 底座顶面：深色钢板，四边一圈档位的颜色，前后各一个指向前方（贴图上方）的箭头
    top = new(shade(BELT_STEEL, 0.7))
    noise(top, rng, 0.04)
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            put(top, x, y, shade(accent, 0.85))
    for row, y in enumerate((3, 4)):
        for x in range(7 - row - 1, 9 + row + 1):
            put(top, x, y, accent)
    for row, y in enumerate((11, 12)):
        for x in range(7 - row, 9 + row):
            put(top, x, y, shade(accent, 0.7))
    save(top, BLOCK, "sorter" + suffix + "_top")
    # 手臂：细条纹的钢，贴图拉伸了也看不出来
    arm = new(shade(BELT_STEEL, 1.05))
    for y in range(N):
        for x in range(N):
            if y % 4 == 0:
                put(arm, x, y, accent)
            elif y % 4 == 1:
                put(arm, x, y, shade(accent, 0.65))
            elif (x + y) % 3 == 0:
                put(arm, x, y, shade(BELT_STEEL, 0.9))
    save(arm, BLOCK, "sorter_arm" + suffix)
    if suffix:
        return
    # 底座侧面：底下四行是黄黑相间的警示条
    side = new(shade(BELT_STEEL, 0.75))
    noise(side, rng, 0.04)
    hazard(side, 12, 15)
    for x in range(N):
        put(side, x, 11, shade(BELT_STEEL, 1.2))
    save(side, BLOCK, "sorter_side")
    # 立柱：钢管，中间一道青色指示灯
    pillar = new(BELT_STEEL)
    noise(pillar, rng, 0.04)
    for y in range(N):
        put(pillar, 5, y, shade(BELT_STEEL, 1.3))
        put(pillar, 10, y, shade(BELT_STEEL, 0.65))
    for y in range(7, 9):
        for x in range(7, 9):
            put(pillar, x, y, CYAN)
    save(pillar, BLOCK, "sorter_pillar")


def splitter():
    rng = random.Random(135)
    # 顶面：钢板，四边各一个接传送带的口（橡胶色），中间是转盘
    top = new(shade(BELT_STEEL, 0.75))
    noise(top, rng, 0.04)
    for i in range(5, 11):
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            put(top, x, y, BELT_RUBBER)
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            put(top, x, y, shade(BELT_AMBER, 0.8))
    frame(top, shade(BELT_STEEL, 1.15), shade(BELT_STEEL, 0.45))
    save(top, BLOCK, "splitter_top")
    # 侧面：中间一个开口，下面一排警示条
    side = new(shade(BELT_STEEL, 0.8))
    noise(side, rng, 0.04)
    rect(side, 4, 10, 11, 15, BELT_RUBBER)
    rect(side, 4, 10, 11, 10, shade(BELT_STEEL, 1.3))
    for x in range(N):
        put(side, x, 9, shade(BELT_STEEL, 1.2))
    save(side, BLOCK, "splitter_side")
    # 转盘：深色的圆盘，四道琥珀色的分隔，中间青色的指示灯
    hub = new(shade(BELT_STEEL, 0.55))
    noise(hub, rng, 0.04)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.5 <= d < 7:
                put(hub, x, y, shade(BELT_STEEL, 1.15))
            elif d < 5.5 and (abs(x - y) < 1 or abs(x + y - 15) < 1):
                put(hub, x, y, BELT_AMBER)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(hub, x, y, CYAN)
    save(hub, BLOCK, "splitter_hub")


def storage_tank():
    rng = random.Random(145)
    steel = shade(BELT_STEEL, 0.85)
    # 侧面：四角立柱、上下钢箍，中间是玻璃（全透明，加几道反光；只用全透明和不透明的像素，模型才走镂空）
    side = clear()
    for y in range(N):
        for x in range(N):
            post = x < 2 or x > 13
            band = y < 2 or y > 13
            if post or band:
                f = 1 + rng.uniform(-0.05, 0.05)
                if x in (0, 15) or y in (0, 15):
                    f *= 0.7
                elif x in (1, 14) or y in (1, 14):
                    f *= 1.15
                put(side, x, y, shade(steel, f))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(side, x - 1 if x > 7 else x, y - 1 if y > 7 else y, steel)
    for i in range(4):
        put(side, 4 + i, 11 - i * 2, (232, 240, 246))
        put(side, 4 + i, 10 - i * 2, (232, 240, 246))
    put(side, 11, 4, (232, 240, 246))
    put(side, 11, 5, (232, 240, 246))
    # 刻度：右边立柱上几道深色的刻线
    for y in range(3, 13, 3):
        put(side, 13, y, shade(steel, 0.5))
    save(side, BLOCK, "storage_tank_side")
    # 顶盖：圆形的检修口，一圈螺栓，一角是青色的指示灯
    top = new(steel)
    noise(top, rng, 0.04)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.5:
                put(top, x, y, shade(steel, 0.7 if d > 3.5 else 0.85))
            elif d < 5.5:
                put(top, x, y, shade(steel, 1.2))
    for a in range(8):
        t = a * math.pi / 4
        put(top, int(round(7.5 + math.cos(t) * 6.2)), int(round(7.5 + math.sin(t) * 6.2)), shade(steel, 1.45))
    frame(top, shade(steel, 1.15), shade(steel, 0.45))
    put(top, 13, 2, CYAN)
    save(top, BLOCK, "storage_tank_top")
    # 液柱的贴图：灰度，按流体颜色着色；几串上升的气泡
    fluid = new((236, 236, 236))
    for y in range(N):
        for x in range(N):
            v = 228 + 18 * math.sin((x + y * 0.5) * 0.7) + rng.uniform(-6, 6)
            put(fluid, x, y, (clamp(v),) * 3)
    for (x, y) in ((3, 12), (4, 8), (3, 4), (10, 14), (11, 9), (10, 3), (7, 6)):
        put(fluid, x, y, (255, 255, 255))
        put(fluid, x + 1, y + 1, (200, 200, 200))
    save(fluid, ENTITY, "storage_tank_fluid")


def storage_depot():
    rng = random.Random(140)
    side = new(shade(BELT_STEEL, 0.8))
    noise(side, rng, 0.04)
    # 竖条加强筋，中间一道琥珀色的 Mk.I 色带
    for y in range(2, 14):
        for x in (4, 8, 12):
            put(side, x - 1, y, shade(BELT_STEEL, 1.15))
            put(side, x, y, shade(BELT_STEEL, 0.6))
    for x in range(2, 14):
        put(side, x, 7, BELT_AMBER)
        put(side, x, 8, shade(BELT_AMBER, 0.7))
    rect(side, 0, 0, 15, 1, shade(BELT_STEEL, 0.55))
    rect(side, 0, 14, 15, 15, shade(BELT_STEEL, 0.55))
    rect(side, 0, 0, 1, 15, shade(BELT_STEEL, 0.55))
    rect(side, 14, 0, 15, 15, shade(BELT_STEEL, 0.55))
    frame(side, shade(BELT_STEEL, 1.1), shade(BELT_STEEL, 0.4))
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rivet(side, x, y, BELT_STEEL)
    save(side, BLOCK, "storage_depot_side")
    # 顶盖：舱门加格栅，一角是青色的指示灯
    top = new(shade(BELT_STEEL, 0.7))
    noise(top, rng, 0.04)
    rect(top, 3, 3, 12, 12, shade(BELT_STEEL, 0.45))
    for y in range(4, 12):
        for x in range(4, 12):
            put(top, x, y, shade(BELT_STEEL, 0.9) if y % 2 == 0 else shade(BELT_STEEL, 0.35))
    frame(top, shade(BELT_STEEL, 1.1), shade(BELT_STEEL, 0.4))
    put(top, 13, 2, CYAN)
    put(top, 2, 13, BELT_AMBER)
    save(top, BLOCK, "storage_depot_top")


def matrix_lab():
    rng = random.Random(150)
    # 底座侧面：下面五行是机壳，一排青色的灯
    side = casing(DARK, rng, seam=False)
    for x in range(1, N - 1):
        put(side, x, 10, shade(DARK, 1.3))
    for x in range(2, N - 2, 3):
        put(side, x, 13, CYAN)
        put(side, x + 1, 13, CYAN_DIM)
    save(side, BLOCK, "matrix_lab_side")
    # 正面多一块小屏幕
    front = side.copy()
    rect(front, 4, 11, 11, 14, (14, 30, 38))
    for x in range(5, 11):
        put(front, x, 12, CYAN if x % 2 else CYAN_DIM)
    put(front, 6, 13, CYAN)
    put(front, 9, 13, (229, 88, 63))
    save(front, BLOCK, "matrix_lab_front")
    # 底座顶面：玻璃罩外面一圈，四角是螺栓
    top = casing(MID, rng, seam=False)
    rect(top, 2, 2, 13, 13, shade(MID, 0.55))
    for y in range(3, 13):
        for x in range(3, 13):
            put(top, x, y, shade(MID, 0.75) if (x + y) % 2 else shade(MID, 0.6))
    save(top, BLOCK, "matrix_lab_top")
    # 立柱和顶盖：钢，边上一道青色
    frame_tex = new(LIGHT)
    noise(frame_tex, rng, 0.04)
    for i in range(N):
        put(frame_tex, i, 0, shade(LIGHT, 1.2))
        put(frame_tex, i, 15, shade(LIGHT, 0.6))
        put(frame_tex, 0, i, CYAN_DIM)
        put(frame_tex, 15, i, shade(LIGHT, 0.7))
    for x in range(3, 13):
        put(frame_tex, x, 7, shade(LIGHT, 0.7))
        put(frame_tex, x, 8, CYAN)
    save(frame_tex, BLOCK, "matrix_lab_frame")
    # 玻璃：淡青色半透明，边上亮一些，斜着一道反光
    glass = Image.new("RGBA", (N, N), (150, 220, 240, 60))
    for i in range(N):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(glass, x, y, (190, 240, 255), 150)
    for i in range(3, 9):
        put(glass, i, 11 - i, (235, 252, 255), 120)
        put(glass, i + 1, 11 - i, (235, 252, 255), 90)
    save(glass, BLOCK, "matrix_lab_glass")
    # 核心：亮的能量方块，中间白，往外是青、蓝，四角一点红（两种矩阵的颜色）
    core = new()
    for y in range(N):
        for x in range(N):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            c = mix((255, 255, 255), CYAN, min(1, d * 1.4))
            if d > 0.75:
                c = mix(CYAN, (60, 110, 230), (d - 0.75) * 4)
            put(core, x, y, c)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(core, x, y, (220, 60, 50))
    save(core, BLOCK, "matrix_lab_core")


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


def gas_bottle(name, shoulder, body=(232, 236, 240)):
    """气瓶：浅色瓶身、彩色瓶肩（氢是蓝色，重氢是青色）。"""
    img = clear()
    rect(img, 5, 4, 10, 14, body)
    rect(img, 5, 4, 10, 6, shoulder)
    rect(img, 7, 2, 8, 3, (150, 156, 164))
    for y in range(7, 14):
        put(img, 6, y, (255, 255, 255))
        put(img, 10, y, shade(body, 0.82))
    put(img, 8, 10, shoulder)
    put(img, 8, 11, shoulder)
    outline(img, (40, 50, 70))
    save(img, ITEM, name)


def ingot(name, base, accent=None):
    """斜放的锭子：顶面亮、侧面暗。"""
    img = clear()
    for y in range(5, 12):
        for x in range(1 + (11 - y) // 2, 15 - (y - 5) // 3):
            f = 1.3 if y <= 6 else 1.0 - 0.05 * (y - 6)
            put(img, x, y, shade(base, f))
    if accent:
        for x in range(4, 11):
            put(img, x, 8, accent)
    outline(img, shade(base, 0.3))
    save(img, ITEM, name)


def crystal(name, light, dark, rng_seed):
    """一簇晶体：几根斜着的棱柱，亮面暗面分开。"""
    rng = random.Random(rng_seed)
    img = clear()
    for (cx, top, w) in ((5, 4, 2), (8, 1, 3), (11, 5, 2)):
        for y in range(top, 14):
            half = w if y > top + w else max(0, y - top)
            for x in range(cx - half, cx + half + 1):
                c = mix(light, dark, 0.15 if x <= cx else 0.65)
                put(img, x, y, shade(c, 1 + rng.uniform(-0.05, 0.05)))
    for x in range(3, 14):
        put(img, x, 14, shade(dark, 0.7))
    outline(img, shade(dark, 0.35))
    save(img, ITEM, name)


def crystal_silicon():
    """晶格硅：一片深灰带蓝光的硅晶圆，表面的晶格纹。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.6:
                c = (74, 84, 110) if (x + y) % 3 else (110, 130, 170)
                if (x - y) % 4 == 0:
                    c = (140, 170, 220)
                put(img, x, y, shade(c, 1.1 - 0.04 * d))
    rect(img, 6, 1, 9, 1, (0, 0, 0))
    for x in range(6, 10):
        put(img, x, 1, (0, 0, 0), 0)
    outline(img, (24, 28, 40))
    save(img, ITEM, "crystal_silicon")


def synthetic_diamond():
    img = clear()
    # 切割好的钻石：上面一圈台面，下面收成尖
    for y in range(3, 14):
        if y < 6:
            half = 3 + (y - 3)
        else:
            half = max(0, int(round(5.5 - (y - 6) * 0.75)))
        for x in range(8 - half, 8 + half):
            t = (x - (8 - half)) / max(1, 2 * half - 1)
            c = mix((235, 250, 255), (120, 200, 240), t)
            if y < 6 and (x + y) % 3 == 0:
                c = (255, 255, 255)
            put(img, x, y, c)
    outline(img, (40, 80, 110))
    save(img, ITEM, "synthetic_diamond")


def plastic():
    img = clear()
    # 一卷白色的塑料板
    rect(img, 2, 4, 13, 11, (220, 226, 230))
    for x in range(2, 14):
        put(img, x, 4, (250, 252, 255))
        put(img, x, 11, (170, 178, 186))
    for y in range(5, 11):
        put(img, 2, y, (196, 204, 210))
    rect(img, 9, 6, 11, 9, (180, 210, 240))
    outline(img, (60, 66, 74))
    save(img, ITEM, "plastic")


def titanium_crystal():
    crystal("titanium_crystal", (200, 220, 255), (90, 110, 190), 211)


def graphene():
    img = clear()
    # 蜂窝状的碳原子网
    for y in range(2, 14):
        for x in range(2, 14):
            hx = (x + (y // 2 % 2) * 2) % 4
            edge = hx == 0 or y % 2 == 0 and hx in (1, 2)
            put(img, x, y, (60, 64, 72) if edge else (30, 32, 38))
    for (x, y) in ((4, 4), (8, 6), (10, 10), (5, 11)):
        put(img, x, y, (150, 160, 180))
    outline(img, (12, 12, 16))
    save(img, ITEM, "graphene")


def carbon_nanotube():
    img = clear()
    # 斜着的一根管子，表面是六角网格的条纹
    for y in range(N):
        for x in range(N):
            d = abs((x - y) / math.sqrt(2))
            along = (x + y) / 2
            if d < 3.0 and 1 < along < 14:
                c = (70, 74, 84) if int(along * 2 + d * 2) % 3 else (130, 140, 160)
                put(img, x, y, shade(c, 1.15 - 0.15 * d))
    outline(img, (16, 16, 22))
    save(img, ITEM, "carbon_nanotube")


def microcrystalline_component():
    img = clear()
    # 小芯片：深色封装、四边的金色引脚、中间一块蓝色晶圆
    rect(img, 4, 4, 11, 11, (40, 42, 50))
    for i in range(5, 11, 2):
        for (x, y) in ((i, 2), (i, 3), (i, 12), (i, 13), (2, i), (3, i), (12, i), (13, i)):
            put(img, x, y, (220, 180, 70))
    rect(img, 6, 6, 9, 9, (90, 130, 200))
    put(img, 6, 6, (170, 200, 250))
    outline(img, (16, 16, 22))
    save(img, ITEM, "microcrystalline_component")


def processor():
    img = clear()
    # 大一点的芯片：绿色基板上一块银色的顶盖
    rect(img, 1, 1, 14, 14, (30, 110, 70))
    for i in range(2, 14, 2):
        put(img, i, 1, (220, 180, 70))
        put(img, i, 14, (220, 180, 70))
        put(img, 1, i, (220, 180, 70))
        put(img, 14, i, (220, 180, 70))
    rect(img, 4, 4, 11, 11, (176, 184, 192))
    rect(img, 5, 5, 10, 10, (150, 158, 168))
    put(img, 5, 5, (230, 236, 240))
    rect(img, 7, 7, 8, 8, CYAN)
    outline(img, (14, 40, 26))
    save(img, ITEM, "processor")


def particle_broadband():
    img = clear()
    # 一卷发光的扁平线缆
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.0 <= d < 6.8:
                band = int(d * 2) % 3
                c = [(80, 60, 140), (160, 120, 240), (230, 210, 255)][band]
                put(img, x, y, c)
    rect(img, 6, 6, 9, 9, (60, 64, 72))
    put(img, 7, 7, (200, 180, 255))
    outline(img, (30, 20, 50))
    save(img, ITEM, "particle_broadband")


def thruster(name="thruster", body=(150, 156, 164), flame=(255, 170, 60)):
    img = clear()
    # 喷管朝下：上面是圆柱的燃烧室，下面喇叭口，喷着火
    rect(img, 5, 1, 10, 7, body)
    for y in range(1, 8):
        put(img, 5, y, shade(body, 1.25))
        put(img, 10, y, shade(body, 0.7))
    for y in range(8, 12):
        half = 3 + (y - 8) // 2
        for x in range(8 - half, 8 + half):
            put(img, x, y, shade(body, 0.85 - 0.04 * (y - 8)))
    for y in range(12, 15):
        half = 3 - (y - 12)
        for x in range(8 - half, 8 + half):
            put(img, x, y, mix((255, 250, 220), flame, (y - 12) / 2.5))
    rect(img, 5, 3, 10, 3, shade(body, 0.6))
    outline(img, shade(body, 0.3))
    save(img, ITEM, name)


def electromagnetic_turbine():
    img = clear()
    # 涡轮：一圈蓝色的叶片，中间是铜色的轴
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            blade = math.sin(a * 6 + d * 0.5) > 0.1
            if d < 6.8 and (blade or d < 3):
                put(img, x, y, shade((90, 150, 230), 1.15 - 0.06 * d) if d >= 3 else COPPER)
    put(img, 7, 7, (240, 200, 150))
    outline(img, (20, 30, 60))
    save(img, ITEM, "electromagnetic_turbine")


def super_magnetic_ring():
    img = clear()
    # 粗圆环，一半红一半蓝（磁极），表面一道高光
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.5 <= d < 6.8:
                c = (220, 70, 60) if x < 7.5 else (70, 110, 230)
                f = 1.2 if d < 4.5 else 1.0 - 0.06 * (d - 4.5)
                put(img, x, y, shade(c, f))
    outline(img, (30, 20, 30))
    save(img, ITEM, "super_magnetic_ring")


def particle_container():
    img = clear()
    # 透明的容器罐，里面悬着一团发光的粒子，上下是金属端盖
    rect(img, 4, 2, 11, 13, (180, 210, 230))
    for y in range(4, 12):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            put(img, x, y, mix((255, 255, 220), (255, 160, 60), d / 3) if d < 2.6 else (90, 120, 150))
    rect(img, 3, 2, 12, 3, (150, 156, 164))
    rect(img, 3, 12, 12, 13, (150, 156, 164))
    outline(img, (30, 36, 46))
    save(img, ITEM, "particle_container")


def logistics_drone():
    img = clear()
    # 俯视的小无人机：白色机身、四个旋翼、青色的眼睛
    for (cx, cy) in ((3.5, 3.5), (11.5, 3.5), (3.5, 11.5), (11.5, 11.5)):
        for y in range(N):
            for x in range(N):
                if math.hypot(x - cx, y - cy) < 2.6:
                    put(img, x, y, (120, 126, 134))
    for i in range(4, 12):
        put(img, i, i, (90, 94, 100))
        put(img, i, 15 - i, (90, 94, 100))
    rect(img, 5, 5, 10, 10, (226, 230, 234))
    rect(img, 6, 5, 9, 5, (255, 255, 255))
    rect(img, 7, 7, 8, 8, CYAN)
    outline(img, (40, 44, 52))
    save(img, ITEM, "logistics_drone")


def logistics_vessel():
    img = clear()
    # 侧视的运输船：长条形的白色船身、蓝色的舱段、尾部的推进器
    rect(img, 2, 6, 12, 10, (220, 226, 232))
    for x in range(2, 13):
        put(img, x, 6, (250, 252, 255))
        put(img, x, 10, (170, 176, 184))
    rect(img, 5, 7, 9, 9, (70, 120, 200))
    for y in range(7, 10):
        put(img, 13, y, (220, 226, 232))
    put(img, 14, 8, (220, 226, 232))
    rect(img, 0, 7, 1, 9, (255, 180, 80))
    rect(img, 4, 4, 8, 5, (180, 186, 194))
    rect(img, 4, 11, 8, 12, (180, 186, 194))
    outline(img, (36, 40, 50))
    save(img, ITEM, "logistics_vessel")


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


# ---- 物流站（ADR-0015）

STATION_WHITE = (196, 202, 208)
STATION_BLUE = (70, 150, 240)
STATION_GOLD = (222, 176, 72)
ILS_STEEL = (70, 78, 92)


def logistics_station_common():
    """物流站共用的贴图：停机坪边沿的警示条、立柱、指示灯（灭和亮）。"""
    rng = random.Random(190)
    pad_side = casing(MID, rng, seam=False)
    hazard(pad_side, 5, 10)
    for x in range(N):
        put(pad_side, x, 4, shade(MID, 1.3))
        put(pad_side, x, 11, shade(MID, 0.6))
    save(pad_side, BLOCK, "logistics_station_pad_side")

    trim = new(shade(MID, 0.9))
    noise(trim, rng, 0.04)
    for y in range(N):
        put(trim, 0, y, shade(MID, 1.3))
        put(trim, N - 1, y, shade(MID, 0.55))
        if y % 4 == 0:
            for x in range(1, N - 1):
                put(trim, x, y, shade(MID, 0.7))
    save(trim, BLOCK, "logistics_station_trim")

    for name, core, rim in (("logistics_station_light", (80, 96, 104), (50, 58, 64)),
                            ("logistics_station_light_on", (220, 250, 255), STATION_BLUE)):
        img = new(rim)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 5:
                    put(img, x, y, mix(core, rim, d / 5 * 0.6))
        save(img, BLOCK, name)


def station_side(base, accent, rng, name, trim_color):
    """塔身侧面：浅色面板，中间一道竖着的灯带，上下各一条装饰线。"""
    img = casing(base, rng, seam=False)
    for y in range(1, N - 1):
        put(img, 7, y, accent)
        put(img, 8, y, mix(accent, (255, 255, 255), 0.35))
        put(img, 6, y, shade(base, 0.7))
        put(img, 9, y, shade(base, 0.7))
    for x in range(1, N - 1):
        put(img, x, 3, trim_color)
        put(img, x, 12, trim_color)
    save(img, BLOCK, name)
    return img


def station_front(base, accent, rng, name, trim_color):
    """正面：一扇带警示框的装卸舱门。"""
    img = casing(base, rng, seam=False)
    rect(img, 3, 3, 12, 13, shade(base, 0.45))
    for x in range(3, 13):
        put(img, x, 3, (214, 172, 46) if x % 2 == 0 else (40, 40, 44))
    for y in range(4, 13):
        put(img, 3, y, shade(base, 0.75))
        put(img, 12, y, shade(base, 0.75))
    # 舱门的横向百叶
    for y in range(5, 13, 2):
        for x in range(4, 12):
            put(img, x, y, shade(base, 0.6))
    for x in range(5, 11):
        put(img, x, 14, accent)
    for x in range(1, N - 1):
        put(img, x, 1, trim_color)
    save(img, BLOCK, name)


def station_pad(base, ring, rng, name):
    """停机坪顶面：深色甲板，中间一圈降落环和十字标记。"""
    img = new(base)
    noise(img, rng, 0.05)
    frame(img, shade(base, 1.4), shade(base, 0.6))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d < 6.4:
                put(img, x, y, ring)
            elif d < 1.6:
                put(img, x, y, mix(ring, (255, 255, 255), 0.4))
    for i in range(3, 13):
        if abs(i - 7.5) > 2:
            put(img, i, 7, shade(ring, 0.8))
            put(img, 7, i, shade(ring, 0.8))
    save(img, BLOCK, name)


def planetary_logistics_station():
    rng = random.Random(191)
    station_side(STATION_WHITE, STATION_BLUE, rng, "planetary_logistics_station_side", shade(STATION_BLUE, 0.8))
    station_front(STATION_WHITE, STATION_BLUE, rng, "planetary_logistics_station_front", shade(STATION_BLUE, 0.8))
    station_pad((58, 64, 72), (230, 196, 70), rng, "planetary_logistics_station_pad")


def interstellar_logistics_station():
    rng = random.Random(192)
    station_side(ILS_STEEL, STATION_BLUE, rng, "interstellar_logistics_station_side", STATION_GOLD)
    station_front(ILS_STEEL, STATION_BLUE, rng, "interstellar_logistics_station_front", STATION_GOLD)
    station_pad((44, 48, 58), STATION_GOLD, rng, "interstellar_logistics_station_pad")
    trim = new(shade(STATION_GOLD, 0.75))
    noise(trim, rng, 0.05)
    for y in range(N):
        put(trim, 0, y, shade(STATION_GOLD, 1.1))
        put(trim, N - 1, y, shade(STATION_GOLD, 0.5))
        if y % 4 == 1:
            for x in range(1, N - 1):
                put(trim, x, y, shade(STATION_GOLD, 0.55))
    save(trim, BLOCK, "interstellar_logistics_station_trim")


def orbital_collector():
    """轨道采集器：罐体是一圈圈的气体色带，导环是带青色灯的金属，进气口是格栅。"""
    rng = random.Random(193)
    core = new()
    bands = [(222, 170, 110), (196, 132, 82), (236, 200, 150), (180, 116, 74)]
    for y in range(N):
        color = bands[(y // 3) % len(bands)]
        for x in range(N):
            put(core, x, y, color)
    noise(core, rng, 0.05)
    # 罐体上的加强筋
    for y in range(N):
        put(core, 0, y, shade(MID, 1.1))
        put(core, N - 1, y, shade(MID, 0.7))
    save(core, BLOCK, "orbital_collector_core")

    ring = new(shade(MID, 1.05))
    noise(ring, rng, 0.04)
    for x in range(N):
        put(ring, x, 0, shade(MID, 1.4))
        put(ring, x, N - 1, shade(MID, 0.55))
        if x % 4 == 1:
            for y in range(6, 10):
                put(ring, x, y, CYAN)
    save(ring, BLOCK, "orbital_collector_ring")

    intake = new(DARK)
    for y in range(N):
        for x in range(N):
            if x % 3 == 0 or y % 3 == 0:
                put(intake, x, y, shade(MID, 0.9))
    frame(intake, shade(MID, 1.2), shade(DARK, 0.6))
    save(intake, BLOCK, "orbital_collector_intake")


SOLAR_BLUE = (34, 62, 128)


def solar_panel():
    """太阳能板：深蓝的电池片，银色的栅线和边框；背面是带加强筋的铝板，底座是深灰机壳。"""
    rng = random.Random(195)
    cells = new(SOLAR_BLUE)
    for y in range(N):
        for x in range(N):
            # 3×3 块电池片，左上到右下有一点渐变的反光
            sheen = 1 + 0.18 * (1 - (x + y) / (2 * N)) - 0.08 * ((x + 2 * y) % 5 == 0)
            put(cells, x, y, shade(SOLAR_BLUE, sheen))
    noise(cells, rng, 0.04)
    grid = (176, 188, 204)
    for i in range(N):
        for line in (5, 10):
            put(cells, line, i, shade(grid, 0.85))
            put(cells, i, line, shade(grid, 0.85))
    # 每片电池中间一道细细的主栅
    for cy in (2, 7, 12):
        for x in range(N):
            if x not in (5, 10):
                put(cells, x, cy, mix(get(cells, x, cy), grid, 0.35))
    frame(cells, (214, 220, 228), (132, 140, 150))
    save(cells, BLOCK, "solar_panel_cells")

    back = new((168, 174, 182))
    noise(back, rng, 0.05)
    for i in range(N):
        put(back, 7, i, (196, 202, 210))
        put(back, 8, i, (120, 126, 134))
        put(back, i, 3, (140, 146, 154))
        put(back, i, 12, (140, 146, 154))
    frame(back, (200, 206, 214), (110, 116, 124))
    save(back, BLOCK, "solar_panel_back")

    metal = new((188, 194, 202))
    noise(metal, rng, 0.04)
    for i in range(N):
        put(metal, i, 0, (222, 228, 234))
        put(metal, 0, i, (222, 228, 234))
        put(metal, i, N - 1, (124, 130, 138))
        put(metal, N - 1, i, (124, 130, 138))
    save(metal, BLOCK, "solar_panel_frame")

    base = casing(DARK, rng, seam=False)
    frame(base, shade(DARK, 1.4), shade(DARK, 0.6))
    for x in range(4, 12):
        put(base, x, 7, CYAN_DIM)
    save(base, BLOCK, "solar_panel_base")


def logistics_craft():
    """无人机和运输船立体模型用的贴图。"""
    rng = random.Random(194)
    body = new(STATION_WHITE)
    noise(body, rng, 0.04)
    frame(body, shade(STATION_WHITE, 1.15), shade(STATION_WHITE, 0.7))
    for x in range(3, 13):
        put(body, x, 7, STATION_BLUE)
        put(body, x, 8, mix(STATION_BLUE, (255, 255, 255), 0.3))
    save(body, BLOCK, "logistics_drone_body")

    rotor = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            angle = math.atan2(y - 7.5, x - 7.5)
            if d < 1.6:
                put(rotor, x, y, DARK)
            elif d < 7.6 and (math.cos(2 * angle) > 0.75 or d > 6.6):
                put(rotor, x, y, (40, 44, 50) if d <= 6.6 else (120, 126, 134))
    save(rotor, BLOCK, "logistics_drone_rotor")

    hull = new(ILS_STEEL)
    noise(hull, rng, 0.05)
    frame(hull, shade(ILS_STEEL, 1.3), shade(ILS_STEEL, 0.6))
    for x in range(N):
        put(hull, x, 4, STATION_GOLD)
        put(hull, x, 11, STATION_GOLD)
    for x in range(2, 14, 3):
        put(hull, x, 7, (180, 230, 255))
        put(hull, x, 8, (120, 190, 240))
    save(hull, BLOCK, "logistics_vessel_hull")

    engine = new(DARK)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6:
                put(engine, x, y, mix((240, 250, 255), (90, 170, 255), d / 6))
    save(engine, BLOCK, "logistics_vessel_engine")


def main():
    machine_casing()
    tesla_tower()
    wind_turbine()
    thermal_plant()
    accumulator()
    arc_smelter()
    assembler()
    assembler("_mk2", (66, 92, 78), (54, 140, 80), (110, 240, 140), 108)
    assembler("_mk3", (62, 76, 106), (60, 110, 190), (120, 190, 255), 109)
    plane_smelter()
    chemical_plant()
    fractionator()
    advanced_mining_machine()
    factory_anchor()
    mining_machine()
    water_pump()
    oil_extractor()
    oil_refinery()
    crude_oil_seep()
    conveyor_belt()
    conveyor_belt("_mk2", BELT_GREEN, 2)
    conveyor_belt("_mk3", BELT_BLUE, 5, 16)
    sorter()
    sorter("_mk2", BELT_GREEN)
    sorter("_mk3", BELT_BLUE)
    splitter()
    storage_depot()
    storage_tank()
    matrix_lab()
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
    gas_bottle("deuterium", (70, 210, 220))
    crystal_silicon()
    ingot("titanium_alloy", (150, 160, 190), (110, 200, 230))
    synthetic_diamond()
    plastic()
    droplet("sulfuric_acid", (250, 240, 140), (200, 170, 40), 235)
    graphene()
    carbon_nanotube()
    titanium_crystal()
    microcrystalline_component()
    processor()
    particle_broadband()
    thruster()
    thruster("reinforced_thruster", (110, 120, 160), (120, 200, 255))
    electromagnetic_turbine()
    super_magnetic_ring()
    particle_container()
    logistics_drone()
    logistics_vessel()
    matrix("structure_matrix", (230, 190, 50))
    matrix("information_matrix", (160, 80, 220))
    logistics_station_common()
    planetary_logistics_station()
    interstellar_logistics_station()
    orbital_collector()
    logistics_craft()
    solar_panel()


if __name__ == "__main__":
    main()
