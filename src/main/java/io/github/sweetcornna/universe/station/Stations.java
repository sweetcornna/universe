package io.github.sweetcornna.universe.station;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.space.StationLayout.Dir;
import io.github.sweetcornna.universe.core.space.StationLayout.Placement;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 空间站的适配层（ADR-0017）：恒星系的空间站在哪（{@link StationLayout} 算，按星团缓存），生成区块时放方块，
 * 判断一个位置在不在站内。客户端也用这里的位置（星团由客户端自己的副本给）。
 */
public final class Stations {
    private record Key(long galaxy, int system) {
    }

    private static final Map<Key, Optional<Site>> SITES = new ConcurrentHashMap<>();

    private Stations() {
    }

    public static Optional<Site> site(Galaxy galaxy, int system) {
        return SITES.computeIfAbsent(new Key(galaxy.seed(), system), key -> galaxy.system(system).flatMap(StationLayout::site));
    }

    /** 维度里的空间站：只有太空维度有。 */
    public static Optional<Site> site(MinecraftServer server, ResourceKey<Level> dimension) {
        OptionalInt system = SpaceDimensions.system(dimension);
        return system.isPresent() ? site(Planets.galaxy(server), system.getAsInt()) : Optional.empty();
    }

    /** 服务端：这个位置在不在空间站里面（有重力和空气）。 */
    public static boolean inside(Level level, Vec3 pos) {
        if (level.isClientSide() || !SpaceDimensions.isSpace(level.dimension())) {
            return false;
        }
        return site(level.getServer(), level.dimension()).map(site -> site.inside(pos.x, pos.y, pos.z)).orElse(false);
    }

    public static Direction direction(Dir dir) {
        return switch (dir) {
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
        };
    }

    /** 空间站的一个部分是哪个方块。 */
    static BlockState state(Placement placement, Site site) {
        return switch (placement.part()) {
            case HULL -> UniverseBlocks.STATION_HULL.get().defaultBlockState();
            case FLOOR -> UniverseBlocks.STATION_FLOOR.get().defaultBlockState();
            case GLASS -> UniverseBlocks.STATION_GLASS.get().defaultBlockState();
            case LIGHT -> UniverseBlocks.STATION_LIGHT.get().defaultBlockState();
            case PAD -> UniverseBlocks.STATION_PAD.get().defaultBlockState();
            case TRIM -> UniverseBlocks.STATION_TRIM.get().defaultBlockState();
            case SOLAR -> UniverseBlocks.STATION_SOLAR_PANEL.get().defaultBlockState();
            case TERMINAL -> UniverseBlocks.TRADE_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction(placement.facing().rotate(site.hangar())));
            case REPAIR -> UniverseBlocks.SHIP_REPAIR.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction(placement.facing().rotate(site.hangar())));
            case TELEPORTER -> UniverseBlocks.STATION_TELEPORTER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction(placement.facing().rotate(site.hangar())));
            case MISSION_BOARD -> UniverseBlocks.MISSION_BOARD.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction(placement.facing().rotate(site.hangar())));
        };
    }

    /** 空间站传送器的位置。 */
    public static BlockPos teleporter(Site site) {
        int[] pos = site.toWorld(StationLayout.TELEPORTER_X, 1, StationLayout.TELEPORTER_Z);
        return new BlockPos(pos[0], pos[1], pos[2]);
    }

    /** 传送到空间站的人站的地方：传送器前面两格，脚下是地板。 */
    public static Vec3 arrival(Site site) {
        int[] pos = site.toWorld(StationLayout.TELEPORTER_X + 2, 1, StationLayout.TELEPORTER_Z);
        return new Vec3(pos[0] + 0.5, pos[1], pos[2] + 0.5);
    }

    /** 到站时面朝机库。 */
    public static float arrivalYaw(Site site) {
        return switch (site.hangar()) {
            case EAST -> -90;
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
        };
    }

    /** 老存档里的空间站生成时还没有传送器和任务板：有人进站时看一眼，那一格空着就补上。 */
    public static void ensureFixtures(ServerLevel level, Site site) {
        ensure(level, site, StationLayout.TELEPORTER_X, StationLayout.TELEPORTER_Z);
        ensure(level, site, StationLayout.MISSION_BOARD_X, StationLayout.MISSION_BOARD_Z);
    }

    private static void ensure(ServerLevel level, Site site, int x, int z) {
        int[] world = site.toWorld(x, 1, z);
        BlockPos pos = new BlockPos(world[0], world[1], world[2]);
        if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state(StationLayout.at(x, 1, z), site), 3);
        }
    }

    /** 生成区块时调用：把落在这个区块里的那部分空间站放上去，顺带更新生成用的高度图。 */
    public static void place(Site site, ChunkAccess chunk) {
        int[] bounds = site.bounds();
        ChunkPos pos = chunk.getPos();
        int minX = Math.max(bounds[0], pos.getMinBlockX());
        int maxX = Math.min(bounds[3], pos.getMaxBlockX());
        int minZ = Math.max(bounds[2], pos.getMinBlockZ());
        int maxZ = Math.min(bounds[5], pos.getMaxBlockZ());
        int minY = Math.max(bounds[1], chunk.getMinY());
        int maxY = Math.min(bounds[4], chunk.getMaxY());
        if (minX > maxX || minZ > maxZ || minY > maxY) {
            return;
        }
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    int[] local = site.toLocal(x, y, z);
                    Placement placement = StationLayout.at(local[0], local[1], local[2]);
                    if (placement == null) {
                        continue;
                    }
                    BlockState state = state(placement, site);
                    chunk.setBlockState(cursor.set(x, y, z), state);
                    oceanFloor.update(x & 15, y, z & 15, state);
                    worldSurface.update(x & 15, y, z & 15, state);
                }
            }
        }
    }
}
