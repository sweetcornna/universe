"""生成界面用的贴图（星图里的恒星光晕、选中圈）。

用法：python tools/gui_textures.py [输出目录]
默认输出到 src/main/resources/assets/universe/textures/gui。

贴图都是白色加透明度，画的时候再乘上颜色。
"""
import math
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures", "gui")
N = 64


def save(name, alpha):
    image = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            a = max(0.0, min(1.0, alpha(x, y)))
            image.putpixel((x, y), (255, 255, 255, int(round(a * 255))))
    image.save(os.path.join(OUT, name))


def radius(x, y):
    # 到中心的距离，边缘是 1
    c = (N - 1) / 2
    return math.hypot(x - c, y - c) / (N / 2)


def star_glow(x, y):
    """恒星：亮的核心加一圈柔和的光晕，边缘淡到全透明。"""
    r = radius(x, y)
    core = math.exp(-(r / 0.16) ** 2)
    halo = 0.45 * math.exp(-(r / 0.42) ** 2)
    fade = max(0.0, 1 - r) ** 0.5
    return (core + halo) * fade


def ring(x, y):
    """选中圈：细圆环，内外边缘柔化。"""
    r = radius(x, y)
    return math.exp(-((r - 0.82) / 0.06) ** 2)


def disc(x, y):
    """实心圆，边缘抗锯齿（行星、黑洞的黑色圆面）。"""
    r = radius(x, y)
    return max(0.0, min(1.0, (0.95 - r) * N / 2))


os.makedirs(OUT, exist_ok=True)
save("star_glow.png", star_glow)
save("ring.png", ring)
save("disc.png", disc)
print("ok")
