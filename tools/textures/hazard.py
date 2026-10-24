"""局部危害（ADR-0034 §六）：间歇泉的结壳、喷口，熔岩弹、热光、孢子雾、尘卷风的贴图。

结壳照黄石公园的泉华画：一层层流下来凝住的梯田，每层上沿受光、下面一道暗影；表面有一粒粒的珠状泉华和小孔。
四种星球四种颜色：灼热星球的是灰白底上一片片硫黄，毒雾星球的盐华发绿、带着黄绿的菌膜，冰冻星球的霜华是冰蓝、
插着几根白的霜针，奇异星球的硅华是带粉紫的乳白，里面闪着几点青光。

喷口顶上是一口深井，井沿一圈湿的菌膜（热泉橙、毒泉黄绿、冷泉深蓝、异泉紫），外面一圈凸起的泉华唇；
四面是一层层的结壳，从顶上挂下几道颜色的流痕。

熔岩弹是一团裂开的黑壳，裂缝里透出橙红的光（发光层）；热光、孢子雾是柔和的一团，往边上渐渐透明；
尘卷风是一道道斜着的尘纹。孢子雾和尘卷风只画灰度，游戏里按孢子的黄绿、星球的沙土色染。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import (BASALT, BLUE, BONE, CYAN_GLOW, GLASS, GRAY, ICE, ORANGE, ORANGE_GLOW, RUST, VIOLET, VIOLET_GLOW, WHITE,
                     YELLOW_GLOW, register)
from planet import despeckle, field
from recipe import texture

# 硫华：带一点绿的硫黄；盐华：发绿的白；菌膜：黄绿；硅华：带粉紫的乳白
SULFUR = register(ramp("hazard_sulfur", 100, 0.130, n=7, lo=0.46, hi=0.94, shift=14, peak=0.6))
SALT = register(ramp("hazard_salt", 118, 0.030, n=7, lo=0.50, hi=0.95, shift=16))
BIOFILM = register(ramp("hazard_biofilm", 112, 0.110, n=6, lo=0.34, hi=0.82, shift=14, peak=0.6))
OPAL = register(ramp("hazard_opal", 322, 0.040, n=7, lo=0.50, hi=0.95, shift=26))

# 每种喷口：结壳的底色、结壳上的斑、井沿的菌膜、井里的颜色、点缀
KINDS = {
    "hot": {"base": BONE, "stain": SULFUR, "mat": ORANGE, "deep": BASALT, "fleck": RUST},
    "toxic": {"base": SALT, "stain": BIOFILM, "mat": BIOFILM, "deep": BASALT, "fleck": SULFUR},
    "cold": {"base": ICE, "stain": WHITE, "mat": BLUE, "deep": BASALT, "fleck": WHITE},
    "strange": {"base": OPAL, "stain": VIOLET, "mat": VIOLET, "deep": BASALT, "fleck": CYAN_GLOW},
}


def crust(rng: random.Random, kind: str, layered: float = 1.0, details: bool = True) -> Canvas:
    """结壳：一层层扇贝边的泉华梯田。每层的唇往外凸、受光，唇下一道暗影，再往下的坡面慢慢转亮；
    唇的走向按正弦起伏，再叠一点噪声。上下左右都接得上（只看在一层里的位置）。
    斑只染在坡面上；details 时再加几粒亮的珠状泉华、几个暗的小孔和每种的点缀。layered 越大唇越亮、影越深。"""
    k = KINDS[kind]
    c = Canvas()
    n = field(rng, ((4, 4), (8, 8)), (0.6, 0.4))
    phase = rng.random() * math.tau
    lip, shadow = round(2 * layered), -1 if layered < 1.3 else -2
    band = {}
    for x, y in c.cells():
        wave = 1.2 * math.sin(math.tau * x / 16 * 2 + phase) + 0.6 * math.sin(math.tau * x / 16 * 3 + phase * 1.7)
        # 一层高 16/3 格：往下 16 格正好三层，上下接得上
        t = ((y + wave + (n[y][x] - 0.5) * 1.6) * 3 / 16) % 1.0
        band[(x, y)] = t
        if t < 0.16:
            lv = 3 + lip
        elif t < 0.3:
            lv = 3 + shadow
        else:
            lv = 3 + round((t - 0.3) * 1.5 + (n[y][x] - 0.5) * 0.8)
        c.set(x, y, k["base"], lv)
    # 斑：成片的，只染坡面，保留明暗换成斑的颜色；冰冻星球的是更白的霜
    g = field(rng, ((4, 4), (8, 8)), (0.6, 0.4))
    for x, y in c.cells():
        if g[y][x] > 0.72 and band[(x, y)] >= 0.3:
            c.recolor(x, y, k["stain"], 1 if kind in ("cold", "strange") else 0)
    despeckle(c)
    if not details:
        return c
    # 珠状泉华：唇上亮的一点，右下一点影子
    lips = [p for p, t in band.items() if t < 0.16]
    for x, y in rng.sample(lips, min(4, len(lips))):
        c.set(x, y, c.get(x, y)[0], 6, glint=True)
        c.shift((x + 1) % 16, (y + 1) % 16, -1)
    # 小孔：坡面上暗的一点，下沿受光
    faces = [p for p, t in band.items() if 0.45 < t < 0.85]
    for x, y in rng.sample(faces, min(3, len(faces))):
        c.set(x, y, k["base"], 1, glint=True)
        c.shift(x, (y + 1) % 16, 1)
    fleck(c, rng, kind)
    return c


def fleck(c: Canvas, rng: random.Random, kind: str):
    """每种的点缀：硫华上几点锈红、盐华上几点硫黄、霜华上几根霜针、硅华里几点青光。"""
    k = KINDS[kind]
    if kind == "cold":
        for _ in range(3):
            x, y = rng.randrange(16), rng.randrange(16)
            dx = rng.choice((-1, 1))
            for i in range(3):
                c.set((x + dx * i) % 16, (y - i) % 16, WHITE, 6 - i, glint=True)
        return
    for _ in range(4 if kind != "strange" else 5):
        x, y = rng.randrange(16), rng.randrange(16)
        lv = 4 if kind == "strange" else 3
        c.set(x, y, k["fleck"], lv, glint=True)


@texture("block/hot_geyser_crust", rough=True, refs=("block/calcite", "block/dripstone_block"))
def hot_crust(rng):
    return crust(rng, "hot")


@texture("block/toxic_geyser_crust", rough=True, refs=("block/calcite",))
def toxic_crust(rng):
    return crust(rng, "toxic")


@texture("block/cold_geyser_crust", rough=True, refs=("block/packed_ice", "block/snow"))
def cold_crust(rng):
    return crust(rng, "cold")


@texture("block/strange_geyser_crust", rough=True, refs=("block/calcite", "block/amethyst_block"))
def strange_crust(rng):
    return crust(rng, "strange")


def vent_side(rng: random.Random, kind: str) -> Canvas:
    """喷口的四面：层次更分明的结壳，顶上一道湿的暗边，从顶上挂下几道菌膜颜色的细流痕：上面湿、颜色深，
    往下变浅、断断续续，最后淡成斑的颜色。"""
    k = KINDS[kind]
    c = crust(rng, kind, layered=1.4)
    for x in range(16):
        c.shift(x, 0, -1)
    for x in rng.sample(range(1, 15), 3):
        length = rng.randint(7, 14)
        for y in range(length):
            t = y / length
            if t > 0.55 and rng.random() < 0.35:
                continue
            if t < 0.7:
                c.set(x, y, k["mat"], 2 if t < 0.3 else 3)
            else:
                c.recolor(x, y, k["stain"], 0)
        c.set(x, 0, k["mat"], 2)
    return c


@texture("block/hot_geyser_vent_side", rough=True, refs=("block/calcite", "block/dripstone_block"))
def hot_vent_side(rng):
    return vent_side(rng, "hot")


@texture("block/toxic_geyser_vent_side", rough=True)
def toxic_vent_side(rng):
    return vent_side(rng, "toxic")


@texture("block/cold_geyser_vent_side", rough=True)
def cold_vent_side(rng):
    return vent_side(rng, "cold")


@texture("block/strange_geyser_vent_side", rough=True)
def strange_vent_side(rng):
    return vent_side(rng, "strange")


def vent_top(rng: random.Random, kind: str) -> Canvas:
    """喷口顶：中间一口深井（越往里越黑，水面反一两点光），井沿一圈湿的菌膜（里深外浅），外面一圈凸起的泉华唇。"""
    k = KINDS[kind]
    c = crust(rng, kind, details=False)
    cx = cy = 7.5
    wobble = field(rng, (4,), (1.0,))
    for x, y in c.cells():
        d = math.hypot(x - cx, y - cy) + (wobble[y][x] - 0.5) * 0.9
        if d < 3.0:
            c.set(x, y, k["deep"], 0 if d < 1.6 else 1)
        elif d < 4.5:
            c.set(x, y, k["mat"], 1 if d < 3.6 else 2)
        elif d < 6.0:
            # 泉华唇：凸起的一圈，朝左上的一侧亮，朝右下的一侧暗
            nx, ny = (x - cx) / max(d, 0.1), (y - cy) / max(d, 0.1)
            facing = -(nx * 0.7 + ny * 0.7)
            c.set(x, y, k["base"], 4 + round(facing * 1.4))
    # 水面的反光
    c.set(6, 6, GLASS, 3, glint=True)
    if kind == "strange":
        c.set(8, 8, VIOLET_GLOW, 3, glint=True)
        c.set(7, 8, VIOLET_GLOW, 2, glint=True)
    return c


@texture("block/hot_geyser_vent_top", tile=False, rough=True)
def hot_vent_top(rng):
    return vent_top(rng, "hot")


@texture("block/toxic_geyser_vent_top", tile=False, rough=True)
def toxic_vent_top(rng):
    return vent_top(rng, "toxic")


@texture("block/cold_geyser_vent_top", tile=False, rough=True)
def cold_vent_top(rng):
    return vent_top(rng, "cold")


@texture("block/strange_geyser_vent_top", tile=False, rough=True)
def strange_vent_top(rng):
    return vent_top(rng, "strange")


# ---- 实体

@texture("entity/hazard/lava_bomb", glow=True, refs=("block/magma", "entity/blaze"))
def lava_bomb(rng):
    """熔岩弹 16×16：边缘不齐的一团。黑壳裂成一块块（离两个最近的点差不多远的地方是缝），按球面打光，
    每块深浅略有不同；缝里透出橙红的光（发光层），越往中间越亮，正中一点黄白的熔芯。"""
    c = Canvas()
    cx = cy = 7.5
    edge = field(rng, (6,), (1.0,))
    seeds = [(cx + rng.uniform(-1.5, 1.5), cy + rng.uniform(-1.5, 1.5))]
    for i in range(6):
        a = i * math.tau / 6 + rng.uniform(-0.4, 0.4)
        r = rng.uniform(3.4, 5.2)
        seeds.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    tones = [rng.choice((-1, 0, 0, 1)) for _ in seeds]
    for x, y in c.cells():
        d = math.hypot(x - cx, y - cy)
        if d > 6.3 + (edge[y][x] - 0.5) * 1.4:
            continue
        dist = sorted((math.hypot(x - sx, y - sy), i) for i, (sx, sy) in enumerate(seeds))
        nx, ny = (x - cx) / 7.0, (y - cy) / 7.0
        nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
        light = -0.58 * nx - 0.62 * ny + 0.53 * nz
        if dist[1][0] - dist[0][0] < 0.85:
            c.set(x, y, ORANGE_GLOW, 5 if d < 2.2 else (4 if d < 4 else (3 if d < 5.4 else 2)), glint=True)
        else:
            c.set(x, y, BASALT, max(0, min(BASALT.top, 2 + round((light - 0.45) * 3) + tones[dist[0][1]])))
    c.set(7, 7, YELLOW_GLOW, 5, glint=True)
    c.set(8, 7, YELLOW_GLOW, 4, glint=True)
    return c


@texture("entity/hazard/lava_bomb_halo", refs=("particle/flame",))
def lava_bomb_halo(rng):
    """熔岩弹外面的热光 16×16：中间亮，往外颜色变暗、越来越透明。"""
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5) / 7.6
        if d >= 1:
            continue
        lv = 1 + round(3.4 * (1 - d) ** 1.4)
        c.set(x, y, ORANGE_GLOW, lv, glint=True, alpha=int(215 * (1 - d) ** 2))
    return c


@texture("entity/hazard/spore_fog", tinted=True, rough=True, refs=("particle/big_smoke_0", "particle/generic_0"))
def spore_fog(rng):
    """孢子雾 32×32：一团翻卷的雾，按噪声起伏，左上受光亮一些；边上渐渐透明；雾里一粒粒的孢子。只画灰度。"""
    c = Canvas(32, 32)
    f = field(rng, ((4, 4), (8, 8)), (0.65, 0.35), 32, 32)
    for x, y in c.cells():
        d = math.hypot(x - 15.5, y - 15.5) / 15.5
        v = f[y][x]
        a = (1 - d * d) * (0.45 + 0.75 * v)
        if d >= 1 or a <= 0.06:
            continue
        lit = (15.5 - x + 15.5 - y) / 62
        lv = 2 + round(v * 3 + lit * 2)
        c.set(x, y, GRAY, lv, alpha=int(200 * min(1.0, a) ** 1.3))
    for _ in range(18):
        x, y = rng.randrange(4, 28), rng.randrange(4, 28)
        if c.get(x, y):
            c.set(x, y, GRAY, rng.choice((1, 6)), glint=True, alpha=min(255, c.alpha.get((x, y), 255) + 50))
    return c


@texture("entity/hazard/dust_devil", tinted=True, rough=True, refs=("entity/breeze/breeze_wind",))
def dust_devil(rng):
    """尘卷风 32×32：一道道斜着往上卷的尘纹，浓淡按噪声起伏，纹和纹之间透空。上下左右都接得上（渲染器绕着柱子铺）。
    只画灰度。"""
    c = Canvas(32, 32)
    f = field(rng, ((4, 8), (8, 4)), (0.6, 0.4), 32, 32)
    for x, y in c.cells():
        # 斜率 1/2：往下走 32 格横着挪 16 格，正好两道纹，上下接得上
        s = ((x + y * 0.5) / 8 + f[y][x] * 0.6) % 1.0
        band = math.sin(s * math.pi) ** 2
        a = band * (0.35 + 0.65 * f[y][x])
        if a < 0.12:
            continue
        lv = 3 + round(band * 2 + f[y][x])
        c.set(x, y, GRAY, lv, alpha=int(230 * min(1.0, a)))
    return c
