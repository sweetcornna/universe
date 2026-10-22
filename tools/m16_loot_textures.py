"""生成 M16（ADR-0028 §六）黑雾掉落的五种零件的贴图。

用法：python tools/m16_loot_textures.py
能量碎片是青蓝色的碎晶，硅基神经元是带突触的暗红芯片，物质重组器是嵌着橙色环的深灰球，
负熵奇点是一团紫黑的漩涡，核心素是一块发红光的八面晶体。等级越高的颜色越亮、越不像地上能捡的东西。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import ITEM, N, shade, mix, clear, put, outline, noise, save  # noqa: E402


def energy_shard():
    """能量碎片：三块斜着的碎晶，青蓝色，边上亮。"""
    rng = random.Random(1611)
    img = clear()
    shards = ((5.0, 9.0, 2.2, 5.5), (10.0, 6.0, 1.8, 4.5), (9.0, 12.0, 1.4, 2.8))
    for cx, cy, half, length in shards:
        for y in range(N):
            for x in range(N):
                # 沿 45° 方向拉长的菱形
                u = ((x - cx) + (y - cy)) / math.sqrt(2)
                v = ((x - cx) - (y - cy)) / math.sqrt(2)
                if abs(u) / length + abs(v) / half <= 1:
                    edge = abs(u) / length + abs(v) / half
                    put(img, x, y, mix((200, 250, 255), (40, 150, 230), edge * 0.9 + (0.1 if v > 0 else 0)))
    noise(img, rng, 0.05)
    put(img, 4, 8, (255, 255, 255))
    put(img, 10, 5, (255, 255, 255))
    outline(img, (12, 36, 64))
    save(img, ITEM, "energy_shard")


def silicon_neuron():
    """硅基神经元：中间一块暗红的芯片，四周伸出发亮的突触。"""
    rng = random.Random(1612)
    img = clear()
    # 突触：从中心往外的折线，末端一个亮点
    for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        x, y = 7.5, 7.5
        for step in range(7):
            x += dx + (rng.choice((-0.4, 0, 0.4)) if dx == 0 else 0)
            y += dy + (rng.choice((-0.4, 0, 0.4)) if dy == 0 else 0)
            put(img, int(round(x)), int(round(y)), mix((255, 120, 140), (120, 40, 60), step / 7))
        put(img, int(round(x)), int(round(y)), (255, 210, 220))
    # 芯片
    for y in range(5, 11):
        for x in range(5, 11):
            put(img, x, y, shade((110, 36, 54), 1 + rng.uniform(-0.08, 0.08)))
    for i in range(5, 11):
        put(img, i, 5, (160, 64, 84))
        put(img, 5, i, (160, 64, 84))
        put(img, i, 10, (60, 18, 30))
        put(img, 10, i, (60, 18, 30))
    put(img, 7, 7, (255, 150, 170))
    put(img, 8, 8, (255, 90, 120))
    outline(img, (30, 8, 16))
    save(img, ITEM, "silicon_neuron")


def matter_recombinator():
    """物质重组器：深灰的球，赤道上一圈橙色的环，中间透出光。"""
    rng = random.Random(1613)
    img = clear()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.2:
                continue
            light = 1.25 - 0.08 * math.hypot(x - 5.5, y - 5.0)
            put(img, x, y, shade((84, 88, 98), max(0.55, light)))
    # 环：斜着的椭圆带
    for y in range(N):
        for x in range(N):
            u = (x - 7.5) * 0.92 + (y - 7.5) * 0.38
            v = -(x - 7.5) * 0.38 + (y - 7.5) * 0.92
            ring = (u / 7.4) ** 2 + (v / 3.0) ** 2
            if 0.45 <= ring <= 1.0:
                front = v > -0.5
                put(img, x, y, (255, 168, 64) if front else (170, 96, 40))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(img, x, y, (255, 214, 140))
    noise(img, rng, 0.04)
    outline(img, (24, 22, 28))
    save(img, ITEM, "matter_recombinator")


def negentropy_singularity():
    """负熵奇点：紫黑色的漩涡，中心一点白光。"""
    img = clear()
    for y in range(N):
        for x in range(N):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 7:
                continue
            a = math.atan2(dy, dx)
            # 三条旋臂
            arm = (math.cos(3 * a + d * 0.9) + 1) / 2
            t = d / 7
            base = mix((40, 10, 60), (12, 4, 20), t)
            c = mix(base, (200, 120, 255), arm * (1 - t) * 0.9)
            alpha = 255 if d < 6 else int(255 * (7 - d))
            put(img, x, y, c, max(alpha, 0))
    for (x, y) in ((7, 7), (8, 8)):
        put(img, x, y, (255, 255, 255))
    for (x, y) in ((8, 7), (7, 8)):
        put(img, x, y, (230, 200, 255))
    save(img, ITEM, "negentropy_singularity")


def core_element():
    """核心素：发红光的八面晶体，上半亮、下半暗。"""
    img = clear()
    for y in range(1, 15):
        half = 6 - abs(y - 7.5) * 0.85
        for x in range(N):
            if abs(x - 7.5) > half:
                continue
            left = x < 7.5
            upper = y < 7.5
            f = (1.25 if upper else 0.8) * (1.1 if left else 0.9)
            c = mix((255, 236, 220), (220, 40, 52), min(1, abs(x - 7.5) / max(half, 1) * 0.7 + 0.3))
            put(img, x, y, shade(c, f))
    for y in range(4, 11):
        put(img, 7, y, (255, 200, 190))
    put(img, 7, 6, (255, 255, 255))
    outline(img, (60, 8, 14))
    save(img, ITEM, "core_element")


def main():
    energy_shard()
    silicon_neuron()
    matter_recombinator()
    negentropy_singularity()
    core_element()


if __name__ == "__main__":
    main()
