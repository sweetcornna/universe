"""生成终局设备的方块模型（ADR-0018）。

用法：python tools/endgame_models.py [模型目录]
默认输出到 src/main/resources/assets/universe/models/block。和 factory_models.py 共用写模型的函数。

人造恒星两格高：下半截是底座和线圈箱，上半截四根立柱和顶框。中间的恒星（核心和日冕）各出一个独立模型，
由方块实体渲染器转动、缩放；物品模型把核心放进去。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import factory_models as fm  # noqa: E402
from factory_models import box, model, shifted, tall, write  # noqa: E402

if len(sys.argv) > 1:
    fm.OUT = sys.argv[1]

ALL_CULL = {"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}


def pillars(y0, y1):
    return [box((x, y0, z), (x + 2.5, y1, z + 2.5), "pillar")
            for x in (0, 13.5) for z in (0, 13.5)]


def artificial_star():
    textures = {
        "particle": "universe:block/artificial_star_base",
        "base": "universe:block/artificial_star_base",
        "top": "universe:block/artificial_star_top",
        "coil": "universe:block/artificial_star_coil",
        "coil_on": "universe:block/artificial_star_coil_on",
        "pillar": "universe:block/artificial_star_pillar",
        "frame": "universe:block/artificial_star_frame",
        "core": "universe:block/artificial_star_core",
        "corona": "universe:block/artificial_star_corona",
    }
    base_faces = {"north": "base", "south": "base", "west": "base", "east": "base", "up": "top", "down": "base"}

    def lower_parts(coil):
        housing = {"north": coil, "south": coil, "west": coil, "east": coil, "up": "top", "down": None}
        parts = [
            box((0, 0, 0), (16, 4, 16), base_faces, cull=ALL_CULL),
            box((3, 4, 3), (13, 15, 13), housing, light=10 if coil == "coil_on" else 0),
            # 顶上朝上的发射极
            box((6, 15, 6), (10, 16, 10), "frame"),
        ]
        return parts + pillars(4, 16)

    lower = lower_parts("coil")
    lower_on = lower_parts("coil_on")
    upper = pillars(0, 14) + [
        box((0, 14, 0), (16, 16, 2.5), "frame"),
        box((0, 14, 13.5), (16, 16, 16), "frame"),
        box((0, 14, 2.5), (2.5, 16, 13.5), "frame"),
        box((13.5, 14, 2.5), (16, 16, 13.5), "frame"),
        # 顶框中间朝下的发射极，两根横梁托着
        box((2.5, 14.5, 7), (13.5, 15.5, 9), "frame"),
        box((6.5, 12.5, 6.5), (9.5, 14.5, 9.5), "frame"),
    ]
    # 恒星：中心在上半截的 (8, 7, 8)
    core = [box((5, 4, 5), (11, 10, 11), "core", light=15)]
    core[0]["shade"] = False
    corona = [box((4, 3, 4), (12, 11, 12), "corona", light=15)]
    corona[0]["shade"] = False
    tall("artificial_star", textures, lower, upper, lower_on=lower_on, item_extra=shifted(core, 16))
    write("artificial_star_core", model(textures, core))
    write("artificial_star_corona", model(textures, corona))


def main():
    artificial_star()


if __name__ == "__main__":
    main()
