"""生成星球方块的灰度贴图（16x16，可无缝平铺）。

星球方块在游戏里按当前星球的配色染色（技术架构 §3.4），所以贴图只画明暗和纹理，不带颜色。
平均亮度控制在 0.8 左右：染色是逐通道相乘，太暗会把配色压得发黑。

用法：python tools/textures/gen_planet_textures.py [--preview 预览图路径]
输出到 src/main/resources/assets/universe/textures/block/。随机数种子固定，重复运行结果相同。
"""

import argparse
import os

import numpy as np
from PIL import Image

SIZE = 16
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "universe",
                       "textures", "block")


def periodic_value_noise(rng, period, size=SIZE):
    """周期为 period 个格子的值噪声，在 size×size 上无缝平铺。"""
    grid = rng.random((period, period))
    coords = np.arange(size) * period / size
    i0 = np.floor(coords).astype(int)
    t = coords - i0
    t = t * t * (3 - 2 * t)  # smoothstep
    i1 = (i0 + 1) % period
    i0 = i0 % period
    # 先沿 x 插值，再沿 y 插值
    a = grid[np.ix_(i0, i0)]
    b = grid[np.ix_(i0, i1)]
    c = grid[np.ix_(i1, i0)]
    d = grid[np.ix_(i1, i1)]
    tx = t[None, :]
    ty = t[:, None]
    top = a + (b - a) * tx
    bottom = c + (d - c) * tx
    return top + (bottom - top) * ty


def fbm(rng, periods, weights):
    total = np.zeros((SIZE, SIZE))
    for period, weight in zip(periods, weights):
        total += periodic_value_noise(rng, period) * weight
    return total / sum(weights)


def spread_points(rng, count, min_distance):
    """环面上彼此至少相隔 min_distance 的随机点，避免 Voronoi 出现细长的碎块。"""
    points = []
    attempts = 0
    while len(points) < count and attempts < 5000:
        attempts += 1
        p = rng.random(2) * SIZE
        ok = True
        for q in points:
            d = np.abs(p - q)
            d = np.minimum(d, SIZE - d)
            if np.hypot(*d) < min_distance:
                ok = False
                break
        if ok:
            points.append(p)
    return np.array(points)


def voronoi(rng, count, min_distance=3.5):
    """环面上的 Voronoi：返回 (到最近点的距离, 到次近点的距离, 所属点的序号)。"""
    points = spread_points(rng, count, min_distance)
    ys, xs = np.mgrid[0:SIZE, 0:SIZE] + 0.5
    d1 = np.full((SIZE, SIZE), 1e9)
    d2 = np.full((SIZE, SIZE), 1e9)
    owner = np.zeros((SIZE, SIZE), dtype=int)
    for index, (px, py) in enumerate(points):
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, SIZE - dx)
        dy = np.minimum(dy, SIZE - dy)
        dist = np.sqrt(dx * dx + dy * dy)
        closer = dist < d1
        d2 = np.where(closer, d1, np.minimum(d2, dist))
        owner = np.where(closer, index, owner)
        d1 = np.where(closer, dist, d1)
    return d1, d2, owner


def normalize(values, low, high):
    v = (values - values.min()) / max(values.max() - values.min(), 1e-9)
    return low + v * (high - low)


def quantize(image, levels):
    """压到有限几级灰度：原版的方块贴图一般只有 4–6 种明暗，渐变太平滑反而不像方块。"""
    low, high = float(image.min()), float(image.max())
    steps = np.round((image - low) / max(high - low, 1e-9) * (levels - 1))
    return low + steps / (levels - 1) * (high - low)


def speckle(rng, image, chance, amount):
    mask = rng.random((SIZE, SIZE)) < chance
    sign = np.where(rng.random((SIZE, SIZE)) < 0.5, -1.0, 1.0)
    return np.where(mask, image + sign * amount, image)


def rock(rng):
    base = fbm(rng, [2, 4, 8, 16], [0.35, 0.3, 0.2, 0.15])
    image = normalize(base, 0.66, 0.94)
    # 细裂纹：Voronoi 边界上压暗一点
    d1, d2, _ = voronoi(rng, 7)
    crack = np.clip((d2 - d1) / 1.2, 0, 1)
    image = image * (0.86 + 0.14 * crack)
    return quantize(speckle(rng, image, 0.08, 0.06), 6)


