"""生成等离子炮塔、近程等离子炮塔和两种胶囊的贴图（ADR-0028 §八）。

用法：python tools/m16_plasma_textures.py
画法沿用 tools/m16_turret_textures.py：底座共用那边画好的 turret_base 等贴图，这里只画转台、炮管和胶囊。
随机数用固定种子，每次生成的结果一样。

等离子炮塔是深蓝灰的壳子，磁环上一道青光，炮口的约束环发青白光；
近程等离子炮塔是紫灰色的壳子，两条导轨中间的线圈发紫光。
胶囊斜放：两头金属帽，中间玻璃管里是一团发光的等离子体（等离子胶囊青色，反物质胶囊品红色、帽上一道红环）。
炮塔打出去的等离子团用 tools/combat_textures.py 画的 entity/plasma_bolt（白色光斑），颜色由渲染器按弹种染，这里不重画。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, ITEM, N, shade, mix, clear, new, put, save, noise, outline  # noqa: E402
from m16_turret_textures import plate, barrel, DARK, YELLOW, BLACK, RED  # noqa: E402

NAVY = (58, 68, 90)
PLASMA = (110, 216, 255)
VIOLET_STEEL = (82, 74, 98)
VIOLET = (176, 112, 255)
STEEL = (150, 156, 164)
MAGENTA = (255, 90, 210)


def glow_disc(core, edge):
    """一块中间白、往外渐变成 edge 色的发光面。"""
    img = new(edge)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            put(img, x, y, mix(core, edge, min(1, d / 8)))
    return img


def plasma(rng):
    img = plate(NAVY, rng)
    for x in range(3, 13):
        put(img, x, 11, shade(PLASMA, 0.8))
    save(img, BLOCK, "plasma_turret_housing")
    save(barrel(shade(DARK, 0.95), rng), BLOCK, "plasma_turret_barrel")
    # 磁环、散热片：深色金属上一道青色的发光带
    img = plate(shade(DARK, 1.05), rng, seam=False, rivets=False)
    for x in range(1, N - 1):
        put(img, x, 7, PLASMA)
        put(img, x, 8, shade(PLASMA, 0.7))
    save(img, BLOCK, "plasma_turret_accent")
    save(glow_disc((240, 255, 255), PLASMA), BLOCK, "plasma_turret_glow")


def sr_plasma(rng):
    img = plate(VIOLET_STEEL, rng)
    for x in range(2, 14):
        put(img, x, 13, YELLOW if x % 4 < 2 else BLACK)
    save(img, BLOCK, "sr_plasma_turret_housing")
    save(barrel(shade(DARK, 1.1), rng, ring=shade(VIOLET_STEEL, 0.6)), BLOCK, "sr_plasma_turret_barrel")
    img = plate(shade(DARK, 0.95), rng, seam=False)
    for x in range(3, 13, 3):
        put(img, x, 7, VIOLET)
        put(img, x, 8, shade(VIOLET, 0.6))
    save(img, BLOCK, "sr_plasma_turret_accent")
    # 线圈：一道道紫色绕线，中间亮
    img = new(VIOLET)
    for y in range(N):
        for x in range(N):
            c = mix((250, 230, 255), VIOLET, min(1, abs(y - 7.5) / 7))
            if x % 3 == 0:
                c = shade(c, 0.7)
            put(img, x, y, c)
    save(img, BLOCK, "sr_plasma_turret_glow")


# ---- 胶囊

def capsule(name, core, cap, band, rng):
    """一颗从左下到右上斜放的胶囊。"""
    img = clear()
    r = 1 / math.sqrt(2)
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - 8, y + 0.5 - 8
            # t 沿轴线（往右上为正），s 是到轴线的有符号距离，正的一侧朝左上，当高光面
            t = (px - py) * r
            s = -(px + py) * r
            if abs(t) > 6.6 or abs(s) > 2.7:
                continue
            if abs(t) > 6.0 and abs(s) > 1.6:
                continue
            light = 1.25 if s > 1.2 else 0.72 if s < -1.2 else 1.0
            if abs(t) > 4.3:
                c = shade(cap, light)
                if band and 4.3 < abs(t) <= 5.0:
                    c = shade(band, light)
            else:
                glow = max(0.0, 1 - abs(s) / 2.4) * (1 - abs(t) / 6)
                c = mix(shade(core, 0.55), mix(core, (255, 255, 255), 0.6), glow * 1.4)
                if s > 1.6:
                    c = mix(c, (220, 240, 255), 0.5)
            put(img, x, y, c)
    noise(img, rng, 0.03)
    outline(img, (24, 24, 26))
    save(img, ITEM, name)


def items(rng):
    capsule("plasma_capsule", PLASMA, STEEL, None, rng)
    capsule("antimatter_capsule", MAGENTA, shade(DARK, 1.3), RED, rng)


def main():
    rng = random.Random(1629)
    plasma(rng)
    sr_plasma(rng)
    items(rng)
    print("plasma textures written")


if __name__ == "__main__":
    main()
