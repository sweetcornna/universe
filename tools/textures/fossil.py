"""化石的贴图（ADR-0034 §三、ADR-0031）：40 块骨头和化石展品的图标，骨架展台、收藏家界面的空格子图标，
立体骨头共用的那张骨面贴图，石哨兵（醒着的实体、扔出来的石块、沉睡时的方块），化石底座和骨架展台。

骨头图标按类群画出各自的样子，同一部位的八块放在一起认得出是同一个部位：
- 头骨朝右的侧面像（巨蠕是正对着的环形口，甲壳兽是俯视的头甲），颈椎、尾骨从左下往右上或从左上往右下斜着摆，
  胸廓是侧面的一排肋骨，四肢是一整条腿（翼兽是翼骨，海兽是鳍，巨蠕是一圈带刺的体节环）。
- 形状用胶囊、椭圆拼出一张高度图，按高度打光（光从左上来），再挖眼眶、鼻孔这些窟窿，压骨缝，点牙。
- 每个类群一条自己的骨色，和立体骨头染的颜色一个调子：巨兽象牙白、掠食兽黄褐、长颈兽灰白、翼兽最白、
  巨蠕带点肉粉、甲壳兽是赭黄的甲、海兽偏青灰、奇异种淡紫（眼眶里还留着一点荧光）。

立体骨头（client/fossil/BoneModels.java）所有类群共用 entity/fossil/bone.png，渲染时按类群染色，所以画得浅、偏中性：
上半是骨面（细的孔、裂纹、矿物染的斑），中间一条是深的窟窿，最下面一条是牙和角（更白、有竖纹）。
每个方块在哪一条由 core/fossil/BoneShapes.java 分好，这里只管把三条材质画满。

石哨兵（client/fossil/StoneSentryModel.java，UV 布局见那边的注释）是一块风化的岩石：暖灰的石面、深色的裂缝、
顶上长着苔藓；正面眉石底下一道眼缝，发光层是白色的亮度图，渲染时按状态染成琥珀、橙红。
沉睡时是方块（models/block/dormant_stone_sentry_template.json）：缩成一团，拳石抱在前面，眼缝闭着；
被吵醒一次眼缝亮一点，两次以后裂缝里也透出火光。
"""
from __future__ import annotations

import math
import random

from canvas import Canvas
from color import Ramp, ramp
from gear import limit_colors, lit_outline
from palette import (AMBER_GLOW, BASALT, BONE, COPPER, CREAM, MOSS, SAND, STONE, VIOLET_GLOW, WHITE_GLOW,
                     register)
from recipe import texture

# ---------------------------------------------------------------- 色阶

FAMILY = {
    "titan": register(ramp("fossil_titan", 86, 0.040, n=7, lo=0.38, hi=0.95, shift=16)),
    "predator": register(ramp("fossil_predator", 70, 0.062, n=7, lo=0.34, hi=0.91, shift=16)),
    "longneck": register(ramp("fossil_longneck", 96, 0.022, n=7, lo=0.38, hi=0.94, shift=14)),
    "flyer": register(ramp("fossil_flyer", 100, 0.018, n=7, lo=0.36, hi=0.96, shift=12)),
    "worm": register(ramp("fossil_worm", 32, 0.048, n=7, lo=0.36, hi=0.93, shift=12)),
    "arthropod": register(ramp("fossil_arthropod", 74, 0.098, n=7, lo=0.32, hi=0.91, shift=14, peak=0.6)),
    "marine": register(ramp("fossil_marine", 222, 0.030, n=7, lo=0.36, hi=0.94, shift=10)),
    "exotic": register(ramp("fossil_exotic", 302, 0.058, n=7, lo=0.34, hi=0.93, shift=10)),
}
FAMILIES = list(FAMILY)
PARTS = ["skull", "neck", "ribcage", "limbs", "tail"]
# 眼眶、鼻孔：带一点土色的深褐
HOLLOW = register(ramp("fossil_hollow", 55, 0.032, n=5, lo=0.20, hi=0.46, shift=10))
# 牙、角、爪
TOOTH = CREAM
# 空格子的图标：和原版的空盔甲格一个颜色
GHOST = register(Ramp("fossil_ghost", ((85, 85, 85),)))

BONE_M, TOOTH_M = "bone", "tooth"


# ---------------------------------------------------------------- 骨头图标的画法

class Body:
    """一块骨头的高度图：像素中心是 (x + 0.5, y + 0.5)，形状叠起来取最高的。"""

    def __init__(self, w: int = 16, h: int = 16):
        self.w, self.h = w, h
        self.z = [[0.0] * w for _ in range(h)]
        self.m: list[list] = [[None] * w for _ in range(h)]
        self.holes: dict[tuple[int, int], float] = {}   # 窟窿：(x, y) -> 深度（0–1，越靠中间越深）
        self.creases: set[tuple[int, int]] = set()      # 骨缝、关节：压暗两级
        self.cracks: set[tuple[int, int]] = set()       # 细裂纹：压暗一级
        self.spots: dict[tuple[int, int], tuple] = {}   # 手点的像素：(色阶, 级数)

    def _put(self, x, y, z, m):
        if 0 <= x < self.w and 0 <= y < self.h and z > self.z[y][x]:
            self.z[y][x] = z
            self.m[y][x] = m

    def cap(self, x0, y0, x1, y1, r0, r1=None, m=BONE_M, lift=0.0):
        """胶囊：两头圆的棒，半径从 r0 渐变到 r1。截面是半圆，中间高。"""
        r1 = r0 if r1 is None else r1
        dx, dy = x1 - x0, y1 - y0
        ll = dx * dx + dy * dy
        for y in range(self.h):
            for x in range(self.w):
                px, py = x + 0.5, y + 0.5
                t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / ll))
                r = r0 + (r1 - r0) * t
                d = math.hypot(px - (x0 + dx * t), py - (y0 + dy * t))
                if d <= r:
                    self._put(x, y, lift + math.sqrt(max(0.0, r * r - d * d)) + 0.2, m)

    def chain(self, pts, r0, r1=None, m=BONE_M, lift=0.0):
        """一串胶囊连起来，半径沿着整串渐变。"""
        r1 = r0 if r1 is None else r1
        n = len(pts) - 1
        for i in range(n):
            a = r0 + (r1 - r0) * i / n
            b = r0 + (r1 - r0) * (i + 1) / n
            self.cap(*pts[i], *pts[i + 1], a, b, m, lift)

    def ell(self, cx, cy, rx, ry, a=0.0, m=BONE_M, lift=0.0, flat=1.0):
        """椭圆的穹顶；a 是转角（弧度），flat 小于 1 时更扁。"""
        ca, sa = math.cos(a), math.sin(a)
        for y in range(self.h):
            for x in range(self.w):
                px, py = x + 0.5 - cx, y + 0.5 - cy
                lx, ly = px * ca + py * sa, -px * sa + py * ca
                q = (lx / rx) ** 2 + (ly / ry) ** 2
                if q <= 1:
                    self._put(x, y, lift + min(rx, ry) * flat * math.sqrt(1 - q) + 0.2, m)

    def hole(self, cx, cy, rx, ry=None, a=0.0):
        """眼眶、鼻孔、窗孔：只挖在骨头上。"""
        ry = rx if ry is None else ry
        ca, sa = math.cos(a), math.sin(a)
        for y in range(self.h):
            for x in range(self.w):
                if self.m[y][x] is None:
                    continue
                px, py = x + 0.5 - cx, y + 0.5 - cy
                lx, ly = px * ca + py * sa, -px * sa + py * ca
                q = (lx / rx) ** 2 + (ly / ry) ** 2
                if q <= 1:
                    self.holes[(x, y)] = max(self.holes.get((x, y), 0.0), 1 - q)

    def cut(self, cx, cy, rx, ry=None, a=0.0):
        """挖穿（透明）。"""
        ry = rx if ry is None else ry
        ca, sa = math.cos(a), math.sin(a)
        for y in range(self.h):
            for x in range(self.w):
                px, py = x + 0.5 - cx, y + 0.5 - cy
                lx, ly = px * ca + py * sa, -px * sa + py * ca
                if (lx / rx) ** 2 + (ly / ry) ** 2 <= 1:
                    self.z[y][x] = 0.0
                    self.m[y][x] = None
                    self.holes.pop((x, y), None)

    def _line(self, x0, y0, x1, y1):
        n = max(1, int(math.ceil(max(abs(x1 - x0), abs(y1 - y0)) * 2)))
        out = []
        for i in range(n + 1):
            t = i / n
            p = (int(math.floor(x0 + (x1 - x0) * t)), int(math.floor(y0 + (y1 - y0) * t)))
            if p not in out:
                out.append(p)
        return out

    def crease(self, x0, y0, x1, y1):
        for p in self._line(x0, y0, x1, y1):
            self.creases.add(p)

    def crack(self, x0, y0, x1, y1):
        for p in self._line(x0, y0, x1, y1):
            self.cracks.add(p)

    def spot(self, x, y, r, lv):
        self.spots[(int(x), int(y))] = (r, lv)

    def mask(self) -> set:
        return {(x, y) for y in range(self.h) for x in range(self.w) if self.m[y][x] is not None}

    def render(self, bone: Ramp, base: float = 4.0, amp: float = 5.0) -> Canvas:
        """按高度打光，压骨缝，挖窟窿，点牙，最后在外面描一圈这块骨头最深的颜色（受光的左、上边浅一级）。"""
        c = Canvas(self.w, self.h)
        ramps = {BONE_M: bone, TOOTH_M: TOOTH}

        def z(x, y):
            return self.z[y][x] if 0 <= x < self.w and 0 <= y < self.h else 0.0

        lx, ly, lz = -0.58, -0.62, 0.53
        for y in range(self.h):
            for x in range(self.w):
                m = self.m[y][x]
                if m is None:
                    continue
                hx = (z(x + 1, y) - z(x - 1, y)) * 0.5
                hy = (z(x, y + 1) - z(x, y - 1)) * 0.5
                n = (-hx, -hy, 1.0)
                ln = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2)
                v = (n[0] * lx + n[1] * ly + n[2] * lz) / ln
                c.set(x, y, ramps[m], round(base + (v - lz) * amp))
        for (x, y) in self.creases:
            if c.get(x, y) and c.get(x, y)[0] is bone:
                c.set(x, y, bone, 1, glint=True)
        for (x, y) in self.cracks:
            if c.get(x, y) and c.get(x, y)[0] is bone:
                c.shift(x, y, -1)
                c.glints.add((x, y))
        # 窟窿：中间最深；下沿、右沿露出一点受光的骨壁，上沿、左沿压暗
        for (x, y), d in self.holes.items():
            if c.get(x, y):
                c.set(x, y, HOLLOW, 0 if d > 0.4 else 1, glint=True)
        for (x, y) in self.holes:
            for nx, ny, dd in ((x + 1, y, 1), (x, y + 1, 1), (x - 1, y, -1), (x, y - 1, -1)):
                q = c.get(nx, ny)
                if (nx, ny) not in self.holes and q and q[0] is bone:
                    c.shift(nx, ny, dd)
        for (x, y), (r, lv) in self.spots.items():
            c.set(x, y, r, lv, glint=True)
        outline_outside(c, bone)
        despeckle(c)
        return c