def cobblestone(rng):
    d1, d2, owner = voronoi(rng, 9, 4.6)
    count = owner.max() + 1
    shade = rng.random(count) * 0.1 + 0.82
    image = shade[owner]
    # 每块石头左上亮、右下暗，像是有体积
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    lit = np.zeros((SIZE, SIZE))
    for index in range(count):
        mask = owner == index
        if not mask.any():
            continue
        cy = ys[mask].mean()
        cx = xs[mask].mean()
        lit = np.where(mask, ((cx - xs) + (cy - ys)) * 0.02, lit)
    image = image + np.clip(lit, -0.07, 0.07)
    image = quantize(image + (rng.random((SIZE, SIZE)) - 0.5) * 0.05, 5)
    mortar = (d2 - d1) < 0.7
    return np.where(mortar, 0.62, image)


def soil(rng):
    base = fbm(rng, [4, 8, 16], [0.25, 0.35, 0.4])
    image = normalize(base, 0.64, 0.92)
    image = speckle(rng, image, 0.12, 0.1)
    # 零星的小石子
    pebbles = rng.random((SIZE, SIZE)) < 0.03
    return quantize(np.where(pebbles, 0.98, image), 6)


def turf_top(rng):
    base = fbm(rng, [4, 8, 16], [0.2, 0.3, 0.5])
    image = normalize(base, 0.62, 0.96)
    # 草叶：随机的亮点和暗点交错
    blades = rng.random((SIZE, SIZE))
    image = np.where(blades < 0.12, image - 0.12, image)
    image = np.where(blades > 0.9, image + 0.06, image)
    return quantize(image, 6)


def turf_side_overlay(rng):
    """侧面上沿的草皮：返回 (灰度, 透明度)。底下的泥土另用土壤贴图。"""
    depth = 3 + (rng.random(SIZE) * 3).astype(int)  # 每一列垂下来 3–5 个像素
    for _ in range(2):  # 平滑一下，免得太碎
        depth = np.round((depth + np.roll(depth, 1) + np.roll(depth, -1)) / 3).astype(int)
    alpha = np.zeros((SIZE, SIZE))
    for x in range(SIZE):
        alpha[:depth[x], x] = 1.0
        if rng.random() < 0.35:  # 偶尔多垂一根草
            alpha[depth[x], x] = 1.0
    image = quantize(normalize(fbm(rng, [8, 16], [0.4, 0.6]), 0.66, 0.96), 5)
    return image, alpha


def regolith(rng):
    base = fbm(rng, [4, 8, 16], [0.2, 0.3, 0.5])
    image = normalize(base, 0.7, 0.9)
    image = speckle(rng, image, 0.18, 0.07)
    pebbles = rng.random((SIZE, SIZE))
    image = np.where(pebbles < 0.035, 0.6, image)
    return quantize(np.where(pebbles > 0.975, 0.98, image), 6)


def sand(rng):
    base = fbm(rng, [8, 16], [0.4, 0.6])
    image = normalize(base, 0.78, 0.95)
    return quantize(speckle(rng, image, 0.2, 0.05), 5)


def snow(rng):
    base = fbm(rng, [4, 8, 16], [0.3, 0.3, 0.4])
    image = normalize(base, 0.88, 1.0)
    return quantize(speckle(rng, image, 0.05, 0.03), 4)


def ice(rng):
    base = fbm(rng, [2, 4, 8], [0.5, 0.3, 0.2])
    image = quantize(normalize(base, 0.74, 0.92), 4)
    # 斜向的亮条纹和白色裂纹
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    streak = ((xs + ys) % 8 == 0) & (rng.random((SIZE, SIZE)) < 0.7)
    image = np.where(streak, image + 0.06, image)
    d1, d2, _ = voronoi(rng, 4)
    crack = (d2 - d1) < 0.55
    return np.where(crack, 0.98, image)


def ash(rng):
    base = fbm(rng, [4, 8, 16], [0.4, 0.3, 0.3])
    image = normalize(base, 0.68, 0.94)
    image = speckle(rng, image, 0.15, 0.09)
    embers = rng.random((SIZE, SIZE)) < 0.02
    return quantize(np.where(embers, 1.0, image), 6)


