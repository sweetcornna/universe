"""生成飞船部件的贴图（16×16 像素画）。

用法：python tools/ship_textures.py [方块贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block 和 textures/item。

船体板按染料的颜色出 16 张；喷口、屏幕这些发光的部分在模型里单独设了自发光，贴图只管颜色。
武器、护盾发生器和 Mk.II 部件（ADR-0017）：Mk.II 用深色的钛合金机壳配金色饰条，和普通版一眼分得出来。
随机数用固定种子，每次生成的结果一样。
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

# 原版 DyeColor 的贴图颜色
DYES = {
    "white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA,
    "yellow": 0xFED83D, "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52,
    "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
    "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21,
}


def rgb(c):
    return (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    r, g, b = color
    return clamp(r * f), clamp(g * f), clamp(b * f)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(color=(0, 0, 0), alpha=255):
    return Image.new("RGBA", (N, N), color + (alpha,))


def put(img, x, y, color, a=255):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), tuple(color) + (a,))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def noise(img, rng, amount):
    for y in range(N):
        for x in range(N):
            f = 1 + rng.uniform(-amount, amount)
            put(img, x, y, shade(get(img, x, y), f))


def frame(img, light, dark):
    """斜面边框：左上亮、右下暗。"""
    for i in range(N):
        put(img, i, 0, light)
        put(img, 0, i, light)
        put(img, i, N - 1, dark)
        put(img, N - 1, i, dark)


def rivet(img, x, y, base):
    put(img, x, y, shade(base, 1.35))
    put(img, x + 1, y + 1, shade(base, 0.6))


def panel(base, rng, seams=True, rivets=True):
    """金属面板：底色带点杂色，四周斜面，中间一道接缝，四角铆钉。"""
    img = new(base)
    noise(img, rng, 0.04)
    if seams:
        for x in range(1, N - 1):
            put(img, x, 7, shade(base, 0.72))
            put(img, x, 8, shade(base, 1.12))
    frame(img, shade(base, 1.22), shade(base, 0.62))
    if rivets:
        for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
            rivet(img, x, y, base)
    return img


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


def hull_panel():
    rng = random.Random(11)
    save(panel((150, 156, 164), rng), BLOCK, "ship_hull_panel")


def helm_screen():
    """驾驶台的屏幕：深蓝底、青色的雷达圈和读数条。"""
    img = new((10, 22, 38))
    cx, cy = 5.5, 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 4.2) < 0.55 or abs(d - 2.0) < 0.45:
                put(img, x, y, (40, 200, 220))
    put(img, 5, 7, (180, 255, 255))
    put(img, 7, 5, (255, 210, 90))
    for i, length in enumerate((5, 3, 4, 2)):
        y = 3 + i * 3
        for x in range(11, 11 + length):
            if x < N - 1:
                put(img, x, y, (60, 230, 140) if i % 2 == 0 else (40, 170, 230))
    frame(img, (60, 70, 90), (20, 24, 32))
    save(img, BLOCK, "ship_helm_screen")


def seat_fabric():
    """座椅的布面：深灰带菱形绗缝。"""
    rng = random.Random(7)
    base = (58, 62, 70)
    img = new(base)
    noise(img, rng, 0.05)
    for y in range(N):
        for x in range(N):
            if (x + y) % 6 == 0 or (x - y) % 6 == 0:
                put(img, x, y, shade(base, 0.7))
    frame(img, shade(base, 1.2), shade(base, 0.6))
    save(img, BLOCK, "ship_seat_fabric")


def thruster():
    rng = random.Random(23)
    base = (120, 126, 134)
    side = panel(base, rng, seams=False, rivets=False)
    # 一圈黄黑警示条和散热槽
    for x in range(1, N - 1):
        for y in (11, 12):
            put(side, x, y, (232, 176, 40) if (x + y) % 4 < 2 else (40, 40, 44))
    for y in (3, 5, 7):
        for x in range(3, 13):
            put(side, x, y, shade(base, 0.55))
    save(side, BLOCK, "ship_thruster_side")

    back = panel((104, 110, 118), rng, seams=False)
    for (x, y) in ((5, 5), (9, 5), (5, 9), (9, 9)):
        rivet(back, x, y, (104, 110, 118))
    save(back, BLOCK, "ship_thruster_back")

    nozzle_side = new((70, 72, 80))
    for y in range(N):
        t = y / (N - 1)
        # 靠近喷口的一端被烧出蓝紫色的回火色
        color = mix((78, 80, 88), (88, 70, 120), t * t)
        for x in range(N):
            put(nozzle_side, x, y, shade(color, 1 + rng.uniform(-0.05, 0.05)))
    for y in (4, 9, 14):
        for x in range(N):
            put(nozzle_side, x, y, (50, 50, 58))
    save(nozzle_side, BLOCK, "ship_thruster_nozzle_side")

    nozzle = new((30, 30, 36))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.2:
                c = (255, 244, 200)
            elif d < 3.6:
                c = (255, 190, 70)
            elif d < 5.0:
                c = (220, 90, 30)
            elif d < 6.2:
                c = (90, 60, 60)
            else:
                c = (44, 44, 52) if (x + y) % 2 else (52, 52, 60)
            put(nozzle, x, y, c)
    save(nozzle, BLOCK, "ship_thruster_nozzle")


def fuel_tank():
    rng = random.Random(31)
    base = (214, 120, 40)
    side = new(base)
    noise(side, rng, 0.05)
    # 竖直的加强筋和左右两根管子
    for y in range(N):
        for x in (1, 14):
            put(side, x, y, (150, 156, 164))
        put(side, 2, y, (96, 100, 108))
        put(side, 13, y, (96, 100, 108))
    for x in range(N):
        for y in (2, 13):
            put(side, x, y, shade(base, 0.7))
    # 液位窗：绿色的燃料
    for y in range(4, 12):
        for x in range(6, 10):
            if x in (6, 9) or y in (4, 11):
                put(side, x, y, (60, 60, 66))
            else:
                put(side, x, y, (90, 230, 120) if y > 6 else (30, 50, 40))
    # 危险标志的小三角
    put(side, 11, 9, (250, 230, 60))
    put(side, 10, 10, (250, 230, 60))
    put(side, 11, 10, (40, 40, 40))
    put(side, 12, 10, (250, 230, 60))
    save(side, BLOCK, "fuel_tank_side")

    top = panel((150, 156, 164), rng, seams=False, rivets=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if abs(d - 4.5) < 0.7:
                put(top, x, y, (200, 40, 40))
            elif d < 1.3:
                put(top, x, y, (90, 90, 96))
    for i in range(-4, 5):
        put(top, 7 + i, 7, (170, 30, 30))
        put(top, 7, 7 + i, (170, 30, 30))
    save(top, BLOCK, "fuel_tank_top")


def impulse_engine():
    rng = random.Random(41)
    base = (54, 62, 78)
    side = panel(base, rng, seams=False)
    for x in range(2, N - 2):
        put(side, x, 5, (60, 220, 255))
        put(side, x, 10, (60, 220, 255))
        put(side, x, 6, (30, 110, 140))
        put(side, x, 11, (30, 110, 140))
    save(side, BLOCK, "impulse_engine_side")

    front = new((24, 28, 38))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.5:
                c = (230, 255, 255)
            elif d < 4.0:
                c = (90, 210, 255)
            elif d < 5.5:
                c = (40, 110, 230)
            elif d < 6.6:
                c = (60, 66, 84)
            else:
                c = (36, 40, 52)
            put(front, x, y, c)
    save(front, BLOCK, "impulse_engine_front")

    back = panel((48, 54, 68), rng, seams=False, rivets=False)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(back, x, y, (22, 24, 30))
    save(back, BLOCK, "impulse_engine_back")


VIOLET = (178, 104, 255)
VIOLET_DIM = (70, 36, 112)


def warp_drive():
    """跃迁引擎：深紫灰的机壳，侧面是竖着的场线圈（另出一张只有线圈的发光贴图），正面是一圈圈发光的环。"""
    rng = random.Random(77)
    side = panel((50, 46, 62), rng, seams=False)
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for x in (3, 7, 8, 12):
        for y in range(2, N - 2):
            put(side, x, y, VIOLET_DIM)
            put(glow, x, y, VIOLET if y % 4 else (230, 200, 255))
    for x in range(2, N - 2):
        put(side, x, 1, (34, 30, 44))
        put(side, x, N - 2, (34, 30, 44))
    save(side, BLOCK, "warp_drive_side")
    save(glow, BLOCK, "warp_drive_glow")

    front = new((30, 26, 40))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.8:
                c = (250, 240, 255)
            elif d < 3.0:
                c = (214, 170, 255)
            elif d < 4.0:
                c = (44, 22, 70)
            elif d < 5.2:
                c = VIOLET
            elif d < 5.9:
                c = (110, 50, 190)
            elif d < 6.9:
                c = (64, 58, 78)
            else:
                c = (38, 34, 48)
            put(front, x, y, c)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(front, x, y, (120, 116, 132))
    save(front, BLOCK, "warp_drive_front")

    back = panel((46, 42, 56), rng, seams=False, rivets=False)
    for y in range(3, 13, 3):
        for x in range(3, 13):
            put(back, x, y, (24, 20, 30))
            put(back, x, y + 1, (70, 40, 100) if x % 3 == 0 else (30, 26, 38))
    save(back, BLOCK, "warp_drive_back")


def space_warper():
    """空间翘曲器：金属圈里嵌着一块发光的紫色晶体。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.6 <= d < 7.2:
                light = 1.15 if (x + y) < 13 else 0.8
                put(img, x, y, shade((168, 172, 182), light))
            elif d < 5.6 and abs(x - 7.5) + abs(y - 7.5) < 5.2:
                t = (abs(x - 7.5) + abs(y - 7.5)) / 5.2
                c = mix((246, 230, 255), VIOLET, t)
                if x < 7 and y < 7:
                    c = mix(c, (255, 255, 255), 0.35)
                put(img, x, y, c)
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        put(img, x, y, (120, 124, 134))
    save(img, ITEM, "space_warper")


