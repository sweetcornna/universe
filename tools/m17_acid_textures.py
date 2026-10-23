"""生成 M17（ADR-0030 §一）酸液的贴图。

用法：python tools/m17_acid_textures.py

酸液的颜色由 FluidTintSource 按星球的液体色算（core.survival.Acid.color），所以贴图画成接近白的灰度，
让染色透出来；只在亮度上做出流动的纹理。尺寸照原版的水：静止 16×512（32 帧），流动 32×1024（32 帧）。

- 静止：两层缓慢漂移的正弦波叠在一起，像粘稠液面上的油光；再撒一点点亮斑当气泡。
- 流动：竖直的条纹往下走，一帧挪一格，接缝处对得上（周期正好等于帧数）。
- 桶：原版桶的轮廓加里面一层液面，液面也是灰度，由物品模型的染色上色。

随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, ITEM, clamp, save  # noqa: E402

from PIL import Image  # noqa: E402

FRAMES = 32
STILL = 16
FLOW = 32
# 灰度的明暗范围：底色压到 0.72，亮纹到 1.0，染色乘上来以后还看得出波纹
LOW = 0.72
HIGH = 1.0


def grey(v, a=255):
    """v 是 0–1 的明度。"""
    c = clamp(255 * (LOW + (HIGH - LOW) * max(0.0, min(1.0, v))))
    return (c, c, c, a)


def still():
    """静止的液面：两道斜着漂的长波加一点气泡亮斑。"""
    img = Image.new("RGBA", (STILL, STILL * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        t = f / FRAMES * math.tau
        for y in range(STILL):
            for x in range(STILL):
                u = x / STILL * math.tau
                v = y / STILL * math.tau
                # 两层波：一层横着漂、一层斜着漂，周期都是整数圈，首尾帧接得上
                a = math.sin(u * 2 + t)
                b = math.sin(u + v * 2 - t * 2)
                value = 0.5 + 0.25 * a + 0.25 * b
                # 粘稠的液体亮纹更集中：把波形压一下，亮的地方窄一些
                value = value ** 1.6
                img.putpixel((x, f * STILL + y), grey(value))
        # 气泡：位置按帧号错开，看着像慢慢冒
        for i in range(3):
            bx = (i * 7 + f * 3) % STILL
            by = (i * 11 + f * 5) % STILL
            img.putpixel((bx, f * STILL + by), grey(1.15))
    return img


def flow():
    """流动：竖着的条纹匀速往下，一帧一格。"""
    img = Image.new("RGBA", (FLOW, FLOW * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        shift = f  # 32 帧正好走完 32 格，循环严丝合缝
        for y in range(FLOW):
            for x in range(FLOW):
                u = x / FLOW * math.tau
                v = (y + shift) / FLOW * math.tau
                # 沿流向拉长的条纹：横向分三股，纵向快一些
                a = math.sin(u * 3)
                b = math.sin(v * 4 + a)
                value = 0.5 + 0.3 * b + 0.2 * math.sin(u * 6 + v)
                value = value ** 1.5
                img.putpixel((x, f * FLOW + y), grey(value))
    return img


def bucket_shell():
    """酸液桶的壳：铁桶，不染色。"""
    iron = (168, 168, 168, 255)
    dark = (108, 108, 108, 255)
    light = (208, 208, 208, 255)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # 桶身：上宽下窄的梯形
    for y in range(5, 14):
        inset = 3 + (y - 5) // 4
        for x in range(inset, 16 - inset):
            img.putpixel((x, y), iron)
    # 桶沿
    for x in range(3, 13):
        img.putpixel((x, 4), light)
        img.putpixel((x, 5), light)
    # 左右的暗边和高光
    for y in range(5, 14):
        inset = 3 + (y - 5) // 4
        img.putpixel((inset, y), dark)
        img.putpixel((15 - inset, y), dark)
        if y < 11:
            img.putpixel((inset + 1, y), light)
    # 桶底
    for x in range(5, 11):
        img.putpixel((x, 13), dark)
    # 桶里掏空，液面由另一层画
    for y in range(6, 9):
        for x in range(5, 11):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img


def bucket_fluid():
    """桶里的液面：灰度的一层，物品模型按酸液的颜色染。"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(6, 9):
        for x in range(5, 11):
            img.putpixel((x, y), grey(1.0 if y == 6 else 0.62))
    return img


def main():
    save(still(), BLOCK, "acid_still")
    save(flow(), BLOCK, "acid_flow")
    save(bucket_shell(), ITEM, "acid_bucket")
    save(bucket_fluid(), ITEM, "acid_bucket_fluid")
    # 动画：每帧 2 刻，静止比流动慢一点在 mcmeta 里分别写
    meta(BLOCK, "acid_still", 3)
    meta(BLOCK, "acid_flow", 1)
    print("ok")


def meta(folder, name, frametime):
    path = os.path.join(folder, name + ".png.mcmeta")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)


if __name__ == "__main__":
    main()
