"""星球上的方块和东西（ADR-0031）：地形、野外的地标、兴趣点、前哨站、知识石、先驱者星门，几样拾到的物品。

地形（planet_*）、生物卵、酸液在游戏里按星球配色逐通道相乘，只画灰度（tinted），平均亮度 0.8 左右；
明暗用 planet_gray，比通用的 gray 多三级，细的纹理（沙、雪）不至于一级一跳。地形都要无缝平铺：
噪声按 16 格的周期取，描边、清理零碎像素时也按环绕算。

岩石照原版石头的画法：成团的块，每块左上一圈亮边、右下一道暗影，再加几道细裂纹；圆石是一块块凸起的石头，
石缝深；泥土、风化层是细碎的颗粒和小石子；沙子是一道道风纹；雪是很淡的起伏和几点闪光。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import (AMBER, AMBER_GLOW, BASALT, BLUE_GLOW, BONE, BRASS, COPPER, CYAN_GLOW, GLASS, GRAPHITE, GREEN,
                     GREEN_GLOW, HAZARD, ICE, IRON, MAGENTA_GLOW, MOSS, ORANGE, ORANGE_GLOW, PINK, RED, RED_GLOW, RUST,
                     SAND, SCREEN, SOIL, STEEL, VIOLET_GLOW, WHITE, WHITE_GLOW, register)
from recipe import texture

# 染色用的灰度：比 palette.GRAY 细，0.52–0.98 十级
TINT = register(ramp("planet_gray", 0, 0.0, n=10, lo=0.52, hi=0.98, shift=0, fade=0))
# 先驱者的石头：发紫的黑（石碑、星门）；知识石是偏蓝的板岩
ANCIENT = register(ramp("planet_ancient", 292, 0.030, n=7, lo=0.19, hi=0.62, shift=10, peak=0.5))
SLATE = register(ramp("planet_slate", 258, 0.034, n=7, lo=0.22, hi=0.66, shift=10))
# 雷击石的玻璃质：灰绿
FULGURITE = register(ramp("planet_fulgurite", 135, 0.040, n=6, lo=0.30, hi=0.86, shift=14))
# 砗磲的壳：粉褐；珍珠层
CLAM = register(ramp("planet_clam", 32, 0.060, n=7, lo=0.30, hi=0.88, shift=14))
NACRE = register(ramp("planet_nacre", 320, 0.025, n=6, lo=0.62, hi=0.97, shift=40))


# ---- 平铺用的噪声和清理

def _noise(rng: random.Random, period, w: int = 16, h: int = 16):
    """值噪声，横竖各 period 格一个周期（period 可以是 (横, 竖)），在 w×h 上无缝；返回 f(x, y)。"""
    px, py = period if isinstance(period, tuple) else (period, period)
    grid = [[rng.random() for _ in range(px)] for _ in range(py)]

    def f(x: float, y: float) -> float:
        gx, gy = x * px / w, y * py / h
        i0, j0 = math.floor(gx), math.floor(gy)
        tx, ty = gx - i0, gy - j0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[j0 % py][i0 % px]
        b = grid[j0 % py][(i0 + 1) % px]
        c = grid[(j0 + 1) % py][i0 % px]
        d = grid[(j0 + 1) % py][(i0 + 1) % px]
        top = a + (b - a) * tx
        bot = c + (d - c) * tx
        return top + (bot - top) * ty
    return f


def field(rng: random.Random, periods, weights, w: int = 16, h: int = 16) -> list[list[float]]:
    """几层噪声叠起来，归一到 0–1。"""
    fs = [_noise(rng, p, w, h) for p in periods]
    out = [[sum(wt * f(x, y) for f, wt in zip(fs, weights)) for x in range(w)] for y in range(h)]
    lo = min(min(r) for r in out)
    hi = max(max(r) for r in out)
    return [[(v - lo) / max(1e-9, hi - lo) for v in r] for r in out]


def wrap(c: Canvas, x: int, y: int):
    return c.p[y % c.h][x % c.w]


def despeckle(c: Canvas, passes: int = 2, tile: bool = True, keep=lambda x, y: False):
    """零碎的单个像素（四邻都和它不同级）改成邻居里最多的那级；平铺的图按环绕算。"""
    for _ in range(passes):
        src = [row[:] for row in c.p]
        for y in range(c.h):
            for x in range(c.w):
                px = src[y][x]
                if not px or keep(x, y) or (x, y) in c.glints:
                    continue
                nb = []
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if tile:
                        nx, ny = nx % c.w, ny % c.h
                    elif not c.inside(nx, ny):
                        continue
                    q = src[ny][nx]
                    if q:
                        nb.append(q)
                if nb and all(q != px for q in nb):
                    best = max(set(nb), key=nb.count)
                    if nb.count(best) >= 2:
                        c.p[y][x] = best


def emboss(c: Canvas, levels: list[list[int]], r, base: int, step: float = 1.0, rim: int = 1, light=(-1, -1)):
    """梯田一样的高度（整数级）打光：每级高地朝光的一圈亮、背光外面一道暗影，和原版石头一样。
    light 是光从哪个相邻格来，(-1, -1) 是左上，(0, -1) 是正上（横纹的岩石）。"""
    h, w = len(levels), len(levels[0])
    mean = sum(map(sum, levels)) / (w * h)
    lx, ly = light
    for y in range(h):
        for x in range(w):
            q = levels[y][x]
            d = q - levels[(y + ly) % h][(x + lx) % w]
            lv = base + round((q - mean) * step) + (rim if d > 0 else (-rim if d < 0 else 0))
            c.set(x, y, r, lv)


def terrace(f: list[list[float]], cuts) -> list[list[int]]:
    return [[sum(1 for t in cuts if v > t) for v in row] for row in f]


def mean_level(c: Canvas) -> float:
    vals = [px[1] for row in c.p for px in row if px]
    return sum(vals) / max(1, len(vals))


def walk(rng: random.Random, x: int, y: int, n: int, dirs=((1, 0), (1, 1), (0, 1), (1, -1))):
    """随机游走的一串格子（裂纹、根须）。"""
    pts = []
    dx, dy = rng.choice(dirs)
    for _ in range(n):
        pts.append((x, y))
        if rng.random() < 0.45:
            dx, dy = rng.choice(dirs)
        x += dx
        y += dy
    return pts


def crack(c: Canvas, pts, r, dark: int, lip: int = 1, tile: bool = True):
    """细裂纹：缝是暗的，缝的右下沿受光（凹槽）。"""
    cells = {((x % c.w), (y % c.h)) if tile else (x, y) for x, y in pts}
    for x, y in cells:
        c.set(x, y, r, dark)
    for x, y in cells:
        for dx, dy in ((1, 0), (0, 1)):
            q = ((x + dx) % c.w, (y + dy) % c.h) if tile else (x + dx, y + dy)
            if q not in cells and c.get(*q):
                c.shift(q[0], q[1], lip)


def pebble(c: Canvas, x: int, y: int, r, lv: int, big: bool = False, tile: bool = True):
    """小石子：亮的一两个像素，右下一道影子。"""
    pts = [(x, y), (x + 1, y)] if big else [(x, y)]
    for px, py in pts:
        c.set(px % c.w if tile else px, py % c.h if tile else py, r, lv, glint=True)
    for px, py in pts:
        sx, sy = px + 1, py + 1
        if tile:
            sx, sy = sx % c.w, sy % c.h
        if (sx, sy) not in [(p % c.w, q % c.h) for p, q in pts]:
            c.set(sx, sy, r, max(0, lv - 3), glint=True)


# ---- 地形（染色）

@texture("block/planet_rock", tinted=True, rough=True, refs=("block/stone", "block/andesite", "block/tuff"))
def planet_rock(rng):
    c = Canvas()
    # 横着压扁的块：横向周期短一半，像一层层压实的岩石；光从上面来，每块上沿亮、下面一道暗影
    f = field(rng, ((4, 8), (8, 16), (16, 16)), (0.55, 0.3, 0.15))
    emboss(c, terrace(f, (0.28, 0.5, 0.7)), TINT, 5, step=0.6, light=(0, -1))
    # 几道横着的暗缝，两三格长
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randint(2, 3)):
            c.set((x + i) % 16, y, TINT, 3)
        c.shift(x % 16, (y + 1) % 16, 1)
    despeckle(c)
    return c


def _wd(x0, y0, x1, y1, w=16, h=16):
    dx = abs(x0 - x1)
    dy = abs(y0 - y1)
    return math.hypot(min(dx, w - dx), min(dy, h - dy))


def _wdelta(x0, y0, x1, y1, w=16, h=16):
    """从点 1 到点 0 的环绕位移。"""
    dx = (x0 - x1 + w / 2) % w - w / 2
    dy = (y0 - y1 + h / 2) % h - h / 2
    return dx, dy


def _points(rng: random.Random, n: int, dmin: float, w: int = 16, h: int = 16):
    """在环绕的平面上撒 n 个点，彼此至少隔 dmin（撒不下就少几个）。"""
    pts = []
    for _ in range(n * 60):
        if len(pts) >= n:
            break
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        if all(_wd(x, y, px, py, w, h) >= dmin for px, py in pts):
            pts.append((x, y))
    return pts


def voronoi(pts, w: int = 16, h: int = 16, jitter=None):
    """每格到最近两个点的距离和最近点的序号。jitter(x, y) 让单双号的点此消彼长，边界弯一点。"""
    out = []
    for y in range(h):
        row = []
        for x in range(w):
            j = jitter(x, y) if jitter else 0.0
            ds = sorted((_wd(x + 0.5, y + 0.5, px, py, w, h) + (j if i % 2 else -j), i)
                        for i, (px, py) in enumerate(pts))
            row.append((ds[0][0], ds[1][0], ds[0][1]))
        out.append(row)
    return out


@texture("block/planet_cobblestone", tinted=True, rough=True, refs=("block/cobblestone", "block/mossy_cobblestone"))
def planet_cobblestone(rng):
    """圆石：一块块凸起的圆石头，大小不一挤在一起；每块左上亮、右下暗，最亮处一点高光；石缝深。"""
    c = Canvas()
    pts = _points(rng, 11, 4.0)
    rad = [rng.uniform(2.6, 3.6) for _ in pts]
    tone = [rng.choice((5, 5, 6, 6, 6, 7)) for _ in pts]
    jn = _noise(rng, 8)
    owner = [[-1] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            ds = sorted((_wd(x + 0.5, y + 0.5, px, py) / rad[i] + (jn(x, y) - 0.5) * 0.25, i)
                        for i, (px, py) in enumerate(pts))
            # 离两块石头差不多远的地方是石缝，离哪块都远的也是
            gap = ds[1][0] - ds[0][0] < 0.16 or ds[0][0] >= 1.0
            owner[y][x] = -1 if gap else ds[0][1]
    for y in range(16):
        for x in range(16):
            i = owner[y][x]
            if i < 0:
                c.set(x, y, TINT, 2)
                continue
            dx, dy = _wdelta(x + 0.5, y + 0.5, *pts[i])
            v = -(dx + dy) / (rad[i] * 1.2)
            c.set(x, y, TINT, min(7, tone[i] + round(max(-1.5, min(1.0, v * 1.4)))))
    # 轮廓：石头的左上沿亮一级、右下沿暗一级；石缝里紧挨着石头右下的一格最深
    for y in range(16):
        for x in range(16):
            i = owner[y][x]
            up, left = owner[(y - 1) % 16][x], owner[y][(x - 1) % 16]
            down, right = owner[(y + 1) % 16][x], owner[y][(x + 1) % 16]
            if i < 0:
                if up >= 0 or left >= 0:
                    c.set(x, y, TINT, 1)
                continue
            if up != i or left != i:
                c.shift(x, y, 1)
            elif down != i or right != i:
                c.shift(x, y, -1)
    # 每块石头上一两点暗的麻点，最亮的石头上一点高光
    for i, (px, py) in enumerate(pts):
        x, y = int(px + 0.5) % 16, int(py + 0.8) % 16
        if owner[y][x] == i and rng.random() < 0.7:
            c.shift(x, y, -1)
        hx, hy = int(px - rad[i] * 0.45) % 16, int(py - rad[i] * 0.45) % 16
        if owner[hy][hx] == i and tone[i] >= 6:
            c.set(hx, hy, TINT, 8, glint=True)
    despeckle(c, 1)
    return c


@texture("block/planet_soil", tinted=True, rough=True, refs=("block/dirt", "block/coarse_dirt", "block/rooted_dirt"))
def planet_soil(rng):
    c = Canvas()
    f = field(rng, (8, 16), (0.6, 0.4))
    emboss(c, terrace(f, (0.35, 0.62)), TINT, 5, step=0.8)
    # 土块：几团暗的，两三格一团
    c.clusters(rng, 0, 0, 15, 15, -1, 9, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 5, 2)
    despeckle(c)
    # 小石子：亮点加右下的影子
    for _ in range(5):
        pebble(c, rng.randrange(16), rng.randrange(16), TINT, 8, big=rng.random() < 0.4)
    return c


@texture("block/planet_turf_top", tinted=True, rough=True, refs=("block/grass_block_top", "block/moss_block"))
def planet_turf_top(rng):
    """草皮顶：细密的一簇簇草，四级明暗，簇的朝光一侧亮；零星几根草尖最亮。"""
    c = Canvas()
    f = field(rng, (8, 16, 16), (0.3, 0.4, 0.3))
    emboss(c, terrace(f, (0.25, 0.48, 0.7)), TINT, 5, step=0.8)
    c.clusters(rng, 0, 0, 15, 15, -1, 8, 2)
    despeckle(c, 1)
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, TINT, 8, glint=True)
        c.set(x, (y + 1) % 16, TINT, 4, glint=True)
    return c


@texture("block/planet_turf_side_overlay", tinted=True, tile=False, refs=("block/grass_block_side_overlay",))
def planet_turf_side_overlay(rng):
    """侧面上沿垂下来的草皮（底下的泥土是另一张）：上沿最亮，往下渐暗，下沿参差，偶尔垂一根长的草叶。"""
    c = Canvas()
    depth = [rng.randint(3, 5) for _ in range(16)]
    for _ in range(2):
        depth = [round((depth[(i - 1) % 16] + depth[i] * 2 + depth[(i + 1) % 16]) / 4) for i in range(16)]
    for i in rng.sample(range(16), 4):
        depth[i] += rng.choice((1, 2))
    f = field(rng, (8, 16), (0.4, 0.6))
    for x in range(16):
        for y in range(depth[x]):
            t = y / max(1, depth[x] - 1)
            lv = 7 - round(t * 2.2) + (1 if f[y][x] > 0.7 else (-1 if f[y][x] < 0.25 else 0))
            c.set(x, y, TINT, lv)
        # 草尖：最下一格深一级
        c.set(x, depth[x] - 1, TINT, max(2, c.level(x, depth[x] - 1) - 1))
    for x in range(16):
        c.set(x, 0, TINT, 7 if rng.random() < 0.6 else 6)
    despeckle(c, 1, tile=False)
    return c


@texture("block/planet_regolith", tinted=True, rough=True, refs=("block/gravel", "block/suspicious_gravel_0"))
def planet_regolith(rng):
    """风化层（月壤）：细的粉尘，零星的小陨坑和碎石。"""
    c = Canvas()
    f = field(rng, (8, 16), (0.5, 0.5))
    emboss(c, terrace(f, (0.4, 0.68)), TINT, 6, step=0.6)
    # 两个小陨坑：坑里左上是影子，右下的坑沿受光
    for _ in range(2):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for dx, dy, d in ((0, 0, -2), (1, 0, -1), (0, 1, -1), (1, 1, 0), (2, 1, 1), (1, 2, 1), (2, 2, 1),
                          (-1, 0, -1), (0, -1, -1), (-1, -1, 0)):
            c.set((cx + dx) % 16, (cy + dy) % 16, TINT, 6 + d)
    despeckle(c, 1)
    for _ in range(6):
        pebble(c, rng.randrange(16), rng.randrange(16), TINT, rng.choice((8, 8, 4)), big=rng.random() < 0.3)
    return c


@texture("block/planet_sand", tinted=True, rough=True, refs=("block/sand", "block/red_sand"))
def planet_sand(rng):
    """沙：细的颗粒用有序抖动，斜着几道很淡的风纹（迎风面亮、背风面暗）。"""
    c = Canvas()
    f = field(rng, (4, 8, 16), (0.3, 0.3, 0.4))
    bayer = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
    for y in range(16):
        for x in range(16):
            # 风纹：沿一条斜着的正弦，周期 16，平铺接得上
            ripple = math.sin((x + y * 2) / 16 * math.tau * 2 + f[y][x] * 2.2)
            v = f[y][x] * 1.6 + ripple * 0.4
            lv = 6 + (1 if v > 1.2 else 0) + (-1 if v < 0.2 else 0)
            if 0.95 < v < 1.2 and bayer[y % 4][x % 4] < 6:
                lv += 1
            if 0.2 < v < 0.4 and bayer[y % 4][x % 4] < 6:
                lv -= 1
            c.set(x, y, TINT, lv)
    # 沙粒：亮的、暗的单个颗粒撒一层
    for _ in range(26):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, TINT, c.level(x, y) + rng.choice((-1, 1, 1)), glint=True)
    return c


@texture("block/planet_snow", tinted=True, rough=True, refs=("block/snow", "block/powder_snow"))
def planet_snow(rng):
    """雪：大片很亮，背光的雪窝暗一级，偶尔一小团更深的影子；零星几点闪光。
    雪本来就白，平均亮度放到 0.91，比别的染色贴图亮。"""
    c = Canvas()
    f = field(rng, ((4, 8), (8, 8), (16, 16)), (0.5, 0.3, 0.2))
    lv = terrace(f, (0.45,))
    for y in range(16):
        for x in range(16):
            c.set(x, y, TINT, 7 + lv[y][x])
    despeckle(c)
    # 雪窝里偶尔一小团更深的影子
    c.clusters(rng, 0, 0, 15, 15, -1, 3, 3)
    for _ in range(9):
        c.set(rng.randrange(16), rng.randrange(16), TINT, 9, glint=True)
    return c


@texture("block/planet_ice", tinted=True, refs=("block/ice", "block/packed_ice", "block/blue_ice"))
def planet_ice(rng):
    """冰：平滑的冰面，深浅两三级成大片；斜着几道亮的反光条，两道细的白裂纹，几个小气泡。"""
    c = Canvas()
    f = field(rng, (2, 4, 8), (0.5, 0.35, 0.15))
    for y in range(16):
        for x in range(16):
            v = f[y][x]
            c.set(x, y, TINT, 5 + (1 if v > 0.45 else 0) + (1 if v > 0.78 else 0))
    # 反光：从左下到右上的斜条，一宽一窄，中间断开几格
    for y in range(16):
        for x in range(16):
            k = (x + y) % 16
            if k in (4, 5) or (k == 11 and (x * 7 + y) % 5):
                c.shift(x, y, 1)
    # 裂纹：白的细线，大体斜着走，中途分一个岔；右边一格暗
    for _ in range(2):
        x0, y0 = rng.randrange(16), rng.randrange(16)
        pts = walk(rng, x0, y0, rng.randint(8, 11), dirs=((1, -1), (1, -1), (1, 0), (0, -1)))
        bx, by = pts[len(pts) // 2]
        pts += walk(rng, bx, by, 3, dirs=((1, 1), (0, 1)))
        cells = {(x % 16, y % 16) for x, y in pts}
        for x, y in cells:
            c.set(x, y, TINT, 8, glint=True)
        for x, y in cells:
            q = ((x + 1) % 16, y)
            if q not in cells:
                c.shift(*q, -1)
    for _ in range(3):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, TINT, 9, glint=True)
        c.set((x + 1) % 16, (y + 1) % 16, TINT, 4, glint=True)
    despeckle(c, 1)
    return c


@texture("block/planet_ash", tinted=True, rough=True, refs=("block/gray_concrete_powder", "block/soul_soil"))
def planet_ash(rng):
    """火山灰：松软的一团团，暗的灰窝，零星的亮点是没烧透的碎屑。"""
    c = Canvas()
    f = field(rng, (4, 8, 16), (0.35, 0.35, 0.3))
    emboss(c, terrace(f, (0.3, 0.55, 0.78)), TINT, 5, step=0.7)
    c.clusters(rng, 0, 0, 15, 15, -1, 10, 3)
    despeckle(c)
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(16), TINT, 9, glint=True)
    for _ in range(4):
        pebble(c, rng.randrange(16), rng.randrange(16), TINT, 7)
    return c


@texture("block/planet_crystal", tinted=True, refs=("block/amethyst_block", "block/budding_amethyst"))
def planet_crystal(rng):
    """结晶岩：斜着、竖着的几道晶棱把面切成一块块平的晶面，每面一个亮度；
    晶棱朝光的一侧是亮线，背光的一侧暗一级，几个晶面上有星点高光。"""
    c = Canvas()
    cuts1 = sorted(rng.sample(range(16), 3))   # 沿 x+y 的晶棱（左下到右上）
    cuts2 = sorted(rng.sample(range(16), 2))   # 沿 x-y 的晶棱
    cuts3 = sorted(rng.sample(range(16), 2))   # 竖的晶棱（柱状晶体）

    def region(x, y):
        a = sum(1 for t in cuts1 if (x + y) % 16 >= t) % len(cuts1)
        b = sum(1 for t in cuts2 if (x - y) % 16 >= t) % len(cuts2)
        d = sum(1 for t in cuts3 if x % 16 >= t) % len(cuts3)
        return a, b, d

    tone = {}
    for y in range(16):
        for x in range(16):
            r = region(x, y)
            if r not in tone:
                tone[r] = rng.choice((4, 5, 5, 6, 6, 7))
            c.set(x, y, TINT, tone[r])
    for y in range(16):
        for x in range(16):
            r = region(x, y)
            if region(x, y - 1) != r or region(x - 1, y) != r:
                c.set(x, y, TINT, min(8, tone[r] + 2))
            elif region(x, y + 1) != r or region(x + 1, y) != r:
                c.set(x, y, TINT, tone[r] - 1)
    despeckle(c, 2)
    for _ in range(4):
        c.set(rng.randrange(16), rng.randrange(16), TINT, 9, glint=True)
    return c


@texture("block/planet_strata", tinted=True, rough=True, refs=("block/terracotta", "block/smooth_sandstone"))
def planet_strata(rng):
    """岩层：一层层横的沉积（颜色游戏按高度染），层里是长短不一的横纹，零星几颗砾石。"""
    c = Canvas()
    rows = []
    y = 0
    while y < 16:
        n = min(16 - y, rng.choice((2, 2, 3, 3, 4)))
        rows += [rng.choice((4, 5, 5, 6, 6, 7))] * n
        y += n
    for y in range(16):
        for x in range(16):
            c.set(x, y, TINT, rows[y])
    # 层里的横纹：三到六格长，亮一级或暗一级
    for _ in range(18):
        x, y = rng.randrange(16), rng.randrange(16)
        d = rng.choice((-1, 1))
        for i in range(rng.randint(3, 6)):
            c.set((x + i) % 16, y, TINT, rows[y] + d)
    # 层与层之间：上层的底沿暗一级，像压出来的缝，偶尔断开
    for y in range(16):
        if rows[y] != rows[(y + 1) % 16]:
            for x in range(16):
                if rng.random() < 0.85:
                    c.set(x, y, TINT, min(rows[y], rows[(y + 1) % 16]) - 1)
    despeckle(c, 1)
    for _ in range(3):
        pebble(c, rng.randrange(16), rng.randrange(16), TINT, 8)
    return c


# ---- 野外的地标（ADR-0026）

@texture("block/fossil_dirt", rough=True, refs=("block/dirt", "block/suspicious_sand_0", "block/bone_block_side"))
def fossil_dirt(rng):
    """化石土：灰褐的沉积土，几道深浅不一的水平层理；左上一个菊石的印子，右下一截长骨，零星几块碎骨。"""
    c = Canvas()
    f = field(rng, ((8, 16), (16, 16)), (0.6, 0.4))
    band = [0, 0, 1, 1, 0, -1, 0, 0, 1, 0, 0, -1, -1, 0, 1, 0]
    for y in range(16):
        for x in range(16):
            v = f[y][x]
            c.set(x, y, SOIL, 3 + band[y] + (1 if v > 0.72 else (-1 if v < 0.25 else 0)))
    despeckle(c)
    # 层理的缝：断断续续的暗线
    for y in (4, 11):
        for x in range(16):
            if (x * 5 + y) % 7 < 5:
                c.set(x, y, SOIL, 1)
                c.shift(x, (y + 1) % 16, 1)
    # 菊石的印子：一圈圈往外盘的螺纹，壳是米白的，缝是土的暗色
    cx, cy = 4.5, 4.5
    shell = c.disc_cells(cx, cy, 2.9)
    for x, y in shell:
        dx, dy = x - cx, y - cy
        c.set(x, y, BONE, 3 - (1 if dx + dy > 1.5 else 0) + (1 if dx + dy < -1.5 else 0))
    for i in range(30):
        t = i / 29 * 2.1 * math.pi
        r = 0.4 + t * 0.42
        x, y = round(cx + math.cos(t) * r), round(cy + math.sin(t) * r)
        if (x, y) in shell:
            c.set(x, y, SOIL, 2)
    c.set(4, 4, BONE, 4)
    # 长骨：斜着的一截，上沿亮、下沿暗，两头鼓出来
    for i in range(6):
        x, y = 9 + i, 12 - i // 2
        c.set(x, y, BONE, 5 - (1 if i > 3 else 0))
        c.set(x, y + 1, BONE, 2)
    for x, y, lv in ((8, 11, 4), (8, 12, 3), (8, 13, 1), (15, 8, 5), (15, 9, 3), (14, 8, 4)):
        c.set(x, y, BONE, lv) if lv > 1 else c.set(x, y, SOIL, 1)
    # 碎骨：两三格的小块，右下一格土的影子
    for x, y in ((1, 12), (12, 2), (6, 14)):
        c.set(x, y, BONE, 4, glint=True)
        c.set(x + 1, y, BONE, 3, glint=True)
        c.set(x + 1, y + 1, SOIL, 1, glint=True)
    for _ in range(4):
        pebble(c, rng.randrange(16), rng.randrange(16), SOIL, 5)
    return c


@texture("block/fulgurite", rough=True, refs=("block/sandstone", "block/suspicious_sand_0"))
def fulgurite(rng):
    """雷击石：外面一层粘满沙粒的黄褐硬壳，中间是闪电一样分叉的灰绿玻璃质，玻璃边上一圈烧焦的暗沙，上面几个亮点。"""
    c = Canvas()
    f = field(rng, (8, 16), (0.5, 0.5))
    emboss(c, terrace(f, (0.35, 0.65)), SAND, 3, step=0.8)
    # 沙粒：成团的亮点、暗点
    c.clusters(rng, 0, 0, 15, 15, -1, 8, 2)
    c.clusters(rng, 0, 0, 15, 15, 1, 6, 2)
    despeckle(c)
    # 闪电一样的玻璃：从上往下走，随机分岔；上下接得上（列数按 16 取模）
    cells = set()
    branches = [(rng.uniform(3, 6), 0.0, rng.uniform(-0.3, 0.3), 1.6), (rng.uniform(10, 13), 0.0, rng.uniform(-0.3, 0.3), 1.2)]
    while branches:
        x, y, dx, width = branches.pop()
        while y < 16 and width > 0.35:
            for w in range(-int(width), int(width) + 1):
                cells.add((int(round(x + w)) % 16, int(y)))
            x += dx + rng.uniform(-0.7, 0.7)
            y += 1
            width -= 0.05
            if rng.random() < 0.14 and width > 0.7:
                branches.append((x, y, rng.choice((-0.9, 0.9)), width * 0.55))
    for x, y in cells:
        left = ((x - 1) % 16, y) not in cells
        right = ((x + 1) % 16, y) not in cells
        c.set(x, y, FULGURITE, 4 if left else (1 if right else 3))
    for x, y in cells:
        for q in (((x - 1) % 16, y), ((x + 1) % 16, y)):
            if q not in cells:
                c.set(q[0], q[1], SAND, 1)
    for x, y in rng.sample(sorted(cells), min(7, len(cells))):
        c.set(x, y, FULGURITE, 5, glint=True)
    return c


def _capsule_rim(c: Canvas):
    """舱顶的一圈：黄黑警示斜条，四角压一颗螺栓。"""
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            if (x + y) % 4 < 2:
                c.set(x, y, HAZARD, 3 if (x == 0 or y == 0) else 2)
            else:
                c.set(x, y, GRAPHITE, 1)


def _scorch(c: Canvas, rng: random.Random, x0, y0, x1, y1, amount: float, ramp_to=None):
    """烧焦：成片的，越往下越重；轻的压暗一级，重的换成烧黑的颜色。"""
    f = field(rng, (4, 8), (0.6, 0.4))
    for x, y in c.cells(x0, y0, x1, y1):
        t = (y - y0) / max(1, y1 - y0)
        v = f[y][x] * 0.7 + t * amount
        if v > 0.62:
            c.shift(x, y, -1)
        if ramp_to and v > 0.86:
            c.set(x, y, ramp_to, 1 if v > 1.0 else 2)


@texture("block/salvage_capsule_top", refs=("block/iron_trapdoor", "block/heavy_core"))
def salvage_capsule_top(rng):
    return _capsule_top(rng, False)


@texture("block/salvage_capsule_top_open")
def salvage_capsule_top_open(rng):
    return _capsule_top(rng, True)


def _capsule_top(rng, opened: bool) -> Canvas:
    """舱顶：烧黑了的金属板，一圈警示条，正中一个圆舱门（凸起、一圈锁紧螺栓、一个把手）；
    开着的：舱门翻到右上，露出黑洞洞的舱里，里面几样东西的轮廓。"""
    c = Canvas()
    c.fill(STEEL, 3)
    _scorch(c, rng, 1, 1, 14, 14, 0.25)
    _capsule_rim(c)
    cx = cy = 7.5
    hatch = c.disc_cells(cx, cy, 4.6)
    ring = [p for p in c.disc_cells(cx, cy, 5.6) if p not in hatch]
    for x, y in ring:
        c.set(x, y, STEEL, 1 if x + y < 15 else 4)
    if opened:
        for x, y in hatch:
            d = math.hypot(x - cx, y - cy)
            c.set(x, y, GRAPHITE, 0 if d < 3.4 else 1)
        # 舱里：一块橙色的东西、一个绿的盒子、一截钢管
        c.set(6, 8, ORANGE, 3)
        c.set(7, 8, ORANGE, 2)
        c.set(9, 6, GREEN, 2)
        c.set(9, 7, GREEN, 1)
        c.set(5, 6, STEEL, 3)
        c.set(6, 6, STEEL, 2)
        # 内壁的反光：舱口下沿一道
        for x, y in hatch:
            if math.hypot(x - cx, y - cy) > 3.4 and x + y > 15:
                c.set(x, y, STEEL, 2)
        # 翻开的舱门：一条厚边压在右上
        for i in range(7):
            c.set(10 + i // 3, 1 + i, STEEL, 5)
            c.set(11 + i // 3, 1 + i, STEEL, 3)
            c.set(12 + i // 3, 1 + i, STEEL, 1)
    else:
        for x, y in hatch:
            d = (x - cx) + (y - cy)
            c.set(x, y, STEEL, 4 + (1 if d < -3 else 0) - (1 if d > 3 else 0))
        # 锁紧的螺栓一圈
        for a in range(8):
            t = a / 8 * math.tau + math.pi / 8
            x, y = round(cx + math.cos(t) * 3.6), round(cy + math.sin(t) * 3.6)
            c.set(x, y, STEEL, 6, glint=True)
            c.set(x + 1, y + 1, STEEL, 2, glint=True) if (x + 1, y + 1) in hatch else None
        # 把手：横着的一道
        c.hline(6, 9, 7, GRAPHITE, 2)
        c.hline(6, 9, 8, STEEL, 6)
        c.set(6, 7, STEEL, 6)
    return c


@texture("block/salvage_capsule_side", refs=("block/iron_block",))
def salvage_capsule_side(rng):
    """侧面：灰白的舱板，中间一道竖缝，上面一道橙色条和一盏信标灯，下半截再入大气时烧焦了。"""
    c = Canvas()
    c.fill(WHITE, 3)
    for x, y in c.cells(0, 0, 15, 15):
        if (x + y * 3) % 11 == 0:
            c.shift(x, y, -1)
    c.hline(0, 15, 0, WHITE, 5)
    c.hline(0, 15, 15, GRAPHITE, 2)
    c.vline(7, 1, 14, WHITE, 1)
    c.vline(8, 1, 14, WHITE, 5)
    c.hline(0, 15, 4, ORANGE, 4)
    c.hline(0, 15, 5, ORANGE, 2)
    c.set(7, 4, ORANGE, 1)
    c.set(7, 5, ORANGE, 1)
    # 信标灯和对面一个灭了的
    c.set(3, 2, AMBER_GLOW, 5, glint=True)
    c.set(4, 2, AMBER_GLOW, 3, glint=True)
    c.set(12, 2, GRAPHITE, 2, glint=True)
    c.set(13, 2, GRAPHITE, 1, glint=True)
    for x in (2, 13):
        c.rivet(x, 10, WHITE, 3)
    _scorch(c, rng, 0, 6, 15, 14, 0.75, GRAPHITE)
    return c


@texture("block/salvage_capsule_bottom", rough=True, refs=("block/blackstone",))
def salvage_capsule_bottom(rng):
    """舱底：烧黑的防热瓦，一块块方的，缝里发暗，个别瓦烧得发褐。"""
    c = Canvas()
    tone = {(i, j): rng.choice((2, 2, 3)) for i in range(4) for j in range(4)}
    for x, y in c.cells():
        c.set(x, y, GRAPHITE, tone[(x // 4, y // 4)])
        if x % 4 == 3 or y % 4 == 3:
            c.set(x, y, GRAPHITE, 1)
        elif x % 4 == 0 or y % 4 == 0:
            c.shift(x, y, 1)
    # 一两块瓦烧得发褐，一块缺了角
    tx, ty = rng.randrange(4) * 4, rng.randrange(4) * 4
    for x, y in c.cells(tx + 1, ty + 1, tx + 2, ty + 2):
        c.set(x, y, RUST, 1)
    c.set(tx, ty, RUST, 2)
    c.clusters(rng, 0, 0, 15, 15, -1, 5, 2, only=GRAPHITE)
    return c


# ---- 生物卵（染色）

def _egg_shell(rng: random.Random) -> Canvas:
    """卵壳的一面：模型是几层盒子摞起来的，侧面只用中间 8 列、下面 10 行。中间亮、两边暗，像圆的；
    下面背光暗一点，上面亮；一点点成团的斑。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) / 4.5
            lv = 7 - round(d * d * 2.2) - (1 if y > 12 else 0) + (1 if y < 9 and x < 8 else 0)
            c.set(x, y, TINT, lv)
    for _ in range(9):
        x, y = rng.randint(4, 11), rng.randint(6, 15)
        c.shift(x, y, -2)
        if rng.random() < 0.5:
            c.shift(x + 1, y, -1)
    c.set(6, 8, TINT, 9, glint=True)
    return c


