"""生成 M12（ADR-0023）的贴图：物流配送器、配送机器人、建造无人机（方块贴图给立体模型用，物品贴图是界面里的平面图标）。

用法：python tools/m12_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数。配送器的模型用原版的自动 UV，贴图上只有模型用到的那几行（列）才看得见：
底座侧面是最下面四行，立柱是第 6–11 行的第 4–11 列，坪的侧面是第 4、5 行。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_textures as ft  # noqa: E402
from factory_textures import (BLOCK, ITEM, N, DARK, CYAN, CYAN_DIM, WHITE, shade, mix, clear, put, rect, outline, save,  # noqa: E402
                              casing)

HULL = (214, 220, 226)
AMBER = (240, 186, 60)
WELD = (255, 150, 60)


def distributor():
    rng = random.Random(1201)
    # 底座：白色机壳，下面四行一道深色的槽，槽里一排青色的小灯
    base = casing(HULL, rng, seam=False)
    for x in range(N):
        put(base, x, 12, shade(HULL, 1.08))
        put(base, x, 13, (70, 78, 88))
        put(base, x, 14, (52, 58, 66))
        put(base, x, 15, shade(HULL, 0.62))
    for x in (3, 6, 9, 12):
        put(base, x, 13, CYAN)
        put(base, x, 14, CYAN_DIM)
    save(base, BLOCK, "logistics_distributor_base")
    top = casing(shade(HULL, 0.8), rng, seam=False)
    ft.frame(top, shade(HULL, 0.95), shade(HULL, 0.6))
    save(top, BLOCK, "logistics_distributor_top")
    # 立柱：深色，竖着的散热筋，中间一道灯带
    for suffix, lit in (("", False), ("_on", True)):
        col = casing(DARK, random.Random(1202), seam=False)
        for y in range(N):
            for x in range(N):
                if x % 3 == 1:
                    put(col, x, y, shade(DARK, 0.75))
        for x in range(N):
            put(col, x, 8, CYAN if lit else CYAN_DIM)
            put(col, x, 9, mix(CYAN, WHITE, 0.4) if lit else shade(CYAN_DIM, 0.8))
            put(col, x, 6, shade(HULL, 0.9))
            put(col, x, 11, shade(HULL, 0.75))
        save(col, BLOCK, "logistics_distributor_column" + suffix)
    # 停机坪：深灰的坪面，一圈白色的降落环，四角黄黑警示
    pad = casing((70, 76, 84), random.Random(1203), seam=False)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 <= d < 5.8:
                put(pad, x, y, WHITE if (x + y) % 5 else shade(WHITE, 0.85))
            elif d < 2.2:
                put(pad, x, y, (52, 58, 66))
    for (x0, y0, dx, dy) in ((0, 0, 1, 1), (15, 0, -1, 1), (0, 15, 1, -1), (15, 15, -1, -1)):
        for i in range(3):
            for j in range(3 - i):
                put(pad, x0 + dx * i, y0 + dy * j, AMBER if (i + j) % 2 == 0 else (40, 40, 44))
    ft.frame(pad, shade(HULL, 0.95), shade(HULL, 0.55))
    save(pad, BLOCK, "logistics_distributor_pad")
    side = casing(HULL, random.Random(1204), seam=False)
    for x in range(N):
        put(side, x, 4, shade(HULL, 1.1))
        put(side, x, 5, (60, 66, 74) if x % 4 == 0 else shade(HULL, 0.78))
    save(side, BLOCK, "logistics_distributor_pad_side")
    for suffix, lit in (("", False), ("_on", True)):
        img = ft.new(CYAN_DIM)
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5) / 10.6
                put(img, x, y, mix(WHITE, CYAN, d * 1.4) if lit else mix(shade(CYAN_DIM, 1.2), shade(CYAN_DIM, 0.7), d))
        save(img, BLOCK, "logistics_distributor_light" + suffix)


def bot_bodies():
    # 配送机器人：白色机壳，一道浅灰的接缝
    body = casing(HULL, random.Random(1210), seam=True)
    save(body, BLOCK, "logistics_bot_body")
    # 建造无人机：黄白两色，像工地上的东西
    drone = casing(HULL, random.Random(1211), seam=False)
    for y in range(N):
        for x in range(N):
            if (x + y) // 4 % 2 == 0:
                put(drone, x, y, shade(AMBER, 1 + random.Random(x * 31 + y).uniform(-0.04, 0.04)))
    save(drone, BLOCK, "construction_drone_body")
    light = ft.new(WELD)
    for y in range(N):
        for x in range(N):
            put(light, x, y, mix((255, 240, 200), WELD, math.hypot(x - 7.5, y - 7.5) / 8))
    save(light, BLOCK, "construction_drone_light")


def logistics_bot_icon():
    """侧面看的配送机器人：扁圆的白机身、腰上的青色灯带、两侧推进舱，下面夹着一个货箱。"""
    img = clear()
    for y in range(3, 9):
        for x in range(4, 12):
            w = 4 - (1 if y in (3, 8) else 0)
            if abs(x - 7.5) < w:
                put(img, x, y, shade(HULL, 1.12 if y < 5 else 0.92 if y > 6 else 1.0))
    for x in range(4, 12):
        put(img, x, 6, CYAN)
    rect(img, 1, 5, 3, 7, (120, 126, 134))
    rect(img, 12, 5, 14, 7, (120, 126, 134))
    put(img, 2, 8, CYAN)
    put(img, 13, 8, CYAN)
    put(img, 6, 9, (90, 94, 100))
    put(img, 9, 9, (90, 94, 100))
    rect(img, 5, 10, 10, 13, (176, 120, 66))
    rect(img, 5, 10, 10, 10, (206, 150, 90))
    for y in range(10, 14):
        put(img, 7, y, (136, 90, 50))
        put(img, 8, y, (136, 90, 50))
    outline(img, (32, 38, 46))
    save(img, ITEM, "logistics_bot")


def construction_drone_icon():
    """俯视的建造无人机：X 形机臂、四个旋翼、黄白机身，中间一盏橙灯。"""
    img = clear()
    for (cx, cy) in ((3.5, 3.5), (11.5, 3.5), (3.5, 11.5), (11.5, 11.5)):
        for y in range(N):
            for x in range(N):
                if math.hypot(x - cx, y - cy) < 2.6:
                    put(img, x, y, (120, 126, 134))
    for i in range(4, 12):
        put(img, i, i, (90, 94, 100))
        put(img, i, 15 - i, (90, 94, 100))
    rect(img, 5, 5, 10, 10, HULL)
    for y in range(5, 11):
        for x in range(5, 11):
            if (x + y) // 2 % 2 == 0:
                put(img, x, y, AMBER)
    rect(img, 7, 7, 8, 8, WELD)
    outline(img, (40, 44, 52))
    save(img, ITEM, "construction_drone")


def main():
    distributor()
    bot_bodies()
    logistics_bot_icon()
    construction_drone_icon()


if __name__ == "__main__":
    main()
