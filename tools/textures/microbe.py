"""M20 微生物和基因的贴图（ADR-0031、ADR-0036 §五）：采样管和样本、菌种管、改造种苗，琼脂、培养基、测序芯片、编辑试剂，
培养箱、测序仪、基因编辑台。

照实验室里的东西画：采样管是蓝盖的离心管，菌种管是金属帽的斜面培养管，培养基是倒好的平板，测序芯片是贴着触点的玻璃流动槽，
编辑试剂是一蓝一红两支小离心管（Cas9 蛋白和向导 RNA）。
培养箱是白色的保温柜，玻璃内门里两层搁板放着平板，门边一块红色的温度读数；测序仪是白机身、深色前脸，左边触摸屏，
右边放芯片的仓门，底下一条灯带；基因编辑台是钢的移液工作站，玻璃罩里一根横梁挂着移液头，台面上一块 96 孔板。
"""
from __future__ import annotations

import random

from canvas import Canvas
from color import ramp
from machines import screen, shell
from palette import (AMBER, AMBER_GLOW, BLUE, BLUE_GLOW, CREAM, GLASS, GOLD_FOIL, GRAPHITE, GREEN_GLOW, LEAF, RED, RED_GLOW,
                     SCREEN, SOIL, STEEL, TEAL, VIOLET_GLOW, WHITE, YELLOW_GLOW, register)
from parts import bolts, casing, status_lights
from recipe import texture

# 这组自己用的色阶
AGAR = register(ramp("microbe_agar", 82, 0.060, n=6, lo=0.52, hi=0.94, shift=10))      # 琼脂、培养基：发黄的半透明
BROTH = register(ramp("microbe_broth", 70, 0.090, n=6, lo=0.36, hi=0.80, shift=12))    # 浑的样本：泥水


# ---------------------------------------------------------------- 管子

# 一支离心管：盖 x 4–11，管身 x 5–10，圆底收到 x 7–8。G 玻璃受光的一边、g 玻璃、h 背光的一边，W/w 写字区
TUBE = [
    "................",
    "....CCCCCCCC....",
    "....cCcCcCck....",
    "....ckckckkk....",
    ".....kkkkkk.....",
    ".....GWWWWh.....",
    ".....GWwwwh.....",
    ".....Gggggh.....",
    ".....Gggggh.....",
    ".....Gggggh.....",
    ".....Gggggh.....",
    ".....Gggggh.....",
    ".....Gggggh.....",
    "......Gggh......",
    ".......gh.......",
]


def tube(cap, cap_base: int, fill=None, fill_base: int = 3, top: int = 9, label: bool = True) -> Canvas:
    """竖着的一支离心管；fill 给了就是从 top 那行往下装着东西，最上一行是亮的液面。"""
    c = Canvas()
    legend = {
        "C": (cap, cap_base + 1), "c": (cap, cap_base), "k": (cap, cap_base - 2),
        "G": (GLASS, 3), "g": (GLASS, 2), "h": (GLASS, 1),
        "W": (WHITE, 5) if label else (GLASS, 3), "w": (WHITE, 4) if label else (GLASS, 2),
    }
    c.ascii(TUBE, legend)
    if not label:
        c.set(10, 5, GLASS, 1)
        c.set(10, 6, GLASS, 1)
    if fill is not None:
        for y in range(top, 15):
            for x in range(5, 11):
                px = c.get(x, y)
                if not px:
                    continue
                lv = fill_base + 1 if y == top else (fill_base - 1 if px[1] == 1 or y == 14 else fill_base)
                c.set(x, y, fill, lv)
    # 玻璃的高光，刻度
    c.set(6, 7, GLASS, GLASS.top, glint=True)
    for y in (8, 10, 12):
        px = c.get(10, y)
        if px and px[0] is GLASS:
            c.set(10, y, GLASS, 0, glint=True)
    c.outline()
    return c


@texture("item/sample_tube", refs=("item/glass_bottle", "item/potion"))
def sample_tube(rng):
    return tube(BLUE, 4)


@texture("item/sample")
def sample(rng):
    # 一管浑的泥水，悬着几粒土
    c = tube(BLUE, 4, BROTH, 3, top=9)
    for x, y in ((7, 11), (9, 12), (6, 12), (8, 13)):
        c.set(x, y, BROTH, 2, glint=True)
    return c