def _egg_crack(c: Canvas, pts):
    """一道裂缝：一格一格连起来，缝是暗的，缝下面一格亮（壳的断口）。"""
    cells = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            t = i / steps if steps else 0
            cells.append((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))
    for x, y in cells:
        c.set(x, y, TINT, 1, glint=True)
    for x, y in cells:
        if (x, y + 1) not in cells:
            c.set(x, y + 1, TINT, 8, glint=True)


EGG_FIRST = [(4, 12), (6, 11), (7, 13), (9, 11), (11, 12)]
EGG_SECOND = [(5, 8), (7, 9), (8, 7), (10, 9), (11, 8)]


@texture("block/creature_egg_side_0", tinted=True, tile=False, refs=("block/sniffer_egg_not_cracked_east", "item/egg"))
def creature_egg_side_0(rng):
    return _egg_shell(random.Random(1460))


@texture("block/creature_egg_side_1", tinted=True, tile=False, refs=("block/sniffer_egg_slightly_cracked_east",))
def creature_egg_side_1(rng):
    c = _egg_shell(random.Random(1460))
    _egg_crack(c, EGG_FIRST)
    return c


@texture("block/creature_egg_side_2", tinted=True, tile=False, refs=("block/sniffer_egg_very_cracked_east",))
def creature_egg_side_2(rng):
    c = _egg_shell(random.Random(1460))
    _egg_crack(c, EGG_FIRST)
    _egg_crack(c, EGG_SECOND)
    # 第二道裂缝旁边掉了一小块壳，露出里面（暗），缺口的下沿是亮的断口
    for x, y in ((8, 8), (9, 8), (8, 9), (9, 9)):
        c.set(x, y, TINT, 0, glint=True)
    c.set(10, 9, TINT, 2, glint=True)
    return c


