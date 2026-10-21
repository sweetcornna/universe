"""生成 M11（ADR-0022）新方块的模型：串在传送带中间的方块（喷涂机、集装机、流速监测器）、集装分拣器、无线输电塔、
卫星配电站、自我演化研究站、分流器的优先出口箭头。

用法：python tools/m11_models.py
一格的设备（地热发电站、能量枢纽、三台高级设备）和地基的模型由数据生成出，不在这里。模型都朝北（前面是 z=0），
方块状态按朝向转。
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "models", "block")


def tex(name):
    return "universe:block/" + name


def write(name, model):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")


def face(texture, uv, cull=None):
    out = {"uv": uv, "texture": "#" + texture}
    if cull:
        out["cullface"] = cull
    return out


def box(frm, to, faces, emission=None):
    element = {"from": frm, "to": to, "faces": faces}
    if emission:
        element["light_emission"] = emission
    return element


def uv_side(x0, x1, y0, y1):
    """侧面的 uv：模型的 y 从下往上，贴图的 v 从上往下。"""
    return [x0, 16 - y1, x1, 16 - y0]


def all_faces(texture, frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {
        "north": face(texture, uv_side(16 - x1, 16 - x0, y0, y1)),
        "south": face(texture, uv_side(x0, x1, y0, y1)),
        "west": face(texture, uv_side(z0, z1, y0, y1)),
        "east": face(texture, uv_side(16 - z1, 16 - z0, y0, y1)),
        "up": face(texture, [x0, z0, x1, z1]),
        "down": face(texture, [x0, 16 - z1, x1, 16 - z0]),
    }


def belt_base():
    """底座：九格高，顶面一段传送带，前后两面开口。"""
    return box([0, 0, 0], [16, 9, 16], {
        "north": face("port", uv_side(0, 16, 0, 9), "north"),
        "south": face("port", uv_side(0, 16, 0, 9), "south"),
        "west": face("base", uv_side(0, 16, 0, 9), "west"),
        "east": face("base", uv_side(0, 16, 0, 9), "east"),
        "up": face("belt", [0, 0, 16, 16]),
        "down": face("base", [0, 0, 16, 16], "down"),
    })


BELT_TEXTURES = {"port": tex("belt_pass_port"), "base": tex("belt_pass_side"), "belt": tex("belt_pass_top")}


def hood(frm, to, side, top):
    """装在底座上的罩子：侧面用 {@code side} 中间的一块，顶面用 {@code top}。"""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    # 侧面取贴图中间那一块，和面一样大，像素不拉伸
    height = y1 - y0
    v0 = (16 - height) / 2
    return box(frm, to, {
        "north": face(side, [16 - x1, v0, 16 - x0, v0 + height]),
        "south": face(side, [x0, v0, x1, v0 + height]),
        "west": face(side, [z0, v0, z1, v0 + height]),
        "east": face(side, [16 - z1, v0, 16 - z0, v0 + height]),
        "up": face(top, [x0, z0, x1, z1]),
    })


def belt_pass():
    textures = dict(BELT_TEXTURES, particle=tex("belt_pass_side"), side=tex("spray_coater_side"), top=tex("spray_coater_top"))
    write("spray_coater", {"parent": "block/block", "textures": textures,
                           "elements": [belt_base(), hood([2, 9, 3], [14, 15, 13], "side", "top")]})

    textures = dict(BELT_TEXTURES, particle=tex("belt_pass_side"), side=tex("automatic_piler_side"), top=tex("automatic_piler_top"))
    write("automatic_piler", {"parent": "block/block", "textures": textures,
                              "elements": [belt_base(), hood([1, 9, 1], [15, 14, 15], "side", "top")]})

    for name, lamp, emission in (("traffic_monitor", "traffic_monitor_lamp", 6), ("traffic_monitor_alarm", "traffic_monitor_lamp_alarm", 15)):
        textures = dict(BELT_TEXTURES, particle=tex("traffic_monitor_frame"), frame=tex("traffic_monitor_frame"),
                        screen=tex("traffic_monitor_screen"), lamp=tex(lamp))
        screen = box([3, 9.5, 7.5], [13, 14, 8.5], {
            "north": face("screen", [3, 3, 13, 13]),
            "south": face("screen", [3, 3, 13, 13]),
            "west": face("frame", [7, 2, 8, 6]),
            "east": face("frame", [7, 2, 8, 6]),
            "down": face("frame", [3, 7, 13, 8]),
        })
        write(name, {"parent": "block/block", "textures": textures, "elements": [
            belt_base(),
            box([0, 9, 7], [2, 16, 9], all_faces("frame", [0, 9, 7], [2, 16, 9])),
            box([14, 9, 7], [16, 16, 9], all_faces("frame", [14, 9, 7], [16, 16, 9])),
            box([2, 14, 7], [14, 16, 9], all_faces("frame", [2, 14, 7], [14, 16, 9])),
            screen,
            # 灯在方块上面一截，uv 直接取贴图中间
            box([6.5, 16, 6.5], [9.5, 18, 9.5], {side: face("lamp", [6, 6, 10, 10])
                                                 for side in ("north", "south", "west", "east", "up")}, emission),
        ]})


def pile_sorter():
    textures = {"top": tex("sorter_pile_top"), "arm": tex("sorter_arm_pile")}
    write("pile_sorter", {"parent": "universe:block/sorter", "textures": textures})
    write("pile_sorter_item", {"parent": "universe:block/sorter_item", "textures": textures})
    write("sorter_arm_pile", {"parent": "universe:block/sorter_arm", "textures": textures})


def towers():
    for name, textures in (
            ("wireless_power_tower", {"coil": tex("wireless_power_tower_coil"), "orb": tex("wireless_power_tower_top_off"),
                                      "orb_on": tex("wireless_power_tower_top")}),
            ("satellite_substation", {"particle": tex("satellite_substation_pole"), "pole": tex("satellite_substation_pole"),
                                      "coil": tex("satellite_substation_coil"), "orb": tex("satellite_substation_top_off"),
                                      "orb_on": tex("satellite_substation_top")})):
        for part in ("lower", "upper", "upper_on", "item"):
            write(name + "_" + part, {"parent": "universe:block/tesla_tower_" + part, "textures": textures})


def self_evolution_lab():
    textures = {"frame": tex("self_evolution_lab_frame"), "core": tex("self_evolution_lab_core")}
    for part in ("", "_item", "_core"):
        write("self_evolution_lab" + part, {"parent": "universe:block/matrix_lab" + part, "textures": textures})


def splitter_priority():
    write("splitter_priority", {"parent": "block/block", "textures": {"particle": tex("splitter_priority"), "mark": tex("splitter_priority")},
                                "elements": [box([5, 6, 1], [11, 7, 4], all_faces("mark", [5, 6, 1], [11, 7, 4]), 10)]})


def main():
    belt_pass()
    pile_sorter()
    towers()
    self_evolution_lab()
    splitter_priority()


if __name__ == "__main__":
    main()
