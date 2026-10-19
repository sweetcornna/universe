"""生成动物相关物品的贴图（16×16）：异兽生肉、熟异兽肉、异兽皮、两种刷怪蛋，
喂养的产物（异星蛋、烤异星蛋、异星绒毛、生物胶质）和两种饲料（ADR-0017）。

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


def alien_egg(cooked):
    """异星蛋：比刷怪蛋小一圈、淡紫带青斑；烤过的壳焦黄，冒一缕热气。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = set()
    for y in range(N):
        for x in range(N):
            py = (y + 0.5 - 9.0) / 6.3
            rx = 4.9 * (1 - 0.2 * max(0, -py))
            if py * py + ((x + 0.5 - 8) / rx) ** 2 <= 1:
                mask.add((x, y))
    if cooked:
        base, spot = (214, 160, 92), (150, 92, 44)
    else:
        base, spot = (206, 196, 236), (78, 176, 168)
    rnd = random.Random(21 if cooked else 17)
    spots = [(rnd.uniform(5, 11), rnd.uniform(6, 14), rnd.uniform(0.5, 1.1)) for _ in range(7)]
    edge = outline(mask)
    dark = shade(base, 0.42)
    for (x, y) in mask:
        c = base
        if any((x - sx) ** 2 + (y - sy) ** 2 < r * r for sx, sy, r in spots):
            c = spot
        c = shade(c, 1.12 - (x * 0.5 + y) * 0.016)
        if (x, y) in edge:
            c = dark
        img.putpixel((x, y), c)
    for (x, y) in ((6, 7), (6, 8), (7, 6)):
        if (x, y) in mask and (x, y) not in edge:
            img.putpixel((x, y), mix(img.getpixel((x, y)), (255, 255, 255), 0.6))
    if cooked:
        # 一缕热气
        for (x, y) in ((9, 3), (10, 2), (9, 1), (6, 2), (7, 1)):
            img.putpixel((x, y), (236, 236, 236, 150))
    return img


def fluff():
    """异星绒毛：一团蓬松的毛，粉青两色，亮的一侧有几根细毛翘出来。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    # 几个圆叠成的一团毛
    tufts = ((8, 6.2, 3.6), (4.9, 8.6, 3.2), (11.1, 8.6, 3.2), (6.2, 11.6, 3.0), (9.9, 11.6, 3.0), (8, 9.4, 3.4))
    mask = {(x, y) for y in range(N) for x in range(N)
            if any((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r for cx, cy, r in tufts)}
    base, light, dark = (150, 214, 210), (236, 206, 232), (58, 104, 112)
    rnd = random.Random(33)
    edge = outline(mask)
    for (x, y) in mask:
        t = max(0.0, min(1.0, (16 - x - y) / 16 + rnd.uniform(-0.12, 0.12)))
        c = mix(base, light, t * 0.8)
        # 一卷一卷的毛：小圈的明暗
        if (x * 3 + y * 5) % 7 == 0:
            c = shade(c, 0.86)
        if (x, y) in edge:
            c = shade(dark, 1.05)
        img.putpixel((x, y), c)
    for (x, y) in ((2, 5), (1, 4), (5, 2), (11, 2)):
        if (x, y) not in mask:
            img.putpixel((x, y), shade(light, 0.95))
    return img


def gel():
    """生物胶质：半透明的绿色一滴，带高光和里面的小气泡。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = set()
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - 8, y + 0.5 - 9.6
            # 下面圆、上面收成尖的水滴
            if py >= 0:
                inside = px * px + py * py <= 5.2 ** 2
            else:
                inside = abs(px) <= 5.2 * (1 + py / 8.6) and py > -8.6
            if inside:
                mask.add((x, y))
    base, deep = (120, 214, 96), (40, 120, 52)
    edge = outline(mask)
    for (x, y) in mask:
        depth = max(0.0, min(1.0, (y - 4) / 11))
        c = mix(base, deep, depth * 0.7)
        alpha = 200 if (x, y) not in edge else 255
        if (x, y) in edge:
            c = shade(deep, 0.75)
        img.putpixel((x, y), c[:3] + (alpha,))
    for (x, y) in ((6, 8), (6, 9), (7, 7)):
        img.putpixel((x, y), (232, 255, 224, 230))
    for (x, y) in ((10, 11), (9, 13)):
        img.putpixel((x, y), (186, 244, 170, 220))
    return img


def feed(herbivore):
    """饲料：麻布小袋，口上露出一把颗粒；草食是绿色的草籽团，肉食是红褐色的肉干粒。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    sack = set()
    for y in range(5, N - 1):
        for x in range(N):
            # 袋子下宽上窄
            half = 4.2 + (y - 5) * 0.22
            if abs(x + 0.5 - 8) <= half:
                sack.add((x, y))
    cloth, dark = (196, 168, 120), (104, 80, 50)
    edge = outline(sack)
    rnd = random.Random(41 if herbivore else 43)
    for (x, y) in sack:
        c = shade(cloth, 1.08 - y * 0.014 + rnd.uniform(-0.05, 0.05))
        # 麻布纹
        if (x + y) % 3 == 0:
            c = shade(c, 0.92)
        if (x, y) in edge:
            c = dark
        img.putpixel((x, y), c)
    # 扎口的绳子
    for x in range(4, 12):
        img.putpixel((x, 6), (128, 92, 52, 255))
    pellet = (112, 178, 64) if herbivore else (156, 64, 44)
    pellet_dark = shade(pellet, 0.6)
    for (x, y) in ((5, 4), (6, 3), (7, 4), (8, 3), (9, 4), (10, 3), (7, 2), (9, 2), (8, 1)):
        img.putpixel((x, y), pellet if (x + y) % 2 else pellet_dark)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    meat(False).save(os.path.join(OUT, "alien_meat.png"))
    meat(True).save(os.path.join(OUT, "cooked_alien_meat.png"))
    hide().save(os.path.join(OUT, "alien_hide.png"))
    egg((70, 168, 150), (150, 72, 190), 5).save(os.path.join(OUT, "alien_creature_spawn_egg.png"))
    egg((226, 150, 60), (60, 110, 200), 9).save(os.path.join(OUT, "alien_flyer_spawn_egg.png"))
    alien_egg(False).save(os.path.join(OUT, "alien_egg.png"))
    alien_egg(True).save(os.path.join(OUT, "cooked_alien_egg.png"))
    fluff().save(os.path.join(OUT, "alien_fluff.png"))
    gel().save(os.path.join(OUT, "bio_gel.png"))
    feed(True).save(os.path.join(OUT, "herbivore_feed.png"))
    feed(False).save(os.path.join(OUT, "carnivore_feed.png"))
    print("wrote item textures to", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
