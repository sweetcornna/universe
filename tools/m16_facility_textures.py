"""生成 M16（ADR-0028 §四、§五）设施耐久那一块的贴图：战场分析基地、残影的碎屑。

用法：python tools/m16_facility_textures.py
画法和配色沿用 tools/factory_textures.py 的工具函数：16×16 像素画、深灰机壳。战场分析基地是防御设施，机壳偏军绿，
指示灯用琥珀色；正面的窗里是一块雷达屏，工作时亮起扫描线；顶面是无人机的停机坪。随机数用固定种子。
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_textures import (BLOCK, N, shade, mix, new, put, noise, frame, rect, save, casing)  # noqa: E402
from m11_textures import cube_machine  # noqa: E402

BASE = (70, 78, 64)
AMBER = (255, 176, 48)
RADAR = (96, 240, 140)
GHOST = (90, 168, 255)


def battlefield_analysis_base():
    def window(img, lit):
        # 雷达屏：同心圆和一条扫描线
        for y in range(7, 13):
            for x in range(4, 12):
                d = math.hypot(x - 7.5, y - 9.5)
                if lit:
                    c = shade(RADAR, 0.25)
                    if abs(d - 2.2) < 0.5 or abs(d - 3.9) < 0.5:
                        c = shade(RADAR, 0.55)
                    if x >= 8 and abs((y - 9.5) - (x - 7.5) * 0.4) < 0.6:
                        c = RADAR
                else:
                    c = (22, 34, 26) if abs(d - 2.2) >= 0.5 else (30, 46, 34)
                put(img, x, y, c)

    def top(rng, lit):
        img = casing(shade(BASE, 0.95), rng, seam=False)
        # 停机坪：一个圈，四角的灯
        for y in range(N):
            for x in range(N):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.2 < d < 5.2:
                    put(img, x, y, shade(AMBER, 0.8 if lit else 0.4))
        rect(img, 6, 5, 6, 10, shade(BASE, 1.5))
        rect(img, 9, 5, 9, 10, shade(BASE, 1.5))
        rect(img, 7, 7, 8, 8, shade(BASE, 1.5))
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            put(img, x, y, AMBER if lit else shade(AMBER, 0.35))
        return img

    cube_machine("battlefield_analysis_base", BASE, AMBER, 1601, window, top_lit=True, top=top)


def facility_ghost():
    """残影的碎屑粒子：蓝色的格子。"""
    rng = random.Random(1602)
    img = new(shade(GHOST, 0.7))
    noise(img, rng, 0.08)
    for i in range(0, N, 4):
        for j in range(N):
            put(img, i, j, mix(GHOST, (255, 255, 255), 0.3))
            put(img, j, i, mix(GHOST, (255, 255, 255), 0.3))
    frame(img, mix(GHOST, (255, 255, 255), 0.5), shade(GHOST, 0.5))
    save(img, BLOCK, "facility_ghost")


def main():
    battlefield_analysis_base()
    facility_ghost()


if __name__ == "__main__":
    main()
