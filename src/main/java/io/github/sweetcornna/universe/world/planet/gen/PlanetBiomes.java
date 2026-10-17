package io.github.sweetcornna.universe.world.planet.gen;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** 星球群系的注册键，一个 {@link BiomeKind} 对应一个 {@code universe:<名称>} 群系（数据生成里定义）。 */
public final class PlanetBiomes {
    private static final Map<BiomeKind, ResourceKey<Biome>> KEYS = new EnumMap<>(BiomeKind.class);
    private static final Map<ResourceKey<Biome>, BiomeKind> KINDS = new HashMap<>();

    static {
        for (BiomeKind kind : BiomeKind.values()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Universe.id(kind.serializedName()));
            KEYS.put(kind, key);
            KINDS.put(key, kind);
        }
    }

    private PlanetBiomes() {
    }

    public static ResourceKey<Biome> key(BiomeKind kind) {
        return KEYS.get(kind);
    }

    /** 群系对应的星球群系种类；不是星球群系时返回 null。 */
    public static BiomeKind kind(Holder<Biome> biome) {
        return biome.unwrapKey().map(KINDS::get).orElse(null);
    }
}