def despeckle(c: Canvas, passes: int = 2):
    """打光留下的单个像素（八邻里没有同色的）并进旁边同一条色阶里最接近的一级；手点的像素不动。"""
    for _ in range(passes):
        changed = False
        for y in range(c.h):
            for x in range(c.w):
                px = c.get(x, y)
                if not px or (x, y) in c.glints:
                    continue
                near = [c.get(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy]
                near = [q for q in near if q]
                if any(q == px for q in near):
                    continue
                same = [q[1] for q in near if q[0] is px[0]]
                if same:
                    lv = min(same, key=lambda v: (abs(v - px[1]), -same.count(v)))
                    c.p[y][x] = (px[0], lv)
                    changed = True
        if not changed:
            return


def outline_outside(c: Canvas, r: Ramp):
    """在形状外面描一圈：右边、下边是最深的一级，左边、上边浅一级。"""
    src = [row[:] for row in c.p]

    def opaque(x, y):
        return 0 <= x < c.w and 0 <= y < c.h and src[y][x] is not None

    for y in range(c.h):
        for x in range(c.w):
            if src[y][x] is not None:
                continue
            if opaque(x + 1, y) or opaque(x, y + 1):
                c.set(x, y, r, 1)
            elif opaque(x - 1, y) or opaque(x, y - 1):
                c.set(x, y, r, 0)


def vert_chain(b: Body, pts, r0, r1, spine=0.0, m=BONE_M):
    """一串椎骨：每节一个沿走向拉长的椭圆，节与节之间压一道关节缝；spine 大于 0 时背上（左上那侧）一根棘突。"""
    n = len(pts) - 1
    for i in range(n):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        r = r0 + (r1 - r0) * (i + 0.5) / n
        b.ell(cx, cy, length / 2 + 0.3, r, math.atan2(dy, dx), m)
        if spine > 0:
            nx, ny = dy / length, -dx / length
            if nx + ny > 0:
                nx, ny = -nx, -ny
            b.cap(cx, cy, cx + nx * (r + spine), cy + ny * (r + spine), 0.55, 0.4, m)
    for i in range(1, n):
        (x0, y0), (x1, y1) = pts[i - 1], pts[i + 1]
        cx, cy = pts[i]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy)
        nx, ny = -dy / ln, dx / ln
        r = (r0 + (r1 - r0) * i / n) * 0.8
        b.crease(cx - nx * r, cy - ny * r, cx + nx * r, cy + ny * r)


def ribs(b: Body, x0, x1, top, n, depth, sweep, r=0.5, spine_r=1.1, spines=1.0, belly=0.0):
    """侧面的胸廓：上面一条脊椎（带棘突），下面 n 根往后弯的肋骨；belly 是肋骨中段往外鼓多少。"""
    vert_chain(b, [(x0 + (x1 - x0) * i / 4, top) for i in range(5)], spine_r, spine_r * 0.9, spine=spines)
    for i in range(n):
        t = (i + 0.5) / n
        x = x0 + 1.2 + (x1 - x0 - 2.4) * t
        d = depth * (0.7 + 0.3 * math.sin(t * math.pi))
        b.chain([(x, top + 0.8), (x - sweep * 0.3 - belly, top + d * 0.5), (x - sweep, top + d)], r, r * 0.8)


def claw(b: Body, x0, y0, x1, y1, r=0.5):
    b.cap(x0, y0, x1, y1, r, max(0.35, r * 0.5), TOOTH_M, lift=0.2)


def teeth(b: Body, x0, x1, y, step=2.0, lv=5):
    """一排牙：每隔 step 一颗。"""
    x = x0
    while x <= x1 + 1e-6:
        b.spot(x, y, TOOTH, lv)
        x += step


def along(p0, p1, n, bend=0.0):
    """从 p0 到 p1 均匀取 n + 1 个点；bend 让中间往左上（正）或右下（负）拱。"""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    nx, ny = dy / ln, -dx / ln
    if nx + ny > 0:
        nx, ny = -nx, -ny
    out = []
    for i in range(n + 1):
        t = i / n
        k = math.sin(t * math.pi) * bend
        out.append((x0 + dx * t + nx * k, y0 + dy * t + ny * k))
    return out


# ---- 头骨（朝右的侧面像）

