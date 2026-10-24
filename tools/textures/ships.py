"""飞船零件、空间站和货船的贴图（ADR-0031）。

- 船体板：航天器的蒙皮，两块板错缝拼成一格，缝是一道暗线加一道受光的唇边，沿缝一排铆钉；
  上下左右都和相邻的方块接得上。16 种染料各一条色阶，按染料的颜色和明暗生成。
- 引擎、推进器、曲速引擎、武器：Mk.I 是钢和石墨的机壳，Mk.II 是深色的钛合金配金色饰条，
  背面右下角两道金杠。喷口、炮口、发光层贴在模型的自发光元素上，当光源画：中间最亮、往外一圈圈暗下去。
- 推进器的喷管、炮管只露出贴图的几块（模型里每个面的 uv 不一样），按"离喷口多远"画回火色和散热环。
- 空间站：浅色的外壳、深色的内饰、青色的灯和屏幕，交易相关的点缀金色；货船是深石板灰的装甲。
"""
from __future__ import annotations

import math
import random
import zlib

from canvas import Canvas
from color import ramp, rgb_to_oklch
from palette import (AMBER, AMBER_GLOW, BASALT, COPPER, ORANGE, RUST, BLUE, BLUE_GLOW, BRASS, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, GREEN, GREEN_GLOW,
                     HAZARD, MAGENTA_GLOW, ORANGE_GLOW, RED, RED_GLOW, SCREEN, STEEL, TEAL, VIOLET, VIOLET_GLOW,
                     WHITE, WHITE_GLOW, YELLOW_GLOW, register)
from recipe import texture

# 这一组自己用的色阶
ALLOY = register(ramp("ships_alloy", 250, 0.030, n=7, lo=0.20, hi=0.70, shift=14))        # Mk.II 的深色钛合金
SLATE = register(ramp("ships_slate", 236, 0.024, n=7, lo=0.22, hi=0.74, shift=16))        # 货船的装甲
WARP = register(ramp("ships_warp", 296, 0.040, n=7, lo=0.18, hi=0.62, shift=10))          # 曲速引擎的紫灰机壳
FABRIC = register(ramp("ships_fabric", 258, 0.032, n=6, lo=0.20, hi=0.56, shift=10))      # 座椅的布面
FOAM = register(ramp("ships_foam", 56, 0.120, n=7, lo=0.30, hi=0.84, shift=14, peak=0.5))  # 燃料罐的隔热泡沫

# 原版 DyeColor 的贴图颜色
DYES = {
    "white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA,
    "yellow": 0xFED83D, "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52,
    "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
    "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21,
}


def dye_ramp(name: str, color: int):
    """按染料的颜色生成一条船体漆的色阶：底色那一级接近染料本身的亮度（太亮太暗的往中间收），彩度在那附近最高。
    黑漆的色阶往上多留一点，螺钉和缝的唇边才亮得起来。"""
    L, C, h = rgb_to_oklch(((color >> 16) & 255, (color >> 8) & 255, color & 255))
    target = min(0.90, max(0.32, L))
    hi = min(0.97, target + (0.20 if target < 0.35 else 0.17))
    lo = max(0.17, target - 0.30)
    return register(ramp(f"ships_dye_{name}", h, max(0.010, min(0.17, C * 0.95)), n=7, lo=lo, hi=hi, shift=12,
                         peak=0.66, fade=0.5))


DYE_RAMPS = {name: dye_ramp(name, color) for name, color in DYES.items()}


# ---- 小工具 ----

def screw(c: Canvas, x: int, y: int, r, base: int):
    """一个像素的沉头螺钉：比周围暗一级，右下受光的一侧亮一点。"""
    c.set(x, y, r, base - 2, glint=True)
    if c.get(x + 1, y):
        c.set(x + 1, y, r, base + 1, glint=True)


def stud(c: Canvas, x: int, y: int, r, base: int):
    """一个像素的铆钉头，右下一点影子。"""
    c.set(x, y, r, base + 1, glint=True)
    c.set(x + 1, y + 1, r, base - 1, glint=True)


