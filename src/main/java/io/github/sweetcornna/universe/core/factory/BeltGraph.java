package io.github.sweetcornna.universe.core.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * 传送带怎么连成线路（ADR-0011）：给出每格传送带的位置、朝向、坡度和档位，算出谁接在谁后面、哪几格合成一条
 * 线路、线路尽头交给谁。适配层的线路重建和方块的弯道外观都用这里的规则，两边不会对不上。
 *
 * <p>朝向用原版的水平序号：0 南（+z）、1 西（-x）、2 北（-z）、3 东（+x），顺时针加一。
 *
 * <p>每格有一个出口和一个入口，都用"物品落在哪一格的地板上"表示。平的和上坡从本格地板进；下坡从本格上面那格的
 * 地板进（也就是本格顶上的后沿）。平的和下坡出到前面一格的地板，上坡出到前上方那格的地板。
 *
 * <p>连接沿用异星工厂的规则：从正后方接进来的同档传送带接成同一条线路；没有正后方的上游、只有一侧接进来时，
 * 这格是弯道，也接成同一条线路；其余从侧面接进来的是侧面汇入，各自是一条线路，尽头交到这格的中间。这样每格最多
 * 一个上游，线路只会是链或者环。
 */
public final class BeltGraph {
    private BeltGraph() {
    }

    /** 坡度。 */
    public enum Slope {
        FLAT,
        UP,
        DOWN
    }

    /** 弯道从哪一侧接进来（相对传送带的朝向）。 */
    public enum Side {
        NONE,
        LEFT,
        RIGHT
    }

    /** 一格传送带。 */
    public interface Node {
        int x();

        int y();

        int z();

        /** 水平朝向，见类注释。 */
        int facing();

        Slope slope();

        BeltTier tier();
    }

    /** 按位置查传送带，没有（或者没加载）返回 null。 */
    @FunctionalInterface
    public interface Lookup<N extends Node> {
        @Nullable N at(int x, int y, int z);
    }

    /** 线路尽头交给的那格传送带；{@code side} 为真时是从侧面汇入，交到那格的中间。 */
    public record Target<N extends Node>(N belt, boolean side) {
    }

    /** 一条线路上的传送带，从起点到尽头；{@code loop} 为真时尽头接回起点。 */
    public record Chain<N extends Node>(List<N> nodes, boolean loop) {
    }

    public static int dx(int facing) {
        return switch (facing & 3) {
            case 1 -> -1;
            case 3 -> 1;
            default -> 0;
        };
    }

    public static int dz(int facing) {
        return switch (facing & 3) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
    }

    public static int clockwise(int facing) {
        return (facing + 1) & 3;
    }

    public static int counterClockwise(int facing) {
        return (facing + 3) & 3;
    }

    /** 两个朝向互相垂直。 */
    public static boolean perpendicular(int a, int b) {
        return ((a - b) & 1) != 0;
    }

    // ---- 出口和上游

    /** 出口落在哪一格的地板上：{x, y, z}。 */
    public static int[] exit(Node n) {
        return new int[] {n.x() + dx(n.facing()), n.y() + (n.slope() == Slope.UP ? 1 : 0), n.z() + dz(n.facing())};
    }

    /** 从正后方进来时落在哪一格的地板上。 */
    public static int[] entry(Node n) {
        return new int[] {n.x(), n.y() + (n.slope() == Slope.DOWN ? 1 : 0), n.z()};
    }

    /** 朝 {@code facing}、出口落在 (x, y, z) 的传送带：平的和下坡在后面一格，上坡在后下方一格。 */
    private static <N extends Node> @Nullable N feederFacing(int x, int y, int z, int facing, Lookup<N> lookup) {
        int bx = x - dx(facing);
        int bz = z - dz(facing);
        N level = lookup.at(bx, y, bz);
        if (level != null && level.facing() == facing && level.slope() != Slope.UP) {
            return level;
        }
        N below = lookup.at(bx, y - 1, bz);
        if (below != null && below.facing() == facing && below.slope() == Slope.UP) {
            return below;
        }
        return null;
    }

    /** 所有出口落在 (x, y, z) 的传送带，不管朝向和档位。 */
    public static <N extends Node> List<N> feeders(int x, int y, int z, Lookup<N> lookup) {
        List<N> out = new ArrayList<>(2);
        for (int facing = 0; facing < 4; facing++) {
            N feeder = feederFacing(x, y, z, facing, lookup);
            if (feeder != null) {
                out.add(feeder);
            }
        }
        return out;
    }

    /** 正后方接进来的同档传送带。 */
    public static <N extends Node> @Nullable N straightFeeder(N n, Lookup<N> lookup) {
        int[] e = entry(n);
        N feeder = feederFacing(e[0], e[1], e[2], n.facing(), lookup);
        return feeder != null && feeder.tier() == n.tier() ? feeder : null;
    }

