package io.github.sweetcornna.universe.core.deepspace;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 太空残骸（ADR-0034 §四）：一艘 60–120 格长的废船，照现实里的核动力飞船搭（ADR-0025 以现实为底）。
 *
 * <p>船沿 x 轴放，x = 0 是船头：
 * <ul>
 * <li>船头是指挥舱，半球形的鼻子上开着驾驶舱的窗；
 * <li>后面一根 3×3 的桁架主梁贯穿全船，中间走着管线束（导管）；
 * <li>主梁上挂着一到三节居住舱，再往后是货舱段（舱板包着的货柜）、推进剂罐；
 * <li>反应堆前面是两张大散热板（核动力的船要把废热辐射出去），再往后是影子屏蔽盘、包在屏蔽层里的堆芯，最后是喷管。
 * </ul>
 * 船身上有几处破口，蒙皮缺了一些；一半的船从中间断开，后半截错开一点。
 *
 * <p>同一个种子每次搭出来一样（存档里只记种子）。
 */
public final class HulkLayout {
    public static final int MIN_LENGTH = 60;
    public static final int MAX_LENGTH = 120;
    /** 破口不会挖掉堆芯和屏蔽层：拆完一艘残骸要拿到堆芯。 */
    private static final Set<Part> KEEP = EnumSet.of(Part.REACTOR, Part.SHIELDING);

    private HulkLayout() {
    }

