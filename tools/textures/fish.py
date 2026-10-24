"""钓鱼的贴图（ADR-0034 §一、ADR-0031）：96 种鱼，钓鱼器，六种饵，三种杂物，漂流瓶和密封补给舱，七道海鲜菜，
捕鱼笼，浮标和水下的鱼影。

鱼按参数画，和原版的鳕鱼一个朝向：尾巴在左下、吻在右上。一条从尾尖到吻端的中轴（鳗鱼这类可以弯成 S 形），
沿着中轴给体高的轮廓（最高点在哪、尾柄多细、头钝还是尖、背和腹哪边厚），再按种类挑尾鳍（叉形、新月、圆、
截形、歪形、尖尾、三叶）、背鳍和臀鳍（三角、帆、低长、带棘）的形状。上色是背深腹浅的反荫蔽，叠上花纹
（横带、纵纹、斑点、眼斑、网纹、菱鳞、发光器……），最后点眼睛、口、鳃盖、须、吻剑、钓竿这些细节。

配色照现实里那一类鱼，再照水域的特点：
- 淡水：橄榄、青灰、金黄，横带和红点，鲤、鲇有须。
- 暖水：颜色最艳（火焰橙、红腹、宝石蓝），骨舌鱼、巨骨舌鱼的鳞片大，肺鱼和刀鱼细长。
- 近海：背蓝腹银的反荫蔽，鲭的波纹、鲷的金线、石斑的红点，比目鱼两只眼睛在一侧。
- 深海：黑褐、深紫，眼睛大，腹面一排青色的发光器，鮟鱇头上挂着发光的饵球，鳗、剑鱼、皇带鱼形状夸张。
- 冰下：偏白偏透明（冰鱼没有血红蛋白、狮子鱼半透明），个头大的长得慢、体色灰。
- 酸水：嗜极生物，壳厚（骨板、鳞甲、盾头）、颜色暗，带一点硫磺黄和锈红。
- 暗河：没有眼睛（眼的位置只剩一点皮下的浅斑）、颜色粉白，须长。
- 奇异：晶体、镜面鳞、荧光、多出来的鳍和眼睛、双尾、螺旋。
"""
from __future__ import annotations

import math

from canvas import Canvas
from color import ramp
from food import BOWL, CRUST, FISH as FISH_FLESH, ROAST, SALMON, paint as food_paint, steam
from gear import limit_colors, lit_outline
from palette import (AMBER, BASALT, BERRY, BLUE, BLUE_GLOW, BONE, BRASS, BREAD, COPPER, CREAM, CYAN_GLOW, GLASS,
                     GRAPHITE, GREEN, GREEN_GLOW, ICE, IRON, LEAF, MAGENTA_GLOW, MOSS, ORANGE, ORANGE_GLOW, PINK,
                     RED, RUBBER, RUST, SAND, STEEL, STONE, TEAL, TITANIUM, VIOLET, VIOLET_GLOW, WHITE, WHITE_GLOW,
                     WOOD, YELLOW_GLOW, register)
from recipe import texture

# ---------------------------------------------------------------- 色阶

SILVER = register(ramp("fish_silver", 235, 0.018, n=7, lo=0.34, hi=0.96, shift=10))
OLIVE = register(ramp("fish_olive", 112, 0.070, n=7, lo=0.28, hi=0.88, shift=16))
GOLDEN = register(ramp("fish_golden", 84, 0.120, n=7, lo=0.32, hi=0.92, shift=14, peak=0.6))
CARMINE = register(ramp("fish_carmine", 25, 0.165, n=7, lo=0.30, hi=0.86, shift=12, peak=0.55))
SEABLUE = register(ramp("fish_seablue", 232, 0.095, n=7, lo=0.26, hi=0.86, shift=10, peak=0.5))
SLATE = register(ramp("fish_slate", 245, 0.035, n=7, lo=0.26, hi=0.88, shift=10))
UMBER = register(ramp("fish_umber", 62, 0.060, n=7, lo=0.24, hi=0.80, shift=14))
ABYSS = register(ramp("fish_abyss", 290, 0.035, n=7, lo=0.19, hi=0.60, shift=10))
CAVE_PINK = register(ramp("fish_cave_pink", 18, 0.045, n=7, lo=0.38, hi=0.97, shift=8))
FROST = register(ramp("fish_frost", 215, 0.030, n=7, lo=0.36, hi=0.97, shift=8))
SOOT = register(ramp("fish_soot", 105, 0.030, n=7, lo=0.20, hi=0.66, shift=12))
SULFUR = register(ramp("fish_sulfur", 98, 0.125, n=7, lo=0.34, hi=0.93, shift=10, peak=0.6))
EMBER = register(ramp("fish_ember", 42, 0.175, n=7, lo=0.32, hi=0.90, shift=12, peak=0.6))
PRISM = register(ramp("fish_prism", 205, 0.055, n=7, lo=0.36, hi=0.97, shift=8))
ORCHID = register(ramp("fish_orchid", 312, 0.105, n=7, lo=0.26, hi=0.86, shift=10))
CORAL = register(ramp("fish_coral", 20, 0.135, n=7, lo=0.34, hi=0.90, shift=10, peak=0.6))
ASH = register(ramp("fish_ash", 250, 0.012, n=7, lo=0.26, hi=0.84, shift=10))
JADE = register(ramp("fish_jade", 165, 0.085, n=7, lo=0.28, hi=0.88, shift=12))
COBALT = register(ramp("fish_cobalt", 262, 0.140, n=7, lo=0.24, hi=0.82, shift=8, peak=0.55))
MUD = register(ramp("fish_mud", 78, 0.045, n=7, lo=0.24, hi=0.76, shift=14))
PEARL = register(ramp("fish_pearl", 280, 0.020, n=7, lo=0.36, hi=0.97, shift=10))

TAIL_PT = (1.0, 14.6)
SNOUT_PT = (14.8, 1.4)


# ---------------------------------------------------------------- 几何

def fish_spec(**kw) -> dict:
    spec = dict(
        T=TAIL_PT, S=SNOUT_PT, bend=0.0, waves=1.0, phase=0.0,
        depth=2.8, back=1.0, belly=1.0, peak=0.55, a=0.8, b=0.55, ped=0.3,
        tl=0.24, tail="fork", th=3.3,
        dorsal=[(0.45, 0.68, 1.6, "tri")], anal=[(0.22, 0.34, 1.1, "tri")],
        pect=(0.7, 2.6), body=SILVER, belly_r=None, split=-0.15, fin=None, tail_r=None,
        pattern=(), eye="normal", eye_x=0.86, eye_v=0.3, mouth="small", gill=True, extras=(), sheen=False,
        rays=False,
    )
    spec.update(kw)
    if spec["fin"] is None:
        spec["fin"] = spec["body"]
    if spec["tail_r"] is None:
        spec["tail_r"] = spec["fin"]
    # 尾鳍张开的两叶要落在画布里：尾尖离左下角太近就顺着中轴往里挪
    if spec["tail"] not in ("none", "point"):
        tx, ty = spec["T"]
        reach = 0.72 * spec["th"]
        k = max(0.0, reach - 0.2 - tx, ty + reach - 15.8)
        spec["T"] = (tx + k, ty - k)
    return spec


class Geo:
    """中轴：从尾尖 T 到吻端 S，可以按正弦弯。点 → (s 沿轴 0–1, t 到轴的距离，往背那边为正)。"""

    N = 240

    def __init__(self, f: dict):
        tx, ty = f["T"]
        sx, sy = f["S"]
        dx, dy = sx - tx, sy - ty
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        nx, ny = uy, -ux
        self.pts = []
        for i in range(self.N + 1):
            s = i / self.N
            b = f["bend"] * math.sin(math.pi * f["waves"] * s + f["phase"])
            self.pts.append((tx + dx * s + nx * b, ty + dy * s + ny * b))
        self.tan, self.nor = [], []
        for i in range(self.N + 1):
            a = self.pts[max(0, i - 1)]
            b = self.pts[min(self.N, i + 1)]
            ex, ey = b[0] - a[0], b[1] - a[1]
            el = math.hypot(ex, ey)
            ex, ey = ex / el, ey / el
            self.tan.append((ex, ey))
            self.nor.append((ey, -ex))
        self.length = sum(math.dist(self.pts[i], self.pts[i + 1]) for i in range(self.N))

    def locate(self, px: float, py: float):
        best, bi = None, 0
        for i, (cx, cy) in enumerate(self.pts):
            d = (px - cx) ** 2 + (py - cy) ** 2
            if best is None or d < best:
                best, bi = d, i
        cx, cy = self.pts[bi]
        ex, ey = self.tan[bi]
        nx, ny = self.nor[bi]
        along = (px - cx) * ex + (py - cy) * ey
        if (bi == 0 and along < -0.35) or (bi == self.N and along > 0.35):
            return None
        return bi / self.N + along / self.length, (px - cx) * nx + (py - cy) * ny

    def point(self, s: float, t: float) -> tuple[float, float]:
        i = max(0, min(self.N, round(s * self.N)))
        cx, cy = self.pts[i]
        nx, ny = self.nor[i]
        return cx + nx * t, cy + ny * t

    def axis(self, s: float):
        i = max(0, min(self.N, round(s * self.N)))
        return self.tan[i], self.nor[i]


def half(f: dict, x: float) -> tuple[float, float]:
    """身体在 x（0 是尾柄、1 是吻端）处背侧、腹侧的半高（像素）。"""
    if x < 0 or x > 1:
        return 0.0, 0.0
    peak = f["peak"]
    if x <= peak:
        r = f["ped"] + (1 - f["ped"]) * math.sin(math.pi / 2 * x / peak) ** f["a"]
    else:
        r = math.cos(math.pi / 2 * (x - peak) / (1 - peak)) ** f["b"]
    d = min(4.4, f["depth"] * DEPTH_GAIN) * r
    return d * f["back"], d * f["belly"]


FIN_GAIN = 1.3
DEPTH_GAIN = 1.2


def fin_height(x: float, fin) -> float:
    x0, x1, h, kind = fin
    h *= FIN_GAIN
    if not x0 <= x <= x1:
        return 0.0
    w = (x - x0) / max(1e-6, x1 - x0)
    if kind == "tri":       # 前高后低
        return h * min(1.0, (1 - w) * 1.4) * min(1.0, 0.35 + w * 3)
    if kind == "back":      # 后高前低（鲤、骨舌鱼靠后的背鳍）
        return h * min(1.0, w * 1.4) * min(1.0, 0.35 + (1 - w) * 3)
    if kind == "sail":      # 高帆（茴鱼）
        return h * math.sin(math.pi * w) ** 0.45
    if kind == "low":       # 低而长（鳗、刀鱼、带鱼）
        return h * min(1.0, w * 6, (1 - w) * 6)
    if kind == "spiny":     # 一根根的棘
        return h * (0.55 + 0.45 * abs(math.sin(w * math.pi * 3.5))) * min(1.0, (1 - w) * 3 + 0.3)
    if kind == "round":
        return h * math.sqrt(max(0.0, math.sin(math.pi * w)))
    if kind == "nub":       # 脂鳍那样的小疙瘩
        return h * math.sin(math.pi * w)
    if kind == "shark":     # 鲨鱼的三角背鳍，后缘内凹
        return h * min(1.0, w * 2.2) * (1 - w) ** 0.35
    return 0.0


def tail_spread(f: dict, w: float, t: float) -> bool:
    """尾鳍：w 从尾柄（0）到尾尖（1）。"""
    th = f["th"]
    p = f["ped"] * min(4.4, f["depth"] * DEPTH_GAIN)
    at = abs(t)
    kind = f["tail"]
    if kind == "fork":
        spread = p + (th - p) * min(1.0, w / 0.7) ** 0.8
        notch = th * 0.5 * max(0.0, (w - 0.72) / 0.28)
        return notch <= at <= spread
    if kind == "lunate":
        spread = p + (th - p) * min(1.0, w / 0.6) ** 0.6
        notch = th * 0.62 * max(0.0, (w - 0.5) / 0.5) ** 0.9
        return notch <= at <= spread
    if kind == "round":
        if w < 0.5:
            spread = p + (th - p) * math.sin(w / 0.5 * math.pi / 2)
        else:
            spread = th * math.sqrt(max(0.0, 1 - ((w - 0.5) / 0.5) ** 2))
        return at <= spread
    if kind == "square":
        return at <= p + (th - p) * min(1.0, w * 1.7)
    if kind == "point":
        return at <= p * (1 - w) + 0.35
    if kind == "hetero":    # 鲨、鲟：上叶长，下叶短
        if t >= 0:
            spread = p + (th - p) * w ** 0.7
            return at <= spread and at >= th * 0.75 * max(0.0, (w - 0.45) / 0.55) ** 1.4
        if w > 0.55:
            return False
        return at <= p + th * 0.55 * math.sin(w / 0.55 * math.pi)
    if kind == "twin":      # 两条尾巴：深叉，两叶各自再分叉
        spread = p + (th - p) * w ** 0.6
        notch = th * 0.55 * max(0.0, (w - 0.1) / 0.9) ** 0.7
        inner = th * 0.7 * w if w > 0.55 else -1
        return notch <= at <= spread and not (inner > 0 and abs(at - inner) < 0.45)
    if kind == "diphy":     # 腔棘鱼：圆扇再从中间伸出一小叶
        if at <= 0.6:
            return True
        if w > 0.72:
            return False
        return at <= p + (th - p) * math.sin(min(1.0, w / 0.72) * math.pi) ** 0.6
    return False


