package io.github.sweetcornna.universe.world.planet.gen;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** 星球群系的注册键，一个 {@link BiomeKind} 对应一个 {@code universe:<名称>} 群系（数据生成里定义）。 */
public final class PlanetBiomes {
    private static final Map<BiomeKind, ResourceKey<Biome>> KEYS = new EnumMap<>(BiomeKind.class);

    static {
        for (BiomeKind kind : BiomeKind.values()) {
            KEYS.put(kind, ResourceKey.create(Registries.BIOME, Universe.id(kind.serializedName())));
        }
    }

    private PlanetBiomes() {
    }

    public static ResourceKey<Biome> key(BiomeKind kind) {
        return KEYS.get(kind);
    }
}
