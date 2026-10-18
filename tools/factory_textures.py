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


def assembler():
    rng = random.Random(107)
    base = (70, 80, 92)
    side = casing(base, rng, seam=False)
    # 一道青色的腰线和检修面板
    for x in range(1, N - 1):
        put(side, x, 4, CYAN_DIM)
    rect(side, 4, 7, 11, 12, shade(base, 0.82))
    for (x, y) in ((4, 7), (11, 7), (4, 12), (11, 12)):
        put(side, x, y, shade(base, 1.3))
    save(side, BLOCK, "assembler_side")

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
    save(top, BLOCK, "assembler_top")

    for name, lit in (("assembler_front", False), ("assembler_front_on", True)):
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
            put(img, x, 2, CYAN if lit and i < 2 else CYAN_DIM)
        save(img, BLOCK, name)


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


def belt_tile():
    """一格带面（朝上为前进方向）：深色橡胶带，琥珀色的人字纹每 8 格一道，周期正好能平铺。"""
    tile = new(BELT_RUBBER)
    for y in range(N):
        for x in range(N):
            tip = abs(x - 7.5) // 2
            row = (y - tip) % 8
            if row == 0:
                put(tile, x, y, BELT_AMBER)
            elif row == 1:
                put(tile, x, y, shade(BELT_AMBER, 0.6))
            elif (x + y) % 5 == 0:
                put(tile, x, y, shade(BELT_RUBBER, 1.25))
        put(tile, 0, y, shade(BELT_RUBBER, 0.7))
        put(tile, 15, y, shade(BELT_RUBBER, 0.7))
    return tile


def conveyor_belt():
    tile = belt_tile()
    # 16 帧动画，每帧往前挪一像素：每秒 20 像素，和 Mk.I 每秒 1.2 格的物品速度差不多
    sheet = Image.new("RGBA", (N, N * N))
    for k in range(N):
        for y in range(N):
            for x in range(N):
                sheet.putpixel((x, k * N + y), tile.getpixel((x, (y + k) % N)))
    save(sheet, BLOCK, "conveyor_belt_surface")
    with open(os.path.join(BLOCK, "conveyor_belt_surface.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "animation": {\n    "frametime": 1\n  }\n}\n')

    # 弯道：从西边进来、绕西北角拐向北的四分之一圆环，人字纹变成放射状的条纹
    curve = new(shade(BELT_STEEL, 0.8))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5, y + 0.5)
            if 1 <= d <= 15:
                angle = math.degrees(math.atan2(x + 0.5, y + 0.5))
                stripe = int(angle // 11.25) % 4
                color = BELT_AMBER if stripe == 0 else BELT_RUBBER
                if d < 2 or d > 14:
                    color = shade(BELT_RUBBER, 0.7)
                put(curve, x, y, color)
    save(curve, BLOCK, "conveyor_belt_curve_left")
    save(curve.transpose(Image.FLIP_LEFT_RIGHT), BLOCK, "conveyor_belt_curve_right")

    # 侧栏：钢条，中间一道琥珀色（Mk.I 的颜色），一排螺栓
    rail = new(BELT_STEEL)
    noise(rail, random.Random(120), 0.04)
    for x in range(N):
        put(rail, x, 12, shade(BELT_STEEL, 1.3))
        put(rail, x, 13, BELT_AMBER)
        put(rail, x, 14, shade(BELT_AMBER, 0.75))
        put(rail, x, 15, shade(BELT_STEEL, 0.6))
    for x in range(2, N, 4):
        put(rail, x, 13, shade(BELT_STEEL, 1.4))
    for y in range(N):
        put(rail, 6, y, shade(BELT_STEEL, 1.25))
    save(rail, BLOCK, "conveyor_belt_rail")

    # 底架：深色钢板，侧面看得到一排滚筒
    frame_tex = new(shade(BELT_STEEL, 0.55))
    noise(frame_tex, random.Random(121), 0.05)
    for x in range(N):
        put(frame_tex, x, 14, (150, 154, 160) if x % 4 in (1, 2) else (60, 62, 68))
        put(frame_tex, x, 15, (90, 92, 98) if x % 4 in (1, 2) else (40, 42, 46))
    save(frame_tex, BLOCK, "conveyor_belt_frame")


def sorter():
    rng = random.Random(130)
    # 底座顶面：深色钢板，四边一圈琥珀色，前后各一个指向前方（贴图上方）的箭头
    top = new(shade(BELT_STEEL, 0.7))
    noise(top, rng, 0.04)
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            put(top, x, y, shade(BELT_AMBER, 0.85))
    for row, y in enumerate((3, 4)):
        for x in range(7 - row - 1, 9 + row + 1):
            put(top, x, y, BELT_AMBER)
    for row, y in enumerate((11, 12)):
        for x in range(7 - row, 9 + row):
            put(top, x, y, shade(BELT_AMBER, 0.7))
    save(top, BLOCK, "sorter_top")
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
    # 手臂：细条纹的钢，贴图拉伸了也看不出来
    arm = new(shade(BELT_STEEL, 1.05))
    for y in range(N):
        for x in range(N):
            if y % 4 == 0:
                put(arm, x, y, BELT_AMBER)
            elif y % 4 == 1:
                put(arm, x, y, shade(BELT_AMBER, 0.65))
            elif (x + y) % 3 == 0:
                put(arm, x, y, shade(BELT_STEEL, 0.9))
    save(arm, BLOCK, "sorter_arm")


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
    arc_smelter()
    assembler()
    mining_machine()
    water_pump()
    oil_extractor()
    oil_refinery()
    crude_oil_seep()
    conveyor_belt()
    sorter()
    storage_depot()
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
