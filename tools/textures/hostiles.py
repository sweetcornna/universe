"""敌对单位和外星 NPC 的贴图（ADR-0031）：守卫机一家、海盗、黑雾一家、三个外星种族。

实体贴图的 UV 布局和模型代码一一对应（每个方块盒子的 texOffs 和尺寸），一点不动：
- 守卫机：client/sentinel/SentinelModel.java（巡逻、重型、维修、召唤）、QuadrupedSentinelModel.java、WalkerSentinelModel.java
- 海盗战机、守卫机拦截机：client/pirate/PirateModel.java（同一副模型换涂装）
- 黑雾：client/darkfog/DarkFogUnitModel.java、DarkFogBuildingModel.java、flight/SeedModel.java、space/SpaceUnitModel.java、
  hive/HiveBuildingModel.java
- 外星 NPC：client/civ/AlienNpcModel.java

发光层（`_glow`）几乎都用 RenderTypes.eyes 画、渲染时按状态着色（守卫机巡逻黄、敌对红，黑雾按种类，没能量时暗下去），
所以画成白色的亮度图：越亮越白、不透明度越高，底下的皮肤在发光的地方画成暗的灯罩、玻璃。底图和发光层是两张不同的图，
不能从一张画布里分出来（分出来的话底图上的灯是白的，着色后会发灰），所以各自登记一个配方，从同一次绘制里取。
外星 NPC 的发光层不着色，画成带颜色的。维修、召唤无人机的 `_accent` 层自带颜色（绿、紫），也不着色。

守卫机一家：白色（重型、步行机是钢灰）的外壳、深色的接缝和关节、琥珀色的识别标；面罩是深色玻璃，发光层是一道眼缝。
黑雾一家：发暗紫的黑色躯壳，棱边泛紫，接缝里透出暗红（发光层着色），像戴森球计划里失控的自我复制机器。
"""
from __future__ import annotations

import math
import random
import zlib
from functools import lru_cache

from canvas import Canvas
from color import ramp
from palette import (AMBER, AMBER_GLOW, BASALT, BONE, BRASS, CREAM, CRIMSON, CYAN_GLOW, FOG, GLASS, GRAPHITE,
                     GREEN, GREEN_GLOW, HAZARD, MAGENTA_GLOW, ORANGE, ORANGE_GLOW, RED, RED_GLOW, RUBBER, SCREEN,
                     STEEL, TEAL, VIOLET, VIOLET_GLOW, WHITE, WHITE_GLOW, WOOD, register)
from recipe import texture

# 召唤无人机的紫灰外壳
DUSK = register(ramp("hostiles_dusk", 292, 0.040, n=7, lo=0.24, hi=0.74, shift=10))

SIDES = ("front", "back", "left", "right")


def faces(u: int, v: int, w: int, h: int, d: int) -> dict:
    """模型方块的贴图展开：每个面在贴图上的矩形 (x, y, 宽, 高)，和 Minecraft 的 addBox 一样。"""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


class Skin:
    """一张实体皮肤：底图 c、着色的发光层 g（白色亮度图）、自带颜色的强调光层 a。"""

    def __init__(self, w: int, h: int, seed: int):
        self.c = Canvas(w, h)
        self.g = Canvas(w, h)
        self.a = Canvas(w, h)
        self.rng = random.Random(seed)

    # ---- 面里的坐标：i、j 从面的左上角数，负数从右、下数
    @staticmethod
    def _at(rect, i, j):
        x0, y0, w, h = rect
        if i < 0:
            i += w
        if j < 0:
            j += h
        if 0 <= i < w and 0 <= j < h:
            return x0 + i, y0 + j
        return None

    def px(self, rect, i, j, r, lv, glint=False):
        p = self._at(rect, i, j)
        if p:
            self.c.set(p[0], p[1], r, lv, glint)

    def shift(self, rect, i, j, d):
        p = self._at(rect, i, j)
        if p:
            self.c.shift(p[0], p[1], d)

    def fill(self, rect, r, lv):
        x0, y0, w, h = rect
        if w > 0 and h > 0:
            self.c.rect(x0, y0, x0 + w - 1, y0 + h - 1, r, lv)

    def row(self, rect, j, r, lv, i0=0, i1=None):
        w = rect[2]
        i1 = w - 1 if i1 is None else (i1 + w if i1 < 0 else i1)
        for i in range(i0, i1 + 1):
            self.px(rect, i, j, r, lv)

    def col(self, rect, i, r, lv, j0=0, j1=None):
        h = rect[3]
        j1 = h - 1 if j1 is None else (j1 + h if j1 < 0 else j1)
        for j in range(j0, j1 + 1):
            self.px(rect, i, j, r, lv)

    def bevel(self, rect, hi=1, lo=-1):
        """面的倒角：上沿、左沿亮，下沿、右沿暗；太窄的面只做能做的那一向。"""
        x0, y0, w, h = rect
        if h >= 3:
            for i in range(w):
                self.c.shift(x0 + i, y0, hi)
                self.c.shift(x0 + i, y0 + h - 1, lo)
        if w >= 3:
            for j in range(1 if h >= 3 else 0, h - 1 if h >= 3 else h):
                self.c.shift(x0, y0 + j, hi)
                self.c.shift(x0 + w - 1, y0 + j, lo)

    def box(self, u, v, w, h, d, r, lv, top=1, bottom=-1, bevel=True) -> dict:
        """整个盒子刷一遍底色：顶面亮一级、底面暗一级，每个面倒角。"""
        f = faces(u, v, w, h, d)
        for key, rect in f.items():
            self.fill(rect, r, lv + (top if key == "top" else bottom if key == "bottom" else 0))
            if bevel:
                self.bevel(rect)
        return f

    # ---- 发光层（白色的亮度图，渲染时着色）
    def glow(self, rect, i, j, strength=1.0):
        p = self._at(rect, i, j)
        if p:
            lv = 5 if strength >= 0.8 else 4 if strength >= 0.5 else 3
            # 灯本来就常常是单独的一个像素，检查时不算零碎
            self.g.set(p[0], p[1], WHITE_GLOW, lv, glint=True, alpha=int(255 * min(1.0, 0.35 + strength * 0.75)))

    def glow_fill(self, rect, strength=1.0, falloff=True):
        x0, y0, w, h = rect
        cx, cy = (w - 1) / 2, (h - 1) / 2
        rad = max(1.0, max(w, h) / 2)
        for j in range(h):
            for i in range(w):
                t = math.hypot(i - cx, j - cy) / rad if falloff else 0
                self.glow(rect, i, j, strength * (1 - 0.5 * min(1.0, t)))

    # ---- 强调光层（自带颜色，不着色）
    def accent(self, rect, i, j, r, lv, alpha=None):
        p = self._at(rect, i, j)
        if p:
            self.a.set(p[0], p[1], r, lv, glint=True, alpha=alpha)


def draw_cached(fn):
    """一张皮肤的几层由不同的配方取，画一次就够了。"""
    return lru_cache(maxsize=None)(fn)


def seed(name: str) -> int:
    return zlib.crc32(name.encode())


# ============================================================ 守卫机一家

