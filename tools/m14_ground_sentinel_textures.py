"""生成地面守卫机（ADR-0026）的贴图：四足机、步行机的皮肤和发光层，四足机伺服器、步行机主脑、两个刷怪蛋。

用法：python tools/m14_ground_sentinel_textures.py [实体贴图目录] [物品贴图目录]
默认输出到 src/main/resources/assets/universe/textures/entity 和 textures/item。

- 方块列表和 client/sentinel/QuadrupedSentinelModel.java、WalkerSentinelModel.java 一一对应（texOffs 和尺寸），
  改了模型要同步改这里。
- 配色跟普通守卫机一致：四足机是白色外壳、黄色标识，步行机是灰色装甲、红色标识和黄黑警示条。
- 发光层只有面罩、发射口、炮口、背后的散热口（弱点）和几颗指示灯不透明，颜色是白的，渲染时按状态着色（巡逻黄、敌对红）。
随机数用固定种子，每次生成的结果一样。面板、面罩、机身这些画法借用 combat_textures.py。
"""
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import combat_textures as ct  # noqa: E402

ENTITY = ct.ENTITY
ITEM = ct.ITEM
N = 16

WHITE = (206, 210, 216)
YELLOW = (232, 176, 58)
METAL = (128, 132, 142)
RED = (210, 60, 46)
JOINT = (70, 72, 80)


def grille(s, rect, base, glow, rows=True):
    """散热格：深色的格栅，发光层亮在缝里。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            slot = (y - y0) % 2 == 1 if rows else (x - x0) % 2 == 1
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if edge:
                s.put(x, y, ct.shade(base, 0.8))
            elif slot:
                s.put(x, y, (26, 24, 24))
                s.light(x, y, glow)
            else:
                s.put(x, y, ct.shade(base, 0.55))


def hazard(s, rect, rows=2):
    """黄黑相间的警示条，画在面的上沿。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + min(rows, h)):
        for x in range(x0, x0 + w):
            s.put(x, y, (226, 180, 40) if (x + y) % 4 < 2 else (30, 30, 34))


def joint_light(s, rect, strength=0.9):
    """面中间一颗指示灯。"""
    x0, y0, w, h = rect
    x, y = x0 + w // 2, y0 + h // 2
    s.put(x, y, (40, 40, 44))
    s.light(x, y, strength)


# ---- 四足机（64×64）

