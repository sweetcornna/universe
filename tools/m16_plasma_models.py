"""生成等离子炮塔、近程等离子炮塔的方块模型（ADR-0028 §八）。

用法：python tools/m16_plasma_models.py [模型目录]
默认输出到 src/main/resources/assets/universe/models/block。和 m16_turret_models.py 一样：
共用一个底座；转台（只转方位）和炮管（再转仰角）各出一个独立模型，转轴在 (8, 11, 8)，炮管沿 +Z 伸出去，
由 TurretRenderer 转动。物品模型把三部分按静止的姿态拼在一起。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_models as fm  # noqa: E402
from factory_models import box  # noqa: E402
import m16_turret_models as tm  # noqa: E402

if len(sys.argv) > 1:
    fm.OUT = sys.argv[1]


def plasma():
    """等离子炮塔：叉架托着一个方鼓鼓的约束腔，两侧散热片；粗炮管外面套三圈磁环，炮口一圈发光的约束环。"""
    head = [
        box((4, 7, 4), (12, 8.5, 12), "housing"),
        box((2.5, 8.5, 5.5), (4, 13.5, 10.5), "housing"),
        box((12, 8.5, 5.5), (13.5, 13.5, 10.5), "housing"),
        box((2, 9.5, 6.5), (2.5, 12.5, 9.5), "accent"),
        box((13.5, 9.5, 6.5), (14, 12.5, 9.5), "accent"),
    ]
    barrel = [
        box((4.5, 8.5, 4), (11.5, 13.5, 11), "housing"),
        box((6, 10, 3.5), (10, 12, 4), "glow", light=12),
        box((6, 9, 11), (10, 13, 20.5), "barrel"),
        box((5.25, 8.25, 12.5), (10.75, 13.75, 13.5), "accent"),
        box((5.25, 8.25, 15.5), (10.75, 13.75, 16.5), "accent"),
        box((5.25, 8.25, 18.5), (10.75, 13.75, 19.5), "accent"),
        box((6.5, 9.5, 20.5), (9.5, 12.5, 21.5), "glow", light=15),
    ]
    tm.turret("plasma_turret", head, barrel, {"glow": "universe:block/plasma_turret_glow"})


def sr_plasma():
    """近程等离子炮塔：矮胖的炮塔；短发射器的前端分成上下两条导轨，中间夹着一段发紫光的线圈。"""
    head = [
        box((3, 7, 3), (13, 9, 13), "housing"),
        box((3.5, 9, 4.5), (5, 12.5, 11.5), "housing"),
        box((11, 9, 4.5), (12.5, 12.5, 11.5), "housing"),
    ]
    barrel = [
        box((5, 9, 4.5), (11, 13, 11), "housing"),
        box((5.5, 12, 11), (10.5, 13, 18), "barrel"),
        box((5.5, 9, 11), (10.5, 10, 18), "barrel"),
        box((6.5, 10, 11), (9.5, 12, 16), "glow", light=13),
        box((5, 8.5, 17), (11, 9, 18.5), "accent"),
        box((5, 13, 17), (11, 13.5, 18.5), "accent"),
    ]
    tm.turret("sr_plasma_turret", head, barrel, {"glow": "universe:block/sr_plasma_turret_glow"})


def main():
    plasma()
    sr_plasma()


if __name__ == "__main__":
    main()