def skull_titan(b: Body):
    b.ell(4.4, 6.0, 3.3, 3.0, -0.55)                      # 颈盾
    b.cap(5.0, 9.4, 11.2, 10.0, 2.4, 1.6)                 # 脸
    b.cap(11.0, 10.0, 13.6, 11.4, 1.4, 0.5)               # 喙
    b.cap(5.6, 12.4, 11.4, 12.6, 1.0, 0.8)                # 下颌
    claw(b, 8.4, 7.4, 11.0, 2.4, 0.75)                    # 眉角
    claw(b, 6.4, 7.4, 6.8, 3.6, 0.6)
    claw(b, 11.6, 9.0, 12.4, 7.4, 0.5)                    # 鼻角
    b.hole(8.5, 9.0, 0.95, 0.8)
    b.hole(12.3, 10.2, 0.5)
    b.hole(3.6, 5.4, 0.9, 0.7, -0.5)                      # 颈盾上的窗孔
    b.crease(6.0, 11.4, 11.6, 11.6)


def skull_predator(b: Body):
    b.ell(4.2, 7.0, 2.8, 3.0)                             # 后脑
    b.cap(4.0, 8.0, 13.0, 8.6, 2.6, 1.5)                  # 吻
    b.cap(3.6, 11.6, 13.0, 11.4, 1.2, 0.7)                # 下颌
    b.hole(4.6, 6.4, 0.8, 1.1)                            # 眼眶
    b.hole(8.4, 8.0, 1.3, 0.75)                           # 眶前孔
    b.hole(11.8, 7.6, 0.45)                               # 鼻孔
    b.crease(5.0, 10.4, 13.2, 10.4)
    teeth(b, 6.2, 12.2, 10.4, 2.0)


def skull_longneck(b: Body):
    b.ell(6.6, 8.0, 3.6, 3.0)                             # 脑壳
    b.cap(7.8, 9.4, 12.6, 9.8, 2.3, 1.8)                  # 钝的吻
    b.cap(5.8, 12.0, 12.4, 12.0, 1.0, 0.9)                # 下颌
    b.cap(3.4, 9.4, 2.4, 10.4, 0.7)                       # 枕髁
    b.hole(5.8, 7.4, 1.0)                                 # 眼眶
    b.hole(8.8, 5.8, 0.95, 0.6)                           # 鼻孔长在头顶
    b.crease(6.2, 11.0, 13.0, 11.2)
    teeth(b, 9.6, 13.0, 11.0, 1.6)                        # 钉子一样的牙


def skull_flyer(b: Body):
    b.ell(4.4, 8.6, 2.5, 2.2)                             # 脑壳
    b.cap(6.0, 9.0, 14.4, 10.4, 1.15, 0.35)               # 长喙
    b.cap(5.6, 11.2, 13.8, 11.4, 0.6, 0.3)                # 下颌
    b.cap(3.6, 7.0, 1.4, 2.0, 0.95, 0.4)                  # 后冠
    b.hole(4.4, 8.4, 0.8)
    b.hole(8.2, 9.6, 1.1, 0.45, 0.15)                     # 鼻眶前孔


def skull_worm(b: Body):
    b.ell(8.0, 8.0, 6.2, 6.2, flat=0.8)
    b.hole(8.0, 8.0, 3.3)                                 # 口
    for i in range(8):                                    # 一圈朝里的牙
        a = i / 8 * math.tau + 0.39
        claw(b, 8 + math.cos(a) * 3.8, 8 + math.sin(a) * 3.8, 8 + math.cos(a) * 2.2, 8 + math.sin(a) * 2.2, 0.5)
    for i in range(8):                                    # 外圈的体节缝
        a = i / 8 * math.tau
        b.crease(8 + math.cos(a) * 4.9, 8 + math.sin(a) * 4.9, 8 + math.cos(a) * 6.0, 8 + math.sin(a) * 6.0)


def skull_arthropod(b: Body):
    b.ell(6.2, 8.8, 4.6, 3.6)                             # 头甲（侧面）
    b.ell(9.8, 10.0, 2.0, 2.0, lift=0.4)                  # 唇基
    claw(b, 8.8, 5.6, 12.6, 2.2, 0.6)                     # 触角的根
    b.chain([(10.2, 9.6), (12.6, 8.8), (14.4, 10.6)], 0.95, 0.45, TOOTH_M, lift=0.3)   # 大颚
    b.chain([(10.2, 11.6), (12.4, 13.2), (14.2, 12.4)], 0.85, 0.4, TOOTH_M, lift=0.3)
    b.hole(7.8, 7.0, 1.2, 0.95)                           # 复眼
    b.crease(4.2, 5.6, 4.2, 12.2)                         # 甲片的缝
    b.crease(2.0, 10.0, 5.0, 10.0)


def skull_marine(b: Body):
    b.ell(5.0, 8.2, 3.4, 3.0)                             # 脑壳
    b.cap(7.0, 8.6, 14.6, 9.2, 1.4, 0.4)                  # 长吻
    b.cap(6.0, 10.6, 14.0, 10.2, 0.7, 0.3)                # 下颌
    b.hole(4.8, 7.8, 2.0, 1.8)                            # 大眼眶
    for i in range(8):                                    # 眼眶里一圈巩膜环
        a = i / 8 * math.tau
        b.spot(4.8 + math.cos(a) * 1.3, 7.8 + math.sin(a) * 1.2, FAMILY["marine"], 5 if math.sin(a) < 0 else 3)
    b.crease(7.6, 9.9, 14.0, 9.7)
    teeth(b, 8.6, 13.0, 9.9, 2.0)


def skull_exotic(b: Body):
    b.ell(7.6, 7.8, 4.7, 4.1)                             # 圆的脑壳
    b.cap(6.2, 11.2, 11.6, 11.6, 1.6, 1.1)                # 下脸
    claw(b, 4.4, 4.8, 2.8, 2.0, 0.6)                      # 三根冠刺
    claw(b, 7.8, 3.8, 8.0, 1.0, 0.6)
    claw(b, 11.0, 4.8, 12.8, 2.2, 0.6)
    for cx, cy in ((5.4, 7.4), (8.0, 5.8), (10.4, 7.6)):  # 三个眼眶，里面还留着一点荧光
        b.hole(cx, cy, 0.95)
        b.spot(cx, cy, VIOLET_GLOW, 3)
    b.crease(6.4, 11.6, 11.8, 11.6)
    teeth(b, 7.0, 11.0, 11.6, 2.0)


# ---- 颈椎（从左下往右上）

def neck_titan(b: Body):
    vert_chain(b, along((3.4, 12.6), (12.6, 3.4), 3), 1.9, 1.7, spine=0.9)


def neck_predator(b: Body):
    vert_chain(b, [(2.4, 13.2), (4.8, 10.4), (7.8, 9.0), (10.4, 6.4), (13.0, 3.0)], 1.25, 1.05, spine=1.1)


def neck_longneck(b: Body):
    vert_chain(b, along((2.0, 14.0), (14.0, 2.0), 4, bend=0.8), 1.0, 0.8, spine=0.7)


def neck_flyer(b: Body):
    pts = along((2.6, 13.4), (13.4, 2.6), 2)
    for i in range(2):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        b.cap(x0, y0, x1, y1, 0.8)                         # 细长中空的椎体
        b.ell(x0 + 0.6, y0 - 0.6, 1.35, 1.35)              # 两头张开
        b.ell(x1 - 0.6, y1 + 0.6, 1.35, 1.35)
        b.hole((x0 + x1) / 2, (y0 + y1) / 2, 0.45)         # 气孔
    b.crease(7.4, 7.4, 8.6, 8.6)


def neck_worm(b: Body):
    for cx, cy in ((4.6, 11.4), (8.0, 8.0), (11.4, 4.6)):
        b.ell(cx, cy, 2.1, 3.3, math.pi / 4, flat=0.8)
    for cx, cy in ((4.6, 11.4), (8.0, 8.0), (11.4, 4.6)):
        b.hole(cx, cy, 0.7, 1.7, math.pi / 4)


