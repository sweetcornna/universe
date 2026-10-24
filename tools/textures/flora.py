"""星球植物和资源作物的贴图（ADR-0031）。

星球植物（planet_*）在游戏里按当前星球的物种染色（PlanetTints）：主体（叶、茎）是染色层 0，
`_detail`（花、果、菌盖、晶尖、孢子）是染色层 1，两层都只画灰度（GRAY），平均亮度 0.8 上下。
两层贴在同一个面上，点缀层压在主体上面；会发光的物种只让点缀层自发光。每种形态 a、b 两个外形，
物品图标用 a。树木方块（原木、树叶、菌盖、花果）同样染色，树叶、花果是镂空的。

资源作物照现实里的原型画、带颜色，长熟和收过两种样子，收过的就是同一株摘掉果子、花或者割短了。

植物都是手画的：茎和叶一个像素一个像素地摆（stroke、ribbon、leaf），花、果、菌盖这类圆东西按法线打光。
光从左上来：叶子朝上、朝左的一面亮，背面和贴地的根部暗；高处亮、低处暗。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import glow, ramp
from palette import (AMBER_GLOW, BONE, CREAM, FLESH, GRAY, HAZARD, ICE, LEAF, MAGENTA_GLOW, MOSS, ORANGE_GLOW,
                     PINK, RED, STONE, WHITE, WOOD, register)
from recipe import texture

G = GRAY


# ---- 画法 ----

def vrun(x: int, y0: int, y1: int) -> list[tuple[int, int]]:
    """竖着的一段像素，从 y0 走到 y1。"""
    s = -1 if y1 < y0 else 1
    return [(x, y) for y in range(y0, y1 + s, s)]


def stroke(c: Canvas, pts, lo: int, hi: int, r=G):
    """沿着像素串从 lo 级渐变到 hi 级（根部暗、梢头亮）。"""
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        c.set(x, y, r, round(lo + (hi - lo) * i / max(1, n - 1)), glint=True)


def ribbon(c: Canvas, pts, lo: int, hi: int, wide: int, inside: int, r=G):
    """带状的叶子：前 wide 个像素是两个像素宽，竖着走时第二列贴在 inside 那侧，横着走时贴在下面。
    下面和右边那列暗一级，左边那列亮一级（光从左上来）。"""
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        lv = round(lo + (hi - lo) * i / max(1, n - 1))
        c.set(x, y, r, lv, glint=True)
        if i < wide:
            a, b = pts[max(0, i - 1)], pts[min(n - 1, i + 1)]
            if abs(b[0] - a[0]) > abs(b[1] - a[1]):
                c.set(x, y + 1, r, lv - 1, glint=True)
            else:
                c.set(x + inside, y, r, lv - 1 if inside > 0 else lv + 1, glint=True)


def zigzag(c: Canvas, pts, lo: int, hi: int, start: int = 2, r=G):
    """锯齿边的羽叶：中轴两侧交替冒出小羽片，左（上）边的亮、右（下）边的暗。"""
    stroke(c, pts, lo, hi, r)
    n = len(pts)
    for i in range(start, n - 1):
        x, y = pts[i]
        a, b = pts[i - 1], pts[i + 1]
        lv = round(lo + (hi - lo) * i / (n - 1))
        if abs(b[1] - a[1]) >= abs(b[0] - a[0]):
            q = (x - 1, y, lv + 1) if i % 2 else (x + 1, y, lv - 1)
        else:
            q = (x, y - 1, lv + 1) if i % 2 else (x, y + 1, lv - 1)
        if c.get(q[0], q[1]) is None:
            c.set(q[0], q[1], r, q[2], glint=True)


def leaf(c: Canvas, x0: float, y0: float, ang: float, length: float, width: float, lo: int, hi: int,
         bend: float = 0.0, r=G):
    """肥厚的尖叶（多肉、球茎的基生叶）：从 (x0, y0) 朝 ang 度（0 朝右、90 朝上）长出去，
    根部最宽、往梢头收尖；bend 让叶片往左手边弯。上面那侧亮一级，下面那侧暗一级。"""
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = dy, -dx  # 左手边（屏幕坐标里）
    if ny > 0:
        nx, ny = -nx, -ny
    samples = []
    for i in range(41):
        s = i / 40
        off = bend * length * s * s
        samples.append((x0 + dx * length * s - dy * off, y0 + dy * length * s + dx * off, s))
    xs = [p[0] for p in samples]
    ys = [p[1] for p in samples]
    tip = samples[-1]
    for y in range(max(0, int(min(ys) - width - 1)), min(c.h, int(max(ys) + width + 2))):
        for x in range(max(0, int(min(xs) - width - 1)), min(c.w, int(max(xs) + width + 2))):
            d2, s, px, py = min(((x - px) ** 2 + (y - py) ** 2, s, px, py) for px, py, s in samples)
            w = width * (1 - s) ** 0.75 * min(1.0, 0.45 + s * 3)
            if math.sqrt(d2) <= max(w, 0.0) + 0.35 or (round(tip[0]), round(tip[1])) == (x, y):
                side = (x - px) * nx + (y - py) * ny
                lv = lo + (hi - lo) * s + (1 if side > 0.4 else -1 if side < -0.4 else 0)
                c.set(x, y, r, round(lv), glint=True)


def wedge(c: Canvas, tip: tuple[float, float], base: tuple[float, float], half: float, lo: int, hi: int, r=G):
    """三角形的肥厚尖叶（龙舌兰、芦荟）：底边中心 base、半宽 half，尖在 tip。
    叶面左半亮、右半暗，右边沿最暗，靠根部再暗一级；前后的叶子靠亮面压暗边分开。"""
    tx, ty = tip
    bx, by = base
    ax, ay = tx - bx, ty - by
    ln = math.hypot(ax, ay)
    ux, uy = ax / ln, ay / ln          # 沿叶子往尖
    nx, ny = -uy, ux                   # 横过叶面
    if nx > 0 or (nx == 0 and ny > 0):
        nx, ny = -nx, -ny              # 让 n 指向左（受光的一侧）
    for y in range(16):
        for x in range(16):
            px, py = x - bx, y - by
            along = px * ux + py * uy
            across = px * nx + py * ny
            if along < -0.5 or along > ln + 0.3:
                continue
            w = half * (1 - along / ln) + 0.35
            if abs(across) > w:
                continue
            t = max(0.0, along / ln)
            lv = lo + (hi - lo) * t
            if across > w * 0.25:
                lv += 1
            elif across < -w * 0.45:
                lv -= 1
            if across < -w + 0.8 and w > 0.9:
                lv -= 1
            c.set(x, y, r, round(lv), glint=True)


def ball(c: Canvas, cx: float, cy: float, rad: float, base: int, r=G, amp: float = 1.6, spec: bool = True):
    """圆的果子、花苞、气囊：按法线打光，左上一个高光点。"""
    c.shade_disc(cx, cy, rad, r, base, amp=amp, spec=spec)
    for x, y in c.disc_cells(cx, cy, rad):
        c.glints.add((x, y))


def sprite(c: Canvas, rows, ramps, ox: int = 0, oy: int = 0):
    """一小块手画的像素（花冠、菌盖）：字符在哪组里用哪条色阶，在组里的位置是级数。"""
    c.draw(rows, ramps, ox, oy)


DETAIL = {"abcdefg": G}

PLANT_REFS = {
    "tuft": ("block/short_grass", "block/tall_grass_top"),
    "blades": ("block/tall_grass_top", "block/large_fern_top"),
    "fern": ("block/fern",),
    "flower": ("block/poppy", "block/allium", "block/blue_orchid"),
    "bulb": ("block/torchflower",),
    "fungus": ("block/red_mushroom", "block/brown_mushroom", "block/crimson_fungus"),
    "tendril": ("block/twisting_vines_plant", "block/weeping_vines"),
    "reed": ("block/sugar_cane",),
    "succulent": ("block/cactus_side", "block/dead_bush"),
    "lichen": ("block/glow_lichen", "block/pink_petals"),
    "crystal_bloom": ("block/amethyst_cluster", "block/large_amethyst_bud"),
}

PLANTS: dict[tuple[str, str], object] = {}


def plant(form: str, shape: str):
    def wrap(fn):
        PLANTS[(form, shape)] = fn
        return fn
    return wrap


# ---- 草丛 ----

@plant("tuft", "a")
def tuft_a(body: Canvas, detail: Canvas):
    # 一丛往外散开的细草，中间高、两边矮而外倾，前后的草叶一深一浅分得开；三根草梢挂着下垂的小穗
    stroke(body, vrun(3, 15, 15) + vrun(2, 14, 13), 1, 2)
    stroke(body, vrun(4, 15, 14) + vrun(3, 13, 12) + vrun(2, 11, 10) + vrun(1, 9, 8), 1, 5)
    stroke(body, vrun(5, 15, 13) + vrun(4, 12, 8) + vrun(3, 7, 5), 1, 5)
    stroke(body, vrun(6, 15, 13) + vrun(6, 12, 11), 0, 2)
    stroke(body, vrun(7, 15, 10) + vrun(6, 9, 3), 2, 6)
    stroke(body, vrun(8, 15, 9) + vrun(9, 8, 4), 1, 5)
    stroke(body, vrun(9, 15, 13) + vrun(10, 12, 10) + vrun(11, 9, 7) + vrun(12, 6, 6), 1, 5)
    stroke(body, vrun(10, 15, 14) + vrun(11, 13, 12) + vrun(12, 11, 11) + vrun(13, 10, 10) + vrun(14, 9, 9), 1, 4)
    stroke(body, vrun(12, 15, 14) + vrun(13, 13, 13), 1, 2)
    for x, y in ((6, 3), (3, 5), (12, 6)):
        detail.set(x, y - 1, G, 6, glint=True)
        detail.set(x, y, G, 5, glint=True)
        detail.set(x + 1, y, G, 3, glint=True)
        detail.set(x + 1, y + 1, G, 2, glint=True)


@plant("tuft", "b")
def tuft_b(body: Canvas, detail: Canvas):
    # 矮而密的一个草垫，叶尖朝四周翻，顶上星星点点开着小花
    blades = (
        (vrun(2, 15, 15) + vrun(1, 14, 13), 1, 3),
        (vrun(3, 15, 14) + vrun(2, 13, 12) + vrun(1, 11, 11), 1, 4),
        (vrun(4, 15, 13) + vrun(3, 12, 10), 1, 4),
        (vrun(5, 15, 12) + vrun(4, 11, 8), 2, 5),
        (vrun(6, 15, 11) + vrun(6, 10, 8), 1, 5),
        (vrun(7, 15, 7), 2, 6),
        (vrun(8, 15, 10) + vrun(9, 9, 7), 1, 5),
        (vrun(9, 15, 12) + vrun(10, 11, 9) + vrun(11, 8, 8), 1, 5),
        (vrun(10, 15, 13) + vrun(11, 12, 11) + vrun(12, 10, 10), 1, 4),
        (vrun(11, 15, 14) + vrun(12, 13, 13) + vrun(13, 12, 12) + vrun(14, 11, 11), 1, 3),
        (vrun(12, 15, 15) + vrun(13, 14, 14) + vrun(14, 13, 13), 1, 2),
        (vrun(5, 15, 15) + vrun(6, 14, 14), 0, 1),
        (vrun(8, 15, 12), 0, 2),
    )
    for p, lo, hi in blades:
        stroke(body, p, lo, hi)
    for x, y in ((4, 7), (7, 6), (11, 7), (2, 10), (13, 10), (9, 9)):
        detail.set(x, y, G, 6, glint=True)
        detail.set(x + 1, y, G, 4, glint=True)
        detail.set(x, y + 1, G, 3, glint=True)


# ---- 长叶草 ----

@plant("blades", "a")
def blades_a(body: Canvas, detail: Canvas):
    # 拱起的带状长叶，像吊兰：叶面两个像素宽、上亮下暗，梢头收细往外垂；中间一根抽出的谷穗
    ribbon(body, vrun(6, 15, 9) + [(5, 8), (5, 7), (4, 6), (3, 5), (2, 5), (1, 6), (0, 7), (0, 8)], 1, 5, 12, 1)
    ribbon(body, vrun(9, 15, 8) + [(10, 7), (10, 6), (11, 5), (12, 4), (13, 4), (14, 5), (15, 6)], 1, 5, 11, -1)
    ribbon(body, vrun(7, 15, 12) + [(6, 11), (5, 10), (4, 10), (3, 11), (2, 12)], 1, 4, 6, 1)
    ribbon(body, vrun(8, 15, 13) + [(9, 12), (10, 11), (11, 11), (12, 12), (13, 13)], 1, 3, 5, -1)
    stroke(body, vrun(8, 15, 6), 2, 4)
    sprite(detail, [
        ".g.",
        "fe.",
        ".ed",
        "fd.",
        ".dc",
        ".c.",
    ], DETAIL, 7, 0)


@plant("blades", "b")
def blades_b(body: Canvas, detail: Canvas):
    # 直立的剑叶，两个像素宽、尖梢；叶面上有横向的斑纹（点缀层，像斑叶芒）
    leaves = (
        (vrun(2, 15, 10) + vrun(1, 9, 6), 1, 4, 1),
        (vrun(5, 15, 5) + vrun(4, 4, 2), 1, 5, 1),
        (vrun(8, 15, 1), 1, 6, 1),
        (vrun(11, 15, 6) + vrun(12, 5, 3), 1, 5, -1),
        (vrun(14, 15, 11) + vrun(15, 10, 8), 1, 4, -1),
    )
    for p, lo, hi, side in leaves:
        ribbon(body, p, lo, hi, len(p) - 3, side)
    for x, side, ys in ((2, 1, (10,)), (5, 1, (13, 7)), (8, 1, (11, 4)), (11, -1, (12, 7)), (14, -1, (13,))):
        for y in ys:
            detail.set(x, y, G, 6, glint=True)
            detail.set(x + side, y, G, 4 if side > 0 else 6, glint=True)
            detail.set(x, y + 1, G, 4, glint=True)
            detail.set(x + side, y + 1, G, 3 if side > 0 else 5, glint=True)


# ---- 蕨 ----

@plant("fern", "a")
def fern_a(body: Canvas, detail: Canvas):
    # 三片人字形的羽叶：中间一片直立，两边各一片斜伸；叶背的孢子囊排成点（点缀层）
    stroke(body, vrun(7, 15, 1), 2, 6)
    for y, lv in ((12, 3), (10, 3), (8, 4), (6, 4), (4, 5), (2, 5)):
        body.set(6, y, G, lv + 1, glint=True)
        body.set(8, y, G, lv - 1, glint=True)
        if 4 <= y <= 10:
            body.set(5, y - 1, G, lv + 1, glint=True)
            body.set(9, y - 1, G, lv - 1, glint=True)
    stroke(body, [(6, 14), (5, 13), (4, 12), (3, 11), (2, 10), (1, 9), (0, 8)], 2, 5)
    for x, y, lv in ((4, 11, 4), (4, 10, 5), (2, 9, 5), (2, 8, 6), (0, 7, 5), (3, 12, 2), (2, 12, 2),
                     (1, 10, 3), (0, 10, 3)):
        body.set(x, y, G, lv, glint=True)
    stroke(body, [(8, 14), (9, 13), (10, 12), (11, 11), (12, 10), (13, 9), (14, 8), (15, 7)], 1, 4)
    for x, y, lv in ((10, 11, 4), (10, 10, 4), (12, 9, 4), (12, 8, 5), (14, 7, 5), (12, 12, 1), (13, 12, 1),
                     (14, 10, 2), (15, 10, 2), (15, 8, 3)):
        body.set(x, y, G, lv, glint=True)
    for x, y, lv in ((5, 8, 4), (9, 8, 3), (5, 6, 5), (9, 6, 3), (4, 10, 4), (12, 8, 4), (2, 12, 3), (14, 10, 3)):
        detail.set(x, y, G, lv, glint=True)


@plant("fern", "b")
def fern_b(body: Canvas, detail: Canvas):
    # 一把锯齿边的嫩叶，中间高、两边往外斜；梢头还卷着拳头样的幼叶（点缀层）
    zigzag(body, vrun(8, 15, 3), 2, 6)
    zigzag(body, [(7, 14), (6, 13), (6, 12), (5, 11), (4, 10), (4, 9), (3, 8), (3, 7), (2, 6)], 1, 5)
    zigzag(body, [(9, 14), (10, 13), (10, 12), (11, 11), (12, 10), (12, 9), (13, 8), (13, 7), (14, 6)], 1, 5)
    stroke(body, [(7, 15), (6, 15), (5, 14), (4, 14)], 1, 3)
    stroke(body, [(9, 15), (10, 15), (11, 14), (12, 14)], 1, 2)
    for x, y, s in ((8, 3, 1), (2, 6, -1), (14, 6, 1)):
        detail.set(x, y - 1, G, 6, glint=True)
        detail.set(x + s, y - 1, G, 5, glint=True)
        detail.set(x + s, y, G, 3, glint=True)
        detail.set(x, y, G, 4, glint=True)
    detail.set(4, 13, G, 5, glint=True)
    detail.set(4, 12, G, 4, glint=True)
    detail.set(12, 13, G, 4, glint=True)


# ---- 花 ----

FLOWER_HEAD = [
    "..ef.e..",
    ".defeffe",
    "cdfgfeed",
    "bdeccfed",
    ".cdbbdc.",
    "..bc.b..",
]

SMALL_BLOSSOM = [
    ".f.",
    "egd",
    ".c.",
]


@plant("flower", "a")
def flower_a(body: Canvas, detail: Canvas):
    # 一根挺直的花茎，两片带状的叶子一高一低；顶上一朵五瓣的大花，花心深
    stroke(body, vrun(8, 15, 6), 1, 4)
    leaf(body, 7.5, 13.0, 155, 5.5, 1.1, 2, 5, bend=-0.12)
    leaf(body, 8.5, 11.0, 30, 5.0, 1.0, 2, 5, bend=0.12)
    stroke(body, [(7, 15), (9, 15)], 0, 1)
    sprite(detail, FLOWER_HEAD, DETAIL, 4, 1)


@plant("flower", "b")
def flower_b(body: Canvas, detail: Canvas):
    # 三根细茎从一处抽出，各顶一朵圆的小花，高低错开；基部一对叶子
    stroke(body, vrun(8, 15, 13) + vrun(7, 12, 9) + vrun(6, 8, 7) + [(5, 6)], 1, 4)
    stroke(body, vrun(8, 15, 4), 1, 5)
    stroke(body, vrun(9, 15, 12) + vrun(10, 11, 9) + [(11, 8)], 1, 4)
    ribbon(body, [(7, 14), (6, 13), (5, 12), (4, 12), (3, 11), (2, 11)], 1, 4, 4, 1)
    ribbon(body, [(9, 14), (10, 13), (11, 13), (12, 12), (13, 12)], 1, 4, 3, -1)
    for x, y in ((5, 5), (8, 3), (11, 7)):
        sprite(detail, SMALL_BLOSSOM, DETAIL, x - 1, y - 1)
    detail.set(4, 4, G, 4, glint=True)
    detail.set(9, 2, G, 5, glint=True)
    detail.set(12, 6, G, 4, glint=True)


# ---- 球茎 ----

def basal_leaves(body: Canvas, lo: int = 2):
    """贴地摊开的一圈肥叶：左右各两片，后排的往上翘、暗一些，前排的平伸、亮一些。"""
    base = (7.5, 15.0)
    for tip, half, l, h in (((2.0, 10.0), 1.3, lo - 1, lo + 2), ((13.0, 10.0), 1.3, lo - 1, lo + 2),
                            ((0.0, 13.0), 1.2, lo, lo + 3), ((15.0, 13.0), 1.2, lo, lo + 3),
                            ((4.0, 12.0), 1.1, lo, lo + 3), ((11.0, 12.0), 1.1, lo, lo + 3)):
        wedge(body, tip, base, half, l, h)


@plant("bulb", "a")
def bulb_a(body: Canvas, detail: Canvas):
    # 基部一圈肥叶摊开，中间一根粗茎托着一个大圆果
    basal_leaves(body)
    stroke(body, vrun(8, 14, 9), 2, 4)
    stroke(body, vrun(7, 14, 10), 2, 5)
    ball(detail, 7.5, 6.0, 3.6, 4)


@plant("bulb", "b")
def bulb_b(body: Canvas, detail: Canvas):
    # 三根弯茎各托着一个小圆果，最高的居中
    stroke(body, vrun(7, 14, 12) + vrun(6, 11, 10) + vrun(5, 9, 8), 1, 4)
    stroke(body, vrun(8, 14, 6), 1, 5)
    stroke(body, vrun(9, 14, 13) + vrun(10, 12, 11) + [(11, 10)], 1, 4)
    basal_leaves(body)
    ball(detail, 4.5, 5.5, 2.0, 4)
    ball(detail, 8.0, 3.5, 2.5, 4)
    ball(detail, 11.5, 7.5, 1.7, 4)


# ---- 菌菇 ----

BIG_CAP = [
    "....defff...",
    "..cdeffgff..",
    ".cdeefffeed.",
    "bcdeeeeedddc",
    "abbccccccbba",
    "..a......a..",
]


@plant("fungus", "a")
def fungus_a(body: Canvas, detail: Canvas):
    # 一朵大蘑菇：粗菌柄带一圈菌环，菌盖圆顶、边缘下卷，盖下一条暗的菌褶
    sprite(body, [
        "....4433....",
        "....4432....",
        "...544321...",
        "....4432....",
        "....4432....",
        "....4332....",
        "....3322....",
        "...332211...",
        "..22211110..",
    ], {"0123456": G}, 2, 7)
    sprite(detail, BIG_CAP, DETAIL, 2, 2)
    for x, y in ((6, 3), (9, 4), (5, 5)):
        detail.set(x, y, G, 6, glint=True)


@plant("fungus", "b")
def fungus_b(body: Canvas, detail: Canvas):
    # 一簇三朵：高的细长、斜着的中等、脚边一朵小的
    stroke(body, vrun(11, 15, 7), 2, 5)
    stroke(body, vrun(12, 15, 8), 1, 3)
    stroke(body, vrun(5, 15, 12) + vrun(5, 11, 10), 2, 4)
    stroke(body, vrun(6, 15, 11), 1, 3)
    stroke(body, vrun(8, 15, 14), 2, 3)
    sprite(detail, [
        "..eff..",
        ".deffe.",
        "cdeeedc",
        "abccbba",
    ], DETAIL, 8, 3)
    sprite(detail, [
        "..eff..",
        ".defe..",
        "cdeedc.",
        "abcbba.",
    ], DETAIL, 2, 6)
    sprite(detail, [
        ".ef.",
        "deed",
        "bccb",
    ], DETAIL, 7, 11)


# ---- 卷须 ----

# 卷须梢头的螺旋（从茎顶接上去，往右上绕一圈再往里收），最后一格是螺旋的芯
CURL = [(0, 0), (0, -1), (0, -2), (1, -3), (2, -3), (3, -2), (3, -1), (2, 0), (1, -1), (2, -2)]


def curl(body: Canvas, detail: Canvas, x: int, y: int, mirror: int, lo: int, hi: int):
    pts = [(x + dx * mirror, y + dy) for dx, dy in CURL]
    stroke(body, pts[:-1], lo, hi)
    detail.set(*pts[-1], G, 6, glint=True)


@plant("tendril", "a")
def tendril_a(body: Canvas, detail: Canvas):
    # 三根茎，梢头卷成螺旋，螺旋的芯是一颗亮珠；茎上零星几个小芽（点缀层）
    stroke(body, vrun(5, 15, 11) + vrun(4, 10, 9), 1, 3)
    curl(body, detail, 4, 8, -1, 3, 6)
    stroke(body, vrun(7, 15, 9) + vrun(8, 8, 5), 1, 4)
    curl(body, detail, 8, 4, 1, 4, 6)
    stroke(body, vrun(10, 15, 13) + vrun(11, 12, 11), 1, 3)
    curl(body, detail, 11, 10, 1, 3, 5)
    for x, y in ((6, 13), (9, 12), (7, 10), (12, 14)):
        detail.set(x, y, G, 4, glint=True)


@plant("tendril", "b")
def tendril_b(body: Canvas, detail: Canvas):
    # 三根波浪样往上扭的细藤，波峰外侧各长一粒小瘤，梢头一点亮
    for x0, phase, top in ((3, 0.3, 3), (8, 0.9, 0), (13, 1.6, 4)):
        pts, last = [], None
        for y in range(15, top - 1, -1):
            x = round(x0 + math.sin((15 - y) * 0.55 + phase) * 1.4)
            if last is not None and abs(x - last) > 1:
                pts.append((last + (1 if x > last else -1), y))
            pts.append((x, y))
            last = x
        stroke(body, pts, 1, 5)
        for i in range(2, len(pts) - 2):
            (xa, _), (x, y), (xb, _) = pts[i - 1], pts[i], pts[i + 1]
            if x < xa and x < xb or (x < xa and x == xb):
                detail.set(x - 1, y, G, 5, glint=True)
            elif x > xa and x > xb or (x > xa and x == xb):
                detail.set(x + 1, y, G, 3, glint=True)
        x, y = pts[-1]
        detail.set(x, y - 1, G, 6, glint=True)


# ---- 芦苇 ----

@plant("reed", "a")
def reed_a(body: Canvas, detail: Canvas):
    # 香蒲：几根直茎，顶上一截棒状的穗（点缀层，圆柱的明暗），穗上还有一根细尖；基部带状长叶往外拱
    stems = ((4, 3), (7, 0), (10, 2), (12, 5))
    for x, top in stems:
        stroke(body, vrun(x, 15, top), 1, 5)
    for x, top in stems:
        for i in range(4):
            y = top + 2 + i
            detail.set(x, y, G, 5 if i else 6, glint=True)
            detail.set(x + 1, y, G, 3 if i else 4, glint=True)
        detail.set(x, top + 6, G, 2, glint=True)
    ribbon(body, vrun(5, 15, 12) + vrun(4, 11, 9) + [(3, 8), (3, 7), (2, 6)], 1, 5, 6, 1)
    ribbon(body, vrun(9, 15, 13) + [(10, 12), (10, 11), (11, 10), (12, 10), (13, 9), (14, 8)], 1, 5, 5, -1)
    stroke(body, vrun(6, 15, 11), 0, 2)


@plant("reed", "b")
def reed_b(body: Canvas, detail: Canvas):
    # 纸莎草：细长的茎顶着一蓬放射状的细丝（点缀层）
    tops = ((4, 5), (8, 2), (12, 4))
    stroke(body, vrun(5, 15, 10) + vrun(4, 9, 6), 1, 5)
    stroke(body, vrun(8, 15, 3), 1, 5)
    stroke(body, vrun(11, 15, 10) + vrun(12, 9, 5), 1, 5)
    for x, top in tops:
        detail.set(x, top, G, 6, glint=True)
        for dx, dy, lv in ((-1, -1, 5), (1, -1, 5), (-2, -1, 4), (2, -1, 4), (-2, 0, 4), (2, 0, 4),
                           (-3, 1, 3), (3, 1, 3), (0, -2, 6), (-1, -2, 4), (1, -2, 4), (-3, 0, 2), (3, 0, 2)):
            detail.set(x + dx, top + dy, G, lv, glint=True)
    ribbon(body, [(7, 15), (7, 14), (6, 13), (5, 12), (4, 12), (3, 11)], 1, 4, 4, 1)
    ribbon(body, [(9, 15), (10, 14), (11, 13), (12, 13), (13, 12)], 1, 4, 3, -1)


# ---- 多肉 ----

@plant("succulent", "a")
def succulent_a(body: Canvas, detail: Canvas):
    # 龙舌兰样的莲座：一圈肥厚的楔形尖叶往外张，后排的暗、前排的亮；正中抽出一根花葶，顶着花苞
    base = (7.5, 14.5)
    for tip, half, lo, hi in (((0.0, 9.0), 1.8, 0, 3), ((15.0, 9.0), 1.8, 0, 3), ((2.0, 4.0), 1.9, 1, 4),
                              ((13.0, 4.0), 1.9, 1, 4), ((5.0, 2.5), 1.7, 2, 4), ((10.5, 2.5), 1.7, 2, 4),
                              ((1.0, 13.0), 1.6, 2, 4), ((14.0, 13.0), 1.6, 2, 4), ((4.0, 7.0), 1.8, 2, 5),
                              ((11.0, 7.0), 1.8, 2, 5)):
        wedge(body, tip, base, half, lo, hi)
    stroke(body, vrun(8, 10, 3), 3, 5)
    sprite(detail, [
        ".f.",
        "egd",
        ".c.",
    ], DETAIL, 7, 0)
    for x, y in ((0, 9), (15, 9), (2, 4), (13, 4)):
        detail.set(x, y, G, 5, glint=True)


@plant("succulent", "b")
def succulent_b(body: Canvas, detail: Canvas):
    # 桶形的肉质茎：竖棱一亮一暗，左边受光；棱脊上隔一段一个刺座（点缀层），顶上一圈花
    for y in range(4, 16):
        w = 4.6 * math.sqrt(max(0.0, 1 - ((y - 10.2) / 6.4) ** 2))
        x0, x1 = round(7.5 - w), round(7.5 + w)
        for x in range(x0, x1 + 1):
            ridge = (x - x0) % 2 == 0
            u = (x - x0) / max(1, x1 - x0)
            lv = 5 - round(u * 3) + (0 if ridge else -1)
            if y > 13:
                lv -= 1
            if x in (x0, x1):
                lv = min(lv, 2 if x == x1 else 3)
            body.set(x, y, G, lv, glint=True)
            if ridge and x not in (x0, x1) and (y + x // 2) % 3 == 0 and 5 < y < 14:
                detail.set(x, y, G, 6 if u < 0.5 else 4, glint=True)
    sprite(detail, [
        ".e.f.e.",
        "dfgfgfd",
        ".cdedc.",
    ], DETAIL, 4, 2)


# ---- 地衣（贴地，俯视） ----

def lichen_patch(c: Canvas, cx: float, cy: float, r: float, lobes: int, phase: float, base: int = 3, amp: float = 0.14):
    """一片叶状地衣：边缘是一圈圆的裂片，裂片的边翘起来亮一级，中间暗一点。"""
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            a = math.atan2(dy, dx)
            rr = r * (1 - amp + amp * math.cos(a * lobes + phase))
            d = math.hypot(dx, dy)
            if d <= rr:
                lv = base + (1 if d > rr - 1.2 else 0) - (1 if d < rr * 0.35 else 0)
                lv += 1 if (dx + dy) < -r * 0.5 else 0
                c.set(x, y, G, max(1, lv), glint=True)


@plant("lichen", "a")
def lichen_a(body: Canvas, detail: Canvas):
    # 两大一小三片圆裂片的叶状地衣；上面长着几个杯状的子实体（点缀层：亮的杯沿、暗的杯心）
    lichen_patch(body, 5.5, 5.5, 4.4, 7, 0.4)
    lichen_patch(body, 11.0, 10.8, 3.8, 6, 1.7)
    lichen_patch(body, 3.8, 12.6, 2.6, 5, 0.9, 2, 0.18)
    for x, y in ((4, 4), (7, 6), (10, 10), (12, 12), (3, 12)):
        detail.set(x, y, G, 5, glint=True)
        detail.set(x + 1, y, G, 4, glint=True)
        detail.set(x, y + 1, G, 4, glint=True)
        detail.set(x + 1, y + 1, G, 2, glint=True)


def crust(c: Canvas, rng: random.Random, cx: float, cy: float, r: float):
    """一块壳状地衣（像地图衣）：斑块的边是一圈深色的线，块面左上亮。"""
    cells = set()
    for y in range(16):
        for x in range(16):
            if math.hypot(x - cx, (y - cy) * 1.15) <= r + rng.uniform(-0.6, 0.4):
                cells.add((x, y))
    for x, y in cells:
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            lv = 2
        else:
            lv = 5 if (x - cx) + (y - cy) < -0.8 else 4
        c.set(x, y, G, lv, glint=True)


@plant("lichen", "b")
def lichen_b(body: Canvas, detail: Canvas):
    # 壳状地衣：大大小小的斑块成群散在地上，边缘不齐；斑块上冒出针尖大的子实体
    rng = random.Random(7104)
    for cx, cy, r in ((3.5, 3.5, 2.3), (11.5, 3.5, 2.0), (5.0, 10.5, 2.7), (12.0, 11.0, 2.3), (8.5, 7.0, 1.2),
                      (1.5, 14.5, 1.0), (14.5, 7.0, 0.9), (9.0, 14.5, 1.0)):
        crust(body, rng, cx, cy, r)
    for x, y in ((3, 3), (11, 3), (5, 10), (4, 11), (12, 11), (8, 7)):
        detail.set(x, y, G, 6, glint=True)
        detail.set(x + 1, y, G, 3, glint=True)


# ---- 晶花 ----

def shard(body: Canvas, detail: Canvas, bx: float, w: float, tip_x: float, tip_y: int, glow: int = 2,
          base_y: int = 15):
    """一根晶柱：底边中心在 bx、宽 w，往 (tip_x, tip_y) 长，尖上是一段收拢的锥；左边沿最亮、左半面亮，
    右半面暗、右边沿最暗，前后的晶柱靠亮边压暗边分开。尖上 glow 格是点缀层（发光的晶尖）。"""
    hw = w / 2
    cone = max(2.0, w * 0.9)
    for y in range(tip_y, base_y + 1):
        u = (y - tip_y) / max(1, base_y - tip_y)
        ax = tip_x + (bx - tip_x) * u
        h = hw * min(1.0, (y - tip_y + 0.5) / cone)
        xl, xr = round(ax - h), round(ax + h - 0.01)
        if xr < xl:
            xr = xl
        for x in range(xl, xr + 1):
            lit = x + 0.5 <= ax
            if y - tip_y < glow:
                detail.set(x, y, G, (6 if lit else 4) - (1 if y - tip_y == glow - 1 else 0), glint=True)
                continue
            if x == xl and xl != xr:
                lv = 5
            elif x == xr and xl != xr:
                lv = 1
            else:
                lv = 4 if lit else 3
            if y >= base_y - 1:
                lv -= 1
            body.set(x, y, G, lv, glint=True)


@plant("crystal_bloom", "a")
def crystal_bloom_a(body: Canvas, detail: Canvas):
    # 一簇晶柱从一处往外张：后面的先画，前面的压上去；每根的尖端透出另一种颜色的光
    shard(body, detail, 5.0, 3, 1.5, 5, 2)
    shard(body, detail, 10.5, 3, 14.0, 4, 2)
    shard(body, detail, 7.5, 4, 7.0, 0, 3)
    shard(body, detail, 5.0, 3, 3.5, 9, 2)
    shard(body, detail, 10.0, 3, 11.5, 8, 2)
    shard(body, detail, 8.0, 3, 8.5, 11, 1)


@plant("crystal_bloom", "b")
def crystal_bloom_b(body: Canvas, detail: Canvas):
    # 一根高高的主晶微微斜着，两边各一根矮的斜出去，脚下一颗小的
    shard(body, detail, 4.5, 3, 2.0, 7, 2)
    shard(body, detail, 10.5, 3, 13.5, 8, 2)
    shard(body, detail, 7.5, 4, 9.0, 0, 4)
    shard(body, detail, 5.5, 2, 5.0, 11, 1)


def _register_plants():
    for (form, shape), fn in PLANTS.items():
        def draw(fn=fn):
            body, detail = Canvas(), Canvas()
            fn(body, detail)
            return body, detail

        texture(f"block/planet_{form}_{shape}", tinted=True, refs=PLANT_REFS[form])(lambda rng, d=draw: d()[0])
        texture(f"block/planet_{form}_{shape}_detail", tinted=True)(lambda rng, d=draw: d()[1])


_register_plants()


# ---- 树木方块（染色） ----

def wrap_set(c: Canvas, x: int, y: int, lv: int):
    c.set(x % c.w, y % c.h, G, lv, glint=True)


@texture("block/planet_log", tinted=True, refs=("block/oak_log", "block/spruce_log", "block/jungle_log"))
def planet_log(rng):
    # 树皮：几道竖的深沟弯弯曲曲往上走，沟之间是一条条树皮板，左边沿受光、右边沿背光；
    # 板上偶尔一道横裂。上下、左右都能接上。
    c = Canvas()
    wob = (
        (0, 0, 1, 1, 1, 0, 0, 0, -1, -1, 0, 0, 0, 1, 0, 0),
        (0, 1, 1, 0, 0, 0, -1, -1, -1, 0, 0, 1, 1, 1, 0, 0),
        (0, 0, 0, -1, -1, 0, 0, 1, 1, 1, 0, 0, -1, -1, 0, 0),
        (1, 1, 0, 0, 0, 1, 1, 0, 0, -1, -1, 0, 0, 0, 0, 1),
    )
    bases = (0, 4, 8, 12)
    for y in range(16):
        fs = [b + w[y] for b, w in zip(bases, wob)]
        for i, f in enumerate(fs):
            nxt = fs[(i + 1) % 4] + (16 if i == 3 else 0)
            wrap_set(c, f, y, 1)
            width = nxt - f - 1
            for d in range(1, width + 1):
                if d == 1:
                    lv = 5
                elif d == width:
                    lv = 2
                elif d == width - 1:
                    lv = 3
                else:
                    lv = 4
                wrap_set(c, f + d, y, lv)
    # 横裂：一道暗线，上沿亮一点
    for x0, y, n in ((1, 3, 2), (9, 6, 2), (5, 10, 2), (13, 13, 2), (1, 14, 1), (10, 1, 1)):
        for i in range(n):
            x = (x0 + i) % 16
            if c.level(x, y) >= 3:
                c.set(x, y, G, 2, glint=True)
                if c.level(x, (y - 1) % 16) >= 3:
                    c.set(x, (y - 1) % 16, G, 5, glint=True)
    return c


@texture("block/planet_log_top", tinted=True, refs=("block/oak_log_top", "block/spruce_log_top"))
def planet_log_top(rng):
    # 截面：一圈树皮，里面是年轮（圆角的方圈，一圈亮一圈暗），正中一点髓心
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            d = (dx ** 3 + dy ** 3) ** (1 / 3)
            if max(dx, dy) > 6.6:
                lv = 2 if (x * 3 + y * 5) % 4 else 3
                if x in (0, 15) and y in (0, 15):
                    lv = 1
            elif max(dx, dy) > 5.6 and d > 6.0:
                lv = 3
            else:
                ring = int(d * 0.95) % 2
                lv = 5 if ring == 0 else 4
                if d < 1.0:
                    lv = 3
            c.set(x, y, G, lv, glint=True)
    # 一道放射状的干裂
    for i, (x, y) in enumerate(((9, 7), (10, 6), (11, 6), (12, 5))):
        c.set(x, y, G, 3, glint=True)
    return c


@texture("block/planet_leaves", tinted=True, refs=("block/oak_leaves", "block/azalea_leaves", "block/cherry_leaves"))
def planet_leaves(rng):
    # 一团团叶子：最底下是深色的叶影，先撒一层暗的后排叶片，再撒一层亮的前排叶片（左上亮、右下暗），
    # 叶片之间漏几个洞。上下左右都能接上。
    rng = random.Random(1601)
    c = Canvas()
    c.fill(G, 1)
    shapes = (
        ((1, 0, 5), (0, 1, 5), (1, 1, 4), (2, 1, 3), (1, 2, 3), (2, 2, 2)),
        ((0, 0, 5), (1, 0, 4), (0, 1, 4), (1, 1, 3), (2, 1, 2), (1, 2, 2)),
        ((1, 0, 5), (2, 0, 4), (0, 1, 5), (1, 1, 4), (2, 1, 3), (1, 2, 2)),
        ((0, 0, 4), (0, 1, 5), (1, 1, 4), (1, 2, 3), (2, 2, 2), (2, 1, 3)),
    )
    covered = set()

    def scatter(step: int, offset: int, dim: int):
        for gy in range(0, 16, step):
            for gx in range(0, 16, step):
                ox = gx + offset + rng.randint(-1, 1)
                oy = gy + offset + rng.randint(-1, 1)
                for dx, dy, lv in rng.choice(shapes):
                    x, y = (ox + dx) % 16, (oy + dy) % 16
                    c.set(x, y, G, lv - dim, glint=True)
                    covered.add((x, y))

    scatter(4, 2, 2)
    scatter(4, 0, 0)
    for y in range(16):
        for x in range(16):
            if (x, y) not in covered and rng.random() < 0.55:
                c.clear(x, y)
    return c


@texture("block/planet_cap", tinted=True, refs=("block/red_mushroom_block", "block/brown_mushroom_block"))
def planet_cap(rng):
    # 巨菌的菌盖：肉质的面上有一大片一大片柔和的深浅，几颗凸起的鳞疣（亮顶、暗的下沿）。上下左右能接上。
    rng = random.Random(1602)
    c = Canvas()
    grid = [[rng.random() for _ in range(4)] for _ in range(4)]

    def noise(x, y):
        gx, gy = x / 4, y / 4
        i0, j0 = int(gx) % 4, int(gy) % 4
        i1, j1 = (i0 + 1) % 4, (j0 + 1) % 4
        tx, ty = gx - int(gx), gy - int(gy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[j0][i0] + (grid[j0][i1] - grid[j0][i0]) * tx
        b = grid[j1][i0] + (grid[j1][i1] - grid[j1][i0]) * tx
        return a + (b - a) * ty

    for y in range(16):
        for x in range(16):
            v = noise(x, y)
            c.set(x, y, G, 3 if v < 0.38 else (4 if v < 0.70 else 5), glint=True)
    # 鳞疣：圆的凸起，左上亮、右下压暗一圈
    for cx, cy, r in ((3.5, 3.5, 2.2), (11.5, 2.5, 1.6), (7.5, 9.0, 2.6), (13.5, 11.0, 1.8), (2.5, 12.5, 1.5)):
        for y in range(int(cy - r - 2), int(cy + r + 3)):
            for x in range(int(cx - r - 2), int(cx + r + 3)):
                d = math.hypot(x - cx, y - cy)
                k = (x - cx) + (y - cy)
                if d <= r:
                    wrap_set(c, x, y, 6 if k < -r * 0.5 else 5)
                elif d <= r + 1.0 and k > 0:
                    wrap_set(c, x, y, 2)
    # 几个细小的气孔
    for x, y in ((6, 2), (10, 6), (1, 7), (5, 14), (11, 14), (15, 5)):
        if c.level(x, y) in (3, 4):
            c.set(x, y, G, 2, glint=True)
    return c


@texture("block/planet_bloom", tinted=True, refs=("block/shroomlight", "block/sweet_berry_bush_stage3"))
def planet_bloom(rng):
    # 一串花果：大大小小的圆果挤在一起，左上有高光、右下压暗，果子之间露出细的果梗，空当是透的
    c = Canvas()
    stems = ((4, 6, 8, 9), (11, 6, 8, 9), (4, 10, 8, 9), (12, 10, 8, 9), (8, 9, 8, 13))
    for x0, y0, x1, y1 in stems:
        c.line(x0, y0, x1, y1, G, 2)
    for cx, cy, r in ((3.5, 3.5, 2.6), (11.0, 3.0, 2.4), (8.0, 9.0, 3.0), (3.0, 12.0, 2.2), (12.5, 12.0, 2.4),
                      (14.5, 7.0, 1.0), (7.5, 14.5, 1.0)):
        ball(c, cx, cy, r, 4, amp=1.8)
        for x, y in c.disc_cells(cx, cy, r):
            d = math.hypot(x - cx, y - cy)
            if d > r - 0.6 and (x - cx) + (y - cy) > 0:
                c.set(x, y, G, 2, glint=True)
    return c


# ---- 资源作物（不染色） ----

# 只有作物用的色阶
STARBERRY = register(ramp("flora_starberry", 96, 0.115, n=7, lo=0.42, hi=0.97, shift=10, peak=0.6))   # 星莓：淡黄
FROST = register(ramp("flora_frost", 228, 0.036, n=7, lo=0.36, hi=0.88, shift=10))                   # 霜苔的灰蓝叶
GAMMA = register(ramp("flora_gamma", 112, 0.105, n=7, lo=0.28, hi=0.88, shift=14, peak=0.55))        # 伽马草：黄绿
GAMMA_GLOW = register(glow("flora_gamma_glow", 118, 0.170))
MOULD = register(ramp("flora_mould", 318, 0.050, n=7, lo=0.28, hi=0.82, shift=10))                   # 毒霉的紫灰菌盖
SPORE = register(ramp("flora_spore", 112, 0.150, n=6, lo=0.46, hi=0.93, shift=8, peak=0.6))          # 黄绿的孢子粉
CACTUS = register(ramp("flora_cactus", 150, 0.085, n=7, lo=0.24, hi=0.78, shift=16))                 # 仙人掌的绿皮
CACTUS_FLESH = register(ramp("flora_cactus_flesh", 118, 0.060, n=5, lo=0.62, hi=0.93, shift=8))      # 切开的淡绿肉
GRAVITINO = register(ramp("flora_gravitino", 305, 0.075, n=6, lo=0.20, hi=0.60, shift=8, peak=0.6))  # 深紫的弯茎
KELP = register(ramp("flora_kelp", 106, 0.085, n=7, lo=0.24, hi=0.72, shift=16, peak=0.5))           # 海带：橄榄绿
BLADDER = register(ramp("flora_bladder", 192, 0.085, n=6, lo=0.50, hi=0.97, shift=8, peak=0.55))                # 狸藻的淡青气囊
MARROW = register(ramp("flora_marrow", 86, 0.072, n=7, lo=0.44, hi=0.97, shift=16, peak=0.6))                  # 髓球的奶白

CROPS = {}


def crop(name: str, refs: tuple = ()):
    """一种资源作物：同一个函数画长熟（ripe=True）和收过的两种样子。"""
    def wrap(fn):
        texture(f"block/{name}", refs=refs)(lambda rng, f=fn: f(Canvas(), True))
        texture(f"block/{name}_harvested")(lambda rng, f=fn: f(Canvas(), False))
        CROPS[name] = fn
        return fn
    return wrap


def seg(x0: int, y0: int, x1: int, y1: int) -> list[tuple[int, int]]:
    """直线上的像素（Bresenham），从 (x0, y0) 到 (x1, y1)。"""
    out = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        out.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return out
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def translucent(c: Canvas, cx: float, cy: float, rad: float, alpha: int):
    for x, y in c.disc_cells(cx, cy, rad):
        c.opacity(x, y, alpha)


@crop("oxygen_bladderwort", refs=("block/kelp", "block/seagrass"))
def oxygen_bladderwort(c: Canvas, ripe: bool):
    # 狸藻：几根细茎轻轻摆，茎上生着细丝样的叶；熟了顶上和茎边挂着一串半透明的淡青气囊
    stems = (
        vrun(4, 15, 13) + vrun(3, 12, 9) + vrun(2, 8, 8),
        vrun(6, 15, 11) + vrun(5, 10, 5),
        vrun(8, 15, 3),
        vrun(10, 15, 10) + vrun(11, 9, 6),
        vrun(12, 15, 13) + vrun(13, 12, 10),
    )
    for i, st in enumerate(stems):
        if not ripe:
            st = st[:max(3, len(st) * 3 // 5)]
        stroke(c, st, 1, 4, LEAF)
        for j in range(2, len(st) - 1, 3):
            x, y = st[j]
            side = 1 if (i + j) % 2 else -1
            if c.get(x + side, y - 1) is None:
                c.set(x + side, y - 1, LEAF, 2 if side > 0 else 3, glint=True)
        if not ripe:
            x, y = st[-1]
            c.set(x, y, MOSS, 1, glint=True)
    if ripe:
        for st in stems:
            x, y = st[-1]
            ball(c, x, y - 1.3, 1.6, 3, BLADDER, amp=1.5)
            translucent(c, x, y - 1.3, 1.6, 200)
        for x, y in ((4, 10), (7, 8), (9, 6), (12, 9), (6, 12)):
            ball(c, x, y, 1.0, 3, BLADDER, amp=1.2, spec=False)
            translucent(c, x, y, 1.0, 185)
            c.set(x, y, BLADDER, 5, glint=True, alpha=225)
    return c


@crop("saltwort", refs=("block/sugar_cane", "block/sea_pickle"))
def saltwort(c: Canvas, ripe: bool):
    # 盐角草：一节一节肉质的绿茎，两个像素宽、左边亮；熟了梢头渐渐发红，结着白色的盐霜；收过的只剩下截短的茎
    stems = ((2, 8), (5, 12), (8, 13), (11, 10), (13, 6))
    for x, h in stems:
        h = h if ripe else max(3, h // 3)
        for i in range(h):
            y = 15 - i
            t = i / max(1, h - 1)
            joint = i % 3 == 2
            lv = 2 + round(t * 2) - (1 if joint else 0)
            r = FLESH if ripe and t > 0.55 else LEAF
            c.set(x, y, r, lv + 1, glint=True)
            c.set(x + 1, y, r, lv, glint=True)
        if not ripe:
            top = 15 - h + 1
            c.set(x, top, CACTUS_FLESH, 3, glint=True)
            c.set(x + 1, top, CACTUS_FLESH, 1, glint=True)
        if ripe and h > 9:
            by = 15 - h // 2
            for dx, dy in ((-1, 0), (-2, -1), (-2, -2)):
                c.set(x + dx, by + dy, LEAF if dy > -2 else FLESH, 4 if dy else 3, glint=True)
    if ripe:
        for x, y in ((5, 6), (8, 4), (8, 9), (11, 8), (2, 10), (5, 11), (13, 11), (11, 12)):
            c.set(x, y, WHITE, 6, glint=True)
    return c


@crop("salt_crystal_cluster", refs=("block/amethyst_cluster", "block/calcite"))
def salt_crystal_cluster(c: Canvas, ripe: bool):
    # 盐晶簇：长在一块矮岩石上的粉白色石盐立方体，顶面最亮、正面中、右面暗；收过的只剩几粒小晶粒
    for x in range(1, 15):
        top = 13 + (1 if x in (1, 2, 13, 14) else 0)
        for y in range(top, 16):
            lv = 4 if y == top else (3 if x < 8 else 2)
            if y == 15:
                lv -= 1
            c.set(x, y, STONE, lv, glint=True)
    cubes = ((6, 12, 5), (2, 13, 4), (11, 13, 3), (8, 14, 3), (4, 14, 2)) if ripe else ((3, 13, 1), (7, 13, 2), (11, 13, 1))
    for x, bottom, sz in cubes:
        for dy in range(sz + 1):
            for dx in range(sz + 1):
                yy = bottom - sz + dy
                if dy == 0:
                    r, lv = WHITE, 6 if dx < sz else 5
                elif dx == sz:
                    r, lv = PINK, 3
                elif dx == 0:
                    r, lv = PINK, 6
                else:
                    r, lv = PINK, 5
                c.set(x + dx, yy, r, lv, glint=True)
        if sz >= 3:
            c.set(x + 1, bottom - sz + 1, WHITE, 6, glint=True)
    if ripe:
        for x, y in ((8, 10), (3, 11), (12, 11)):
            c.set(x, y, PINK, 4, glint=True)
    return c


@crop("star_bramble", refs=("block/sweet_berry_bush_stage3", "block/sweet_berry_bush_stage1"))
def star_bramble(c: Canvas, ripe: bool):
    # 星莓丛：带刺的矮灌木，木枝从根上分开，叶子一簇簇的；熟了结着淡黄色、四角发亮的星形浆果
    for pts in (seg(7, 15, 3, 5), seg(8, 15, 8, 3), seg(8, 15, 12, 5), seg(7, 13, 5, 9), seg(8, 12, 11, 9)):
        stroke(c, pts, 1, 3, WOOD)
        for i in range(2, len(pts) - 1, 3):
            x, y = pts[i]
            c.set(x + 1, y, WOOD, 5, glint=True)
    rng = random.Random(7311)
    leaves = ((2, 6), (4, 4), (7, 3), (10, 3), (12, 5), (13, 8), (2, 9), (5, 8), (9, 7), (11, 11), (4, 11),
              (7, 10), (13, 12), (1, 12))
    for x, y in leaves:
        c.set(x, y, MOSS, 4, glint=True)
        c.set(x + 1, y, MOSS, 3, glint=True)
        c.set(x, y + 1, MOSS, 2, glint=True)
        c.set(x + 1, y + 1, MOSS, 1, glint=True)
        if rng.random() < 0.6:
            c.set(x - 1, y, MOSS, 5, glint=True)
    if ripe:
        for x, y in ((4, 6), (9, 4), (12, 8), (6, 10), (10, 11), (2, 11)):
            c.set(x, y, STARBERRY, 6, glint=True)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c.set(x + dx, y + dy, STARBERRY, 4, glint=True)
            c.set(x + 1, y + 1, STARBERRY, 1, glint=True)
    return c


@crop("frostwort", refs=("block/fern", "block/powder_snow"))
def frostwort(c: Canvas, ripe: bool):
    # 霜苔：贴地的一圈灰蓝色叶子，叶面结霜；熟了抽出几根细茎，梢头挂着六角的冰晶
    for ang, ln, lo in ((170, 6.5, 1), (10, 6.5, 1), (150, 6.0, 2), (30, 6.0, 2), (128, 5.0, 2), (52, 5.0, 2),
                        (105, 4.0, 3), (75, 4.0, 3)):
        leaf(c, 7.5, 15.0, ang, ln, 1.0, lo, lo + 3, r=FROST)
    if ripe:
        for x, top, lean in ((4, 6, -1), (8, 2, 0), (12, 6, 1)):
            stroke(c, seg(x - lean, 13, x, top + 2), 2, 4, FROST)
            sprite(c, [
                "..f..",
                ".d.c.",
                "fegdc",
                ".c.b.",
                "..c..",
            ], {"abcdefg": ICE}, x - 2, top - 2)
        for x, y in ((3, 13), (6, 12), (10, 12), (12, 14), (8, 13)):
            c.set(x, y, ICE, 6, glint=True)
    else:
        for x in (5, 8, 11):
            c.set(x, 13, FROST, 2, glint=True)
            c.set(x, 12, FROST, 3, glint=True)
    return c


FLAME = [
    ".e.",
    ".fe",
    "edc",
    "Edc",
    ".D.",
]


@crop("solar_vine", refs=("block/twisting_vines_plant", "block/torchflower"))
def solar_vine(c: Canvas, ripe: bool):
    # 日焰藤：一根扭着往上爬的藤，叶子交替长在弯的外侧；熟了开着火焰状的花，底下红、往上橙到金黄
    pts = []
    for y in range(15, 0, -1):
        x = round(7.5 + 2.6 * math.sin((15 - y) * 0.5))
        if pts and abs(x - pts[-1][0]) > 1:
            pts.append((pts[-1][0] + (1 if x > pts[-1][0] else -1), y))
        pts.append((x, y))
    for i, (x, y) in enumerate(pts):
        t = i / (len(pts) - 1)
        c.set(x, y, WOOD, 2 + round(t * 2), glint=True)
        if c.get(x + 1, y) is None:
            c.set(x + 1, y, WOOD, 1 + round(t * 2), glint=True)
    for i in range(3, len(pts) - 1, 3):
        x, y = pts[i]
        side = -1 if x >= 8 else 1
        bx = x + (2 if side > 0 else -1)
        c.set(bx, y, MOSS, 3, glint=True)
        c.set(bx + side, y - 1, MOSS, 5, glint=True)
        c.set(bx, y - 1, MOSS, 4, glint=True)
        c.set(bx + side, y, MOSS, 2, glint=True)
    for x, y in (pts[4], pts[9], pts[len(pts) - 2]):
        side = 1 if x < 8 else -1
        fx = x + (2 if side > 0 else -3)
        if ripe:
            sprite(c, FLAME, {"abcdef": ORANGE_GLOW, "ABCDEFG": RED}, fx, y - 3)
        else:
            c.set(fx + 1, y, MOSS, 2, glint=True)
            c.set(fx + 1, y - 1, MOSS, 3, glint=True)
    return c


@crop("gamma_weed", refs=("block/tall_grass_top", "block/torchflower"))
def gamma_weed(c: Canvas, ripe: bool):
    # 伽马草：一丛硬挺的黄绿色剑叶从根上散开；熟了中间抽出一根发光的穗，一粒一粒地交错着
    blades = ((6, 1, 6, -1), (6, 4, 2, -1), (9, 11, 2, 1), (10, 14, 6, 1), (7, 3, 10, -1), (9, 12, 10, 1))
    for bx, tx, ty, side in blades:
        if not ripe:
            ty = min(12, ty + 5)
        pts = seg(bx, 15, tx, ty)
        stroke(c, pts, 1, 5, GAMMA)
        for x, y in pts[:max(2, len(pts) // 3)]:
            if c.get(x - side, y) is None:
                c.set(x - side, y, GAMMA, 2 if side < 0 else 3, glint=True)
    if ripe:
        stroke(c, vrun(8, 15, 10), 2, 3, GAMMA)
        stroke(c, vrun(7, 15, 12), 1, 2, GAMMA)
        for i, y in enumerate(range(9, 0, -1)):
            lv = 3 + min(1, i // 4)
            c.set(7, y, GAMMA_GLOW, lv + (1 if y % 2 else 0), glint=True)
            c.set(8, y, GAMMA_GLOW, lv - (0 if y % 2 else 1), glint=True)
        c.set(7, 0, GAMMA_GLOW, 5, glint=True)
    else:
        stroke(c, vrun(8, 15, 12), 2, 3, GAMMA)
        c.set(8, 11, GAMMA, 5, glint=True)
    return c


MOULD_CAPS = (
    (4, 9, [".fee.", "feddc", "bCccb"]),
    (9, 6, [".ffed.", "feeddc", "bCcccb"]),
    (12, 10, [".ed.", "eddc", "bCcb"]),
    (7, 12, [".ed.", "cddc"]),
)


@crop("fungal_cluster", refs=("block/brown_mushroom", "block/crimson_fungus"))
def fungal_cluster(c: Canvas, ripe: bool):
    # 毒霉簇：几朵紫灰色的小菌盖立在淡色的细柄上，盖顶铺着一层黄绿的孢子粉；收过的只剩断了的菌柄
    for x, y, cap in MOULD_CAPS:
        top = y if ripe else 15 - (15 - y) // 3
        stroke(c, vrun(x, 15, top + 1), 3, 5, BONE)
        if ripe:
            w = len(cap[0])
            sprite(c, cap, {"abcdefg": MOULD, "ABCDEF": BONE}, x - w // 2, top - 1)
            for i in range(w - 2):
                if (i + x) % 2 == 0:
                    c.set(x - w // 2 + 1 + i, top - 1, SPORE, 5 - (i % 2), glint=True)
        else:
            c.set(x, top, BONE, 2, glint=True)
            c.set(x, top + 1, BONE, 4, glint=True)
    if ripe:
        for x, y in ((3, 5), (9, 2), (13, 6)):
            c.set(x, y, SPORE, 3, glint=True)
    return c


@crop("echinocactus", refs=("block/cactus_side", "block/cactus_top"))
def echinocactus(c: Canvas, ripe: bool):
    # 桶形仙人掌：竖棱一亮一暗的绿桶，棱上一排排黄刺；熟了顶上一圈绒毛和黄花；收过的顶被切掉，露出淡绿的肉
    r = 5.6 if ripe else 4.8
    cy = 15 - r + 0.6
    top = None
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / (r + 0.6), (y - cy) / r
            if dx * dx + dy * dy <= 1 and y <= 15:
                if top is None:
                    top = y
                ridge = x % 2 == 1
                u = (x - 7.5) / (r + 0.6)
                lv = 4 - round(u * 2) + (1 if ridge else -1)
                if y >= 14:
                    lv -= 1
                c.set(x, y, CACTUS, lv, glint=True)
                if ridge and (y + x) % 4 == 1 and y < 14:
                    c.set(x, y, HAZARD, 5 if u < 0.3 else 3, glint=True)
    for x, y in list(c.cells()):
        px = c.get(x, y)
        if px and px[0] is CACTUS and any(c.get(x + dx, y + dy) is None for dx, dy in ((1, 0), (0, 1))):
            c.set(x, y, CACTUS, 1, glint=True)
    if ripe:
        for x in range(5, 11):
            c.set(x, top, CREAM, 4 if x < 8 else 3, glint=True)
        sprite(c, [
            ".e..e.",
            "efdfed",
        ], {"abcdef": HAZARD}, 5, top - 2)
    else:
        for x in range(4, 12):
            c.set(x, top, CACTUS_FLESH, 4 if x < 8 else 3, glint=True)
            if c.get(x, top + 1):
                c.set(x, top + 1, CACTUS_FLESH, 2, glint=True)
        c.set(7, top, CACTUS_FLESH, 2, glint=True)
        c.set(8, top + 1, CACTUS_FLESH, 1, glint=True)
    return c


@crop("gravitino_host", refs=("block/weeping_vines", "block/chorus_flower"))
def gravitino_host(c: Canvas, ripe: bool):
    # 引力果丛：几根深紫色的弯茎，梢头往下勾，熟了吊着发光的品红色圆果；收过的勾上只剩一个小托
    stalks = (
        (vrun(5, 15, 10) + vrun(4, 9, 6) + [(3, 5), (2, 5), (1, 6)], (1, 8.4)),
        (vrun(8, 15, 5) + vrun(9, 4, 3) + [(10, 2), (11, 2), (12, 3)], (12, 5.4)),
        (vrun(10, 15, 11) + vrun(11, 10, 8) + [(12, 7), (13, 7), (14, 8)], (14, 10.4)),
    )
    for pts, (fx, fy) in stalks:
        stroke(c, pts, 1, 4, GRAVITINO)
        hx, hy = pts[-1]
        if ripe:
            ball(c, fx, fy, 1.6, 3, MAGENTA_GLOW, amp=1.4)
        else:
            c.set(hx, hy + 1, GRAVITINO, 5, glint=True)
            c.set(hx - 1, hy + 1, GRAVITINO, 3, glint=True)
    return c


@crop("cave_marrow", refs=("block/shroomlight", "block/brown_mushroom"))
def cave_marrow(c: Canvas, ripe: bool):
    # 洞穴髓球：贴着岩石长的一团奶白色球状菌体，表面有几个微微发光的亮点；收过的只剩根须和一颗小球
    if ripe:
        for x, y, r in ((3.0, 13.5, 1.5), (12.5, 13.5, 1.7), (5.0, 12.0, 2.6), (7.5, 8.5, 2.3), (10.5, 11.5, 3.0)):
            ball(c, x, y, r, 4, MARROW, amp=1.6)
            for qx, qy in c.disc_cells(x, y, r):
                d = math.hypot(qx - x, qy - y)
                if d > r - 0.6 and (qx - x) + (qy - y) > 0:
                    c.set(qx, qy, MARROW, 1, glint=True)
        for x, y in ((4, 11), (9, 10), (7, 7), (11, 13)):
            c.set(x, y, CREAM, 5, glint=True)
    else:
        for x0, x1 in ((4, 3), (7, 7), (10, 11), (12, 13)):
            stroke(c, seg(x0, 15, x1, 12), 1, 3, WOOD)
        ball(c, 7.5, 11.0, 1.4, 3, MARROW, amp=1.2)
    return c


@crop("candle_kelp", refs=("block/kelp", "block/kelp_plant"))
def candle_kelp(c: Canvas, ripe: bool):
    # 烛光海带：几片飘带样的叶子从柄上往上飘，两个像素宽、左右缓缓摆；熟了叶根上鼓着发光的琥珀色气囊
    fronds = ((5, 1, 0.0, 1.6), (8, 0, 1.4, 1.2), (11, 2, 2.6, 1.8), (3, 6, 4.0, 1.0), (13, 6, 5.1, 1.0))
    pods = ((5, 10), (8, 11), (11, 10), (3, 12), (13, 12))
    for x0, top, phase, sway in fronds:
        for y in range(15, top - 1, -1):
            t = (15 - y) / (15 - top)
            x = round(x0 + sway * math.sin(t * 2.4 + phase) * t)
            lv = 1 + round(t * 3) + (1 if y % 3 == 0 else 0)
            c.set(x, y, KELP, lv + 1, glint=True)
            if 0.12 < t < 0.92 and c.get(x + 1, y) is None:
                c.set(x + 1, y, KELP, lv - 1, glint=True)
    for x, y in pods[:4]:
        if ripe:
            ball(c, x, y, 1.3, 3, AMBER_GLOW, amp=1.3)
            c.set(x, y + 2, KELP, 1, glint=True)
        else:
            c.set(x, y + 1, KELP, 1, glint=True)
            c.set(x + 1, y + 1, KELP, 2, glint=True)
    return c