@texture("item/strain_tube")
def strain_tube(rng):
    # 斜面培养管：金属帽，琼脂斜着凝固（左低右高），斜面上长着几个菌落
    c = tube(STEEL, 5, label=False)
    surface = {5: 12, 6: 12, 7: 11, 8: 10, 9: 9, 10: 9}
    for x, y0 in surface.items():
        for y in range(y0, 15):
            px = c.get(x, y)
            if px and px[0] is GLASS:
                c.set(x, y, AGAR, 4 if y == y0 else (3 if x < 8 else 2))
    for x, y in ((7, 11), (8, 10), (9, 9)):
        c.set(x, y, AMBER, 4, glint=True)
    c.set(6, 7, GLASS, GLASS.top, glint=True)
    c.outline()
    return c


@texture("item/engineered_seedling", refs=("item/wheat_seeds", "block/sweet_berry_bush_stage0"))
def engineered_seedling(rng):
    c = Canvas()
    # 育苗的穴盘格：白塑料，上宽下窄，里面是基质
    c.draw([
        "5444444443",
        "4333333332",
        ".43333332.",
        ".43333322.",
        "..433322..",
        "...4322...",
    ], {"0123456": WHITE}, 3, 10)
    c.hline(4, 11, 10, SOIL, 3)
    c.hline(4, 6, 10, SOIL, 4)
    # 一根茎、两片子叶、顶上冒出来的第一对真叶
    c.draw([
        "......45......",
        ".....4554.....",
        ".....3443.....",
        ".2344..3..4432",
        "345554.3.45543",
        ".34443.3.34432",
        "..222..3..222.",
        "......3.......",
        "......3.......",
    ], {"0123456": LEAF}, 1, 1)
    # 贴在穴盘上的标签：青色一条，表示是改过的
    c.hline(6, 9, 12, TEAL, 4)
    c.set(6, 12, TEAL, 5, glint=True)
    c.outline()
    return c


# ---------------------------------------------------------------- 试剂

@texture("item/agar", refs=("item/dried_kelp", "item/slime_ball"))
def agar(rng):
    # 一块切成方的琼脂：顶面最亮，左面中间，右面背光；凝胶是半透明的，侧面靠上那行透着光，前面的棱发亮
    c = Canvas()
    top = set()
    for y in range(2, 9):
        h = 2 * (y - 2) + 1 if y <= 5 else 2 * (8 - y) + 1
        for x in range(8 - h, 8 + h):
            if 1 <= x <= 14:
                top.add((x, y))
    for x, y in top:
        c.set(x, y, AGAR, 5 if x + y < 9 else 4)
    for x in range(1, 15):
        ybot = max(y for (tx, y) in top if tx == x)
        for y in range(ybot + 1, ybot + 6):
            left = x <= 7
            lv = (3 if left else 2) + (1 if y == ybot + 1 else 0)
            c.set(x, y, AGAR, lv)
        # 顶面和侧面交界的棱
        c.set(x, ybot, AGAR, AGAR.top if x <= 7 else 4, glint=x in (3, 5))
    # 凝胶里的一个小气泡
    c.set(4, 10, AGAR, 5, glint=True)
    c.set(11, 10, AGAR, 3, glint=True)
    c.set(7, 3, AGAR, AGAR.top, glint=True)
    c.outline()
    return c


@texture("item/culture_medium", refs=("item/bowl",))
def culture_medium(rng):
    # 倒好的平板：稍微斜着看的培养皿，玻璃的边，里面一层琥珀色的培养基，下面露出一道皿壁
    c = Canvas()
    cx, cy, rx, ry = 8.0, 7.0, 7.0, 4.6
    inside = {}
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if d <= 1.0:
                inside[(x, y)] = d
    for (x, y), d in inside.items():
        if d > 0.62:
            c.set(x, y, GLASS, 4 if y + 0.5 < cy else 3)
        else:
            c.set(x, y, AMBER, 4 if x + y < 11 else (2 if x - y > 7 or y > 8 else 3))
    for x in range(16):
        col = [y for (tx, y) in inside if tx == x]
        if col:
            ybot = max(col)
            c.set(x, ybot + 1, GLASS, 2)
            if 2 < x < 13:
                c.set(x, ybot + 2, GLASS, 1)
    # 皿盖的反光
    for x, y in ((3, 5), (4, 4), (5, 4)):
        c.set(x, y, GLASS, GLASS.top, glint=True)
    c.outline()
    return c


