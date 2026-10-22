package io.github.sweetcornna.universe.core.sentinel;

import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;

/**
 * 守卫塔的位置（ADR-0026）。每颗有守卫机的星球一座，离降落点 {@value #MIN_DISTANCE}–{@value #MAX_DISTANCE} 格、
 * 在世界边界以内，避开兴趣点和传送门。位置只由星球种子（派生键 {@code sentinel_pillar}）和降落点决定，不用生成区块就能算。
 *
 * <p>先找高出海面、平坦、能站人的地面；一圈都找不到（海洋星球）就用第一个不撞东西的位置，塔从海底垫起来。
 */
public final class PillarLayout {
    public static final int MIN_DISTANCE = 400;
    public static final int MAX_DISTANCE = 900;
    /** 塔的占地半径：底座加上终端前的一块空地。 */
    public static final int RADIUS = 6;
    /** 底座范围里的高低差最多多少格。 */
    static final int SLOPE = 5;
    /** 塔离世界边界至少多远。 */
    static final int MARGIN = 32;
    /** 和兴趣点、传送门之间至少隔开多少格（两边的半径以外）。 */
    static final int SPACING = 8;
    private static final int ATTEMPTS = 48;
    /** 兴趣点最大的占地半径加一点余量。 */
    private static final int POI_CLEARANCE = 24;

    private PillarLayout() {
    }

    /** 这颗星球有没有守卫塔：能登陆、有守卫机。 */
    public static boolean has(Planet planet) {
        return planet.landable() && SentinelLevel.of(planet).present();
    }

    /**
     * 这颗星球的守卫塔在哪 {x, z}；没有守卫塔的星球为空。
     *
     * @param landing 降落点 {x, z}（传送门也按它定位置）
     */
    public static Optional<int[]> site(Planet planet, int[] landing) {
        if (!has(planet)) {
            return Optional.empty();
        }
        PoiLayout pois = PoiLayout.of(planet);
        TerrainModel model = TerrainModel.of(planet);
        boolean liquid = planet.liquid() != Liquid.NONE;
        PoiLayout.Ground ground = new PoiLayout.Ground() {
            @Override
            public double height(int x, int z) {
                return model.sample(x, z).height();
            }

            @Override
            public boolean dry(int x, int z) {
                return (!liquid || model.sample(x, z).height() >= model.seaLevel() + 1) && !model.lavaLake(x, z);
            }
        };
        int[] offset = Glyphs.offset(planet.seed());
        int portalX = landing[0] + offset[0];
        int portalZ = landing[1] + offset[1];
        int portalRadius = PortalLayout.PLATFORM_HALF_WIDTH + 4;
        LandmarkLayout.Blocked blocked = (x, z, radius) -> {
            double dx = x - portalX;
            double dz = z - portalZ;
            double reach = radius + portalRadius + SPACING;
            if (dx * dx + dz * dz < reach * reach) {
                return true;
            }
            for (Poi poi : pois.within(x, z, radius + POI_CLEARANCE + SPACING)) {
                if (poi.distance(x, z) < radius + poi.type().radius() + SPACING) {
                    return true;
                }
            }
            return false;
        };
        return Optional.of(site(planet.seed(), landing[0], landing[1], planet.worldSize(), ground, blocked));
    }

    /**
     * 按种子挑位置：一次次随机取方向和距离，第一个在边界里、不撞东西、地面平坦干燥的就是；没有干燥的地面就用第一个
     * 在边界里、不撞东西的；连这样的也没有（降落点贴着边界），就从降落点朝星球中心挪 {@value #MIN_DISTANCE} 格。
     *
     * @param worldSize 世界边界的边长；0 表示不限
     */
    public static int[] site(long planetSeed, int landingX, int landingZ, int worldSize, PoiLayout.Ground ground,
            LandmarkLayout.Blocked blocked) {
        int limit = limit(worldSize);
        Rng rng = Rng.of(Seeds.derive(planetSeed, "sentinel_pillar"));
        int[] fallback = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = rng.range(0, Math.PI * 2);
            double distance = rng.range(MIN_DISTANCE, MAX_DISTANCE);
            int x = landingX + (int) Math.round(Math.cos(angle) * distance);
            int z = landingZ + (int) Math.round(Math.sin(angle) * distance);
            if (Math.abs(x) > limit || Math.abs(z) > limit || blocked.blocked(x, z, RADIUS)) {
                continue;
            }
            if (flatAndDry(ground, x, z)) {
                return new int[] {x, z};
            }
            if (fallback == null) {
                fallback = new int[] {x, z};
            }
        }
        if (fallback != null) {
            return fallback;
        }
        double toCenter = landingX == 0 && landingZ == 0 ? 0 : Math.atan2(-landingZ, -landingX);
        int x = landingX + (int) Math.round(Math.cos(toCenter) * MIN_DISTANCE);
        int z = landingZ + (int) Math.round(Math.sin(toCenter) * MIN_DISTANCE);
        return new int[] {Math.clamp(x, -limit, limit), Math.clamp(z, -limit, limit)};
    }

    /** 塔中心的坐标范围（世界边界往里收 {@value #MARGIN} 格）；不限时为整数最大值。 */
    static int limit(int worldSize) {
        return worldSize <= 0 ? Integer.MAX_VALUE : worldSize / 2 - MARGIN;
    }

    /** 中心和占地范围四角、四边都能站人，高低差不超过 {@value #SLOPE} 格。 */
    static boolean flatAndDry(PoiLayout.Ground ground, int x, int z) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int sx = x + i * RADIUS;
                int sz = z + j * RADIUS;
                if (!ground.dry(sx, sz)) {
                    return false;
                }
                double h = ground.height(sx, sz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min <= SLOPE;
    }

    /** 塔底座那一层的高度估计（区块还没生成时用）：地面的最上一格；有海的星球不低于水面。 */
    public static int estimatedY(Planet planet, int x, int z) {
        TerrainModel model = TerrainModel.of(planet);
        double height = model.sample(x, z).height();
        if (planet.liquid() != Liquid.NONE) {
            height = Math.max(height, model.seaLevel() - 1);
        }
        return (int) Math.floor(height);
    }
}