def quadruped():
    s = ct.Skin(64, 64, 71)
    # 机身：白色外壳，侧面一道标识条，肚子是深色的
    torso = s.box(0, 0, 9, 6, 16, WHITE)
    for key in ("left", "right"):
        x0, y0, w, h = torso[key]
        s.hline(x0, x0 + w, y0 + h // 2, ct.shade(WHITE, 0.72))
        for x in range(x0 + 2, x0 + w - 2, 3):
            s.put(x, y0 + 1, YELLOW)
        # 腿根的关节盖
        for x in (x0 + 2, x0 + w - 3):
            s.put(x, y0 + h - 2, ct.shade(JOINT, 1.2))
            s.put(x + 1, y0 + h - 2, ct.shade(JOINT, 1.2))
    s.panel(torso["bottom"], (58, 60, 66), 0.05)
    x0, y0, w, h = torso["top"]
    for y in range(y0 + 3, y0 + h - 2, 4):
        s.hline(x0 + 1, x0 + w - 1, y, ct.shade(WHITE, 0.75))
    x0, y0, w, h = torso["front"]
    s.panel((x0 + 1, y0 + 1, w - 2, h - 2), ct.shade(WHITE, 0.6), 0.04)
    # 脊甲：深一点的金属，横着几道棱
    spine = s.box(0, 22, 6, 2, 11, (150, 154, 162))
    x0, y0, w, h = spine["top"]
    for y in range(y0 + 1, y0 + h, 2):
        s.hline(x0, x0 + w, y, ct.shade((150, 154, 162), 0.7))
    # 背后的散热口：弱点，发光层亮
    vent = s.box(36, 22, 5, 4, 1, (90, 92, 100))
    grille(s, vent["back"], (90, 92, 100), 0.95)
    # 尾巴天线，顶上一颗灯
    tail = s.box(36, 28, 1, 6, 1, JOINT)
    x0, y0, w, h = tail["top"]
    s.light(x0, y0, 1.0)
    # 头：白色外壳，正面围着面罩的深色框
    skull = s.box(0, 36, 7, 6, 7, WHITE)
    x0, y0, w, h = skull["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, ct.shade(WHITE, 0.45 if 1 <= y - y0 <= 3 else 0.85))
    for key in ("left", "right"):
        x0, y0, w, h = skull[key]
        s.put(x0 + w // 2, y0 + 1, YELLOW)
        s.put(x0 + w // 2, y0 + 2, YELLOW)
    ct.visor(s, ct.faces(30, 36, 5, 2, 1))
    # 下巴的发射口：深色的炮口，镜头中间最亮
    emitter = s.box(44, 36, 3, 3, 3, (64, 66, 74))
    x0, y0, w, h = emitter["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (18, 18, 22))
            centre = x == x0 + w // 2 and y == y0 + h // 2
            s.light(x, y, 1.0 if centre else 0.45)
    # 耳朵一样的传感鳍
    fin = s.box(56, 0, 1, 3, 3, YELLOW)
    x0, y0, w, h = fin["right"]
    s.hline(x0, x0 + w, y0 + h - 1, ct.shade(YELLOW, 0.6))
    # 腿：大腿灰，膝盖一颗灯，小腿深色，脚掌更深
    upper = s.box(0, 50, 3, 7, 3, (150, 154, 162))
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = upper[key]
        s.hline(x0, x0 + w, y0 + 2, ct.shade(WHITE, 1.05))
    joint_light(s, upper["front"], 0.7)
    shin = s.box(12, 50, 2, 5, 2, JOINT)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = shin[key]
        s.vline(x0, y0, y0 + h, ct.shade(JOINT, 1.3))
    s.box(20, 50, 3, 1, 4, (48, 50, 56))
    ct.save(s.img, ENTITY, "quadruped_sentinel")
    ct.save(s.glow, ENTITY, "quadruped_sentinel_glow")


# ---- 步行机（128×128）

def walker():
    s = ct.Skin(128, 128, 79)
    # 座舱：灰色装甲板，铆钉，侧面的警示条和红色标识
    cockpit = s.box(0, 0, 18, 14, 18, METAL)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = cockpit[key]
        s.hline(x0, x0 + w, y0 + h // 2 + 2, ct.shade(METAL, 0.7))
        s.hline(x0, x0 + w, y0 + h // 2 + 3, ct.shade(METAL, 1.12))
        for x in (x0 + 1, x0 + w - 2):
            for y in range(y0 + 1, y0 + h - 1, 3):
                s.put(x, y, ct.shade(METAL, 1.35))
    for key in ("left", "right"):
        x0, y0, w, h = cockpit[key]
        hazard(s, (x0, y0, w, h))
        for y in range(y0 + 4, y0 + 8):
            s.hline(x0 + w // 2 - 2, x0 + w // 2 + 2, y, RED)
    x0, y0, w, h = cockpit["front"]
    for y in range(y0 + 3, y0 + 8):
        for x in range(x0 + 2, x0 + w - 2):
            s.put(x, y, ct.shade(METAL, 0.42))
    x0, y0, w, h = cockpit["top"]
    for y in range(y0 + 2, y0 + h - 2, 5):
        s.hline(x0 + 2, x0 + w - 2, y, ct.shade(METAL, 0.72))
    s.panel(cockpit["bottom"], (70, 72, 80), 0.05)
    # 传感罩：深色，顶上三颗灯
    dome = s.box(76, 0, 8, 4, 8, (84, 88, 98))
    x0, y0, w, h = dome["top"]
    for i, x in enumerate((x0 + 2, x0 + 4, x0 + 6)):
        s.put(x, y0 + 3, (30, 30, 34))
        s.light(x, y0 + 3, 0.9 if i == 1 else 0.6)
    x0, y0, w, h = dome["front"]
    s.hline(x0 + 1, x0 + w - 1, y0 + 1, (30, 30, 34))
    for x in range(x0 + 1, x0 + w - 1):
        s.light(x, y0 + 1, 0.5)
    antenna = s.box(110, 0, 1, 6, 1, JOINT)
    x0, y0, w, h = antenna["top"]
    s.light(x0, y0, 1.0)
    ct.visor(s, ct.faces(76, 12, 12, 3, 1))
    # 下巴：深色的进气格栅
    chin = s.box(76, 16, 12, 3, 12, (70, 72, 80))
    x0, y0, w, h = chin["front"]
    for x in range(x0 + 1, x0 + w - 1, 2):
        s.vline(x, y0, y0 + h, (34, 34, 38))
    # 背后的反应堆散热口：弱点
    vent = s.box(0, 32, 10, 7, 2, (90, 92, 100))
    grille(s, vent["back"], (90, 92, 100), 1.0)
    hazard(s, vent["top"], 1)
    # 炮口
    muzzle = s.box(86, 32, 2, 2, 1, (40, 40, 44))
    x0, y0, w, h = muzzle["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, (20, 18, 18))
            s.light(x, y, 1.0)
    # 骨盆：深色，两侧关节
    pelvis = s.box(0, 42, 14, 6, 8, (90, 94, 104))
    for key in ("left", "right"):
        x0, y0, w, h = pelvis[key]
        cx, cy = x0 + w / 2 - 0.5, y0 + h / 2 - 0.5
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if math.hypot(x - cx, y - cy) < 2.2:
                    s.put(x, y, ct.shade(JOINT, 0.9))
    # 炮：枪灰色，炮管上一圈圈的箍
    cannon = s.box(48, 42, 4, 4, 14, (64, 66, 74))
    for key in ("top", "bottom", "left", "right"):
        x0, y0, w, h = cannon[key]
        horizontal = key in ("left", "right")
        for i in range(2, 14, 4):
            if horizontal:
                s.vline(x0 + i, y0, y0 + h, (44, 46, 52))
            else:
                s.hline(x0, x0 + w, y0 + i, (44, 46, 52))
    x0, y0, w, h = cannon["left"]
    for x in range(x0 + 6, x0 + 10):
        s.put(x, y0 + 1, RED)
    # 大腿、小腿：液压管，膝盖装甲板有红色标识
    thigh = s.box(0, 60, 6, 18, 6, (110, 114, 124))
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = thigh[key]
        s.vline(x0 + 1, y0 + 2, y0 + h - 2, (60, 62, 70))
        s.vline(x0 + w - 2, y0 + 2, y0 + h - 2, (150, 120, 60))
    joint_light(s, (thigh["front"][0], thigh["front"][1], thigh["front"][2], 4), 0.8)
    shin = s.box(24, 60, 5, 18, 5, (96, 100, 110))
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = shin[key]
        for y in range(y0 + 3, y0 + h, 5):
            s.hline(x0, x0 + w, y, ct.shade((96, 100, 110), 0.7))
    foot = s.box(44, 60, 8, 4, 11, (60, 62, 70))
    hazard(s, foot["front"], 2)
    x0, y0, w, h = foot["left"]
    s.light(x0 + 2, y0 + 1, 0.6)
    x0, y0, w, h = foot["right"]
    s.light(x0 + w - 3, y0 + 1, 0.6)
    knee = s.box(84, 60, 7, 6, 2, METAL)
    x0, y0, w, h = knee["front"]
    for y in range(y0 + 1, y0 + h - 1):
        s.put(x0 + w // 2, y, RED)
    ct.save(s.img, ENTITY, "walker_sentinel")
    ct.save(s.glow, ENTITY, "walker_sentinel_glow")


# ---- 物品

def servo():
    """四足机伺服器：白色外壳的圆柱电机，侧面一颗黄灯，后面拖着一截红蓝电线。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    rng = random.Random(83)
    cx, cy, r = 7.5, 7.0, 5.2
    mask = set()
    for y in range(N):
        for x in range(N):
            if math.hypot(x - cx, (y - cy) * 1.15) <= r:
                mask.add((x, y))
    edge = ct.outline(mask)
    for (x, y) in mask:
        d = math.hypot(x - cx, (y - cy) * 1.15) / r
        f = 1.18 - d * 0.35 - (x + y) * 0.012 + rng.uniform(-0.03, 0.03)
        c = ct.shade(WHITE, f)
        if d < 0.45:
            c = ct.shade((96, 100, 110), 1.15 - d)
        if abs(d - 0.62) < 0.08:
            c = ct.shade(WHITE, 0.6)
        if (x, y) in edge:
            c = ct.shade(WHITE, 0.45)
        img.putpixel((x, y), tuple(c) + (255,))
    # 输出轴
    for (x, y) in ((7, 7), (8, 7), (7, 6), (8, 6)):
        img.putpixel((x, y), (40, 40, 46, 255))
    img.putpixel((7, 6), (150, 154, 162, 255))
    # 黄色指示灯
    img.putpixel((11, 4), (255, 214, 80, 255))
    img.putpixel((11, 5), (232, 176, 58, 255))
    # 电线
    for (x, y, c) in ((4, 12, (200, 60, 50)), (3, 13, (200, 60, 50)), (2, 14, (200, 60, 50)),
                      (6, 12, (60, 110, 200)), (6, 13, (60, 110, 200)), (5, 14, (60, 110, 200)), (5, 15, (60, 110, 200))):
        img.putpixel((x, y), c + (255,))
    ct.save(img, ITEM, "quadruped_servo")


def brain():
    """步行机主脑：灰色金属框里一块深色的核心，中间透出红橙色的光，四角接着电路。"""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    rng = random.Random(89)
    frame = (128, 132, 142)
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            inner = 4 <= x <= 11 and 4 <= y <= 11
            if inner:
                d = math.hypot(x - 7.5, y - 7.5) / 4.2
                glow = max(0.0, 1 - d)
                c = ct.mix((24, 20, 22), (255, 120, 60), glow ** 1.4)
                if glow > 0.75:
                    c = ct.mix(c, (255, 220, 170), (glow - 0.75) * 3)
            else:
                f = 1.15 - (x + y) * 0.02 + rng.uniform(-0.04, 0.04)
                c = ct.shade(frame, f * (0.55 if edge else 1))
            img.putpixel((x, y), tuple(c) + (255,))
    # 框上的铆钉和红色标识
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img.putpixel((x, y), tuple(ct.shade(frame, 1.4)) + (255,))
    for x in range(6, 10):
        img.putpixel((x, 3), RED + (255,))
    # 伸出去的电路脚
    for (x, y) in ((1, 5), (1, 8), (1, 11), (14, 4), (14, 7), (14, 10), (5, 14), (9, 14), (6, 1), (10, 1)):
        img.putpixel((x, y), (200, 170, 70, 255))
    ct.save(img, ITEM, "walker_brain")


if __name__ == "__main__":
    quadruped()
    walker()
    servo()
    brain()
    ct.save(ct.egg(WHITE, (150, 154, 162), 97), ITEM, "quadruped_sentinel_spawn_egg")
    ct.save(ct.egg(METAL, YELLOW, 101), ITEM, "walker_sentinel_spawn_egg")
    print("ground sentinel textures written")
