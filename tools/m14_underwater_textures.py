"""生成 M14（ADR-0026）水下部分的物品和方块贴图：生鱼、熟鱼、海带囊、活珍珠、水生动物刷怪蛋、潜艇（物品图标）、
海带脆片和海味卷两道菜；烛光海带（长熟的和收过的）、甲壳蚌（壳顶、壳侧、壳里）、海底密封箱。

用法：python tools/m14_underwater_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数，随机数用固定种子，每次生成的结果一样。

照着现实里的原型画：
- 生鱼像一条鲭鱼那样的远洋鱼：背上深蓝绿，往下渐渐变成银白（背深腹浅的保护色），侧线上一排淡青的发光点；熟鱼烤成焦黄，带两道烤痕。
- 烛光海带照巨藻画：橄榄褐色的长叶片从一根柄上往上飘，叶片根上挂着圆的气囊；长熟时气囊里亮着琥珀色的光（像蜡烛），收过的只剩叶片。
- 海带囊是巨藻的气囊：一个半透明的琥珀色圆囊，连着一小截柄和叶片根。
- 活珍珠是带晕彩的奶白色圆珠，边上泛粉、泛青。
- 甲壳蚌照砗磲画：壳面一道道放射的粗肋，边缘波浪形，粉褐色，上面有几点藤壶和绿藻；壳里是珍珠层和淡橙色的外套膜。
- 海底密封箱是泡了很久的金属箱：青灰色的箱板，锈斑和绿藻，正面一圈封条亮着微弱的青光。
- 潜艇的图标是侧面：白色的耐压壳，一道橙色的条，前面一个深蓝的玻璃罩，后面螺旋桨，艇头一盏灯。
- 海带脆片是几片压平烤干的深绿色海带；海味卷是一卷海带包着鱼肉，切开两片看得到粉色的鱼肉。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, ITEM, N, shade, mix, clear, new, put, rect, outline, save, noise  # noqa: E402


def blob(img, cx, cy, rx, ry, color, a=255, light=0.2):
    """实心的椭圆：左上亮、右下暗。"""
    for y in range(N):
        for x in range(N):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            if dx * dx + dy * dy <= 1:
                put(img, x, y, shade(color, 1 + light * (-dx - dy) / 2), a)


def line(img, x0, y0, x1, y1, color, a=255):
    steps = max(round(abs(x1 - x0)), round(abs(y1 - y0)), 1)
    for i in range(steps + 1):
        t = i / steps
        put(img, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), color, a)


def filled(img, x, y):
    return img.getpixel((x, y))[3] != 0


# ---- 鱼

BACK = (40, 86, 104)
FLANK = (150, 176, 186)
BELLY = (226, 232, 230)
FIN = (82, 150, 158)
GLOW = (150, 240, 230)


def fish_mask():
    """斜放的一条鱼：头在右上，尾在左下（和原版鳕鱼一样的朝向）。返回 {(x, y): (沿身体的位置 t, 从背到腹 s)}。"""
    cells = {}
    # 身体的中轴从尾柄 (3, 12.5) 到吻端 (13.2, 2.8)
    ax, ay, bx, by = 3.0, 12.5, 13.2, 2.8
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    nx, ny = uy, -ux  # 指向背（左上）
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px * ux + py * uy) / length
            d = px * nx + py * ny
            if not 0.08 <= t <= 1.0:
                continue
            # 纺锤形：最粗处在前三分之一，尾柄细，吻端尖
            if t < 0.62:
                half = 0.9 + 2.9 * math.sin(math.pi / 2 * (t - 0.08) / 0.54)
            else:
                half = 3.8 * math.cos(math.pi / 2 * (t - 0.62) / 0.38) ** 0.7
            if abs(d) <= half:
                cells[(x, y)] = (t, (half - d) / max(0.5, 2 * half))
    return cells


def tail(img, color):
    """叉形尾鳍，连在尾柄上。"""
    for (x, y) in ((2, 12), (1, 12), (0, 11), (2, 13), (1, 13), (3, 14), (2, 14), (3, 15), (0, 12), (1, 14)):
        put(img, x, y, color if (x + y) % 2 else shade(color, 0.8))


def raw_fish():
    img = clear()
    cells = fish_mask()
    for (x, y), (t, s) in cells.items():
        # s=0 是背，s=1 是腹：背上深蓝绿，往下银白
        if s < 0.45:
            c = mix(BACK, FLANK, (s / 0.45) ** 1.5)
        else:
            c = mix(FLANK, BELLY, min(1, (s - 0.45) / 0.4))
        # 背上的深色波纹（鲭鱼的斑纹）
        if s < 0.4 and (x * 2 + y) % 4 == 0:
            c = shade(c, 0.72)
        put(img, x, y, c)
    # 侧线上的发光点
    for t in (0.3, 0.42, 0.54, 0.66):
        best = min(cells, key=lambda p: abs(cells[p][0] - t) + abs(cells[p][1] - 0.5))
        put(img, best[0], best[1], GLOW)
    tail(img, FIN)
    # 背鳍和腹鳍
    for (x, y) in ((6, 5), (7, 4), (5, 6)):
        put(img, x, y, shade(FIN, 0.9))
    for (x, y) in ((10, 10), (9, 11)):
        put(img, x, y, shade(FIN, 1.1))
    # 眼睛和鳃盖
    put(img, 12, 4, (16, 20, 24))
    put(img, 11, 4, (214, 224, 224))
    line(img, 10, 5, 11, 7, shade(FLANK, 0.72))
    return img


def cooked_fish():
    img = clear()
    cells = fish_mask()
    crust = (186, 120, 58)
    for (x, y), (t, s) in cells.items():
        c = mix(shade(crust, 0.74), mix(crust, (234, 198, 134), 0.6), min(1, s * 1.3))
        put(img, x, y, shade(c, 1 + 0.08 * math.sin(x * 1.7 + y)))
    # 两道烤痕
    for (x0, y0) in ((4, 8), (7, 5)):
        for i in range(6):
            x, y = x0 + i, y0 + i
            if (x, y) in cells:
                put(img, x, y, (92, 52, 28))
    tail(img, (140, 82, 40))
    put(img, 12, 4, (60, 40, 30))
    return img


# ---- 海带

KELP = (122, 112, 52)
KELP_DARK = (84, 76, 36)
POD = (226, 168, 64)
POD_LIT = (255, 226, 140)


def candle_kelp(ripe, rng):
    img = clear()
    # 几片叶子从底下的柄往上飘，叶片宽 2 格，左右缓缓摆
    fronds = ((5, 1, 0.0, 1.6), (8, 0, 1.4, 1.2), (11, 2, 2.6, 1.8), (3, 5, 4.0, 1.0), (13, 6, 5.1, 1.0))
    pods = []
    for (x0, top, phase, sway) in fronds:
        for y in range(15, top - 1, -1):
            t = (15 - y) / (15 - top)
            x = round(x0 + sway * math.sin(t * 2.4 + phase) * t)
            c = mix(KELP_DARK, KELP, 0.4 + 0.6 * t)
            put(img, x, y, c)
            if t > 0.15:
                put(img, x + 1, y, shade(c, 0.82))
            # 叶片上的皱褶
            if y % 3 == 0:
                put(img, x, y, shade(c, 1.15))
        # 气囊长在叶片根上（离底下三到六格）
        py = 15 - rng.randint(3, 6)
        t = (15 - py) / (15 - top)
        pods.append((round(x0 + sway * math.sin(t * 2.4 + phase) * t) + rng.choice((-1, 2)), py))
    if ripe:
        for (x, y) in pods[:4]:
            blob(img, x, y, 1.2, 1.4, POD, 235, light=0.3)
            put(img, x, y, POD_LIT)
            put(img, x, y + 2, KELP_DARK)
    else:
        for (x, y) in pods[:4]:
            put(img, x, y + 1, shade(KELP_DARK, 0.9))
    return img


def kelp_pod():
    img = clear()
    # 柄和一截叶片根
    line(img, 3, 15, 6, 10, KELP_DARK)
    line(img, 4, 15, 7, 10, KELP)
    for (x, y) in ((2, 13), (1, 12), (2, 12), (5, 15)):
        put(img, x, y, KELP)
    # 半透明的气囊，里面一点亮光
    blob(img, 9.5, 7, 4.3, 4.6, POD, 225, light=0.35)
    blob(img, 9, 6.5, 2.0, 2.2, mix(POD, POD_LIT, 0.6), 235, light=0.1)
    put(img, 8, 5, (255, 246, 210))
    put(img, 7, 4, (255, 236, 190), 200)
    return img


# ---- 活珍珠

def living_pearl():
    img = clear()
    base = (238, 230, 214)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8.5
            d = math.hypot(dx, dy)
            if d <= 5.4:
                rim = d / 5.4
                # 晕彩：边上按角度泛粉、泛青
                a = math.atan2(dy, dx)
                tint = mix((246, 196, 214), (176, 226, 230), (math.sin(a * 2 + 0.6) + 1) / 2)
                c = mix(base, tint, rim ** 2 * 0.8)
                c = shade(c, 1.06 - 0.22 * rim * (0.6 + 0.4 * (dx + dy) / max(d, 0.1) / 1.4))
                put(img, x, y, c)
    for (x, y, k) in ((6, 6, 0.7), (5, 7, 0.5), (6, 5, 0.4), (7, 6, 0.3)):
        put(img, x, y, mix(img.getpixel((x, y))[:3], (255, 255, 255), k))
    return img


# ---- 刷怪蛋

def egg(base, spot, seed):
    img = clear()
    rng = random.Random(seed)
    spots = [(rng.uniform(4, 12), rng.uniform(4, 14), rng.uniform(0.6, 1.5)) for _ in range(6)]
    for y in range(1, N):
        for x in range(N):
            py = (y + 0.5 - 9.0) / 6.6
            rx = 5.2 * (1 - 0.18 * max(0, -py))
            if py * py + ((x + 0.5 - 8) / rx) ** 2 <= 1:
                c = spot if any((x - sx) ** 2 + (y - sy) ** 2 < r * r for sx, sy, r in spots) else base
                put(img, x, y, shade(c, 1.15 - (x * 0.5 + y) * 0.018))
    for (x, y) in ((5, 5), (5, 6), (6, 4)):
        if filled(img, x, y):
            put(img, x, y, mix(img.getpixel((x, y))[:3], (255, 255, 255), 0.55))
    return img


# ---- 潜艇的物品图标

HULL = (222, 226, 228)
STRIPE = (232, 122, 42)
DOME = (40, 70, 110)


def submarine():
    img = clear()
    # 艇身：横放的长圆，头在右
    for y in range(N):
        for x in range(N):
            dx, dy = (x + 0.5 - 7.5) / 6.6, (y + 0.5 - 9.5) / 3.4
            if dx * dx + dy * dy <= 1:
                c = shade(HULL, 1.08 - 0.25 * max(0, dy) - 0.05 * dx)
                put(img, x, y, c)
    # 橙色的条
    for x in range(2, 14):
        if filled(img, x, 10):
            put(img, x, 10, STRIPE)
            put(img, x, 11, shade(STRIPE, 0.8))
    # 玻璃罩
    blob(img, 9.5, 6.6, 2.6, 1.9, DOME, light=0.35)
    put(img, 9, 6, (150, 200, 230))
    put(img, 8, 6, (110, 160, 200))
    # 艇头的灯
    put(img, 14, 9, (255, 240, 170))
    put(img, 14, 10, (240, 210, 120))
    # 尾舵和螺旋桨
    for y in range(6, 14):
        put(img, 1, y, (90, 96, 104) if y not in (9, 10) else (60, 64, 70))
    put(img, 0, 8, (120, 126, 134))
    put(img, 0, 11, (120, 126, 134))
    line(img, 2, 7, 3, 7, shade(HULL, 0.8))
    line(img, 2, 12, 3, 12, shade(HULL, 0.7))
    return img


# ---- 两道菜

def kelp_crisps(rng):
    img = clear()
    sheet = (54, 84, 44)
    for (x0, y0, w, h) in ((2, 8, 9, 5), (5, 5, 9, 5), (3, 10, 10, 4)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if (x == x0 or x == x0 + w - 1) and (y == y0 or y == y0 + h - 1):
                    continue
                put(img, x, y, shade(sheet, rng.uniform(0.85, 1.2)))
        for x in range(x0 + 1, x0 + w - 1):
            put(img, x, y0, shade(sheet, 1.35))
    # 一点盐粒
    for _ in range(6):
        x, y = rng.randrange(3, 13), rng.randrange(5, 14)
        if filled(img, x, y):
            put(img, x, y, (226, 228, 220))
    return img


def seafood_roll(rng):
    img = clear()
    wrap = (40, 66, 38)
    flesh = (236, 136, 112)
    fat = (250, 214, 200)
    # 一整卷斜放在后面
    for i in range(8):
        blob(img, 4 + i, 8 - i * 0.55, 2.4, 2.4, wrap, light=0.25)
    # 切下来的两片：外面一圈海带，里面是一卷粉色的鱼肉，带白色的脂肪纹
    for (cx, cy) in ((5.5, 11), (11, 11.5)):
        blob(img, cx, cy, 3.2, 3.0, wrap, light=0.1)
        blob(img, cx, cy, 2.4, 2.2, flesh, light=0.2)
        for k in range(3):
            put(img, round(cx) - 1 + k, round(cy) - 1 + (k % 2), fat)
    return img


# ---- 甲壳蚌

SHELL = (198, 150, 136)
SHELL_DARK = (128, 88, 78)
NACRE = (232, 226, 236)
MANTLE = (236, 168, 112)
ALGAE = (96, 130, 70)
BARNACLE = (214, 210, 196)


def clam_top(rng):
    """壳顶：从后面的铰合处往前放射的粗肋（肋凸起、肋沟深），边缘一圈波浪，一圈圈浅色的生长纹。"""
    img = new(SHELL)
    for y in range(N):
        for x in range(N):
            # 铰合处在下边中间（模型里朝南的一边）
            dx, dy = x + 0.5 - 8, 17 - (y + 0.5)
            a = math.atan2(dx, dy)
            r = math.hypot(dx, dy)
            rib = math.cos(a * 26)
            c = mix(SHELL_DARK, mix(SHELL, (232, 196, 180), 0.4), 0.5 + 0.5 * rib)
            if int(r) % 4 == 0:
                c = mix(c, (240, 214, 200), 0.35)
            put(img, x, y, shade(c, 1 + rng.uniform(-0.04, 0.04)))
    for _ in range(4):
        x, y = rng.randrange(1, 14), rng.randrange(1, 14)
        put(img, x, y, BARNACLE)
        put(img, x + 1, y, shade(BARNACLE, 0.8))
        put(img, x, y + 1, shade(BARNACLE, 0.7))
    for _ in range(9):
        put(img, rng.randrange(0, 16), rng.randrange(0, 6), ALGAE, 255)
    return img


def clam_side(rng):
    """壳侧：上下两片壳，中间一道波浪形的合缝，壳上一圈圈生长纹。"""
    img = new(SHELL)
    for y in range(N):
        for x in range(N):
            growth = 0.9 + 0.12 * ((y % 3) == 0)
            put(img, x, y, shade(SHELL, growth + rng.uniform(-0.05, 0.05)))
    # 合缝在第 7–8 行附近（模型里两片壳的接缝），波浪形
    for x in range(N):
        y = 7 + (1 if (x // 2) % 2 else 0)
        put(img, x, y, shade(SHELL_DARK, 0.7))
        put(img, x, y + 1, shade(SHELL, 1.2))
    for _ in range(5):
        put(img, rng.randrange(0, 16), rng.randrange(9, 16), ALGAE)
    return img


def clam_inner(rng):
    """壳里：外面一圈珍珠层，中间淡橙色的外套膜。"""
    img = new(NACRE)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            a = math.atan2(y - 8, x - 8)
            sheen = mix(NACRE, mix((240, 206, 222), (196, 228, 236), (math.sin(a * 3) + 1) / 2), 0.4)
            if d < 5.5:
                c = shade(MANTLE, 1.0 + 0.1 * math.sin(d * 2.2) + rng.uniform(-0.05, 0.05))
            else:
                c = shade(sheen, 1 + rng.uniform(-0.03, 0.03))
            put(img, x, y, c)
    return img


# ---- 海底密封箱

PLATE = (78, 112, 112)
RUST = (138, 92, 58)
SEAL = (110, 236, 220)


def crate_base(rng):
    img = new(PLATE)
    noise(img, rng, 0.05)
    for i in range(N):
        put(img, i, 0, shade(PLATE, 1.25))
        put(img, 0, i, shade(PLATE, 1.15))
        put(img, i, N - 1, shade(PLATE, 0.6))
        put(img, N - 1, i, shade(PLATE, 0.7))
    # 四角的铆钉
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, shade(PLATE, 1.45))
        put(img, x + 1, y + 1, shade(PLATE, 0.55))
    # 锈斑和绿藻
    for _ in range(10):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        put(img, x, y, shade(RUST, rng.uniform(0.8, 1.1)))
    for _ in range(12):
        x, y = rng.randrange(0, 16), rng.randrange(10, 16)
        put(img, x, y, shade(ALGAE, rng.uniform(0.8, 1.2)))
    return img


def sealed_crate():
    front = crate_base(random.Random(2601))
    # 一圈封条：微微发青光
    for x in range(3, 13):
        put(front, x, 4, SEAL)
        put(front, x, 11, SEAL)
    for y in range(4, 12):
        put(front, 3, y, SEAL)
        put(front, 12, y, SEAL)
    # 中间的锁盘
    for y in range(6, 10):
        for x in range(6, 10):
            put(front, x, y, (60, 70, 74) if (x in (6, 9) or y in (6, 9)) else (36, 40, 44))
    put(front, 7, 7, SEAL)
    put(front, 8, 8, shade(SEAL, 0.7))
    save(front, BLOCK, "sealed_crate_front")
    side = crate_base(random.Random(2602))
    for x in range(1, 15):
        put(side, x, 7, shade(PLATE, 0.62))
        put(side, x, 8, shade(PLATE, 1.2))
    for x in range(5, 11):
        put(side, x, 7, shade(SEAL, 0.6))
    save(side, BLOCK, "sealed_crate_side")
    top = crate_base(random.Random(2603))
    for i in range(3, 13):
        put(top, i, i, shade(PLATE, 0.7))
        put(top, 15 - i, i, shade(PLATE, 0.7))
    for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
        put(top, x, y, shade(SEAL, 0.8))
    save(top, BLOCK, "sealed_crate_top")


def main():
    for name, draw in (("alien_fish", raw_fish), ("cooked_alien_fish", cooked_fish), ("kelp_pod", kelp_pod),
                       ("living_pearl", living_pearl), ("submarine", submarine)):
        img = draw()
        outline(img, (30, 30, 36))
        save(img, ITEM, name)
    img = egg((40, 104, 150), (96, 226, 206), 2611)
    outline(img, (20, 34, 48))
    save(img, ITEM, "aquatic_creature_spawn_egg")
    for name, draw in (("kelp_crisps", kelp_crisps), ("seafood_roll", seafood_roll)):
        img = draw(random.Random(sum(map(ord, name))))
        outline(img, (40, 32, 30))
        save(img, ITEM, name)
    for ripe in (True, False):
        save(candle_kelp(ripe, random.Random(2621)), BLOCK, "candle_kelp" if ripe else "candle_kelp_harvested")
    save(clam_top(random.Random(2631)), BLOCK, "clam_top")
    save(clam_side(random.Random(2632)), BLOCK, "clam_side")
    save(clam_inner(random.Random(2633)), BLOCK, "clam_inner")
    sealed_crate()


if __name__ == "__main__":
    main()