    /** 从两侧接进来的同档传送带（只有平的传送带收侧面来的东西）。 */
    public static <N extends Node> List<N> sideFeeders(N n, Lookup<N> lookup) {
        List<N> out = new ArrayList<>(2);
        if (n.slope() != Slope.FLAT) {
            return out;
        }
        for (int facing : new int[] {clockwise(n.facing()), counterClockwise(n.facing())}) {
            N feeder = feederFacing(n.x(), n.y(), n.z(), facing, lookup);
            if (feeder != null && feeder.tier() == n.tier()) {
                out.add(feeder);
            }
        }
        return out;
    }

    /** 这格在线路上的上游：正后方的那格，或者弯道唯一的那一侧；没有返回 null。 */
    public static <N extends Node> @Nullable N previous(N n, Lookup<N> lookup) {
        N straight = straightFeeder(n, lookup);
        if (straight != null) {
            return straight;
        }
        List<N> sides = sideFeeders(n, lookup);
        return sides.size() == 1 ? sides.get(0) : null;
    }

    /** 弯道从哪一侧进来；不是弯道返回 {@link Side#NONE}。 */
    public static <N extends Node> Side curve(N n, Lookup<N> lookup) {
        if (n.slope() != Slope.FLAT || straightFeeder(n, lookup) != null) {
            return Side.NONE;
        }
        List<N> sides = sideFeeders(n, lookup);
        if (sides.size() != 1) {
            return Side.NONE;
        }
        // 往顺时针方向走的传送带是从左边进来的
        return sides.get(0).facing() == clockwise(n.facing()) ? Side.LEFT : Side.RIGHT;
    }

    /**
     * 线路尽头 {@code end} 的东西交给哪格传送带：正前方顺着同一方向的那格，或者从侧面汇入的那格。前面没有、
     * 迎面对着、或者是坡道的侧面时返回 null（物品停在尽头）。
     */
    public static <N extends Node> @Nullable Target<N> target(N end, Lookup<N> lookup) {
        int[] t = exit(end);
        N n = lookup.at(t[0], t[1], t[2]);
        if (n != null) {
            if (n.facing() == end.facing() && n.slope() != Slope.DOWN) {
                return new Target<>(n, false);
            }
            if (n.slope() == Slope.FLAT && perpendicular(n.facing(), end.facing())) {
                return new Target<>(n, true);
            }
        }
        N down = lookup.at(t[0], t[1] - 1, t[2]);
        if (down != null && down.slope() == Slope.DOWN && down.facing() == end.facing()) {
            return new Target<>(down, false);
        }
        return null;
    }

    // ---- 线路

    /** 断开环线时选的起点：坐标最小的那格。 */
    private static final Comparator<Node> ORDER = Comparator.comparingInt(Node::x).thenComparingInt(Node::y).thenComparingInt(Node::z);

    /**
     * 把 {@code nodes} 串成线路。上游不在 {@code nodes} 里的格子当作起点（调用方应当保证要重建的格子连同它们的
     * 上下游都在里面）。
     */
    public static <N extends Node> List<Chain<N>> chains(Collection<N> nodes, Lookup<N> lookup) {
        Map<N, N> next = new IdentityHashMap<>();
        Map<N, Boolean> hasPrevious = new IdentityHashMap<>();
        Map<N, Boolean> member = new IdentityHashMap<>();
        for (N n : nodes) {
            member.put(n, Boolean.TRUE);
        }
        for (N n : nodes) {
            N previous = previous(n, lookup);
            if (previous != null && member.containsKey(previous)) {
                next.put(previous, n);
                hasPrevious.put(n, Boolean.TRUE);
            }
        }
        List<Chain<N>> out = new ArrayList<>();
        Map<N, Boolean> visited = new IdentityHashMap<>();
        for (N n : nodes) {
            if (!hasPrevious.containsKey(n)) {
                out.add(new Chain<>(walk(n, next, visited), false));
            }
        }
        // 剩下的都在环上
        List<N> rest = new ArrayList<>();
        for (N n : nodes) {
            if (!visited.containsKey(n)) {
                rest.add(n);
            }
        }
        rest.sort(ORDER);
        for (N n : rest) {
            if (!visited.containsKey(n)) {
                out.add(new Chain<>(walk(n, next, visited), true));
            }
        }
        return out;
    }

    private static <N extends Node> List<N> walk(N start, Map<N, N> next, Map<N, Boolean> visited) {
        List<N> chain = new ArrayList<>();
        N current = start;
        while (current != null && !visited.containsKey(current)) {
            visited.put(current, Boolean.TRUE);
            chain.add(current);
            current = next.get(current);
        }
        return chain;
    }
}
