package io.github.sweetcornna.universe.core.deepspace;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 碎片带（ADR-0034 §四）：一场事故留下的三五十块残骸碎片，散在一个扁平的椭球里，中间密、外面稀。
 * 每块碎片是一个实体，按种子搭出几格到几十格的残骸部件（舱板、桁架、散热板、罐子的碎片），慢慢翻滚；
 * 采矿光束打碎了掉废金属、合金板、电子元件，偶尔有技术模块。小块的能用引力抓取器抓。
 */
public final class DebrisField {
    public static final int MIN_COUNT = 30;
    public static final int MAX_COUNT = 50;
    /** 碎片带水平方向的半径（格）。 */
    public static final double RADIUS = 110;
    /** 碎片带上下的半厚（格）。 */
    public static final double THICKNESS = 28;

    private DebrisField() {
    }

    /** 碎片的大小：方块数、画的时候缩放多少、有多结实（采矿光束要打多久）。 */
    public enum Size {
        /** 小块：两三格，缩到一半画，一格左右大，能抓。 */
        SMALL(2, 4, 0.5, 30),
        MEDIUM(6, 14, 1.0, 120),
        LARGE(18, 40, 1.0, 320);

        private final int min;
        private final int max;
        private final double scale;
        private final double durability;

        Size(int min, int max, double scale, double durability) {
            this.min = min;
            this.max = max;
            this.scale = scale;
            this.durability = durability;
        }

        public int min() {
            return this.min;
        }

        public int max() {
            return this.max;
        }

        /** 画的时候每格的边长（格）。 */
        public double scale() {
            return this.scale;
        }

        /** 打碎要多少伤害（采矿光束每刻约 4 点）。 */
        public double durability() {
            return this.durability;
        }
    }

    /**
     * 碎片带里的一块。
     *
     * @param index 在碎片带里的序号（打掉了按序号记，不再长出来）
     * @param dx    相对碎片带中心的位置
     */
    public record Slot(int index, double dx, double dy, double dz, Size size, long seed) {
    }

    /** 碎片带里所有的碎片，按种子定下，同一个种子每次一样。 */
    public static List<Slot> slots(long fieldSeed) {
        Rng rng = Rng.of(Seeds.derive(fieldSeed, "debris_field"));
        int count = rng.range(MIN_COUNT, MAX_COUNT);
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 半径按平方根往外撒会很均匀，这里按 0.75 次方：中间密一些
            double r = Math.pow(rng.nextDouble(), 0.75) * RADIUS;
            double angle = rng.nextDouble() * Math.PI * 2;
            double dy = rng.gaussian() * THICKNESS * 0.4;
            double roll = rng.nextDouble();
            Size size = roll < 0.45 ? Size.SMALL : roll < 0.85 ? Size.MEDIUM : Size.LARGE;
            slots.add(new Slot(i, Math.cos(angle) * r, Math.clamp(dy, -THICKNESS, THICKNESS), Math.sin(angle) * r, size, rng.nextLong()));
        }
        return slots;
    }

    /** 一块碎片的样子：残骸的部件拼成的一小团，中心大致在原点。 */
    public static Layout shape(long seed, Size size) {
        Rng rng = Rng.of(Seeds.derive(seed, "debris"));
        Layout.Builder b = new Layout.Builder();
        int target = rng.range(size.min(), size.max());
        int kind = rng.nextInt(4);
        switch (kind) {
            case 0 -> plate(b, target, rng);
            case 1 -> truss(b, target, rng);
            case 2 -> tank(b, target, rng);
            default -> lump(b, target, rng);
        }
        return b.build();
    }

    /** 一片弯曲的舱板，背面贴着管线。 */
    private static void plate(Layout.Builder b, int target, Rng rng) {
        int w = Math.max(1, (int) Math.round(Math.sqrt(target)));
        for (int i = 0; b.size() < target && i < w * w * 2; i++) {
            int x = i % w - w / 2;
            int z = i / w - w / 2;
            int y = (x * x + z * z) / 6;
            b.set(x, y, z, rng.chance(0.15) ? Part.WINDOW : Part.PLATING);
            if (b.size() < target && rng.chance(0.18)) {
                b.set(x, y + 1, z, Part.CONDUIT);
            }
        }
    }

    /** 一截桁架，挂着一角散热板。 */
    private static void truss(Layout.Builder b, int target, Rng rng) {
        int length = Math.max(2, target / 3);
        for (int x = -length / 2; x < length - length / 2 && b.size() < target; x++) {
            b.set(x, 0, 0, Part.FRAME);
            if (b.size() < target && rng.chance(0.6)) {
                b.set(x, 1, 0, Part.FRAME);
            }
            if (b.size() < target && x % 2 == 0) {
                b.set(x, 0, 1, Part.RADIATOR);
            }
            if (b.size() < target && rng.chance(0.4)) {
                b.set(x, 0, 2, Part.RADIATOR);
            }
            if (b.size() < target && rng.chance(0.2)) {
                b.set(x, 1, -1, Part.CONDUIT);
            }
        }
    }

    /** 推进剂罐炸开的一片弧形罐壁。 */
    private static void tank(Layout.Builder b, int target, Rng rng) {
        int r = target < 8 ? 1 : target < 20 ? 2 : 3;
        for (int x = -r; x <= r && b.size() < target; x++) {
            for (int y = 0; y <= r && b.size() < target; y++) {
                for (int z = -r; z <= r && b.size() < target; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= r + 0.5 && d > r - 0.8) {
                        b.set(x, y, z, Part.TANK);
                    }
                }
            }
        }
        if (b.size() < target && rng.chance(0.5)) {
            b.set(0, 0, 0, Part.FRAME);
        }
    }

    /** 一团扭在一起的舱板、桁架和管线。 */
    private static void lump(Layout.Builder b, int target, Rng rng) {
        int x = 0;
        int y = 0;
        int z = 0;
        Part[] parts = {Part.PLATING, Part.PLATING, Part.FRAME, Part.CONDUIT, Part.PLATING, Part.RADIATOR};
        for (int guard = 0; b.size() < target && guard < target * 8; guard++) {
            b.set(x, y, z, parts[rng.nextInt(parts.length)]);
            switch (rng.nextInt(6)) {
                case 0 -> x++;
                case 1 -> x--;
                case 2 -> y++;
                case 3 -> y--;
                case 4 -> z++;
                default -> z--;
            }
            x = Math.clamp(x, -3, 3);
            y = Math.clamp(y, -2, 2);
            z = Math.clamp(z, -3, 3);
        }
    }

    /** 碎片的外接球半径（格，已经乘上缩放）。 */
    public static double radius(Layout shape, Size size) {
        double[] c = shape.center();
        double max = 0.5;
        for (Layout.Cell cell : shape.cells()) {
            double dx = cell.x() + 0.5 - c[0];
            double dy = cell.y() + 0.5 - c[1];
            double dz = cell.z() + 0.5 - c[2];
            max = Math.max(max, Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.5);
        }
        return max * size.scale();
    }
}
