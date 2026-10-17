"""生成外骨骼的盔甲贴图和物品图标。

用法：python tools/suit_textures.py [贴图根目录]
默认输出到 src/main/resources/assets/universe/textures。

盔甲贴图是原版的 64×32 人形盔甲布局：
- entity/equipment/humanoid/exosuit.png：头盔（头）、胸甲（身体、手臂）、靴子（腿的下半截）
- entity/equipment/humanoid_leggings/exosuit.png：护腿（腰带、腿的上半截）
外观是白色的宇航服，橙色条纹，深色面罩，背上有生命维持背包。物品图标按字符画逐像素画。
"""
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")

WHITE = (232, 234, 238)
SHADE = (196, 200, 207)
SEAM = (150, 156, 166)
DARK = (62, 66, 76)
GREY = (118, 124, 134)
GREY_DARK = (84, 88, 98)
ORANGE = (238, 128, 40)
ORANGE_DARK = (182, 88, 26)
VISOR = (28, 38, 56)
VISOR_GLINT = (92, 198, 232)
LIGHT_GREEN = (110, 230, 120)
LIGHT_BLUE = (110, 200, 255)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def rect(img, x, y, w, h, color):
    for j in range(y, y + h):
        for i in range(x, x + w):
            img.putpixel((i, j), color + (255,))


def px(img, x, y, color):
    img.putpixel((x, y), color + (255,))


def panel(img, x, y, w, h, base=WHITE, edge=SEAM):
    """一块带接缝的白色面板：四周一圈深一点。"""
    rect(img, x, y, w, h, base)
    for i in range(x, x + w):
        px(img, i, y + h - 1, edge)
    for j in range(y, y + h):
        px(img, x + w - 1, j, SHADE if base == WHITE else edge)


def box(img, u, v, w, h, d, faces):
    """按原版的长方体展开填六个面。faces 是 {面: 函数(img, x, y, 宽, 高)}，缺的面画成白色面板。"""
    layout = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for name, (x, y, fw, fh) in layout.items():
        faces.get(name, lambda im, a, b, c, e: panel(im, a, b, c, e))(img, x, y, fw, fh)


