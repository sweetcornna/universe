package io.github.sweetcornna.universe.core.survival;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 扫描仪把扫到的矿石方块并成矿脉（玩法设计 §4）：同一种矿、离某条矿脉的第一块不超过 {@link #MERGE} 格的算同一条。
 * HUD 上一条矿脉画一个标记，写着有几块。
 */
public final class VeinClusters {
    /** 合并距离（切比雪夫距离，格）。 */
    public static final int MERGE = 4;

    /** 一条矿脉：位置是各块的平均（取整），{@code kind} 是适配层给的矿种编号。 */
    public record Vein(int x, int y, int z, int kind, int count) {
        public double distanceSq(int ox, int oy, int oz) {
            double dx = this.x - ox;
            double dy = this.y - oy;
            double dz = this.z - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static final class Builder {
        final int seedX;
        final int seedY;
        final int seedZ;
        final int kind;
        long sumX;
        long sumY;
        long sumZ;
        int count;

        Builder(int x, int y, int z, int kind) {
            this.seedX = x;
            this.seedY = y;
            this.seedZ = z;
            this.kind = kind;
        }
    }

    private final List<Builder> builders = new ArrayList<>();

    public void add(int x, int y, int z, int kind) {
        Builder target = null;
        for (Builder builder : this.builders) {
            if (builder.kind == kind && Math.abs(builder.seedX - x) <= MERGE && Math.abs(builder.seedY - y) <= MERGE
                    && Math.abs(builder.seedZ - z) <= MERGE) {
                target = builder;
                break;
            }
        }
        if (target == null) {
            target = new Builder(x, y, z, kind);
            this.builders.add(target);
        }
        target.sumX += x;
        target.sumY += y;
        target.sumZ += z;
        target.count++;
    }

    /** 离 (ox, oy, oz) 最近的至多 {@code limit} 条矿脉，由近到远。 */
    public List<Vein> nearest(int ox, int oy, int oz, int limit) {
        List<Vein> veins = new ArrayList<>(this.builders.size());
        for (Builder b : this.builders) {
            veins.add(new Vein((int) Math.round((double) b.sumX / b.count), (int) Math.round((double) b.sumY / b.count),
                    (int) Math.round((double) b.sumZ / b.count), b.kind, b.count));
        }
        veins.sort(Comparator.comparingDouble(vein -> vein.distanceSq(ox, oy, oz)));
        return veins.size() > limit ? List.copyOf(veins.subList(0, limit)) : veins;
    }
}
