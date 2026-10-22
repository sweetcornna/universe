package io.github.sweetcornna.universe.poi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.Poi;
import io.github.sweetcornna.universe.core.poi.PoiLayout;
import io.github.sweetcornna.universe.core.portal.Glyphs;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.world.planet.PlanetCommand;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 地标在服务端的部分（ADR-0026）：每颗星球的分布。地标给兴趣点和传送门让路：附近有兴趣点、传送门的位置不放。
 */
public final class Landmarks {
    /** 兴趣点最大的占地半径加一点余量。 */
    private static final int POI_CLEARANCE = 24;
    private static final Map<Long, LandmarkLayout> LAYOUTS = new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, LandmarkLayout> eldest) {
            return this.size() > 8;
        }
    };

    private Landmarks() {
    }

    /** 星球上地标的分布；不是星球维度、不能登陆的星球没有。可以在世界生成的线程里调用。 */
    public static Optional<LandmarkLayout> layout(Planet planet) {
        if (!planet.landable()) {
            return Optional.empty();
        }
        synchronized (LAYOUTS) {
            return Optional.of(LAYOUTS.computeIfAbsent(planet.seed(), seed -> create(planet)));
        }
    }

    public static Optional<LandmarkLayout> layout(ServerLevel level) {
        return Planets.planet(level).flatMap(Landmarks::layout);
    }

    private static LandmarkLayout create(Planet planet) {
        Optional<PoiLayout> pois = Pois.layout(planet);
        // 传送门在着陆点旁边，按和 Portals 一样的算法定位置
        int[] landing = PlanetCommand.landingColumn(planet);
        int[] offset = Glyphs.offset(planet.seed());
        int portalX = landing[0] + offset[0];
        int portalZ = landing[1] + offset[1];
        int portalRadius = PortalLayout.PLATFORM_HALF_WIDTH + 4;
        LandmarkLayout.Blocked blocked = (x, z, radius) -> {
            double dx = x - portalX;
            double dz = z - portalZ;
            double reach = radius + portalRadius;
            if (dx * dx + dz * dz < reach * reach) {
                return true;
            }
            if (pois.isEmpty()) {
                return false;
            }
            for (Poi poi : pois.get().within(x, z, radius + POI_CLEARANCE)) {
                if (poi.distance(x, z) < radius + poi.type().radius() + 6) {
                    return true;
                }
            }
            return false;
        };
        return LandmarkLayout.of(planet, blocked);
    }

    /** 地标中心的地面高度：区块已经加载时取真实的海底，否则用地形模型估计。 */
    public static int groundY(ServerLevel level, LandmarkLayout layout, Landmark landmark) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(landmark.x() >> 4, landmark.z() >> 4);
        if (chunk != null) {
            return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, landmark.x() & 15, landmark.z() & 15);
        }
        return layout.estimatedY(landmark) + 1;
    }

    public static BlockPos position(ServerLevel level, LandmarkLayout layout, Landmark landmark) {
        return new BlockPos(landmark.x(), groundY(level, layout, landmark), landmark.z());
    }

    public static Component name(LandmarkType type) {
        return Component.translatable("landmark.universe." + type.id());
    }
}