def scratch(c: Canvas, rng: random.Random, x0, y0, x1, y1, n: int = 2, d: int = 1):
    """几道短的划痕：两三个像素斜着，漆面被刮亮。"""
    for _ in range(n):
        x, y = rng.randint(x0, x1 - 2), rng.randint(y0, y1 - 1)
        dx = rng.choice((1, -1))
        for i in range(rng.randint(2, 3)):
            c.shift(x + i, y + (i if dx > 0 else -i) // 2, d)


def seam_h(c: Canvas, y: int, r, base: int, x0: int = 0, x1: int = 15):
    """一道横缝：暗线下面跟一道受光的唇边。"""
    c.hline(x0, x1, y, r, base - 2)
    c.hline(x0, x1, y + 1, r, base + 1)


def seam_v(c: Canvas, x: int, r, base: int, y0: int = 0, y1: int = 15):
    c.vline(x, y0, y1, r, base - 2)
    c.vline(x + 1, y0, y1, r, base + 1)


def end_distance(x: int, y: int, top: tuple, west: tuple, east: tuple, length: int = 6) -> int | None:
    """管子侧面贴图上一点离喷口（炮口）多远。模型里上下面用贴图上方一块（x 在 top 里，y 从 0 往下是从喷口往里），
    西面用左边一块（y 在 west 里，x 从 0 往右），东面用右边一块（y 在 east 里，x 从 15 往左）；管子长 length。
    不在这三块里的像素模型用不到，返回 None。"""
    ds = []
    if top[0] <= x <= top[1] and y < length:
        ds.append(y)
    if west[0] <= y <= west[1] and x < length:
        ds.append(x)
    if east[0] <= y <= east[1] and 15 - x < length:
        ds.append(15 - x)
    return min(ds) if ds else None


def radial(c: Canvas, cx: float, cy: float, bands: list, outside=None):
    """同心圆：bands 是 [(半径上限, 色阶, 级数), ...] 从里往外；outside 是圆外的 (色阶, 级数)。"""
    for y in range(c.h):
        for x in range(c.w):
            d = math.hypot(x - cx, y - cy)
            for rad, r, lv in bands:
                if d < rad:
                    c.set(x, y, r, lv)
                    break
            else:
                if outside:
                    c.set(x, y, outside[0], outside[1])


# ---- 船体板 ----

def hull_plate(rng: random.Random, r, base: int = 4) -> Canvas:
    """一格是一块面板，中间一道横缝分成上下两片；缝在方块的下边和右边，和相邻的方块接成连续的网格。
    每片四角一颗铆钉，漆面有一两团轻微的深浅，偶尔一道划痕。"""
    c = Canvas()
    c.fill(r, base)
    # 每片板的下沿暗一级，看得出两片板微微鼓起
    c.hline(1, 14, 6, r, base - 1)
    c.hline(1, 14, 14, r, base - 1)
    c.clusters(rng, 3, 2, 12, 4, -1, 1, 3)
    c.clusters(rng, 3, 10, 12, 12, -1, 1, 3)
    scratch(c, rng, 3, 3, 12, 12, 1)
    c.hline(0, 15, 0, r, base + 1)
    c.vline(0, 0, 15, r, base + 1)
    c.hline(0, 15, 15, r, base - 2)
    c.vline(15, 0, 15, r, base - 2)
    seam_h(c, 7, r, base, 1, 14)
    c.set(0, 7, r, base - 1)
    c.set(15, 8, r, base - 1)
    c.set(15, 15, r, base - 4)
    c.set(15, 0, r, base)
    c.set(0, 15, r, base)
    for x, y in ((2, 2), (12, 2), (2, 5), (12, 5), (2, 10), (12, 10), (2, 13), (12, 13)):
        stud(c, x, y, r, base)
    return c


def _register_plating():
    for name, r in DYE_RAMPS.items():
        # 白漆用亮一级的底色，不然看着发灰；黑漆用暗一级，和灰漆分得开
        def fn(rng, r=r, base={"white": 5, "black": 3}.get(name, 4)):
            return hull_plate(rng, r, base)
        texture(f"block/hull_plating_{name}", refs=("block/iron_block", "block/white_concrete"))(fn)


_register_plating()


@texture("block/alloy_hull_plating", refs=("block/netherite_block",))
def alloy_hull_plating(rng):
    """合金船体板：四块凸起的装甲方砖，横竖交替的加强筋（像编织），四块交汇的地方一颗金色螺栓。"""
    c = Canvas()
    c.fill(ALLOY, 3)
    for i, (x0, y0) in enumerate(((0, 0), (8, 0), (0, 8), (8, 8))):
        x1, y1 = x0 + 7, y0 + 7
        horizontal = i in (0, 3)
        for x, y in c.cells(x0 + 1, y0 + 1, x1 - 1, y1 - 1):
            k = ((y - y0) if horizontal else (x - x0)) % 3
            c.set(x, y, ALLOY, 4 if k == 1 else (2 if k == 0 else 3))
        c.bevel(x0, y0, x1, y1, 2, -2)
        c.set(x1, y1, ALLOY, 0)
    c.edge_wear(rng, 0, 0, 15, 15, 0.12)
    # 交汇处的金色螺栓压在缝上
    c.rect(6, 6, 9, 9, ALLOY, 1)
    c.bolt(7, 7, GOLD_FOIL, 3)
    c.set(6, 6, ALLOY, 4)
    return c


@texture("block/freighter_plate", refs=("block/iron_block", "block/polished_deepslate"))
def freighter_plate(rng):
    """货船装甲板：四块凸起的装甲拼成一格，接缝深，每块沿边一圈铆钉，表面有微陨石打出的小坑。"""
    c = Canvas()
    c.fill(SLATE, 3)
    for x0, y0 in ((0, 0), (8, 0), (0, 8), (8, 8)):
        x1, y1 = x0 + 7, y0 + 7
        c.rect(x0, y0, x1, y1, SLATE, 3)
        c.clusters(rng, x0 + 2, y0 + 2, x1 - 2, y1 - 2, -1, 1, 3)
        c.bevel(x0, y0, x1, y1, 2, -2)
        c.set(x1, y1, SLATE, 0)
        for x, y in ((x0 + 2, y0 + 2), (x0 + 5, y0 + 2), (x0 + 2, y0 + 5), (x0 + 5, y0 + 5)):
            stud(c, x, y, SLATE, 3)
    # 微陨石坑：一个暗点，右下一个亮点
    for _ in range(4):
        x, y = rng.randint(1, 13), rng.randint(1, 13)
        if c.level(x, y) == 3 and c.level(x + 1, y + 1) == 3:
            c.set(x, y, SLATE, 1, glint=True)
            c.set(x + 1, y + 1, SLATE, 4, glint=True)
    return c


# ---- 空间站的建材 ----

@texture("block/station_hull", refs=("block/white_concrete", "block/quartz_block"))
def station_hull(rng):
    """外壳：白色的面板，中间一道横缝，缝两边各一排沉头螺钉；方块边上接成连续的缝。"""
    c = Canvas()
    c.fill(WHITE, 5)
    c.clusters(rng, 2, 2, 13, 5, -1, 2, 3)
    c.clusters(rng, 2, 10, 13, 13, -1, 2, 3)
    c.hline(0, 15, 0, WHITE, 6)
    c.vline(0, 0, 15, WHITE, 6)
    c.hline(1, 14, 6, WHITE, 4)
    c.hline(1, 14, 14, WHITE, 4)
    c.hline(0, 15, 15, WHITE, 2)
    c.vline(15, 0, 15, WHITE, 2)
    seam_h(c, 7, WHITE, 5, 1, 14)
    c.set(0, 7, WHITE, 4)
    c.set(15, 8, WHITE, 3)
    for x in (3, 8, 12):
        screw(c, x, 2, WHITE, 5)
        screw(c, x, 10, WHITE, 5)
    c.set(15, 15, WHITE, 1)
    return c


@texture("block/station_floor", refs=("block/iron_bars", "block/iron_trapdoor"))
def station_floor(rng):
    """地板：钢格栅，横筋比竖筋亮（光从上来），每个格子底下透出一点青色的灯光。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            gx, gy = x % 4, y % 4
            if gy == 0 and gx == 0:
                c.set(x, y, STEEL, 5, glint=True)
            elif gy == 0:
                c.set(x, y, STEEL, 4)
            elif gx == 0:
                c.set(x, y, STEEL, 3)
            elif gy == 1:
                c.set(x, y, GRAPHITE, 0)       # 横筋底下的影子
            elif gx == 2 and gy == 2:
                c.set(x, y, CYAN_GLOW, 0, glint=True)      # 底下的灯
            else:
                c.set(x, y, GRAPHITE, 1)
    # 筋上的磨损：踩亮的几段
    for _ in range(5):
        x, y = rng.randrange(1, 16), rng.randrange(0, 4) * 4
        c.shift(x, y, 1)
    return c


@texture("block/station_glass", refs=("block/glass", "block/light_blue_stained_glass"))
def station_glass(rng):
    """窗户：淡蓝的半透明玻璃，白色的窗框（上、左受光），两道斜的反光。"""
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, GLASS, 3, alpha=78)
    # 靠框的一圈玻璃稍微浓一点（侧看玻璃更厚）
    for x, y in c.cells(1, 1, 14, 14):
        if x in (1, 14) or y in (1, 14):
            c.set(x, y, GLASS, 2, alpha=104)
    for i in range(4, 10):
        c.set(i, 13 - i, GLASS, 5, alpha=160, glint=True)
        c.set(i + 1, 13 - i, GLASS, 4, alpha=118, glint=True)
    for i in range(9, 12):
        c.set(i, 20 - i, GLASS, 5, alpha=120, glint=True)
    c.hline(0, 15, 0, WHITE, 5)
    c.vline(0, 0, 15, WHITE, 5)
    c.hline(1, 15, 15, WHITE, 2)
    c.vline(15, 1, 15, WHITE, 2)
    c.set(15, 0, WHITE, 3)
    c.set(0, 15, WHITE, 3)
    return c


@texture("block/station_light", refs=("block/sea_lantern", "block/redstone_lamp_on"))
def station_light(rng):
    """灯板：白色的漫射面板，靠边一圈带点青；外面一圈白漆的框，四角螺钉。"""
    c = Canvas()
    c.fill(WHITE, 4)
    c.hline(0, 15, 0, WHITE, 5)
    c.vline(0, 0, 15, WHITE, 5)
    c.hline(1, 15, 15, WHITE, 1)
    c.vline(15, 1, 15, WHITE, 1)
    c.hline(2, 13, 2, WHITE, 2)
    c.vline(2, 2, 13, WHITE, 2)
    c.hline(2, 13, 13, WHITE, 5)
    c.vline(13, 2, 13, WHITE, 5)
    for x, y in c.cells(3, 3, 12, 12):
        edge = x in (3, 12) or y in (3, 12)
        corner = x in (3, 12) and y in (3, 12)
        c.set(x, y, CYAN_GLOW, 4) if corner else c.set(x, y, CYAN_GLOW if edge else WHITE_GLOW, 5)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        screw(c, x, y, WHITE, 4)
    return c


@texture("block/station_pad", rough=True, refs=("block/yellow_concrete", "block/black_concrete"))
def station_pad(rng):
    """停机坪标线：黄黑斜条纹，接得上；漆面被起落架磨掉几块，露出底下的深色。"""
    c = Canvas()
    for x, y in c.cells():
        k = (x + y) % 8
        if k < 4:
            c.set(x, y, HAZARD, 5 if k == 0 else 4, glint=k == 0)
        else:
            c.set(x, y, GRAPHITE, 0 if k == 4 else 1, glint=k == 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 4, 3)
    c.clusters(rng, 0, 0, 15, 15, 1, 2, 2)
    for _ in range(3):
        x, y = rng.randint(0, 14), rng.randint(0, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            px = c.get(x + dx, y + dy)
            if px and px[0] is HAZARD:
                c.set(x + dx, y + dy, HAZARD, 2)
    return c


@texture("block/station_trim", refs=("block/sea_lantern",))
def station_trim(rng):
    """灯带：深色的饰条，中间一道发青光的灯槽，左右接得上，摆成一排是一条连续的灯带。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.hline(0, 15, 0, GRAPHITE, 5)
    c.hline(0, 15, 1, GRAPHITE, 4)
    c.hline(0, 15, 14, GRAPHITE, 2)
    c.hline(0, 15, 15, GRAPHITE, 1)
    c.clusters(rng, 0, 2, 15, 4, -1, 2, 2)
    c.clusters(rng, 0, 11, 15, 13, -1, 2, 2)
    c.hline(0, 15, 5, GRAPHITE, 1)        # 灯槽上沿的影子
    c.hline(0, 15, 6, CYAN_GLOW, 3)
    c.hline(0, 15, 7, CYAN_GLOW, 5)
    c.hline(0, 15, 8, CYAN_GLOW, 4)
    c.hline(0, 15, 9, CYAN_GLOW, 2)
    c.hline(0, 15, 10, GRAPHITE, 5)       # 下沿受光
    for x in (3, 11):
        screw(c, x, 3, GRAPHITE, 3)
        screw(c, x, 12, GRAPHITE, 3)
    return c


@texture("block/station_solar_panel", refs=("block/daylight_detector_top", "block/blue_stained_glass"))
def station_solar_panel(rng):
    """太阳翼：深蓝的电池片排成格子，银色的栅线，上下两道金色的框；电池片上一道斜的反光。"""
    c = Canvas()
    for x, y in c.cells():
        c.set(x, y, BLUE, 1)
    for x, y in c.cells(0, 1, 15, 14):
        # 每片电池上一道从左上到右下的反光带
        k = (x % 4) + ((y - 1) % 7)
        if k in (2, 3):
            c.set(x, y, BLUE, 2)
    for x in range(0, 16, 4):
        c.vline(x, 1, 14, STEEL, 5)
    c.hline(0, 15, 8, STEEL, 5)
    for x in range(0, 16, 4):
        c.set(x, 8, STEEL, 6)
    c.hline(0, 15, 0, GOLD_FOIL, 4)
    c.hline(0, 15, 15, GOLD_FOIL, 1)
    for x in range(1, 16, 4):
        c.set(x, 0, GOLD_FOIL, 5, glint=True)
    c.set(6, 3, BLUE, 4, glint=True)
    c.set(13, 11, BLUE, 3, glint=True)
    return c


# ---- 推进器：钢机壳、警示带，喷管外面烧出回火色，喷口里是火 ----

def mk2_badge(c: Canvas):
    """Mk.II 的标记：右下角两道金色的竖杠。"""
    for y in range(11, 14):
        c.set(11, y, GOLD_FOIL, 4 if y == 11 else 3)
        c.set(13, y, GOLD_FOIL, 4 if y == 11 else 3)
    c.set(11, 14, GOLD_FOIL, 1)
    c.set(13, 14, GOLD_FOIL, 1)


def housing(rng: random.Random, r, base: int) -> Canvas:
    """部件的机壳面：外圈上左亮、下右暗，里面成团的拉丝纹。"""
    c = Canvas()
    c.fill(r, base)
    c.clusters(rng, 2, 2, 13, 13, -1, 4, 3)
    c.clusters(rng, 2, 2, 13, 13, 1, 2, 2)
    c.hline(0, 14, 0, r, base + 1)
    c.vline(0, 0, 14, r, base + 1)
    c.hline(1, 15, 15, r, base - 2)
    c.vline(15, 1, 15, r, base - 2)
    c.hline(1, 13, 1, r, base + 1)
    c.hline(2, 14, 14, r, base - 1)
    c.vline(14, 2, 14, r, base - 1)
    return c


def thruster_side(rng, r, base, stripe, stripe_lv, dark, dark_lv):
    c = housing(rng, r, base)
    c.vent(3, 3, 12, 7, r, base)
    c.hline(1, 14, 9, r, base - 2)
    c.stripes(1, 10, 14, 12, stripe, stripe_lv, dark, dark_lv, period=4, width=2)
    c.hline(1, 14, 13, r, base + 1)
    for x in (2, 13):
        c.set(x, 3, r, base + 2, glint=True)
    return c


def thruster_back(rng, r, base, mk2: bool):
    """背板：中间一个圆的检修口（四颗螺栓压着），四角螺栓。正面这面中间被喷管挡住，只露一圈。"""
    c = housing(rng, r, base)
    c.ring(7.5, 7.5, 4.6, r, base - 2, 1.2)
    c.shade_disc(7.5, 7.5, 3.4, r, base, amp=1.5, spec=False)
    for x, y in ((7, 4), (4, 7), (10, 7), (7, 10)):
        c.set(x, y, r, base + 2, glint=True)
    c.set(7, 7, r, base - 2)
    c.set(8, 8, r, base + 1)
    for x, y in ((2, 2), (12, 2), (2, 12)):
        c.bolt(x, y, r, base)
    if mk2:
        mk2_badge(c)
    else:
        c.bolt(12, 12, r, base)
    return c


def nozzle_side(rng, r, base, temper: list, band) -> Canvas:
    """喷管外壁：离喷口越近回火色越重（从喷口往里：蓝、紫、黄铜色），再往里一道深的冷却环，最后接机身。
    temper 是从喷口往里的 [(色阶, 级数), ...]；band 是靠机身那圈的 (色阶, 级数)。"""
    c = Canvas()
    c.fill(r, base - 1)
    rows = [(r, base + 2)] + temper + [(r, base - 3), (r, base)] + [band]
    for x, y in c.cells():
        d = end_distance(x, y, (3, 12), (3, 12), (3, 12))
        if d is None:
            continue
        ramp_, lv = rows[min(d, len(rows) - 1)]
        c.set(x, y, ramp_, lv)
    # 每个面靠受光的一条边亮一点（上面的左边、侧面的上边）
    for y in range(0, 6):
        c.shift(3, y, 1)
    for x in range(0, 6):
        c.shift(x, 3, 1)
        c.shift(15 - x, 3, 1)
    return c


def nozzle_face(rim, rim_base, lip, glow_r) -> Canvas:
    """喷口正面：外圈喷管的唇边（上左受光），里面是暗的内壁，再往里火一圈圈亮到中间发白。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        lit = dx + dy < -1
        if d >= 6.9:
            c.set(x, y, rim, rim_base - 1)
        elif d >= 5.7:
            c.set(x, y, lip[0], lip[1] + (1 if lit else (-1 if dx + dy > 1 else 0)))
        elif d >= 4.7:
            c.set(x, y, rim, rim_base - 3 if not lit else rim_base - 2)
        elif d >= 3.6:
            c.set(x, y, glow_r, 2)
        elif d >= 2.3:
            c.set(x, y, glow_r, 3)
        elif d >= 1.2:
            c.set(x, y, glow_r, 4)
        else:
            c.set(x, y, glow_r, 5)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.set(x, y, rim, rim_base + 1, glint=True)
    return c


@texture("block/ship_thruster_side", refs=("block/blast_furnace_side",))
def ship_thruster_side(rng):
    return thruster_side(rng, STEEL, 4, HAZARD, 3, GRAPHITE, 1)


@texture("block/ship_thruster_back")
def ship_thruster_back(rng):
    return thruster_back(rng, STEEL, 4, False)


@texture("block/ship_thruster_nozzle_side")
def ship_thruster_nozzle_side(rng):
    return nozzle_side(rng, GRAPHITE, 3, [(BLUE, 1), (VIOLET, 2), (BRASS, 2)], (GRAPHITE, 2))


@texture("block/ship_thruster_nozzle", refs=("block/blast_furnace_front_on",))
def ship_thruster_nozzle(rng):
    return nozzle_face(GRAPHITE, 3, (GRAPHITE, 4), ORANGE_GLOW)


@texture("block/ship_thruster_mk2_side")
def ship_thruster_mk2_side(rng):
    return thruster_side(rng, ALLOY, 3, GOLD_FOIL, 3, ALLOY, 0)


@texture("block/ship_thruster_mk2_back")
def ship_thruster_mk2_back(rng):
    return thruster_back(rng, ALLOY, 3, True)


@texture("block/ship_thruster_mk2_nozzle_side")
def ship_thruster_mk2_nozzle_side(rng):
    return nozzle_side(rng, ALLOY, 3, [(BLUE, 2), (BLUE, 1), (VIOLET, 1)], (GOLD_FOIL, 3))


@texture("block/ship_thruster_mk2_nozzle")
def ship_thruster_mk2_nozzle(rng):
    return nozzle_face(ALLOY, 3, (GOLD_FOIL, 3), BLUE_GLOW)


# ---- 燃料罐：橙色的隔热泡沫（像航天飞机的外挂燃料箱），钢的加强筋，液位窗 ----

def gauge(c: Canvas, x0, y0, x1, y1, level_y: int):
    """液位窗：深色的框，液面以下是发绿光的燃料，液面一道亮线，上面是空的暗玻璃。"""
    c.rect(x0, y0, x1, y1, GRAPHITE, 1)
    for x, y in c.cells(x0 + 1, y0 + 1, x1 - 1, y1 - 1):
        if y < level_y:
            c.set(x, y, SCREEN, 1)
        elif y == level_y:
            c.set(x, y, GREEN_GLOW, 4)
        else:
            c.set(x, y, GREEN_GLOW, 3 if x > x0 + 1 else 2)
    c.hline(x0, x1, y1 + 1, GRAPHITE, 4)
    c.set(x0 + 1, y0 + 1, SCREEN, 3, glint=True)


@texture("block/fuel_tank_side", rough=True, refs=("block/orange_terracotta", "block/copper_block"))
def fuel_tank_side(rng):
    c = Canvas()
    c.fill(FOAM, 4)
    c.clusters(rng, 3, 0, 12, 15, -1, 14, 3)
    c.clusters(rng, 3, 0, 12, 15, 1, 6, 2)
    # 两道环焊缝
    for y in (2, 13):
        c.hline(3, 12, y, FOAM, 2)
        c.hline(3, 12, y + 1, FOAM, 5)
    # 左右两根钢的加强筋（圆柱）
    c.cylinder(0, 0, 2, 15, STEEL, 3)
    c.cylinder(13, 0, 15, 15, STEEL, 3)
    for y in (1, 5, 9, 13):
        c.set(1, y, STEEL, 5, glint=True)
        c.set(14, y, STEEL, 5, glint=True)
    gauge(c, 5, 4, 8, 11, 7)
    # 危险品的小三角
    c.set(11, 8, HAZARD, 4)
    c.hline(10, 12, 9, HAZARD, 4)
    c.set(11, 9, GRAPHITE, 1)
    c.hline(10, 12, 10, HAZARD, 2)
    return c


@texture("block/fuel_tank_top", refs=("block/iron_block",))
def fuel_tank_top(rng):
    """罐顶：钢的封头，中间一个加注阀，一圈红色的危险环，四颗螺栓。"""
    c = Canvas()
    c.fill(STEEL, 3)
    c.shade_disc(7.5, 7.5, 7.0, STEEL, 4, amp=1.3, spec=False)
    c.ring(7.5, 7.5, 5.0, RED, 3, 1.3)
    for x, y in c.cells():
        if c.get(x, y)[0] is RED:
            dx, dy = x - 7.5, y - 7.5
            c.set(x, y, RED, 4 if dx + dy < -2 else (2 if dx + dy > 2 else 3))
    c.shade_disc(7.5, 7.5, 1.8, GRAPHITE, 3, amp=1.5)
    for x, y in ((7, 1), (1, 7), (14, 7), (7, 14)):
        c.set(x, y, STEEL, 6, glint=True)
    c.hline(0, 15, 0, STEEL, 5)
    c.vline(0, 0, 15, STEEL, 5)
    c.hline(1, 15, 15, STEEL, 1)
    c.vline(15, 1, 15, STEEL, 1)
    return c


@texture("block/fuel_tank_mk2_side", refs=("block/red_terracotta",))
def fuel_tank_mk2_side(rng):
    c = Canvas()
    c.fill(RED, 3)
    c.clusters(rng, 3, 2, 12, 13, -1, 7, 3)
    c.cylinder(0, 0, 1, 15, GOLD_FOIL, 3)
    c.cylinder(14, 0, 15, 15, GOLD_FOIL, 2)
    c.vline(2, 0, 15, ALLOY, 3)
    c.vline(13, 0, 15, ALLOY, 1)
    for y, lv in ((0, 4), (1, 3), (14, 2), (15, 1)):
        c.hline(2, 13, y, ALLOY, lv)
    gauge(c, 4, 3, 6, 11, 5)
    gauge(c, 9, 3, 11, 11, 6)
    return c


@texture("block/fuel_tank_mk2_top")
def fuel_tank_mk2_top(rng):
    c = Canvas()
    c.fill(ALLOY, 3)
    c.shade_disc(7.5, 7.5, 7.0, ALLOY, 3, amp=1.2, spec=False)
    c.ring(7.5, 7.5, 5.2, GOLD_FOIL, 3, 1.2)
    c.shade_disc(7.5, 7.5, 3.6, RED, 3, amp=1.5, spec=False)
    for i in range(-2, 3):
        c.set(7 + i, 7, WHITE, 6)
        c.set(7, 7 + i, WHITE, 6)
    c.set(8, 8, WHITE, 4)
    c.hline(0, 15, 0, ALLOY, 4)
    c.vline(0, 0, 15, ALLOY, 4)
    c.hline(1, 15, 15, ALLOY, 1)
    c.vline(15, 1, 15, ALLOY, 1)
    return c


# ---- 脉冲引擎：深色机壳、两道青色的灯槽；正面是离子推进器的栅格，整面自发光 ----

def impulse_side(rng, r, base, trim):
    c = housing(rng, r, base)
    for y in (4, 10):
        c.hline(2, 13, y - 1, r, base - 2)          # 灯槽上沿的影子
        c.hline(2, 13, y, CYAN_GLOW, 4)
        c.hline(2, 13, y + 1, CYAN_GLOW, 2)
        c.hline(2, 13, y + 2, r, base + 1)
        c.set(2, y, CYAN_GLOW, 5)
    c.vent(4, 7, 11, 8, r, base, horizontal=False, pitch=2)
    if trim:
        c.hline(2, 13, 14, GOLD_FOIL, 3)
        c.set(2, 14, GOLD_FOIL, 4)
    for x, y in ((1, 1), (13, 1)):
        c.set(x, y, r, base + 2, glint=True)
    return c


def impulse_front(rim, rim_base, ring_r, ring_base, core_bright: bool):
    """离子推进器的栅格：外圈一道金属环（上左亮），里面青蓝的光一圈圈亮到中间；四根斜的支撑杆托着中间的中和器。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        lit = dx + dy < -1.5
        shadow = dx + dy > 1.5
        if d >= 6.8:
            c.set(x, y, rim, rim_base)
        elif d >= 5.6:
            c.set(x, y, ring_r, ring_base + (1 if lit else (-1 if shadow else 0)))
        elif d >= 4.7:
            c.set(x, y, BLUE_GLOW, 3 if core_bright else 2)
        elif d >= 3.4:
            c.set(x, y, CYAN_GLOW, 4 if core_bright else 3)
        elif d >= 2.0:
            c.set(x, y, CYAN_GLOW, 4)
        else:
            c.set(x, y, CYAN_GLOW, 5)
    # 支撑杆：四根连续的斜杆，从中和器伸到外环
    for x0, y0, x1, y1 in ((5, 5, 3, 3), (10, 5, 12, 3), (5, 10, 3, 12), (10, 10, 12, 12)):
        c.line(x0, y0, x1, y1, rim, rim_base + 2)
    c.shade_disc(7.5, 7.5, 1.4, ring_r, ring_base, amp=1.5)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, ring_r, ring_base + 2, glint=True)
    return c


