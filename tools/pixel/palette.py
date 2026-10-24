"""全模组共用的调色板（ADR-0031 §二）：每种材质一条色阶，贴图只从这里取色。

新材质先在这里加一条色阶、在总览图里和别的色阶比过，再拿去画。不要在贴图脚本里临时写 RGB。
只有一组贴图自己用的色阶（比如 16 色的船体涂装）可以写在配方模块里，但要用 register() 登记，
这样它也出现在调色板总览图里。
"""
from __future__ import annotations

from color import Ramp, glow, ramp

_ALL: dict[str, Ramp] = {}


def _add(r: Ramp) -> Ramp:
    _ALL[r.name] = r
    return r


def register(r: Ramp) -> Ramp:
    """配方模块里自己定的色阶也登记进来，出现在调色板总览图里。名字不能和已有的重复。"""
    if r.name in _ALL and _ALL[r.name] != r:
        raise ValueError(f"色阶重名：{r.name}")
    return _add(r)


# 结构和金属
STEEL = _add(ramp("steel", 250, 0.020, n=8, lo=0.25, hi=0.90, shift=18))          # 机壳：冷灰的钢
GRAPHITE = _add(ramp("graphite", 262, 0.024, n=7, lo=0.20, hi=0.64, shift=14))     # 框架、深色件
WHITE = _add(ramp("white", 95, 0.012, n=7, lo=0.46, hi=0.97, shift=26))            # 白漆面板、航天器的白
IRON = _add(ramp("iron", 85, 0.006, n=7, lo=0.30, hi=0.96, shift=20))              # 和原版铁一个调子
TITANIUM = _add(ramp("titanium", 232, 0.030, n=7, lo=0.30, hi=0.95, shift=16))
COPPER = _add(ramp("copper", 48, 0.120, n=7, lo=0.28, hi=0.86, shift=18, peak=0.5))
BRASS = _add(ramp("brass", 86, 0.125, n=7, lo=0.33, hi=0.95, shift=14, peak=0.55))
GOLD_FOIL = _add(ramp("gold_foil", 80, 0.150, n=6, lo=0.40, hi=0.94, shift=12, peak=0.6))
RUBBER = _add(ramp("rubber", 275, 0.016, n=5, lo=0.18, hi=0.46, shift=8))
CONCRETE = _add(ramp("concrete", 80, 0.010, n=7, lo=0.34, hi=0.86, shift=20))

# 涂装和等级色：Mk.I 琥珀、Mk.II 绿、Mk.III 蓝
AMBER = _add(ramp("amber", 68, 0.150, n=7, lo=0.30, hi=0.90, shift=16, peak=0.6))
HAZARD = _add(ramp("hazard", 96, 0.165, n=6, lo=0.36, hi=0.93, shift=10, peak=0.7))
RED = _add(ramp("red", 26, 0.170, n=7, lo=0.26, hi=0.82, shift=14, peak=0.55))
GREEN = _add(ramp("green", 150, 0.140, n=7, lo=0.26, hi=0.88, shift=16, peak=0.55))
TEAL = _add(ramp("teal", 196, 0.100, n=7, lo=0.26, hi=0.88, shift=14))
BLUE = _add(ramp("blue", 256, 0.150, n=7, lo=0.24, hi=0.84, shift=10, peak=0.55))
VIOLET = _add(ramp("violet", 302, 0.150, n=7, lo=0.24, hi=0.84, shift=10, peak=0.55))

