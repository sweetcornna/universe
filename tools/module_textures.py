"""生成技术模块的物品贴图（16×16 像素画，ADR-0014）。

用法：python tools/module_textures.py [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/item。

每种模块两张：module_<种类>.png 是芯片和图案，module_<种类>_overlay.png 是边框和指示灯（灰度），
游戏里按品级染色（client/tech/ModuleGradeTint）。随机数用固定种子，每次生成的结果一样。
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures", "item")
OUT = sys.argv[1] if len(sys.argv) > 1 else ROOT
N = 16

EXOSUIT = (110, 220, 245)
MULTITOOL = (255, 170, 90)
SHIP = (190, 150, 255)


def new():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def put(img, x, y, color, a=255):
    if 0 <= x < N and 0 <= y < N:
        img.putpixel((x, y), (color[0], color[1], color[2], a))


def shade(color, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in color)


def chip(rng):
    """芯片底板：深色金属，左右各三根针脚。"""
    img = new()
    base = (52, 60, 68)
    for y in range(2, 14):
        for x in range(2, 14):
            # 圆角
            if (x in (2, 13)) and (y in (2, 13)):
                continue
            f = 1.0 + rng.uniform(-0.06, 0.06)
            if x == 2 or y == 2:
                f += 0.18
            if x == 13 or y == 13:
                f -= 0.22
            put(img, x, y, shade(base, f))
    for y in (5, 8, 11):
        put(img, 1, y, (150, 156, 160))
        put(img, 14, y, (110, 116, 120))
    # 中间的凹槽
    for y in range(4, 12):
        for x in range(4, 12):
            put(img, x, y, shade((28, 33, 38), 1.0 + rng.uniform(-0.05, 0.05)))
    return img


def overlay():
    """边框和左上角的指示灯：灰度，染成品级的颜色。"""
    img = new()
    for i in range(3, 13):
        put(img, i, 2, (235, 235, 235))
        put(img, i, 13, (150, 150, 150))
        put(img, 2, i, (220, 220, 220))
        put(img, 13, i, (160, 160, 160))
    put(img, 3, 3, (255, 255, 255))
    put(img, 12, 12, (120, 120, 120))
    return img


def glyph(img, pixels, color):
    """图案：字符画，'#' 主色，'+' 亮色，'-' 暗色；左上角对齐到 (4, 4)，8×8。"""
    for dy, row in enumerate(pixels):
        for dx, ch in enumerate(row):
            if ch == "#":
                put(img, 4 + dx, 4 + dy, color)
            elif ch == "+":
                put(img, 4 + dx, 4 + dy, shade(color, 1.35))
            elif ch == "-":
                put(img, 4 + dx, 4 + dy, shade(color, 0.6))


GLYPHS = {
    # 氧气泡
    "life_support": (EXOSUIT, [
        "........",
        "..++....",
        ".+##+...",
        ".#--#.+.",
        ".+##+.#.",
        "..##....",
        "....++..",
        "....##..",
    ]),
    # 雪花
    "cold_lining": (EXOSUIT, [
        "...+....",
        ".#.#.#..",
        "..###...",
        "++#+#++.",
        "..###...",
        ".#.#.#..",
        "...#....",
        "........",
    ]),
    # 火焰
    "heat_lining": (EXOSUIT, [
        "...+....",
        "...##...",
        "..+##...",
        "..###+..",
        ".##-##..",
        ".#---#..",
        "..###...",
        "........",
    ]),
    # 过滤网格
    "toxin_filter": (EXOSUIT, [
        "........",
        ".######.",
        ".#-#-#+.",
        ".######.",
        ".#-#-#-.",
        ".+#####.",
        "........",
        "........",
    ]),
    # 三叶标志
    "radiation_shield": (EXOSUIT, [
        "........",
        ".##..##.",
        ".###+##.",
        "...++...",
        "...##...",
        "..####..",
        "..####..",
        "........",
    ]),
    # 向上的箭头和尾焰
    "jet_booster": (EXOSUIT, [
        "...++...",
        "..####..",
        ".######.",
        "...##...",
        "...##...",
        "..-##-..",
        "...--...",
        "........",
    ]),
    # 双箭头
    "servo": (EXOSUIT, [
        "........",
        ".#..#...",
        "..#+.#..",
        "...#+.#.",
        "..#+.#..",
        ".#..#...",
        "........",
        "........",
    ]),
    # 盾牌
    "shield": (EXOSUIT, [
        "........",
        ".######.",
        ".#++++#.",
        ".#+##+#.",
        ".#####-.",
        "..###-..",
        "...#-...",
        "........",
    ]),
    # 斜着的光束
    "beam_focus": (MULTITOOL, [
        "......++",
        ".....+#.",
        "....+#..",
        "...+#...",
        "..##....",
        ".###....",
        "-##.....",
        "........",
    ]),
    # 散热片
    "heat_sink": (MULTITOOL, [
        "........",
        ".#.#.#..",
        ".#.#.#..",
        ".+.+.+..",
        ".#.#.#..",
        "-######.",
        "........",
        "........",
    ]),
    # 扫描波纹
    "scanner_array": (MULTITOOL, [
        "........",
        ".####...",
        "#....#..",
        "..##..#.",
        ".#..#.#.",
        ".#.+#.#.",
        "........",
        "........",
    ]),
    # 眼睛
    "analyzer": (MULTITOOL, [
        "........",
        "..####..",
        ".#....#.",
        "#..++..#",
        "#..++..#",
        ".#....#.",
        "..####..",
        "........",
    ]),
    # 准星
    "weapon_calibrator": (MULTITOOL, [
        "...#....",
        "..###...",
        ".#.#.#..",
        "####+###",
        ".#.#.#..",
        "..###...",
        "...#....",
        "........",
    ]),
    # ---- 飞船（ADR-0024）
    # 喷口和火焰
    "thrust_efficiency": (SHIP, [
        "..####..",
        "..#--#..",
        ".######.",
        "..+..+..",
        "..+..+..",
        "...++...",
        "...+....",
        "........",
    ]),
    # 往右的箭头加速度线
    "pulse_tuning": (SHIP, [
        "........",
        "-...##..",
        "--..###.",
        "+++#####",
        "--..###.",
        "-...##..",
        "........",
        "........",
    ]),
    # 星和跃迁的弧线
    "hyperdrive_booster": (SHIP, [
        "......+.",
        ".....+#+",
        "..-...+.",
        ".-......",
        "-...#...",
        "-..###..",
        ".-..#...",
        "..--....",
    ]),
    # 护盾的弧面
    "deflector": (SHIP, [
        "..####..",
        ".#++++#.",
        "#+....+#",
        "#......#",
        "#......#",
        ".#....#.",
        "..#..#..",
        "...##...",
    ]),
    # 铆钉钢板
    "hull_reinforcement": (SHIP, [
        "########",
        "#+....+#",
        "#.----.#",
        "#.----.#",
        "#.----.#",
        "#.----.#",
        "#+....+#",
        "########",
    ]),
    # 炮管和瞄准点
    "fire_control": (SHIP, [
        "......+.",
        ".....+++",
        "....#.+.",
        "...#....",
        "..#.....",
        ".##.....",
        "###.....",
        "-#......",
    ]),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (color, pixels) in GLYPHS.items():
        rng = random.Random(sum(map(ord, name)))
        img = chip(rng)
        glyph(img, pixels, color)
        img.save(os.path.join(OUT, f"module_{name}.png"))
        overlay().save(os.path.join(OUT, f"module_{name}_overlay.png"))
    print(f"wrote {len(GLYPHS) * 2} textures to {OUT}")


if __name__ == "__main__":
    main()
