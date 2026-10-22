"""生成 M14（ADR-0026 §六）多功能工具的类型贴图和空间站的多功能工具柜。

用法：python tools/m14_multitool_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，每次生成的结果一样。

多功能工具照着原来那张（textures/item/multitool.png，手枪和没有类型的旧工具用它）按调色板换颜色，外形不变：
- 步枪：枪灰的机身，红色涂装，枪口是橙黄的光，枪管多出一格；
- 实验型：白色机身，紫色涂装，枪口是品红的光；
- 异星型：绿色、像长出来的机身，黄色斑纹，枪口是青绿的光，握把是棕色；
- 守卫机型：深色装甲，琥珀色灯带，枪口是红橙的光，和守卫机一个配色。
工具柜和空间站的交易终端是一家：正面屏幕里斜挂着三把小工具（白、橙、青），下面一排琥珀色价签；顶面的徽记换成橙色，侧面照搬。
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, ITEM, N, shade, put, rect, save  # noqa: E402

# 原贴图的调色板：外框、高光、枪口光、机身亮/中/暗、涂装、握把
OUTLINE = (62, 66, 76)
HIGHLIGHT = (196, 236, 255)
GLOW = (92, 198, 232)
BODY_LIGHT = (232, 234, 238)
BODY_MID = (196, 200, 207)
BODY_DARK = (118, 124, 134)
ACCENT = (238, 128, 40)
GRIP = (84, 88, 98)

PALETTES = {
    "rifle": {
        BODY_LIGHT: (156, 162, 172), BODY_MID: (116, 122, 132), BODY_DARK: (76, 80, 88),
        ACCENT: (206, 58, 48), GLOW: (255, 186, 72), HIGHLIGHT: (255, 236, 186), GRIP: (58, 54, 52),
    },
    "experimental": {
        ACCENT: (166, 104, 240), GLOW: (214, 112, 255), HIGHLIGHT: (244, 214, 255),
    },
    "alien": {
        OUTLINE: (38, 60, 44), BODY_LIGHT: (156, 214, 140), BODY_MID: (112, 172, 106), BODY_DARK: (70, 120, 72),
        ACCENT: (238, 216, 88), GLOW: (118, 255, 188), HIGHLIGHT: (210, 255, 228), GRIP: (96, 72, 58),
    },
    "sentinel": {
        OUTLINE: (24, 26, 30), BODY_LIGHT: (78, 82, 92), BODY_MID: (58, 62, 70), BODY_DARK: (40, 43, 50),
        ACCENT: (255, 176, 40), GLOW: (255, 92, 52), HIGHLIGHT: (255, 212, 150), GRIP: (36, 38, 44),
    },
}


def load(folder, name):
    return Image.open(os.path.join(folder, name + ".png")).convert("RGBA")


def multitool(kind, palette):
    base = load(ITEM, "multitool")
    out = base.copy()
    for y in range(N):
        for x in range(N):
            r, g, b, a = base.getpixel((x, y))
            if a == 0:
                continue
            color = palette.get((r, g, b))
            if color is not None:
                put(out, x, y, color, a)
    if kind == "rifle":
        # 枪管再伸出去一格：右上角枪口外面补一格枪口光
        put(out, 15, 1, palette[GLOW])
    save(out, ITEM, "multitool_" + kind)


def cabinet():
    front = load(BLOCK, "ship_repair_front")
    screen = front.getpixel((2, 2))[:3]
    rect(front, 2, 2, 13, 10, screen)
    # 三把斜挂着的小工具：握把在左下，枪口在右上
    for x0, color in ((3, (226, 229, 232)), (7, (238, 128, 40)), (11, (92, 198, 232))):
        for i in range(5):
            put(front, x0 - 1 + i // 2, 8 - i, color if i < 4 else shade(color, 1.25))
        put(front, x0 - 2, 9, shade(color, 0.55))
    # 价签
    glow = (232, 160, 60)
    for x in range(3, 13):
        put(front, x, 11 - 1, glow if x % 4 != 2 else shade(glow, 0.55))
    save(front, BLOCK, "multitool_cabinet_front")
    top = load(BLOCK, "trade_terminal_top")
    for y in range(N):
        for x in range(N):
            r, g, b, a = top.getpixel((x, y))
            if r > 150 and r > b + 60:
                light = (r + g + b) / (3 * 255)
                put(top, x, y, shade((255, 170, 90), 0.55 + 0.6 * light))
    save(top, BLOCK, "multitool_cabinet_top")
    save(load(BLOCK, "trade_terminal_side"), BLOCK, "multitool_cabinet_side")


def main():
    for kind, palette in PALETTES.items():
        multitool(kind, palette)
    cabinet()


if __name__ == "__main__":
    main()