@texture("block/creature_egg_top", tinted=True, tile=False, refs=("block/sniffer_egg_not_cracked_top",))
def creature_egg_top(rng):
    """卵顶：模型只用中间 8 格。正中最亮，往外一圈圈暗下去，左上偏亮。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.2, y - 7.2) / 4.5
            c.set(x, y, TINT, 8 - round(d * d * 2.0))
    despeckle(c, 2, tile=False)
    for _ in range(6):
        x, y = rng.randint(4, 11), rng.randint(4, 11)
        c.set(x, y, TINT, c.level(x, y) - 2, glint=True)
    c.set(6, 6, TINT, 9, glint=True)
    return c


# ---- 酸液（染色，动画）

@texture("block/acid_still", tinted=True, rough=True, frames=32, refs=("block/water_still",))
def acid_still(rng):
    """静止的酸液：粘稠的液面上两层慢慢漂的波，亮的波峰窄、成团；几个气泡慢慢冒上来再破掉。32 帧首尾接得上。"""
    c = Canvas(16, 16 * 32)
    for f in range(32):
        t = f / 32 * math.tau
        for y in range(16):
            for x in range(16):
                u, v = x / 16 * math.tau, y / 16 * math.tau
                a = math.sin(u * 2 + v + t)
                b = math.sin(u - v * 2 - t * 2 + 1.3 * math.sin(u + t))
                w = (0.5 + 0.25 * a + 0.25 * b) ** 1.5
                c.set(x, y + f * 16, TINT, 3 + round(w * 4.2))
        # 气泡：每个活 8 帧，从暗点长成亮的一圈再破
        for i in range(4):
            age = (f + i * 8) % 32
            if age >= 8:
                continue
            bx = (i * 5 + 3 + (f + i * 8) // 32 * 7) % 16
            by = (i * 9 + 4) % 16
            if age < 6:
                c.set(bx, by + f * 16, TINT, 8 if age > 2 else 6, glint=True)
                c.set((bx + 1) % 16, (by + 1) % 16 + f * 16, TINT, 2, glint=True)
            else:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    c.set((bx + dx) % 16, (by + dy) % 16 + f * 16, TINT, 8, glint=True)
    return c


@texture("block/acid_flow", tinted=True, rough=True, frames=32, refs=("block/water_flow",))
def acid_flow(rng):
    """流动的酸液：顺着流向拉长的条纹，一帧往下挪一格，32 帧正好走完一圈；条纹的头亮、尾巴暗。"""
    c = Canvas(32, 32 * 32)
    for f in range(32):
        for y in range(32):
            for x in range(32):
                u = x / 32 * math.tau
                v = (y - f) / 32 * math.tau
                a = math.sin(u * 3 + 0.8 * math.sin(v * 2))
                b = math.sin(v * 4 + a * 1.2 + u)
                w = (0.5 + 0.32 * b + 0.18 * math.sin(u * 5 + v)) ** 1.4
                c.set(x, y + f * 32, TINT, 3 + round(max(0.0, min(1.0, w)) * 4.4))
    return c


@texture("item/acid_bucket", refs=("item/bucket", "item/lava_bucket"))
def acid_bucket(rng):
    """酸液桶的壳：铁皮桶，上口是椭圆的（里面的液面另一层画），桶身往下收，左边受光，一道箍，提手是一根细铁丝。"""
    c = Canvas()
    c.draw([
        "................",
        "......1111......",
        ".....1....1.....",
        "....1......1....",
        "...0566666651...",
        "..05........40..",
        "..05........40..",
        "..15........30..",
        "..156666665430..",
        "..155555554320..",
        "...4444444321...",
        "...5666665421...",
        "...1444443211...",
        "....44443210....",
        ".....211110.....",
        "................",
    ], {"0123456": IRON})
    return c


@texture("item/acid_bucket_fluid", tinted=True, tile=False)
def acid_bucket_fluid(rng):
    """桶里的液面（染成酸液的颜色）：上沿一道亮的反光，往里渐暗，正中一个小气泡。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "................",
        "................",
        "................",
        "....98888888....",
        "....77777776....",
        "....65566555....",
    ], {"0123456789": TINT}, 0, 0)
    c.set(8, 6, TINT, 9, glint=True)
    return c


