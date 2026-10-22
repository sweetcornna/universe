"""生成 M14（ADR-0026）野外地标的贴图：化石土、雷击石、打捞舱（舱顶关着和开着、侧面、底面）；
螺壳化石、节肢动物化石、头骨化石、打捞的废料四个物品。

用法：python tools/m14_wild_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

照着现实里的原型画：
- 化石土是一层层的沉积土：灰褐色，几道深浅不一的水平层理，露着几块米白的碎骨和一个螺壳的印子。
- 雷击石照沙漠里挖出来的雷击石画：外面一层粗糙的、粘满沙粒的黄褐色硬壳，中间是分叉的、像闪电一样的
  灰绿色玻璃质，玻璃上有亮点。
- 打捞舱是一个烧黑了的金属舱：舱顶一个圆形舱门，四周一圈黄黑警示条和螺栓；开着的舱门翻到一边，露出黑洞洞的舱里；
  侧面是灰白的舱板，一道橙色的条，下半截烧焦了。
- 螺壳化石照菊石画：一圈圈盘起来的壳，壳上一道道横肋，石头的米灰色。
- 节肢动物化石照三叶虫画：一块深灰的石板上，椭圆的身体分成头、胸节、尾三段，中间一条隆起的轴。
- 头骨化石照鱼龙的头骨画：长长的吻部，一个大眼眶，骨头是米黄色的，带着褐色的斑。
- 打捞的废料是一捆变形的零件：弯了的锈板、一截铜线、半块电路板。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, ITEM, N, shade, mix, clear, new, put, rect, outline, save, noise  # noqa: E402


def filled(img, x, y):
    return 0 <= x < N and 0 <= y < N and img.getpixel((x, y))[3] != 0


# ---- 方块

SOIL = (112, 96, 80)
BONE = (226, 214, 186)


def fossil_dirt():
    rng = random.Random(2701)
    img = new(SOIL)
    # 沉积层：几道水平的深浅条带，边上不齐
    for y in range(N):
        band = math.sin(y * 0.9 + 1.3) * 0.08 + (0.07 if y % 5 == 2 else 0) - (0.08 if y % 7 == 4 else 0)
        for x in range(N):
            put(img, x, y, shade(SOIL, 1 + band + rng.uniform(-0.06, 0.06)))
    for _ in range(14):
        x, y = rng.randrange(N), rng.randrange(N)
        put(img, x, y, shade(SOIL, rng.choice((0.7, 1.25))))
    # 螺壳的印子（左上）
    cx, cy = 4.5, 4.5
    for i in range(40):
        t = i / 39 * 2.2 * math.pi
        r = 0.6 + t * 0.48
        x, y = round(cx + math.cos(t) * r), round(cy + math.sin(t) * r)
        put(img, x, y, shade(BONE, 0.82 + 0.1 * math.cos(t * 3)))
    # 碎骨：右下一截长骨，左下两小块
    for i in range(6):
        put(img, 9 + i, 11 - i // 2, shade(BONE, 1.0 - i * 0.03))
        put(img, 9 + i, 12 - i // 2, shade(BONE, 0.78))
    put(img, 8, 11, BONE)
    put(img, 15, 8, BONE)
    for (x, y) in ((2, 12), (3, 12), (2, 13), (6, 14), (12, 3), (13, 3)):
        put(img, x, y, shade(BONE, rng.uniform(0.75, 0.95)))
    save(img, BLOCK, "fossil_dirt")


CRUST = (188, 160, 112)
GLASS = (120, 132, 112)


def fulgurite():
    rng = random.Random(2711)
    img = new(CRUST)
    for y in range(N):
        for x in range(N):
            # 粘着的沙粒：颗粒感强一点
            grain = rng.choice((0.82, 0.92, 1.0, 1.0, 1.08, 1.16))
            put(img, x, y, shade(CRUST, grain))
    # 闪电一样分叉的玻璃质：从上往下走，随机分岔
    branches = [(rng.uniform(4, 7), 0.0, rng.uniform(-0.3, 0.3), 2.0), (rng.uniform(10, 13), 0.0, rng.uniform(-0.3, 0.3), 1.4)]
    cells = set()
    while branches:
        x, y, dx, width = branches.pop()
        while y < N and width > 0.4:
            for w in range(-int(width), int(width) + 1):
                cells.add((int(round(x + w)) % N, int(y)))
            x += dx + rng.uniform(-0.6, 0.6)
            y += 1
            width -= 0.06
            if rng.random() < 0.12 and width > 0.8:
                branches.append((x, y, rng.choice((-0.9, 0.9)), width * 0.6))
    for (x, y) in cells:
        put(img, x, y, shade(GLASS, rng.uniform(0.8, 1.05)))
    # 玻璃的边发暗，中间偶尔一个亮点
    for (x, y) in cells:
        for ox, oy in ((1, 0), (-1, 0)):
            nx = (x + ox) % N
            if (nx, y) not in cells:
                put(img, nx, y, shade(CRUST, 0.62))
        if rng.random() < 0.18:
            put(img, x, y, (226, 236, 214))
    save(img, BLOCK, "fulgurite")


HULL = (176, 180, 184)
STRIPE = (214, 116, 44)
HAZARD_Y = (214, 172, 46)
HAZARD_K = (40, 40, 44)


def capsule_frame(img):
    """舱顶的一圈：黄黑警示条，四角螺栓。"""
    for i in range(N):
        for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
            put(img, x, y, HAZARD_Y if ((x + y) // 2) % 2 == 0 else HAZARD_K)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, shade(HULL, 1.3))
        put(img, x + 1, y + 1, shade(HULL, 0.5))


def salvage_capsule_top(opened):
    rng = random.Random(2721)
    img = new(shade(HULL, 0.82))
    noise(img, rng, 0.06)
    capsule_frame(img)
    cx, cy = 7.5, 7.5
    for y in range(1, N - 1):
        for x in range(1, N - 1):
            d = math.hypot(x - cx, y - cy)
            if opened and d <= 5.2:
                # 舱里：黑的，边上一圈反光，底下几样东西的轮廓
                put(img, x, y, shade((26, 28, 32), 1 + 0.25 * (d / 5.2)))
            elif d <= 5.2:
                put(img, x, y, shade(HULL, 1.05 - 0.04 * (y - cy) / 5))
            elif d <= 6.2:
                put(img, x, y, shade(HULL, 0.55))
    if opened:
        for (x, y, c) in ((6, 8, (196, 112, 58)), (7, 8, (196, 112, 58)), (9, 6, (60, 120, 70)), (9, 7, (60, 120, 70)),
                          (5, 6, (120, 124, 130))):
            put(img, x, y, c)
        # 翻开的舱门：一条斜着的厚边压在右上
        for i in range(7):
            put(img, 9 + i // 2, 1 + i, shade(HULL, 1.2))
            put(img, 10 + i // 2, 1 + i, shade(HULL, 0.9))
    else:
        # 舱门上的把手和锁紧的螺栓
        rect(img, 6, 7, 9, 8, shade(HULL, 0.6))
        put(img, 6, 7, shade(HULL, 1.25))
        for a in range(8):
            t = a / 8 * 2 * math.pi
            put(img, round(cx + math.cos(t) * 4.4), round(cy + math.sin(t) * 4.4), shade(HULL, 0.62))
    save(img, BLOCK, "salvage_capsule_top_open" if opened else "salvage_capsule_top")


def salvage_capsule_side():
    rng = random.Random(2731)
    img = new(HULL)
    noise(img, rng, 0.05)
    for x in range(N):
        put(img, x, 0, shade(HULL, 1.25))
        put(img, x, 4, STRIPE)
        put(img, x, 5, shade(STRIPE, 0.8))
        put(img, x, N - 1, shade(HULL, 0.5))
    for y in range(N):
        put(img, 7, y, shade(HULL, 0.72))
        put(img, 8, y, shade(HULL, 1.12))
    # 一盏信标灯
    put(img, 3, 2, (255, 214, 120))
    put(img, 12, 2, shade(HULL, 0.6))
    # 下半截烧焦
    for y in range(8, N):
        for x in range(N):
            if rng.random() < (y - 7) / 9:
                put(img, x, y, shade(mix(HULL, (40, 34, 30), rng.uniform(0.4, 0.9)), 1))
    save(img, BLOCK, "salvage_capsule_side")


def salvage_capsule_bottom():
    rng = random.Random(2741)
    img = new((58, 56, 54))
    noise(img, rng, 0.08)
    save(img, BLOCK, "salvage_capsule_bottom")


# ---- 物品

STONE = (184, 170, 146)


def shell_fossil():
    img = clear()
    cx, cy = 7.5, 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= 6.8:
                # 左上亮、右下暗的圆盘
                put(img, x, y, shade(STONE, 1.1 - d / 6.8 * 0.2 + 0.08 * (-(x - cx) - (y - cy)) / 6.8))
    # 盘绕的缝：一条往外越绕越宽的螺线
    for i in range(160):
        t = i / 159 * 2.6 * math.pi
        r = 0.8 + t * 0.72
        if r > 6.6:
            break
        put(img, round(cx + math.cos(t) * r), round(cy + math.sin(t) * r), shade(STONE, 0.55))
    # 最外一圈的横肋
    for k in range(14):
        t = k / 14 * 2 * math.pi
        for r in (5.2, 6.0):
            put(img, round(cx + math.cos(t) * r), round(cy + math.sin(t) * r), shade(STONE, 0.78))
    outline(img, (70, 60, 48))
    save(img, ITEM, "shell_fossil")


SLATE = (88, 90, 92)
TRILO = (150, 138, 116)


def arthropod_fossil():
    img = clear()
    rng = random.Random(2751)
    # 石板
    for y in range(1, 15):
        for x in range(2, 14):
            if (x in (2, 13) and y in (1, 14)) or rng.random() < 0.03:
                continue
            put(img, x, y, shade(SLATE, rng.uniform(0.9, 1.1)))
    # 三叶虫：椭圆的身子，头是一块宽的半月，胸节一道道横纹，尾巴收成一个小扇；中间一条隆起的轴，两边的肋叶
    cx, cy = 7.5, 7.8
    for y in range(N):
        for x in range(N):
            u, v = (x - cx) / 4.6, (y - cy) / 6.0
            if u * u + v * v > 1:
                continue
            f = 1.0
            if y <= 4:
                f = 1.12 if abs(x - cx) <= 1.5 else 1.02  # 头
            elif y == 5 or (6 <= y <= 11 and y % 2 == 1):
                f = 0.74  # 胸节之间的缝
            if abs(x - cx) <= 1.0 and y > 4:
                f *= 1.2  # 轴
            elif abs(x - cx) <= 1.8 and y > 4:
                f *= 0.8  # 轴两边的沟
            put(img, x, y, shade(TRILO, f))
    # 一对眼
    put(img, 5, 4, shade(TRILO, 0.55))
    put(img, 10, 4, shade(TRILO, 0.55))
    outline(img, (40, 40, 42))
    save(img, ITEM, "arthropod_fossil")


def skull_fossil():
    img = clear()
    rng = random.Random(2761)
    # 侧面的头骨，斜着放：圆的颅骨在左下，长吻伸向右上
    ang = math.radians(35)
    ca, sa = math.cos(ang), math.sin(ang)
    ox, oy = 4.2, 11.0
    for y in range(N):
        for x in range(N):
            u = (x - ox) * ca - (y - oy) * sa   # 沿吻部
            v = (x - ox) * sa + (y - oy) * ca   # 往下为正
            if u < -3.4 or u > 13.2:
                continue
            if u < 1.5:
                inside = (u / 3.4) ** 2 + (v / 3.4) ** 2 <= 1  # 颅骨
            else:
                top = -3.0 + (u - 1.5) * 0.2
                bottom = 2.6 - (u - 1.5) * 0.17
                inside = top <= v <= bottom
            if inside:
                f = 1.08 - 0.1 * (v + 3) / 6
                put(img, x, y, shade(BONE, f * rng.uniform(0.94, 1.04)))
    def at(u, v):
        return round(ox + u * ca + v * sa), round(oy - u * sa + v * ca)
    # 大眼眶
    for (u, v) in ((-0.6, -0.8), (0.4, -0.8), (-0.6, 0.2), (0.4, 0.2), (1.2, -0.3)):
        put(img, *at(u, v), (52, 42, 34))
    # 上下颌之间的缝，缝上一排牙
    for i in range(10):
        u = 2.0 + i
        put(img, *at(u, 1.0 - i * 0.06), (98, 80, 60))
        if i % 2 == 0:
            put(img, *at(u, 0.2 - i * 0.06), shade(BONE, 1.18))
    put(img, *at(10.5, -1.4), (90, 74, 56))
    # 褐色的斑
    for _ in range(9):
        x, y = rng.randrange(N), rng.randrange(N)
        if filled(img, x, y):
            put(img, x, y, (156, 120, 80))
    outline(img, (64, 52, 40))
    save(img, ITEM, "skull_fossil")


RUST = (150, 86, 50)


def salvaged_scrap():
    img = clear()
    rng = random.Random(2771)
    # 一块弯了的板，边上锈了
    for y in range(5, 13):
        for x in range(2, 11):
            bend = 1 if x > 6 else 0
            yy = y - bend
            if 4 <= yy <= 12:
                base = mix(HULL, RUST, rng.uniform(0, 0.25) + (0.45 if x in (2, 10) or y in (5, 12) else 0))
                put(img, x, yy, shade(base, 0.92 if x > 6 else 1.05))
    for (x, y) in ((3, 6), (9, 5), (3, 11), (9, 10)):
        put(img, x, y, shade(HULL, 1.3))
    # 半块电路板压在上面
    rect(img, 8, 2, 13, 6, (46, 110, 64))
    for (x, y) in ((9, 3), (11, 3), (12, 5), (10, 5)):
        put(img, x, y, (214, 180, 60))
    put(img, 13, 2, (0, 0, 0), 0)
    # 一截铜线绕出来
    for i in range(9):
        t = i / 8
        put(img, round(11 + 3 * math.sin(t * 3.0)), 7 + i, (200, 112, 52))
    outline(img, (36, 34, 34))
    save(img, ITEM, "salvaged_scrap")


def main():
    fossil_dirt()
    fulgurite()
    salvage_capsule_top(False)
    salvage_capsule_top(True)
    salvage_capsule_side()
    salvage_capsule_bottom()
    shell_fossil()
    arthropod_fossil()
    skull_fossil()
    salvaged_scrap()


if __name__ == "__main__":
    main()
