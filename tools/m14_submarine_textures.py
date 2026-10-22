"""生成潜艇和鱼雷的贴图（ADR-0026）：艇身皮肤（含驾驶舱玻璃）、发光层（艇头的灯、灯照出去的光柱、侧推和仪表），鱼雷和它的发光层。

用法：python tools/m14_submarine_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/exocraft/SubmarineModel.java、TorpedoRenderer.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 配色照真实的深潜器（阿尔文号这类）：上半截白、腰上一道安全橙、下半截深灰蓝、底下黑色防污漆；驾驶舱是淡青色的玻璃。
- 艇身贴图 256×128：左半边是不透明的艇身，右半边是玻璃（半透明）、光柱（只在发光层里有）和驾驶舱底座。
随机数用固定种子，每次生成的结果一样。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from combat_textures import Skin, faces, mix, save, shade  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")

WHITE = (226, 228, 222)
ORANGE = (230, 112, 38)
HULL_DARK = (58, 70, 82)
ANTIFOUL = (30, 32, 36)
METAL = (120, 126, 134)
FRAME = (52, 56, 62)
LAMP = (255, 246, 222)
GLASS = (150, 212, 236)


def clear(s, rect):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.img.putpixel((x, y), (0, 0, 0, 0))


def hull_side(s, rect, stripe_at):
    """侧面：上白、腰线橙、下深灰蓝、最底下黑；一排铆钉线和舱板缝。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        row = y - y0
        if row < stripe_at:
            base = WHITE
        elif row < stripe_at + 2:
            base = ORANGE
        elif row < h - 2:
            base = HULL_DARK
        else:
            base = ANTIFOUL
        for x in range(x0, x0 + w):
            s.put(x, y, shade(base, 1 + s.rng.uniform(-0.04, 0.04)))
    # 舱板竖缝
    for x in range(x0 + 6, x0 + w - 2, 10):
        for y in range(y0, y0 + h - 2):
            s.put(x, y, shade(s.get(x, y), 0.8))
    # 铆钉
    for x in range(x0 + 2, x0 + w - 1, 3):
        if y0 + 1 < y0 + h:
            s.put(x, y0 + 1, shade(WHITE, 0.82))


