"""生成 M16 太空巢穴建筑的贴图（ADR-0028 §八）：核心、光子接收器、等离子塔、相位激光塔、枪骑港、座头鲸港，各有皮肤和发光层。

用法：python tools/m16_hive_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/darkfog/hive/HiveBuildingModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。贴图都是 128×128。
- 和地面的黑雾一样是失控的自我复制机器：黑色、深紫的机壳，棱边泛紫，接缝和核心发光；太空里的更大、接缝更密。
- 发光层（_glow）是白的，渲染时按种类着色，没能量时暗下去。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402
from m16_dark_fog_textures import DARK, EDGE, HULL, glow_fill, glow_lines, plating, vents  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")
SIZE = 128
GLOW_DARK = (60, 20, 40)


def ring_glyph(s, rect, radius, strength=0.8):
    """面上一圈发光的环。"""
    x0, y0, w, h = rect
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if abs(math.hypot(x - cx, y - cy) - radius) < 0.6:
                s.put(x, y, GLOW_DARK)
                s.light(x, y, strength)


def border_glow(s, rect, strength=0.9, inset=0):
    """面的一圈边发光。"""
    x0, y0, w, h = rect
    for y in range(y0 + inset, y0 + h - inset):
        for x in range(x0 + inset, x0 + w - inset):
            if x in (x0 + inset, x0 + w - 1 - inset) or y in (y0 + inset, y0 + h - 1 - inset):
                s.put(x, y, GLOW_DARK)
                s.light(x, y, strength)


def sides(f):
    return [f[k] for k in ("front", "back", "left", "right")]


def save(s, name):
    ct.save(s.img, ENTITY, name)
    ct.save(s.glow, ENTITY, name + "_glow")


def core():
    s = ct.Skin(SIZE, SIZE, 1621)
    base = s.box(0, 0, 24, 6, 24, ct.shade(HULL, 0.9))
    plating(s, base)
    for rect in sides(base):
        vents(s, rect)
    ring_glyph(s, base["top"], 9.5)
    ring_glyph(s, base["bottom"], 7)
    column = s.box(0, 30, 16, 12, 16, HULL)
    plating(s, column)
    for rect in sides(column):
        glow_lines(s, rect, horizontal=False, step=3, strength=0.85)
    crown = s.box(0, 58, 20, 3, 20, ct.shade(HULL, 1.1))
    plating(s, crown)
    for rect in sides(crown):
        x0, y0, w, h = rect
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y0 + 1, GLOW_DARK)
            s.light(x, y0 + 1, 0.85)
    ring_glyph(s, crown["top"], 7.5)
    ring_glyph(s, crown["top"], 4, 0.6)
    spire = s.box(96, 0, 4, 10, 4, DARK)
    glow_fill(s, spire["top"], 1.0, falloff=False)
    for rect in sides(spire):
        x0, y0, w, h = rect
        for y in range(y0, y0 + h, 2):
            s.light(x0 + w // 2, y, 0.9)
    keel = s.box(96, 14, 8, 8, 8, DARK)
    plating(s, keel, seams=False)
    glow_fill(s, keel["bottom"], 1.0)
    rib = s.box(64, 30, 2, 11, 4, ct.shade(HULL, 1.15))
    plating(s, rib, seams=False)
    for key in ("left", "right"):
        glow_lines(s, rib[key], horizontal=True, step=3, strength=0.6)
    rib2 = s.box(80, 30, 4, 11, 2, ct.shade(HULL, 1.15))
    plating(s, rib2, seams=False)
    for key in ("front", "back"):
        glow_lines(s, rib2[key], horizontal=True, step=3, strength=0.6)
    for u, v, w, h, d in ((0, 81, 32, 2, 3), (0, 86, 3, 2, 26)):
        bar = s.box(u, v, w, h, d, DARK)
        for key, rect in bar.items():
            x0, y0, rw, rh = rect
            for y in range(y0, y0 + rh):
                for x in range(x0, x0 + rw):
                    if key in ("top", "bottom"):
                        if (x + y) % 3 == 0:
                            s.put(x, y, GLOW_DARK)
                            s.light(x, y, 0.85)
                    else:
                        s.put(x, y, GLOW_DARK)
                        s.light(x, y, 0.9 if (x - x0) % 2 == 0 else 0.45)
    save(s, "hive_core")


def photon_receiver():
    s = ct.Skin(SIZE, SIZE, 1622)
    mount = s.box(0, 0, 10, 6, 10, ct.shade(HULL, 0.9))
    plating(s, mount)
    for rect in sides(mount):
        vents(s, rect)
    neck = s.box(40, 0, 4, 6, 4, HULL)
    plating(s, neck, seams=False)
    plate = s.box(0, 16, 20, 20, 2, ct.shade(HULL, 1.05))
    plating(s, plate, seams=False)
    # 接收盘的正面：一圈圈同心的发光环，像晶格
    x0, y0, w, h = plate["front"]
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - cx, y - cy)
            if d < 9.5 and (abs(d - round(d / 3) * 3) < 0.5 or (x - x0) % 5 == 2 and (y - y0) % 5 == 2):
                s.put(x, y, GLOW_DARK)
                s.light(x, y, 0.95 - d * 0.03)
    plating(s, {"back": plate["back"]})
    vents(s, plate["back"])
    for u, v, w, h, d in ((0, 40, 24, 2, 3), (56, 16, 2, 20, 3)):
        rim = s.box(u, v, w, h, d, DARK)
        plating(s, rim, seams=False)
        for key in ("front", "top", "bottom", "left", "right"):
            x0, y0, rw, rh = rim[key]
            for x in range(x0, x0 + rw, 2):
                s.light(x, y0, 0.5)
    rod = s.box(0, 46, 2, 2, 9, EDGE)
    for rect in rod.values():
        glow_lines(s, rect, horizontal=False, step=2, strength=0.5)
    tip = s.box(24, 46, 4, 4, 2, DARK)
    for rect in tip.values():
        glow_fill(s, rect, 1.0)
    save(s, "hive_photon_receiver")


def plasma_tower():
    s = ct.Skin(SIZE, SIZE, 1623)
    base = s.box(0, 0, 16, 4, 16, ct.shade(HULL, 0.9))
    plating(s, base)
    for rect in sides(base):
        vents(s, rect)
    ring_glyph(s, base["top"], 6.5)
    body = s.box(0, 20, 10, 10, 10, HULL)
    plating(s, body)
    for rect in sides(body):
        x0, y0, w, h = rect
        for y in range(y0 + 2, y0 + h - 2, 2):
            for x in range(x0 + 2, x0 + w - 2):
                if (x - x0) % 2 == 0:
                    s.put(x, y, GLOW_DARK)
                    s.light(x, y, 0.6)
    collar = s.box(64, 0, 14, 2, 14, ct.shade(HULL, 1.1))
    plating(s, collar, seams=False)
    for rect in sides(collar):
        x0, y0, w, h = rect
        for x in range(x0, x0 + w, 2):
            s.light(x, y0, 0.7)
    turret = s.box(0, 40, 12, 6, 12, ct.shade(HULL, 1.1))
    plating(s, turret)
    for key in ("left", "right", "back"):
        vents(s, turret[key])
    border_glow(s, turret["top"], 0.6, inset=2)
    barrel = s.box(48, 20, 4, 4, 14, DARK)
    plating(s, barrel, seams=False)
    glow_fill(s, barrel["front"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = barrel[key]
        for x in range(x0, x0 + w, 3):
            for y in range(y0, y0 + h):
                s.light(x, y, 0.5)
    coil = s.box(84, 20, 6, 6, 2, EDGE)
    for rect in coil.values():
        border_glow(s, rect, 0.8)
    save(s, "hive_plasma_tower")


def phase_laser():
    s = ct.Skin(SIZE, SIZE, 1624)
    base = s.box(0, 0, 12, 3, 12, ct.shade(HULL, 0.9))
    plating(s, base)
    ring_glyph(s, base["top"], 4.5)
    mast = s.box(48, 0, 4, 12, 4, HULL)
    plating(s, mast)
    for rect in sides(mast):
        x0, y0, w, h = rect
        for y in range(y0 + 2, y0 + h - 1, 3):
            for x in range(x0 + 1, x0 + w - 1):
                s.put(x, y, GLOW_DARK)
                s.light(x, y, 0.7)
    for u, v, w, h, d in ((64, 0, 1, 8, 3), (72, 0, 3, 8, 1)):
        vane = s.box(u, v, w, h, d, ct.shade(HULL, 1.1))
        plating(s, vane, seams=False)
        for rect in vane.values():
            x0, y0, rw, rh = rect
            for y in range(y0, y0 + rh, 2):
                s.light(x0, y, 0.6)
    housing = s.box(0, 16, 8, 6, 8, ct.shade(HULL, 1.1))
    plating(s, housing)
    for key in ("left", "right", "back"):
        vents(s, housing[key])
    lens = s.box(32, 16, 3, 3, 4, DARK)
    glow_fill(s, lens["front"], 1.0, falloff=False)
    for key in ("left", "right", "top", "bottom"):
        glow_lines(s, lens[key], horizontal=False, step=2, strength=0.5)
    emitter = s.box(48, 16, 2, 2, 5, DARK)
    glow_fill(s, emitter["front"], 1.0, falloff=False)
    save(s, "hive_phase_laser")


def lancer_port():
    s = ct.Skin(SIZE, SIZE, 1625)
    housing = s.box(0, 0, 20, 16, 12, HULL)
    plating(s, housing)
    for key in ("left", "right"):
        glow_lines(s, housing[key], horizontal=True, step=3, strength=0.6)
    vents(s, housing["back"])
    top = s.box(0, 28, 24, 4, 4, ct.shade(HULL, 1.1))
    plating(s, top, seams=False)
    bottom = s.box(0, 36, 24, 2, 4, ct.shade(HULL, 1.1))
    plating(s, bottom, seams=False)
    # 门框朝外的一面：一排导引灯
    for frame in (top, bottom):
        x0, y0, w, h = frame["front"]
        for x in range(x0 + 1, x0 + w - 1, 2):
            s.put(x, y0 + h // 2, GLOW_DARK)
            s.light(x, y0 + h // 2, 0.9)
    post = s.box(64, 0, 4, 16, 4, ct.shade(HULL, 1.05))
    plating(s, post, seams=False)
    for key in ("front", "left", "right"):
        x0, y0, w, h = post[key]
        for y in range(y0 + 1, y0 + h - 1, 2):
            s.put(x0 + w // 2, y, GLOW_DARK)
            s.light(x0 + w // 2, y, 0.85)
    portal = s.box(0, 42, 16, 16, 1, DARK)
    for rect in portal.values():
        glow_fill(s, rect, 1.0)
    fin = s.box(80, 0, 2, 3, 8, DARK)
    plating(s, fin, seams=False)
    for key in ("left", "right"):
        x0, y0, w, h = fin[key]
        for x in range(x0, x0 + w):
            s.light(x, y0, 0.8)
    save(s, "hive_lancer_port")


def humpback_port():
    s = ct.Skin(SIZE, SIZE, 1626)
    base = s.box(0, 0, 20, 3, 20, ct.shade(HULL, 0.9))
    plating(s, base)
    for rect in sides(base):
        vents(s, rect)
    ring_glyph(s, base["bottom"], 7.5)
    hall = s.box(0, 23, 16, 10, 16, HULL)
    plating(s, hall)
    for key in ("left", "right", "back"):
        glow_lines(s, hall[key], horizontal=False, step=4, strength=0.6)
    ridge = s.box(64, 23, 6, 3, 18, ct.shade(HULL, 1.1))
    plating(s, ridge, seams=False)
    x0, y0, w, h = ridge["top"]
    for y in range(y0, y0 + h):
        s.put(x0 + w // 2, y, GLOW_DARK)
        s.light(x0 + w // 2, y, 0.8)
    gate = s.box(0, 49, 10, 7, 1, DARK)
    for rect in gate.values():
        glow_fill(s, rect, 1.0)
    pod = s.box(24, 49, 4, 5, 8, ct.shade(HULL, 1.05))
    plating(s, pod, seams=False)
    for key in ("front", "back"):
        glow_fill(s, pod[key], 0.8)
    save(s, "hive_humpback_port")


if __name__ == "__main__":
    core()
    photon_receiver()
    plasma_tower()
    phase_laser()
    lancer_port()
    humpback_port()
