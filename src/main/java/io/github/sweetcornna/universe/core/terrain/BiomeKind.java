package io.github.sweetcornna.universe.core.terrain;

import java.util.Locale;

/**
 * 星球群系。所有星球共用这一套群系，每颗星球按类型取其中一部分（{@link BiomeLayout}）。
 * 颜色来自星球调色板而不是群系，所以同一种群系在不同星球上看起来并不一样。
 *
 * <p>{@code temperature} 和 {@code downfall} 直接用作原版群系的气候参数：温度低于 0.15 时下雪、水面结冰。
 * 群系 ID 是 {@code universe:<serializedName>}，写进区块数据，改名要附带迁移。
 */
public enum BiomeKind {
    GRASSLAND("星原", "Grassland", 0.8F, 0.4F, true, Surface.TURF, Surface.SOIL, Surface.SAND),
    FOREST("林地", "Woodland", 0.7F, 0.8F, true, Surface.TURF, Surface.SOIL, Surface.SAND),
    JUNGLE("雨林", "Rainforest", 0.95F, 0.9F, true, Surface.TURF, Surface.SOIL, Surface.SAND),
    WETLAND("湿地", "Wetland", 0.8F, 0.9F, true, Surface.TURF, Surface.SOIL, Surface.SOIL),
    HIGHLANDS("高地", "Highlands", 0.5F, 0.4F, true, Surface.TURF, Surface.SOIL, Surface.SOIL),
    ALPINE("雪峰", "Alpine Peaks", -0.5F, 0.4F, true, Surface.SNOW, Surface.ROCK, Surface.ROCK),
    TUNDRA("冻原", "Tundra", -0.3F, 0.4F, true, Surface.SNOW, Surface.SOIL, Surface.SAND),
    DESERT("荒漠", "Desert", 2.0F, 0.0F, false, Surface.SAND, Surface.SAND, Surface.SAND),
    BEACH("海滩", "Beach", 0.8F, 0.4F, true, Surface.SAND, Surface.SAND, Surface.SAND),
    RIVER("河谷", "River Valley", 0.6F, 0.6F, true, Surface.TURF, Surface.SOIL, Surface.SAND),
    OCEAN("海洋", "Ocean", 0.5F, 0.5F, true, Surface.SAND, Surface.SAND, Surface.SAND),
    DEEP_OCEAN("深海", "Deep Ocean", 0.5F, 0.5F, true, Surface.REGOLITH, Surface.REGOLITH, Surface.REGOLITH),
    FROZEN_OCEAN("冰洋", "Frozen Ocean", -0.5F, 0.5F, true, Surface.SAND, Surface.SAND, Surface.SAND),
    SNOWFIELD("雪原", "Snowfield", -0.7F, 0.5F, true, Surface.SNOW, Surface.SNOW, Surface.ROCK),
    GLACIER("冰川", "Glacier", -1.0F, 0.5F, true, Surface.ICE, Surface.ICE, Surface.ICE),
    ASH_WASTES("灰烬荒原", "Ash Wastes", 2.0F, 0.0F, false, Surface.ASH, Surface.ASH, Surface.ASH),
    BASALT_FIELDS("玄武岩原", "Basalt Fields", 2.0F, 0.0F, false, Surface.ROCK, Surface.ROCK, Surface.ROCK),
    LAVA_SEA("熔岩海", "Lava Sea", 2.0F, 0.0F, false, Surface.ASH, Surface.ROCK, Surface.ROCK),
    REGOLITH_PLAINS("风化平原", "Regolith Plains", 0.5F, 0.0F, false, Surface.REGOLITH, Surface.REGOLITH, Surface.REGOLITH),
    CRATERED_HIGHLANDS("环形山高地", "Cratered Highlands", 0.5F, 0.0F, false, Surface.REGOLITH, Surface.ROCK, Surface.ROCK),
    DUST_BASIN("尘埃盆地", "Dust Basin", 0.5F, 0.0F, false, Surface.SAND, Surface.SAND, Surface.SAND),
    TOXIC_MARSH("毒沼", "Toxic Marsh", 0.9F, 0.9F, true, Surface.SOIL, Surface.SOIL, Surface.SOIL),
    FUNGAL_FOREST("菌林", "Fungal Forest", 0.9F, 0.8F, true, Surface.TURF, Surface.SOIL, Surface.SOIL),
    ACID_SEA("酸海", "Acid Sea", 0.9F, 0.9F, true, Surface.SOIL, Surface.SOIL, Surface.SOIL),
    SULFUR_FLATS("硫磺平原", "Sulfur Flats", 1.5F, 0.0F, false, Surface.SAND, Surface.SAND, Surface.SAND),
    RADIANT_WASTES("辐射荒原", "Radiant Wastes", 1.0F, 0.0F, false, Surface.REGOLITH, Surface.REGOLITH, Surface.REGOLITH),
    CRYSTAL_FIELDS("晶簇原", "Crystal Fields", 0.6F, 0.2F, false, Surface.REGOLITH, Surface.REGOLITH, Surface.REGOLITH),
    ALIEN_THICKET("异形密林", "Alien Thicket", 0.8F, 0.8F, true, Surface.TURF, Surface.SOIL, Surface.SOIL),
    VOLCANIC("火山坡", "Volcanic Slopes", 1.2F, 0.1F, false, Surface.ASH, Surface.ROCK, Surface.ROCK),
    MESA("方山台地", "Mesa", 1.6F, 0.1F, false, Surface.SAND, Surface.STRATA, Surface.SAND),
    CANYON("峡谷", "Canyon", 1.4F, 0.2F, false, Surface.SAND, Surface.STRATA, Surface.SAND);

    private final String nameZh;
    private final String nameEn;
    private final float temperature;
    private final float downfall;
    private final boolean precipitation;
    private final Surface top;
    private final Surface filler;
    private final Surface underwater;

    BiomeKind(String nameZh, String nameEn, float temperature, float downfall, boolean precipitation,
            Surface top, Surface filler, Surface underwater) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.temperature = temperature;
        this.downfall = downfall;
        this.precipitation = precipitation;
        this.top = top;
        this.filler = filler;
        this.underwater = underwater;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public float temperature() {
        return this.temperature;
    }

    public float downfall() {
        return this.downfall;
    }

    public boolean precipitation() {
        return this.precipitation;
    }

    /** 露天地面的表层方块。 */
    public Surface top() {
        return this.top;
    }

    /** 表层之下几格的填充方块。 */
    public Surface filler() {
        return this.filler;
    }

    /** 水（或别的液体）底下的地面。 */
    public Surface underwater() {
        return this.underwater;
    }

    /** 是否冷到会下雪、水面结冰。阈值和原版一样是 0.15。 */
    public boolean cold() {
        return this.temperature < 0.15F;
    }

    /** 崖壁和地下是彩色岩层的群系。 */
    public boolean stratified() {
        return this == MESA || this == CANYON;
    }

    /** 海洋类群系：地表在液面以下。 */
    public boolean ocean() {
        return this == OCEAN || this == DEEP_OCEAN || this == FROZEN_OCEAN || this == LAVA_SEA || this == ACID_SEA;
    }
}