def survey_data():
    """勘探数据：深色的数据芯片，青色的电路纹，一角亮着读数灯。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    cyan = (90, 226, 240)
    board = (34, 38, 48)
    for y in range(3, 13):
        for x in range(2, 14):
            # 切掉右上角，像一张存储卡
            if x - 9 > y - 3 and y < 7:
                continue
            edge = x in (2, 13) or y in (3, 12) or x - 9 == y - 3
            light = 1.2 if (x < 5 or y < 5) else 0.9
            put(img, x, y, shade((70, 76, 90), light) if edge else board)
    # 电路：几条线连到焊点
    trace = mix(cyan, board, 0.35)
    for x in range(4, 10):
        put(img, x, 6, trace)
    for x in range(4, 12):
        put(img, x, 9, trace)
    for y in range(6, 10):
        put(img, 9, y, trace)
    for x, y in ((4, 6), (4, 9), (11, 9)):
        put(img, x, y, cyan)
    # 读数灯
    put(img, 11, 11, (255, 255, 255))
    put(img, 10, 11, cyan)
    # 底边的金手指
    for x in range(4, 12, 2):
        put(img, x, 12, (214, 178, 84))
    save(img, ITEM, "survey_data")


def plating():
    for name, color in DYES.items():
        rng = random.Random(sum(ord(ch) for ch in name))
        base = rgb(color)
        # 太亮、太暗的颜色往中间收一点，面板的明暗才看得出来
        lum = 0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2]
        if lum > 210:
            base = shade(base, 0.9)
        elif lum < 40:
            base = mix(base, (60, 60, 66), 0.4)
        img = panel(base, random.Random(5), seams=True)
        noise(img, rng, 0.02)
        save(img, BLOCK, "hull_plating_" + name)


def launch_fuel():
    """发射燃料罐：红色的罐身、灰色的盖子、黄色的标签。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    body = (196, 52, 40)
    for y in range(4, 15):
        for x in range(4, 12):
            edge = x in (4, 11)
            put(img, x, y, shade(body, 0.75 if edge else (1.15 if x == 5 else 1.0)))
    for x in range(5, 11):
        put(img, x, 3, (150, 156, 164))
        put(img, x, 15, shade(body, 0.6))
    for x in range(6, 10):
        put(img, x, 2, (110, 114, 122))
        put(img, x, 1, (170, 176, 184))
    for y in range(8, 12):
        for x in range(5, 11):
            put(img, x, y, (240, 210, 60))
    for x in range(6, 10):
        put(img, x, 9, (40, 40, 40))
    put(img, 7, 10, (40, 40, 40))
    put(img, 8, 10, (40, 40, 40))
    save(img, ITEM, "launch_fuel")