def helmet_front(img, x, y, w, h):
    panel(img, x, y, w, h)
    # 面罩：第 2–5 行，左右各留一格
    rect(img, x + 1, y + 2, w - 2, 4, VISOR)
    px(img, x + 2, y + 2, VISOR_GLINT)
    px(img, x + 3, y + 2, VISOR_GLINT)
    px(img, x + 2, y + 3, VISOR_GLINT)
    for i in range(x + 1, x + w - 1):
        px(img, i, y + 6, SEAM)
    px(img, x + w // 2 - 1, y, ORANGE)
    px(img, x + w // 2, y, ORANGE)


def helmet_side(img, x, y, w, h):
    panel(img, x, y, w, h)
    rect(img, x + 2, y + 3, 3, 3, GREY)
    px(img, x + 3, y + 4, LIGHT_GREEN)


def helmet_top(img, x, y, w, h):
    panel(img, x, y, w, h)
    for j in range(y, y + h):
        px(img, x + w // 2 - 1, j, ORANGE)
        px(img, x + w // 2, j, ORANGE_DARK)


def helmet_back(img, x, y, w, h):
    panel(img, x, y, w, h)
    rect(img, x + 2, y + 4, w - 4, 3, GREY)
    px(img, x + 3, y + 5, GREY_DARK)
    px(img, x + w - 4, y + 5, GREY_DARK)


def chest_front(img, x, y, w, h):
    panel(img, x, y, w, h)
    # 领口、胸前的控制面板、腰部的橙色条
    for i in range(x + 2, x + w - 2):
        px(img, i, y, SEAM)
    rect(img, x + 2, y + 3, 4, 3, GREY)
    px(img, x + 3, y + 4, LIGHT_GREEN)
    px(img, x + 4, y + 4, LIGHT_BLUE)
    for i in range(x, x + w):
        px(img, i, y + 8, ORANGE)
        px(img, i, y + 9, ORANGE_DARK)
    for j in range(y, y + 8):
        px(img, x + w - 2, j, ORANGE if j % 3 else ORANGE_DARK)


def chest_back(img, x, y, w, h):
    panel(img, x, y, w, h)
    # 生命维持背包：灰色箱体加两只橙色气罐
    rect(img, x + 1, y + 1, w - 2, 8, GREY)
    rect(img, x + 2, y + 2, 2, 6, ORANGE)
    rect(img, x + w - 4, y + 2, 2, 6, ORANGE)
    for j in range(y + 2, y + 8):
        px(img, x + 3, j, ORANGE_DARK)
        px(img, x + w - 3, j, ORANGE_DARK)
    px(img, x + w // 2, y + 3, LIGHT_GREEN)
    for i in range(x + 1, x + w - 1):
        px(img, i, y + 9, GREY_DARK)


def chest_side(img, x, y, w, h):
    panel(img, x, y, w, h)
    for i in range(x, x + w):
        px(img, i, y + 8, ORANGE)
        px(img, i, y + 9, ORANGE_DARK)


def arm_face(img, x, y, w, h):
    panel(img, x, y, w, h)
    # 上臂的橙色臂章、手肘的关节、手腕的灰色护腕
    for i in range(x, x + w):
        px(img, i, y + 2, ORANGE)
        px(img, i, y + 5, SEAM)
    rect(img, x, y + h - 3, w, 3, GREY)
    for i in range(x, x + w):
        px(img, i, y + h - 1, GREY_DARK)


def boots_face(img, x, y, w, h):
    # 靴子只画腿的最下面 5 行，上面透明
    rect(img, x, y + h - 5, w, 5, GREY)
    for i in range(x, x + w):
        px(img, i, y + h - 5, WHITE)
        px(img, i, y + h - 3, ORANGE)
        px(img, i, y + h - 1, GREY_DARK)


def boots_bottom(img, x, y, w, h):
    rect(img, x, y, w, h, GREY_DARK)


def clear(img, x, y, w, h):
    pass


def legs_face(img, x, y, w, h, side=False):
    # 护腿画上面 9 行：大腿白色、膝盖灰色护垫，外侧一道橙色
    panel(img, x, y, w, 9)
    for i in range(x, x + w):
        px(img, i, y + 5, GREY)
        px(img, i, y + 6, GREY_DARK)
    if side:
        for j in range(y, y + 9):
            px(img, x + w // 2, j, ORANGE)


def belt(img, x, y, w, h):
    # 身体的下半截是腰带
    rect(img, x, y + h - 5, w, 5, WHITE)
    for i in range(x, x + w):
        px(img, i, y + h - 5, GREY)
        px(img, i, y + h - 4, GREY_DARK)
    px(img, x + w // 2 - 1, y + h - 5, ORANGE)
    px(img, x + w // 2, y + h - 5, ORANGE)


def armor_layers():
    main = new(64, 32)
    box(main, 0, 0, 8, 8, 8, {"front": helmet_front, "right": helmet_side, "left": helmet_side,
                               "top": helmet_top, "back": helmet_back})
    box(main, 16, 16, 8, 12, 4, {"front": chest_front, "back": chest_back, "right": chest_side, "left": chest_side})
    box(main, 40, 16, 4, 12, 4, {name: arm_face for name in ("front", "back", "left", "right")})
    box(main, 0, 16, 4, 12, 4, {"front": boots_face, "back": boots_face, "left": boots_face, "right": boots_face,
                                 "top": clear, "bottom": boots_bottom})
    leggings = new(64, 32)
    box(leggings, 16, 16, 8, 12, 4, {"front": belt, "back": belt, "left": belt, "right": belt, "top": clear, "bottom": clear})
    box(leggings, 0, 16, 4, 12, 4, {"front": legs_face, "back": legs_face,
                                     "left": lambda im, a, b, c, e: legs_face(im, a, b, c, e, True),
                                     "right": lambda im, a, b, c, e: legs_face(im, a, b, c, e, True),
                                     "top": lambda im, a, b, c, e: rect(im, a, b, c, e, WHITE), "bottom": clear})
    return main, leggings


# ---------------------------------------------------------------- 物品图标

PALETTE = {
    "1": DARK, "2": WHITE, "3": SHADE, "g": GREY, "G": GREY_DARK, "o": ORANGE, "O": ORANGE_DARK,
    "v": VISOR, "c": VISOR_GLINT, "l": LIGHT_GREEN,
    "b": (80, 170, 230), "B": (40, 100, 160), "h": (196, 236, 255),
    "y": (240, 200, 50), "Y": (178, 138, 22), "k": (40, 40, 46),
}

ICONS = {
    "exosuit_helmet": [
        "................",
        "................",
        ".....111111.....",
        "....12o22221....",
        "...122o222221...",
        "..12222o222221..",
        "..13vvvvvvvv31..",
        "..13vccvvvvv31..",
        "..13vcvvvvvv31..",
        "..13vvvvvvvv31..",
        "..123333333321..",
        "..1g22222222g1..",
        "...1gggggggg1...",
        "....11111111....",
        "................",
        "................",
    ],
    "exosuit_chestplate": [
        "................",
        ".1111......1111.",
        "1222211111122221",
        "1232222oo2222321",
        "1332222oo2222331",
        "..1222gggg2221..",
        "..1222glcg2221..",
        "..1222gggg2221..",
        "..122222222221..",
        "..132222oo2231..",
        "..133222222331..",
        "..1gggggggggg1..",
        "...1111111111...",
        "................",
        "................",
        "................",
    ],
    "exosuit_leggings": [
        "................",
        "..111111111111..",
        "..1ggggoogggg1..",
        "..122222222221..",
        "..122223322221..",
        "..122221122221..",
        "..1o22211222o1..",
        "..1o221..122o1..",
        "..1o221..122o1..",
        "..1gg31..13gg1..",
        "..1GG31..13GG1..",
        "..1o221..122o1..",
        "..13221..12231..",
        "..11111..11111..",
        "................",
        "................",
    ],
    "exosuit_boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..1111....1111..",
        "..1221....1221..",
        "..1o21....12o1..",
        "..1221....1221..",
        ".1g2221..1222g1.",
        ".1oooo1..1oooo1.",
        ".1gggg1..1gggg1.",
        ".1GGGG1..1GGGG1.",
        ".111111..111111.",
        "................",
        "................",
    ],
    "oxygen_capsule": [
        "................",
        "......1111......",
        "......1gg1......",
        ".....1GggG1.....",
        "....11111111....",
        "....1hbbbbB1....",
        "....1hbbbbB1....",
        "....1h2222B1....",
        "....1h3333B1....",
        "....1hbbbbB1....",
        "....1hbbbbB1....",
        "....1hbbbbB1....",
        "....1BBBBBB1....",
        "....11111111....",
        "................",
        "................",
    ],
    "hazard_cell": [
        "................",
        "................",
        ".......11.......",
        "......1gg1......",
        "....11111111....",
        "....1yyyyyy1....",
        "....1ykkyyY1....",
        "....1yykkyY1....",
        "....1yyykkY1....",
        "....1kyyykY1....",
        "....1kkyyyY1....",
        "....1ykkyyY1....",
        "....1YYYYYY1....",
        "....11111111....",
        "................",
        "................",
    ],
}


def icon(rows):
    assert len(rows) == 16, rows
    img = new(16, 16)
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                px(img, x, y, PALETTE[ch])
    return img


def save(img, *parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    main_layer, leggings = armor_layers()
    save(main_layer, "entity", "equipment", "humanoid", "exosuit.png")
    save(leggings, "entity", "equipment", "humanoid_leggings", "exosuit.png")
    for name, rows in ICONS.items():
        save(icon(rows), "item", name + ".png")


if __name__ == "__main__":
    main()
