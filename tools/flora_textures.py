"""生成星球植物的灰度贴图（16×16），游戏里按物种染色。

用法：python tools/flora_textures.py [输出目录]
默认输出到 src/main/resources/assets/universe/textures/block。

每种地面植物两种外形（a、b），每种外形两层：body 是叶和茎，detail 是花、果、菌盖、晶尖。
灰度越亮染出来越接近物种颜色，所以主体用 150–240 的灰，暗一些的描边让形状清楚。
透明像素决定方块走镂空渲染（26.x 按贴图透明度自动选渲染层）。
"""
import math
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures", "block")
N = 16


def new():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def put(img, x, y, v, a=255):
    x = int(round(x))
    y = int(round(y))
    if 0 <= x < N and 0 <= y < N:
        v = max(0, min(255, int(v)))
        img.putpixel((x, y), (v, v, v, a))


def get_alpha(img, x, y):
    if 0 <= x < N and 0 <= y < N:
        return img.getpixel((x, y))[3]
    return 0


def line(img, x0, y0, x1, y1, v0, v1=None):
    v1 = v0 if v1 is None else v1
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        put(img, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, v0 + (v1 - v0) * t)


def curve(img, x0, y0, x1, y1, bend, v0, v1, width=1):
    """二次曲线：从 (x0, y0) 到 (x1, y1)，中点横向偏 bend。"""
    mx = (x0 + x1) / 2 + bend
    my = (y0 + y1) / 2
    steps = 40
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * mx + t * t * x1
        y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * my + t * t * y1
        pts.append((x, y, v0 + (v1 - v0) * t))
        put(img, x, y, v0 + (v1 - v0) * t)
        if width > 1 and t < 0.6:
            put(img, x + 1, y, v0 + (v1 - v0) * t - 25)
    return pts


def disc(img, cx, cy, r, v, rim=40, highlight=True):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= r + 0.25:
                shade = v
                if d > r - 0.9:
                    shade = v - rim
                elif highlight and math.hypot(x - (cx - r * 0.35), y - (cy - r * 0.35)) < r * 0.35:
                    shade = min(255, v + 25)
                put(img, x, y, shade)


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))


# ---------------------------------------------------------------- 地面植物