# ---- 兴趣点（core.poi.PoiType 的颜色：掉落舱青、石碑紫、科技储藏箱绿、信号增强器粉）

CACHE = register(ramp("planet_cache", 205, 0.026, n=7, lo=0.22, hi=0.72, shift=12))   # 科技储藏箱的青灰金属


def shell(rng: random.Random, r, base: int, wear: float = 0.18, clusters: int = 6) -> Canvas:
    """一块外壳板：最外一圈左上亮、右下暗（凸起的边），板面一点成团的明暗，边上偶尔磨亮。"""
    c = Canvas()
    c.fill(r, base)
    c.clusters(rng, 1, 1, 14, 14, -1, clusters, 2)
    c.hline(0, 14, 0, r, base + 1)
    c.vline(0, 1, 14, r, base + 1)
    c.hline(1, 15, 15, r, base - 2)
    c.vline(15, 1, 15, r, base - 2)
    c.edge_wear(rng, 0, 0, 15, 15, wear)
    return c


def hazard_band(c: Canvas, x0, y0, x1, y1, period: int = 4):
    """黄黑（或橙黑）的斜警示条：上沿亮一级，下沿暗一级。"""
    for x, y in c.cells(x0, y0, x1, y1):
        if (x + y) % period < period // 2:
            c.set(x, y, ORANGE, 4 if y == y0 else (2 if y == y1 else 3))
        else:
            c.set(x, y, GRAPHITE, 2 if y == y0 else 0 if y == y1 else 1)


def porthole(c: Canvas, cx: float, cy: float, rad: float, frame, lit: bool, broken: bool = False):
    """舷窗：一圈凸起的窗框（左上亮、右下暗），窗里暗的玻璃；亮着的从边上往中间越来越亮，左上一道反光。"""
    glass = c.disc_cells(cx, cy, rad - 1)
    rim = [p for p in c.disc_cells(cx, cy, rad) if p not in glass]
    for x, y in rim:
        c.set(x, y, frame, 5 if (x - cx) + (y - cy) < -1 else (2 if (x - cx) + (y - cy) > 1 else 3))
    for x, y in glass:
        d = math.hypot(x - cx, y - cy) / max(1.0, rad - 1)
        if lit:
            c.set(x, y, CYAN_GLOW, 5 - round(d * 3))
        else:
            c.set(x, y, SCREEN, 1 + (1 if (x - cx) + (y - cy) < -1 else 0))
    gx, gy = round(cx - rad * 0.4), round(cy - rad * 0.45)
    c.set(gx, gy, WHITE_GLOW if lit else GLASS, 5 if lit else 2, glint=True)
    c.set(gx - 1, gy + 1, WHITE_GLOW if lit else GLASS, 4 if lit else 1, glint=True)
    if broken:
        for x, y in ((cx + 1, cy - 2), (cx + 2, cy - 1), (cx + 2, cy), (cx + 3, cy + 1)):
            c.set(round(x), round(y), GLASS, 2, glint=True)


def drop_pod_front_face(rng, repaired: bool) -> Canvas:
    c = shell(rng, WHITE, 4, clusters=4)
    porthole(c, 7.5, 6.5, 5.4, WHITE, repaired, broken=not repaired)
    # 舷窗下面一排状态灯：坏着的只有第一盏红灯，修好了一排绿灯
    c.hline(4, 11, 13, GRAPHITE, 1)
    for i, x in enumerate(range(5, 11)):
        if repaired:
            c.set(x, 13, GREEN_GLOW, 4 if i % 2 == 0 else 3, glint=True)
        elif i == 0:
            c.set(x, 13, RED_GLOW, 5, glint=True)
        else:
            c.set(x, 13, SCREEN, 2, glint=True)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, WHITE, 3)
    if not repaired:
        _scorch(c, rng, 1, 1, 14, 14, 0.35)
    return c


@texture("block/drop_pod_front", refs=("block/observer_front", "block/dispenser_front"))
def drop_pod_front(rng):
    """掉落舱正面：白色舱壳，一个大舷窗；没修好时玻璃暗着、裂了一道，只亮一盏红灯，舱壳熏黑了几块。"""
    return drop_pod_front_face(random.Random(41), False)


@texture("block/drop_pod_front_repaired")
def drop_pod_front_repaired(rng):
    """修好的掉落舱：舷窗亮青色，一排绿灯。"""
    return drop_pod_front_face(random.Random(41), True)


@texture("block/drop_pod_side")
def drop_pod_side(rng):
    """掉落舱侧面：白壳，下面一道橙黑警示条，中间一道竖的接缝，左上一组散热缝，右上印着编号。"""
    c = shell(rng, WHITE, 4, clusters=4)
    hazard_band(c, 1, 10, 14, 12)
    c.vline(7, 1, 9, WHITE, 2)
    c.vline(8, 1, 9, WHITE, 5)
    for y in (3, 5, 7):
        c.hline(2, 5, y, WHITE, 1)
        c.hline(2, 5, y + 1, WHITE, 5)
    # 编号：两个笔画粗一点的字
    c.draw([
        "11.111",
        ".1...1",
        ".1..1.",
        "111.1.",
    ], {"0123456": GRAPHITE}, 9, 3)
    _scorch(c, rng, 1, 1, 14, 14, 0.2)
    return c


@texture("block/drop_pod_top")
def drop_pod_top(rng):
    """掉落舱顶：一圈凹下去的舱口缝，四个橙色定位标，正中一颗青色的信标灯。"""
    c = shell(rng, WHITE, 4, clusters=4)
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 5.2):
        if math.hypot(x - cx, y - cy) > 4.2:
            c.set(x, y, WHITE, 1 if x + y < 15 else 5)
    for a in range(4):
        t = math.radians(a * 90 + 45)
        x, y = round(cx + 4.7 * math.cos(t)), round(cy + 4.7 * math.sin(t))
        c.set(x, y, ORANGE, 4, glint=True)
    c.shade_disc(cx, cy, 1.6, WHITE, 3, spec=False)
    c.set(7, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, CYAN_GLOW, 3, glint=True)
    c.set(8, 7, CYAN_GLOW, 4, glint=True)
    c.set(7, 8, CYAN_GLOW, 4, glint=True)
    _scorch(c, rng, 1, 1, 14, 14, 0.15)
    return c


@texture("block/data_terminal_front", refs=("block/crafter_north",))
def data_terminal_front(rng):
    """数据终端正面：深色机壳，凹进去的屏幕里几行绿字和一个亮的光标，一道扫描线；下面一排按键，右下一盏琥珀灯。"""
    c = shell(rng, GRAPHITE, 3)
    c.recess(1, 1, 14, 10, SCREEN, 1)
    c.rect(2, 2, 13, 9, SCREEN, 0)
    r = random.Random(52)
    for y, n in ((3, 9), (5, 11), (7, 6)):
        for x in range(3, 3 + n):
            if r.random() < 0.8:
                c.set(x, y, GREEN_GLOW, 4 if r.random() < 0.7 else 2, glint=True)
    c.set(10, 7, GREEN_GLOW, 5, glint=True)
    for x in range(2, 14):
        if c.get(x, 8)[0] is SCREEN:
            c.set(x, 8, SCREEN, 1)
    for x in range(2, 13, 2):
        c.set(x, 12, GRAPHITE, 5, glint=True)
        c.set(x + 1, 12, GRAPHITE, 4, glint=True)
        c.set(x, 13, GRAPHITE, 1, glint=True)
        c.set(x + 1, 13, GRAPHITE, 1, glint=True)
    c.set(13, 14, AMBER_GLOW, 4, glint=True)
    return c


