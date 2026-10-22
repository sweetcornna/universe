"""生成 M13（ADR-0024）的贴图：着陆台、镉驱动、翠金驱动、铟驱动、飞船交易终端、损坏的驾驶台，
货船的装甲板、引擎喷口、控制台和舰队终端。

用法：python tools/m13_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

着陆台是深灰的坪面，一圈白色的降落环，四边黄黑警示线，正面一排指示灯；加燃料时（_on）环里和指示灯亮青色。
三档驱动是同一个外形（一节带散热片的圆柱芯，两端金属箍），芯的颜色按档次：镉是橙红，翠金是绿，铟是蓝紫。
飞船交易终端和空间站的交易终端、维修台是一家：正面屏幕里是一艘俯视的飞船和一排琥珀色价签，顶面的徽记换成青色，侧面照搬。
损坏的驾驶台照着驾驶台的贴图改：船体板熏黑、裂开，屏幕暗下去，横着一道裂纹。
货船装甲板是深石板灰，四块装甲拼成一格，接缝深、边上一圈铆钉，和空间站的浅色外壳搭着用。
引擎喷口是一圈深色喷管，里面一层层亮到中间发白的蓝光。
控制台和舰队终端照着交易终端：控制台的屏幕里是星图（几颗星、一条虚线航线），舰队终端的屏幕里是排成队形的几艘小船，
顶面的徽记分别换成橙色和紫色。
先驱者传送门：石块和巨石是一种深紫灰的石头，砌成方块，接缝深；十六个符文在 7×7 的格子里画，嵌在石块正中，发淡紫的光；
拨号台顶面一圈十六个刻痕围着一颗亮的晶石；门开时环里那层光是一张会动的半透明贴图，蓝紫色的涡旋慢慢转。
拨号盘用的符文表（textures/gui/portal/glyphs.png）是一条 256×16 的白色符文，画的时候乘颜色。
太空遭遇：守卫机的拦截机套海盗战机的模型（tools/combat_textures.py 的 fighter），白色机身、金色涂装、深色翼尖，座舱是琥珀色的面罩；
漏电的线管是一节剥了皮的线槽，铜线断开的地方冒着黄白的火花；信用点芯片是一枚金色的卡片，左边一排触点；
应急信号接收器是一台灰色的手持机，屏幕上一道橙色的波形，天线顶上一颗红灯。
"""
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import factory_textures as ft  # noqa: E402
from factory_textures import (BLOCK, ITEM, N, DARK, CYAN, CYAN_DIM, WHITE, shade, mix, clear, put, rect, outline, save,  # noqa: E402
                              casing, hazard)
import poi_textures as poi  # noqa: E402
import combat_textures as combat  # noqa: E402

GUI = os.path.join(os.path.dirname(BLOCK), "gui")

PAD = (70, 76, 84)
AMBER = (214, 172, 46)
HAZARD_DARK = (40, 40, 44)


def landing_pad():
    for suffix, lit in (("", False), ("_on", True)):
        top = casing(PAD, random.Random(1301), seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 5.0 <= d < 6.4:
                    put(top, x, y, WHITE if (x + y) % 6 else shade(WHITE, 0.82))
                elif 2.6 <= d < 3.4:
                    put(top, x, y, mix(CYAN, WHITE, 0.3) if lit else CYAN_DIM)
                elif d < 1.6:
                    put(top, x, y, CYAN if lit else shade(CYAN_DIM, 0.8))
        # 四边一圈黄黑斜纹
        for i in range(N):
            for (x, y) in ((i, 0), (i, N - 1), (0, i), (N - 1, i)):
                put(top, x, y, AMBER if (x + y) % 4 < 2 else HAZARD_DARK)
        save(top, BLOCK, "landing_pad_top" + suffix)
        front = casing(shade(DARK, 1.1), random.Random(1302), seam=False)
        hazard(front, 0, 2, AMBER, HAZARD_DARK)
        for x in range(N):
            put(front, x, 3, shade(DARK, 1.35))
        # 一排指示灯，加燃料时亮
        for x in (3, 6, 9, 12):
            rect(front, x, 8, x + 1, 9, CYAN if lit else CYAN_DIM)
            put(front, x, 10, shade(CYAN, 0.6) if lit else shade(CYAN_DIM, 0.7))
            put(front, x + 1, 10, shade(CYAN, 0.6) if lit else shade(CYAN_DIM, 0.7))
        for x in range(N):
            put(front, x, 13, shade(DARK, 0.7))
            put(front, x, 15, shade(DARK, 0.55))
        save(front, BLOCK, "landing_pad_front" + suffix)
    side = casing(shade(DARK, 1.1), random.Random(1303), seam=False)
    hazard(side, 0, 2, AMBER, HAZARD_DARK)
    for x in range(N):
        put(side, x, 3, shade(DARK, 1.35))
        put(side, x, 13, shade(DARK, 0.7))
        put(side, x, 15, shade(DARK, 0.55))
    for y in range(5, 12):
        for x in (2, 13):
            put(side, x, y, shade(DARK, 0.75))
    save(side, BLOCK, "landing_pad_side")


def drive(name, core):
    img = clear()
    steel = (176, 184, 192)
    # 斜放的一节圆柱：轴线从左下到右上，按每个像素到轴线的距离上色，左上一侧受光
    ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)
    vx, vy = 1 / math.sqrt(2), 1 / math.sqrt(2)
    length = 14.5
    for y in range(N):
        for x in range(N):
            rx, ry = x + 0.5 - 1.6, y + 0.5 - 14.4
            t = rx * ux + ry * uy
            s = rx * vx + ry * vy
            if t < 0 or t > length or abs(s) > 2.9:
                continue
            light = 1.18 - (s + 1.2) * 0.12
            if t < 2.4 or t > length - 2.4:
                # 两端的金属箍
                color = shade(steel, light)
            elif int(t - 2.4) % 3 == 2:
                # 散热片
                color = shade(steel, light * 0.78)
            else:
                color = shade(core, light)
                if abs(s + 0.6) < 0.55:
                    # 芯里的一道亮线
                    color = mix(core, WHITE, 0.5)
            put(img, x, y, color)
    outline(img, (24, 26, 30))
    save(img, ITEM, name)


