package io.github.sweetcornna.universe.core.hazard;

import java.util.EnumSet;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/**
 * 哪里会有哪种局部危害（ADR-0034 §六）。
 *
 * <ul>
 *   <li>间歇泉：冰冻、毒雾、灼热、奇异星球上有水或冰的地方；</li>
 *   <li>火山喷发：灼热星球的火山坡、熔岩海、玄武岩原、灰烬荒原；</li>
 *   <li>孢子云：毒雾星球的毒沼、菌林、异形密林；</li>
 *   <li>尘卷风：有大气、有风的星球上的荒漠、尘埃盆地、硫磺平原、方山台地、峡谷、风化平原。</li>
 * </ul>
 */
public final class HazardSites {
    public static final Set<BiomeKind> VOLCANIC = EnumSet.of(BiomeKind.VOLCANIC, BiomeKind.LAVA_SEA, BiomeKind.BASALT_FIELDS,
            BiomeKind.ASH_WASTES);
    public static final Set<BiomeKind> SPORES = EnumSet.of(BiomeKind.TOXIC_MARSH, BiomeKind.FUNGAL_FOREST, BiomeKind.ALIEN_THICKET);
    public static final Set<BiomeKind> DUSTY = EnumSet.of(BiomeKind.DESERT, BiomeKind.DUST_BASIN, BiomeKind.SULFUR_FLATS, BiomeKind.MESA,
            BiomeKind.CANYON, BiomeKind.REGOLITH_PLAINS);
    /** 星球上没有成片的水时，这些地方地下也有水或冰：冰原、冻土、湿地。 */
    public static final Set<BiomeKind> WET = EnumSet.of(BiomeKind.GLACIER, BiomeKind.SNOWFIELD, BiomeKind.TUNDRA, BiomeKind.ALPINE,
            BiomeKind.FROZEN_OCEAN, BiomeKind.WETLAND, BiomeKind.TOXIC_MARSH, BiomeKind.RIVER, BiomeKind.BEACH);
    /** 风力到这个程度才起尘卷风。 */
    public static final double MIN_WIND = 0.15;

    private HazardSites() {
    }

    public static boolean geyser(PlanetType type, Liquid liquid, BiomeKind biome) {
        if (GeyserKind.of(type).isEmpty() || biome == null) {
            return false;
        }
        // 海面上、熔岩海、酸海里不冒
        if (biome == BiomeKind.OCEAN || biome == BiomeKind.DEEP_OCEAN || biome == BiomeKind.LAVA_SEA || biome == BiomeKind.ACID_SEA) {
            return false;
        }
        return liquid == Liquid.WATER || liquid == Liquid.ICE || WET.contains(biome);
    }

    public static boolean eruption(PlanetType type, BiomeKind biome) {
        return type == PlanetType.SCORCHED && VOLCANIC.contains(biome);
    }

    public static boolean sporeCloud(PlanetType type, BiomeKind biome) {
        return type == PlanetType.TOXIC && SPORES.contains(biome);
    }

    public static boolean dustDevil(boolean atmosphere, double wind, BiomeKind biome) {
        return atmosphere && wind >= MIN_WIND && DUSTY.contains(biome);
    }

    /** 这里会不会发生这种危害（间歇泉看 {@link #geyser}）。 */
    public static boolean allows(HazardKind kind, PlanetType type, boolean atmosphere, double wind, Liquid liquid, BiomeKind biome) {
        if (biome == null) {
            return false;
        }
        return switch (kind) {
            case GEYSER -> geyser(type, liquid, biome);
            case ERUPTION -> eruption(type, biome);
            case SPORE_CLOUD -> sporeCloud(type, biome);
            case DUST_DEVIL -> dustDevil(atmosphere, wind, biome);
        };
    }
}
