"""生成戴森球设备的方块模型（ADR-0016）。

用法：python tools/dyson_models.py [模型目录]
默认输出到 src/main/resources/assets/universe/models/block。和 factory_models.py 共用写模型的函数。

会动的部分（弹射器的转台和炮管、接收站的碟面、发射井里的火箭、飞出去的太阳帆）各出一个独立模型，
由方块实体渲染器转动或移动；物品模型把它们按静止的姿态拼在一起。
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

TALL_ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
}


def em_rail_ejector():
    """电磁轨道弹射器：方形底座和转台是方块模型；叉架（只转方位）和炮管（再转仰角）各一个模型，
    转轴在 (8, 10, 8)。炮管沿 +Z 伸出去，两条导轨中间一道发光的加速通道，三圈线圈。"""
    textures = {
        "particle": "universe:block/em_rail_ejector_base",
        "base": "universe:block/em_rail_ejector_base",
        "top": "universe:block/em_rail_ejector_top",
        "ring": "universe:block/em_rail_ejector_ring",
        "rail": "universe:block/em_rail_ejector_rail",
        "coil": "universe:block/em_rail_ejector_coil",
        "glow": "universe:block/em_rail_ejector_glow",
    }
    base_faces = {"north": "base", "south": "base", "west": "base", "east": "base", "up": "top", "down": "base"}
    base = [
        box((0, 0, 0), (16, 4, 16), base_faces, cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        box((1, 4, 1), (15, 5, 15), "ring"),
        box((3, 5, 3), (13, 6, 13), "ring"),
        box((5.5, 6, 5.5), (10.5, 7, 10.5), "rail"),
    ]
    yoke = [
        box((4.5, 7, 4.5), (11.5, 8, 11.5), "ring"),
        box((4, 8, 6.5), (5.5, 12, 9.5), "rail"),
        box((10.5, 8, 6.5), (12, 12, 9.5), "rail"),
    ]
    barrel = [
        box((5.5, 8.5, 3), (10.5, 11.5, 8), "coil"),
        box((5.5, 9, 8), (6.5, 11, 24), "rail"),
        box((9.5, 9, 8), (10.5, 11, 24), "rail"),
        box((6.5, 8.5, 6), (9.5, 9.25, 22), "rail"),
        box((6.75, 9.5, 8), (9.25, 10.5, 23.5), "glow", light=12),
    ]
    for z in (10, 15, 20):
        barrel.append(box((5, 8.25, z), (11, 11.75, z + 1.25), "coil"))
    write("em_rail_ejector", model(textures, base))
    write("em_rail_ejector_yoke", model(textures, yoke))
    write("em_rail_ejector_barrel", model(textures, barrel))
    # 物品模型：炮管朝前上方翘起 22.5 度（模型元素只能转 22.5 的倍数）
    tilted = []
    for e in barrel:
        e = dict(e)
        e["rotation"] = {"angle": -22.5, "axis": "x", "origin": [8, 10, 8]}
        tilted.append(e)
    write("em_rail_ejector_item", model(textures, base + yoke + tilted, display=ITEM_DISPLAY))


def solar_sail_flight():
    """飞向轨道的太阳帆：一片发光的薄片，中心在 (8, 8, 8)，渲染器缩小了画。"""
    textures = {"particle": "universe:block/solar_sail_flight", "sail": "universe:block/solar_sail_flight"}
    write("solar_sail_flight", model(textures, [
        box((2, 7.5, 2), (14, 8.5, 14), "sail", light=15, uvs={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}),
    ]))


def vertical_launching_silo():
    """垂直发射井：下半截是实心的井筒，上半截是四面井壁围着的井口，四角的立柱顶上有灯。
    火箭由渲染器画，平时立在井口里，鼻锥露出井壁。"""
    textures = {
        "particle": "universe:block/vertical_launching_silo_side",
        "side": "universe:block/vertical_launching_silo_side",
        "lower": "universe:block/vertical_launching_silo_lower",
        "rim": "universe:block/vertical_launching_silo_rim",
        "pad": "universe:block/vertical_launching_silo_pad",
        "post": "universe:block/em_rail_ejector_rail",
        "light": "universe:block/vertical_launching_silo_light",
        "light_on": "universe:block/vertical_launching_silo_light_on",
    }
    lower = [
        box((0, 0, 0), (16, 16, 16), {"north": "lower", "south": "lower", "west": "lower", "east": "lower", "up": "pad", "down": "side"},
            cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
    ]

    def upper_parts(light_texture, light):
        parts = [
            box((0, 0, 0), (16, 10, 2), "side", cull={"north": "north", "down": "down"}),
            box((0, 0, 14), (16, 10, 16), "side", cull={"south": "south", "down": "down"}),
            box((0, 0, 2), (2, 10, 14), "side", cull={"west": "west", "down": "down"}),
            box((14, 0, 2), (16, 10, 14), "side", cull={"east": "east", "down": "down"}),
            box((0, 10, 0), (16, 11, 3), "rim"),
            box((0, 10, 13), (16, 11, 16), "rim"),
            box((0, 10, 3), (3, 11, 13), "rim"),
            box((13, 10, 3), (16, 11, 13), "rim"),
            box((2, 0, 2), (14, 0.25, 14), "pad"),
        ]
        for (x, z) in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
            parts.append(box((x, 11, z), (x + 2, 14, z + 2), "post"))
            parts.append(box((x + 0.5, 14, z + 0.5), (x + 1.5, 15, z + 1.5), light_texture, light=light))
        return parts

    fm.tall("vertical_launching_silo", textures, lower, upper_parts("light", 0), upper_on=upper_parts("light_on", 14))


def small_carrier_rocket():
    """小型运载火箭：白色箭体、红色的级间段和鼻锥、四片尾翼、底下的喷管。底面在 y=0，中心在 x=z=8，
    高 20 像素（立在发射井里时露出井壁）。"""
    textures = {
        "particle": "universe:block/small_carrier_rocket_body",
        "body": "universe:block/small_carrier_rocket_body",
        "trim": "universe:block/small_carrier_rocket_trim",
        "nozzle": "universe:block/em_rail_ejector_coil",
    }
    elements = [
        box((6.5, 1, 6.5), (9.5, 15, 9.5), "body"),
        box((6.75, 15, 6.75), (9.25, 17, 9.25), "trim"),
        box((7.25, 17, 7.25), (8.75, 19, 8.75), "trim"),
        box((7.75, 19, 7.75), (8.25, 20, 8.25), "trim"),
        box((6.4, 8, 6.4), (9.6, 9, 9.6), "trim"),
        box((7, 0, 7), (9, 1, 9), "nozzle"),
        box((5, 1, 7.75), (6.5, 5, 8.25), "trim"),
        box((9.5, 1, 7.75), (11, 5, 8.25), "trim"),
        box((7.75, 1, 5), (8.25, 5, 6.5), "trim"),
        box((7.75, 1, 9.5), (8.25, 5, 11), "trim"),
    ]
    write("small_carrier_rocket_model", model(textures, elements))
    # 火焰：发光的一束，渲染器拉长了画在喷管下面
    write("rocket_flame", model({"particle": "universe:block/rocket_flame", "flame": "universe:block/rocket_flame"}, [
        box((6.5, -8, 6.5), (9.5, 0, 9.5), "flame", light=15),
        box((7.25, -14, 7.25), (8.75, -8, 8.75), "flame", light=15),
    ]))


def ray_receiver():
    """射线接收站：底座和支柱是方块模型；碟面连同中间的接收针另出一个模型，转轴在 (8, 10.5, 8)，
    渲染器把碟面的法线（+Y）转向恒星。"""
    textures = {
        "particle": "universe:block/ray_receiver_base",
        "base": "universe:block/ray_receiver_base",
        "top": "universe:block/ray_receiver_top",
        "pillar": "universe:block/em_rail_ejector_rail",
        "dish": "universe:block/ray_receiver_dish",
        "back": "universe:block/ray_receiver_back",
        "core": "universe:block/ray_receiver_core",
    }
    base_faces = {"north": "base", "south": "base", "west": "base", "east": "base", "up": "top", "down": "base"}
    base = [
        box((1, 0, 1), (15, 4, 15), base_faces, cull={"down": "down"}),
        box((2.5, 4, 2.5), (13.5, 5, 13.5), "pillar"),
        box((6, 5, 6), (10, 9.5, 10), "pillar"),
    ]
    dish_faces = {"north": "back", "south": "back", "west": "back", "east": "back", "up": "dish", "down": "back"}
    dish = [
        box((7, 9.5, 7), (9, 11, 9), "pillar"),
        box((3, 11, 3), (13, 11.75, 13), dish_faces, uvs={"up": [3, 3, 13, 13], "down": [3, 3, 13, 13]}),
        box((1, 11.5, 3), (3, 12.75, 13), dish_faces),
        box((13, 11.5, 3), (15, 12.75, 13), dish_faces),
        box((3, 11.5, 1), (13, 12.75, 3), dish_faces),
        box((3, 11.5, 13), (13, 12.75, 15), dish_faces),
        box((7.6, 11.75, 7.6), (8.4, 16, 8.4), "pillar"),
        box((7, 16, 7), (9, 17.5, 9), "core", light=15),
    ]
    write("ray_receiver", model(textures, base))
    write("ray_receiver_dish", model(textures, dish))
    write("ray_receiver_item", model(textures, base + dish, display=ITEM_DISPLAY))


def main():
    em_rail_ejector()
    solar_sail_flight()
    vertical_launching_silo()
    small_carrier_rocket()
    ray_receiver()


if __name__ == "__main__":
    main()
