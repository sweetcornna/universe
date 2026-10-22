package io.github.sweetcornna.universe.darkfog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.BaseLayout;
import io.github.sweetcornna.universe.core.darkfog.BaseSites;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.DarkFogLoot;
import io.github.sweetcornna.universe.core.darkfog.DarkFogSettings;
import io.github.sweetcornna.universe.core.darkfog.Threat;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogBuilding;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogUnit;
import io.github.sweetcornna.universe.darkfog.facility.FacilityRepairs;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.network.DarkFogPayloads;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.stats.ProductionData;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 黑雾地面基地的管理（ADR-0028 §二、§三、§六）：放基地、按时间结算、威胁、派进攻、掉落。
 *
 * <ul>
 *   <li>第一次见到一颗星球时按种子选好基地点（{@link BaseSites}）；基地中心所在的区块是新生成的，基地才在那里扎下，
 *       以前就生成过的区块里的点作废。</li>
 *   <li>每秒结算一次所有基地（{@link BaseState}）。有人在 160 格内、区块已加载，就按存档把建筑和一部分守家的单位放出来；
 *       人走远了收回，血量写回存档。</li>
 *   <li>威胁满了，在这颗星球上离基地最近、用电最多的那个电网旁边（朝基地方向 80 格外）放一波进攻部队，前 30 秒预警。</li>
 *   <li>实体那边挨打、死掉时回调这里：攒经验、涨威胁、记下血量、掉东西；钻芯打掉基地瓦解，原地留下地核。</li>
 * </ul>
 */
public final class DarkFogBases {
    private static final int INTERVAL = 20;
    /** 有人在这么近、区块已加载，就把基地放出来。 */
    private static final double PLACE_RANGE = 160;
    /** 人走得比这还远就收回。 */
    private static final double RECALL_RANGE = 192;
    /** 玩家在这么近时营地超载。 */
    private static final double OVERDRIVE_RANGE = 64;
    /** 放出来守家的单位，每种最多几个（营地里的数照记，死一个补一个）。 */
    private static final int[] DEFENDERS = {5, 5, 6};
    /** 守家的单位离家太远就收回营地。 */
    private static final double LEASH = 112;
    /** 掉落物和收拾残局的提示发给多远内的人。 */
    private static final double NOTICE_RANGE = 160;

    /** 每处放出来的基地（或者有进攻部队的基地）在内存里的样子；不存档。 */
    private static final Map<ServerLevel, Map<Integer, Live>> LIVE = new WeakHashMap<>();
    /** 还在等中心区块生成的基地点：维度 → 区块。区块加载事件在别的线程里查这个。 */
    private static final Map<ResourceKey<Level>, Set<Long>> WAITING = new ConcurrentHashMap<>();
    /** 区块加载事件不能直接动世界（会死锁），先排队，下一刻处理。 */
    private static final Queue<Loaded> LOADED = new ConcurrentLinkedQueue<>();
    /** 游戏内测试用的基地：不自动放出来、不派进攻。 */
    private static final Set<String> INERT = new HashSet<>();

    private record Loaded(ResourceKey<Level> dimension, long chunk, boolean fresh) {
    }

    /** 一处基地放出来的实体和正在打的进攻。 */
    private static final class Live {
        final DarkFogBuilding[] buildings = new DarkFogBuilding[BaseLayout.size()];
        final List<DarkFogUnit> defenders = new ArrayList<>();
        boolean placed;
        @Nullable Wave wave;
    }

    /** 一波进攻：先预警，再放出来，打完或者到点撤走。 */
    private static final class Wave {
        final BlockPos target;
        final BlockPos from;
        final Threat.Wave planned;
        int countdown;
        long started = -1;
        int killed;
        final List<DarkFogUnit> units = new ArrayList<>();

        Wave(BlockPos target, BlockPos from, Threat.Wave planned) {
            this.target = target;
            this.from = from;
            this.planned = planned;
            this.countdown = Threat.WARNING_TICKS;
        }
    }