def load(name):
    return Image.open(os.path.join(BLOCK, name + ".png")).convert("RGBA")


def ship_dealer():
    front = load("ship_repair_front")
    screen = front.getpixel((2, 2))[:3]
    rect(front, 2, 2, 13, 10, screen)
    hull = (214, 236, 242)
    glass = (95, 190, 213)
    glow = (232, 160, 60)
    # 俯视的飞船，船头朝上
    rows = {
        2: (7, 8),
        3: (6, 9),
        4: (5, 10),
        5: (3, 12),
        6: (3, 12),
        7: (5, 10),
    }
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            put(front, x, y, shade(hull, 0.85 if x in (x0, x1) else 1.0))
    for (x, y) in ((7, 3), (8, 3), (7, 4), (8, 4)):
        put(front, x, y, glass)
    for x in (3, 12):
        put(front, x, 4, shade(hull, 0.7))
    for x in (6, 9):
        put(front, x, 8, glow)
    # 价签
    for x in range(4, 10):
        put(front, x, 10, glow if x % 2 == 0 else shade(glow, 0.7))
    put(front, 11, 10, shade(glow, 0.45))
    save(front, BLOCK, "ship_dealer_front")
    top = load("trade_terminal_top")
    for y in range(N):
        for x in range(N):
            r, g, b, a = top.getpixel((x, y))
            if r > 150 and r > b + 60:
                light = (r + g + b) / (3 * 255)
                put(top, x, y, shade((80, 200, 225), 0.55 + 0.6 * light))
    save(top, BLOCK, "ship_dealer_top")
    save(load("trade_terminal_side"), BLOCK, "ship_dealer_side")


def damaged_helm():
    rng = random.Random(1331)
    panel = load("ship_hull_panel")
    for y in range(N):
        for x in range(N):
            r, g, b, a = panel.getpixel((x, y))
            # 下半截熏黑，越往下越黑；再撒一些焦斑
            soot = 0.55 + 0.45 * (1 - y / (N - 1)) ** 0.7
            if rng.random() < 0.18:
                soot *= 0.6
            put(panel, x, y, shade((r, g, b), soot), a)
    crack = [(3, 2), (4, 3), (4, 4), (5, 5), (6, 5), (6, 6), (7, 7), (8, 8), (8, 9), (9, 10), (10, 10), (11, 11), (11, 12)]
    for (x, y) in crack:
        put(panel, x, y, (28, 26, 24))
    save(panel, BLOCK, "damaged_ship_hull_panel")
    screen = load("ship_helm_screen")
    for y in range(N):
        for x in range(N):
            r, g, b, a = screen.getpixel((x, y))
            put(screen, x, y, shade((r, g, b), 0.32 if rng.random() > 0.08 else 0.12), a)
    for (x, y) in ((1, 4), (2, 5), (3, 5), (4, 6), (5, 6), (6, 7), (7, 7), (8, 8), (9, 8), (10, 9), (11, 9), (12, 10), (13, 10), (14, 11)):
        put(screen, x, y, (176, 190, 196))
    for (x, y) in ((11, 3), (12, 3)):
        put(screen, x, y, (210, 60, 50))
    save(screen, BLOCK, "damaged_ship_helm_screen")