def neck_arthropod(b: Body):
    pts = along((3.0, 13.0), (13.0, 3.0), 3)
    for i in range(3):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        w = 1.8 + i * 0.5
        b.cap(cx - w * 0.707, cy - w * 0.707, cx + w * 0.707, cy + w * 0.707, 1.3, lift=i * 0.3)   # 一片片套着的环甲
    for x, y in pts[1:-1]:
        b.crease(x - 2.0, y - 2.0, x + 2.0, y + 2.0)


def neck_marine(b: Body):
    pts = along((2.8, 12.6), (13.2, 3.4), 4)
    for i in range(4):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        b.ell((x0 + x1) / 2, (y0 + y1) / 2, 1.5, 2.2, math.atan2(y1 - y0, x1 - x0))   # 短的盘状椎体
        b.cap((x0 + x1) / 2, (y0 + y1) / 2, (x0 + x1) / 2 - 1.8, (y0 + y1) / 2 - 2.2, 0.5, 0.4)
    vert_chain(b, pts, 1.6, 1.6)


def neck_exotic(b: Body):
    pts = [(2.4, 13.4), (5.2, 11.0), (7.6, 8.0), (10.6, 5.6), (13.4, 2.6)]
    vert_chain(b, pts, 1.1, 0.95)
    for i in range(1, 5):                                 # 两边交替的弯棘
        (x0, y0), (x1, y1) = pts[i - 1], pts[i]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        if i % 2:
            claw(b, cx, cy, cx - 2.6, cy - 1.8, 0.45)
        else:
            claw(b, cx, cy, cx + 2.0, cy + 2.4, 0.45)
    b.spot(7.6, 8.0, VIOLET_GLOW, 3)


# ---- 胸廓（侧面：上面一条脊椎，下面一排肋骨）

def ribcage_titan(b: Body):
    ribs(b, 2.0, 14.0, 3.0, 4, 10.4, 1.6, r=0.6, spine_r=1.3, spines=0.9)


def ribcage_predator(b: Body):
    ribs(b, 1.6, 14.4, 2.8, 4, 8.0, 2.0, r=0.5)
    for x in (5.0, 9.4):                                  # 腹肋
        b.cap(x - 1.4, 12.6, x, 13.8, 0.4)
        b.cap(x, 13.8, x + 1.4, 12.6, 0.4)


def ribcage_longneck(b: Body):
    ribs(b, 1.6, 14.4, 2.6, 4, 11.0, 2.0, r=0.5, belly=1.4)


def ribcage_flyer(b: Body):
    ribs(b, 2.6, 13.4, 3.0, 3, 6.4, 1.2, r=0.45, spine_r=0.9, spines=0.8)
    b.ell(8.0, 11.0, 4.4, 1.1)                            # 胸骨
    b.cap(6.2, 11.6, 8.0, 14.4, 0.6, 0.3)                 # 龙骨突
    b.cap(8.0, 14.4, 10.0, 11.6, 0.3, 0.6)


def ribcage_worm(b: Body):
    for cx in (4.0, 8.0, 12.0):
        b.ell(cx, 8.0, 1.9, 6.0, flat=0.7)
    for cx in (4.0, 8.0, 12.0):
        b.hole(cx, 8.0, 0.6, 3.8)


def ribcage_arthropod(b: Body):
    b.ell(8.0, 10.6, 6.6, 6.0, flat=0.9)                  # 背甲
    b.cut(8.0, 15.0, 8.4, 3.4)                            # 底下是平的
    for i in range(4):
        a = math.pi * (0.2 + 0.2 * i)
        b.crease(8.0 + math.cos(a) * 2.4, 11.0 - math.sin(a) * 2.4, 8.0 + math.cos(a) * 6.4, 11.0 - math.sin(a) * 5.8)
    b.crease(1.6, 11.4, 14.4, 11.4)                       # 边上一圈卷边


def ribcage_marine(b: Body):
    ribs(b, 1.2, 14.8, 4.0, 4, 6.8, 2.4, r=0.5, spine_r=1.1, spines=0.7)


def ribcage_exotic(b: Body):
    vert_chain(b, [(8.0, 1.8 + i * 3.1) for i in range(5)], 1.3, 1.0)    # 中轴
    for y, k in ((3.6, 1.0), (7.0, 0.9), (10.4, 0.75)):
        for s in (-1, 1):                                  # 往外、往上翻的肋骨
            b.chain([(8.0, y), (8.0 + s * 3.0 * k, y + 1.6), (8.0 + s * 5.0 * k, y - 0.4)], 0.55, 0.45)
    b.spot(8.0, 7.2, VIOLET_GLOW, 3)


# ---- 四肢（一整条腿）

def limbs_titan(b: Body):
    b.cap(9.2, 2.2, 7.6, 8.4, 1.8, 1.3)                   # 股骨
    b.ell(9.0, 2.4, 2.4, 1.6, 0.3)
    b.ell(7.6, 8.6, 1.8, 1.2)                             # 膝
    b.cap(7.6, 8.8, 7.2, 12.4, 1.3, 1.2)                  # 胫骨
    b.ell(7.2, 13.4, 3.4, 1.3)                            # 脚
    teeth(b, 4.6, 10.2, 14.4, 2.0, 4)
    b.crease(6.2, 8.8, 9.0, 8.8)


def limbs_predator(b: Body):
    b.cap(10.8, 2.0, 7.6, 7.2, 1.3, 0.9)                  # 股骨
    b.ell(10.8, 2.2, 1.8, 1.3, 0.5)
    b.cap(7.6, 7.2, 9.6, 11.4, 0.85, 0.7)                 # 胫骨
    b.cap(9.6, 11.4, 8.6, 13.0, 0.7, 0.6)                 # 跖骨
    b.chain([(8.6, 13.0), (6.4, 13.8), (4.6, 14.0)], 0.5, 0.4)     # 三根趾
    b.chain([(8.6, 13.0), (10.6, 13.8), (12.2, 14.0)], 0.5, 0.4)
    claw(b, 4.6, 14.0, 2.6, 13.6, 0.5)
    claw(b, 12.2, 14.0, 14.0, 13.4, 0.5)
    b.crease(6.8, 7.0, 8.6, 7.6)


def limbs_longneck(b: Body):
    b.cap(8.2, 2.2, 8.0, 7.4, 1.6, 1.4)                   # 股骨
    b.ell(8.2, 2.2, 2.2, 1.2)
    b.ell(8.0, 7.6, 1.9, 1.2)                             # 膝
    b.cap(8.0, 7.8, 7.8, 12.0, 1.4, 1.3)                  # 胫骨
    b.ell(7.8, 13.2, 2.9, 1.4, flat=0.8)                  # 柱子一样的脚
    claw(b, 5.4, 13.0, 3.4, 14.0, 0.5)
    b.crease(6.4, 7.6, 9.8, 7.6)


def limbs_flyer(b: Body):
    b.cap(2.6, 13.4, 5.6, 10.4, 1.15, 0.95)               # 肱骨
    b.cap(5.6, 10.4, 9.6, 7.0, 0.85, 0.75)                # 前臂
    b.cap(9.6, 7.0, 14.0, 2.0, 0.75, 0.4)                 # 翼指
    b.ell(9.6, 7.0, 1.3, 1.2)                             # 腕
    claw(b, 9.0, 6.4, 6.6, 4.4, 0.4)                      # 小爪
    claw(b, 8.8, 7.0, 6.0, 6.8, 0.4)
    b.crease(5.0, 10.0, 6.2, 11.0)


def limbs_worm(b: Body):
    b.ell(8.0, 8.0, 4.7, 4.7, flat=0.7)                   # 一圈体节环
    b.cut(8.0, 8.0, 2.1)
    for i in range(6):                                    # 外面一圈刺
        a = i / 6 * math.tau + math.pi / 6
        claw(b, 8 + math.cos(a) * 4.0, 8 + math.sin(a) * 4.0, 8 + math.cos(a) * 6.7, 8 + math.sin(a) * 6.7, 0.85)
    for i in range(3):
        a = i / 3 * math.tau + math.pi / 2
        b.crease(8 + math.cos(a) * 2.8, 8 + math.sin(a) * 2.8, 8 + math.cos(a) * 4.8, 8 + math.sin(a) * 4.8)


