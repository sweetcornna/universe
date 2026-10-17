package io.github.sweetcornna.universe.world.planet;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.DayCycle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 星球维度的维度类型，每种昼夜周期一个（数据生成里由原版主世界的维度类型和时间线改出来）。
 *
 * <p>维度类型属于登录时同步的注册表，运行时不能新增，所以按周期预先做好几种，星球按自己的周期挑。
 * 时间线挂在主世界的时钟上：一天 48000 刻的星球，太阳走一圈要用两个主世界日。
 */
public final class PlanetDimensionTypes {
    private static final Map<DayCycle, ResourceKey<DimensionType>> KEYS = new EnumMap<>(DayCycle.class);

    static {
        for (DayCycle cycle : DayCycle.values()) {
            KEYS.put(cycle, ResourceKey.create(Registries.DIMENSION_TYPE, Universe.id("planet_" + cycle.serializedName())));
        }
    }

    private PlanetDimensionTypes() {
    }

    public static ResourceKey<DimensionType> forCycle(DayCycle cycle) {
        return KEYS.get(cycle);
    }
}
