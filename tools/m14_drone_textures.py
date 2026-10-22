"""生成 M14 的两种守卫机无人机的贴图（ADR-0026）：维修无人机、召唤无人机，各有皮肤、发光层和自带颜色的强调光层。

用法：python tools/m14_drone_textures.py [实体贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity。

- 方块列表和 client/sentinel/SentinelModel.java 的 repair()、summoner() 一一对应（texOffs 和尺寸），改了模型要同步改这里。
- 发光层（_glow）是白的，渲染时按状态着色（巡逻黄、敌对红），只有面罩和推进格栅；
  强调光层（_accent）自带颜色、不着色：维修无人机腹下的修理头和背上的十字是绿的，召唤无人机的环是紫的。
随机数用固定种子，每次生成的结果一样。
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "entity")


class AccentSkin(ct.Skin):
    """多一层自带颜色的强调光。"""

    def __init__(self, width, height, seed):
        super().__init__(width, height, seed)
        self.accent = Image.new("RGBA", (width, height), (0, 0, 0, 0))

    def glow_color(self, x, y, color, strength=1.0):
        self.accent.putpixel((x, y), tuple(ct.clamp(c) for c in color) + (ct.clamp(255 * strength),))


def emitter(s, f, color):
    """发光的修理头、环：深色底，强调光层按面画一圈亮边、中间稍暗。"""
    for key, (x0, y0, w, h) in f.items():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                s.put(x, y, (40, 44, 50) if edge else (26, 28, 32))
                s.glow_color(x, y, color, 0.95 if not edge or w <= 2 or h <= 2 else 0.6)


def repair():
    s = AccentSkin(64, 32, 71)
    white = (214, 220, 214)
    accent = (88, 200, 128)
    green = (108, 255, 176)
    body = s.box(0, 0, 6, 5, 6, white)
    ct.sentinel_body(s, body, white, accent)
    ct.visor(s, ct.faces(24, 0, 4, 3, 1))
    emitter(s, ct.faces(36, 0, 4, 1, 4), green)
    ct.pod(s, s.box(0, 12, 2, 3, 4, (92, 100, 96)), (92, 100, 96))
    arm = s.box(16, 12, 1, 4, 1, (78, 82, 88))
    # 机械臂末端的夹钳是亮的
    x0, y0, w, h = arm["bottom"]
    s.glow_color(x0, y0, green, 0.8)
    cross = s.box(24, 12, 3, 1, 1, white)
    for key in ("front", "back", "top"):
        x0, y0, w, h = cross[key]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                s.put(x, y, (40, 120, 72))
                s.glow_color(x, y, green, 0.85)
    # 侧面的绿色十字标
    for key in ("left", "right"):
        x0, y0, w, h = body[key]
        cx, cy = x0 + w // 2, y0 + 1
        for dx, dy in ((0, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
            s.put(cx + dx, cy + dy, accent)
    ct.save(s.img, ENTITY, "repair_sentinel")
    ct.save(s.glow, ENTITY, "repair_sentinel_glow")
    ct.save(s.accent, ENTITY, "repair_sentinel_accent")


def summoner():
    s = AccentSkin(64, 64, 73)
    hull = (70, 66, 86)
    accent = (150, 96, 230)
    purple = (190, 120, 255)
    body = s.box(0, 0, 8, 8, 8, hull)
    ct.sentinel_body(s, body, hull, accent)
    # 正面面罩两侧的竖条纹
    x0, y0, w, h = body["front"]
    for y in range(y0 + 1, y0 + h - 1):
        s.put(x0 + 1, y, ct.shade(accent, 0.8))
        s.put(x0 + w - 2, y, ct.shade(accent, 0.8))
    ct.visor(s, ct.faces(32, 0, 6, 3, 1))
    spike = s.box(48, 0, 2, 3, 2, ct.shade(hull, 0.8))
    x0, y0, w, h = spike["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.glow_color(x, y, purple, 1.0)
    for u, v, w, h, d in ((0, 16, 14, 1, 2), (0, 20, 14, 1, 2), (0, 24, 2, 1, 10), (24, 24, 2, 1, 10)):
        f = ct.faces(u, v, w, h, d)
        emitter(s, f, purple)
        # 环上隔一段一个暗的接头
        for key in ("front", "back", "left", "right"):
            x0, y0, fw, fh = f[key]
            for x in range(x0 + 2, x0 + fw, 4):
                s.put(x, y0, (90, 84, 104))
                s.glow_color(x, y0, purple, 0.25)
    ct.pod(s, s.box(0, 40, 3, 4, 4, (60, 58, 72)), (60, 58, 72))
    ct.save(s.img, ENTITY, "summoner_sentinel")
    ct.save(s.glow, ENTITY, "summoner_sentinel_glow")
    ct.save(s.accent, ENTITY, "summoner_sentinel_accent")


if __name__ == "__main__":
    repair()
    summoner()
    print("drone textures written")
