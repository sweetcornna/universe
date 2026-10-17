"""生成动物相关物品的贴图（16×16）：异兽生肉、熟异兽肉、异兽皮、两种刷怪蛋。

用法：python tools/fauna_textures.py [输出目录]
默认输出到 src/main/resources/assets/universe/textures/item。

形状用解析式算（椭圆、圆角矩形），外圈一像素压暗当描边，和原版物品的画法一致。
生肉偏紫红，和原版的牛肉区分开。
"""
import math
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures", "item")
N = 16


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def outline(mask):
    """mask 里挨着外面的像素。"""
    edge = set()
    for (x, y) in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask:
                edge.add((x, y))
                break
    return edge


def ellipse(cx, cy, rx, ry, angle=0.0):
    ca, sa = math.cos(angle), math.sin(angle)
    mask = set()
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            u = px * ca + py * sa
            v = -px * sa + py * ca
            if (u / rx) ** 2 + (v / ry) ** 2 <= 1:
                mask.add((x, y))
    return mask


def meat(cooked):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    body = ellipse(8.8, 8.6, 6.0, 4.4, math.radians(-35))
    # 骨头从左下角伸出去，末端两个骨节
    bone = set()
    for t in range(5):
        bone.add((3 - t // 2, 12 + (t + 1) // 2))
    bone |= {(0, 14), (1, 15), (0, 15), (2, 15)}
    bone = {(x, y) for (x, y) in bone if 0 <= x < N and 0 <= y < N and (x, y) not in body}
    if cooked:
        base, fat, dark = (139, 74, 44), (196, 132, 82), (70, 34, 18)
    else:
        base, fat, dark = (158, 42, 98), (236, 168, 206), (78, 16, 50)
    rnd = random.Random(7 if cooked else 3)
    edge = outline(body)
    for (x, y) in body:
        c = base
        # 斜着的几道脂肪纹
        if (x + y * 0.6) % 4.3 < 0.9 and (x, y) not in edge:
            c = fat
        # 左上亮、右下暗
        c = shade(c, 1.12 - (x + y) * 0.012 + rnd.uniform(-0.05, 0.05))
        if (x, y) in edge:
            c = shade(dark, 1.0)
        img.putpixel((x, y), c)
    for (x, y) in bone:
        img.putpixel((x, y), (236, 230, 214, 255) if (x + y) % 2 else (206, 198, 180, 255))
    return img


def hide():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = set()
    for y in range(N):
        for x in range(N):
            # 圆角的身子
            dx = max(0, abs(x + 0.5 - 8) - 2.4)
            dy = max(0, abs(y + 0.5 - 8.5) - 3.2)
            if dx * dx + dy * dy <= 2.2 ** 2:
                mask.add((x, y))
    # 四条腿皮斜着伸出去，上面一个头皮，下面一条尾巴
    for sx in (-1, 1):
        for sy in (-1, 1):
            for t in range(3):
                x = int(8 + sx * (4 + t) - (1 if sx < 0 else 0))
                y = int(8.5 + sy * (3 + t * 0.7))
                mask.add((x, y))
                mask.add((x - sx, y))
    for (x, y) in ((7, 1), (8, 1), (7, 2), (8, 2), (8, 14), (8, 15), (7, 14)):
        mask.add((x, y))
    mask = {(x, y) for (x, y) in mask if 0 <= x < N and 0 <= y < N}
    torso = {(x, y) for (x, y) in mask if 3 <= x <= 12 and 3 <= y <= 13}
    base, spot, dark = (128, 150, 120), (72, 104, 96), (52, 64, 50)
    rnd = random.Random(11)
    edge = outline(mask)
    spots = [(rnd.uniform(4, 12), rnd.uniform(4, 12)) for _ in range(5)]
    for (x, y) in mask:
        c = base
        if any((x - sx) ** 2 + (y - sy) ** 2 < 1.6 for sx, sy in spots):
            c = spot
        c = shade(c, 1.08 - y * 0.012 + rnd.uniform(-0.06, 0.06))
        if (x, y) in edge:
            # 细细的腿皮、尾巴只压暗一些，不然整条都是描边色
            c = dark if (x, y) in torso else shade(base, 0.72)
        img.putpixel((x, y), c)
    return img


def egg(base, spot, seed):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = set()
    for y in range(1, N):
        for x in range(N):
            py = (y + 0.5 - 9.0) / 6.6
            # 上窄下宽
            rx = 5.2 * (1 - 0.18 * max(0, -py))
            if py * py + ((x + 0.5 - 8) / rx) ** 2 <= 1:
                mask.add((x, y))
    rnd = random.Random(seed)
    spots = [(rnd.uniform(4, 12), rnd.uniform(4, 14), rnd.uniform(0.6, 1.5)) for _ in range(6)]
    edge = outline(mask)
    dark = shade(base, 0.45)
    for (x, y) in mask:
        c = base
        if any((x - sx) ** 2 + (y - sy) ** 2 < r * r for sx, sy, r in spots):
            c = spot
        c = shade(c, 1.15 - (x * 0.5 + y) * 0.018)
        if (x, y) in edge:
            c = dark
        img.putpixel((x, y), c)
    # 高光
    for (x, y) in ((5, 5), (5, 6), (6, 4)):
        if (x, y) in mask and (x, y) not in edge:
            img.putpixel((x, y), mix(img.getpixel((x, y)), (255, 255, 255), 0.55))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    meat(False).save(os.path.join(OUT, "alien_meat.png"))
    meat(True).save(os.path.join(OUT, "cooked_alien_meat.png"))
    hide().save(os.path.join(OUT, "alien_hide.png"))
    egg((70, 168, 150), (150, 72, 190), 5).save(os.path.join(OUT, "alien_creature_spawn_egg.png"))
    egg((226, 150, 60), (60, 110, 200), 9).save(os.path.join(OUT, "alien_flyer_spawn_egg.png"))
    print("wrote item textures to", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
