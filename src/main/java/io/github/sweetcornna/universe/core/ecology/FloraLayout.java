package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.procgen.SimplexNoise;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/**
 * 植物在地面上怎么分布：哪里长、长哪一种。
 *
 * <p>植被按低频噪声成片起伏，有茂密的草甸也有空地；每个物种另有一层噪声，所以同一种植物扎堆生长，
 * 不同物种的斑块交错。树也一样，有成片的树林和林间空地。随机数由调用方给（世界生成的随机源），
 * 这里只按位置和群系算概率。
 */
public final class FloraLayout {
    /** 树木密度为 1、群系系数为 1 时，一个区块平均几棵树。 */
    public static final double TREES_PER_CHUNK = 7;

    private final Flora flora;
    private final Ecology ecology;
    private final SimplexNoise patches;
    private final SimplexNoise species;
    private final SimplexNoise groves;

    public FloraLayout(Flora flora, Ecology ecology) {
        this.flora = flora;
        this.ecology = ecology;
        long seed = Seeds.derive(ecology.seed(), "flora/layout");
        this.patches = new SimplexNoise(Seeds.derive(seed, "patches"));
        this.species = new SimplexNoise(Seeds.derive(seed, "species"));
        this.groves = new SimplexNoise(Seeds.derive(seed, "groves"));
    }

    public Flora flora() {
        return this.flora;
    }

    /**
     * 这一格地面长哪种植物。
     *
     * @param roll 决定长不长的随机数 0–1
     * @param pick 挑物种的随机数 0–1
     * @return 不长时返回 null
     */
    public PlantSpecies plantAt(int x, int z, BiomeKind biome, double roll, double pick) {
        Biotope biotope = Biotope.of(biome);
        if (biotope == null || this.flora.plants().isEmpty()) {
            return null;
        }
        // 斑块：-1–1 的噪声映射成 0.15–1.6 的倍数
        double patch = 0.85 + 0.75 * this.patches.fbm(x / 48.0, z / 48.0, 3);
        double density = this.ecology.flora() * Biotope.plantDensity(biome) * Math.max(0.15, patch);
        if (roll >= density) {
            return null;
        }
        List<PlantSpecies> candidates = new ArrayList<>();
        double[] weights = new double[this.flora.plants().size()];
        double total = 0;
        for (PlantSpecies plant : this.flora.plants()) {
            if (!plant.biotopes().contains(biotope)) {
                continue;
            }
            // 每个物种一层噪声，偏移开来互不相关
            double clump = this.species.noise(x / 22.0 + plant.index() * 31.7, z / 22.0 - plant.index() * 17.3);
            double w = plant.abundance() * Math.max(0.03, 0.35 + clump);
            weights[candidates.size()] = w;
            candidates.add(plant);
            total += w;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        double target = pick * total;
        for (int i = 0; i < candidates.size(); i++) {
            target -= weights[i];
            if (target < 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 一个区块里尝试种几棵树。
     *
     * @param x    区块中心的方块坐标
     * @param roll 0–1 的随机数，把小数部分变成概率
     */
    public int treeAttempts(int x, int z, BiomeKind biome, double roll) {
        if (this.flora.trees().isEmpty()) {
            return 0;
        }
        double grove = 0.9 + 0.8 * this.groves.fbm(x / 96.0, z / 96.0, 2);
        double expected = this.ecology.trees() * Biotope.treeDensity(biome) * TREES_PER_CHUNK * Math.max(0, grove);
        // 没有树林的星球也零星长几株
        if (expected <= 0 && Biotope.treeDensity(biome) > 0) {
            expected = 0.15 * this.ecology.flora();
        }
        return (int) Math.floor(expected + roll);
    }

    /** 这个位置长哪种树；这个群系里没有合适的树种时返回 null。 */
    public TreeSpecies treeAt(int x, int z, BiomeKind biome, double pick) {
        Biotope biotope = Biotope.of(biome);
        if (biotope == null) {
            return null;
        }
        List<TreeSpecies> candidates = new ArrayList<>();
        double total = 0;
        double[] weights = new double[this.flora.trees().size()];
        for (TreeSpecies tree : this.flora.trees()) {
            if (!tree.biotopes().contains(biotope)) {
                continue;
            }
            double clump = this.species.noise(x / 40.0 - tree.index() * 23.1, z / 40.0 + tree.index() * 41.9);
            double w = tree.abundance() * Math.max(0.05, 0.4 + clump);
            weights[candidates.size()] = w;
            candidates.add(tree);
            total += w;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        double target = pick * total;
        for (int i = 0; i < candidates.size(); i++) {
            target -= weights[i];
            if (target < 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}