@texture("block/data_terminal_side")
def data_terminal_side(rng):
    """侧面：一组横的散热缝。"""
    c = shell(rng, GRAPHITE, 3)
    c.vent(3, 3, 12, 12, GRAPHITE, 3)
    return c


@texture("block/data_terminal_top")
def data_terminal_top(rng):
    """顶面（底面也用）：两块盖板中间一道缝，四颗螺栓，一盏绿的电源灯。"""
    c = shell(rng, GRAPHITE, 3)
    c.hline(1, 14, 7, GRAPHITE, 1)
    c.hline(1, 14, 8, GRAPHITE, 4)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, GRAPHITE, 3)
    c.set(7, 4, GREEN_GLOW, 4, glint=True)
    return c


def crate_shell(rng: random.Random) -> Canvas:
    """补给箱：深灰的箱体，四边两格宽的橙色包边（左上亮、右下暗），包角压一颗螺栓。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 2, 2, 13, 13, -1, 5, 2)
    for i in range(16):
        for t in (0, 1):
            c.set(i, t, ORANGE, 5 if t == 0 else 4)
            c.set(t, i, ORANGE, 4 if t == 0 else 3)
            c.set(i, 15 - t, ORANGE, 1 if t == 0 else 2)
            c.set(15 - t, i, ORANGE, 2 if t == 0 else 3)
    c.set(0, 15, ORANGE, 2)
    c.set(15, 0, ORANGE, 3)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, GRAPHITE, 3)
    return c


@texture("block/supply_crate_front", refs=("block/barrel_side", "block/vault_front_off"))
def supply_crate_front(rng):
    """补给箱正面：中间一道合缝，一个钢的锁扣，锁扣上一盏琥珀灯。"""
    c = crate_shell(rng)
    c.hline(2, 13, 7, GRAPHITE, 1)
    c.hline(2, 13, 8, GRAPHITE, 4)
    c.panel(6, 6, 9, 10, STEEL, 4)
    c.set(7, 8, GRAPHITE, 0)
    c.set(8, 8, GRAPHITE, 0)
    c.set(8, 7, AMBER_GLOW, 5, glint=True)
    return c


@texture("block/supply_crate_side")
def supply_crate_side(rng):
    """侧面：一根斜撑，右上一块印着编号的白色标签。"""
    c = crate_shell(rng)
    for i in range(2, 14):
        c.set(i, i, GRAPHITE, 5, glint=True)
        c.set(i, i + 1, GRAPHITE, 1, glint=True) if i + 1 < 14 else None
    c.panel(8, 3, 12, 5, WHITE, 4)
    c.hline(9, 11, 4, GRAPHITE, 2)
    c.set(10, 4, WHITE, 4)
    return c


@texture("block/supply_crate_top")
def supply_crate_top(rng):
    """顶面：中间一道提手槽（凹进去，下沿受光）。"""
    c = crate_shell(rng)
    c.hline(5, 10, 7, GRAPHITE, 0)
    c.hline(5, 10, 8, STEEL, 4)
    c.set(4, 7, STEEL, 3)
    c.set(11, 7, STEEL, 3)
    return c


# ---- 先驱者石碑：发紫的黑石头，刻着发紫光的大符文

MONO_GLYPHS = [
    ["..#..", ".#.#.", "#...#", ".###.", "..#.."],
    ["#.#.#", ".###.", "..#..", ".#.#.", "#...#"],
    [".###.", "#...#", "#.#.#", "#...#", ".###."],
    ["#####", "..#..", ".###.", "#.#.#", "..#.."],
]


def ancient_stone(rng: random.Random, r=None, base: int = 2) -> Canvas:
    """先驱者的石头：细密的凿痕（两三格的横向暗纹），几块亮一级的斑。"""
    r = r or ANCIENT
    c = Canvas()
    f = field(rng, (8, 16), (0.6, 0.4))
    for y in range(16):
        for x in range(16):
            c.set(x, y, r, base + (1 if f[y][x] > 0.72 else 0) - (1 if f[y][x] < 0.22 else 0))
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randint(2, 4)):
            c.set((x + i) % 16, y, r, base - 1)
    despeckle(c, 1)
    return c


def carve(c: Canvas, pattern, ox: int, oy: int, glow, r=None, halo: bool = True):
    """刻进石头里的发光笔画：笔画用发光色，周围一圈石头被照亮（石头色阶亮两级）。"""
    r = r or ANCIENT
    lit = {(ox + i, oy + j) for j, row in enumerate(pattern) for i, ch in enumerate(row) if ch == "#"}
    if halo:
        for x, y in lit:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in lit and c.get(*q) and c.get(*q)[0] is r:
                    c.set(q[0], q[1], r, 4, glint=True)
    for x, y in lit:
        c.set(x, y, glow, 5 if (x + y) % 4 == 0 else 4, glint=True)
    return lit


@texture("block/monolith_side", refs=("block/obsidian", "block/crying_obsidian"))
def monolith_side(rng):
    """石碑侧面：几格高的石柱，上下接得上；两列大符文错开排。"""
    c = ancient_stone(random.Random(71))
    for k, (ox, oy) in enumerate(((2, 1), (9, 4), (2, 9), (9, 11))):
        carve(c, MONO_GLYPHS[k], ox, oy, VIOLET_GLOW)
    return c


@texture("block/monolith_top")
def monolith_top(rng):
    """石碑顶：一圈刻出来的环，正中一颗亮的。"""
    c = ancient_stone(random.Random(72))
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 5.0):
        d = math.hypot(x - cx, y - cy)
        if d > 4.0:
            c.set(x, y, VIOLET_GLOW, 3 if x + y < 15 else 2, glint=True)
        elif d > 3.0:
            c.set(x, y, ANCIENT, 0)
    for x, y in c.disc_cells(cx, cy, 1.2):
        c.set(x, y, VIOLET_GLOW, 5, glint=True)
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    return c


# ---- 科技储藏箱：青灰的金属壳，发绿光的接缝，顶上一个六边形标记

@texture("block/tech_cache_side", refs=("block/vault_side_off",))
def tech_cache_side(rng):
    c = shell(rng, CACHE, 3)
    for y in (5, 10):
        for x in range(1, 15):
            if (x + (2 if y == 10 else 0)) % 5:
                c.set(x, y, GREEN_GLOW, 3, glint=True)
            else:
                c.set(x, y, CACHE, 1)
        c.hline(1, 14, y + 1, CACHE, 4)
    c.vline(7, 6, 9, CACHE, 1)
    c.vline(8, 6, 9, CACHE, 5)
    c.set(12, 7, GREEN_GLOW, 5, glint=True)
    c.set(12, 8, GREEN_GLOW, 3, glint=True)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, CACHE, 3)
    return c


@texture("block/tech_cache_top")
def tech_cache_top(rng):
    c = shell(rng, CACHE, 3)
    cx = cy = 7.5
    for x, y in c.cells(1, 1, 14, 14):
        dx, dy = abs(x - cx), abs(y - cy)
        hexd = max(dx * 0.866 + dy * 0.5, dy)
        if abs(hexd - 4.4) < 0.55:
            c.set(x, y, GREEN_GLOW, 3, glint=True)
        elif hexd < 1.6:
            c.set(x, y, GREEN_GLOW, 5, glint=True)
        elif hexd < 4.0:
            c.set(x, y, CACHE, 2 if x + y < 15 else 4)
    return c


# ---- 冒烟的残骸：烧黑的船体板，裂缝里透出余火

def wreck(rng: random.Random, embers: int) -> Canvas:
    c = Canvas()
    c.fill(GRAPHITE, 2)
    f = field(rng, (4, 8), (0.6, 0.4))
    for x, y in c.cells():
        if f[y][x] > 0.78:
            c.set(x, y, RUST, 1)
        elif f[y][x] < 0.35:
            c.set(x, y, GRAPHITE, 1)
    c.hline(0, 15, 7, GRAPHITE, 0)
    c.hline(0, 15, 8, GRAPHITE, 4)
    c.hline(0, 14, 0, GRAPHITE, 4)
    c.vline(0, 1, 14, GRAPHITE, 3)
    c.hline(1, 15, 15, GRAPHITE, 0)
    c.vline(15, 1, 15, GRAPHITE, 0)
    for x, y in ((2, 2), (13, 2), (2, 12), (13, 12)):
        c.rivet(x, y, GRAPHITE, 2)
    # 裂缝：随机游走，两头暗红，中间最亮
    for _ in range(embers):
        pts = walk(rng, rng.randrange(2, 13), rng.randrange(2, 13), rng.randint(4, 7),
                   dirs=((1, 0), (1, 1), (0, 1), (1, -1)))
        n = len(pts)
        for i, (x, y) in enumerate(pts):
            t = 1 - abs(i - (n - 1) / 2) / ((n - 1) / 2 + 0.01)
            if 0 < x < 15 and 0 < y < 15:
                c.set(x, y, ORANGE_GLOW, 2 + round(t * 3), glint=True)
    return c


@texture("block/smoldering_wreck_side", refs=("block/magma", "block/blackstone"))
def smoldering_wreck_side(rng):
    return wreck(random.Random(91), 3)


@texture("block/smoldering_wreck_top")
def smoldering_wreck_top(rng):
    return wreck(random.Random(92), 5)


# ---- 信号增强器：底座、机身、天线，天线顶上一颗粉色的灯

@texture("block/signal_booster_base", tile=False, refs=("block/lodestone_side",))
def signal_booster_base(rng):
    """底座：顶面、底面用 1–14 格；侧面只截第 12–15 行，那里画一道粉色的警示条。"""
    c = shell(rng, STEEL, 3)
    for x, y in ((2, 2), (12, 2), (2, 9), (12, 9)):
        c.rivet(x, y, STEEL, 3)
    # 上面那节机身压着的地方（4–11 格）凹一圈
    c.recess(4, 4, 11, 11, STEEL, 3)
    c.hline(1, 14, 12, STEEL, 5)
    for x in range(1, 15):
        c.set(x, 13, PINK, 4 if x % 4 else 2)
    c.hline(1, 14, 14, STEEL, 2)
    return c


@texture("block/signal_booster_body", tile=False)
def signal_booster_body(rng):
    """机身：模型取 4–11 列、5–11 行。浅灰的机壳，一小块屏幕上一排粉色的信号格，下面一排散热缝。"""
    c = shell(rng, STEEL, 5, clusters=3)
    c.recess(4, 5, 11, 9, SCREEN, 1)
    c.rect(5, 6, 10, 8, SCREEN, 0)
    for i, (x, h) in enumerate(zip(range(6, 10), (1, 2, 3, 3))):
        for y in range(9 - h, 9):
            c.set(x, y, MAGENTA_GLOW, 4 if i < 3 else 2, glint=True)
    for x in range(4, 12, 2):
        c.set(x, 11, STEEL, 2)
        c.set(x + 1, 11, STEEL, 6)
    c.hline(4, 11, 10, STEEL, 4)
    return c


@texture("block/signal_booster_antenna", tile=False)
def signal_booster_antenna(rng):
    """天线：一根细杆，每个面取 1.5×2.5 格。四格一组画成圆杆（左亮右暗），每四行一道接头。"""
    c = Canvas()
    for x, y in c.cells():
        lv = (6, 5, 4, 2)[x % 4]
        if y % 4 == 3:
            lv = 2 if x % 4 == 3 else 3
        c.set(x, y, STEEL, lv)
    return c


@texture("block/signal_booster_tip", tile=False)
def signal_booster_tip(rng):
    """天线顶的灯（自发光的元素）：取正中 3×3 格，粉色的光，中间最亮。"""
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        c.set(x, y, MAGENTA_GLOW, max(2, 5 - round(d * 0.7)))
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/navigation_data", refs=("item/map", "item/filled_map"))
def navigation_data(rng):
    """导航数据：一张粉色边的数据卡，卡面一小片星图，一条虚线航线连到亮的目的地，底边一排金手指。"""
    c = Canvas()
    c.draw([
        "................",
        "................",
        "...5444444443...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...4000000002...",
        "...3000000002...",
        "...2222222221...",
        "................",
    ], {"012345": PINK})
    # 卡面：深色
    for x, y in c.cells(4, 3, 11, 12):
        if c.get(x, y) and c.get(x, y)[0] is PINK and c.level(x, y) == 0:
            c.set(x, y, SCREEN, 1 if (x + y) % 5 else 0)
    for x, y in ((5, 4), (10, 4), (8, 6), (5, 9), (10, 10), (7, 11)):
        c.set(x, y, WHITE_GLOW, 3, glint=True)
    for i, (x, y) in enumerate(((5, 11), (6, 10), (7, 9), (8, 8), (9, 7))):
        if i % 2 == 0:
            c.set(x, y, MAGENTA_GLOW, 4, glint=True)
    c.set(10, 6, WHITE_GLOW, 5, glint=True)
    c.set(11, 6, MAGENTA_GLOW, 4, glint=True)
    c.set(10, 5, MAGENTA_GLOW, 4, glint=True)
    for x in range(4, 11, 2):
        c.set(x, 13, BRASS, 5, glint=True)
        c.set(x + 1, 13, BRASS, 2, glint=True)
    return c


# ---- 前哨站：加固外壳、加固门、设施终端、观测终端（ADR-0026）

BLUE_SKY = register(ramp("planet_night", 262, 0.060, n=5, lo=0.16, hi=0.42, shift=8))   # 屏幕上的夜空、镜头的深处

@texture("block/reinforced_plating", refs=("block/iron_block", "block/heavy_core"))
def reinforced_plating(rng):
    """装甲钢板：深枪灰的厚板，像砌砖一样错缝（上半块的竖缝在正中，下半块在两边，拼起来正好错开）；
    拼缝是暗的、下沿受光，缝两边一排铆钉，板面几道划痕。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 6, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 3, 2)
    c.hline(0, 15, 0, GRAPHITE, 4)
    c.hline(0, 15, 7, GRAPHITE, 1)
    c.hline(0, 15, 8, GRAPHITE, 4)
    c.hline(0, 15, 15, GRAPHITE, 1)
    c.vline(7, 1, 6, GRAPHITE, 1)
    c.vline(8, 1, 6, GRAPHITE, 4)
    c.vline(15, 9, 14, GRAPHITE, 1)
    c.vline(0, 9, 14, GRAPHITE, 4)
    for x in (2, 5, 10, 13):
        c.rivet(x, 3, GRAPHITE, 3)
    for x in (3, 7, 11):
        c.rivet(x, 11, GRAPHITE, 3)
    for i in range(4):
        c.set(9 + i, 13 - i // 2, GRAPHITE, 5, glint=True)
    return c


def door_panel(rng: random.Random) -> Canvas:
    c = Canvas()
    c.fill(STEEL, 3)
    c.clusters(rng, 1, 1, 14, 14, -1, 5, 2)
    c.hline(0, 14, 0, STEEL, 5)
    c.vline(0, 1, 14, STEEL, 5)
    c.hline(1, 15, 15, STEEL, 1)
    c.vline(15, 1, 15, STEEL, 1)
    return c


def door_rib(c: Canvas, y: int):
    """一道横的加强筋：上沿亮、中间、下沿暗，下面一道影子。"""
    c.hline(1, 14, y, STEEL, 5)
    c.hline(1, 14, y + 1, STEEL, 4)
    c.hline(1, 14, y + 2, STEEL, 1)


@texture("block/reinforced_door_lower", refs=("block/iron_door_bottom",))
def reinforced_door_lower(rng):
    """防爆门下半：两道加强筋，筋上两颗螺栓，底部一道黄黑相间的警示斜条。"""
    c = door_panel(rng)
    door_rib(c, 2)
    door_rib(c, 6)
    for x in (2, 12):
        c.rivet(x, 5, STEEL, 3)
    c.hline(1, 14, 10, STEEL, 1)
    for x, y in c.cells(1, 11, 14, 14):
        if (x + y) % 6 < 3:
            c.set(x, y, HAZARD, 4 if y == 11 else (2 if y == 14 else 3))
        else:
            c.set(x, y, GRAPHITE, 2 if y == 11 else 0 if y == 14 else 1)
    return c


@texture("block/reinforced_door_upper", refs=("block/iron_door_top",))
def reinforced_door_upper(rng):
    """防爆门上半：顶上一排状态灯（只亮一盏红的），一条红色的观察窗灯带，一道加强筋。"""
    c = door_panel(rng)
    c.hline(2, 13, 2, GRAPHITE, 1)
    for x in range(3, 13, 3):
        if x == 6:
            c.set(x, 2, RED_GLOW, 5, glint=True)
            c.set(x + 1, 2, RED_GLOW, 3, glint=True)
        else:
            c.set(x, 2, SCREEN, 2, glint=True)
    c.hline(2, 13, 5, STEEL, 1)
    for x in range(2, 14):
        c.set(x, 6, RED_GLOW, 4 if x % 3 else 5)
        c.set(x, 7, RED_GLOW, 2)
    c.vline(2, 5, 7, STEEL, 1)
    c.hline(2, 13, 8, STEEL, 5)
    door_rib(c, 10)
    for x in (2, 12):
        c.rivet(x, 13, STEEL, 3)
    return c


def terminal_casing(rng: random.Random) -> Canvas:
    c = shell(rng, GRAPHITE, 3, clusters=4)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, GRAPHITE, 3)
    return c


