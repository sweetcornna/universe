"""生成 M14（ADR-0026）营养处理器做的半成品和菜的物品贴图，还有便携精炼机做的四种防护凝胶。

用法：python tools/m14_food_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

几种器皿各画一次，按菜换颜色：果酱和酱是带盖的玻璃罐，汁是一杯，汤是一碗，卡士达是一个小烤盅，
果冻是一块半透明的方冻，馅饼是一个圆派（顶上格子皮或者整张皮），挞是一个小挞，蛋糕是一角切开的层蛋糕，
肉是一块浇了酱的烤肉，冰淇淋是一个蛋卷筒。防护凝胶是一支小管，管里的凝胶按危害的颜色：寒冷蓝、高温橙、辐射黄绿、毒性紫。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import ITEM, N, shade, mix, clear, put, outline, save  # noqa: E402

WHITE = (240, 240, 236)
CRUST = (214, 160, 88)
GLASS = (196, 222, 230)


def blob(img, cx, cy, rx, ry, color, a=255, light=0.18):
    for y in range(N):
        for x in range(N):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            if dx * dx + dy * dy <= 1:
                put(img, x, y, shade(color, 1 + light * (-dx - dy) / 2), a)


def rect(img, x0, y0, x1, y1, color, a=255):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, color, a)


def sprinkle(img, rng, color, count, region=(0, 0, 15, 15)):
    x0, y0, x1, y1 = region
    for _ in range(count):
        x, y = rng.randint(x0, x1), rng.randint(y0, y1)
        if img.getpixel((x, y))[3]:
            put(img, x, y, color)


# ---- 器皿

def heap(color, rng):
    img = clear()
    for y in range(7, 15):
        half = (y - 6) * 0.9
        for x in range(round(7.5 - half), round(7.5 + half) + 1):
            put(img, x, y, shade(color, 1.08 - (y - 7) * 0.02 + rng.uniform(-0.04, 0.04)))
    return img


def jar(content, rng, lid=(150, 104, 60)):
    img = clear()
    rect(img, 4, 5, 11, 14, GLASS, 170)
    rect(img, 5, 7, 10, 13, content)
    for y in range(7, 14):
        put(img, 5, y, mix(content, WHITE, 0.35))
    rect(img, 4, 3, 11, 4, lid)
    rect(img, 5, 2, 10, 2, shade(lid, 1.2))
    put(img, 9, 8, mix(content, WHITE, 0.6))
    return img


def cup(content, rng):
    img = clear()
    for y in range(4, 15):
        w = 3 if y < 13 else 2
        for x in range(8 - w - 1, 8 + w + 1):
            put(img, x, y, GLASS, 150)
    for y in range(6, 14):
        w = 3 if y < 13 else 2
        for x in range(8 - w, 8 + w):
            put(img, x, y, shade(content, 1 - (y - 6) * 0.02))
    put(img, 6, 7, mix(content, WHITE, 0.5))
    rect(img, 9, 1, 9, 6, (230, 80, 80))  # 吸管
    return img


def bowl(content, rng, bits=None):
    img = clear()
    for y in range(8, 15):
        half = 7 - (y - 8) * 0.8 if y > 9 else 7
        for x in range(round(7.5 - half), round(7.5 + half) + 1):
            put(img, x, y, shade((176, 132, 92), 1.1 - (y - 8) * 0.04))
    blob(img, 7.5, 8.5, 6.5, 1.6, content, light=0.1)
    if bits:
        sprinkle(img, rng, bits, 5, (3, 7, 12, 9))
    return img


def ramekin(top, rng, jam=None):
    img = clear()
    rect(img, 3, 9, 12, 14, (236, 236, 230))
    for x in range(3, 13, 2):
        put(img, x, 12, (210, 210, 204))
    blob(img, 7.5, 8.5, 5, 2, top)
    if jam:
        blob(img, 7.5, 8, 2.5, 1.2, jam)
    put(img, 5, 8, mix(top, WHITE, 0.5))
    return img


def jelly(color, rng):
    img = clear()
    for y in range(5, 14):
        wobble = round(math.sin(y * 0.9) * 0.6)
        for x in range(3 + wobble, 13 + wobble):
            put(img, x, y, shade(color, 1.15 - (y - 5) * 0.04 - (x - 3) * 0.015), 215)
    rect(img, 2, 14, 13, 14, (220, 220, 214))
    put(img, 5, 6, mix(color, WHITE, 0.7), 240)
    put(img, 6, 7, mix(color, WHITE, 0.5), 240)
    return img


def pie(filling, rng, lattice=True):
    img = clear()
    blob(img, 7.5, 9.5, 7, 4.8, shade(CRUST, 0.9))
    blob(img, 7.5, 9, 5.6, 3.6, filling)
    if lattice:
        for x in range(3, 13, 3):
            for y in range(6, 13):
                if img.getpixel((x, y))[3] and abs((x - 7.5) / 5.6) ** 2 + abs((y - 9) / 3.6) ** 2 <= 1:
                    put(img, x, y, CRUST)
        for y in (7, 10):
            for x in range(3, 13):
                if abs((x - 7.5) / 5.6) ** 2 + abs((y - 9) / 3.6) ** 2 <= 1:
                    put(img, x, y, shade(CRUST, 1.08))
    else:
        blob(img, 7.5, 9, 5.6, 3.6, CRUST)
        for x in (6, 8, 10):
            put(img, x, 9, shade(filling, 0.9))
    return img


def tart(filling, rng):
    img = clear()
    blob(img, 7.5, 10, 6, 3.5, shade(CRUST, 0.95))
    blob(img, 7.5, 9.5, 4.6, 2.4, filling)
    put(img, 6, 9, mix(filling, WHITE, 0.5))
    return img


def cake(layer, frosting, rng, top=None):
    img = clear()
    # 一角切开的蛋糕：左边是切面露出夹层，右边是侧面
    for y in range(5, 15):
        for x in range(2, 14):
            if x - 2 > (14 - y) * 1.3 + 4:
                continue
            band = (y - 5) % 4
            c = frosting if y < 7 else (layer if band else shade(frosting, 0.95))
            put(img, x, y, shade(c, 1 - (x - 2) * 0.012))
    for x in range(2, 10):
        put(img, x, 4, shade(frosting, 1.1))
    if top:
        for x in (4, 7):
            blob(img, x, 3.5, 1.2, 1.2, top)
    return img


def meat(sauce, rng):
    img = clear()
    blob(img, 7.5, 9, 6, 4, (150, 84, 60))
    for x in range(3, 13, 2):
        put(img, x, 8 + (x % 3), (190, 120, 90))  # 烤出的纹
    blob(img, 7, 7.5, 4, 1.6, sauce, 230)
    sprinkle(img, rng, shade(sauce, 1.3), 3, (4, 6, 11, 8))
    return img


def ice_cream(scoop, rng):
    img = clear()
    for y in range(8, 15):
        half = (15 - y) * 0.45
        for x in range(round(7.5 - half), round(7.5 + half) + 1):
            put(img, x, y, (206, 156, 86) if (x + y) % 3 else (176, 126, 66))
    blob(img, 7.5, 6, 4.2, 3.4, scoop)
    put(img, 6, 4, mix(scoop, WHITE, 0.7))
    sprinkle(img, rng, (232, 246, 255), 3, (4, 3, 11, 8))
    return img


def candy(color, rng):
    img = clear()
    blob(img, 7.5, 6, 5.5, 4.5, color, 240, light=0.1)
    sprinkle(img, rng, mix(color, WHITE, 0.5), 10, (2, 2, 13, 10))
    for y in range(10, 16):
        put(img, 7, y, (224, 214, 190))
    return img


def ball(color, rng, spots=None):
    img = clear()
    blob(img, 7.5, 8.5, 5, 5, color)
    if spots:
        sprinkle(img, rng, spots, 7, (3, 4, 12, 13))
    return img


def tube(gel, rng):
    img = clear()
    rect(img, 5, 3, 10, 13, (220, 224, 228))
    rect(img, 6, 5, 9, 12, gel)
    for y in range(5, 13):
        put(img, 6, y, mix(gel, WHITE, 0.4))
    rect(img, 6, 1, 9, 2, (70, 76, 84))
    rect(img, 5, 14, 10, 14, (170, 176, 182))
    return img


# ---- 每一样

FOODS = {
    "flour": lambda r: heap((236, 230, 214), r),
    "dough": lambda r: ball((226, 206, 160), r),
    "butter": lambda r: butter(r),
    "pastry": lambda r: pastry(r),
    "cake_base": lambda r: cake_base(r),
    "star_jam": lambda r: jar((222, 168, 52), r),
    "solar_sauce": lambda r: jar((200, 56, 30), r),
    "mould_paste": lambda r: jar((112, 80, 58), r),
    "cactus_juice": lambda r: cup((146, 196, 90), r),
    "frost_ice_cream": lambda r: ice_cream((190, 222, 240), r),
    "root_soup": lambda r: bowl((176, 196, 76), r, (220, 240, 120)),
    "roasted_marrow": lambda r: ball((206, 168, 100), r, (140, 98, 52)),
    "gravitino_jelly": lambda r: jelly((226, 100, 214), r),
    "custard": lambda r: ramekin((240, 210, 110), r),
    "fluff_candy": lambda r: candy((240, 190, 214), r),
    "star_pie": lambda r: pie((214, 150, 40), r),
    "meat_pie": lambda r: pie((140, 82, 58), r, lattice=False),
    "spicy_roast": lambda r: meat((210, 64, 32), r),
    "braised_meat": lambda r: meat((96, 58, 40), r),
    "alien_cake": lambda r: cake((238, 214, 150), (246, 240, 226), r),
    "star_custard": lambda r: ramekin((240, 210, 110), r, jam=(226, 166, 48)),
    "egg_tart": lambda r: tart((246, 204, 86), r),
    "frost_tart": lambda r: tart((196, 226, 242), r),
    "solar_pie": lambda r: pie((210, 72, 36), r),
    "cactus_jelly": lambda r: jelly((150, 200, 96), r),
    "gravitino_pastry": lambda r: gravitino_pastry(r),
    "marrow_soup": lambda r: bowl((228, 214, 172), r, (200, 160, 100)),
    "fish_pie": lambda r: pie((226, 212, 196), r, lattice=False),
    "star_cake": lambda r: cake((238, 214, 150), (246, 240, 226), r, top=(240, 214, 90)),
}

GELS = {
    "cold_gel": (110, 176, 240),
    "heat_gel": (240, 140, 52),
    "radiation_gel": (190, 226, 70),
    "toxin_gel": (150, 96, 196),
}


def butter(rng):
    img = clear()
    rect(img, 2, 9, 13, 13, (236, 232, 220))  # 包装纸
    rect(img, 3, 6, 12, 11, (246, 222, 120))
    for x in range(3, 13):
        put(img, x, 6, (252, 236, 160))
    for y in range(6, 12):
        put(img, 12, y, (214, 186, 90))
    return img


def pastry(rng):
    img = clear()
    for i, y in enumerate(range(7, 14)):
        c = CRUST if i % 2 == 0 else shade(CRUST, 1.2)
        rect(img, 2 + (i % 2), y, 13 - (i % 2), y, c)
    return img


def cake_base(rng):
    img = clear()
    blob(img, 7.5, 10, 6.5, 4.5, (232, 200, 120))
    blob(img, 7.5, 8, 6.5, 2.5, (244, 220, 150))
    sprinkle(img, rng, (210, 176, 96), 6, (2, 8, 13, 14))
    return img


def gravitino_pastry(rng):
    img = pastry(rng)
    blob(img, 7.5, 6.5, 3, 2.2, (226, 100, 214))
    put(img, 6, 6, (255, 206, 250))
    return img


def main():
    for name, draw in FOODS.items():
        img = draw(random.Random(sum(map(ord, name))))
        outline(img, (40, 32, 30))
        save(img, ITEM, name)
    for name, color in GELS.items():
        img = tube(color, random.Random(sum(map(ord, name))))
        outline(img, (34, 30, 36))
        save(img, ITEM, name)


if __name__ == "__main__":
    main()