def freighter_plate():
    rng = random.Random(1341)
    base = (78, 86, 96)
    img = clear()
    for y in range(N):
        for x in range(N):
            put(img, x, y, shade(base, 0.92 + rng.random() * 0.12))
    # 四块装甲：十字接缝，左上受光
    for i in range(N):
        put(img, i, 7, shade(base, 0.55))
        put(img, 7, i, shade(base, 0.55))
        put(img, i, 8, shade(base, 1.18))
        put(img, 8, i, shade(base, 1.18))
    for i in range(N):
        put(img, i, 0, shade(base, 1.2))
        put(img, 0, i, shade(base, 1.2))
        put(img, i, N - 1, shade(base, 0.6))
        put(img, N - 1, i, shade(base, 0.6))
    for (x, y) in ((2, 2), (5, 2), (2, 5), (10, 2), (13, 2), (13, 5), (2, 10), (2, 13), (5, 13), (13, 10), (10, 13), (13, 13)):
        put(img, x, y, shade(base, 1.45))
        put(img, x + 1 if x < 8 else x - 1, y + 1 if y < 8 else y - 1, shade(base, 0.7))
    save(img, BLOCK, "freighter_plate")


def freighter_engine():
    img = clear()
    rim = (52, 56, 64)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d >= 7.0:
                c = shade(rim, 0.8)
            elif d >= 6.0:
                c = shade(rim, 1.25 if x + y < 15 else 0.9)
            elif d >= 4.8:
                c = (40, 90, 160)
            elif d >= 3.4:
                c = (70, 150, 230)
            elif d >= 2.0:
                c = (150, 210, 255)
            else:
                c = (236, 248, 255)
            put(img, x, y, c)
    save(img, BLOCK, "freighter_engine")


def terminal(name, emblem, screen_painter):
    front = load("trade_terminal_front")
    screen = front.getpixel((3, 3))[:3]
    rect(front, 2, 2, 13, 10, screen)
    screen_painter(front)
    save(front, BLOCK, name + "_front")
    top = load("trade_terminal_top")
    for y in range(N):
        for x in range(N):
            r, g, b, a = top.getpixel((x, y))
            if r > 150 and r > b + 60:
                light = (r + g + b) / (3 * 255)
                put(top, x, y, shade(emblem, 0.55 + 0.6 * light))
    save(top, BLOCK, name + "_top")
    save(load("trade_terminal_side"), BLOCK, name + "_side")


def star_map(img):
    rng = random.Random(1342)
    for _ in range(9):
        x, y = rng.randint(2, 13), rng.randint(2, 10)
        put(img, x, y, shade(WHITE, 0.55 + rng.random() * 0.3))
    route = [(3, 9), (5, 7), (7, 7), (9, 5), (11, 4)]
    for i, (x, y) in enumerate(route):
        put(img, x, y, AMBER if i in (0, len(route) - 1) else CYAN)
    for (x, y) in ((4, 8), (6, 7), (8, 6), (10, 4)):
        put(img, x, y, CYAN_DIM)
    put(img, 11, 3, shade(AMBER, 0.7))
    put(img, 12, 4, shade(AMBER, 0.7))


def formation(img):
    hull = (214, 236, 242)
    glow = (190, 140, 255)
    # 一艘大的居中，两边各一艘小的，船头朝上
    for (cx, cy, big) in ((7, 4, True), (4, 7, False), (11, 7, False)):
        rows = [(0, 0), (-1, 1), (-1, 1), (-2, 2)] if big else [(0, 0), (-1, 1), (-1, 1)]
        for i, (a, b) in enumerate(rows):
            for x in range(cx + a, cx + b + 1):
                put(img, x, cy + i, shade(hull, 0.85 if x in (cx + a, cx + b) else 1.0))
        put(img, cx, cy + len(rows), glow)
    for x in range(3, 13):
        put(img, x, 10, shade(CYAN_DIM, 0.8) if x % 3 else CYAN)


# ---- 先驱者传送门（ADR-0024）

# 十六个符文，按 core.portal.PortalAddress 的编号 0–15
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
PORTAL_BASE = (34, 31, 44)
PORTAL_GLOW = (206, 170, 255)


