"""生成 M16（ADR-0028 §八）行星护盾发生器的方块模型。

用法：python tools/m16_shield_models.py
两格高，分 _lower、_upper，穹顶展开时上半截的发射头亮，另出 _upper_on；物品用两半叠起来的 _item（照 factory_models.py）。
下半截是一圈储能电容，上半截是托着发射头的四根支架和一圈抛物面的盘。模型都朝北，方块状态由数据生成按朝向转。
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


def planetary_shield_generator():
    textures = {
        "particle": "universe:block/planetary_shield_generator_bank",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "bank": "universe:block/planetary_shield_generator_bank",
        "core": "universe:block/planetary_shield_generator_core",
        "dish": "universe:block/planetary_shield_generator_dish",
        "emitter": "universe:block/planetary_shield_generator_top_off",
        "emitter_on": "universe:block/planetary_shield_generator_top",
    }
    plate = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
    lower = [
        box((0, 0, 0), (16, 3, 16), plate, cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        # 储能电容：四面一排电容格
        box((2, 3, 2), (14, 14, 14), sides("bank")),
        box((1, 14, 1), (15, 16, 15), plate, cull={"up": "up"}),
    ]

    def upper(emitter, light):
        elements = [
            # 中间的导能柱
            box((5, 0, 5), (11, 7, 11), sides("core", "plate")),
            # 抛物面的盘：一圈宽的、一圈窄的，叠出弧度
            box((1, 6, 1), (15, 8, 15), sides("dish", "dish", "dish")),
            box((3, 8, 3), (13, 9, 13), sides("dish", "dish", None)),
        ]
        # 四根支架托着发射头
        for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12)):
            elements.append(box((x, 8, z), (x + 2, 13, z + 2), sides("core", "plate", None)))
        elements.append(box((4, 12, 4), (12, 13, 12), sides("core", "plate", "plate")))
        elements.append(box((6, 9, 6), (10, 16, 10), emitter, light=light, cull={"up": "up"}))
        return elements

    tall("planetary_shield_generator", textures, lower, upper("emitter", 0), upper_on=upper("emitter_on", 15))


def main():
    planetary_shield_generator()


if __name__ == "__main__":
    main()
