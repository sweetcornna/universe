"""深空的贴图（ADR-0034 §四、§七，画法照 ADR-0031）：太空残骸和废弃前哨站的方块、打捞物、牵引光束模块、引力抓取器。

残骸照真的航天器画（ADR-0025）：
- 舱板是晒得发黄的白漆铝板，左右两片错缝拼接，沿缝一排铆钉；微流星体打出的小坑（坑底暗，右下的坑沿受光），
  一块掉了漆露出铝的底子，一道斜着的烧蚀痕。
- 桁架是铝的方框加两根交叉的斜撑，交点一块连接板，下梁上绑着一根电缆，空格透明。
- 散热板照国际空间站的散热器：白色的面板，一道道横的冷却管鼓起来，有一处被打穿了，裂口往外翻。
- 推进剂罐照航天飞机的外储箱：发黄的隔热泡沫（粗糙，允许抖动），一圈加强环，几块泡沫掉了露出铝罐。
  罐是一个大球，顶面也是泡沫，换成球瓣的焊缝。
- 导管是一束管线：铝管、橙色的保温管、黑色的电缆（隔一段一道警示黄的标签）、细铜管，每格一道卡箍；
  断了的一根电缆头上还冒着火星。顶面是这束管子的截面。
- 货舱是褪了色的黄色货柜，竖的瓦楞，上下钢轨，四角铸件，喷着编号；顶上是带把手的舱盖，四周一圈警示条。
- 反应堆屏蔽层是四块凸起的深色钨板，中间钉着一块辐射警示牌；堆芯是控制棒之间透出的切连科夫蓝光，
  一明一暗、光斑往上走（8 帧动画）。
- 舷窗是厚框里一块旧玻璃（半透明），一道反光，一处被打出的放射状裂纹。
- 舱灯平时是暖白的应急灯，报警时红光从左往右扫（8 帧动画），关着时是一块灰白的磨砂灯罩。
- 甲板是防滑的菱形花纹钢板，中间一道座椅导轨（空间站上固定东西用的）。
- 发动机侧面是一根根钎焊在一起的再生冷却铜管，两道钢箍，熏得一块块发黑；顶面是喷口，里面黑洞洞的，
  背光的内壁映出一点亮。
- 前哨站：太阳能板几片碎了、一片烧坏了，右下积着灰；侧面是电池层、蜂窝铝的底板和边框。
  天线是网状的抛物面，三根撑杆托着金色的馈源。设备柜的屏幕早就黑了，裂了一道，只剩一颗琥珀色的待机灯还亮着。

物品：一块撕裂、折了角的舱板（废金属），两块叠着的钛合金板，一块断了半截、烧焦的电路板（打捞的电子元件），
一根燃料组件（打捞的反应堆芯，窗里透出蓝光），牵引光束模块（芯片：飞船往下照出一道光，吸着一只货箱），
引力抓取器（装进多功能工具的枪管组件：三圈紫光的线圈，前面张开的叉形发射极夹着一团紫光）。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import Ramp, ramp
from gear import cells_of, limit_colors, lit_outline, module_chip, module_overlay, paint, weapon
from palette import (AMBER_GLOW, BASALT, BLUE, BLUE_GLOW, COPPER, CYAN_GLOW, GLASS, GOLD_FOIL, GRAPHITE, HAZARD, ORANGE,
                     ORANGE_GLOW, RED_GLOW, RUBBER, SCREEN, STEEL, TITANIUM, VIOLET_GLOW, WHITE, WHITE_GLOW, register)
from parts import casing
from planet import field
from recipe import texture
from ships import scratch, seam_h, seam_v, stud

# 这一组自己用的色阶
HULL = register(ramp("deepspace_hull", 86, 0.020, n=7, lo=0.30, hi=0.88, shift=16))                # 晒黄的白漆铝板
FOAM = register(ramp("deepspace_foam", 54, 0.105, n=7, lo=0.28, hi=0.80, shift=14, peak=0.5))      # 老化的隔热泡沫
CARGO = register(ramp("deepspace_cargo", 88, 0.100, n=7, lo=0.30, hi=0.86, shift=14, peak=0.6))     # 褪色的货柜黄
PCB = register(ramp("deepspace_pcb", 158, 0.075, n=6, lo=0.24, hi=0.66, shift=12))                 # 电路板的绿


# ---------------------------------------------------------------- 小工具

def pit(c: Canvas, x: int, y: int, r: Ramp, base: int):
    """微流星体打出的小坑：坑底暗两级，右下的坑沿受光（凹进去的东西右下亮）。"""
    c.set(x, y, r, base - 2, glint=True)
    if c.get(x + 1, y + 1):
        c.set(x + 1, y + 1, r, base + 1, glint=True)


def pits(c: Canvas, rng: random.Random, n: int, x0: int, y0: int, x1: int, y1: int, r: Ramp):
    """在 r 色阶的平整地方打 n 个小坑（缝、铆钉上不打）。"""
    for _ in range(n * 4):
        if n <= 0:
            return
        x, y = rng.randint(x0, x1), rng.randint(y0, y1)
        here, below = c.get(x, y), c.get(x + 1, y + 1)
        if here and below and here[0] is r and below[0] is r and abs(here[1] - below[1]) <= 1 and (x, y) not in c.glints:
            pit(c, x, y, r, here[1])
            n -= 1


def scorch(c: Canvas, rng: random.Random, x0: float, y0: float, x1: float, y1: float, width: float = 2.0, strength: int = 2):
    """一道烧蚀痕：沿 (x0, y0)→(x1, y1) 的一条带子变暗，中间深、两边浅、断断续续。"""
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    for x, y in list(c.cells()):
        if not c.get(x, y):
            continue
        t = ((x - x0) * dx + (y - y0) * dy) / (n * n)
        if not 0 <= t <= 1:
            continue
        d = abs((x - x0) * dy - (y - y0) * dx) / n
        if d > width:
            continue
        k = 1 - d / width
        if rng.random() < 0.3 + 0.6 * k:
            c.shift(x, y, -max(1, round(strength * k)))


def peel(c: Canvas, rng: random.Random, cx: float, cy: float, rad: float, bare: Ramp, lv: int) -> set:
    """掉漆（掉泡沫）：一块不规则的地方露出底下的金属。底子比漆面低一层：左上沿是漆的影子，右下沿受光。"""
    cells = set()
    for x, y in c.cells():
        if math.hypot(x - cx, y - cy) <= rad + rng.uniform(-0.6, 0.5) and c.get(x, y):
            cells.add((x, y))
    for x, y in cells:
        if (x - 1, y) not in cells or (x, y - 1) not in cells:
            c.set(x, y, bare, lv - 1)
        elif (x + 1, y) not in cells or (x, y + 1) not in cells:
            c.set(x, y, bare, lv + 1)
        else:
            c.set(x, y, bare, lv)
    return cells


def plate_edges(c: Canvas, r: Ramp, base: int):
    """一格方块的四边：上、左边受光，下、右边背光，和相邻的方块拼出连续的缝。"""
    c.hline(0, 15, 0, r, base + 1)
    c.vline(0, 0, 15, r, base + 1)
    c.hline(0, 15, 15, r, base - 2)
    c.vline(15, 0, 15, r, base - 2)
    c.set(15, 0, r, base)
    c.set(0, 15, r, base)


# ---------------------------------------------------------------- 残骸的船身

@texture("block/derelict_plating", refs=("block/iron_block", "block/white_concrete"))
def derelict_plating(rng):
    c = Canvas()
    base = 4
    c.fill(HULL, base)
    # 晒黄、积灰的斑：大片、边缘软，不是一粒粒的
    n = field(rng, ((4, 4), (8, 8)), (0.7, 0.3))
    for x, y in c.cells():
        if n[y][x] > 0.68:
            c.shift(x, y, -1)
    plate_edges(c, HULL, base)
    # 两片板错缝：竖缝在中间，左片的横缝高、右片的横缝低
    seam_v(c, 7, HULL, base, 1, 14)
    seam_h(c, 4, HULL, base, 1, 6)
    seam_h(c, 11, HULL, base, 9, 14)
    for y in (2, 7, 13):
        stud(c, 5, y, HULL, base)
    for y in (2, 8, 13):
        stud(c, 10, y, HULL, base)
    # 左片下面掉了一块漆，露出铝
    peel(c, rng, 3.2, 9.6, 1.4, STEEL, 5)
    pits(c, rng, 3, 1, 1, 13, 13, HULL)
    # 右下角一道烧蚀痕
    scorch(c, rng, 9.0, 15.5, 15.5, 8.5, 2.2, 2)
    scratch(c, rng, 9, 2, 13, 7, 1)
    return c


@texture("block/derelict_frame", refs=("block/iron_bars", "block/scaffolding_side"))
def derelict_frame(rng):
    c = Canvas()
    r = STEEL
    # 方框：两像素宽的铝梁，外侧一道受光，里侧一道背光
    for x in range(16):
        c.set(x, 0, r, 6)
        c.set(x, 1, r, 4)
        c.set(x, 14, r, 4)
        c.set(x, 15, r, 2)
    for y in range(1, 15):
        c.set(0, y, r, 6)
        c.set(1, y, r, 4)
        c.set(14, y, r, 4)
        c.set(15, y, r, 2)
    c.set(15, 0, r, 4)
    c.set(0, 15, r, 4)
    c.set(1, 1, r, 5)
    c.set(14, 14, r, 3)
    # 两根斜撑：朝上的一侧受光
    for i in range(2, 14):
        c.set(i, i, r, 3)
        if i < 13:
            c.set(i + 1, i, r, 5)
        x = 15 - i
        c.set(x, i, r, 3)
        if i < 13:
            c.set(x - 1, i, r, 5)
    # 角上的连接板（三角形）
    for x, y, lv in ((2, 2, 5), (3, 2, 5), (2, 3, 5), (13, 2, 5), (12, 2, 5), (13, 3, 4), (2, 13, 4), (2, 12, 5), (3, 13, 3),
                     (13, 13, 3), (12, 13, 3), (13, 12, 3)):
        c.set(x, y, r, lv)
    # 交点一块连接板，一颗螺栓
    c.panel(6, 6, 9, 9, r, 4, depth=1)
    c.bolt(7, 7, GRAPHITE, 3)
    # 下梁里侧绑着一根电缆，两道扎带
    for x in range(3, 13):
        if c.get(x, 13) is None:
            c.set(x, 13, RUBBER, 2 if x % 2 else 1)
    for x in (5, 10):
        c.set(x, 13, STEEL, 5, glint=True)
    c.edge_wear(rng, 0, 0, 15, 15, 0.15)
    return c


@texture("block/derelict_radiator", refs=("block/smooth_quartz", "block/white_concrete"))
def derelict_radiator(rng):
    c = Canvas()
    # 面板上一道道横的冷却管：每 4 行一根，管顶受光、管底一道影子
    for y in range(16):
        lv = (4, 5, 3, 4)[y % 4]
        c.hline(0, 15, y, WHITE, lv)
    # 面板之间的铰链缝在右边
    c.vline(15, 0, 15, GRAPHITE, 2)
    c.vline(0, 0, 15, WHITE, 5)
    for y in range(1, 16, 4):
        c.set(15, y, GRAPHITE, 3)
    # 紫外线照黄了几块
    c.clusters(rng, 1, 0, 14, 15, -1, 8, 3, only=WHITE)
    # 被打穿的一处：黑洞，裂口往外翻（右下的翻边受光），四道裂纹
    hx, hy = 9, 9
    c.rect(hx, hy, hx + 1, hy, GRAPHITE, 0)
    c.set(hx, hy + 1, GRAPHITE, 1, glint=True)
    for x, y, lv in ((hx + 2, hy, 6), (hx + 1, hy + 1, 6), (hx - 1, hy, 2), (hx, hy - 1, 2), (hx + 1, hy - 1, 3)):
        c.set(x, y, WHITE, lv, glint=True)
    for path in (((hx + 3, hy - 1), (hx + 4, hy - 2)), ((hx - 2, hy + 1), (hx - 3, hy + 2), (hx - 4, hy + 2)),
                 ((hx + 2, hy + 2), (hx + 3, hy + 3)), ((hx - 1, hy - 2), (hx - 1, hy - 3))):
        for x, y in path:
            c.set(x, y, WHITE, 2, glint=True)
    return c


def foam(rng: random.Random) -> Canvas:
    """泡沫的底子：一粒粒鼓起来的爆米花状表面（受光的一粒亮、底下一点影）。"""
    c = Canvas()
    n = field(rng, ((4, 4), (8, 8), (16, 16)), (0.5, 0.3, 0.2))
    for x, y in c.cells():
        c.set(x, y, FOAM, 4 if n[y][x] > 0.62 else 2 if n[y][x] < 0.3 else 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 5, 2)
    for _ in range(7):
        x, y = rng.randint(0, 14), rng.randint(0, 14)
        c.set(x, y, FOAM, 5, glint=True)
        c.set(x + 1, y + 1, FOAM, 2, glint=True)
    return c


@texture("block/derelict_tank_side", rough=True, refs=("block/orange_terracotta", "block/copper_block"))
def derelict_tank_side(rng):
    c = foam(rng)
    # 一圈加强环：凸起的一道，上亮下暗
    c.hline(0, 15, 11, FOAM, 5)
    c.hline(0, 15, 12, FOAM, 4)
    c.hline(0, 15, 13, FOAM, 1)
    # 泡沫掉了两块，露出铝罐
    peel(c, rng, 4.0, 5.0, 2.3, STEEL, 4)
    peel(c, rng, 12.5, 2.0, 1.2, STEEL, 4)
    # 掉块的下面挂着几道泡沫屑流下来的痕
    for x in (3, 5):
        for y in range(8, 10):
            c.shift(x, y, -1)
    return c


@texture("block/derelict_tank_top", rough=True)
def derelict_tank_top(rng):
    c = foam(rng)
    # 球瓣的焊缝：一横一竖，压出一道浅沟
    for i in range(16):
        c.set(i, 7, FOAM, 1)
        c.set(i, 8, FOAM, 4)
        c.set(7, i, FOAM, 1)
        c.set(8, i, FOAM, 4)
    c.set(7, 7, FOAM, 0)
    c.set(8, 8, FOAM, 5)
    peel(c, rng, 11.5, 11.5, 1.8, STEEL, 4)
    return c


# 导管里从左到右的几根线：(起, 止, 色阶, 截面的中心级数)
CONDUIT_LINES = ((0, 3, STEEL, 4), (4, 5, RUBBER, 2), (6, 8, ORANGE, 3), (9, 10, COPPER, 4), (11, 11, RUBBER, 2),
                 (12, 15, STEEL, 4))


@texture("block/derelict_conduit_side", refs=("block/chain", "block/copper_block"))
def derelict_conduit_side(rng):
    c = Canvas()
    for x0, x1, r, base in CONDUIT_LINES:
        c.cylinder(x0, 0, x1, 15, r, base, amp=1 if x1 > x0 else 0)
    # 管子之间压出一道暗缝
    for x0, x1, r, base in CONDUIT_LINES[1:]:
        if x1 > x0:
            c.vline(x0, 0, 15, r, base - 2)
    # 电缆上隔一段一道警示黄的标签
    for y in (2, 3):
        c.set(4, y, HAZARD, 4)
        c.set(5, y, HAZARD, 3)
    c.set(11, 9, HAZARD, 3)
    c.set(11, 10, HAZARD, 3)
    # 每格一道卡箍：横着的钢带，上亮下暗，两头一颗螺钉
    c.hline(0, 15, 6, STEEL, 6)
    c.hline(0, 15, 7, STEEL, 3)
    c.set(1, 6, GRAPHITE, 2, glint=True)
    c.set(14, 6, GRAPHITE, 2, glint=True)
    # 一根电缆断了：断口发红，冒两点火星
    c.set(11, 13, GRAPHITE, 1)
    c.set(11, 14, ORANGE_GLOW, 4, glint=True)
    c.set(11, 15, ORANGE_GLOW, 2, glint=True)
    c.set(10, 13, ORANGE_GLOW, 5, glint=True)
    return c


@texture("block/derelict_conduit_top")
def derelict_conduit_top(rng):
    c = Canvas()
    # 截面：背板上一圈管子的切口，管壁亮、管腔暗
    c.fill(GRAPHITE, 2)
    c.bevel(0, 0, 15, 15, 1, -1)
    for cx, cy, rad, r, hi, lo in ((4.0, 4.0, 2.8, STEEL, 6, 3), (11.0, 4.5, 2.4, ORANGE, 4, 2), (4.5, 11.0, 2.2, COPPER, 5, 3),
                                   (11.5, 11.5, 2.8, STEEL, 6, 3)):
        inner = set(c.disc_cells(cx, cy, rad - 1.2))
        for x, y in c.disc_cells(cx, cy, rad):
            if (x, y) in inner:
                # 管腔：右下的内壁背着光，左上的内壁映一点亮
                c.set(x, y, GRAPHITE, 1 if x + y < cx + cy else 0)
            else:
                c.set(x, y, r, hi if x + y < cx + cy else lo)
    # 中间两根电缆的芯：铜芯外面一圈黑皮
    for cx, cy in ((8, 8), (7, 7)):
        c.set(cx, cy, RUBBER, 1)
    c.set(8, 7, COPPER, 5, glint=True)
    c.set(7, 8, COPPER, 4, glint=True)
    return c


@texture("block/derelict_cargo_side", refs=("block/yellow_terracotta", "block/iron_block"))
def derelict_cargo_side(rng):
    c = Canvas()
    # 竖的瓦楞：每 4 列一道，受光的棱、平面、背光的槽
    for x in range(16):
        c.vline(x, 2, 13, CARGO, (5, 4, 3, 2)[x % 4])
    # 上下两道钢轨
    c.hline(0, 15, 0, STEEL, 5)
    c.hline(0, 15, 1, STEEL, 3)
    c.hline(0, 15, 14, STEEL, 4)
    c.hline(0, 15, 15, STEEL, 2)
    # 四角的铸件：一块凸起的钢，中间一个吊装孔
    for x0, y0 in ((0, 0), (14, 0), (0, 14), (14, 14)):
        c.panel(x0, y0, x0 + 1, y0 + 1, GRAPHITE, 3)
        c.set(x0 + 1, y0 + 1, GRAPHITE, 1)
    # 喷的编号：三块短的字
    for x, y in ((3, 4), (4, 4), (3, 5), (3, 6), (4, 6), (6, 4), (6, 5), (6, 6), (7, 6), (9, 4), (10, 4), (10, 5), (9, 6), (10, 6)):
        c.set(x, y, GRAPHITE, 2)
    # 掉漆、刮痕、一道熏黑
    c.edge_wear(rng, 0, 2, 15, 13, 0.25)
    scratch(c, rng, 2, 8, 13, 12, 2, -1)
    scorch(c, rng, 0.0, 13.5, 6.0, 8.0, 1.8, 1)
    return c


@texture("block/derelict_cargo_top", refs=("block/iron_trapdoor",))
def derelict_cargo_top(rng):
    c = Canvas()
    # 一圈警示条，里面是舱盖
    c.stripes(0, 0, 15, 15, HAZARD, 4, GRAPHITE, 2, 4, 2)
    c.panel(2, 2, 13, 13, CARGO, 4, depth=1)
    c.clusters(rng, 3, 3, 12, 12, -1, 4, 3)
    # 凹进去的把手
    c.recess(5, 7, 10, 9, GRAPHITE, 3)
    c.hline(6, 9, 8, STEEL, 5)
    # 四个卡扣
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.bolt(x, y, STEEL, 4)
    return c


# ---------------------------------------------------------------- 反应堆

def trefoil(c: Canvas, x0: int, y0: int, size: int = 8):
    """辐射警示牌：黄底，中间一个圆点，三片扇叶（朝下、左上、右上），各张 60°。"""
    cx = cy = (size - 1) / 2
    for x, y in c.cells(x0, y0, x0 + size - 1, y0 + size - 1):
        dx, dy = x - x0 - cx, y - y0 - cy
        r = math.hypot(dx, dy)
        a = math.degrees(math.atan2(dy, dx))
        blade = any(abs((a - b + 180) % 360 - 180) <= 30 for b in (90, -30, -150))
        k = r < 1.1 or (1.9 <= r <= size / 2 - 0.6 and blade)
        c.set(x, y, GRAPHITE if k else HAZARD, 1 if k else 4)


@texture("block/reactor_shielding", refs=("block/netherite_block", "block/polished_blackstone"))
def reactor_shielding(rng):
    c = Canvas()
    c.fill(GRAPHITE, 3)
    # 四块凸起的钨板
    for x0, y0 in ((0, 0), (8, 0), (0, 8), (8, 8)):
        c.panel(x0, y0, x0 + 7, y0 + 7, GRAPHITE, 3, depth=2)
        c.clusters(rng, x0 + 2, y0 + 2, x0 + 5, y0 + 5, 1, 1, 2)
        c.set(x0 + 7, y0 + 7, GRAPHITE, 0)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.bolt(x, y, STEEL, 3)
    # 中间钉着一块辐射警示牌
    trefoil(c, 3, 3, 10)
    c.bevel(3, 3, 12, 12, 1, -1)
    c.set(3, 3, HAZARD, 5, glint=True)
    c.set(12, 12, HAZARD, 2, glint=True)
    return c


REACTOR_FRAMES = 8


@texture("block/reactor_core", frames=REACTOR_FRAMES, refs=("block/beacon", "block/sea_lantern"))
def reactor_core(rng):
    """控制棒之间透出切连科夫蓝光：整体一明一暗，几团亮斑往上走。"""
    c = Canvas(16, 16 * REACTOR_FRAMES)
    rods = (0, 5, 6, 10, 11, 15)
    bubbles = [(rng.randint(1, 14), rng.randint(0, 11)) for _ in range(6)]
    for i in range(REACTOR_FRAMES):
        oy = 16 * i
        pulse = 0.5 + 0.5 * math.cos(i / REACTOR_FRAMES * math.tau)
        for x, y in c.cells(0, oy + 2, 15, oy + 13):
            if x in rods:
                continue
            ly = y - oy
            d = math.hypot(x - 7.5, ly - 7.5)
            v = 2.6 + 1.8 * pulse - d * 0.22
            for bx, by in bubbles:
                yy = (by - i * 2) % 12 + 2
                if bx == x and abs(yy - ly) <= 1:
                    v += 1.6 - abs(yy - ly) * 0.8
            lv = max(1, min(5, round(v)))
            c.set(x, y, CYAN_GLOW if lv >= 3 else BLUE_GLOW, lv if lv >= 3 else lv + 1, glint=True)
        # 控制棒：钢的圆柱，刻着刻度
        for x0, x1 in ((0, 0), (5, 6), (10, 11), (15, 15)):
            c.cylinder(x0, oy + 2, x1, oy + 13, STEEL, 4, amp=1 if x1 > x0 else 0)
            for y in range(oy + 3, oy + 13, 3):
                c.set(x1, y, STEEL, 2, glint=True)
        # 上下两块压板
        for y0 in (oy, oy + 14):
            c.panel(0, y0, 15, y0 + 1, GRAPHITE, 3)
            for x in (2, 8, 13):
                c.set(x, y0, STEEL, 5, glint=True)
    return c


# ---------------------------------------------------------------- 舷窗、舱灯、甲板

@texture("block/derelict_window", refs=("block/glass", "block/tinted_glass"))
def derelict_window(rng):
    c = Canvas()
    c.fill(HULL, 4)
    plate_edges(c, HULL, 4)
    c.hline(1, 14, 1, HULL, 5)
    c.vline(1, 1, 14, HULL, 5)
    c.frame(2, 2, 13, 13, RUBBER, 1)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, HULL, 2, glint=True)
    for x, y in c.cells(3, 3, 12, 12):
        c.set(x, y, GLASS, 1 if x + y > 17 else 2, alpha=96)
    # 一道斜的反光
    for x, y in c.cells(3, 3, 12, 12):
        if x + y in (9, 10):
            c.set(x, y, GLASS, 4, alpha=150)
        elif x + y == 13:
            c.set(x, y, GLASS, 3, alpha=120)
    # 被打出的放射状裂纹
    ix, iy = 9, 8
    c.set(ix, iy, GLASS, 5, alpha=210)
    for path in ((1, -1), (-1, 1), (1, 1), (-1, -1), (0, 1), (1, 0)):
        n = rng.randint(2, 3)
        for k in range(1, n + 1):
            x, y = ix + path[0] * k, iy + path[1] * k
            if 3 <= x <= 12 and 3 <= y <= 12:
                c.set(x, y, GLASS, 5, alpha=170)
    c.edge_wear(rng, 0, 0, 15, 15, 0.2)
    return c


def light_panel(rng: random.Random, c: Canvas, oy: int = 0):
    """舱灯的壳：白漆的面板，一块凹进去的灯槽，下面一道警示条，四角螺钉。灯罩在 (3–12, 5–10)。"""
    c.rect(0, oy, 15, oy + 15, HULL, 4)
    c.hline(0, 15, oy, HULL, 5)
    c.vline(0, oy, oy + 15, HULL, 5)
    c.hline(1, 15, oy + 15, HULL, 2)
    c.vline(15, oy + 1, oy + 15, HULL, 2)
    c.recess(2, oy + 4, 13, oy + 11, GRAPHITE, 3)
    for x in range(3, 13):
        c.set(x, oy + 13, HAZARD if x % 4 < 2 else GRAPHITE, 4 if x % 4 < 2 else 2)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        stud(c, x, oy + y, HULL, 4)


@texture("block/derelict_light", refs=("block/redstone_lamp", "block/sea_lantern"))
def derelict_light(rng):
    c = Canvas()
    light_panel(rng, c)
    # 关着：灰白的磨砂灯罩，竖的棱镜纹
    for x, y in c.cells(3, 5, 12, 10):
        c.set(x, y, WHITE, 2 if x % 2 else 3)
    c.hline(3, 12, 5, WHITE, 4)
    return c


@texture("block/derelict_light_on", refs=("block/redstone_lamp_on", "block/sea_lantern"))
def derelict_light_on(rng):
    c = Canvas()
    light_panel(rng, c)
    for x, y in c.cells(3, 5, 12, 10):
        d = abs(x - 7.5) / 5 + abs(y - 7.5) / 3
        c.set(x, y, WHITE_GLOW, max(2, min(5, round(5.2 - d * 2))) - (x % 2 if d > 0.6 else 0), glint=True)
    return c


LIGHT_FRAMES = 8


@texture("block/derelict_light_alarm", frames=LIGHT_FRAMES, refs=("block/redstone_lamp_on",))
def derelict_light_alarm(rng):
    """警报：红光一下下从左往右扫过灯罩。"""
    c = Canvas(16, 16 * LIGHT_FRAMES)
    for i in range(LIGHT_FRAMES):
        oy = 16 * i
        light_panel(rng, c, oy)
        sweep = 2 + i * 12 / LIGHT_FRAMES
        for x, y in c.cells(3, oy + 5, 12, oy + 10):
            d = abs(x - sweep)
            lv = 5 if d < 1 else 4 if d < 2.2 else 3 if d < 3.5 else 2
            c.set(x, y, RED_GLOW, lv - (1 if x % 2 and lv > 2 else 0), glint=True)
    return c


@texture("block/derelict_deck", refs=("block/iron_block", "block/iron_trapdoor"))
def derelict_deck(rng):
    c = Canvas()
    base = 3
    c.fill(STEEL, base)
    # 菱形花纹：4×4 一格一粒，棋盘格地一粒往右斜、一粒往左斜；每粒是凸起的一道短棱，朝上的一侧受光，脚下一点影
    for j, gy in enumerate((0, 4, 9, 13)):
        for i, gx in enumerate(range(0, 16, 4)):
            if (i + j) % 2 == 0:
                lugs = ((gx + 1, gy, 5), (gx + 2, gy + 1, 4), (gx + 3, gy + 2, 2))
            else:
                lugs = ((gx + 2, gy, 5), (gx + 1, gy + 1, 4), (gx, gy + 2, 2))
            for x, y, lv in lugs:
                if y <= 15:
                    c.set(x, y, STEEL, lv, glint=True)
    # 中间一道座椅导轨：一道暗槽，下沿受光，槽里一排孔
    c.hline(0, 15, 7, GRAPHITE, 2)
    c.hline(0, 15, 8, STEEL, 5)
    for x in range(1, 16, 3):
        c.set(x, 7, GRAPHITE, 0, glint=True)
    c.edge_wear(rng, 0, 0, 15, 15, 0.12)
    return c


# ---------------------------------------------------------------- 发动机

@texture("block/derelict_engine_side", rough=True, refs=("block/copper_block", "block/blast_furnace_side"))
def derelict_engine_side(rng):
    c = Canvas()
    # 竖的冷却管：每根 3 列，左边受光，中间本色，右边是钎焊的暗缝
    for x in range(16):
        c.vline(x, 0, 15, COPPER, (4, 3, 1)[x % 3])
    # 两道钢箍
    for y0 in (2, 11):
        c.hline(0, 15, y0, STEEL, 5)
        c.hline(0, 15, y0 + 1, STEEL, 3)
        for x in (1, 7, 13):
            c.set(x, y0, STEEL, 6, glint=True)
            c.set(x + 1, y0 + 1, STEEL, 2, glint=True)
    # 熏黑：大块的、边缘软的烟熏，越往下越重；最重的地方铜色都看不出来了
    n = field(rng, ((4, 4), (8, 8)), (0.6, 0.4))
    for x, y in c.cells():
        px = c.get(x, y)
        v = n[y][x] + 0.25 * (y / 15)
        if px[0] is COPPER and v > 0.62:
            if v > 0.86:
                c.set(x, y, BASALT, max(0, min(3, px[1])))
            else:
                c.shift(x, y, -1)
    return c


@texture("block/derelict_engine_top", refs=("block/blast_furnace_top",))
def derelict_engine_top(rng):
    c = Canvas()
    c.fill(GRAPHITE, 2)
    c.bevel(0, 0, 15, 15, 1, -1)
    cx = cy = 7.5
    for x, y in c.cells():
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        lit = (dx + dy) / max(d, 1e-6)    # 右下为正
        if d > 7.4:
            continue
        if d > 6.2:
            # 喷口的唇：凸起，左上受光
            c.set(x, y, STEEL, 5 if lit < -0.3 else 4 if lit < 0.4 else 2)
        elif d > 3.6:
            # 喷管内壁：放射状的冷却管，背光的右下内壁映出一点亮
            a = math.atan2(dy, dx)
            k = int((a + math.pi) / math.tau * 24) % 2
            lv = 2 + (1 if lit > 0.3 else 0) - k
            c.set(x, y, COPPER if lit > -0.5 else BASALT, max(0, lv))
        elif d > 2.2:
            c.set(x, y, BASALT, 1 if lit > 0.2 else 0)
        else:
            c.set(x, y, BASALT, 0)
    return c


# ---------------------------------------------------------------- 前哨站

@texture("block/outpost_solar_top", refs=("block/daylight_detector_top", "block/blue_stained_glass"))
def outpost_solar_top(rng):
    c = Canvas()
    for x, y in c.cells():
        # 每片电池上一道从左上到右下的反光
        k = (x % 4) + (y % 4)
        c.set(x, y, BLUE, 2 if k in (2, 3) else 1)
    for v in (0, 4, 8, 12):
        c.vline(v, 0, 15, STEEL, 4)
        c.hline(0, 15, v, STEEL, 4)
    for x in (0, 4, 8, 12):
        for y in (0, 4, 8, 12):
            c.set(x, y, STEEL, 5, glint=True)
    # 一片碎了：裂纹是亮的
    for x, y in ((5, 1), (6, 2), (6, 3), (7, 2), (5, 3)):
        c.set(x, y, BLUE, 4, glint=True)
    # 一片烧坏了，发黑
    for x, y in c.cells(9, 5, 11, 7):
        c.set(x, y, BASALT, 1 if (x + y) % 2 else 2)
    c.set(10, 6, ORANGE, 1, glint=True)
    # 右下积着灰
    for x, y in c.cells(6, 9, 15, 15):
        if (x + y) > 19 and rng.random() < 0.5 and c.get(x, y)[0] is BLUE:
            c.set(x, y, BLUE, 3)
    return c


@texture("block/outpost_solar_side")
def outpost_solar_side(rng):
    c = Canvas()
    # 最上面是电池层的边，下面一道背板，再下面是蜂窝铝的底板，最下面是边框
    c.hline(0, 15, 0, BLUE, 3)
    c.hline(0, 15, 1, BLUE, 1)
    c.hline(0, 15, 2, STEEL, 6)
    for x, y in c.cells(0, 3, 15, 12):
        k = (x + (2 if (y // 2) % 2 else 0)) % 4
        lv = 2 if k == 0 or (y % 2 == 0 and k == 1) else 4
        c.set(x, y, HULL, lv)
    c.panel(0, 13, 15, 15, STEEL, 4)
    for x in (2, 8, 13):
        c.set(x, 14, GRAPHITE, 2, glint=True)
    return c


@texture("block/outpost_antenna", refs=("block/iron_block", "block/lightning_rod"))
def outpost_antenna(rng):
    c = Canvas()
    c.fill(GRAPHITE, 2)
    cx = cy = 7.5
    # 抛物面：凹的，右下受光；网格是一圈圈的环和放射的肋
    c.shade_disc(cx, cy, 7.0, WHITE, 3, amp=1.5, spec=False, convex=False)
    for x, y in c.disc_cells(cx, cy, 7.0):
        dx, dy = x - cx, y - cy
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx) % (math.tau / 8)
        if abs(d - 3.6) < 0.5 or abs(d - 5.8) < 0.45 or a < 0.22:
            c.shift(x, y, -1)
        if d > 6.4:
            c.set(x, y, STEEL, 5 if dx + dy < 0 else 3)
    # 三根撑杆托着馈源
    for ex, ey in ((7, 1), (2, 11), (13, 11)):
        c.line(ex, ey, 7, 7, STEEL, 4)
    c.rect(7, 7, 8, 8, GOLD_FOIL, 3)
    c.set(7, 7, GOLD_FOIL, 5, glint=True)
    c.set(8, 8, GOLD_FOIL, 1, glint=True)
    return c


@texture("block/outpost_device_front", refs=("block/crafter_north", "block/furnace_front"))
def outpost_device_front(rng):
    c = Canvas()
    c.fill(STEEL, 2)
    # 两边的机架耳：一条亮的钢，上下两颗螺钉
    for x0 in (0, 14):
        c.panel(x0, 0, x0 + 1, 15, STEEL, 4)
        c.set(x0, 2, GRAPHITE, 1, glint=True)
        c.set(x0, 13, GRAPHITE, 1, glint=True)
    # 上面一块黑了的屏幕，积着灰，裂了一道
    c.recess(2, 1, 13, 7, SCREEN, 1)
    c.set(3, 2, SCREEN, 3, glint=True)
    c.set(4, 2, SCREEN, 2, glint=True)
    for x, y in ((9, 2), (10, 3), (10, 4), (11, 5), (8, 3)):
        c.set(x, y, SCREEN, 3, glint=True)
    c.clusters(rng, 3, 5, 12, 6, 1, 2, 2, only=SCREEN)
    # 中间一排拨动开关，一颗琥珀色的待机灯还亮着
    c.recess(2, 8, 13, 10, GRAPHITE, 3)
    for x, up in ((4, True), (6, False), (8, True)):
        c.set(x, 9 if up else 10, STEEL, 6, glint=True)
        c.set(x, 10 if up else 9, GRAPHITE, 1, glint=True)
    c.set(11, 9, AMBER_GLOW, 5, glint=True)
    c.set(12, 9, AMBER_GLOW, 2, glint=True)
    # 下面是通风口和一个数据口
    c.vent(2, 11, 9, 14, STEEL, 3)
    c.recess(11, 12, 13, 14, GRAPHITE, 2)
    c.set(12, 13, COPPER, 4, glint=True)
    return c


@texture("block/outpost_device_side")
def outpost_device_side(rng):
    c = casing(rng, 3)
    c.vent(4, 4, 11, 11, STEEL, 3)
    return c


@texture("block/outpost_device_top")
def outpost_device_top(rng):
    c = casing(rng, 3)
    # 两个电缆接头：黑橡胶的圈，中间钢的锁紧螺母
    for cx, cy in ((5.0, 5.0), (10.5, 10.5)):
        c.shade_disc(cx, cy, 1.8, RUBBER, 2, spec=False)
        c.set(round(cx), round(cy), STEEL, 5, glint=True)
    c.clusters(rng, 2, 2, 13, 13, 1, 3, 3)
    return c


# ---------------------------------------------------------------- 打捞物

# 撕下来的舱板的轮廓（像素中心在里面的算），右边、下边是撕裂的锯齿口
SCRAP_OUTLINE = ((1.5, 5.0), (9.0, 1.5), (12.5, 2.5), (12.0, 4.5), (14.5, 6.0), (12.5, 7.5), (14.5, 10.0), (11.5, 10.5),
                 (12.0, 13.0), (8.5, 12.0), (6.0, 14.5), (4.5, 12.0), (1.5, 12.5), (2.5, 9.0))
# 撕裂口是轮廓的第 2–10 段
SCRAP_TORN = range(2, 10)


def inside(poly, x: float, y: float) -> bool:
    """点在不在多边形里（射线法）。"""
    hit = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


def segment_distance(px: float, py: float, a, b) -> float:
    (x0, y0), (x1, y1) = a, b
    dx, dy = x1 - x0, y1 - y0
    t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - x0 - t * dx, py - y0 - t * dy)


@texture("item/scrap_metal", refs=("item/iron_ingot", "item/netherite_scrap"))
def scrap_metal(rng):
    """撕下来的一块舱板：白漆的正面，一排铆钉；斜着一道折痕，折下去的下半截背着光；
    右边、下边是撕裂的锯齿口，露出亮的铝。"""
    c = Canvas()
    fold = ((1.5, 10.0), (13.5, 5.5))
    for x, y in c.cells():
        px, py = x + 0.5, y + 0.5
        if not inside(SCRAP_OUTLINE, px, py):
            continue
        # 折痕下面那半截往后折：暗两级
        side = (py - fold[0][1]) * (fold[1][0] - fold[0][0]) - (px - fold[0][0]) * (fold[1][1] - fold[0][1])
        lv = 4 if side < 0 else 2
        if side < 0 and x + y < 9:
            lv = 5
        c.set(x, y, HULL, lv)
    # 折痕：一道亮的棱，下面一道影
    for x, y in list(c.cells()):
        if c.get(x, y) and segment_distance(x + 0.5, y + 0.5, *fold) < 0.55:
            c.set(x, y, HULL, 6, glint=True)
            if c.get(x, y + 1) and c.get(x, y + 1)[1] == 2:
                c.set(x, y + 1, HULL, 1, glint=True)
    # 撕裂口：露出亮的铝
    for x, y in list(c.cells()):
        if not c.get(x, y):
            continue
        d = min(segment_distance(x + 0.5, y + 0.5, SCRAP_OUTLINE[i], SCRAP_OUTLINE[(i + 1) % len(SCRAP_OUTLINE)])
                for i in SCRAP_TORN)
        if d < 0.9:
            c.set(x, y, STEEL, 6 if x + y < 17 else 4, glint=True)
    # 一排铆钉，和上沿平行
    for x, y in ((4, 5), (6, 4), (8, 3)):
        stud(c, x, y, HULL, 4)
    pits(c, rng, 2, 3, 9, 9, 12, HULL)
    lit_outline(c)
    return limit_colors(c)


def para(c: Canvas, x0: int, y0: int, w: int, h: int, r: Ramp) -> set:
    """斜着看的一块薄板：板面是平行四边形（每往下两行往左错一格），板面受光，上沿最亮、左沿亮，
    中间斜着一道拉丝的反光；下面露出两行板厚，右边一列侧面背光。"""
    top = set()
    rows = []
    for k in range(h):
        left = x0 - k // 2
        rows.append((left, left + w - 1))
        for x in range(left, left + w):
            top.add((x, y0 + k))
    left, right = rows[-1]
    for x in range(left, right + 1):
        c.set(x, y0 + h, r, 3)
        c.set(x, y0 + h + 1, r, 2)
    for k, (lx, rx) in enumerate(rows):
        c.set(rx + 1, y0 + k + 1, r, 1)
    c.set(right + 1, y0 + h + 1, r, 1)
    for x, y in top:
        k = y - y0
        lx, rx = rows[k]
        lv = 4
        if k == 0:
            lv = 6
        elif x == lx:
            lv = 5
        elif k == h - 1:
            lv = 3
        elif (x - lx) - k in (2, 3):
            lv = 5
        c.set(x, y, r, lv)
    return top


@texture("item/alloy_plate", refs=("item/iron_ingot",))
def alloy_plate(rng):
    """两块叠着的钛合金板，上面那块压着一道钢印。"""
    c = Canvas()
    para(c, 5, 7, 8, 5, TITANIUM)
    top = para(c, 7, 2, 8, 5, TITANIUM)
    for x, y in ((9, 4), (10, 4), (11, 4)):
        if (x, y) in top:
            c.set(x, y, TITANIUM, 2, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/salvaged_electronics", refs=("item/redstone", "item/comparator"))
def salvaged_electronics(rng):
    """断了半截的电路板：绿的板子，一块大芯片、一块小芯片、两颗电容，金的走线和金手指；
    右边是断口，烧得发黑；右上拖着一截扯断的排线。"""
    c = Canvas()
    edge = (12, 13, 13, 12, 13, 11, 12, 10, 11, 9)
    for y, x1 in enumerate(edge, start=2):
        for x in range(2, x1 + 1):
            c.set(x, y, PCB, 3)
    # 走线
    for x in range(3, 11):
        c.set(x, 9, GOLD_FOIL, 2)
    for y in range(3, 9):
        c.set(9, y, GOLD_FOIL, 2)
    # 大芯片和针脚
    c.panel(3, 3, 6, 6, GRAPHITE, 2)
    for i in range(3, 7):
        c.set(i, 7, STEEL, 5, glint=True)
    c.set(4, 4, GRAPHITE, 4, glint=True)
    c.panel(10, 4, 11, 6, GRAPHITE, 2)
    # 两颗电容
    for x in (7, 10):
        c.set(x, 10, BLUE, 4, glint=True)
        c.set(x, 11, BLUE, 2, glint=True)
    # 金手指
    for x in range(2, 10):
        c.set(x, 12, GOLD_FOIL, 4 if x % 2 else 2)
    # 断口烧黑
    for y, x1 in enumerate(edge, start=2):
        for x in range(x1 - 1, x1 + 1):
            if rng.random() < 0.7:
                c.set(x, y, BASALT, rng.choice((1, 2)))
    # 扯断的排线
    for i, (x, y) in enumerate(((12, 1), (13, 0), (14, 1), (13, 2))):
        c.set(x, y, COPPER if i % 2 else RUBBER, 4 if i % 2 else 2, glint=True)
    lit_outline(c)
    return limit_colors(c)


REACTOR_CORE = [
    "............SS..",
    "...........SSSS.",
    "..........SSSSS.",
    ".........HSSSS..",
    "........HHHSS...",
    ".......GGGHH....",
    "......GGGGG.....",
    ".....GGGGG......",
    "....GGGGG.......",
    "...HHGGG........",
    "..SSHHH.........",
    ".SSSSH..........",
    ".SSSSS..........",
    "..SSS...........",
    "................",
    "................",
]


@texture("item/salvaged_reactor_core", refs=("item/end_crystal", "item/heart_of_the_sea"))
def salvaged_reactor_core(rng):
    """一根燃料组件：两头钢的端盖，两道警示黄的箍，中间的窗里透出一根根发蓝光的燃料棒。"""
    grid = REACTOR_CORE
    c = Canvas()
    paint(c, grid, {"S": (STEEL, 6, 3), "H": (HAZARD, 5, 2), "G": (CYAN_GLOW, 4, 3)})
    lit_outline(c)
    for x, y in cells_of(grid, "G"):
        k = (x + y) % 3
        c.set(x, y, CYAN_GLOW if k else BLUE_GLOW, (5 if k == 1 else 3) if k else 3, glint=True)
    c.set(12, 1, STEEL, 6, glint=True)
    c.set(2, 11, STEEL, 6, glint=True)
    return limit_colors(c)


# ---------------------------------------------------------------- 牵引光束模块、引力抓取器

# 上面是发射器，往下张开一道锥形的光，中间一个往上的箭头，底下吸着一只货箱
TRACTOR_GLYPH = [
    ".######.",
    "..+--+..",
    "..-++-..",
    ".-.++.-.",
    ".-.--.-.",
    "-.####.-",
    "..#..#..",
    "..####..",
]


@texture("item/module_tractor_beam")
def module_tractor_beam(rng):
    return module_chip(CYAN_GLOW, TRACTOR_GLYPH)


@texture("item/module_tractor_beam_overlay", tinted=True)
def module_tractor_beam_overlay(rng):
    return module_overlay()


def gravity_coil_cells() -> dict:
    """引力抓取器的剪影，按斜放的轴算：s = x + y 是横着轴的位置（15 在正中），d = x − y 是顺着轴往右上的位置。
    P 是插进工具的接口，B 是管身，K 是三圈凸出来的线圈，M 是叉形的发射极（底座加两根叉），E 是叉中间那团光。"""
    cells = {}
    for x in range(16):
        for y in range(16):
            s, d = x + y, x - y
            if 14 <= s <= 16 and -13 <= d <= 5:
                cells[(x, y)] = "P" if d <= -9 else "B"
            if 13 <= s <= 17 and any(lo <= d <= lo + 1 for lo in (-6, -2, 2)):
                cells[(x, y)] = "K"
            if 12 <= s <= 18 and 6 <= d <= 7:
                cells[(x, y)] = "M"
            if s in (12, 18) and 8 <= d <= 12:
                cells[(x, y)] = "M"
            if 14 <= s <= 16 and 8 <= d <= 11:
                cells[(x, y)] = "E"
    return cells


GRAVITY_COIL = ["".join(gravity_coil_cells().get((x, y), ".") for x in range(16)) for y in range(16)]


@texture("item/gravity_coil", refs=("item/iron_sword",))
def gravity_coil(rng):
    """引力抓取器：管身套着三圈线圈，朝前的一侧透出紫光；枪口两根叉形的发射极，中间夹着一团紫光。"""
    grid = GRAVITY_COIL
    c = weapon(grid, {"B": (STEEL, 6, 3), "K": (COPPER, 5, 2), "M": (GRAPHITE, 5, 2), "P": (GRAPHITE, 4, 1),
                      "E": (VIOLET_GLOW, 4, 3)})
    for x, y in cells_of(grid, "K"):
        # 每圈线圈靠枪口的那一匝发光
        if (x - y) in (-5, -1, 3):
            c.set(x, y, VIOLET_GLOW, 4 if x + y <= 15 else 3, glint=True)
    for x, y in cells_of(grid, "E"):
        d = math.hypot(x - 12.25, y - 2.75)
        c.set(x, y, VIOLET_GLOW, max(2, VIOLET_GLOW.top - round(d * 1.2)), glint=True)
    return limit_colors(c)
