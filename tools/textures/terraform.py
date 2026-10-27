"""星球改造（ADR-0036 §六）：大气释放塔、轨道反射镜的贴图。

释放塔照化工厂的高压气站画：白漆的钛合金塔身，正面一块压力表、一排阀门指示灯和一小块流量屏，侧面两根竖的高压管、
底下一道警示条；顶上是喷口，一圈圈法兰围着黑的出气孔。运转时屏幕和阀门灯亮青绿。

轨道反射镜是半格高的镜板，照祖布林和麦凯的方案用镀铝的薄膜：顶面四块镜面，镀铝膜的银白里映着一点天蓝，几道斜的反光；
切成遮阳板时换成深色的消光膜。四周是钛合金的边框，底面是金色的多层隔热毯和斜撑的桁架。镜板一块块拼起来，边框连成网格。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import ramp
from palette import (CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN_GLOW, HAZARD, IRON, MOSS, RED, SCREEN, SOIL, STEEL, TEAL,
                     TITANIUM, WHITE, register)
from parts import bolts, casing, status_lights
from recipe import texture

# 镀铝膜：带一点蓝的银白；消光膜：发紫的黑
FILM = register(ramp("terraform_film", 214, 0.030, n=7, lo=0.50, hi=0.98, shift=12))
MATTE = register(ramp("terraform_matte", 270, 0.030, n=6, lo=0.12, hi=0.40, shift=8))


# ---- 大气释放塔

def tower_shell(rng: random.Random) -> Canvas:
    """白漆的塔身：外圈深色的边，里面白漆面板，上左受光，偶尔掉漆露出钢。"""
    c = Canvas()
    c.fill(WHITE, 3)
    for x, y in c.cells(2, 2, 13, 13):
        k = x + y + ((x * 5 + y * 3) % 3) - 1
        if k < 9:
            c.shift(x, y, 1)
        elif k > 20:
            c.shift(x, y, -1)
    for _ in range(6):
        x, y = rng.randint(2, 12), rng.randint(3, 12)
        for i in range(rng.randint(2, 3)):
            c.shift(x + i, y, -1)
    c.frame(0, 0, 15, 15, STEEL, 2)
    c.rect(1, 1, 14, 1, WHITE, 5)
    c.rect(1, 1, 1, 14, WHITE, 5)
    c.rect(2, 14, 14, 14, WHITE, 2)
    c.rect(14, 2, 14, 14, WHITE, 2)
    c.edge_wear(rng, 1, 1, 14, 14, 0.12)
    c.grime(rng, 3, 10, 13, 13, 0.06)
    return c


def gauge(c: Canvas, cx: float, cy: float, rad: float, on: bool):
    """压力表：钢圈里白的表盘，一根指针；运转时指针打到右上。"""
    for x, y in c.disc_cells(cx, cy, rad):
        dist = math.hypot(x - cx, y - cy)
        if dist > rad - 1:
            toward = ((x - cx) * -0.58 + (y - cy) * -0.62) / max(0.01, dist)
            c.set(x, y, STEEL, 5 if toward > 0.3 else 3)
        else:
            c.set(x, y, WHITE, 5)
    angle = -0.6 if on else 2.2
    for i in range(1, int(rad)):
        c.set(round(cx + math.cos(angle) * i * 0.8), round(cy - math.sin(angle) * i * 0.8), GRAPHITE, 1)
    c.set(round(cx), round(cy), GRAPHITE, 2)


def tower_face(rng: random.Random, on: bool) -> Canvas:
    c = tower_shell(rng)
    status_lights(c, on)
    gauge(c, 5.0, 6.5, 2.9, on)
    # 流量屏：一根随着放气涨起来的条
    c.frame(9, 4, 12, 9, GRAPHITE, 1)
    c.rect(10, 5, 11, 8, SCREEN, 1)
    if on:
        for y in range(6, 9):
            c.set(10, y, CYAN_GLOW, 4 if y == 6 else 3, glint=True)
            c.set(11, y, CYAN_GLOW, 3, glint=True)
    else:
        c.set(10, 5, SCREEN, 2)
    # 一排阀门：手轮和下面的指示灯
    for x in (4, 7, 10):
        c.set(x, 11, STEEL, 5)
        c.set(x - 1, 11, STEEL, 3)
        c.set(x + 1, 11, STEEL, 3)
        c.set(x, 10, STEEL, 4)
        c.set(x, 12, GREEN_GLOW if on else SCREEN, 5 if on else 2, glint=True)
    bolts(c, 1, 5, WHITE)
    return c


@texture("block/atmosphere_release_tower_front", refs=("block/blast_furnace_front",))
def atmosphere_release_tower_front(rng):
    return tower_face(rng, False)


@texture("block/atmosphere_release_tower_front_on", glow=True)
def atmosphere_release_tower_front_on(rng):
    return tower_face(rng, True)


@texture("block/atmosphere_release_tower_side")
def atmosphere_release_tower_side(rng):
    """侧面：两根竖的高压管，每隔几格一道箍，底下一道黄黑的警示条。"""
    c = tower_shell(rng)
    for x0 in (3, 9):
        c.cylinder(x0, 2, x0 + 3, 12, STEEL, 4)
        for y in (4, 9):
            c.hline(x0 - 1, x0 + 4, y, STEEL, 6)
            c.hline(x0 - 1, x0 + 4, y + 1, STEEL, 3)
    for x in range(1, 15):
        c.set(x, 13, HAZARD if (x // 2) % 2 == 0 else GRAPHITE, 3 if (x // 2) % 2 == 0 else 1)
    return c


@texture("block/atmosphere_release_tower_top", refs=("block/smoker_top",))
def atmosphere_release_tower_top(rng):
    """顶上的喷口：白漆的顶板上一圈圈法兰，中间黑的出气孔，四个角上螺栓。"""
    c = tower_shell(rng)
    cx = cy = 7.5
    for x, y in c.disc_cells(cx, cy, 5.6):
        dist = math.hypot(x - cx, y - cy)
        toward = ((x - cx) * -0.58 + (y - cy) * -0.62) / max(0.01, dist)
        if dist > 4.6:
            c.set(x, y, STEEL, 5 if toward > 0.3 else 2 if toward < -0.3 else 4)
        elif dist > 3.4:
            c.set(x, y, STEEL, 3 if toward > 0 else 5)
        elif dist > 2.4:
            c.set(x, y, GRAPHITE, 2 if toward > 0 else 3)
        else:
            c.set(x, y, GRAPHITE, 0)
    for angle in range(0, 360, 45):
        a = math.radians(angle)
        c.set(round(cx + math.cos(a) * 4.0), round(cy + math.sin(a) * 4.0), STEEL, 6)
    bolts(c, 1, 5, WHITE)
    return c


@texture("block/atmosphere_release_tower_bottom")
def atmosphere_release_tower_bottom(rng):
    """底面：钢的底座，中间进气的格栅。"""
    c = casing(rng, 3)
    c.grille(4, 4, 11, 11, STEEL, 3)
    return c


# ---- 轨道反射镜

def mirror_frame(c: Canvas):
    """钛合金的边框：上左亮、下右暗，拼起来连成网格。"""
    c.hline(0, 15, 0, TITANIUM, 5)
    c.vline(0, 0, 15, TITANIUM, 5)
    c.hline(1, 15, 15, TITANIUM, 2)
    c.vline(15, 1, 15, TITANIUM, 3)


def film(c: Canvas, x0: int, y0: int, x1: int, y1: int, rng: random.Random):
    """一块镜面：银白的镀铝膜往右下渐暗，左上映一点天蓝，两道斜的反光，膜上几处细的褶。"""
    for x, y in c.cells(x0, y0, x1, y1):
        t = (x - x0 + y - y0) / max(1, (x1 - x0) + (y1 - y0))
        c.set(x, y, FILM, 5 - round(t * 3))
        if x - x0 + y - y0 <= 2:
            c.set(x, y, GLASS, 4)
    for x, y in c.cells(x0, y0, x1, y1):
        d = (x - x0) - (y - y0)
        if d in (1, 2) and (x + y) % 5 != 0:
            c.set(x, y, FILM, FILM.top, glint=True)
    for _ in range(2):
        x, y = rng.randint(x0, x1), rng.randint(y0, y1)
        c.shift(x, y, -1)


@texture("block/orbital_mirror_top", refs=("block/glass", "block/iron_block"))
def orbital_mirror_top(rng):
    c = Canvas()
    c.fill(TITANIUM, 3)
    for x0, y0 in ((1, 1), (8, 1), (1, 8), (8, 8)):
        film(c, x0, y0, x0 + 6, y0 + 6, rng)
    # 中间的十字骨架
    c.hline(1, 14, 7, TITANIUM, 4)
    c.vline(7, 1, 14, TITANIUM, 4)
    c.set(7, 7, TITANIUM, 6, glint=True)
    mirror_frame(c)
    return c


@texture("block/orbital_mirror_shade_top")
def orbital_mirror_shade_top(rng):
    """遮阳板：深色的消光膜，几乎不反光，只在左上角泛一点光。"""
    c = Canvas()
    c.fill(TITANIUM, 3)
    for x0, y0 in ((1, 1), (8, 1), (1, 8), (8, 8)):
        for x, y in c.cells(x0, y0, x0 + 6, y0 + 6):
            t = x - x0 + y - y0
            c.set(x, y, MATTE, 3 if t <= 1 else 2 if t <= 8 else 1)
    c.hline(1, 14, 7, TITANIUM, 4)
    c.vline(7, 1, 14, TITANIUM, 4)
    c.set(7, 7, TITANIUM, 6, glint=True)
    mirror_frame(c)
    return c


@texture("block/orbital_mirror_side")
def orbital_mirror_side(rng):
    """侧边：镜板只有半格高，用第 8–15 行。钛合金的边梁，上沿一线镜面的亮边，中间一排减重孔。"""
    c = Canvas()
    c.fill(TITANIUM, 3)
    c.hline(0, 15, 8, FILM, 6)
    c.hline(0, 15, 9, TITANIUM, 5)
    for x in range(1, 16, 4):
        c.rect(x, 11, x + 1, 12, GRAPHITE, 1)
        c.set(x, 11, GRAPHITE, 0)
    c.hline(0, 15, 14, TITANIUM, 2)
    c.hline(0, 15, 15, GRAPHITE, 2)
    # 上半张（整格用不到）照着边梁的样子，免得别处取到时是一片空
    for y in range(0, 8):
        for x in range(16):
            c.set(x, y, TITANIUM, 3 if y % 4 else 4)
    return c


@texture("block/orbital_mirror_bottom")
def orbital_mirror_bottom(rng):
    """底面：金色的多层隔热毯，上面斜撑的钛合金桁架。"""
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, GOLD_FOIL, 3 if (x * 3 + y * 5) % 7 > 2 else 2)
    for _ in range(6):
        x, y = rng.randint(1, 13), rng.randint(1, 13)
        c.shift(x, y, 1)
        c.shift(x + 1, y, 1)
    for i in range(16):
        c.set(i, i, TITANIUM, 4)
        c.set(15 - i, i, TITANIUM, 3)
    c.hline(0, 15, 7, IRON, 4)
    c.vline(7, 0, 15, IRON, 4)
    mirror_frame(c)
    return c


# ---- 生态播种站
#
# 照实验室的培养设备和温室的喷灌头画：钢的机身，正面一个圆的观察窗看得到里面绿色的培养液，旁边一块读数屏，下面一排
# 放菌种管的卡槽；侧面是带液位窗的储水罐；顶上是喷头，四根喷臂，外面一圈生长灯。运转时培养液、屏幕、生长灯亮。

LICHEN_GRAY = register(ramp("lichen_gray", 140, 0.050, n=6, lo=0.48, hi=0.90, shift=14))
LICHEN_ORANGE = register(ramp("lichen_orange", 60, 0.150, n=6, lo=0.40, hi=0.86, shift=14, peak=0.6))


def culture_window(c: Canvas, cx: float, cy: float, rad: float, on: bool):
    """圆的观察窗：钢圈、玻璃，里面下半是绿的培养液，液面上一道亮线；运转时培养液发光，冒几个气泡。"""
    for x, y in c.disc_cells(cx, cy, rad):
        dist = math.hypot(x - cx, y - cy)
        toward = ((x - cx) * -0.58 + (y - cy) * -0.62) / max(0.01, dist)
        if dist > rad - 1:
            c.set(x, y, STEEL, 5 if toward > 0.3 else 2)
        elif y >= cy - 0.5:
            if on:
                c.set(x, y, GREEN_GLOW, 4 if y > cy + 1 else 5, glint=True)
            else:
                c.set(x, y, MOSS, 3 if y > cy + 1 else 4)
        else:
            c.set(x, y, GLASS, 4)
    c.set(round(cx - rad + 2), round(cy - rad + 2), GLASS, GLASS.top, glint=True)
    if on:
        c.set(round(cx), round(cy + 1), GREEN_GLOW, 6, glint=True)
        c.set(round(cx + 1), round(cy + 2), GREEN_GLOW, 6, glint=True)


def seeder_face(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng, 4)
    status_lights(c, on)
    culture_window(c, 5.5, 7.0, 3.6, on)
    # 读数屏：一条生长曲线
    c.frame(10, 4, 13, 9, GRAPHITE, 1)
    c.rect(11, 5, 12, 8, SCREEN, 1)
    if on:
        for x, y in ((11, 8), (11, 7), (12, 6), (12, 5)):
            c.set(x, y, GREEN_GLOW, 4, glint=True)
    else:
        c.set(11, 5, SCREEN, 2)
    # 菌种管的卡槽：深色的槽里三根管子的盖
    c.rect(3, 12, 12, 12, GRAPHITE, 1)
    for x in (4, 7, 10):
        c.set(x, 12, MOSS, 4, glint=True)
        c.set(x + 1, 12, MOSS, 3, glint=True)
    return c


@texture("block/ecology_seeder_front", refs=("block/composter_side", "block/brewing_stand"))
def ecology_seeder_front(rng):
    return seeder_face(rng, False)


@texture("block/ecology_seeder_front_on", glow=True)
def ecology_seeder_front_on(rng):
    return seeder_face(rng, True)


@texture("block/ecology_seeder_side")
def ecology_seeder_side(rng):
    """侧面：储水罐，竖着一道液位窗（下面是水，上面是空的玻璃），旁边一根进水管和阀门。"""
    c = casing(rng, 4)
    c.cylinder(3, 3, 9, 13, STEEL, 4)
    c.rect(5, 4, 6, 12, GRAPHITE, 1)
    for y in range(5, 12):
        if y >= 8:
            c.set(5, y, TEAL, 4 if y == 8 else 3)
            c.set(6, y, TEAL, 3 if y == 8 else 2)
        else:
            c.set(5, y, GLASS, 4)
            c.set(6, y, GLASS, 3)
    c.set(5, 8, TEAL, TEAL.top, glint=True)
    c.vline(12, 2, 13, STEEL, 5)
    c.vline(13, 2, 13, STEEL, 3)
    c.rect(11, 6, 13, 7, RED, 3)
    c.set(11, 6, RED, 4, glint=True)
    return c


def seeder_top(rng: random.Random, on: bool) -> Canvas:
    """顶上：凹下去的深色台面，喷头在中间，四根喷臂伸向四角；四边各一条生长灯。"""
    c = casing(rng, 3)
    c.recess(2, 2, 13, 13, GRAPHITE, 2)
    for i in range(2, 6):
        c.set(7 - i + 1, 7 - i + 1, STEEL, 6)
        c.set(8 + i - 1, 7 - i + 1, STEEL, 5)
        c.set(7 - i + 1, 8 + i - 1, STEEL, 5)
        c.set(8 + i - 1, 8 + i - 1, STEEL, 3)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, STEEL, 4, glint=True)
    c.disc(7.5, 7.5, 1.7, STEEL, 4)
    c.set(7, 7, STEEL, 6, glint=True)
    c.set(8, 8, STEEL, 2)
    for x in range(6, 10):
        for y in (3, 12):
            c.set(x, y, GREEN_GLOW if on else SCREEN, 5 if on else 2, glint=True)
    for y in range(6, 10):
        for x in (3, 12):
            c.set(x, y, GREEN_GLOW if on else SCREEN, 5 if on else 2, glint=True)
    bolts(c, 1, 4)
    return c


@texture("block/ecology_seeder_top", refs=("block/composter_top",))
def ecology_seeder_top(rng):
    return seeder_top(rng, False)


@texture("block/ecology_seeder_top_on", glow=True)
def ecology_seeder_top_on(rng):
    return seeder_top(rng, True)


@texture("block/ecology_seeder_bottom")
def ecology_seeder_bottom(rng):
    """底面：钢的底座，中间排水的格栅。"""
    c = casing(rng, 3)
    c.recess(4, 4, 11, 11, STEEL, 2)
    for y in range(5, 11, 2):
        c.hline(5, 10, y, GRAPHITE, 1)
    return c


# ---- 地衣
#
# 照现实里岩石上的壳状地衣画，俯视：灰绿的像茶渍衣、梅衣，一块块斑块的边缘深一圈，上面散着褐色的小盘（子囊盘）；
# 橙黄的像石黄衣，裂片从中间往外放射，上面是深橙色的杯状子囊盘。斑块之间是透明的，露出底下的岩石。

def lichen_crust(c: Canvas, rng: random.Random, ramp_, cx: float, cy: float, r: float, lobes: int = 0):
    """一块地衣：外缘一圈暗，里面左上亮；lobes > 0 时边缘是一圈圆的裂片，中间老的部分暗一点。"""
    cells = set()
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            rr = r
            if lobes:
                rr = r * (0.84 + 0.16 * math.cos(math.atan2(dy, dx) * lobes + cx))
            if math.hypot(dx, dy * 1.1) <= rr + rng.uniform(-0.5, 0.35):
                cells.add((x, y))
    for x, y in cells:
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            lv = 1
        else:
            lv = 4 if (x - cx) + (y - cy) < -0.8 else 3
            if lobes and math.hypot(x - cx, y - cy) < r * 0.35:
                lv = 2
        c.set(x, y, ramp_, lv, glint=True)


@texture("block/gray_lichen", refs=("block/glow_lichen", "block/moss_carpet"))
def gray_lichen(rng):
    c = Canvas()
    for cx, cy, r in ((3.5, 3.5, 3.3), (11.5, 3.5, 2.8), (5.5, 11.0, 3.6), (12.5, 11.5, 3.0), (8.5, 7.0, 1.8),
                      (1.0, 14.5, 1.4), (15.0, 7.5, 1.3)):
        lichen_crust(c, rng, LICHEN_GRAY, cx, cy, r)
    # 褐色的子囊盘
    for x, y in ((3, 2), (6, 10), (5, 11), (12, 11), (11, 4)):
        c.set(x, y, SOIL, 3, glint=True)
        c.set(x + 1, y + 1, SOIL, 2, glint=True)
    return c


@texture("block/orange_lichen", refs=("block/glow_lichen", "block/orange_terracotta"))
def orange_lichen(rng):
    c = Canvas()
    for cx, cy, r, lobes in ((4.5, 4.5, 4.0, 9), (11.5, 10.5, 3.8, 8), (12.0, 3.0, 2.3, 6), (3.5, 12.5, 2.5, 7), (8.0, 15.0, 1.2, 5)):
        lichen_crust(c, rng, LICHEN_ORANGE, cx, cy, r, lobes)
    # 深橙色的杯状子囊盘，杯沿亮一级
    for x, y in ((4, 4), (6, 5), (11, 10), (12, 12), (3, 12)):
        c.set(x, y, LICHEN_ORANGE, 0, glint=True)
        c.set(x - 1, y, LICHEN_ORANGE, 5, glint=True)
    return c
