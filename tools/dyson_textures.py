"""生成戴森球阶段的物品和设备贴图（ADR-0016），16×16 像素画。

用法：python tools/dyson_textures.py [方块贴图目录] [物品贴图目录]
和 factory_textures.py 共用画图的函数和配色；随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_textures as ft  # noqa: E402
from factory_textures import (CYAN, CYAN_DIM, DARK, LIGHT, MID, N, casing, clear, frame, get, matrix, mix, new, noise,  # noqa: E402
                              outline, put, rect, rivet, save, shade)

BLOCK = ft.BLOCK
ITEM = ft.ITEM

SAIL_GOLD = (236, 196, 92)
PHOTON = (255, 240, 170)
WHITE = (232, 236, 240)
ROCKET_RED = (200, 60, 50)


# ---- 物品

def photon_combiner():
    """光子合并器：银色的框里一块棱镜，几束彩色的光汇成一束白光。"""
    img = clear()
    rect(img, 2, 3, 13, 12, (150, 158, 168))
    rect(img, 3, 4, 12, 11, (40, 46, 60))
    for i, c in enumerate(((255, 90, 90), (120, 255, 120), (110, 150, 255))):
        y = 5 + i * 2
        for x in range(3, 8):
            put(img, x, y + (x - 3) // 3, c)
    for x in range(8, 13):
        put(img, x, 7, (255, 255, 240))
        put(img, x, 8, (255, 250, 200))
    for y in range(5, 11):
        put(img, 8, y, mix((180, 230, 255), (255, 255, 255), (y - 5) / 6))
    outline(img, (30, 34, 44))
    save(img, ITEM, "photon_combiner")


def solar_sail():
    """太阳帆：一片金色的六边形薄膜，中间的十字骨架，薄膜上有细细的反光纹。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if dx * 0.5 + dy * 0.866 <= 6.4 and dx <= 6.6:
                sheen = 1.12 - 0.04 * dy + (0.1 if (x + y) % 5 == 0 else 0)
                put(img, x, y, shade(SAIL_GOLD, sheen))
    for i in range(2, 14):
        put(img, i, 7, (250, 240, 210))
        put(img, 7, i, (250, 240, 210))
    put(img, 7, 7, (255, 255, 255))
    outline(img, (110, 80, 20))
    save(img, ITEM, "solar_sail")


