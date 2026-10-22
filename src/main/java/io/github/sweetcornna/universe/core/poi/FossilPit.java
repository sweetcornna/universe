package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 化石坑（ADR-0026）：7×7 的发掘坑像考古现场那样一层层往下挖，外圈 1 格深，中圈 2 格，正中 3×3 挖到 3 格；
 * 坑底露着 6–10 块化石土，越往中间越多。坐标以坑中心的地面为原点，y 往上。
 */
public final class FossilPit {
    /** 坑的半边长：7×7。 */
    public static final int HALF = 3;
    public static final int MIN_FOSSILS = 6;
    public static final int MAX_FOSSILS = 10;

    /** 一块化石土的位置。 */
    public record Cell(int dx, int dy, int dz) {
    }

    private FossilPit() {
    }

    /** 这一列挖多深（格）；坑外为 0。 */
    public static int depth(int dx, int dz) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring > HALF) {
            return 0;
        }
        return ring <= 1 ? 3 : ring == 2 ? 2 : 1;
    }

    /** 化石土的位置：都在坑底（挖开的最下面一格的下面），按深度加权挑，深的地方多。 */
    public static List<Cell> layout(long seed) {
        Rng rng = Rng.of(seed);
        List<Cell> candidates = new ArrayList<>();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                candidates.add(new Cell(dx, -depth(dx, dz), dz));
            }
        }
        int count = rng.range(MIN_FOSSILS, MAX_FOSSILS);
        List<Cell> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Cell cell = rng.weighted(candidates, c -> -c.dy());
            candidates.remove(cell);
            picked.add(cell);
        }
        return picked;
    }
}
