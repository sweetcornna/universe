package io.github.sweetcornna.universe.core.ecology;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 资源植物在一颗星球上的分布（ADR-0026）。按 {@link #CELL} 格的网格，每格有一定机会长一丛（同一种的 3–7 株），
 * 用自己的派生键 {@code resource_flora}，不碰装饰植物和树的布局。
 *
 * <p>每丛只决定位置和一个随机数，种哪一种由放置时那一格的生境决定（{@link #pick}）：适配层知道群系，
 * 这里不知道。
 */
public final class ResourceFlora {
    /** 网格边长（格）。 */
    public static final int CELL = 20;
    /** 一丛离中心最远几格。 */
    static final int PATCH_RADIUS = 3;
    /** 有植被的星球每格长一丛的机会。 */
    static final double PATCH_CHANCE = 0.45;
    /** 没有植被的星球（只有盐晶簇）。 */
    static final double LIFELESS_CHANCE = 0.3;
    /** 每个区块试几次找洞穴的地面放髓球。 */
    static final int CAVE_TRIES = 6;
    /** 每个区块有洞穴髓球的机会。 */
    static final double CAVE_CHANCE = 0.5;

    /** 一株的位置；{@code roll} 是这一丛共用的随机数，挑种类用。 */
    public record Spot(int x, int z, double roll) {
    }

    private final long seed;
    private final PlanetType type;
    private final boolean lifeless;

    public ResourceFlora(long planetSeed, PlanetType type, boolean lifeless) {
        this.seed = Seeds.derive(planetSeed, "resource_flora");
        this.type = type;
        this.lifeless = lifeless;
    }

    /** 海底每个区块长一丛烛光海带的机会。 */
    static final double KELP_CHANCE = 0.55;
    /** 海底每个区块有甲壳蚌的机会。 */
    static final double CLAM_CHANCE = 0.12;

    /** 有海、有生命的星球海底长烛光海带、有甲壳蚌（ADR-0026）。 */
    public static boolean seabedLife(Planet planet) {
        return planet.landable() && !planet.ecology().lifeless() && Fauna.hasSea(planet.liquid());
    }

    /** 这类星球的招牌植物；海洋星球的是海底的烛光海带（{@link #seabedSpots}），陆地上没有。 */
    public static Optional<ResourcePlant> signature(PlanetType type) {
        return Optional.ofNullable(switch (type) {
            case HABITABLE -> ResourcePlant.STAR_BRAMBLE;
            case FROZEN -> ResourcePlant.FROSTWORT;
            case SCORCHED -> ResourcePlant.SOLAR_VINE;
            case IRRADIATED -> ResourcePlant.GAMMA_WEED;
            case TOXIC -> ResourcePlant.FUNGAL_CLUSTER;
            case BARREN -> ResourcePlant.ECHINOCACTUS;
            case EXOTIC -> ResourcePlant.GRAVITINO_HOST;
            case OCEAN, GAS_GIANT -> null;
        });
    }

    /** 这颗星球上会长的资源植物（地表和洞穴）。 */
    public static List<ResourcePlant> plants(PlanetType type, boolean lifeless) {
        List<ResourcePlant> plants = new ArrayList<>();
        if (!type.landable()) {
            return plants;
        }
        if (lifeless) {
            plants.add(ResourcePlant.SALT_CRYSTAL);
            return plants;
        }
        plants.add(ResourcePlant.OXYGEN_BLADDER);
        plants.add(ResourcePlant.SALTWORT);
        signature(type).ifPresent(plants::add);
        // 干旱的地方另外长仙人掌、地表有盐壳
        if (!plants.contains(ResourcePlant.ECHINOCACTUS) && Biotope.forPlanet(type).contains(Biotope.ARID)) {
            plants.add(ResourcePlant.ECHINOCACTUS);
        }
        if (Biotope.forPlanet(type).contains(Biotope.ARID)) {
            plants.add(ResourcePlant.SALT_CRYSTAL);
        }
        plants.add(ResourcePlant.CAVE_MARROW);
        return plants;
    }

    /** 这颗星球上能不能把 {@code plant} 种在地里（水培槽不受限制）。 */
    public static boolean grows(ResourcePlant plant, PlanetType type, boolean lifeless) {
        return plant.habitat() == ResourcePlant.Habitat.SOIL && plants(type, lifeless).contains(plant);
    }

    /** 落在这个区块里的地表植株，按位置排好。 */
    public List<Spot> spots(int chunkX, int chunkZ) {
        List<Spot> spots = new ArrayList<>();
        if (!this.type.landable()) {
            return spots;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int cellMinX = Math.floorDiv(x0 - PATCH_RADIUS, CELL);
        int cellMaxX = Math.floorDiv(x0 + 15 + PATCH_RADIUS, CELL);
        int cellMinZ = Math.floorDiv(z0 - PATCH_RADIUS, CELL);
        int cellMaxZ = Math.floorDiv(z0 + 15 + PATCH_RADIUS, CELL);
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                Rng rng = Rng.of(Seeds.derive(this.seed, ((long) cx << 32) ^ (cz & 0xFFFFFFFFL)));
                if (!rng.chance(this.lifeless ? LIFELESS_CHANCE : PATCH_CHANCE)) {
                    continue;
                }
                int centerX = cx * CELL + rng.range(PATCH_RADIUS, CELL - 1 - PATCH_RADIUS);
                int centerZ = cz * CELL + rng.range(PATCH_RADIUS, CELL - 1 - PATCH_RADIUS);
                double roll = rng.nextDouble();
                int count = rng.range(3, 7);
                for (int i = 0; i < count; i++) {
                    int x = centerX + rng.range(-PATCH_RADIUS, PATCH_RADIUS);
                    int z = centerZ + rng.range(-PATCH_RADIUS, PATCH_RADIUS);
                    if (x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16) {
                        spots.add(new Spot(x, z, roll));
                    }
                }
            }
        }
        return spots;
    }

    /**
     * 这一丛种哪一种。{@code biotope} 是那一格的生境，水下、冰川、岩浆海这些为空；
     * {@code rock} 表示脚下是岩石、风化层、沙子这些矿物地面而不是土：没有植被的星球上只有它长盐晶簇，
     * 干旱地带的矿物地面上长盐晶簇和仙人掌，别处的矿物地面和土一样。返回空表示这里不长。
     */
    public @Nullable ResourcePlant pick(@Nullable Biotope biotope, boolean rock, double roll) {
        if (!this.type.landable()) {
            return null;
        }
        if (this.lifeless) {
            return rock ? ResourcePlant.SALT_CRYSTAL : null;
        }
        if (biotope == null) {
            return null;
        }
        List<ResourcePlant> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        if (rock && biotope == Biotope.ARID) {
            add(options, weights, ResourcePlant.SALT_CRYSTAL, 3);
            add(options, weights, ResourcePlant.ECHINOCACTUS, 2);
            return choose(options, weights, roll);
        }
        add(options, weights, ResourcePlant.OXYGEN_BLADDER, 3);
        add(options, weights, ResourcePlant.SALTWORT, biotope == Biotope.WETLAND || biotope == Biotope.ARID ? 4 : 2);
        signature(this.type).ifPresent(plant -> add(options, weights, plant, 2.5));
        if (biotope == Biotope.ARID && signature(this.type).orElse(null) != ResourcePlant.ECHINOCACTUS) {
            add(options, weights, ResourcePlant.ECHINOCACTUS, 2);
        }
        return choose(options, weights, roll);
    }

    private static ResourcePlant choose(List<ResourcePlant> options, List<Double> weights, double roll) {
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        double target = roll * total;
        for (int i = 0; i < options.size(); i++) {
            target -= weights.get(i);
            if (target < 0) {
                return options.get(i);
            }
        }
        return options.getLast();
    }

    private static void add(List<ResourcePlant> options, List<Double> weights, ResourcePlant plant, double weight) {
        options.add(plant);
        weights.add(weight);
    }

    /**
     * 落在这个区块里的烛光海带（一丛 3–7 株，按区块定有没有）。适配层在每列找海底，只种在水里。
     * 用自己的派生键 {@code sea}，不动地上的植物。没有海、没有生命的星球不要调。
     */
    public List<Spot> seabedSpots(int chunkX, int chunkZ) {
        List<Spot> spots = new ArrayList<>();
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(this.seed, "sea"), ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL)));
        if (!rng.chance(KELP_CHANCE)) {
            return spots;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int centerX = x0 + rng.range(PATCH_RADIUS, 15 - PATCH_RADIUS);
        int centerZ = z0 + rng.range(PATCH_RADIUS, 15 - PATCH_RADIUS);
        int count = rng.range(3, 7);
        for (int i = 0; i < count; i++) {
            spots.add(new Spot(centerX + rng.range(-PATCH_RADIUS, PATCH_RADIUS), centerZ + rng.range(-PATCH_RADIUS, PATCH_RADIUS), 0));
        }
        return spots;
    }

    /** 这个区块里的甲壳蚌（0–2 个），派生键 {@code clam}。适配层只放在够深的海底。 */
    public List<Spot> clamSpots(int chunkX, int chunkZ) {
        List<Spot> spots = new ArrayList<>();
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(this.seed, "clam"), ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL)));
        if (!rng.chance(CLAM_CHANCE)) {
            return spots;
        }
        int count = rng.chance(0.3) ? 2 : 1;
        for (int i = 0; i < count; i++) {
            spots.add(new Spot((chunkX << 4) + rng.nextInt(16), (chunkZ << 4) + rng.nextInt(16), rng.nextDouble()));
        }
        return spots;
    }

    /**
     * 这个区块里试着放洞穴髓球的列（{@code x, z}，世界坐标）和每列的一个随机数；适配层在列里往下找洞穴的地面。
     * 没有植被的星球洞里也没有。
     */
    public List<Spot> caveColumns(int chunkX, int chunkZ) {
        List<Spot> columns = new ArrayList<>();
        if (this.lifeless || !this.type.landable()) {
            return columns;
        }
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(this.seed, "cave"), ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL)));
        if (!rng.chance(CAVE_CHANCE)) {
            return columns;
        }
        for (int i = 0; i < CAVE_TRIES; i++) {
            columns.add(new Spot((chunkX << 4) + rng.nextInt(16), (chunkZ << 4) + rng.nextInt(16), rng.nextDouble()));
        }
        return columns;
    }
}
