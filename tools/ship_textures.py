"""生成飞船部件的贴图（16×16 像素画）。

用法：python tools/ship_textures.py [方块贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block 和 textures/item。

船体板按染料的颜色出 16 张；喷口、屏幕这些发光的部分在模型里单独设了自发光，贴图只管颜色。
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


if __name__ == "__main__":
    hull_panel()
    helm_screen()
    seat_fabric()
    thruster()
    fuel_tank()
    impulse_engine()
    plating()
    launch_fuel()
    print("ship textures written")
