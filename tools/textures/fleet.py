"""机甲舰队的贴图（ADR-0031、ADR-0033 §一）：五种单位的实体皮肤和发光层，五个物品图标。

实体贴图的 UV 布局和 client/mecha/FleetUnitModel.java 一一对应（每个方块盒子的 texOffs 和尺寸），改了那边要同步改这里。

涂装照着伊卡洛斯的机甲：白漆的机身、钢灰的框架和关节、一道青色的识别涂装；炮口、推进器、舷窗是发光层。
发光层用 RenderTypes.eyes 画、渲染时着青色，所以画成白色的亮度图（和守卫机一样，见 hostiles.py），底图上发光的地方是暗的灯罩。
地面的三种是巴掌大的无人机，细节少、对比强；两种舰船面积大，板缝、舷窗、编号条多一些，远看也认得出船头。
"""
from __future__ import annotations

from canvas import Canvas
from hostiles import SIDES, Skin, draw_cached, faces, seed
from palette import CYAN_GLOW, GRAPHITE, SCREEN, STEEL, TEAL, TITANIUM, WHITE
from recipe import texture


# ---------------------------------------------------------------- 共用的画法

def plate(s: Skin, u, v, w, h, d, r=WHITE, lv=5, seams=True) -> dict:
    """白漆的机身盒子：倒角，长的面上隔几格一道竖的板缝（暗一级的缝、右边亮一级的唇）。"""
    f = s.box(u, v, w, h, d, r, lv)
    if seams:
        for key in ("left", "right", "top", "bottom"):
            rect = f[key]
            rw = rect[2]
            if rw >= 8:
                for i in range(rw // 3, rw - 2, max(4, rw // 3)):
                    s.col(rect, i, r, lv - 2, 1, -2)
                    s.col(rect, i + 1, r, min(r.top, lv + 1), 1, -2)
    return f


def stripe(s: Skin, f: dict, j: int, r=TEAL, lv=4, keys=("left", "right")):
    """识别涂装：侧面一道横条，朝船头的一端暗一级。"""
    for key in keys:
        rect = f[key]
        s.row(rect, j, r, lv, 1, -2)
        s.px(rect, 1 if key == "left" else -2, j, r, lv - 1)


def nozzle(s: Skin, rect, strength=1.0):
    """推进器的喷口：一圈钢的唇，里面深色，发光层从中间往外暗。"""
    x0, y0, w, h = rect
    s.fill(rect, STEEL, 2)
    for j in range(h):
        for i in range(w):
            inner = 0 < i < w - 1 and 0 < j < h - 1 or w <= 2 or h <= 2
            if inner:
                s.px(rect, i, j, GRAPHITE, 0)
    s.glow_fill(rect, strength)


def muzzle(s: Skin, f: dict, r=GRAPHITE, lv=2, glow=0.9):
    """炮管、发射管：深色，侧面一道亮边，正面（朝前）是炮口，发光层点亮。"""
    for key, rect in f.items():
        s.fill(rect, r, lv + (1 if key == "top" else 0))
    for key in SIDES:
        rect = f[key]
        if rect[3] >= 1:
            s.row(rect, 0, STEEL, 3)
    front = f["front"]
    s.fill(front, GRAPHITE, 0)
    s.glow_fill(front, glow, falloff=False)


def window(s: Skin, rect, j, i0, i1, glow=0.6):
    """一排舷窗：深色玻璃，发光层透出暖一点的舱内光（着色后是青的）。"""
    for i in range(i0, i1 + 1, 2):
        s.px(rect, i, j, SCREEN, 1)
        s.glow(rect, i, j, glow)


def lamp(s: Skin, rect, i, j, strength=1.0):
    s.px(rect, i, j, SCREEN, 2)
    s.glow(rect, i, j, strength)


# ---------------------------------------------------------------- 原型机

@draw_cached
def prototype_skin():
    s = Skin(64, 32, seed("fleet_prototype"))
    body = plate(s, 0, 0, 6, 4, 8, seams=False)
    stripe(s, body, 1)
    # 机头：一块深色的传感器窗，中间一盏灯
    front = body["front"]
    s.row(front, 1, SCREEN, 1, 1, -2)
    lamp(s, front, 2, 1, 0.8)
    lamp(s, front, 3, 1, 0.8)
    # 机背一块散热格栅，尾部推进口
    top = body["top"]
    for j in range(2, 6):
        s.row(top, j, STEEL, 3 if j % 2 else 2, 1, -2)
    nozzle(s, (body["back"][0] + 1, body["back"][1] + 1, 4, 2))
    # 腹下一块舱门
    bottom = body["bottom"]
    s.row(bottom, 2, WHITE, 3, 1, -2)
    s.row(bottom, 5, WHITE, 3, 1, -2)
    # 激光发射管、天线
    muzzle(s, faces(0, 14, 1, 1, 3))
    ant = s.box(12, 14, 1, 3, 1, GRAPHITE, 2)
    lamp(s, ant["top"], 0, 0, 1.0)
    # 两侧的推进舱：钢灰，后面喷口
    pod = s.box(30, 0, 2, 2, 4, STEEL, 4)
    for key in ("left", "right"):
        s.row(pod[key], 0, STEEL, 5)
    s.fill(pod["front"], STEEL, 3)
    nozzle(s, pod["back"], 0.9)
    return s


@texture("entity/fleet_prototype", refs=("entity/allay/allay", "entity/bee/bee"))
def fleet_prototype(rng):
    return prototype_skin().c


@texture("entity/fleet_prototype_glow")
def fleet_prototype_glow(rng):
    return prototype_skin().g


# ---------------------------------------------------------------- 精准无人机

@draw_cached
def precision_skin():
    s = Skin(64, 32, seed("fleet_precision_drone"))
    body = plate(s, 0, 0, 4, 3, 10)
    stripe(s, body, 1)
    top = body["top"]
    # 机背：窄的座舱玻璃，后面一排散热孔
    for j in range(1, 4):
        s.px(top, 1, j, SCREEN, 1)
        s.px(top, 2, j, SCREEN, 2 if j == 1 else 1)
    s.px(top, 2, 1, SCREEN, 3, glint=True)
    for j in range(6, 9):
        s.row(top, j, STEEL, 2, 1, -2)
    front = body["front"]
    s.fill(front, WHITE, 4)
    lamp(s, front, 1, 1, 0.9)
    lamp(s, front, 2, 1, 0.9)
    nozzle(s, body["back"], 0.6)
    # 长发射管：瞄准用的透镜在炮口
    muzzle(s, faces(0, 15, 1, 1, 4), glow=1.0)
    # 尾鳍：白的，后缘一道青色
    fin = s.box(30, 7, 1, 3, 3, WHITE, 4)
    for key in ("left", "right"):
        s.col(fin[key], 0 if key == "right" else -1, TEAL, 4)
        s.row(fin[key], 0, WHITE, 6)
    # 尾部的推进器
    eng = s.box(12, 15, 3, 2, 1, STEEL, 3)
    nozzle(s, eng["back"], 1.0)
    # 前掠翼：白的上面，钢的下面，翼尖一盏灯
    wing = s.box(30, 0, 5, 1, 4, WHITE, 5)
    wtop = wing["top"]
    s.row(wtop, 0, WHITE, 6)
    s.row(wtop, -1, TEAL, 4)
    s.fill(wing["bottom"], STEEL, 3)
    lamp(s, wing["left"], 0, 0, 0.7)
    lamp(s, wing["front"], 0, 0, 0.7)
    return s


@texture("entity/fleet_precision_drone", refs=("entity/allay/allay",))
def fleet_precision_drone(rng):
    return precision_skin().c


@texture("entity/fleet_precision_drone_glow")
def fleet_precision_drone_glow(rng):
    return precision_skin().g


# ---------------------------------------------------------------- 攻击无人机

@draw_cached
def attack_skin():
    s = Skin(64, 64, seed("fleet_attack_drone"))
    body = plate(s, 0, 0, 6, 4, 10)
    stripe(s, body, 1)
    stripe(s, body, 2, TEAL, 3)
    top = body["top"]
    # 背上两道装甲板的缝，尾部一块格栅
    s.row(top, 3, WHITE, 3, 1, -2)
    s.row(top, 7, WHITE, 3, 1, -2)
    for j in range(8, 10):
        for i in range(1, 5, 2):
            s.px(top, i, j, GRAPHITE, 2)
    front = body["front"]
    s.row(front, 1, SCREEN, 1, 1, -2)
    lamp(s, front, 1, 1, 0.8)
    lamp(s, front, 4, 1, 0.8)
    s.fill(body["back"], STEEL, 3)
    # 腹下的等离子炮：粗的炮管，炮口一圈亮
    cannon = faces(0, 16, 2, 2, 6)
    muzzle(s, cannon, r=STEEL, lv=2, glow=1.0)
    for key in SIDES:
        rect = cannon[key]
        if rect[2] >= 6:
            s.px(rect, 2, 1, TEAL, 4)
            s.px(rect, 3, 1, TEAL, 4)
    # 座舱：深色玻璃
    canopy = s.box(34, 9, 3, 1, 4, SCREEN, 1)
    s.px(canopy["top"], 0, 0, SCREEN, 3, glint=True)
    s.px(canopy["top"], 1, 1, SCREEN, 2)
    # 两台发动机
    eng = s.box(18, 16, 2, 2, 3, STEEL, 3)
    for key in ("left", "right"):
        s.row(eng[key], 0, STEEL, 5)
    nozzle(s, eng["back"], 1.0)
    # 三角翼：白漆，前缘钢灰，后缘一道青色，翼尖的航行灯
    wing = s.box(34, 0, 6, 1, 6, WHITE, 5)
    wtop = wing["top"]
    for j in range(6):
        s.px(wtop, j % 6, j, STEEL, 4)
    s.row(wtop, -1, TEAL, 4)
    s.fill(wing["bottom"], STEEL, 3)
    lamp(s, wing["left"], 0, 0, 0.8)
    return s


@texture("entity/fleet_attack_drone", refs=("entity/phantom",))
def fleet_attack_drone(rng):
    return attack_skin().c


@texture("entity/fleet_attack_drone_glow")
def fleet_attack_drone_glow(rng):
    return attack_skin().g


# ---------------------------------------------------------------- 炮艇

@draw_cached
def corvette_skin():
    s = Skin(128, 128, seed("fleet_corvette"))
    hull = plate(s, 0, 0, 12, 8, 36)
    # 两舷：一道青色的识别条、一排舷窗、船尾的编号块
    for key in ("left", "right"):
        rect = hull[key]
        s.row(rect, 2, TEAL, 4, 2, -3)
        s.row(rect, 3, TEAL, 3, 2, -3)
        window(s, rect, 5, 6, 26)
        for i in range(29, 33):
            s.px(rect, i, 2, WHITE, 3)
            s.px(rect, i, 3, GRAPHITE, 2)
    top = hull["top"]
    # 甲板：中间一道钢灰的脊，两边的检修口
    for j in range(top[3]):
        s.px(top, 5, j, STEEL, 4)
        s.px(top, 6, j, STEEL, 3)
    for j0 in (6, 16, 26):
        for j in range(j0, j0 + 3):
            s.px(top, 2, j, WHITE, 3)
            s.px(top, 9, j, WHITE, 3)
    bottom = hull["bottom"]
    s.fill(bottom, STEEL, 4)
    for j in range(2, bottom[3] - 2, 4):
        s.row(bottom, j, STEEL, 2, 1, -2)
    # 舰首：白的，正面一块深色的观察窗
    front = hull["front"]
    s.row(front, 2, SCREEN, 1, 3, -4)
    s.row(front, 3, SCREEN, 1, 3, -4)
    lamp(s, front, 5, 2, 0.7)
    lamp(s, front, 6, 2, 0.7)
    s.fill(hull["back"], STEEL, 3)
    # 舰桥：后置的小塔，前面一排亮的窗
    bridge = plate(s, 0, 46, 6, 4, 8, seams=False)
    s.row(bridge["front"], 1, SCREEN, 1)
    for i in range(6):
        s.glow(bridge["front"], i, 1, 0.8)
    for key in ("left", "right"):
        window(s, bridge[key], 1, 1, 6, 0.5)
    # 发动机块：三个大喷口
    eng = s.box(0, 68, 10, 6, 2, STEEL, 3)
    back = eng["back"]
    for x0 in (0, 4, 7):
        nozzle(s, (back[0] + x0 + (1 if x0 == 0 else 0), back[1] + 1, 3 if x0 == 4 else 2, 4), 1.0)
    # 尖的舰首段
    nose = plate(s, 26, 68, 8, 5, 4, seams=False)
    s.fill(nose["front"], WHITE, 4)
    s.row(nose["front"], 2, SCREEN, 1, 2, -3)
    stripe(s, nose, 1)
    # 挂架、发动机舱
    pylon = s.box(72, 46, 2, 1, 6, STEEL, 3)
    s.row(pylon["top"], 0, STEEL, 5)
    nacelle = plate(s, 30, 46, 4, 4, 16)
    for key in ("left", "right"):
        s.row(nacelle[key], 1, TEAL, 4, 1, -2)
    s.fill(nacelle["front"], STEEL, 4)
    s.px(nacelle["front"], 1, 1, GRAPHITE, 1)
    s.px(nacelle["front"], 2, 2, GRAPHITE, 1)
    nozzle(s, nacelle["back"], 1.0)
    # 炮塔和双联炮管
    turret = s.box(0, 60, 3, 2, 3, STEEL, 4)
    s.px(turret["top"], 1, 1, GRAPHITE, 2)
    muzzle(s, faces(14, 60, 1, 1, 5), r=GRAPHITE, lv=2, glow=1.0)
    return s


@texture("entity/fleet_corvette", refs=("entity/boat/oak",))
def fleet_corvette(rng):
    return corvette_skin().c


@texture("entity/fleet_corvette_glow")
def fleet_corvette_glow(rng):
    return corvette_skin().g


# ---------------------------------------------------------------- 驱逐舰

@draw_cached
def destroyer_skin():
    s = Skin(128, 128, seed("fleet_destroyer"))
    hull = plate(s, 0, 0, 12, 8, 40)
    for key in ("left", "right"):
        rect = hull[key]
        # 两舷：上面一道青条，下面一道钢灰的水线，两排舷窗
        s.row(rect, 1, TEAL, 4, 2, -3)
        s.row(rect, 6, STEEL, 3, 1, -2)
        s.row(rect, 7, STEEL, 2)
        window(s, rect, 3, 8, 30)
        window(s, rect, 4, 9, 31, 0.4)
    top = hull["top"]
    for j in range(top[3]):
        s.px(top, 5, j, STEEL, 4)
        s.px(top, 6, j, STEEL, 3)
    # 前甲板的黄白斜纹（主炮的射界）
    for j in range(2, 7):
        for i in range(1, 11):
            if (i + j) % 4 == 0:
                s.px(top, i, j, WHITE, 3)
    bottom = hull["bottom"]
    s.fill(bottom, STEEL, 3)
    for j in range(3, bottom[3] - 3, 5):
        s.row(bottom, j, GRAPHITE, 2, 1, -2)
    front = hull["front"]
    s.row(front, 3, SCREEN, 1, 2, -3)
    lamp(s, front, 5, 3, 0.8)
    lamp(s, front, 6, 3, 0.8)
    s.fill(hull["back"], STEEL, 3)
    # 上层建筑：一圈窗，前面亮
    sup = plate(s, 0, 50, 8, 4, 14)
    for key in ("left", "right"):
        window(s, sup[key], 1, 1, 12, 0.6)
        s.row(sup[key], 3, TEAL, 3, 1, -2)
    s.row(sup["front"], 1, SCREEN, 1)
    for i in range(8):
        s.glow(sup["front"], i, 1, 0.9)
    # 桅楼：钢灰，顶上一盏航行灯
    tower = s.box(46, 50, 4, 3, 4, STEEL, 4)
    for key in SIDES:
        s.row(tower[key], 1, SCREEN, 1, 1, -2)
    lamp(s, tower["top"], 1, 1, 1.0)
    lamp(s, tower["top"], 2, 2, 1.0)
    # 两舷的装甲带：深一点的钢，一格一格的板
    belt = s.box(0, 70, 2, 4, 24, TITANIUM, 3)
    for key in ("left", "right"):
        rect = belt[key]
        for i in range(3, rect[2] - 1, 5):
            s.col(rect, i, TITANIUM, 1)
            s.col(rect, i + 1, TITANIUM, 4)
    # 发动机：大喷口
    eng = s.box(54, 76, 4, 4, 3, STEEL, 3)
    for key in ("left", "right"):
        s.row(eng[key], 0, STEEL, 5)
    nozzle(s, eng["back"], 1.0)
    # 舰首：楔形的冲角，白漆，青色的舰号
    prow = plate(s, 70, 70, 8, 6, 6, seams=False)
    for key in ("left", "right"):
        rect = prow[key]
        s.px(rect, 2, 2, TEAL, 4)
        s.px(rect, 3, 2, TEAL, 4)
        s.px(rect, 2, 3, TEAL, 3)
    s.fill(prow["front"], WHITE, 4)
    s.row(prow["front"], 3, STEEL, 3, 1, -2)
    # 主炮：钢灰的炮塔，双管，炮口亮
    main = s.box(64, 50, 5, 2, 5, STEEL, 4)
    s.row(main["top"], 0, STEEL, 5)
    s.px(main["top"], 2, 2, GRAPHITE, 1)
    muzzle(s, faces(86, 50, 1, 1, 8), r=GRAPHITE, lv=2, glow=1.0)
    # 点防御炮
    pd = s.box(54, 70, 2, 1, 2, STEEL, 4)
    s.px(pd["top"], 0, 0, STEEL, 5)
    muzzle(s, faces(100, 70, 1, 1, 3), r=GRAPHITE, lv=2, glow=0.8)
    return s


@texture("entity/fleet_destroyer", refs=("entity/boat/oak",))
def fleet_destroyer(rng):
    return destroyer_skin().c


@texture("entity/fleet_destroyer_glow")
def fleet_destroyer_glow(rng):
    return destroyer_skin().g


# ---------------------------------------------------------------- 物品图标

def icon(rows: list[str]) -> Canvas:
    """手绘的图标：白漆 0–6、钢 a–g、石墨 h–l、青涂装 A–G、青光 p–u、玻璃 v–z。"""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    c = Canvas()
    c.draw(rows, {"0123456": WHITE, "abcdefg": STEEL, "hijkl": GRAPHITE, "ABCDEFG": TEAL, "pqrstu": CYAN_GLOW, "vwxyz": SCREEN})
    c.outline(outside=True)
    return c


@texture("item/prototype", refs=("item/phantom_membrane",))
def prototype_icon(rng):
    """俯视的原型机，机头朝上：方的白机身，两侧钢灰的推进舱，机背中间一盏青灯，机头伸出发射管，尾巴喷着青光。"""
    return icon([
        "................",
        "................",
        ".......kk.......",
        ".......jj.......",
        ".....c6655b.....",
        "..iic6C6C55bii..",
        ".ib4c66qq55b4bi.",
        ".ia3c6qtq54b3ai.",
        ".ia3c55qq44b3ai.",
        ".ib4c5544433b4i.",
        "..iic5B4B43bii..",
        ".....c4433b.....",
        "......crrc......",
        ".......ss.......",
        "................",
        "................",
    ])


@texture("item/precision_drone")
def precision_drone_icon(rng):
    """俯视的精准无人机：细长的机身，两片往前掠的白翼，翼的后缘青色，机头一根长发射管。"""
    return icon([
        ".......kk.......",
        ".......jj.......",
        ".......jj.......",
        "......c66b......",
        ".c....c6vb....b.",
        ".cb...c6wb...ba.",
        "..c5b.c65b.b4a..",
        "..c55bc65bb44a..",
        "...c5556544aa...",
        "...cAAc54bAAa...",
        "......c54b......",
        "......c43b......",
        "......bdcb......",
        ".......rr.......",
        ".......ss.......",
        "................",
    ])


@texture("item/attack_drone")
def attack_drone_icon(rng):
    """俯视的攻击无人机：厚的白机身，宽的三角翼，翼尖青色，两台发动机喷着光；机头下面露出等离子炮口。"""
    return icon([
        "................",
        ".......tt.......",
        "......cjjb......",
        "......c66b......",
        ".....c6vw5b.....",
        ".....c6ww5b.....",
        "....cc6655bb....",
        "...c66665544b...",
        "..c666665544bb..",
        ".c5666655544443.",
        ".AA55555444443AA",
        "...bbc5444bba...",
        "....cdb..cdb....",
        "....rs....rs....",
        "................",
        "................",
    ])


@texture("item/corvette", refs=("item/minecart",))
def corvette_icon(rng):
    """侧看的炮艇，船头朝右：长的白船身，青色的识别条，后置的舰桥，船头上面一座双联激光炮塔，尾部推进光。"""
    return icon([
        "................",
        "................",
        "................",
        "................",
        ".......cc.......",
        "......c66b..kkj.",
        "......vvvb.cbjj.",
        "..cc666666666b..",
        "qc66666666666665",
        "qcAAAAAAAAAAAAA4",
        "qc5vv5vv5vv55544",
        "..ba44444444444.",
        "....aaaaaaaaa...",
        "................",
        "................",
        "................",
    ])


@texture("item/destroyer")
def destroyer_icon(rng):
    """侧看的驱逐舰，船头朝右：更高更长的船身，中间的上层建筑和桅楼，船头的双管主炮，两排舷窗，钢灰的水线。"""
    return icon([
        "................",
        "................",
        ".......kk.......",
        ".......kc.......",
        "......c66b......",
        ".....c6vvvb..kkj",
        "....cc66666bcbj.",
        ".cc66666666666b.",
        "qc66666666666665",
        "qcAAAAAAAAAAAAAA",
        "qc5vv5vv5vv5vv54",
        "qc4vv4vv4vv4vv44",
        ".cbbbbbbbbbbbba.",
        "...aaaaaaaaaaa..",
        "................",
        "................",
    ])
