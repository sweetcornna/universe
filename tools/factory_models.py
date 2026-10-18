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


# ---- 原油萃取站

def oil_extractor():
    textures = {
        "particle": "universe:block/oil_extractor_side",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "side": "universe:block/oil_extractor_side",
        "front": "universe:block/oil_extractor_front",
        "front_on": "universe:block/oil_extractor_front_on",
        "beam": "universe:block/oil_extractor_beam",
        "pipe": "universe:block/oil_extractor_pipe",
    }
    slab = {"north": "casing", "south": "casing", "west": "casing", "east": "casing", "up": "plate", "down": "plate"}
    housing = {"north": "front", "south": "side", "west": "side", "east": "side", "up": "plate", "down": None}
    pipe = {"north": "pipe", "south": "pipe", "west": "pipe", "east": "pipe", "up": None, "down": None}
    lower = [
        box((0, 0, 0), (16, 2, 16), slab, cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        box((2, 2, 1), (14, 12, 13), housing),
        # 井口：一根粗管子往上接井架
        box((6, 12, 6), (10, 16, 10), pipe),
        # 背后的储油罐
        box((4, 2, 13), (12, 8, 15), {"north": None, "south": "side", "west": "side", "east": "side", "up": "plate", "down": None}),
    ]
    # 工作时压力表和指示灯发光
    lamp = box((4, 2, 0.95), (12, 12, 0.95), {"north": "front_on"}, faces=("north",), light=10, uvs={"north": [4, 4, 12, 14]})
    lower_on = lower + [lamp]

    upper = []
    # 井架：四根角柱、四面桁架、顶上的天车
    for x0, z0 in ((2, 2), (12.5, 2), (2, 12.5), (12.5, 12.5)):
        upper.append(box((x0, 0, z0), (x0 + 1.5, 13, z0 + 1.5), "beam", uvs={f: [0, 3, 2, 16] for f in FACES}))
    # 四面是斜撑交叉的镂空桁架
    lattice = {f: [0, 0, 16, 16] for f in FACES}
    upper.append(box((2, 0, 2.75), (14, 13, 2.75), "beam", faces=("north", "south"), uvs=lattice))
    upper.append(box((2, 0, 13.25), (14, 13, 13.25), "beam", faces=("north", "south"), uvs=lattice))
    upper.append(box((2.75, 0, 2), (2.75, 13, 14), "beam", faces=("west", "east"), uvs=lattice))
    upper.append(box((13.25, 0, 2), (13.25, 13, 14), "beam", faces=("west", "east"), uvs=lattice))
    upper.append(box((7, 0, 7), (9, 13, 9), pipe))
    upper.append(box((3, 13, 3), (13, 15, 13), slab))
    upper.append(box((6, 15, 7), (10, 16, 9), "casing", cull={"up": "up"}))
    tall("oil_extractor", textures, lower, upper, lower_on=lower_on)


# ---- 原油精炼厂

def oil_refinery():
    textures = {
        "particle": "universe:block/oil_refinery_side",
        "casing": "universe:block/machine_casing",
        "plate": "universe:block/machine_casing_top",
        "side": "universe:block/oil_refinery_side",
        "top": "universe:block/oil_refinery_top",
        "front": "universe:block/oil_refinery_front",
        "front_on": "universe:block/oil_refinery_front_on",
        "column": "universe:block/oil_refinery_column",
        "pipe": "universe:block/oil_extractor_pipe",
    }
    body = {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}
    column = {"north": "column", "south": "column", "west": "column", "east": "column", "up": "plate", "down": None}
    pipe = {"north": "pipe", "south": "pipe", "west": "pipe", "east": "pipe", "up": "pipe", "down": "pipe"}
    lower = [
        box((1, 0, 1), (15, 13, 15), body, cull={"down": "down"}),
        box((0, 0, 0), (16, 2, 16), "casing", cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        # 两座分馏塔的塔基
        box((2, 13, 9), (7, 16, 14), {"north": "column", "south": "column", "west": "column", "east": "column", "up": None, "down": None}),
        box((9, 13, 9), (14, 16, 14), {"north": "column", "south": "column", "west": "column", "east": "column", "up": None, "down": None}),
        # 托住上面横放储罐的两个鞍座
        box((4, 13, 2.5), (6, 16, 5.5), "casing"),
        box((10, 13, 2.5), (12, 16, 5.5), "casing"),
    ]
    window = box((4, 5, 0.95), (11, 13, 0.95), {"north": "front_on"}, faces=("north",), light=12, uvs={"north": [4, 3, 11, 11]})
    lower_on = lower + [window]
    upper = [
        # 高塔、矮塔和塔顶的盖
        box((2, 0, 9), (7, 15, 14), column),
        box((2.5, 15, 9.5), (6.5, 16, 13.5), "casing", cull={"up": "up"}),
        box((9, 0, 9), (14, 10, 14), column),
        box((9.5, 10, 9.5), (13.5, 11, 13.5), "casing"),
        # 两塔之间的连管
        box((7, 7, 11), (9, 8, 12), pipe),
        # 前面横放的储罐
        box((3, 0, 2), (13, 4, 6), {"north": "column", "south": "column", "west": "plate", "east": "plate", "up": "column", "down": None}),
    ]
    tall("oil_refinery", textures, lower, upper, lower_on=lower_on)


# ---- 传送带

BELT_TEXTURES = {
    "particle": "universe:block/conveyor_belt_rail",
    "surface": "universe:block/conveyor_belt_surface",
    "curve_left": "universe:block/conveyor_belt_curve_left",
    "curve_right": "universe:block/conveyor_belt_curve_right",
    "frame": "universe:block/conveyor_belt_frame",
    "rail": "universe:block/conveyor_belt_rail",
}


def belt_rail(f, t, cull=None):
    """侧栏：顶面取贴图里亮的那一列。"""
    return box(f, t, "rail", cull=cull, uvs={"up": [6, 0, 7, 16], "down": [6, 0, 7, 16]})


def conveyor_belt():
    frame = {"north": "frame", "south": "frame", "west": "frame", "east": "frame", "up": None, "down": "frame"}
    surface = {"north": "frame", "south": "frame", "west": None, "east": None, "up": "surface", "down": None}
    # 平的：底架、带面（顶面 3 像素高）、两边的侧栏
    flat = [
        box((1, 0, 0), (15, 2, 16), frame, cull={"down": "down"}),
        box((1, 2, 0), (15, 3, 16), surface),
        belt_rail((0, 0, 0), (1, 4, 16), cull={"down": "down", "west": "west"}),
        belt_rail((15, 0, 0), (16, 4, 16), cull={"down": "down", "east": "east"}),
    ]
    write("conveyor_belt", model(BELT_TEXTURES, flat))

    # 弯道：带面铺满一格，外侧两边有侧栏，内侧拐角一根立柱
    full_frame = box((0, 0, 0), (16, 2, 16), frame, cull={"down": "down"})
    left = [
        full_frame,
        box((0, 2, 0), (16, 3, 16), {"up": "curve_left"}, faces=("up",)),
        belt_rail((15, 0, 0), (16, 4, 16), cull={"down": "down", "east": "east"}),
        belt_rail((0, 0, 15), (15, 4, 16), cull={"down": "down", "south": "south"}),
        belt_rail((0, 0, 0), (1, 4, 1), cull={"down": "down"}),
    ]
    write("conveyor_belt_curve_left", model(BELT_TEXTURES, left))
    right = [
        full_frame,
        box((0, 2, 0), (16, 3, 16), {"up": "curve_right"}, faces=("up",)),
        belt_rail((0, 0, 0), (1, 4, 16), cull={"down": "down", "west": "west"}),
        belt_rail((1, 0, 15), (16, 4, 16), cull={"down": "down", "south": "south"}),
        belt_rail((15, 0, 0), (16, 4, 1), cull={"down": "down"}),
    ]
    write("conveyor_belt_curve_right", model(BELT_TEXTURES, right))

    # 坡道：整组元素绕 x 轴转 45° 并拉长，带面比对角线高出 3 像素，和平的传送带接得上
    for name, angle in (("conveyor_belt_up", 45), ("conveyor_belt_down", -45)):
        rotation = {"origin": [8, 8, 8], "axis": "x", "angle": angle, "rescale": True}
        sloped = [
            box((1, 6.5, 0), (15, 8.5, 16), frame, rotation=dict(rotation)),
            box((1, 8.5, 0), (15, 9.5, 16), surface, rotation=dict(rotation)),
            box((0, 6.5, 0), (1, 10.5, 16), "rail", rotation=dict(rotation), uvs={"up": [6, 0, 7, 16], "down": [6, 0, 7, 16]}),
            box((15, 6.5, 0), (16, 10.5, 16), "rail", rotation=dict(rotation), uvs={"up": [6, 0, 7, 16], "down": [6, 0, 7, 16]}),
        ]
        write(name, model(BELT_TEXTURES, sloped))


SORTER_TEXTURES = {
    "particle": "universe:block/sorter_side",
    "top": "universe:block/sorter_top",
    "side": "universe:block/sorter_side",
    "pillar": "universe:block/sorter_pillar",
    "arm": "universe:block/sorter_arm",
}


def turned(elements):
    """绕方块中心转 180°（前后、左右对调）；分拣器的手臂左右对称，物品模型里把它摆成朝后。"""
    result = []
    swap = {"north": "south", "south": "north", "west": "east", "east": "west"}
    for e in elements:
        e = copy.deepcopy(e)
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        e["from"] = [16 - x1, y0, 16 - z1]
        e["to"] = [16 - x0, y1, 16 - z0]
        e["faces"] = {swap.get(face, face): spec for face, spec in e["faces"].items()}
        result.append(e)
    return result


def sorter():
    base = [
        box((2, 0, 2), (14, 4, 14), {"north": "side", "south": "side", "west": "side", "east": "side", "up": "top", "down": "side"},
            cull={"down": "down"}),
        box((5, 4, 5), (11, 10, 11), "pillar"),
    ]
    write("sorter", model(SORTER_TEXTURES, base))
    # 手臂（朝前，也就是朝北）：轮毂、配重、横梁、爪子；方块实体渲染器绕 (8, 8) 转它
    arm = [
        box((6, 10, 6), (10, 13, 10), "arm"),
        box((6.5, 10.5, 10), (9.5, 12.5, 12.5), "arm"),
        box((7, 11, -3), (9, 12.5, 6), "arm"),
        box((5, 9.5, -5.5), (11, 11.5, -2.5), "arm"),
        box((5, 6.5, -5), (5.75, 9.5, -3), "arm"),
        box((10.25, 6.5, -5), (11, 9.5, -3), "arm"),
    ]
    write("sorter_arm", model(SORTER_TEXTURES, arm))
    write("sorter_item", model(SORTER_TEXTURES, base + turned(arm)))


def matrix_lab():
    textures = {
        "particle": "universe:block/matrix_lab_side",
        "side": "universe:block/matrix_lab_side",
        "front": "universe:block/matrix_lab_front",
        "top": "universe:block/matrix_lab_top",
        "frame": "universe:block/matrix_lab_frame",
        "glass": "universe:block/matrix_lab_glass",
        "core": "universe:block/matrix_lab_core",
    }
    walls = {"north": "glass", "south": "glass", "west": "glass", "east": "glass", "up": None, "down": None}
    block = [
        box((0, 0, 0), (16, 5, 16), {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "side"},
            cull={"down": "down", "north": "north", "south": "south", "west": "west", "east": "east"}),
        # 玻璃罩、四根立柱、顶盖，罩里放核心的小台子
        box((1.5, 5, 1.5), (14.5, 14.5, 14.5), walls),
        box((1, 5, 1), (2.5, 15, 2.5), "frame"),
        box((13.5, 5, 1), (15, 15, 2.5), "frame"),
        box((1, 5, 13.5), (2.5, 15, 15), "frame"),
        box((13.5, 5, 13.5), (15, 15, 15), "frame"),
        box((1, 14.5, 1), (15, 16, 15), "frame"),
        box((6, 5, 6), (10, 6, 10), "frame"),
    ]
    write("matrix_lab", model(textures, block))
    # 核心由方块实体渲染器转起来，中心在 (8, 10, 8)
    core = [box((5.5, 7.5, 5.5), (10.5, 12.5, 10.5), "core", light=15)]
    # 转起来时各个面一样亮，不按朝向变暗
    core[0]["shade"] = False
    write("matrix_lab_core", model(textures, core))
    write("matrix_lab_item", model(textures, block + core))


def main():
    tesla_tower()
    wind_turbine()
    thermal_plant()
    oil_extractor()
    oil_refinery()
    conveyor_belt()
    sorter()
    matrix_lab()


if __name__ == "__main__":
    main()