def submarine():
    s = Skin(256, 128, 2026)

    # ---- 艇身主体 (0,0) 20×14×40
    hull = faces(0, 0, 20, 14, 40)
    for rect in hull.values():
        s.panel(rect, WHITE, 0.04)
    for key in ("left", "right"):
        hull_side(s, hull[key], 6)
        # 侧面靠前一个舷窗，靠后一块编号牌。"right" 面（-x）右边是艇头，"left" 面（+x）左边是艇头
        x0, y0, w, h = hull[key]
        front = x0 + w - 6 if key == "right" else x0 + 5
        back = x0 + 6 if key == "right" else x0 + w - 14
        for x in range(back, back + 8):
            s.put(x, y0 + 3, (40, 44, 50))
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                s.put(front + dx, y0 + 3 + dy, (70, 120, 150))
        s.put(front, y0 + 3, (160, 210, 230))
    for key in ("front", "back"):
        hull_side(s, hull[key], 6)
    # 顶面：白色甲板和防滑纹（前半截被驾驶舱底座盖住）
    x0, y0, w, h = hull["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(WHITE, 1 + s.rng.uniform(-0.03, 0.03)))
            if (x - y) % 4 == 0:
                s.put(x, y, shade(WHITE, 0.86))
    x0, y0, w, h = hull["bottom"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(ANTIFOUL, 1 + s.rng.uniform(-0.08, 0.08)))

    # ---- 上甲板 (0,54) 16×3×20
    deck = s.box(0, 54, 16, 3, 20, WHITE, 0.03)
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = deck[key]
        s.hline(x0, x0 + w, y0 + h - 1, shade(WHITE, 0.75))
    x0, y0, w, h = deck["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x + y) % 4 == 0:
                s.put(x, y, shade(WHITE, 0.86))
    # 舱口盖
    for y in range(y0 + 8, y0 + 13):
        for x in range(x0 + 5, x0 + 11):
            edge = y in (y0 + 8, y0 + 12) or x in (x0 + 5, x0 + 10)
            s.put(x, y, shade(METAL, 0.8 if edge else 1.0))

    # ---- 艇头 (72,54) 16×10×4 和尖头 (72,68) 10×6×2：白色，中间一圈橙
    nose = s.box(72, 54, 16, 10, 4, WHITE, 0.04)
    for key in ("left", "right", "front"):
        hull_side(s, nose[key], 2)
    tip = s.box(72, 68, 10, 6, 2, WHITE, 0.04)
    x0, y0, w, h = tip["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(METAL, 1 + s.rng.uniform(-0.05, 0.05)))
    # 声呐罩：尖头正面一块深色
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 2, x0 + w - 2):
            s.put(x, y, (38, 44, 52))

    # ---- 尾锥 (0,77) 14×10×5
    tail = s.box(0, 77, 14, 10, 5, WHITE, 0.04)
    for key in ("left", "right", "back", "front"):
        hull_side(s, tail[key], 2)

    # ---- 螺旋桨：桨毂 (96,68) 3×3×4，桨叶 (110,68) 1×12×1、(38,77) 12×1×1，铜色
    bronze = (178, 124, 66)
    s.box(96, 68, 3, 3, 4, METAL)
    for (u, v, w, h, d) in ((110, 68, 1, 12, 1), (38, 77, 12, 1, 1)):
        rects = s.box(u, v, w, h, d, bronze, 0.08)
        for rect in rects.values():
            x0, y0, rw, rh = rect
            s.put(x0, y0, shade(bronze, 1.25))

    # ---- 导流罩：上下 (38,79) 16×1×4，两侧 (78,79) 1×12×4，橙色
    for (u, v, w, h, d) in ((38, 79, 16, 1, 4), (78, 79, 1, 12, 4)):
        rects = s.box(u, v, w, h, d, ORANGE, 0.04)
        for rect in rects.values():
            x0, y0, rw, rh = rect
            for x in range(x0, x0 + rw):
                s.put(x, y0, shade(ORANGE, 1.15))

    # ---- 尾翼：竖鳍 (88,81) 1×6×10，下鳍 (110,81) 1×2×7，水平鳍 (38,84) 5×1×7；橙色鳍尖
    for (u, v, w, h, d) in ((88, 81, 1, 6, 10), (110, 81, 1, 2, 7), (38, 84, 5, 1, 7)):
        rects = s.box(u, v, w, h, d, WHITE, 0.04)
        for key, rect in rects.items():
            x0, y0, rw, rh = rect
            for y in range(y0, y0 + rh):
                s.put(x0 + rw - 1, y, ORANGE)
    # 竖鳍顶上一盏红色航行灯
    fin = faces(88, 81, 1, 6, 10)
    for key in ("left", "right"):
        x0, y0, w, h = fin[key]
        s.put(x0 + w - 2, y0, (230, 50, 40))
        s.light(x0 + w - 2, y0, 1.0)

    # ---- 侧推舱 (0,92) 4×6×14：深灰蓝，尾部喷口发青光
    pod = s.box(0, 92, 4, 6, 14, HULL_DARK, 0.05)
    for key in ("left", "right", "top"):
        x0, y0, w, h = pod[key]
        if key == "top":
            for y in range(y0, y0 + h):
                s.put(x0 + w // 2, y, ORANGE)
        else:
            s.hline(x0, x0 + w, y0 + 1, ORANGE)
    for key in ("back",):
        x0, y0, w, h = pod[key]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                s.put(x, y, (40, 44, 50) if edge else (110, 220, 255))
                if not edge:
                    s.light(x, y, 0.85)
    x0, y0, w, h = pod["front"]
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y, (30, 34, 38))

    # ---- 大灯 (36,92) 3×3×1：前面亮
    lamp = s.box(36, 92, 3, 3, 1, FRAME)
    x0, y0, w, h = lamp["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, LAMP)
            s.light(x, y, 1.0)

    # ---- 机械臂 (44,92) 2×2×7：金属，关节深色
    arm = s.box(44, 92, 2, 2, 7, METAL, 0.06)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = arm[key]
        if w > h:
            for x in range(x0 + 2, x0 + w, 3):
                s.vline(x, y0, y0 + h, shade(METAL, 0.6))
        else:
            for y in range(y0 + 2, y0 + h, 3):
                s.hline(x0, x0 + w, y, shade(METAL, 0.6))
    x0, y0, w, h = arm["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (200, 170, 60))

    # ---- 驾驶舱玻璃 (128,0)：所有玻璃块共用这一片，淡青、半透明，斜着几道反光
    glass = faces(128, 0, 16, 5, 20)
    for rect in glass.values():
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                streak = (x - y) % 11 in (0, 1)
                c = mix(GLASS, (255, 255, 255), 0.55) if streak else shade(GLASS, 1 + s.rng.uniform(-0.03, 0.03))
                s.img.putpixel((x, y), c + (150 if streak else 100,))

    # ---- 光柱 (128,26) 22×16×26、(128,68) 14×10×18、(192,68) 8×6×12：皮肤上全透明，只在发光层里亮，越远越淡；两头的面不画
    for (u, v, w, h, d, strength) in ((192, 68, 8, 6, 12, 0.34), (128, 68, 14, 10, 18, 0.22), (128, 26, 22, 16, 26, 0.13)):
        for key, rect in faces(u, v, w, h, d).items():
            clear(s, rect)
            if key in ("front", "back"):
                continue
            x0, y0, rw, rh = rect
            for y in range(y0, y0 + rh):
                for x in range(x0, x0 + rw):
                    s.glow.putpixel((x, y), (255, 244, 214, int(255 * strength * (1 + s.rng.uniform(-0.08, 0.08)))))

    # ---- 驾驶舱底座 (128,96) 17×1×21：深灰的框，顶面隔着玻璃看得见舱里。
    # 顶面贴图第一行是舱尾（z=2.5），第 r 行在 z=2-r；一圈框、深色地板、最前面的仪表台、中间的座椅
    collar = s.box(128, 96, 17, 1, 21, FRAME, 0.04)
    x0, y0, w, h = collar["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            s.put(x, y, shade(FRAME, 1.3) if edge else ((46, 50, 56) if (x + y) % 2 else (40, 44, 50)))
    # 仪表台：z -17.5..-14.5，一排屏幕和指示灯
    for y in range(y0 + 16, y0 + 20):
        for x in range(x0 + 2, x0 + w - 2):
            s.put(x, y, (30, 34, 38))
    for x in range(x0 + 3, x0 + w - 3, 3):
        s.put(x, y0 + 18, (90, 220, 200))
        s.light(x, y0 + 18, 0.9)
        s.put(x + 1, y0 + 17, (240, 190, 70))
        s.light(x + 1, y0 + 17, 0.7)
    # 座椅：z -10..-4
    for y in range(y0 + 6, y0 + 12):
        for x in range(x0 + 5, x0 + 12):
            s.put(x, y, (70, 46, 40) if y > y0 + 6 else (52, 34, 30))

    save(s.img, ENTITY, "submarine")
    save(s.glow, ENTITY, "submarine_glow")


def torpedo():
    """鱼雷 32×32：灰色弹体 (0,0) 3×3×10，红色弹头 (0,13) 2×2×1，尾部喷口 (6,13) 2×2×2 发光，十字尾翼 (0,17) 7×1×2、(18,17) 1×7×2。"""
    s = Skin(32, 32, 7)
    body = s.box(0, 0, 3, 3, 10, (150, 154, 160), 0.04)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = body[key]
        if w > h:
            s.vline(x0 + 2, y0, y0 + h, (200, 160, 40))
            s.vline(x0 + 3, y0, y0 + h, (40, 40, 44))
    s.box(0, 13, 2, 2, 1, (200, 60, 44))
    nozzle = s.box(6, 13, 2, 2, 2, (60, 62, 68))
    x0, y0, w, h = nozzle["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (140, 230, 255))
            s.light(x, y, 1.0)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = nozzle[key]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                s.light(x, y, 0.45)
    s.box(0, 17, 7, 1, 2, (90, 94, 100))
    s.box(18, 17, 1, 7, 2, (90, 94, 100))
    save(s.img, ENTITY, "torpedo")
    save(s.glow, ENTITY, "torpedo_glow")


if __name__ == "__main__":
    submarine()
    torpedo()
    print("submarine textures written")