def impulse_back(rng, r, base, mk2):
    c = housing(rng, r, base)
    c.vent(3, 3, 12, 12, r, base, pitch=2)
    for x, y in ((1, 1), (13, 1), (1, 13)):
        c.set(x, y, r, base + 2, glint=True)
    if mk2:
        c.rect(10, 10, 14, 14, r, base)
        mk2_badge(c)
    else:
        c.set(13, 13, r, base + 2, glint=True)
    return c


@texture("block/impulse_engine_side")
def impulse_engine_side(rng):
    return impulse_side(rng, GRAPHITE, 3, False)


@texture("block/impulse_engine_front", refs=("block/beacon", "block/sea_lantern"))
def impulse_engine_front(rng):
    return impulse_front(GRAPHITE, 1, STEEL, 4, False)


@texture("block/impulse_engine_back")
def impulse_engine_back(rng):
    return impulse_back(rng, GRAPHITE, 3, False)


@texture("block/impulse_engine_mk2_side")
def impulse_engine_mk2_side(rng):
    return impulse_side(rng, ALLOY, 3, True)


@texture("block/impulse_engine_mk2_front")
def impulse_engine_mk2_front(rng):
    return impulse_front(ALLOY, 1, GOLD_FOIL, 3, True)


@texture("block/impulse_engine_mk2_back")
def impulse_engine_mk2_back(rng):
    return impulse_back(rng, ALLOY, 3, True)


