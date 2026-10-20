"""生成传送器、空间站传送器和基地信标的贴图（16×16 像素画，ADR-0017）。

用法：python tools/teleport_textures.py [方块贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block。

传送器是深灰机壳，顶上一块传送台，充满电时台面和正面的线圈亮青色；空间站的那台用空间站外壳的浅灰涂装。
基地信标是手写模型，贴图分底座、柱身和顶上的灯。随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
BLOCK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "block")
N = 16

DARK = (54, 61, 69)
HULL = (206, 211, 216)
CYAN = (110, 220, 245)
CYAN_DIM = (34, 78, 92)
GLASS = (18, 30, 38)


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


def noise(img, rng, amount):
    for y in range(N):
        for x in range(N):
            px = img.getpixel((x, y))
            put(img, x, y, shade(px, 1 + rng.uniform(-amount, amount)), px[3])


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


def save(img, name):
    os.makedirs(BLOCK, exist_ok=True)
    img.save(os.path.join(BLOCK, name + ".png"))


def casing(base, rng):
    img = new(base)
    noise(img, rng, 0.03)
    frame(img, shade(base, 1.2), shade(base, 0.62))
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y, base)
    return img


def pad(base, lit, rng):
    """俯视的传送台：外圈金属，里面三道同心环，中间一个点。"""
    img = casing(base, rng)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.6:
                put(img, x, y, shade(base, 0.7))
            if d < 6.0:
                put(img, x, y, GLASS)
            ring = d < 5.6 and (4.6 <= d or 2.6 <= d < 3.4)
            if ring:
                put(img, x, y, CYAN if lit else CYAN_DIM)
            if d < 1.2:
                put(img, x, y, (230, 250, 255) if lit else shade(CYAN_DIM, 1.3))
    if lit:
        # 环和环之间透一点光
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 3.4 <= d < 4.6 or 1.2 <= d < 2.6:
                    put(img, x, y, mix(GLASS, CYAN, 0.25))
    return img


def side(base, rng, stripe):
    """侧面：两道竖的散热槽和一条腰线。"""
    img = casing(base, rng)
    for x in range(1, N - 1):
        put(img, x, 3, stripe)
    for x in (4, 11):
        for y in range(6, 13):
            put(img, x, y, shade(base, 0.6))
            put(img, x + 1, y, shade(base, 1.12))
    return img


def front(base, lit, rng, stripe):
    """正面：一扇窗，里面是竖着的线圈，充电满了发亮。"""
    img = casing(base, rng)
    for x in range(1, N - 1):
        put(img, x, 3, stripe)
    rect(img, 3, 5, 12, 13, shade(base, 0.55))
    rect(img, 4, 6, 11, 12, GLASS)
    coil = CYAN if lit else CYAN_DIM
    for y in range(6, 13):
        if y % 2 == 0:
            for x in range(5, 11):
                put(img, x, y, coil)
        else:
            put(img, 5, y, shade(coil, 0.8))
            put(img, 10, y, shade(coil, 0.8))
    if lit:
        for y in range(7, 13, 2):
            for x in range(6, 10):
                put(img, x, y, mix(GLASS, CYAN, 0.3))
    # 状态灯
    put(img, 12, 1, CYAN if lit else (150, 60, 50))
    return img


def teleporter(name, base, stripe, seed):
    rng = random.Random(seed)
    save(side(base, rng, stripe), name + "_side")
    save(front(base, False, random.Random(seed + 1), stripe), name + "_front")
    save(front(base, True, random.Random(seed + 1), stripe), name + "_front_on")
    save(pad(base, False, random.Random(seed + 2)), name + "_top")
    save(pad(base, True, random.Random(seed + 2)), name + "_top_on")


def base_beacon():
    rng = random.Random(231)
    steel = (88, 98, 108)
    # 底座：深色金属，四角铆钉，一圈青色细线
    base = casing(steel, rng)
    for i in range(3, 13):
        for (x, y) in ((i, 3), (i, 12), (3, i), (12, i)):
            put(base, x, y, CYAN_DIM)
    save(base, "base_beacon_base")
    # 柱身：竖的青色灯带
    body = new(shade(steel, 1.1))
    noise(body, rng, 0.03)
    for y in range(N):
        put(body, 0, y, shade(steel, 0.8))
        put(body, N - 1, y, shade(steel, 0.8))
        for x in (7, 8):
            put(body, x, y, CYAN if y % 4 != 3 else shade(CYAN, 0.7))
        put(body, 6, y, shade(steel, 0.75))
        put(body, 9, y, shade(steel, 0.75))
    save(body, "base_beacon_body")
    # 顶灯：中间亮，外面一圈浅
    cap = new(CYAN)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            put(cap, x, y, mix((235, 252, 255), CYAN, min(1.0, d / 7)))
    save(cap, "base_beacon_cap")


def main():
    teleporter("teleporter", DARK, CYAN_DIM, 221)
    teleporter("station_teleporter", HULL, shade(CYAN, 0.8), 225)
    base_beacon()


if __name__ == "__main__":
    main()
