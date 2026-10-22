"""生成 M14（ADR-0026）悬浮车、重型车、机甲的贴图（皮肤和发光层）、三种载具的物品图标，以及载具召唤台的方块贴图。

用法：python tools/m14_vehicle_textures.py
输出到 src/main/resources/assets/universe/textures/entity、textures/item 和 textures/block。

- 方块列表和 client/exocraft/HoverCarModel.java、HeavyRoverModel.java、ExoMechModel.java 一一对应（texOffs 和尺寸），
  改了模型要同步改这里。
- 配色跟探索车一个系列：米白的车身、橙色的条纹、深灰的底盘和关节、黑色的轮胎，玻璃是深青色。
  悬浮车的推力垫和喷口发青光；重型车车尾的精炼机观察窗是炉火色（只在炼东西时画出来）；机甲的面罩青色、喷口橙色。
- 召唤台照停车场的取车终端画：正面屏幕上一辆车的轮廓和一圈定位环，侧面散热格栅和橙色状态灯，顶上一个落点标记。
随机数用固定种子，每次生成的结果一样。
"""
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from combat_textures import Skin, faces, mix, save, shade  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "textures")
ENTITY = os.path.join(ROOT, "entity")
ITEM = os.path.join(ROOT, "item")
BLOCK = os.path.join(ROOT, "block")

BODY = (222, 216, 202)
ORANGE = (232, 128, 40)
FRAME = (62, 64, 70)
METAL = (120, 126, 134)
TIRE = (32, 32, 34)
RIM = (150, 152, 158)
SEAT = (54, 44, 40)
GLASS = (34, 56, 68)
CYAN = (110, 220, 255)
LAMP = (255, 246, 214)
FIRE = (255, 150, 50)
HAZARD = (214, 172, 46)


# ---- 画面板的小工具

def fill(s, rect, color, noise=0.04):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(color, 1 + s.rng.uniform(-noise, noise)))


def row(s, rect, r, color):
    x0, y0, w, h = rect
    if 0 <= r < h:
        s.hline(x0, x0 + w, y0 + r, color)


def col(s, rect, c, color):
    x0, y0, w, h = rect
    if 0 <= c < w:
        s.vline(x0 + c, y0, y0 + h, color)


def glow(s, rect, color, strength=1.0):
    """整块涂成发光的颜色，发光层同一块点亮。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, color)
            s.light(x, y, strength)


def glass(s, rect, frame=FRAME):
    """深青色的玻璃：一圈框，斜着两道反光。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            streak = (x - y) % 7 in (0, 1)
            if edge and w > 2 and h > 2:
                s.put(x, y, frame)
            else:
                s.put(x, y, mix(GLASS, (200, 230, 240), 0.35) if streak else shade(GLASS, 1 + s.rng.uniform(-0.05, 0.05)))


def grille(s, rect, base=FRAME):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(base, 0.55 if (y - y0) % 2 else 1.1))


def hazard(s, rect):
    """黄黑相间的警示斜条。"""
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, HAZARD if ((x + y) // 2) % 2 == 0 else (36, 36, 40))


def tire(s, rect):
    """轮子的侧面：黑色轮胎、灰色轮毂，中间一颗轴盖。"""
    x0, y0, w, h = rect
    cx, cy = x0 + (w - 1) / 2, y0 + (h - 1) / 2
    r = min(w, h) / 2
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - cx, y - cy)
            if d <= r * 0.25:
                s.put(x, y, shade(RIM, 0.7))
            elif d <= r * 0.55:
                s.put(x, y, shade(RIM, 1.1 if (x + y) % 3 else 0.85))
            else:
                s.put(x, y, shade(TIRE, 1 + s.rng.uniform(-0.08, 0.08)))


def tread(s, rect):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            s.put(x, y, shade(TIRE, 1.35 if (y - y0) % 3 == 0 else 0.95))


def wheel(s, u, v, w, h, d):
    f = faces(u, v, w, h, d)
    for key in ("front", "back", "top", "bottom"):
        tread(s, f[key])
    for key in ("left", "right"):
        tire(s, f[key])


# ---- 悬浮车

