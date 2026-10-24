package io.github.sweetcornna.universe.core.fishing;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/**
 * 钓鱼的水域（ADR-0034 §一）：8 种，每种 12 种鱼。按浮标那一格的星球类型、群系、液体、看不看得到天、水深来分。
 *
 * <p>{@code color} 是这种水的颜色，图鉴的格子和水下的鱼影用它压暗。
 */
public enum Habitat {
    FRESH("淡水", "Freshwater", 0x3E7F9C),
    WARM("暖水", "Warm Water", 0x2F8C78),
    COASTAL("近海", "Coastal", 0x2C6FA8),
    DEEP("深海", "Deep Sea", 0x1B3570),
    ICE("冰下", "Under Ice", 0x7FB4CC),
    ACID("酸水", "Acid Water", 0x8C9A2A),
    CAVE("暗河", "Underground River", 0x46525C),
    EXOTIC("奇异", "Exotic Water", 0x7A4AA0);

    /** 海洋里水深到这么多格算深海。 */
    public static final int DEEP_SEA_DEPTH = 12;

    /** 主世界原版群系归成几类（适配层按群系标签分）。 */
    public enum Vanilla {
        OCEAN,
        DEEP_OCEAN,
        RIVER,
        /** 冷的：雪原、冰原、冻洋、冷水海洋。 */
        COLD,
        /** 热的：丛林、红树林沼泽、热带草原、沙漠、恶地。 */
        HOT,
        OTHER
    }

    private final String nameZh;
    private final String nameEn;
    private final int color;

    Habitat(String nameZh, String nameEn, int color) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.color = color;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public int color() {
        return this.color;
    }

    /** 语言键里用的名字。 */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 浮标那一格属于哪种水域。
     *
     * @param planet  星球的类型；主世界传 null，看 {@code vanilla}
     * @param biome   星球群系；主世界或者不是星球群系时传 null
     * @param vanilla 主世界的群系归成哪一类；星球上传 null
     * @param acid    浮标泡在酸液里
     * @param sky     浮标那一格看得到天
     * @param depth   浮标往下数有几格水
     */
    public static Habitat of(@Nullable PlanetType planet, @Nullable BiomeKind biome, @Nullable Vanilla vanilla, boolean acid, boolean sky,
            int depth) {
        if (!sky) {
            return CAVE;
        }
        if (acid) {
            return ACID;
        }
        if (vanilla != null) {
            return switch (vanilla) {
                case COLD -> ICE;
                case DEEP_OCEAN -> DEEP;
                case OCEAN -> depth >= DEEP_SEA_DEPTH ? DEEP : COASTAL;
                case HOT -> WARM;
                case RIVER, OTHER -> FRESH;
            };
        }
        if (planet != null) {
            switch (planet) {
                case EXOTIC, IRRADIATED -> {
                    return EXOTIC;
                }
                case TOXIC -> {
                    return ACID;
                }
                case FROZEN -> {
                    return ICE;
                }
                // 灼热星球上的水只有温泉
                case SCORCHED -> {
                    return WARM;
                }
                default -> {
                }
            }
        }
        if (biome == null) {
            return FRESH;
        }
        return switch (biome) {
            case ACID_SEA, TOXIC_MARSH -> ACID;
            case FROZEN_OCEAN, TUNDRA, SNOWFIELD, GLACIER, ALPINE -> ICE;
            case DEEP_OCEAN -> DEEP;
            case OCEAN, BEACH -> depth >= DEEP_SEA_DEPTH ? DEEP : COASTAL;
            case JUNGLE, FUNGAL_FOREST, ALIEN_THICKET, DESERT, ASH_WASTES, BASALT_FIELDS, LAVA_SEA, VOLCANIC, SULFUR_FLATS, MESA, CANYON -> WARM;
            case CRYSTAL_FIELDS, RADIANT_WASTES -> EXOTIC;
            default -> FRESH;
        };
    }
}