def weapon(name, accent, seed, beam):
    """光子炮和采矿光束：深灰的炮座，侧面一道彩色的能量槽，炮管带散热环，炮口发光。"""
    rng = random.Random(seed)
    base = (70, 74, 84)
    side = panel(base, rng, seams=False)
    for x in range(1, N - 1):
        put(side, x, 9, shade(accent, 0.55))
        put(side, x, 10, accent)
        put(side, x, 11, shade(accent, 0.55))
    for y in (3, 5):
        for x in range(3, 13):
            put(side, x, y, shade(base, 0.6))
    save(side, BLOCK, name + "_side")

    back = panel(shade(base, 0.9), rng, seams=False, rivets=False)
    for y in range(4, 12, 2):
        for x in range(4, 12):
            put(back, x, y, (34, 36, 42))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(back, x, y, base)
    save(back, BLOCK, name + "_back")

    front = panel(base, rng, seams=False, rivets=False)
    for y in range(N):
        for x in range(N):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 4.0:
                put(front, x, y, (40, 42, 48))
            elif d < 5.0:
                put(front, x, y, shade(accent, 0.7))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(front, x, y, base)
    save(front, BLOCK, name + "_front")

    barrel = new((86, 90, 100))
    for y in range(N):
        for x in range(N):
            # 一圈圈散热环：沿炮管方向（贴图的 y）交替明暗
            ring = (y % 3 == 0)
            c = (58, 60, 68) if ring else (96, 100, 110)
            if x in (0, N - 1):
                c = shade(c, 0.8)
            put(barrel, x, y, shade(c, 1 + rng.uniform(-0.04, 0.04)))
    if beam:
        for y in range(N):
            put(barrel, 7, y, shade(accent, 0.8))
            put(barrel, 8, y, shade(accent, 0.8))
    save(barrel, BLOCK, name + "_barrel")

    muzzle = new((30, 30, 36))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.0:
                c = (255, 250, 230)
            elif d < 4.0:
                c = mix((255, 240, 200), accent, (d - 2.0) / 2.0)
            elif d < 6.0:
                c = shade(accent, 0.75)
            else:
                c = (60, 62, 70)
            put(muzzle, x, y, c)
    save(muzzle, BLOCK, name + "_muzzle")