def frame_material():
    """框架材料：一段灰黑色的桁架，碳纳米管的斜撑，钛合金的弦杆。"""
    img = clear()
    for x in range(1, 15):
        put(img, x, 3, (190, 200, 220))
        put(img, x, 4, (130, 140, 165))
        put(img, x, 11, (190, 200, 220))
        put(img, x, 12, (130, 140, 165))
    for k in range(0, 14, 4):
        for t in range(7):
            put(img, 1 + k + t // 2, 5 + t, (70, 74, 84))
            if 1 + k + 3 - t // 2 < 15:
                put(img, 1 + k + 3 - t // 2, 5 + t, (95, 100, 112))
    outline(img, (24, 26, 34))
    save(img, ITEM, "frame_material")


def dyson_sphere_component():
    """戴森球组件：一块弧形的金色板，背后是深色的桁架，角上发着青光。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 20)
            if 11 <= d <= 18.5 and 1 <= x <= 14:
                c = SAIL_GOLD if d < 16 else (80, 86, 100)
                put(img, x, y, shade(c, 1.15 - 0.03 * (d - 11)))
    for x in range(2, 14, 3):
        put(img, x, 9, (255, 236, 160))
    put(img, 2, 12, CYAN)
    put(img, 13, 12, CYAN)
    outline(img, (40, 34, 20))
    save(img, ITEM, "dyson_sphere_component")


def small_carrier_rocket():
    """小型运载火箭：斜着的白色箭体，红色鼻锥和尾翼，底下喷着火。"""
    img = clear()
    for t in range(11):
        cx, cy = 4 + t * 0.75, 12 - t * 0.75
        for w in (-1, 0, 1):
            x, y = int(round(cx + w * 0.7)), int(round(cy + w * 0.7))
            c = WHITE if t < 8 else ROCKET_RED
            if t in (3, 4) and w == 0:
                c = (60, 120, 200)
            put(img, x, y, shade(c, 1.1 - 0.12 * w))
    put(img, 12, 3, ROCKET_RED)
    put(img, 13, 2, ROCKET_RED)
    for (x, y) in ((2, 11), (3, 12), (4, 14), (5, 13)):
        put(img, x, y, ROCKET_RED)
    for (x, y, c) in ((2, 14, (255, 220, 120)), (1, 15, (255, 150, 60)), (3, 13, (255, 250, 200))):
        put(img, x, y, c)
    outline(img, (40, 40, 50))
    save(img, ITEM, "small_carrier_rocket")


def deuteron_fuel_rod():
    """氘核燃料棒：竖着的金属棒，两端是钛合金的帽，中间一段发青光的燃料。"""
    img = clear()
    rect(img, 6, 1, 9, 14, (150, 160, 190))
    rect(img, 6, 4, 9, 11, (60, 210, 220))
    for y in range(4, 12):
        put(img, 6, y, (150, 250, 255))
        put(img, 9, y, (30, 140, 160))
    rect(img, 5, 1, 10, 2, (110, 120, 150))
    rect(img, 5, 13, 10, 14, (110, 120, 150))
    outline(img, (20, 30, 50))
    save(img, ITEM, "deuteron_fuel_rod")


def titanium_glass():
    """钛化玻璃：一块带淡蓝色调的厚玻璃板，边上是钛的包边，两道高光。"""
    img = clear()
    rect(img, 2, 2, 13, 13, (170, 200, 225))
    for y in range(3, 13):
        for x in range(3, 13):
            put(img, x, y, (140, 180, 215), 200)
    for i in range(4):
        put(img, 4 + i, 9 - i, (240, 250, 255))
        put(img, 8 + i, 11 - i, (220, 236, 250))
    frame_c = (150, 160, 190)
    for i in range(2, 14):
        put(img, i, 2, frame_c)
        put(img, i, 13, shade(frame_c, 0.7))
        put(img, 2, i, frame_c)
        put(img, 13, i, shade(frame_c, 0.7))
    outline(img, (40, 50, 70))
    save(img, ITEM, "titanium_glass")


def casimir_crystal():
    ft.crystal("casimir_crystal", (200, 255, 250), (60, 150, 190), 311)


def plane_filter():
    """位面过滤器：圆形的滤镜，钛化玻璃里叠着几层紫色的干涉条纹。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.8:
                if d > 5.6:
                    c = (150, 160, 190)
                else:
                    band = int(d * 1.6 + (x - y) * 0.3) % 3
                    c = [(110, 70, 180), (170, 120, 240), (210, 190, 255)][band]
                put(img, x, y, c)
    put(img, 5, 5, (255, 255, 255))
    outline(img, (30, 24, 50))
    save(img, ITEM, "plane_filter")


def quantum_chip():
    """量子芯片：深色的大芯片，金色引脚，中间一块发紫光的量子核心。"""
    img = clear()
    rect(img, 2, 2, 13, 13, (36, 30, 54))
    for i in range(3, 13, 2):
        for (x, y) in ((i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)):
            put(img, x, y, (226, 186, 80))
    for y in range(5, 11):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            put(img, x, y, mix((255, 230, 255), (140, 80, 230), d / 3.6))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, CYAN)
    outline(img, (14, 10, 24))
    save(img, ITEM, "quantum_chip")


def graviton_lens():
    """引力透镜：一枚透明的透镜，中心是一个小黑洞，周围的光被扭成一圈亮环。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.9:
                if d < 1.6:
                    c = (6, 6, 12)
                elif d < 2.8:
                    c = mix((255, 250, 220), (255, 170, 80), (d - 1.6) / 1.2)
                elif d > 5.9:
                    c = (130, 140, 170)
                else:
                    c = mix((120, 170, 230), (60, 90, 160), (d - 2.8) / 3.1)
                put(img, x, y, c)
    put(img, 4, 4, (255, 255, 255))
    outline(img, (20, 26, 44))
    save(img, ITEM, "graviton_lens")


def critical_photon():
    """临界光子：一团明亮的白金色光球，四周放射出光芒。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            ray = abs(math.sin(a * 4)) > 0.92 and d < 7.4
            if d < 3.6:
                put(img, x, y, mix((255, 255, 255), PHOTON, d / 3.6))
            elif ray:
                put(img, x, y, shade(PHOTON, 1.05 - 0.08 * d), 220)
            elif d < 4.6:
                put(img, x, y, (255, 200, 90), 180)
    save(img, ITEM, "critical_photon")


# ---- 设备

def em_rail_ejector():
    rng = random.Random(401)
    base = casing(DARK, rng, seam=False)
    ft.hazard(base, 12, 14)
    save(base, BLOCK, "em_rail_ejector_base")

    top = casing(MID, rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.5 < d < 6.6:
                put(top, x, y, CYAN_DIM)
    save(top, BLOCK, "em_rail_ejector_top")

    ring = new(LIGHT)
    noise(ring, rng, 0.04)
    for i in range(N):
        put(ring, i, 0, shade(LIGHT, 1.2))
        put(ring, i, N - 1, shade(LIGHT, 0.65))
    for x in range(1, N, 3):
        put(ring, x, 7, shade(LIGHT, 0.75))
    save(ring, BLOCK, "em_rail_ejector_ring")

    rail = new((120, 128, 140))
    noise(rail, rng, 0.05)
    for i in range(N):
        put(rail, 0, i, (170, 178, 188))
        put(rail, N - 1, i, (70, 76, 86))
        if i % 4 == 0:
            for x in range(1, N - 1):
                put(rail, x, i, (96, 102, 112))
    save(rail, BLOCK, "em_rail_ejector_rail")

    coil = new((176, 104, 56))
    for y in range(N):
        for x in range(N):
            put(coil, x, y, shade((196, 118, 62), 1.15 if y % 2 == 0 else 0.8))
    frame(coil, (120, 128, 140), (70, 76, 86))
    save(coil, BLOCK, "em_rail_ejector_coil")

    glow = new(CYAN)
    for y in range(N):
        for x in range(N):
            put(glow, x, y, mix((220, 255, 255), CYAN, abs(x - 7.5) / 7.5))
    save(glow, BLOCK, "em_rail_ejector_glow")

    sail = clear()
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if dx * 0.5 + dy * 0.866 <= 7 and dx <= 7.2:
                put(sail, x, y, mix((255, 255, 230), SAIL_GOLD, max(dx, dy) / 7.5))
    save(sail, BLOCK, "solar_sail_flight")


def vertical_launching_silo():
    rng = random.Random(402)
    side = casing((206, 210, 216), rng, seam=False)
    for x in range(N):
        put(side, x, 5, (150, 156, 164))
        put(side, x, 10, (150, 156, 164))
    for (x, y) in ((3, 2), (12, 2)):
        put(side, x, y, (220, 60, 50))
    save(side, BLOCK, "vertical_launching_silo_side")

    lower = casing((196, 200, 206), rng)
    ft.hazard(lower, 13, 15)
    for y in range(3, 11):
        put(lower, 7, y, (110, 116, 124))
        put(lower, 8, y, (230, 234, 238))
    save(lower, BLOCK, "vertical_launching_silo_lower")

    rim = new((90, 96, 106))
    noise(rim, rng, 0.05)
    ft.hazard(rim, 0, 15, (214, 172, 46), (40, 40, 44))
    save(rim, BLOCK, "vertical_launching_silo_rim")

    pad = new((48, 52, 60))
    for y in range(N):
        for x in range(N):
            if x % 4 == 0 or y % 4 == 0:
                put(pad, x, y, (70, 76, 86))
    for y in range(N):
        for x in range(N):
            if math.hypot(x - 7.5, y - 7.5) < 3:
                put(pad, x, y, (30, 30, 34))
    save(pad, BLOCK, "vertical_launching_silo_pad")

    light = new((90, 40, 36))
    save(light, BLOCK, "vertical_launching_silo_light")
    light_on = new((255, 90, 70))
    for y in range(4, 12):
        for x in range(4, 12):
            put(light_on, x, y, (255, 200, 170))
    save(light_on, BLOCK, "vertical_launching_silo_light_on")

    body = new(WHITE)
    noise(body, rng, 0.03)
    for y in range(N):
        put(body, 0, y, (250, 252, 255))
        put(body, N - 1, y, (180, 186, 196))
        if y in (4, 11):
            for x in range(N):
                put(body, x, y, (190, 196, 206))
    for y in range(6, 10):
        for x in range(6, 10):
            put(body, x, y, (60, 120, 200))
    save(body, BLOCK, "small_carrier_rocket_body")

    trim = new(ROCKET_RED)
    noise(trim, rng, 0.05)
    for y in range(N):
        put(trim, 0, y, shade(ROCKET_RED, 1.25))
        put(trim, N - 1, y, shade(ROCKET_RED, 0.7))
    save(trim, BLOCK, "small_carrier_rocket_trim")

    flame = new((255, 200, 90))
    for y in range(N):
        for x in range(N):
            put(flame, x, y, mix((255, 255, 230), (255, 120, 40), (abs(x - 7.5) / 7.5 + y / 16) / 1.6))
    save(flame, BLOCK, "rocket_flame")


def ray_receiver():
    rng = random.Random(403)
    base = casing(DARK, rng, seam=False)
    for x in range(3, 13):
        put(base, x, 11, CYAN_DIM)
        put(base, x, 12, shade(CYAN_DIM, 0.7))
    save(base, BLOCK, "ray_receiver_base")
    top = casing(MID, rng, seam=False)
    save(top, BLOCK, "ray_receiver_top")

    dish = new((200, 214, 230))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            ring = int(d * 1.3) % 2
            c = mix((250, 252, 255), (150, 176, 210), d / 10)
            put(dish, x, y, shade(c, 1.0 if ring else 0.9))
    for i in range(N):
        put(dish, i, i, (230, 240, 255))
    save(dish, BLOCK, "ray_receiver_dish")

    back = new((120, 128, 140))
    noise(back, rng, 0.05)
    for i in range(0, N, 5):
        for x in range(N):
            put(back, x, i, (96, 102, 112))
            put(back, i, x, (96, 102, 112))
    save(back, BLOCK, "ray_receiver_back")

    core = new(PHOTON)
    for y in range(N):
        for x in range(N):
            put(core, x, y, mix((255, 255, 255), (255, 200, 90), math.hypot(x - 7.5, y - 7.5) / 10))
    save(core, BLOCK, "ray_receiver_core")


def miniature_particle_collider():
    """微型粒子对撞机：深色机壳，正面一圈环形的加速管，工作时发紫光。"""
    rng = random.Random(404)
    side = casing((60, 56, 76), rng)
    save(side, BLOCK, "miniature_particle_collider_side")
    top = casing((84, 80, 100), rng, seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.5 < d < 6.2:
                put(top, x, y, (110, 100, 140))
    save(top, BLOCK, "miniature_particle_collider_top")
    for name, lit in (("miniature_particle_collider_front", False), ("miniature_particle_collider_front_on", True)):
        img = casing((60, 56, 76), random.Random(405), seam=False)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.2 < d < 6.3:
                    if lit:
                        a = math.atan2(y - 7.5, x - 7.5)
                        put(img, x, y, mix((255, 230, 255), (170, 90, 255), (math.sin(a * 2) + 1) / 2))
                    else:
                        put(img, x, y, (96, 88, 120))
                elif d < 2:
                    put(img, x, y, (255, 255, 255) if lit else (70, 64, 90))
        save(img, BLOCK, name)


def main():
    photon_combiner()
    solar_sail()
    frame_material()
    dyson_sphere_component()
    small_carrier_rocket()
    deuteron_fuel_rod()
    titanium_glass()
    casimir_crystal()
    plane_filter()
    quantum_chip()
    graviton_lens()
    critical_photon()
    matrix("gravity_matrix", (60, 200, 90))
    em_rail_ejector()
    vertical_launching_silo()
    ray_receiver()
    miniature_particle_collider()


if __name__ == "__main__":
    main()