# ---- 曲速引擎：紫灰的机壳，侧面竖着几根场线圈（发光层只画线圈），正面一圈圈发光的环 ----

def warp_side(rng, coils, band, band_lv, coil_dim):
    c = housing(rng, WARP, 3)
    c.hline(1, 14, 1, band, band_lv)
    c.hline(1, 14, 14, band, max(0, band_lv - 2))
    for x in coils:
        c.vline(x, 2, 13, WARP, 0)
        c.vline(x + 1, 3, 13, WARP, 4)
        for y in range(3, 13):
            c.set(x, y, coil_dim[0], coil_dim[1] + (1 if y % 4 == 1 else 0))
        c.set(x, 2, STEEL, 4)
        c.set(x, 13, STEEL, 2)
    return c


def warp_glow(coils, glow_r):
    c = Canvas()
    for x in coils:
        for y in range(3, 13):
            c.set(x, y, glow_r, 5 if y % 4 == 1 else (4 if y % 2 else 3), glint=True)
    return c


def warp_front(glow_r, ring_r, ring_base):
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        lit = dx + dy < -1.5
        shadow = dx + dy > 1.5
        if d >= 6.9:
            c.set(x, y, WARP, 1)
        elif d >= 5.9:
            c.set(x, y, ring_r, ring_base + (1 if lit else (-1 if shadow else 0)))
        elif d >= 5.1:
            c.set(x, y, glow_r, 2)
        elif d >= 4.0:
            c.set(x, y, glow_r, 4 if lit else 3)
        elif d >= 3.0:
            c.set(x, y, WARP, 0)
        elif d >= 1.7:
            c.set(x, y, glow_r, 4)
        else:
            c.set(x, y, glow_r, 5)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, WARP, 5, glint=True)
    return c


def warp_back(rng, accent, mk2):
    c = housing(rng, WARP, 3)
    for y in (3, 6, 9, 12):
        c.hline(3, 12, y, WARP, 0)
        for x in range(3, 13):
            if x % 3 == 0:
                c.set(x, y + 1, accent[0], accent[1])
            else:
                c.set(x, y + 1, WARP, 4)
    if mk2:
        c.rect(10, 10, 14, 14, WARP, 3)
        mk2_badge(c)
    return c


WARP_COILS = (3, 7, 11)
WARP_MK2_COILS = (2, 5, 9, 12)


@texture("block/warp_drive_side")
def warp_drive_side(rng):
    return warp_side(rng, WARP_COILS, WARP, 1, (VIOLET, 1))


@texture("block/warp_drive_glow")
def warp_drive_glow(rng):
    return warp_glow(WARP_COILS, VIOLET_GLOW)


@texture("block/warp_drive_front", refs=("block/end_portal_frame_top", "block/respawn_anchor_top"))
def warp_drive_front(rng):
    return warp_front(VIOLET_GLOW, WARP, 4)


@texture("block/warp_drive_back")
def warp_drive_back(rng):
    return warp_back(rng, (VIOLET, 2), False)


@texture("block/warp_drive_mk2_side")
def warp_drive_mk2_side(rng):
    return warp_side(rng, WARP_MK2_COILS, GOLD_FOIL, 3, (MAGENTA_GLOW, 0))


@texture("block/warp_drive_mk2_glow")
def warp_drive_mk2_glow(rng):
    return warp_glow(WARP_MK2_COILS, MAGENTA_GLOW)


@texture("block/warp_drive_mk2_front")
def warp_drive_mk2_front(rng):
    return warp_front(MAGENTA_GLOW, GOLD_FOIL, 3)


@texture("block/warp_drive_mk2_back")
def warp_drive_mk2_back(rng):
    return warp_back(rng, (MAGENTA_GLOW, 1), True)


# ---- 武器：光子炮（琥珀色）和采矿光束（红橙色） ----

def weapon_side(rng, accent, accent_glow):
    c = housing(rng, STEEL, 3)
    c.vent(3, 3, 12, 6, STEEL, 3)
    c.hline(1, 14, 8, STEEL, 1)
    c.hline(1, 14, 9, accent, 2)
    c.hline(1, 14, 10, accent_glow, 4)
    c.hline(1, 14, 11, accent, 1)
    c.hline(1, 14, 12, STEEL, 4)
    for x in (1, 14):
        c.set(x, 10, accent, 3)
    return c


def weapon_back(rng):
    c = housing(rng, STEEL, 3)
    c.vent(4, 4, 11, 11, STEEL, 3)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.bolt(x, y, STEEL, 3)
    return c


def weapon_front(rng, accent, half: int):
    """炮座的正面：中间凹进去一个方的炮管座（炮管从这里伸出来），一圈等级色的框，四角螺栓。"""
    c = housing(rng, STEEL, 3)
    lo, hi = 7 - half, 8 + half
    c.rect(lo - 1, lo - 1, hi + 1, hi + 1, accent, 2)
    c.hline(lo - 1, hi + 1, lo - 1, accent, 3)
    c.vline(lo - 1, lo - 1, hi + 1, accent, 3)
    c.recess(lo, lo, hi, hi, GRAPHITE, 2)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        c.bolt(x, y, STEEL, 3)
    return c


