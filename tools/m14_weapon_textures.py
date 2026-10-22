"""生成 M14（ADR-0026 §六）多功能工具四种武器的物品贴图：散射爆能枪、脉冲喷射器、烈焰标枪、等离子发射器。

用法：python tools/m14_weapon_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，每次生成的结果一样。

四种都是装进多功能工具槽里的枪管组件，和多功能工具一样从左下斜到右上，灰色的金属管身，按武器的颜色点缀：
- 散射爆能枪：短而粗的枪管，枪口张开成一个方喇叭，喇叭口上三个黑孔，一道橙色的箍。
- 脉冲喷射器：细长的枪管，管身套着三道散热环，枪口一点黄绿的光。
- 烈焰标枪：最长最细的一根导轨，中段一圈橙红发光的线圈，枪尖烧得发白。
- 等离子发射器：粗短的炮管，炮口顶着一团紫色的等离子球。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import ITEM, N, shade, mix, clear, put, outline, save  # noqa: E402

STEEL = (150, 158, 166)
STEEL_DARK = (88, 96, 106)
STEEL_LIGHT = (206, 212, 218)
EDGE = (30, 32, 38)
HOLE = (24, 24, 28)


def segment(px, py, x0, y0, x1, y1):
    """点到线段的距离和投影位置（0 在起点，1 在终点）。"""
    dx, dy = x1 - x0, y1 - y0
    t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)))
    cx, cy = x0 + dx * t, y0 + dy * t
    return math.hypot(px - cx, py - cy), t


def barrel(img, x0, y0, x1, y1, radius, band=None, rings=()):
    """斜着的圆管：靠左上的一侧亮、右下的一侧暗；{@code band} 是 (起, 止, 颜色) 的一道箍，{@code rings} 是散热环的位置。"""
    for y in range(N):
        for x in range(N):
            cx, cy = x + 0.5, y + 0.5
            d, t = segment(cx, cy, x0, y0, x1, y1)
            if d > radius:
                continue
            # 管子的法线方向：左上为正
            nx, ny = (y1 - y0), -(x1 - x0)
            side = ((cx - x0) * nx + (cy - y0) * ny) / math.hypot(nx, ny)
            color = STEEL_LIGHT if side < -radius * 0.35 else STEEL if side < radius * 0.35 else STEEL_DARK
            if band and band[0] <= t <= band[1]:
                color = band[2] if side < radius * 0.35 else shade(band[2], 0.7)
            for ring in rings:
                if abs(t - ring) < 0.035:
                    color = mix(color, STEEL_LIGHT, 0.6)
            put(img, x, y, color)


def glow(img, cx, cy, radius, color):
    """一团光：中间发白，往外是武器的颜色。"""
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= radius:
                put(img, x, y, mix((255, 255, 255), color, min(1.0, d / radius + 0.2)))


def scatter_blaster():
    img = clear()
    orange = (255, 180, 90)
    barrel(img, 3.0, 13.0, 9.5, 6.5, 2.1, band=(0.45, 0.6, orange))
    # 张开的方喇叭
    for y in range(2, 8):
        for x in range(9, 15):
            if (x - 9) + (7 - y) >= 2:
                put(img, x, y, STEEL if (x + y) % 5 else STEEL_LIGHT)
    for hx, hy in ((12, 3), (13, 5), (11, 5)):
        put(img, hx, hy, HOLE)
    put(img, 12, 4, shade(orange, 0.9))
    # 握把
    for i in range(3):
        put(img, 5 - i, 13 + i // 2, STEEL_DARK)
    outline(img, EDGE)
    return img


def pulse_spitter():
    img = clear()
    green = (200, 255, 90)
    barrel(img, 2.0, 14.0, 13.0, 3.0, 1.45, rings=(0.3, 0.45, 0.6))
    glow(img, 13.6, 2.4, 1.6, green)
    for i in range(3):
        put(img, 4 - i, 13 + i // 2, STEEL_DARK)
    outline(img, EDGE)
    return img


def blaze_javelin():
    img = clear()
    flame = (255, 106, 42)
    barrel(img, 1.5, 14.5, 14.0, 2.0, 1.05, band=(0.42, 0.62, flame))
    # 枪尖烧得发白
    put(img, 14, 1, (255, 236, 200))
    put(img, 13, 2, (255, 200, 140))
    # 线圈外面一圈光
    for t in (0.45, 0.52, 0.59):
        x = 1.5 + (14.0 - 1.5) * t
        y = 14.5 + (2.0 - 14.5) * t
        put(img, int(x) + 1, int(y) + 1, mix(flame, (255, 230, 160), 0.4))
    outline(img, EDGE)
    return img


def plasma_launcher():
    img = clear()
    violet = (212, 106, 255)
    barrel(img, 2.5, 13.5, 9.0, 7.0, 2.6, band=(0.15, 0.27, shade(violet, 0.75)))
    glow(img, 11.3, 4.7, 3.0, violet)
    for i in range(3):
        put(img, 4 - i, 14 + i // 2, STEEL_DARK)
    outline(img, EDGE)
    return img


def main():
    save(scatter_blaster(), ITEM, "scatter_blaster")
    save(pulse_spitter(), ITEM, "pulse_spitter")
    save(blaze_javelin(), ITEM, "blaze_javelin")
    save(plasma_launcher(), ITEM, "plasma_launcher")


if __name__ == "__main__":
    main()
