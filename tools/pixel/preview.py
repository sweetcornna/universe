"""总览图（ADR-0031 §四）：每张贴图一行，旧图、新图放大，新图 3×3 平铺看接缝，后面跟原版的参照。"""
from __future__ import annotations

from pathlib import Path
from typing import Optional

from PIL import Image, ImageDraw

from palette import all_ramps

BG = (58, 60, 66, 255)
TEXT = (226, 228, 232, 255)


def _scale(w: int, base: int) -> int:
    """小图放大 base 倍；实体贴图大，放大得少一些，一行不至于太宽。"""
    if w <= 16:
        return base
    if w <= 32:
        return max(2, base // 2)
    if w <= 64:
        return 4
    if w <= 128:
        return 3
    return 2


def _frame(img: Image.Image, frames: int, i: int = 0) -> Image.Image:
    w, h = img.size
    fh = h // max(1, frames)
    return img.crop((0, i * fh, w, (i + 1) * fh))


def _up(img: Image.Image, k: int) -> Image.Image:
    w, h = img.size
    return img.resize((w * k, h * k), Image.NEAREST)


def _tile(img: Image.Image, n: int = 3) -> Image.Image:
    w, h = img.size
    out = Image.new("RGBA", (w * n, h * n))
    for i in range(n):
        for j in range(n):
            out.alpha_composite(img, (i * w, j * h))
    return out


def _strip(img: Image.Image, frames: int, k: int = 2, most: int = 8) -> Image.Image:
    """动画的前几帧横着排开。"""
    n = min(frames, most)
    first = _frame(img, frames)
    w, h = first.size
    out = Image.new("RGBA", ((w * k + 4) * n, h * k))
    for i in range(n):
        out.alpha_composite(_up(_frame(img, frames, i), k), (i * (w * k + 4), 0))
    return out


def sheet(rows: list[tuple], out: Path, refs: list[Image.Image] = (), scale: int = 6):
    """rows: (名字, 旧图或 None, 新图, 是否平铺[, 帧数])。refs: 原版的参照图，排在最下面一行。

    每行：名字、旧图、新图（放大），平铺的再放一张 3×3 的平铺，动画的放前几帧。
    """
    laid = []
    width = 0
    for row in rows:
        name, old, new, tiled = row[:4]
        frames = row[4] if len(row) > 4 else 1
        k = _scale(new.size[0], scale)
        parts = []
        if old is not None:
            parts.append(_up(_frame(old, frames), _scale(old.size[0], scale)))
        else:
            parts.append(None)
        parts.append(_up(_frame(new, frames), k))
        if frames > 1:
            parts.append(_strip(new, frames))
        elif tiled:
            parts.append(_tile(_up(new, 2)))
        cell = max(p.size[0] for p in parts if p is not None)
        h = max(p.size[1] for p in parts if p is not None)
        laid.append((name, parts, cell, h))
        width = max(width, 150 + sum((p.size[0] if p is not None else cell) + 10 for p in parts))
    cell16 = 16 * scale
    refs = [_up(_frame(r, max(1, r.size[1] // r.size[0])), _scale(r.size[0], scale)) for r in refs]
    ref_w = 10 + sum(r.size[0] + 8 for r in refs)
    ref_h = max((r.size[1] for r in refs), default=0)
    height = sum(h + 14 for *_, h in laid) + (ref_h + 30 if refs else 0) + 10
    img = Image.new("RGBA", (max(width, ref_w), height), BG)
    d = ImageDraw.Draw(img)
    y = 6
    for name, parts, cell, h in laid:
        d.text((6, y + min(h, cell16) // 2 - 6), name[:22], fill=TEXT)
        x = 150
        for p in parts:
            if p is not None:
                img.alpha_composite(p, (x, y))
            x += (p.size[0] if p is not None else cell) + 10
        y += h + 14
    if refs:
        d.text((6, y), "vanilla", fill=TEXT)
        y += 14
        x = 10
        for r in refs:
            img.alpha_composite(r, (x, y))
            x += r.size[0] + 8
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def palette_sheet(out: Path, sw: int = 26):
    ramps = list(all_ramps().values())
    img = Image.new("RGBA", (130 + 9 * sw, len(ramps) * (sw + 4) + 8), BG)
    d = ImageDraw.Draw(img)
    for i, r in enumerate(ramps):
        y = 4 + i * (sw + 4)
        d.text((6, y + sw // 2 - 6), r.name + (" *" if r.emissive else ""), fill=TEXT)
        for j, c in enumerate(r.colors):
            d.rectangle((126 + j * sw, y, 126 + (j + 1) * sw - 2, y + sw - 1), fill=tuple(c) + (255,))
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
