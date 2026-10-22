package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Role;
import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 星球上坠毁的飞船（ADR-0024）：飞船设计器生成的一艘完整的船，摔掉了一些船体板，驾驶台坏了。
 * 拿 {@link #REPAIR} 修好驾驶台，组装起来就能飞。
 */
public final class CrashedShip {
    /** 修驾驶台要的材料。 */
    public static final List<Tech.ItemCost> REPAIR = List.of(new Tech.ItemCost("universe:titanium_ingot", 4),
            new Tech.ItemCost("universe:circuit_board", 2));
    /** 档次的概率（%）：C、B、A、S。 */
    static final double[] GRADE_ODDS = {55, 32, 10, 3};
    /** 船体板摔掉几成。 */
    static final double BROKEN = 0.15;

    private CrashedShip() {
    }

    /** 兴趣点种子对应的那艘船（新的派生键 {@code crashed_ship}）。 */
    public static ShipDesign design(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "crashed_ship"));
        ShipType type = rng.weighted(List.of(ShipType.values()), t -> 1);
        Grade grade = rng.weighted(List.of(Grade.values()), g -> GRADE_ODDS[g.ordinal()]);
        return ShipDesigner.design(rng.nextLong(), type, grade);
    }

    /**
     * 摔坏以后剩下的格子：外层的船体板、灯拿掉约 {@link #BROKEN}，功能部件和驾驶舱的玻璃不动；
     * 拿掉以后剩下的每一格都还和驾驶台连着（不然组装时会落下推进器）。
     */
    public static List<Cell> wreck(ShipDesign design, long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "crashed_ship_damage"));
        Map<Long, Cell> cells = new HashMap<>();
        Cell helm = null;
        for (Cell cell : design.cells()) {
            cells.put(key(cell.x(), cell.y(), cell.z()), cell);
            if (cell.role() == Role.HELM) {
                helm = cell;
            }
        }
        if (helm == null) {
            return design.cells();
        }
        List<Cell> plates = new ArrayList<>();
        for (Cell cell : design.cells()) {
            if (cell.role() == Role.HULL || cell.role() == Role.TRIM || cell.role() == Role.LAMP) {
                plates.add(cell);
            }
        }
        int target = (int) Math.round(plates.size() * BROKEN);
        int removed = 0;
        for (int tries = 0; tries < plates.size() * 3 && removed < target && !plates.isEmpty(); tries++) {
            Cell cell = plates.remove(rng.nextInt(plates.size()));
            long key = key(cell.x(), cell.y(), cell.z());
            cells.remove(key);
            if (connected(cells, helm)) {
                removed++;
            } else {
                cells.put(key, cell);
            }
        }
        List<Cell> out = new ArrayList<>();
        for (Cell cell : design.cells()) {
            if (cells.containsKey(key(cell.x(), cell.y(), cell.z()))) {
                out.add(cell);
            }
        }
        return out;
    }

    /** 从驾驶台出发，六个方向相邻地走，能不能走到每一格。 */
    static boolean connected(Map<Long, Cell> cells, Cell helm) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(helm);
        seen.add(key(helm.x(), helm.y(), helm.z()));
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            for (int[] s : steps) {
                long key = key(cell.x() + s[0], cell.y() + s[1], cell.z() + s[2]);
                Cell next = cells.get(key);
                if (next != null && seen.add(key)) {
                    queue.add(next);
                }
            }
        }
        return seen.size() == cells.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