def weapon_barrel(rng, lo: int, hi: int, accent_glow, beam: bool):
    """炮管：只露出 [lo, hi] 宽的三块（上面那块、西面、东面）。从炮口往里：亮的炮口环、一道槽、
    几道散热环。采矿光束沿中线一道发光的聚焦槽。"""
    c = Canvas()
    c.fill(GRAPHITE, 2)
    rows = [(STEEL, 5), (GRAPHITE, 1), (STEEL, 4), (GRAPHITE, 2), (STEEL, 4), (GRAPHITE, 1)]
    for x, y in c.cells():
        d = end_distance(x, y, (lo, hi), (lo, hi), (lo, hi))
        if d is None:
            continue
        r, lv = rows[min(d, 5)]
        # 横截面的明暗：受光的一边亮一级，背光的一边暗一级
        across = x if (lo <= x <= hi and y <= 5) else y
        edge = across in (lo, hi)
        if across == lo:
            lv += 1
        elif across == hi:
            lv -= 1
        c.set(x, y, r, lv, glint=edge)
        if beam and across in ((lo + hi) // 2, (lo + hi) // 2 + 1) and d >= 1:
            c.set(x, y, accent_glow, 3 if d % 2 else 2)
    return c


def weapon_muzzle(glow_r, half: int):
    """炮口：只露出中间 2×half 见方的一块，中间最亮，往外暗下去，最外一圈是炮管的唇边。"""
    c = Canvas()
    c.fill(GRAPHITE, 1)
    for x, y in c.cells(7 - half + 1, 7 - half + 1, 8 + half - 1, 8 + half - 1):
        d = math.hypot(x - 7.5, y - 7.5) / half
        c.set(x, y, glow_r, 5 if d < 0.45 else (4 if d < 0.8 else 3))
    c.frame(7 - half + 1, 7 - half + 1, 8 + half - 1, 8 + half - 1, glow_r, 2)
    c.frame(7 - half, 7 - half, 8 + half, 8 + half, STEEL, 3)
    return c


@texture("block/photon_cannon_side")
def photon_cannon_side(rng):
    return weapon_side(rng, AMBER, AMBER_GLOW)


@texture("block/photon_cannon_back")
def photon_cannon_back(rng):
    return weapon_back(rng)


@texture("block/photon_cannon_front")
def photon_cannon_front(rng):
    return weapon_front(rng, AMBER, 2)


@texture("block/photon_cannon_barrel")
def photon_cannon_barrel(rng):
    return weapon_barrel(rng, 5, 10, AMBER_GLOW, False)


@texture("block/photon_cannon_muzzle")
def photon_cannon_muzzle(rng):
    return weapon_muzzle(AMBER_GLOW, 3)


@texture("block/mining_beam_side")
def mining_beam_side(rng):
    return weapon_side(rng, RED, RED_GLOW)


@texture("block/mining_beam_back")
def mining_beam_back(rng):
    return weapon_back(rng)


@texture("block/mining_beam_front")
def mining_beam_front(rng):
    return weapon_front(rng, RED, 1)


@texture("block/mining_beam_barrel")
def mining_beam_barrel(rng):
    return weapon_barrel(rng, 6, 9, RED_GLOW, True)


@texture("block/mining_beam_muzzle")
def mining_beam_muzzle(rng):
    return weapon_muzzle(RED_GLOW, 2)


# ---- 护盾发生器：白色机壳，四个侧面一圈环形线圈（发光层只画线圈），顶上散热格栅 ----

SHIELD_RING = 4.6


@texture("block/shield_generator_side", refs=("block/beacon",))
def shield_generator_side(rng):
    c = housing(rng, WHITE, 4)
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if abs(d - SHIELD_RING) < 0.8:
            c.set(x, y, GRAPHITE, 1 if x + y < 15 else 2)
        elif d < 2.2:
            c.set(x, y, GRAPHITE, 2)
        elif abs(d - SHIELD_RING) < 1.6:
            c.set(x, y, WHITE, 3 if (x - 7.5) + (y - 7.5) < 0 and d < SHIELD_RING else 4)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.bolt(x, y, WHITE, 4)
    return c


@texture("block/shield_generator_glow")
def shield_generator_glow(rng):
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if abs(d - SHIELD_RING) < 0.8:
            a = math.atan2(dy, dx)
            node = int((a + math.pi) / (2 * math.pi) * 12) % 3 == 0
            c.set(x, y, CYAN_GLOW, 5 if node else 3, glint=True)
        elif d < 2.2:
            c.set(x, y, CYAN_GLOW, 5 if d < 1.0 else 4)
    return c


@texture("block/shield_generator_top")
def shield_generator_top(rng):
    c = housing(rng, WHITE, 4)
    c.vent(3, 4, 12, 11, WHITE, 4, pitch=2)
    for y in (2, 13):
        c.hline(3, 12, y, TEAL, 3)
    c.set(3, 2, TEAL, 4)
    c.set(3, 13, TEAL, 4)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, WHITE, 6, glint=True)
    return c


# ---- 驾驶台和座椅：浅灰的船体面板（模型里只截一块用），布面座椅，自发光的屏幕 ----

@texture("block/ship_hull_panel", refs=("block/iron_block", "block/smooth_stone"))
def ship_hull_panel(rng):
    """驾驶台、座椅的面板：浅灰的钢板，四周倒角，中间一道横缝，四角铆钉。模型里每个面只截贴图的一块，所以不画居中的大图案。"""
    c = Canvas()
    c.fill(STEEL, 5)
    c.clusters(rng, 2, 2, 13, 13, -1, 4, 3)
    scratch(c, rng, 3, 9, 12, 12, 1)
    c.hline(0, 15, 0, STEEL, 6)
    c.vline(0, 0, 15, STEEL, 6)
    c.hline(1, 15, 15, STEEL, 3)
    c.vline(15, 1, 15, STEEL, 3)
    c.hline(1, 14, 14, STEEL, 4)
    c.vline(14, 1, 14, STEEL, 4)
    seam_h(c, 7, STEEL, 5, 1, 13)
    for x, y in ((2, 2), (12, 2), (2, 11), (12, 11)):
        stud(c, x, y, STEEL, 5)
    return c