def visor_box(s: Skin, u, v, w, h, d=1, slit=None):
    """面罩：一块深色的玻璃。发光层是一道横着的眼缝，中间最亮，往两边变暗，上下各有一点余光。"""
    f = faces(u, v, w, h, d)
    for key, rect in f.items():
        s.fill(rect, GRAPHITE, 1)
    front = f["front"]
    s.fill(front, SCREEN, 0)
    rows = slit if slit is not None else ([h // 2 - 1, h // 2] if h >= 4 else [h // 2])
    cx = (w - 1) / 2
    for j in range(h):
        for i in range(w):
            dx = abs(i - cx) / max(1.0, w / 2)
            if j in rows:
                s.px(front, i, j, SCREEN, 2 if dx < 0.5 else 1)
                s.glow(front, i, j, max(0.45, 1.0 - dx * 0.6))
            elif abs(j - rows[0]) == 1 or abs(j - rows[-1]) == 1:
                s.glow(front, i, j, 0.12 if dx < 0.7 else 0.0)
    # 玻璃上沿一道反光
    if h >= 3:
        s.px(front, 0, 0, SCREEN, 3, glint=True)
        s.px(front, 1, 0, SCREEN, 2)
    return f


def sentinel_shell(s: Skin, f: dict, r, lv, accent, band=True, hatch=True):
    """球形机身的外壳：侧面一圈深色的腰带（接缝）、腰带上方两侧的识别标、顶上的圆形舱盖、
    背面的散热片、底面的推进格栅（发光层点亮中间）。"""
    w = f["front"][2]
    h = f["front"][3]
    mid = h // 2
    for key in SIDES:
        rect = f[key]
        rw = rect[2]
        # 弧面：腰带以下一行行往下暗，最底下一行背光
        for j in range(mid + 2, h - 1):
            t = (j - mid - 1) / max(1, h - mid - 2)
            if t > 0.5:
                s.row(rect, j, r, lv - 1, 1 if rw >= 3 else 0, -2 if rw >= 3 else -1)
        if band:
            # 腰带：一道深色的槽，下面一道受光的唇；槽的两头更深，像绕到背面去了
            s.row(rect, mid, GRAPHITE, 2)
            s.row(rect, mid + 1, r, min(r.top, lv + 1))
            if rw >= 5:
                s.px(rect, 0, mid, GRAPHITE, 1)
                s.px(rect, -1, mid, GRAPHITE, 1)
                s.px(rect, -1, mid + 1, r, lv)
        # 下半截一道竖的板缝
        if rw >= 6 and h - mid >= 4:
            sx = rw // 3 if key in ("left", "back") else rw - 1 - rw // 3
            for j in range(mid + 2, h - 1):
                s.px(rect, sx, j, r, lv - 2)
                if sx + 1 < rw - 1:
                    s.px(rect, sx + 1, j, r, min(r.top, lv + 1))
    for key in ("left", "right"):
        rect = f[key]
        dw = rect[2]
        # 识别标：腰带上方一道短的涂装条，朝前的一端削尖
        n = max(2, dw // 3)
        c0 = (dw - n) // 2
        for k in range(n):
            s.px(rect, c0 + k, 1, accent, 4 if k < n - 1 else 3)
        if mid - 1 > 1:
            tip = c0 + n - 1 if key == "left" else c0
            s.px(rect, tip, 2, accent, 2)
    # 正面：面罩后面的深色凹座
    front = f["front"]
    for j in range(1, h - 1):
        for i in range(1, w - 1):
            edge = i in (1, w - 2) or j in (1, h - 2)
            s.px(front, i, j, GRAPHITE, 2 if edge else 1)
    s.row(front, 1, GRAPHITE, 0, 1, -2)
    s.col(front, 1, GRAPHITE, 0, 1, -2)
    # 背面：竖着的散热片
    back = f["back"]
    for i in range(1, back[2] - 1):
        for j in range(1, back[3] - 1):
            s.px(back, i, j, GRAPHITE if i % 2 else r, 2 if i % 2 else lv - 1)
    # 顶面：圆形舱盖，一圈深色的缝，舱盖中间受光
    top = f["top"]
    if hatch:
        tw, td = top[2], top[3]
        cx, cy = (tw - 1) / 2, (td - 1) / 2
        rad = min(tw, td) * 0.33
        for j in range(td):
            for i in range(tw):
                dd = math.hypot(i - cx, j - cy)
                if abs(dd - rad) < 0.55:
                    s.px(top, i, j, r, lv - 2)
                elif dd < rad - 0.5 and i + j < tw - 1:
                    s.shift(top, i, j, 1)
    # 底面：圆形的推进口，外圈一道钢环，里面是深色的喷口，发光层从中间往外暗
    bottom = f["bottom"]
    bw, bd = bottom[2], bottom[3]
    cx, cy = (bw - 1) / 2, (bd - 1) / 2
    rad = min(bw, bd) / 2 - 0.6
    for j in range(bd):
        for i in range(bw):
            dd = math.hypot(i - cx, j - cy)
            if dd <= rad - 0.9:
                s.px(bottom, i, j, GRAPHITE, 0 if dd < rad - 1.8 else 1)
                s.glow(bottom, i, j, 0.35 + 0.65 * (1 - dd / max(1.0, rad - 0.9)))
            elif dd <= rad + 0.1:
                s.px(bottom, i, j, STEEL, 3 if i + j < bw else 2)


def thruster_pod(s: Skin, u, v, w, h, d, r=STEEL, lv=4):
    """侧面的推进舱：外壳上一道道散热纹，底面是喷口（发光层亮）。"""
    f = s.box(u, v, w, h, d, r, lv)
    for key in SIDES:
        rect = f[key]
        for j in range(1, rect[3] - 1, 2):
            s.row(rect, j, r, lv - 2, 1, -2)
    bottom = f["bottom"]
    s.fill(bottom, GRAPHITE, 1)
    for j in range(1, bottom[3] - 1):
        for i in range(1, bottom[2] - 1):
            s.px(bottom, i, j, GRAPHITE, 0)
            s.glow(bottom, i, j, 0.9)
    return f


@draw_cached
def sentinel_drone():
    s = Skin(64, 32, seed("sentinel"))
    body = s.box(0, 0, 8, 7, 8, WHITE, 5)
    sentinel_shell(s, body, WHITE, 5, AMBER)
    visor_box(s, 32, 0, 5, 4)
    # 背鳍：白色，后缘一道琥珀色
    fin = s.box(44, 0, 1, 3, 5, WHITE, 4)
    for key in ("left", "right"):
        s.row(fin[key], 0, WHITE, 6)
        s.col(fin[key], 0 if key == "right" else -1, AMBER, 3)
    # 尾部的散热板
    rear = s.box(20, 16, 6, 5, 1, WHITE, 4)
    back = rear["back"]
    for i in range(1, 5):
        s.col(back, i, GRAPHITE if i % 2 else STEEL, 2 if i % 2 else 3, 1, -2)
    thruster_pod(s, 0, 16, 3, 4, 5)
    antenna = s.box(16, 16, 1, 3, 1, GRAPHITE, 2)
    s.px(antenna["bottom"], 0, 0, SCREEN, 1)
    s.glow(antenna["bottom"], 0, 0, 1.0)
    for key in SIDES:
        s.px(antenna[key], 0, -1, SCREEN, 2)
        s.glow(antenna[key], 0, -1, 0.7)
    return s


@draw_cached
def sentinel_heavy():
    s = Skin(64, 64, seed("heavy_sentinel"))
    body = s.box(0, 0, 12, 9, 12, STEEL, 5)
    sentinel_shell(s, body, STEEL, 5, RED)
    # 正面两列铆钉，侧面多一道装甲接缝
    front = body["front"]
    for j in range(2, 8, 2):
        s.px(front, 0, j, STEEL, 7, glint=True)
        s.px(front, -1, j, STEEL, 3, glint=True)
    for key in ("left", "right"):
        rect = body[key]
        s.col(rect, 2, STEEL, 3, 4, -2)
        s.col(rect, -3, STEEL, 3, 4, -2)
    visor_box(s, 0, 22, 8, 5)
    # 腹下的炮：枪灰，炮口深，发光层点亮
    cannon = s.box(36, 22, 3, 2, 7, GRAPHITE, 3)
    for key in ("left", "right"):
        s.col(cannon[key], 2, GRAPHITE, 1)
        s.col(cannon[key], 4, GRAPHITE, 1)
    muzzle = cannon["front"]
    s.fill(muzzle, GRAPHITE, 0)
    s.glow(muzzle, 1, 0, 1.0)
    s.glow(muzzle, 1, 1, 0.9)
    s.glow(muzzle, 0, 0, 0.45)
    s.glow(muzzle, 2, 1, 0.45)
    s.glow(muzzle, 0, 1, 0.35)
    s.glow(muzzle, 2, 0, 0.35)
    # 顶冠：深一点的钢，侧面一排红色的竖纹
    crest = s.box(0, 32, 2, 3, 8, STEEL, 4)
    for key in ("left", "right"):
        rect = crest[key]
        for i in range(0, rect[2], 2):
            s.col(rect, i, RED, 3, 0, 1)
    # 两侧的装甲板：上沿黄黑警示条
    plates = s.box(20, 32, 1, 6, 8, STEEL, 4)
    for key in ("left", "right"):
        rect = plates[key]
        for i in range(rect[2]):
            for j in range(2):
                hz = (i + j) % 4 < 2
                s.px(rect, i, j, HAZARD if hz else GRAPHITE, 4 if hz else 1)
        s.row(rect, 2, STEEL, 2)
        for i in (1, rect[2] - 2):
            s.px(rect, i, -2, STEEL, 6, glint=True)
    thruster_pod(s, 20, 22, 4, 5, 4, STEEL, 3)
    return s


def emitter(s: Skin, f: dict, color, base=GRAPHITE):
    """自带颜色的发光件（修理头、召唤环）：深色的底；强调光层用发光色阶彩度最高的那一级，
    宽的面中间亮一级（发光色阶再往上就接近白了，颜色会丢）。"""
    for key, rect in f.items():
        x0, y0, w, h = rect
        for j in range(h):
            for i in range(w):
                edge = i in (0, w - 1) or j in (0, h - 1)
                s.px(rect, i, j, base, 2 if edge else 1)
                thin = w <= 2 or h <= 2
                s.accent(rect, i, j, color, 3 if (edge or thin) else 4, alpha=210 if edge and not thin else None)


@draw_cached
def sentinel_repair():
    s = Skin(64, 32, seed("repair_sentinel"))
    body = s.box(0, 0, 6, 5, 6, WHITE, 5)
    sentinel_shell(s, body, WHITE, 5, GREEN, hatch=False)
    # 侧面的绿色十字标（替换掉箭头）
    for key in ("left", "right"):
        rect = body[key]
        s.row(rect, 1, WHITE, 5, 1, -2)
        s.row(rect, 0, WHITE, 6, 1, -2)
        cx = rect[2] // 2
        for di, dj in ((0, 0), (-1, 1), (0, 1), (1, 1)):
            s.px(rect, cx + di, dj, GREEN, 4 if dj == 0 or di == 0 else 3)
    top = body["top"]
    for di, dj in ((2, 1), (3, 1), (1, 2), (2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4)):
        lit = (di, dj) in ((2, 1), (3, 1), (2, 4), (3, 4)) or dj in (2, 3) and di in (2, 3)
        if lit:
            s.px(top, di, dj, GREEN, 4 if dj < 3 else 3)
    visor_box(s, 24, 0, 4, 3, slit=[1])
    emitter(s, faces(36, 0, 4, 1, 4), GREEN_GLOW)
    thruster_pod(s, 0, 12, 2, 3, 4, STEEL, 4)
    arm = s.box(16, 12, 1, 4, 1, GRAPHITE, 3)
    for key in SIDES:
        s.px(arm[key], 0, 1, STEEL, 5)
    s.px(arm["bottom"], 0, 0, GRAPHITE, 1)
    s.accent(arm["bottom"], 0, 0, GREEN_GLOW, 4)
    for key in SIDES:
        s.accent(arm[key], 0, -1, GREEN_GLOW, 3, alpha=170)
    cross = s.box(24, 12, 3, 1, 1, WHITE, 4)
    for key in ("front", "back", "top"):
        rect = cross[key]
        for i in range(rect[2]):
            for j in range(rect[3]):
                s.px(rect, i, j, GREEN, 2)
                s.accent(rect, i, j, GREEN_GLOW, 4 if i == rect[2] // 2 else 3)
    return s


@draw_cached
def sentinel_summoner():
    s = Skin(64, 64, seed("summoner_sentinel"))
    body = s.box(0, 0, 8, 8, 8, DUSK, 4)
    sentinel_shell(s, body, DUSK, 4, VIOLET)
    # 正面面罩两侧的竖条
    front = body["front"]
    for j in range(1, 7):
        s.px(front, 0, j, VIOLET, 3 if j < 4 else 2)
        s.px(front, -1, j, VIOLET, 2)
    visor_box(s, 32, 0, 6, 3, slit=[1])
    spike = s.box(48, 0, 2, 3, 2, DUSK, 3)
    for key in SIDES:
        s.col(spike[key], 0, DUSK, 5)
        s.px(spike[key], 0, 0, VIOLET, 4)
        s.px(spike[key], 1, 0, VIOLET, 3)
        s.accent(spike[key], 0, 0, VIOLET_GLOW, 3, alpha=200)
        s.accent(spike[key], 1, 0, VIOLET_GLOW, 3, alpha=200)
    top = spike["top"]
    for i in range(2):
        for j in range(2):
            s.px(top, i, j, VIOLET, 3)
            s.accent(top, i, j, VIOLET_GLOW, 4)
    # 外圈的四段光环：深色的轨道，强调光层亮紫，隔一段一个暗的接头
    for u, v, w, h, d in ((0, 16, 14, 1, 2), (0, 20, 14, 1, 2), (0, 24, 2, 1, 10), (24, 24, 2, 1, 10)):
        f = faces(u, v, w, h, d)
        emitter(s, f, VIOLET_GLOW, DUSK)
        for key in ("front", "back", "left", "right", "top", "bottom"):
            x0, y0, fw, fh = f[key]
            long_x = fw >= fh
            for k in range(2, max(fw, fh), 4):
                for m in range(min(fw, fh)):
                    i, j = (k, m) if long_x else (m, k)
                    s.px(f[key], i, j, DUSK, 5)
                    s.accent(f[key], i, j, VIOLET_GLOW, 2, alpha=70)
    thruster_pod(s, 0, 40, 3, 4, 4, DUSK, 3)
    return s


@texture("entity/sentinel", refs=("entity/ghast/ghast", "entity/iron_golem/iron_golem"))
def sentinel(rng):
    return sentinel_drone().c


@texture("entity/sentinel_glow")
def sentinel_glow(rng):
    return sentinel_drone().g


@texture("entity/heavy_sentinel")
def heavy_sentinel(rng):
    return sentinel_heavy().c


@texture("entity/heavy_sentinel_glow")
def heavy_sentinel_glow(rng):
    return sentinel_heavy().g


@texture("entity/repair_sentinel")
def repair_sentinel(rng):
    return sentinel_repair().c


@texture("entity/repair_sentinel_glow")
def repair_sentinel_glow(rng):
    return sentinel_repair().g


@texture("entity/repair_sentinel_accent")
def repair_sentinel_accent(rng):
    return sentinel_repair().a


@texture("entity/summoner_sentinel")
def summoner_sentinel(rng):
    return sentinel_summoner().c


@texture("entity/summoner_sentinel_glow")
def summoner_sentinel_glow(rng):
    return sentinel_summoner().g


@texture("entity/summoner_sentinel_accent")
def summoner_sentinel_accent(rng):
    return sentinel_summoner().a


def hazard(s: Skin, rect, rows=2, j0=0):
    """黄黑相间的斜条，画在面的上沿。"""
    for j in range(j0, j0 + min(rows, rect[3])):
        for i in range(rect[2]):
            on = (i + j) % 4 < 2
            s.px(rect, i, j, HAZARD if on else GRAPHITE, (4 if j == j0 else 3) if on else 1)


def grille(s: Skin, rect, r, lv, glow=0.0, rows=True):
    """散热格：一道道深槽，槽下沿受光；glow 大于 0 时发光层亮在槽里（弱点）。"""
    w, h = rect[2], rect[3]
    for j in range(h):
        for i in range(w):
            edge = i in (0, w - 1) or j in (0, h - 1)
            slot = (j % 2 == 1) if rows else (i % 2 == 1)
            if edge:
                s.px(rect, i, j, r, lv - 1 if (i == w - 1 or j == h - 1) else lv + 1)
            elif slot:
                s.px(rect, i, j, GRAPHITE, 0)
                if glow:
                    s.glow(rect, i, j, glow)
            else:
                s.px(rect, i, j, r, lv - 1)


def lamp(s: Skin, rect, i, j, strength=0.9):
    """一颗指示灯：深色的灯罩，发光层点亮。"""
    s.px(rect, i, j, SCREEN, 1)
    s.glow(rect, i, j, strength)


def seams(s: Skin, rect, r, lv, every=4, vertical=False, start=None):
    """装甲板的接缝：一道深色、旁边一道受光。"""
    w, h = rect[2], rect[3]
    n = w if vertical else h
    for k in range(start if start is not None else every, n - 1, every):
        if vertical:
            s.col(rect, k, r, lv - 2)
            if k + 1 < w - 1:
                s.col(rect, k + 1, r, min(r.top, lv + 1))
        else:
            s.row(rect, k, r, lv - 2)
            if k + 1 < h - 1:
                s.row(rect, k + 1, r, min(r.top, lv + 1))


@draw_cached
def sentinel_quadruped():
    s = Skin(64, 64, seed("quadruped_sentinel"))
    # 机身：白色外壳，侧面腰带和识别标，背上几块盖板，肚子是深色的
    torso = s.box(0, 0, 9, 6, 16, WHITE, 5)
    for key in ("left", "right"):
        rect = torso[key]
        s.row(rect, 3, GRAPHITE, 2)
        s.row(rect, 4, WHITE, 6)
        s.row(rect, 5, WHITE, 4)
        seams(s, rect, WHITE, 5, every=5, vertical=True, start=5)
        for i in range(2, rect[2] - 2, 3):
            s.px(rect, i, 1, AMBER, 4)
        # 腿根的关节盖
        for i in (2, rect[2] - 4):
            s.px(rect, i, 4, GRAPHITE, 3)
            s.px(rect, i + 1, 4, GRAPHITE, 2)
    top = torso["top"]
    seams(s, top, WHITE, 6, every=4, start=3)
    s.col(top, 4, WHITE, 4, 1, -2)
    bottom = torso["bottom"]
    s.fill(bottom, GRAPHITE, 2)
    seams(s, bottom, GRAPHITE, 2, every=4)
    front = torso["front"]
    s.fill((front[0] + 1, front[1] + 1, front[2] - 2, front[3] - 2), GRAPHITE, 2)
    s.row(front, 1, GRAPHITE, 1, 1, -2)
    for i in (2, 4, 6):
        s.px(front, i, 3, STEEL, 4)
    s.row(torso["back"], 3, GRAPHITE, 2)
    # 脊甲：钢的，一道道横棱
    spine = s.box(0, 22, 6, 2, 11, STEEL, 5)
    seams(s, spine["top"], STEEL, 5, every=2, start=1)
    for key in ("left", "right"):
        for i in range(1, spine[key][2], 2):
            s.px(spine[key], i, 0, STEEL, 3)
    # 尾部的散热口：弱点，发光层亮
    vent = s.box(36, 22, 5, 4, 1, STEEL, 3)
    grille(s, vent["back"], STEEL, 4, glow=0.95)
    tail = s.box(36, 28, 1, 6, 1, GRAPHITE, 2)
    for key in SIDES:
        s.px(tail[key], 0, 0, STEEL, 5)
    lamp(s, tail["top"], 0, 0, 1.0)
    # 头：白色外壳，正面一道深色的面罩座，侧面识别标
    skull = s.box(0, 36, 7, 6, 7, WHITE, 5)
    sf = skull["front"]
    for j in range(1, 4):
        s.row(sf, j, GRAPHITE, 1 if j == 1 else 2)
    s.row(sf, 4, WHITE, 6)
    s.px(sf, 3, 5, GRAPHITE, 2)
    for key in ("left", "right"):
        rect = skull[key]
        s.px(rect, 3, 1, AMBER, 4)
        s.px(rect, 3, 2, AMBER, 3)
        s.row(rect, 4, WHITE, 4, 1, -2)
    seams(s, skull["top"], WHITE, 6, every=3)
    visor_box(s, 30, 36, 5, 2, slit=[0, 1])
    # 下巴的发射口：深色的炮口，中间最亮
    em = s.box(44, 36, 3, 3, 3, GRAPHITE, 3)
    lens = em["front"]
    s.fill(lens, GRAPHITE, 0)
    s.px(lens, 1, 1, SCREEN, 2)
    for j in range(3):
        for i in range(3):
            s.glow(lens, i, j, 1.0 if (i, j) == (1, 1) else 0.4)
    # 耳朵一样的传感鳍
    fin = s.box(56, 0, 1, 3, 3, AMBER, 4)
    for key in ("left", "right"):
        s.row(fin[key], -1, AMBER, 2)
        s.row(fin[key], 0, AMBER, 5)
    # 腿：大腿钢灰，膝上一颗灯，小腿深色，脚掌更深
    upper = s.box(0, 50, 3, 7, 3, STEEL, 5)
    for key in SIDES:
        s.row(upper[key], 2, STEEL, 3)
        s.row(upper[key], 3, STEEL, 6)
    lamp(s, upper["front"], 1, 5, 0.7)
    shin = s.box(12, 50, 2, 5, 2, GRAPHITE, 3)
    for key in SIDES:
        s.col(shin[key], 0, GRAPHITE, 4)
        s.px(shin[key], 0, 0, STEEL, 4)
        s.px(shin[key], 1, 0, STEEL, 3)
    foot = s.box(20, 50, 3, 1, 4, GRAPHITE, 2)
    for key in SIDES:
        s.row(foot[key], 0, GRAPHITE, 3)
    return s


@draw_cached
def sentinel_walker():
    s = Skin(128, 128, seed("walker_sentinel"))
    # 座舱：钢灰装甲，四边一圈铆钉，侧面黄黑警示条和红色标识，正面一道深色的观察槽
    cockpit = s.box(0, 0, 18, 14, 18, STEEL, 5)
    for key in SIDES:
        rect = cockpit[key]
        s.row(rect, 9, STEEL, 3)
        s.row(rect, 10, STEEL, 6)
        s.row(rect, -2, STEEL, 4, 1, -2)
        for j in range(2, 13, 3):
            s.px(rect, 1, j, STEEL, 7, glint=True)
            s.px(rect, -2, j, STEEL, 7, glint=True)
        seams(s, rect, STEEL, 5, every=6, vertical=True, start=6)
    for key in ("left", "right"):
        rect = cockpit[key]
        hazard(s, rect, 2)
        for j in range(4, 8):
            s.row(rect, j, RED, 4 if j == 4 else 3, 7, 10)
        s.row(rect, 8, RED, 2, 7, 10)
    cf = cockpit["front"]
    for j in range(3, 8):
        s.row(cf, j, GRAPHITE, 1 if j in (3, 7) else 2, 2, -3)
    s.row(cf, 3, GRAPHITE, 0, 2, -3)
    s.row(cf, 8, STEEL, 6, 2, -3)
    ctop = cockpit["top"]
    seams(s, ctop, STEEL, 6, every=5, start=4)
    for i, j in ((2, 2), (15, 2), (2, 15), (15, 15)):
        s.px(ctop, i, j, STEEL, 7, glint=True)
        s.px(ctop, i + 1, j + 1, STEEL, 3, glint=True)
    s.fill(cockpit["bottom"], GRAPHITE, 2)
    seams(s, cockpit["bottom"], GRAPHITE, 2, every=6)
    # 传感罩：深色，顶上三颗灯，正面一道扫描缝
    dome = s.box(76, 0, 8, 4, 8, GRAPHITE, 3)
    dtop = dome["top"]
    for k, i in enumerate((2, 4, 6)):
        lamp(s, dtop, i - 1, 3, 0.95 if k == 1 else 0.6)
    s.row(dtop, 0, GRAPHITE, 5)
    dfront = dome["front"]
    s.row(dfront, 1, SCREEN, 1, 1, -2)
    for i in range(1, 7):
        s.glow(dfront, i, 1, 0.55 if i in (3, 4) else 0.35)
    antenna = s.box(110, 0, 1, 6, 1, GRAPHITE, 2)
    for key in SIDES:
        s.px(antenna[key], 0, 3, STEEL, 5)
    lamp(s, antenna["top"], 0, 0, 1.0)
    visor_box(s, 76, 12, 12, 3, slit=[1])
    # 下巴：深色的进气格栅
    chin = s.box(76, 16, 12, 3, 12, STEEL, 4)
    chf = chin["front"]
    for i in range(12):
        s.col(chf, i, GRAPHITE, 1 if i % 2 else 3)
    s.row(chf, 0, STEEL, 5)
    s.fill(chin["bottom"], GRAPHITE, 2)
    # 背后的反应堆散热口：弱点，发光层最亮
    vent = s.box(0, 32, 10, 7, 2, STEEL, 4)
    grille(s, vent["back"], STEEL, 4, glow=1.0)
    hazard(s, vent["top"], 1)
    muzzle = s.box(86, 32, 2, 2, 1, GRAPHITE, 1)
    for j in range(2):
        for i in range(2):
            s.px(muzzle["front"], i, j, GRAPHITE, 0)
            s.glow(muzzle["front"], i, j, 1.0)
    # 骨盆：深色，两侧是圆的关节
    pelvis = s.box(0, 42, 14, 6, 8, GRAPHITE, 3)
    for key in ("left", "right"):
        rect = pelvis[key]
        cx, cy = (rect[2] - 1) / 2, (rect[3] - 1) / 2
        for j in range(rect[3]):
            for i in range(rect[2]):
                d = math.hypot(i - cx, j - cy)
                if d < 2.6:
                    s.px(rect, i, j, STEEL, 5 if d < 1.2 else (4 if i + j < cx + cy else 2))
    seams(s, pelvis["front"], GRAPHITE, 3, every=3, vertical=True)
    # 炮：枪灰，炮管上一圈圈箍，侧面一条红色
    cannon = s.box(48, 42, 4, 4, 14, GRAPHITE, 4)
    for key in ("top", "bottom", "left", "right"):
        rect = cannon[key]
        horizontal = rect[2] > rect[3]
        for k in range(2, 14, 4):
            if horizontal:
                s.col(rect, k, GRAPHITE, 2)
                s.col(rect, k + 1, GRAPHITE, 5)
            else:
                s.row(rect, k, GRAPHITE, 2)
                s.row(rect, k + 1, GRAPHITE, 5)
    s.row(cannon["left"], 1, RED, 3, 6, 9)
    s.row(cannon["right"], 1, RED, 3, 4, 7)
    s.fill(cannon["front"], GRAPHITE, 1)
    s.fill((cannon["front"][0] + 1, cannon["front"][1] + 1, 2, 2), GRAPHITE, 0)
    # 大腿、小腿：液压管，膝上一颗灯
    thigh = s.box(0, 60, 6, 18, 6, STEEL, 5)
    for key in SIDES:
        rect = thigh[key]
        s.col(rect, 1, GRAPHITE, 2, 2, -3)
        s.col(rect, -2, BRASS, 3, 2, -3)
        s.px(rect, -2, 2, BRASS, 5)
        seams(s, rect, STEEL, 5, every=6, start=5)
    lamp(s, thigh["front"], 3, 2, 0.8)
    shin = s.box(24, 60, 5, 18, 5, STEEL, 4)
    for key in SIDES:
        seams(s, shin[key], STEEL, 4, every=5, start=3)
    foot = s.box(44, 60, 8, 4, 11, GRAPHITE, 3)
    hazard(s, foot["front"], 2)
    lamp(s, foot["left"], 2, 1, 0.6)
    lamp(s, foot["right"], -3, 1, 0.6)
    for key in ("left", "right"):
        s.row(foot[key], -1, GRAPHITE, 1)
    knee = s.box(84, 60, 7, 6, 2, STEEL, 5)
    kf = knee["front"]
    s.col(kf, 3, RED, 4, 1, -2)
    s.px(kf, 3, 1, RED, 5)
    s.px(kf, 0, 0, STEEL, 7, glint=True)
    s.px(kf, -1, 0, STEEL, 7, glint=True)
    return s


@texture("entity/quadruped_sentinel", refs=("entity/wolf/wolf",))
def quadruped_sentinel(rng):
    return sentinel_quadruped().c


@texture("entity/quadruped_sentinel_glow")
def quadruped_sentinel_glow(rng):
    return sentinel_quadruped().g


@texture("entity/walker_sentinel", refs=("entity/iron_golem/iron_golem",))
def walker_sentinel(rng):
    return sentinel_walker().c


@texture("entity/walker_sentinel_glow")
def walker_sentinel_glow(rng):
    return sentinel_walker().g


# ============================================================ 海盗战机和守卫机拦截机（同一副模型）

def fighter(name: str, hull, hl: int, paint, pl: int, tip, tl: int, canopy, cl: int) -> Skin:
    """战机：机身、机头、座舱罩、垂尾、内外两段机翼、翼尖炮、两台引擎。
    hull/paint/tip/canopy 是机身、涂装、翼尖条纹、座舱玻璃的色阶，后面跟各自的基准级数。"""
    s = Skin(128, 64, seed(name))
    # 机身：顶上一道脊线、隔一段一道横缝，机头那端一段涂装；侧面一道涂装线和竖缝；肚子暗
    body = s.box(0, 0, 6, 4, 24, hull, hl)
    top = body["top"]
    s.col(top, 2, hull, hl + 2)
    s.col(top, 3, hull, hl - 1)
    seams(s, top, hull, hl + 1, every=6, start=5)
    for j in range(3):
        s.row(top, j, paint, pl + (1 if j == 0 else 0))
    s.row(top, 3, paint, pl - 2)
    for key in ("left", "right"):
        rect = body[key]
        s.row(rect, 1, paint, pl)
        s.row(rect, 0, hull, hl + 1)
        for i in range(4, rect[2], 6):
            s.col(rect, i, hull, hl - 2, 2)
        # 进气口
        k = 3 if key == "right" else rect[2] - 6
        s.row(rect, 2, GRAPHITE, 0, k, k + 2)
    bottom = body["bottom"]
    s.fill(bottom, hull, hl - 1)
    seams(s, bottom, hull, hl - 1, every=6)
    s.fill(body["front"], GRAPHITE, 1)
    s.fill(body["back"], GRAPHITE, 1)
    s.row(body["back"], 0, GRAPHITE, 3)
    # 机头：涂装，尖上更暗
    nose = s.box(60, 0, 4, 3, 5, paint, pl)
    s.fill(nose["front"], paint, pl - 2)
    s.px(nose["front"], 1, 1, GRAPHITE, 1)
    s.px(nose["front"], 2, 1, GRAPHITE, 1)
    for key in ("left", "right"):
        s.row(nose[key], -1, paint, pl - 1)
    # 座舱罩：深色的玻璃，顶上一道高光，发光层微微亮
    cp = s.box(80, 0, 4, 2, 7, canopy, cl, top=1, bottom=0, bevel=False)
    for key, rect in cp.items():
        for j in range(rect[3]):
            for i in range(rect[2]):
                s.glow(rect, i, j, 0.12)
    ctop = cp["top"]
    for j in range(1, ctop[3] - 1):
        s.px(ctop, 1, j, canopy, cl + 3, glint=True)
    s.px(ctop, 2, 2, canopy, cl + 2, glint=True)
    for key in ("left", "right"):
        rect = cp[key]
        s.row(rect, 0, canopy, cl + 1)
        s.col(rect, 0 if key == "right" else -1, hull, hl - 1)
    # 垂尾：上半截涂装
    fin = s.box(104, 0, 1, 6, 7, hull, hl)
    for key in ("left", "right"):
        rect = fin[key]
        s.row(rect, 0, paint, pl + 1)
        s.row(rect, 1, paint, pl)
        s.row(rect, 2, paint, pl - 1)
        s.col(rect, 0 if key == "right" else -1, hull, hl + 1, 3)
    # 内段机翼：涂装的斜条纹，前缘亮
    inner = s.box(0, 28, 11, 1, 12, hull, hl)
    it = inner["top"]
    for j in range(1, it[3] - 1):
        for i in range(1, it[2] - 1):
            k = (i + j) % 7
            if k < 3:
                s.px(it, i, j, paint, pl + 1 if k == 0 else pl if k == 1 else pl - 1)
    s.row(it, 0, hull, hl + 1)
    s.row(it, 4, hull, hl - 1, 1, -2)
    for i in (2, 8):
        s.px(it, i, 2, hull, hl + 2, glint=True)
    s.fill(inner["bottom"], hull, hl - 1)
    s.row(inner["bottom"], 0, hull, hl - 2)
    # 外段机翼：涂装，翼尖两列条纹
    outer = s.box(46, 28, 7, 1, 7, paint, pl)
    ot = outer["top"]
    for j in range(ot[3]):
        for i in range(2):
            on = (i + j) % 2 == 0
            s.px(ot, i, j, tip if on else GRAPHITE, tl if on else 1)
    s.row(ot, 0, paint, pl + 1, 2)
    s.fill(outer["bottom"], paint, pl - 2)
    # 翼尖炮：深色的管子，中间一道箍，炮口发光
    gun = s.box(74, 28, 2, 2, 12, GRAPHITE, 3)
    for key in ("left", "right", "top", "bottom"):
        rect = gun[key]
        if rect[2] > rect[3]:
            s.col(rect, rect[2] // 2, GRAPHITE, 5)
            s.col(rect, rect[2] // 2 + 1, GRAPHITE, 1)
        else:
            s.row(rect, rect[3] // 2, GRAPHITE, 5)
            s.row(rect, rect[3] // 2 + 1, GRAPHITE, 1)
    mz = gun["front"]
    s.fill(mz, GRAPHITE, 0)
    for j in range(2):
        for i in range(2):
            s.glow(mz, i, j, 0.9)
    # 引擎：钢的外壳，一圈圈散热纹，尾喷口中间最亮
    engine = s.box(0, 44, 4, 4, 7, STEEL, 4)
    for key in ("left", "right", "top", "bottom"):
        rect = engine[key]
        if rect[2] >= rect[3]:
            for i in range(1, rect[2], 2):
                s.col(rect, i, STEEL, 2)
        else:
            for j in range(1, rect[3], 2):
                s.row(rect, j, STEEL, 2)
    nz = engine["back"]
    for j in range(4):
        for i in range(4):
            edge = i in (0, 3) or j in (0, 3)
            s.px(nz, i, j, STEEL, 3 if edge else 1)
            if not edge:
                s.px(nz, i, j, GRAPHITE, 0)
                s.glow(nz, i, j, 1.0)
            else:
                s.glow(nz, i, j, 0.25)
    s.fill(engine["front"], STEEL, 2)
    return s


@draw_cached
def pirate_skin():
    return fighter("pirate_fighter", GRAPHITE, 3, RED, 3, HAZARD, 4, CRIMSON, 0)


@draw_cached
def interceptor_skin():
    return fighter("sentinel_interceptor", WHITE, 5, AMBER, 4, WHITE, 6, AMBER, 0)


@texture("entity/pirate_fighter", refs=("entity/phantom",))
def pirate_fighter(rng):
    return pirate_skin().c


@texture("entity/pirate_fighter_glow")
def pirate_fighter_glow(rng):
    return pirate_skin().g


@texture("entity/sentinel_interceptor")
def sentinel_interceptor(rng):
    return interceptor_skin().c


@texture("entity/sentinel_interceptor_glow")
def sentinel_interceptor_glow(rng):
    return interceptor_skin().g


# ============================================================ 黑雾一家

def chamfer(s: Skin, rect, i0, j0, i1, j1, lv, cut=1):
    """一块切了角的甲片：一圈深缝，缝里面上、左受光，下、右背光，四个角斜着切掉。"""
    for j in range(j0, j1 + 1):
        for i in range(i0, i1 + 1):
            di = min(i - i0, i1 - i)
            dj = min(j - j0, j1 - j)
            if di + dj < cut:
                s.px(rect, i, j, FOG, lv - 2)
                continue
            edge = di == 0 or dj == 0 or di + dj == cut
            if edge:
                s.px(rect, i, j, FOG, lv - 2)
            elif (i - i0 == 1 or j - j0 == 1 or (di + dj == cut + 1 and (i - i0 < 3 or j - j0 < 3))) and i + j < i1 + j1 - 1:
                s.px(rect, i, j, FOG, lv + 1)
            elif i1 - i == 1 or j1 - j == 1:
                s.px(rect, i, j, FOG, lv - 1)


def fog_face(s: Skin, rect, lv=2, seams_=True):
    """黑雾的一块甲：发暗紫的黑，棱边泛紫（上、左两边亮两级），面上是切了角的甲片，大面分成两三块。"""
    w, h = rect[2], rect[3]
    s.fill(rect, FOG, lv)
    if seams_ and w >= 5 and h >= 5:
        nx = 1 if w < 11 else 2 if w < 22 else 3
        ny = 1 if h < 10 else 2 if h < 20 else 3
        xs = [round(k * w / nx) for k in range(nx + 1)]
        ys = [round(k * h / ny) for k in range(ny + 1)]
        for a in range(nx):
            for b in range(ny):
                i0, i1 = xs[a] + (1 if a == 0 else 0), xs[a + 1] - (2 if a == nx - 1 else 1)
                j0, j1 = ys[b] + (1 if b == 0 else 0), ys[b + 1] - (2 if b == ny - 1 else 1)
                if i1 - i0 >= 3 and j1 - j0 >= 3:
                    chamfer(s, rect, i0, j0, i1, j1, lv, 2 if min(i1 - i0, j1 - j0) >= 6 else 1)
    # 棱边
    if h >= 2:
        s.row(rect, 0, FOG, lv + 2)
        s.row(rect, -1, FOG, max(0, lv - 1))
    if w >= 2:
        s.col(rect, 0, FOG, lv + 2, 1 if h >= 2 else 0)
        s.col(rect, -1, FOG, max(0, lv - 1), 1 if h >= 2 else 0, -2 if h >= 2 else -1)


def fog_box(s: Skin, u, v, w, h, d, lv=2, seams_=True) -> dict:
    f = faces(u, v, w, h, d)
    for key, rect in f.items():
        fog_face(s, rect, lv + (1 if key == "top" else -1 if key == "bottom" else 0), seams_)
    return f


def fog_seam(s: Skin, rect, i, j, strength=0.8):
    """一格发光的缝：底图是暗红的裂口，发光层点亮。"""
    s.px(rect, i, j, CRIMSON, 1)
    s.glow(rect, i, j, strength)


def fog_lines(s: Skin, rect, horizontal=True, step=3, strength=0.85, start=1):
    """隔几行（列）一道发光的接缝，两头收进棱边里。"""
    w, h = rect[2], rect[3]
    if horizontal:
        for j in range(start, h - 1, step):
            for i in range(1, w - 1):
                fog_seam(s, rect, i, j, strength * (0.75 if i in (1, w - 2) else 1.0))
    else:
        for i in range(start, w - 1, step):
            for j in range(1, h - 1):
                fog_seam(s, rect, i, j, strength * (0.75 if j in (1, h - 2) else 1.0))


def fog_fill(s: Skin, rect, strength=1.0, falloff=True):
    """整块发光（核心、喷口、传送门）：底图是暗红到深红，中间亮。"""
    w, h = rect[2], rect[3]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    rad = max(1.0, max(w, h) / 2)
    for j in range(h):
        for i in range(w):
            t = min(1.0, math.hypot(i - cx, j - cy) / rad) if falloff else 0
            s.px(rect, i, j, CRIMSON, 2 if t < 0.4 else 1 if t < 0.8 else 0)
            s.glow(rect, i, j, max(0.3, strength * (1 - 0.6 * t)))


def fog_eye(s: Skin, rect):
    """独眼：深色的玻璃，中间一道亮缝。"""
    w, h = rect[2], rect[3]
    s.fill(rect, FOG, 0)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for j in range(h):
        for i in range(w):
            dx = abs(i - cx) / max(1.0, w / 2)
            dy = abs(j - cy) / max(1.0, h / 2)
            v = 1 - dx * 0.55 - dy * 0.7
            if v > 0.55:
                s.px(rect, i, j, CRIMSON, 2)
            s.glow(rect, i, j, max(0.25, v))


def fog_vents(s: Skin, rect):
    """散热口：一道道深槽，槽下沿泛紫。"""
    for j in range(1, rect[3] - 1, 2):
        s.row(rect, j, FOG, 0, 1, -2)
        if j + 1 < rect[3] - 1:
            s.row(rect, j + 1, FOG, 3, 1, -2)


def fog_ring(s: Skin, rect, radius, strength=0.8):
    """面上一圈发光的环。"""
    w, h = rect[2], rect[3]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for j in range(h):
        for i in range(w):
            if abs(math.hypot(i - cx, j - cy) - radius) < 0.6:
                fog_seam(s, rect, i, j, strength)


def fog_border(s: Skin, rect, strength=0.9, inset=0):
    w, h = rect[2], rect[3]
    for j in range(inset, h - inset):
        for i in range(inset, w - inset):
            if i in (inset, w - 1 - inset) or j in (inset, h - 1 - inset):
                fog_seam(s, rect, i, j, strength)


@draw_cached
def fog_raider():
    s = Skin(64, 64, seed("dark_fog_raider"))
    body = fog_box(s, 0, 0, 12, 4, 14)
    for key in ("left", "right"):
        fog_lines(s, body[key], horizontal=False, step=3, strength=0.7, start=2)
    front = body["front"]
    fog_eye(s, (front[0] + 4, front[1] + 1, 4, 2))
    s.px(front, 3, 1, FOG, 4)
    s.px(front, 8, 1, FOG, 4)
    dome = fog_box(s, 0, 18, 8, 3, 8, lv=3, seams_=False)
    fog_ring(s, dome["top"], 2.3, 0.8)
    s.px(dome["top"], 3, 3, FOG, 5, glint=True)
    prong = fog_box(s, 32, 18, 2, 2, 6, seams_=False)
    fog_fill(s, prong["front"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        rect = prong[key]
        if rect[2] > rect[3]:
            fog_seam(s, rect, 0 if key in ("right", "bottom", "top") else -1, 0, 0.6)
    plate = fog_box(s, 0, 30, 10, 1, 10, lv=1, seams_=False)
    fog_fill(s, plate["bottom"], 0.95)
    pb = plate["bottom"]
    for j in range(pb[3]):
        for i in range(pb[2]):
            if (i + j) % 4 == 0 and 1 <= i < 9 and 1 <= j < 9:
                s.px(pb, i, j, FOG, 1)
    vane = fog_box(s, 40, 30, 1, 3, 1, lv=3, seams_=False)
    fog_seam(s, vane["bottom"], 0, 0, 0.8)
    return s


@draw_cached
def fog_ranger():
    s = Skin(64, 32, seed("dark_fog_ranger"))
    body = fog_box(s, 0, 0, 4, 3, 12)
    fog_fill(s, body["back"], 1.0)
    for key in ("left", "right"):
        rect = body[key]
        for i in range(1, rect[2] - 1):
            if i % 4 != 0:
                fog_seam(s, rect, i, 1, 0.6)
    s.col(body["top"], 1, FOG, 4, 1, -2)
    nose = fog_box(s, 32, 0, 2, 2, 3, lv=1, seams_=False)
    fog_eye(s, nose["front"])
    fin = fog_box(s, 32, 8, 1, 3, 4, lv=3, seams_=False)
    for key in ("left", "right"):
        fog_seam(s, fin[key], 0 if key == "right" else -1, 0, 0.6)
    wing = fog_box(s, 0, 16, 7, 1, 6, lv=2, seams_=False)
    for key in ("top", "bottom"):
        rect = wing[key]
        for i in range(rect[2]):
            fog_seam(s, rect, i, -1, 0.7)
        s.glow(rect, -1, 0, 1.0)
        for j in range(1, rect[3] - 1):
            s.px(rect, 2 + j // 2, j, FOG, 0)
    return s


@draw_cached
def fog_guardian():
    s = Skin(64, 64, seed("dark_fog_guardian"))
    core = fog_box(s, 0, 0, 10, 10, 10)
    for key in SIDES + ("top",):
        rect = core[key]
        w, h = rect[2], rect[3]
        # 十字形的发光接缝，交点上一块亮的核心
        for i in range(1, w - 1):
            fog_seam(s, rect, i, h // 2, 0.75)
        for j in range(1, h - 1):
            fog_seam(s, rect, w // 2, j, 0.75)
        fog_seam(s, rect, w // 2 - 1, h // 2 - 1, 0.95)
        fog_seam(s, rect, w // 2, h // 2, 1.0)
    eye = fog_box(s, 40, 0, 4, 3, 1, lv=1, seams_=False)
    fog_eye(s, eye["front"])
    shield = fog_box(s, 0, 20, 8, 12, 2, lv=3)
    sh = shield["front"]
    for j in range(2, sh[3] - 2):
        if j % 3 != 0:
            fog_seam(s, sh, sh[2] // 2, j, 0.85)
            s.px(sh, sh[2] // 2 - 1, j, FOG, 1)
    for k in range(2, 6):
        s.px(sh, k - 1, k - 1, FOG, 5, glint=True)
    spike = fog_box(s, 24, 20, 3, 4, 3, lv=1, seams_=False)
    fog_fill(s, spike["bottom"], 1.0, falloff=False)
    for key in SIDES:
        fog_seam(s, spike[key], 1, -1, 0.7)
    return s


@draw_cached
def fog_core():
    s = Skin(64, 64, seed("dark_fog_core"))
    base = fog_box(s, 0, 0, 16, 3, 16, lv=1)
    for key in SIDES:
        fog_vents(s, base[key])
    fog_ring(s, base["top"], 6.5, 0.6)
    column = fog_box(s, 0, 19, 8, 11, 8)
    for key in SIDES:
        fog_lines(s, column[key], horizontal=False, step=3, strength=0.85, start=2)
    crown = fog_box(s, 0, 38, 12, 3, 12, lv=3)
    for key in SIDES:
        rect = crown[key]
        for i in range(1, rect[2] - 1):
            fog_seam(s, rect, i, 1, 0.8)
    fog_ring(s, crown["top"], 3.5, 0.7)
    spire = fog_box(s, 48, 38, 2, 5, 2, lv=1, seams_=False)
    fog_fill(s, spire["top"], 1.0, falloff=False)
    for key in SIDES:
        for i in range(2):
            fog_seam(s, spire[key], i, 0, 0.9)
            fog_seam(s, spire[key], i, 2, 0.6)
    ring = faces(0, 53, 10, 1, 10)
    for key, rect in ring.items():
        s.fill(rect, FOG, 1)
        w, h = rect[2], rect[3]
        for j in range(h):
            for i in range(w):
                if key in ("top", "bottom"):
                    if i in (0, w - 1) or j in (0, h - 1):
                        fog_seam(s, rect, i, j, 0.9)
                    elif i in (1, w - 2) or j in (1, h - 2):
                        s.px(rect, i, j, FOG, 3)
                else:
                    fog_seam(s, rect, i, j, 0.9 if i % 2 == 0 else 0.5)
    return s


@draw_cached
def fog_camp():
    s = Skin(64, 64, seed("dark_fog_camp"))
    platform = fog_box(s, 0, 0, 16, 2, 16, lv=1)
    top = platform["top"]
    cx, cy = 7.5, 7.5
    for j in range(16):
        for i in range(16):
            d = max(abs(i - cx) * 0.87 + abs(j - cy) * 0.5, abs(j - cy))
            if abs(d - 5.5) < 0.5:
                fog_seam(s, top, i, j, 0.7)
            elif d < 2.0:
                s.px(top, i, j, FOG, 0)
    pillar = fog_box(s, 0, 18, 2, 10, 2, lv=2, seams_=False)
    for key in SIDES:
        for j in range(1, 9, 3):
            fog_seam(s, pillar[key], 0, j, 0.8)
    frame = fog_box(s, 0, 30, 14, 2, 14, lv=2)
    fb = frame["bottom"]
    for j in range(3, 11):
        for i in range(3, 11):
            fog_seam(s, fb, i, j, 0.45 if (i + j) % 2 else 0.6)
    portal = faces(0, 46, 6, 6, 6)
    for key, rect in portal.items():
        fog_fill(s, rect, 1.0)
    return s


@draw_cached
def fog_laser_tower():
    s = Skin(64, 64, seed("dark_fog_laser_tower"))
    base = fog_box(s, 0, 0, 12, 4, 12, lv=1)
    for key in SIDES:
        fog_vents(s, base[key])
    mast = fog_box(s, 0, 16, 4, 20, 4)
    for key in SIDES:
        rect = mast[key]
        for j in range(2, rect[3] - 1, 4):
            for i in range(1, rect[2] - 1):
                fog_seam(s, rect, i, j, 0.7)
    head = fog_box(s, 16, 16, 8, 6, 8, lv=3)
    for key in ("left", "right", "back"):
        fog_vents(s, head[key])
    s.row(head["front"], 1, FOG, 5, 1, -2)
    lens = fog_box(s, 48, 0, 3, 3, 2, lv=1, seams_=False)
    fog_fill(s, lens["front"], 1.0, falloff=False)
    s.px(lens["front"], 1, 1, CRIMSON, 3)
    return s


@draw_cached
def fog_plasma_sentry():
    s = Skin(64, 64, seed("dark_fog_plasma_sentry"))
    base = fog_box(s, 0, 0, 16, 3, 16, lv=1)
    fog_ring(s, base["top"], 6.0, 0.5)
    body = fog_box(s, 0, 19, 10, 9, 10)
    for key in SIDES:
        rect = body[key]
        for j in range(2, rect[3] - 2, 2):
            for i in range(2, rect[2] - 2, 2):
                fog_seam(s, rect, i, j, 0.6)
    head = fog_box(s, 0, 38, 8, 4, 8, lv=3)
    s.row(head["front"], 1, FOG, 0, 1, -2)
    barrel = fog_box(s, 32, 38, 3, 3, 9, lv=1, seams_=False)
    fog_fill(s, barrel["front"], 1.0, falloff=False)
    for key in ("left", "right", "top"):
        rect = barrel[key]
        for i in range(0, rect[2], 3):
            for j in range(rect[3]):
                s.glow(rect, i, j, 0.45)
                s.px(rect, i, j, CRIMSON, 0)
    return s


@draw_cached
def fog_relay():
    s = Skin(64, 64, seed("dark_fog_relay"))
    hull = fog_box(s, 0, 0, 12, 4, 12)
    for key in SIDES:
        fog_lines(s, hull[key], horizontal=True, step=2, strength=0.6)
    fog_ring(s, hull["top"], 3.5, 0.6)
    arm = fog_box(s, 0, 16, 2, 2, 6, lv=3, seams_=False)
    fog_fill(s, arm["back"], 1.0, falloff=False)
    emitter = fog_box(s, 16, 16, 4, 3, 4, lv=1, seams_=False)
    fog_fill(s, emitter["bottom"], 1.0)
    fin = fog_box(s, 32, 16, 1, 3, 6, lv=3, seams_=False)
    for key in ("left", "right"):
        rect = fin[key]
        for i in range(rect[2]):
            fog_seam(s, rect, i, 0, 0.8)
    return s


@draw_cached
def fog_seed():
    s = Skin(64, 64, seed("dark_fog_seed"))
    shell = fog_box(s, 0, 0, 8, 8, 8, seams_=False)
    for key, rect in shell.items():
        w, h = rect[2], rect[3]
        for i in range(1, w - 1):
            fog_seam(s, rect, i, h // 2, 0.75)
        for j in range(1, h - 1):
            fog_seam(s, rect, w // 2, j, 0.75)
        # 四个角上的甲片隆起
        for i, j in ((2, 2), (5, 2), (2, 5), (5, 5)):
            s.px(rect, i, j, FOG, 4, glint=True)
    spike = fog_box(s, 0, 16, 2, 2, 6, lv=3, seams_=False)
    fog_fill(s, spike["back"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        rect = spike[key]
        if rect[2] >= rect[3]:
            for i in range(rect[2]):
                s.glow(rect, i, 0, 0.3 + 0.5 * (i / max(1, rect[2] - 1)) if key == "right" else 0.3 + 0.5 * (1 - i / max(1, rect[2] - 1)))
                s.px(rect, i, 0, FOG, 4)
        else:
            for j in range(rect[3]):
                s.glow(rect, 0, j, 0.3 + 0.5 * (j / max(1, rect[3] - 1)))
                s.px(rect, 0, j, FOG, 4)
    ring = faces(0, 24, 14, 1, 14)
    for key, rect in ring.items():
        s.fill(rect, FOG, 1)
        w, h = rect[2], rect[3]
        for j in range(h):
            for i in range(w):
                if key in ("top", "bottom"):
                    d = max(abs(i - (w - 1) / 2), abs(j - (h - 1) / 2))
                    if d > w / 2 - 1.5:
                        fog_seam(s, rect, i, j, 0.95)
                    elif d > w / 2 - 2.5:
                        s.px(rect, i, j, FOG, 3)
                else:
                    fog_seam(s, rect, i, j, 0.9 if i % 3 else 0.4)
    pod = fog_box(s, 32, 0, 4, 4, 4, lv=3, seams_=False)
    for key in SIDES:
        fog_lines(s, pod[key], horizontal=True, step=2, strength=0.7)
    fog_fill(s, pod["top"], 0.9)
    fog_fill(s, pod["bottom"], 0.9)
    return s


@draw_cached
def fog_lancer():
    s = Skin(64, 64, seed("dark_fog_lancer"))
    body = fog_box(s, 0, 0, 6, 4, 20)
    for key in ("left", "right"):
        rect = body[key]
        for i in range(1, rect[2] - 1):
            fog_seam(s, rect, i, 2, 0.7)
    top = body["top"]
    for j in range(1, top[3] - 1):
        if j % 4 != 0:
            fog_seam(s, top, 3, j, 0.55)
    front = body["front"]
    fog_eye(s, (front[0] + 1, front[1] + 1, 4, 2))
    spine = fog_box(s, 0, 24, 4, 2, 9, lv=3, seams_=False)
    st = spine["top"]
    for j in range(1, 4):
        for i in range(1, 3):
            s.px(st, i, j, FOG, 0)
            s.glow(st, i, j, 0.45)
    s.px(st, 1, 1, FOG, 5, glint=True)
    engine = fog_box(s, 52, 0, 4, 3, 2, lv=1, seams_=False)
    fog_fill(s, engine["back"], 1.0)
    prong = faces(28, 24, 1, 1, 10)
    for key, rect in prong.items():
        s.fill(rect, FOG, 1)
        w, h = rect[2], rect[3]
        for j in range(h):
            for i in range(w):
                if key in ("top", "bottom"):
                    t = j / max(1, h - 1)
                elif key == "right":
                    t = i / max(1, w - 1)
                else:
                    t = 1 - i / max(1, w - 1)
                if t > 0.45:
                    fog_seam(s, rect, i, j, 0.4 + 0.6 * t)
    fog_fill(s, prong["front"], 1.0, falloff=False)
    wing = fog_box(s, 0, 36, 12, 1, 8, lv=2, seams_=False)
    for key in ("top", "bottom"):
        rect = wing[key]
        for i in range(rect[2]):
            fog_seam(s, rect, i, 0, 0.6)
        for j in range(rect[3]):
            s.glow(rect, 0, j, 0.9)
            s.px(rect, 0, j, CRIMSON, 1)
        for k in range(2, rect[2] - 2, 5):
            for j in range(1, rect[3] - 1):
                i = k + j // 2
                if i < rect[2] - 1:
                    s.px(rect, i, j, FOG, 0)
                    if i + 1 < rect[2] - 1:
                        s.px(rect, i + 1, j, FOG, 3)
    fin = fog_box(s, 40, 36, 1, 5, 5, lv=3, seams_=False)
    for key in ("left", "right"):
        rect = fin[key]
        for i in range(rect[2]):
            fog_seam(s, rect, i, 0, 0.75)
    return s


@draw_cached
def fog_humpback():
    s = Skin(128, 128, seed("dark_fog_humpback"))
    hull = fog_box(s, 0, 0, 20, 12, 40)
    for key in ("left", "right"):
        rect = hull[key]
        for i in range(3, rect[2] - 3, 4):
            for j in (3, 4):
                fog_seam(s, rect, i, j, 0.75)
                fog_seam(s, rect, i + 1, j, 0.75)
        s.row(rect, -3, FOG, 0, 1, -2)
        s.row(rect, -2, FOG, 3, 1, -2)
    top = hull["top"]
    for j in range(4, top[3] - 4, 6):
        s.row(top, j, FOG, 1, 2, -3)
        s.row(top, j + 1, FOG, 4, 2, -3)
    for i in range(2, top[2] - 2, 6):
        s.col(top, i, FOG, 1, 2, -3)
    front = hull["front"]
    for i in range(3, front[2] - 3):
        fog_seam(s, front, i, -4, 0.9)
    fog_eye(s, (front[0] + 2, front[1] + 3, 4, 2))
    fog_eye(s, (front[0] + front[2] - 6, front[1] + 3, 4, 2))
    back = hull["back"]
    fog_vents(s, (back[0] + 2, back[1] + 2, back[2] - 4, back[3] - 4))
    belly = fog_box(s, 0, 52, 16, 3, 30, lv=1)
    bb = belly["bottom"]
    for i in range(2, bb[2] - 2, 3):
        s.col(bb, i, FOG, 0, 1, -2)
        s.col(bb, i + 1, FOG, 2, 1, -2)
    for j in range(bb[3] // 2 - 2, bb[3] // 2 + 2):
        for i in range(bb[2] // 2 - 2, bb[2] // 2 + 2):
            fog_seam(s, bb, i, j, 0.8)
    hump = fog_box(s, 0, 86, 8, 3, 8, lv=3, seams_=False)
    for key in SIDES:
        rect = hump[key]
        for i in range(1, rect[2] - 1):
            fog_seam(s, rect, i, 1, 0.55)
    turret = fog_box(s, 32, 86, 6, 3, 6, lv=1, seams_=False)
    fog_eye(s, (turret["front"][0] + 1, turret["front"][1] + 1, 4, 1))
    barrel = fog_box(s, 56, 86, 1, 1, 8, lv=1, seams_=False)
    fog_fill(s, barrel["front"], 1.0, falloff=False)
    engine = fog_box(s, 92, 52, 5, 5, 3, lv=1, seams_=False)
    fog_fill(s, engine["back"], 1.0)
    fin = fog_box(s, 0, 98, 14, 2, 12, lv=2, seams_=False)
    for key in ("top", "bottom"):
        rect = fin[key]
        for i in range(1, rect[2] - 1, 2):
            s.px(rect, i, 1, FOG, 5, glint=True)
        for i in range(rect[2]):
            fog_seam(s, rect, i, -1, 0.6)
        s.glow(rect, 0, -1, 1.0)
    fluke = fog_box(s, 52, 98, 24, 2, 9, lv=2, seams_=False)
    for key in ("top", "bottom"):
        rect = fluke[key]
        w, h = rect[2], rect[3]
        cx = (w - 1) / 2
        for i in range(w):
            notch = int(round(2.5 * math.exp(-((i - cx) / 2.5) ** 2)))
            fog_seam(s, rect, i, h - 1 - notch, 0.7)
    return s


def sides(f):
    return [f[k] for k in SIDES]


@draw_cached
def hive_core():
    s = Skin(128, 128, seed("hive_core"))
    base = fog_box(s, 0, 0, 24, 6, 24, lv=1)
    for rect in sides(base):
        fog_vents(s, rect)
    fog_ring(s, base["top"], 9.5)
    fog_ring(s, base["bottom"], 7)
    column = fog_box(s, 0, 30, 16, 12, 16)
    for rect in sides(column):
        fog_lines(s, rect, horizontal=False, step=3, strength=0.85, start=2)
    crown = fog_box(s, 0, 58, 20, 3, 20, lv=3)
    for rect in sides(crown):
        for i in range(1, rect[2] - 1):
            fog_seam(s, rect, i, 1, 0.85)
    fog_ring(s, crown["top"], 7.5)
    fog_ring(s, crown["top"], 4, 0.6)
    spire = fog_box(s, 96, 0, 4, 10, 4, lv=1, seams_=False)
    fog_fill(s, spire["top"], 1.0, falloff=False)
    for rect in sides(spire):
        for j in range(0, rect[3], 2):
            fog_seam(s, rect, rect[2] // 2, j, 0.9)
    keel = fog_box(s, 96, 14, 8, 8, 8, lv=1, seams_=False)
    fog_fill(s, keel["bottom"], 1.0)
    rib = fog_box(s, 64, 30, 2, 11, 4, lv=3, seams_=False)
    for key in ("left", "right"):
        fog_lines(s, rib[key], horizontal=True, step=3, strength=0.6)
    rib2 = fog_box(s, 80, 30, 4, 11, 2, lv=3, seams_=False)
    for key in ("front", "back"):
        fog_lines(s, rib2[key], horizontal=True, step=3, strength=0.6)
    for u, v, w, h, d in ((0, 81, 32, 2, 3), (0, 86, 3, 2, 26)):
        bar = faces(u, v, w, h, d)
        for key, rect in bar.items():
            s.fill(rect, FOG, 1)
            rw, rh = rect[2], rect[3]
            for j in range(rh):
                for i in range(rw):
                    if key in ("top", "bottom"):
                        if (i + j) % 3 == 0:
                            fog_seam(s, rect, i, j, 0.85)
                        else:
                            s.px(rect, i, j, FOG, 3 if j == 0 else 1)
                    else:
                        fog_seam(s, rect, i, j, 0.9 if i % 2 == 0 else 0.45)
    return s


@draw_cached
def hive_photon_receiver():
    s = Skin(128, 128, seed("hive_photon_receiver"))
    mount = fog_box(s, 0, 0, 10, 6, 10, lv=1)
    for rect in sides(mount):
        fog_vents(s, rect)
    fog_box(s, 40, 0, 4, 6, 4, seams_=False)
    plate = fog_box(s, 0, 16, 20, 20, 2, lv=3, seams_=False)
    # 接收盘的正面：同心的发光环，环之间是暗的晶格
    pf = plate["front"]
    cx, cy = (pf[2] - 1) / 2, (pf[3] - 1) / 2
    for j in range(pf[3]):
        for i in range(pf[2]):
            d = math.hypot(i - cx, j - cy)
            if d < 9.5:
                if abs(d - round(d / 3) * 3) < 0.5:
                    fog_seam(s, pf, i, j, 0.95 - d * 0.03)
                elif i % 5 == 2 and j % 5 == 2:
                    fog_seam(s, pf, i, j, 0.6)
                else:
                    s.px(pf, i, j, FOG, 1)
    fog_vents(s, plate["back"])
    for u, v, w, h, d in ((0, 40, 24, 2, 3), (56, 16, 2, 20, 3)):
        rim = fog_box(s, u, v, w, h, d, lv=1, seams_=False)
        for key in ("front", "top", "bottom", "left", "right"):
            rect = rim[key]
            for i in range(0, rect[2], 2):
                fog_seam(s, rect, i, 0, 0.5)
    rod = faces(0, 46, 2, 2, 9)
    for key, rect in rod.items():
        s.fill(rect, FOG, 4)
        fog_lines(s, rect, horizontal=False, step=2, strength=0.5, start=0)
    tip = faces(24, 46, 4, 4, 2)
    for key, rect in tip.items():
        fog_fill(s, rect, 1.0)
    return s


@draw_cached
def hive_plasma_tower():
    s = Skin(128, 128, seed("hive_plasma_tower"))
    base = fog_box(s, 0, 0, 16, 4, 16, lv=1)
    for rect in sides(base):
        fog_vents(s, rect)
    fog_ring(s, base["top"], 6.5)
    body = fog_box(s, 0, 20, 10, 10, 10)
    for rect in sides(body):
        for j in range(2, rect[3] - 2, 2):
            for i in range(2, rect[2] - 2, 2):
                fog_seam(s, rect, i, j, 0.6)
    collar = fog_box(s, 64, 0, 14, 2, 14, lv=3, seams_=False)
    for rect in sides(collar):
        for i in range(0, rect[2], 2):
            fog_seam(s, rect, i, 0, 0.7)
    turret = fog_box(s, 0, 40, 12, 6, 12, lv=3)
    for key in ("left", "right", "back"):
        fog_vents(s, turret[key])
    fog_border(s, turret["top"], 0.6, inset=2)
    barrel = fog_box(s, 48, 20, 4, 4, 14, lv=1, seams_=False)
    fog_fill(s, barrel["front"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        rect = barrel[key]
        long_x = rect[2] > rect[3]
        for k in range(0, max(rect[2], rect[3]), 3):
            for m in range(min(rect[2], rect[3])):
                i, j = (k, m) if long_x else (m, k)
                s.px(rect, i, j, CRIMSON, 0)
                s.glow(rect, i, j, 0.5)
    coil = faces(84, 20, 6, 6, 2)
    for key, rect in coil.items():
        s.fill(rect, FOG, 4)
        fog_border(s, rect, 0.8)
    return s


@draw_cached
def hive_phase_laser():
    s = Skin(128, 128, seed("hive_phase_laser"))
    base = fog_box(s, 0, 0, 12, 3, 12, lv=1)
    fog_ring(s, base["top"], 4.5)
    mast = fog_box(s, 48, 0, 4, 12, 4)
    for rect in sides(mast):
        for j in range(2, rect[3] - 1, 3):
            for i in range(1, rect[2] - 1):
                fog_seam(s, rect, i, j, 0.7)
    for u, v, w, h, d in ((64, 0, 1, 8, 3), (72, 0, 3, 8, 1)):
        vane = fog_box(s, u, v, w, h, d, lv=3, seams_=False)
        for key, rect in vane.items():
            for j in range(0, rect[3], 2):
                fog_seam(s, rect, 0, j, 0.6)
    housing = fog_box(s, 0, 16, 8, 6, 8, lv=3)
    for key in ("left", "right", "back"):
        fog_vents(s, housing[key])
    lens = fog_box(s, 32, 16, 3, 3, 4, lv=1, seams_=False)
    fog_fill(s, lens["front"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        fog_lines(s, lens[key], horizontal=False, step=2, strength=0.5, start=0)
    emitter = fog_box(s, 48, 16, 2, 2, 5, lv=1, seams_=False)
    fog_fill(s, emitter["front"], 1.0, falloff=False)
    return s


@draw_cached
def hive_lancer_port():
    s = Skin(128, 128, seed("hive_lancer_port"))
    housing = fog_box(s, 0, 0, 20, 16, 12)
    for key in ("left", "right"):
        fog_lines(s, housing[key], horizontal=True, step=3, strength=0.6, start=2)
    fog_vents(s, housing["back"])
    top = fog_box(s, 0, 28, 24, 4, 4, lv=3, seams_=False)
    bottom = fog_box(s, 0, 36, 24, 2, 4, lv=3, seams_=False)
    # 门框朝外的一面：一排导引灯
    for frame in (top, bottom):
        rect = frame["front"]
        for i in range(1, rect[2] - 1, 2):
            fog_seam(s, rect, i, rect[3] // 2, 0.9)
    post = fog_box(s, 64, 0, 4, 16, 4, lv=2, seams_=False)
    for key in ("front", "left", "right"):
        rect = post[key]
        for j in range(1, rect[3] - 1, 2):
            fog_seam(s, rect, rect[2] // 2, j, 0.85)
    portal = faces(0, 42, 16, 16, 1)
    for key, rect in portal.items():
        fog_fill(s, rect, 1.0)
    pf = portal["front"]
    for k in range(3):
        r = 2.5 + k * 2.6
        for j in range(16):
            for i in range(16):
                if abs(math.hypot(i - 7.5, j - 7.5) - r) < 0.5:
                    s.px(pf, i, j, CRIMSON, 3)
    fin = fog_box(s, 80, 0, 2, 3, 8, lv=1, seams_=False)
    for key in ("left", "right"):
        rect = fin[key]
        for i in range(rect[2]):
            fog_seam(s, rect, i, 0, 0.8)
    return s


@draw_cached
def hive_humpback_port():
    s = Skin(128, 128, seed("hive_humpback_port"))
    base = fog_box(s, 0, 0, 20, 3, 20, lv=1)
    for rect in sides(base):
        fog_vents(s, rect)
    fog_ring(s, base["bottom"], 7.5)
    hall = fog_box(s, 0, 23, 16, 10, 16)
    for key in ("left", "right", "back"):
        fog_lines(s, hall[key], horizontal=False, step=4, strength=0.6, start=2)
    ridge = fog_box(s, 64, 23, 6, 3, 18, lv=3, seams_=False)
    rt = ridge["top"]
    for j in range(rt[3]):
        fog_seam(s, rt, rt[2] // 2, j, 0.8)
    gate = faces(0, 49, 10, 7, 1)
    for key, rect in gate.items():
        fog_fill(s, rect, 1.0)
    pod = fog_box(s, 24, 49, 4, 5, 8, lv=3, seams_=False)
    for key in ("front", "back"):
        fog_fill(s, pod[key], 0.8)
    return s


# ============================================================ 外星 NPC（发光层不着色，画成带颜色的）

SCHOLAR_SKIN = register(ramp("hostiles_scholar_skin", 222, 0.045, n=7, lo=0.36, hi=0.88, shift=12))
# 商旅族的羽毛：级差比琥珀色阶小，一排排羽片看得出来又不花
PLUME = register(ramp("hostiles_plume", 74, 0.135, n=9, lo=0.42, hi=0.92, shift=14, peak=0.55))
INDIGO = register(ramp("hostiles_indigo", 284, 0.095, n=7, lo=0.18, hi=0.62, shift=10, peak=0.5))
ENAMEL = register(ramp("hostiles_enamel", 36, 0.170, n=7, lo=0.28, hi=0.80, shift=12, peak=0.55))
SUIT = register(ramp("hostiles_suit", 8, 0.028, n=6, lo=0.20, hi=0.52, shift=8))


def glowc(s: Skin, rect, i, j, r, lv, alpha=None):
    """带颜色的发光像素（外星 NPC 的发光层不着色）。"""
    p = s._at(rect, i, j)
    if p:
        s.g.set(p[0], p[1], r, lv, glint=True, alpha=alpha)


def vgrad(s: Skin, rect, r, top_lv, bottom_lv, edge=True):
    """从上往下按级数渐变；顶上一行亮一级，底下一行暗一级，像有体积。"""
    w, h = rect[2], rect[3]
    for j in range(h):
        t = j / max(1, h - 1)
        lv = round(top_lv + (bottom_lv - top_lv) * t)
        if edge and h > 2 and j == 0:
            lv += 1
        elif edge and h > 2 and j == h - 1:
            lv -= 1
        s.row(rect, j, r, lv)


def feathers(s: Skin, rect, r, lv, offset=0):
    """鱼鳞一样一排排的羽毛：每片 4 格宽、2 格高，排与排错开半片；片的上沿中间两格受光，下沿的接缝暗一级。"""
    w, h = rect[2], rect[3]
    for j in range(h):
        rowi = (j + offset) // 2
        top = (j + offset) % 2 == 0
        for i in range(w):
            k = (i + (rowi % 2) * 2) % 4
            # 羽片的纹样是故意排出来的，检查时不算零碎像素
            if top and k in (1, 2):
                s.px(rect, i, j, r, lv + 1, glint=True)
            elif not top and k == 0:
                s.px(rect, i, j, r, lv - 1, glint=True)
            else:
                s.px(rect, i, j, r, lv, glint=True)


def scales(s: Skin, rect, r, lv):
    """菱形的鳞（腿、脚）。"""
    for j in range(rect[3]):
        for i in range(rect[2]):
            edge = (i + j) % 3 == 0 or (i - j) % 3 == 0
            s.px(rect, i, j, r, lv - 1 if edge else lv, glint=True)


def hex_plates(s: Skin, rect, r, lv, size=3, phase=0):
    """一块块六角形的甲片：缝是深色的，片中间泛亮，越往下越暗。"""
    w, h = rect[2], rect[3]
    for j in range(h):
        rowi = (j + phase) // size
        shift_ = (rowi % 2) * (size // 2 + 1)
        dark = 1 if j > h * 0.6 else 0
        for i in range(w):
            lx = (i + shift_) % (size + 1)
            ly = (j + phase) % size
            if lx == size or ly == size - 1:
                s.px(rect, i, j, r, lv - 2)
            elif lx == size // 2 and ly == max(0, size // 2 - (1 if size > 3 else 0)):
                s.px(rect, i, j, r, lv + 2 - dark)
            else:
                s.px(rect, i, j, r, lv - dark)


GLYPHS = ((1, 0, 1, 1, 1, 0), (1, 1, 0, 0, 1, 1), (0, 1, 1, 1, 0, 1), (1, 0, 0, 1, 1, 1), (1, 1, 1, 0, 0, 1))


def glyph_band(s: Skin, rect, base, blv, ink, ilv, rows, alpha, vertical=False):
    """一条外星文字：银蓝色的笔画，按几种固定的字形排，发光层淡淡地亮。"""
    w, h = rect[2], rect[3]
    cells = []
    if vertical:
        for j in range(h):
            for i in range(w):
                cells.append((i, j, (j // 2) % len(GLYPHS), (i % 2) + (j % 2) * 2))
    else:
        for j in rows:
            for i in range(w):
                cells.append((i, j, (i // 3) % len(GLYPHS), (i % 3) + (j - rows[0]) * 3))
    for i, j, shape, bit in cells:
        on = GLYPHS[shape][bit % 6]
        s.px(rect, i, j, ink if on else base, ilv if on else blv)
        if on:
            glowc(s, rect, i, j, CYAN_GLOW, 4, alpha)


@draw_cached
def npc_trader():
    """商旅族：琥珀色的羽毛、奶白的脸、象牙色的喙、红到黄的头冠，青绿色的袍子金边，斜挎带上一枚金徽章，
    腰上皮挎包，橙黄的鳞腿。发光层只有青色的眼睛。"""
    s = Skin(64, 64, seed("alien_npc_trader"))
    f = faces(0, 0, 7, 7, 7)
    for key in ("back", "left", "right", "top"):
        feathers(s, f[key], PLUME, 5)
    s.fill(f["bottom"], CREAM, 2)
    front = f["front"]
    feathers(s, front, PLUME, 5)
    for j in range(1, 7):
        for i in range(7):
            if j >= 2 or i in (0, 3, 6):
                s.px(front, i, j, CREAM, 3 if j < 6 else 2)
    for i in (1, 2, 4, 5):
        s.px(front, i, 0, PLUME, 1)
    for ex in (1, 4):
        outer = ex == 1
        hi, pupil = ((ex, 1), (ex + 1, 2)) if outer else ((ex + 1, 1), (ex, 2))
        for i in (ex, ex + 1):
            for j in (1, 2):
                if (i, j) == hi:
                    s.px(front, i, j, GLASS, 5, glint=True)
                elif (i, j) == pupil:
                    s.px(front, i, j, GRAPHITE, 0)
                else:
                    s.px(front, i, j, TEAL, 4 if j == 1 else 3)
                    glowc(s, front, i, j, CYAN_GLOW, 4 if j == 1 else 3, 220)
    for i in (2, 3, 4):
        for j in (3, 4):
            s.px(front, i, j, BONE, 3)
    s.px(front, 0, 4, ORANGE, 4)
    s.px(front, 6, 4, ORANGE, 4)
    top = f["top"]
    for j in range(7):
        s.px(top, 3, j, RED, 4 if j < 3 else 3)
    for key in ("left", "right"):
        for j in (1, 2):
            for i in (3, 4, 5):
                s.px(f[key], i, j, PLUME, 1)
    # 上喙、下喙
    f = faces(28, 0, 3, 2, 4)
    for key in ("left", "right"):
        vgrad(s, f[key], BONE, 5, 4, edge=False)
    s.fill(f["top"], BONE, 6)
    s.fill(f["bottom"], BONE, 3)
    vgrad(s, f["front"], BONE, 5, 4, edge=False)
    s.px(f["front"], 1, 1, WOOD, 2)
    s.fill(f["back"], BONE, 3)
    for key in ("left", "right"):
        r_ = f[key]
        s.px(r_, 1, 0, WOOD, 1)
        for j in range(2):
            s.px(r_, 0 if key == "right" else 3, j, WOOD, 3)
    for i in range(3):
        s.px(f["top"], i, 0, WOOD, 3)
    f = faces(28, 6, 2, 1, 3)
    for key, r_ in f.items():
        s.fill(r_, BONE, 3)
    s.fill(f["front"], WOOD, 2)
    # 头冠：根上深红，往上、往后变成金黄
    f = faces(42, 0, 1, 3, 6)
    for key in ("left", "right"):
        x0, y0, w, h = f[key]
        for j in range(h):
            for i in range(w):
                t = 1 - j / (h - 1)
                rr, lv = (RED, 3) if t < 0.3 else (ORANGE, 4) if t < 0.7 else (HAZARD, 4)
                if (i + (1 if key == "left" else 0)) % 2 == 0:
                    lv -= 1
                s.px(f[key], i, j, rr, lv)
    for key in ("front", "back", "bottom"):
        s.fill(f[key], RED, 3)
    s.fill(f["top"], HAZARD, 4)
    f = faces(56, 0, 1, 2, 3)
    for key, r_ in f.items():
        s.row(r_, 0, PLUME, 5)
        if r_[3] > 1:
            s.row(r_, 1, ORANGE, 3)
    # 身子：胸前的羽毛，青绿的斜挎带、金边，带上一枚金徽章
    f = faces(0, 16, 8, 7, 5)
    for key in ("front", "back", "left", "right", "top"):
        feathers(s, f[key], PLUME, 5, 1)
    s.fill(f["bottom"], CREAM, 2)
    for i in range(1, 7):
        for j in range(5):
            s.px(f["front"], i, j + 1, CREAM, 3 if j < 4 else 2)
    for key, flip in (("front", False), ("back", True)):
        r_ = f[key]
        for j in range(7):
            i = j if not flip else 7 - j
            for dd in (-1, 0, 1):
                if dd == 0:
                    s.px(r_, i + dd, j, TEAL, 3)
                else:
                    s.px(r_, i + dd, j, BRASS, 5 if dd < 0 else 4)
    badge = f["front"]
    s.px(badge, 3, 3, BRASS, 6, glint=True)
    s.px(badge, 4, 3, BRASS, 5)
    s.px(badge, 3, 4, BRASS, 5)
    s.px(badge, 4, 4, BRASS, 3)
    for j in range(2):
        s.px(f["left"], 2, j, TEAL, 3)
        s.px(f["right"], 2, 6 - j, TEAL, 3)
    # 袍子的下摆：青绿，中间一行菱形花纹，下沿金边
    f = faces(0, 28, 9, 3, 6)
    for key in SIDES:
        r_ = f[key]
        for j in range(r_[3]):
            for i in range(r_[2]):
                rr, lv = TEAL, 3 if j == 0 else 2
                if j == 1 and i % 3 == 1:
                    lv = 4
                if j == r_[3] - 1:
                    rr, lv = BRASS, 5 if i % 2 == 0 else 4
                s.px(r_, i, j, rr, lv)
    s.fill(f["top"], TEAL, 1)
    s.fill(f["bottom"], TEAL, 1)
    # 挎包：皮的，盖子深一点，正面一个金扣
    f = faces(26, 16, 2, 3, 3)
    for key, r_ in f.items():
        vgrad(s, r_, WOOD, 4, 2, edge=False)
    for key in SIDES:
        s.row(f[key], 0, WOOD, 1)
    s.px(f["left"], 1, 1, BRASS, 6, glint=True)
    s.px(f["right"], 1, 1, BRASS, 6, glint=True)
    # 胳膊：羽毛，手是深色的三爪
    f = faces(36, 10, 2, 6, 2)
    for key in SIDES + ("top",):
        feathers(s, f[key], PLUME, 5)
    s.fill(f["bottom"], WOOD, 0)
    for key in SIDES:
        s.row(f[key], -1, ORANGE, 3)
        s.px(f[key], 0, -1, WOOD, 0)
    # 飞羽：一根根竖着的长羽，羽尖褐色
    f = faces(44, 10, 1, 6, 3)
    for key, r_ in f.items():
        for j in range(r_[3]):
            for i in range(r_[2]):
                if j >= r_[3] - 2:
                    s.px(r_, i, j, WOOD, 3 if j == r_[3] - 2 else 2)
                else:
                    s.px(r_, i, j, PLUME, 5 if i % 2 == 0 else 3)
    # 腿：上面两格是羽毛的"裤腿"，下面是鳞
    f = faces(52, 10, 2, 6, 2)
    for key in SIDES:
        x0, y0, w, h = f[key]
        scales(s, (x0, y0 + 2, w, h - 2), ORANGE, 4)
        feathers(s, (x0, y0, w, 2), PLUME, 5)
    s.fill(f["top"], PLUME, 1)
    s.fill(f["bottom"], ORANGE, 2)
    # 脚：三根脚趾，趾尖深色的爪
    f = faces(30, 28, 3, 1, 4)
    top = f["top"]
    for j in range(4):
        for i in range(3):
            if j == 0:
                s.px(top, i, j, WOOD, 0)
            else:
                s.px(top, i, j, ORANGE, 3 if (i + j) % 2 else 4)
    s.fill(f["bottom"], ORANGE, 2)
    for key in SIDES:
        s.fill(f[key], ORANGE, 4)
    for i in (0, 2):
        s.px(f["front"], i, 0, WOOD, 0)
    return s


@draw_cached
def npc_scholar():
    """学者族：淡蓝灰的皮肤和斑点、两侧的鳃缝；背上和头上是深蓝的六角甲壳；靛青长袍，前襟和下摆是银蓝的文字。
    发光层：三只眼、触须尖、文字、壳脊上的光点。"""
    s = Skin(64, 64, seed("alien_npc_scholar"))
    sk = SCHOLAR_SKIN
    f = faces(0, 0, 5, 7, 5)
    for key, r_ in f.items():
        s.fill(r_, sk, 4)
        for j in range(r_[3]):
            for i in range(r_[2]):
                if (i * 3 + j * 5 + (2 if key == "front" else 0)) % 7 == 0:
                    s.px(r_, i, j, sk, 3)
    front = f["front"]
    s.row(front, 0, sk, 3)
    s.px(front, 2, 1, GLASS, 5, glint=True)
    glowc(s, front, 2, 1, VIOLET_GLOW, 4, 230)
    for ex in (0, 3):
        for i in (ex, ex + 1):
            for j in (2, 3):
                s.px(front, i, j, SCREEN, 1)
        pupil = ex + 1 if ex == 0 else ex
        s.px(front, pupil, 2, CYAN_GLOW, 5, glint=True)
        glowc(s, front, pupil, 2, CYAN_GLOW, 4)
        s.px(front, pupil, 3, TEAL, 3)
        glowc(s, front, pupil, 3, CYAN_GLOW, 3, 120)
    s.row(front, 4, sk, 5)
    for i in (1, 2, 3):
        s.px(front, i, 6, sk, 2)
    for key in ("left", "right"):
        r_ = f[key]
        for i in (1, 2, 3):
            for j in (3, 4, 5):
                s.px(r_, i, j, sk, 2 if i % 2 == 1 else 5)
    # 壳盖
    f = faces(20, 0, 6, 3, 7)
    for key, r_ in f.items():
        hex_plates(s, r_, BLUE_SHELL, 3, 3, 1)
    for key in SIDES:
        s.row(f[key], -1, BLUE_SHELL, 5)
    # 触须：深色的杆，尖上发光
    f = faces(46, 0, 1, 3, 1)
    for key, r_ in f.items():
        vgrad(s, r_, BLUE_SHELL, 2, 1, edge=False)
    s.fill(f["top"], CYAN_GLOW, 5)
    glowc(s, f["top"], 0, 0, CYAN_GLOW, 4)
    for key in SIDES:
        s.px(f[key], 0, 0, CYAN_GLOW, 4)
        glowc(s, f[key], 0, 0, CYAN_GLOW, 3, 200)
    # 口器
    f = faces(50, 0, 3, 2, 1)
    for key, r_ in f.items():
        vgrad(s, r_, sk, 2, 1, edge=False)
    s.px(f["front"], 1, 1, SCREEN, 0)
    # 身子：靛青长袍，前襟一条银蓝文字，领口亮，腰上一道银带
    f = faces(0, 16, 5, 10, 4)
    for key in SIDES:
        vgrad(s, f[key], INDIGO, 3, 1)
        s.row(f[key], 0, INDIGO, 5)
        s.row(f[key], 7, GLASS, 3)
    s.fill(f["top"], sk, 2)
    s.fill(f["bottom"], INDIGO, 1)
    x0, y0, w, h = f["front"]
    glyph_band(s, (x0 + 2, y0 + 1, 1, 6), INDIGO, 3, GLASS, 4, None, 140, vertical=True)
    for j in range(1, 7):
        s.px(f["front"], 1, j, INDIGO, 2)
        s.px(f["front"], 3, j, INDIGO, 2)
    # 背上的壳：大块的六角甲片，背面亮、贴着身子的那面暗
    f = faces(18, 16, 7, 12, 3)
    hex_plates(s, f["back"], BLUE_SHELL, 4, 4)
    for key in ("left", "right", "top"):
        hex_plates(s, f[key], BLUE_SHELL, 3, 3)
    s.fill(f["front"], BLUE_SHELL, 1)
    s.fill(f["bottom"], BLUE_SHELL, 1)
    for key in ("left", "right"):
        s.col(f[key], -1 if key == "left" else 0, BLUE_SHELL, 5)
    # 壳脊：一节节的，每三节一个光点
    f = faces(38, 16, 1, 12, 1)
    for key, r_ in f.items():
        for j in range(r_[3]):
            s.px(r_, 0, j, BLUE_SHELL, 5 if j % 3 != 2 else 1)
            if j % 3 == 0 and key in SIDES:
                glowc(s, r_, 0, j, CYAN_GLOW, 4, 150)
    # 长袍下摆：靛青，下沿两行文字
    f = faces(20, 32, 7, 6, 5)
    for key in SIDES:
        vgrad(s, f[key], INDIGO, 3, 1)
        glyph_band(s, f[key], INDIGO, 1, GLASS, 4, (4, 5), 110)
    s.fill(f["top"], INDIGO, 3)
    s.fill(f["bottom"], INDIGO, 1)
    # 腿：一节节的，关节深色
    f = faces(0, 32, 2, 13, 2)
    for key in SIDES:
        vgrad(s, f[key], sk, 4, 3, edge=False)
        for j in (3, 7, 11):
            s.row(f[key], j, sk, 1)
    s.fill(f["top"], sk, 2)
    s.fill(f["bottom"], BLUE_SHELL, 1)
    f = faces(8, 32, 3, 1, 3)
    for key, r_ in f.items():
        s.fill(r_, BLUE_SHELL, 1)
    # 胳膊：上半截是袖子（银边袖口），下面是皮肤
    f = faces(44, 32, 2, 13, 2)
    for key in SIDES:
        x0, y0, w, h = f[key]
        vgrad(s, (x0, y0, w, 6), INDIGO, 3, 2, edge=False)
        s.row(f[key], 6, GLASS, 4)
        vgrad(s, (x0, y0 + 7, w, h - 7), sk, 4, 3, edge=False)
        s.row(f[key], 10, sk, 1)
    s.fill(f["top"], INDIGO, 3)
    s.fill(f["bottom"], sk, 2)
    f = faces(52, 32, 2, 2, 1)
    for key, r_ in f.items():
        vgrad(s, r_, sk, 2, 1, edge=False)
    return s


BLUE_SHELL = register(ramp("hostiles_shell", 252, 0.120, n=7, lo=0.22, hi=0.80, shift=10, peak=0.5))


def armor_plate(s: Skin, rect, r, lv, every=4, rivets=True):
    """甲板：横着的接缝，接缝上一排铆钉，上沿亮、下沿暗。"""
    w, h = rect[2], rect[3]
    vgrad(s, rect, r, lv, lv - 1)
    for j in range(every, h, every):
        s.row(rect, j, r, lv - 2)
        if j + 1 < h:
            s.row(rect, j + 1, r, lv + 1)
        if rivets:
            for i in range(1, w - 1, 3):
                s.px(rect, i, j - 1, r, lv + 2, glint=True)


def enamel(s: Skin, rect, rim=True):
    """红橙色的珐琅：左上亮、右下暗，下沿一圈枪灰。"""
    w, h = rect[2], rect[3]
    for j in range(h):
        for i in range(w):
            t = (i / max(1, w - 1) + j / max(1, h - 1)) / 2
            s.px(rect, i, j, ENAMEL, 5 if t < 0.25 else 4 if t < 0.6 else 3)
    if rim and w > 2 and h > 2:
        s.row(rect, -1, STEEL, 1)


@draw_cached
def npc_warden():
    """戍卫族：枪灰色的甲，红橙色的珐琅（胸甲、肩甲、护膝、盔冠），铆钉、接缝；面罩一道橙色的光缝，
    下颌两根骨白的獠牙。发光层：面罩和背甲的通风缝。"""
    s = Skin(128, 64, seed("alien_npc_warden"))
    gun = STEEL
    f = faces(0, 0, 12, 14, 7)
    for key in SIDES:
        armor_plate(s, f[key], gun, 3, 3)
    for key in ("left", "right"):
        r_ = f[key]
        for j in range(r_[3]):
            s.px(r_, r_[2] // 2, j, ENAMEL, 4 if j % 3 else 2)
    s.fill(f["top"], gun, 3)
    for i in range(4, 8):
        for j in range(2, 5):
            s.px(f["top"], i, j, SUIT, 2)
    s.fill(f["bottom"], SUIT, 1)
    # 胸甲：珐琅，正中一枚盾形徽记，四角铆钉
    f = faces(38, 0, 13, 8, 1)
    for key, r_ in f.items():
        enamel(s, r_, key in SIDES)
    front = f["front"]
    s.col(front, 0, ENAMEL, 2)
    s.col(front, -1, ENAMEL, 2)
    for i, j in ((0, 0), (12, 0), (0, 7), (12, 7)):
        s.px(front, i, j, BONE, 5, glint=True)
    for j in range(1, 7):
        half = 2 if j < 5 else (1 if j == 5 else 0)
        for i in range(6 - half, 7 + half):
            s.px(front, i, j, gun, 1 if abs(i - 6) == half else 3)
    for j in range(2, 6):
        s.px(front, 6, j, BONE, 5)
    for i in (5, 6, 7):
        s.px(front, i, 3, BONE, 5)
    # 腰带：皮的，一排铆钉，正面一个珐琅扣
    f = faces(0, 21, 13, 3, 8)
    for key, r_ in f.items():
        vgrad(s, r_, WOOD, 3, 2, edge=False)
    for key in SIDES:
        for i in range(1, f[key][2], 3):
            s.px(f[key], i, 1, gun, 5, glint=True)
    for i in range(5, 8):
        for j in range(3):
            s.px(f["front"], i, j, ENAMEL, 4 if (i, j) != (6, 1) else 2)
    s.px(f["front"], 6, 1, BONE, 5)
    # 背甲：枪灰，横着的通风缝透出橙光，正中一道红条
    f = faces(42, 21, 8, 9, 2)
    for key in ("left", "right", "top", "bottom", "front"):
        armor_plate(s, f[key], gun, 3, 4, False)
    back = f["back"]
    armor_plate(s, back, gun, 3, 9, False)
    for j in (2, 4, 6):
        for i in range(1, 7):
            if i in (3, 4):
                continue
            s.px(back, i, j, SUIT, 0)
            glowc(s, back, i, j, ORANGE_GLOW, 3, 150)
    for j in range(9):
        s.px(back, 3, j, ENAMEL, 4)
        s.px(back, 4, j, ENAMEL, 2)
    # 头盔：一道橙色的面罩光缝，下面是呼吸格栅，侧面一道红条
    f = faces(66, 0, 7, 7, 7)
    for key in ("back", "left", "right", "top"):
        armor_plate(s, f[key], gun, 3, 4)
    s.fill(f["bottom"], SUIT, 1)
    front = f["front"]
    vgrad(s, front, gun, 4, 2, edge=False)
    for i in range(7):
        s.px(front, i, 1, ENAMEL, 4 if i not in (0, 6) else 2)
    for i in range(1, 6):
        s.px(front, i, 2, ORANGE_GLOW, 5 if i == 3 else 4)
        glowc(s, front, i, 2, ORANGE_GLOW, 4 if i == 3 else 3)
        s.px(front, i, 3, ORANGE, 2)
        glowc(s, front, i, 3, ORANGE_GLOW, 3, 130)
    s.px(front, 0, 2, gun, 1)
    s.px(front, 6, 2, gun, 1)
    for i in (1, 3, 5):
        s.px(front, i, 5, gun, 1)
    s.row(front, 6, gun, 1)
    for key in ("left", "right"):
        r_ = f[key]
        for j in range(7):
            s.px(r_, 3, j, ENAMEL, 4 if j < 4 else 2)
        for i, j in ((4, 3), (5, 3), (4, 4), (5, 4)):
            s.px(r_, i if key == "left" else 6 - i, j, gun, 5 if (i + j) % 2 == 0 else 1)
    for j in range(7):
        s.px(f["top"], 3, j, ENAMEL, 2)
    # 盔冠
    f = faces(94, 0, 2, 2, 8)
    for key, r_ in f.items():
        enamel(s, r_, False)
    s.col(f["top"], 0, ENAMEL, 6)
    # 獠牙
    f = faces(114, 0, 1, 2, 1)
    for key, r_ in f.items():
        vgrad(s, r_, BONE, 5, 3, edge=False)
    # 胳膊：上面是深色的衣服和一道红条，下面是枪灰的护臂
    f = faces(62, 21, 4, 13, 4)
    for key in SIDES:
        x0, y0, w, h = f[key]
        vgrad(s, (x0, y0, w, 6), SUIT, 2, 1, edge=False)
        for j in range(2, 6):
            s.px(f[key], 1, j, ENAMEL, 2)
        armor_plate(s, (x0, y0 + 6, w, h - 6), gun, 3, 3)
    s.fill(f["top"], SUIT, 2)
    s.fill(f["bottom"], gun, 1)
    # 肩甲：珐琅，下沿一圈枪灰，顶上三道纹
    f = faces(78, 21, 7, 4, 7)
    for key, r_ in f.items():
        enamel(s, r_, key in SIDES)
    for i in (1, 3, 5):
        s.col(f["top"], i, ENAMEL, 5)
    for key in SIDES:
        s.px(f[key], 1, 1, BONE, 5, glint=True)
        s.px(f[key], 5, 1, BONE, 5, glint=True)
    # 护手：枪灰，正面一排指节
    f = faces(106, 21, 5, 4, 5)
    for key, r_ in f.items():
        armor_plate(s, r_, gun, 3, 5, False)
    s.fill(f["bottom"], gun, 1)
    for i in range(5):
        s.px(f["front"], i, 2, gun, 5 if i % 2 == 0 else 1)
    # 腿：深色的裤子，大腿和小腿上是甲板
    f = faces(0, 32, 4, 12, 4)
    for key in SIDES:
        x0, y0, w, h = f[key]
        vgrad(s, f[key], SUIT, 2, 1)
        armor_plate(s, (x0, y0, w, 4), gun, 3, 4, False)
        armor_plate(s, (x0, y0 + 8, w, 2), gun, 3, 4, False)
    s.fill(f["top"], SUIT, 2)
    s.fill(f["bottom"], gun, 1)
    # 护膝
    f = faces(16, 32, 5, 4, 1)
    for key, r_ in f.items():
        enamel(s, r_, False)
    s.px(f["front"], 2, 1, BONE, 5, glint=True)
    s.row(f["front"], -1, ENAMEL, 2)
    # 靴子
    f = faces(28, 32, 5, 2, 5)
    for key, r_ in f.items():
        s.fill(r_, gun, 3)
    for key in SIDES:
        s.row(f[key], -1, gun, 1)
    for i in range(1, 4):
        s.px(f["front"], i, 0, gun, 5)
    s.fill(f["bottom"], GRAPHITE, 0)
    return s


for _name, _fn in (("alien_npc_trader", npc_trader), ("alien_npc_scholar", npc_scholar),
                   ("alien_npc_warden", npc_warden)):
    def _reg(name=_name, fn=_fn):
        @texture(f"entity/{name}", refs=("entity/villager/villager", "entity/illager/vindicator"))
        def base(rng):
            return fn().c

        @texture(f"entity/{name}_glow")
        def glow_(rng):
            return fn().g
    _reg()


# ============================================================ 光斑：等离子弹、黑雾的光束（白色，渲染时着色）

@texture("entity/plasma_bolt", kind="entity", tile=False)
def plasma_bolt(rng):
    """等离子弹：白色的光斑，中心不透明、往外按平方衰减；渲染时按弹体的颜色着色。"""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.6
            if d >= 1:
                continue
            a = min(1.0, (1 - d) ** 1.5 * 1.35)
            lv = 5 if d < 0.35 else 4 if d < 0.6 else 3
            c.set(x, y, WHITE_GLOW, lv, alpha=int(255 * a))
    return c


@texture("entity/dark_fog_beam", kind="entity", tile=False)
def dark_fog_beam(rng):
    """黑雾的光束：横着从透明到亮再到透明，中间一道最亮的芯，竖着不变；渲染时按光束的颜色着色。"""
    c = Canvas(16, 16)
    for x in range(16):
        d = abs(x - 7.5) / 7.5
        a = max(0.0, 1 - d) ** 1.3
        lv = 5 if d < 0.2 else 4 if d < 0.5 else 3
        for y in range(16):
            if a > 0.02:
                c.set(x, y, WHITE_GLOW, lv, alpha=int(255 * a))
    return c


# ============================================================ 物品

def egg_canvas(r, lv, top_squeeze=0.2) -> Canvas:
    """刷怪蛋的蛋壳：原版那样 12 格宽、15 格高的蛋形，按椭球的法线打光，左上一个高光，选择性描边。"""
    c = Canvas()
    cx, cy = 7.5, 8.6
    for y, (x0, x1) in EGG_ROWS.items():
        for x in range(x0, x1 + 1):
            # 蛋的上半截窄：法线按这一行的半宽算
            half = (x1 - x0 + 1) / 2
            nx = (x - cx) / (half + 0.4)
            ny = (y - cy) / 7.8
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            v = nx * -0.58 + ny * -0.62 + nz * 0.53
            c.set(x, y, r, lv + round((v - 0.38) * 3.0))
    return c


# 原版刷怪蛋的轮廓：每行的起止列，12 格宽、15 格高，上窄下宽
EGG_ROWS = {1: (6, 9), 2: (5, 10), 3: (4, 11), 4: (4, 11), 5: (3, 12), 6: (3, 12), 7: (3, 12), 8: (2, 13),
            9: (2, 13), 10: (2, 13), 11: (2, 13), 12: (3, 12), 13: (3, 12), 14: (4, 11), 15: (6, 9)}


def egg_finish(c: Canvas, r):
    c.outline()
    c.set(5, 3, r, r.top, glint=True)
    c.set(4, 4, r, r.top - 1, glint=True)


def egg_visor(c: Canvas, y: int, glow_ramp, inset: int = 0):
    """蛋上一圈面罩：三行深色的玻璃带绕满蛋身（上沿受光、下沿最暗），中间一行亮着眼缝。"""
    for k, lv in ((0, 2), (1, 0), (2, 1)):
        x0, x1 = EGG_ROWS[y + k]
        for x in range(x0 + inset, x1 + 1 - inset):
            c.set(x, y + k, GRAPHITE, lv)
    x0, x1 = EGG_ROWS[y + 1]
    x0, x1 = x0 + 2 + inset, x1 - 2 - inset
    mid = (x0 + x1) / 2
    for x in range(x0, x1 + 1):
        d = abs(x - mid) / max(1.0, (x1 - x0) / 2)
        c.set(x, y + 1, glow_ramp, 5 if d < 0.25 else 4 if d < 0.7 else 3, glint=True)


def egg_hazard(c: Canvas, y: int, phase: int = 0):
    """蛋上一圈黄黑相间的警示条。"""
    x0, x1 = EGG_ROWS[y]
    for x in range(x0 + 1, x1):
        on = (x + phase) % 4 < 2
        c.set(x, y, HAZARD if on else GRAPHITE, 4 if on else 1, glint=True)


def egg_thruster(c: Canvas, glow_ramp):
    """蛋底下的推进口：一圈深色，中间亮。"""
    for x in range(6, 10):
        c.set(x, 13, GRAPHITE, 2)
    for x in (6, 9):
        c.set(x, 14, GRAPHITE, 1)
    c.set(7, 14, glow_ramp, 4, glint=True)
    c.set(8, 14, glow_ramp, 3, glint=True)


@texture("item/sentinel_spawn_egg", refs=("item/iron_golem_spawn_egg", "item/vex_spawn_egg"))
def sentinel_spawn_egg(rng):
    """巡逻无人机的蛋：白色蛋壳，一圈面罩（琥珀色的眼缝），两侧识别标，腰下一道接缝，底下推进口，顶上一根天线。"""
    c = egg_canvas(WHITE, 5)
    egg_visor(c, 7, AMBER_GLOW)
    for x, lv in ((4, 4), (5, 3)):
        c.set(x, 5, AMBER, lv, glint=True)
    for x, lv in ((10, 3), (11, 2)):
        c.set(x, 5, AMBER, lv, glint=True)
    x0, x1 = EGG_ROWS[11]
    for x in range(x0, x1 + 1):
        c.shift(x, 11, -2)
    egg_thruster(c, AMBER_GLOW)
    egg_finish(c, WHITE)
    c.set(8, 1, GRAPHITE, 2)
    c.set(8, 0, AMBER_GLOW, 5, glint=True)
    return c


@texture("item/heavy_sentinel_spawn_egg", refs=("item/iron_golem_spawn_egg",))
def heavy_sentinel_spawn_egg(rng):
    """重型守卫机的蛋：钢灰的蛋壳，面罩、两侧铆钉、底下一圈黄黑警示条，顶上红色的冠。"""
    c = egg_canvas(STEEL, 5)
    egg_visor(c, 6, RED_GLOW)
    for y in (10, 12):
        c.set(4, y, STEEL, 7, glint=True)
        c.set(11, y, STEEL, 3, glint=True)
    egg_hazard(c, 13)
    for x in range(7, 9):
        for y in (1, 2, 3):
            c.set(x, y, RED, (4 if x == 7 else 3) - (1 if y == 3 else 0), glint=True)
    egg_finish(c, STEEL)
    return c


@texture("item/quadruped_sentinel_spawn_egg", refs=("item/wolf_spawn_egg", "item/warden_spawn_egg"))
def quadruped_sentinel_spawn_egg(rng):
    """四足守卫机的蛋：白色蛋壳，顶上两片琥珀色的传感鳍像耳朵，面罩高一点，腰上一道深色的腰带。"""
    c = egg_canvas(WHITE, 5)
    egg_visor(c, 5, AMBER_GLOW)
    x0, x1 = EGG_ROWS[10]
    for x in range(x0, x1 + 1):
        c.set(x, 10, GRAPHITE, 2)
    x0, x1 = EGG_ROWS[11]
    for x in range(x0 + 1, x1):
        c.set(x, 11, WHITE, 6 if x < 8 else 5)
    egg_thruster(c, AMBER_GLOW)
    egg_finish(c, WHITE)
    for (x, y, lv) in ((3, 0, 5), (3, 1, 4), (4, 1, 4), (4, 2, 3), (12, 0, 4), (12, 1, 3), (11, 1, 3), (11, 2, 2)):
        c.set(x, y, AMBER, lv, glint=True)
    return c


@texture("item/walker_sentinel_spawn_egg", refs=("item/ravager_spawn_egg",))
def walker_sentinel_spawn_egg(rng):
    """步行守卫机的蛋：钢灰的蛋壳，顶上两根天线，面罩高，腰上一道装甲接缝，两侧红色标识，底下警示条。"""
    c = egg_canvas(STEEL, 5)
    egg_visor(c, 4, AMBER_GLOW)
    x0, x1 = EGG_ROWS[9]
    for x in range(x0 + 1, x1):
        c.set(x, 9, STEEL, 3)
    x0, x1 = EGG_ROWS[10]
    for x in range(x0 + 1, x1):
        c.set(x, 10, STEEL, 6 if x < 8 else 5)
    for y in (8, 9, 10, 11):
        c.set(3, y, RED, 4 if y < 10 else 3, glint=True)
        c.set(12, y, RED, 3 if y < 10 else 2, glint=True)
    egg_hazard(c, 13, 1)
    egg_finish(c, STEEL)
    for x in (6, 9):
        c.set(x, 1, GRAPHITE, 2)
        c.set(x, 0, AMBER_GLOW, 5, glint=True)
    return c


def poly_cells(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    out = set()
    for y in range(min(ys), max(ys) + 1):
        for x in range(min(xs), max(xs) + 1):
            inside = False
            j = len(points) - 1
            for i in range(len(points)):
                xi, yi = points[i]
                xj, yj = points[j]
                if (yi > y) != (yj > y) and x + 0.5 < (xj - xi) * (y + 0.5 - yi) / (yj - yi) + xi:
                    inside = not inside
                j = i
            if inside:
                out.add((x, y))
    return out


def plate(c: Canvas, points, r, lv, tilt=0.06):
    """一片外壳碎片：左上亮、右下暗，最外一圈受光的边亮一级。"""
    cells = poly_cells(points)
    for x, y in cells:
        t = (x + y) * tilt
        c.set(x, y, r, lv + (1 if t < 0.6 else 0 if t < 1.2 else -1))
    for x, y in cells:
        if (x - 1, y) not in cells or (x, y - 1) not in cells:
            c.shift(x, y, 1)
    return cells


@texture("item/sentinel_scrap", refs=("item/netherite_scrap", "item/iron_nugget"))
def sentinel_scrap(rng):
    """守卫机残骸：一片弯了的白色外壳，带着机身那道深色腰带和琥珀色识别标；断口露出深色的夹层、
    红蓝两根电线和一颗还亮着的元件。"""
    c = Canvas()
    c.draw([
        "................",
        "....1111........",
        "...1666651......",
        "..16666655511...",
        "..1666yyz55540..",
        ".16666655554440.",
        ".1655555554443a.",
        ".1bbbbbbbbbbba..",
        ".1544444433aa...",
        ".143333332a.....",
        "..1222222ab.r...",
        "...0000000aBr...",
        ".........aAr.uu.",
        "..........BrU...",
        "...........r....",
        "................",
    ], {"0123456": WHITE, "abcd": GRAPHITE, "~~z~y": AMBER, "~~~~r": RED, "~~U~u": BLUE_SHELL, "~~~B~A": AMBER_GLOW})
    return c


@texture("item/pirate_scrap", refs=("item/netherite_scrap",))
def pirate_scrap(rng):
    """海盗残骸：一片焦黑卷边的深灰外壳，带着半截红色涂装，旁边一截炮管，炮口还红着。"""
    c = Canvas()
    cells = plate(c, [(1, 4), (10, 2), (13, 7), (11, 12), (4, 13), (1, 9)], GRAPHITE, 3)
    for x, y in cells:
        if x + y * 0.6 < 8:
            c.recolor(x, y, RED, 0)
    for x, y in ((6, 8), (7, 8), (7, 9), (8, 10), (9, 6), (10, 6), (3, 10)):
        if (x, y) in cells:
            c.set(x, y, GRAPHITE, 0)
    for x, y in ((5, 8), (8, 9), (10, 5)):
        if (x, y) in cells:
            c.set(x, y, ORANGE, 2)
    # 一截炮管
    for i in range(5):
        x, y = 9 + i, 13 - i // 2
        c.set(x, y, GRAPHITE, 4)
        c.set(x, y + 1, GRAPHITE, 2)
    c.outline()
    c.set(14, 11, ORANGE_GLOW, 4, glint=True)
    c.set(14, 12, ORANGE_GLOW, 5, glint=True)
    return c


@texture("item/quadruped_servo", refs=("item/heavy_core",))
def quadruped_servo(rng):
    """四足机伺服器：横着的白色圆柱电机，朝外的端盖上一圈深色的定子、钢的轮毂和输出轴，
    机身两道散热筋、顶上一颗琥珀灯，尾部拖出一截红蓝电线。"""
    c = Canvas()
    for y, lv in ((4, 6), (5, 5), (6, 5), (7, 4), (8, 4), (9, 4), (10, 3), (11, 3), (12, 2)):
        c.hline(7, 14, y, WHITE, lv)
    for x in (10, 12):
        c.vline(x, 5, 11, WHITE, 2)
        c.set(x, 4, WHITE, 4)
    c.vline(14, 4, 12, WHITE, 2)
    cx, cy = 5.5, 8.0
    for x, y in c.disc_cells(cx, cy, 4.4):
        d = math.hypot(x - cx, y - cy)
        lit = (x - cx) + (y - cy) < 0
        if d > 3.4:
            c.set(x, y, WHITE, 6 if lit and (x - cx) + (y - cy) < -2 else 5 if lit else 3)
        elif d > 2.3:
            c.set(x, y, GRAPHITE, 2 if lit else 1)
        elif d > 1.0:
            c.set(x, y, STEEL, 5 if lit else 3)
        else:
            c.set(x, y, STEEL, 7 if (x, y) == (5, 7) else 2)
    for x, y in ((14, 13), (15, 14), (15, 15)):
        c.set(x, y, RED, 4, glint=True)
    for x, y in ((13, 13), (13, 14), (14, 15)):
        c.set(x, y, BLUE_SHELL, 4, glint=True)
    c.outline()
    c.set(11, 3, GRAPHITE, 1, glint=True)
    c.set(11, 2, AMBER_GLOW, 5, glint=True)
    c.set(5, 7, STEEL, 7, glint=True)
    return c


@texture("item/walker_brain", refs=("item/heart_of_the_sea", "item/redstone"))
def walker_brain(rng):
    """步行机主脑：钢框里一块深色的核心，中间透出橙红的光，四角铆钉，框上红色标识，四边伸出金色的引脚。"""
    c = Canvas()
    c.rect(2, 2, 13, 13, STEEL, 4)
    c.bevel(2, 2, 13, 13, 2, -2)
    c.recess(4, 4, 11, 11, GRAPHITE, 2)
    for x, y in c.cells(5, 5, 10, 10):
        d = math.hypot(x - 7.5, y - 7.5) / 3.6
        if d < 0.45:
            c.set(x, y, ORANGE_GLOW, 5 if d < 0.25 else 4, glint=True)
        elif d < 0.8:
            c.set(x, y, ORANGE_GLOW, 2 if d > 0.65 else 3, glint=True)
        else:
            c.set(x, y, GRAPHITE, 1)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, STEEL, 7, glint=True)
    for x in range(6, 10):
        c.set(x, 3, RED, 4 if x < 8 else 3)
    for x, y in ((1, 5), (1, 8), (1, 11), (14, 4), (14, 7), (14, 10), (5, 14), (9, 14), (6, 1), (10, 1)):
        c.set(x, y, BRASS, 5 if x < 8 or y < 8 else 3, glint=True)
    c.outline()
    return c


# ============================================================ 方块：守卫塔、黑雾基地

def pillar_plating(rng) -> Canvas:
    """守卫机机身那样的白色面板：四块板拼成，十字板缝一道暗一道亮，每块板一颗铆钉，左上亮右下暗。"""
    c = Canvas()
    c.fill(WHITE, 5)
    for x, y in c.cells(1, 1, 14, 14):
        k = x + y + ((x * 7 + y * 3) % 3) - 1
        if k > 20:
            c.shift(x, y, -1)
    c.frame(0, 0, 15, 15, WHITE, 3)
    c.hline(1, 14, 1, WHITE, 6)
    c.vline(1, 1, 14, WHITE, 6)
    c.hline(2, 14, 14, WHITE, 4)
    c.vline(14, 2, 14, WHITE, 4)
    c.hline(1, 14, 7, WHITE, 2)
    c.hline(1, 14, 8, WHITE, 6)
    c.vline(7, 1, 14, WHITE, 2)
    c.vline(8, 1, 14, WHITE, 6)
    c.set(7, 7, WHITE, 1)
    c.set(8, 8, WHITE, 5)
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.rivet(x, y, WHITE, 4)
    return c


@texture("block/sentinel_pillar_plating", refs=("block/quartz_block_side", "block/smooth_quartz"))
def sentinel_pillar_plating(rng):
    c = pillar_plating(rng)
    # 右下那块板上一小段琥珀色的识别标，和守卫机侧面的一样
    for x in range(10, 14):
        c.set(x, 10, AMBER, 4 if x < 13 else 3)
    c.set(10, 11, AMBER, 2)
    return c


@texture("block/sentinel_pillar_light", refs=("block/redstone_lamp_on", "block/sea_lantern"))
def sentinel_pillar_light(rng):
    """灯带：钢框里一整块琥珀色的光，中间最亮，往两边暗；横着几道扫描线，框的四角压着铆钉。"""
    c = Canvas()
    c.fill(GRAPHITE, 3)
    c.frame(0, 0, 15, 15, GRAPHITE, 2)
    c.hline(0, 14, 0, GRAPHITE, 4)
    c.vline(0, 0, 14, GRAPHITE, 4)
    for x, y in c.cells(2, 2, 13, 13):
        d = abs(x - 7.5) / 5.5
        lv = 5 if d < 0.3 else 4 if d < 0.65 else 3 if d < 0.9 else 2
        if y % 4 == 1:
            lv -= 1
        c.set(x, y, AMBER_GLOW, lv, glint=True)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, STEEL, 5, glint=True)
    return c


@texture("block/sentinel_pillar_terminal_front", refs=("block/observer_front",))
def sentinel_pillar_terminal_front(rng):
    """终端正面：白色面板上开一块黑屏，屏上一道发亮的眼缝（守卫机的面罩），下面三行琥珀色的读数。"""
    c = pillar_plating(rng)
    c.recess(2, 2, 13, 13, WHITE, 3)
    c.rect(3, 3, 12, 12, SCREEN, 0)
    for x in range(4, 12):
        d = abs(x - 7.5) / 4
        c.set(x, 5, AMBER_GLOW, 5 if d < 0.4 else 4, glint=True)
        c.set(x, 6, AMBER_GLOW, 3 if d < 0.6 else 2, glint=True)
    for row, n in ((9, 6), (10, 4), (11, 7)):
        for x in range(4, 4 + n):
            c.set(x, row, AMBER_GLOW, 2 if x % 2 else 3, glint=True)
    c.set(3, 3, SCREEN, 2, glint=True)
    c.set(4, 3, SCREEN, 1)
    return c


def fog_plating_canvas(rng) -> Canvas:
    """黑雾的底板：近黑的炮铜色，两块错开的切角面板，缝是暗的，面板上沿泛紫。"""
    c = Canvas()
    c.fill(FOG, 2)
    # 上下两排面板，竖缝错开
    for y0, y1, splits in ((0, 7, (11,)), (8, 15, (4,))):
        x_edges = [0] + [s_ + 1 for s_ in splits] + [16]
        for a in range(len(x_edges) - 1):
            x0, x1 = x_edges[a], x_edges[a + 1] - 1
            c.rect(x0, y0, x1, y1, FOG, 2)
            c.hline(x0, x1, y0, FOG, 4)
            c.vline(x0, y0, y1, FOG, 3)
            c.hline(x0, x1, y1, FOG, 0)
            c.vline(x1, y0, y1, FOG, 0)
            # 切角
            c.set(x0, y0, FOG, 1)
            c.set(x1, y1, FOG, 0)
    for x, y in ((5, 3), (13, 11), (1, 12)):
        c.set(x, y, FOG, 5, glint=True)
    return c


@texture("block/dark_fog_plating", refs=("block/netherite_block", "block/polished_blackstone"))
def dark_fog_plating(rng):
    c = fog_plating_canvas(rng)
    for x, y in ((8, 4), (2, 11)):
        c.set(x, y, CRIMSON, 2, glint=True)
    return c


@texture("block/dark_fog_conduit", refs=("block/crying_obsidian",))
def dark_fog_conduit(rng):
    """导轨：底板上一个十字形的导槽，槽底暗红，中间一道亮红，隔一段一个热点。"""
    c = fog_plating_canvas(rng)
    for i in range(16):
        for k, lv in ((6, 1), (7, 3), (8, 3), (9, 1)):
            c.set(i, k, CRIMSON, lv)
            c.set(k, i, CRIMSON, lv)
        c.set(i, 5, FOG, 0)
        c.set(5, i, FOG, 0)
        c.set(i, 10, FOG, 4)
        c.set(10, i, FOG, 4)
    for i in range(16):
        if i % 4 == 1:
            c.set(i, 7, RED_GLOW, 5, glint=True)
            c.set(8, i, RED_GLOW, 5, glint=True)
    for x, y in c.cells(6, 6, 9, 9):
        c.set(x, y, RED_GLOW, 4 if (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)) else 3, glint=True)
    return c


@texture("block/dark_fog_core_remnant", rough=True, refs=("block/magma", "block/crying_obsidian"))
def dark_fog_core_remnant(rng):
    """地核：钻芯打穿地壳后露出来的熔融物质，一块块凝固的黑壳浮在上面，壳缝里是亮橙的熔岩，中间更热。"""
    c = Canvas()
    for x, y in c.cells():
        d = math.hypot(x - 7.5, y - 7.5) / 10.6
        c.set(x, y, ORANGE_GLOW, 5 if d < 0.25 else 4 if d < 0.55 else 3)
    # 一块块硬壳：形状固定（种子按路径），块与块之间留出发光的缝
    crusts = []
    for _ in range(9):
        crusts.append((rng.uniform(0, 16), rng.uniform(0, 16), rng.uniform(2.0, 3.4)))
    for x, y in c.cells():
        best = None
        for cx, cy, r in crusts:
            for ox in (-16, 0, 16):
                for oy in (-16, 0, 16):
                    d = math.hypot(x - cx - ox, y - cy - oy) / r
                    if best is None or d < best[0]:
                        best = (d, cx + ox, cy + oy, r)
        d, cx, cy, r = best
        if d < 0.82:
            # 硬壳：上左受光
            lit = (x - cx) + (y - cy) < -0.5
            c.set(x, y, BASALT, 3 if lit else 1 if d > 0.6 else 2)
        elif d < 1.0:
            c.set(x, y, CRIMSON, 3 if d < 0.92 else 4)
    return c


def fog_skin_recipes(name: str, fn):
    @texture(f"entity/{name}")
    def base(rng):
        return fn().c

    @texture(f"entity/{name}_glow")
    def glow_(rng):
        return fn().g

    base.__name__ = f"{name}"
    glow_.__name__ = f"{name}_glow"


for _name, _fn in (("dark_fog_raider", fog_raider), ("dark_fog_ranger", fog_ranger), ("dark_fog_guardian", fog_guardian),
                   ("dark_fog_core", fog_core), ("dark_fog_camp", fog_camp), ("dark_fog_laser_tower", fog_laser_tower),
                   ("dark_fog_plasma_sentry", fog_plasma_sentry), ("dark_fog_relay", fog_relay),
                   ("dark_fog_seed", fog_seed), ("dark_fog_lancer", fog_lancer), ("dark_fog_humpback", fog_humpback),
                   ("hive_core", hive_core), ("hive_photon_receiver", hive_photon_receiver),
                   ("hive_plasma_tower", hive_plasma_tower), ("hive_phase_laser", hive_phase_laser),
                   ("hive_lancer_port", hive_lancer_port), ("hive_humpback_port", hive_humpback_port)):
    fog_skin_recipes(_name, _fn)
