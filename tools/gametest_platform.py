"""生成游戏内测试（GameTest）用的场地结构：底层一层石台，其余是空气。

用法：python tools/gametest_platform.py [场地] [数据版本]
场地有三种，默认 platform；数据版本默认 5023（26.3）。输出到 src/main/resources/data/universe/structure/gametest/<场地>.nbt。
- platform：7×4×7，平滑石地面，测设备。
- hangar：17×8×17，石头地面（石头不会被组装进飞船，ADR-0024），测整艘设计出来的船。
- portal：13×12×11，石头地面，放得下一整座先驱者传送门（ADR-0024）。
"""
import gzip
import os
import struct
import sys

END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def int_payload(v):
    return struct.pack(">i", v)


def string_payload(s):
    return name(s)


def list_payload(tag_type, payloads):
    return struct.pack(">bi", tag_type if payloads else END, len(payloads)) + b"".join(payloads)


def compound_payload(entries):
    out = b""
    for tag_type, key, payload in entries:
        out += struct.pack(">b", tag_type) + name(key) + payload
    return out + struct.pack(">b", END)


def int_list(values):
    return list_payload(INT, [int_payload(v) for v in values])


SITES = {
    "platform": ((7, 4, 7), "minecraft:smooth_stone"),
    "hangar": ((17, 8, 17), "minecraft:stone"),
    "portal": ((13, 12, 11), "minecraft:stone"),
}
site = sys.argv[1] if len(sys.argv) > 1 else "platform"
(sx, sy, sz), floor = SITES[site]

blocks = []
for x in range(sx):
    for z in range(sz):
        blocks.append(compound_payload([
            (LIST, "pos", int_list([x, 0, z])),
            (INT, "state", int_payload(0)),
        ]))

root = compound_payload([
    (INT, "DataVersion", int_payload((int(sys.argv[2]) if len(sys.argv) > 2 else 5023))),
    (LIST, "size", int_list([sx, sy, sz])),
    (LIST, "palette", list_payload(COMPOUND, [compound_payload([(STRING, "Name", string_payload(floor))])])),
    (LIST, "blocks", list_payload(COMPOUND, blocks)),
    (LIST, "entities", list_payload(COMPOUND, [])),
])
data = struct.pack(">b", COMPOUND) + name("") + root

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "universe", "structure", "gametest", site + ".nbt")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "wb") as f:
    f.write(gzip.compress(data, mtime=0))
print(out, os.path.getsize(out))