def shield_generator():
    """护盾发生器：灰白机壳，侧面一个环形线圈（另出一张发光的线圈贴图），顶上是散热格栅。"""
    rng = random.Random(97)
    base = (176, 184, 194)
    blue = (110, 220, 255)
    side = panel(base, rng, seams=False)
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if abs(d - 4.6) < 0.8:
                put(side, x, y, (40, 70, 96))
                put(glow, x, y, blue if (x + y) % 3 else (220, 250, 255))
            elif d < 2.2:
                put(side, x, y, (30, 50, 70))
                put(glow, x, y, (180, 240, 255))
    save(side, BLOCK, "shield_generator_side")
    save(glow, BLOCK, "shield_generator_glow")

    top = panel(base, rng, seams=False, rivets=False)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(top, x, y, (70, 76, 86))
            put(top, x, y + 1, shade(base, 1.1))
    for x in range(3, 13):
        put(top, x, 2, shade(blue, 0.7))
        put(top, x, 13, shade(blue, 0.7))
    save(top, BLOCK, "shield_generator_top")


GOLD = (232, 190, 70)
TITANIUM = (58, 64, 76)


def mk2_badge(img):
    """Mk.II 的标记：右下角两道金色的竖杠。"""
    for y in range(11, 14):
        put(img, 11, y, GOLD)
        put(img, 13, y, GOLD)


