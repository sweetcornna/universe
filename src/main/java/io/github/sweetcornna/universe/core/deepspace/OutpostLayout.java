package io.github.sweetcornna.universe.core.deepspace;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 废弃前哨站（ADR-0034 §四）：太空里一座小站，照现实里的空间站舱段搭（ADR-0025）。核心舱是一节横放的圆柱，
 * 侧面接一个气闸，后面一条方形走廊通到仓库；两侧伸出太阳能板的桁架，顶上一根天线。没电，灯是黑的。
 *
 * <p>核心舱里有数据终端和两三台能拆的设备，仓库里有三到五个补给箱。核心舱的中心在原点，地板在 y = −3。
 */
public final class OutpostLayout {
    /** 核心舱的半径和半长。 */
    static final int CORE_RADIUS = 5;
    static final int CORE_HALF = 6;

    private OutpostLayout() {
    }

    public static Layout generate(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "outpost"));
        Layout.Builder b = new Layout.Builder();
        core(b, rng);
        airlock(b, rng.chance(0.5) ? 1 : -1);
        int corridor = rng.range(6, 10);
        int storageX = corridor(b, -CORE_HALF - 1, corridor);
        storage(b, storageX, rng);
        solar(b, rng);
        antenna(b, rng);
        // 废弃多年：掉了几块蒙皮
        for (int i = 0; i < 4; i++) {
            int x = rng.range(-CORE_HALF + 1, CORE_HALF - 1);
            int side = rng.chance(0.5) ? 1 : -1;
            b.clear(x, rng.range(-2, 3), side * CORE_RADIUS);
        }
        return b.build();
    }

    /** 核心舱：沿 x 的圆柱，两头封口，中间一圈舷窗；里面一台数据终端和两三台设备。 */
    private static void core(Layout.Builder b, Rng rng) {
        int r = CORE_RADIUS;
        for (int x = -CORE_HALF; x <= CORE_HALF; x++) {
            boolean cap = Math.abs(x) == CORE_HALF;
            for (int y = -r - 1; y <= r + 1; y++) {
                for (int z = -r - 1; z <= r + 1; z++) {
                    double d = Math.sqrt(y * y + z * z);
                    if (d > r + 0.5) {
                        continue;
                    }
                    if (!cap && d <= r - 0.7) {
                        b.clear(x, y, z);
                        if (y == -r + 2) {
                            b.set(x, y, z, Part.DECK);
                        }
                        continue;
                    }
                    Part part = Part.PLATING;
                    if (!cap && Math.abs(x) <= 2 && (y == 0 || y == 1)) {
                        part = Part.WINDOW;
                    } else if (!cap && y == r && z == 0 && x % 3 == 0) {
                        part = Part.LIGHT;
                    }
                    b.set(x, y, z, part);
                }
            }
        }
        int floor = -r + 3;
        b.set(CORE_HALF - 1, floor, 0, Part.TERMINAL);
        int devices = rng.range(2, 3);
        for (int i = 0; i < devices; i++) {
            int x = -CORE_HALF + 2 + i * 3;
            b.set(x, floor, (i % 2 == 0 ? 1 : -1) * (r - 2), Part.DEVICE);
        }
    }

    /** 气闸：核心舱侧面的一个小方舱，外侧开一道两格高的门，里面通进核心舱。 */
    private static void airlock(Layout.Builder b, int side) {
        int r = CORE_RADIUS;
        for (int x = -2; x <= 2; x++) {
            for (int y = -r + 2; y <= 1; y++) {
                for (int dz = r; dz <= r + 4; dz++) {
                    boolean wall = Math.abs(x) == 2 || y == -r + 2 || y == 1 || dz == r + 4;
                    int z = side * dz;
                    if (wall) {
                        b.set(x, y, z, dz == r + 4 && Math.abs(x) <= 1 && y == 0 ? Part.WINDOW : Part.PLATING);
                    } else {
                        b.clear(x, y, z);
                    }
                }
            }
        }
        // 外门和里面的过道
        for (int y = -r + 3; y <= -r + 4; y++) {
            b.clear(0, y, side * (r + 4));
            b.clear(0, y, side * r);
            b.clear(0, y, side * (r - 1));
        }
        b.set(0, 0, side * (r + 2), Part.LIGHT);
    }

    /** 方形走廊（外面 5×5，里面 3×3），隔几格开一扇窗；返回走廊尽头的 x。 */
    private static int corridor(Layout.Builder b, int x0, int length) {
        int end = x0 - length + 1;
        for (int x = x0; x >= end; x--) {
            for (int y = -3; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(z) == 2 || y == -3 || y == 1;
                    if (!wall) {
                        b.clear(x, y, z);
                        continue;
                    }
                    Part part = Part.PLATING;
                    if (Math.abs(z) == 2 && y == -1 && (x0 - x) % 3 == 1) {
                        part = Part.WINDOW;
                    } else if (y == -3 && Math.abs(z) < 2) {
                        part = Part.DECK;
                    } else if (y == 1 && z == 0 && (x0 - x) % 4 == 2) {
                        part = Part.LIGHT;
                    }
                    b.set(x, y, z, part);
                }
            }
        }
        // 走廊通进核心舱
        for (int y = -2; y <= 0; y++) {
            for (int z = -1; z <= 1; z++) {
                b.clear(-CORE_HALF, y, z);
            }
        }
        return end - 1;
    }

    /** 仓库：走廊尽头的一个方舱，地上靠墙放三到五个补给箱和一台设备。 */
    private static void storage(Layout.Builder b, int x1, Rng rng) {
        int x0 = x1 - 7;
        for (int x = x0; x <= x1; x++) {
            for (int y = -4; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    boolean wall = x == x0 || x == x1 || y == -4 || y == 3 || Math.abs(z) == 4;
                    if (!wall) {
                        b.clear(x, y, z);
                        continue;
                    }
                    Part part = y == -4 ? Part.DECK : Part.PLATING;
                    if (y == 3 && z == 0 && (x - x0) % 3 == 1) {
                        part = Part.LIGHT;
                    }
                    b.set(x, y, z, part);
                }
            }
        }
        // 门洞对着走廊
        for (int y = -2; y <= 0; y++) {
            for (int z = -1; z <= 1; z++) {
                b.clear(x1, y, z);
            }
        }
        int crates = rng.range(3, 5);
        for (int i = 0; i < crates; i++) {
            int x = x0 + 1 + i % 3 * 2;
            int z = i < 3 ? 3 : -3;
            b.set(x, -3, z, Part.CRATE);
        }
        b.set(x0 + 1, -3, 0, Part.DEVICE);
    }

    /** 太阳能板：核心舱两侧各伸出一根桁架，桁架两边是板子；一边的板子缺了一截。 */
    private static void solar(Layout.Builder b, Rng rng) {
        int length = rng.range(10, 14);
        int broken = rng.chance(0.5) ? rng.range(4, length) : length + 1;
        for (int side = -1; side <= 1; side += 2) {
            for (int dz = CORE_RADIUS + 1; dz <= CORE_RADIUS + length; dz++) {
                int z = side * dz;
                b.set(0, 0, z, Part.FRAME);
                if (side > 0 && dz - CORE_RADIUS > broken) {
                    continue;
                }
                for (int x = -3; x <= 3; x++) {
                    if (x != 0) {
                        b.set(x, 0, z, (dz - CORE_RADIUS) % 5 == 0 ? Part.FRAME : Part.SOLAR);
                    }
                }
            }
        }
    }

    /** 天线：核心舱顶上的桅杆，杆头一面碟子。 */
    private static void antenna(Layout.Builder b, Rng rng) {
        int height = rng.range(4, 7);
        int top = CORE_RADIUS + height;
        for (int y = CORE_RADIUS + 1; y <= top; y++) {
            b.set(0, y, 0, Part.FRAME);
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                b.set(x, top + 1, z, Part.ANTENNA);
            }
        }
    }
}
