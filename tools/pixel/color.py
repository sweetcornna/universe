"""颜色：sRGB 和 OKLab/OKLCH 互转，按规矩生成色阶（ADR-0031 §二）。

色阶从暗到亮排，下标 0 最暗。暗部往冷色（蓝紫）偏、亮部往暖色（黄）偏，彩度在中间偏暗处最高、
往两头收，最暗不到纯黑、最亮不到纯白。这是商业像素画通用的"色相偏移"做法，比单纯乘亮度系数生动。
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field


def _to_linear(c: float) -> float:
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _to_srgb(c: float) -> float:
    c = max(0.0, min(1.0, c))
    v = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return v * 255.0


def _cbrt(x: float) -> float:
    return math.copysign(abs(x) ** (1 / 3), x)


def rgb_to_oklab(rgb) -> tuple[float, float, float]:
    r, g, b = (_to_linear(v) for v in rgb[:3])
    l = _cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    m = _cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    s = _cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def _oklab_to_linear(L: float, a: float, b: float) -> tuple[float, float, float]:
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)


def oklch_to_rgb(L: float, C: float, h: float) -> tuple[int, int, int]:
    """OKLCH 转 sRGB；超出色域时保持亮度和色相、降彩度。"""
    def lin(c):
        hr = math.radians(h)
        return _oklab_to_linear(L, c * math.cos(hr), c * math.sin(hr))

    def inside(v):
        return all(-1e-4 <= x <= 1 + 1e-4 for x in v)

    v = lin(C)
    if not inside(v):
        lo, hi = 0.0, C
        for _ in range(24):
            mid = (lo + hi) / 2
            if inside(lin(mid)):
                lo = mid
            else:
                hi = mid
        v = lin(lo)
    return tuple(int(round(_to_srgb(x))) for x in v)


def rgb_to_oklch(rgb) -> tuple[float, float, float]:
    L, a, b = rgb_to_oklab(rgb)
    return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360


def _toward(h: float, target: float, amount: float) -> float:
    """色相 h 朝 target 转 amount 度（走近的那边），转过头就停在 target。"""
    d = (target - h + 540) % 360 - 180
    step = math.copysign(min(abs(d), amount), d)
    return (h + step) % 360


COOL = 268.0   # 暗部偏向的色相：蓝紫，环境光
WARM = 82.0    # 亮部偏向的色相：暖黄，日光


@dataclass(frozen=True)
class Ramp:
    """一条色阶。colors[0] 最暗。emissive 的色阶在发光层里也画出来。"""
    name: str
    colors: tuple
    emissive: bool = False
    meta: dict = field(default_factory=dict, compare=False, hash=False)

    def __len__(self):
        return len(self.colors)

    def __getitem__(self, i: int):
        return self.colors[max(0, min(len(self.colors) - 1, i))]

    @property
    def top(self) -> int:
        return len(self.colors) - 1


def ramp(name: str, hue: float, chroma: float, n: int = 7, lo: float = 0.24, hi: float = 0.93,
         shift: float = 22.0, peak: float = 0.42, fade: float = 0.6, emissive: bool = False,
         gamma: float = 1.0) -> Ramp:
    """生成一条色阶。

    hue/chroma 是中间那级的色相和彩度（OKLCH），lo/hi 是两头的亮度。shift 是两头最多转多少度色相，
    peak 是彩度最高的位置（0–1），fade 是两头彩度最多降掉多少，gamma 调亮度分布（大于 1 时暗部级数多）。
    """
    out = []
    for i in range(n):
        t = i / (n - 1)
        L = lo + (hi - lo) * (t ** gamma)
        if t < 0.5:
            h = _toward(hue, COOL, shift * (0.5 - t) * 2)
        else:
            h = _toward(hue, WARM, shift * (t - 0.5) * 2)
        span = peak if t < peak else 1 - peak
        c = chroma * (1 - fade * min(1.0, abs(t - peak) / max(span, 1e-6)) ** 1.5)
        out.append(oklch_to_rgb(L, c, h))
    return Ramp(name, tuple(out), emissive, {"hue": hue, "chroma": chroma})


def glow(name: str, hue: float, chroma: float, n: int = 6) -> Ramp:
    """发光的色阶：暗端是不亮时的颜色，亮端接近白，越亮色相越往暖处偏一点、彩度先升后降。"""
    out = []
    for i in range(n):
        t = i / (n - 1)
        L = 0.30 + 0.68 * t
        c = chroma * (0.55 + 0.45 * math.sin(math.pi * min(1.0, t * 1.15)))
        if t > 0.8:
            c *= 1 - (t - 0.8) * 3.2
        out.append(oklch_to_rgb(L, max(0.0, c), _toward(hue, WARM, 10 * t)))
    return Ramp(name, tuple(out), True, {"hue": hue, "chroma": chroma})


def hexs(r: Ramp) -> str:
    return " ".join("#%02x%02x%02x" % c for c in r.colors)
