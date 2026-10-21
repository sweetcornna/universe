"""生成空间站方块的贴图（16×16 像素画，ADR-0017）。

用法：python tools/station_textures.py [方块贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block。

空间站的配色：浅灰白的外壳、深灰的内饰、青色的灯光和屏幕，交易相关的东西点缀金色（信用点的颜色）。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
BLOCK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "block")
N = 16

HULL = (206, 211, 216)
INTERIOR = (62, 68, 76)
CYAN = (110, 220, 245)
GOLD = (242, 201, 76)
SCREEN = (10, 22, 30)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(color=(0, 0, 0), alpha=255):
    return Image.new("RGBA", (N, N), tuple(color) + (alpha,))


def put(img, x, y, color, a=255):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), tuple(color[:3]) + (a,))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def noise(img, rng, amount):
    for y in range(N):
        for x in range(N):
            put(img, x, y, shade(get(img, x, y), 1 + rng.uniform(-amount, amount)))


def frame(img, light, dark):
    """斜面边框：左上亮、右下暗。"""
    for i in range(N):
        put(img, i, 0, light)
        put(img, 0, i, light)
        put(img, i, N - 1, dark)
        put(img, N - 1, i, dark)


def rivet(img, x, y, base):
    put(img, x, y, shade(base, 1.35))
    put(img, x + 1, y + 1, shade(base, 0.55))


def save(img, name):
    os.makedirs(BLOCK, exist_ok=True)
    img.save(os.path.join(BLOCK, name + ".png"))


# ---- 交易终端：深灰的机身，正面一块屏幕画着行情柱状图和一条金色的价格线

def terminal_casing(rng):
    img = new(INTERIOR)
    noise(img, rng, 0.04)
    frame(img, shade(INTERIOR, 1.4), shade(INTERIOR, 0.6))
    return img


def trade_terminal_front():
    rng = random.Random(171)
    img = terminal_casing(rng)
    # 屏幕
    for y in range(2, 11):
        for x in range(2, 14):
            put(img, x, y, SCREEN)
    for x in range(2, 14):
        put(img, x, 1, shade(INTERIOR, 0.45))
        put(img, x, 11, shade(INTERIOR, 1.25))
    # 柱状图：青色，高低不一
    heights = [3, 5, 4, 6, 3, 7, 5, 6, 4, 7]
    for i, h in enumerate(heights):
        x = 3 + i
        for y in range(10 - h, 10):
            t = (y - (10 - h)) / max(1, h - 1)
            put(img, x, y, mix(CYAN, shade(CYAN, 0.45), t))
    # 金色价格线
    line = [7, 6, 6, 5, 6, 4, 4, 3, 4, 3]
    for i, y in enumerate(line):
        put(img, 3 + i, y, GOLD)
    # 屏幕上方的扫描线高光
    for x in range(2, 14):
        put(img, x, 2, mix(SCREEN, CYAN, 0.25))
    # 下面一排按键：金色的"交易"键和几个灰键
    for x in (3, 5, 7, 9):
        put(img, x, 13, shade(INTERIOR, 1.55))
        put(img, x + 1, 13, shade(INTERIOR, 1.2))
    put(img, 11, 13, GOLD)
    put(img, 12, 13, shade(GOLD, 0.75))
    put(img, 11, 14, shade(GOLD, 0.6))
    put(img, 12, 14, shade(GOLD, 0.45))
    return img


def trade_terminal_side():
    rng = random.Random(172)
    img = terminal_casing(rng)
    # 散热格栅
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(img, x, y, shade(INTERIOR, 0.5))
            put(img, x, y + 1, shade(INTERIOR, 1.15))
    # 一条青色的状态灯
    for y in range(3, 13):
        put(img, 14, y, mix(INTERIOR, CYAN, 0.35 + 0.4 * ((y % 4) == 0)))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


def trade_terminal_top():
    rng = random.Random(173)
    img = terminal_casing(rng)
    # 中间一圈金色的信用点标志
    cx, cy = 7.5, 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if 3.2 < d < 4.6:
                put(img, x, y, shade(GOLD, 0.8 + 0.25 * (x < cx and y < cy)))
            elif d <= 3.2:
                put(img, x, y, shade(INTERIOR, 0.75))
    for y in (6, 9):
        for x in range(6, 10):
            put(img, x, y, GOLD)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


# ---- 飞船维修台：同一个机壳，屏幕上画着一艘船和绿色的船体条，顶上一把橙色的扳手

ORANGE = (240, 150, 60)
GREEN = (90, 220, 120)


def ship_repair_front():
    rng = random.Random(191)
    img = terminal_casing(rng)
    for y in range(2, 11):
        for x in range(2, 14):
            put(img, x, y, SCREEN)
    for x in range(2, 14):
        put(img, x, 1, shade(INTERIOR, 0.45))
        put(img, x, 11, shade(INTERIOR, 1.25))
    # 一艘小船的剖面：青色的线框
    ship = ["   ##   ", "  ####  ", " ###### ", "########", " #    # "]
    for j, row in enumerate(ship):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, 4 + i, 3 + j, mix(CYAN, SCREEN, 0.15 if j < 3 else 0.4))
    # 船体条：绿色，最后一截暗着
    for x in range(3, 13):
        put(img, x, 9, GREEN if x < 10 else shade(GREEN, 0.35))
    for x in (3, 5, 7, 9):
        put(img, x, 13, shade(INTERIOR, 1.55))
        put(img, x + 1, 13, shade(INTERIOR, 1.2))
    put(img, 11, 13, ORANGE)
    put(img, 12, 13, shade(ORANGE, 0.75))
    put(img, 11, 14, shade(ORANGE, 0.6))
    put(img, 12, 14, shade(ORANGE, 0.45))
    return img


def ship_repair_side():
    rng = random.Random(192)
    img = terminal_casing(rng)
    for y in range(N):
        for x in range(1, N - 1):
            if 5 <= y <= 10:
                put(img, x, y, ORANGE if (x + y) % 4 < 2 else (40, 40, 44))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


def ship_repair_top():
    rng = random.Random(193)
    img = terminal_casing(rng)
    # 斜放的扳手
    for i in range(3, 12):
        put(img, i, 15 - i - 1, ORANGE)
        put(img, i + 1, 15 - i - 1, shade(ORANGE, 0.7))
    for x, y in ((10, 3), (12, 3), (12, 5), (11, 2), (13, 4)):
        put(img, x, y, ORANGE)
    for x, y in ((2, 11), (4, 13), (3, 13), (2, 12)):
        put(img, x, y, ORANGE)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


# ---- 空间站的建材

def station_hull():
    """外壳：浅灰的面板，中间一道接缝，四角铆钉。"""
    rng = random.Random(181)
    img = new(HULL)
    noise(img, rng, 0.025)
    frame(img, shade(HULL, 1.07), shade(HULL, 0.72))
    for x in range(1, N - 1):
        put(img, x, 7, shade(HULL, 0.78))
        put(img, x, 8, shade(HULL, 1.06))
    for x, y in ((2, 2), (12, 2), (2, 11), (12, 11)):
        rivet(img, x, y, HULL)
    return img


def station_floor():
    """地板：深灰的格栅，横竖的筋，缝里透出一点青光。"""
    rng = random.Random(182)
    base = shade(INTERIOR, 0.85)
    img = new(base)
    for y in range(N):
        for x in range(N):
            if x % 4 == 0 or y % 4 == 0:
                put(img, x, y, shade(INTERIOR, 1.25))
            elif (x % 4 == 2) and (y % 4 == 2):
                put(img, x, y, mix(base, CYAN, 0.18))
            else:
                put(img, x, y, shade(base, 0.7))
    noise(img, rng, 0.03)
    frame(img, shade(INTERIOR, 1.4), shade(INTERIOR, 0.55))
    return img


def station_glass():
    """窗户：浅蓝的半透明玻璃，金属窗框，一道斜的反光。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            put(img, x, y, (150, 205, 230), 70)
    for i in range(3, 9):
        put(img, i, 12 - i, (230, 248, 255), 150)
        put(img, i + 1, 12 - i, (230, 248, 255), 110)
    for i in range(N):
        for x, y in ((i, 0), (0, i), (i, N - 1), (N - 1, i)):
            put(img, x, y, shade(HULL, 0.85))
    return img