def limbs_arthropod(b: Body):
    pts = [(3.2, 2.0), (6.0, 6.0), (4.8, 9.8), (8.8, 12.2), (12.0, 12.8)]
    for i in range(4):
        b.cap(*pts[i], *pts[i + 1], 1.05 - i * 0.12, 0.95 - i * 0.12)
        b.ell(*pts[i + 1], 1.05 - i * 0.1, 1.05 - i * 0.1, lift=0.3)   # 关节
    claw(b, 12.0, 12.8, 14.4, 11.2, 0.65)                 # 钳
    claw(b, 12.0, 12.8, 14.2, 14.4, 0.55)
    for x, y in pts[1:4]:
        b.crease(x - 0.8, y - 0.4, x + 0.8, y + 0.4)


def limbs_marine(b: Body):
    b.cap(10.4, 2.0, 9.4, 4.6, 1.3, 1.2)                  # 肱骨
    b.ell(8.0, 9.2, 3.6, 5.4, 0.45, flat=0.55)            # 鳍
    for y in (6.6, 9.4, 12.0):                            # 一块块的指骨
        b.crease(4.4, y + 0.8, 11.8, y - 2.0)
    b.crease(6.2, 5.0, 8.6, 14.0)
    b.crease(8.6, 4.4, 11.0, 12.6)


def limbs_exotic(b: Body):
    pts = [(11.6, 1.8), (8.2, 5.6), (9.4, 9.4)]
    b.chain(pts, 1.1, 0.8)
    for p in pts[1:]:
        b.ell(*p, 1.2, 1.2, lift=0.3)
    b.chain([(9.4, 9.4), (7.0, 11.8), (4.6, 13.6)], 0.6, 0.4)      # 分叉的两根
    b.chain([(9.4, 9.4), (11.4, 11.8), (12.4, 14.4)], 0.6, 0.4)
    claw(b, 4.6, 13.6, 2.6, 14.2, 0.45)
    claw(b, 12.4, 14.4, 14.2, 14.4, 0.45)
    b.spot(9.4, 9.4, VIOLET_GLOW, 3)


# ---- 尾骨（从左上往右下）

def tail_titan(b: Body):
    vert_chain(b, along((2.0, 3.0), (10.2, 10.2), 3, bend=-0.5), 1.4, 0.9, spine=0.9)
    b.ell(12.0, 12.0, 2.6, 2.0, 0.75)                      # 尾锤
    b.ell(10.8, 13.2, 1.2, 1.0, lift=0.6)
    b.ell(13.2, 10.8, 1.2, 1.0, lift=0.6)


def tail_predator(b: Body):
    vert_chain(b, along((1.8, 2.8), (14.2, 13.0), 5, bend=-0.4), 1.25, 0.45, spine=0.8)


def tail_longneck(b: Body):
    vert_chain(b, [(1.8, 2.8), (4.4, 6.4), (7.6, 9.0), (10.8, 10.4), (13.8, 9.8)], 1.3, 0.6, spine=0.6)
    b.cap(13.6, 10.0, 14.8, 8.8, 0.5, 0.4)


def tail_flyer(b: Body):
    vert_chain(b, along((2.2, 2.6), (10.2, 10.2), 3), 0.8, 0.65)
    b.ell(12.0, 12.0, 2.6, 1.6, math.pi / 4, flat=0.5)    # 尾端的菱形尾翼
    b.crease(11.0, 11.0, 13.4, 13.4)


def tail_worm(b: Body):
    pts = along((2.6, 2.6), (11.6, 11.6), 3)
    for i in range(3):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        b.ell((x0 + x1) / 2, (y0 + y1) / 2, 1.8, 2.6 - i * 0.55, math.pi / 4, flat=0.8)
    vert_chain(b, pts, 2.4, 1.4)
    claw(b, 11.4, 11.4, 14.2, 14.2, 0.8)                  # 尾尖


def tail_arthropod(b: Body):
    pts = [(2.0, 11.0), (5.2, 12.8), (8.8, 12.4), (11.4, 9.8), (12.2, 6.6)]
    for i in range(4):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        b.ell((x0 + x1) / 2, (y0 + y1) / 2, math.hypot(x1 - x0, y1 - y0) / 2 + 0.3, 1.3 - i * 0.1,
              math.atan2(y1 - y0, x1 - x0))
    vert_chain(b, pts, 0.1, 0.1)
    b.ell(11.8, 4.6, 1.3, 1.1, lift=0.3)                   # 毒囊
    b.chain([(11.4, 3.8), (10.0, 2.2), (8.2, 2.8)], 0.6, 0.3, TOOTH_M, lift=0.4)   # 尾刺


def tail_marine(b: Body):
    vert_chain(b, along((1.8, 3.6), (9.6, 8.8), 3), 1.2, 0.75)
    b.chain([(9.4, 8.8), (11.8, 6.2), (14.0, 3.2)], 1.0, 0.35)     # 新月形的尾鳍
    b.chain([(9.4, 8.8), (11.6, 11.4), (13.8, 13.8)], 1.0, 0.35)


def tail_exotic(b: Body):
    vert_chain(b, along((2.0, 2.4), (7.6, 8.0), 2), 1.1, 0.9, spine=0.6)
    b.chain([(7.6, 8.0), (10.6, 8.0), (13.4, 5.6)], 0.65, 0.4)     # 分叉的尾巴
    b.chain([(7.6, 8.0), (9.2, 11.0), (11.0, 13.6)], 0.65, 0.4)
    claw(b, 13.4, 5.6, 14.4, 3.6, 0.45)
    claw(b, 11.0, 13.6, 12.2, 14.6, 0.45)
    b.spot(7.6, 8.0, VIOLET_GLOW, 3)


SHAPES = {name: fn for name, fn in dict(globals()).items()
          if callable(fn) and "_" in name and name.split("_", 1)[0] in PARTS and name.split("_", 1)[1] in FAMILY}


def bone_body(family: str, part: str) -> Body:
    b = Body()
    SHAPES[f"{part}_{family}"](b)
    return b


def bone_icon(family: str, part: str) -> Canvas:
    return limit_colors(bone_body(family, part).render(FAMILY[family]), 16)


def register_bones():
    for family in FAMILIES:
        for part in PARTS:
            def make(family=family, part=part):
                def fn(rng):
                    return bone_icon(family, part)
                fn.__name__ = f"bone_{family}_{part}"
                return fn
            texture(f"item/bone_{family}_{part}", refs=("item/bone", "item/skeleton_skull"))(make())


register_bones()


# ---------------------------------------------------------------- 化石展品

def exhibit_body() -> Body:
    """一副侧面的小骨架：头在右边，胸廓是一圈，前后两条腿，尾巴垂在左边。"""
    b = Body()
    vert_chain(b, [(2.6, 6.2), (5.0, 5.4), (7.6, 5.2), (10.4, 5.0)], 0.75, 0.7, spine=0.5)
    b.chain([(2.6, 6.2), (1.4, 8.0), (1.6, 9.8)], 0.5, 0.4)            # 尾巴垂下来
    b.ell(12.6, 4.2, 1.9, 1.4, -0.2)                                   # 头骨
    b.cap(13.0, 4.8, 14.6, 5.2, 0.8, 0.45)                             # 吻
    b.hole(12.2, 3.8, 0.55)
    b.ell(7.0, 7.4, 2.6, 1.7)                                          # 胸廓
    b.hole(7.0, 7.6, 1.5, 0.75)
    b.cap(4.0, 6.0, 3.6, 10.8, 0.6, 0.5)                               # 后腿
    b.cap(10.0, 5.6, 10.6, 10.8, 0.55, 0.5)                            # 前腿
    return b