@texture("item/sequencing_chip", refs=("item/paper",))
def sequencing_chip(rng):
    # 流动槽：深色塑料托，上面一片玻璃，玻璃里四条测序通道，右头一排金色触点，两头各一个进出液孔
    c = Canvas()
    c.rect(1, 4, 14, 11, GRAPHITE, 3)
    c.hline(1, 14, 4, GRAPHITE, 4)
    c.vline(1, 4, 11, GRAPHITE, 4)
    c.hline(1, 14, 11, GRAPHITE, 1)
    c.vline(14, 5, 11, GRAPHITE, 1)
    c.rect(3, 5, 11, 10, GLASS, 2)
    c.hline(3, 11, 5, GLASS, 4)
    for y in (6, 7, 8, 9):
        c.hline(4, 10, y, BLUE, 3 if y % 2 == 0 else 2)
    c.set(4, 6, GLASS, GLASS.top, glint=True)
    for y in (5, 7, 9):
        c.set(13, y, GOLD_FOIL, 4)
        c.set(12, y, GOLD_FOIL, 2)
    c.set(2, 6, GRAPHITE, 0)
    c.set(2, 9, GRAPHITE, 0)
    c.outline()
    return c


@texture("item/editing_kit", refs=("item/potion",))
def editing_kit(rng):
    # 两支小离心管：蓝盖的是 Cas9 蛋白，红盖的是向导 RNA；锥形的底
    c = Canvas()
    rows = [
        ".4444.",
        "455553",
        ".2222.",
        ".2552.",
        ".2442.",
        ".2442.",
        ".2332.",
        "..22..",
        "..2...",
    ]
    for ox, oy, cap in ((2, 3, BLUE), (8, 5, RED)):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == ".":
                    continue
                lv = int(ch)
                if y < 2:
                    c.set(ox + x, oy + y, cap, lv)
                else:
                    c.set(ox + x, oy + y, GLASS, lv)
        # 管里一点液体，在最下面
        c.set(ox + 2, oy + 7, cap, 2)
        c.set(ox + 1, oy + 3, GLASS, GLASS.top, glint=True)
    c.outline()
    return c


# ---------------------------------------------------------------- 培养箱：白色保温柜，玻璃内门

DISHES = ((4, 6), (7, 6), (4, 9), (8, 9))