def terminal_screen(c: Canvas, r, lv: int):
    """屏幕：凹进去，上沿一道深影，下沿受光。"""
    c.rect(2, 2, 13, 10, r, lv)
    c.hline(2, 13, 1, GRAPHITE, 0)
    c.hline(2, 13, 11, GRAPHITE, 5)


def terminal_keys(c: Canvas, accent):
    for x in (3, 5, 7, 9):
        c.set(x, 13, GRAPHITE, 6, glint=True)
        c.set(x, 14, GRAPHITE, 1, glint=True)
    c.set(11, 13, accent, 5, glint=True)
    c.set(12, 13, accent, 3, glint=True)
    c.set(11, 14, accent, 2, glint=True)
    c.set(12, 14, accent, 2, glint=True)


def terminal_side(rng: random.Random, accent) -> Canvas:
    c = terminal_casing(rng)
    c.vent(3, 3, 12, 12, GRAPHITE, 3)
    for y in range(3, 13):
        c.set(14, y, accent, 4 if y % 4 == 0 else 2, glint=True)
    return c


@texture("block/facility_terminal_front", refs=("block/crafter_north_crafting",))
def facility_terminal_front(rng):
    """设施终端：深灰机身，暗红底的屏幕上三个琥珀色的模块格，中间那个选中，上面一行红字；下面一排键。"""
    c = terminal_casing(random.Random(2611))
    terminal_screen(c, RED, 0)
    for x0 in (3, 7, 11):
        chosen = x0 == 7
        lv = 4 if chosen else 2
        for x in range(x0, x0 + 3):
            c.set(x, 4, AMBER_GLOW, lv, glint=True)
            c.set(x, 8, AMBER_GLOW, lv, glint=True)
        for y in range(4, 9):
            c.set(x0, y, AMBER_GLOW, lv, glint=True)
            c.set(x0 + 2, y, AMBER_GLOW, lv, glint=True)
        for y in range(5, 8):
            c.set(x0 + 1, y, AMBER_GLOW if chosen else RED, 2 if chosen else 1, glint=True)
    c.set(8, 6, AMBER_GLOW, 5, glint=True)
    for x in range(3, 11, 2):
        c.set(x, 2, RED_GLOW, 3, glint=True)
    terminal_keys(c, AMBER_GLOW)
    return c


@texture("block/facility_terminal_side")
def facility_terminal_side(rng):
    return terminal_side(random.Random(2612), AMBER_GLOW)


@texture("block/facility_terminal_top")
def facility_terminal_top(rng):
    """顶上一个齿轮标志：琥珀色，左上受光。"""
    c = terminal_casing(random.Random(2613))
    cx = cy = 7.5
    for x, y in c.cells(2, 2, 13, 13):
        d = math.hypot(x - cx, y - cy)
        a = math.atan2(y - cy, x - cx)
        if d <= 1.6:
            c.set(x, y, GRAPHITE, 1)
        elif d <= 3.4 or (d <= 4.7 and math.cos(a * 8) > 0.2):
            c.set(x, y, AMBER, 4 + (1 if (x - cx) + (y - cy) < -2 else 0) - (1 if (x - cx) + (y - cy) > 2 else 0))
    return c


@texture("block/observatory_terminal_front", refs=("block/crafter_north",))
def observatory_terminal_front(rng):
    """观测终端：屏幕是深蓝的星空，几颗暗星和亮星，四个青色的角框住最亮的一颗。"""
    c = terminal_casing(random.Random(2621))
    terminal_screen(c, BLUE_SKY, 0)
    for x, y, lv in ((3, 3, 2), (4, 8, 3), (3, 6, 1), (13, 4, 3), (13, 9, 2), (6, 9, 1), (10, 3, 1), (12, 7, 1)):
        c.set(x, y, WHITE_GLOW, lv, glint=True)
    c.set(9, 6, WHITE_GLOW, 5, glint=True)
    for sx in (-1, 1):
        for sy in (-1, 1):
            c.set(9 + 3 * sx, 6 + 3 * sy, CYAN_GLOW, 4, glint=True)
            c.set(9 + 2 * sx, 6 + 3 * sy, CYAN_GLOW, 3, glint=True)
            c.set(9 + 3 * sx, 6 + 2 * sy, CYAN_GLOW, 3, glint=True)
    terminal_keys(c, CYAN_GLOW)
    return c


@texture("block/observatory_terminal_side")
def observatory_terminal_side(rng):
    return terminal_side(random.Random(2622), CYAN_GLOW)


@texture("block/observatory_terminal_top")
def observatory_terminal_top(rng):
    """顶上像望远镜的镜头：一圈圈同心的镜片和镜筒，正中深色，左上一点反光。"""
    c = terminal_casing(random.Random(2623))
    cx = cy = 7.5
    for x, y in c.cells(2, 2, 13, 13):
        d = math.hypot(x - cx, y - cy)
        lit = (x - cx) + (y - cy) < -1
        if d <= 1.6:
            c.set(x, y, BLUE_SKY, 0)
        elif d <= 2.6:
            c.set(x, y, GLASS, 3 if lit else 1)
        elif d <= 3.6:
            c.set(x, y, GRAPHITE, 1)
        elif d <= 4.7:
            c.set(x, y, GLASS, 2 if lit else 0)
        elif d <= 5.6:
            c.set(x, y, GRAPHITE, 4 if lit else 2)
    c.set(6, 6, WHITE_GLOW, 5, glint=True)
    return c


# ---- 知识石（ADR-0027 §五）：偏蓝的深灰板岩，凿出来的框，框里一行行发光的小字

LETTERS = [
    ["#.", "##", ".#"], ["##", "#.", "##"], [".#", "##", "#."], ["#.", "#.", "##"], ["##", ".#", "#."],
    ["#.", ".#", "#."], ["##", "..", "##"], [".#", "#.", ".#"], ["#.", "##", "#."],
]


def slate(rng: random.Random) -> Canvas:
    """板岩：成片的两三级，几道横的细纹。"""
    c = Canvas()
    f = field(rng, ((8, 16), 16), (0.6, 0.4))
    for x, y in c.cells():
        c.set(x, y, SLATE, 2 + (1 if f[y][x] > 0.66 else 0) - (1 if f[y][x] < 0.2 else 0))
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randint(2, 4)):
            c.set((x + i) % 16, y, SLATE, 1)
    despeckle(c, 1)
    return c


def slate_frame(c: Canvas, top: bool = True, bottom: bool = True):
    """凿出来的框：外圈一道亮边（左、上）或暗边（右、下），里面一道暗槽。上下接着的两半不画接缝那边。"""
    for i in range(16):
        c.set(0, i, SLATE, 4)
        c.set(1, i, SLATE, 0)
        c.set(14, i, SLATE, 0)
        c.set(15, i, SLATE, 1)
    if top:
        for i in range(16):
            c.set(i, 0, SLATE, 4)
            if 1 <= i <= 14:
                c.set(i, 1, SLATE, 0)
    if bottom:
        for i in range(16):
            c.set(i, 15, SLATE, 1)
            if 1 <= i <= 14:
                c.set(i, 14, SLATE, 0)


