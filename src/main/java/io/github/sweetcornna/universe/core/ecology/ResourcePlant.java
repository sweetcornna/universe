package io.github.sweetcornna.universe.core.ecology;

import java.util.Optional;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 能采的资源植物（ADR-0026）。和装饰用的 {@link PlantForm} 分开：每种是一个固定的方块，长成以后收获，
 * 收过的过一会儿重新长出来。顺序是存档格式的一部分（方块按它注册），只往后加。
 *
 * @param id 方块和物品的注册名
 * @param product 收获得到的物品的注册名
 * @param habitat 长在哪里
 * @param trayMinutes 在水培槽里多少分钟（游戏时间）一熟；0 表示不能种在槽里
 * @param wildMin 野外收一次最少得几个
 * @param wildMax 野外收一次最多得几个
 */
public enum ResourcePlant {
    OXYGEN_BLADDER("oxygen_bladderwort", "oxygen", "氧囊草", "Oxygen Bladderwort", Habitat.SOIL, 3, 2, 4),
    SALTWORT("saltwort", "sodium", "盐角草", "Saltwort", Habitat.SOIL, 5, 2, 4),
    SALT_CRYSTAL("salt_crystal_cluster", "sodium", "盐晶簇", "Salt Crystal Cluster", Habitat.ROCK, 0, 3, 5),
    STAR_BRAMBLE("star_bramble", "star_bulb", "星莓丛", "Star Bramble", Habitat.SOIL, 10, 1, 3),
    FROSTWORT("frostwort", "frost_crystal", "霜苔", "Frostwort", Habitat.SOIL, 5, 1, 3),
    SOLAR_VINE("solar_vine", "solanium", "日焰藤", "Solar Vine", Habitat.SOIL, 20, 1, 3),
    GAMMA_WEED("gamma_weed", "gamma_root", "伽马草", "Gamma Weed", Habitat.SOIL, 10, 1, 3),
    FUNGAL_CLUSTER("fungal_cluster", "fungal_mould", "毒霉簇", "Fungal Cluster", Habitat.SOIL, 10, 1, 3),
    ECHINOCACTUS("echinocactus", "cactus_flesh", "桶形仙人掌", "Echinocactus", Habitat.SOIL, 20, 1, 3),
    GRAVITINO_HOST("gravitino_host", "gravitino_ball", "引力果丛", "Gravitino Host", Habitat.SOIL, 30, 1, 1),
    CAVE_MARROW("cave_marrow", "marrow_bulb", "洞穴髓球", "Cave Marrow", Habitat.CAVE, 0, 1, 2);

    /** 野外收过以后平均多久重新长出来（刻，15 分钟）。 */
    public static final int REGROW_TICKS = 15 * 60 * 20;
    /** 水培槽一次收几个。 */
    public static final int TRAY_MIN = 4;
    public static final int TRAY_MAX = 8;

    /** 长在哪里。 */
    public enum Habitat {
        /** 地表，下面是能长植物的土。 */
        SOIL,
        /** 地表的岩石、沙子上（矿物）。 */
        ROCK,
        /** 洞穴的地面。 */
        CAVE
    }

    private final String id;
    private final String product;
    private final String nameZh;
    private final String nameEn;
    private final Habitat habitat;
    private final int trayMinutes;
    private final int wildMin;
    private final int wildMax;

    ResourcePlant(String id, String product, String nameZh, String nameEn, Habitat habitat, int trayMinutes, int wildMin, int wildMax) {
        this.id = id;
        this.product = product;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.habitat = habitat;
        this.trayMinutes = trayMinutes;
        this.wildMin = wildMin;
        this.wildMax = wildMax;
    }

    public String id() {
        return this.id;
    }

    public String product() {
        return this.product;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public Habitat habitat() {
        return this.habitat;
    }

    /** 能不能种在水培槽里。 */
    public boolean farmable() {
        return this.trayMinutes > 0;
    }

    /** 在水培槽里长熟要几刻。 */
    public int trayTicks() {
        return this.trayMinutes * 60 * 20;
    }

    /** 野外收一次得几个。 */
    public int wildMin() {
        return this.wildMin;
    }

    public int wildMax() {
        return this.wildMax;
    }

    public int wildYield(Rng rng) {
        return rng.range(this.wildMin, this.wildMax);
    }

    /** 水培槽收一次得几个；引力果只结一两个。 */
    public int trayYield(Rng rng) {
        return this == GRAVITINO_HOST ? rng.range(1, 2) : rng.range(TRAY_MIN, TRAY_MAX);
    }

    /**
     * 收过的植物一次随机刻里重新长出来的机会。随机刻速度 3 时每格平均 1365 刻轮到一次，
     * 这样平均 {@link #REGROW_TICKS} 后长回来。
     */
    public static double regrowChance(int randomTickSpeed) {
        if (randomTickSpeed <= 0) {
            return 0;
        }
        double interval = 4096.0 / randomTickSpeed;
        return Math.min(1, interval / REGROW_TICKS);
    }

    /** 产物能种回去的植物（钠种出来是盐角草）；不能种的为空。 */
    public static Optional<ResourcePlant> fromProduct(String product) {
        for (ResourcePlant plant : values()) {
            if (plant.product.equals(product) && plant.habitat != Habitat.ROCK) {
                return Optional.of(plant);
            }
        }
        return Optional.empty();
    }
}
