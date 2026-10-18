"""生成战斗相关的贴图：等离子弹的光斑、守卫机（普通和重型）的皮肤和发光层、守卫机残骸、刷怪蛋。

用法：python tools/combat_textures.py [实体贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity 和 textures/item。

- 等离子弹的贴图是白色的，渲染时按弹体的颜色着色（多功能工具青色，守卫机红色），透明度从中心往外衰减。
- 守卫机的方块列表和 client/sentinel/SentinelModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
  发光层只有面罩、推进器这些地方不透明，颜色是白的，渲染时按状态着色（巡逻黄、敌对红）。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")
ITEM = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "item")
N = 16


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, f):
    return tuple(clamp(c * f) for c in color[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


def plasma_bolt():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c, y - c) / c
            if d >= 1:
                continue
            alpha = min(1.0, (1 - d) ** 1.6 * 1.25)
            v = int(255 * min(1.0, 0.55 + 0.45 * (1 - d)))
            img.putpixel((x, y), (v, v, v, int(255 * alpha)))
    save(img, ENTITY, "plasma_bolt")


# ---- 守卫机

def faces(u, v, w, h, d):
    """模型方块的贴图展开：每个面在贴图上的矩形 (x, y, 宽, 高)。"""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


class Skin:
    def __init__(self, width, height, seed):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def put(self, x, y, color):
        self.img.putpixel((x, y), tuple(color[:3]) + (255,))

    def get(self, x, y):
        return self.img.getpixel((x, y))[:3]

    def light(self, x, y, strength=1.0):
        a = clamp(255 * strength)
        self.glow.putpixel((x, y), (255, 255, 255, a))

    def panel(self, rect, base, noise=0.05, bevel=True):
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                f = 1 + self.rng.uniform(-noise, noise)
                if bevel and w > 2 and h > 2:
                    if x == x0 or y == y0:
                        f *= 1.12
                    elif x == x0 + w - 1 or y == y0 + h - 1:
                        f *= 0.78
                self.put(x, y, shade(base, f))

    def box(self, u, v, w, h, d, base, noise=0.05):
        for rect in faces(u, v, w, h, d).values():
            self.panel(rect, base, noise)
        return faces(u, v, w, h, d)

    def hline(self, x0, x1, y, color):
        for x in range(x0, x1):
            self.put(x, y, color)

    def vline(self, x, y0, y1, color):
        for y in range(y0, y1):
            self.put(x, y, color)


def sentinel_body(s, f, base, accent):
    """球形机身：面板接缝、正面围着面罩的深色圈、背面散热格、顶上的舱盖、底部的推进格栅。"""
    dark = shade(base, 0.55)
    # 每个侧面中间一道横接缝
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = f[key]
        mid = y0 + h // 2
        s.hline(x0, x0 + w, mid, shade(base, 0.72))
        s.hline(x0, x0 + w, mid + 1, shade(base, 1.1))
    # 侧面的标识条
    for key in ("left", "right"):
        x0, y0, w, h = f[key]
        for y in range(y0 + 1, y0 + h // 2 - 1):
            s.put(x0 + w // 2, y, accent)
    # 正面：面罩周围的深色框
    x0, y0, w, h = f["front"]
    cx0, cx1 = x0 + w // 2 - 3, x0 + w // 2 + 3
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(cx0, cx1):
            if cx0 <= x < cx1:
                s.put(x, y, shade(dark, 0.8 if (x in (cx0, cx1 - 1) or y in (y0 + 1, y0 + h - 2)) else 0.6))
    # 背面：散热格
    x0, y0, w, h = f["back"]
    for y in range(y0 + 1, y0 + h - 1, 2):
        s.hline(x0 + 2, x0 + w - 2, y, dark)
    # 顶面：圆形舱盖
    x0, y0, w, h = f["top"]
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - cx, y - cy)
            if abs(d - min(w, h) * 0.32) < 0.6:
                s.put(x, y, dark)
    # 底面：推进格栅，发光层点亮中间
    x0, y0, w, h = f["bottom"]
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y, (40, 42, 48) if (x + y) % 2 == 0 else (24, 26, 30))
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - cx, y - cy)
            if d < min(w, h) * 0.3:
                s.light(x, y, 0.75 * (1 - d / (min(w, h) * 0.3)) + 0.25)


def visor(s, f):
    """面罩：正面是深色玻璃，发光层是一道横着的眼缝，中间最亮。"""
    for key in ("top", "bottom", "left", "right", "back"):
        s.panel(f[key], (52, 54, 60), 0.04, bevel=False)
    x0, y0, w, h = f["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (14, 14, 18))
    cy = y0 + (h - 1) / 2
    cx = x0 + (w - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            dy = abs(y - cy) / (h / 2)
            dx = abs(x - cx) / (w / 2)
            if dy < 0.6:
                s.light(x, y, max(0.35, 1 - dx * 0.65))
            else:
                s.light(x, y, 0.18)


def pod(s, f, base):
    dark = shade(base, 0.6)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = f[key]
        for y in range(y0 + 1, y0 + h - 1, 2):
            s.hline(x0 + 1, x0 + w - 1, y, dark)
    x0, y0, w, h = f["bottom"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            s.put(x, y, (60, 62, 68) if edge else (20, 20, 24))
            if not edge:
                s.light(x, y, 0.9)


def drone():
    s = Skin(64, 32, 31)
    white = (206, 210, 216)
    accent = (232, 176, 58)
    body = s.box(0, 0, 8, 7, 8, white)
    sentinel_body(s, body, white, accent)
    visor(s, faces(32, 0, 5, 4, 1))
    s.box(44, 0, 1, 3, 5, shade(white, 0.85))
    rear = s.box(20, 16, 6, 5, 1, shade(white, 0.9))
    x0, y0, w, h = rear["back"]
    for x in range(x0 + 1, x0 + w - 1, 2):
        s.vline(x, y0 + 1, y0 + h - 1, shade(white, 0.55))
    pod(s, s.box(0, 16, 3, 4, 5, (92, 96, 106)), (92, 96, 106))
    antenna = s.box(16, 16, 1, 3, 1, (70, 72, 80))
    x0, y0, w, h = antenna["bottom"]
    s.light(x0, y0, 1.0)
    save(s.img, ENTITY, "sentinel")
    save(s.glow, ENTITY, "sentinel_glow")


def heavy():
    s = Skin(64, 64, 37)
    metal = (128, 132, 142)
    accent = (210, 60, 46)
    body = s.box(0, 0, 12, 9, 12, metal)
    sentinel_body(s, body, metal, accent)
    # 正面多两排铆钉
    x0, y0, w, h = body["front"]
    for x in (x0 + 1, x0 + w - 2):
        for y in range(y0 + 1, y0 + h - 1, 2):
            s.put(x, y, shade(metal, 1.35))
    visor(s, faces(0, 22, 8, 5, 1))
    cannon = s.box(36, 22, 3, 2, 7, (64, 66, 74))
    x0, y0, w, h = cannon["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (18, 18, 22))
            s.light(x, y, 0.8 if x == x0 + w // 2 else 0.4)
    crest = s.box(0, 32, 2, 3, 8, shade(metal, 0.8))
    x0, y0, w, h = crest["right"]
    for x in range(x0, x0 + w, 2):
        s.vline(x, y0, y0 + h, accent)
    plates = s.box(20, 32, 1, 6, 8, shade(metal, 0.9))
    # 装甲板上黄黑相间的警示条
    for key in ("right", "left"):
        x0, y0, w, h = plates[key]
        for y in range(y0, y0 + 2):
            for x in range(x0, x0 + w):
                s.put(x, y, (226, 180, 40) if (x + y) % 4 < 2 else (30, 30, 34))
    pod(s, s.box(20, 22, 4, 5, 4, (78, 82, 92)), (78, 82, 92))
    save(s.img, ENTITY, "heavy_sentinel")
    save(s.glow, ENTITY, "heavy_sentinel_glow")


# ---- 物品

def outline(mask):
    edge = set()
    for (x, y) in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask:
                edge.add((x, y))
                break
    return edge


def egg(base, spot, seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = set()
    for y in range(1, N):
        for x in range(N):
            py = (y + 0.5 - 9.0) / 6.6
            rx = 5.2 * (1 - 0.18 * max(0, -py))
            if py * py + ((x + 0.5 - 8) / rx) ** 2 <= 1:
                mask.add((x, y))
    rnd = random.Random(seed)
    spots = [(rnd.uniform(4, 12), rnd.uniform(4, 14), rnd.uniform(0.6, 1.5)) for _ in range(6)]
    edge = outline(mask)
    dark = shade(base, 0.45)
    for (x, y) in mask:
        c = base
        if any((x - sx) ** 2 + (y - sy) ** 2 < r * r for sx, sy, r in spots):
            c = spot
        c = shade(c, 1.15 - (x * 0.5 + y) * 0.018)
        if (x, y) in edge:
            c = dark
        img.putpixel((x, y), tuple(c) + (255,))
    for (x, y) in ((5, 5), (5, 6), (6, 4)):
        if (x, y) in mask and (x, y) not in edge:
            img.putpixel((x, y), tuple(mix(img.getpixel((x, y)), (255, 255, 255), 0.55)) + (255,))
    return img


def scrap():
    """守卫机残骸：两片扭曲的白色外壳碎片，断口露出电线和一颗还亮着的黄色元件。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    rng = random.Random(53)
    shell = (204, 208, 214)

    def plate(points, base):
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        mask = set()
        for y in range(min(ys), max(ys) + 1):
            for x in range(min(xs), max(xs) + 1):
                inside = False
                j = len(points) - 1
                for i in range(len(points)):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                        inside = not inside
                    j = i
                if inside:
                    mask.add((x, y))
        edge = outline(mask)
        for (x, y) in mask:
            f = 1.15 - (x + y) * 0.02 + rng.uniform(-0.04, 0.04)
            c = shade(base, f * (0.62 if (x, y) in edge and (x + y) > 14 else 1))
            img.putpixel((x, y), tuple(c) + (255,))
        return mask

    a = plate([(2, 3), (9, 2), (11, 6), (7, 10), (3, 9)], shell)
    b = plate([(7, 9), (13, 7), (14, 12), (9, 14), (6, 13)], shade(shell, 0.85))
    # 接缝和焦痕
    for x in range(3, 9):
        if (x, 6) in a:
            img.putpixel((x, 6), tuple(shade(shell, 0.6)) + (255,))
    for (x, y) in ((10, 10), (11, 10), (12, 11)):
        if (x, y) in b:
            img.putpixel((x, y), (60, 58, 56, 255))
    # 电线
    for (x, y, c) in ((8, 8, (200, 60, 50)), (9, 8, (200, 60, 50)), (10, 7, (200, 60, 50)), (6, 9, (60, 110, 200)), (5, 10, (60, 110, 200))):
        img.putpixel((x, y), c + (255,))
    # 还亮着的元件
    img.putpixel((4, 5), (255, 214, 80, 255))
    img.putpixel((5, 5), (255, 240, 170, 255))
    save(img, ITEM, "sentinel_scrap")


if __name__ == "__main__":
    plasma_bolt()
    drone()
    heavy()
    scrap()
    save(egg((206, 210, 216), (232, 176, 58), 61), ITEM, "sentinel_spawn_egg")
    save(egg((96, 100, 110), (210, 60, 46), 67), ITEM, "heavy_sentinel_spawn_egg")
    print("combat textures written")
