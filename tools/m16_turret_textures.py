"""生成炮塔和弹药的贴图（ADR-0028 §五）。

用法：python tools/m16_turret_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数（方块、物品）和 tools/combat_textures.py 的 Skin（实体），
随机数用固定种子，每次生成的结果一样。

炮塔是军用的枪灰色涂装，底座四边一圈黄黑警示条，和工厂的设备一眼分得开：
高斯炮塔的线圈舱是铜色的；激光炮塔的壳子是白色的，透镜发青光；聚爆炮是厚重的橄榄色装甲；
导弹炮塔的发射箱正面四个发射管，顶上一盏红灯。
弹药：子弹装在弹箱里（机枪弹箱是橄榄色铁皮箱、黄铜弹头，钛化弹箱是钢蓝色、银色弹头，超合金弹箱是深紫灰、金色弹头）；
炮弹组是三发并排的炮弹（普通的灰弹头，高爆的弹头一道红环，晶石的弹头是紫色晶体）；
导弹组是两枚斜放的导弹（普通的白弹体红弹头，超音速的细长、橙色尾翼，引力的黑弹体、弹头发紫光）。
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import (BLOCK, ITEM, N, shade, mix, clear, new, put, rect, outline, save, noise, frame)  # noqa: E402
from combat_textures import Skin  # noqa: E402

ENTITY = os.path.join(os.path.dirname(BLOCK), "entity")

GUNMETAL = (70, 76, 82)
DARK = (42, 46, 51)
YELLOW = (214, 178, 52)
BLACK = (30, 31, 34)
COPPER = (190, 110, 58)
WHITE = (214, 218, 220)
CYAN = (110, 230, 255)
OLIVE = (88, 96, 66)
RED = (200, 56, 44)
BRASS = (204, 164, 78)


def plate(base, rng, seam=True, rivets=True, amount=0.04):
    img = new(base)
    noise(img, rng, amount)
    frame(img, shade(base, 1.22), shade(base, 0.62))
    if seam:
        for x in range(1, N - 1):
            put(img, x, 7, shade(base, 0.76))
            put(img, x, 8, shade(base, 1.08))
    if rivets:
        for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
            put(img, x, y, shade(base, 1.4))
            put(img, x + 1, y + 1, shade(base, 0.6))
    return img


def base_textures(rng):
    # 底座侧面：上半是机壳，下面一圈黄黑警示条
    img = plate(GUNMETAL, rng, seam=False)
    for y in range(10, 14):
        for x in range(1, N - 1):
            put(img, x, y, YELLOW if (x + y) // 2 % 2 == 0 else BLACK)
    for x in range(1, N - 1):
        put(img, x, 9, shade(GUNMETAL, 0.6))
        put(img, x, 14, shade(GUNMETAL, 0.6))
    save(img, BLOCK, "turret_base")
    # 底座顶面：四角螺栓，中间一圈转盘的槽
    img = plate(shade(GUNMETAL, 0.95), rng, seam=False, rivets=False)
    for i in range(3, 13):
        for j in (3, 12):
            put(img, i, j, shade(GUNMETAL, 0.6))
            put(img, j, i, shade(GUNMETAL, 0.6))
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, shade(GUNMETAL, 1.5))
    save(img, BLOCK, "turret_base_top")
    # 转盘：深色金属，一圈刻度
    img = plate(DARK, rng, seam=False, rivets=False, amount=0.03)
    for x in range(0, N, 3):
        put(img, x, 1, shade(DARK, 1.6))
    save(img, BLOCK, "turret_ring")


def barrel(base, rng, ring=None):
    img = new(base)
    noise(img, rng, 0.03)
    # 竖着的高光和阴影，看起来是圆管
    for y in range(N):
        put(img, 5, y, shade(base, 1.3))
        put(img, 6, y, shade(base, 1.15))
        put(img, 11, y, shade(base, 0.7))
        put(img, 12, y, shade(base, 0.6))
    if ring:
        for y in (3, 4, 11, 12):
            for x in range(N):
                put(img, x, y, ring)
    return img


def gauss(rng):
    save(plate(GUNMETAL, rng), BLOCK, "gauss_turret_housing")
    save(barrel(shade(DARK, 0.9), rng), BLOCK, "gauss_turret_barrel")
    # 线圈舱、制退器：铜色线圈一圈圈绕着
    img = new(COPPER)
    noise(img, rng, 0.05)
    for y in range(0, N, 2):
        for x in range(N):
            put(img, x, y, shade(COPPER, 0.72))
    frame(img, shade(COPPER, 1.2), shade(COPPER, 0.5))
    save(img, BLOCK, "gauss_turret_accent")


def laser(rng):
    img = plate(WHITE, rng)
    for x in range(3, 13):
        put(img, x, 11, CYAN)
    save(img, BLOCK, "laser_turret_housing")
    save(barrel(shade(WHITE, 0.82), rng, ring=shade(DARK, 1.1)), BLOCK, "laser_turret_barrel")
    img = plate(shade(DARK, 1.2), rng, seam=False, rivets=False)
    for y in range(2, N - 2, 3):
        for x in range(1, N - 1):
            put(img, x, y, shade(DARK, 0.7))
    save(img, BLOCK, "laser_turret_accent")
    # 透镜：中间白、往外青色
    img = new(CYAN)
    for y in range(N):
        for x in range(N):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            put(img, x, y, mix((240, 255, 255), CYAN, min(1, d / 8)))
    save(img, BLOCK, "laser_turret_lens")


def implosion(rng):
    img = plate(OLIVE, rng)
    # 装甲板的斜接缝
    for i in range(3, 13):
        put(img, i, 15 - i, shade(OLIVE, 0.7))
    save(img, BLOCK, "implosion_cannon_housing")
    save(barrel(shade(OLIVE, 0.7), rng, ring=shade(OLIVE, 0.45)), BLOCK, "implosion_cannon_barrel")
    img = plate(shade(DARK, 1.1), rng, seam=False)
    for x in range(2, 14):
        put(img, x, 5, YELLOW if x % 4 < 2 else BLACK)
        put(img, x, 10, YELLOW if x % 4 < 2 else BLACK)
    save(img, BLOCK, "implosion_cannon_accent")


def missile(rng):
    save(plate(shade(GUNMETAL, 1.15), rng), BLOCK, "missile_turret_housing")
    img = plate(shade(GUNMETAL, 1.05), rng, seam=False)
    for x in range(2, 14):
        put(img, x, 13, YELLOW if x % 4 < 2 else BLACK)
    save(img, BLOCK, "missile_turret_barrel")
    save(plate(DARK, rng, seam=False), BLOCK, "missile_turret_accent")
    # 发射箱正面：四个发射管，管里看得到红色的弹头
    img = plate(shade(GUNMETAL, 0.9), rng, seam=False, rivets=False)
    for (cx, cy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        for y in range(cy - 2, cy + 2):
            for x in range(cx - 2, cx + 2):
                put(img, x, y, BLACK)
        put(img, cx - 1, cy - 1, RED)
        put(img, cx, cy - 1, shade(RED, 0.8))
        put(img, cx - 1, cy, shade(RED, 0.7))
        put(img, cx, cy, shade(RED, 0.6))
    save(img, BLOCK, "missile_turret_pod_front")
    img = new((255, 80, 60))
    frame(img, (255, 160, 140), (170, 40, 30))
    save(img, BLOCK, "missile_turret_lamp")


# ---- 弹药

def ammo_box(name, body, tip, rng):
    img = clear()
    # 箱子：x 2–13，y 6–14；盖子 y 5
    rect(img, 2, 6, 13, 14, body)
    rect(img, 2, 5, 13, 5, shade(body, 1.25))
    for x in range(2, 14):
        put(img, x, 14, shade(body, 0.7))
    for y in range(6, 15):
        put(img, 2, y, shade(body, 1.15))
        put(img, 13, y, shade(body, 0.75))
    # 箱子上的提手和标记
    rect(img, 6, 9, 9, 10, shade(body, 0.6))
    put(img, 4, 8, YELLOW)
    put(img, 11, 8, YELLOW)
    # 露出盖子的子弹
    for x in (4, 6, 8, 10):
        put(img, x, 4, tip)
        put(img, x, 3, shade(tip, 1.2))
        put(img, x + 1, 4, shade(tip, 0.75))
    noise(img, rng, 0.04)
    outline(img, (24, 24, 26))
    save(img, ITEM, name)


def shell(img, x0, head, band=None, crystal=False):
    """一发竖着的炮弹：底下黄铜弹壳，上面弹头。"""
    rect(img, x0, 9, x0 + 2, 14, BRASS)
    for y in range(9, 15):
        put(img, x0, y, shade(BRASS, 1.2))
        put(img, x0 + 2, y, shade(BRASS, 0.75))
    rect(img, x0, 5, x0 + 2, 8, head)
    put(img, x0 + 1, 4, head)
    put(img, x0 + 1, 3, shade(head, 1.3))
    if band:
        rect(img, x0, 7, x0 + 2, 7, band)
    if crystal:
        put(img, x0 + 1, 2, (230, 200, 255))
        put(img, x0, 4, shade(head, 1.2))
        put(img, x0 + 2, 4, shade(head, 1.2))
    put(img, x0, 14, shade(BRASS, 0.6))


def shell_set(name, head, band, crystal, rng):
    img = clear()
    for x0 in (2, 6, 10):
        shell(img, x0, head, band, crystal)
    noise(img, rng, 0.03)
    outline(img, (24, 24, 26))
    save(img, ITEM, name)


def missile_icon(img, x0, y0, body, tip, fin, slim=False):
    """一枚从左下往右上斜放的导弹：九格长。"""
    for i in range(9):
        x, y = x0 + i, y0 - i
        put(img, x, y, body)
        if not slim:
            put(img, x + 1, y, shade(body, 0.75))
    put(img, x0 + 9, y0 - 9, tip)
    put(img, x0 + 8, y0 - 8, tip)
    if not slim:
        put(img, x0 + 9, y0 - 8, shade(tip, 0.7))
    # 尾翼
    put(img, x0 - 1, y0, fin)
    put(img, x0, y0 + 1, fin)
    put(img, x0 - 1, y0 + 1, shade(fin, 0.8))


def missile_set(name, body, tip, fin, slim, glow_tip, rng):
    img = clear()
    missile_icon(img, 2, 11, body, tip, fin, slim)
    missile_icon(img, 4, 14, body, tip, fin, slim)
    if glow_tip:
        for (x, y) in ((11, 1), (13, 4)):
            put(img, x, y, (220, 160, 255))
    noise(img, rng, 0.03)
    outline(img, (24, 24, 26))
    save(img, ITEM, name)


def items(rng):
    ammo_box("magnum_ammo_box", (96, 104, 70), BRASS, rng)
    ammo_box("titanium_ammo_box", (88, 108, 128), (200, 206, 214), rng)
    ammo_box("superalloy_ammo_box", (82, 72, 96), (226, 190, 80), rng)
    shell_set("shell_set", (130, 134, 140), None, False, rng)
    shell_set("high_explosive_shell_set", (120, 124, 128), RED, False, rng)
    shell_set("crystal_shell_set", (150, 100, 220), None, True, rng)
    missile_set("missile_set", (226, 228, 230), RED, (120, 124, 130), False, False, rng)
    missile_set("supersonic_missile_set", (200, 204, 208), RED, (236, 140, 40), True, False, rng)
    missile_set("gravity_missile_set", (52, 50, 60), (170, 100, 255), (90, 80, 110), False, True, rng)


# ---- 实体

def shell_entity():
    """炮弹 32×32：弹体 (0,0) 3×3×4 黄铜色，弹头 (0,8) 2×2×1 灰色，尾部引信环 (0,12) 2×2×1 发光。"""
    s = Skin(32, 32, 16)
    s.box(0, 0, 3, 3, 4, BRASS, 0.05)
    s.box(0, 8, 2, 2, 1, (130, 134, 140), 0.04)
    fuse = s.box(0, 12, 2, 2, 1, (255, 170, 80), 0.02)
    for x0, y0, w, h in fuse.values():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                s.light(x, y, 0.9)
    save(s.img, ENTITY, "turret_shell")
    save(s.glow, ENTITY, "turret_shell_glow")


def missile_entity():
    """导弹 32×32：弹体 (0,0) 2×2×10 白色带一道黑环，弹头 (0,14) 1×1×2 红色，喷口 (8,14) 1×1×1 发光，
    尾翼 (0,18) 5×1×2、(14,18) 1×5×2 灰色。"""
    s = Skin(32, 32, 17)
    body = s.box(0, 0, 2, 2, 10, (226, 228, 230), 0.03)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = body[key]
        if w > h:
            s.vline(x0 + 7, y0, y0 + h, (40, 40, 44))
        else:
            s.hline(x0, x0 + w, y0 + 7, (40, 40, 44))
    s.box(0, 14, 1, 1, 2, RED, 0.03)
    nozzle = s.box(8, 14, 1, 1, 1, (70, 72, 78), 0.02)
    x0, y0, w, h = nozzle["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (255, 190, 90))
            s.light(x, y, 1.0)
    s.box(0, 18, 5, 1, 2, (120, 124, 130), 0.04)
    s.box(14, 18, 1, 5, 2, (120, 124, 130), 0.04)
    save(s.img, ENTITY, "turret_missile")
    save(s.glow, ENTITY, "turret_missile_glow")


def main():
    rng = random.Random(1628)
    base_textures(rng)
    gauss(rng)
    laser(rng)
    implosion(rng)
    missile(rng)
    items(rng)
    shell_entity()
    missile_entity()
    print("turret textures written")


if __name__ == "__main__":
    main()