def portal_stone(seed):
    """砌成方块的先驱者石：四块错缝的石砖，接缝深，砖面左上亮。"""
    rng = random.Random(seed)
    img = poi.stone(rng, PORTAL_BASE)
    for y in range(N):
        for x in range(N):
            # 上下两层砖，下层错开半块
            row = y // 8
            bx = (x + (4 if row else 0)) % 8
            by = y % 8
            if by == 7 or bx == 7:
                put(img, x, y, shade(PORTAL_BASE, 0.55))
            elif by == 0 or bx == 0:
                put(img, x, y, shade(poi.get(img, x, y), 1.25))
    return img


def portal_glyph_block(index):
    img = portal_stone(1340 + index)
    # 符文压在正中的 9×9 凹槽里
    for y in range(3, 12):
        for x in range(3, 12):
            put(img, x, y, shade(PORTAL_BASE, 0.7 if x in (3, 11) or y in (3, 11) else 0.85))
    pattern = PORTAL_GLYPHS[index]
    lit = set()
    for j, row in enumerate(pattern):
        for i, ch in enumerate(row):
            if ch == "#":
                lit.add((4 + i, 4 + j))
    for (x, y) in lit:
        put(img, x, y, PORTAL_GLOW if (x + y) % 5 else mix(PORTAL_GLOW, WHITE, 0.45))
    # 笔画边上一圈淡淡的光
    for (x, y) in lit:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in lit and 3 <= q[0] <= 11 and 3 <= q[1] <= 11:
                put(img, q[0], q[1], mix(poi.get(img, q[0], q[1]), PORTAL_GLOW, 0.25))
    save(img, BLOCK, "portal_glyph_%d" % index)


def portal_dial():
    top = portal_stone(1360)
    cx = cy = 7.5
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if 5.2 <= d < 6.6:
                put(top, x, y, shade(PORTAL_BASE, 0.75))
            if d < 2.2:
                put(top, x, y, mix(PORTAL_GLOW, WHITE, 0.5) if d < 1.0 else PORTAL_GLOW)
            elif d < 3.0:
                put(top, x, y, mix(PORTAL_BASE, PORTAL_GLOW, 0.3))
    # 十六道刻痕，一个符文一道
    for k in range(16):
        a = k * math.pi * 2 / 16
        x = int(round(cx + math.cos(a) * 5.6))
        y = int(round(cy + math.sin(a) * 5.6))
        put(top, x, y, mix(PORTAL_GLOW, PORTAL_BASE, 0.35))
    save(top, BLOCK, "portal_dial_top")
    side = portal_stone(1361)
    for x in range(N):
        put(side, x, 2, mix(PORTAL_BASE, PORTAL_GLOW, 0.55) if x % 3 else PORTAL_GLOW)
    for x in range(4, 12):
        put(side, x, 9, shade(PORTAL_BASE, 0.6))
    save(side, BLOCK, "portal_dial_side")


def portal_field():
    """门开着时环里的光：32 帧，蓝紫色的涡旋绕着中心慢慢转，越往外越透。"""
    frames = 32
    sheet = Image.new("RGBA", (N, N * frames), (0, 0, 0, 0))
    deep = (52, 40, 150)
    bright = (150, 210, 255)
    for f in range(frames):
        t = f / frames * math.pi * 2
        for y in range(N):
            for x in range(N):
                # 贴图会平铺在整片光上，所以只用周期为 16 的波，接缝看不出来
                u = x / N * math.pi * 2
                v = y / N * math.pi * 2
                # 低频的波把坐标扭一扭再取一次波，成团的亮斑像涡旋，不成网格
                wu = u + 1.3 * math.sin(v + t)
                wv = v + 1.3 * math.cos(u - t)
                w = 0.55 * math.sin(wu) * math.cos(wv) + 0.45 * math.sin(wu + wv + t)
                k = 0.5 + 0.5 * w
                color = mix(deep, bright, k ** 1.6)
                a = int(150 + 80 * k)
                sheet.putpixel((x, f * N + y), color + (a,))
    os.makedirs(BLOCK, exist_ok=True)
    sheet.save(os.path.join(BLOCK, "portal_field.png"))
    with open(os.path.join(BLOCK, "portal_field.png.mcmeta"), "w", encoding="utf-8", newline="\n") as out:
        out.write('{\n  "animation": {\n    "frametime": 2,\n    "interpolate": true\n  }\n}\n')


def portal_gui():
    """拨号盘的符文表：16 个，每个 16×16，笔画放大两倍、白色。"""
    sheet = Image.new("RGBA", (16 * 16, 16), (0, 0, 0, 0))
    for index, pattern in enumerate(PORTAL_GLYPHS):
        for j, row in enumerate(pattern):
            for i, ch in enumerate(row):
                if ch == "#":
                    for dx in range(2):
                        for dy in range(2):
                            sheet.putpixel((index * 16 + 1 + i * 2 + dx, 1 + j * 2 + dy), (255, 255, 255, 255))
    folder = os.path.join(GUI, "portal")
    os.makedirs(folder, exist_ok=True)
    sheet.save(os.path.join(folder, "glyphs.png"))