def thruster_mk2():
    rng = random.Random(123)
    side = panel(TITANIUM, rng, seams=False, rivets=False)
    for x in range(1, N - 1):
        for y in (10, 11):
            put(side, x, y, GOLD if (x + y) % 4 < 2 else (30, 30, 34))
    for y in (3, 5, 7):
        for x in range(3, 13):
            put(side, x, y, shade(TITANIUM, 0.55))
    save(side, BLOCK, "ship_thruster_mk2_side")

    back = panel(shade(TITANIUM, 0.9), rng, seams=False)
    mk2_badge(back)
    save(back, BLOCK, "ship_thruster_mk2_back")

    nozzle_side = new((60, 62, 72))
    for y in range(N):
        color = mix((66, 68, 78), (70, 100, 150), (y / (N - 1)) ** 2)
        for x in range(N):
            put(nozzle_side, x, y, shade(color, 1 + rng.uniform(-0.05, 0.05)))
    for y in (3, 7, 11, 15):
        for x in range(N):
            put(nozzle_side, x, y, GOLD if y == 15 else (44, 44, 52))
    save(nozzle_side, BLOCK, "ship_thruster_mk2_nozzle_side")

    nozzle = new((26, 26, 32))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.4:
                c = (240, 250, 255)
            elif d < 3.8:
                c = (140, 210, 255)
            elif d < 5.2:
                c = (60, 120, 230)
            elif d < 6.3:
                c = GOLD
            else:
                c = (40, 42, 50) if (x + y) % 2 else (48, 50, 58)
            put(nozzle, x, y, c)
    save(nozzle, BLOCK, "ship_thruster_mk2_nozzle")


