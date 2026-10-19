"""生成探索车的贴图：车身皮肤、发光层（大灯和激光炮口）和物品图标。

用法：python tools/exocraft_textures.py [实体贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity 和 textures/item。

- 方块列表和 client/exocraft/ExocraftModel.java 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 配色：米白的车身、橙色的条纹和货箱、深灰的底盘和防滚架、黑色的轮胎。
随机数用固定种子，每次生成的结果一样。
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from combat_textures import Skin, faces, mix, save, shade  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")
ITEM = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "item")

BODY = (222, 216, 202)
ORANGE = (232, 128, 40)
FRAME = (62, 64, 70)
TIRE = (32, 32, 34)
RIM = (150, 152, 158)
SEAT = (54, 44, 40)


def exocraft():
    s = Skin(128, 128, 83)
    dark = shade(FRAME, 0.7)

    # 底盘：侧面米白，一道橙色条纹，底下深灰；顶上是踏板纹
    chassis = s.box(0, 0, 20, 8, 36, BODY)
    for key in ("left", "right"):
        x0, y0, w, h = chassis[key]
        s.hline(x0, x0 + w, y0 + 2, ORANGE)
        s.hline(x0, x0 + w, y0 + 3, shade(ORANGE, 0.85))
        for y in range(y0 + h - 2, y0 + h):
            s.hline(x0, x0 + w, y, FRAME)
        # 车门的缝
        for y in range(y0, y0 + h - 2):
            s.put(x0 + w // 2 - 4, y, shade(BODY, 0.7))
            s.put(x0 + w // 2 + 6, y, shade(BODY, 0.7))
    x0, y0, w, h = chassis["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                s.put(x, y, shade(FRAME, 1.25))
            else:
                s.put(x, y, FRAME)
    x0, y0, w, h = chassis["bottom"]
    for y in range(y0, y0 + h):
        s.hline(x0, x0 + w, y, shade(FRAME, 0.6))
    for key in ("front", "back"):
        x0, y0, w, h = chassis[key]
        for y in range(y0 + h - 3, y0 + h):
            s.hline(x0, x0 + w, y, FRAME)
        # 保险杠上的黄黑警示条
        for x in range(x0, x0 + w):
            s.put(x, y0 + h - 3, (230, 190, 40) if (x // 2) % 2 == 0 else (30, 30, 32))

    # 发动机罩：米白，中间一道橙色宽条，前面是散热格栅
    hood = s.box(32, 44, 18, 3, 12, BODY)
    x0, y0, w, h = hood["top"]
    for y in range(y0, y0 + h):
        for x in range(x0 + w // 2 - 3, x0 + w // 2 + 3):
            s.put(x, y, ORANGE)
    x0, y0, w, h = hood["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, dark if x % 2 == 0 else FRAME)

    # 货箱：橙色，箱板的缝和边框，侧面印着编号样的白条
    crate = s.box(0, 68, 18, 6, 10, ORANGE)
    for key, rect in crate.items():
        x0, y0, w, h = rect
        for x in range(x0, x0 + w):
            s.put(x, y0, shade(ORANGE, 0.7))
            s.put(x, y0 + h - 1, shade(ORANGE, 0.7))
        for y in range(y0, y0 + h):
            s.put(x0, y, shade(ORANGE, 0.7))
            s.put(x0 + w - 1, y, shade(ORANGE, 0.7))
        if key in ("left", "right", "back") and h >= 6:
            s.hline(x0 + 2, x0 + min(w - 2, 7), y0 + 2, (240, 236, 226))
    x0, y0, w, h = crate["top"]
    for x in range(x0 + 1, x0 + w - 1, 4):
        s.vline(x, y0 + 1, y0 + h - 1, shade(ORANGE, 0.8))

    # 座椅和靠背：深棕的皮面，缝线
    seat = s.box(56, 68, 8, 5, 8, SEAT)
    x0, y0, w, h = seat["top"]
    for y in range(y0 + 1, y0 + h - 1, 3):
        s.hline(x0 + 1, x0 + w - 1, y, shade(SEAT, 1.35))
    back = s.box(88, 68, 8, 11, 2, SEAT)
    for key in ("front", "back"):
        x0, y0, w, h = back[key]
        for y in range(y0 + 2, y0 + h - 1, 3):
            s.hline(x0 + 1, x0 + w - 1, y, shade(SEAT, 1.35))

    # 防滚架：深灰的管子，接头处亮一点
    for (u, v, w, h, d) in ((92, 44, 2, 15, 2), (0, 86, 2, 2, 13), (32, 86, 18, 2, 2)):
        rects = s.box(u, v, w, h, d, FRAME, 0.03)
        for rect in rects.values():
            x0, y0, rw, rh = rect
            for x in range(x0, x0 + rw):
                s.put(x, y0, shade(FRAME, 1.3))

    # 挡泥板：米白，外沿一圈橙
    fender = s.box(72, 86, 5, 2, 12, BODY)
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = fender[key]
        s.hline(x0, x0 + w, y0 + h - 1, ORANGE)

    # 轮子：黑色轮胎，侧面是灰色轮毂和螺栓，胎面上横纹
    wheel = faces(0, 44, 4, 12, 12)
    for key, rect in wheel.items():
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                s.put(x, y, shade(TIRE, 1 + s.rng.uniform(-0.06, 0.06)))
        if key in ("right", "left"):
            cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                    if d < 3.6:
                        s.put(x, y, shade(RIM, 1.1 - d * 0.06))
                    if d < 1.2:
                        s.put(x, y, shade(RIM, 0.6))
            for (dx, dy) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                s.put(int(cx + dx + 0.5), int(cy + dy + 0.5), shade(RIM, 0.55))
        else:
            # 胎面
            if w <= h:
                for y in range(y0, y0 + h, 2):
                    s.hline(x0, x0 + w, y, shade(TIRE, 1.6))
            else:
                for x in range(x0, x0 + w, 2):
                    s.vline(x, y0, y0 + h, shade(TIRE, 1.6))

    # 激光炮塔：深灰的底座，炮管上一圈圈散热片，炮口在发光层里亮青色
    turret = s.box(0, 104, 4, 3, 4, FRAME)
    x0, y0, w, h = turret["top"]
    s.put(x0 + 1, y0 + 1, ORANGE)
    s.put(x0 + 2, y0 + 1, ORANGE)
    barrel = s.box(16, 104, 2, 2, 6, (44, 46, 52))
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = barrel[key]
        for x in range(x0, x0 + w, 2):
            s.vline(x, y0, y0 + h, (80, 82, 90))
    x0, y0, w, h = barrel["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (120, 220, 255))
            s.light(x, y, 1.0)

    # 大灯：前面亮黄白
    light = s.box(32, 104, 3, 2, 1, (60, 60, 64))
    x0, y0, w, h = light["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (255, 246, 214))
            s.light(x, y, 1.0)

    # 天线：细杆，顶上一个红色信号灯
    antenna = s.box(40, 104, 1, 10, 1, (90, 92, 98))
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = antenna[key]
        s.put(x0, y0, (220, 50, 40))
        s.light(x0, y0, 0.9)

    save(s.img, ENTITY, "exocraft")
    save(s.glow, ENTITY, "exocraft_glow")


def icon():
    """物品图标：侧面看的探索车，米白车身、橙条、黑轮子、防滚架和车尾的橙色货箱。"""
    n = 16
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))

    def put(x, y, c):
        if 0 <= x < n and 0 <= y < n:
            img.putpixel((x, y), tuple(c[:3]) + (255,))

    # 车身（车头朝左）
    for x in range(1, 15):
        for y in range(8, 11):
            put(x, y, shade(BODY, 1.08 - (y - 8) * 0.08))
    for x in range(1, 15):
        put(x, 9, ORANGE)
    for x in range(0, 16):
        put(x, 11, FRAME)
    # 发动机罩和格栅
    for x in range(1, 6):
        put(x, 7, BODY)
    put(0, 8, FRAME)
    put(0, 9, FRAME)
    put(1, 8, (255, 246, 214))
    # 货箱
    for x in range(10, 15):
        for y in range(5, 8):
            put(x, y, ORANGE if y > 5 else shade(ORANGE, 1.15))
    put(10, 5, shade(ORANGE, 0.7))
    put(14, 7, shade(ORANGE, 0.7))
    # 防滚架和座椅
    for y in range(3, 8):
        put(6, y, FRAME)
        put(9, y, FRAME)
    for x in range(6, 10):
        put(x, 3, FRAME)
    put(8, 6, SEAT)
    put(8, 7, SEAT)
    put(7, 7, SEAT)
    # 激光炮管
    put(3, 6, FRAME)
    put(2, 6, FRAME)
    put(1, 6, (120, 220, 255))
    # 轮子
    for cx in (3, 12):
        for y in range(10, 15):
            for x in range(cx - 2, cx + 3):
                d = ((x - cx) ** 2 + (y - 12) ** 2) ** 0.5
                if d <= 2.3:
                    put(x, y, TIRE)
                if d <= 1.0:
                    put(x, y, RIM)
    # 一点高光和描边
    for x in range(2, 14):
        put(x, 8, mix(img.getpixel((x, 8))[:3], (255, 255, 255), 0.25))
    save(img, ITEM, "exocraft")


if __name__ == "__main__":
    exocraft()
    icon()
    print("exocraft textures written")
