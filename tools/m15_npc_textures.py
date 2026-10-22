"""生成外星 NPC（ADR-0027 §一）三个种族的皮肤和发光层。

用法：python tools/m15_npc_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity：
alien_npc_trader.png / _glow.png（64×64）、alien_npc_scholar.png / _glow.png（64×64）、alien_npc_warden.png / _glow.png（128×64）。

- 方块列表和 client/civ/AlienNpcModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 商旅族：琥珀色的羽毛（鱼鳞一样一排排）、奶白的脸、象牙色的喙、红到黄的头冠；穿青绿色的袍子，金边，斜挎一条带子，别着金徽章，
  腰上一个皮挎包；腿和脚是橙黄的鳞。
- 学者族：淡蓝灰的皮肤和斑点、两侧的鳃缝；背上和头上是深蓝的壳，一块块六角甲片，甲片中间泛亮；靛青长袍，前襟和下摆是银蓝的文字。
- 戍卫族：枪灰色的甲，红橙色的珐琅（胸甲、肩甲、护膝、盔冠），铆钉、接缝；面罩一道橙色的光缝，下颌两根骨白的獠牙。
- 发光层画成带颜色的：商旅族的眼睛，学者族的眼睛、触须尖、文字和壳脊上的光点，戍卫族的面罩和背甲的通风口。
随机数用固定种子，每次生成的结果一样。面的展开和 Skin 借用 combat_textures.py。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402

ENTITY = sys.argv[1] if len(sys.argv) > 1 else ct.ENTITY


# ---- 通用的画法

def px(s, rect, i, j, color):
    """面里的第 (i, j) 格（从左上角数）。"""
    x0, y0, w, h = rect
    if 0 <= i < w and 0 <= j < h:
        s.put(x0 + i, y0 + j, color)


def glow(s, rect, i, j, color, strength=1.0):
    x0, y0, w, h = rect
    if 0 <= i < w and 0 <= j < h:
        s.glow.putpixel((x0 + i, y0 + j), tuple(color[:3]) + (ct.clamp(255 * strength),))


def fill(s, rect, color, noise=0.04):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, ct.shade(color, 1 + s.rng.uniform(-noise, noise)))


def gradient(s, rect, top, bottom, noise=0.04):
    """从上往下渐变；顶上一行亮一点，底下一行暗一点，像有体积。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        t = (y - y0) / max(1, h - 1)
        base = ct.mix(top, bottom, t)
        for x in range(x0, x0 + w):
            f = 1 + s.rng.uniform(-noise, noise)
            if h > 2 and y == y0:
                f *= 1.08
            elif h > 2 and y == y0 + h - 1:
                f *= 0.86
            s.put(x, y, ct.shade(base, f))


def feathers(s, rect, base, dark, light, offset=0):
    """鱼鳞一样一排排的羽毛：每排 3 格宽、2 格高，排与排错开半片，片的下沿深、上沿亮。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        row = (y - y0 + offset) // 2
        top = (y - y0 + offset) % 2 == 0
        for x in range(x0, x0 + w):
            k = (x - x0 + (row % 2) * 2) % 3
            f = 1 + s.rng.uniform(-0.05, 0.05)
            if not top and k == 0:
                c = dark
            elif top and k == 1:
                c = light
            else:
                c = base
            s.put(x, y, ct.shade(c, f))


def scales(s, rect, base, dark):
    """菱形的鳞（腿、脚）。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = (x - x0 + y - y0) % 3 == 0 or (x - x0 - (y - y0)) % 3 == 0
            s.put(x, y, ct.shade(dark if edge else base, 1 + s.rng.uniform(-0.05, 0.05)))


def border(s, rect, color, sides="tblr"):
    x0, y0, w, h = rect
    if "t" in sides:
        s.hline(x0, x0 + w, y0, color)
    if "b" in sides:
        s.hline(x0, x0 + w, y0 + h - 1, color)
    if "l" in sides:
        s.vline(x0, y0, y0 + h, color)
    if "r" in sides:
        s.vline(x0 + w - 1, y0, y0 + h, color)


def each(f, keys, fn):
    for key in keys:
        fn(f[key])


SIDES = ("front", "back", "left", "right")


# ---- 商旅族

