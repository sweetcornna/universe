package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/** 地表条件：这一列在活火山口的熔岩湖里，见 {@link TerrainModel#lavaLake}。 */
public record PlanetLavaCondition(PlanetGenSpec spec) implements MaterialCondition {
    public static final MapCodec<PlanetLavaCondition> CODEC = PlanetGenSpec.CODEC.fieldOf("planet")
            .xmap(PlanetLavaCondition::new, PlanetLavaCondition::spec);

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        TerrainModel model = this.spec.model();
        return new MaterialRuleContext.LazyXZCondition(context) {
            @Override
            protected boolean compute() {
                return model.lavaLake(this.context.blockX(), this.context.blockZ());
            }
        };
    }

    @Override
    public MapCodec<PlanetLavaCondition> codec() {
        return CODEC;
    }
}
