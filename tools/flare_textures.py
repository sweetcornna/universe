"""生成太空里看恒星时的眩光贴图（ADR-0021）：光晕、衍射星芒、镜头鬼影、横向光带。

用法：python tools/flare_textures.py [输出目录]
默认输出到 src/main/resources/assets/universe/textures/gui/flare。

贴图是白色（带颜色的鬼影是归一化的颜色）加透明度，透明度就是亮度：用叠加混合的管线画（颜色乘透明度再加到屏幕上），
画的时候再乘上恒星的颜色。每张贴图旁边写一个 .mcmeta，打开线性过滤，放大了也不出锯齿。
"""
import json
import math
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures", "gui", "flare")


def save(name, width, height, value):
    image = Image.new("RGBA", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            # 像素中心，坐标范围 -1..1
            u = (x + 0.5) / width * 2 - 1
            v = (y + 0.5) / height * 2 - 1
            c = value(u, v)
            if isinstance(c, float):
                c = (c, c, c)
            c = [max(0.0, min(1.0, k)) for k in c]
            peak = max(c)
            if peak <= 0:
                pixels[x, y] = (255, 255, 255, 0)
                continue
            pixels[x, y] = tuple(int(round(k / peak * 255)) for k in c) + (int(round(peak * 255)),)
    image.save(os.path.join(OUT, name))
    with open(os.path.join(OUT, name + ".mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)
        f.write("\n")


def fade(r):
    """贴图边上淡到 0，看不出方框。"""
    return max(0.0, 1 - r) ** 1.5


def glow(u, v):
    """恒星周围的光晕：一个很亮的核心，外面是缓慢衰减的散射光。"""
    r = math.hypot(u, v)
    core = math.exp(-(r / 0.04) ** 2)
    inner = 0.5 * math.exp(-r / 0.12)
    outer = 0.28 / (1 + (r / 0.2) ** 2)
    return (core + inner + outer) * fade(r)


def spikes(u, v):
    """衍射星芒：六条细长的光芒（六边形的光圈），根部亮，越往外越细越淡。"""
    r = math.hypot(u, v)
    if r < 1e-6:
        return 1.0
    angle = math.atan2(v, u)
    value = 0.0
    for k in range(6):
        a = angle - k * math.pi / 3
        # 到这条光芒中线的距离（按角度换算成弧长）
        across = abs(math.sin(a)) * r
        along = math.cos(a) * r
        if along <= 0:
            continue
        width = 0.004 + 0.012 * along
        value += math.exp(-(across / width) ** 2) * math.exp(-along / 0.32)
    # 很细的一圈彩虹似的绕射环，几乎看不出
    halo = 0.06 * math.exp(-((r - 0.36) / 0.05) ** 2)
    return (value * 0.9 + halo) * fade(r)


def ghost(u, v):
    """镜头鬼影：边缘稍亮的六边形，里面是很淡的一片。"""
    angle = math.atan2(v, u)
    r = math.hypot(u, v)
    # 六边形：按角度把半径拉到正六边形的边上
    sector = (angle + math.pi / 6) % (math.pi / 3) - math.pi / 6
    hex_r = r * math.cos(sector) / math.cos(math.pi / 6)
    edge = 0.85
    body = 1.0 / (1 + math.exp((hex_r - edge) / 0.02))
    rim = math.exp(-((hex_r - edge) / 0.05) ** 2)
    value = body * (0.35 + 0.25 * hex_r) + 0.4 * rim
    return value * (1 - min(1.0, max(0.0, (hex_r - 0.95) / 0.05)))


def ring(u, v):
    """大圈的鬼影：一道细圆环，内侧带一点颜色。"""
    r = math.hypot(u, v)
    red = math.exp(-((r - 0.86) / 0.05) ** 2)
    green = math.exp(-((r - 0.83) / 0.05) ** 2)
    blue = math.exp(-((r - 0.80) / 0.05) ** 2)
    return (red * 0.9, green * 0.8, blue)


def streak(u, v):
    """横向的光带：很扁，中间亮，往两头淡出。"""
    across = math.exp(-(v / 0.12) ** 2)
    along = math.exp(-abs(u) / 0.25) * 0.7 + 0.3 * max(0.0, 1 - abs(u)) ** 2
    return across * along


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save("glow.png", 256, 256, glow)
    save("spikes.png", 256, 256, spikes)
    save("ghost.png", 64, 64, ghost)
    save("ring.png", 128, 128, ring)
    save("streak.png", 256, 32, streak)
