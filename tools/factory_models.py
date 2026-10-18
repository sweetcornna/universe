"""生成工业设备的方块模型（JSON 元素模型）。

用法：python tools/factory_models.py [模型目录]
默认输出到 src/main/resources/assets/universe/models/block。

两格高的设备分上下两个模型（_lower、_upper），另外出一个把两半叠在一起的 _item 模型给物品栏用；
工作时发光的部分另出 _on 变体。方块状态由数据生成（UniverseModelProvider）按朝向和状态挑模型。

所有面都写明 UV（按元素在本格里的位置投影），这样叠成两格高的物品模型时上半截的贴图不会错位。
"""
import copy
import json
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "universe", "models", "block")

FACES = ("north", "south", "west", "east", "up", "down")


def uv_for(face, f, t):
    """原版自动 UV 的算法，按元素在本格里的位置投影。"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == "north":
        return [16 - x1, 16 - y1, 16 - x0, 16 - y0]
    if face == "south":
        return [x0, 16 - y1, x1, 16 - y0]
    if face == "west":
        return [z0, 16 - y1, z1, 16 - y0]
    if face == "east":
        return [16 - z1, 16 - y1, 16 - z0, 16 - y0]
    if face == "up":
        return [x0, z0, x1, z1]
    return [x0, 16 - z1, x1, 16 - z0]


def clamp_uv(uv):
    return [max(0, min(16, round(v, 3))) for v in uv]


def box(f, t, tex, faces=FACES, cull=None, light=0, rotation=None, uvs=None):
    """一个长方体元素。tex 可以是一个贴图变量，也可以是 {面: 变量}；cull 是 {面: 剔除方向}。"""
    element = {"from": list(f), "to": list(t), "faces": {}}
    for face in faces:
        texture = tex[face] if isinstance(tex, dict) else tex
        if texture is None:
            continue
        uv = uvs[face] if uvs and face in uvs else clamp_uv(uv_for(face, f, t))
        spec = {"uv": uv, "texture": "#" + texture}
        if cull and face in cull:
            spec["cullface"] = cull[face]
        element["faces"][face] = spec
    if light:
        element["light_emission"] = light
    if rotation:
        element["rotation"] = rotation
    return element


def model(textures, elements, parent="block/block", display=None):
    m = {"parent": parent, "textures": textures, "elements": elements}
    if display:
        m["display"] = display
    return m


def shifted(elements, dy):
    """把上半截的元素整体往上挪一格，拼进物品模型。UV 已经写死，不受影响。"""
    result = []
    for e in elements:
        e = copy.deepcopy(e)
        e["from"][1] += dy
        e["to"][1] += dy
        if "rotation" in e:
            e["rotation"]["origin"][1] += dy
        result.append(e)
    return result


TALL_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.36, 0.36, 0.36]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.18, 0.18, 0.18]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.36, 0.36, 0.36]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.24, 0.24, 0.24]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.24, 0.24, 0.24]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.24, 0.24, 0.24]},
}


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tall(name, textures, lower, upper, upper_on=None, lower_on=None, item_extra=()):
    write(name + "_lower", model(textures, lower))
    write(name + "_upper", model(textures, upper))
    if upper_on is not None:
        write(name + "_upper_on", model(textures, upper_on))
    if lower_on is not None:
        write(name + "_lower_on", model(textures, lower_on))
    item = lower + shifted(upper, 16) + list(item_extra)
    write(name + "_item", model(textures, item, display=TALL_DISPLAY))


# ---- 电力感应塔

def tesla_tower():
    textures = {
        "particle": "universe:block/tesla_tower_pole",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "pole": "universe:block/tesla_tower_pole",
        "coil": "universe:block/tesla_tower_coil",
        "orb": "universe:block/tesla_tower_top_off",
        "orb_on": "universe:block/tesla_tower_top",
    }
    plate = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
    lower = [
        box((2, 0, 2), (14, 2, 14), plate, cull={"down": "down"}),
        # 四个支脚
        box((1, 0, 1), (4, 3, 4), "casing", cull={"down": "down"}),
        box((12, 0, 1), (15, 3, 4), "casing", cull={"down": "down"}),
        box((1, 0, 12), (4, 3, 15), "casing", cull={"down": "down"}),
        box((12, 0, 12), (15, 3, 15), "casing", cull={"down": "down"}),
        box((5, 2, 5), (11, 16, 11), {"north": "pole", "south": "pole", "west": "pole", "east": "pole", "up": None, "down": None}),
    ]

    def upper(orb, light):
        flange = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
        return [
            box((6, 0, 6), (10, 6, 10), {"north": "pole", "south": "pole", "west": "pole", "east": "pole", "up": None, "down": None}),
            box((3, 6, 3), (13, 7, 13), flange),
            box((4, 7, 4), (12, 10, 12), {"north": "coil", "south": "coil", "west": "coil", "east": "coil", "up": None, "down": None}),
            box((3, 10, 3), (13, 11, 13), flange),
            box((7, 11, 7), (9, 12, 9), "pole"),
            box((5, 12, 5), (11, 16, 11), orb, light=light, cull={"up": "up"}),
        ]

    tall("tesla_tower", textures, lower, upper("orb", 0), upper_on=upper("orb_on", 15))


# ---- 风力涡轮机

def wind_turbine():
    textures = {
        "particle": "universe:block/wind_turbine_body",
        "base": "universe:block/wind_turbine_base",
        "body": "universe:block/wind_turbine_body",
        "nacelle": "universe:block/wind_turbine_nacelle",
        "blade": "universe:block/wind_turbine_blade",
    }
    lower = [
        box((3, 0, 3), (13, 3, 13), "base", cull={"down": "down"}),
        box((5, 3, 5), (11, 16, 11), {"north": "body", "south": "body", "west": "body", "east": "body", "up": None, "down": None}),
    ]
    nacelle = {"north": "body", "south": "body", "west": "nacelle", "east": "nacelle", "up": "body", "down": "body"}
    upper = [
        box((5.5, 0, 5.5), (10.5, 8, 10.5), {"north": "body", "south": "body", "west": "body", "east": "body", "up": None, "down": None}),
        box((5, 8, 3), (11, 14, 15), nacelle),
        # 尾部的散热罩
        box((6, 9, 15), (10, 13, 16), "body"),
    ]
    # 风轮单独做成两个模型，由方块实体渲染器转起来；中心在 (8, 8, 8)
    hub = [
        box((6, 6, 6), (10, 10, 9), "body"),
        box((6.5, 6.5, 4.75), (9.5, 9.5, 6), "body"),
        box((7.25, 7.25, 4), (8.75, 8.75, 4.75), "body"),
    ]
    pitch = {"angle": 22.5, "axis": "y", "origin": [8, 18, 8]}
    blade_uv_main = {f: [3, 7, 13, 16] for f in FACES}
    blade_uv_tip = {f: [4, 0, 12, 7] for f in FACES}
    blade = [
        box((7.5, 9, 7.5), (8.5, 11, 8.5), "body"),
        box((6.5, 11, 7.6), (9.5, 22, 8.4), "blade", rotation=pitch, uvs=blade_uv_main),
        box((7, 22, 7.6), (9, 28, 8.4), "blade", rotation=pitch, uvs=blade_uv_tip),
    ]
    write("wind_turbine_hub", model(textures, hub))
    write("wind_turbine_blade", model(textures, blade))
    # 物品模型里放一个静止的风轮：轮毂加左右两片叶片（往上的叶片会超出模型允许的 32 格高），挪到机舱前面
    rotor = []
    for e in hub + [sideways(e, 1) for e in blade] + [sideways(e, -1) for e in blade]:
        e = copy.deepcopy(e)
        e["from"][2] -= 6
        e["to"][2] -= 6
        e["from"][1] += 20
        e["to"][1] += 20
        if "rotation" in e:
            e["rotation"]["origin"][2] -= 6
            e["rotation"]["origin"][1] += 20
        rotor.append(e)
    tall("wind_turbine", textures, lower, upper, item_extra=[e for e in rotor if fits(e)])


def sideways(e, direction):
    """把朝上的叶片绕轮毂中心 (8, 8) 转到朝东（1）或朝西（-1）。"""
    e = copy.deepcopy(e)
    f, t = e["from"], e["to"]
    if direction > 0:
        e["from"] = [f[1], 16 - t[0], f[2]]
        e["to"] = [t[1], 16 - f[0], t[2]]
        faces = {"up": "east", "down": "west", "east": "down", "west": "up"}
    else:
        e["from"] = [16 - t[1], f[0], f[2]]
        e["to"] = [16 - f[1], t[0], t[2]]
        faces = {"up": "west", "down": "east", "east": "up", "west": "down"}
    e["faces"] = {faces.get(k, k): v for k, v in e["faces"].items()}
    for k in ("north", "south"):
        if k in e["faces"]:
            e["faces"][k]["rotation"] = 90
    if "rotation" in e:
        o = e["rotation"]["origin"]
        e["rotation"]["origin"] = [o[1], 16 - o[0], o[2]] if direction > 0 else [16 - o[1], o[0], o[2]]
        e["rotation"]["axis"] = "x"
    return e


def fits(e):
    """元素坐标必须在 -16 到 32 之间。"""
    return all(-16 <= v <= 32 for v in e["from"] + e["to"])


# ---- 火力发电厂

def thermal_plant():
    textures = {
        "particle": "universe:block/thermal_plant_side",
        "side": "universe:block/thermal_plant_side",
        "top": "universe:block/thermal_plant_top",
        "front": "universe:block/thermal_plant_front",
        "front_on": "universe:block/thermal_plant_front_on",
        "chimney": "universe:block/thermal_plant_chimney",
        "turbine": "universe:block/thermal_plant_turbine",
        "casing": "universe:block/machine_casing",
    }
    body = {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}
    lower = [
        box((1, 0, 1), (15, 16, 15), body, cull={"down": "down"}),
        box((0, 0, 0), (16, 2, 16), "casing", cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
    ]
    # 烧着时炉门透光：在正面贴一层自发光的炉门
    door = box((3, 2, 0.95), (13, 10, 0.95), {"north": "front_on"}, faces=("north",), light=14,
               uvs={"north": [3, 6, 13, 14]})
    lower_on = lower + [door]
    upper = [
        box((1, 0, 1), (9, 7, 15), {"north": "turbine", "south": "turbine", "west": "turbine", "east": "turbine",
                                    "up": "top", "down": None}),
        box((2, 7, 3), (8, 8, 13), "casing"),
        box((9, 3, 11), (10, 5, 13), "casing"),
        box((10, 0, 10), (14, 16, 14), {"north": "chimney", "south": "chimney", "west": "chimney", "east": "chimney",
                                        "up": "casing", "down": None}),
    ]
    tall("thermal_plant", textures, lower, upper, lower_on=lower_on)


def main():
    tesla_tower()
    wind_turbine()
    thermal_plant()


if __name__ == "__main__":
    main()