@texture("block/damaged_ship_hull_panel", rough=True)
def damaged_ship_hull_panel(rng):
    """坠毁的驾驶台：同一块面板，下半截熏黑、越往下越黑，几块烧焦的斑，斜着一道裂缝（右边一道受光的断口）。"""
    # 和 build.py 一样按路径定种子，底子和完好的面板一模一样
    c = ship_hull_panel(random.Random(zlib.crc32(b"block/ship_hull_panel")))
    for x, y in c.cells():
        t = y / 15
        if rng.random() < 0.15 + 0.65 * t:
            c.shift(x, y, -1)
        if y > 9 and rng.random() < 0.45 * t:
            c.shift(x, y, -1)
    for _ in range(3):
        x, y = rng.randint(2, 12), rng.randint(8, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if rng.random() < 0.8:
                c.set(x + dx, y + dy, BASALT, rng.choice((1, 2)))
    crack = [(3, 2), (4, 3), (4, 4), (5, 5), (6, 5), (6, 6), (7, 7), (8, 8), (8, 9), (9, 10), (10, 10), (11, 11), (11, 12)]
    for x, y in crack:
        c.set(x, y, GRAPHITE, 0, glint=True)
    for x, y in crack:
        if (x + 1, y) not in crack:
            c.set(x + 1, y, STEEL, 6, glint=True)
    for x, y in ((12, 12), (12, 13), (2, 2)):
        c.set(x, y, RUST, 2, glint=True)
    return c


def bars(c: Canvas, x0: int, rows: list, length: int):
    """读数条：两行高（屏幕在模型里被压扁了），亮的一截是读数，暗的一截是空槽。"""
    for (y, n, r) in rows:
        for x in range(x0, x0 + length):
            for yy in (y, y + 1):
                if x < x0 + n:
                    c.set(x, yy, r, 4 if yy == y else 3)
                else:
                    c.set(x, yy, SCREEN, 2)


@texture("block/ship_helm_screen")
def ship_helm_screen(rng):
    """驾驶台的屏幕：左边一个雷达（贴到模型上会压扁，所以画成竖的椭圆），扫描线和一个琥珀色的目标，右边四条读数。"""
    c = Canvas()
    c.fill(SCREEN, 1)
    c.frame(0, 0, 15, 15, GRAPHITE, 2)
    c.hline(1, 14, 1, SCREEN, 2)
    cx, cy, rx, ry = 5.0, 8.0, 3.6, 5.6
    for x, y in c.cells(1, 2, 9, 14):
        e = math.hypot((x - cx) / rx, (y - cy) / ry)
        if abs(e - 1) < 0.12:
            c.set(x, y, CYAN_GLOW, 3)
        elif abs(e - 0.5) < 0.1:
            c.set(x, y, CYAN_GLOW, 2)
    c.line(5, 8, 7, 4, CYAN_GLOW, 4)
    c.set(5, 8, CYAN_GLOW, 5)
    c.set(3, 10, CYAN_GLOW, 4)
    c.set(7, 6, AMBER_GLOW, 5, glint=True)
    c.set(7, 7, AMBER_GLOW, 4, glint=True)
    bars(c, 10, [(3, 4, GREEN_GLOW), (6, 2, CYAN_GLOW), (9, 3, GREEN_GLOW), (12, 1, AMBER_GLOW)], 4)
    return c


@texture("block/damaged_ship_helm_screen")
def damaged_ship_helm_screen(rng):
    """坏掉的屏幕：暗下去，雷达只剩几段残影，横着一道裂纹，角上一个红色的警告灯还亮着。"""
    c = Canvas()
    c.fill(SCREEN, 0)
    c.frame(0, 0, 15, 15, GRAPHITE, 1)
    cx, cy, rx, ry = 5.0, 8.0, 3.6, 5.6
    for x, y in c.cells(1, 2, 9, 14):
        e = math.hypot((x - cx) / rx, (y - cy) / ry)
        if abs(e - 1) < 0.16 and rng.random() < 0.55:
            c.set(x, y, CYAN_GLOW, 1)
    for y in (3, 6, 9, 12):
        c.hline(10, 13, y, SCREEN, 1)
    c.hline(10, 11, 9, GREEN_GLOW, 1)
    c.clusters(rng, 1, 2, 14, 14, 1, 4, 3)
    crack = [(1, 4), (2, 5), (3, 5), (4, 6), (5, 6), (6, 7), (7, 7), (8, 8), (9, 8), (10, 9), (11, 9), (12, 10),
             (13, 10), (14, 11)]
    for x, y in crack:
        c.set(x, y, GLASS, 4, glint=True)
        if (x, y + 1) not in crack:
            c.set(x, y + 1, GLASS, 1, glint=True)
    for x, y in ((6, 6), (10, 10), (11, 10)):
        c.set(x, y, GLASS, 2, glint=True)
    for x, y in ((12, 3), (13, 3), (12, 4), (13, 4)):
        c.set(x, y, RED_GLOW, 4 if y == 3 else 3, glint=True)
    return c


@texture("block/ship_seat_fabric", refs=("block/gray_wool",))
def ship_seat_fabric(rng):
    """座椅的布面：菱形绗缝，每格鼓起来（上左受光、下右暗），缝线交叉处一颗暗扣；接得上。"""
    c = Canvas()
    for x, y in c.cells():
        u, v = (x + y) % 8, (x - y) % 8
        if u == 0 and v == 0:
            c.set(x, y, FABRIC, 0)
        elif u == 0 or v == 0:
            c.set(x, y, FABRIC, 1, glint=True)
        else:
            c.set(x, y, FABRIC, 4 if u in (1, 2) else (2 if u in (6, 7) else 3))
    return c


# ---- 着陆台：深灰的坪面、白色的降落环、四边警示线；加燃料时（_on）环里和指示灯亮青色 ----

def pad_surface(rng) -> Canvas:
    c = Canvas()
    c.fill(STEEL, 2)
    c.clusters(rng, 1, 1, 14, 14, -1, 5, 3)
    c.clusters(rng, 1, 1, 14, 14, 1, 3, 2)
    return c


def landing_pad_top(rng, on: bool):
    c = pad_surface(rng)
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if 5.0 <= d < 6.4:
            c.set(x, y, WHITE, 4 if (x * 3 + y) % 7 == 0 else 5)
    # 中间一盏灯嵌在深色的灯座里，四个方向各一盏引导灯
    c.frame(6, 6, 9, 9, STEEL, 1)
    c.rect(7, 7, 8, 8, CYAN_GLOW, 5 if on else 1)
    c.set(8, 8, CYAN_GLOW, 4 if on else 0)
    for x, y in ((7, 3), (11, 7), (7, 11), (3, 7)):
        w, h = (2, 1) if y in (3, 11) else (1, 2)
        c.rect(x, y, x + w - 1, y + h - 1, CYAN_GLOW, 4 if on else 1)
        c.set(x + w - 1, y + h, STEEL, 4)
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            c.set(x, y, HAZARD, 4) if (x + y) % 4 < 2 else c.set(x, y, GRAPHITE, 1)
    c.hline(1, 14, 1, STEEL, 1)
    c.vline(1, 1, 14, STEEL, 1)
    return c


def landing_pad_face(rng, on: bool | None):
    """着陆台的侧面：上面一道警示带，下面一排指示灯（侧面没有灯，换成两块检修板和一组通风口）。"""
    c = pad_surface(rng)
    c.stripes(0, 0, 15, 2, HAZARD, 4, GRAPHITE, 1, period=4, width=2)
    c.hline(0, 15, 3, STEEL, 4)
    c.hline(0, 15, 12, STEEL, 0)
    c.hline(0, 15, 13, STEEL, 3)
    c.hline(0, 15, 14, STEEL, 1)
    c.hline(0, 15, 15, STEEL, 0)
    if on is None:
        for x0 in (2, 11):
            c.panel(x0, 5, x0 + 2, 10, STEEL, 2, raised=False)
        c.vent(6, 6, 9, 9, STEEL, 3)
    else:
        for x in (3, 6, 9, 12):
            c.hline(x, x + 1, 6, STEEL, 0)
            c.rect(x, 7, x + 1, 8, CYAN_GLOW if on else SCREEN, 5 if on else 2)
            c.set(x + 1, 8, CYAN_GLOW if on else SCREEN, 4 if on else 1)
            c.hline(x, x + 1, 9, STEEL, 4)
            if on:
                c.hline(x, x + 1, 10, CYAN_GLOW, 1)
    return c


@texture("block/landing_pad_top", refs=("block/smooth_stone",))
def landing_pad_top_off(rng):
    return landing_pad_top(rng, False)


@texture("block/landing_pad_top_on", glow=True)
def landing_pad_top_lit(rng):
    return landing_pad_top(rng, True)


@texture("block/landing_pad_front")
def landing_pad_front(rng):
    return landing_pad_face(rng, False)


@texture("block/landing_pad_front_on", glow=True)
def landing_pad_front_on(rng):
    return landing_pad_face(rng, True)


@texture("block/landing_pad_side")
def landing_pad_side(rng):
    return landing_pad_face(rng, None)


# ---- 空间站和货船的终端：深灰的机身，正面一块屏幕，顶上一枚徽记；侧面都一样 ----

def terminal_casing(rng, base: int = 3) -> Canvas:
    c = Canvas()
    c.fill(GRAPHITE, base)
    c.clusters(rng, 2, 2, 13, 13, -1, 3, 3)
    c.clusters(rng, 2, 2, 13, 13, 1, 2, 2)
    c.frame(0, 0, 15, 15, GRAPHITE, base - 2)
    c.hline(1, 14, 1, GRAPHITE, base + 2)
    c.vline(1, 1, 14, GRAPHITE, base + 1)
    c.hline(2, 14, 14, GRAPHITE, base - 1)
    c.vline(14, 2, 14, GRAPHITE, base - 1)
    return c


def screen(c: Canvas, x0, y0, x1, y1):
    """凹进去的屏幕：上、左一圈影子，下、右受光的唇边，屏幕最上一行有一道反光。"""
    c.rect(x0, y0, x1, y1, SCREEN, 1)
    c.hline(x0 - 1, x1 + 1, y0 - 1, GRAPHITE, 0)
    c.vline(x0 - 1, y0 - 1, y1 + 1, GRAPHITE, 0)
    c.hline(x0, x1 + 1, y1 + 1, GRAPHITE, 5)
    c.vline(x1 + 1, y0, y1 + 1, GRAPHITE, 4)
    c.hline(x0, x1, y0, SCREEN, 2)


def keys(c: Canvas, y: int, special):
    """屏幕下面一排按键：三个灰键和一个彩色的功能键，下面一行影子。"""
    for x in (4, 7):
        c.set(x, y, GRAPHITE, 5)
        c.set(x + 1, y, GRAPHITE, 4)
        c.hline(x, x + 1, y + 1, GRAPHITE, 1)
    r, lv = special
    c.set(10, y, r, lv + 1)
    c.set(11, y, r, lv)
    c.hline(10, 11, y + 1, r, max(0, lv - 2))


def emblem_top(rng, r, inner) -> Canvas:
    """顶面：一圈彩色的徽记环（上左受光），里面压着一个图案。"""
    c = terminal_casing(rng)
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if 3.2 < d < 4.7:
            c.set(x, y, r, 4 if dx + dy < -1.5 else (2 if dx + dy > 1.5 else 3))
        elif d <= 3.2:
            c.set(x, y, GRAPHITE, 2)
    inner(c, r)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        stud(c, x, y, GRAPHITE, 3)
    return c


def credit_mark(c: Canvas, r):
    for y in (6, 9):
        c.hline(6, 9, y, r, 4)
        c.hline(6, 9, y + 1, r, 1)


def terminal_side(rng) -> Canvas:
    c = terminal_casing(rng)
    c.vent(3, 3, 11, 12, GRAPHITE, 3)
    for y in range(3, 13):
        c.set(13, y, CYAN_GLOW, 4 if y % 4 == 0 else 2)
    return c


@texture("block/trade_terminal_front", refs=("block/crafter_north",))
def trade_terminal_front(rng):
    """交易终端：屏幕上是行情的柱状图和一条金色的价格线；下面一排按键，右边是金色的交易键。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 10)
    for x, h in ((4, 3), (6, 5), (8, 4), (10, 6), (12, 2)):
        for y in range(10 - h, 10):
            c.set(x, y, CYAN_GLOW, 4 if y == 10 - h else (3 if y < 8 else 2))
    pts = [(4, 7), (6, 5), (8, 6), (10, 4), (12, 3)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        c.line(xa, ya, xb, yb, AMBER_GLOW, 4)
    keys(c, 12, (BRASS, 4))
    return c


@texture("block/trade_terminal_side")
def trade_terminal_side(rng):
    return terminal_side(rng)


@texture("block/trade_terminal_top")
def trade_terminal_top(rng):
    return emblem_top(rng, GOLD_FOIL, credit_mark)


@texture("block/ship_repair_front")
def ship_repair_front(rng):
    """维修台：屏幕上是一艘船的线框图（一处损伤标红），下面一条绿色的船体完整度；橙色的维修键。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 10)
    c.draw([
        "....44....",
        "...4114...",
        "...4114...",
        ".44111144.",
        "41111r1114",
        "4441111444",
        "...4444...",
    ], {"01234": CYAN_GLOW, "pqrstu": RED_GLOW}, 3, 3)
    for x in range(4, 12):
        c.set(x, 10, GREEN_GLOW, 4 if x < 10 else 1)
    keys(c, 12, (AMBER, 4))
    return c