def script_line(c: Canvas, rng: random.Random, y: int):
    """一行字：框里 3–12 列，字和字之间空一格，词和词之间空两格。"""
    x = 3 + rng.randrange(2)
    while True:
        n = rng.randrange(1, 4)
        if x + 2 * n + (n - 1) > 13:
            n = (13 - x + 1) // 3
        if n <= 0:
            break
        for _ in range(n):
            carve(c, rng.choice(LETTERS), x, y, VIOLET_GLOW, SLATE, halo=False)
            x += 3
        x += 1


@texture("block/knowledge_stone_lower", tile=False, refs=("block/chiseled_deepslate", "block/deepslate_tiles"))
def knowledge_stone_lower(rng):
    c = slate(random.Random(1501))
    slate_frame(c, top=False, bottom=True)
    r = random.Random(1501)
    for y in (2, 6, 10):
        script_line(c, r, y)
    return c


@texture("block/knowledge_stone_upper", tile=False)
def knowledge_stone_upper(rng):
    """上半：顶上刻一个圆环，正中一颗亮点；下面两行字接着下半往下排。"""
    c = slate(random.Random(1502))
    slate_frame(c, top=True, bottom=False)
    cx, cy = 7.5, 5.0
    for x, y in c.disc_cells(cx, cy, 2.7):
        d = math.hypot(x - cx, y - cy)
        if d > 1.8:
            c.set(x, y, VIOLET_GLOW, 4 if (x - cx) + (y - cy) < 0 else 3, glint=True)
    c.set(7, 5, WHITE_GLOW, 5, glint=True)
    c.set(8, 5, VIOLET_GLOW, 5, glint=True)
    r = random.Random(1502)
    for y in (9, 13):
        script_line(c, r, y)
    return c


@texture("block/knowledge_stone_top", tile=False)
def knowledge_stone_top(rng):
    """顶面：凿出来的框，里面两圈环纹，正中一颗亮点。"""
    c = slate(random.Random(1503))
    slate_frame(c)
    cx = cy = 7.5
    for x, y in c.cells(2, 2, 13, 13):
        d = math.hypot(x - cx, y - cy)
        if abs(d - 4.6) < 0.5:
            c.set(x, y, VIOLET_GLOW, 2, glint=True)
        elif abs(d - 2.3) < 0.5:
            c.set(x, y, VIOLET_GLOW, 3, glint=True)
        elif d < 0.8:
            c.set(x, y, VIOLET_GLOW, 5, glint=True)
    return c


# ---- 先驱者星门（ADR-0024）：深紫灰的石砖，十六个符文，拨号台，门里的光

