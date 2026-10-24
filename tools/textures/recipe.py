"""贴图配方的登记（ADR-0031 §三）。一个函数画一张图，返回 Canvas。"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

REGISTRY: list["Texture"] = []


@dataclass
class Texture:
    path: str                       # 相对 textures/ 的路径，不带 .png，例如 block/assembler_front
    fn: Callable
    module: str
    kind: str                       # block、item、entity、gui
    tile: bool                      # 预览里平铺看接缝
    rough: bool                     # 粗糙材质，检查时放宽零碎像素
    glow: bool                      # 另写一张 <名字>_glow.png，只含发光像素，给模型的自发光层用
    refs: tuple = field(default=()) # 原版参照，例如 ("block/furnace_front_on",)
    frames: int = 1                 # 动画的帧数：帧从上往下排成一长条，.png.mcmeta 另外放着不动
    tinted: bool = False            # 游戏里会染色（星球方块、植物的底层）：只画灰度，平均亮度 0.8 左右


def texture(path: str, kind: str | None = None, tile: bool | None = None, rough: bool = False,
            glow: bool = False, refs: tuple = (), frames: int = 1, tinted: bool = False):
    def wrap(fn):
        k = kind or path.split("/", 1)[0]
        REGISTRY.append(Texture(path, fn, fn.__module__, k, k == "block" if tile is None else tile,
                                rough, glow, tuple(refs), frames, tinted))
        return fn
    return wrap
