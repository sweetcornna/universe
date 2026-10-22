"""生成 M14（ADR-0026）第一部分的贴图：十一种资源植物（长熟的和收过的两种样子）、十种产物、水培槽。

用法：python tools/m14_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

资源植物照着现实里的原型画，颜色压低一点，和星球上原有的植物放在一起不扎眼：
氧囊草是狸藻那样细茎上挂一串半透明的气囊；盐角草是一节一节肉质的绿茎，梢头发红，上面结着白盐霜；
盐晶簇是长在岩石上的粉白色石盐立方体；星莓丛是带刺的矮灌木，结着淡黄色、四角发亮的浆果；
霜苔是贴地的一圈灰蓝色叶子，抽出几根挂冰晶的细茎；日焰藤是一根扭着往上爬的藤，开橙红到金黄的火焰状花；
伽马草是一丛硬挺的黄绿色剑叶，中间一根发光的穗；毒霉簇是几朵紫灰色的小菌盖，上面一层黄绿的孢子粉；
桶形仙人掌是一个竖棱的绿色圆桶，棱上一排排黄刺，顶上开一圈黄花；引力果丛是深紫色的弯茎，吊着几颗发光的品红色圆果；
洞穴髓球是贴着岩石长的一团奶白色球状菌体，微微发光。
收过的样子：果子、花、气囊、盐晶摘掉，只剩茎叶或者矮矮的一截。

水培槽是一个白色的塑料槽，十格高；侧面一扇小窗能看到里面的营养液，正面一块小面板，通电时一条品红色的补光灯亮起来；
顶面一圈槽沿，中间是营养液和一个装着岩棉的定植杯。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import (BLOCK, ITEM, N, shade, mix, clear, new, put, rect, outline, save, noise, frame)  # noqa: E402


def line(img, x0, y0, x1, y1, color, a=255):
    steps = max(round(abs(x1 - x0)), round(abs(y1 - y0)), 1)
    for i in range(steps + 1):
        t = i / steps
        put(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color, a)


def blob(img, cx, cy, r, color, a=255, highlight=True):
    """实心的圆：左上亮、右下暗，左上角一个高光点。"""
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                lit = ((cx - x) + (cy - y)) / max(r, 0.5)
                put(img, x, y, shade(color, 1 + 0.18 * lit), a)
    if highlight and r >= 1.2:
        put(img, round(cx - r * 0.45), round(cy - r * 0.45), mix(color, (255, 255, 255), 0.6), min(255, a + 30))


def stem(img, x, y0, y1, color, sway=0.0, phase=0.0):
    """从 y0 往上长到 y1 的一根茎，sway 是左右摆动的格数。"""
    for y in range(y1, y0 + 1):
        t = (y0 - y) / max(1, y0 - y1)
        put(img, round(x + sway * math.sin(t * math.pi + phase) * t), y, shade(color, 0.85 + 0.3 * t))


# ---- 资源植物

STEM = (72, 120, 82)
LEAF = (88, 140, 84)


def oxygen_bladderwort(ripe, rng):
    img = clear()
    pods = []
    for x, h, sway in ((4, 9, -1.2), (6, 12, 0.8), (8, 13, -0.6), (10, 11, 1.1), (12, 8, 0.9)):
        top = 15 - (h if ripe else h // 2)
        stem(img, x, 15, top, STEM, sway)
        pods.append((x, top))
        # 细小的叶丝
        for y in range(top + 2, 15, 3):
            put(img, x + (1 if y % 2 else -1), y, shade(LEAF, 0.9), 220)
    if ripe:
        pod = (200, 232, 236)
        for (x, y) in pods:
            blob(img, x, y - 0.5, 1.5, pod, 215)
        for (x, y) in ((5, 9), (9, 7), (11, 9), (7, 11)):
            blob(img, x, y, 1.0, shade(pod, 0.95), 200, highlight=False)
            put(img, x, y, (240, 252, 255), 230)
    else:
        for (x, y) in pods:
            put(img, x, y - 1, shade(STEM, 0.7))
    return img


def saltwort(ripe, rng):
    img = clear()
    green = (104, 152, 76)
    red = (164, 84, 76)
    for x, h in ((3, 7), (5, 11), (7, 13), (9, 12), (11, 10), (13, 7)):
        h = h if ripe else max(3, h // 3)
        for i in range(h):
            y = 15 - i
            t = i / max(1, h - 1)
            c = mix(green, red, max(0.0, t - 0.45) * 1.6) if ripe else shade(green, 0.85)
            # 每两格一个节：节上深一点
            joint = i % 3 == 2
            put(img, x, y, shade(c, 0.72 if joint else 1.0))
            put(img, x + 1, y, shade(c, 0.6 if joint else 0.82))
            if ripe and not joint and rng.random() < 0.22:
                put(img, x + rng.choice((0, 1)), y, (236, 238, 232))
        # 侧枝
        if ripe and h > 8:
            by = 15 - h // 2
            put(img, x - 1, by, shade(green, 0.9))
            put(img, x - 2, by - 1, shade(green, 0.95))
            put(img, x - 2, by - 2, mix(green, red, 0.5))
    return img


def salt_crystal_cluster(ripe, rng):
    img = clear()
    salt = (236, 222, 224)
    # (左边 x, 底边 y, 边长)：后面的先画，前面的盖上去
    if ripe:
        cubes = ((6, 12, 5), (2, 14, 4), (11, 13, 4), (8, 15, 3), (4, 15, 2), (13, 15, 2))
    else:
        cubes = ((3, 15, 1), (7, 15, 2), (11, 15, 1), (9, 15, 1))
    for (x, bottom, s) in cubes:
        # 一块立方体：顶面亮，正面中，右面暗
        for dy in range(s + 1):
            for dx in range(s + 1):
                face = mix(salt, (255, 255, 255), 0.5) if dy == 0 else salt
                if dx == s:
                    face = shade(salt, 0.78)
                put(img, x + dx, bottom - s + dy, shade(face, 1 + rng.uniform(-0.03, 0.03)))
        put(img, x, bottom - s + 1, mix(salt, (255, 230, 236), 0.5))
    # 一点淡粉色的杂质
    for _ in range(4 if ripe else 1):
        x, y = rng.randrange(2, 14), rng.randrange(9, 16)
        if img.getpixel((x, y))[3]:
            put(img, x, y, (226, 186, 196))
    return img


def star_bramble(ripe, rng):
    img = clear()
    bark = (92, 70, 54)
    leaf = (62, 104, 66)
    for x0, x1, top in ((3, 6, 6), (8, 5, 4), (12, 10, 6), (6, 9, 8)):
        line(img, x0, 15, x1, top, bark)
        for t in (0.35, 0.7):
            y = round(15 - (15 - top) * t)
            x = round(x0 + (x1 - x0) * t)
            put(img, x + 1, y, shade(bark, 1.3))  # 刺
    for _ in range(46):
        x, y = rng.randrange(1, 15), rng.randrange(4, 15)
        if math.hypot((x - 7.5) / 7, (y - 10) / 6) < 1:
            put(img, x, y, shade(leaf, rng.uniform(0.8, 1.25)))
    if ripe:
        berry = (244, 226, 128)
        for (x, y) in ((4, 6), (9, 4), (12, 8), (6, 10), (10, 11)):
            put(img, x, y, (255, 250, 214))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(img, x + dx, y + dy, berry)
            for dx, dy in ((1, 1), (-1, -1)):
                put(img, x + dx, y + dy, shade(berry, 0.7), 160)
    return img


def frostwort(ripe, rng):
    img = clear()
    leaf = (150, 176, 186)
    for i in range(9):
        a = math.pi * (0.08 + 0.84 * i / 8)
        for r in range(1, 6):
            x = round(7.5 + math.cos(a) * r * 1.3)
            y = round(15 - math.sin(a) * r * 0.55)
            put(img, x, y, shade(leaf, 1.05 - r * 0.05))
    if ripe:
        ice = (214, 236, 246)
        for x, top in ((5, 4), (8, 2), (11, 5)):
            line(img, x, 13, x + (1 if x < 8 else -1 if x > 8 else 0), top, shade(leaf, 0.9))
            tx = x + (1 if x < 8 else -1 if x > 8 else 0)
            put(img, tx, top - 1, (250, 254, 255))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, -1), (-1, -1)):
                put(img, tx + dx, top + dy, ice, 230)
        for _ in range(8):
            x, y = rng.randrange(3, 13), rng.randrange(11, 16)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (236, 246, 250))
    return img


def solar_vine(ripe, rng):
    img = clear()
    vine = (112, 82, 52)
    leaf = (104, 120, 60)
    pts = []
    for y in range(15, 1, -1):
        x = round(7.5 + 3 * math.sin((15 - y) * 0.55))
        put(img, x, y, vine)
        put(img, x + 1, y, shade(vine, 0.7))
        pts.append((x, y))
    for (x, y) in pts[2::3]:
        side = 1 if x < 8 else -1
        put(img, x + 2 * side, y, leaf)
        put(img, x + 3 * side, y - 1, shade(leaf, 1.15))
        put(img, x + 2 * side, y - 1, shade(leaf, 0.9))
    if ripe:
        for (x, y) in (pts[4], pts[8], pts[12]):
            side = -1 if x < 8 else 1
            fx = x + 2 * side
            # 火焰状的花：底下红，往上橙、金黄
            for i, c in enumerate(((196, 52, 32), (232, 104, 36), (246, 172, 52), (252, 226, 120))):
                put(img, fx, y - i + 1, c)
                if i < 2:
                    put(img, fx - side, y - i + 1, shade(c, 0.85))
            put(img, fx + side, y, (216, 76, 36))
    return img


def gamma_weed(ripe, rng):
    img = clear()
    blade = (138, 156, 62)
    for x0, x1, top in ((2, 1, 6), (4, 3, 3), (6, 6, 5), (9, 10, 4), (11, 13, 2), (13, 14, 7)):
        top = top if ripe else top + 6
        line(img, x0 + 1, 15, x1 + 1, top, shade(blade, rng.uniform(0.85, 1.1)))
        put(img, x1 + 1, top, shade(blade, 1.25))
    if ripe:
        glow = (208, 246, 92)
        for y in range(1, 9):
            put(img, 8, y, mix(glow, (255, 255, 200), 0.4) if y < 3 else glow)
            if 2 < y < 8:
                put(img, 7 if y % 2 else 9, y, shade(glow, 0.85))
        line(img, 8, 9, 8, 15, shade(blade, 0.8))
    return img


def fungal_cluster(ripe, rng):
    img = clear()
    stalk = (196, 184, 176)
    cap = (136, 104, 140)
    spore = (190, 212, 84)
    caps = ((4, 9, 3), (9, 6, 4), (12, 10, 2), (7, 11, 2))
    for (x, y, r) in caps:
        top = y if ripe else 15 - (15 - y) // 3
        line(img, x, 15, x, top + 1, stalk)
        if ripe:
            for dx in range(-r, r + 1):
                put(img, x + dx, top, shade(cap, 1.1 - abs(dx) * 0.06))
                if abs(dx) < r:
                    put(img, x + dx, top - 1, shade(cap, 1.25 - abs(dx) * 0.06))
            put(img, x - r, top + 1, shade(cap, 0.7))
            put(img, x + r, top + 1, shade(cap, 0.7))
            for _ in range(r):
                put(img, x + rng.randint(-r + 1, r - 1), top - 1, spore)
        else:
            put(img, x, top, shade(stalk, 0.8))
    if ripe:
        for _ in range(5):
            put(img, rng.randrange(2, 14), rng.randrange(1, 6), spore, 150)
    return img


def echinocactus(ripe, rng):
    img = clear()
    body = (74, 128, 76)
    flesh = (190, 208, 150)
    r = 5.6 if ripe else 4.6
    cy = 15 - r + 0.6
    for y in range(N):
        for x in range(N):
            dx, dy = (x - 7.5) / (r + 0.6), (y - cy) / r
            if dx * dx + dy * dy <= 1 and y <= 15:
                rib = (x % 3) == 0
                c = shade(body, 0.7 if rib else 1.0 + 0.12 * (-dy))
                put(img, x, y, c)
                if rib and (y % 2 == 0):
                    put(img, x, y, (226, 206, 132))  # 刺座
    top = round(cy - r)
    if ripe:
        for x in range(5, 11):
            put(img, x, top, (226, 214, 180))  # 顶上的绒毛
        for x in (5, 7, 8, 10):
            put(img, x, top - 1, (240, 204, 62))
        put(img, 6, top - 2, (250, 226, 96))
        put(img, 9, top - 2, (250, 226, 96))
    else:
        # 切掉了一块，露出淡色的肉
        for x in range(4, 12):
            put(img, x, top + 1, flesh)
            put(img, x, top, shade(flesh, 1.1))
    return img


def gravitino_host(ripe, rng):
    img = clear()
    stalk = (70, 46, 88)
    for x0, x1, top, bend in ((5, 3, 4, -1), (8, 9, 2, 1), (11, 13, 5, 1)):
        for i in range(16):
            t = i / 15
            y = round(15 - (15 - top) * t)
            x = round(x0 + (x1 - x0) * t + bend * math.sin(t * math.pi) * 1.5)
            put(img, x, y, shade(stalk, 0.9 + 0.3 * t))
        # 梢头往下勾，吊着果子
        put(img, x1 + bend, top + 1, stalk)
        if ripe:
            blob(img, x1 + bend, top + 3.2, 1.7, (236, 112, 220))
            put(img, x1 + bend, top + 3, (255, 214, 250))
        else:
            put(img, x1 + bend, top + 2, shade(stalk, 1.4))
            put(img, x1 + bend - 1, top + 2, shade(stalk, 1.2))
            put(img, x1 + bend + 1, top + 2, shade(stalk, 1.2))
    return img


def cave_marrow(ripe, rng):
    img = clear()
    bulb = (222, 212, 172)
    root = (150, 132, 104)
    if ripe:
        for (x, y, r) in ((5, 12, 2.6), (10, 11.5, 3.0), (7.5, 8.5, 2.2), (12.5, 13.5, 1.6), (3, 14, 1.4)):
            blob(img, x, y, r, bulb)
        for _ in range(6):
            x, y = rng.randrange(2, 14), rng.randrange(7, 16)
            if img.getpixel((x, y))[3]:
                put(img, x, y, (246, 240, 204))
    else:
        for x in (4, 7, 10, 12):
            line(img, x, 15, x + rng.choice((-1, 0, 1)), 12, root)
        put(img, 7, 11, shade(bulb, 0.85))
    return img


PLANTS = {
    "oxygen_bladderwort": oxygen_bladderwort,
    "saltwort": saltwort,
    "salt_crystal_cluster": salt_crystal_cluster,
    "star_bramble": star_bramble,
    "frostwort": frostwort,
    "solar_vine": solar_vine,
    "gamma_weed": gamma_weed,
    "fungal_cluster": fungal_cluster,
    "echinocactus": echinocactus,
    "gravitino_host": gravitino_host,
    "cave_marrow": cave_marrow,
}


# ---- 产物

def item_oxygen():
    img = clear()
    pod = (190, 228, 236)
    blob(img, 6, 9, 3.6, pod, 225)
    blob(img, 10.5, 6, 2.6, shade(pod, 1.04), 225)
    blob(img, 10.5, 11.5, 2.0, shade(pod, 0.96), 225)
    line(img, 8, 13, 12, 15, (72, 120, 82))
    return img


def item_sodium():
    img = clear()
    salt = (238, 230, 182)
    for (x, y, s) in ((3, 8, 5), (8, 5, 4), (9, 10, 4)):
        rect(img, x, y, x + s, y + s, salt)
        for i in range(s + 1):
            put(img, x + i, y, mix(salt, (255, 255, 255), 0.5))
            put(img, x + s, y + i, shade(salt, 0.76))
    return img


def item_star_bulb():
    img = clear()
    berry = (244, 222, 116)
    blob(img, 7.5, 8.5, 4.2, berry)
    for dx, dy in ((0, -6), (0, 6), (-6, 0), (6, 0)):
        line(img, 7.5 + dx * 0.55, 8.5 + dy * 0.55, 7.5 + dx * 0.95, 8.5 + dy * 0.95, mix(berry, (255, 255, 230), 0.4))
    put(img, 6, 7, (255, 252, 224))
    line(img, 8, 3, 10, 1, (92, 70, 54))
    return img


def item_frost_crystal():
    img = clear()
    ice = (198, 228, 244)
    for x0, y0, x1, y1, w in ((7, 15, 6, 2, 2), (5, 14, 2, 6, 1), (9, 14, 13, 5, 1)):
        for o in range(w + 1):
            line(img, x0 + o, y0, x1 + o, y1, shade(ice, 1.0 - 0.12 * o))
        put(img, x1, y1, (250, 254, 255))
    return img


def item_solanium():
    img = clear()
    for i, c in enumerate(((150, 40, 30), (196, 56, 32), (230, 100, 36), (244, 158, 48), (250, 210, 92), (254, 238, 160))):
        r = 4.2 - i * 0.55
        blob(img, 7.5, 10.5 - i * 1.2, r, c, highlight=False)
    line(img, 7, 15, 9, 13, (104, 120, 60))
    return img


def item_gamma_root():
    img = clear()
    root = (176, 200, 76)
    for i in range(12):
        x = round(4 + i * 0.7 + math.sin(i * 0.9) * 1.4)
        y = 14 - i
        put(img, x, y, shade(root, 0.9 + 0.02 * i))
        put(img, x + 1, y, shade(root, 0.72))
    for (x, y) in ((3, 12), (11, 4), (6, 8)):
        put(img, x, y, (226, 252, 140))
    return img


def item_fungal_mould():
    img = clear()
    rng = random.Random(1411)
    cap = (136, 104, 140)
    blob(img, 7.5, 9, 4.6, cap)
    for _ in range(14):
        x, y = rng.randrange(3, 13), rng.randrange(5, 14)
        if img.getpixel((x, y))[3]:
            put(img, x, y, (190, 212, 84) if rng.random() < 0.6 else shade(cap, 1.3))
    return img


def item_cactus_flesh():
    img = clear()
    flesh = (190, 208, 150)
    skin = (74, 128, 76)
    for y in range(4, 14):
        for x in range(3, 13):
            if abs(x - 7.5) + abs(y - 8.5) * 0.8 < 6:
                put(img, x, y, shade(flesh, 1 + (8.5 - y) * 0.02))
    for x in range(3, 13):
        for y in range(4, 14):
            if img.getpixel((x, y))[3]:
                put(img, x, y, skin)
                if x % 3 == 0:
                    put(img, x, y - 1, (226, 206, 132))
                break
    return img


def item_gravitino_ball():
    img = clear()
    blob(img, 7.5, 8, 5, (226, 100, 214))
    for i in range(10):
        a = i * 0.7
        put(img, round(7.5 + math.cos(a) * i * 0.4), round(8 + math.sin(a) * i * 0.4), (255, 206, 250))
    return img


def item_marrow_bulb():
    img = clear()
    bulb = (222, 212, 172)
    blob(img, 7, 9.5, 4.6, bulb)
    blob(img, 11, 6, 2.2, shade(bulb, 1.04))
    line(img, 6, 14, 4, 15, (150, 132, 104))
    return img


ITEMS = {
    "oxygen": item_oxygen,
    "sodium": item_sodium,
    "star_bulb": item_star_bulb,
    "frost_crystal": item_frost_crystal,
    "solanium": item_solanium,
    "gamma_root": item_gamma_root,
    "fungal_mould": item_fungal_mould,
    "cactus_flesh": item_cactus_flesh,
    "gravitino_ball": item_gravitino_ball,
    "marrow_bulb": item_marrow_bulb,
}


# ---- 水培槽

PLASTIC = (206, 210, 212)
WATER = (52, 104, 118)


def tray_side(rng):
    img = new(PLASTIC)
    noise(img, rng, 0.025)
    # 模型只有下面十格高：上面六行用不到，填同色
    for x in range(N):
        put(img, x, 6, shade(PLASTIC, 1.12))
        put(img, x, 7, shade(PLASTIC, 0.86))
        put(img, x, 15, shade(PLASTIC, 0.62))
    for y in range(6, N):
        put(img, 0, y, shade(PLASTIC, 1.08))
        put(img, N - 1, y, shade(PLASTIC, 0.7))
    return img


def tray_textures():
    rng = random.Random(1401)
    side = tray_side(rng)
    # 侧面一扇小窗，看得到营养液的液位
    window = side.copy()
    rect(window, 3, 9, 12, 13, (60, 66, 70))
    rect(window, 4, 10, 11, 12, mix(WATER, PLASTIC, 0.15))
    for x in range(4, 12):
        put(window, x, 10, mix(WATER, (170, 220, 230), 0.45))
    save(window, BLOCK, "hydroponic_tray_side")
    for suffix, lit in (("", False), ("_on", True)):
        front = side.copy()
        # 补光灯条：通电时品红色（红光加蓝光）
        for x in range(2, 14):
            put(front, x, 9, (232, 84, 196) if lit else (96, 70, 92))
            put(front, x, 10, (176, 60, 152) if lit else (72, 56, 70))
        # 控制面板：一块小屏和一盏状态灯
        rect(front, 3, 12, 8, 14, (40, 46, 52))
        for x in range(4, 8):
            put(front, x, 13, (110, 220, 140) if lit else (50, 80, 62))
        put(front, 11, 13, (120, 240, 120) if lit else (90, 50, 46))
        put(front, 12, 13, shade((120, 240, 120) if lit else (90, 50, 46), 0.7))
        save(front, BLOCK, "hydroponic_tray_front" + suffix)
    top = new(WATER)
    noise(top, rng, 0.06)
    for y in range(N):
        for x in range(N):
            if (x * 3 + y * 5) % 11 == 0:
                put(top, x, y, mix(WATER, (150, 210, 220), 0.35))  # 水面反光
    # 一圈槽沿
    for i in range(N):
        for w in range(2):
            put(top, i, w, shade(PLASTIC, 1.08 - w * 0.12))
            put(top, i, N - 1 - w, shade(PLASTIC, 0.86 + w * 0.06))
            put(top, w, i, shade(PLASTIC, 1.04 - w * 0.1))
            put(top, N - 1 - w, i, shade(PLASTIC, 0.86 + w * 0.06))
    # 定植杯和岩棉
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.6 <= d < 3.6:
                put(top, x, y, (54, 58, 62))
            elif d < 2.6:
                put(top, x, y, shade((196, 176, 128), 1 + rng.uniform(-0.08, 0.08)))
    save(top, BLOCK, "hydroponic_tray_top")


def main():
    for name, draw in PLANTS.items():
        for ripe in (True, False):
            img = draw(ripe, random.Random(sum(map(ord, name)) * 7 + ripe))
            save(img, BLOCK, name if ripe else name + "_harvested")
    for name, draw in ITEMS.items():
        img = draw()
        outline(img, (34, 30, 36))
        save(img, ITEM, name)
    tray_textures()


if __name__ == "__main__":
    main()