    public static Layout generate(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "hulk"));
        return generate(rng, rng.range(MIN_LENGTH, MAX_LENGTH));
    }

    /** 指定长度（游戏内测试用短的）。 */
    public static Layout generate(long seed, int length) {
        return generate(Rng.of(Seeds.derive(seed, "hulk")), length);
    }

    private static Layout generate(Rng rng, int length) {
        Layout.Builder b = new Layout.Builder();
        int bowRadius = length >= 90 ? 5 : 4;
        int bowLength = rng.range(8, 10);
        int modules = length >= 105 ? 3 : length >= 80 ? 2 : 1;
        boolean cargo = length >= 75 || rng.chance(0.5);
        int radiatorLength = Math.clamp((length - 60) / 6 + 10, 10, 18);
        int tail = 3 + 3 + 2 + 5;
        int need = bowLength + modules * 9 + (cargo ? 10 : 0) + 8 + radiatorLength + tail;
        int slack = Math.max(modules + 2, length - need);
        int gapCount = modules + 2;
        int[] gaps = new int[gapCount];
        for (int i = 0; i < gapCount; i++) {
            gaps[i] = slack / gapCount + (i < slack % gapCount ? 1 : 0);
        }

        // 先按段落排好 x，再一段一段搭
        int x = bowLength;
        int[][] moduleSpans = new int[modules][];
        for (int i = 0; i < modules; i++) {
            x += gaps[i];
            moduleSpans[i] = new int[] {x, x + 8};
            x += 9;
        }
        int cargoStart = -1;
        if (cargo) {
            cargoStart = x;
            x += 10;
        }
        x += gaps[modules];
        int tankStart = x;
        x += 8;
        x += gaps[modules + 1];
        int radiatorStart = x;
        x += radiatorLength;
        int shieldX = x + 1;
        int reactorX = shieldX + 3;
        int engineStart = reactorX + 4;
        int end = engineStart + 4;

        truss(b, bowLength - 1, engineStart - 1, rng);
        module(b, 0, bowLength - 1, bowRadius, true, rng);
        for (int[] span : moduleSpans) {
            module(b, span[0], span[1], 3, false, rng);
        }
        if (cargo) {
            cargoPods(b, cargoStart, rng);
        }
        tanks(b, tankStart + 4, rng);
        radiators(b, radiatorStart, radiatorStart + radiatorLength - 1, rng);
        disc(b, shieldX, 3, Part.SHIELDING);
        reactor(b, reactorX);
        engine(b, engineStart, end);
        damage(b, length, moduleSpans, tankStart, rng);
        return b.build();
    }

    // ---- 各段

    /** 主梁：四根纵梁，每 4 格一圈横梁；中间的管线束一段一段的，中间有断开的地方。 */
    private static void truss(Layout.Builder b, int x0, int x1, Rng rng) {
        boolean conduit = true;
        int run = rng.range(4, 8);
        for (int x = x0; x <= x1; x++) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    b.set(x, y, z, Part.FRAME);
                }
            }
            if ((x - x0) % 4 == 0) {
                b.set(x, -1, 0, Part.FRAME);
                b.set(x, 0, -1, Part.FRAME);
                b.set(x, 0, 1, Part.FRAME);
                // 每三圈横梁顶上挂一盏灯
                b.set(x, 1, 0, (x - x0) % 12 == 0 ? Part.LIGHT : Part.FRAME);
            }
            if (--run <= 0) {
                conduit = !conduit;
                run = conduit ? rng.range(3, 7) : rng.range(4, 10);
            }
            if (conduit) {
                b.set(x, 0, 0, Part.CONDUIT);
            }
        }
    }

    /** 沿 x 轴的圆柱舱段：一层舱板，两头封口，里面掏空、铺一层地板，顶上隔几格一盏灯，两侧一排舷窗。船头的鼻子是半球。 */
    private static void module(Layout.Builder b, int x0, int x1, int radius, boolean nose, Rng rng) {
        for (int x = x0; x <= x1; x++) {
            double r = sliceRadius(x - x0, radius, nose);
            // 船头的鼻子：比前一片粗出来的那一圈露在外面，也是蒙皮
            double front = x == x0 ? 0 : sliceRadius(x - x0 - 1, radius, nose);
            boolean cap = x == x1 || x == x0;
            for (int y = -radius - 1; y <= radius + 1; y++) {
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    double d = Math.sqrt(y * y + z * z);
                    if (d > r) {
                        continue;
                    }
                    boolean shell = cap || d > r - 1.2 || nose && d > front - 0.2;
                    if (!shell) {
                        // 里面空着，底下一层地板
                        b.clear(x, y, z);
                        if (y == -radius + 1) {
                            b.set(x, y, z, Part.DECK);
                        }
                        continue;
                    }
                    Part part = Part.PLATING;
                    if (nose && x <= x0 + 1 && Math.abs(y) <= 1 && Math.abs(z) <= 2) {
                        // 驾驶舱正前方的窗
                        part = Part.WINDOW;
                    } else if (!cap && (y == 0 || y == 1) && Math.abs(z) >= radius - 1 && (x - x0) % 2 == 1) {
                        part = Part.WINDOW;
                    } else if (!cap && y == radius && z == 0 && (x - x0) % 3 == 1) {
                        part = Part.LIGHT;
                    }
                    b.set(x, y, z, part);
                }
            }
        }
        // 舱里靠墙放一个货柜
        int cx = x0 + 2 + rng.nextInt(Math.max(1, x1 - x0 - 3));
        b.set(cx, -radius + 2, rng.chance(0.5) ? radius - 2 : -(radius - 2), Part.CARGO);
    }

    /** 舱段第 {@code i} 片的半径：船头的鼻子前几片是半球，往前越来越细。 */
    private static double sliceRadius(int i, int radius, boolean nose) {
        double r = radius + 0.5;
        int back = radius - i;
        if (!nose || back <= 0) {
            return r;
        }
        return Math.sqrt(Math.max(0.6, r * r - back * back));
    }

    /** 货舱段：主梁两侧挂着 3×3×3 的货柜，舱板包着，中间一格是货。 */
    private static void cargoPods(Layout.Builder b, int x0, Rng rng) {
        int pods = rng.range(2, 6);
        int placed = 0;
        int[][] sides = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int slot = 0; slot < 2 && placed < pods; slot++) {
            int px = x0 + 1 + slot * 5;
            for (int[] side : sides) {
                if (placed >= pods || rng.chance(0.25)) {
                    continue;
                }
                placed++;
                for (int dx = 0; dx < 3; dx++) {
                    for (int a = -1; a <= 1; a++) {
                        for (int c = 2; c <= 4; c++) {
                            int y = side[0] != 0 ? side[0] * c : a;
                            int z = side[1] != 0 ? side[1] * c : a;
                            boolean center = dx == 1 && a == 0 && c == 3;
                            b.set(px + dx, y, z, center ? Part.CARGO : Part.PLATING);
                        }
                    }
                }
            }
        }
    }

    /** 推进剂罐：主梁两侧各一个球罐（大船上下再各一个），桁架撑着。 */
    private static void tanks(Layout.Builder b, int cx, Rng rng) {
        boolean four = rng.chance(0.4);
        int[][] centers = four ? new int[][] {{0, 5}, {0, -5}, {5, 0}, {-5, 0}} : new int[][] {{0, 5}, {0, -5}};
        for (int[] c : centers) {
            sphere(b, cx, c[0], c[1], 3, Part.TANK);
            // 撑杆
            for (int t = 2; t <= 3; t++) {
                b.set(cx, c[0] == 0 ? 0 : Integer.signum(c[0]) * t, c[1] == 0 ? 0 : Integer.signum(c[1]) * t, Part.FRAME);
            }
        }
    }

    /** 两张散热板：在主梁两侧水平伸出去，前后缘是桁架。 */
    private static void radiators(Layout.Builder b, int x0, int x1, Rng rng) {
        for (int side = -1; side <= 1; side += 2) {
            int width = rng.range(6, 12);
            for (int x = x0; x <= x1; x++) {
                for (int w = 2; w <= 1 + width; w++) {
                    boolean edge = x == x0 || x == x1 || w == 1 + width;
                    b.set(x, 0, side * w, edge ? Part.FRAME : Part.RADIATOR);
                }
            }
        }
    }

    /** 实心圆盘（影子屏蔽）。 */
    private static void disc(Layout.Builder b, int x, int radius, Part part) {
        for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
                if (y * y + z * z <= radius * radius + 1) {
                    b.set(x, y, z, part);
                }
            }
        }
    }

    /** 反应堆：3×3×3 的屏蔽层包着中间的堆芯。 */
    private static void reactor(Layout.Builder b, int cx) {
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    b.set(x, y, z, Part.SHIELDING);
                }
            }
        }
        b.set(cx, 0, 0, Part.REACTOR);
    }

    /** 喷管：一块推力板，后面是越来越粗的喷管。 */
    private static void engine(Layout.Builder b, int x0, int x1) {
        disc(b, x0, 2, Part.FRAME);
        for (int x = x0 + 1; x <= x1; x++) {
            double r = 1.5 + (x - x0) * 0.5;
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    double d = Math.sqrt(y * y + z * z);
                    if (d <= r && d > r - 1.2) {
                        b.set(x, y, z, Part.ENGINE);
                    }
                }
            }
        }
    }

    private static void sphere(Layout.Builder b, int cx, int cy, int cz, int radius, Part part) {
        double r = radius + 0.5;
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int y = -radius - 1; y <= radius + 1; y++) {
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= r && d > r - 1.3) {
                        b.set(cx + x, cy + y, cz + z, part);
                    }
                }
            }
        }
    }

    // ---- 损坏

    /** 破口、掉了的蒙皮，一半的船断成两截。 */
    private static void damage(Layout.Builder b, int length, int[][] modules, int tankStart, Rng rng) {
        List<int[]> entries = b.entries();
        // 蒙皮和散热板掉了一些
        for (int[] e : entries) {
            Part part = Part.values()[e[3]];
            if (part == Part.PLATING && rng.chance(0.03) || part == Part.RADIATOR && rng.chance(0.06)) {
                b.clear(e[0], e[1], e[2]);
            }
        }
        // 破口：挑几块舱板为中心挖掉一团
        int breaches = 2 + length / 30;
        for (int i = 0; i < breaches; i++) {
            int[] e = entries.get(rng.nextInt(entries.size()));
            Part part = Part.values()[e[3]];
            if (part != Part.PLATING && part != Part.TANK && part != Part.RADIATOR) {
                continue;
            }
            b.breach(e[0] + 0.5, e[1] + 0.5, e[2] + 0.5, rng.range(1.4, 2.8), KEEP);
        }
        // 断成两截：在第一节居住舱和推进剂罐之间断开，后半截上下左右错开一点
        if (rng.chance(0.5) && modules.length > 0) {
            int from = modules[0][1] + 2;
            int to = tankStart - 4;
            if (to > from) {
                int breakX = rng.range(from, to);
                int gap = rng.range(2, 4);
                for (int[] e : b.entries()) {
                    if (e[0] >= breakX && e[0] < breakX + gap) {
                        b.clear(e[0], e[1], e[2]);
                    }
                }
                int dy = rng.range(-2, 2);
                int dz = rng.range(-3, 3);
                if (dy == 0 && dz == 0) {
                    dz = 2;
                }
                b.shift(breakX + gap, dy, dz);
            }
        }
    }
}