def hover_car():
    s = Skin(128, 128, 2611)
    # 车身：侧面米白，一道橙条，底下一行深灰；顶上两道橙色赛车条
    hull = s.box(0, 0, 24, 6, 32, BODY)
    for key in ("left", "right", "front", "back"):
        row(s, hull[key], 2, ORANGE)
        row(s, hull[key], 3, shade(ORANGE, 0.85))
        row(s, hull[key], 5, shade(FRAME, 1.2))
    grille(s, (hull["front"][0] + 6, hull["front"][1] + 1, 12, 3))
    for c in (6, 7, 16, 17):
        col(s, hull["top"], c, ORANGE)
    # 车底的裙板：深灰，侧面最下面一行是青色的灯带
    skirt = s.box(0, 38, 26, 3, 28, FRAME)
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = skirt[key]
        for x in range(x0, x0 + w):
            if (x - x0) % 4 != 3:
                s.put(x, y0 + h - 1, CYAN)
                s.light(x, y0 + h - 1, 0.7)
    fill(s, skirt["bottom"], shade(FRAME, 0.7), 0.06)
    # 车头：米白，正面一道格栅和橙色的边
    nose = s.box(0, 69, 20, 5, 6, BODY)
    grille(s, (nose["front"][0] + 4, nose["front"][1] + 1, 12, 2))
    row(s, nose["front"], 4, ORANGE)
    for key in ("left", "right"):
        row(s, nose[key], 4, ORANGE)
    # 座椅、靠背（靠背顶上一行橙色头枕）
    s.box(52, 69, 8, 2, 7, SEAT)
    back = s.box(82, 69, 8, 7, 2, SEAT)
    for key in ("front", "back", "left", "right"):
        row(s, back[key], 0, ORANGE)
    row(s, back["top"], 0, ORANGE)
    row(s, back["top"], 1, ORANGE)
    # 挡风玻璃
    ws = faces(0, 80, 18, 6, 1)
    for rect in ws.values():
        fill(s, rect, FRAME)
    glass(s, ws["front"])
    glass(s, ws["back"])
    # 翼舱：橙色，前面一个进气口，后面一个小喷口
    pod = s.box(38, 80, 3, 3, 12, ORANGE)
    fill(s, pod["front"], (30, 32, 36))
    glow(s, pod["back"], CYAN, 0.85)
    for key in ("left", "right"):
        row(s, pod[key], 2, shade(ORANGE, 0.7))
    # 车尾的喷口罩：深灰，后面一圈深色
    housing = s.box(68, 80, 14, 6, 4, FRAME)
    x0, y0, w, h = housing["back"]
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            s.put(x, y, (28, 30, 34))
    # 两个喷口：后面亮青色
    nozzle = s.box(0, 95, 4, 4, 1, FRAME)
    x0, y0, w, h = nozzle["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            s.put(x, y, mix(CYAN, (255, 255, 255), 0.3) if not edge else CYAN)
            s.light(x, y, 1.0 if not edge else 0.75)
    # 推力垫：底面亮青色，中间最亮
    padf = s.box(10, 95, 6, 1, 6, FRAME)
    x0, y0, w, h = padf["bottom"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = math.hypot(x - (x0 + (w - 1) / 2), y - (y0 + (h - 1) / 2)) / (w / 2)
            s.put(x, y, mix(CYAN, (255, 255, 255), max(0, 0.5 - d * 0.5)))
            s.light(x, y, max(0.45, 1 - d * 0.5))
    # 采矿激光：底座、炮管，炮口发青光
    s.box(34, 95, 4, 3, 4, FRAME)
    barrel = s.box(50, 95, 2, 2, 6, shade(FRAME, 1.2))
    glow(s, barrel["front"], CYAN, 1.0)
    # 大灯
    lamp = s.box(66, 95, 3, 2, 1, FRAME)
    glow(s, lamp["front"], LAMP, 1.0)
    save(s.img, ENTITY, "hover_car")
    save(s.glow, ENTITY, "hover_car_glow")


# ---- 重型车

def heavy_rover():
    s = Skin(256, 128, 2612)
    # 底盘：深灰，侧面一排螺栓，顶上是花纹钢板
    chassis = s.box(0, 0, 26, 6, 52, FRAME)
    for key in ("left", "right"):
        x0, y0, w, h = chassis[key]
        for x in range(x0 + 2, x0 + w - 1, 4):
            s.put(x, y0 + 1, shade(METAL, 1.1))
        row(s, chassis[key], h - 1, shade(FRAME, 0.7))
    x0, y0, w, h = chassis["top"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x + 2 * y) % 5 == 0:
                s.put(x, y, shade(FRAME, 1.35))
    hazard(s, (chassis["front"][0], chassis["front"][1] + 1, chassis["front"][2], 2))
    # 驾驶室：米白；正面一整块风挡、下面格栅；侧面一扇窗、一道橙条、车门缝
    cab = s.box(156, 0, 24, 27, 16, BODY)
    front = cab["front"]
    glass(s, (front[0] + 2, front[1] + 3, 20, 9))
    grille(s, (front[0] + 5, front[1] + 18, 14, 4))
    row(s, front, 14, ORANGE)
    row(s, front, 15, shade(ORANGE, 0.85))
    # 风挡底下的仪表灯（隔着玻璃看得见）
    for x in range(front[0] + 4, front[0] + 20, 3):
        s.put(x, front[1] + 10, (90, 220, 200))
        s.light(x, front[1] + 10, 0.8)
    for key in ("left", "right"):
        x0, y0, w, h = cab[key]
        # "right" 面的右边是车头，"left" 面的左边是车头：窗户靠车头那一边
        wx = x0 + w - 11 if key == "right" else x0 + 2
        glass(s, (wx, y0 + 3, 9, 8))
        row(s, cab[key], 14, ORANGE)
        row(s, cab[key], 15, shade(ORANGE, 0.85))
        dx = x0 + w - 12 if key == "right" else x0 + 11
        for y in range(y0 + 2, y0 + 24):
            s.put(dx, y, shade(BODY, 0.72))
    for c in range(3, 21, 4):
        col(s, cab["top"], c, shade(BODY, 0.8))
    # 货舱：橙色集装箱，侧面竖着的瓦楞，后面两扇门
    cargo = s.box(0, 58, 24, 18, 18, ORANGE)
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = cargo[key]
        for x in range(x0 + 1, x0 + w - 1, 2):
            s.vline(x, y0 + 1, y0 + h - 1, shade(ORANGE, 0.82))
        row(s, cargo[key], 0, shade(ORANGE, 1.15))
        row(s, cargo[key], h - 1, shade(ORANGE, 0.6))
    fill(s, cargo["top"], shade(ORANGE, 0.9))
    # 精炼机：灰色金属，顶上一圈警示条；侧面散热格栅和一根管子；背面一扇暗着的观察窗
    refiner = s.box(84, 58, 22, 20, 15, METAL)
    for key in ("left", "right", "back", "front"):
        x0, y0, w, h = refiner[key]
        hazard(s, (x0, y0, w, 2))
    for key in ("left", "right"):
        x0, y0, w, h = refiner[key]
        grille(s, (x0 + 3, y0 + 5, w - 6, 8), METAL)
        row(s, refiner[key], 15, shade(METAL, 0.6))
        row(s, refiner[key], 16, shade(METAL, 1.25))
    x0, y0, w, h = refiner["back"]
    for y in range(y0 + 6, y0 + 12):
        for x in range(x0 + 6, x0 + 16):
            edge = x in (x0 + 6, x0 + 15) or y in (y0 + 6, y0 + 11)
            s.put(x, y, FRAME if edge else (40, 26, 20))
    x0, y0, w, h = refiner["top"]
    for y in range(y0 + 3, y0 + h - 3):
        for x in range(x0 + 4, x0 + 12):
            edge = x in (x0 + 4, x0 + 11) or y in (y0 + 3, y0 + h - 4)
            s.put(x, y, shade(METAL, 0.7 if edge else 0.95))
    # 观察窗里的炉火：只在发光层上亮，皮肤上也是炉火色
    furnace = s.box(158, 58, 10, 6, 1, FRAME)
    x0, y0, w, h = furnace["back"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = (y - y0) / max(1, h - 1)
            flicker = s.rng.uniform(-0.12, 0.12)
            s.put(x, y, mix(FIRE, (255, 236, 160), max(0, t - 0.2 + flicker)))
            s.light(x, y, 0.8 + 0.2 * t)
    # 烟囱：顶上一圈黑
    chimney = s.box(180, 58, 3, 8, 3, FRAME)
    fill(s, chimney["top"], (18, 18, 20))
    # 灯排：正面一排大灯
    bar = s.box(192, 58, 20, 2, 3, FRAME)
    x0, y0, w, h = bar["front"]
    for x in range(x0, x0 + w):
        if (x - x0) % 4 in (1, 2):
            for y in range(y0, y0 + h):
                s.put(x, y, LAMP)
                s.light(x, y, 1.0)
    # 八个轮子共用一块
    wheel(s, 0, 94, 5, 10, 10)
    # 采矿激光
    s.box(30, 94, 5, 3, 5, FRAME)
    barrel = s.box(50, 94, 2, 2, 7, shade(FRAME, 1.2))
    glow(s, barrel["front"], CYAN, 1.0)
    # 大灯、天线（顶上一盏红灯）、前保险杠（警示条）
    lamp = s.box(68, 94, 4, 3, 1, FRAME)
    glow(s, lamp["front"], LAMP, 1.0)
    antenna = s.box(78, 94, 1, 10, 1, FRAME)
    glow(s, antenna["top"], (230, 50, 40), 1.0)
    for key in ("left", "right", "front", "back"):
        x0, y0, w, h = antenna[key]
        s.put(x0, y0, (230, 50, 40))
        s.light(x0, y0, 1.0)
    bumper = s.box(82, 94, 24, 3, 2, FRAME)
    hazard(s, bumper["front"])
    save(s.img, ENTITY, "heavy_rover")
    save(s.glow, ENTITY, "heavy_rover_glow")


# ---- 机甲

def exo_mech():
    s = Skin(128, 128, 2613)
    # 胸口：米白装甲板，正面玻璃下面两道橙色 V 形条，侧面一道橙条和散热口；背面是深灰的机械
    torso = s.box(0, 0, 20, 23, 18, BODY)
    x0, y0, w, h = torso["front"]
    for i in range(6):
        for dy in (0, 1):
            s.put(x0 + 4 + i, y0 + 15 + i // 2 + dy, ORANGE)
            s.put(x0 + w - 5 - i, y0 + 15 + i // 2 + dy, ORANGE)
    row(s, torso["front"], 21, shade(BODY, 0.7))
    for key in ("left", "right"):
        row(s, torso[key], 4, ORANGE)
        row(s, torso[key], 5, shade(ORANGE, 0.85))
        x0, y0, w, h = torso[key]
        grille(s, (x0 + 4, y0 + 9, w - 8, 8), shade(BODY, 0.6))
    fill(s, torso["back"], FRAME)
    x0, y0, w, h = torso["back"]
    for y in range(y0 + 2, y0 + h - 2, 3):
        s.hline(x0 + 2, x0 + w - 2, y, shade(FRAME, 0.7))
    for c in (4, 15):
        col(s, torso["top"], c, shade(BODY, 0.75))
    # 驾驶舱玻璃：深青色，发光层上是淡淡的平视仪表
    canopy = faces(76, 0, 14, 10, 1)
    for rect in canopy.values():
        fill(s, rect, FRAME)
    glass(s, canopy["front"])
    x0, y0, w, h = canopy["front"]
    for x in range(x0 + 3, x0 + w - 3):
        s.light(x, y0 + h // 2, 0.25)
    for y in range(y0 + 2, y0 + h - 2):
        s.light(x0 + w // 2, y, 0.25)
    # 头：米白，正面一道青色的面罩
    head = s.box(76, 11, 8, 4, 9, BODY)
    x0, y0, w, h = head["front"]
    for x in range(x0, x0 + w):
        for y in (y0 + 1, y0 + 2):
            s.put(x, y, mix(CYAN, (255, 255, 255), 0.25 if x in (x0 + 3, x0 + 4) else 0))
            s.light(x, y, 1.0 if x in (x0 + 3, x0 + 4) else 0.7)
    for key in ("left", "right"):
        row(s, head[key], 3, ORANGE)
    # 骨盆：深灰
    pelvis = s.box(0, 41, 16, 4, 12, FRAME)
    row(s, pelvis["front"], 1, shade(FRAME, 1.3))
    # 喷气包：灰色金属，顶上橙色的盖
    pack = s.box(56, 41, 5, 15, 5, METAL)
    for key in ("left", "right", "front", "back"):
        row(s, pack[key], 0, ORANGE)
        row(s, pack[key], 1, ORANGE)
        x0, y0, w, h = pack[key]
        for y in range(y0 + 4, y0 + h - 2, 3):
            s.hline(x0, x0 + w, y, shade(METAL, 0.7))
    fill(s, pack["top"], ORANGE)
    # 喷口：底面发橙光
    nozzle = s.box(76, 41, 3, 2, 3, (40, 40, 44))
    glow(s, nozzle["bottom"], (255, 170, 80), 1.0)
    # 大腿：米白装甲，上下两头深灰的关节
    thigh = s.box(0, 61, 5, 12, 5, BODY)
    for key in ("left", "right", "front", "back"):
        row(s, thigh[key], 0, FRAME)
        row(s, thigh[key], 11, FRAME)
        row(s, thigh[key], 5, ORANGE)
    # 小腿：深灰液压杆，一道橙线
    shin = s.box(20, 61, 4, 10, 4, FRAME)
    for key in ("left", "right", "front", "back"):
        col(s, shin[key], 1, shade(METAL, 1.1))
    # 脚：深灰，底面是防滑纹，脚尖一道警示条
    foot = s.box(36, 61, 7, 4, 9, FRAME)
    tread(s, foot["bottom"])
    hazard(s, (foot["front"][0], foot["front"][1] + 2, foot["front"][2], 2))
    # 护膝：橙色
    s.box(68, 61, 5, 4, 2, ORANGE)
    # 肩甲：橙色，边上一道深色
    shoulder = s.box(0, 78, 7, 5, 7, ORANGE)
    for key in ("left", "right", "front", "back"):
        row(s, shoulder[key], 4, shade(ORANGE, 0.6))
    # 上臂：深灰
    s.box(28, 78, 5, 10, 5, FRAME)
    # 炮身：米白，一道橙条，正面深色
    cannon = s.box(48, 78, 7, 7, 14, BODY)
    for key in ("left", "right", "top", "bottom"):
        x0, y0, w, h = cannon[key]
        if w > h:
            s.hline(x0, x0 + w, y0 + h // 2, ORANGE)
        else:
            s.vline(x0 + w // 2, y0, y0 + h, ORANGE)
    fill(s, cannon["front"], (36, 36, 40))
    # 炮管：炮口发橙光
    barrel = s.box(90, 78, 3, 3, 8, shade(FRAME, 1.15))
    glow(s, barrel["front"], (255, 176, 74), 1.0)
    # 扫描仪：米白，正面一块青色的镜头；扫描盘发青光的格子
    scanner = s.box(0, 99, 5, 5, 10, BODY)
    glow(s, (scanner["front"][0] + 1, scanner["front"][1] + 1, 3, 3), CYAN, 0.9)
    dish = s.box(30, 99, 6, 6, 1, METAL)
    x0, y0, w, h = dish["front"]
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if (x - x0) % 2 == 0 or (y - y0) % 2 == 0:
                s.put(x, y, shade(CYAN, 0.8))
                s.light(x, y, 0.55)
    # 天线：顶上一盏红灯
    antenna = s.box(44, 99, 1, 5, 1, FRAME)
    glow(s, antenna["top"], (230, 50, 40), 1.0)
    save(s.img, ENTITY, "exo_mech")
    save(s.glow, ENTITY, "exo_mech_glow")


# ---- 物品图标

def icon_canvas():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), tuple(c[:3]) + (255,))

    return img, put


def hover_car_icon():
    """侧面看的悬浮车（车头朝左）：扁平的米白车身、橙条、斜的挡风玻璃，车底一道青光，没有轮子。"""
    img, put = icon_canvas()
    for x in range(1, 15):
        for y in range(8, 11):
            put(x, y, shade(BODY, 1.08 - (y - 8) * 0.08))
    for x in range(0, 4):
        put(x, 9, BODY)
        put(x, 10, BODY)
    put(0, 8, FRAME)
    for x in range(1, 15):
        put(x, 9, ORANGE)
    for x in range(1, 15):
        put(x, 11, FRAME)
    # 挡风玻璃和靠背
    for i, y in enumerate((7, 6, 5)):
        put(5 + i, y, GLASS)
        put(6 + i, y, mix(GLASS, (200, 230, 240), 0.4))
    for y in range(5, 8):
        put(11, y, SEAT)
    put(12, 5, ORANGE)
    # 翼舱和尾部喷口
    for x in range(9, 14):
        put(x, 10, shade(ORANGE, 0.85))
    put(15, 9, CYAN)
    put(15, 10, CYAN)
    # 推力垫的青光
    for x in (2, 3, 4, 10, 11, 12):
        put(x, 12, CYAN)
        put(x, 13, shade(CYAN, 0.6))
    # 激光炮管
    put(1, 7, FRAME)
    put(2, 7, FRAME)
    put(0, 7, CYAN)
    save(img, ITEM, "hover_car")


def heavy_rover_icon():
    """侧面看的重型车（车头朝左）：驾驶室、橙色货舱、灰色精炼机和烟囱，四个大轮子。"""
    img, put = icon_canvas()
    # 底盘
    for x in range(0, 16):
        put(x, 10, FRAME)
        put(x, 11, shade(FRAME, 0.8))
    # 驾驶室
    for x in range(0, 5):
        for y in range(3, 10):
            put(x, y, shade(BODY, 1.05 - (y - 3) * 0.03))
    for x in range(1, 4):
        for y in range(4, 6):
            put(x, y, GLASS)
    put(1, 4, mix(GLASS, (200, 230, 240), 0.4))
    for x in range(0, 5):
        put(x, 7, ORANGE)
    put(0, 8, LAMP)
    # 货舱
    for x in range(5, 11):
        for y in range(5, 10):
            put(x, y, shade(ORANGE, 0.82 if x % 2 else 1.0))
    # 精炼机和烟囱
    for x in range(11, 16):
        for y in range(4, 10):
            put(x, y, METAL)
    for x in range(11, 16):
        put(x, 4, HAZARD if x % 2 else (36, 36, 40))
    put(13, 7, FIRE)
    put(14, 7, FIRE)
    for y in range(1, 4):
        put(14, y, FRAME)
    # 驾驶室顶的灯排
    put(1, 2, LAMP)
    put(3, 2, LAMP)
    # 四个轮子
    for cx in (2, 6, 9, 13):
        for y in range(10, 15):
            for x in range(cx - 2, cx + 3):
                d = math.hypot(x - cx, y - 12.5)
                if d <= 2.1:
                    put(x, y, TIRE)
                if d <= 0.8:
                    put(x, y, RIM)
    save(img, ITEM, "heavy_rover")


def exo_mech_icon():
    """正面看的机甲：米白的胸口、深青色的驾驶舱玻璃、青色面罩，右臂（画面左边）一门炮，两条腿。"""
    img, put = icon_canvas()
    # 头和面罩
    for x in range(6, 10):
        put(x, 0, BODY)
        put(x, 1, CYAN)
    put(9, 0, FRAME)
    # 胸口
    for x in range(4, 12):
        for y in range(2, 8):
            put(x, y, shade(BODY, 1.05 - (y - 2) * 0.03))
    for x in range(5, 11):
        for y in range(3, 6):
            put(x, y, GLASS)
    put(5, 3, mix(GLASS, (200, 230, 240), 0.4))
    put(6, 6, ORANGE)
    put(9, 6, ORANGE)
    # 骨盆
    for x in range(5, 11):
        put(x, 8, FRAME)
    # 右臂的炮（画面左边）
    for y in range(2, 4):
        put(2, y, ORANGE)
        put(3, y, ORANGE)
    for y in range(4, 9):
        put(1, y, BODY)
        put(2, y, BODY)
        put(3, y, shade(BODY, 0.8))
    put(2, 9, FRAME)
    put(2, 10, (255, 176, 74))
    # 左臂的扫描仪（画面右边）
    for y in range(2, 4):
        put(12, y, ORANGE)
        put(13, y, ORANGE)
    for y in range(4, 8):
        put(12, y, FRAME)
        put(13, y, BODY)
    put(13, 8, CYAN)
    # 两条腿：大腿米白，小腿深灰，脚
    for lx in (5, 9):
        for y in range(9, 12):
            put(lx, y, BODY)
            put(lx + 1, y, shade(BODY, 0.85))
        for y in range(12, 14):
            put(lx, y, FRAME)
            put(lx + 1, y, shade(FRAME, 0.8))
        put(lx, 11, ORANGE)
        for x in range(lx - 1, lx + 3):
            put(x, 14, FRAME)
            put(x, 15, shade(FRAME, 0.7))
    save(img, ITEM, "exo_mech")


# ---- 载具召唤台

def block_canvas(rng):
    casing = (66, 70, 78)
    img = Image.new("RGBA", (16, 16), casing + (255,))
    for y in range(16):
        for x in range(16):
            f = 1 + rng.uniform(-0.04, 0.04)
            if x == 0 or y == 0:
                f *= 1.35
            elif x == 15 or y == 15:
                f *= 0.6
            img.putpixel((x, y), shade(casing, f) + (255,))
    return img, casing


def bput(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c[:3]) + (255,))


def summoner_front():
    rng = random.Random(2614)
    img, casing = block_canvas(rng)
    screen = (8, 20, 26)
    for y in range(2, 11):
        for x in range(2, 14):
            bput(img, x, y, screen)
    for x in range(2, 14):
        bput(img, x, 1, shade(casing, 0.45))
        bput(img, x, 11, shade(casing, 1.25))
    # 屏幕上一辆车的轮廓（车头朝左）和一圈定位环
    for x in range(4, 11):
        bput(img, x, 7, CYAN)
    for x in range(5, 9):
        bput(img, x, 6, shade(CYAN, 0.8))
    bput(img, 5, 8, shade(CYAN, 0.7))
    bput(img, 9, 8, shade(CYAN, 0.7))
    for a in range(0, 360, 30):
        x = 11 + round(1.6 * math.cos(math.radians(a)))
        y = 4 + round(1.6 * math.sin(math.radians(a)))
        bput(img, x, y, ORANGE)
    bput(img, 11, 4, (255, 220, 160))
    # 下面一排按键，最右边一个橙色
    for x in (3, 5, 7, 9):
        bput(img, x, 13, shade(casing, 1.55))
        bput(img, x + 1, 13, shade(casing, 1.2))
    bput(img, 11, 13, ORANGE)
    bput(img, 12, 13, shade(ORANGE, 0.75))
    bput(img, 11, 14, shade(ORANGE, 0.6))
    bput(img, 12, 14, shade(ORANGE, 0.45))
    save(img, BLOCK, "vehicle_summoner_front")


def summoner_side():
    rng = random.Random(2615)
    img, casing = block_canvas(rng)
    for y in range(3, 13, 2):
        for x in range(3, 12):
            bput(img, x, y, shade(casing, 0.5))
            bput(img, x, y + 1, shade(casing, 1.15))
    for y in range(3, 13):
        bput(img, 13, y, mix(casing, ORANGE, 0.35 + 0.4 * ((y % 4) == 0)))
    save(img, BLOCK, "vehicle_summoner_side")


def summoner_top():
    """顶上一个落点标记：橙色的圈，四个方向的三角。"""
    rng = random.Random(2616)
    img, casing = block_canvas(rng)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if 4.2 <= d <= 5.3:
                bput(img, x, y, ORANGE)
            elif d <= 1.6:
                bput(img, x, y, shade(ORANGE, 0.85))
    for i in range(2):
        for dx in range(-i, i + 1):
            bput(img, 7 + dx, 1 + i, HAZARD)
            bput(img, 8 + dx, 1 + i, HAZARD)
            bput(img, 7 + dx, 14 - i, HAZARD)
            bput(img, 8 + dx, 14 - i, HAZARD)
            bput(img, 1 + i, 7 + dx, HAZARD)
            bput(img, 1 + i, 8 + dx, HAZARD)
            bput(img, 14 - i, 7 + dx, HAZARD)
            bput(img, 14 - i, 8 + dx, HAZARD)
    save(img, BLOCK, "vehicle_summoner_top")


if __name__ == "__main__":
    hover_car()
    heavy_rover()
    exo_mech()
    hover_car_icon()
    heavy_rover_icon()
    exo_mech_icon()
    summoner_front()
    summoner_side()
    summoner_top()
    print("vehicle textures written")