AMBER = (232, 176, 74)
AMBER_DARK = (172, 112, 42)
AMBER_LIGHT = (252, 214, 132)
# 羽片的下沿：比 AMBER_DARK 浅，鱼鳞纹看得出来又不会花成斑点
FEATHER_EDGE = (204, 146, 60)
CREAM = (244, 226, 180)
IVORY = (238, 206, 140)
BEAK_TIP = (150, 92, 44)
TEAL = (38, 116, 128)
TEAL_LIGHT = (74, 164, 172)
TEAL_DARK = (22, 70, 80)
GOLD = (230, 186, 82)
GOLD_LIGHT = (255, 232, 150)
LEATHER = (128, 80, 46)
LEG = (214, 148, 56)
LEG_DARK = (150, 96, 34)
CLAW = (64, 46, 36)
EYE_GLOW = (90, 236, 226)


def trader():
    s = ct.Skin(64, 64, 2715)
    # 脑袋 7×7×7
    f = ct.faces(0, 0, 7, 7, 7)
    each(f, ("back", "left", "right", "top"), lambda r: feathers(s, r, AMBER, FEATHER_EDGE, AMBER_LIGHT))
    fill(s, f["bottom"], CREAM)
    # 脸：奶白的面盘，上沿一圈羽毛，两只大眼睛，眉骨深一点
    front = f["front"]
    feathers(s, front, AMBER, FEATHER_EDGE, AMBER_LIGHT)
    for j in range(1, 7):
        for i in range(7):
            if j >= 2 or i in (0, 3, 6):
                px(s, front, i, j, ct.shade(CREAM, 1 + s.rng.uniform(-0.03, 0.03) - 0.03 * (j == 6)))
    for i in (1, 2, 4, 5):
        px(s, front, i, 0, ct.shade(AMBER_DARK, 0.9))
    for ex in (1, 4):
        outer = ex == 1
        # 眼睛 2×2：外上角是高光，内下角是深色的瞳孔，另外两格是青色的虹膜（发光）
        hi, pupil = ((ex, 1), (ex + 1, 2)) if outer else ((ex + 1, 1), (ex, 2))
        for i in (ex, ex + 1):
            for j in (1, 2):
                if (i, j) == hi:
                    px(s, front, i, j, (236, 252, 250))
                elif (i, j) == pupil:
                    px(s, front, i, j, (18, 26, 36))
                else:
                    px(s, front, i, j, (40, 168, 176) if j == 1 else (30, 128, 146))
                    glow(s, front, i, j, EYE_GLOW, 0.85)
    # 喙下面的面盘深一点（被喙挡住的地方）
    for i in (2, 3, 4):
        for j in (3, 4):
            px(s, front, i, j, ct.shade(IVORY, 0.8))
    # 两颊一点红晕，下巴的羽毛
    px(s, front, 0, 4, (232, 150, 110))
    px(s, front, 6, 4, (232, 150, 110))
    for i in range(7):
        px(s, front, i, 6, ct.shade(CREAM, 0.88))
    # 头顶正中一道红羽（头冠的根）
    top = f["top"]
    for j in range(7):
        px(s, top, 3, j, (214, 92, 46))
    # 两边耳羽的根
    for key in ("left", "right"):
        r = f[key]
        for j in (1, 2):
            for i in (3, 4, 5):
                px(s, r, i, j, ct.shade(AMBER_DARK, 0.85))

    # 上喙 3×2×4、下喙 2×1×3
    f = ct.faces(28, 0, 3, 2, 4)
    each(f, ("left", "right"), lambda r: gradient(s, r, IVORY, ct.shade(IVORY, 0.82)))
    fill(s, f["top"], ct.shade(IVORY, 1.06))
    fill(s, f["bottom"], ct.shade(IVORY, 0.7))
    gradient(s, f["front"], ct.shade(IVORY, 1.02), ct.shade(IVORY, 0.85))
    px(s, f["front"], 1, 1, BEAK_TIP)
    fill(s, f["back"], ct.shade(IVORY, 0.75))
    for key in ("left", "right"):
        r = f[key]
        # 鼻孔，喙尖那一头深
        px(s, r, 1, 0, (88, 60, 40))
        for j in range(2):
            px(s, r, 0 if key == "right" else 3, j, ct.shade(BEAK_TIP, 1.1))
    top = f["top"]
    for i in range(3):
        px(s, top, i, 0, ct.shade(BEAK_TIP, 1.15))
    f = ct.faces(28, 6, 2, 1, 3)
    for key, r in f.items():
        fill(s, r, ct.shade(IVORY, 0.78))
    fill(s, f["front"], ct.shade(BEAK_TIP, 0.9))

    # 头冠 1×3×6：根上深红，往上、往后变成金黄
    f = ct.faces(42, 0, 1, 3, 6)
    for key in ("left", "right"):
        x0, y0, w, h = f[key]
        for y in range(h):
            for x in range(w):
                t = 1 - y / (h - 1)
                c = ct.mix((196, 70, 40), (252, 206, 96), t * 0.85)
                if (x + (1 if key == "left" else 0)) % 2 == 0:
                    c = ct.shade(c, 0.88)
                s.put(x0 + x, y0 + y, c)
    each(f, ("front", "back", "bottom"), lambda r: fill(s, r, (196, 74, 40)))
    fill(s, f["top"], (252, 210, 104))
    # 耳羽 1×2×3
    f = ct.faces(56, 0, 1, 2, 3)
    for key, r in f.items():
        gradient(s, r, AMBER, (200, 84, 44))

    # 身子 8×7×5：胸前的羽毛，一条青绿的斜挎带、金边，带上一枚金徽章
    f = ct.faces(0, 16, 8, 7, 5)
    each(f, ("front", "back", "left", "right", "top"), lambda r: feathers(s, r, AMBER, FEATHER_EDGE, AMBER_LIGHT, 1))
    fill(s, f["bottom"], CREAM)
    for i in range(1, 7):
        for j in range(5):
            px(s, f["front"], i, j + 1, ct.shade(CREAM, 0.96 + s.rng.uniform(-0.03, 0.03)))
    for key, flip in (("front", False), ("back", True)):
        r = f[key]
        for j in range(7):
            i = j if not flip else 7 - j
            for d in (-1, 0, 1):
                k = i + d
                c = TEAL if d == 0 else GOLD
                px(s, r, k, j, ct.shade(c, 1 + s.rng.uniform(-0.04, 0.04)))
    badge = f["front"]
    px(s, badge, 3, 3, GOLD_LIGHT)
    px(s, badge, 4, 3, GOLD)
    px(s, badge, 3, 4, GOLD)
    px(s, badge, 4, 4, ct.shade(GOLD, 0.7))
    for j in range(2):
        px(s, f["left"], 2, j, TEAL)
        px(s, f["right"], 2, 6 - j, TEAL)

    # 袍子的下摆 9×3×6：青绿，中间一行菱形花纹，下沿金边
    f = ct.faces(0, 28, 9, 3, 6)
    for key in SIDES:
        x0, y0, w, h = f[key]
        for y in range(h):
            for x in range(w):
                c = TEAL
                if y == 1 and x % 3 == 1:
                    c = TEAL_LIGHT
                if y == h - 1:
                    c = GOLD if x % 2 == 0 else ct.shade(GOLD, 0.82)
                s.put(x0 + x, y0 + y, ct.shade(c, 1 + s.rng.uniform(-0.04, 0.04)))
    fill(s, f["top"], TEAL_DARK)
    fill(s, f["bottom"], TEAL_DARK)
    # 挎包 2×3×3：皮的，盖子深一点，正面一个金扣
    f = ct.faces(26, 16, 2, 3, 3)
    for key, r in f.items():
        gradient(s, r, ct.shade(LEATHER, 1.1), ct.shade(LEATHER, 0.8))
    for key in SIDES:
        x0, y0, w, h = f[key]
        s.hline(x0, x0 + w, y0, ct.shade(LEATHER, 0.65))
    px(s, f["left"], 1, 1, GOLD_LIGHT)
    px(s, f["right"], 1, 1, GOLD_LIGHT)

    # 胳膊 2×6×2：羽毛，手是深色的三爪
    f = ct.faces(36, 10, 2, 6, 2)
    each(f, SIDES + ("top",), lambda r: feathers(s, r, AMBER, FEATHER_EDGE, AMBER_LIGHT))
    fill(s, f["bottom"], CLAW)
    for key in SIDES:
        x0, y0, w, h = f[key]
        s.hline(x0, x0 + w, y0 + h - 1, ct.shade(LEG, 0.9))
        px(s, f[key], 0, h - 1, CLAW)
    # 飞羽 1×6×3：一根根竖着的长羽，羽尖褐色
    f = ct.faces(44, 10, 1, 6, 3)
    for key, r in f.items():
        x0, y0, w, h = r
        for y in range(h):
            for x in range(w):
                c = AMBER if x % 2 == 0 else AMBER_DARK
                if y >= h - 2:
                    c = ct.mix(c, (120, 70, 36), 0.6 + 0.2 * (y == h - 1))
                s.put(x0 + x, y0 + y, ct.shade(c, 1 + s.rng.uniform(-0.04, 0.04)))

    # 腿 2×6×2：上面两格是羽毛的"裤腿"，下面是鳞
    f = ct.faces(52, 10, 2, 6, 2)
    for key in SIDES:
        x0, y0, w, h = f[key]
        scales(s, (x0, y0 + 2, w, h - 2), LEG, LEG_DARK)
        feathers(s, (x0, y0, w, 2), AMBER, FEATHER_EDGE, AMBER_LIGHT)
    fill(s, f["top"], AMBER_DARK)
    fill(s, f["bottom"], LEG_DARK)
    # 脚 3×1×4：三根脚趾，趾尖深色的爪
    f = ct.faces(30, 28, 3, 1, 4)
    top = f["top"]
    for j in range(4):
        for i in range(3):
            c = LEG if i != 1 or j > 0 else ct.shade(LEG, 1.08)
            if j == 0:
                c = CLAW
            elif (i + j) % 2 == 1:
                c = ct.shade(LEG, 0.86)
            px(s, top, i, j, c)
    fill(s, f["bottom"], LEG_DARK)
    for key in SIDES:
        fill(s, f[key], LEG)
    for i in (0, 2):
        px(s, f["front"], i, 0, CLAW)
    ct.save(s.img, ENTITY, "alien_npc_trader")
    ct.save(s.glow, ENTITY, "alien_npc_trader_glow")