# 自然和有机物
GRAY = _add(ramp("gray", 0, 0.0, n=7, lo=0.50, hi=0.97, shift=0, fade=0))          # 染色用的灰度：游戏按星球配色乘上去
STONE = _add(ramp("stone", 75, 0.014, n=7, lo=0.28, hi=0.84, shift=20))            # 暖灰的岩石
BASALT = _add(ramp("basalt", 265, 0.018, n=6, lo=0.20, hi=0.62, shift=12))         # 冷黑的玄武岩、陨石
SOIL = _add(ramp("soil", 58, 0.055, n=7, lo=0.24, hi=0.72, shift=16))              # 泥土
SAND = _add(ramp("sand", 84, 0.065, n=7, lo=0.42, hi=0.93, shift=14))
WOOD = _add(ramp("wood", 60, 0.075, n=7, lo=0.26, hi=0.80, shift=16, peak=0.5))    # 木头、树皮
LEAF = _add(ramp("leaf", 138, 0.125, n=7, lo=0.26, hi=0.88, shift=22, peak=0.5))   # 叶子、草
MOSS = _add(ramp("moss", 118, 0.085, n=7, lo=0.26, hi=0.84, shift=20))             # 苔藓、橄榄绿
ICE = _add(ramp("ice", 226, 0.050, n=7, lo=0.50, hi=0.97, shift=10))
FLESH = _add(ramp("flesh", 18, 0.120, n=7, lo=0.28, hi=0.86, shift=14, peak=0.5))  # 生肉、红色的有机组织
BONE = _add(ramp("bone", 86, 0.032, n=7, lo=0.42, hi=0.95, shift=18))              # 骨头、蛋壳、化石
BREAD = _add(ramp("bread", 70, 0.105, n=7, lo=0.30, hi=0.90, shift=16, peak=0.55)) # 烤过的面食：金黄到焦褐
CREAM = _add(ramp("cream", 92, 0.045, n=6, lo=0.62, hi=0.97, shift=10))            # 奶油、面糊
BERRY = _add(ramp("berry", 345, 0.140, n=7, lo=0.24, hi=0.84, shift=12, peak=0.5)) # 浆果、果酱、紫红的花
ORANGE = _add(ramp("orange", 52, 0.155, n=7, lo=0.30, hi=0.90, shift=14, peak=0.6))
PINK = _add(ramp("pink", 2, 0.100, n=7, lo=0.40, hi=0.93, shift=12, peak=0.55))
RUST = _add(ramp("rust", 42, 0.100, n=7, lo=0.24, hi=0.74, shift=14, peak=0.5))    # 锈、残骸
OIL = _add(ramp("oil", 300, 0.030, n=6, lo=0.16, hi=0.50, shift=10))               # 原油、焦油：带一点紫的黑

# 黑雾：发暗紫的黑色躯壳，裂缝里透出深红
FOG = _add(ramp("fog", 305, 0.035, n=7, lo=0.16, hi=0.58, shift=10, peak=0.6))
CRIMSON = _add(ramp("crimson", 18, 0.165, n=7, lo=0.22, hi=0.74, shift=10, peak=0.55))

# 玻璃和屏幕
GLASS = _add(ramp("glass", 222, 0.040, n=6, lo=0.40, hi=0.97, shift=10))
SCREEN = _add(ramp("screen", 238, 0.050, n=5, lo=0.18, hi=0.42, shift=6, peak=0.7))

# 发光：和界面一样的青色是主色
CYAN_GLOW = _add(glow("cyan_glow", 208, 0.135))
AMBER_GLOW = _add(glow("amber_glow", 62, 0.170))
GREEN_GLOW = _add(glow("green_glow", 145, 0.170))
RED_GLOW = _add(glow("red_glow", 26, 0.190))
VIOLET_GLOW = _add(glow("violet_glow", 305, 0.170))
WHITE_GLOW = _add(glow("white_glow", 95, 0.030))
ORANGE_GLOW = _add(glow("orange_glow", 45, 0.180))     # 火焰、炽热的金属、推进器尾焰
BLUE_GLOW = _add(glow("blue_glow", 262, 0.160))        # 曲速、等离子、星门
MAGENTA_GLOW = _add(glow("magenta_glow", 345, 0.180))  # 黑雾的能量、异星生物的荧光
YELLOW_GLOW = _add(glow("yellow_glow", 98, 0.160))     # 太阳、警示灯


def all_ramps() -> dict[str, Ramp]:
    return dict(_ALL)


def by_name(name: str) -> Ramp:
    return _ALL[name]