@texture("block/ship_repair_side")
def ship_repair_side(rng):
    c = terminal_casing(rng)
    c.hline(1, 14, 4, GRAPHITE, 1)
    c.stripes(1, 5, 14, 10, AMBER, 4, GRAPHITE, 1, period=4, width=2)
    c.hline(1, 14, 11, GRAPHITE, 5)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        stud(c, x, y, GRAPHITE, 3)
    return c


@texture("block/ship_repair_top")
def ship_repair_top(rng):
    """顶面一把橙色的扳手斜放着：开口朝右上，手柄往左下，尾端一个圆环。"""
    c = terminal_casing(rng)
    for i in range(7):
        x, y = 4 + i, 11 - i
        c.set(x, y, AMBER, 5)
        c.set(x + 1, y, AMBER, 3)
        c.set(x, y + 1, AMBER, 2)
    c.disc(11.5, 3.5, 2.2, AMBER, 4)
    for x, y in ((12, 3), (13, 2), (12, 2), (13, 3)):
        c.clear(x, y)
        c.set(x, y, GRAPHITE, 3)
    c.set(10, 2, AMBER, 5)
    c.set(11, 2, AMBER, 5)
    c.set(13, 5, AMBER, 2)
    c.set(12, 5, AMBER, 3)
    c.disc(3.5, 11.5, 1.8, AMBER, 3)
    c.set(3, 11, GRAPHITE, 1)
    c.set(2, 11, AMBER, 5)
    c.set(3, 10, AMBER, 5)
    for x, y in ((2, 2), (12, 12)):
        stud(c, x, y, GRAPHITE, 3)
    return c


