"""贴图的自动检查（ADR-0031 §四）：只抓明显的问题，好不好看还是要放大了用眼睛看。"""
from __future__ import annotations

from canvas import Canvas
from color import rgb_to_oklab, rgb_to_oklch

LIMITS = {"block": 14, "item": 16, "entity": 40, "gui": 40}


def lint(c: Canvas, kind: str = "block", rough: bool = False, tinted: bool = False, frames: int = 1) -> list[str]:
    """返回问题列表，空列表表示没发现问题。rough 表示粗糙材质（岩石、锈），允许更多零碎像素。

    tinted 表示游戏里会染色的灰度贴图；frames 大于 1 时按帧分开检查（帧从上往下排）。
    """
    if frames > 1:
        fh = c.h // frames
        first: dict[str, str] = {}       # 问题的种类 -> 第一次出现时的完整说明
        where: dict[str, list[int]] = {}  # 问题的种类 -> 出现在哪几帧
        for i in range(frames):
            for msg in lint(c.crop(0, i * fh, c.w, fh), kind, rough, tinted):
                key = msg.split("：")[0].split("（")[0]
                first.setdefault(key, msg)
                where.setdefault(key, []).append(i)
        return [f"{first[k]}（第 {'、'.join(map(str, v[:6]))} 帧）" for k, v in where.items()]
    problems = []
    colors = {}
    lit = set()
    opaque = 0
    for y in range(c.h):
        for x in range(c.w):
            px = c.p[y][x]
            if not px:
                continue
            opaque += 1
            rgb = px[0][px[1]]
            colors[rgb] = colors.get(rgb, 0) + 1
            if px[0].emissive:
                lit.add(rgb)
    plain = len(set(colors) - lit)
    if plain > LIMITS.get(kind, 16):
        problems.append(f"颜色太多：{plain} 种（上限 {LIMITS.get(kind)}）")
    Ls = {rgb: rgb_to_oklab(rgb)[0] for rgb in colors}
    for rgb, L in Ls.items():
        if L < 0.13:
            problems.append("有接近纯黑的颜色 #%02x%02x%02x" % rgb)
            break
    for rgb, L in Ls.items():
        if L > 0.985:
            problems.append("有纯白 #%02x%02x%02x" % rgb)
            break
    if tinted:
        for rgb in colors:
            if rgb_to_oklch(rgb)[1] > 0.02:
                problems.append("染色的贴图里有彩色 #%02x%02x%02x（只画灰度）" % rgb)
                break
        if opaque:
            mean = sum(Ls[rgb] * n for rgb, n in colors.items()) / opaque
            if not 0.66 <= mean <= 0.90:
                problems.append(f"染色贴图的平均亮度 {mean:.2f}，应在 0.66–0.90（太暗会把配色压黑）")
    # 只有零星几个像素的叠加层（发光层、矿点）不看明暗跨度：它贴在别的面上，远看是那个面
    if opaque >= c.w * c.h // 4 and Ls:
        span = max(Ls.values()) - min(Ls.values())
        need = 0.12 if tinted else 0.22
        if span < need and kind in ("block", "item"):
            problems.append(f"明暗跨度太小（{span:.2f}），远看是一片")
    # 孤立像素：八邻都和它不同色，又不是登记过的高光点（斜线、圆的边只在角上相接，不算孤立）
    orphans = 0
    for y in range(c.h):
        for x in range(c.w):
            px = c.p[y][x]
            if not px or (x, y) in c.glints:
                continue
            same = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
                q = c.get(x + dx, y + dy)
                if q and q[0][q[1]] == px[0][px[1]]:
                    same = True
                    break
            if not same:
                orphans += 1
    if opaque:
        ratio = orphans / opaque
        limit = 0.22 if rough else 0.10
        if ratio > limit:
            problems.append(f"零碎像素太多：{orphans} 个（{ratio:.0%}，上限 {limit:.0%}）")
    return problems