def fuel_tank_mk2():
    rng = random.Random(131)
    base = (176, 50, 44)
    side = new(base)
    noise(side, rng, 0.05)
    for y in range(N):
        for x in (1, 14):
            put(side, x, y, GOLD)
        put(side, 2, y, (70, 74, 84))
        put(side, 13, y, (70, 74, 84))
    for x in range(N):
        for y in (1, 14):
            put(side, x, y, TITANIUM)
    # 两个液位窗
    for wx in (4, 9):
        for y in range(3, 13):
            for x in range(wx, wx + 3):
                if x in (wx, wx + 2) or y in (3, 12):
                    put(side, x, y, (50, 50, 56))
                else:
                    put(side, x, y, (90, 230, 120) if y > 5 else (30, 50, 40))
    save(side, BLOCK, "fuel_tank_mk2_side")

    top = panel(TITANIUM, rng, seams=False, rivets=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if abs(d - 4.8) < 0.7:
                put(top, x, y, GOLD)
            elif d < 3.4:
                put(top, x, y, shade(base, 0.9))
    for i in range(-2, 3):
        put(top, 7 + i, 7, (250, 240, 220))
        put(top, 7, 7 + i, (250, 240, 220))
    save(top, BLOCK, "fuel_tank_mk2_top")


def impulse_engine_mk2():
    rng = random.Random(141)
    side = panel(TITANIUM, rng, seams=False)
    for x in range(2, N - 2):
        for y, c in ((4, (90, 240, 255)), (5, (40, 130, 160)), (9, (90, 240, 255)), (10, (40, 130, 160))):
            put(side, x, y, c)
    for x in range(2, N - 2):
        put(side, x, 13, GOLD)
    save(side, BLOCK, "impulse_engine_mk2_side")

    front = new((20, 24, 34))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.8:
                c = (240, 255, 255)
            elif d < 4.2:
                c = (110, 230, 255)
            elif d < 5.4:
                c = (50, 130, 240)
            elif d < 6.2:
                c = GOLD
            else:
                c = (34, 38, 50)
            put(front, x, y, c)
    save(front, BLOCK, "impulse_engine_mk2_front")

    back = panel(shade(TITANIUM, 0.85), rng, seams=False, rivets=False)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(back, x, y, (18, 20, 26))
    mk2_badge(back)
    save(back, BLOCK, "impulse_engine_mk2_back")


def warp_drive_mk2():
    rng = random.Random(177)
    magenta = (255, 110, 220)
    side = panel((44, 38, 58), rng, seams=False)
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for x in (2, 5, 10, 13):
        for y in range(2, N - 2):
            put(side, x, y, (90, 30, 90))
            put(glow, x, y, magenta if y % 4 else (255, 220, 250))
    for x in range(2, N - 2):
        put(side, x, 1, GOLD)
        put(side, x, N - 2, GOLD)
    save(side, BLOCK, "warp_drive_mk2_side")
    save(glow, BLOCK, "warp_drive_mk2_glow")

    front = new((28, 22, 38))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.8:
                c = (255, 245, 255)
            elif d < 3.0:
                c = (255, 170, 240)
            elif d < 3.8:
                c = (60, 20, 70)
            elif d < 5.0:
                c = magenta
            elif d < 5.8:
                c = (150, 50, 170)
            elif d < 6.8:
                c = GOLD
            else:
                c = (36, 30, 48)
            put(front, x, y, c)
    save(front, BLOCK, "warp_drive_mk2_front")

    back = panel((42, 36, 54), rng, seams=False, rivets=False)
    for y in range(3, 13, 3):
        for x in range(3, 13):
            put(back, x, y, (22, 18, 28))
            put(back, x, y + 1, (110, 40, 110) if x % 3 == 0 else (30, 26, 38))
    mk2_badge(back)
    save(back, BLOCK, "warp_drive_mk2_back")


def alloy_plating():
    """合金船体板：深色的钛合金面板，斜向的加强纹，四角金色铆钉。"""
    rng = random.Random(151)
    img = panel(TITANIUM, rng, seams=False, rivets=False)
    for y in range(1, N - 1):
        for x in range(1, N - 1):
            if (x + y) % 5 == 0:
                put(img, x, y, shade(TITANIUM, 1.25))
            elif (x + y) % 5 == 1:
                put(img, x, y, shade(TITANIUM, 0.8))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(img, x, y, GOLD)
        put(img, x + 1, y + 1, shade(GOLD, 0.55))
    save(img, BLOCK, "alloy_hull_plating")


if __name__ == "__main__":
    weapon("photon_cannon", (255, 190, 70), 61, False)
    weapon("mining_beam", (255, 96, 60), 62, True)
    shield_generator()
    thruster_mk2()
    fuel_tank_mk2()
    impulse_engine_mk2()
    warp_drive_mk2()
    alloy_plating()
    hull_panel()
    helm_screen()
    seat_fabric()
    thruster()
    fuel_tank()
    impulse_engine()
    warp_drive()
    space_warper()
    survey_data()
    plating()
    launch_fuel()
    print("ship textures written")