def station_light():
    """灯板：白色发光的面板，外面一圈金属框。"""
    img = new(HULL)
    frame(img, shade(HULL, 1.05), shade(HULL, 0.65))
    for y in range(2, 14):
        for x in range(2, 14):
            t = max(abs(x - 7.5), abs(y - 7.5)) / 6
            put(img, x, y, mix((255, 255, 250), (205, 235, 245), t * 0.8))
    for i in range(2, 14):
        put(img, i, 1, shade(HULL, 0.8))
        put(img, 1, i, shade(HULL, 0.8))
    return img


def station_pad():
    """停机坪标线：黄黑斜条纹。"""
    rng = random.Random(185)
    yellow = (236, 190, 48)
    dark = (40, 40, 44)
    img = new(yellow)
    for y in range(N):
        for x in range(N):
            put(img, x, y, yellow if ((x + y) // 4) % 2 == 0 else dark)
    noise(img, rng, 0.05)
    frame(img, shade(yellow, 1.1), shade(dark, 0.8))
    return img


def station_trim():
    """灯带：深色底上一道发光的青色横条。"""
    img = new(INTERIOR)
    frame(img, shade(INTERIOR, 1.4), shade(INTERIOR, 0.6))
    for x in range(1, N - 1):
        for y, t in ((6, 0.55), (7, 1.0), (8, 1.0), (9, 0.55)):
            put(img, x, y, mix(INTERIOR, CYAN, t))
        put(img, x, 7, mix(CYAN, (255, 255, 255), 0.35))
    return img


def station_solar_panel():
    """太阳能板：深蓝的电池片排成格子，银色的细栅线，边上一道金色的框（国际空间站的太阳翼）。"""
    rng = random.Random(187)
    cell = (24, 40, 92)
    img = new(cell)
    for y in range(N):
        for x in range(N):
            if x % 4 == 0 or y % 8 == 0:
                put(img, x, y, (150, 160, 175))
            else:
                # 电池片上一点斜的反光
                t = ((x + y) % 8) / 8
                put(img, x, y, mix(cell, (52, 84, 160), 0.35 * (1 - abs(t - 0.5) * 2)))
    noise(img, rng, 0.04)
    for i in range(N):
        put(img, i, 0, (196, 150, 60))
        put(img, i, N - 1, (150, 110, 40))
    return img


# ---- 任务板：同一个机壳，屏幕上三行任务，每行前面一个彩色的小方块，最后一行打了勾

RED = (255, 106, 90)
MINT = (124, 242, 176)


def mission_board_front():
    rng = random.Random(191)
    img = terminal_casing(rng)
    for y in range(2, 13):
        for x in range(2, 14):
            put(img, x, y, SCREEN)
    for x in range(2, 14):
        put(img, x, 1, shade(INTERIOR, 0.45))
        put(img, x, 13, shade(INTERIOR, 1.25))
    # 三行任务：彩色标记 + 一行字
    for row, (color, length) in enumerate(((GOLD, 7), (RED, 5), (MINT, 6))):
        y = 3 + row * 3
        put(img, 3, y, color)
        put(img, 3, y + 1, shade(color, 0.7))
        for x in range(5, 5 + length):
            put(img, x, y, mix(SCREEN, (210, 225, 232), 0.75 if x % 3 else 0.5))
    # 右下角的勾
    for x, y in ((10, 10), (11, 11), (12, 10), (13, 9)):
        put(img, x - 1, y, MINT)
    for x in range(2, 14):
        put(img, x, 2, mix(SCREEN, CYAN, 0.25))
    return img


def mission_board_side():
    rng = random.Random(192)
    img = terminal_casing(rng)
    # 一摞文件夹的侧面：几道彩色的边
    for y, color in ((4, GOLD), (7, RED), (10, MINT)):
        for x in range(3, 13):
            put(img, x, y, shade(color, 0.75))
            put(img, x, y + 1, shade(INTERIOR, 0.6))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


def mission_board_top():
    rng = random.Random(193)
    img = terminal_casing(rng)
    # 青色的星图标记：一个圈加十字
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.4 < d < 4.6:
                put(img, x, y, shade(CYAN, 0.85))
    for i in range(3, 13):
        put(img, i, 7, shade(CYAN, 0.6))
        put(img, 7, i, shade(CYAN, 0.6))
    put(img, 7, 7, CYAN)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


def main():
    save(station_hull(), "station_hull")
    save(station_floor(), "station_floor")
    save(station_glass(), "station_glass")
    save(station_light(), "station_light")
    save(station_pad(), "station_pad")
    save(station_trim(), "station_trim")
    save(station_solar_panel(), "station_solar_panel")
    save(trade_terminal_front(), "trade_terminal_front")
    save(trade_terminal_side(), "trade_terminal_side")
    save(trade_terminal_top(), "trade_terminal_top")
    save(ship_repair_front(), "ship_repair_front")
    save(ship_repair_side(), "ship_repair_side")
    save(ship_repair_top(), "ship_repair_top")
    save(mission_board_front(), "mission_board_front")
    save(mission_board_side(), "mission_board_side")
    save(mission_board_top(), "mission_board_top")


if __name__ == "__main__":
    main()
