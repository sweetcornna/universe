package io.github.sweetcornna.universe.survival;

import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;

/**
 * 缺氧和环境危害的伤害类型（数据包注册表的条目，数据生成写出）。都无视护甲、不击退，死亡消息各不相同。
 * 废弃货船里漏电的线管（ADR-0024）电人也在这里，它不无视护甲。
 */
public final class SurvivalDamage {
    public static final ResourceKey<DamageType> VACUUM = key("vacuum");
    public static final ResourceKey<DamageType> COLD = key("cold");
    public static final ResourceKey<DamageType> HEAT = key("heat");
    public static final ResourceKey<DamageType> TOXIC = key("toxic");
    public static final ResourceKey<DamageType> RADIATION = key("radiation");
    public static final ResourceKey<DamageType> SHOCK = key("shock");
    public static final Map<Hazard, ResourceKey<DamageType>> BY_HAZARD = Map.of(
            Hazard.COLD, COLD, Hazard.HEAT, HEAT, Hazard.TOXIC, TOXIC, Hazard.RADIATION, RADIATION);

    private SurvivalDamage() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Universe.id(name));
    }

    public static void bootstrap(BootstrapContext<DamageType> context) {
        context.register(VACUUM, new DamageType("universe.vacuum", DamageScaling.NEVER, 0.0F, DamageEffects.DROWNING));
        context.register(COLD, new DamageType("universe.cold", DamageScaling.NEVER, 0.0F, DamageEffects.FREEZING));
        context.register(HEAT, new DamageType("universe.heat", DamageScaling.NEVER, 0.0F, DamageEffects.BURNING));
        context.register(TOXIC, new DamageType("universe.toxic", DamageScaling.NEVER, 0.0F));
        context.register(RADIATION, new DamageType("universe.radiation", DamageScaling.NEVER, 0.0F));
        context.register(SHOCK, new DamageType("universe.shock", DamageScaling.NEVER, 0.1F));
    }
}