# 十六个符文，按 core.portal.PortalAddress 的编号 0–15（和拨号盘的符文表是同一套）
PORTAL_GLYPHS = [
    [".......", "..###..", ".#...#.", "#.....#", "#######", ".......", ".#.#.#."],  # 日出
    [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],  # 眼
    ["#.....#", ".#...#.", "..#.#..", "...#...", "...#...", "..#.#..", "......."],  # 鸟
    [".......", ".##..##", "#..##..", ".......", ".##..##", "#..##..", "......."],  # 浪
    ["...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."],  # 星
    ["...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."],  # 菱
    ["...#...", "..###..", ".#.#.#.", "...#...", "...#...", "..#.#..", ".#...#."],  # 箭
    ["...#...", "..###..", ".#####.", "#######", "...#...", "...#...", "..###.."],  # 树
    ["..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."],  # 月
    [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#...#", "#.#####", "......."],  # 涡
    ["#######", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#######"],  # 沙漏
    [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.#.#.#", "#.#.#.#", "###.###"],  # 门
    ["......#", ".....##", "....#.#", "...#..#", "..#...#", ".#....#", "#######"],  # 阶
    [".......", ".#...#.", "#.#.#..", "#..#...", "#.#.#..", ".#...#.", "......."],  # 鱼
    ["#.#.#.#", "#.#.#.#", "#.#.#.#", "#######", "...#...", "...#...", "...#..."],  # 根
    ["#######", "##...##", "#.#.#.#", "#..#..#", "#.#.#.#", "##...##", "#######"],  # 封
]


def portal_stone_face(seed: int) -> Canvas:
    """砌成方块的先驱者石：上下两层石砖，下层错开半块；砖缝深，砖的上沿、左沿亮一级，下沿、右沿暗一级。"""
    rng = random.Random(seed)
    c = ancient_stone(rng)
    for x, y in c.cells():
        row = y // 8
        bx = (x + (4 if row else 0)) % 8
        by = y % 8
        if by == 7 or bx == 7:
            c.set(x, y, ANCIENT, 0)
        elif by == 0 or bx == 0:
            c.shift(x, y, 1)
        elif by == 6 or bx == 6:
            c.shift(x, y, -1)
    return c


@texture("block/portal_stone", refs=("block/polished_blackstone_bricks", "block/deepslate_bricks"))
def portal_stone(rng):
    return portal_stone_face(1330)


def _portal_glyph(index: int) -> Canvas:
    """符文石：石砖正中凿一个 9×9 的方槽（上、左沿是影子，下、右沿受光），符文在槽里发淡紫的光。"""
    c = portal_stone_face(1340 + index)
    c.rect(3, 3, 11, 11, ANCIENT, 1)
    c.hline(3, 11, 3, ANCIENT, 0)
    c.vline(3, 3, 11, ANCIENT, 0)
    c.hline(4, 11, 11, ANCIENT, 3)
    c.vline(11, 4, 11, ANCIENT, 3)
    carve(c, PORTAL_GLYPHS[index], 4, 4, VIOLET_GLOW, halo=False)
    return c


for _i in range(16):
    texture(f"block/portal_glyph_{_i}", refs=("block/chiseled_polished_blackstone",) if _i == 0 else ())(
        lambda rng, i=_i: _portal_glyph(i))


@texture("block/portal_dial_top", tile=False, refs=("block/lodestone_top", "block/respawn_anchor_top"))
def portal_dial_top(rng):
    """拨号台顶：一圈凹下去的环，环上十六道发光的刻痕（一个符文一道），正中一颗亮的晶石。"""
    c = portal_stone_face(1360)
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 6.4):
        d = math.hypot(x - cx, y - cy)
        if d > 5.0:
            c.set(x, y, ANCIENT, 1 if (x - cx) + (y - cy) < 0 else 3)
        elif d > 3.1:
            c.set(x, y, ANCIENT, 2)
    for k in range(16):
        a = k * math.tau / 16
        c.set(round(cx + math.cos(a) * 5.6), round(cy + math.sin(a) * 5.6), VIOLET_GLOW, 3, glint=True)
    for x, y in c.disc_cells(cx, cy, 2.2):
        d = math.hypot(x - cx, y - cy)
        c.set(x, y, VIOLET_GLOW, 5 if d < 1.0 else (4 if (x - cx) + (y - cy) < 0 else 2), glint=True)
    c.set(7, 7, WHITE_GLOW, 5, glint=True)
    return c


@texture("block/portal_dial_side", tile=False)
def portal_dial_side(rng):
    """拨号台侧面：上沿一道发光的符文带，中间一道凹槽。"""
    c = portal_stone_face(1361)
    c.hline(0, 15, 1, ANCIENT, 0)
    for x in range(16):
        c.set(x, 2, VIOLET_GLOW, 4 if x % 3 == 0 else 2, glint=True)
    c.hline(0, 15, 3, ANCIENT, 3)
    c.hline(4, 11, 9, ANCIENT, 0)
    c.hline(4, 11, 10, ANCIENT, 3)
    return c


@texture("block/portal_field", frames=32, refs=("block/nether_portal", "block/end_gateway"))
def portal_field(rng):
    """门开着时环里的光：32 帧，蓝紫色的涡旋慢慢转，半透明；亮斑成团，越亮越不透。
    贴图平铺在整片光上，只用周期为 16 的波，接缝看不出来。"""
    steps = [(VIOLET_GLOW, 1), (VIOLET_GLOW, 2), (VIOLET_GLOW, 3), (BLUE_GLOW, 3), (BLUE_GLOW, 4), (BLUE_GLOW, 5)]
    c = Canvas(16, 16 * 32)
    for f in range(32):
        t = f / 32 * math.tau
        for y in range(16):
            for x in range(16):
                u, v = x / 16 * math.tau, y / 16 * math.tau
                wu = u + 1.3 * math.sin(v + t)
                wv = v + 1.3 * math.cos(u - t)
                w = 0.55 * math.sin(wu) * math.cos(wv) + 0.45 * math.sin(wu + wv + t)
                k = (0.5 + 0.5 * w) ** 1.3
                i = min(len(steps) - 1, int(k * len(steps)))
                r, lv = steps[i]
                c.set(x, y + f * 16, r, lv, alpha=150 + 18 * i)
    return c


@texture("gui/portal/glyphs", tile=False)
def portal_glyphs_gui(rng):
    """拨号盘的符文表：16 个，每个 16×16，笔画放大两倍；界面里乘颜色，所以画成近白的灰，
    笔画的下沿、右沿暗两级，像刻出来的。"""
    c = Canvas(256, 16)
    for index, pattern in enumerate(PORTAL_GLYPHS):
        cells = {(i, j) for j, row in enumerate(pattern) for i, ch in enumerate(row) if ch == "#"}
        for i, j in cells:
            for dx in range(2):
                for dy in range(2):
                    edge = (dy == 1 and (i, j + 1) not in cells) or (dx == 1 and (i + 1, j) not in cells)
                    c.set(index * 16 + 1 + i * 2 + dx, 1 + j * 2 + dy, TINT, 7 if edge else 9, glint=True)
    return c


# ---- 水下：甲壳蚌（照砗磲画）、海底密封箱、活珍珠

@texture("block/clam_top", tile=False, refs=("block/brain_coral_block", "block/tube_coral_block"))
def clam_top(rng):
    """壳顶：从后面的铰合处（下边中间）往前放射的粗肋，肋凸起受光、肋沟深；一圈圈浅色的生长纹，
    几点藤壶和绿藻。模型用 2–13 列、3–12 行。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x + 0.5 - 8, 17 - (y + 0.5)
        a = math.atan2(dx, dy)
        r = math.hypot(dx, dy)
        rib = math.cos(a * 13)
        # 肋：受光的一侧（左）亮两级，肋沟暗一级
        lv = 3 + (2 if rib > 0.55 and dx < 2 else (1 if rib > 0.1 else (-1 if rib < -0.5 else 0)))
        # 生长纹：隔几格一道亮线
        if abs(r - 7.5) < 0.5 or abs(r - 11.5) < 0.5:
            lv += 1
        c.set(x, y, CLAM, lv)
    despeckle(c, 1, tile=False)
    for _ in range(4):
        x, y = rng.randrange(3, 12), rng.randrange(3, 11)
        c.set(x, y, BONE, 5, glint=True)
        c.set(x + 1, y, BONE, 3, glint=True)
        c.set(x, y + 1, BONE, 2, glint=True)
    for _ in range(6):
        x, y = rng.randrange(2, 14), rng.randrange(3, 7)
        c.set(x, y, MOSS, 3)
        c.set(x + 1, y, MOSS, 2)
    return c


@texture("block/clam_side", tile=False)
def clam_side(rng):
    """壳侧：上片壳占第 4–6 行，下片壳第 8–10 行；壳边一道波浪形的唇，横的生长纹，下片上挂着绿藻。"""
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, CLAM, 3 + (1 if y % 3 == 0 else 0))
    # 上片：顶上一行亮，下沿（第 6 行）是波浪形的暗唇
    for x in range(16):
        c.set(x, 4, CLAM, 5)
        c.set(x, 6 if (x // 2) % 2 else 5, CLAM, 1)
    # 下片：上沿（第 8 行）波浪形的亮唇，往下渐暗
    for x in range(16):
        c.set(x, 8 if (x // 2) % 2 else 9, CLAM, 5)
        c.set(x, 10, CLAM, 2)
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(9, 11), MOSS, 3)
    return c


@texture("block/clam_inner", tile=False)
def clam_inner(rng):
    """壳里：外面一圈带晕彩的珍珠层，中间淡橙色的外套膜，膜上一道道褶。"""
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        a = math.atan2(y - 8, x - 8)
        if d < 5.0:
            c.set(x, y, ORANGE, 4 + (1 if math.sin(d * 2.4 + a) > 0.3 else 0) - (1 if d > 4.0 else 0))
        else:
            c.set(x, y, NACRE, 3 + round(math.sin(a * 3) * 1.4) - (1 if d > 6.5 else 0))
    c.set(6, 6, ORANGE, 6, glint=True)
    return c


SEAPLATE = register(ramp("planet_seaplate", 190, 0.040, n=7, lo=0.24, hi=0.72, shift=12))  # 泡在海里的青灰钢板


def sea_crate(rng: random.Random) -> Canvas:
    """密封箱的一面：青灰的钢板，凸起的边，四角铆钉；锈斑和绿藻成团，越往下藻越多。"""
    c = shell(rng, SEAPLATE, 3, clusters=4)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.rivet(x, y, SEAPLATE, 3)
    f = field(rng, (4, 8), (0.6, 0.4))
    for x, y in c.cells(1, 1, 14, 14):
        if f[y][x] > 0.82:
            c.set(x, y, RUST, 2 if f[y][x] < 0.9 else 3)
        elif f[y][x] < 0.3 and y > 8:
            c.set(x, y, MOSS, 2 if f[y][x] > 0.15 else 1)
    return c


@texture("block/sealed_crate_front", refs=("block/vault_front_off", "block/sea_lantern"))
def sealed_crate_front(rng):
    """正面：一圈封条亮着微弱的青光，中间一个锁盘。"""
    c = sea_crate(random.Random(2601))
    for x in range(3, 13):
        c.set(x, 4, CYAN_GLOW, 3, glint=True)
        c.set(x, 11, CYAN_GLOW, 2, glint=True)
    for y in range(4, 12):
        c.set(3, y, CYAN_GLOW, 3, glint=True)
        c.set(12, y, CYAN_GLOW, 2, glint=True)
    c.panel(6, 6, 9, 9, GRAPHITE, 2)
    c.set(7, 7, CYAN_GLOW, 5, glint=True)
    c.set(8, 8, GRAPHITE, 0)
    return c


@texture("block/sealed_crate_side")
def sealed_crate_side(rng):
    """侧面：中间一道合缝，缝里一截封条。"""
    c = sea_crate(random.Random(2602))
    c.hline(1, 14, 7, SEAPLATE, 1)
    c.hline(1, 14, 8, SEAPLATE, 5)
    for x in range(5, 11):
        c.set(x, 7, CYAN_GLOW, 2, glint=True)
    return c


@texture("block/sealed_crate_top")
def sealed_crate_top(rng):
    """顶面：一个 X 形的加强筋，交叉处一点青光。"""
    c = sea_crate(random.Random(2603))
    for i in range(3, 13):
        c.set(i, i, SEAPLATE, 5, glint=True)
        c.set(15 - i, i, SEAPLATE, 5, glint=True)
        c.set(i + 1, i, SEAPLATE, 1, glint=True) if i < 12 else None
    c.set(7, 7, CYAN_GLOW, 4, glint=True)
    c.set(8, 8, CYAN_GLOW, 3, glint=True)
    return c


@texture("item/living_pearl", refs=("item/ender_pearl", "item/heart_of_the_sea"))
def living_pearl(rng):
    """活珍珠：奶白的圆珠，左上一块高光，右下暗；边上一圈晕彩，上边泛粉、下边泛青。"""
    c = Canvas()
    cx, cy = 7.5, 8.0
    disc = c.disc_cells(cx, cy, 5.6)
    for x, y in disc:
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy) / 6.0
        lit = -(dx + dy) / 8.0
        c.set(x, y, NACRE, max(2, min(5, 4 + round(lit * 2.0 - d * d * 1.4))))
    for x, y in disc:
        dx, dy = x - cx, y - cy
        if 3.8 < math.hypot(dx, dy):
            if dx - dy > 3.5:
                c.set(x, y, PINK, 5)
            elif dy - dx > 3.5:
                c.set(x, y, ICE, 4)
    # 描边：冷灰，受光的左上浅一级
    inside = set(disc)
    for x, y in disc:
        if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            c.set(x, y, ICE, 1 if x + y < 15 else 0)
    c.set(5, 5, WHITE_GLOW, 5, glint=True)
    c.set(6, 5, NACRE, 5, glint=True)
    c.set(5, 6, NACRE, 5, glint=True)
    c.set(10, 11, PINK, 4, glint=True)
    return c


# ---- 化石和废料

@texture("item/shell_fossil", refs=("item/nautilus_shell", "item/flint"))
def shell_fossil(rng):
    """螺壳化石（照菊石）：一圈圈盘起来的壳，壳上一道道横肋，螺缝深；石头的米灰色，左上受光。"""
    c = Canvas()
    cx = cy = 7.5
    disc = c.disc_cells(cx, cy, 6.2)
    for x, y in disc:
        dx, dy = x - cx, y - cy
        c.set(x, y, BONE, 3 + round(-(dx + dy) / 6.0 * 1.4))
    # 螺缝：一条往外越绕越宽的螺线
    for i in range(200):
        t = i / 199 * 2.7 * math.pi
        r = 0.6 + t * 0.66
        if r > 6.0:
            break
        x, y = round(cx + math.cos(t) * r), round(cy + math.sin(t) * r)
        if (x, y) in disc:
            c.set(x, y, BONE, 1, glint=True)
    # 最外一圈的横肋：亮一级的短线
    for k in range(14):
        t = k / 14 * math.tau
        x, y = round(cx + math.cos(t) * 5.3), round(cy + math.sin(t) * 5.3)
        if (x, y) in disc and c.level(x, y) > 1:
            c.set(x, y, BONE, c.level(x, y) + 1, glint=True)
    c.set(7, 7, BONE, 0)
    c.outline()
    c.set(4, 4, BONE, 6, glint=True)
    return c


@texture("item/arthropod_fossil", refs=("item/flint", "item/disc_fragment_5"))
def arthropod_fossil(rng):
    """节肢动物化石（照三叶虫）：一块深灰的石板，上面的三叶虫分成头、胸节、尾三段，中间一条隆起的轴，一对眼。"""
    c = Canvas()
    for x, y in c.cells(2, 1, 13, 14):
        if (x in (2, 13) and y in (1, 14)) or (x == 13 and y == 2):
            continue
        c.set(x, y, BASALT, 2 + (1 if (x * 3 + y * 5) % 7 == 0 else 0))
    c.hline(3, 12, 1, BASALT, 4)
    c.vline(2, 2, 13, BASALT, 3)
    cx, cy = 7.5, 7.8
    for x, y in c.cells():
        u, v = (x - cx) / 4.4, (y - cy) / 5.8
        if u * u + v * v > 1:
            continue
        lv = 3
        if y <= 4:
            lv = 4 if abs(x - cx) <= 1.5 else 3          # 头
        elif y == 5 or (6 <= y <= 11 and y % 2 == 1):
            lv = 1                                         # 胸节之间的缝
        if abs(x - cx) <= 1.0 and y > 4 and lv > 1:
            lv += 1                                        # 轴
        elif abs(x - cx) <= 1.8 and y > 4 and lv > 1:
            lv -= 1                                        # 轴两边的沟
        if x > cx + 2 and lv > 1:
            lv -= 1                                        # 右边背光
        c.set(x, y, SAND, lv)
    c.set(5, 4, BASALT, 1)
    c.set(10, 4, BASALT, 1)
    c.outline()
    c.set(6, 3, SAND, 5, glint=True)
    return c


@texture("item/skull_fossil", refs=("item/bone", "item/skeleton_skull"))
def skull_fossil(rng):
    """头骨化石（照鱼龙）：斜着放的侧面头骨，圆的颅骨在左下，长吻伸向右上，一个大眼眶，一排牙；骨头米黄带褐斑。"""
    c = Canvas()
    ang = math.radians(35)
    ca, sa = math.cos(ang), math.sin(ang)
    ox, oy = 4.2, 11.0
    for x, y in c.cells():
        u = (x - ox) * ca - (y - oy) * sa
        v = (x - ox) * sa + (y - oy) * ca
        if u < -3.4 or u > 13.2:
            continue
        if u < 1.5:
            inside = (u / 3.4) ** 2 + (v / 3.4) ** 2 <= 1
        else:
            inside = -3.0 + (u - 1.5) * 0.2 <= v <= 2.6 - (u - 1.5) * 0.17
        if inside:
            c.set(x, y, BONE, 6 - round((v + 3) / 6 * 2))
    despeckle(c, 1, tile=False)

    def at(u, v):
        return round(ox + u * ca + v * sa), round(oy - u * sa + v * ca)
    for u, v in ((-0.6, -0.8), (0.4, -0.8), (-0.6, 0.2), (0.4, 0.2), (1.2, -0.3)):
        c.set(*at(u, v), SOIL, 1, glint=True)
    for i in range(10):
        u = 2.0 + i
        c.set(*at(u, 1.0 - i * 0.06), SOIL, 2, glint=True)
        if i % 2 == 0:
            c.set(*at(u, 0.2 - i * 0.06), BONE, 6, glint=True)
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        if c.get(x, y) and c.get(x, y)[0] is BONE:
            c.set(x, y, SOIL, 4, glint=True)
    c.outline(outside=True)
    return c


@texture("item/salvaged_scrap", refs=("item/netherite_scrap", "item/iron_nugget"))
def salvaged_scrap(rng):
    """打捞的废料：一块折弯了的钢板（折线左边朝光、右边背光，边上锈成一片片），两个铆钉孔；
    半块电路板压在右上，一截铜线从底下绕出来。"""
    c = Canvas()
    for x, y in c.cells(2, 4, 10, 12):
        yy = y - (1 if x > 6 else 0)
        c.set(x, yy, STEEL, 5 if x <= 6 else 3)
    c.hline(2, 6, 4, STEEL, 6)
    c.hline(7, 10, 3, STEEL, 4)
    c.vline(6, 4, 12, STEEL, 6)
    f = field(rng, (4, 8), (0.6, 0.4))
    for x, y in c.cells(2, 3, 10, 12):
        px = c.get(x, y)
        edge = x in (2, 10) or y in (3, 4, 11, 12)
        if px and (f[y][x] > 0.68 or (edge and f[y][x] > 0.45)):
            c.set(x, y, RUST, 3)
    for x, y in ((3, 6), (9, 9)):
        c.set(x, y, GRAPHITE, 0)
        c.set(x + 1, y + 1, STEEL, 6)
    # 半块电路板：缺一个角
    c.rect(8, 1, 13, 5, GREEN, 2)
    c.hline(8, 12, 1, GREEN, 4)
    c.vline(8, 2, 5, GREEN, 3)
    c.clear(13, 1)
    c.clear(13, 2)
    c.outline()
    for x, y in ((9, 2), (11, 2), (12, 4), (10, 4)):
        c.set(x, y, BRASS, 5, glint=True)
    # 铜线：描边之后画，免得被压暗；左边亮、右边一格暗
    for i in range(9):
        t = i / 8
        x, y = round(11 + 3 * math.sin(t * 3.0)), 6 + i
        c.set(x, y, COPPER, 5, glint=True)
        c.set(x + 1, y, COPPER, 2, glint=True)
    return c


# ---- 进度界面"寰宇"一页的背景：平铺的深蓝夜空

@texture("gui/advancements/backgrounds/universe", tile=True, refs=("gui/advancements/backgrounds/end",))
def advancement_background(rng):
    """深蓝的夜空，两三级的暗云成团，几颗星：大多是一个像素的暗星，一颗亮的带十字光芒。"""
    c = Canvas()
    f = field(rng, (4, 8), (0.6, 0.4))
    for x, y in c.cells():
        c.set(x, y, BLUE_SKY, 0 if f[y][x] < 0.45 else (1 if f[y][x] < 0.8 else 2))
    despeckle(c)
    for x, y, lv in ((3, 2, 3), (11, 5, 2), (6, 10, 2), (1, 14, 2), (9, 1, 1), (14, 9, 1)):
        c.set(x, y, WHITE_GLOW, lv, glint=True)
    cx, cy = 13, 13
    c.set(cx, cy, WHITE_GLOW, 5, glint=True)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.set(cx + dx, cy + dy, WHITE_GLOW, 2, glint=True)
    return c
