"""生成 M16 黑雾种子的贴图（ADR-0028 §八）：皮肤和发光层。飞着的中继站用地面中继站的贴图，不另做。

用法：python tools/m16_flight_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/darkfog/flight/SeedModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。贴图 64×64。
- 种子是一颗带刺的黑壳，外面一圈斜着转的环，环上挂两个小舱；壳的接缝和刺尖发光，环一圈都亮。
- 发光层（_glow）是白的，渲染时着成暗红，一明一暗。
随机数用固定种子，每次生成的结果一样。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402
from m16_dark_fog_textures import DARK, HULL, glow_fill, glow_lines, plating  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")
GLOW_DARK = (60, 20, 40)


def seed():
    s = ct.Skin(64, 64, 1641)
    shell = s.box(0, 0, 8, 8, 8, HULL)
    plating(s, shell)
    for rect in shell.values():
        # 每个面中间一道十字形的亮缝
        x0, y0, w, h = rect
        cx, cy = x0 + w // 2, y0 + h // 2
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, cy, GLOW_DARK)
            s.light(x, cy, 0.75)
        for y in range(y0 + 1, y0 + h - 1):
            s.put(cx, y, GLOW_DARK)
            s.light(cx, y, 0.75)
    spike = s.box(0, 16, 2, 2, 6, ct.shade(HULL, 1.1))
    plating(s, spike, seams=False)
    glow_fill(s, spike["back"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        # 顺着刺一道亮线
        x0, y0, w, h = spike[key]
        if w >= h:
            for x in range(x0, x0 + w):
                s.light(x, y0, 0.55)
        else:
            for y in range(y0, y0 + h):
                s.light(x0, y, 0.55)
    ring = s.box(0, 24, 14, 1, 14, DARK)
    for key, rect in ring.items():
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if key in ("top", "bottom"):
                    d = max(abs(x - (x0 + (w - 1) / 2)), abs(y - (y0 + (h - 1) / 2)))
                    if d > w / 2 - 2.5:
                        s.put(x, y, GLOW_DARK)
                        s.light(x, y, 0.95 if d > w / 2 - 1.5 else 0.5)
                else:
                    s.put(x, y, GLOW_DARK)
                    s.light(x, y, 0.9 if (x - x0) % 3 else 0.4)
    pod = s.box(32, 0, 4, 4, 4, ct.shade(HULL, 1.05))
    plating(s, pod, seams=False)
    for key in ("front", "back", "left", "right"):
        glow_lines(s, pod[key], horizontal=True, step=2, strength=0.7)
    glow_fill(s, pod["top"], 0.9)
    glow_fill(s, pod["bottom"], 0.9)
    ct.save(s.img, ENTITY, "dark_fog_seed")
    ct.save(s.glow, ENTITY, "dark_fog_seed_glow")


if __name__ == "__main__":
    seed()
