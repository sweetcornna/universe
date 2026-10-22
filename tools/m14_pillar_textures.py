"""生成守卫塔（ADR-0026）的方块贴图（16×16 像素画）：外壳、灯带、终端正面。

用法：python tools/m14_pillar_textures.py [方块贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block。

配色对齐守卫机（tools/combat_textures.py）：外壳是守卫机机身的灰白面板，灯带和终端屏幕是巡逻扫描光的琥珀色。
随机数用固定种子，每次生成的结果一样。
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
BLOCK = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "block")
N = 16

WHITE = (206, 210, 216)
AMBER = (232, 176, 58)
GLOW = (255, 214, 120)
SCREEN = (14, 14, 18)


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


def noise(img, rng, amount):
    for y in range(N):
        for x in range(N):
            put(img, x, y, shade(get(img, x, y), 1 + rng.uniform(-amount, amount)))


def save(img, name):
    os.makedirs(BLOCK, exist_ok=True)
    img.save(os.path.join(BLOCK, name + ".png"))


def plating_base(rng):
    """守卫机机身那样的灰白面板：四块板拼成，板缝一道暗一道亮，四角铆钉，左上亮右下暗。"""
    img = new(WHITE)
    noise(img, rng, 0.03)
    # 板缝：中间一横一竖
    for i in range(N):
        put(img, i, 7, shade(WHITE, 0.62))
        put(img, i, 8, shade(WHITE, 1.08))
        put(img, 7, i, shade(WHITE, 0.62))
        put(img, 8, i, shade(WHITE, 1.08))
    # 外框
    for i in range(N):
        put(img, i, 0, shade(WHITE, 1.1))
        put(img, 0, i, shade(WHITE, 1.1))
        put(img, i, N - 1, shade(WHITE, 0.58))
        put(img, N - 1, i, shade(WHITE, 0.58))
    # 每块板一颗铆钉
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        put(img, x, y, shade(WHITE, 1.2))
        put(img, x + 1, y + 1, shade(WHITE, 0.55))
    return img


def plating():
    rng = random.Random(2601)
    img = plating_base(rng)
    # 右下那块板上一小段琥珀色的标识条，和守卫机侧面的一样
    for x in range(10, 14):
        put(img, x, 10, AMBER)
    save(img, "sentinel_pillar_plating")


def light():
    """灯带：深色框里一条发亮的琥珀色光，中间最亮，上下有几道扫描线。"""
    rng = random.Random(2602)
    img = new(shade(WHITE, 0.45))
    for y in range(N):
        for x in range(N):
            edge = x in (0, N - 1) or y in (0, N - 1)
            if edge:
                put(img, x, y, shade(WHITE, 0.4 if x == N - 1 or y == N - 1 else 0.7))
                continue
            # 横着离中间越远越暗
            d = abs(x - 7.5) / 6.5
            color = mix(GLOW, AMBER, min(1.0, d * 1.3))
            if d > 0.85:
                color = shade(AMBER, 0.75)
            if y % 4 == 0:
                color = shade(color, 0.88)
            put(img, x, y, shade(color, 1 + rng.uniform(-0.03, 0.03)))
    save(img, "sentinel_pillar_light")


def terminal_front():
    """终端正面：外壳上开一块黑屏，屏上一道发亮的眼缝（守卫机的面罩），下面三行读数。"""
    rng = random.Random(2603)
    img = plating_base(rng)
    for y in range(2, 14):
        for x in range(2, 14):
            border = x in (2, 13) or y in (2, 13)
            put(img, x, y, shade(WHITE, 0.5) if border else SCREEN)
    # 眼缝
    for x in range(4, 12):
        t = abs(x - 7.5) / 4
        put(img, x, 5, mix(GLOW, AMBER, t))
        put(img, x, 6, mix(AMBER, shade(AMBER, 0.55), t))
    # 读数
    for row, length in ((9, 6), (10, 4), (11, 7)):
        for x in range(4, 4 + length):
            put(img, x, row, shade(AMBER, 0.55 if x % 2 else 0.75))
    save(img, "sentinel_pillar_terminal_front")


if __name__ == "__main__":
    plating()
    light()
    terminal_front()