@texture("item/fossil_exhibit", refs=("item/armor_stand", "item/bone"))
def fossil_exhibit(rng):
    c = exhibit_body().render(FAMILY["titan"])
    # 石座和铜牌
    for x, y in c.cells(0, 11, 15, 15):
        c.clear(x, y)
    c.hline(1, 14, 11, BASALT, 1)
    c.rect(1, 12, 14, 13, BASALT, 3)
    c.hline(2, 13, 12, BASALT, 4)
    c.hline(1, 14, 14, BASALT, 1)
    c.vline(0, 12, 13, BASALT, 2)
    c.vline(15, 12, 14, BASALT, 0)
    c.hline(2, 15, 15, BASALT, 0)
    c.set(0, 14, BASALT, 1)
    c.hline(6, 9, 13, COPPER, 4)
    c.set(6, 13, COPPER, 5, glint=True)
    # 两条腿踩在石座上
    for x in (3, 10):
        c.set(x, 11, FAMILY["titan"], 3)
        c.set(x + 1, 11, FAMILY["titan"], 1)
    return limit_colors(c, 16)


# ---------------------------------------------------------------- 空格子的图标

def ghost(mask: set, holes: set = frozenset()) -> Canvas:
    """原版空格子图标的画法：一圈一像素的灰线描出轮廓，里面是空的；窟窿也描一圈。"""
    c = Canvas(16, 16)
    for (x, y) in mask:
        edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        rim = any((x + dx, y + dy) in holes for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge or rim:
            c.set(x, y, GHOST, 0, glint=True)
    return c


def ghost_of_canvas(c: Canvas) -> Canvas:
    """按画好的图标描：不透明的像素是轮廓，眼眶这种窟窿另描一圈。"""
    holes = {(x, y) for x, y in c.cells() if c.get(x, y) and c.get(x, y)[0] is HOLLOW}
    mask = {(x, y) for x, y in c.cells() if c.get(x, y)} - holes
    return ghost(mask, holes)


for _part in PARTS:
    def _make(part=_part):
        def fn(rng):
            return ghost_of_canvas(bone_icon("predator", part))
        fn.__name__ = f"slot_bone_{part}"
        return fn
    texture(f"gui/sprites/container/slot/bone_{_part}", refs=("gui/sprites/container/slot/helmet",))(_make())


@texture("gui/sprites/container/slot/bone_any", refs=("gui/sprites/container/slot/sword",))
def slot_bone_any(rng):
    b = Body()
    b.cap(4.4, 11.6, 11.6, 4.4, 0.9)
    for cx, cy in ((3.0, 11.2), (4.8, 13.0), (11.2, 3.0), (13.0, 4.8)):
        b.ell(cx, cy, 1.5, 1.5)
    return ghost(b.mask())


@texture("gui/sprites/container/slot/fossil_exhibit", refs=("gui/sprites/container/slot/helmet",))
def slot_fossil_exhibit(rng):
    return ghost_of_canvas(fossil_exhibit(rng))


# ---------------------------------------------------------------- 立体骨头的骨面

def value_noise(rng: random.Random, w: int, h: int, cell: int) -> list[list[float]]:
    """平滑的值噪声，横向和纵向都能接缝。"""
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


@texture("entity/fossil/bone", kind="entity", refs=("block/bone_block_side", "entity/skeleton/skeleton"))
def bone_surface(rng):
    c = Canvas(64, 64)
    # 骨面：大块的、柔和的明暗起伏，稀疏的细孔，几道顺着长的裂纹，矿物染的淡斑
    n1 = value_noise(rng, 64, 32, 8)
    for y in range(32):
        for x in range(64):
            c.set(x, y, BONE, 4 if n1[y][x] < 0.32 else 5)
    c.clusters(rng, 0, 0, 63, 31, -1, 60, size=2, only=BONE)
    c.clusters(rng, 0, 0, 63, 31, 1, 26, size=2, only=BONE)
    for _ in range(26):                              # 细孔
        x, y = rng.randint(0, 63), rng.randint(0, 31)
        c.set(x, y, BONE, 2, glint=True)
    for _ in range(12):                              # 裂纹
        x, y = rng.randint(0, 63), rng.randint(0, 31)
        for _ in range(rng.randint(3, 6)):
            if c.get(x, y):
                c.set(x, y, BONE, 3, glint=True)
            x = (x + rng.choice((1, 1, 2))) % 64
            y = max(0, min(31, y + rng.choice((-1, 0, 0, 1))))
    for _ in range(9):                               # 染斑
        cx, cy = rng.randint(0, 63), rng.randint(0, 31)
        for x, y in c.disc_cells(cx, cy, rng.choice((1.0, 1.5, 2.0))):
            if y < 32 and rng.random() < 0.8:
                c.recolor(x, y, SAND, -1)
    # 窟窿：越往中间越深
    for y in range(32, 48):
        t = abs(y - 39.5) / 8
        for x in range(64):
            c.set(x, y, HOLLOW, 0 if t < 0.45 else 1 if t < 0.8 else 2)
    c.clusters(rng, 0, 32, 63, 47, 1, 40, size=2, only=HOLLOW)
    # 牙和角：更白，竖着的细纹，根部发黄
    for y in range(48, 64):
        for x in range(64):
            c.set(x, y, CREAM, (5 if x % 3 else 4) - (1 if y >= 60 else 0))
    c.clusters(rng, 0, 48, 63, 63, -1, 30, size=2, only=CREAM)
    return c


# ---------------------------------------------------------------- 石哨兵

def faces(u: int, v: int, w: int, h: int, d: int) -> dict:
    """和 hostiles.py 一样：模型方块展开后每个面的矩形 (x, y, 宽, 高)。"""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def facets(c: Canvas, rect, rng: random.Random, n: int, base: int = 4, moss: float = 0.0, spread=(-1, 0, 0, 1)):
    """把一块面切成 n 块大的石棱面：每块一个明暗，块与块之间一道暗缝，缝的右下一侧亮一级（光从左上来）。

    moss 大于 0 时，靠上、受光的地方长一层苔藓。
    """
    x0, y0, w, h = rect
    if w <= 0 or h <= 0:
        return
    seeds = [(x0 + rng.uniform(-0.5, w - 0.5), y0 + rng.uniform(-0.5, h - 0.5)) for _ in range(max(1, n))]
    tone = [base + rng.choice(spread) for _ in seeds]
    cell = {}
    for x, y in c.cells(x0, y0, x0 + w - 1, y0 + h - 1):
        # 纵向压扁一点：石块多是横着裂的
        cell[(x, y)] = min(range(len(seeds)), key=lambda k: (x - seeds[k][0]) ** 2 + 1.6 * (y - seeds[k][1]) ** 2)
    for (x, y), k in cell.items():
        c.set(x, y, STONE, tone[k])
    for (x, y), k in cell.items():
        right, down = cell.get((x + 1, y), k), cell.get((x, y + 1), k)
        left, up = cell.get((x - 1, y), k), cell.get((x, y - 1), k)
        if right != k or down != k:
            c.set(x, y, STONE, max(1, tone[k] - 2))
        elif left != k or up != k:
            c.shift(x, y, 1)
    c.clusters(rng, x0, y0, x0 + w - 1, y0 + h - 1, -1, max(1, w * h // 16), size=2, only=STONE)
    if moss > 0:
        for (x, y), k in cell.items():
            if (y - y0) < h * moss * 1.6 and rng.random() < 0.6 and c.level(x, y) >= base:
                c.recolor(x, y, MOSS, -1)


class Rock:
    """风化的岩石皮：底图 c，白色亮度的发光层 g（渲染时着色）。"""

    def __init__(self, w: int, h: int, rng: random.Random):
        self.c = Canvas(w, h)
        self.g = Canvas(w, h)
        self.rng = rng

    def box(self, u, v, w, h, d, n=3, top_moss=0.0, dark_bottom=True):
        f = faces(u, v, w, h, d)
        for key, rect in f.items():
            area = rect[2] * rect[3]
            k = max(1, min(n, area // 12))
            facets(self.c, rect, self.rng, k, base=3 if key == "bottom" and dark_bottom else 4,
                   moss=top_moss if key == "top" else 0.0)
        return f

    def glow(self, x, y, strength):
        lv = 5 if strength >= 0.8 else 4 if strength >= 0.5 else 3
        self.g.set(x, y, WHITE_GLOW, lv, glint=True, alpha=int(255 * min(1.0, 0.35 + strength * 0.75)))

    def fissure(self, rect, pts, glow=0.0):
        """一道裂缝（面里的折线）：底图是深色的缝，glow 大于 0 时发光层上也画一道，越往末端越暗。"""
        x0, y0, w, h = rect
        cells = []
        for (i0, j0), (i1, j1) in zip(pts, pts[1:]):
            n = max(abs(i1 - i0), abs(j1 - j0))
            for t in range(n + 1):
                p = (round(i0 + (i1 - i0) * t / max(1, n)), round(j0 + (j1 - j0) * t / max(1, n)))
                if p not in cells:
                    cells.append(p)
        for k, (i, j) in enumerate(cells):
            if 0 <= i < w and 0 <= j < h:
                self.c.set(x0 + i, y0 + j, BASALT, 0 if glow else 1, glint=True)
                if glow > 0:
                    self.glow(x0 + i, y0 + j, glow * (1 - 0.5 * k / max(1, len(cells) - 1)))


def sentry_skin() -> Rock:
    s = Rock(64, 64, random.Random(0x5E47))
    # 身子 10×10×10
    body = s.box(0, 0, 10, 10, 10, n=4, top_moss=0.45)
    fx, fy, fw, fh = body["front"]
    # 眉石底下的影子，眼缝是一道杏仁形的横缝：中间最亮
    s.c.hline(fx, fx + fw - 1, fy + 2, STONE, 1)
    for i in range(2, 8):
        s.c.set(fx + i, fy + 3, BASALT, 0, glint=True)
        s.glow(fx + i, fy + 3, 1.0 if 3 <= i <= 6 else 0.55)
    for i in range(3, 7):
        s.c.set(fx + i, fy + 4, BASALT, 1, glint=True)
        s.glow(fx + i, fy + 4, 0.5 if 4 <= i <= 5 else 0.3)
    s.c.set(fx + 1, fy + 3, STONE, 1, glint=True)
    s.c.set(fx + 8, fy + 3, STONE, 1, glint=True)
    s.fissure(body["front"], [(1, 5), (0, 7)])                          # 眼角往下的裂纹
    s.fissure(body["front"], [(8, 5), (9, 7)])
    s.c.hline(fx + 2, fx + 7, fy + 9, BASALT, 1)                        # 颚石接在下面的缝
    s.fissure(body["back"], [(5, 0), (4, 3), (5, 6), (4, 9)], glow=0.6)  # 背上一道深缝，透出里面的火
    s.fissure(body["right"], [(3, 0), (4, 3), (3, 5)])
    s.fissure(body["left"], [(6, 1), (5, 4), (6, 7)])
    # 冠石 6×3×5
    s.box(0, 20, 6, 3, 5, n=2, top_moss=0.6)
    # 颚石 8×2×8：正面一排参差的石齿
    jaw = s.box(22, 20, 8, 2, 8, n=3)
    jx, jy, jw, _ = jaw["front"]
    for i in range(jw):
        s.c.set(jx + i, jy + 1, STONE, 5 if i % 2 == 0 else 1, glint=True)
    # 眉石 11×2×4：伸出来的一块，底面暗
    brow = s.box(32, 30, 11, 2, 4, n=3, top_moss=0.5)
    bx, by, bw, bh = brow["bottom"]
    s.c.shift_rect(bx, by, bx + bw - 1, by + bh - 1, -1)
    # 拳石 5×6×5：正面一道发光的细缝
    fist = s.box(0, 30, 5, 6, 5, n=2, top_moss=0.4)
    s.fissure(fist["front"], [(2, 0), (1, 2), (2, 4)], glow=0.3)
    # 碎石 3×3×3
    s.box(20, 30, 3, 3, 3, n=1)
    # 扔出来的石块 6×5×6（SentryRockRenderer）
    rock = s.box(0, 41, 6, 5, 6, n=2)
    s.fissure(rock["front"], [(2, 0), (3, 2), (2, 4)])
    return s


_SKIN = None


def skin() -> Rock:
    global _SKIN
    if _SKIN is None:
        _SKIN = sentry_skin()
    return _SKIN


@texture("entity/stone_sentry", kind="entity", rough=True, refs=("entity/iron_golem/iron_golem", "block/cobblestone"))
def stone_sentry(rng):
    return skin().c


@texture("entity/stone_sentry_glow", kind="entity")
def stone_sentry_glow(rng):
    return skin().g


# ---- 沉睡的石哨兵（方块）

def sentry_glow_level(stage: int, strength: float) -> int:
    """被吵醒的次数和裂缝的亮度换成琥珀光的级数；没吵醒时不亮（-1）。"""
    if stage == 0:
        return -1
    v = strength * (0.55 if stage == 1 else 1.0)
    return 4 if v > 0.75 else 3 if v > 0.45 else 2 if v > 0.25 else 1


def ember(c: Canvas, x: int, y: int, stage: int, strength: float, dark: int = 0):
    lv = sentry_glow_level(stage, strength)
    if lv < 0:
        c.set(x, y, BASALT, dark, glint=True)
    else:
        c.set(x, y, AMBER_GLOW, lv, glint=True)


def dormant_base(seed: int, moss_top: bool = False) -> Canvas:
    """方块的侧面、正面共用的底：身子在第 5–15 行（x 1–14），冠石在第 2–4 行（x 3–12），其余的地方模型用不到。"""
    rng = random.Random(seed)
    c = Canvas(16, 16)
    facets(c, (0, 0, 16, 16), rng, 3)
    facets(c, (1, 5, 14, 11), rng, 5, base=4)
    facets(c, (3, 2, 10, 3), rng, 2, base=4, moss=0.5)
    c.hline(3, 12, 5, STONE, 1)                      # 冠石脚下的影子
    c.vline(14, 5, 15, STONE, 1)                     # 身子右边、下边暗
    c.hline(1, 14, 15, STONE, 1)
    c.vline(1, 6, 14, STONE, 5)
    return c


def dormant_front(stage: int) -> Canvas:
    c = dormant_base(0xD0F1)
    # 抱在前面的两块拳石：圆的，上、左亮，四周一圈暗缝
    for cx in (3.0, 12.0):
        c.shade_disc(cx, 12.6, 2.2, STONE, 4, amp=2.6, spec=False)
        c.ring(cx, 12.6, 2.6, STONE, 1, 0.8)
    # 眉石：一道伸出来的横棱，底下是影子
    c.hline(2, 13, 6, STONE, 5)
    c.hline(2, 13, 7, STONE, 1)
    # 杏仁形的眼缝
    for x in range(5, 11):
        ember(c, x, 8, stage, 1.0 if 6 <= x <= 9 else 0.6)
    for x in range(6, 10):
        ember(c, x, 9, stage, 0.5, dark=1)
    # 吵醒两次以后，眼角的裂纹也透出火光
    for pts in (((4, 8), (3, 9), (3, 10)), ((11, 8), (12, 9), (12, 10))):
        for k, (x, y) in enumerate(pts):
            if stage == 2:
                ember(c, x, y, stage, 0.6 - k * 0.2)
            else:
                c.set(x, y, STONE, 1, glint=True)
    c.hline(6, 9, 11, BASALT, 1)                     # 颚的缝
    return c


def dormant_side(stage: int) -> Canvas:
    c = dormant_base(0xD0F2)
    c.shade_disc(3.0, 12.6, 2.2, STONE, 4, amp=2.6, spec=False)    # 侧面看得见一块拳石
    c.ring(3.0, 12.6, 2.6, STONE, 1, 0.8)
    pts = [(10, 5), (9, 7), (10, 9), (9, 11), (10, 13)]
    for k, (x, y) in enumerate(pts):                 # 一道从肩上往下的裂缝，吵得越多亮得越长
        if k <= 1 + stage * 2:
            ember(c, x, y, stage, 0.8 - k * 0.12, dark=1)
        else:
            c.set(x, y, STONE, 1, glint=True)
    return c


@texture("block/dormant_stone_sentry_top", rough=True, refs=("block/cobblestone", "block/mossy_cobblestone"))
def dormant_top(rng):
    c = Canvas(16, 16)
    facets(c, (0, 0, 16, 16), rng, 6, base=4)
    facets(c, (3, 3, 10, 10), rng, 3, base=4, moss=0.45)            # 冠石的顶
    c.hline(4, 13, 13, STONE, 1)                     # 冠石在肩上投的影子
    c.vline(13, 4, 13, STONE, 1)
    c.hline(1, 14, 1, STONE, 5)
    c.vline(1, 1, 14, STONE, 5)
    return c


@texture("block/dormant_stone_sentry_bottom", rough=True, refs=("block/cobblestone",))
def dormant_bottom(rng):
    c = Canvas(16, 16)
    facets(c, (0, 0, 16, 16), rng, 6, base=3)
    return c


for _stage in range(3):
    def _front(rng, stage=_stage):
        return dormant_front(stage)

    def _side(rng, stage=_stage):
        return dormant_side(stage)
    _front.__name__ = f"dormant_front_{_stage}"
    _side.__name__ = f"dormant_side_{_stage}"
    texture(f"block/dormant_stone_sentry_front_{_stage}", rough=True, tile=False, refs=("block/carved_pumpkin",))(_front)
    texture(f"block/dormant_stone_sentry_side_{_stage}", rough=True, tile=False)(_side)


# ---------------------------------------------------------------- 化石底座

@texture("block/fossil_pedestal_side", tile=False, refs=("block/smooth_stone", "block/chiseled_stone_bricks"))
def fossil_pedestal_side(rng):
    """顶板在第 4–5 行（x 2–13），立柱第 6–13 行（x 4–11，带两道凹槽），底板第 14–15 行（x 1–14）；最上面四行接着画立柱。"""
    c = Canvas(16, 16)
    c.cylinder(0, 0, 15, 15, STONE, 4, vertical=True, amp=1)
    c.cylinder(4, 0, 11, 15, STONE, 4, vertical=True, amp=2)
    for x in (6, 9):
        c.vline(x, 0, 15, STONE, 2)
        c.vline(x + 1, 0, 15, STONE, 5)
    c.clusters(rng, 0, 0, 15, 15, -1, 10, size=2, only=STONE)
    c.panel(2, 4, 13, 5, STONE, 5)
    c.hline(2, 13, 4, STONE, 6)
    c.hline(4, 11, 6, STONE, 2)                      # 顶板底下的影子
    c.hline(4, 11, 13, STONE, 3)
    c.panel(1, 14, 14, 15, STONE, 4)
    c.hline(1, 14, 14, STONE, 5)
    return c


@texture("block/fossil_pedestal_top", refs=("block/smooth_stone",))
def fossil_pedestal_top(rng):
    """顶板是 x 2–13 的方块，中间凿一个放骨头的浅槽；最外一圈是底板露出来的边。"""
    c = Canvas(16, 16)
    c.fill(STONE, 4)
    c.clusters(rng, 0, 0, 15, 15, -1, 14, size=2, only=STONE)
    c.bevel(0, 0, 15, 15, 1, -1)
    c.panel(2, 2, 13, 13, STONE, 5)
    c.edge_wear(rng, 2, 2, 13, 13, 0.2)
    for x, y in c.disc_cells(7.5, 7.5, 3.2):
        c.set(x, y, STONE, 3)
    c.ring(7.5, 7.5, 3.2, STONE, 2, 1.0)
    for x, y in c.disc_cells(7.5, 7.5, 3.2):         # 凹槽：右下的槽壁受光
        if x - 7.5 + y - 7.5 > 3.0:
            c.set(x, y, STONE, 5)
    return c


@texture("block/fossil_pedestal_bottom", rough=True, refs=("block/stone",))
def fossil_pedestal_bottom(rng):
    c = Canvas(16, 16)
    c.fill(STONE, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 16, size=2, only=STONE)
    c.clusters(rng, 0, 0, 15, 15, 1, 10, size=2, only=STONE)
    c.bevel(0, 0, 15, 15, 1, -1)
    return c


# ---------------------------------------------------------------- 骨架展台

def display_plinth(rng: random.Random) -> Canvas:
    """侧面：深色的石台在第 13–15 行，铺沙的台面在第 11–12 行；上面用不到的行接着画石台。"""
    c = Canvas(16, 16)
    c.fill(BASALT, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 14, size=2, only=BASALT)
    for y in range(0, 11):
        if y % 4 == 3:
            c.hline(0, 15, y, BASALT, 2)
    c.hline(0, 15, 13, BASALT, 4)
    c.hline(0, 15, 14, BASALT, 3)
    c.hline(0, 15, 15, BASALT, 1)
    c.edge_wear(rng, 0, 13, 15, 15, 0.25)
    c.rect(1, 11, 14, 12, SAND, 4)
    c.hline(1, 14, 11, SAND, 5)
    c.clusters(rng, 1, 11, 14, 12, -1, 4, size=2, only=SAND)
    return c


@texture("block/skeleton_display_side", tile=False, refs=("block/polished_basalt_side", "block/sandstone"))
def skeleton_display_side(rng):
    return display_plinth(rng)


@texture("block/skeleton_display_front", tile=False, refs=("block/polished_basalt_side",))
def skeleton_display_front(rng):
    c = display_plinth(random.Random(0xD15F))
    # 铜牌的托槽
    c.rect(4, 13, 11, 14, BASALT, 2)
    # 铜牌本身画在最上面两行（模型里铜牌那块的 UV 指到这里）
    c.rect(4, 0, 11, 1, COPPER, 4)
    c.hline(4, 11, 0, COPPER, 5)
    c.set(5, 0, COPPER, 6, glint=True)
    for x in (6, 7, 9):                              # 刻的字
        c.set(x, 1, COPPER, 2, glint=True)
    c.set(11, 1, COPPER, 2)
    return c


@texture("block/skeleton_display_top", refs=("block/sand", "block/suspicious_sand_0"))
def skeleton_display_top(rng):
    c = Canvas(16, 16)
    n = value_noise(rng, 16, 16, 4)
    for x, y in c.cells():
        c.set(x, y, SAND, 5 if n[y][x] > 0.55 else 4)
    c.clusters(rng, 1, 1, 14, 14, -1, 10, size=3, only=SAND)
    c.clusters(rng, 1, 1, 14, 14, 1, 6, size=2, only=SAND)
    for _ in range(5):                               # 几粒小石子
        x, y = rng.randint(2, 13), rng.randint(2, 13)
        c.set(x, y, STONE, 4, glint=True)
        c.set(x + 1, y + 1, STONE, 2, glint=True)
    for _ in range(3):                               # 骨头碎屑
        x, y = rng.randint(2, 12), rng.randint(2, 12)
        c.set(x, y, BONE, 5, glint=True)
        c.set(x + 1, y, BONE, 4, glint=True)
    c.bevel(1, 1, 14, 14, 1, -1)
    return c


@texture("block/skeleton_display_bottom", refs=("block/polished_basalt_top",))
def skeleton_display_bottom(rng):
    c = Canvas(16, 16)
    c.fill(BASALT, 3)
    c.clusters(rng, 0, 0, 15, 15, -1, 12, size=2, only=BASALT)
    c.clusters(rng, 0, 0, 15, 15, 1, 6, size=2, only=BASALT)
    c.bevel(0, 0, 15, 15, 1, -1)
    c.frame(1, 1, 14, 14, BASALT, 2)
    return c