@texture("block/mission_board_front", refs=("block/lectern_front",))
def mission_board_front(rng):
    """任务板：屏幕上三行任务，每行前面一个彩色标记、后面一行字和一行小字，最后一行打了勾。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 11)
    for y, mark, words in ((4, AMBER_GLOW, "333.33.3"), (7, RED_GLOW, "33.333.."), (10, GREEN_GLOW, "333.....")):
        c.set(4, y, mark, 5)
        c.set(4, y + 1, mark, 3)
        for i, ch in enumerate(words):
            if ch == "3":
                c.set(6 + i, y, WHITE_GLOW, 4)
        if y < 10:
            c.hline(6, 9, y + 1, SCREEN, 3)
    for x, y in ((9, 10), (10, 11), (11, 10), (12, 9)):
        c.set(x, y, GREEN_GLOW, 5)
    return c


@texture("block/mission_board_side")
def mission_board_side(rng):
    """侧面：一摞文件夹的侧边，几道彩色的边。"""
    c = terminal_casing(rng)
    for y, r in ((4, AMBER), (7, RED), (10, GREEN)):
        c.hline(3, 12, y, r, 4)
        c.set(3, y, r, 5)
        c.hline(3, 12, y + 1, GRAPHITE, 1)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        stud(c, x, y, GRAPHITE, 3)
    return c


@texture("block/mission_board_top")
def mission_board_top(rng):
    """顶面：青色的星图标记，一个圈加十字。"""
    c = terminal_casing(rng)
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if 3.4 < d < 4.6:
            c.set(x, y, CYAN_GLOW, 3)
    for i in range(3, 13):
        if c.get(i, 7)[0] is not CYAN_GLOW:
            c.set(i, 7, CYAN_GLOW, 1)
        if c.get(7, i)[0] is not CYAN_GLOW:
            c.set(7, i, CYAN_GLOW, 1)
    c.set(7, 7, CYAN_GLOW, 5)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        stud(c, x, y, GRAPHITE, 3)
    return c


@texture("block/ship_dealer_front")
def ship_dealer_front(rng):
    """飞船交易终端：屏幕里是一艘俯视的飞船（船头朝上）和一排琥珀色的价签。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 10)
    rows = {3: (7, 8), 4: (6, 9), 5: (5, 10), 6: (3, 12), 7: (3, 12), 8: (5, 10)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            c.set(x, y, CYAN_GLOW, 4 if x in (x0, x1) or y == 3 else 5)
    for x, y in ((7, 4), (8, 4), (7, 5), (8, 5)):
        c.set(x, y, BLUE_GLOW, 3)
    for x in (3, 12):
        c.set(x, 7, CYAN_GLOW, 3)
    for x in (6, 9):
        c.set(x, 9, AMBER_GLOW, 5)
    for x in range(4, 10):
        c.set(x, 10, AMBER_GLOW, 4 if x % 2 == 0 else 3)
    c.set(11, 10, AMBER_GLOW, 2)
    keys(c, 12, (TEAL, 4))
    return c


@texture("block/ship_dealer_side")
def ship_dealer_side(rng):
    return terminal_side(rng)


@texture("block/ship_dealer_top")
def ship_dealer_top(rng):
    return emblem_top(rng, TEAL, credit_mark)


@texture("block/fleet_terminal_front")
def fleet_terminal_front(rng):
    """舰队终端：屏幕里几艘小船排成队形，尾焰是紫色的；下面一条青色的虚线。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 10)
    for cx, cy, big in ((7, 3, True), (4, 6, False), (10, 6, False)):
        widths = [(0, 1), (-1, 2), (-1, 2), (-2, 3)] if big else [(0, 1), (0, 1), (-1, 2)]
        for i, (a, b) in enumerate(widths):
            for x in range(cx + a, cx + b + 1):
                c.set(x, cy + i, CYAN_GLOW, 4 if x in (cx + a, cx + b) else 5)
        for x in (cx, cx + 1):
            c.set(x, cy + len(widths), VIOLET_GLOW, 4)
    for x in range(3, 13):
        c.set(x, 10, CYAN_GLOW, 3 if x % 3 else 1)
    keys(c, 12, (VIOLET, 4))
    return c


@texture("block/fleet_terminal_side")
def fleet_terminal_side(rng):
    return terminal_side(rng)


def fleet_mark(c: Canvas, r):
    """一个箭头形的船标，船头朝上，尾部两片翼。"""
    c.draw([
        "..54..",
        ".5443.",
        "544332",
        "43..32",
    ], {"012345": r}, 5, 5)


@texture("block/fleet_terminal_top")
def fleet_terminal_top(rng):
    return emblem_top(rng, VIOLET, fleet_mark)


@texture("block/freighter_console_front")
def freighter_console_front(rng):
    """货船控制台：屏幕里是星图，几颗星，一条从琥珀色起点到琥珀色终点的青色航线。"""
    c = terminal_casing(rng)
    screen(c, 3, 3, 12, 10)
    for x, y in ((4, 4), (11, 5), (6, 9), (9, 3), (12, 9), (5, 6), (10, 8)):
        c.set(x, y, WHITE_GLOW, rng.choice((2, 3, 4)), glint=True)
    route = [(4, 9), (6, 7), (8, 7), (10, 5), (11, 4)]
    for (xa, ya), (xb, yb) in zip(route, route[1:]):
        c.line(xa, ya, xb, yb, CYAN_GLOW, 3)
    for x, y in route[1:-1]:
        c.set(x, y, CYAN_GLOW, 5, glint=True)
    c.set(4, 9, AMBER_GLOW, 5, glint=True)
    c.set(11, 4, AMBER_GLOW, 5, glint=True)
    c.set(12, 4, AMBER_GLOW, 2, glint=True)
    c.set(11, 3, AMBER_GLOW, 2, glint=True)
    keys(c, 12, (ORANGE, 4))
    return c


@texture("block/freighter_console_side")
def freighter_console_side(rng):
    return terminal_side(rng)


def route_mark(c: Canvas, r):
    """一条斜的航线连着两个点。"""
    c.line(5, 10, 10, 5, r, 3)
    c.set(5, 10, r, 5)
    c.set(10, 5, r, 5)
    c.set(6, 10, r, 1)
    c.set(11, 5, r, 1)


@texture("block/freighter_console_top")
def freighter_console_top(rng):
    return emblem_top(rng, ORANGE, route_mark)


# ---- 货船的引擎和漏电的线管 ----

@texture("block/freighter_engine", refs=("block/beacon",))
def freighter_engine(rng):
    """货船的引擎喷口：一圈深色的喷管（唇边上左受光），里面一层层亮到中间发白的蓝光；四角螺栓。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if d >= 6.9:
            c.set(x, y, SLATE, 2)
        elif d >= 5.9:
            c.set(x, y, SLATE, 4 if dx + dy < -1.5 else (2 if dx + dy > 1.5 else 3), glint=True)
        elif d >= 5.2:
            c.set(x, y, SLATE, 0)
        elif d >= 4.2:
            c.set(x, y, BLUE_GLOW, 2, glint=True)
        elif d >= 2.9:
            c.set(x, y, BLUE_GLOW, 3, glint=True)
        elif d >= 1.6:
            c.set(x, y, BLUE_GLOW, 4, glint=True)
        else:
            c.set(x, y, BLUE_GLOW, 5)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.bolt(x, y, SLATE, 3)
    return c


@texture("block/live_conduit")
def live_conduit(rng):
    """漏电的线管：剥开的线槽里三股铜线，两处断开，冒着黄白的火花；左右接得上。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.clusters(rng, 0, 1, 15, 3, -1, 2, 2)
    c.clusters(rng, 0, 12, 15, 14, -1, 2, 2)
    c.hline(0, 15, 0, GRAPHITE, 5)
    c.hline(0, 15, 15, GRAPHITE, 1)
    c.hline(0, 15, 4, GRAPHITE, 1)
    c.rect(0, 5, 15, 10, GRAPHITE, 0)
    c.hline(0, 15, 11, GRAPHITE, 5)
    for y, lv, gap in ((6, 4, (4, 6)), (8, 3, (10, 12)), (9, 2, (2, 3))):
        for x in range(16):
            if gap[0] <= x <= gap[1]:
                continue
            c.set(x, y, COPPER, lv + (1 if (x + y) % 4 == 0 else 0))
    for x, y, r, lv in ((5, 6, WHITE_GLOW, 5), (4, 5, YELLOW_GLOW, 4), (6, 7, YELLOW_GLOW, 4), (5, 7, YELLOW_GLOW, 3),
                        (11, 8, WHITE_GLOW, 5), (10, 7, YELLOW_GLOW, 4), (12, 9, YELLOW_GLOW, 3), (11, 9, YELLOW_GLOW, 4),
                        (2, 9, YELLOW_GLOW, 3)):
        c.set(x, y, r, lv, glint=True)
    for x in (2, 13):
        screw(c, x, 2, GRAPHITE, 3)
        screw(c, x, 13, GRAPHITE, 3)
    return c


# ---- 物品 ----

def drive(core, core_glow) -> Canvas:
    """三档驱动：斜放的一节圆柱（左下到右上），两端金属箍，中间一节带散热片的芯，芯里一道亮线。"""
    c = Canvas()
    # 斜着画的圆柱按对角线分带：s 是横截面方向（x+y），t 是沿轴方向（x−y）。
    # 两者奇偶相同，每条带都取相邻两条对角线，不然会断成棋盘格。
    shade = {-5: 1, -4: 1, -3: 2, -2: 2, -1: 1, 0: 0, 1: 0, 2: -1, 3: -1, 4: -1}
    for x, y in c.cells():
        s, t = x + y - 15, x - y
        if s not in shade or abs(t) > 12 or (abs(t) > 11 and s in (-5, -4, 3, 4)):
            continue
        k = shade[s]
        a = abs(t)
        if a >= 9:
            c.set(x, y, STEEL, 4 + k, glint=True)              # 两端的金属箍
        elif a in (7, 8) or a in (3, 4):
            c.set(x, y, STEEL, 2 + k, glint=True)              # 箍和芯之间的槽、散热片
        elif s in (-3, -2):
            c.set(x, y, core_glow, 4, glint=True)              # 芯里的一道亮线
        else:
            c.set(x, y, core, 3 + k, glint=True)
    c.outline()
    return c


@texture("item/cadmium_drive", refs=("item/blaze_rod", "item/end_rod"))
def cadmium_drive(rng):
    return drive(RED, RED_GLOW)


@texture("item/emeril_drive")
def emeril_drive(rng):
    return drive(GREEN, GREEN_GLOW)


@texture("item/indium_drive")
def indium_drive(rng):
    return drive(VIOLET, VIOLET_GLOW)


@texture("item/credit_chip", refs=("item/gold_ingot", "item/name_tag"))
def credit_chip(rng):
    """信用点芯片：一枚金色的卡片，中间压出一块凹面和一道横线，左边一排青色的触点。"""
    c = Canvas()
    c.rect(2, 4, 13, 11, GOLD_FOIL, 3)
    for x, y in ((2, 4), (13, 4), (2, 11), (13, 11)):
        c.clear(x, y)
    c.hline(3, 12, 5, GOLD_FOIL, 4)
    c.vline(3, 5, 10, GOLD_FOIL, 4)
    c.hline(4, 12, 10, GOLD_FOIL, 2)
    c.recess(6, 6, 11, 9, GOLD_FOIL, 3)
    c.hline(7, 10, 7, GOLD_FOIL, 5)
    for y in (6, 8):
        c.set(4, y, TEAL, 5)
        c.set(4, y + 1, TEAL, 3)
    c.outline()
    c.set(3, 5, GOLD_FOIL, 5, glint=True)
    return c


@texture("item/emergency_receiver", refs=("item/compass_00", "item/recovery_compass_00"))
def emergency_receiver(rng):
    """应急信号接收器：灰色的手持机，屏幕上一道橙色的波形，下面三个键（一个红的），天线顶上一颗红灯。"""
    c = Canvas()
    c.rect(3, 4, 11, 14, STEEL, 4)
    c.vline(3, 4, 14, STEEL, 5)
    c.vline(4, 4, 14, STEEL, 5)
    c.vline(10, 4, 14, STEEL, 3)
    c.vline(11, 4, 14, STEEL, 2)
    c.hline(3, 11, 4, STEEL, 5)
    c.rect(4, 6, 10, 9, SCREEN, 1)
    c.hline(4, 10, 6, SCREEN, 2)
    wave = [(4, 8), (5, 8), (6, 7), (7, 9), (8, 7), (9, 8), (10, 8)]
    for (xa, ya), (xb, yb) in zip(wave, wave[1:]):
        c.line(xa, ya, xb, yb, ORANGE_GLOW, 4)
    c.hline(4, 10, 10, STEEL, 5)
    for x in (5, 7):
        c.set(x, 12, GRAPHITE, 2)
    c.set(9, 12, RED, 4)
    c.vline(10, 1, 3, GRAPHITE, 3)
    c.set(10, 0, RED_GLOW, 5)
    c.outline()
    c.set(10, 0, RED_GLOW, 4, glint=True)
    c.set(4, 5, STEEL, 6, glint=True)
    return c


@texture("item/launch_fuel", refs=("item/potion", "item/bucket"))
def launch_fuel(rng):
    """发射燃料罐：红色的罐身（圆柱，左边受光），灰色的罐口和盖子，中间一圈黄色的标签。"""
    c = Canvas()
    c.cylinder(4, 4, 11, 14, RED, 3)
    c.hline(5, 10, 3, STEEL, 4)
    c.set(5, 3, STEEL, 5)
    c.rect(6, 1, 9, 2, STEEL, 4)
    c.hline(6, 9, 1, STEEL, 5)
    c.vline(9, 1, 2, STEEL, 3)
    c.rect(4, 8, 11, 11, HAZARD, 4)
    c.vline(4, 8, 11, HAZARD, 5)
    c.vline(10, 8, 11, HAZARD, 3)
    c.vline(11, 8, 11, HAZARD, 2)
    c.hline(6, 9, 9, GRAPHITE, 1)
    c.hline(7, 8, 10, GRAPHITE, 1)
    c.clear(4, 14)
    c.clear(11, 14)
    c.outline()
    c.set(5, 5, RED, 6, glint=True)
    return c


@texture("item/space_warper", refs=("item/ender_eye", "item/heart_of_the_sea"))
def space_warper(rng):
    """空间翘曲器：一圈金属环（上左受光，四个卡口），环里悬着一块发紫光的菱形晶体。"""
    c = Canvas()
    for x, y in c.cells():
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if 5.5 <= d < 7.3:
            c.set(x, y, STEEL, 5 if dx + dy < -2 else (3 if dx + dy > 2 else 4))
        elif d < 5.5 and abs(dx) + abs(dy) >= 5.0:
            c.set(x, y, VIOLET, 1 if dx + dy < 0 else 0)     # 环里暗紫色的场
        elif abs(dx) + abs(dy) < 5.0:
            t = (abs(dx) + abs(dy)) / 5.0
            lv = 5 if t < 0.3 else (4 if t < 0.65 else 3)
            if dx < 0 and dy < 0:
                lv = min(5, lv + 1)
            elif dx > 0 and dy > 0:
                lv -= 1
            c.set(x, y, VIOLET_GLOW, lv, glint=True)
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        c.set(x, y, GRAPHITE, 3)
    c.outline()
    c.set(6, 5, WHITE_GLOW, 5, glint=True)
    return c


@texture("item/survey_data", refs=("item/map", "item/filled_map"))
def survey_data(rng):
    """勘探数据：一张切了角的深色存储卡，青色的电路走线连着焊点，一角亮着读数灯，下边一排金手指。"""
    c = Canvas()
    for x, y in c.cells(2, 3, 13, 12):
        if x - 9 > y - 3 and y < 7:
            continue
        c.set(x, y, GRAPHITE, 2)
    c.hline(2, 9, 3, GRAPHITE, 4)
    c.vline(2, 3, 12, GRAPHITE, 4)
    c.line(10, 4, 12, 6, GRAPHITE, 4)
    for x in range(4, 10):
        c.set(x, 6, CYAN_GLOW, 2)
    for x in range(4, 12):
        c.set(x, 9, CYAN_GLOW, 2)
    for y in range(6, 10):
        c.set(9, y, CYAN_GLOW, 2)
    for x, y in ((4, 6), (4, 9), (11, 9)):
        c.set(x, y, CYAN_GLOW, 4, glint=True)
    for x in range(4, 12, 2):
        c.set(x, 12, GOLD_FOIL, 4, glint=True)
    c.outline()
    c.set(11, 11, WHITE_GLOW, 5, glint=True)
    c.set(10, 11, CYAN_GLOW, 4, glint=True)
    return c
