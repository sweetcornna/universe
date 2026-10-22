package io.github.sweetcornna.universe.world.planet;

import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.poi.Fulgurite;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.teleport.BaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 雷击石（ADR-0026）：星球上落雷时，打中沙、土、石头的地方有时熔成雷击石（{@link Fulgurite}）。
 *
 * <ul>
 *   <li>下雨的星球照原版的规矩在雷暴里打雷；火山灰的风暴不下雨，原版不打，这里在玩家附近补几道。</li>
 *   <li>只改天然的方块（{@link #HOSTS}）：打在避雷针、玩家的东西上不变；8 格内有方块实体、64 格内有基地的也不变，
 *       免得把基地的地面烧出洞来。</li>
 * </ul>
 */
public final class Fulgurites {
    /** 能熔成雷击石的方块。 */
    public static final TagKey<Block> HOSTS = TagKey.create(Registries.BLOCK, Universe.id("fulgurite_hosts"));
    /** 离方块实体多近就不熔（格）。 */
    static final int BUILD_CLEARANCE = 8;
    /** 离基地信标多近就不熔（格）。 */
    static final int BASE_CLEARANCE = 64;

    private Fulgurites() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() instanceof LightningBolt bolt && !event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level
                    && Planets.planet(level).isPresent()) {
                strike(level, bolt.blockPosition());
            }
        });
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 7) {
                volcanicStorm(level);
            }
        });
    }

    /**
     * 雷落在 {@code bolt}（原版的落点是地面上面那一格空气）：下面那块是天然的沙土、石头时，按概率熔成一根雷击石管。
     *
     * @return 熔成了几块
     */
    public static int strike(ServerLevel level, BlockPos bolt) {
        BlockPos hit = bolt.below();
        RandomSource random = level.getRandom();
        if (!level.getBlockState(hit).is(HOSTS) || random.nextDouble() >= Fulgurite.CONVERT_CHANCE || nearBuilds(level, hit)) {
            return 0;
        }
        return fuse(level, hit, Rng.of(random.nextLong()));
    }

    /** 把 {@code hit} 熔成一根雷击石管，不看概率和附近的建筑（命令和测试用）。 */
    public static int fuse(ServerLevel level, BlockPos hit, Rng rng) {
        BlockState glass = UniverseBlocks.FULGURITE.get().defaultBlockState();
        int fused = 0;
        for (int[] cell : Fulgurite.tube(rng)) {
            BlockPos at = hit.offset(cell[0], cell[1], cell[2]);
            if (level.getBlockState(at).is(HOSTS)) {
                level.setBlockAndUpdate(at, glass);
                fused++;
            }
        }
        if (fused > 0) {
            Vec3 top = Vec3.atCenterOf(hit).add(0, 0.6, 0);
            level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 6, 0.2, 0.1, 0.2, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 10, 0.3, 0.2, 0.3, 0.02);
            level.playSound(null, hit, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return fused;
    }

    /** 附近有玩家的东西：方块实体或者基地。 */
    private static boolean nearBuilds(ServerLevel level, BlockPos pos) {
        if (BaseData.get(level.getServer()).near(level.dimension(), pos, BASE_CLEARANCE)) {
            return true;
        }
        int r = BUILD_CLEARANCE;
        ChunkPos from = ChunkPos.containing(pos.offset(-r, 0, -r));
        ChunkPos to = ChunkPos.containing(pos.offset(r, 0, r));
        for (int cx = from.x(); cx <= to.x(); cx++) {
            for (int cz = from.z(); cz <= to.z(); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos entity : chunk.getBlockEntitiesPos()) {
                    if (entity.distSqr(pos) <= r * r) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 火山灰的风暴打雷时，每个玩家附近隔一阵落一道雷（雷本身照原版，会点火、会打人）。 */
    private static void volcanicStorm(ServerLevel level) {
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty() || level.thunderLevel < 0.9F) {
            return;
        }
        WeatherProfile weather = planet.get().weather();
        if (!weather.lightning() || weather.precipitation().falls()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextDouble() >= Fulgurite.VOLCANIC_STRIKE_CHANCE) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Fulgurite.VOLCANIC_MIN_DISTANCE
                    + random.nextDouble() * (Fulgurite.VOLCANIC_MAX_DISTANCE - Fulgurite.VOLCANIC_MIN_DISTANCE);
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (bolt != null) {
                bolt.snapTo(Vec3.atBottomCenterOf(pos));
                level.addFreshEntity(bolt);
            }
        }
    }
}