    private DarkFogBases() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                plan(level);
            }
        });
        gameBus.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                Set<Long> waiting = WAITING.get(level.dimension());
                long chunk = event.getChunk().getPos().pack();
                if (waiting != null && waiting.contains(chunk)) {
                    LOADED.add(new Loaded(level.dimension(), chunk, event.isNewChunk()));
                }
            }
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            LIVE.clear();
            WAITING.clear();
            LOADED.clear();
            INERT.clear();
        });
    }

    private static String key(Level level) {
        return level.dimension().identifier().toString();
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- 选址

    /** 第一次见到这颗星球：按种子选好基地点。母星不放。 */
    private static void plan(ServerLevel level) {
        Optional<Planet> body = Planets.planet(level);
        if (body.isEmpty()) {
            WAITING.put(level.dimension(), Set.of());
            return;
        }
        DarkFogData data = DarkFogData.get(level.getServer());
        DarkFogData.Planet planet = data.planet(key(level)).orElseGet(() -> data.plan(key(level), sites(level.getServer(), body.get())));
        Set<Long> waiting = ConcurrentHashMap.newKeySet();
        for (BaseState base : planet.bases()) {
            if (base.status() == BaseState.Status.PENDING) {
                waiting.add(ChunkPos.pack(base.x() >> 4, base.z() >> 4));
            }
        }
        WAITING.put(level.dimension(), waiting);
    }

    private static List<BaseState> sites(MinecraftServer server, Planet body) {
        double occupation = UniverseRules.darkFog(server).occupation();
        Galaxy galaxy = Planets.galaxy(server);
        Optional<StarSystem> system = galaxy.system(body.id().system());
        List<BaseSites.Body> bodies = new ArrayList<>();
        system.ifPresent(s -> s.bodies().stream()
                .filter(Planet::landable)
                .filter(p -> !p.id().equals(GalaxyGenerator.HOME_PLANET_ID))
                .forEach(p -> bodies.add(new BaseSites.Body(p.id().path(), p.seed(), p.worldSize()))));
        boolean home = body.id().system() == GalaxyGenerator.HOME_SYSTEM;
        long systemSeed = system.map(StarSystem::seed).orElse(0L);
        int count = BaseSites.counts(bodies, home, systemSeed, occupation).getOrDefault(body.id().path(), 0);
        List<BaseState> bases = new ArrayList<>();
        for (BaseSites.Site site : BaseSites.place(new BaseSites.Body(body.id().path(), body.seed(), body.worldSize()), count, occupation)) {
            bases.add(new BaseState(site.index(), site.x(), site.z()));
        }
        return bases;
    }

    // ---- 每秒结算

    private static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % INTERVAL != 0 || !server.tickRateManager().runsNormally()) {
            return;
        }
        DarkFogData data = DarkFogData.get(server);
        DarkFogSettings settings = UniverseRules.darkFog(server);
        long now = now(server);
        for (Loaded loaded; (loaded = LOADED.poll()) != null;) {
            ServerLevel level = server.getLevel(loaded.dimension());
            if (level != null) {
                settle(level, data, settings, loaded, now);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!WAITING.containsKey(level.dimension())) {
                plan(level);
            }
            Optional<DarkFogData.Planet> planet = data.planet(key(level));
            if (planet.isEmpty() || planet.get().bases().isEmpty()) {
                continue;
            }
            double threat = Threat.powerPerTick(watts(server, level, now), settings);
            boolean dirty = false;
            for (BaseState base : planet.get().bases()) {
                if (INERT.contains(inertKey(level, base.id()))) {
                    continue;
                }
                dirty |= tickBase(level, base, settings, threat, now);
            }
            if (dirty) {
                data.setDirty();
            }
        }
    }

    /** 基地中心的区块加载了：新生成的就扎下，以前生成过的作废。 */
    private static void settle(ServerLevel level, DarkFogData data, DarkFogSettings settings, Loaded loaded, long now) {
        data.planet(key(level)).ifPresent(planet -> {
            for (BaseState base : planet.bases()) {
                if (base.status() == BaseState.Status.PENDING && ChunkPos.pack(base.x() >> 4, base.z() >> 4) == loaded.chunk()) {
                    if (loaded.fresh() && settings.enabled()) {
                        base.activate(settings.initialLevel(), now, settings.growth());
                    } else {
                        base.voidSite();
                    }
                    data.setDirty();
                }
            }
            Set<Long> waiting = WAITING.get(level.dimension());
            if (waiting != null) {
                waiting.remove(loaded.chunk());
            }
        });
    }

    /** 这颗星球最近一分钟的用电（瓦）。 */
    private static double watts(MinecraftServer server, ServerLevel level, long now) {
        ProductionStats.Ledger ledger = ProductionData.get(server).stats().ledgers().get(key(level));
        if (ledger == null) {
            return 0;
        }
        double perTick = (double) ledger.demand().sum(ProductionStats.Range.MINUTE, now) / (ProductionStats.SHORT_TICKS * ProductionStats.SHORT_SLOTS);
        return perTick * 20;
    }

    private static boolean tickBase(ServerLevel level, BaseState base, DarkFogSettings settings, double threatPerTick, long now) {
        Live live = live(level, base.id());
        if (base.status() != BaseState.Status.ACTIVE) {
            if (live != null) {
                tickWave(level, base, live, settings, now);
                if (live.placed) {
                    recall(level, base, live);
                }
            }
            return false;
        }
        if (!settings.enabled()) {
            // 关了黑雾：停摆，单位收回，进攻撤走
            base.skipTo(now);
            if (live != null) {
                cancelWave(level, base, live);
                live.defenders.forEach(Entity::discard);
                live.defenders.clear();
                for (DarkFogBuilding building : live.buildings) {
                    if (building != null) {
                        building.setPowered(false);
                    }
                }
            }
            return true;
        }
        Vec3 core = core(base);
        boolean near = nearestPlayer(level, core, OVERDRIVE_RANGE) != null;
        boolean overdrive = near || Taunts.overdrives(level, core);
        boolean attacking = live != null && live.wave != null;
        double threat = attacking || settings.passive() ? 0 : threatPerTick;
        base.advanceTo(now, new BaseState.Environment(settings.growth(), overdrive, threat));
        boolean wanted = nearestPlayer(level, core, live != null && live.placed ? RECALL_RANGE : PLACE_RANGE) != null
                && level.isLoaded(BlockPos.containing(core));
        if (wanted) {
            live = liveOrNew(level, base.id());
            place(level, base, live);
        } else if (live != null && live.placed) {
            recall(level, base, live);
        }
        live = live(level, base.id());
        if (live == null && base.threatFull() && !settings.passive()) {
            live = liveOrNew(level, base.id());
        }
        if (live != null) {
            tickWave(level, base, live, settings, now);
        }
        return true;
    }

    // ---- 放出来、收回

    private static @Nullable Live live(ServerLevel level, int id) {
        Map<Integer, Live> map = LIVE.get(level);
        return map == null ? null : map.get(id);
    }

    private static Live liveOrNew(ServerLevel level, int id) {
        return LIVE.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(id, key -> new Live());
    }

    /** 钻芯脚下那一格的中心（地面没压平时高度按 0 算）。 */
    private static Vec3 core(BaseState base) {
        return new Vec3(base.x() + 0.5, base.ground() ? base.y() + 1 : 0, base.z() + 0.5);
    }

    private static BlockPos corePos(BaseState base) {
        return new BlockPos(base.x(), base.y() + 1, base.z());
    }

    private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 pos, double range) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - pos.x;
            double dz = player.getZ() - pos.z;
            double d = dx * dx + dz * dz;
            if (d <= bestDistance) {
                bestDistance = d;
                best = player;
            }
        }
        return best;
    }

    /** 槽位上建筑的位置：地面上的站在底板上，中继站悬在上空。 */
    private static Vec3 slotPos(BaseState base, int slot) {
        BaseLayout.Slot s = BaseLayout.slot(slot);
        double y = base.y() + 1 + (s.kind() == BuildingKind.RELAY ? BaseLayout.RELAY_HEIGHT : 0);
        return new Vec3(base.x() + s.dx() + 0.5, y, base.z() + s.dz() + 0.5);
    }

    private static void place(ServerLevel level, BaseState base, Live live) {
        if (!base.ground()) {
            if (!groundLoaded(level, base)) {
                return;
            }
            buildGround(level, base);
        }
        live.placed = true;
        for (int slot = 0; slot < BaseLayout.size(); slot++) {
            DarkFogBuilding building = live.buildings[slot];
            if (building != null && (building.isRemoved() || !building.isAlive())) {
                live.buildings[slot] = building = null;
            }
            if (!base.alive(slot)) {
                if (building != null) {
                    building.discard();
                    live.buildings[slot] = null;
                }
                continue;
            }
            if (building == null) {
                Vec3 pos = slotPos(base, slot);
                building = DarkFogBuilding.create(level, BaseLayout.slot(slot).kind(), base.id(), base.level(), pos);
                building.setHealth(Math.max(1, building.getMaxHealth() * base.health(slot)));
                building.setPowered(base.powered());
                level.addFreshEntity(building);
                live.buildings[slot] = building;
            } else {
                if (building.darkFogLevel() != base.level()) {
                    relevel(building, base.level());
                }
                building.setPowered(base.powered());
                base.setHealth(slot, building.getHealth() / building.getMaxHealth());
            }
        }
        placeDefenders(level, base, live);
        repairGround(level, base);
    }

    /** 等级变了：血量上限跟着变，血量的比例不变。 */
    private static void relevel(DarkFogBuilding building, int level) {
        building.setDarkFogLevel(level);
    }

    private static void placeDefenders(ServerLevel level, BaseState base, Live live) {
        Vec3 home = core(base);
        live.defenders.removeIf(unit -> {
            if (unit.isRemoved() || !unit.isAlive()) {
                return true;
            }
            if (unit.position().distanceToSqr(home) > LEASH * LEASH) {
                // 追得太远了：回营地
                unit.discard();
                return true;
            }
            return false;
        });
        if (!base.powered() && live.defenders.isEmpty()) {
            return;
        }
        for (UnitKind kind : UnitKind.values()) {
            int wanted = Math.min(base.units(kind), DEFENDERS[kind.ordinal()]);
            List<DarkFogUnit> ofKind = live.defenders.stream().filter(unit -> unit.kind() == kind).toList();
            for (int i = wanted; i < ofKind.size(); i++) {
                // 营地里的少了（派去进攻了）：多出来的收回去
                ofKind.get(i).discard();
                live.defenders.remove(ofKind.get(i));
            }
            for (int i = ofKind.size(); i < wanted; i++) {
                Vec3 at = campPos(level, base, kind);
                live.defenders.add(DarkFogUnit.spawn(level, kind, base.id(), base.level(), at, corePos(base), DarkFogUnit.Mode.DEFEND, null));
            }
        }
    }

    /** 这种单位从哪个营地出来：随便挑一个还在的，旁边几格。 */
    private static Vec3 campPos(ServerLevel level, BaseState base, UnitKind kind) {
        List<Integer> camps = new ArrayList<>();
        for (int slot = 0; slot < BaseLayout.size(); slot++) {
            if (base.alive(slot) && BaseLayout.slot(slot).kind() == kind.camp()) {
                camps.add(slot);
            }
        }
        Vec3 pos = camps.isEmpty() ? core(base) : slotPos(base, camps.get(level.getRandom().nextInt(camps.size())));
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double r = 2.5 + level.getRandom().nextDouble() * 2;
        double lift = kind == UnitKind.RAIDER ? 1.2 : 5 + level.getRandom().nextDouble() * 3;
        return pos.add(Math.cos(angle) * r, lift, Math.sin(angle) * r);
    }

    /** 人走远了：建筑的血量写回存档，实体都收回。 */
    private static void recall(ServerLevel level, BaseState base, Live live) {
        for (int slot = 0; slot < BaseLayout.size(); slot++) {
            DarkFogBuilding building = live.buildings[slot];
            if (building != null) {
                if (building.isAlive() && !building.isRemoved()) {
                    base.setHealth(slot, building.getHealth() / building.getMaxHealth());
                }
                building.discard();
                live.buildings[slot] = null;
            }
        }
        live.defenders.forEach(Entity::discard);
        live.defenders.clear();
        live.placed = false;
        if (live.wave == null) {
            Map<Integer, Live> map = LIVE.get(level);
            if (map != null) {
                map.remove(base.id());
            }
        }
    }

    // ---- 地面

    private static boolean groundLoaded(ServerLevel level, BaseState base) {
        int r = BaseLayout.RADIUS + 6;
        return level.isLoaded(new BlockPos(base.x() - r, 0, base.z() - r)) && level.isLoaded(new BlockPos(base.x() + r, 0, base.z() - r))
                && level.isLoaded(new BlockPos(base.x() - r, 0, base.z() + r)) && level.isLoaded(new BlockPos(base.x() + r, 0, base.z() + r));
    }

    /** 压平基地那一圈地，铺上底板和导轨，边上削成缓坡。 */
    private static void buildGround(ServerLevel level, BaseState base) {
        int r = BaseLayout.RADIUS;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz <= r * r) {
                    heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.x() + dx, base.z() + dz) - 1);
                }
            }
        }
        heights.sort(Integer::compare);
        int y = Mth.clamp(heights.get(heights.size() / 2), level.getMinY() + 8, level.getMaxY() - 64);
        BlockState rock = UniverseBlocks.PLANET_ROCK.get().defaultBlockState();
        int edge = r + 6;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -edge; dx <= edge; dx++) {
            for (int dz = -edge; dz <= edge; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > edge) {
                    continue;
                }
                int x = base.x() + dx;
                int z = base.z() + dz;
                if (distance <= r) {
                    for (int yy = y + 1; yy <= y + 28; yy++) {
                        clear(level, pos.set(x, yy, z));
                    }
                    level.setBlock(pos.set(x, y, z), pattern(dx, dz), Block.UPDATE_CLIENTS);
                    for (int yy = y - 1; yy >= y - 6; yy--) {
                        fill(level, pos.set(x, yy, z), rock);
                    }
                } else {
                    // 边上一圈：高的削低、低的垫高，坡度一格一格
                    int slope = (int) Math.ceil(distance - r);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (top > y + slope) {
                        for (int yy = y + slope + 1; yy <= Math.min(top, y + 28); yy++) {
                            clear(level, pos.set(x, yy, z));
                        }
                    } else if (top < y - slope) {
                        for (int yy = top + 1; yy <= y - slope; yy++) {
                            fill(level, pos.set(x, yy, z), rock);
                        }
                    }
                }
            }
        }
        base.groundBuilt(y);
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || current.canBeReplaced() || !current.getFluidState().isEmpty()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** 底板上的花纹：一圈导轨，加上从钻芯往六个方向放射的导轨。 */
    private static BlockState pattern(int dx, int dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean ring = Math.abs(distance - 14) < 0.6;
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        double off = Math.abs(((angle % 60) + 60) % 60 - 30);
        boolean spoke = distance > 3 && off > 30 - Math.toDegrees(Math.atan2(0.55, distance));
        return (ring || spoke ? UniverseBlocks.DARK_FOG_CONDUIT : UniverseBlocks.DARK_FOG_PLATING).get().defaultBlockState();
    }

    /** 底板被挖掉了就补上：每次随便查几格。 */
    private static void repairGround(ServerLevel level, BaseState base) {
        int r = BaseLayout.RADIUS;
        for (int i = 0; i < 24; i++) {
            int dx = level.getRandom().nextInt(2 * r + 1) - r;
            int dz = level.getRandom().nextInt(2 * r + 1) - r;
            if (dx * dx + dz * dz > r * r) {
                continue;
            }
            BlockPos pos = new BlockPos(base.x() + dx, base.y(), base.z() + dz);
            BlockState state = level.getBlockState(pos);
            if (!state.is(UniverseBlocks.DARK_FOG_PLATING.get()) && !state.is(UniverseBlocks.DARK_FOG_CONDUIT.get())
                    && !state.is(UniverseBlocks.DARK_FOG_CORE_REMNANT.get()) && (state.isAir() || state.canBeReplaced())) {
                level.setBlock(pos, pattern(dx, dz), Block.UPDATE_ALL);
            }
        }
    }

    // ---- 进攻

    private static void tickWave(ServerLevel level, BaseState base, Live live, DarkFogSettings settings, long now) {
        Wave wave = live.wave;
        if (wave == null) {
            if (base.status() == BaseState.Status.ACTIVE && base.threatFull() && !settings.passive() && base.powered()
                    && base.cooldown() == 0 && !level.players().isEmpty()) {
                plan(level, base, live, settings);
            }
            return;
        }
        if (wave.started < 0) {
            wave.countdown -= INTERVAL;
            if (wave.countdown <= 0) {
                launch(level, base, live, wave, settings, now);
            } else if (wave.countdown % 100 == 0) {
                warn(level, wave, wave.countdown);
            }
            return;
        }
        wave.units.removeIf(unit -> unit.isRemoved() || !unit.isAlive());
        if (wave.units.isEmpty()) {
            finishWave(level, base, live, wave.killed > 0 ? "message.universe.dark_fog.repelled" : null);
        } else if (now - wave.started > Threat.WAVE_TIMEOUT_TICKS) {
            // 到点了：没死的撤回营地
            for (DarkFogUnit unit : wave.units) {
                base.setUnits(unit.kind(), base.units(unit.kind()) + 1);
                unit.discard();
            }
            wave.units.clear();
            finishWave(level, base, live, "message.universe.dark_fog.retreated");
        }
    }

    /** 威胁满了：挑目标，先预警。 */
    private static void plan(ServerLevel level, BaseState base, Live live, DarkFogSettings settings) {
        Optional<BlockPos> target = target(level, base);
        if (target.isEmpty()) {
            return;
        }
        Threat.Wave planned = Threat.wave(base.units(UnitKind.RAIDER), base.units(UnitKind.RANGER), base.level(), settings.aggressiveness());
        if (planned.total() == 0) {
            return;
        }
        Wave wave = new Wave(target.get(), spawnPoint(level, target.get(), base), planned);
        live.wave = wave;
        warn(level, wave, wave.countdown);
    }

    /** 这颗星球上离基地最近、用电最多的那个电网，取它离基地最近的那台设备。 */
    static Optional<BlockPos> target(ServerLevel level, BaseState base) {
        LevelPower power = LevelPower.existing(level);
        if (power == null) {
            return Optional.empty();
        }
        BlockPos best = null;
        double bestScore = 0;
        for (PowerSystem.Grid grid : power.grids()) {
            if (grid.demand() <= 0) {
                continue;
            }
            long id = grid.nearest(base.x(), base.z());
            if (id == Long.MIN_VALUE) {
                continue;
            }
            BlockPos pos = BlockPos.of(id);
            double distance = Math.hypot(pos.getX() - base.x(), pos.getZ() - base.z());
            double score = grid.demand() / (1 + (distance / 500) * (distance / 500));
            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 进攻部队出现的地方：目标朝基地方向 80 格外；那里的区块没加载就往目标那边挪。 */
    private static BlockPos spawnPoint(ServerLevel level, BlockPos target, BaseState base) {
        double dx = base.x() - target.getX();
        double dz = base.z() - target.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        for (double d = Threat.SPAWN_DISTANCE; d >= 16; d -= 8) {
            int x = (int) Math.floor(target.getX() + dx / length * d);
            int z = (int) Math.floor(target.getZ() + dz / length * d);
            BlockPos column = new BlockPos(x, target.getY(), z);
            if (level.isLoaded(column)) {
                // 落在玩家的建筑上面，不落进建筑里（MOTION_BLOCKING 不认玩家的方块）
                return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            }
        }
        return target.above(2);
    }

    private static void launch(ServerLevel level, BaseState base, Live live, Wave wave, DarkFogSettings settings, long now) {
        Threat.Wave actual = base.status() == BaseState.Status.ACTIVE ? base.launch(settings.aggressiveness()) : Threat.Wave.NONE;
        wave.started = now;
        BlockPos home = corePos(base);
        spawnWave(level, base, wave, UnitKind.RAIDER, actual.raiders(), home);
        spawnWave(level, base, wave, UnitKind.RANGER, actual.rangers(), home);
        if (wave.units.isEmpty()) {
            finishWave(level, base, live, null);
            return;
        }
        DarkFogPayloads.Warning arrived = new DarkFogPayloads.Warning(wave.target, wave.from, actual.raiders(), actual.rangers(), 0);
        level.players().forEach(player -> PacketDistributor.sendToPlayer(player, arrived));
    }

    private static void spawnWave(ServerLevel level, BaseState base, Wave wave, UnitKind kind, int count, BlockPos home) {
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double r = level.getRandom().nextDouble() * 6;
            double lift = kind == UnitKind.RAIDER ? 1.2 : 6 + level.getRandom().nextDouble() * 4;
            Vec3 at = new Vec3(wave.from.getX() + 0.5 + Math.cos(angle) * r, wave.from.getY() + lift, wave.from.getZ() + 0.5 + Math.sin(angle) * r);
            wave.units.add(DarkFogUnit.spawn(level, kind, base.id(), base.level(), at, home, DarkFogUnit.Mode.ATTACK, wave.target));
        }
    }

    private static void warn(ServerLevel level, Wave wave, int ticks) {
        DarkFogPayloads.Warning warning = new DarkFogPayloads.Warning(wave.target, wave.from, wave.planned.raiders(), wave.planned.rangers(), ticks);
        level.players().forEach(player -> PacketDistributor.sendToPlayer(player, warning));
    }

    private static void finishWave(ServerLevel level, BaseState base, Live live, @Nullable String message) {
        Wave wave = live.wave;
        live.wave = null;
        if (base.status() == BaseState.Status.ACTIVE) {
            base.waveOver();
        }
        level.players().forEach(player -> PacketDistributor.sendToPlayer(player, DarkFogPayloads.Warning.CLEAR));
        if (message != null && wave != null) {
            notice(level, Vec3.atCenterOf(wave.target), Component.translatable(message).withStyle(ChatFormatting.GREEN), NOTICE_RANGE * 2);
        }
        DarkFogData.get(level.getServer()).setDirty();
        if (!live.placed) {
            Map<Integer, Live> map = LIVE.get(level);
            if (map != null) {
                map.remove(base.id());
            }
        }
    }

    private static void cancelWave(ServerLevel level, BaseState base, Live live) {
        if (live.wave != null) {
            live.wave.units.forEach(Entity::discard);
            live.wave = null;
            level.players().forEach(player -> PacketDistributor.sendToPlayer(player, DarkFogPayloads.Warning.CLEAR));
        }
    }

    // ---- 实体的回调

    private static Optional<BaseState> base(ServerLevel level, int id) {
        return DarkFogData.get(level.getServer()).planet(key(level)).flatMap(planet -> planet.base(id));
    }

    /** 建筑挨了打（伤害已经扣过）。中继站挨打，这个恒星系的巢穴威胁猛涨（ADR-0028 §八）。 */
    public static void onBuildingHurt(ServerLevel level, DarkFogBuilding building, float damage, DamageSource source) {
        if (building.kind() == BuildingKind.RELAY && damage > 0) {
            Planets.bodyId(level.dimension()).ifPresent(body -> DarkFogHives.onRelayHurt(level.getServer(), body.system(), damage));
        }
        base(level, building.baseId()).ifPresent(base -> {
            base.hurt(damage, Threat.buildingPerDamage(building.kind(), UniverseRules.darkFog(level.getServer())));
            int slot = slotOf(level, base, building);
            if (slot >= 0 && building.getMaxHealth() > 0) {
                base.setHealth(slot, building.getHealth() / building.getMaxHealth());
            }
            DarkFogData.get(level.getServer()).setDirty();
        });
    }

    /** 建筑被打掉了。 */
    public static void onBuildingDestroyed(ServerLevel level, DarkFogBuilding building, DamageSource source) {
        dropLoot(level, building.position().add(0, 0.5, 0), building.darkFogLevel(), DarkFogLoot.weight(building.kind()));
        base(level, building.baseId()).ifPresent(base -> {
            int slot = slotOf(level, base, building);
            if (slot < 0 || base.status() != BaseState.Status.ACTIVE) {
                return;
            }
            Live live = live(level, base.id());
            if (live != null) {
                live.buildings[slot] = null;
            }
            if (slot == BaseLayout.CORE) {
                collapse(level, base, live);
            } else {
                base.destroy(slot);
                if (slot == BaseLayout.RELAY) {
                    notice(level, building.position(), Component.translatable("message.universe.dark_fog.relay_down").withStyle(ChatFormatting.YELLOW),
                            NOTICE_RANGE);
                }
            }
            DarkFogData.get(level.getServer()).setDirty();
        });
    }

    /** 这座建筑是哪个槽位：先看放出来的实体，找不到再按种类和位置对。 */
    private static int slotOf(ServerLevel level, BaseState base, DarkFogBuilding building) {
        Live live = live(level, base.id());
        if (live != null) {
            for (int slot = 0; slot < BaseLayout.size(); slot++) {
                if (live.buildings[slot] == building) {
                    return slot;
                }
            }
        }
        for (int slot = 0; slot < BaseLayout.size(); slot++) {
            if (BaseLayout.slot(slot).kind() == building.kind() && base.alive(slot) && slotPos(base, slot).distanceToSqr(building.position()) < 4) {
                return slot;
            }
        }
        return -1;
    }

    /** 钻芯没了：建筑一起瓦解、掉东西，守家的单位散了，原地留下地核。 */
    private static void collapse(ServerLevel level, BaseState base, @Nullable Live live) {
        if (live != null) {
            for (int slot = 0; slot < BaseLayout.size(); slot++) {
                DarkFogBuilding other = live.buildings[slot];
                if (other != null && other.isAlive() && !other.isRemoved()) {
                    dropLoot(level, other.position().add(0, 0.5, 0), other.darkFogLevel(), DarkFogLoot.weight(other.kind()) / 2);
                    level.sendParticles(ParticleTypes.EXPLOSION, other.getX(), other.getY() + 1, other.getZ(), 2, 0.8, 0.8, 0.8, 0);
                    other.discard();
                }
                live.buildings[slot] = null;
            }
            live.defenders.forEach(Entity::discard);
            live.defenders.clear();
            live.placed = false;
        }
        base.destroy(BaseLayout.CORE);
        if (base.ground()) {
            BlockPos remnant = new BlockPos(base.x(), base.y(), base.z());
            level.setBlock(remnant, UniverseBlocks.DARK_FOG_CORE_REMNANT.get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, remnant, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        notice(level, core(base), Component.translatable("message.universe.dark_fog.base_destroyed").withStyle(ChatFormatting.GOLD), NOTICE_RANGE);
    }

    /** 单位挨了打（伤害已经扣过）：守家的涨威胁，进攻部队只算经验。 */
    public static void onUnitHurt(ServerLevel level, DarkFogUnit unit, float damage, DamageSource source) {
        base(level, unit.baseId()).ifPresent(base -> {
            boolean defending = unit.mode() == DarkFogUnit.Mode.DEFEND;
            base.hurt(damage, defending ? Threat.unitPerDamage(UniverseRules.darkFog(level.getServer())) : 0);
            DarkFogData.get(level.getServer()).setDirty();
        });
    }

    /** 单位被打死了。 */
    public static void onUnitKilled(ServerLevel level, DarkFogUnit unit, DamageSource source) {
        dropLoot(level, unit.position(), unit.darkFogLevel(), DarkFogLoot.weight(unit.kind()));
        base(level, unit.baseId()).ifPresent(base -> {
            Live live = live(level, base.id());
            if (live != null && live.wave != null && live.wave.units.remove(unit)) {
                live.wave.killed++;
                return;
            }
            if (live != null) {
                live.defenders.remove(unit);
            }
            if (unit.mode() == DarkFogUnit.Mode.DEFEND) {
                base.unitLost(unit.kind());
                DarkFogData.get(level.getServer()).setDirty();
            }
        });
    }

    /** 在原地掉东西，2 分钟不捡就消失。巢穴的建筑和单位也用这个（{@code weight} 按 {@link DarkFogLoot#weight} 的量级给）。 */
    public static void dropLoot(ServerLevel level, Vec3 at, int darkFogLevel, double weight) {
        Rng rng = Rng.of(level.getRandom().nextLong());
        for (DarkFogLoot.Drop drop : DarkFogLoot.roll(darkFogLevel, weight, rng)) {
            Identifier id = Identifier.tryParse(drop.item());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.get(id).ifPresent(item -> {
                ItemStack stack = new ItemStack(item, drop.count());
                ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack,
                        (level.getRandom().nextDouble() - 0.5) * 0.3, 0.25, (level.getRandom().nextDouble() - 0.5) * 0.3);
                entity.setDefaultPickUpDelay();
                entity.lifespan = DarkFogLoot.LIFESPAN_TICKS;
                // 战场分析基地"只捡黑雾掉落"认这个标签（沙土这类普通物品也算黑雾的掉落）
                entity.addTag(FacilityRepairs.DARK_FOG_LOOT_TAG);
                level.addFreshEntity(entity);
            });
        }
    }

    private static void notice(ServerLevel level, Vec3 at, Component message, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= range * range) {
                player.sendSystemMessage(message);
            }
        }
    }

    // ---- 巢穴的中继站（ADR-0028 §八）

    /** 新落地的基地离别的基地至少这么远（中心到中心）。 */
    public static final int LANDING_SPACING = BaseLayout.RADIUS * 2 + 48;

    /** 一处基地和它所在的星球维度。 */
    public record BaseRef(ResourceKey<Level> dimension, BaseState base) {
    }

    /** 这个恒星系里所有星球上的基地（星球维度没加载也算；母星没有基地）。 */
    public static List<BaseRef> bases(MinecraftServer server, int system) {
        List<BaseRef> list = new ArrayList<>();
        DarkFogData.get(server).planets().forEach((key, planet) -> {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                return;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
            if (Planets.bodyId(dimension).filter(body -> body.system() == system).isPresent()) {
                planet.bases().forEach(base -> list.add(new BaseRef(dimension, base)));
            }
        });
        return list;
    }

    /** 这颗星球维度上的基地点有没有选过；没选过就按种子选好（中继站落地前要先有，免得以后选的点和落地的基地叠在一起）。 */
    private static Optional<DarkFogData.Planet> planned(MinecraftServer server, ResourceKey<Level> dimension) {
        Optional<Planet> body = Planets.generatedPlanet(Planets.galaxy(server), dimension);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        DarkFogData data = DarkFogData.get(server);
        String key = dimension.identifier().toString();
        return Optional.of(data.planet(key).orElseGet(() -> data.plan(key, sites(server, body.get()))));
    }

    /**
     * 巢穴的中继站在 ({@code x}, {@code z}) 落地，建一处新基地，从 {@code darkFogLevel} 级开始。星球维度不用加载：地面等有人来了再压平。
     *
     * @return 新的基地；母星、不是星球、护盾盖着、离别的基地太近时是空
     */
    public static Optional<BaseState> landRelay(MinecraftServer server, ResourceKey<Level> dimension, int x, int z, int darkFogLevel) {
        if (dimension == Level.OVERWORLD || !UniverseRules.darkFog(server).enabled() || Shields.covers(server, dimension, x + 0.5, z + 0.5)) {
            return Optional.empty();
        }
        Optional<DarkFogData.Planet> planet = planned(server, dimension);
        if (planet.isEmpty()) {
            return Optional.empty();
        }
        for (BaseState other : planet.get().bases()) {
            if (other.status() != BaseState.Status.VOID && Math.hypot(other.x() - x, other.z() - z) < LANDING_SPACING) {
                return Optional.empty();
            }
        }
        DarkFogData data = DarkFogData.get(server);
        BaseState base = data.add(dimension.identifier().toString(), x, z);
        base.land(darkFogLevel, now(server));
        data.setDirty();
        return Optional.of(base);
    }

    /**
     * 巢穴的中继站补上这处基地被打掉的中继站。
     *
     * @return 补上了；基地不在、瓦解了、中继站还在时是 false
     */
    public static boolean restoreRelay(MinecraftServer server, ResourceKey<Level> dimension, int id) {
        DarkFogData data = DarkFogData.get(server);
        Optional<BaseState> base = data.planet(dimension.identifier().toString()).flatMap(planet -> planet.base(id));
        if (base.isEmpty() || !base.get().restoreRelay()) {
            return false;
        }
        data.setDirty();
        return true;
    }

    // ---- 调试命令和游戏内测试用

    /** 这颗星球上的基地（没有就空）。 */
    static List<BaseState> bases(ServerLevel level) {
        return DarkFogData.get(level.getServer()).planet(key(level)).map(DarkFogData.Planet::bases).orElse(List.of());
    }

    static Optional<BaseState> find(ServerLevel level, int id) {
        return base(level, id);
    }

    /** 在这里马上扎下一处基地（不管区块新不新）；{@code groundY} 给了就不压平地面，直接当底板在那一层。 */
    public static BaseState create(ServerLevel level, int x, int z, @Nullable Integer groundY) {
        DarkFogData data = DarkFogData.get(level.getServer());
        BaseState base = data.add(key(level), x, z);
        DarkFogSettings settings = UniverseRules.darkFog(level.getServer());
        base.activate(settings.initialLevel(), now(level.getServer()), settings.growth());
        if (groundY != null) {
            base.groundBuilt(groundY);
        }
        data.setDirty();
        return base;
    }

    /** 游戏内测试用：一处不自动放出来、不派进攻的基地，底板就在 {@code ground} 那一层，建筑马上放好（不放守家的单位）。 */
    public static BaseState createInert(ServerLevel level, BlockPos ground) {
        BaseState base = create(level, ground.getX(), ground.getZ(), ground.getY());
        INERT.add(inertKey(level, base.id()));
        for (UnitKind kind : UnitKind.values()) {
            base.setUnits(kind, 0);
        }
        Live live = liveOrNew(level, base.id());
        live.placed = true;
        for (int slot = 0; slot < BaseLayout.size(); slot++) {
            if (base.alive(slot)) {
                DarkFogBuilding building = DarkFogBuilding.create(level, BaseLayout.slot(slot).kind(), base.id(), base.level(), slotPos(base, slot));
                level.addFreshEntity(building);
                live.buildings[slot] = building;
            }
        }
        return base;
    }

    /** 游戏内测试用：放着的那座建筑。 */
    public static Optional<DarkFogBuilding> building(ServerLevel level, int id, int slot) {
        Live live = live(level, id);
        return live == null ? Optional.empty() : Optional.ofNullable(live.buildings[slot]);
    }

    /** 游戏内测试用：马上按这个目标派一波（不预警），返回放出来的单位。 */
    public static List<DarkFogUnit> attackNow(ServerLevel level, BaseState base, BlockPos target) {
        Live live = liveOrNew(level, base.id());
        Wave wave = new Wave(target, spawnPoint(level, target, base),
                Threat.wave(base.units(UnitKind.RAIDER), base.units(UnitKind.RANGER), base.level(), UniverseRules.darkFog(level.getServer()).aggressiveness()));
        live.wave = wave;
        launch(level, base, live, wave, UniverseRules.darkFog(level.getServer()), now(level.getServer()));
        return live.wave == null ? List.of() : List.copyOf(wave.units);
    }

    /** 收拾掉一处基地：实体都收回，存档里删掉。 */
    public static void remove(ServerLevel level, int id) {
        Live live = live(level, id);
        base(level, id).ifPresent(base -> {
            if (live != null) {
                cancelWave(level, base, live);
                recall(level, base, live);
            }
        });
        Map<Integer, Live> map = LIVE.get(level);
        if (map != null) {
            map.remove(id);
        }
        INERT.remove(inertKey(level, id));
        DarkFogData.get(level.getServer()).planet(key(level)).ifPresent(planet -> planet.bases().removeIf(base -> base.id() == id));
        DarkFogData.get(level.getServer()).setDirty();
    }

    /** 收拾掉这颗星球上所有的基地。 */
    static int clear(ServerLevel level) {
        List<Integer> ids = bases(level).stream().map(BaseState::id).toList();
        ids.forEach(id -> remove(level, id));
        return ids.size();
    }

    /** 有没有正在预警或者打着的进攻。 */
    static boolean attacking(ServerLevel level, int id) {
        Live live = live(level, id);
        return live != null && live.wave != null;
    }

    private static String inertKey(ServerLevel level, int id) {
        return key(level) + "#" + id;
    }
}
