"""配方的登记（ADR-0029）。用法见 build.py。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import numpy as np


@dataclass(frozen=True)
class Recipe:
    path: str
    variants: int
    loop: float
    crossfade: float
    power: bool
    level: float
    fn: Callable[[np.random.Generator, int], np.ndarray]


REGISTRY: list[Recipe] = []


def sound(path: str, variants: int = 1, loop: float = 0.0, level: float = -18.0, crossfade: float = 0.4,
          power: bool = True):
    """登记一个声音。path 是 sounds/ 下的路径（不带序号和扩展名），和 Java 里注册的名字对应（点换成斜杠）。"""

    def register(fn):
        if any(r.path == path for r in REGISTRY):
            raise ValueError(f"重复的配方 {path}")
        REGISTRY.append(Recipe(path, variants, loop, crossfade, power, level, fn))
        return fn

    return register
