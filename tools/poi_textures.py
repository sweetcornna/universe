"""生成兴趣点方块和导航数据的贴图（16×16 像素画）。

用法：python tools/poi_textures.py [方块贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block 和 textures/item。

每种兴趣点的方块用兴趣点自己的颜色（core.poi.PoiType）点缀：掉落舱青色、石碑紫色、科技储藏箱绿色、
信号增强器粉色。随机数用固定种子，每次生成的结果一样。
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

POD = (110, 220, 245)
MONOLITH = (199, 155, 255)
CACHE = (124, 242, 176)
BOOSTER = (255, 110, 154)
OUTPOST = (242, 208, 107)
EMBER = (255, 138, 60)


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
    put(img, x + 1, y + 1, shade(base, 0.6))


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


def scorch(img, rng, count, strength):
    """烧痕：几团随机的暗斑，越靠中心越黑。"""
    for _ in range(count):
        cx, cy, r = rng.uniform(0, N), rng.uniform(0, N), rng.uniform(1.5, 4.5)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - cx, y - cy)
                if d < r:
                    put(img, x, y, shade(get(img, x, y), 1 - strength * (1 - d / r)))


def scratches(img, rng, count, base):
    for _ in range(count):
        x, y = rng.randrange(1, N - 4), rng.randrange(1, N - 1)
        for i in range(rng.randrange(2, 5)):
            put(img, x + i, y + (i // 2 if rng.random() < 0.5 else 0), shade(base, 1.18))


# ---- 掉落舱：白色的舱壳、橙色的警示条；正面的舷窗没修好时暗着闪红灯，修好了亮青色

def pod_shell(rng):
    base = (218, 222, 226)
    img = new(base)
    noise(img, rng, 0.03)
    frame(img, shade(base, 1.08), shade(base, 0.68))
    return img, base


def drop_pod_front(repaired):
    rng = random.Random(41)
    img, base = pod_shell(rng)
    cx, cy = 7.5, 7.0
    glass = (18, 26, 34)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d < 5.3:
                put(img, x, y, shade(base, 0.62))
            if d < 4.4:
                put(img, x, y, glass)
                if repaired:
                    t = 1 - d / 4.4
                    put(img, x, y, mix(mix(glass, POD, 0.45), (230, 255, 255), t * 0.55))
            if 4.4 <= d < 5.3 and (x - cx) + (y - cy) < -1:
                put(img, x, y, shade(base, 0.85))
    # 舷窗上的反光
    put(img, 6, 4, (255, 255, 255) if repaired else (90, 104, 116))
    put(img, 5, 5, (220, 250, 255) if repaired else (70, 82, 94))
    # 舷窗下面的状态灯
    for i, x in enumerate(range(5, 11)):
        lit = repaired or i == 0
        put(img, x, 13, (110, 230, 140) if repaired else ((255, 70, 50) if lit else (70, 40, 38)))
    if not repaired:
        # 裂纹和烧痕
        for x, y in ((9, 5), (10, 6), (10, 7), (11, 8)):
            put(img, x, y, (120, 132, 140))
        scorch(img, random.Random(43), 3, 0.35)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, base)
    save(img, BLOCK, "drop_pod_front_repaired" if repaired else "drop_pod_front")


def drop_pod_side():
    rng = random.Random(42)
    img, base = pod_shell(rng)
    # 一条斜着的橙黑警示条
    for y in range(10, 13):
        for x in range(1, N - 1):
            put(img, x, y, (238, 132, 40) if ((x + y) // 2) % 2 == 0 else (40, 40, 44))
    # 竖着的接缝和散热格
    for y in range(1, 10):
        put(img, 7, y, shade(base, 0.72))
        put(img, 8, y, shade(base, 1.06))
    for y in range(3, 8, 2):
        for x in range(2, 6):
            put(img, x, y, shade(base, 0.55))
    # 标号
    for x, y in ((10, 3), (11, 3), (12, 3), (12, 4), (11, 5), (11, 6), (11, 7)):
        put(img, x, y, (60, 64, 70))
    scorch(img, rng, 2, 0.25)
    scratches(img, rng, 3, base)
    save(img, BLOCK, "drop_pod_side")


def drop_pod_top():
    rng = random.Random(44)
    img, base = pod_shell(rng)
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 5.0) < 0.6:
                put(img, x, y, shade(base, 0.6))
            elif d < 2.0:
                put(img, x, y, shade(base, 0.82))
    for a in range(0, 360, 90):
        x = round(cx + 5 * math.cos(math.radians(a + 45)))
        y = round(cy + 5 * math.sin(math.radians(a + 45)))
        put(img, x, y, (238, 132, 40))
    put(img, 7, 7, POD)
    put(img, 8, 8, shade(POD, 0.7))
    scorch(img, rng, 2, 0.2)
    save(img, BLOCK, "drop_pod_top")


# ---- 数据终端：深色的机壳，绿色的字和闪烁的光标，下面一排按键

def terminal_shell(rng):
    base = (64, 70, 80)
    img = new(base)
    noise(img, rng, 0.05)
    frame(img, shade(base, 1.3), shade(base, 0.6))
    return img, base


def data_terminal():
    rng = random.Random(51)
    front, base = terminal_shell(rng)
    screen = (8, 20, 14)
    for y in range(2, 10):
        for x in range(2, 14):
            put(front, x, y, screen)
    for x in range(2, 14):
        put(front, x, 1, shade(base, 0.55))
        put(front, x, 10, shade(base, 1.25))
    green = (90, 240, 140)
    rows = ((3, 9), (5, 11), (7, 6))
    rng2 = random.Random(52)
    for y, length in rows:
        for x in range(3, 3 + length):
            if rng2.random() < 0.8:
                put(front, x, y, green if rng2.random() < 0.7 else shade(green, 0.6))
    put(front, 10, 7, (220, 255, 230))
    # 扫描线
    for x in range(2, 14):
        put(front, x, 8, mix(screen, green, 0.15))
    # 按键
    for x in range(2, 14, 2):
        put(front, x, 12, shade(base, 1.45))
        put(front, x + 1, 12, shade(base, 0.8))
        put(front, x, 13, shade(base, 0.8))
    put(front, 13, 14, (255, 190, 60))
    save(front, BLOCK, "data_terminal_front")

    side, base = terminal_shell(random.Random(53))
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(side, x, y, shade(base, 0.5))
            put(side, x, y + 1, shade(base, 1.15))
    save(side, BLOCK, "data_terminal_side")

    top, base = terminal_shell(random.Random(54))
    for x in range(1, N - 1):
        put(top, x, 7, shade(base, 0.7))
        put(top, x, 8, shade(base, 1.15))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(top, x, y, base)
    put(top, 7, 4, green)
    save(top, BLOCK, "data_terminal_top")


# ---- 补给箱：深灰的箱体，橙色的边条和锁扣，侧面印着编号

def crate_shell(rng):
    base = (82, 88, 92)
    img = new(base)
    noise(img, rng, 0.05)
    trim = (214, 128, 46)
    for i in range(N):
        for t in (0, 1):
            put(img, i, t, shade(trim, 1.1 if t == 0 else 0.9))
            put(img, i, N - 1 - t, shade(trim, 0.75 if t == 0 else 0.9))
            put(img, t, i, shade(trim, 1.05))
            put(img, N - 1 - t, i, shade(trim, 0.8))
    return img, base


def supply_crate():
    front, base = crate_shell(random.Random(61))
    for x in range(2, 14):
        put(front, x, 7, shade(base, 0.6))
        put(front, x, 8, shade(base, 1.2))
    # 锁扣
    for y in range(6, 11):
        for x in range(6, 10):
            put(front, x, y, (170, 176, 184) if y < 10 else (100, 104, 112))
    put(front, 7, 8, (40, 40, 44))
    put(front, 8, 8, (40, 40, 44))
    put(front, 8, 7, (255, 200, 90))
    save(front, BLOCK, "supply_crate_front")

    side, base = crate_shell(random.Random(62))
    # 斜撑
    for i in range(2, 14):
        put(side, i, i, shade(base, 1.22))
        put(side, i, i + 1, shade(base, 0.7))
    # 印的编号块
    for y in range(3, 6):
        for x in range(8, 13):
            put(side, x, y, (220, 220, 212) if (x + y) % 3 else (180, 180, 172))
    save(side, BLOCK, "supply_crate_side")

    top, base = crate_shell(random.Random(63))
    for x in range(5, 11):
        put(top, x, 7, (40, 42, 46))
        put(top, x, 8, (150, 154, 160))
    put(top, 4, 7, (110, 114, 120))
    put(top, 11, 7, (110, 114, 120))
    save(top, BLOCK, "supply_crate_top")


# ---- 先驱者石碑：近乎黑色的石头，刻着发紫光的符文

GLYPHS = [
    ["..#..", ".#.#.", "#...#", ".###.", "..#.."],
    ["#.#.#", ".###.", "..#..", ".#.#.", "#...#"],
    [".###.", "#...#", "#.#.#", "#...#", ".###."],
    ["#####", "..#..", ".###.", "#.#.#", "..#.."],
    ["#...#", "##.##", "#.#.#", "..#..", ".###."],
    ["..#..", "#####", ".#.#.", "#.#.#", "#...#"],
]


def stone(rng, base=(30, 28, 38)):
    img = new(base)
    noise(img, rng, 0.1)
    for _ in range(5):
        x, y = rng.randrange(N), rng.randrange(N)
        for i in range(rng.randrange(2, 5)):
            put(img, x + i, y + rng.choice((0, 0, 1)), shade(base, 0.65))
    return img


def glyph(img, pattern, ox, oy, color):
    for j, row in enumerate(pattern):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, ox + i, oy + j, color)


def monolith():
    rng = random.Random(71)
    side = stone(rng)
    # 两列符文，上下错开
    for k, (ox, oy) in enumerate(((2, 1), (9, 4), (2, 8), (9, 10))):
        glyph(side, GLYPHS[k % len(GLYPHS)], ox, oy, MONOLITH if k % 2 == 0 else mix(MONOLITH, (255, 255, 255), 0.3))
    # 符文周围淡淡的光晕
    glow = side.copy()
    for y in range(N):
        for x in range(N):
            if get(side, x, y) == MONOLITH:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < N and 0 <= ny < N and sum(get(side, nx, ny)) < 200:
                        put(glow, nx, ny, mix(get(side, nx, ny), MONOLITH, 0.22))
    save(glow, BLOCK, "monolith_side")

    top = stone(random.Random(72))
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 4.5) < 0.55:
                put(top, x, y, mix(MONOLITH, (30, 28, 38), 0.35))
            elif d < 1.6:
                put(top, x, y, MONOLITH)
    save(top, BLOCK, "monolith_top")


# ---- 科技储藏箱：金属外壳，发绿光的接缝，顶上一个六边形标记

def tech_cache():
    rng = random.Random(81)
    base = (58, 72, 76)
    side = new(base)
    noise(side, rng, 0.05)
    frame(side, shade(base, 1.3), shade(base, 0.55))
    for x in range(1, N - 1):
        put(side, x, 5, mix(base, CACHE, 0.8) if x % 5 else shade(base, 0.6))
        put(side, x, 10, mix(base, CACHE, 0.8) if (x + 2) % 5 else shade(base, 0.6))
    for y in range(6, 10):
        put(side, 7, y, shade(base, 0.6))
        put(side, 8, y, shade(base, 1.2))
    put(side, 12, 7, CACHE)
    put(side, 12, 8, shade(CACHE, 0.7))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(side, x, y, base)
    save(side, BLOCK, "tech_cache_side")

    top = new(base)
    noise(top, random.Random(82), 0.05)
    frame(top, shade(base, 1.3), shade(base, 0.55))
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x - cx), abs(y - cy)
            hexd = max(dx * 0.866 + dy * 0.5, dy)
            if abs(hexd - 4.2) < 0.6:
                put(top, x, y, CACHE)
            elif hexd < 1.8:
                put(top, x, y, mix(CACHE, (255, 255, 255), 0.4))
            elif hexd < 3.6:
                put(top, x, y, shade(base, 0.8))
    save(top, BLOCK, "tech_cache_top")


# ---- 冒烟的残骸：烧黑的船体板，裂缝里透出余火

def smoldering_wreck():
    for name, seed, embers in (("smoldering_wreck_side", 91, 3), ("smoldering_wreck_top", 92, 5)):
        rng = random.Random(seed)
        base = (52, 50, 50)
        img = new(base)
        noise(img, rng, 0.12)
        for x in range(1, N - 1):
            put(img, x, 7, shade(base, 0.6))
            put(img, x, 8, shade(base, 1.18))
        frame(img, shade(base, 1.2), shade(base, 0.55))
        scorch(img, rng, 4, 0.5)
        # 裂缝：随机游走，越靠中间越亮
        for _ in range(embers):
            x, y = rng.randrange(2, N - 2), rng.randrange(2, N - 2)
            length = rng.randrange(4, 8)
            for i in range(length):
                t = 1 - abs(i - length / 2) / (length / 2)
                put(img, x, y, mix(shade(EMBER, 0.55), (255, 226, 120), t))
                x += rng.choice((-1, 0, 1, 1))
                y += rng.choice((0, 1, 1, -1))
        save(img, BLOCK, name)


# ---- 信号增强器：底座、机身、天线，天线顶上一颗闪烁的粉色灯

def signal_booster():
    rng = random.Random(101)
    base = (74, 78, 86)
    plate = new(base)
    noise(plate, rng, 0.05)
    frame(plate, shade(base, 1.3), shade(base, 0.6))
    for i in range(1, N - 1):
        put(plate, i, 1, shade(base, 1.15))
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(plate, x, y, base)
    # 侧面那几行会被底座的 4 格高度截取：第 12–15 行画一条粉色的警示条
    for x in range(1, N - 1):
        put(plate, x, 13, mix(BOOSTER, base, 0.25) if x % 4 else shade(base, 0.5))
    save(plate, BLOCK, "signal_booster_base")

    body_base = (150, 154, 162)
    body = new(body_base)
    noise(body, random.Random(102), 0.04)
    frame(body, shade(body_base, 1.2), shade(body_base, 0.6))
    # 机身中间一块小屏和一排散热格（模型取 4–12 列、5–12 行）
    for y in range(6, 9):
        for x in range(5, 11):
            put(body, x, y, (24, 14, 20))
    for x in range(6, 10):
        put(body, x, 7, BOOSTER if x % 2 == 0 else shade(BOOSTER, 0.6))
    for x in range(5, 11):
        put(body, x, 10, shade(body_base, 0.55))
    for x in range(5, 11, 2):
        put(body, x, 11, shade(body_base, 0.55))
    save(body, BLOCK, "signal_booster_body")

    antenna = new((120, 124, 132))
    for y in range(N):
        for x in range(N):
            f = 1.25 if x % 4 == 0 else (0.75 if x % 4 == 3 else 1.0)
            if y % 4 == 0:
                f *= 0.8
            put(antenna, x, y, shade((120, 124, 132), f))
    save(antenna, BLOCK, "signal_booster_antenna")

    tip = new(BOOSTER)
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            put(tip, x, y, mix(BOOSTER, (255, 230, 240), max(0, 1 - d / 7)))
    save(tip, BLOCK, "signal_booster_tip")


# ---- 导航数据：一张粉色边的数据卡，卡面是一小片星图和一条航线

def navigation_data():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    card = (28, 24, 40)
    edge = (150, 90, 120)
    for y in range(2, 14):
        for x in range(3, 13):
            if (x, y) in ((3, 2), (12, 2), (3, 13), (12, 13)):
                continue
            border = x in (3, 12) or y in (2, 13)
            put(img, x, y, shade(edge, 1.2 if (x == 3 or y == 2) else 0.8) if border else card)
    # 星点
    for x, y in ((5, 4), (10, 4), (8, 6), (5, 9), (10, 10), (7, 11)):
        put(img, x, y, (190, 200, 230))
    # 航线：从左下到右上的虚线
    route = [(5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6)]
    for i, (x, y) in enumerate(route):
        if i % 2 == 0:
            put(img, x, y, BOOSTER)
    put(img, 10, 5, (255, 255, 255))
    put(img, 11, 5, BOOSTER)
    put(img, 10, 4, BOOSTER)
    # 底边的金手指
    for x in range(5, 11, 2):
        put(img, x, 13, (214, 178, 84))
    save(img, ITEM, "navigation_data")


if __name__ == "__main__":
    drop_pod_front(False)
    drop_pod_front(True)
    drop_pod_side()
    drop_pod_top()
    data_terminal()
    supply_crate()
    monolith()
    tech_cache()
    smoldering_wreck()
    signal_booster()
    navigation_data()
    print("poi textures written")