def tuft(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        for i in range(7):
            x0 = 4 + i * 1.3 + rng.uniform(-0.4, 0.4)
            h = rng.randint(5, 10)
            tip_x = x0 + (x0 - 8) * 0.5 + rng.uniform(-1, 1)
            curve(body, x0, 15, tip_x, 15 - h, rng.uniform(-1.5, 1.5), 150, 230)
            if i % 3 == 1:
                put(detail, tip_x, 15 - h, 235)
                put(detail, tip_x, 14 - h, 205)
    else:
        for i in range(11):
            x0 = 3 + i * 0.95 + rng.uniform(-0.3, 0.3)
            h = rng.randint(3, 7)
            tip_x = x0 + (x0 - 8) * 0.7
            curve(body, x0, 15, tip_x, 15 - h, rng.uniform(-1, 1), 145, 220)
        for x, y in ((5, 9), (9, 8), (11, 10), (7, 10)):
            put(detail, x, y, 240)
    return body, detail


def blades(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        for i in range(6):
            x0 = 5 + i * 1.2
            side = -1 if i < 3 else 1
            h = rng.randint(10, 14)
            curve(body, x0, 15, x0 + side * rng.uniform(3, 6), 15 - h, -side * rng.uniform(1, 3), 145, 235, width=2)
        # 两根抽穗
        for x0, h in ((7, 15), (9, 13)):
            line(body, x0, 15, x0, 16 - h, 160, 190)
            for y in range(16 - h, 16 - h + 4):
                put(detail, x0, y, 230 - (y - (16 - h)) * 10)
                put(detail, x0 + (1 if y % 2 else -1), y, 205)
    else:
        for i in range(5):
            x0 = 4 + i * 2 + rng.uniform(-0.5, 0.5)
            h = rng.randint(11, 15)
            line(body, x0, 15, x0 + rng.uniform(-1, 1), 16 - h, 150, 235)
            line(body, x0 + 1, 15, x0 + 1, 18 - h, 140, 200)
            put(detail, x0, 16 - h + 2, 220)
            put(detail, x0 + 1, 16 - h + 4, 220)
    return body, detail


def fern(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        for side in (-1, 1):
            for k in range(2):
                pts = curve(body, 8, 15, 8 + side * (5 + k * 2), 3 + k * 4, side * -3, 140, 210)
                for j, (x, y, v) in enumerate(pts):
                    if j % 5 == 2 and j > 5:
                        put(body, x + side, y + 1, v + 10)
                        put(body, x - side * 0.5, y - 1, v + 15)
                        put(detail, x + side * 2, y + 1, 225)
    else:
        for i in range(5):
            a = math.pi * (0.15 + 0.7 * i / 4)
            x1 = 8 - math.cos(a) * 7
            y1 = 15 - math.sin(a) * 11
            pts = curve(body, 8, 15, x1, y1, (8 - x1) * 0.3, 145, 215)
            for j, (x, y, v) in enumerate(pts):
                if j % 6 == 3:
                    put(body, x + 1, y, v + 15)
                    put(body, x - 1, y, v + 15)
            # 卷起的嫩芽
            put(detail, x1, y1, 235)
            put(detail, x1 + (1 if x1 < 8 else -1), y1, 215)
            put(detail, x1, y1 + 1, 200)
    return body, detail


def flower(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        curve(body, 8, 15, 8, 5, 1, 140, 190)
        curve(body, 8, 13, 4, 10, -1, 150, 210)
        curve(body, 8, 12, 12, 9, 1, 150, 210)
        cx, cy = 8, 4
        for k in range(5):
            a = k * math.tau / 5 - math.pi / 2
            disc(detail, cx + math.cos(a) * 2.2, cy + math.sin(a) * 2.2, 1.3, 230, rim=35, highlight=False)
        disc(detail, cx, cy, 1, 160, rim=0, highlight=False)
    else:
        for x1, y1, bend in ((4, 6, -1), (8, 3, 0.5), (12, 7, 1)):
            curve(body, 8, 15, x1, y1, bend, 140, 190)
            disc(detail, x1, y1, 1.6, 225, rim=45, highlight=False)
            put(detail, x1, y1, 255)
        curve(body, 8, 14, 5, 12, -1, 150, 205)
        curve(body, 8, 14, 11, 12, 1, 150, 205)
    return body, detail


def bulb(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        line(body, 8, 15, 8, 9, 150, 185)
        curve(body, 8, 15, 4, 12, -1, 160, 215, width=2)
        curve(body, 8, 15, 12, 11, 1, 160, 215, width=2)
        disc(detail, 8, 6, 3.4, 220, rim=45)
    else:
        for x1, y1, r in ((4.5, 7, 1.8), (8.5, 4, 2.3), (11.5, 8.5, 1.6)):
            curve(body, 8, 15, x1, y1 + r, (x1 - 8) * 0.4, 150, 195)
            disc(detail, x1, y1, r, 225, rim=45)
        curve(body, 8, 15, 5, 13, -1, 160, 210)
        curve(body, 8, 15, 11, 13, 1, 160, 210)
    return body, detail


def fungus(shape, rng):
    body, detail = new(), new()

    def mushroom(cx, top, width, stem_h):
        for y in range(top + 2, min(16, top + 2 + stem_h)):
            put(body, cx, y, 215)
            put(body, cx - 1, y, 190)
            if width > 5:
                put(body, cx + 1, y, 170)
        half = width / 2
        for dy in range(3):
            w = half - (2 - dy) * 0.9 if dy < 2 else half
            for x in range(int(cx - w), int(cx + w) + 1):
                v = 225 - dy * 10
                if x in (int(cx - w), int(cx + w)):
                    v -= 40
                put(detail, x, top + dy, v)
        # 菌盖上的斑点
        for x in range(int(cx - half) + 1, int(cx + half), 3):
            put(detail, x, top + 1, 255)

    if shape == "a":
        mushroom(8, 5, 10, 11)
    else:
        mushroom(5, 8, 6, 8)
        mushroom(11, 4, 7, 12)
        mushroom(8, 11, 4, 5)
    return body, detail


def tendril(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        # 三根茎，顶上卷成螺旋
        for x0, top, turn in ((4.5, 6, 1), (8, 3, -1), (11.5, 7, 1)):
            curve(body, x0, 15, x0 + turn * 0.5, top + 2, turn * 1.2, 150, 205, width=2)
            # 茎顶接一段收紧的弧：从左侧绕过顶部再往里卷
            cx, cy = x0 + turn * 2.2, top + 2
            prev = None
            for i in range(9):
                a = math.pi - i * 0.55
                r = 2.4 - i * 0.17
                point = (cx + turn * math.cos(a) * r, cy - math.sin(a) * r)
                if prev:
                    line(body, prev[0], prev[1], point[0], point[1], 205 + i * 5)
                prev = point
            put(detail, prev[0], prev[1], 250)
            put(detail, x0, 11, 225)
    else:
        for i in range(4):
            x0 = 4 + i * 2.5
            phase = rng.uniform(0, math.tau)
            last = None
            for y in range(15, 2, -1):
                x = x0 + math.sin(y * 0.6 + phase) * 1.4
                put(body, x, y, 150 + (15 - y) * 6)
                last = (x, y)
                if y in (11, 7):
                    put(detail, x + 1, y, 225)
                    put(detail, x - 1, y, 225)
            put(detail, last[0], last[1] - 1, 245)
    return body, detail


def reed(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        for x0, h in ((4, 13), (7, 15), (10, 14), (12, 11)):
            line(body, x0, 15, x0, 16 - h, 150, 200)
            for y in range(16 - h + 1, 16 - h + 5):
                put(detail, x0, y, 200)
                put(detail, x0 + 1, y, 170)
            put(body, x0, 15 - h + 1, 220)
        curve(body, 6, 15, 2, 7, -1, 150, 210)
        curve(body, 9, 15, 14, 6, 1, 150, 210)
    else:
        for x0, h in ((5, 15), (8, 13), (11, 15)):
            line(body, x0, 15, x0 + 0.5, 16 - h, 150, 205)
            for k in range(4):
                put(detail, x0 - 1 + k % 3, 16 - h + k, 235 - k * 8)
            curve(body, x0, 13, x0 + (-3 if x0 < 8 else 3), 8, 0, 155, 210)
    return body, detail


def succulent(shape, rng):
    body, detail = new(), new()
    if shape == "a":
        for k in range(7):
            a = math.pi * (0.08 + 0.84 * k / 6)
            length = 6 + (3 if 2 <= k <= 4 else 0)
            x1 = 8 - math.cos(a) * length
            y1 = 15 - math.sin(a) * length
            line(body, 8, 15, x1, y1, 160, 230)
            line(body, 8 + (0.6 if k > 3 else -0.6), 15, x1, y1 + 0.5, 140, 200)
        line(body, 8, 15, 8, 6, 160, 190)
        disc(detail, 8, 4, 1.6, 230, rim=40, highlight=False)
    else:
        for y in range(5, 16):
            w = 4.5 * math.sqrt(max(0, 1 - ((y - 10.5) / 6) ** 2))
            for x in range(int(8 - w), int(8 + w) + 1):
                rib = 215 if (x - 8) % 2 == 0 else 180
                if x in (int(8 - w), int(8 + w)):
                    rib -= 45
                put(body, x, y, rib)
        for x, y in ((5, 8), (11, 8), (4, 11), (12, 11), (6, 6), (10, 6)):
            put(detail, x, y, 240)
        disc(detail, 8, 4, 1.4, 230, rim=40, highlight=False)
    return body, detail


def lichen(shape, rng):
    """地衣贴在地面上，贴图是俯视的一片斑块。"""
    body, detail = new(), new()
    blobs = 6 if shape == "a" else 10
    for _ in range(blobs):
        cx, cy = rng.uniform(2, 14), rng.uniform(2, 14)
        r = rng.uniform(1.5, 3.2) if shape == "a" else rng.uniform(0.8, 2)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - cx, y - cy) + rng.uniform(-0.6, 0.6)
                if d <= r:
                    put(body, x, y, 200 if d < r - 1 else 165)
    for _ in range(9 if shape == "a" else 14):
        x, y = rng.randint(1, 14), rng.randint(1, 14)
        if body.getpixel((x, y))[3]:
            put(detail, x, y, 240)
    return body, detail


def crystal_bloom(shape, rng):
    body, detail = new(), new()

    def shard(x0, x1, tip_x, tip_y, base_y=15):
        for y in range(tip_y, base_y + 1):
            t = (y - tip_y) / max(1, base_y - tip_y)
            left = tip_x + (x0 - tip_x) * t
            right = tip_x + (x1 - tip_x) * t
            for x in range(int(math.floor(left)), int(math.ceil(right)) + 1):
                v = 230 if x < (left + right) / 2 else 180
                put(body, x, y, v)
            put(detail, left, y, 250)
        put(detail, tip_x, tip_y, 255)

    if shape == "a":
        shard(6, 9, 8, 2)
        shard(3, 6, 3, 7)
        shard(10, 13, 13, 6)
        shard(8, 11, 10, 9)
    else:
        shard(6, 10, 8, 1)
        shard(4, 6, 4, 10)
        shard(10, 12, 12, 8)
    return body, detail


PLANTS = {
    "tuft": tuft, "blades": blades, "fern": fern, "flower": flower, "bulb": bulb, "fungus": fungus,
    "tendril": tendril, "reed": reed, "succulent": succulent, "lichen": lichen, "crystal_bloom": crystal_bloom,
}


# ---------------------------------------------------------------- 树木方块

def log_side(rng):
    img = new()
    for x in range(N):
        base = 175 + (18 if x % 4 in (1, 2) else 0) + rng.randint(-8, 8)
        for y in range(N):
            v = base + rng.randint(-10, 10)
            if (x + y * 3) % 11 == 0 or (x % 4 == 0 and rng.random() < 0.6):
                v -= 45
            put(img, x, y, v)
    return img


def log_top(rng):
    img = new()
    for y in range(N):
        for x in range(N):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                v = 150 + rng.randint(-10, 10)
            else:
                ring = math.hypot(x - 7.5, y - 7.5)
                v = 215 - (25 if int(ring) % 2 == 0 else 0) + rng.randint(-6, 6)
            put(img, x, y, v)
    return img


def leaves(rng):
    img = new()
    for y in range(N):
        for x in range(N):
            if rng.random() < 0.18:
                continue
            v = rng.choice((150, 175, 195, 215, 235))
            put(img, x, y, v)
    # 几簇亮叶
    for _ in range(6):
        cx, cy = rng.randint(1, 14), rng.randint(1, 14)
        put(img, cx, cy, 245)
        put(img, cx + 1, cy, 225)
    return img


def cap(rng):
    img = new()
    for y in range(N):
        for x in range(N):
            put(img, x, y, 200 + rng.randint(-14, 14))
    for _ in range(7):
        cx, cy, r = rng.uniform(1, 15), rng.uniform(1, 15), rng.uniform(1, 2.2)
        for y in range(N):
            for x in range(N):
                if math.hypot(x - cx, y - cy) < r:
                    put(img, x, y, 245)
    return img


def bloom(rng):
    """一串果子或花苞：亮的圆球，中间透空。"""
    img = new()
    for cx, cy, r in ((4, 4, 2.6), (11, 3.5, 2.4), (8, 9, 3), (3.5, 12, 2.2), (12.5, 12, 2.4)):
        disc(img, cx, cy, r, 230, rim=55)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in PLANTS.items():
        for shape in ("a", "b"):
            rng = random.Random(f"{name}/{shape}")
            body, detail = fn(shape, rng)
            save(body, f"planet_{name}_{shape}")
            save(detail, f"planet_{name}_{shape}_detail")
    rng = random.Random("trees")
    save(log_side(rng), "planet_log")
    save(log_top(rng), "planet_log_top")
    save(leaves(rng), "planet_leaves")
    save(cap(rng), "planet_cap")
    save(bloom(rng), "planet_bloom")


if __name__ == "__main__":
    main()
