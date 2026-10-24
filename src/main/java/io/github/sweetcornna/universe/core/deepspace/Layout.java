package io.github.sweetcornna.universe.core.deepspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一座用方块搭的东西（残骸、前哨站、碎片）：局部坐标里的一组格子，每格一种部件。
 *
 * <p>搭的时候用 {@link Builder}：后放的覆盖先放的，{@link Builder#clear} 挖掉；最后按坐标排好，同一个种子每次一样。
 */
public final class Layout {
    /** 一格。 */
    public record Cell(int x, int y, int z, Part part) {
    }

    private final List<Cell> cells;
    private final int[] min;
    private final int[] max;

    private Layout(List<Cell> cells) {
        this.cells = List.copyOf(cells);
        this.min = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        this.max = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Cell cell : cells) {
            this.min[0] = Math.min(this.min[0], cell.x());
            this.min[1] = Math.min(this.min[1], cell.y());
            this.min[2] = Math.min(this.min[2], cell.z());
            this.max[0] = Math.max(this.max[0], cell.x());
            this.max[1] = Math.max(this.max[1], cell.y());
            this.max[2] = Math.max(this.max[2], cell.z());
        }
        if (cells.isEmpty()) {
            this.min[0] = this.min[1] = this.min[2] = 0;
            this.max[0] = this.max[1] = this.max[2] = 0;
        }
    }

    public List<Cell> cells() {
        return this.cells;
    }

    /** 包住所有格子的最小角（含）。 */
    public int[] min() {
        return this.min.clone();
    }

    /** 包住所有格子的最大角（含）。 */
    public int[] max() {
        return this.max.clone();
    }

    /** 包围盒的中心（方块中心的坐标）。 */
    public double[] center() {
        return new double[] {(this.min[0] + this.max[0] + 1) / 2.0, (this.min[1] + this.max[1] + 1) / 2.0, (this.min[2] + this.max[2] + 1) / 2.0};
    }

    /** 最长的一边有几格。 */
    public int length() {
        return Math.max(this.max[0] - this.min[0], Math.max(this.max[1] - this.min[1], this.max[2] - this.min[2])) + 1;
    }

    /** 每种部件有几格。 */
    public Map<Part, Integer> counts() {
        Map<Part, Integer> counts = new EnumMap<>(Part.class);
        for (Cell cell : this.cells) {
            counts.merge(cell.part(), 1, Integer::sum);
        }
        return counts;
    }

    public int count(Part part) {
        return this.counts().getOrDefault(part, 0);
    }

    /** 某种部件的第一格（按坐标排的顺序）；没有时为空。 */
    public java.util.Optional<Cell> first(Part part) {
        for (Cell cell : this.cells) {
            if (cell.part() == part) {
                return java.util.Optional.of(cell);
            }
        }
        return java.util.Optional.empty();
    }

    /** 搭一座：往格子里放部件。 */
    public static final class Builder {
        private final Map<Long, Part> cells = new HashMap<>();

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        private static int unpack(long value, int shift) {
            int raw = (int) ((value >>> shift) & 0x1FFFFF);
            return (raw << 11) >> 11;
        }

        public Builder set(int x, int y, int z, Part part) {
            this.cells.put(key(x, y, z), part);
            return this;
        }

        /** 只在空格里放。 */
        public Builder fill(int x, int y, int z, Part part) {
            this.cells.putIfAbsent(key(x, y, z), part);
            return this;
        }

        public Builder clear(int x, int y, int z) {
            this.cells.remove(key(x, y, z));
            return this;
        }

        public Part get(int x, int y, int z) {
            return this.cells.get(key(x, y, z));
        }

        public boolean has(int x, int y, int z) {
            return this.cells.containsKey(key(x, y, z));
        }

        public int size() {
            return this.cells.size();
        }

        /** 把 x ≥ {@code fromX} 的格子整体挪开（断成两截的残骸，后半截错开）。 */
        public Builder shift(int fromX, int dy, int dz) {
            Map<Long, Part> moved = new HashMap<>();
            this.cells.entrySet().removeIf(entry -> {
                long k = entry.getKey();
                int x = unpack(k, 42);
                if (x < fromX) {
                    return false;
                }
                moved.put(key(x, unpack(k, 21) + dy, unpack(k, 0) + dz), entry.getValue());
                return true;
            });
            this.cells.putAll(moved);
            return this;
        }

        /** 挖掉一个球（船壳上的破口）；{@code keep} 里的部件不挖。 */
        public Builder breach(double cx, double cy, double cz, double radius, java.util.Set<Part> keep) {
            int r = (int) Math.ceil(radius);
            for (int x = (int) Math.floor(cx) - r; x <= (int) Math.floor(cx) + r; x++) {
                for (int y = (int) Math.floor(cy) - r; y <= (int) Math.floor(cy) + r; y++) {
                    for (int z = (int) Math.floor(cz) - r; z <= (int) Math.floor(cz) + r; z++) {
                        double dx = x + 0.5 - cx;
                        double dy = y + 0.5 - cy;
                        double dz = z + 0.5 - cz;
                        Part part = this.get(x, y, z);
                        if (part != null && !keep.contains(part) && dx * dx + dy * dy + dz * dz <= radius * radius) {
                            this.clear(x, y, z);
                        }
                    }
                }
            }
            return this;
        }

        /** 所有格子的坐标：{x, y, z, 部件序号}。 */
        List<int[]> entries() {
            List<int[]> list = new ArrayList<>();
            this.cells.forEach((k, part) -> list.add(new int[] {unpack(k, 42), unpack(k, 21), unpack(k, 0), part.ordinal()}));
            return list;
        }

        public Layout build() {
            List<int[]> entries = this.entries();
            entries.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[2], b[2]));
            List<Cell> list = new ArrayList<>(entries.size());
            Part[] parts = Part.values();
            for (int[] e : entries) {
                list.add(new Cell(e[0], e[1], e[2], parts[e[3]]));
            }
            return new Layout(Collections.unmodifiableList(list));
        }
    }
}
