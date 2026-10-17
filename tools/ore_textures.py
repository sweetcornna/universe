"""生成星球矿石和资源物品的贴图（16×16 像素画）。

用法：python tools/ore_textures.py [方块贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/block 和 textures/item。

矿石只画矿点那一层（`<矿石>_overlay.png`），底下的岩石用 planet_rock 按星球染色。矿点层只有全透明和
不透明两种像素，这样方块走镂空渲染层（半透明像素会让整个方块进半透明层）。
物品按形状分几类：粗矿是一团石头，锭是斜放的金属块，晶体是几根棱柱，还有粉堆、球、马蹄形磁铁。
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


def rgb(c):
    return (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF


def ramp(*colors):
    return [rgb(c) for c in colors]


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), tuple(color) + (255,))


def filled(img, x, y):
    return 0 <= x < N and 0 <= y < N and img.getpixel((x, y))[3] > 0


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + ".png"))


# ---------------------------------------------------------------- 矿点

def grow(rng, cx, cy, size, taken):
    """从 (cx, cy) 长出 size 个像素的一团：挨着已有像素多的格子更容易长，团得紧凑。"""
    cells = [(cx, cy)]
    while len(cells) < size:
        options = []
        for x, y in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y + dy)
                if 0 <= p[0] <= N - 1 and 0 <= p[1] <= N - 1 and p not in cells and p not in taken:
                    around = sum((p[0] + ax, p[1] + ay) in cells for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                    options.append((p, around * around))
        if not options:
            break
        total = sum(w for _, w in options)
        pick = rng.uniform(0, total)
        for p, w in options:
            pick -= w
            if pick <= 0:
                cells.append(p)
                break
    return cells


def centers(rng, count, spacing):
    found = []
    for _ in range(400):
        if len(found) == count:
            break
        p = (rng.randint(2, N - 3), rng.randint(2, N - 3))
        if all((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 >= spacing ** 2 for q in found):
            found.append(p)
    return found


def spots(name, colors, seed, count=5, sizes=(6, 10), fleck=None):
    """金属、矿物的矿点：几团不规则的色块，左上亮、右下暗。"""
    rng = random.Random(seed)
    img = new()
    taken = set()
    for cx, cy in centers(rng, count, 5.2):
        cells = grow(rng, cx, cy, rng.randint(*sizes), taken)
        taken.update(cells)
        for x, y in cells:
            edge = (x + 1, y) not in cells and (x, y + 1) not in cells
            top = (x - 1, y) not in cells and (x, y - 1) not in cells
            if edge and not top:
                c = colors[0]
            elif top and not edge:
                c = colors[2]
            else:
                c = colors[1]
            put(img, x, y, c)
        # 每团一个高光点
        hx, hy = min(cells, key=lambda p: p[0] + p[1])
        put(img, hx, hy, colors[3])
        if fleck is not None and len(cells) > 3:
            fx, fy = rng.choice(cells[1:])
            put(img, fx, fy, fleck)
    save(img, BLOCK, name + "_overlay")


def shards(name, colors, seed, count=4):
    """晶体矿：几簇斜着露出来的棱柱，左边一列亮、右边一列暗，尖上一点白，根部一圈暗色。"""
    rng = random.Random(seed)
    img = new()
    for cx, cy in centers(rng, count, 5.5):
        cy = min(N - 2, cy + 2)
        for k in range(rng.choice([1, 2, 2])):
            length = rng.randint(4, 6) - k
            lean = rng.choice([-1, 1]) if k else rng.choice([-1, 0, 1])
            bx = cx + (2 * k * lean if k else 0)
            pts = [(bx + (lean * i) // 3, cy - i) for i in range(length)]
            for i, (x, y) in enumerate(pts):
                tip = i == length - 1
                put(img, x, y, colors[3] if tip else colors[2])
                if not tip:
                    put(img, x + 1, y, colors[1])
            x, y = pts[-1]
            put(img, x, y - 1, colors[3])
            put(img, bx - 1, cy + 1, colors[0])
            put(img, bx, cy + 1, colors[0])
            put(img, bx + 1, cy + 1, colors[0])
            put(img, bx + 2, cy, colors[0])
    save(img, BLOCK, name + "_overlay")


def cubes(name, colors, seed, count=5):
    """岩盐：几粒小方块。"""
    rng = random.Random(seed)
    img = new()
    for cx, cy in centers(rng, count, 4.6):
        s = rng.choice([3, 3, 4])
        for y in range(s):
            for x in range(s):
                c = colors[1]
                if x == s - 1 or y == s - 1:
                    c = colors[0]
                if x == 0 and y == 0:
                    c = colors[3]
                put(img, cx + x, cy + y, c)
    save(img, BLOCK, name + "_overlay")


# ---------------------------------------------------------------- 物品形状

LIGHT = (-0.55, -0.7, 0.45)


def light(nx, ny, nz):
    length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
    return (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / length


def shade_index(v, levels=5):
    # v 在 -1..1：映射到 1..4（0 留给轮廓）
    t = (v + 0.35) / 1.15
    return max(1, min(levels - 1, int(t * (levels - 1)) + 1))


def outline(img, color):
    """不透明区域外沿描一圈（只描右边和下边，像原版物品那样左上亮）。"""
    edge = []
    for y in range(N):
        for x in range(N):
            if filled(img, x, y) and (not filled(img, x + 1, y) or not filled(img, x, y + 1)
                                      or not filled(img, x - 1, y) or not filled(img, x, y - 1)):
                edge.append((x, y))
    for x, y in edge:
        put(img, x, y, color)


def lump(colors, seed, flecks=None, fleck_count=4):
    """粗矿：几个圆叠成的一团石头，按球面打光。"""
    rng = random.Random(seed)
    blobs = [(8 + rng.uniform(-0.5, 0.5), 8.5 + rng.uniform(-0.5, 0.5), rng.uniform(5.2, 5.8))]
    for i in range(4):
        a = i * math.tau / 4 + rng.uniform(-0.6, 0.6)
        blobs.append((8 + math.cos(a) * 3.0, 8.5 + math.sin(a) * 2.6, rng.uniform(2.8, 3.6)))
    img = new()
    for y in range(N):
        for x in range(N):
            best = None
            for bx, by, r in blobs:
                d = math.hypot(x + 0.5 - bx, y + 0.5 - by)
                if d < r:
                    h = math.sqrt(max(0.0, r * r - d * d))
                    if best is None or h > best[0]:
                        best = (h, (x + 0.5 - bx) / r, (y + 0.5 - by) / r, h / r)
            if best is not None:
                v = light(best[1], best[2], best[3]) + rng.uniform(-0.12, 0.12)
                put(img, x, y, colors[shade_index(v)])
    outline(img, colors[0])
    if flecks:
        inner = [(x, y) for y in range(N) for x in range(N)
                 if filled(img, x, y) and img.getpixel((x, y))[:3] != colors[0]]
        for x, y in rng.sample(inner, min(fleck_count, len(inner))):
            put(img, x, y, flecks[0])
            if filled(img, x - 1, y - 1):
                put(img, x - 1, y - 1, flecks[1])
    return img


def polygon(img, points, color):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        for x in range(int(min(xs)), int(max(xs)) + 1):
            if inside(x + 0.5, y + 0.5, points):
                put(img, x, y, color)


def inside(px, py, points):
    result = False
    j = len(points) - 1
    for i in range(len(points)):
        xi, yi = points[i]
        xj, yj = points[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
            result = not result
        j = i
    return result


def ingot(colors):
    """锭：斜放的金属块，顶面最亮、正面中间、侧面暗，顶边一道高光。"""
    img = new()
    polygon(img, [(1, 9), (6, 5), (15, 5), (10, 9)], colors[3])
    polygon(img, [(1, 9), (10, 9), (10, 13), (1, 13)], colors[2])
    polygon(img, [(10, 9), (15, 5), (15, 9), (10, 13)], colors[1])
    outline(img, colors[0])
    for x in range(6, 14):
        put(img, x, 6, colors[4])
    put(img, 3, 10, colors[3])
    put(img, 4, 10, colors[3])
    return img


def crystals(colors, seed, count=3, tint=None):
    """晶体：几根竖着的六棱柱，左面亮、右面暗，尖顶。tint 给每根柱子换一套颜色（光学晶体的虹彩）。"""
    rng = random.Random(seed)
    img = new()
    columns = [(8, 3, 12, 3), (4, 6, 13, 2), (12, 7, 13, 2)][:count]
    for i, (cx, top, bottom, half) in enumerate(columns):
        c = tint[i % len(tint)] if tint else colors
        for y in range(top, bottom + 1):
            w = half if y > top + half else max(0, y - top)
            for x in range(cx - w, cx + w + 1):
                if x < cx:
                    put(img, x, y, c[3])
                elif x == cx:
                    put(img, x, y, c[2])
                else:
                    put(img, x, y, c[1])
        put(img, cx, top, c[4])
        put(img, cx - 1, top + 2, c[4])
    outline(img, colors[0])
    # 柱尖的高光不要被轮廓盖掉
    for i, (cx, top, bottom, half) in enumerate(columns):
        c = tint[i % len(tint)] if tint else colors
        put(img, cx, top, c[4])
    return img


def pile(colors, seed):
    """粉末堆：半圆的一堆，表面撒些亮暗的颗粒。"""
    rng = random.Random(seed)
    img = new()
    for y in range(N):
        for x in range(N):
            dx = (x + 0.5 - 8) / 6.5
            dy = (y + 0.5 - 13) / 7.5
            if dy <= 0.12 and dx * dx + dy * dy < 1:
                nz = math.sqrt(max(0.0, 1 - dx * dx - dy * dy))
                v = light(dx, dy, nz) + rng.uniform(-0.25, 0.25)
                put(img, x, y, colors[shade_index(v)])
    outline(img, colors[0])
    return img


def salt_cubes(colors):
    """盐：三颗叠着的立方晶体，各自描边，后面的先画。"""
    img = new()
    d = 2
    for ox, oy, s in [(5, 1, 5), (1, 7, 5), (8, 7, 5)]:
        cube = new()
        polygon(cube, [(ox, oy + d), (ox + d, oy), (ox + s + d, oy), (ox + s, oy + d)], colors[4])
        polygon(cube, [(ox, oy + d), (ox + s, oy + d), (ox + s, oy + s + d), (ox, oy + s + d)], colors[3])
        polygon(cube, [(ox + s, oy + d), (ox + s + d, oy), (ox + s + d, oy + s), (ox + s, oy + s + d)], colors[2])
        outline(cube, colors[0])
        img.alpha_composite(cube)
    return img


def orb(colors, seed):
    """奇异物质：发光的球，中间一个亮核，外面一圈旋涡。"""
    rng = random.Random(seed)
    img = new()
    for y in range(N):
        for x in range(N):
            dx = x + 0.5 - 8
            dy = y + 0.5 - 8
            d = math.hypot(dx, dy)
            if d < 6.4:
                a = math.atan2(dy, dx)
                swirl = math.sin(a * 2 + d * 1.1)
                v = 1.0 - d / 6.4 + swirl * 0.25 + rng.uniform(-0.08, 0.08)
                i = max(1, min(4, int(v * 4.2) + 1))
                put(img, x, y, colors[i])
    outline(img, colors[0])
    put(img, 7, 7, (255, 255, 255))
    put(img, 8, 7, colors[4])
    put(img, 7, 8, colors[4])
    return img


def magnet(red, steel):
    """马蹄形磁铁：红色的弯，两头是钢色的极。"""
    img = new()
    for y in range(N):
        for x in range(N):
            dx = x + 0.5 - 8
            dy = y + 0.5 - 8
            d = math.hypot(dx, dy)
            arc = 2.6 <= d <= 6.2 and dy <= 0.5
            leg = (1.8 <= abs(dx) <= 6.2 and abs(abs(dx) - 4.4) <= 1.8) and 0.5 < dy <= 6.5
            if arc or leg:
                tip = dy > 3.5
                ramp_ = steel if tip else red
                if arc:
                    v = light(dx / d, dy / d, 0.4) if d else 0
                else:
                    v = -0.2 if abs(dx) > 4.4 else 0.6
                    v = v if dx < 0 else v - 0.4
                put(img, x, y, ramp_[shade_index(v)])
    outline(img, red[0])
    for x, y in [(3, 14), (4, 14), (5, 14), (11, 14), (12, 14), (13, 14)]:
        if filled(img, x, y):
            put(img, x, y, steel[0])
    return img


def wafer(colors):
    """高纯硅：一块斜放的晶圆，表面有网格的反光。"""
    img = new()
    polygon(img, [(1, 9), (7, 4), (15, 6), (9, 12)], colors[2])
    polygon(img, [(1, 9), (9, 12), (9, 14), (1, 11)], colors[1])
    polygon(img, [(9, 12), (15, 6), (15, 8), (9, 14)], colors[0])
    for t in range(1, 4):
        a = (1 + (7 - 1) * t / 4, 9 + (4 - 9) * t / 4)
        b = (9 + (15 - 9) * t / 4, 12 + (6 - 12) * t / 4)
        for s in range(9):
            x = a[0] + (b[0] - a[0]) * s / 8
            y = a[1] + (b[1] - a[1]) * s / 8
            if filled(img, int(x), int(y)):
                put(img, int(x), int(y), colors[3])
    outline(img, colors[0])
    put(img, 7, 5, colors[4])
    put(img, 8, 5, colors[4])
    put(img, 6, 6, colors[4])
    return img


# ---------------------------------------------------------------- 颜色

def main():
    spots("planet_iron_ore", ramp(0x8F6A52, 0xC49A7C, 0xE8C7A8, 0xF8E4D0), 11)
    spots("planet_copper_ore", ramp(0x8A3E1E, 0xD06A3A, 0xEE9A5E, 0xF8C08A), 12, fleck=rgb(0x5FC9A2))
    spots("planet_coal_ore", ramp(0x101010, 0x222222, 0x363636, 0x4E4E4E), 13, count=6)
    spots("titanium_ore", ramp(0x46546A, 0x8C9DB4, 0xD0DCEC, 0xFFFFFF), 14, sizes=(7, 11))
    shards("silicon_ore", ramp(0x1C2333, 0x3C4A63, 0x6A80A6, 0xB8CAE8), 15)
    spots("sulfur_ore", ramp(0x9C8410, 0xD8C024, 0xF5E45A, 0xFFF8C0), 16, count=6, sizes=(4, 7))
    shards("fire_ice_ore", ramp(0x2A84A8, 0x68C8EA, 0xB0ECFF, 0xFFFFFF), 17)
    shards("optical_crystal_ore", ramp(0x5A28B0, 0xA868FF, 0x6EE6FF, 0xFFFFFF), 18, count=3)
    shards("organic_crystal_ore", ramp(0x26703A, 0x58C068, 0xA2EE88, 0xF0FFE0), 19)
    spots("uranium_ore", ramp(0x2C861A, 0x5CD832, 0xA6FF6E, 0xEEFFD8), 20, count=4, sizes=(4, 7))
    cubes("salt_ore", ramp(0x9C8088, 0xE2CCD2, 0xF6EAEE, 0xFFFFFF), 21)
    spots("magnetic_ore", ramp(0x141418, 0x2C2C36, 0x50506A, 0x9C9CB8), 22)
    shards("strange_matter_ore", ramp(0x45107F, 0x9638FF, 0xDE9CFF, 0xFFFFFF), 23, count=3)

    rock = ramp(0x2E2C30, 0x55535A, 0x7A7880, 0x9E9CA6, 0xC4C2CC)
    save(lump(rock, 31, flecks=ramp(0xD4DEEA, 0x8E9DB2), fleck_count=5), ITEM, "raw_titanium")
    save(ingot(ramp(0x3A4352, 0x6E7D93, 0x9DADC2, 0xC9D5E3, 0xF2F7FC)), ITEM, "titanium_ingot")
    save(lump(ramp(0x1C1D22, 0x393B44, 0x5A5E6C, 0x7D8496, 0xA8B2C8), 32,
              flecks=ramp(0xC8D6F0, 0x6A80A6), fleck_count=3), ITEM, "raw_silicon")
    save(wafer(ramp(0x1A2030, 0x34405A, 0x56698C, 0x8EA6CC, 0xDCE8FA)), ITEM, "silicon")
    save(pile(ramp(0x6B5A0A, 0xB09A16, 0xDCC428, 0xF3E260, 0xFFF7B0), 33), ITEM, "sulfur")
    save(crystals(ramp(0x1B5670, 0x3C9CBC, 0x7CD6EE, 0xBEEEFF, 0xFFFFFF), 34), ITEM, "fire_ice")
    save(crystals(ramp(0x2A1660, 0x7A3FD0, 0xB070FF, 0xD8B0FF, 0xFFFFFF), 35, tint=[
        ramp(0x2A1660, 0x7A3FD0, 0xB070FF, 0xD8B0FF, 0xFFFFFF),
        ramp(0x103A5A, 0x2C8CC0, 0x6EE6FF, 0xC0F6FF, 0xFFFFFF),
        ramp(0x5A1050, 0xC03A9C, 0xFF7AD0, 0xFFC0EA, 0xFFFFFF)]), ITEM, "optical_crystal")
    save(crystals(ramp(0x1C4626, 0x3A9C52, 0x74DA78, 0xBDF59C, 0xF4FFE8), 36), ITEM, "organic_crystal")
    save(lump(ramp(0x22281C, 0x414A35, 0x606C4E, 0x7E8A68, 0x9EAA86), 37,
              flecks=ramp(0x9CFF5A, 0x4CC22E), fleck_count=5), ITEM, "raw_uranium")
    save(ingot(ramp(0x1A3612, 0x357022, 0x58AE36, 0x88E85A, 0xD0FFB8)), ITEM, "uranium_ingot")
    save(salt_cubes(ramp(0x7A666C, 0xB49CA4, 0xD8C6CC, 0xF0E6EA, 0xFFFFFF)), ITEM, "salt")
    save(lump(ramp(0x0E0E12, 0x22222A, 0x383844, 0x50505E, 0x6E6E80), 38,
              flecks=ramp(0xB4B4CC, 0x6C6C84), fleck_count=4), ITEM, "raw_magnetite")
    save(magnet(ramp(0x4A0A0A, 0x8E1A1A, 0xC82C2C, 0xEC5C5C, 0xFFA8A8),
                ramp(0x3A3E46, 0x6E747E, 0xA2A8B2, 0xCDD2DA, 0xF2F4F8)), ITEM, "magnet")
    save(orb(ramp(0x1C0634, 0x50149A, 0x9238FF, 0xCC88FF, 0xFFE2FF), 39), ITEM, "strange_matter")


if __name__ == "__main__":
    main()
