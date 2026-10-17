package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * 地表条件：陡坡，露出岩石。
 *
 * <p>原版的 {@code steep} 只看两个朝向，北坡和西坡上的草皮会一块块挂在崖壁上。
 * 这里四个朝向都算，另外按地形模型看所在 4 格网格单元的坡度，崖壁上的小台阶也算作陡坡。
 * 网格角点正是密度函数插值用的那些列，每个区块只取 25 个高度（多半已在列缓存里）。
 */
public record PlanetSteepCondition(PlanetGenSpec spec) implements MaterialCondition {
    public static final MapCodec<PlanetSteepCondition> CODEC = PlanetGenSpec.CODEC.fieldOf("planet")
            .xmap(PlanetSteepCondition::new, PlanetSteepCondition::spec);
    /** 实际地表左右两格的高度差达到这个值算陡坡，和原版相同。 */
    private static final int LOCAL_DROP = 4;
    /** 网格单元一条边（4 格）上的高度差达到这个值算陡坡，约 50 度。 */
    private static final double MODEL_DROP = 5;

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        TerrainModel model = this.spec.model();
        return new MaterialRuleContext.LazyXZCondition(context) {
            /** 本区块 5×5 个网格角点的高度，第一次用到时取。 */
            private final double[] corners = new double[25];
            private int chunkX = Integer.MIN_VALUE;
            private int chunkZ = Integer.MIN_VALUE;

            @Override
            protected boolean compute() {
                if (Math.abs(this.context.surfaceGradientX()) >= LOCAL_DROP || Math.abs(this.context.surfaceGradientZ()) >= LOCAL_DROP) {
                    return true;
                }
                int x = this.context.blockX();
                int z = this.context.blockZ();
                int originX = x & ~15;
                int originZ = z & ~15;
                if (originX != this.chunkX || originZ != this.chunkZ) {
                    this.chunkX = originX;
                    this.chunkZ = originZ;
                    for (int i = 0; i < 5; i++) {
                        for (int j = 0; j < 5; j++) {
                            this.corners[i * 5 + j] = model.sample(originX + i * 4, originZ + j * 4).height();
                        }
                    }
                }
                int i = (x - originX) >> 2;
                int j = (z - originZ) >> 2;
                double h00 = this.corners[i * 5 + j];
                double h10 = this.corners[(i + 1) * 5 + j];
                double h01 = this.corners[i * 5 + j + 1];
                double h11 = this.corners[(i + 1) * 5 + j + 1];
                double drop = Math.max(Math.max(Math.abs(h10 - h00), Math.abs(h11 - h01)),
                        Math.max(Math.abs(h01 - h00), Math.abs(h11 - h10)));
                return drop >= MODEL_DROP;
            }
        };
    }

    @Override
    public MapCodec<PlanetSteepCondition> codec() {
        return CODEC;
    }
}
