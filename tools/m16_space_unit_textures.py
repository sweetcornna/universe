"""生成 M16 太空巢穴单位的贴图（ADR-0028 §八）：枪骑、座头鲸，各有皮肤和发光层。

用法：python tools/m16_space_unit_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/darkfog/space/SpaceUnitModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 和地面的黑雾单位一个画法（m16_dark_fog_textures.py 的机壳、发光接缝）：黑色、深紫的机壳，棱边泛紫，接缝和引擎发光。
- 发光层（_glow）是白的，渲染时着色（枪骑暗红、座头鲸偏紫）。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402
import m16_dark_fog_textures as df  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")

SEAM = (60, 20, 40)


def seam(s, x, y, strength):
    s.put(x, y, SEAM)
    s.light(x, y, strength)


def lancer():
    s = ct.Skin(64, 64, 1611)
    body = s.box(0, 0, 6, 4, 20, df.HULL)
    df.plating(s, body)
    # 机身两侧一道发光的腰线，顶上一道从机头通到机尾的脊线
    for key in ("left", "right"):
        x0, y0, w, h = body[key]
        for x in range(x0 + 1, x0 + w - 1):
            seam(s, x, y0 + h // 2, 0.7)
    x0, y0, w, h = body["top"]
    for y in range(y0 + 1, y0 + h - 1):
        if (y - y0) % 4 != 0:
            seam(s, x0 + w // 2, y, 0.55)
    df.eye(s, (body["front"][0] + 1, body["front"][1] + 1, 4, 2))
    spine = s.box(0, 24, 4, 2, 9, ct.shade(df.HULL, 1.15))
    df.plating(s, spine, seams=False)
    x0, y0, w, h = spine["top"]
    for y in range(y0 + 1, y0 + 4):
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y, (14, 8, 14))
            s.light(x, y, 0.45)
    engine = s.box(52, 0, 4, 3, 2, df.DARK)
    df.plating(s, engine, seams=False)
    df.glow_fill(s, engine["back"], 1.0)
    # 枪骑的"枪"：机头伸出去的两根发光的长叉
    prong = s.box(28, 24, 1, 1, 10, df.DARK)
    df.plating(s, prong, seams=False)
    for key in ("top", "bottom", "left", "right"):
        x0, y0, w, h = prong[key]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if key in ("top", "bottom"):
                    t = (y - y0) / max(1, h - 1)
                else:
                    t = (x - x0) / max(1, w - 1) if key == "right" else 1 - (x - x0) / max(1, w - 1)
                if t > 0.45:
                    seam(s, x, y, 0.4 + 0.6 * t)
    df.glow_fill(s, prong["front"], 1.0, falloff=False)
    wing = s.box(0, 36, 12, 1, 8, ct.shade(df.HULL, 0.95))
    df.plating(s, wing, seams=False)
    for key in ("top", "bottom"):
        x0, y0, w, h = wing[key]
        # 前缘一道发光的边，翼尖亮一点
        for x in range(x0, x0 + w):
            seam(s, x, y0, 0.6)
        for y in range(y0, y0 + h):
            s.light(x0, y, 0.9)
        # 翼面上两道斜的接缝
        for i in range(2, w - 2, 5):
            for j in range(1, h - 1):
                x = x0 + i + j // 2
                if x < x0 + w - 1:
                    s.put(x, y0 + j, ct.shade(df.HULL, 0.6))
    fin = s.box(40, 36, 1, 5, 5, ct.shade(df.HULL, 1.1))
    df.plating(s, fin, seams=False)
    for key in ("left", "right"):
        x0, y0, w, h = fin[key]
        for x in range(x0, x0 + w):
            seam(s, x, y0, 0.75)
    ct.save(s.img, ENTITY, "dark_fog_lancer")
    ct.save(s.glow, ENTITY, "dark_fog_lancer_glow")


def humpback():
    s = ct.Skin(128, 128, 1612)
    hull = s.box(0, 0, 20, 12, 40, df.HULL)
    df.plating(s, hull)
    # 两侧一排排发光的舷窗，顶上的甲板一格一格
    for key in ("left", "right"):
        x0, y0, w, h = hull[key]
        for x in range(x0 + 3, x0 + w - 3, 4):
            for y in (y0 + 3, y0 + 4):
                seam(s, x, y, 0.75)
                seam(s, x + 1, y, 0.75)
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y0 + h - 3, ct.shade(df.HULL, 0.55))
    x0, y0, w, h = hull["top"]
    for y in range(y0 + 4, y0 + h - 4, 6):
        for x in range(x0 + 2, x0 + w - 2):
            s.put(x, y, ct.shade(df.HULL, 0.6))
    for x in range(x0 + 2, x0 + w - 2, 6):
        for y in range(y0 + 2, y0 + h - 2):
            s.put(x, y, ct.shade(df.HULL, 0.6))
    # 鲸鱼的"嘴"：船头一道宽的发光缝，两边各一只眼
    x0, y0, w, h = hull["front"]
    for x in range(x0 + 3, x0 + w - 3):
        seam(s, x, y0 + h - 4, 0.9)
    df.eye(s, (x0 + 2, y0 + 3, 4, 2))
    df.eye(s, (x0 + w - 6, y0 + 3, 4, 2))
    x0, y0, w, h = hull["back"]
    df.vents(s, (x0 + 2, y0 + 2, w - 4, h - 4))
    belly = s.box(0, 52, 16, 3, 30, ct.shade(df.HULL, 0.85))
    df.plating(s, belly)
    x0, y0, w, h = belly["bottom"]
    # 腹部的条纹，像座头鲸的喉褶
    for x in range(x0 + 2, x0 + w - 2, 3):
        for y in range(y0 + 1, y0 + h - 1):
            s.put(x, y, ct.shade(df.HULL, 0.55))
    for y in range(y0 + h // 2 - 2, y0 + h // 2 + 2):
        for x in range(x0 + w // 2 - 2, x0 + w // 2 + 2):
            seam(s, x, y, 0.8)
    hump = s.box(0, 86, 8, 3, 8, ct.shade(df.HULL, 1.1))
    df.plating(s, hump, seams=False)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = hump[key]
        for x in range(x0 + 1, x0 + w - 1):
            seam(s, x, y0 + 1, 0.55)
    turret = s.box(32, 86, 6, 3, 6, df.DARK)
    df.plating(s, turret, seams=False)
    df.eye(s, (turret["front"][0] + 1, turret["front"][1] + 1, 4, 1))
    barrel = s.box(56, 86, 1, 1, 8, df.DARK)
    df.plating(s, barrel, seams=False)
    df.glow_fill(s, barrel["front"], 1.0, falloff=False)
    engine = s.box(92, 52, 5, 5, 3, df.DARK)
    df.plating(s, engine, seams=False)
    df.glow_fill(s, engine["back"], 1.0)
    fin = s.box(0, 98, 14, 2, 12, ct.shade(df.HULL, 0.95))
    df.plating(s, fin, seams=False)
    for key in ("top", "bottom"):
        x0, y0, w, h = fin[key]
        # 鳍的前缘上一排疙瘩（座头鲸的胸鳍），后缘发光
        for x in range(x0 + 1, x0 + w - 1, 2):
            s.put(x, y0 + 1, ct.mix(df.HULL, df.EDGE, 0.8))
        for x in range(x0, x0 + w):
            seam(s, x, y0 + h - 1, 0.6)
        s.light(x0, y0 + h - 1, 1.0)
    fluke = s.box(52, 98, 24, 2, 9, ct.shade(df.HULL, 0.95))
    df.plating(s, fluke, seams=False)
    for key in ("top", "bottom"):
        x0, y0, w, h = fluke[key]
        cx = x0 + (w - 1) / 2
        for x in range(x0, x0 + w):
            # 尾叶的后缘是一道弧，中间凹进去一点
            notch = int(round(2.5 * math.exp(-((x - cx) / 2.5) ** 2)))
            y = y0 + h - 1 - notch
            seam(s, x, y, 0.7)
    ct.save(s.img, ENTITY, "dark_fog_humpback")
    ct.save(s.glow, ENTITY, "dark_fog_humpback_glow")


if __name__ == "__main__":
    lancer()
    humpback()