class FishPainter:
    def __init__(self, f: dict, rng):
        self.f = f
        self.rng = rng
        self.geo = Geo(f)
        self.c = Canvas()
        self.cells: dict = {}
        tl = f["tl"]
        self.body_len = (1 - tl) * self.geo.length
        for y in range(16):
            for x in range(16):
                at = self.geo.locate(x + 0.5, y + 0.5)
                if at is None:
                    continue
                s, t = at
                part = None
                bx = (s - tl) / (1 - tl) if tl < 1 else 0
                v = 0.0
                if s >= tl and s <= 1.0:
                    hd, hv = half(f, bx)
                    if -hv <= t <= hd and (hd > 0.05 or hv > 0.05):
                        part = "body"
                        v = t / hd if t > 0 else (t / hv if hv > 0 else 0)
                    elif t > hd and t <= hd + max(fin_height(bx, fin) for fin in f["dorsal"] or [(0, 0, 0, "tri")]):
                        part = "dorsal"
                    elif t < -hv and -t <= hv + max(fin_height(bx, fin) for fin in f["anal"] or [(0, 0, 0, "tri")]):
                        part = "anal"
                elif s < tl and s >= 0:
                    w = (tl - s) / tl
                    if tail_spread(f, w, t):
                        part = "tail"
                if part:
                    self.cells[(x, y)] = (part, s, t, bx, v)

    # ---- 上色

    def paint_base(self):
        f = self.f
        for (x, y), (part, s, t, bx, v) in self.cells.items():
            if part == "body":
                # 背深腹浅，分三条明暗带就够了：分得太细，和花纹一交叉就成了棋盘格
                lv = 3 if v > 0.45 else 4 if v > -0.35 else 5
                if f["sheen"] and 0.0 < v <= 0.45 and 0.2 < bx < 0.85:
                    lv += 1
                r = f["belly_r"] if f["belly_r"] is not None and v < f["split"] else f["body"]
                self.c.set(x, y, r, round(lv))
            elif part == "tail":
                # 鳍是半透明的膜，比身体浅；根部厚一点，往外薄；鳍条隔一根暗一级
                w = (f["tl"] - s) / max(1e-6, f["tl"])
                lv = 4 if w < 0.55 else 3
                ray = f["rays"] and round(t * 1.1) % 2 == 1
                self.c.set(x, y, f["tail_r"], lv - (1 if ray else 0), glint=ray)
            else:
                hd, hv = half(f, bx)
                edge = abs(t) - (hd if part == "dorsal" else hv)
                lv = 4 if edge < 1.0 else 3
                ray = f["rays"] and round(s * self.geo.length) % 2 == 1
                self.c.set(x, y, f["fin"], lv - (1 if ray else 0), glint=ray)

    def body_cells(self, cond=None):
        for (x, y), (part, s, t, bx, v) in self.cells.items():
            if part == "body" and (cond is None or cond(bx, v)):
                yield x, y, bx, v

    def px(self, s: float, t: float) -> tuple[int, int]:
        x, y = self.geo.point(s, t)
        return int(math.floor(x)), int(math.floor(y))

    def at_body(self, bx: float, v: float) -> tuple[int, int]:
        tl = self.f["tl"]
        s = tl + bx * (1 - tl)
        hd, hv = half(self.f, bx)
        return self.px(s, v * (hd if v > 0 else hv))

    def set(self, x, y, r, lv, alpha=None):
        self.c.set(x, y, r, lv, glint=True, alpha=alpha)

    def shift(self, x, y, d):
        if self.c.get(x, y):
            self.c.shift(x, y, d)
            self.c.glints.add((x, y))

    # ---- 花纹

    def pattern(self):
        for p in self.f["pattern"]:
            getattr(self, "pat_" + p[0])(*p[1:])

    def pat_bars(self, n, x0, x1, r, lv, vmin=-0.5, width=0.45):
        """横带：和中轴垂直的 n 道。斜放的鱼身上垂直于中轴的是 x - y 相同的一串格子，按这个挑，带子是干净的斜线，
        不会糊成棋盘格。width 大于 0.5 的带子两格宽。"""
        k0 = self.diag(x0)
        k1 = self.diag(x1)
        if k1 == k0:
            return
        thick = 2 if width > 0.5 else 1
        picks = set()
        for i in range(n):
            k = round(k0 + (k1 - k0) * (i + 0.5) / n)
            picks.update(range(k, k + thick))
        for x, y, bx, v in self.body_cells(lambda bx, v: v > vmin):
            if x - y in picks:
                self.set(x, y, r, lv)

    def diag(self, bx: float) -> int:
        x, y = self.at_body(bx, 0.0)
        return x - y

    def pat_stripe(self, v0, w, r, lv, x0=0.0, x1=1.0):
        for x, y, bx, v in self.body_cells(lambda bx, v: x0 <= bx <= x1 and abs(v - v0) <= w):
            self.set(x, y, r, lv)

    def pat_belly(self, r, below=-0.25, d=0):
        for x, y, bx, v in self.body_cells(lambda bx, v: v < below):
            self.c.recolor(x, y, r, d)

    def pat_back(self, r, above=0.3, d=0):
        for x, y, bx, v in self.body_cells(lambda bx, v: v > above):
            self.c.recolor(x, y, r, d)

    def pat_spots(self, count, r, lv, vmin=-0.7, vmax=0.8, x0=0.1, x1=0.85, halo=None):
        cand = [(x, y) for x, y, bx, v in self.body_cells(lambda bx, v: x0 <= bx <= x1 and vmin <= v <= vmax)]
        self.rng.shuffle(cand)
        taken = []
        for x, y in cand:
            if len(taken) >= count:
                break
            if any(abs(x - a) <= 1 and abs(y - b) <= 1 for a, b in taken):
                continue
            taken.append((x, y))
            self.set(x, y, r, lv)
            if halo:
                hr, hl = halo
                for dx, dy in ((1, 0), (0, 1)):
                    q = (x + dx, y + dy)
                    if q in self.cells and self.cells[q][0] == "body" and q not in taken:
                        self.set(q[0], q[1], hr, hl)

    def pat_eyespot(self, bx, v, ring, ring_lv, core, core_lv):
        x, y = self.at_body(bx, v)
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if (x + dx, y + dy) in self.cells:
                self.set(x + dx, y + dy, ring, ring_lv)
        self.set(x, y, core, core_lv)

    def pat_mottle(self, count, r, lv, size=2):
        cand = [(x, y) for x, y, bx, v in self.body_cells(lambda bx, v: bx < 0.9)]
        self.rng.shuffle(cand)
        for x, y in cand[:count]:
            self.set(x, y, r, lv)
            for _ in range(size - 1):
                dx, dy = self.rng.choice(((1, 0), (0, 1), (-1, 0), (0, -1)))
                q = (x + dx, y + dy)
                if q in self.cells and self.cells[q][0] == "body":
                    self.set(q[0], q[1], r, lv)

    def pat_photophores(self, glow, v0=-0.7, step=2, x0=0.12, x1=0.8, lv=4):
        seen = set()
        i = 0
        for k in range(60):
            bx = x0 + (x1 - x0) * k / 59
            x, y = self.at_body(bx, v0)
            if (x, y) in seen or (x, y) not in self.cells:
                continue
            seen.add((x, y))
            if i % step == 0:
                self.set(x, y, glow, lv)
            i += 1

    def pat_diamonds(self, r, d=-1):
        """菱形的硬鳞：隔一格压暗一级，斜着排。"""
        for x, y, bx, v in self.body_cells(lambda bx, v: bx < 0.82 and abs(v) < 0.85):
            if (x + y) % 3 == 0 and (x - y) % 2 == 0:
                self.c.recolor(x, y, r, d)
                self.c.glints.add((x, y))

    def pat_scales(self, r, lv, x0=0.2, x1=0.75):
        """镜鳞鲤、骨舌鱼：沿着身体中线几片大鳞，亮的。"""
        for k in range(5):
            bx = x0 + (x1 - x0) * k / 4
            x, y = self.at_body(bx, 0.15 if k % 2 else -0.15)
            if (x, y) in self.cells:
                self.set(x, y, r, lv)

    def pat_lightning(self, glow, lv=4, x0=0.08, x1=0.85):
        """一道之字形的电纹。"""
        seen = set()
        for k in range(48):
            bx = x0 + (x1 - x0) * k / 47
            v = 0.45 * (1 if (k // 8) % 2 else -1) * (((k % 8) / 8) * 2 - 1)
            x, y = self.at_body(bx, v)
            if (x, y) in self.cells and (x, y) not in seen:
                seen.add((x, y))
                self.set(x, y, glow, lv)

    def pat_hexplates(self, r, d=-2):
        for x, y, bx, v in self.body_cells(lambda bx, v: bx < 0.9):
            if (x % 3 == 0 and y % 2 == 0) or (x % 3 == 1 and y % 2 == 1 and (x + y) % 4 == 0):
                self.c.recolor(x, y, r, d)
                self.c.glints.add((x, y))

    def pat_tailbands(self, r, lv, n=2):
        """刀鱼尾柄上的两道白环。"""
        for x, y, bx, v in self.body_cells(lambda bx, v: bx < 0.18):
            if int(bx / 0.18 * n * 2) % 2 == 1:
                self.set(x, y, r, lv)

    def pat_fins(self, r, d=0):
        for (x, y), (part, s, t, bx, v) in self.cells.items():
            if part in ("dorsal", "anal"):
                self.c.recolor(x, y, r, d)

    def pat_tail(self, r, d=0):
        for (x, y), (part, s, t, bx, v) in self.cells.items():
            if part == "tail":
                self.c.recolor(x, y, r, d)

    def pat_rear(self, r, x1=0.35, d=0):
        """身体后段换色（巨骨舌鱼尾部的红鳞）。"""
        for x, y, bx, v in self.body_cells(lambda bx, v: bx < x1):
            self.c.recolor(x, y, r, d)

    def pat_head(self, r, x0, d=0, seam=None):
        """头部换一种材质（盾头、骨甲），seam 给了就在交界画一道缝。"""
        for x, y, bx, v in self.body_cells(lambda bx, v: bx > x0):
            self.c.recolor(x, y, r, d)
            self.c.glints.add((x, y))
        if seam:
            for x, y, bx, v in self.body_cells(lambda bx, v: abs(bx - x0) * self.body_len < 0.6):
                self.set(x, y, seam[0], seam[1])

    def pat_translucent(self, alpha, spine=None):
        for x, y, bx, v in self.body_cells():
            self.c.opacity(x, y, alpha)
        for (x, y), cell in self.cells.items():
            if cell[0] != "body":
                self.c.opacity(x, y, max(90, alpha - 50))
        if spine:
            for x, y, bx, v in self.body_cells(lambda bx, v: abs(v) < 0.18 and bx < 0.8):
                self.set(x, y, spine[0], spine[1], alpha=min(255, alpha + 40))

    def pat_lateral(self, r, lv, v0=0.15, x0=0.05, x1=0.78):
        self.pat_stripe(v0, 0.12, r, lv, x0, x1)

    def pat_saddles(self, n, r, lv, x0=0.1, x1=0.85):
        self.pat_bars(n, x0, x1, r, lv, vmin=0.2, width=0.4)

    # ---- 头、眼、细节

    def head(self):
        f = self.f
        if f["gill"]:
            xg = f["eye_x"] - 0.11
            for x, y, bx, v in self.body_cells(lambda bx, v: abs(bx - xg) * self.body_len < 0.55 and abs(v) < 0.75):
                self.shift(x, y, -1)
        # 胸鳍：鳃后面一小片，朝后
        if f["pect"]:
            px0, size = f["pect"]
            for x, y, bx, v in self.body_cells():
                dx = (px0 - bx) * self.body_len
                if 0 <= dx <= size and abs(v + 0.35) <= 0.32 * (1 - dx / size) + 0.12:
                    self.set(x, y, f["fin"], 4 if dx < size / 2 else 3)
        mouth = f["mouth"]
        if mouth in ("small", "up"):
            v0 = 0.15 if mouth == "up" else -0.15
            for x, y, bx, v in self.body_cells(lambda bx, v: bx > 0.95 and abs(v - v0) < 0.35):
                self.shift(x, y, -2)
        elif mouth in ("big", "huge"):
            x0 = 0.8 if mouth == "big" else 0.62
            for x, y, bx, v in self.body_cells(lambda bx, v: bx > x0 and -0.32 < v < 0.02):
                self.set(x, y, GRAPHITE, 1)
        eye = f["eye"]
        x, y = self.at_body(f["eye_x"], f["eye_v"])
        if eye == "normal":
            self.set(x, y, GRAPHITE, 0)
            self.set(x, y - 1, WHITE, 5)
        elif eye == "small":
            self.set(x, y, GRAPHITE, 0)
        elif eye == "large":
            self.set(x, y, GRAPHITE, 0)
            self.set(x + 1, y, GRAPHITE, 1)
            self.set(x, y + 1, GRAPHITE, 1)
            self.set(x + 1, y + 1, GRAPHITE, 0)
            self.set(x, y - 1, WHITE, 5)
        elif eye == "blind":
            self.shift(x, y, 1)
        elif eye == "two":       # 比目鱼：两只眼睛在朝上的一侧
            self.set(x, y, GRAPHITE, 0)
            self.set(x, y - 1, WHITE, 5)
            x2, y2 = self.at_body(f["eye_x"] - 0.08, f["eye_v"] + 0.35)
            self.set(x2, y2, GRAPHITE, 0)
        elif eye == "three":
            self.set(x, y, GRAPHITE, 0)
            self.set(x, y - 1, WHITE, 5)
            for dv, dxx in ((0.55, -0.03), (-0.35, -0.04)):
                x2, y2 = self.at_body(f["eye_x"] + dxx, f["eye_v"] + dv)
                self.set(x2, y2, GRAPHITE, 0)

    def extras(self):
        for e in self.f["extras"]:
            getattr(self, "ex_" + e[0])(*e[1:])

    def line(self, x0, y0, x1, y1, r, lv, only_empty=False, alpha=None):
        n = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
        for i in range(n + 1):
            x = int(math.floor(x0 + (x1 - x0) * i / n))
            y = int(math.floor(y0 + (y1 - y0) * i / n))
            if only_empty and self.c.get(x, y):
                continue
            self.set(x, y, r, lv, alpha=alpha)

    def ex_barbels(self, n, length, r=None, lv=2):
        """须：从吻下面往后、往下拖。"""
        r = r or self.f["fin"]
        (ux, uy), (nx, ny) = self.geo.axis(0.97)
        for k in range(n):
            sx, sy = self.geo.point(0.96 - 0.03 * k, -0.6 * half(self.f, 0.9)[1])
            ex = sx - ux * length * (0.5 + 0.2 * k) - nx * length * (0.75 - 0.15 * k)
            ey = sy - uy * length * (0.5 + 0.2 * k) - ny * length * (0.75 - 0.15 * k)
            self.line(sx, sy, ex, ey, r, lv, only_empty=True)

    def ex_sword(self, length, r=None, lv=3):
        r = r or self.f["body"]
        (ux, uy), _ = self.geo.axis(1.0)
        sx, sy = self.f["S"]
        self.line(sx - ux * 0.5, sy - uy * 0.5, sx + ux * length, sy + uy * length, r, lv)

    def ex_lure(self, glow):
        """鮟鱇的钓竿：从额头往上、往前弯，顶上一颗发光的饵球。"""
        (ux, uy), (nx, ny) = self.geo.axis(0.9)
        hd = half(self.f, 0.85)[0]
        bx0, by0 = self.geo.point(self.f["tl"] + 0.85 * (1 - self.f["tl"]), hd)
        mx, my = bx0 + nx * 2.2 + ux * 0.6, by0 + ny * 2.2 + uy * 0.6
        tx, ty = mx + ux * 2.0 + nx * 0.3, my + uy * 2.0 + ny * 0.3
        self.line(bx0, by0, mx, my, self.f["fin"], 2, only_empty=True)
        self.line(mx, my, tx, ty, self.f["fin"], 2, only_empty=True)
        gx, gy = int(math.floor(tx)), int(math.floor(ty))
        self.set(gx, gy, glow, 5)
        for dx, dy in ((1, 0), (0, 1)):
            if not self.c.get(gx + dx, gy + dy):
                self.set(gx + dx, gy + dy, glow, 3)

    def ex_spines(self, every, r=None, lv=2):
        r = r or self.f["fin"]
        edge = []
        for x, y, bx, v in self.body_cells(lambda bx, v: abs(v) > 0.7):
            edge.append((bx, v, x, y))
        edge.sort()
        for i, (bx, v, x, y) in enumerate(edge):
            if i % every:
                continue
            ox, oy = self.at_body(bx, v * 1.8)
            if not self.c.get(ox, oy):
                self.set(ox, oy, r, lv)

    def ex_teeth(self, n=3):
        (ux, uy), (nx, ny) = self.geo.axis(0.95)
        for k in range(n):
            x, y = self.geo.point(0.95 - 0.035 * k, -0.15 * self.f["depth"] - 0.6 * (k % 2))
            self.set(int(math.floor(x + nx * -0.6)), int(math.floor(y + ny * -0.6)), BONE, 6)

    def ex_crystals(self, n, glow, r=PRISM):
        """背上长出来的几枚晶体：根部是浅色的晶面，尖上发光。"""
        for k in range(n):
            bx = 0.3 + 0.45 * k / max(1, n - 1)
            hd = half(self.f, bx)[0]
            s = self.f["tl"] + bx * (1 - self.f["tl"])
            for j, (lvl, rr) in enumerate(((5, r), (6, r), (4, glow))):
                x, y = self.px(s, hd - 0.2 + 0.9 * j)
                self.set(x, y, rr, lvl)

    def ex_scutes(self, r, lv, rows=(0.75, 0.0)):
        """鲟的骨板：沿背脊和体侧一排排的白色小骨板。"""
        for v0 in rows:
            seen = set()
            for k in range(40):
                bx = 0.08 + 0.75 * k / 39
                x, y = self.at_body(bx, v0)
                if (x, y) in self.cells and (x, y) not in seen and self.cells[(x, y)][0] == "body":
                    seen.add((x, y))
                    if len(seen) % 2:
                        self.set(x, y, r, lv)

    def ex_crest(self, r, n=3):
        """皇带鱼头顶的红色冠：几根长鳍条往后飘。"""
        hd = half(self.f, 0.9)[0]
        s = self.f["tl"] + 0.9 * (1 - self.f["tl"])
        bx0, by0 = self.geo.point(s, hd)
        (ux, uy), (nx, ny) = self.geo.axis(s)
        for k in range(n):
            ex = bx0 + nx * (2.2 + 0.5 * k) - ux * (1.0 + 1.3 * k)
            ey = by0 + ny * (2.2 + 0.5 * k) - uy * (1.0 + 1.3 * k)
            self.line(bx0, by0, ex, ey, r, 4 - (k % 2), only_empty=True)

    def ex_wings(self, r, length=7.5):
        """飞鱼的胸鳍：长长的一对翼，从鳃后往背后方张开，比身体还宽。"""
        s0 = self.f["tl"] + 0.72 * (1 - self.f["tl"])
        rx, ry = self.geo.point(s0, 0.2)
        (ux, uy), (nx, ny) = self.geo.axis(s0)
        wx, wy = -ux * 0.8 + nx * 0.6, -uy * 0.8 + ny * 0.6
        wl = math.hypot(wx, wy)
        wx, wy = wx / wl, wy / wl
        px_, py_ = -wy, wx
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - rx, y + 0.5 - ry
                a = dx * wx + dy * wy
                b = dx * px_ + dy * py_
                if 0 <= a <= length and abs(b) <= 1.9 * math.sin(math.pi * min(1.0, a / length) ** 0.7) + 0.3:
                    self.set(x, y, r, 4 if abs(b) < 0.8 else 3, alpha=None)
                    if int(a) % 2 == 0 and abs(b) > 0.6:
                        self.c.shift(x, y, -1)

    def ex_whip(self, r, glow):
        """囊咽鳗的鞭尾：细线往左下拖出去，尖上一点光。"""
        sx, sy = self.geo.point(0.0, 0)
        self.line(sx, sy, 0.4, 15.4, r, 2, only_empty=True)
        self.set(0, 15, glow, 4)

    def ex_filaments(self, r):
        """肺鱼的线状偶鳍：胸鳍、腹鳍细长一根。"""
        for bx0 in (0.7, 0.42):
            hv = half(self.f, bx0)[1]
            s = self.f["tl"] + bx0 * (1 - self.f["tl"])
            x0, y0 = self.geo.point(s, -hv)
            (ux, uy), (nx, ny) = self.geo.axis(s)
            self.line(x0, y0, x0 - ux * 3.5 - nx * 1.6, y0 - uy * 3.5 - ny * 1.6, r, 3, only_empty=True)

    def ex_lobes(self, r):
        """腔棘鱼的肉鳍：胸鳍、腹鳍、第二背鳍、臀鳍都是有柄的小叶。"""
        for bx0, side in ((0.68, -1), (0.45, -1), (0.32, 1), (0.3, -1)):
            hd, hv = half(self.f, bx0)
            s = self.f["tl"] + bx0 * (1 - self.f["tl"])
            for k, lv in ((0.4, 3), (1.3, 4), (2.1, 3)):
                x, y = self.px(s - 0.02 * k, side * ((hd if side > 0 else hv) + k))
                self.set(x, y, r, lv)

    def ex_leafstem(self, r):
        x, y = self.at_body(0.98, -0.6)
        self.set(x + 1, y + 1, r, 2)

    def ex_glint(self, r, lv, n=2):
        """镜面鳞上的反光点。"""
        cand = [(x, y) for x, y, bx, v in self.body_cells(lambda bx, v: 0.2 < bx < 0.8 and 0 < v < 0.6)]
        self.rng.shuffle(cand)
        for x, y in cand[:n]:
            self.set(x, y, r, lv)

    def ex_spiral(self, r, lv):
        """螺旋鳗身上一圈圈绕着的亮纹。"""
        for x, y, bx, v in self.body_cells():
            if ((bx * 9 + v * 0.9) % 1.0) < 0.28:
                self.set(x, y, r, lv)

    def ex_parasol(self, r, glow):
        """伞鳍鱼：背鳍撑开成一把伞，伞沿一圈发光点。"""
        hd = half(self.f, 0.55)[0]
        s = self.f["tl"] + 0.55 * (1 - self.f["tl"])
        cx, cy = self.geo.point(s, hd + 2.3)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                (ux, uy), (nx, ny) = self.geo.axis(s)
                if d <= 3.2 and dx * nx + dy * ny >= -0.6 and not self.c.get(x, y):
                    self.set(x, y, r, 4 if d < 2.2 else 3)
                    if d > 2.5 and (x + y) % 2 == 0:
                        self.set(x, y, glow, 4)
        x0, y0 = self.geo.point(s, hd)
        self.line(x0, y0, cx, cy, r, 2)

    def ex_sixfins(self, r):
        """六鳍鲂鮄：腹面三对扇形的鳍。"""
        for bx0 in (0.62, 0.48, 0.34):
            hv = half(self.f, bx0)[1]
            s = self.f["tl"] + bx0 * (1 - self.f["tl"])
            (ux, uy), (nx, ny) = self.geo.axis(s)
            x0, y0 = self.geo.point(s, -hv)
            for k in range(3):
                self.line(x0, y0, x0 - ux * (1.2 + 0.6 * k) - nx * (2.2 - 0.4 * k), y0 - uy * (1.2 + 0.6 * k) - ny * (2.2 - 0.4 * k),
                          r, 4 - k % 2, only_empty=True)

    def ex_gills(self, r, lv=1, n=4):
        """鲨鱼的鳃裂：头后面几道竖线。"""
        for k in range(n):
            bx0 = 0.74 - 0.035 * k
            for v in (-0.2, 0.15, 0.45):
                x, y = self.at_body(bx0, v)
                if (x, y) in self.cells:
                    self.set(x, y, r, lv)

    def ex_glowtip(self, glow):
        x, y = self.at_body(0.99, 0.0)
        self.set(x, y, glow, 4)

    # ---- 收尾

    def finish(self) -> Canvas:
        lit_outline(self.c)
        tidy(self.c)
        return limit_colors(self.c, 16)


def tidy(c: Canvas, passes: int = 3):
    """收拾逐格打光留下的孤零零的一格：八邻里没有同色的、又不是故意点上去的，就并进四邻里最多的那种颜色
    （同一条色阶优先）。"""
    for _ in range(passes):
        changed = False
        for y in range(c.h):
            for x in range(c.w):
                px = c.get(x, y)
                if not px or (x, y) in c.glints:
                    continue
                if any(c.get(x + dx, y + dy) == px for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy):
                    continue
                votes: dict = {}
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = c.get(x + dx, y + dy)
                    if q and (x + dx, y + dy) not in c.glints:
                        votes[q] = votes.get(q, 0) + (2 if q[0] is px[0] else 1)
                if votes:
                    best = max(votes, key=lambda q: (votes[q], -abs(q[1] - px[1])))
                    c.p[y][x] = best
                    changed = True
        if not changed:
            return


def draw_fish(f: dict, rng) -> Canvas:
    p = FishPainter(f, rng)
    p.paint_base()
    p.pattern()
    p.head()
    p.extras()
    return p.finish()


# ---------------------------------------------------------------- 体形的底子

def minnow(**kw):
    base = dict(depth=2.2, peak=0.5, a=0.8, b=0.55, ped=0.32, th=3.0, tl=0.24,
                dorsal=[(0.42, 0.56, 1.5, "tri")], anal=[(0.2, 0.32, 1.1, "tri")], sheen=True)
    base.update(kw)
    return fish_spec(**base)


def perch(**kw):
    base = dict(depth=3.0, peak=0.52, a=0.8, b=0.6, ped=0.3, th=3.0,
                dorsal=[(0.5, 0.78, 1.9, "spiny"), (0.25, 0.46, 1.5, "tri")],
                anal=[(0.2, 0.33, 1.3, "tri"), (0.5, 0.58, 1.1, "tri")], rays=True)
    base.update(kw)
    return fish_spec(**base)


def carp(**kw):
    base = dict(depth=3.3, peak=0.55, a=0.7, b=0.5, ped=0.32, th=3.4,
                dorsal=[(0.3, 0.68, 1.6, "back")], anal=[(0.18, 0.28, 1.3, "tri"), (0.46, 0.53, 1.0, "tri")],
                extras=[("barbels", 1, 1.6)])
    base.update(kw)
    return fish_spec(**base)


def trout(**kw):
    base = dict(depth=2.6, peak=0.5, a=0.8, b=0.6, ped=0.32, th=3.0, tail="fork",
                dorsal=[(0.45, 0.62, 1.6, "tri"), (0.12, 0.18, 0.9, "nub")],
                anal=[(0.18, 0.3, 1.2, "tri"), (0.42, 0.5, 0.9, "tri")])
    base.update(kw)
    return fish_spec(**base)


def killifish(**kw):
    base = dict(T=(2.0, 13.6), S=(14.4, 1.8), depth=2.4, peak=0.5, a=0.7, b=0.42, ped=0.4, th=2.6, tail="round", tl=0.2,
                dorsal=[(0.18, 0.38, 1.5, "round")], anal=[(0.15, 0.4, 1.5, "round")], eye_v=0.45)
    base.update(kw)
    return fish_spec(**base)


def loach(**kw):
    base = dict(depth=1.7, peak=0.55, a=0.55, b=0.5, ped=0.5, th=1.9, tail="round", tl=0.14, bend=0.6, waves=1.5,
                dorsal=[(0.38, 0.5, 1.1, "round")], anal=[(0.15, 0.25, 0.9, "round")], pect=(0.8, 1.6),
                extras=[("barbels", 3, 1.4)], eye="small", eye_x=0.9)
    base.update(kw)
    return fish_spec(**base)


def eel(**kw):
    base = dict(depth=1.6, peak=0.62, a=0.55, b=0.55, ped=0.08, tl=0.04, tail="point", th=1.0, bend=1.5, waves=2.0,
                dorsal=[(0.02, 0.62, 1.0, "low")], anal=[(0.02, 0.5, 0.9, "low")], pect=(0.78, 1.4), eye_x=0.92,
                eye="small")
    base.update(kw)
    return fish_spec(**base)


def catfish(**kw):
    base = dict(depth=2.6, belly=1.1, peak=0.66, a=0.7, b=0.35, ped=0.3, th=2.8, tail="fork",
                dorsal=[(0.6, 0.72, 1.8, "tri"), (0.14, 0.24, 0.8, "nub")], anal=[(0.15, 0.4, 1.2, "low")],
                extras=[("barbels", 3, 2.6)], eye="small", eye_x=0.88, eye_v=0.35, mouth="big")
    base.update(kw)
    return fish_spec(**base)


def sturgeon(**kw):
    base = dict(depth=2.3, peak=0.48, a=0.9, b=1.2, ped=0.25, tl=0.22, tail="hetero", th=3.4,
                dorsal=[(0.14, 0.24, 1.4, "tri")], anal=[(0.12, 0.2, 1.1, "tri")], pect=(0.74, 2.4),
                eye="small", eye_x=0.84, gill=True, mouth="none")
    base.update(kw)
    return fish_spec(**base)


def shark(**kw):
    base = dict(depth=2.9, peak=0.56, a=0.9, b=0.7, ped=0.22, tl=0.24, tail="hetero", th=3.6,
                dorsal=[(0.5, 0.64, 2.3, "shark"), (0.16, 0.22, 1.0, "tri")], anal=[(0.14, 0.2, 0.9, "tri"), (0.42, 0.5, 1.1, "tri")],
                pect=(0.68, 3.2), eye="small", eye_x=0.88, gill=False, mouth="big")
    base.update(kw)
    return fish_spec(**base)


def cod(**kw):
    base = dict(depth=2.8, peak=0.6, a=0.8, b=0.6, ped=0.3, tail="square", th=2.9,
                dorsal=[(0.62, 0.76, 1.4, "round"), (0.42, 0.58, 1.3, "round"), (0.2, 0.38, 1.2, "round")],
                anal=[(0.2, 0.36, 1.2, "round"), (0.4, 0.55, 1.1, "round")], extras=[("barbels", 1, 1.4)])
    base.update(kw)
    return fish_spec(**base)


def deep(**kw):
    """体侧扁、头大的深海小鱼。"""
    base = dict(T=(2.0, 13.6), S=(14.2, 2.0), depth=2.6, peak=0.6, a=0.7, b=0.45, ped=0.3, th=2.6, tail="fork",
                dorsal=[(0.45, 0.58, 1.2, "tri")], anal=[(0.2, 0.34, 1.0, "tri")], eye="large", eye_x=0.84, eye_v=0.2,
                body=ABYSS, fin=ABYSS)
    base.update(kw)
    return fish_spec(**base)


def disc(**kw):
    """高身的鲳、鲷、丽鱼、锯脂鲤。"""
    base = dict(T=(1.6, 14.2), S=(14.4, 1.8), depth=4.1, peak=0.5, a=0.65, b=0.55, ped=0.2, th=3.3, tl=0.2,
                dorsal=[(0.25, 0.7, 1.4, "back")], anal=[(0.15, 0.55, 1.3, "back")])
    base.update(kw)
    return fish_spec(**base)


def gar(**kw):
    base = dict(depth=1.9, peak=0.4, a=0.8, b=1.6, ped=0.45, tl=0.16, tail="round", th=2.4,
                dorsal=[(0.1, 0.22, 1.3, "round")], anal=[(0.08, 0.2, 1.2, "round")], pect=(0.66, 1.8), eye_x=0.78,
                eye="small", mouth="none", extras=[("teeth", 2)])
    base.update(kw)
    return fish_spec(**base)


def lungfish(**kw):
    base = dict(depth=2.0, peak=0.6, a=0.6, b=0.5, ped=0.12, tl=0.04, tail="point", th=1.0, bend=0.9, waves=1.3,
                dorsal=[(0.02, 0.45, 1.0, "low")], anal=[(0.02, 0.35, 0.9, "low")], pect=None, eye="small", eye_x=0.92)
    base.update(kw)
    return fish_spec(**base)


# ---------------------------------------------------------------- 96 种鱼

FISH = {
    # ---- 淡水
    "creek_dace": minnow(body=OLIVE, belly_r=SILVER, split=0.05, fin=BLUE, pattern=[("lateral", GRAPHITE, 2)]),
    "banded_perch": perch(body=OLIVE, belly_r=GOLDEN, fin=CARMINE, pattern=[("bars", 3, 0.15, 0.8, OLIVE, 1, -0.45, 0.6)]),
    "silver_crucian": carp(T=(1.6, 14.0), S=(14.4, 1.8), depth=3.6, body=SILVER, fin=SLATE, sheen=True, extras=[]),
    "reed_gudgeon": minnow(depth=1.9, a=0.7, b=0.45, body=SAND, belly_r=CREAM, fin=SAND,
                           pattern=[("spots", 6, UMBER, 1, -0.2, 0.3)], extras=[("barbels", 1, 1.2)], eye_v=0.45),
    "mud_loach": loach(body=MUD, fin=MUD, pattern=[("spots", 8, UMBER, 0, -0.6, 0.9, 0.05, 0.9)]),
    "brook_trout": trout(body=OLIVE, belly_r=CARMINE, fin=CARMINE, split=-0.3,
                         pattern=[("mottle", 5, MOSS, 1), ("spots", 6, CARMINE, 4, -0.3, 0.4, 0.1, 0.8, (BLUE, 4))]),
    "whiskered_catfish": catfish(body=MUD, belly_r=CREAM, fin=MUD, extras=[("barbels", 4, 3.4)]),
    "goldback_carp": carp(body=GOLDEN, fin=COPPER, sheen=True, pattern=[("back", BRASS, 0.4, -1)]),
    "striped_pike": fish_spec(depth=2.0, peak=0.42, a=0.9, b=1.0, ped=0.38, tl=0.18, th=2.8,
                              dorsal=[(0.12, 0.24, 1.5, "back")], anal=[(0.1, 0.22, 1.4, "back")], pect=(0.7, 2.0),
                              body=OLIVE, belly_r=CREAM, fin=OLIVE, eye_x=0.8, mouth="big",
                              pattern=[("bars", 5, 0.05, 0.75, MOSS, 1, -0.5, 0.35)]),
    "thunder_char": trout(body=SLATE, belly_r=EMBER, fin=EMBER, split=-0.25,
                          pattern=[("spots", 7, CREAM, 6, -0.2, 0.7), ("lightning", YELLOW_GLOW, 4, 0.1, 0.6)]),
    "dusky_eel": eel(body=UMBER, belly_r=GOLDEN, fin=UMBER, split=-0.4),
    "river_sturgeon": sturgeon(body=ASH, belly_r=CREAM, fin=ASH, extras=[("scutes", BONE, 5), ("barbels", 2, 1.4)]),

    # ---- 暖水
    "ember_tetra": minnow(T=(2.2, 13.4), S=(14.2, 2.0), depth=2.6, body=EMBER, fin=CARMINE,
                          pattern=[("stripe", 0.15, 0.2, ORANGE_GLOW, 3, 0.2, 0.8)],
                          dorsal=[(0.42, 0.56, 1.6, "tri"), (0.12, 0.18, 0.8, "nub")]),
    "eyespot_cichlid": disc(depth=3.3, body=OLIVE, belly_r=GOLDEN, fin=JADE, dorsal=[(0.2, 0.75, 1.5, "spiny")],
                            pattern=[("bars", 3, 0.2, 0.75, OLIVE, 2, -0.3, 0.6), ("eyespot", 0.1, 0.4, GOLDEN, 6, GRAPHITE, 0)]),
    "sawtooth_pacu": disc(body=SLATE, belly_r=CARMINE, fin=SLATE, split=-0.1, sheen=True, mouth="big",
                          dorsal=[(0.45, 0.62, 1.6, "tri"), (0.14, 0.2, 0.9, "nub")], anal=[(0.15, 0.45, 1.3, "low")],
                          extras=[("teeth", 2)], pattern=[("spots", 5, SILVER, 6, 0.0, 0.8)]),
    "spring_pupfish": killifish(body=COBALT, fin=GRAPHITE, pattern=[("bars", 3, 0.2, 0.8, SLATE, 5, -0.6)],
                                extras=[]),
    "leaf_fish": fish_spec(T=(1.6, 14.0), S=(14.6, 1.4), depth=3.2, peak=0.45, a=0.6, b=1.1, ped=0.35, tail="round", th=2.4,
                           tl=0.14, dorsal=[(0.18, 0.62, 1.2, "low")], anal=[(0.14, 0.5, 1.1, "low")], body=UMBER, fin=UMBER,
                           eye_x=0.8, pattern=[("mottle", 6, SAND, 5), ("stripe", 0.0, 0.1, UMBER, 1, 0.05, 0.9)],
                           extras=[("leafstem", UMBER)]),
    "silver_bonytongue": fish_spec(depth=2.3, peak=0.55, a=0.8, b=0.7, ped=0.3, tl=0.16, tail="round", th=2.4,
                                   dorsal=[(0.05, 0.3, 1.2, "back")], anal=[(0.04, 0.4, 1.3, "back")], body=SILVER, fin=SLATE,
                                   mouth="up", eye_v=0.4, sheen=True, pattern=[("scales", PEARL, 6, 0.2, 0.8)],
                                   extras=[("barbels", 2, 1.4, SILVER, 4)]),
    "ghost_knifefish": fish_spec(depth=1.8, peak=0.62, a=0.7, b=0.6, ped=0.18, tl=0.06, tail="point", th=1.0,
                                 dorsal=[], anal=[(0.08, 0.85, 1.6, "low")], body=GRAPHITE, fin=GRAPHITE, eye="small",
                                 pattern=[("tailbands", WHITE, 6), ("stripe", 0.85, 0.15, WHITE, 5, 0.75, 0.98)]),
    "spotted_lungfish": lungfish(body=OLIVE, belly_r=SAND, fin=OLIVE, pattern=[("spots", 9, UMBER, 0, -0.5, 0.9, 0.05, 0.9)],
                                 extras=[("filaments", OLIVE)]),
    "spore_goby": fish_spec(T=(2.0, 13.8), S=(14.2, 2.0), depth=2.0, peak=0.6, a=0.6, b=0.4, ped=0.45, tail="round", th=2.2,
                            dorsal=[(0.5, 0.62, 1.4, "round"), (0.2, 0.4, 1.2, "round")], anal=[(0.18, 0.4, 1.0, "round")],
                            body=SAND, fin=SAND, eye_v=0.6, pattern=[("spots", 7, GREEN_GLOW, 3, -0.5, 0.8), ("mottle", 3, UMBER, 2)]),
    "thunder_eel": eel(depth=1.9, body=GRAPHITE, belly_r=AMBER, fin=GRAPHITE, split=-0.45, dorsal=[], anal=[(0.02, 0.7, 1.1, "low")],
                       pattern=[("lightning", YELLOW_GLOW, 4, 0.05, 0.85)]),
    "redtail_catfish": catfish(depth=2.9, body=SLATE, belly_r=CREAM, fin=SLATE, split=-0.35, tail_r=CARMINE,
                               pattern=[("stripe", -0.3, 0.12, CREAM, 6, 0.1, 0.85), ("spots", 6, GRAPHITE, 1, 0.2, 0.9)]),
    "giant_arapaima": fish_spec(depth=2.5, peak=0.55, a=0.8, b=0.8, ped=0.35, tl=0.15, tail="round", th=2.6,
                                dorsal=[(0.04, 0.22, 1.3, "back")], anal=[(0.03, 0.2, 1.3, "back")], body=ASH, fin=CARMINE,
                                mouth="up", pattern=[("rear", CARMINE, 0.38), ("scales", CORAL, 5, 0.1, 0.4),
                                                     ("tail", CARMINE, 0)]),

    # ---- 近海
    "banded_sprat": minnow(depth=2.1, body=SEABLUE, belly_r=SILVER, split=0.1, fin=SLATE,
                           pattern=[("stripe", 0.1, 0.15, PEARL, 6, 0.05, 0.85)]),
    "tidepool_wrasse": fish_spec(depth=2.4, peak=0.55, a=0.7, b=0.6, ped=0.42, tail="square", th=2.5,
                                 dorsal=[(0.2, 0.75, 1.1, "low")], anal=[(0.15, 0.45, 1.0, "low")], body=JADE, fin=CORAL,
                                 mouth="small", pattern=[("stripe", 0.35, 0.14, CORAL, 4, 0.05, 0.9),
                                                         ("stripe", -0.25, 0.12, CORAL, 4, 0.05, 0.8)]),
    "striped_mackerel": fish_spec(depth=2.4, peak=0.5, a=0.9, b=0.8, ped=0.18, tail="lunate", th=3.3,
                                  dorsal=[(0.6, 0.72, 1.4, "tri"), (0.3, 0.42, 1.0, "tri")], anal=[(0.28, 0.4, 0.9, "tri")],
                                  body=SEABLUE, belly_r=SILVER, split=0.05, fin=SLATE, sheen=True,
                                  pattern=[("bars", 7, 0.08, 0.8, GRAPHITE, 1, 0.25, 0.3)]),
    "kelp_rockfish": perch(depth=3.0, body=UMBER, belly_r=CORAL, fin=UMBER, split=-0.3, eye_v=0.45,
                           pattern=[("mottle", 6, MOSS, 2), ("mottle", 4, CORAL, 4)], tail="square"),
    "spotted_flounder": fish_spec(T=(1.2, 14.2), S=(14.6, 1.6), depth=4.2, peak=0.5, a=0.6, b=0.7, ped=0.22, tl=0.15,
                                  tail="round", th=2.4, dorsal=[(0.05, 0.92, 1.0, "low")], anal=[(0.05, 0.75, 1.0, "low")],
                                  body=SAND, fin=SAND, eye="two", eye_x=0.84, eye_v=0.2, pect=None,
                                  pattern=[("spots", 8, UMBER, 1, -0.8, 0.8, 0.1, 0.85), ("mottle", 4, CREAM, 5)]),
    "goldline_bream": disc(depth=3.6, body=SILVER, fin=SLATE, sheen=True,
                           pattern=[("stripe", 0.25, 0.1, GOLDEN, 5, 0.05, 0.9), ("stripe", -0.15, 0.1, GOLDEN, 5, 0.05, 0.85)],
                           dorsal=[(0.3, 0.75, 1.5, "spiny")]),
    "starry_conger": eel(depth=1.7, body=SLATE, belly_r=PEARL, fin=GRAPHITE, split=-0.35,
                         pattern=[("spots", 9, PEARL, 6, -0.2, 0.8, 0.05, 0.9)]),
    "spiny_puffer": fish_spec(T=(3.0, 12.8), S=(13.4, 2.6), depth=3.9, peak=0.5, a=0.5, b=0.4, ped=0.25, tl=0.16,
                              tail="round", th=2.2, dorsal=[(0.18, 0.28, 1.2, "round")], anal=[(0.16, 0.26, 1.1, "round")],
                              body=SAND, belly_r=CREAM, fin=SAND, eye_v=0.35, eye_x=0.78,
                              pattern=[("spots", 8, UMBER, 1, -0.1, 0.9)], extras=[("spines", 3, UMBER, 1)]),
    "moonscale_pomfret": disc(depth=4.3, body=PEARL, fin=SLATE, sheen=True, tail="lunate", th=3.6,
                              dorsal=[(0.2, 0.6, 2.0, "tri")], anal=[(0.15, 0.55, 1.9, "tri")], pect=(0.66, 3.0),
                              extras=[("glint", WHITE, 6, 3)]),
    "gale_jack": fish_spec(depth=2.9, peak=0.55, a=0.9, b=0.6, ped=0.15, tail="lunate", th=3.6,
                           dorsal=[(0.52, 0.66, 1.6, "tri"), (0.15, 0.48, 1.0, "low")], anal=[(0.12, 0.4, 1.0, "low")],
                           body=SEABLUE, belly_r=SILVER, split=0.0, fin=SLATE, sheen=True,
                           pattern=[("stripe", -0.05, 0.1, YELLOW_GLOW, 3, 0.05, 0.7)]),
    "coral_grouper": perch(depth=3.2, b=0.5, body=CORAL, fin=CORAL, tail="square", mouth="big",
                           pattern=[("spots", 10, CYAN_GLOW, 4, -0.7, 0.9, 0.05, 0.85)]),
    "silver_tarpon": fish_spec(depth=2.8, peak=0.52, a=0.9, b=0.6, ped=0.25, tail="fork", th=3.7,
                               dorsal=[(0.42, 0.55, 2.4, "tri")], anal=[(0.2, 0.32, 1.4, "tri")], pect=(0.72, 2.6),
                               body=SILVER, fin=SLATE, sheen=True, mouth="up", eye_v=0.35,
                               pattern=[("scales", WHITE, 6, 0.15, 0.75), ("back", SLATE, 0.6, 0)]),

    # ---- 深海
    "starspot_lanternfish": deep(depth=2.2, b=0.5, body=ABYSS, pattern=[("photophores", CYAN_GLOW, -0.65, 2),
                                                                         ("photophores", CYAN_GLOW, -0.1, 3, 0.25, 0.7)]),
    "silver_hatchetfish": deep(T=(2.6, 13.0), S=(13.6, 2.4), depth=2.2, back=0.6, belly=2.0, peak=0.55, b=0.6, body=SILVER,
                               fin=SLATE, sheen=True, eye_v=0.4, pattern=[("photophores", CYAN_GLOW, -0.85, 1, 0.15, 0.8)]),
    "rattail_grenadier": fish_spec(depth=2.6, peak=0.78, a=1.8, b=0.6, ped=0.05, tl=0.02, tail="point", th=0.8,
                                   dorsal=[(0.62, 0.74, 1.8, "tri"), (0.02, 0.55, 0.7, "low")], anal=[(0.02, 0.6, 0.8, "low")],
                                   body=ASH, fin=ASH, eye="large", eye_x=0.88, eye_v=0.25, mouth="small",
                                   extras=[("barbels", 1, 1.2)]),
    "deepwater_cod": cod(body=UMBER, belly_r=SAND, fin=UMBER, pattern=[("lateral", SAND, 5)], eye="large"),
    "bluewing_flyingfish": fish_spec(depth=1.9, peak=0.55, a=0.8, b=0.6, ped=0.25, tail="fork", th=3.2,
                                     dorsal=[(0.2, 0.3, 1.0, "tri")], anal=[(0.18, 0.28, 0.9, "tri")], pect=None,
                                     body=SEABLUE, belly_r=SILVER, split=0.0, fin=COBALT, sheen=True,
                                     extras=[("wings", COBALT)]),
    "fangtooth_viperfish": deep(depth=2.0, a=0.6, b=0.7, body=ABYSS, mouth="big", dorsal=[(0.72, 0.8, 2.4, "nub")],
                                pattern=[("photophores", BLUE_GLOW, -0.7, 2)], extras=[("teeth", 4)]),
    "lantern_angler": fish_spec(T=(2.4, 13.4), S=(13.8, 2.2), depth=3.9, peak=0.62, a=0.5, b=0.35, ped=0.22, tl=0.16,
                                tail="round", th=2.2, dorsal=[(0.2, 0.3, 1.0, "round")], anal=[(0.18, 0.28, 0.9, "round")],
                                body=ABYSS, fin=ABYSS, mouth="huge", eye="small", eye_v=0.55, pect=(0.5, 1.8),
                                extras=[("lure", CYAN_GLOW), ("teeth", 3)]),
    "pelican_gulper": fish_spec(T=(5.0, 11.0), S=(14.6, 1.4), depth=3.4, peak=0.8, a=1.6, b=0.4, ped=0.05, tl=0.0,
                                tail="none", dorsal=[], anal=[], body=ABYSS, fin=ABYSS, mouth="huge", eye="small",
                                eye_x=0.95, eye_v=0.5, pect=None, extras=[("whip", ABYSS, MAGENTA_GLOW)]),
    "broadbill_swordfish": fish_spec(T=(1.0, 14.6), S=(11.6, 4.4), depth=2.6, peak=0.55, a=0.9, b=0.8, ped=0.15,
                                     tail="lunate", th=3.6, dorsal=[(0.62, 0.78, 2.6, "shark")], anal=[(0.2, 0.3, 1.0, "tri")],
                                     body=COBALT, belly_r=SILVER, split=-0.1, fin=GRAPHITE, eye="large", eye_x=0.9,
                                     mouth="none", extras=[("sword", 5.0, GRAPHITE, 3)]),
    "king_oarfish": fish_spec(depth=1.3, peak=0.6, a=0.5, b=0.7, ped=0.12, tl=0.03, tail="point", th=0.8, bend=1.4,
                              waves=2.2, dorsal=[(0.02, 0.92, 1.1, "low")], anal=[], pect=None, body=SILVER, fin=CARMINE,
                              sheen=True, pattern=[("spots", 6, SLATE, 2, -0.3, 0.6, 0.1, 0.8)],
                              extras=[("crest", CARMINE, 3)]),
    "abyssal_coelacanth": fish_spec(depth=3.0, peak=0.5, a=0.8, b=0.55, ped=0.35, tl=0.2, tail="diphy", th=2.8,
                                    dorsal=[(0.55, 0.66, 1.6, "spiny")], anal=[], body=COBALT, fin=COBALT,
                                    pattern=[("spots", 8, PEARL, 6, -0.6, 0.8, 0.1, 0.85)], extras=[("lobes", COBALT)]),
    "megamouth": shark(depth=3.2, b=0.4, body=UMBER, belly_r=SAND, fin=UMBER, split=-0.4, mouth="huge",
                       extras=[("gills", UMBER, 1, 3)], pattern=[("stripe", -0.15, 0.1, PEARL, 6, 0.8, 0.98)]),

    # ---- 冰下
    "ice_smelt": minnow(depth=1.9, a=0.9, b=0.8, body=FROST, fin=FROST,
                        pattern=[("stripe", 0.1, 0.15, SILVER, 6, 0.05, 0.85), ("translucent", 200)]),
    "polar_cod": cod(depth=2.4, body=UMBER, belly_r=FROST, fin=UMBER, split=-0.1, extras=[("barbels", 1, 1.0)],
                     pattern=[("spots", 6, MUD, 1, 0.2, 0.9)]),
    "whiteblood_icefish": fish_spec(depth=2.2, peak=0.62, a=0.7, b=1.1, ped=0.3, tail="square", th=2.5,
                                    dorsal=[(0.6, 0.7, 1.6, "spiny"), (0.2, 0.52, 1.1, "low")], anal=[(0.15, 0.5, 1.0, "low")],
                                    body=FROST, fin=FROST, eye="large", eye_x=0.8, eye_v=0.4, mouth="big",
                                    pattern=[("translucent", 185, (CAVE_PINK, 5)), ("bars", 4, 0.1, 0.6, SLATE, 4, 0.0)]),
    "arctic_char": trout(body=SLATE, belly_r=EMBER, fin=EMBER, split=-0.2,
                         pattern=[("spots", 6, CAVE_PINK, 5, -0.1, 0.8)]),
    "frost_sculpin": fish_spec(T=(1.8, 13.8), S=(14.4, 1.8), depth=2.4, peak=0.72, a=0.6, b=0.35, ped=0.3, tail="round", th=2.4,
                               dorsal=[(0.55, 0.7, 1.6, "spiny"), (0.2, 0.5, 1.3, "low")], anal=[(0.15, 0.45, 1.0, "low")],
                               pect=(0.68, 3.0), body=FROST, fin=SLATE, eye_v=0.55, eye="normal", mouth="big",
                               pattern=[("mottle", 6, SLATE, 2), ("saddles", 3, SLATE, 1)]),
    "ice_toothfish": cod(depth=2.9, tail="square", body=ASH, belly_r=FROST, fin=ASH, split=-0.3, mouth="big",
                         extras=[("teeth", 2)], eye="large"),
    "burbot": cod(depth=2.0, a=0.6, b=0.5, ped=0.3, tail="round", th=2.2, dorsal=[(0.55, 0.65, 1.0, "round"), (0.04, 0.5, 1.0, "low")],
                  anal=[(0.04, 0.42, 1.0, "low")], bend=0.7, waves=1.2, body=MUD, fin=MUD,
                  pattern=[("mottle", 7, UMBER, 1)], extras=[("barbels", 1, 1.8)]),
    "glacier_grayling": trout(body=SLATE, belly_r=SILVER, fin=ORCHID, dorsal=[(0.38, 0.72, 3.0, "sail"), (0.12, 0.18, 0.8, "nub")],
                              pattern=[("spots", 5, GRAPHITE, 1, 0.0, 0.7, 0.4, 0.85)], extras=[], rays=True),
    "glass_snailfish": fish_spec(T=(1.6, 14.0), S=(14.0, 2.2), depth=3.0, peak=0.75, a=1.2, b=0.4, ped=0.08, tl=0.03,
                                 tail="point", th=0.8, dorsal=[(0.02, 0.6, 0.9, "low")], anal=[(0.02, 0.55, 0.9, "low")],
                                 body=CAVE_PINK, fin=CAVE_PINK, eye="small", eye_v=0.35,
                                 pattern=[("translucent", 165, (BERRY, 4))]),
    "blizzard_trout": trout(depth=2.8, body=ASH, belly_r=FROST, fin=ASH, split=-0.25, tail="fork",
                            pattern=[("spots", 10, WHITE, 6, -0.3, 0.9, 0.05, 0.9)]),
    "spotted_wolffish": fish_spec(depth=2.5, peak=0.7, a=0.6, b=0.35, ped=0.18, tl=0.1, tail="round", th=1.8,
                                  dorsal=[(0.05, 0.75, 1.1, "low")], anal=[(0.05, 0.45, 1.0, "low")], pect=(0.66, 2.4),
                                  body=SLATE, fin=SLATE, mouth="big", eye_v=0.5, bend=0.5, waves=1.0,
                                  pattern=[("spots", 9, GRAPHITE, 1, -0.4, 0.9, 0.05, 0.85)], extras=[("teeth", 3)]),
    "sleeper_shark": shark(depth=2.8, body=ASH, belly_r=SLATE, fin=ASH, dorsal=[(0.42, 0.5, 1.2, "shark"), (0.24, 0.3, 1.0, "shark")],
                           anal=[], th=3.0, pattern=[("mottle", 4, UMBER, 2)], extras=[("gills", ASH, 1, 3)]),

    # ---- 酸水
    "sulfur_killifish": killifish(body=SULFUR, fin=AMBER, pattern=[("bars", 3, 0.15, 0.75, AMBER, 2, -0.3, 0.6)]),
    "blackshell_catfish": catfish(depth=2.7, body=SOOT, fin=SOOT, extras=[("barbels", 2, 1.8)],
                                  pattern=[("head", SOOT, 0.66, -1, (SOOT, 0)), ("stripe", 0.1, 0.12, SOOT, 1, 0.05, 0.65)]),
    "marsh_loach": loach(body=SOOT, fin=SULFUR, pattern=[("spots", 6, SULFUR, 4, -0.5, 0.8, 0.05, 0.9)]),
    "rustscale_barb": minnow(depth=2.5, body=RUST, belly_r=SAND, fin=RUST, sheen=False,
                             pattern=[("diamonds", RUST, -1), ("spots", 2, GRAPHITE, 0, -0.1, 0.3, 0.3, 0.6)],
                             extras=[("barbels", 1, 1.2)]),
    "plated_shieldhead": fish_spec(depth=2.2, peak=0.75, a=1.0, b=0.3, ped=0.3, tl=0.22, tail="hetero", th=2.6,
                                   dorsal=[(0.3, 0.38, 1.2, "tri")], anal=[], pect=None, body=SOOT, fin=SOOT,
                                   eye="small", eye_v=0.75, gill=False, mouth="none",
                                   pattern=[("head", BONE, 0.58, -2, (BONE, 1)), ("diamonds", SOOT, -1)]),
    "sulfide_lungfish": lungfish(body=SOOT, belly_r=SULFUR, fin=SOOT, split=-0.5, extras=[("filaments", SULFUR)]),
    "charcoal_eel": eel(body=SOOT, fin=SOOT, pattern=[("mottle", 5, GRAPHITE, 1), ("spots", 4, EMBER, 3, -0.3, 0.5)]),
    "acidophile_cichlid": disc(depth=3.1, body=MUD, belly_r=SULFUR, fin=SOOT, split=-0.2,
                               dorsal=[(0.2, 0.75, 1.5, "spiny")], pattern=[("bars", 3, 0.15, 0.7, SOOT, 1, -0.2, 0.6)]),
    "thickplate_sturgeon": sturgeon(depth=2.5, body=SOOT, belly_r=MUD, fin=SOOT,
                                    extras=[("scutes", BONE, 3, (0.8, 0.25, -0.4)), ("barbels", 2, 1.2)]),
    "stormlit_boxfish": fish_spec(T=(3.0, 12.6), S=(13.2, 2.8), depth=3.4, peak=0.5, a=0.25, b=0.25, ped=0.25, tl=0.18,
                                  tail="round", th=2.2, dorsal=[(0.1, 0.18, 1.0, "round")], anal=[(0.1, 0.18, 0.9, "round")],
                                  body=SULFUR, fin=AMBER, eye_x=0.8, eye_v=0.45, gill=False,
                                  pattern=[("hexplates", SULFUR, -3), ("spots", 4, BLUE_GLOW, 4, -0.6, 0.6)]),
    "armored_gar": gar(body=MUD, belly_r=SAND, fin=MUD, split=-0.3, pattern=[("diamonds", MUD, -2), ("spots", 4, SOOT, 0, -0.1, 0.8)]),
    "great_placoderm": shark(depth=3.2, b=0.45, peak=0.6, body=SOOT, fin=SOOT, belly_r=MUD, split=-0.5, mouth="big",
                             dorsal=[(0.42, 0.52, 1.8, "shark")], anal=[], gill=False,
                             pattern=[("head", BONE, 0.66, -2, (BONE, 1))], extras=[("teeth", 2)]),

    # ---- 暗河
    "eyeless_killifish": killifish(body=CAVE_PINK, fin=CAVE_PINK, eye="blind"),
    "pale_cave_barb": minnow(depth=2.4, body=CAVE_PINK, belly_r=PEARL, fin=CAVE_PINK, eye="blind", sheen=False,
                             extras=[("barbels", 2, 1.6, CAVE_PINK, 3)]),
    "blind_loach": loach(body=CAVE_PINK, fin=CAVE_PINK, eye="blind", extras=[("barbels", 3, 2.0, CAVE_PINK, 3)]),
    "skylight_dace": minnow(body=PEARL, fin=CAVE_PINK, eye="small", pattern=[("lateral", SLATE, 4)]),
    "pinkgill_eel": eel(body=CAVE_PINK, fin=CAVE_PINK, eye="blind", pattern=[("stripe", -0.2, 0.25, BERRY, 4, 0.78, 0.86)]),
    "blind_catfish": catfish(body=PEARL, fin=CAVE_PINK, eye="blind", extras=[("barbels", 4, 3.2, CAVE_PINK, 3)]),
    "cave_lungfish": lungfish(body=CAVE_PINK, fin=CAVE_PINK, eye="blind", extras=[("filaments", CAVE_PINK)]),
    "phosphor_cavefish": killifish(body=PEARL, fin=PEARL, eye="blind", pattern=[("photophores", GREEN_GLOW, 0.0, 2, 0.1, 0.85),
                                                                                  ("translucent", 210)]),
    "albino_cave_perch": perch(body=PEARL, fin=CAVE_PINK, eye="small", sheen=False),
    "floodwater_eel": eel(depth=1.9, body=PEARL, fin=CAVE_PINK, eye="blind", pattern=[("lightning", CYAN_GLOW, 3, 0.1, 0.8)]),
    "cave_sturgeon": sturgeon(body=PEARL, fin=CAVE_PINK, eye="blind", extras=[("scutes", WHITE, 6), ("barbels", 2, 1.6, CAVE_PINK, 3)]),
    "underriver_catfish": catfish(depth=3.0, body=CAVE_PINK, belly_r=PEARL, fin=CAVE_PINK, eye="blind", mouth="huge",
                                  extras=[("barbels", 5, 3.6, CAVE_PINK, 3)]),

    # ---- 奇异
    "crystalscale_dace": minnow(body=PRISM, fin=CYAN_GLOW, pattern=[("diamonds", PRISM, 2)], extras=[("glint", WHITE_GLOW, 5, 3)]),
    "twintail_killifish": killifish(body=ORCHID, fin=MAGENTA_GLOW, tail="twin", th=3.0, tl=0.24,
                                    pattern=[("spots", 4, PEARL, 6, -0.4, 0.7)]),
    "glowspot_perch": perch(body=COBALT, fin=COBALT, pattern=[("spots", 7, GREEN_GLOW, 4, -0.6, 0.9, 0.05, 0.85)]),
    "sixfin_gurnard": fish_spec(depth=2.5, peak=0.7, a=0.8, b=0.45, ped=0.3, tail="square", th=2.6,
                                dorsal=[(0.55, 0.72, 1.6, "spiny"), (0.2, 0.45, 1.2, "low")], anal=[(0.18, 0.42, 1.0, "low")],
                                pect=None, body=CORAL, belly_r=PEARL, fin=TEAL, eye_v=0.55, extras=[("sixfins", TEAL)]),
    "mirrorscale_carp": carp(body=PEARL, fin=SLATE, pattern=[("scales", WHITE_GLOW, 5, 0.15, 0.8)], extras=[("glint", WHITE, 6, 3)]),
    "parasol_fin": killifish(body=ORCHID, fin=ORCHID, dorsal=[], extras=[("parasol", ORCHID, CYAN_GLOW)]),
    "three_eyed_puffer": fish_spec(T=(3.0, 12.8), S=(13.4, 2.6), depth=3.8, peak=0.5, a=0.5, b=0.42, ped=0.25, tl=0.16,
                                   tail="round", th=2.2, dorsal=[(0.18, 0.28, 1.2, "round")], anal=[(0.16, 0.26, 1.1, "round")],
                                   body=JADE, belly_r=PEARL, fin=JADE, eye="three", eye_x=0.76, eye_v=0.2,
                                   pattern=[("spots", 5, VIOLET_GLOW, 4, -0.2, 0.9)]),
    "spiral_eel": eel(body=TEAL, fin=TEAL, bend=1.8, waves=2.6, extras=[("spiral", CYAN_GLOW, 4)]),
    "silica_catfish": catfish(body=PRISM, fin=SLATE, extras=[("barbels", 3, 2.4, PRISM, 4), ("crystals", 3, CYAN_GLOW)],
                              pattern=[("diamonds", PRISM, -2)]),
    "aurora_ray": None,     # 鳐是俯视的，单独画
    "leadfin_grouper": perch(depth=3.2, b=0.5, body=ASH, fin=GRAPHITE, tail="square", mouth="big",
                             pattern=[("spots", 8, VIOLET_GLOW, 3, -0.6, 0.9), ("fins", GRAPHITE, -1)]),
    "crystalback_sturgeon": sturgeon(body=SLATE, belly_r=PEARL, fin=SLATE, extras=[("scutes", PRISM, 6, (0.0,)),
                                                                                  ("crystals", 4, CYAN_GLOW),
                                                                                  ("barbels", 2, 1.4)]),
}


def aurora_ray(rng) -> Canvas:
    """极光鳐：俯视的一块菱形，胸鳍展开成两翼，背上几道发光的极光带，后面拖一条细尾。"""
    c = Canvas()
    cx, cy = 9.0, 6.6
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            # 斜着放：吻朝右上
            a = (dx - dy) / math.sqrt(2)          # 沿身体
            b = (dx + dy) / math.sqrt(2)          # 左右翼
            width = 6.2 * max(0.0, 1 - abs(a + 0.6) / 5.2) ** 0.8
            if -5.0 <= a <= 4.6 and abs(b) <= width:
                lv = 4 - round(abs(b) / max(1.0, width) * 2)
                c.set(x, y, COBALT, lv)
                band = (a * 0.9 + abs(b) * 0.35) % 2.4
                if band < 0.5 and abs(b) > 1.2:
                    c.set(x, y, GREEN_GLOW if a > 0 else CYAN_GLOW, 4, glint=True)
    for k in range(9):
        x = int(cx - 4.0 - k * 0.75)
        y = int(cy + 4.0 + k * 0.75)
        c.set(x, y, COBALT, 2, glint=True)
    c.set(int(cx + 2.2), int(cy - 3.4), GRAPHITE, 0, glint=True)
    c.set(int(cx + 3.4), int(cy - 2.2), GRAPHITE, 0, glint=True)
    lit_outline(c)
    return limit_colors(c, 16)


def register_fish():
    for fid, spec in FISH.items():
        def make(spec=spec, fid=fid):
            def fn(rng):
                return aurora_ray(rng) if spec is None else draw_fish(spec, rng)
            fn.__name__ = "fish_" + fid
            return fn
        texture("item/fish_" + fid, refs=("item/cod", "item/salmon", "item/tropical_fish", "item/pufferfish"))(make())


register_fish()


# ---------------------------------------------------------------- 钓鱼器、饵

FISHING_RIG = [
    "............GGGT",
    "...........GGG.L",
    "..........GSS..L",
    ".........GSS...L",
    "........SSS....L",
    ".......SSS.....L",
    "......RRR......L",
    ".....RRRR......L",
    "....KRRRR......L",
    "...KKRRR......BB",
    "..PPKK.......BWB",
    ".PPPK........BBB",
    ".PPP.........LB.",
    ".PP.............",
    "..P.............",
    "................",
]


@texture("item/fishing_rig", refs=("item/fishing_rod", "item/iron_sword"))
def fishing_rig(rng):
    """钓鱼器：多功能工具的附件。一节伸缩的钛合金短竿，根上一只绕满线的绕线轮，竿尖垂下一根线、挂着橙白的浮标，
    竿尖一点青色的导线灯（和钓鱼器的颜色一致）。"""
    from gear import paint as gear_paint
    c = Canvas()
    gear_paint(c, [row.replace("L", ".").replace("B", ".").replace("W", ".").replace("T", ".") for row in FISHING_RIG],
               {"G": (TITANIUM, 6, 3), "S": (STEEL, 6, 3), "R": (STEEL, 5, 2), "K": (TEAL, 5, 2), "P": (GRAPHITE, 4, 1)})
    # 绕线轮：一圈黄铜的线轴，中间青色的线
    for x, y in c.disc_cells(6.5, 7.6, 2.0):
        r = math.hypot(x + 0.5 - 6.5, y + 0.5 - 7.6)
        c.set(x, y, BRASS if r > 1.1 else TEAL, 5 if (x + y) < 14 else 3, glint=True)
    c.set(6, 7, GRAPHITE, 1, glint=True)
    # 线和浮标
    for y in range(1, 13):
        if FISHING_RIG[y][15] == "L":
            c.set(15, y, WHITE, 4, glint=True, alpha=190)
    for x, y in ((13, 9), (14, 9), (13, 10), (15, 10), (13, 11), (14, 11), (15, 11), (14, 12)):
        c.set(x, y, ORANGE if y < 11 else WHITE, 4 if y < 11 else 5, glint=True)
    c.set(14, 10, WHITE, 6, glint=True)
    c.set(15, 0, CYAN_GLOW, 5, glint=True)
    lit_outline(c)
    return limit_colors(c, 16)


def blob(c: Canvas, cx, cy, rx, ry, r, base=4, lo=1):
    """一团圆鼓鼓的东西，左上亮右下暗。"""
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1:
                lv = base + round(-(dx + dy) * 1.2) - (1 if d > 0.7 else 0)
                c.set(x, y, r, max(lo, lv))


@texture("item/stink_bait", refs=("item/rotten_flesh", "item/slime_ball"))
def stink_bait(rng):
    """腥饵：一团鱼肉和异星肉剁碎拌成的饵，红褐色，冒着几缕绿色的臭气，沾着两三点碎骨。"""
    c = Canvas()
    blob(c, 7.5, 10.0, 5.0, 4.2, CARMINE, 3)
    for x, y in ((5, 9), (8, 8), (10, 11), (6, 12), (9, 10)):
        c.set(x, y, UMBER, 2, glint=True)
    for x, y in ((7, 10), (11, 9)):
        c.set(x, y, BONE, 5, glint=True)
    for x, y in ((4, 4), (5, 3), (4, 2), (9, 4), (10, 3), (9, 2), (10, 1), (7, 5), (7, 4)):
        c.set(x, y, GREEN, 4, glint=True, alpha=170)
    lit_outline(c)
    return limit_colors(c)


@texture("item/spinner_lure", refs=("item/gold_nugget",))
def spinner_lure(rng):
    """亮片拟饵：一根钢丝从右上的线环斜下来，旁边挂一枚柳叶形的金色旋转亮片，中段几颗黄铜珠子，
    末端一截半透明的软胶身子，尾上一只钩。"""
    c = Canvas()
    for k in range(10):
        c.set(13 - k, 2 + k, STEEL, 4 if k % 3 else 5, glint=True)
    c.set(14, 1, STEEL, 5, glint=True)
    c.set(14, 2, STEEL, 3, glint=True)
    # 亮片：沿着钢丝的方向拉长的椭圆，在钢丝左上侧
    cx, cy = 8.2, 4.2
    for y in range(16):
        for x in range(16):
            along = ((x + 0.5 - cx) - (y + 0.5 - cy)) / math.sqrt(2)
            across = ((x + 0.5 - cx) + (y + 0.5 - cy)) / math.sqrt(2)
            if (along / 3.4) ** 2 + (across / 1.9) ** 2 <= 1:
                lv = 6 if across < -0.8 else 5 if across < 0.2 else 4 if across < 1.0 else 3
                c.set(x, y, GOLDEN, lv, glint=True)
    c.set(7, 3, WHITE, 6, glint=True)
    c.set(8, 2, WHITE, 6, glint=True)
    for x, y in ((9, 6), (8, 7)):
        c.set(x, y, BRASS, 5, glint=True)
    # 软胶身子
    for k, (x, y) in enumerate(((7, 8), (6, 9), (5, 10), (4, 11))):
        c.set(x, y, JADE, 5 if k < 2 else 4, glint=True, alpha=225)
        c.set(x + 1, y, JADE, 4, glint=True, alpha=225)
        c.set(x, y + 1, JADE, 3, glint=True, alpha=225)
    for x, y in ((3, 12), (2, 13), (2, 14), (3, 15), (4, 14), (1, 12)):
        c.set(x, y, STEEL, 5 if y < 14 else 4, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/deep_sinker", refs=("item/iron_nugget",))
def deep_sinker(rng):
    """深水坠饵：一颗泪滴形的铁坠，下面钩着一块异星肉，顶上一个线环。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 7.5, y + 0.5 - 7.0
            r = 3.2 * min(1.0, (dy + 4.5) / 4.5) if dy < 0 else 3.2 * math.sqrt(max(0.0, 1 - (dy / 3.4) ** 2))
            if -4.5 <= dy <= 3.4 and abs(dx) <= r:
                c.set(x, y, IRON, 4 + round(-dx * 0.8 - dy * 0.3) - (1 if abs(dx) > r - 1 else 0))
    c.set(6, 5, WHITE, 6, glint=True)
    for x, y in ((7, 1), (8, 1), (7, 0)):
        c.set(x, y, STEEL, 5, glint=True)
    for x, y in ((8, 11), (9, 12), (9, 13)):
        c.set(x, y, STEEL, 4, glint=True)
    blob(c, 10.2, 13.4, 2.2, 1.6, CARMINE, 4, 2)
    lit_outline(c)
    return limit_colors(c)


@texture("item/glow_bait", refs=("item/glow_berries", "item/glow_ink_sac"))
def glow_bait(rng):
    """夜光饵：一串拌了伽马根荧光须的肉团，挂在钩上，一颗颗发着黄绿色的光。"""
    c = Canvas()
    c.line(8, 1, 8, 4, STEEL, 4)
    for i, (x, y, r) in enumerate(((8.0, 6.0, 1.9), (6.4, 9.0, 2.1), (9.6, 11.4, 2.0), (7.0, 13.4, 1.7))):
        for px, py in c.disc_cells(x, y, r):
            d = math.hypot(px + 0.5 - x, py + 0.5 - y)
            c.set(px, py, GREEN_GLOW, 5 if d < r * 0.45 else 3 if d < r * 0.8 else 2, glint=True)
        c.set(int(x - 0.8), int(y - 0.8), YELLOW_GLOW, 5, glint=True)
    for x, y in ((11, 5), (4, 7), (12, 9), (3, 12)):
        c.set(x, y, GREEN_GLOW, 3, glint=True, alpha=150)
    lit_outline(c)
    return limit_colors(c)


@texture("item/static_lure", refs=("item/lightning_rod",))
def static_lure(rng):
    """静电拟饵：一段雷击石熔成的玻璃管做身子，两头包铜，管里封着一道蓝白的电弧；头上一片铜唇，右上一个线环，
    尾上一只钩。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            along = (x - y) / math.sqrt(2)
            across = (x + y + 1 - 16) / math.sqrt(2)
            if -4.6 <= along <= 4.4 and abs(across) <= 1.95:
                cap = along < -3.2 or along > 2.9
                lv = 5 if across < -0.7 else 4 if across < 0.7 else 3
                if cap:
                    c.set(x, y, COPPER, lv + (1 if cap and along > 2.9 and across < 0 else 0))
                else:
                    c.set(x, y, GLASS, lv + 1, alpha=205)
    # 电弧：沿管子走的之字
    for x, y in ((5, 10), (6, 10), (6, 9), (7, 8), (8, 8), (8, 7), (9, 6), (10, 6)):
        c.set(x, y, BLUE_GLOW, 5, glint=True)
    c.set(7, 9, BLUE_GLOW, 3, glint=True)
    c.set(9, 7, BLUE_GLOW, 3, glint=True)
    # 铜唇和线环
    for x, y in ((13, 4), (14, 4), (14, 5)):
        c.set(x, y, COPPER, 3, glint=True)
    for x, y in ((13, 1), (14, 1), (14, 2)):
        c.set(x, y, STEEL, 5, glint=True)
    # 尾钩
    for x, y in ((2, 13), (1, 14), (2, 15), (3, 14)):
        c.set(x, y, STEEL, 5 if y < 15 else 4, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/star_core_bait", refs=("item/nether_star", "item/heart_of_the_sea"))
def star_core_bait(rng):
    """星核饵：一颗发着橙光的能量芯，裹在半开的活珍珠壳里，四周几颗星芒。"""
    c = Canvas()
    blob(c, 8.0, 10.2, 5.2, 3.6, PEARL, 4, 2)
    for x, y in c.disc_cells(8.0, 7.6, 2.6):
        d = math.hypot(x + 0.5 - 8.0, y + 0.5 - 7.6)
        c.set(x, y, ORANGE_GLOW if d > 1.2 else YELLOW_GLOW, 5 if d < 1.2 else 4, glint=True)
    c.set(7, 6, WHITE_GLOW, 5, glint=True)
    for x, y in ((3, 3), (12, 2), (13, 6), (2, 7)):
        c.set(x, y, YELLOW_GLOW, 4, glint=True)
    c.set(12, 3, YELLOW_GLOW, 3, glint=True)
    c.set(11, 2, YELLOW_GLOW, 3, glint=True)
    lit_outline(c)
    return limit_colors(c)


# ---------------------------------------------------------------- 杂物、漂流瓶、补给舱

@texture("item/rusted_parts", rough=True, refs=("item/iron_nugget", "item/chain"))
def rusted_parts(rng):
    """锈蚀零件：一只锈透的齿轮，一颗歪了的螺栓，几块剥落的锈皮。"""
    c = Canvas()
    cx, cy = 6.5, 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            teeth = 4.6 + (0.9 if math.cos(ang * 8) > 0.2 else 0)
            if 1.3 < d <= teeth:
                c.set(x, y, RUST, 4 + round(-(dx + dy) / max(1.0, d) * 1.3))
    for x, y in c.cells():
        if c.get(x, y) and rng.random() < 0.18:
            c.shift(x, y, -1)
    for k in range(5):
        c.set(10 + k, 9 + k // 2, IRON, 4 - k % 2, glint=True)
        c.set(10 + k, 10 + k // 2, RUST, 2, glint=True)
    c.set(10, 8, IRON, 5, glint=True)
    c.set(11, 8, IRON, 4, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/tangled_weed", rough=True, refs=("item/kelp", "item/seagrass"))
def tangled_weed(rng):
    """缠结的藻团：几根海藻和钓线缠成一团，滴着水。"""
    c = Canvas()
    for k in range(5):
        ph = rng.random() * math.pi * 2
        for i in range(40):
            t = i / 39
            x = 2.5 + t * 11 + math.sin(t * 9 + ph) * 2.0
            y = 3.5 + k * 2.0 + math.cos(t * 7 + ph) * 1.6
            c.set(int(x), int(y), LEAF if k % 2 else MOSS, 3 + (1 if math.sin(t * 9 + ph) > 0 else 0))
    for i in range(14):
        c.set(2 + i, 6 + int(2.2 * math.sin(i * 0.8)), WHITE, 5, glint=True, alpha=180)
    for x, y in ((5, 14), (11, 15)):
        c.set(x, y, GLASS, 5, glint=True, alpha=170)
    lit_outline(c)
    return limit_colors(c)


@texture("item/broken_canister", refs=("item/bucket",))
def broken_canister(rng):
    """破损的储罐：一只压瘪了的白色储罐，橙色的警示条剥了一半，侧面一道裂口在漏。"""
    c = Canvas()
    for y in range(3, 15):
        for x in range(4, 12):
            dent = 1 if (y in (8, 9) and x > 8) else 0
            if x < 12 - dent:
                lv = 5 if x < 6 else 4 if x < 9 else 3
                c.set(x, y, WHITE, lv)
    for x in range(4, 12):
        c.set(x, 6, ORANGE, 4 if x < 8 else 3, glint=True)
    for x in (9, 10):
        c.clear(x, 6)
        c.set(x, 6, WHITE, 3, glint=True)
    for x in range(5, 11):
        c.set(x, 2, STEEL, 4 if x < 8 else 3)
    c.set(7, 1, STEEL, 5, glint=True)
    c.set(8, 1, STEEL, 4, glint=True)
    for x, y in ((9, 10), (10, 11), (9, 12)):
        c.set(x, y, GRAPHITE, 1, glint=True)
    for x, y in ((11, 12), (12, 13), (12, 14)):
        c.set(x, y, SULFUR, 4, glint=True, alpha=200)
    lit_outline(c)
    return limit_colors(c)


@texture("item/message_bottle", refs=("item/glass_bottle", "item/paper"))
def message_bottle(rng):
    """漂流瓶：斜放的绿玻璃瓶，瓶底在左下，软木塞在右上；瓶里卷着一张发黄的纸条，纸卷系着一根红线；
    瓶身挂着一缕海藻。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            along = (x - y) / math.sqrt(2)
            across = (x + y + 1 - 16) / math.sqrt(2)
            body = -7.4 <= along <= 2.0 and abs(across) <= 3.0 - max(0.0, along - 0.6) * 0.9
            neck = 2.0 < along <= 4.9 and abs(across) <= 1.15
            cork = 4.9 < along <= 6.6 and abs(across) <= 1.3
            if body and along < -6.6 and abs(across) > 2.2:
                continue
            if cork:
                c.set(x, y, WOOD, 5 if across < 0 else 3)
            elif body or neck:
                lv = 6 if across < -1.6 else 5 if across < 0.3 else 4 if across < 1.8 else 3
                c.set(x, y, JADE, lv, alpha=200)
    # 纸卷
    for y in range(16):
        for x in range(16):
            along = (x - y) / math.sqrt(2)
            across = (x + y + 1 - 16) / math.sqrt(2)
            if -5.6 <= along <= 0.8 and -1.2 <= across <= 1.0:
                c.set(x, y, SAND, 6 if across < -0.3 else 5, glint=True)
    for x, y in ((6, 9), (7, 9), (6, 8)):
        c.set(x, y, RED, 4, glint=True)
    # 瓶身上的高光
    for x, y in ((4, 9), (5, 8), (8, 5), (9, 4)):
        if c.get(x, y):
            c.set(x, y, WHITE, 6, glint=True, alpha=220)
    for x, y in ((2, 12), (1, 13), (1, 14)):
        c.set(x, y, LEAF, 3 if y < 14 else 2, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/sealed_cache", refs=("item/chest_minecart",))
def sealed_cache(rng):
    """密封补给舱：一只白壳的小补给舱，两头倒圆角，中间一道橙色的环带和锁扣，侧面一盏绿灯表示密封完好。"""
    c = Canvas()
    for y in range(4, 13):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (4, 12))
            if corner:
                continue
            lv = 5 if y < 6 else 4 if y < 10 else 3
            c.set(x, y, WHITE, lv)
    for y in range(4, 13):
        c.set(7, y, ORANGE, 4 if y < 8 else 3, glint=True)
        c.set(8, y, ORANGE, 3 if y < 8 else 2, glint=True)
    for x, y in ((7, 8), (8, 8)):
        c.set(x, y, STEEL, 5, glint=True)
    for x in range(3, 13):
        if x not in (7, 8):
            c.set(x, 11, STEEL, 3, glint=True)
    c.set(4, 6, GREEN_GLOW, 5, glint=True)
    lit_outline(c)
    return limit_colors(c)


# ---------------------------------------------------------------- 海鲜菜

PLATE = [
    "..BCDDDDDDDDCB..",
    ".BDEEEEEEEEEEDB.",
    "BCDEEEEEEEEEEDCB",
    ".ABCCCCCCCCCCBA.",
    "...AAAAAAAAAA...",
]


def plate(c: Canvas, oy: int = 10, r=WHITE):
    food_paint(c, PLATE, {"ABCDEFG": r}, 0, oy)


@texture("item/grilled_fillet", refs=("item/cooked_salmon", "item/cooked_cod"))
def grilled_fillet(rng):
    """烤鱼排：盘子上一块切成长方的鱼排，金黄带焦，两道烤网的焦痕，旁边一片柠檬一样的日焰果片。"""
    c = Canvas()
    plate(c)
    for y in range(4, 12):
        for x in range(3, 13):
            if (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
                continue
            lv = 5 if y < 6 else 4 if y < 9 else 3
            c.set(x, y, CRUST, lv)
    for k in range(6):
        c.set(5 + k, 4 + k, ROAST, 1, glint=True)
        c.set(8 + k, 4 + k if 4 + k < 12 else 11, ROAST, 1, glint=True)
    for x in range(4, 12, 3):
        c.set(x, 5, SALMON, 5, glint=True)
    for x, y in ((12, 3), (13, 3), (13, 4), (14, 4)):
        c.set(x, y, SULFUR, 5, glint=True)
    steam(c, ((6, 1), (7, 0), (10, 1), (9, 2)))
    lit_outline(c)
    return limit_colors(c)


def soup_bowl(soup, bits, glow=None) -> Canvas:
    c = Canvas()
    food_paint(c, BOWL, {"0123456": soup, "ABCDEFG": WOOD}, 0, 5)
    for x, y, r, lv in bits:
        c.set(x, y, r, lv, glint=True)
    steam(c, ((6, 1), (5, 2), (6, 3), (9, 0), (10, 1), (9, 2)))
    lit_outline(c)
    return limit_colors(c)


@texture("item/fish_soup", refs=("item/mushroom_stew", "item/rabbit_stew"))
def fish_soup(rng):
    """鱼汤：木碗里奶白的鱼汤，漂着几块鱼肉和伽马根丁、一点绿叶。"""
    return soup_bowl(CREAM, ((4, 7, FISH_FLESH, 5), (5, 7, FISH_FLESH, 6), (9, 6, FISH_FLESH, 5), (10, 7, FISH_FLESH, 6),
                             (7, 8, LEAF, 3), (8, 8, LEAF, 4), (6, 6, GREEN, 4), (11, 6, GREEN, 3)))


@texture("item/sashimi_platter", refs=("item/cooked_salmon",))
def sashimi_platter(rng):
    """生鱼片拼盘：一块木托板上并排码着三叠生鱼片：橙红带白色脂肪纹的、深红的、半透明白的，每叠三片错开；
    右上一片绿叶，左边一小团绿色的调料。"""
    c = Canvas()
    for x in range(1, 15):
        c.set(x, 11, WOOD, 5 if x < 8 else 4)
        c.set(x, 12, WOOD, 3)
    c.set(0, 11, WOOD, 3)
    c.set(15, 11, WOOD, 2)
    for x in (2, 13):
        c.set(x, 13, WOOD, 2)
    for g, (r, ox) in enumerate(((SALMON, 1), (CARMINE, 5), (PEARL, 9))):
        for i in range(3):
            for dx in range(2):
                for dy in range(3):
                    x, y = ox + i + dx, 10 - dy - (1 if i == 1 else 0)
                    top = r.top
                    lv = top - dy * 0 - (1 if dy == 0 else 0) - (1 if dx == 1 else 0) - (2 if r is CARMINE else 0)
                    if dy == 2:
                        lv = top - (0 if r is not CARMINE else 1)
                    c.set(x, y, r, max(2, lv), glint=True)
            if r is SALMON:
                c.set(ox + i, 9 - (1 if i == 1 else 0), WHITE, 6, glint=True)
    for (x, y), lv in zip(((13, 4), (14, 4), (12, 5), (13, 5), (14, 5), (12, 6), (13, 6), (11, 7)), (4, 3, 5, 4, 3, 4, 3, 2)):
        c.set(x, y, LEAF, lv, glint=True)
    for x, y, lv in ((13, 9, 5), (14, 9, 4), (13, 10, 4), (14, 10, 3)):
        c.set(x, y, GREEN, lv, glint=True)
    lit_outline(c)
    return limit_colors(c)


@texture("item/smoked_fish", refs=("item/cooked_cod",))
def smoked_fish(rng):
    """熏鱼：一条熏成深琥珀色的整鱼，鱼鳃上穿着麻绳挂起来，身上几道熏架的印子。"""
    f = fish_spec(body=ROAST, fin=ROAST, pattern=[("bars", 3, 0.2, 0.8, ROAST, 1, -1.0, 0.15)], eye="small", sheen=True)
    c = draw_fish(f, rng)
    for x, y in ((13, 3), (14, 2), (15, 1), (15, 0)):
        c.set(x, y, SAND, 4, glint=True)
    return c


@texture("item/deep_sea_stew", refs=("item/mushroom_stew", "item/suspicious_stew"))
def deep_sea_stew(rng):
    """深海炖菜：墨绿发蓝的浓汤，海带和鱼块，几点海带囊里的氧泡发着青光。"""
    return soup_bowl(JADE, ((4, 7, FISH_FLESH, 5), (9, 6, FISH_FLESH, 5), (10, 7, FISH_FLESH, 6), (6, 8, MOSS, 2),
                            (7, 8, MOSS, 3), (5, 6, CYAN_GLOW, 4), (11, 7, CYAN_GLOW, 4), (8, 6, CYAN_GLOW, 3)))


@texture("item/ice_fish_soup", refs=("item/beetroot_soup", "item/mushroom_stew"))
def ice_fish_soup(rng):
    """冰鱼汤：清澈发白的汤底，半透明的冰鱼片，浮着几点日焰辣酱的红油，热气腾腾。"""
    return soup_bowl(FROST, ((4, 7, PEARL, 6), (5, 7, PEARL, 5), (9, 7, PEARL, 6), (10, 6, PEARL, 5), (6, 6, EMBER, 4),
                             (8, 8, EMBER, 3), (11, 7, EMBER, 4), (7, 7, CARMINE, 4)))


@texture("item/legendary_feast", refs=("item/cake", "item/golden_carrot"))
def legendary_feast(rng):
    """传说鱼宴：一只大银盘上横卧着一整条烤得金黄的大鱼，身上两道烤痕，盘边几块日焰派和几片绿叶，鱼背上闪一点金光。"""
    c = Canvas()
    plate(c, 10, SILVER)
    f = fish_spec(T=(1.0, 9.4), S=(15.2, 6.6), depth=2.3, peak=0.55, body=CRUST, fin=ROAST, eye="small", sheen=True,
                  dorsal=[(0.4, 0.66, 1.3, "tri")], anal=[(0.25, 0.4, 1.0, "tri")], pect=None, gill=True)
    fish_c = draw_fish(f, rng)
    for x, y in fish_c.cells():
        px = fish_c.get(x, y)
        if px and px[0] is CRUST and px[1] >= 3 and x in (6, 10):
            fish_c.set(x, y, ROAST, 2, glint=True)
    c.paste(fish_c, 0, 0)
    for x, y in ((1, 12), (12, 12)):
        c.set(x, y, CRUST, 5, glint=True)
        c.set(x + 1, y, EMBER, 4, glint=True)
        c.set(x + 1, y + 1, EMBER, 3, glint=True)
    for x, y, lv in ((4, 12, 4), (5, 12, 3), (9, 12, 4), (10, 12, 3), (14, 11, 4)):
        c.set(x, y, LEAF, lv, glint=True)
    c.set(8, 3, YELLOW_GLOW, 5, glint=True)
    c.set(12, 1, YELLOW_GLOW, 4, glint=True)
    c.set(3, 4, YELLOW_GLOW, 3, glint=True)
    lit_outline(c)
    return limit_colors(c)


# ---------------------------------------------------------------- 捕鱼笼

def cage(c: Canvas, x0: int, y0: int, x1: int, y1: int, pitch: int = 3):
    """钢框，里面是一格一格的钢丝网，网眼透明。"""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            wire = (x - x0) % pitch == 0 or (y - y0) % pitch == 0
            if edge:
                c.set(x, y, STEEL, 5 if (x == x0 or y == y0) else 2)
            elif wire:
                c.set(x, y, IRON, 4 if (x + y) % 2 else 3, glint=True)


@texture("block/fish_trap_side", tile=False, refs=("block/iron_bars", "block/cobweb"))
def fish_trap_side(rng):
    """捕鱼笼的侧面（模型用 1–15 列、4–16 行）：钢框，钢丝网，中间一个漏斗形的入口，框上一块黄铜的铭牌。"""
    c = Canvas()
    cage(c, 1, 4, 14, 15)
    for x, y in c.disc_cells(7.5, 9.5, 2.6):
        d = math.hypot(x + 0.5 - 7.5, y + 0.5 - 9.5)
        if d > 1.4:
            c.set(x, y, BRASS, 4 if (x + y) < 17 else 2, glint=True)
        else:
            c.clear(x, y)
    for x in (3, 4):
        c.set(x, 13, BRASS, 5, glint=True)
    return c


@texture("block/fish_trap_top", tile=False, refs=("block/iron_bars",))
def fish_trap_top(rng):
    """捕鱼笼的顶：钢框和网，中间一块放饵的小盖板。"""
    c = Canvas()
    cage(c, 1, 1, 14, 14)
    for y in range(6, 10):
        for x in range(6, 10):
            c.set(x, y, STEEL, 4 if (x + y) < 15 else 3)
    c.set(7, 7, BRASS, 5, glint=True)
    c.set(8, 7, BRASS, 4, glint=True)
    return c


@texture("block/fish_trap_bottom", refs=("block/iron_block",))
def fish_trap_bottom(rng):
    """捕鱼笼的底：一整块冲了漏水孔的钢板。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, STEEL, 4 if (x + y) % 7 else 5)
    for y in range(3, 14, 3):
        for x in range(3, 14, 3):
            c.set(x, y, GRAPHITE, 1, glint=True)
    c.bevel(1, 1, 14, 14)
    return c


# ---------------------------------------------------------------- 浮标和鱼影

@texture("entity/fishing_bobber", refs=("entity/fishing_hook",))
def fishing_bobber(rng):
    """浮标（朝着镜头的一张图）：上红下白的圆浮子，顶上一根天线，天线头上一点青色的灯。"""
    c = Canvas()
    for x, y in c.disc_cells(8.0, 10.0, 4.2):
        dx, dy = x + 0.5 - 8.0, y + 0.5 - 10.0
        top = dy < 0
        lv = 4 + round(-(dx + dy) / 4.2 * 1.4)
        c.set(x, y, RED if top else WHITE, max(1, lv))
    for x in range(4, 13):
        c.set(x, 10, STEEL, 3, glint=True)
    for y in range(2, 6):
        c.set(8, y, STEEL, 4)
    c.set(8, 1, CYAN_GLOW, 5, glint=True)
    c.set(7, 1, CYAN_GLOW, 3, glint=True)
    lit_outline(c)
    return c


@texture("entity/fishing_shadow")
def fishing_shadow(rng):
    """水下不是鱼的东西的影子：一团边上虚掉的浅灰，游戏里按水色压暗。"""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.2, (y + 0.5 - 8) / 4.0
            d = dx * dx + dy * dy
            if d <= 1:
                c.set(x, y, WHITE, 5, alpha=int(255 * (1 - d) ** 0.7))
    return c