def incubator_face(rng: random.Random, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    status_lights(c, on)
    # 内门的钢框和玻璃，柜里两层搁板
    c.recess(2, 4, 10, 12, STEEL, 4)
    for x, y in c.cells(3, 5, 9, 11):
        if on:
            c.set(x, y, AMBER_GLOW, 2 if y in (8, 11) else 1)
        else:
            c.set(x, y, GLASS, 1)
    for y in (8, 11):
        c.hline(3, 9, y, STEEL, 4)
    # 搁板上的平板：两格宽，上面一道亮的皿盖
    for x, y in DISHES:
        c.hline(x, x + 1, y, WHITE, 5)
        c.hline(x, x + 1, y + 1, AMBER, 3)
    if not on:
        c.set(3, 5, WHITE, 5, glint=True)
        c.set(4, 5, WHITE, 5, glint=True)
    # 门边：温度读数和门把手
    screen(c, 12, 4, 13, 5, on, r=RED_GLOW, lines=[2, 1])
    c.vline(12, 8, 11, WHITE, 5)
    c.vline(13, 8, 11, WHITE, 2)
    return c


@texture("block/incubator_front", refs=("block/smoker_front", "block/glass"))
def incubator_front(rng):
    return incubator_face(rng, False)


@texture("block/incubator_front_on", glow=True)
def incubator_front_on(rng):
    return incubator_face(rng, True)


@texture("block/incubator_side")
def incubator_side(rng):
    c = shell(rng, WHITE)
    # 下面一排散热百叶，上面一个二氧化碳进气口接着一根细管
    c.vent(3, 9, 12, 13, WHITE, 3)
    c.ring(10.5, 4.5, 1.4, STEEL, 3)
    c.set(10, 4, GRAPHITE, 1)
    c.hline(3, 9, 4, STEEL, 4)
    c.hline(3, 9, 5, STEEL, 2)
    bolts(c, 2, r=WHITE)
    return c


@texture("block/incubator_top", refs=("block/smoker_top",))
def incubator_top(rng):
    c = shell(rng, WHITE)
    c.grille(4, 4, 11, 9, WHITE, 3)
    # 后面一个排气口
    c.ring(7.5, 12.0, 1.4, STEEL, 3)
    c.set(7, 11, GRAPHITE, 1)
    return c


# ---------------------------------------------------------------- 测序仪：白机身、深色前脸

# 屏幕上的读数：四种碱基四种颜色，一行一行的峰
BASES = (GREEN_GLOW, BLUE_GLOW, YELLOW_GLOW, RED_GLOW)


def sequencer_face(rng: random.Random, on: bool) -> Canvas:
    c = shell(rng, WHITE)
    c.panel(2, 3, 13, 13, GRAPHITE, 2, raised=False)
    # 触摸屏
    c.frame(2, 4, 9, 10, GRAPHITE, 1)
    c.rect(3, 5, 8, 9, SCREEN, 1)
    if on:
        for i, y in enumerate((5, 7, 9)):
            for x in range(3, 9):
                c.set(x, y, BASES[(x * 3 + i * 5 + (x * i) % 3) % 4], 4, glint=True)
    else:
        c.set(3, 5, SCREEN, 3, glint=True)
        c.set(4, 5, SCREEN, 2, glint=True)
    # 放芯片的仓门：一道缝，一个按钮
    c.recess(11, 4, 12, 10, GRAPHITE, 3)
    c.vline(11, 6, 8, GRAPHITE, 0)
    c.set(12, 11, GREEN_GLOW if on else SCREEN, 4 if on else 2, glint=True)
    # 底下的灯带
    c.hline(3, 12, 12, BLUE_GLOW if on else SCREEN, 3 if on else 2)
    return c


@texture("block/sequencer_front", refs=("block/observer_front", "block/crafter_south"))
def sequencer_front(rng):
    return sequencer_face(rng, False)


@texture("block/sequencer_front_on", glow=True)
def sequencer_front_on(rng):
    return sequencer_face(rng, True)


@texture("block/sequencer_side")
def sequencer_side(rng):
    c = shell(rng, WHITE)
    # 竖的散热缝，后半截一块深色的接口板
    c.vent(3, 3, 8, 12, WHITE, 3, horizontal=False)
    c.panel(10, 8, 12, 12, GRAPHITE, 2)
    c.set(11, 9, STEEL, 5)
    c.set(11, 11, STEEL, 5)
    return c


@texture("block/sequencer_top", refs=("block/crafter_top",))
def sequencer_top(rng):
    c = shell(rng, WHITE)
    # 试剂仓的翻盖：一道缝，前沿一个把手
    c.frame(3, 3, 12, 10, WHITE, 2)
    c.hline(4, 11, 4, WHITE, 5)
    c.hline(6, 9, 11, GRAPHITE, 2)
    c.hline(6, 9, 12, WHITE, 3)
    return c


# ---------------------------------------------------------------- 基因编辑台：钢的移液工作站

def editor_face(rng: random.Random, on: bool) -> Canvas:
    c = casing(rng)
    status_lights(c, on)
    # 玻璃罩
    c.recess(2, 4, 13, 13, STEEL, 4)
    for x, y in c.cells(3, 5, 12, 12):
        c.set(x, y, GLASS, 1)
    # 横梁和挂在上面的移液头
    c.hline(3, 12, 5, STEEL, 5)
    c.hline(3, 12, 6, STEEL, 2)
    c.rect(6, 7, 8, 8, GRAPHITE, 3)
    c.hline(6, 8, 7, GRAPHITE, 4)
    c.vline(7, 9, 9, WHITE, 4)
    # 台面上的 96 孔板：白板子，一格一个孔
    c.rect(3, 11, 12, 12, WHITE, 4)
    for x in range(3, 13):
        if x % 2 == 1:
            c.set(x, 11, VIOLET_GLOW if on and x in (5, 7, 9) else WHITE, 3 if on and x in (5, 7, 9) else 2)
            c.set(x, 12, WHITE, 2)
    c.hline(3, 12, 13, STEEL, 3)
    if on:
        c.set(7, 10, VIOLET_GLOW, 4, glint=True)
    else:
        c.set(3, 7, GLASS, 4, glint=True)
        c.set(4, 7, GLASS, 3, glint=True)
    return c


@texture("block/gene_editor_front", refs=("block/crafter_south", "block/glass"))
def gene_editor_front(rng):
    return editor_face(rng, False)


@texture("block/gene_editor_front_on", glow=True)
def gene_editor_front_on(rng):
    return editor_face(rng, True)


@texture("block/gene_editor_side")
def gene_editor_side(rng):
    c = casing(rng)
    # 罩子的侧窗和底下的电源、气路接口
    c.recess(3, 3, 12, 8, STEEL, 4)
    for x, y in c.cells(4, 4, 11, 7):
        c.set(x, y, GLASS, 1)
    c.set(4, 4, GLASS, 3, glint=True)
    c.panel(4, 10, 7, 12, GRAPHITE, 2)
    c.ring(10.5, 11.5, 1.3, STEEL, 3)
    c.set(10, 11, GRAPHITE, 1)
    bolts(c, 2)
    return c


@texture("block/gene_editor_top", refs=("block/crafter_top",))
def gene_editor_top(rng):
    c = casing(rng)
    # 罩顶的高效过滤器：一整块细密的格栅
    c.recess(3, 3, 12, 12, STEEL, 4)
    c.grille(4, 4, 11, 11, STEEL, 4)
    return c