def crystal(rng):
    d1, d2, owner = voronoi(rng, 6)
    shade = rng.random(6) * 0.3 + 0.68
    image = shade[owner]
    # 晶面边缘高光
    edge = np.clip(1 - (d2 - d1) / 0.9, 0, 1)
    image = np.maximum(image, edge * 1.0)
    image += (1 - np.clip(d1 / 5.0, 0, 1)) * 0.1
    return quantize(image, 6)


def strata(rng):
    """岩层：横向的细纹理，颜色由游戏按方块高度分层染色，贴图只给层内的细密纹路。"""
    rows = np.zeros((SIZE, SIZE))
    # 每行一个亮度，相邻行略有差别，像一层层压实的沉积
    row_shade = periodic_value_noise(rng, 8)[:, :1] * 0.5 + rng.random((SIZE, 1)) * 0.5
    rows += row_shade
    streak = periodic_value_noise(rng, 2) * 0.3 + periodic_value_noise(rng, 4) * 0.2
    image = normalize(rows + streak * 0.6, 0.7, 0.95)
    return quantize(speckle(rng, image, 0.06, 0.05), 5)


def to_image(gray, alpha=None):
    g = (np.clip(gray, 0, 1) * 255).round().astype(np.uint8)
    if alpha is None:
        return Image.fromarray(g, "L").convert("RGBA")
    a = (np.clip(alpha, 0, 1) * 255).round().astype(np.uint8)
    rgba = np.stack([g, g, g, a], axis=-1)
    return Image.fromarray(rgba, "RGBA")


TEXTURES = {
    "planet_rock": rock,
    "planet_cobblestone": cobblestone,
    "planet_soil": soil,
    "planet_turf_top": turf_top,
    "planet_regolith": regolith,
    "planet_sand": sand,
    "planet_snow": snow,
    "planet_ice": ice,
    "planet_ash": ash,
    "planet_crystal": crystal,
    "planet_strata": strata,
}


def generate():
    images = {}
    for index, (name, func) in enumerate(TEXTURES.items()):
        rng = np.random.default_rng(1000 + index)
        images[name] = to_image(func(rng))
    gray, alpha = turf_side_overlay(np.random.default_rng(2000))
    images["planet_turf_side_overlay"] = to_image(gray, alpha)
    return images


def tinted(image, color):
    """按游戏的方式染色：灰度逐通道乘以颜色。"""
    arr = np.asarray(image).astype(float) / 255
    rgb = np.array([(color >> 16) & 255, (color >> 8) & 255, color & 255]) / 255
    out = arr.copy()
    out[..., :3] = arr[..., :3] * rgb
    return Image.fromarray((out * 255).round().astype(np.uint8), "RGBA")


def preview(images, path):
    """每种贴图 2×2 平铺、放大 4 倍，配几组示例颜色，检查接缝和观感。"""
    palettes = [0xFFFFFF, 0x7CBD6B, 0xB45A3C, 0x8E7BD6, 0xD9E4F0, 0x3C3230]
    names = list(images)
    tile = SIZE * 2 * 4
    sheet = Image.new("RGBA", (tile * len(palettes), tile * len(names)), (40, 40, 40, 255))
    for row, name in enumerate(names):
        for col, color in enumerate(palettes):
            img = images[name]
            if name == "planet_turf_side_overlay":
                base = tinted(images["planet_soil"], 0x866043)
                img = Image.alpha_composite(base, tinted(img, color))
            else:
                img = tinted(img, color)
            tiled = Image.new("RGBA", (SIZE * 2, SIZE * 2))
            for dx in (0, SIZE):
                for dy in (0, SIZE):
                    tiled.paste(img, (dx, dy))
            sheet.paste(tiled.resize((tile, tile), Image.NEAREST), (col * tile, row * tile))
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview")
    args = parser.parse_args()
    images = generate()
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, image in images.items():
        image.save(os.path.join(OUT_DIR, name + ".png"))
    if args.preview:
        preview(images, args.preview)
    print("generated", len(images), "textures in", os.path.normpath(OUT_DIR))


if __name__ == "__main__":
    main()
