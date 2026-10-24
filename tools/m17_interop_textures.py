"""生成 M17（ADR-0030 §三）电网接口的贴图。

用法：python tools/m17_interop_textures.py
一格的设备，画法沿用 tools/m11_textures.py 的 cube_machine：正面窗里是一排接线端子，工作时端子之间的电流亮；
顶面四角是接线柱。配色是铜色的端子加青色的电。随机数用固定种子，每次生成的结果一样。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import BLOCK, MID, CYAN, CYAN_DIM, shade, mix, put, rect, save, casing  # noqa: E402
from m11_textures import cube_machine  # noqa: E402

COPPER = (198, 116, 70)


def grid_interface():
    def window(img, lit):
        # 三个铜端子，中间两道电流
        for x0 in (4, 7, 10):
            rect(img, x0, 8, x0 + 1, 11, shade(COPPER, 0.8))
            put(img, x0, 8, shade(COPPER, 1.25))
            put(img, x0 + 1, 11, shade(COPPER, 0.55))
        for x in (6, 9):
            for y in range(8, 12):
                c = mix((255, 255, 255), CYAN, abs(y - 9.5) / 2) if lit else CYAN_DIM
                if (x + y) % 2 == 0 or lit:
                    put(img, x, y, c)

    def top(rng, lit):
        img = casing(shade(MID, 0.95), rng, seam=False)
        for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
            rect(img, x, y, x + 1, y + 1, shade(COPPER, 0.85))
            put(img, x, y, shade(COPPER, 1.3))
        for x in range(5, 11):
            put(img, x, 7, shade(CYAN, 0.6))
            put(img, x, 8, shade(CYAN, 0.45))
        return img

    cube_machine("grid_interface", (60, 70, 82), CYAN, 1701, window, top=top)


def main():
    grid_interface()
    print("贴图写到", BLOCK)


if __name__ == "__main__":
    main()
