"""生成炮塔的方块模型（ADR-0028 §五）。

用法：python tools/m16_turret_models.py [模型目录]
默认输出到 src/main/resources/assets/universe/models/block。和 factory_models.py 共用写模型的函数。

四种炮塔共用一个底座（方块模型）；转台（只转方位）和炮管（再转仰角）各出一个独立模型，转轴在 (8, 11, 8)，
炮管沿 +Z 伸出去，由 TurretRenderer 转动。物品模型把三部分按静止的姿态拼在一起，炮管抬起 22.5 度。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_models as fm  # noqa: E402
from factory_models import box, model, write  # noqa: E402

if len(sys.argv) > 1:
    fm.OUT = sys.argv[1]

ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}
PIVOT = [8, 11, 8]


def textures(name, extra=None):
    t = {
        "particle": "universe:block/turret_base",
        "base": "universe:block/turret_base",
        "base_top": "universe:block/turret_base_top",
        "ring": "universe:block/turret_ring",
        "housing": "universe:block/%s_housing" % name,
        "barrel": "universe:block/%s_barrel" % name,
        "accent": "universe:block/%s_accent" % name,
    }
    if extra:
        t.update(extra)
    return t


def base():
    faces = {"north": "base", "south": "base", "west": "base", "east": "base", "up": "base_top", "down": "base"}
    return [
        box((0, 0, 0), (16, 4, 16), faces, cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        box((1.5, 4, 1.5), (14.5, 5.5, 14.5), "ring"),
        box((4.5, 5.5, 4.5), (11.5, 7, 11.5), "ring"),
    ]


def turret(name, head, barrel, extra=None):
    t = textures(name, extra)
    write(name, model(t, base()))
    write(name + "_head", model(t, head))
    write(name + "_barrel", model(t, barrel))
    tilted = []
    for e in barrel:
        e = dict(e)
        e["rotation"] = {"angle": -22.5, "axis": "x", "origin": PIVOT}
        tilted.append(e)
    write(name + "_item", model(t, base() + head + tilted, display=ITEM_DISPLAY))


def gauss():
    """高斯炮塔：方形炮塔壳，两侧是线圈舱；两根并排的炮管，炮口有制退器。"""
    head = [
        box((3.5, 7, 4), (12.5, 9, 12), "housing"),
        box((2.5, 8, 6), (3.5, 12.5, 10.5), "accent"),
        box((12.5, 8, 6), (13.5, 12.5, 10.5), "accent"),
        box((3.5, 9, 5), (5, 13, 11), "housing"),
        box((11, 9, 5), (12.5, 13, 11), "housing"),
    ]
    barrel = [
        box((5, 9, 4.5), (11, 13.5, 11.5), "housing"),
        box((5.75, 10, 11.5), (7.5, 11.75, 21.5), "barrel"),
        box((8.5, 10, 11.5), (10.25, 11.75, 21.5), "barrel"),
        box((5.25, 9.5, 20.5), (8, 12.25, 22.5), "accent"),
        box((8, 9.5, 20.5), (10.75, 12.25, 22.5), "accent"),
        box((6.5, 13.5, 6), (9.5, 14.5, 10), "accent"),
    ]
    turret("gauss_turret", head, barrel)


def laser():
    """激光炮塔：圆一点的壳子顶上一道散热鳍；一根发射管，几圈散热片，管口是发光的透镜。"""
    head = [
        box((4, 7, 4), (12, 9, 12), "housing"),
        box((4.5, 9, 5), (6, 13, 11), "housing"),
        box((10, 9, 5), (11.5, 13, 11), "housing"),
        box((7.25, 13, 5.5), (8.75, 14.5, 10.5), "accent"),
    ]
    barrel = [
        box((6, 9, 5.5), (10, 13, 11), "housing"),
        box((6.75, 9.75, 11), (9.25, 12.25, 20.5), "barrel"),
        box((5.75, 8.75, 13), (10.25, 13.25, 14), "accent"),
        box((5.75, 8.75, 16), (10.25, 13.25, 17), "accent"),
        box((6.25, 9.25, 19.5), (9.75, 12.75, 21.5), "accent"),
        box((7, 10, 21.5), (9, 12, 22), "lens", light=15),
    ]
    turret("laser_turret", head, barrel, {"lens": "universe:block/laser_turret_lens"})


def implosion():
    """聚爆炮：又宽又厚的装甲炮塔；一根粗炮管，炮口一圈加强环，管子上面一根复进机。"""
    head = [
        box((2, 7, 3), (14, 9.5, 13), "housing"),
        box((2, 9.5, 3), (4, 14, 12), "housing"),
        box((12, 9.5, 3), (14, 14, 12), "housing"),
        box((4, 13, 3), (12, 14.5, 6), "accent"),
    ]
    barrel = [
        box((4.5, 8.5, 5), (11.5, 13.5, 12), "accent"),
        box((5.75, 9, 12), (10.25, 13, 22), "barrel"),
        box((5.25, 8.5, 20.5), (10.75, 13.5, 23), "accent"),
        box((6.75, 13, 7), (9.25, 14.5, 17), "barrel"),
    ]
    turret("implosion_cannon", head, barrel)


def missile():
    """导弹炮塔：一个叉架托着发射箱，发射箱正面四个发射管，一盏指示灯。"""
    head = [
        box((4, 7, 4), (12, 8.5, 12), "housing"),
        box((2, 8.5, 6), (3.5, 13.5, 10), "housing"),
        box((12.5, 8.5, 6), (14, 13.5, 10), "housing"),
    ]
    pod_faces = {"north": "barrel", "south": "pod_front", "west": "barrel", "east": "barrel", "up": "barrel", "down": "barrel"}
    barrel = [
        box((3.5, 8, 4), (12.5, 14, 14), pod_faces),
        box((3, 7.5, 13), (13, 14.5, 14.5), "accent", faces=("north", "west", "east", "up", "down")),
        box((7.25, 14, 6), (8.75, 15, 7.5), "lamp", light=12),
    ]
    turret("missile_turret", head, barrel, {"pod_front": "universe:block/missile_turret_pod_front",
                                            "lamp": "universe:block/missile_turret_lamp"})


def main():
    gauss()
    laser()
    implosion()
    missile()


if __name__ == "__main__":
    main()
