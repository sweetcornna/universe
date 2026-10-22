"""生成 M16 黑雾单位和建筑的贴图（ADR-0028）：突袭者、游骑兵、守卫者，钻芯、营地、高能激光塔、等离子哨塔、中继站，
各有皮肤和发光层；还有光束的贴图。

用法：python tools/m16_dark_fog_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/darkfog/DarkFogUnitModel.java、DarkFogBuildingModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 黑雾是失控的自我复制机器：黑色、深紫的机壳，棱边泛紫，接缝和核心发光。
- 发光层（_glow）是白的，渲染时着色（单位暗红、守卫者偏紫，营地按种类，建筑没能量时暗下去）。
- 光束贴图横着从透明到亮再到透明，竖着不变，渲染时按光束的颜色着色。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")

HULL = (34, 28, 42)
DARK = (20, 16, 26)
EDGE = (78, 58, 98)
PURPLE = (96, 46, 122)
VENT = (12, 10, 16)


def plating(s, f, base=HULL, seams=True):
    """机壳：每个面一圈泛紫的棱边，中间按格子错开的暗色接缝，零星的亮点像蚀刻的纹路。"""
    for key, (x0, y0, w, h) in f.items():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                if edge and w > 1 and h > 1:
                    s.put(x, y, ct.mix(base, EDGE, 0.55))
                elif seams and w > 3 and h > 3 and ((x - x0) % 4 == 0 and (y - y0) % 2 == 0 or (y - y0) % 4 == 3 and (x - x0) % 3 == 1):
                    s.put(x, y, ct.shade(base, 0.62))
                elif s.rng.random() < 0.04:
                    s.put(x, y, ct.mix(base, PURPLE, 0.5))


def glow_lines(s, rect, horizontal=True, step=3, strength=0.9):
    """发光的接缝：隔几行（列）一道。"""
    x0, y0, w, h = rect
    if horizontal:
        for y in range(y0 + 1, y0 + h - 1, step):
            for x in range(x0 + 1, x0 + w - 1):
                s.put(x, y, (60, 20, 40))
                s.light(x, y, strength)
    else:
        for x in range(x0 + 1, x0 + w - 1, step):
            for y in range(y0 + 1, y0 + h - 1):
                s.put(x, y, (60, 20, 40))
                s.light(x, y, strength)


def glow_fill(s, rect, strength=1.0, falloff=True):
    """整块发光：中间最亮，往边上暗一些。"""
    x0, y0, w, h = rect
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    r = max(1.0, max(w, h) / 2)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - cx, y - cy) / r if falloff else 0
            s.put(x, y, ct.mix((90, 24, 56), (40, 12, 30), min(1, d)))
            s.light(x, y, max(0.35, strength * (1 - 0.55 * min(1, d))))


def vents(s, rect, color=VENT):
    x0, y0, w, h = rect
    for y in range(y0 + 1, y0 + h - 1, 2):
        s.hline(x0 + 1, x0 + w - 1, y, color)


def eye(s, rect):
    """独眼：深色的玻璃，中间一道亮缝。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (14, 8, 14))
    cy = y0 + (h - 1) / 2
    cx = x0 + (w - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            dy = abs(y - cy) / max(1, h / 2)
            dx = abs(x - cx) / max(1, w / 2)
            s.light(x, y, max(0.3, 1 - dx * 0.5 - dy * 0.6))


# ---- 单位

def raider():
    s = ct.Skin(64, 64, 1601)
    body = s.box(0, 0, 12, 4, 14, HULL)
    plating(s, body)
    # 侧面一排发光的散热口
    for key in ("left", "right"):
        glow_lines(s, body[key], horizontal=False, step=3, strength=0.7)
    eye(s, (body["front"][0] + 4, body["front"][1] + 1, 4, 2))
    dome = s.box(0, 18, 8, 3, 8, ct.shade(HULL, 1.15))
    plating(s, dome, seams=False)
    x0, y0, w, h = dome["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if abs(math.hypot(x - (x0 + 3.5), y - (y0 + 3.5)) - 2.2) < 0.7:
                s.put(x, y, (60, 20, 40))
                s.light(x, y, 0.8)
    prong = s.box(32, 18, 2, 2, 6, DARK)
    plating(s, prong, seams=False)
    glow_fill(s, prong["front"], 1.0, falloff=False)
    plate = s.box(0, 30, 10, 1, 10, DARK)
    plating(s, plate, seams=False)
    glow_fill(s, plate["bottom"], 0.95)
    vane = s.box(40, 30, 1, 3, 1, EDGE)
    x0, y0, w, h = vane["bottom"]
    s.light(x0, y0, 0.8)
    ct.save(s.img, ENTITY, "dark_fog_raider")
    ct.save(s.glow, ENTITY, "dark_fog_raider_glow")


def ranger():
    s = ct.Skin(64, 32, 1602)
    body = s.box(0, 0, 4, 3, 12, HULL)
    plating(s, body)
    glow_fill(s, body["back"], 1.0)
    for key in ("left", "right"):
        x0, y0, w, h = body[key]
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y0 + 1, (60, 20, 40))
            s.light(x, y0 + 1, 0.6)
    nose = s.box(32, 0, 2, 2, 3, DARK)
    eye(s, nose["front"])
    fin = s.box(32, 8, 1, 3, 4, ct.shade(HULL, 1.1))
    plating(s, fin, seams=False)
    wing = s.box(0, 16, 7, 1, 6, ct.shade(HULL, 0.95))
    plating(s, wing, seams=False)
    # 翼面上一道发光的边线，翼尖亮一点
    for key in ("top", "bottom"):
        x0, y0, w, h = wing[key]
        for x in range(x0, x0 + w):
            s.put(x, y0 + h - 1, (60, 20, 40))
            s.light(x, y0 + h - 1, 0.7)
        s.light(x0 + w - 1, y0, 1.0)
    ct.save(s.img, ENTITY, "dark_fog_ranger")
    ct.save(s.glow, ENTITY, "dark_fog_ranger_glow")


def guardian():
    s = ct.Skin(64, 64, 1603)
    core = s.box(0, 0, 10, 10, 10, HULL)
    plating(s, core)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = core[key]
        # 十字形的发光接缝
        for i in range(1, w - 1):
            s.put(x0 + i, y0 + h // 2, (60, 20, 40))
            s.light(x0 + i, y0 + h // 2, 0.75)
        for j in range(1, h - 1):
            s.put(x0 + w // 2, y0 + j, (60, 20, 40))
            s.light(x0 + w // 2, y0 + j, 0.75)
    eye_box = s.box(40, 0, 4, 3, 1, DARK)
    eye(s, eye_box["front"])
    shield = s.box(0, 20, 8, 12, 2, ct.shade(HULL, 1.05))
    plating(s, shield)
    x0, y0, w, h = shield["front"]
    # 护盾板上一道竖着的符文
    for y in range(y0 + 2, y0 + h - 2):
        if (y - y0) % 3 != 0:
            s.put(x0 + w // 2, y, (60, 20, 40))
            s.light(x0 + w // 2, y, 0.85)
            s.put(x0 + w // 2 - 1, y, ct.shade(HULL, 0.6))
    spike = s.box(24, 20, 3, 4, 3, DARK)
    plating(s, spike, seams=False)
    glow_fill(s, spike["bottom"], 1.0, falloff=False)
    ct.save(s.img, ENTITY, "dark_fog_guardian")
    ct.save(s.glow, ENTITY, "dark_fog_guardian_glow")


# ---- 建筑

def core():
    s = ct.Skin(64, 64, 1611)
    base = s.box(0, 0, 16, 3, 16, ct.shade(HULL, 0.9))
    plating(s, base)
    for key in ("front", "back", "left", "right"):
        vents(s, base[key])
    column = s.box(0, 19, 8, 11, 8, HULL)
    plating(s, column)
    for key in ("front", "back", "left", "right"):
        glow_lines(s, column[key], horizontal=False, step=3, strength=0.85)
    crown = s.box(0, 38, 12, 3, 12, ct.shade(HULL, 1.1))
    plating(s, crown)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = crown[key]
        for x in range(x0 + 1, x0 + w - 1):
            s.light(x, y0 + 1, 0.8)
            s.put(x, y0 + 1, (60, 20, 40))
    spire = s.box(48, 38, 2, 5, 2, DARK)
    glow_fill(s, spire["top"], 1.0, falloff=False)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = spire[key]
        for x in range(x0, x0 + w):
            s.light(x, y0, 0.9)
    ring = s.box(0, 53, 10, 1, 10, DARK)
    for key, rect in ring.items():
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if key in ("top", "bottom"):
                    edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    if edge:
                        s.put(x, y, (60, 20, 40))
                        s.light(x, y, 0.9)
                else:
                    s.put(x, y, (60, 20, 40))
                    s.light(x, y, 0.9 if (x - x0) % 2 == 0 else 0.5)
    ct.save(s.img, ENTITY, "dark_fog_core")
    ct.save(s.glow, ENTITY, "dark_fog_core_glow")


def camp():
    s = ct.Skin(64, 64, 1612)
    platform = s.box(0, 0, 16, 2, 16, ct.shade(HULL, 0.9))
    plating(s, platform)
    x0, y0, w, h = platform["top"]
    # 平台上一圈发光的六边形纹路
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = max(abs(x - cx) * 0.87 + abs(y - cy) * 0.5, abs(y - cy))
            if abs(d - 5.5) < 0.5:
                s.put(x, y, (60, 20, 40))
                s.light(x, y, 0.7)
    pillar = s.box(0, 18, 2, 10, 2, HULL)
    plating(s, pillar, seams=False)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = pillar[key]
        for y in range(y0 + 1, y0 + h - 1, 3):
            s.light(x0, y, 0.8)
    frame = s.box(0, 30, 14, 2, 14, ct.shade(HULL, 1.05))
    plating(s, frame)
    x0, y0, w, h = frame["bottom"]
    for y in range(y0 + 3, y0 + h - 3):
        for x in range(x0 + 3, x0 + w - 3):
            s.put(x, y, (60, 20, 40))
            s.light(x, y, 0.45)
    portal = s.box(0, 46, 6, 6, 6, DARK)
    for rect in portal.values():
        glow_fill(s, rect, 1.0)
    ct.save(s.img, ENTITY, "dark_fog_camp")
    ct.save(s.glow, ENTITY, "dark_fog_camp_glow")


def laser_tower():
    s = ct.Skin(64, 64, 1613)
    base = s.box(0, 0, 12, 4, 12, ct.shade(HULL, 0.9))
    plating(s, base)
    for key in ("front", "back", "left", "right"):
        vents(s, base[key])
    mast = s.box(0, 16, 4, 20, 4, HULL)
    plating(s, mast)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = mast[key]
        for y in range(y0 + 2, y0 + h - 1, 4):
            for x in range(x0 + 1, x0 + w - 1):
                s.put(x, y, (60, 20, 40))
                s.light(x, y, 0.7)
    head = s.box(16, 16, 8, 6, 8, ct.shade(HULL, 1.1))
    plating(s, head)
    for key in ("left", "right", "back"):
        vents(s, head[key])
    lens = s.box(48, 0, 3, 3, 2, DARK)
    glow_fill(s, lens["front"], 1.0, falloff=False)
    ct.save(s.img, ENTITY, "dark_fog_laser_tower")
    ct.save(s.glow, ENTITY, "dark_fog_laser_tower_glow")


def plasma_sentry():
    s = ct.Skin(64, 64, 1614)
    base = s.box(0, 0, 16, 3, 16, ct.shade(HULL, 0.9))
    plating(s, base)
    body = s.box(0, 19, 10, 9, 10, HULL)
    plating(s, body)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = body[key]
        # 塔身上的发光的格栅
        for y in range(y0 + 2, y0 + h - 2, 2):
            for x in range(x0 + 2, x0 + w - 2):
                if (x - x0) % 2 == 0:
                    s.put(x, y, (60, 20, 40))
                    s.light(x, y, 0.6)
    head = s.box(0, 38, 8, 4, 8, ct.shade(HULL, 1.1))
    plating(s, head)
    barrel = s.box(32, 38, 3, 3, 9, DARK)
    plating(s, barrel, seams=False)
    glow_fill(s, barrel["front"], 1.0, falloff=False)
    for key in ("left", "right", "top"):
        x0, y0, w, h = barrel[key]
        for x in range(x0, x0 + w, 3):
            for y in range(y0, y0 + h):
                s.light(x, y, 0.5)
    ct.save(s.img, ENTITY, "dark_fog_plasma_sentry")
    ct.save(s.glow, ENTITY, "dark_fog_plasma_sentry_glow")


def relay():
    s = ct.Skin(64, 64, 1615)
    hull = s.box(0, 0, 12, 4, 12, HULL)
    plating(s, hull)
    for key in ("front", "back", "left", "right"):
        glow_lines(s, hull[key], horizontal=True, step=2, strength=0.6)
    arm = s.box(0, 16, 2, 2, 6, ct.shade(HULL, 1.1))
    plating(s, arm, seams=False)
    glow_fill(s, arm["back"], 1.0, falloff=False)
    emitter = s.box(16, 16, 4, 3, 4, DARK)
    glow_fill(s, emitter["bottom"], 1.0)
    fin = s.box(32, 16, 1, 3, 6, ct.shade(HULL, 1.1))
    plating(s, fin, seams=False)
    x0, y0, w, h = fin["right"]
    for x in range(x0, x0 + w):
        s.light(x, y0, 0.8)
    ct.save(s.img, ENTITY, "dark_fog_relay")
    ct.save(s.glow, ENTITY, "dark_fog_relay_glow")


def beam():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        d = abs(x - 7.5) / 7.5
        alpha = max(0.0, 1 - d) ** 1.4
        v = int(255 * (0.75 + 0.25 * (1 - d)))
        for y in range(16):
            img.putpixel((x, y), (v, v, v, int(255 * alpha)))
    ct.save(img, ENTITY, "dark_fog_beam")


if __name__ == "__main__":
    raider()
    ranger()
    guardian()
    core()
    camp()
    laser_tower()
    plasma_sentry()
    relay()
    beam()
