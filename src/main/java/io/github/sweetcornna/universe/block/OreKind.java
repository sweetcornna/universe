package io.github.sweetcornna.universe.block;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Resource;

/**
 * 星球矿石（玩法设计 §9.1）：星球资源里每种能用镐挖的固体资源一种，按星球的资源丰度生成。方块的底色是按星球
 * 染色的星岩，矿点不染色，所以同一种矿在不同星球上底色不同，矿点一眼认得出。
 *
 * <p>铁、铜、煤掉原版的物品，其余掉本模组的物品；"粗矿"要再烧成锭。
 */
public enum OreKind {
    IRON(Resource.IRON, "planet_iron_ore", 1, 0, "minecraft:raw_iron", 1, 1, 0, 0, null, 1.0, 6),
    COPPER(Resource.COPPER, "planet_copper_ore", 1, 0, "minecraft:raw_copper", 2, 5, 0, 0, null, 0.8, 7),
    COAL(Resource.COAL, "planet_coal_ore", 0, 0, "minecraft:coal", 1, 1, 0, 2, null, 1.0, 8),
    TITANIUM(Resource.TITANIUM, "titanium_ore", 2, 0, "raw_titanium", 1, 1, 0, 0, "titanium_ingot", 0.8, 6),
    SILICON(Resource.SILICON, "silicon_ore", 1, 0, "raw_silicon", 1, 1, 0, 0, "silicon", 0.7, 6),
    SULFUR(Resource.SULFUR, "sulfur_ore", 1, 0, "sulfur", 2, 4, 1, 3, null, 0.8, 7),
    FIRE_ICE(Resource.FIRE_ICE, "fire_ice_ore", 1, 3, "fire_ice", 1, 2, 1, 3, null, 0.6, 6),
    OPTICAL_CRYSTAL(Resource.OPTICAL_CRYSTAL, "optical_crystal_ore", 2, 5, "optical_crystal", 1, 2, 3, 7, null, 0.35, 4),
    ORGANIC_CRYSTAL(Resource.ORGANIC_CRYSTAL, "organic_crystal_ore", 1, 2, "organic_crystal", 1, 2, 2, 5, null, 0.45, 5),
    URANIUM(Resource.URANIUM, "uranium_ore", 2, 4, "raw_uranium", 1, 1, 0, 0, "uranium_ingot", 0.4, 5),
    SALT(Resource.SALT, "salt_ore", 0, 0, "salt", 2, 4, 0, 1, null, 0.8, 8),
    MAGNETIC(Resource.MAGNETIC_ORE, "magnetic_ore", 2, 0, "raw_magnetite", 1, 1, 0, 0, "magnet", 0.4, 5),
    STRANGE_MATTER(Resource.STRANGE_MATTER, "strange_matter_ore", 3, 7, "strange_matter", 1, 1, 5, 10, null, 0.2, 3);

    private final Resource resource;
    private final String blockName;
    private final int toolTier;
    private final int light;
    private final String drop;
    private final int minDrops;
    private final int maxDrops;
    private final int minXp;
    private final int maxXp;
    private final @Nullable String smelted;
    private final double frequency;
    private final int veinSize;

    OreKind(Resource resource, String blockName, int toolTier, int light, String drop, int minDrops, int maxDrops,
            int minXp, int maxXp, @Nullable String smelted, double frequency, int veinSize) {
        this.resource = resource;
        this.blockName = blockName;
        this.toolTier = toolTier;
        this.light = light;
        this.drop = drop;
        this.minDrops = minDrops;
        this.maxDrops = maxDrops;
        this.minXp = minXp;
        this.maxXp = maxXp;
        this.smelted = smelted;
        this.frequency = frequency;
        this.veinSize = veinSize;
    }

    public Resource resource() {
        return this.resource;
    }

    public String blockName() {
        return this.blockName;
    }

    /** 至少要什么镐：0 木、1 石、2 铁、3 钻石。 */
    public int toolTier() {
        return this.toolTier;
    }

    /** 矿石发的光（晶体、放射性矿会发光）。 */
    public int light() {
        return this.light;
    }

    /** 掉落物：带 {@code minecraft:} 前缀的是原版物品，否则是本模组的物品名。 */
    public String drop() {
        return this.drop;
    }

    public boolean vanillaDrop() {
        return this.drop.startsWith("minecraft:");
    }

    public int minDrops() {
        return this.minDrops;
    }

    public int maxDrops() {
        return this.maxDrops;
    }

    public int minXp() {
        return this.minXp;
    }

    public int maxXp() {
        return this.maxXp;
    }

    /** 粗矿烧出来的东西；直接掉成品的矿为空。 */
    public @Nullable String smelted() {
        return this.smelted;
    }

    /** 相对常见程度：丰度相同时，每个区块的矿脉数按它缩放。 */
    public double frequency() {
        return this.frequency;
    }

    /** 一条矿脉的基础大小（方块数），丰度越高越大。 */
    public int veinSize() {
        return this.veinSize;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** 这种资源对应的矿石；液体、气体资源没有。 */
    public static @Nullable OreKind of(Resource resource) {
        for (OreKind kind : values()) {
            if (kind.resource == resource) {
                return kind;
            }
        }
        return null;
    }
}