# ---- 学者族

SKIN = (152, 192, 214)
SKIN_DARK = (108, 146, 172)
SHELL = (46, 80, 134)
SHELL_DARK = (24, 40, 74)
SHELL_LIGHT = (104, 168, 218)
ROBE = (60, 52, 112)
ROBE_DARK = (38, 32, 74)
GLYPH = (172, 214, 242)
SCHOLAR_GLOW = (120, 220, 255)


def mottle(s, rect, base, dark, density=0.18):
    """皮肤上的斑点。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = dark if s.rng.random() < density else base
            s.put(x, y, ct.shade(c, 1 + s.rng.uniform(-0.04, 0.04)))


def hex_plates(s, rect, base, dark, light, size=3, phase=0):
    """一块块六角形（近似）的甲片：缝是深色的，片的中间泛亮，越往下越暗。"""
    x0, y0, w, h = rect
    for y in range(h):
        row = (y + phase) // size
        shift = (row % 2) * (size // 2 + 1)
        for x in range(w):
            lx = (x + shift) % (size + 1)
            ly = (y + phase) % size
            t = y / max(1, h - 1)
            c = ct.mix(base, ct.shade(base, 0.7), t)
            if lx == size or ly == size - 1:
                c = dark
            elif lx == size // 2 and ly == size // 2 - (1 if size > 3 else 0):
                c = ct.mix(c, light, 0.75)
            s.put(x0 + x, y0 + y, ct.shade(c, 1 + s.rng.uniform(-0.04, 0.04)))


def glyph_band(s, rect, base, ink, rows, strength, vertical=False):
    """一条外星文字：银蓝色的笔画，按固定的几种字形排，发光层淡淡地亮。"""
    shapes = ((1, 0, 1, 1, 1, 0), (1, 1, 0, 0, 1, 1), (0, 1, 1, 1, 0, 1), (1, 0, 0, 1, 1, 1), (1, 1, 1, 0, 0, 1))
    x0, y0, w, h = rect
    cells = []
    if vertical:
        for y in range(h):
            for x in range(w):
                cells.append((x, y, (y // 2) % len(shapes), (x % 2) + (y % 2) * 2))
    else:
        for y in rows:
            for x in range(w):
                cells.append((x, y, (x // 3) % len(shapes), (x % 3) + (y - rows[0]) * 3))
    for x, y, shape, bit in cells:
        on = shapes[shape][bit % 6]
        c = ink if on else base
        s.put(x0 + x, y0 + y, ct.shade(c, 1 + s.rng.uniform(-0.03, 0.03)))
        if on:
            s.glow.putpixel((x0 + x, y0 + y), SCHOLAR_GLOW + (ct.clamp(255 * strength),))


def scholar():
    s = ct.Skin(64, 64, 4127)
    # 头 5×7×5：淡蓝灰的皮肤，脸上三只眼（额头一只小的），两侧鳃缝
    f = ct.faces(0, 0, 5, 7, 5)
    for key, r in f.items():
        mottle(s, r, SKIN, SKIN_DARK, 0.12)
    front = f["front"]
    for i in range(5):
        px(s, front, i, 0, ct.shade(SKIN, 0.85))
    # 额头的小眼
    px(s, front, 2, 1, (230, 220, 255))
    glow(s, front, 2, 1, (200, 170, 255), 0.9)
    # 两只眼：深色的眼窝，一格发光的瞳孔
    for ex in (0, 3):
        for i in (ex, ex + 1):
            for j in (2, 3):
                px(s, front, i, j, (28, 40, 58))
        pupil = ex + 1 if ex == 0 else ex
        px(s, front, pupil, 2, (170, 240, 255))
        glow(s, front, pupil, 2, SCHOLAR_GLOW, 1.0)
        px(s, front, pupil, 3, (60, 140, 170))
        glow(s, front, pupil, 3, SCHOLAR_GLOW, 0.45)
    # 颧骨的棱、嘴边
    for i in range(5):
        px(s, front, i, 4, ct.shade(SKIN, 1.1))
    for i in (1, 2, 3):
        px(s, front, i, 6, ct.shade(SKIN_DARK, 0.8))
    for key in ("left", "right"):
        r = f[key]
        for i in (1, 2, 3):
            for j in (3, 4, 5):
                px(s, r, i, j, ct.shade(SKIN_DARK, 0.7) if i % 2 == 1 else ct.shade(SKIN, 1.05))
    # 壳盖 6×3×7
    f = ct.faces(20, 0, 6, 3, 7)
    for key, r in f.items():
        hex_plates(s, r, SHELL, SHELL_DARK, SHELL_LIGHT, 3, 1)
    for key in SIDES:
        x0, y0, w, h = f[key]
        s.hline(x0, x0 + w, y0 + h - 1, ct.shade(SHELL_LIGHT, 0.9))
    # 触须 1×3×1：深色的杆，尖上发光
    f = ct.faces(46, 0, 1, 3, 1)
    for key, r in f.items():
        gradient(s, r, (70, 100, 140), (36, 54, 84))
    fill(s, f["top"], (190, 246, 255))
    glow(s, f["top"], 0, 0, SCHOLAR_GLOW, 1.0)
    for key in SIDES:
        px(s, f[key], 0, 0, (150, 230, 250))
        glow(s, f[key], 0, 0, SCHOLAR_GLOW, 0.8)
    # 口器 3×2×1
    f = ct.faces(50, 0, 3, 2, 1)
    for key, r in f.items():
        gradient(s, r, (96, 118, 140), (54, 70, 90))
    px(s, f["front"], 1, 1, (30, 36, 48))

    # 身子 5×10×4：靛青长袍，前襟一条银蓝文字，领口亮一点，腰上一道银带
    f = ct.faces(0, 16, 5, 10, 4)
    for key in SIDES:
        gradient(s, f[key], ROBE, ROBE_DARK)
        x0, y0, w, h = f[key]
        s.hline(x0, x0 + w, y0, ct.shade(ROBE, 1.3))
        s.hline(x0, x0 + w, y0 + 7, ct.shade(GLYPH, 0.85))
    fill(s, f["top"], SKIN_DARK)
    fill(s, f["bottom"], ROBE_DARK)
    x0, y0, w, h = f["front"]
    glyph_band(s, (x0 + 2, y0 + 1, 1, 6), ROBE, GLYPH, None, 0.5, vertical=True)
    for j in range(1, 7):
        px(s, f["front"], 1, j, ct.shade(ROBE, 0.8))
        px(s, f["front"], 3, j, ct.shade(ROBE, 0.8))
    # 背上的壳 7×12×3：大块的六角甲片，背面亮、贴着身子的那面暗
    f = ct.faces(18, 16, 7, 12, 3)
    hex_plates(s, f["back"], (56, 96, 156), SHELL_DARK, SHELL_LIGHT, 4)
    each(f, ("left", "right", "top"), lambda r: hex_plates(s, r, SHELL, SHELL_DARK, SHELL_LIGHT, 3))
    fill(s, f["front"], (30, 40, 62))
    fill(s, f["bottom"], SHELL_DARK)
    for key in ("left", "right"):
        x0, y0, w, h = f[key]
        s.vline(x0 + (w - 1 if key == "left" else 0), y0, y0 + h, ct.shade(SHELL_LIGHT, 0.8))
    # 壳脊 1×12×1：一节节的，每三节一个光点
    f = ct.faces(38, 16, 1, 12, 1)
    for key, r in f.items():
        x0, y0, w, h = r
        for y in range(h):
            c = (122, 182, 232) if y % 3 != 2 else (40, 64, 104)
            s.put(x0, y0 + y, c)
            if y % 3 == 0 and key in SIDES:
                s.glow.putpixel((x0, y0 + y), SCHOLAR_GLOW + (150,))

    # 长袍的下摆 7×6×5：靛青，下沿两行文字
    f = ct.faces(20, 32, 7, 6, 5)
    for key in SIDES:
        gradient(s, f[key], ct.shade(ROBE, 1.05), ROBE_DARK)
        glyph_band(s, f[key], ROBE_DARK, GLYPH, (4, 5), 0.4)
    fill(s, f["top"], ROBE)
    fill(s, f["bottom"], ROBE_DARK)

    # 腿 2×13×2：一节节的，关节深色
    f = ct.faces(0, 32, 2, 13, 2)
    for key in SIDES:
        x0, y0, w, h = f[key]
        gradient(s, f[key], SKIN, SKIN_DARK, 0.05)
        for y in (3, 7, 11):
            s.hline(x0, x0 + w, y0 + y, ct.shade(SKIN_DARK, 0.7))
    fill(s, f["top"], SKIN_DARK)
    fill(s, f["bottom"], (50, 70, 90))
    f = ct.faces(8, 32, 3, 1, 3)
    for key, r in f.items():
        fill(s, r, (54, 74, 96))
    # 胳膊 2×13×2：上半截是袖子（银边袖口），下面是皮肤
    f = ct.faces(44, 32, 2, 13, 2)
    for key in SIDES:
        x0, y0, w, h = f[key]
        gradient(s, (x0, y0, w, 6), ROBE, ROBE_DARK)
        s.hline(x0, x0 + w, y0 + 6, GLYPH)
        gradient(s, (x0, y0 + 7, w, h - 7), SKIN, SKIN_DARK)
        s.hline(x0, x0 + w, y0 + 10, ct.shade(SKIN_DARK, 0.75))
    fill(s, f["top"], ROBE)
    fill(s, f["bottom"], SKIN_DARK)
    # 爪 2×2×1
    f = ct.faces(52, 32, 2, 2, 1)
    for key, r in f.items():
        gradient(s, r, (70, 96, 120), (36, 50, 66))
    ct.save(s.img, ENTITY, "alien_npc_scholar")
    ct.save(s.glow, ENTITY, "alien_npc_scholar_glow")


# ---- 戍卫族

GUN = (76, 80, 90)
GUN_DARK = (44, 46, 54)
GUN_LIGHT = (128, 134, 148)
ENAMEL = (206, 94, 66)
ENAMEL_LIGHT = (242, 146, 104)
ENAMEL_DARK = (132, 52, 40)
SUIT = (54, 42, 46)
BELT = (98, 66, 44)
BONE = (232, 222, 198)
VISOR = (255, 150, 64)


def armor(s, rect, base, seams=4, rivets=True):
    """甲板：横着的接缝、接缝上一排铆钉，上沿亮、下沿暗。"""
    x0, y0, w, h = rect
    gradient(s, rect, ct.shade(base, 1.08), ct.shade(base, 0.86), 0.03)
    for y in range(seams, h, seams):
        s.hline(x0, x0 + w, y0 + y, ct.shade(base, 0.6))
        if y + 1 < h:
            s.hline(x0, x0 + w, y0 + y + 1, ct.shade(base, 1.18))
        if rivets:
            for x in range(1, w - 1, 3):
                s.put(x0 + x, y0 + y - 1, ct.shade(GUN_LIGHT, 1.15))


def enamel(s, rect, rim=True):
    """红橙色的珐琅：左上亮、右下暗，边上一圈枪灰。"""
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            t = (x / max(1, w - 1) + y / max(1, h - 1)) / 2
            c = ct.mix(ENAMEL_LIGHT, ENAMEL_DARK, t * 0.9)
            s.put(x0 + x, y0 + y, ct.shade(c, 1 + s.rng.uniform(-0.03, 0.03)))
    if rim and w > 2 and h > 2:
        border(s, rect, GUN_DARK, "b")


def warden():
    s = ct.Skin(128, 64, 9031)
    # 身子 12×14×7：胸甲挡住上半截，露出来的是腹甲、侧甲，侧面一道红条
    f = ct.faces(0, 0, 12, 14, 7)
    each(f, SIDES, lambda r: armor(s, r, GUN, 3))
    for key in ("left", "right"):
        x0, y0, w, h = f[key]
        for y in range(h):
            s.put(x0 + w // 2, y0 + y, ENAMEL if y % 3 != 0 else ENAMEL_DARK)
    fill(s, f["top"], GUN)
    x0, y0, w, h = f["top"]
    for x in range(4, 8):
        for y in range(2, 5):
            s.put(x0 + x, y0 + y, SUIT)
    fill(s, f["bottom"], SUIT)
    # 胸甲 13×8×1：珐琅，正中一枚盾形的徽记，四角铆钉
    f = ct.faces(38, 0, 13, 8, 1)
    for key, r in f.items():
        enamel(s, r, key in SIDES)
    front = f["front"]
    border(s, front, ENAMEL_DARK, "lr")
    for i, j in ((0, 0), (12, 0), (0, 7), (12, 7)):
        px(s, front, i, j, BONE)
    # 盾形徽记：枪灰的底，骨白的竖线和横梁
    for j in range(1, 7):
        half = 2 if j < 5 else (1 if j == 5 else 0)
        for i in range(6 - half, 7 + half):
            px(s, front, i, j, GUN_DARK if abs(i - 6) == half else GUN)
    for j in range(2, 6):
        px(s, front, 6, j, BONE)
    for i in (5, 6, 7):
        px(s, front, i, 3, BONE)
    # 腰带 13×3×8：皮的，一排铆钉，正面一个珐琅扣
    f = ct.faces(0, 21, 13, 3, 8)
    for key, r in f.items():
        gradient(s, r, ct.shade(BELT, 1.1), ct.shade(BELT, 0.8))
    for key in SIDES:
        x0, y0, w, h = f[key]
        for x in range(1, w, 3):
            s.put(x0 + x, y0 + 1, GUN_LIGHT)
    x0, y0, w, h = f["front"]
    for i in range(5, 8):
        for j in range(3):
            s.put(x0 + i, y0 + j, ENAMEL if (i, j) != (6, 1) else BONE)
    # 背甲 8×9×2：枪灰，横着的通风缝透出橙光，正中一道红条
    f = ct.faces(42, 21, 8, 9, 2)
    each(f, ("left", "right", "top", "bottom", "front"), lambda r: armor(s, r, GUN, 4, False))
    back = f["back"]
    armor(s, back, GUN, 9, False)
    for j in (2, 4, 6):
        for i in range(1, 7):
            if i in (3, 4):
                continue
            px(s, back, i, j, (40, 24, 20))
            glow(s, back, i, j, VISOR, 0.55)
    for j in range(9):
        px(s, back, 3, j, ENAMEL)
        px(s, back, 4, j, ENAMEL_DARK)

    # 头盔 7×7×7：一道橙色的面罩光缝，下面是呼吸格栅，侧面一道红条
    f = ct.faces(66, 0, 7, 7, 7)
    each(f, ("back", "left", "right", "top"), lambda r: armor(s, r, GUN, 4))
    fill(s, f["bottom"], SUIT)
    front = f["front"]
    gradient(s, front, ct.shade(GUN, 1.1), ct.shade(GUN, 0.85))
    for i in range(7):
        px(s, front, i, 1, ENAMEL if i not in (0, 6) else ENAMEL_DARK)
    for i in range(1, 6):
        px(s, front, i, 2, (255, 214, 150))
        glow(s, front, i, 2, VISOR, 1.0)
        px(s, front, i, 3, (190, 80, 30))
        glow(s, front, i, 3, VISOR, 0.5)
    px(s, front, 0, 2, GUN_DARK)
    px(s, front, 6, 2, GUN_DARK)
    for i in (1, 3, 5):
        px(s, front, i, 5, GUN_DARK)
    for i in range(7):
        px(s, front, i, 6, ct.shade(GUN_DARK, 0.8))
    for key in ("left", "right"):
        r = f[key]
        for j in range(7):
            px(s, r, 3, j, ENAMEL if j < 4 else ENAMEL_DARK)
        # 耳边一个圆的接口
        for i, j in ((4, 3), (5, 3), (4, 4), (5, 4)):
            px(s, r, i if key == "left" else 6 - i, j, GUN_LIGHT if (i + j) % 2 == 0 else GUN_DARK)
    top = f["top"]
    for j in range(7):
        px(s, top, 3, j, ENAMEL_DARK)
    # 盔冠 2×2×8
    f = ct.faces(94, 0, 2, 2, 8)
    for key, r in f.items():
        enamel(s, r, False)
    x0, y0, w, h = f["top"]
    s.vline(x0, y0, y0 + h, ENAMEL_LIGHT)
    # 獠牙 1×2×1
    f = ct.faces(114, 0, 1, 2, 1)
    for key, r in f.items():
        gradient(s, r, BONE, ct.shade(BONE, 0.78))

    # 胳膊 4×13×4：上面是深色的衣服和一道红条，下面是枪灰的护臂（护手和肩甲另画）
    f = ct.faces(62, 21, 4, 13, 4)
    for key in SIDES:
        x0, y0, w, h = f[key]
        gradient(s, (x0, y0, w, 6), SUIT, ct.shade(SUIT, 0.8))
        for y in range(2, 6):
            s.put(x0 + 1, y0 + y, ENAMEL_DARK)
        armor(s, (x0, y0 + 6, w, h - 6), GUN, 3)
    fill(s, f["top"], SUIT)
    fill(s, f["bottom"], GUN_DARK)
    # 肩甲 7×4×7：珐琅，下沿一圈枪灰，顶上三道纹
    f = ct.faces(78, 21, 7, 4, 7)
    for key, r in f.items():
        enamel(s, r, key in SIDES)
    x0, y0, w, h = f["top"]
    for x in (1, 3, 5):
        s.vline(x0 + x, y0, y0 + h, ct.shade(ENAMEL, 1.15))
    for key in SIDES:
        px(s, f[key], 1, 1, BONE)
        px(s, f[key], 5, 1, BONE)
    # 护手 5×4×5：枪灰，正面一排指节的凸起
    f = ct.faces(106, 21, 5, 4, 5)
    for key, r in f.items():
        armor(s, r, GUN, 5, False)
    fill(s, f["bottom"], GUN_DARK)
    for i in range(5):
        px(s, f["front"], i, 2, GUN_LIGHT if i % 2 == 0 else GUN_DARK)

    # 腿 4×12×4：深色的裤子，大腿和小腿上是甲板（护膝、靴子另画）
    f = ct.faces(0, 32, 4, 12, 4)
    for key in SIDES:
        x0, y0, w, h = f[key]
        gradient(s, f[key], SUIT, ct.shade(SUIT, 0.75))
        armor(s, (x0, y0, w, 4), GUN, 4, False)
        armor(s, (x0, y0 + 8, w, 2), GUN, 4, False)
    fill(s, f["top"], SUIT)
    fill(s, f["bottom"], GUN_DARK)
    # 护膝 5×4×1：珐琅，正中一颗骨白的铆钉
    f = ct.faces(16, 32, 5, 4, 1)
    for key, r in f.items():
        enamel(s, r, False)
    px(s, f["front"], 2, 1, BONE)
    border(s, f["front"], ENAMEL_DARK, "b")
    # 靴子 5×2×5：枪灰，鞋底深色，靴头亮一点
    f = ct.faces(28, 32, 5, 2, 5)
    for key, r in f.items():
        fill(s, r, GUN)
    for key in SIDES:
        x0, y0, w, h = f[key]
        s.hline(x0, x0 + w, y0 + h - 1, GUN_DARK)
    for i in range(1, 4):
        px(s, f["front"], i, 0, GUN_LIGHT)
    fill(s, f["bottom"], (30, 30, 34))
    ct.save(s.img, ENTITY, "alien_npc_warden")
    ct.save(s.glow, ENTITY, "alien_npc_warden_glow")


if __name__ == "__main__":
    trader()
    scholar()
    warden()
