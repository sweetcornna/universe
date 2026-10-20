"""生成模组图标：64×64 像素画，一颗恒星外面套着搭了一半的戴森球骨架。

用法：python tools/logo.py [模组内图标] [发布页图标]
默认输出 src/main/resources/universe_logo.png（放大到 128×128，模组列表用）和
docs/release/icon.png（放大到 512×512，Modrinth、CurseForge 用）。随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

N = 64
ROOT = os.path.join(os.path.dirname(__file__), "..")

SPACE_TOP = (10, 14, 30)
SPACE_BOTTOM = (22, 26, 52)
CYAN = (95, 211, 230)
CYAN_DIM = (34, 66, 92)
GOLD = (236, 196, 92)
PANEL = (180, 140, 60)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def rotate(p, tilt, spin):
    """先绕竖轴转 spin，再绕横轴倾斜 tilt。"""
    x, y, z = p
    c, s = math.cos(spin), math.sin(spin)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(tilt), math.sin(tilt)
    y, z = y * c - z * s, y * s + z * c
    return x, y, z


def main():
    rng = random.Random(7)
    img = Image.new("RGB", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = mix(SPACE_TOP, SPACE_BOTTOM, y / (N - 1))
    for _ in range(40):
        x, y = rng.randrange(N), rng.randrange(N)
        px[x, y] = mix(px[x, y], (230, 236, 255), rng.uniform(0.3, 0.9))

    cx, cy = N / 2 - 0.5, N / 2 - 0.5
    radius = 25.0
    tilt, spin = math.radians(22), math.radians(18)

    # 骨架：经线和纬线采样成点，背面的暗、正面的亮
    back, front = {}, {}

    def plot(p, colour_front, colour_back):
        x, y, z = rotate(p, tilt, spin)
        sx, sy = round(cx + x * radius), round(cy - y * radius)
        if not (0 <= sx < N and 0 <= sy < N):
            return
        (front if z > 0 else back)[(sx, sy)] = colour_front if z > 0 else colour_back

    for lon in range(0, 180, 45):
        a = math.radians(lon)
        for i in range(720):
            t = math.radians(i / 2)
            plot((math.cos(t) * math.cos(a), math.sin(t), math.cos(t) * math.sin(a)), CYAN, CYAN_DIM)
    for lat in (-45, 0, 45):
        b = math.radians(lat)
        for i in range(720):
            t = math.radians(i / 2)
            plot((math.cos(b) * math.cos(t), math.sin(b), math.cos(b) * math.sin(t)), CYAN, CYAN_DIM)

    for (x, y), c in back.items():
        px[x, y] = c

    # 恒星：白黄的核，往外变橙，外面一圈光晕
    star = 10.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= star:
                px[x, y] = mix((255, 252, 230), (255, 150, 50), (d / star) ** 2)
            elif d <= star + 5:
                px[x, y] = mix(px[x, y], (255, 170, 70), 0.55 * (1 - (d - star) / 5) ** 1.5)

    # 正面左上已经铺上的几块球壳：半透明的金色，盖住一部分恒星
    covered = set()
    for i in range(0, 900):
        for j in range(0, 1800):
            b, a = math.radians(i / 10), math.radians(j / 10)
            x, y, z = rotate((math.cos(b) * math.cos(a), math.sin(b), math.cos(b) * math.sin(a)), tilt, spin)
            if z > 0.2 and x < -0.05 and y > 0.05:
                covered.add((round(cx + x * radius), round(cy - y * radius)))
    for x, y in covered:
        if 0 <= x < N and 0 <= y < N:
            px[x, y] = mix(px[x, y], PANEL, 0.6)
    for (x, y), c in front.items():
        px[x, y] = c

    # 节点：经纬线交点里朝前的那些
    for lon in range(0, 180, 45):
        for lat in (-45, 0, 45):
            for sign in (1, -1):
                a, b = math.radians(lon), math.radians(lat)
                x, y, z = rotate((sign * math.cos(b) * math.cos(a), math.sin(b), sign * math.cos(b) * math.sin(a)), tilt, spin)
                if z > 0.15:
                    sx, sy = round(cx + x * radius), round(cy - y * radius)
                    if 0 <= sx < N and 0 <= sy < N:
                        px[sx, sy] = GOLD

    inside = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "src", "main", "resources", "universe_logo.png")
    page = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "docs", "release", "icon.png")
    for path, size in ((inside, 128), (page, 512)):
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        img.resize((size, size), Image.NEAREST).save(path)
        print(path, size)


if __name__ == "__main__":
    main()
