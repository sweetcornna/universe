"""生成 M14（ADR-0026）交易站、制造设施、观测站的方块贴图：加固外壳、加固门（上下两半）、设施终端和观测终端
（正面、侧面、顶面）。交易站终端借用空间站交易终端的贴图，这里不画。

用法：python tools/m14_outpost_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

照着现实里的原型画：
- 加固外壳照装甲钢板画：深枪灰色的厚板，像砌砖那样错开拼缝，拼缝两边一排铆钉，板面有几道划痕。
- 加固门照防爆门画：横着一道道加强筋，下半截底部一道黄黑相间的警示斜条；上半截一条红色的观察窗灯带，
  顶上一排状态灯，只亮着一盏红的。
- 设施终端是工厂里的控制台：深灰机身，正面屏幕是暗红底，三个琥珀色的格子代表三个模块，中间那个选中；
  侧面散热格栅和一条琥珀色的状态灯，顶上一个齿轮标志。
- 观测终端照天文台的控制台画：正面屏幕是深蓝的星空，几颗星，四个青色的角框住其中最亮的一颗；
  侧面散热格栅和一条浅蓝的状态灯，顶上一圈圈同心圆，像望远镜的镜头。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, N, shade, mix, new, put, save, noise  # noqa: E402

STEEL = (70, 74, 80)
DOOR = (86, 90, 97)
INTERIOR = (62, 68, 76)
AMBER = (242, 184, 75)
RED = (222, 64, 52)
HAZARD = (214, 172, 46)
HAZARD_DARK = (36, 36, 40)
SKY = (8, 14, 32)
STAR = (226, 236, 255)
LENS = (120, 196, 240)
CYAN = (110, 220, 245)


def bevel(img, base, light=1.3, dark=0.58):
    for i in range(N):
        put(img, i, 0, shade(base, light))
        put(img, 0, i, shade(base, light))
        put(img, i, N - 1, shade(base, dark))
        put(img, N - 1, i, shade(base, dark))


def rivet(img, x, y, base):
    put(img, x, y, shade(base, 1.45))
    put(img, x + 1, y, shade(base, 1.1))
    put(img, x, y + 1, shade(base, 0.95))
    put(img, x + 1, y + 1, shade(base, 0.55))


# ---- 加固外壳

def reinforced_plating():
    rng = random.Random(2601)
    img = new(STEEL)
    noise(img, rng, 0.05)
    # 横向的拼缝，上下两块板
    for x in range(N):
        put(img, x, 7, shade(STEEL, 0.5))
        put(img, x, 8, shade(STEEL, 1.22))
    # 竖向的拼缝错开：上半块在中间，下半块在边上（和旁边的方块拼起来正好错开）
    for y in range(0, 7):
        put(img, 7, y, shade(STEEL, 0.5))
        put(img, 8, y, shade(STEEL, 1.18))
    for y in range(9, N):
        put(img, 0, y, shade(STEEL, 1.18))
        put(img, N - 1, y, shade(STEEL, 0.5))
    # 上下两边也压一道暗线，拼起来是一层层的板
    for x in range(N):
        put(img, x, 0, shade(STEEL, 1.15))
        put(img, x, N - 1, shade(STEEL, 0.62))
    # 拼缝两边的铆钉
    for x in (2, 5, 10, 13):
        rivet(img, x, 4, STEEL)
    for x in (3, 7, 11):
        rivet(img, x, 11, STEEL)
    # 一道斜着的划痕
    for i in range(5):
        put(img, 9 + i, 13 - i // 2, shade(STEEL, 1.3))
    save(img, BLOCK, "reinforced_plating")


# ---- 加固门

def door_base(rng):
    img = new(DOOR)
    noise(img, rng, 0.04)
    bevel(img, DOOR)
    return img


def rib(img, y):
    for x in range(1, N - 1):
        put(img, x, y, shade(DOOR, 1.28))
        put(img, x, y + 1, shade(DOOR, 0.88))
        put(img, x, y + 2, shade(DOOR, 0.6))


def reinforced_door_lower():
    rng = random.Random(2602)
    img = door_base(rng)
    rib(img, 2)
    rib(img, 7)
    for x in (2, 12):
        rivet(img, x, 5, DOOR)
    # 底部的黄黑警示斜条
    for y in range(11, 15):
        for x in range(1, N - 1):
            stripe = ((x + y) // 3) % 2 == 0
            put(img, x, y, shade(HAZARD if stripe else HAZARD_DARK, 1 + rng.uniform(-0.05, 0.05)))
    for x in range(1, N - 1):
        put(img, x, 10, shade(DOOR, 0.55))
    save(img, BLOCK, "reinforced_door_lower")


def reinforced_door_upper():
    rng = random.Random(2603)
    img = door_base(rng)
    # 顶上一排状态灯，只亮一盏红的
    for x in range(3, 13, 3):
        lit = x == 6
        put(img, x, 2, RED if lit else shade(DOOR, 0.5))
        put(img, x + 1, 2, shade(RED, 0.7) if lit else shade(DOOR, 0.62))
    # 一条红色的观察窗灯带
    for x in range(2, N - 2):
        put(img, x, 5, shade(DOOR, 0.45))
        put(img, x, 6, mix(RED, (255, 140, 110), 0.25 * (x % 3 == 0)))
        put(img, x, 7, shade(RED, 0.62))
        put(img, x, 8, shade(DOOR, 1.25))
    rib(img, 10)
    for x in (2, 12):
        rivet(img, x, 13, DOOR)
    save(img, BLOCK, "reinforced_door_upper")


# ---- 终端的机身

def casing(rng):
    img = new(INTERIOR)
    noise(img, rng, 0.04)
    bevel(img, INTERIOR, 1.4, 0.6)
    return img


def screen(img, color):
    for y in range(2, 11):
        for x in range(2, 14):
            put(img, x, y, color)
    for x in range(2, 14):
        put(img, x, 1, shade(INTERIOR, 0.45))
        put(img, x, 11, shade(INTERIOR, 1.25))


def keys(img, accent):
    for x in (3, 5, 7, 9):
        put(img, x, 13, shade(INTERIOR, 1.55))
        put(img, x + 1, 13, shade(INTERIOR, 1.2))
    put(img, 11, 13, accent)
    put(img, 12, 13, shade(accent, 0.75))
    put(img, 11, 14, shade(accent, 0.6))
    put(img, 12, 14, shade(accent, 0.45))


def side(seed, accent):
    rng = random.Random(seed)
    img = casing(rng)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            put(img, x, y, shade(INTERIOR, 0.5))
            put(img, x, y + 1, shade(INTERIOR, 1.15))
    for y in range(3, 13):
        put(img, 14, y, mix(INTERIOR, accent, 0.35 + 0.4 * ((y % 4) == 0)))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    return img


# ---- 设施终端

def facility_terminal_front():
    rng = random.Random(2611)
    img = casing(rng)
    screen(img, (30, 10, 10))
    # 三个模块格子，中间那个选中
    for x0 in (3, 7, 11):
        chosen = x0 == 7
        edge = AMBER if chosen else shade(AMBER, 0.5)
        for x in range(x0, x0 + 3):
            put(img, x, 4, edge)
            put(img, x, 8, edge)
        for y in range(4, 9):
            put(img, x0, y, edge)
            put(img, x0 + 2, y, edge)
        for y in range(5, 8):
            put(img, x0 + 1, y, shade(AMBER, 0.6 if chosen else 0.22))
    put(img, 8, 6, (255, 230, 160))
    # 屏幕上方一行红字
    for x in range(3, 11, 2):
        put(img, x, 2, shade(RED, 0.8))
    keys(img, AMBER)
    save(img, BLOCK, "facility_terminal_front")


def facility_terminal_top():
    rng = random.Random(2613)
    img = casing(rng)
    cx, cy = 7.5, 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            tooth = math.cos(a * 8) > 0.2
            if d <= 2.0:
                put(img, x, y, shade(INTERIOR, 0.6))
            elif d <= 3.6 or (d <= 4.8 and tooth):
                put(img, x, y, shade(AMBER, 0.75 + 0.3 * (x < cx and y < cy)))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    save(img, BLOCK, "facility_terminal_top")


# ---- 观测终端

def observatory_terminal_front():
    rng = random.Random(2621)
    img = casing(rng)
    screen(img, SKY)
    # 星空：几颗暗星和亮星
    for (x, y, b) in ((3, 3, 0.5), (4, 8, 0.75), (3, 6, 0.35), (13, 4, 0.6), (13, 9, 0.4), (8, 8, 0.3), (10, 4, 0.25)):
        put(img, x, y, mix(SKY, STAR, b))
    # 瞄准框套住一颗亮星
    put(img, 9, 6, STAR)
    for sx in (-1, 1):
        for sy in (-1, 1):
            put(img, 9 + 3 * sx, 6 + 3 * sy, CYAN)
            put(img, 9 + 2 * sx, 6 + 3 * sy, CYAN)
            put(img, 9 + 3 * sx, 6 + 2 * sy, CYAN)
    keys(img, LENS)
    save(img, BLOCK, "observatory_terminal_front")


def observatory_terminal_top():
    rng = random.Random(2623)
    img = casing(rng)
    cx, cy = 7.5, 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= 1.6:
                put(img, x, y, shade(SKY, 1.4))
            elif d <= 2.6:
                put(img, x, y, shade(LENS, 0.7 + 0.35 * (x < cx and y < cy)))
            elif d <= 3.6:
                put(img, x, y, shade(INTERIOR, 0.55))
            elif d <= 4.8:
                put(img, x, y, shade(LENS, 0.5 + 0.25 * (x < cx and y < cy)))
            elif d <= 5.6:
                put(img, x, y, shade(INTERIOR, 0.7))
    put(img, 6, 6, STAR)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, INTERIOR)
    save(img, BLOCK, "observatory_terminal_top")


def main():
    reinforced_plating()
    reinforced_door_lower()
    reinforced_door_upper()
    facility_terminal_front()
    save(side(2612, AMBER), BLOCK, "facility_terminal_side")
    facility_terminal_top()
    observatory_terminal_front()
    save(side(2622, LENS), BLOCK, "observatory_terminal_side")
    observatory_terminal_top()


if __name__ == "__main__":
    main()
