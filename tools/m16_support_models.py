"""生成 M16（ADR-0028 §五）信号塔、干扰塔的方块模型。

用法：python tools/m16_support_models.py
两格高，分 _lower、_upper，工作时上半截发光的另出 _upper_on，物品用两半叠起来的 _item（照 factory_models.py）。
模型都朝北，方块状态由数据生成按朝向转。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_models import box, tall  # noqa: E402

SIDES = ("north", "south", "west", "east")


def sides(texture, top=None, bottom=None):
    faces = {face: texture for face in SIDES}
    faces["up"] = top
    faces["down"] = bottom
    return faces


def base_plate():
    plate = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
    return [
        box((1, 0, 1), (15, 2, 15), plate, cull={"down": "down"}),
        box((0, 0, 0), (3, 3, 3), "casing", cull={"down": "down"}),
        box((13, 0, 0), (16, 3, 3), "casing", cull={"down": "down"}),
        box((0, 0, 13), (3, 3, 16), "casing", cull={"down": "down"}),
        box((13, 0, 13), (16, 3, 16), "casing", cull={"down": "down"}),
    ]


def signal_tower():
    """信号塔：红白桁架的塔身，上半截一圈天线，顶上警示灯。"""
    textures = {
        "particle": "universe:block/signal_tower_pole",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "pole": "universe:block/signal_tower_pole",
        "dish": "universe:block/signal_tower_dish",
        "beacon": "universe:block/signal_tower_top_off",
        "beacon_on": "universe:block/signal_tower_top",
    }
    lower = base_plate() + [
        box((4, 2, 4), (12, 16, 12), sides("pole")),
    ]

    def upper(beacon, light):
        flange = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
        return [
            box((5, 0, 5), (11, 7, 11), sides("pole")),
            # 天线阵：四面各一块
            box((2, 7, 6), (5, 12, 10), sides("dish", "plate", "plate")),
            box((11, 7, 6), (14, 12, 10), sides("dish", "plate", "plate")),
            box((6, 7, 2), (10, 12, 5), sides("dish", "plate", "plate")),
            box((6, 7, 11), (10, 12, 14), sides("dish", "plate", "plate")),
            box((5, 7, 5), (11, 12, 11), flange),
            box((6, 12, 6), (10, 16, 10), beacon, light=light, cull={"up": "up"}),
        ]

    tall("signal_tower", textures, lower, upper("beacon", 0), upper_on=upper("beacon_on", 15))


def jammer_tower():
    """干扰塔：方的机壳，上半截三圈发射线圈，顶上发射头。"""
    textures = {
        "particle": "universe:block/jammer_tower_body",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "body": "universe:block/jammer_tower_body",
        "coil": "universe:block/jammer_tower_coil",
        "emitter": "universe:block/jammer_tower_top_off",
        "emitter_on": "universe:block/jammer_tower_top",
    }
    lower = base_plate() + [
        box((3, 2, 3), (13, 16, 13), sides("body")),
    ]

    def upper(emitter, light):
        flange = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
        elements = [box((4, 0, 4), (12, 4, 12), sides("body", "plate"))]
        for i, y in enumerate((4, 7, 10)):
            inset = 3 + i
            elements.append(box((inset, y, inset), (16 - inset, y + 2, 16 - inset), sides("coil", "plate", "plate")))
            elements.append(box((6, y + 2, 6), (10, y + 3, 10), flange))
        elements.append(box((5, 13, 5), (11, 16, 11), emitter, light=light, cull={"up": "up"}))
        return elements

    tall("jammer_tower", textures, lower, upper("emitter", 0), upper_on=upper("emitter_on", 15))


def main():
    signal_tower()
    jammer_tower()


if __name__ == "__main__":
    main()
