"""全息信标和黑雾透镜的贴图（ADR-0031、ADR-0033 §六、§七）。

全息信标是一块矮的底座（14×3）、上面一圈投影头（8×2）、正中一片发光的透镜（5×1），模型见
models/block/holo_beacon.json：每张贴图只有模型用到的那几块要画好，注释里写着用的是哪块。
机壳照 parts.casing（深色外框、上左亮下右暗的倒角），灯带是界面那种青色，和基地信标一家人。
黑雾透镜照引力透镜的画法：镜框换成黑雾那种暗紫黑的金属，镜片里是暗红的雾，中心一个黑核。
"""
from __future__ import annotations

import math

from canvas import Canvas
from palette import (CRIMSON, CYAN_GLOW, FOG, GLASS, GRAPHITE, MAGENTA_GLOW, OIL, RED_GLOW, STEEL, TEAL,
                     WHITE_GLOW)
from parts import bolts, casing
from recipe import texture


# ---- 全息信标 ----

@texture("block/holo_beacon_top", refs=("block/beacon", "block/lodestone_top"))
def holo_beacon_top(rng):
    # 底座的顶面，用 1–14：正中 4–11 被投影头盖住，露出外圈、一圈青色的灯槽和四角的螺栓
    c = casing(rng, base=3)
    c.recess(3, 3, 12, 12, GRAPHITE, 3)
    c.panel(4, 4, 11, 11, STEEL, 3)
    # 灯槽里的四段灯，各在一条边的中间
    for x in (7, 8):
        c.set(x, 3, CYAN_GLOW, 4, glint=True)
        c.set(x, 12, CYAN_GLOW, 3, glint=True)
    for y in (7, 8):
        c.set(3, y, CYAN_GLOW, 4, glint=True)
        c.set(12, y, CYAN_GLOW, 3, glint=True)
    bolts(c, inset=1, base=3)
    return c


@texture("block/holo_beacon_side", refs=("block/beacon",))
def holo_beacon_side(rng):
    # 底面用 1–14 整块；侧面用第 13–15 行：上沿受光，中间一条嵌在深槽里的青色灯带，下沿暗
    c = casing(rng, base=3)
    c.hline(1, 14, 13, STEEL, 5)
    c.hline(1, 14, 14, GRAPHITE, 1)
    for x in range(3, 13):
        c.set(x, 14, CYAN_GLOW, 3 if x % 3 else 4, glint=True)
    c.set(2, 14, TEAL, 2, glint=True)
    c.set(13, 14, TEAL, 2, glint=True)
    c.hline(1, 14, 15, STEEL, 2)
    return c


@texture("block/holo_beacon_emitter", refs=("block/beacon",))
def holo_beacon_emitter(rng):
    # 投影头：顶面用 4–11（中间 5–10 是透镜，只露出半格），侧面用第 14–15 行
    c = Canvas()
    c.fill(STEEL, 3)
    c.clusters(rng, 0, 0, 15, 13, -1, 6)
    c.panel(4, 4, 11, 11, STEEL, 4, depth=1)
    # 透镜的座：一圈深色的环，环上四个青色的对位点
    c.frame(5, 5, 10, 10, GRAPHITE, 2)
    for x, y in ((7, 5), (5, 8), (10, 7), (8, 10)):
        c.set(x, y, CYAN_GLOW, 4, glint=True)
    c.rect(6, 6, 9, 9, GRAPHITE, 1)
    # 侧面：上沿亮一道，下面一排细的散热缝
    c.hline(0, 15, 14, STEEL, 5)
    for x in range(0, 16):
        c.set(x, 15, STEEL, 1 if x % 2 else 2)
    c.set(4, 14, STEEL, 6, glint=True)
    return c


@texture("block/holo_beacon_lens", refs=("block/sea_lantern",))
def holo_beacon_lens(rng):
    # 发光的透镜（模型里自发光）：顶面用 5–10，侧面用第 7 行。正中白热，往外是青色，边上暗一级
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5)
        if d < 1.2:
            c.set(x, y, WHITE_GLOW, 5)
        elif d < 2.4:
            c.set(x, y, CYAN_GLOW, 5)
        elif d < 3.6:
            c.set(x, y, CYAN_GLOW, 4)
        elif d < 6:
            c.set(x, y, CYAN_GLOW, 3)
        else:
            c.set(x, y, CYAN_GLOW, 2)
    # 镜面上一道斜的反光
    for i in range(3):
        c.set(5 + i, 6 - i, WHITE_GLOW, 4, glint=True)
    return c


# ---- 黑雾透镜 ----

@texture("item/dark_fog_lens", refs=("item/ender_eye",))
def dark_fog_lens(rng):
    """黑雾透镜：暗紫黑的镜框上三个爪，镜片里一圈圈卷进去的暗红雾，中间一圈弯折的红光，正中一个黑核。"""
    c = Canvas()
    for x, y in c.disc_cells(7.5, 7.5, 6.6):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        if d > 5.0:
            # 两格宽的镜框：按朝向分三级，左上亮、右下暗
            facing = -(dx + dy) / max(d, 0.5)
            c.set(x, y, FOG, 5 if facing > 0.5 else (4 if facing > -0.5 else 2))
        elif d > 2.9:
            # 雾往里卷：两条螺旋臂，臂的前缘是亮一点的品红，臂之间是暗的雾
            t = (d / 2.6 - a / math.pi) % 1.0
            if t < 0.45:
                c.set(x, y, CRIMSON, 3 if dx + dy < 0 else 2)
            elif t < 0.72:
                c.set(x, y, MAGENTA_GLOW, 2)
            else:
                c.set(x, y, FOG, 1)
        elif d > 1.9:
            c.set(x, y, RED_GLOW, 4 if dx - dy < 0 else 3)
        else:
            c.set(x, y, OIL, 0)
    c.outline()
    # 镜框上三个爪：12 点、4 点、8 点，上左受光
    for x, y, lv in ((7, 0, 6), (8, 0, 4), (13, 11, 3), (12, 12, 2), (2, 11, 5), (3, 12, 4)):
        c.set(x, y, FOG, lv, glint=True)
    c.set(4, 4, GLASS, GLASS.top, glint=True)
    c.set(5, 3, GLASS, GLASS.top - 1, glint=True)
    return c
