"""按色阶画的像素画布（ADR-0031 §三）。

每个像素记"哪条色阶、第几级"，不记 RGB；画倒角、阴影、高光都是在级数上加减，最后统一换成颜色。
好处：一张贴图里的颜色都来自调色板，明暗关系一致，换一条色阶就整体换色。

光从左上来：凸起的东西上边、左边亮，下边、右边暗；凹进去的反过来。
坐标都是闭区间，(0, 0) 在左上角。
"""
from __future__ import annotations

import math
import random
from typing import Callable, Iterable, Optional

from PIL import Image

from color import Ramp

Pixel = Optional[tuple]  # (Ramp, level) 或 None（透明）

# 光的方向：从左上方、略微从前面照过来
LIGHT = (-0.58, -0.62, 0.53)


class Canvas:
    def __init__(self, w: int = 16, h: int = 16):
        self.w, self.h = w, h
        self.p: list[list[Pixel]] = [[None] * w for _ in range(h)]
        # 允许单独出现的像素（高光点、指示灯、铆钉），检查"孤立像素"时不算
        self.glints: set[tuple[int, int]] = set()
        # 半透明的像素（玻璃、液体、柔和的光晕）：(x, y) -> 不透明度 0–255；不在里面的就是不透明
        self.alpha: dict[tuple[int, int], int] = {}

    # ---- 基础 ----
    def inside(self, x: int, y: int) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x: int, y: int) -> Pixel:
        return self.p[y][x] if self.inside(x, y) else None

    def set(self, x: int, y: int, r: Ramp, lv: int, glint: bool = False, alpha: Optional[int] = None):
        """alpha 给了就是半透明（0–255），不给就是不透明。"""
        if self.inside(x, y):
            self.p[y][x] = (r, max(0, min(r.top, lv)))
            if glint:
                self.glints.add((x, y))
            if alpha is None or alpha >= 255:
                self.alpha.pop((x, y), None)
            else:
                self.alpha[(x, y)] = max(0, int(alpha))

    def clear(self, x: int, y: int):
        if self.inside(x, y):
            self.p[y][x] = None
            self.alpha.pop((x, y), None)
            self.glints.discard((x, y))

    def opacity(self, x: int, y: int, alpha: int):
        """只改不透明度，不改颜色。"""
        if self.get(x, y):
            if alpha >= 255:
                self.alpha.pop((x, y), None)
            else:
                self.alpha[(x, y)] = max(0, int(alpha))

    def level(self, x: int, y: int) -> int:
        px = self.get(x, y)
        return px[1] if px else -1

    def shift(self, x: int, y: int, d: int):
        px = self.get(x, y)
        if px:
            self.p[y][x] = (px[0], max(0, min(px[0].top, px[1] + d)))

    def recolor(self, x: int, y: int, r: Ramp, d: int = 0):
        """换色阶，保留明暗（按两条色阶的相对位置换算级数）。"""
        px = self.get(x, y)
        if px:
            t = px[1] / max(1, px[0].top)
            self.p[y][x] = (r, max(0, min(r.top, round(t * r.top) + d)))

    def cells(self, x0=0, y0=0, x1=None, y1=None) -> Iterable[tuple[int, int]]:
        x1 = self.w - 1 if x1 is None else x1
        y1 = self.h - 1 if y1 is None else y1
        for y in range(max(0, y0), min(self.h - 1, y1) + 1):
            for x in range(max(0, x0), min(self.w - 1, x1) + 1):
                yield x, y

    def copy(self) -> "Canvas":
        c = Canvas(self.w, self.h)
        c.p = [row[:] for row in self.p]
        c.glints = set(self.glints)
        c.alpha = dict(self.alpha)
        return c

    def crop(self, x0: int, y0: int, w: int, h: int) -> "Canvas":
        c = Canvas(w, h)
        for y in range(h):
            for x in range(w):
                if self.inside(x + x0, y + y0):
                    c.p[y][x] = self.p[y + y0][x + x0]
        c.glints = {(x - x0, y - y0) for x, y in self.glints if x0 <= x < x0 + w and y0 <= y < y0 + h}
        c.alpha = {(x - x0, y - y0): a for (x, y), a in self.alpha.items() if x0 <= x < x0 + w and y0 <= y < y0 + h}
        return c

    def paste(self, other: "Canvas", ox: int = 0, oy: int = 0):
        for y in range(other.h):
            for x in range(other.w):
                px = other.p[y][x]
                if px and self.inside(x + ox, y + oy):
                    self.p[y + oy][x + ox] = px
                    if (x, y) in other.glints:
                        self.glints.add((x + ox, y + oy))
                    if (x, y) in other.alpha:
                        self.alpha[(x + ox, y + oy)] = other.alpha[(x, y)]
                    else:
                        self.alpha.pop((x + ox, y + oy), None)

    def flip_h(self) -> "Canvas":
        c = self.copy()
        c.p = [row[::-1] for row in self.p]
        c.glints = {(self.w - 1 - x, y) for x, y in self.glints}
        c.alpha = {(self.w - 1 - x, y): a for (x, y), a in self.alpha.items()}
        return c

    def rot90(self) -> "Canvas":
        """顺时针转 90 度（只用于对称的图案，光的方向不跟着转）。"""
        c = Canvas(self.h, self.w)
        for y in range(self.h):
            for x in range(self.w):
                c.p[x][self.h - 1 - y] = self.p[y][x]
        c.glints = {(self.h - 1 - y, x) for x, y in self.glints}
        c.alpha = {(self.h - 1 - y, x): a for (x, y), a in self.alpha.items()}
        return c

    # ---- 形状 ----
    def fill(self, r: Ramp, lv: int):
        self.rect(0, 0, self.w - 1, self.h - 1, r, lv)

    def rect(self, x0, y0, x1, y1, r: Ramp, lv: int):
        for x, y in self.cells(x0, y0, x1, y1):
            self.set(x, y, r, lv)

    def shift_rect(self, x0, y0, x1, y1, d: int):
        for x, y in self.cells(x0, y0, x1, y1):
            self.shift(x, y, d)

    def hline(self, x0, x1, y, r: Ramp, lv: int):
        self.rect(x0, y, x1, y, r, lv)

    def vline(self, x, y0, y1, r: Ramp, lv: int):
        self.rect(x, y0, x, y1, r, lv)

    def line(self, x0, y0, x1, y1, r: Ramp, lv: int):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, r, lv)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def frame(self, x0, y0, x1, y1, r: Ramp, lv: int):
        self.hline(x0, x1, y0, r, lv)
        self.hline(x0, x1, y1, r, lv)
        self.vline(x0, y0, y1, r, lv)
        self.vline(x1, y0, y1, r, lv)

    def disc_cells(self, cx: float, cy: float, rad: float) -> list[tuple[int, int]]:
        """像素圆：圆心可以在格子中间（x.5）；半径小时用经验的阈值，边缘不长刺。"""
        out = []
        k = rad * rad + rad * 0.6
        for y in range(self.h):
            for x in range(self.w):
                if (x - cx) ** 2 + (y - cy) ** 2 <= k:
                    out.append((x, y))
        return out

    def disc(self, cx, cy, rad, r: Ramp, lv: int):
        for x, y in self.disc_cells(cx, cy, rad):
            self.set(x, y, r, lv)

    def ring(self, cx, cy, rad, r: Ramp, lv: int, width: float = 1.0):
        inner = set(self.disc_cells(cx, cy, rad - width))
        for x, y in self.disc_cells(cx, cy, rad):
            if (x, y) not in inner:
                self.set(x, y, r, lv)

    # ---- 光影 ----
    def bevel(self, x0, y0, x1, y1, hi: int = 1, lo: int = -1, raised: bool = True):
        """给一块区域的四边加倒角：凸起时上、左亮，下、右暗；凹进去反过来。"""
        if not raised:
            hi, lo = lo, hi
        # 右上、左下两个角在明暗交界上，不加不减
        for x in range(x0, x1):
            self.shift(x, y0, hi)
        for x in range(x0 + 1, x1 + 1):
            self.shift(x, y1, lo)
        for y in range(y0 + 1, y1):
            self.shift(x0, y, hi)
            self.shift(x1, y, lo)

    def panel(self, x0, y0, x1, y1, r: Ramp, lv: int, raised: bool = True, depth: int = 1):
        """一块面板：底色加倒角。depth 是倒角的明暗级差。"""
        self.rect(x0, y0, x1, y1, r, lv)
        self.bevel(x0, y0, x1, y1, depth, -depth, raised)

    def recess(self, x0, y0, x1, y1, r: Ramp, lv: int):
        """凹槽：上、左边是阴影（深两级），下、右边受光。"""
        self.rect(x0, y0, x1, y1, r, lv)
        self.hline(x0, x1, y0, r, lv - 2)
        self.vline(x0, y0, y1, r, lv - 2)
        self.hline(x0 + 1, x1, y1, r, lv + 1)
        self.vline(x1, y0 + 1, y1, r, lv + 1)

    def shade_disc(self, cx, cy, rad, r: Ramp, base: int, amp: float = 2.0, spec: bool = True, convex: bool = True):
        """球面或圆形的凸起（凹陷）按法线打光。"""
        cells = self.disc_cells(cx, cy, rad)
        best, best_v = None, -9
        for x, y in cells:
            nx, ny = (x - cx) / (rad + 0.5), (y - cy) / (rad + 0.5)
            if not convex:
                nx, ny = -nx, -ny
            nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
            v = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
            self.set(x, y, r, base + round(amp * (v - 0.55) * 2.2))
            if v > best_v:
                best, best_v = (x, y), v
        if spec and best:
            self.set(best[0], best[1], r, r.top, glint=True)

    def cylinder(self, x0, y0, x1, y1, r: Ramp, base: int, vertical: bool = True, amp: int = 2):
        """圆柱（管子、线圈、棒）：横截面上亮带偏向受光的一侧，背光的边最暗。"""
        n = (x1 - x0 + 1) if vertical else (y1 - y0 + 1)
        for i in range(n):
            t = (i + 0.5) / n                     # 0 是受光的一侧（左或上）
            nn = 2 * t - 1                        # 截面法线在横向上的分量
            nz = math.sqrt(max(0.0, 1 - nn * nn))
            v = nn * (LIGHT[0] if vertical else LIGHT[1]) + nz * LIGHT[2]
            d = round(amp * (v - 0.35) * 1.8)
            if vertical:
                self.rect(x0 + i, y0, x0 + i, y1, r, base + d)
            else:
                self.rect(x0, y0 + i, x1, y0 + i, r, base + d)

    def light_height(self, height: Callable[[int, int], float], cells: Iterable[tuple[int, int]],
                     r: Ramp, base: int, amp: float = 2.0):
        """按高度场打光：坡朝左上的亮，朝右下的暗。"""
        for x, y in cells:
            hx = height(x + 1, y) - height(x - 1, y)
            hy = height(x, y + 1) - height(x, y - 1)
            n = (-hx, -hy, 2.0)
            ln = math.sqrt(n[0] ** 2 + n[1] ** 2 + n[2] ** 2)
            v = (n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]) / ln
            self.set(x, y, r, base + round(amp * (v - LIGHT[2]) * 3))

    # ---- 细节 ----
    def rivet(self, x, y, r: Ramp, base: int):
        """铆钉：亮点在左上，影子在右下。"""
        self.set(x, y, r, base + 2, glint=True)
        self.set(x + 1, y + 1, r, base - 2, glint=True)

    def bolt(self, x, y, r: Ramp, base: int):
        """2×2 的螺栓头。"""
        self.set(x, y, r, base + 2)
        self.set(x + 1, y, r, base + 1)
        self.set(x, y + 1, r, base)
        self.set(x + 1, y + 1, r, base - 2)

    def vent(self, x0, y0, x1, y1, r: Ramp, base: int, horizontal: bool = True, pitch: int = 2):
        """百叶通风口：每条缝是深色的槽加一道受光的唇边。"""
        if horizontal:
            for y in range(y0, y1 + 1):
                k = (y - y0) % pitch
                lv = base - 3 if k == 0 else base + (1 if k == 1 else 0)
                self.hline(x0, x1, y, r, lv)
            self.vline(x1, y0, y1, r, base - 1)
        else:
            for x in range(x0, x1 + 1):
                k = (x - x0) % pitch
                lv = base - 3 if k == 0 else base + (1 if k == 1 else 0)
                self.vline(x, y0, y1, r, lv)
            self.hline(x0, x1, y1, r, base - 1)

    def grille(self, x0, y0, x1, y1, r: Ramp, base: int, pitch: int = 2):
        """方孔格栅：孔是暗的，孔的右下边受光。"""
        for x, y in self.cells(x0, y0, x1, y1):
            if (x - x0) % pitch == 0 and (y - y0) % pitch == 0:
                self.set(x, y, r, base - 3)
            else:
                self.set(x, y, r, base)

    def stripes(self, x0, y0, x1, y1, a: Ramp, la: int, b: Ramp, lb: int, period: int = 4, width: int = 2):
        """斜条纹（警示带），从左下往右上。"""
        for x, y in self.cells(x0, y0, x1, y1):
            if (x + y) % period < width:
                self.set(x, y, a, la)
            else:
                self.set(x, y, b, lb)

    def clusters(self, rng: random.Random, x0, y0, x1, y1, d: int, count: int, size: int = 2,
                 only: Optional[Ramp] = None):
        """成团的明暗变化（2–3 个像素一团），代替一个一个的随机噪点。"""
        shapes = {2: [(0, 0), (1, 0)], 3: [(0, 0), (1, 0), (0, 1)], 4: [(0, 0), (1, 0), (0, 1), (1, 1)]}
        for _ in range(count):
            x = rng.randint(x0, x1)
            y = rng.randint(y0, y1)
            shape = shapes[min(4, max(2, size + rng.choice((-1, 0, 0, 1))))]
            if rng.random() < 0.5:
                shape = [(dy, dx) for dx, dy in shape]
            for dx, dy in shape:
                if x0 <= x + dx <= x1 and y0 <= y + dy <= y1:
                    px = self.get(x + dx, y + dy)
                    if px and (only is None or px[0] is only):
                        self.shift(x + dx, y + dy, d)

    def edge_wear(self, rng: random.Random, x0, y0, x1, y1, density: float = 0.18):
        """凸起的边上掉漆、磨亮：上边、左边偶尔亮一级，下边偶尔暗一级。"""
        for x in range(x0 + 1, x1):
            if rng.random() < density:
                self.shift(x, y0, 1)
            if rng.random() < density * 0.7:
                self.shift(x, y1, -1)
        for y in range(y0 + 1, y1):
            if rng.random() < density:
                self.shift(x0, y, 1)

    def grime(self, rng: random.Random, x0, y0, x1, y1, density: float = 0.12):
        """积灰：越往下、越靠角越多，成团地暗一级。"""
        for x, y in self.cells(x0, y0, x1, y1):
            t = (y - y0) / max(1, y1 - y0)
            edge = min(x - x0, x1 - x) <= 1
            if rng.random() < density * (0.3 + t) * (1.6 if edge else 1.0):
                self.shift(x, y, -1)

    def dither(self, x0, y0, x1, y1, r: Ramp, la: int, lb: int, amount: float = 0.5):
        """有序抖动：只用在粗糙材质的过渡上。amount 是 lb 的占比。"""
        bayer = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
        for x, y in self.cells(x0, y0, x1, y1):
            self.set(x, y, r, lb if bayer[y % 4][x % 4] < amount * 16 else la)

    def draw(self, rows: list[str], ramps: dict, ox: int = 0, oy: int = 0):
        """手绘：网格里一个字符一个像素，字符在哪组里就用哪条色阶，在组里的位置就是级数。

        例如 ramps={"0123456": STEEL, "abcdefg": BRASS}："4" 是钢的第 4 级，"c" 是黄铜的第 2 级。
        "." 和空格不画，"_" 擦成透明。手绘的像素检查时不算"零碎像素"（抗锯齿、高光点都是故意的）。
        """
        lut = {}
        for chars, r in ramps.items():
            for i, ch in enumerate(chars):
                lut[ch] = (r, i)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch == "_":
                    self.clear(x + ox, y + oy)
                    continue
                r, lv = lut[ch]
                self.set(x + ox, y + oy, r, lv, glint=True)

    def ascii(self, rows: list[str], legend: dict, ox: int = 0, oy: int = 0):
        """按字符画：legend 里字符对应 (色阶, 级数)，或 None 表示透明；不在 legend 里的字符跳过。"""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch not in legend:
                    continue
                v = legend[ch]
                if v is None:
                    self.clear(x + ox, y + oy)
                else:
                    glint = len(v) > 2 and v[2]
                    self.set(x + ox, y + oy, v[0], v[1], glint)

    # ---- 物品 ----
    def outline(self, lit: int = 1, shadow: int = 0, outside: bool = False):
        """物品的描边：用相邻材质自己最深的颜色，不用纯黑。受光的一侧（左、上）浅一级。

        outside=False 时把最外一圈不透明像素压暗（不改轮廓大小）；True 时在外面加一圈。
        """
        src = [row[:] for row in self.p]

        def opaque(x, y):
            return 0 <= x < self.w and 0 <= y < self.h and src[y][x] is not None

        for y in range(self.h):
            for x in range(self.w):
                if outside:
                    if src[y][x] is not None:
                        continue
                    for dx, dy, side in ((1, 0, "lit"), (0, 1, "lit"), (-1, 0, "shadow"), (0, -1, "shadow")):
                        if opaque(x + dx, y + dy):
                            r = src[y + dy][x + dx][0]
                            self.p[y][x] = (r, lit if side == "lit" else shadow)
                            break
                else:
                    if src[y][x] is None:
                        continue
                    r = src[y][x][0]
                    if not opaque(x + 1, y) or not opaque(x, y + 1):
                        self.p[y][x] = (r, min(src[y][x][1], shadow))
                    elif not opaque(x - 1, y) or not opaque(x, y - 1):
                        self.p[y][x] = (r, min(src[y][x][1], lit))

    # ---- 输出 ----
    def rgba(self, x: int, y: int) -> tuple:
        px = self.p[y][x]
        if not px:
            return (0, 0, 0, 0)
        return tuple(px[0][px[1]]) + (self.alpha.get((x, y), 255),)

    def image(self) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h))
        img.putdata([self.rgba(x, y) for y in range(self.h) for x in range(self.w)])
        return img

    def emissive_image(self) -> Optional[Image.Image]:
        """只含发光像素的图（其余透明），用在模型的自发光覆盖层上；没有发光像素时返回 None。"""
        img = Image.new("RGBA", (self.w, self.h))
        data, any_lit = [], False
        for y in range(self.h):
            for x in range(self.w):
                px = self.p[y][x]
                if px and px[0].emissive and px[1] >= 2:
                    data.append(tuple(px[0][px[1]]) + (self.alpha.get((x, y), 255),))
                    any_lit = True
                else:
                    data.append((0, 0, 0, 0))
        img.putdata(data)
        return img if any_lit else None
