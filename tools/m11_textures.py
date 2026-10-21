"""生成 M11（ADR-0022）新方块和物品的贴图：增产剂、喷涂机、集装机、流速监测器、集装分拣器、无线输电塔、卫星配电站、
地热发电站、能量枢纽、三台高级设备、自我演化研究站、地基、分流器的优先出口箭头。

用法：python tools/m11_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数：16×16 像素画、深灰机壳、青色指示灯。随机数用固定种子。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_textures as ft  # noqa: E402
from factory_textures import (BLOCK, ITEM, N, DARK, MID, LIGHT, CYAN, CYAN_DIM, BELT_RUBBER, BELT_AMBER, BELT_STEEL,  # noqa: E402
                              shade, mix, new, clear, put, noise, frame, rect, outline, save, casing, hazard)

# 增产剂的三档颜色：戴森球计划里是浅绿、青绿、蓝紫
PROLIFERATOR = ((150, 222, 96), (64, 206, 176), (128, 120, 255))
PILE = (176, 96, 230)
LAVA = (255, 120, 30)
GOLD = (232, 190, 70)


# ---- 串在传送带中间的方块

def belt_pass():
    """底座：顶面是一段传送带（朝上为前进方向），侧面下半截是机壳，前后两面开口让传送带穿过。"""
    rng = random.Random(1101)
    top = new(shade(BELT_STEEL, 0.75))
    noise(top, rng, 0.04)
    rect(top, 3, 0, 12, 15, BELT_RUBBER)
    for y in range(N):
        put(top, 2, y, shade(BELT_STEEL, 1.2))
        put(top, 13, y, shade(BELT_STEEL, 0.55))
        # 人字纹，和传送带一个样子
        for x in range(3, 13):
            if (y + abs(x - 7.5)) % 8 < 1:
                put(top, x, y, shade(BELT_AMBER, 0.75))
    save(top, BLOCK, "belt_pass_top")

    side = new(shade(BELT_STEEL, 0.78))
    noise(side, rng, 0.04)
    hazard(side, 13, 15)
    for x in range(N):
        put(side, x, 7, shade(BELT_STEEL, 1.25))
        put(side, x, 12, shade(BELT_STEEL, 0.55))
    for (x, y) in ((2, 9), (13, 9)):
        put(side, x, y, shade(BELT_STEEL, 1.4))
        put(side, x + 1, y + 1, shade(BELT_STEEL, 0.6))
    save(side, BLOCK, "belt_pass_side")

    port = side.copy()
    rect(port, 3, 8, 12, 15, (22, 22, 26))
    for x in range(3, 13):
        put(port, x, 8, BELT_RUBBER)
        put(port, x, 9, shade(BELT_AMBER, 0.6) if x % 3 == 0 else BELT_RUBBER)
    save(port, BLOCK, "belt_pass_port")


def spray_coater():
    """喷涂机：白色的罩子，正面一扇观察窗，顶上一个装增产剂的罐子和喷嘴。"""
    rng = random.Random(1102)
    white = (196, 204, 206)
    side = ft.casing(white, rng, seam=False)
    for x in range(1, N - 1):
        put(side, x, 3, shade(PROLIFERATOR[0], 0.8))
    rect(side, 4, 6, 11, 12, shade(white, 0.82))
    rect(side, 5, 7, 10, 11, (40, 60, 52))
    # 喷出来的雾
    for (x, y) in ((6, 9), (7, 10), (8, 9), (9, 10), (7, 8)):
        put(side, x, y, mix(PROLIFERATOR[0], (230, 255, 220), 0.4))
    save(side, BLOCK, "spray_coater_side")

    top = ft.casing(shade(white, 1.03), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 4.5:
                put(top, x, y, shade(PROLIFERATOR[0], 1.05 - 0.07 * d))
            elif d < 5.3:
                put(top, x, y, shade(white, 0.7))
    rect(top, 7, 7, 8, 8, (40, 60, 52))
    save(top, BLOCK, "spray_coater_top")


def automatic_piler():
    """自动集装机：深色机壳，正面一排叠起来的箱子图案，顶上是压板。"""
    rng = random.Random(1103)
    side = ft.casing(DARK, rng, seam=False)
    for x in range(1, N - 1):
        put(side, x, 3, PILE)
        put(side, x, 4, shade(PILE, 0.6))
    for i, y in enumerate((11, 8)):
        rect(side, 5 + i, y, 10 - i, y + 2, shade(BELT_AMBER, 0.9 - 0.1 * i))
        for x in range(5 + i, 11 - i):
            put(side, x, y, shade(BELT_AMBER, 1.15))
    save(side, BLOCK, "automatic_piler_side")

    top = ft.casing(MID, rng, seam=False)
    rect(top, 3, 3, 12, 12, shade(MID, 0.6))
    for i in range(3, 13):
        put(top, i, 3, shade(PILE, 0.85))
        put(top, i, 12, shade(PILE, 0.85))
    rect(top, 6, 6, 9, 9, shade(MID, 1.2))
    put(top, 7, 7, CYAN)
    save(top, BLOCK, "automatic_piler_top")


def traffic_monitor():
    """流速监测器：钢架，横梁上一块小屏幕和一盏灯（报警时红）。"""
    rng = random.Random(1104)
    frame_tex = new(BELT_STEEL)
    noise(frame_tex, rng, 0.04)
    for y in range(N):
        put(frame_tex, 0, y, shade(BELT_STEEL, 1.3))
        put(frame_tex, 15, y, shade(BELT_STEEL, 0.6))
        if y % 5 == 0:
            for x in range(1, 15):
                put(frame_tex, x, y, shade(BELT_STEEL, 0.8))
    save(frame_tex, BLOCK, "traffic_monitor_frame")

    screen = new((18, 30, 36))
    frame(screen, shade(BELT_STEEL, 1.1), shade(BELT_STEEL, 0.5))
    # 流量的小柱状图
    for i, h in enumerate((3, 5, 4, 7, 6, 8, 5, 9, 7, 10)):
        x = 3 + i
        for y in range(13 - h, 13):
            put(screen, x, y, CYAN if y > 12 - h else shade(CYAN, 1.2))
    save(screen, BLOCK, "traffic_monitor_screen")

    for name, core, edge in (("traffic_monitor_lamp", (120, 230, 140), (40, 120, 60)),
                             ("traffic_monitor_lamp_alarm", (255, 210, 190), (229, 60, 40))):
        img = new(edge)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5) / 8
                put(img, x, y, mix(core, edge, min(1, d * 1.3)))
        save(img, BLOCK, name)


def pile_sorter():
    ft.sorter("_pile", PILE)


def splitter_priority():
    """分流器优先出口的箭头：亮黄色，朝上（北）。"""
    img = new(shade(BELT_AMBER, 0.6))
    for y in range(N):
        for x in range(N):
            if (x + y) % 6 < 3:
                put(img, x, y, (255, 214, 72))
    save(img, BLOCK, "splitter_priority")


# ---- 电力

def towers():
    """无线输电塔：青色的线圈和一圈圈的感应环；卫星配电站：白色塔身、金色的碟面。"""
    rng = random.Random(1110)
    coil = new(CYAN_DIM)
    for y in range(N):
        f = 1.25 if y % 3 == 0 else 0.8
        for x in range(N):
            put(coil, x, y, shade(mix(CYAN_DIM, CYAN, 0.35), f * (1 + rng.uniform(-0.04, 0.04))))
    for x in range(N):
        put(coil, x, 0, (70, 76, 84))
        put(coil, x, 15, (50, 54, 60))
    save(coil, BLOCK, "wireless_power_tower_coil")
    for name, core, edge in (("wireless_power_tower_top", (240, 255, 230), (110, 230, 140)),
                             ("wireless_power_tower_top_off", (70, 120, 90), (30, 70, 50))):
        img = new(edge)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5) / 8
                put(img, x, y, shade(mix(core, edge, min(1, d * 1.2)), 1 + rng.uniform(-0.03, 0.03)))
        save(img, BLOCK, name)

    white = (214, 218, 222)
    pole = new(white)
    noise(pole, rng, 0.03)
    for y in range(N):
        put(pole, 0, y, shade(white, 1.1))
        put(pole, 15, y, shade(white, 0.65))
        for x in range(1, 15):
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                put(pole, x, y, shade(white, 0.72))
    for y in (0, 8):
        for x in range(N):
            put(pole, x, y, shade(white, 0.85))
    save(pole, BLOCK, "satellite_substation_pole")
    dish = new(GOLD)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            put(dish, x, y, shade(GOLD, 1.15 - 0.03 * d) if int(d) % 3 else shade(GOLD, 0.7))
    save(dish, BLOCK, "satellite_substation_coil")
    for name, core, edge in (("satellite_substation_top", (255, 250, 220), GOLD),
                             ("satellite_substation_top_off", (120, 104, 60), (80, 66, 36))):
        img = new(edge)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5) / 8
                put(img, x, y, mix(core, edge, min(1, d * 1.2)))
        save(img, BLOCK, name)


def cube_machine(name, base, accent, seed, window, top_lit=False, top=None):
    """一格的设备：机壳侧面、顶面，正面一扇窗，工作时窗里亮。{@code window(img, lit)} 画窗里的东西。"""
    rng = random.Random(seed)
    side = casing(base, rng, seam=False)
    for x in range(1, N - 1):
        put(side, x, 3, accent)
        put(side, x, 4, shade(accent, 0.55))
    rect(side, 4, 8, 11, 12, shade(base, 0.82))
    for (x, y) in ((4, 8), (11, 8), (4, 12), (11, 12)):
        put(side, x, y, shade(base, 1.3))
    save(side, BLOCK, name + "_side")
    for suffix, lit in (("", False), ("_on", True)):
        img = side.copy()
        rect(img, 3, 6, 12, 13, (34, 38, 44))
        rect(img, 4, 7, 11, 12, (20, 24, 28))
        window(img, lit)
        for i, x in enumerate((4, 6, 8)):
            put(img, x, 2, accent if lit and i < 2 else shade(accent, 0.45))
        save(img, BLOCK, name + "_front" + suffix)
        if top is not None and (top_lit or not lit):
            save(top(rng, lit), BLOCK, name + "_top" + (suffix if top_lit else ""))


def geothermal():
    def window(img, lit):
        # 熔岩在井口翻腾
        for y in range(7, 13):
            for x in range(4, 12):
                t = (y - 7) / 5
                c = mix((255, 220, 120), LAVA, t) if lit else mix((120, 60, 30), (60, 30, 20), t)
                if (x * 7 + y * 3) % 5 == 0:
                    c = shade(c, 1.2 if lit else 0.8)
                put(img, x, y, c)

    def top(rng, lit):
        img = casing(shade(DARK, 1.1), rng, seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 3.8:
                    put(img, x, y, shade((90, 90, 96), 0.8 + 0.05 * d))
                elif d < 4.6:
                    put(img, x, y, (60, 60, 66))
        rect(img, 7, 7, 8, 8, (30, 30, 34))
        return img

    cube_machine("geothermal_power_station", (78, 64, 58), LAVA, 1120, window, top=top)


def energy_exchanger():
    def window(img, lit):
        # 中间一根能量柱，两边是蓄电器的卡槽
        for y in range(7, 13):
            put(img, 5, y, (70, 90, 100))
            put(img, 10, y, (70, 90, 100))
            for x in (7, 8):
                put(img, x, y, mix((255, 255, 255), CYAN, abs(y - 9.5) / 3) if lit else CYAN_DIM)

    def top(rng, lit):
        img = casing(MID, rng, seam=False)
        for (x0, y0) in ((3, 3), (9, 3), (3, 9), (9, 9)):
            rect(img, x0, y0, x0 + 3, y0 + 3, shade(MID, 0.6))
            put(img, x0 + 1, y0 + 1, CYAN)
        return img

    cube_machine("energy_exchanger", (64, 72, 92), CYAN, 1121, window, top=top)


def advanced_machines():
    violet = (150, 90, 230)

    def negentropy_window(img, lit):
        # 观察窗里一团旋转的紫色光
        for y in range(7, 13):
            for x in range(4, 12):
                a = math.atan2(y - 9.5, x - 7.5)
                d = math.hypot(x - 7.5, y - 9.5)
                swirl = (math.sin(a * 2 + d * 1.3) + 1) / 2
                base = mix((255, 255, 255), violet, min(1, d / 3)) if lit else shade(violet, 0.3)
                put(img, x, y, shade(base, 0.7 + 0.4 * swirl))

    def negentropy_top(rng, lit):
        img = casing((40, 36, 56), rng, seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 4.8:
                    put(img, x, y, mix((255, 255, 255), violet, d / 4.8) if lit else shade(violet, 0.35 + 0.05 * d))
        return img

    cube_machine("negentropy_smelter", (40, 36, 56), violet, 1130, negentropy_window, top_lit=True, top=negentropy_top)

    gold = GOLD

    def recomposing_window(img, lit):
        # 机械臂，两头各一个
        arm = (255, 220, 90) if lit else (120, 100, 40)
        for (x, y) in ((4, 12), (5, 11), (6, 10), (7, 9), (8, 9), (9, 10), (10, 11), (11, 12)):
            put(img, x, y, arm)
        put(img, 7, 8, (230, 230, 240) if lit else (90, 90, 96))
        put(img, 8, 8, (230, 230, 240) if lit else (90, 90, 96))

    def recomposing_top(rng, lit):
        img = casing((52, 52, 60), rng, seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 3.5 <= d < 5.5:
                    put(img, x, y, shade(gold, 1.1 - 0.05 * d))
        rect(img, 7, 3, 8, 12, shade(gold, 0.8))
        return img

    cube_machine("recomposing_assembler", (52, 52, 60), gold, 1131, recomposing_window, top=recomposing_top)

    teal = (60, 220, 200)

    def quantum_window(img, lit):
        # 两只烧瓶，量子化的蓝绿色溶液
        for x0 in (4, 8):
            for y in range(9, 13):
                for x in range(x0, x0 + 4):
                    put(img, x, y, shade(teal, (1.0 if lit else 0.35) - 0.06 * (y - 9)))
            put(img, x0 + 1, 8, (120, 130, 136))
            put(img, x0 + 2, 8, (120, 130, 136))
        if lit:
            for (x, y) in ((5, 10), (10, 9), (9, 11)):
                put(img, x, y, (230, 255, 250))

    def quantum_top(rng, lit):
        white = (200, 206, 214)
        img = casing(white, rng, seam=False)
        for (cx, cy) in ((5.0, 5.0), (10.5, 5.0), (7.75, 10.5)):
            for y in range(N):
                for x in range(N):
                    d = math.hypot(x - cx, y - cy)
                    if d < 2.6:
                        put(img, x, y, shade(teal, 1.1 - 0.15 * d))
        return img

    cube_machine("quantum_chemical_plant", (200, 206, 214), teal, 1132, quantum_window, top=quantum_top)


def self_evolution_lab():
    """自我演化研究站：和矩阵研究站一个造型，立柱换成金色，核心是紫绿两色。"""
    rng = random.Random(1140)
    frame_tex = new(GOLD)
    noise(frame_tex, rng, 0.04)
    for i in range(N):
        put(frame_tex, i, 0, shade(GOLD, 1.2))
        put(frame_tex, i, 15, shade(GOLD, 0.6))
        put(frame_tex, 0, i, shade(GOLD, 0.7))
        put(frame_tex, 15, i, shade(GOLD, 0.7))
    for x in range(3, 13):
        put(frame_tex, x, 7, shade(GOLD, 0.7))
        put(frame_tex, x, 8, (170, 255, 140))
    save(frame_tex, BLOCK, "self_evolution_lab_frame")
    core = new()
    for y in range(N):
        for x in range(N):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            c = mix((255, 255, 255), (170, 255, 140), min(1, d * 1.4))
            if d > 0.7:
                c = mix((170, 255, 140), (150, 90, 230), (d - 0.7) * 3.3)
            put(core, x, y, c)
    save(core, BLOCK, "self_evolution_lab_core")


def foundation():
    """地基：灰色的混凝土板，四边一道黄线，中间压着防滑纹。"""
    rng = random.Random(1150)
    base = (118, 120, 124)
    img = new(base)
    noise(img, rng, 0.05)
    for i in range(N):
        put(img, i, 0, shade(base, 1.15))
        put(img, 0, i, shade(base, 1.15))
        put(img, i, 15, shade(base, 0.7))
        put(img, 15, i, shade(base, 0.7))
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            put(img, x, y, shade((214, 172, 46), 0.9 + rng.uniform(-0.05, 0.05)))
    for y in range(4, 12):
        for x in range(4, 12):
            if (x + y) % 4 == 0:
                put(img, x, y, shade(base, 0.85))
    save(img, BLOCK, "foundation")


# ---- 物品

def proliferators():
    """增产剂：一只喷罐，罐身是档位的颜色，Mk.II、Mk.III 罐身上多一、两道白圈。"""
    for tier, color in enumerate(PROLIFERATOR, start=1):
        img = clear()
        for y in range(4, 15):
            for x in range(5, 11):
                f = 1.2 if x == 6 else 0.75 if x == 10 else 1.0
                put(img, x, y, shade(color, f))
        for x in range(6, 10):
            put(img, x, 3, (190, 196, 200))
        rect(img, 7, 1, 8, 2, (90, 96, 104))
        put(img, 9, 1, (60, 64, 70))
        for i in range(tier - 1):
            for x in range(5, 11):
                put(img, x, 8 + i * 3, (236, 240, 242))
        for x in range(5, 11):
            put(img, x, 14, shade(color, 0.55))
        outline(img, (20, 24, 28))
        save(img, ITEM, "proliferator_mk%d" % tier)


def combustible_unit():
    """燃烧单元：压成方块的燃料，橙红色，裂缝里透出火光。"""
    rng = random.Random(1160)
    img = clear()
    for y in range(3, 13):
        for x in range(3, 13):
            put(img, x, y, shade((70, 44, 36), 1 + rng.uniform(-0.12, 0.12)))
    for (x, y) in ((5, 5), (6, 6), (7, 6), (8, 7), (9, 8), (6, 9), (5, 10), (10, 5), (10, 10)):
        put(img, x, y, (255, 150, 50))
    for (x, y) in ((7, 7), (8, 8)):
        put(img, x, y, (255, 230, 140))
    for i in range(3, 13):
        put(img, i, 3, (110, 76, 60))
        put(img, 3, i, (110, 76, 60))
    outline(img, (24, 14, 10))
    save(img, ITEM, "combustible_unit")


def hydrogen_fuel_rod():
    """液氢燃料棒：钛壳，中间一段透明的窗看得见淡蓝的氢，两头红色的端盖。"""
    img = clear()
    for y in range(2, 14):
        for x in range(6, 10):
            f = 1.2 if x == 6 else 0.7 if x == 9 else 1.0
            put(img, x, y, shade((170, 178, 190), f))
    for y in range(5, 11):
        for x in range(7, 9):
            put(img, x, y, (170, 220, 255) if (y % 2) else (130, 190, 240))
    for x in range(6, 10):
        put(img, x, 1, (200, 60, 50))
        put(img, x, 2, (230, 80, 64))
        put(img, x, 13, (200, 60, 50))
        put(img, x, 14, (150, 40, 34))
    outline(img, (20, 24, 28))
    save(img, ITEM, "hydrogen_fuel_rod")


def main():
    belt_pass()
    spray_coater()
    automatic_piler()
    traffic_monitor()
    pile_sorter()
    splitter_priority()
    towers()
    geothermal()
    energy_exchanger()
    advanced_machines()
    self_evolution_lab()
    foundation()
    proliferators()
    combustible_unit()
    hydrogen_fuel_rod()


if __name__ == "__main__":
    main()
