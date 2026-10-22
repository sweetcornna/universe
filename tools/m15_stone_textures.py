"""生成知识石（ADR-0027 §五）的方块贴图（16×16 像素画）：石柱下半、上半的侧面，顶面。

用法：python tools/m15_stone_textures.py [方块贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block。

石料是偏蓝的深灰板岩，四边一道凿出来的框；框里一行一行刻着发光的外星文字，颜色对齐扫描标记的淡紫（0xC8A0FF）。
和先驱者石碑（tools/poi_textures.py 的 monolith）区分开：石碑是紫黑的石头上几个大符文，这里是一段段小字，像对照文本。
上半顶上刻一个圆环，顶面是一圈环纹。随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
BLOCK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "block")
N = 16

SLATE = (54, 60, 76)
SCRIPT = (226, 204, 255)
GLOW = (200, 160, 255)

# 小字：2×3 的笔画，拼成一个个"词"
LETTERS = [
    ["#.", "##", ".#"],
    ["##", "#.", "##"],
    [".#", "##", "#."],
    ["#.", "#.", "##"],
    ["##", ".#", "#."],
    ["#.", ".#", "#."],
    ["##", "..", "##"],
    [".#", "#.", ".#"],
    ["#.", "##", "#."],
]


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(color):
    return Image.new("RGBA", (N, N), tuple(color) + (255,))


def put(img, x, y, color):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), tuple(color[:3]) + (255,))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def save(img, name):
    os.makedirs(BLOCK, exist_ok=True)
    img.save(os.path.join(BLOCK, name + ".png"))


def slate(rng):
    """板岩：一点颗粒，几道横着的细纹。"""
    img = new(SLATE)
    for y in range(N):
        for x in range(N):
            put(img, x, y, shade(SLATE, 1 + rng.uniform(-0.08, 0.08)))
    for _ in range(4):
        x, y = rng.randrange(N), rng.randrange(N)
        for i in range(rng.randrange(2, 5)):
            put(img, x + i, y, shade(SLATE, 0.78))
    return img


def frame(img, top=True, bottom=True):
    """凿出来的框：外圈一道亮边，里面一道暗槽。上下接着的两半不画接缝那一边。"""
    for i in range(N):
        put(img, 0, i, shade(SLATE, 1.18))
        put(img, N - 1, i, shade(SLATE, 0.7))
        put(img, 1, i, shade(SLATE, 0.62))
        put(img, N - 2, i, shade(SLATE, 0.62))
    if top:
        for i in range(N):
            put(img, i, 0, shade(SLATE, 1.18))
            if 1 <= i <= N - 2:
                put(img, i, 1, shade(SLATE, 0.62))
    if bottom:
        for i in range(N):
            put(img, i, N - 1, shade(SLATE, 0.7))
            if 1 <= i <= N - 2:
                put(img, i, N - 2, shade(SLATE, 0.62))


def line(img, rng, y):
    """一行字：框里 x = 3..12，字和字之间空一格，词和词之间空两格。"""
    x = 3 + rng.randrange(2)
    while True:
        letters = rng.randrange(1, 4)
        if x + 2 * letters + (letters - 1) > 13:
            letters = (13 - x + 1) // 3
        if letters <= 0:
            break
        for _ in range(letters):
            pattern = rng.choice(LETTERS)
            for j, row in enumerate(pattern):
                for i, ch in enumerate(row):
                    if ch == "#":
                        put(img, x + i, y + j, SCRIPT)
            x += 3
        x += 1


def halo(img):
    """字周围淡淡的光。"""
    out = img.copy()
    for y in range(N):
        for x in range(N):
            if get(img, x, y) == SCRIPT:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < N and 0 <= ny < N and get(img, nx, ny) != SCRIPT:
                        put(out, nx, ny, mix(get(img, nx, ny), GLOW, 0.2))
    return out


def lower():
    rng = random.Random(1501)
    img = slate(rng)
    frame(img, top=False, bottom=True)
    for y in (1, 5, 9):
        line(img, rng, y)
    save(halo(img), "knowledge_stone_lower")


def upper():
    rng = random.Random(1502)
    img = slate(rng)
    frame(img, top=True, bottom=False)
    # 顶上的圆环：半径 2.5，正中一个亮点
    cx, cy = 7.5, 4.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 2.6) < 0.5 or d < 0.8:
                put(img, x, y, SCRIPT)
    # 两行字，接着下半的字往下排
    for y in (9, 13):
        line(img, rng, y)
    save(halo(img), "knowledge_stone_upper")


def top():
    rng = random.Random(1503)
    img = slate(rng)
    frame(img)
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 4.6) < 0.5:
                put(img, x, y, mix(SLATE, GLOW, 0.45))
            elif abs(d - 2.2) < 0.5:
                put(img, x, y, mix(SLATE, GLOW, 0.7))
            elif d < 0.8:
                put(img, x, y, SCRIPT)
    save(img, "knowledge_stone_top")


if __name__ == "__main__":
    lower()
    upper()
    top()
