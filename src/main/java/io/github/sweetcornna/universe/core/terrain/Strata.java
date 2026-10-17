package io.github.sweetcornna.universe.core.terrain;

import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.procgen.Colors;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 岩层的层序：每颗星球一套，从世界底部到顶部逐层定下颜色。
 *
 * <p>颜色取自星球配色（岩石、沙、土、点缀色）和一种红褐的氧化色，每层厚 1 到 6 格，明暗略有起伏。
 * 客户端给岩层方块染色时按方块的 Y 查表，于是方山和峡谷的崖壁上出现一条条彩色的地层。
 */
public final class Strata {
    /** 氧化铁的红褐色，各星球的岩层里都掺一些，像丹霞地貌的红层。 */
    private static final int OXIDE = 0xA4472C;

    private Strata() {
    }

    /** 下标 0 对应 {@link TerrainModel#MIN_Y}，长度覆盖整个世界高度。 */
    public static int[] colors(Palette palette, long planetSeed) {
        Rng rng = Rng.of(Seeds.derive(planetSeed, "strata"));
        // 不用地表色：宜居星球的地表色是草绿，混进岩层像一级级梯田
        List<Integer> sources = List.of(
                palette.rock(),
                palette.sand(),
                palette.soil(),
                Colors.lerp(palette.sand(), OXIDE, 0.45),
                Colors.lerp(palette.sand(), palette.accent(), 0.35),
                Colors.lerp(palette.rock(), palette.soil(), 0.5));
        double[] weights = {3, 3, 2, 1.6, 0.8, 1.5};
        int height = TerrainModel.MAX_Y - TerrainModel.MIN_Y + 1;
        int[] colors = new int[height];
        int y = 0;
        int previous = -1;
        while (y < height) {
            int index;
            do {
                index = rng.weighted(List.of(0, 1, 2, 3, 4, 5), i -> weights[i]);
            } while (index == previous);
            previous = index;
            double brightness = rng.range(0.82, 1.12);
            int color = scale(sources.get(index), brightness);
            int thickness = rng.chance(0.3) ? 1 : rng.range(2, 6);
            for (int i = 0; i < thickness && y < height; i++, y++) {
                colors[y] = color;
            }
        }
        return colors;
    }

    private static int scale(int rgb, double f) {
        return Colors.rgb(
                Math.min(255, (int) (Colors.red(rgb) * f)),
                Math.min(255, (int) (Colors.green(rgb) * f)),
                Math.min(255, (int) (Colors.blue(rgb) * f)));
    }
}
