"""生成 M12（ADR-0023）的模型：物流配送器（停机坪和指示灯工作时亮），配送机器人和建造无人机的立体模型。

用法：python tools/m12_models.py
配送器上下左右对称，不按朝向转。两种飞行器的模型给 FlyersClient 画在世界里用，界面里用平面图标（数据生成里接起来）。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from factory_models import box, model, write, CRAFT_DISPLAY  # noqa: E402


def tex(name):
    return "universe:block/" + name


def distributor():
    """底座、立柱、一整格宽的停机坪，坪中间一盏信标；工作时立柱的灯带和信标亮。"""
    for suffix, lit in (("", False), ("_on", True)):
        textures = {
            "particle": tex("logistics_distributor_pad"),
            "base": tex("logistics_distributor_base"),
            "top": tex("logistics_distributor_top"),
            "column": tex("logistics_distributor_column" + suffix),
            "pad": tex("logistics_distributor_pad"),
            "pad_side": tex("logistics_distributor_pad_side"),
            "light": tex("logistics_distributor_light" + suffix),
        }
        elements = [
            box((1, 0, 1), (15, 4, 15), {"north": "base", "south": "base", "west": "base", "east": "base", "up": "top", "down": "top"},
                cull={"down": "down"}),
            box((4, 4, 4), (12, 10, 12), {"north": "column", "south": "column", "west": "column", "east": "column", "up": None, "down": None}),
            box((0, 10, 0), (16, 12, 16), {"north": "pad_side", "south": "pad_side", "west": "pad_side", "east": "pad_side", "up": "pad",
                                           "down": "top"},
                cull={"north": "north", "south": "south", "west": "west", "east": "east"}),
            box((7, 12, 7), (9, 13.5, 9), "light", light=15 if lit else 0),
        ]
        # 坪的四角各一盏小灯
        for (x, z) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            elements.append(box((x, 12, z), (x + 1, 12.5, z + 1), "light", faces=("north", "south", "west", "east", "up"),
                                light=12 if lit else 0))
        write("logistics_distributor" + suffix, model(textures, elements))


def logistics_bot():
    """配送机器人：白色的扁圆机身，腰上一圈青色灯带，两侧两只推进舱，腹下一对夹爪。机头朝 +Z。"""
    textures = {
        "particle": tex("logistics_bot_body"),
        "body": tex("logistics_bot_body"),
        "band": tex("logistics_station_light_on"),
        "pod": tex("logistics_station_trim"),
        "clamp": tex("logistics_station_trim"),
    }
    elements = [
        box((5, 6.5, 5), (11, 9.5, 11), "body"),
        box((6, 9.5, 6), (10, 10.25, 10), "body"),
        box((4.75, 7.5, 4.75), (11.25, 8.25, 11.25), "band", light=15),
        box((2.5, 7, 6.5), (4.75, 9, 9.5), "pod"),
        box((11.25, 7, 6.5), (13.5, 9, 9.5), "pod"),
        box((2.75, 6.5, 7), (4.5, 7, 9), "band", light=15),
        box((11.5, 6.5, 7), (13.25, 7, 9), "band", light=15),
        box((6.5, 5.5, 7.5), (7.25, 6.5, 8.5), "clamp"),
        box((8.75, 5.5, 7.5), (9.5, 6.5, 8.5), "clamp"),
        box((7, 8.25, 11), (9, 9, 11.25), "band", light=15),
    ]
    write("logistics_bot_model", model(textures, elements, parent="block/block", display=CRAFT_DISPLAY))


def construction_drone():
    """建造无人机：机甲放出去的小四旋翼，黄白相间的机身，腹下一盏焊接用的橙灯。机头朝 +Z。"""
    textures = {
        "particle": tex("construction_drone_body"),
        "body": tex("construction_drone_body"),
        "arm": tex("logistics_station_trim"),
        "rotor": tex("logistics_drone_rotor"),
        "light": tex("construction_drone_light"),
    }
    elements = [
        box((5.5, 7, 5.5), (10.5, 9, 10.5), "body"),
        box((6.5, 9, 6.5), (9.5, 9.5, 9.5), "body"),
    ]
    # X 形的机臂：两根长臂绕竖轴各转 45 度，末端各一个旋翼
    for angle in (45, -45):
        elements.append(box((1.5, 7.75, 7.5), (14.5, 8.5, 8.5), "arm", rotation={"origin": [8, 8, 8], "axis": "y", "angle": angle}))
    for (x, z) in ((3.4, 3.4), (12.6, 3.4), (3.4, 12.6), (12.6, 12.6)):
        elements.append(box((x - 0.5, 8.5, z - 0.5), (x + 0.5, 9.25, z + 0.5), "arm"))
        elements.append(box((x - 2, 9.25, z - 2), (x + 2, 9.35, z + 2), {"up": "rotor", "down": "rotor"}, faces=("up", "down"),
                            uvs={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}))
    elements.append(box((7, 6.25, 7), (9, 7, 9), "light", light=15))
    elements.append(box((7, 7.5, 10.5), (9, 8.5, 10.75), "light", light=15))
    write("construction_drone_model", model(textures, elements, parent="block/block", display=CRAFT_DISPLAY))


def main():
    distributor()
    logistics_bot()
    construction_drone()


if __name__ == "__main__":
    main()