def portal():
    save(portal_stone(1330), BLOCK, "portal_stone")
    for index in range(len(PORTAL_GLYPHS)):
        portal_glyph_block(index)
    portal_dial()
    portal_field()
    portal_gui()


def live_conduit():
    rng = random.Random(1361)
    img = clear()
    casing_color = (58, 62, 70)
    for y in range(N):
        for x in range(N):
            put(img, x, y, shade(casing_color, 0.9 + rng.random() * 0.14))
    for i in range(N):
        put(img, i, 0, shade(casing_color, 1.25))
        put(img, i, N - 1, shade(casing_color, 0.6))
    # 中间一道剥开的线槽，里面三股铜线，两处断开冒火花
    for y in range(5, 11):
        for x in range(N):
            put(img, x, y, (24, 24, 28))
    for x in range(N):
        put(img, x, 4, shade(casing_color, 0.55))
        put(img, x, 11, shade(casing_color, 1.3))
    copper = (204, 120, 60)
    for row, gap in ((6, (4, 6)), (8, (10, 12)), (9, (2, 3))):
        for x in range(N):
            if gap[0] <= x <= gap[1]:
                continue
            put(img, x, row, shade(copper, 0.9 + 0.2 * ((x + row) % 3 == 0)))
    for (x, y, c) in ((5, 6, (255, 250, 210)), (4, 7, (255, 226, 120)), (6, 5, (255, 236, 150)),
                      (11, 8, (255, 250, 210)), (10, 7, (255, 226, 120)), (12, 9, (255, 236, 150)), (2, 10, (255, 226, 120))):
        put(img, x, y, c)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, shade(casing_color, 1.5))
        put(img, x + 1, y + 1, shade(casing_color, 0.6))
    save(img, BLOCK, "live_conduit")


def credit_chip():
    img = clear()
    gold = (222, 178, 64)
    for y in range(4, 12):
        for x in range(2, 14):
            if (x in (2, 13)) and (y in (4, 11)):
                continue
            f = 1.15 - (x + y) * 0.02
            put(img, x, y, shade(gold, f))
    # 中间压出来的一块深色面板和一道横线
    for y in range(6, 10):
        for x in range(6, 12):
            put(img, x, y, shade(gold, 0.7))
    for x in range(7, 11):
        put(img, x, 7, shade(gold, 1.25))
    # 左边一排触点
    for y in (5, 7, 9):
        put(img, 3, y, (120, 220, 240))
        put(img, 4, y, (80, 170, 200))
    outline(img, (70, 52, 20))
    save(img, ITEM, "credit_chip")


def emergency_receiver():
    img = clear()
    body = (122, 130, 140)
    for y in range(4, 15):
        for x in range(3, 12):
            put(img, x, y, shade(body, 1.15 - (x - 3) * 0.04 - (y - 4) * 0.015))
    # 屏幕：深色，一道橙色的波形
    for y in range(6, 10):
        for x in range(4, 11):
            put(img, x, y, (24, 28, 34))
    wave = (8, 7, 8, 6, 9, 7, 8)
    for i, y in enumerate(wave):
        put(img, 4 + i, y, (255, 150, 60))
    # 下面一排按键
    for x in (5, 7, 9):
        put(img, x, 12, (60, 64, 72))
    put(img, 9, 12, (220, 70, 50))
    # 天线和顶上的红灯
    for y in range(1, 4):
        put(img, 10, y, (70, 74, 82))
    put(img, 10, 0, (255, 70, 60))
    outline(img, (40, 42, 48))
    save(img, ITEM, "emergency_receiver")


def encounter():
    combat.fighter("sentinel_interceptor", (206, 210, 216), (232, 176, 58), (60, 64, 72), (40, 30, 10), (250, 210, 120))
    live_conduit()
    credit_chip()
    emergency_receiver()


def main():
    portal()
    encounter()
    landing_pad()
    freighter_plate()
    freighter_engine()
    terminal("freighter_console", (236, 128, 60), star_map)
    terminal("fleet_terminal", (170, 120, 240), formation)
    ship_dealer()
    damaged_helm()
    drive("cadmium_drive", (232, 96, 60))
    drive("emeril_drive", (92, 214, 120))
    drive("indium_drive", (124, 112, 255))


if __name__ == "__main__":
    main()
