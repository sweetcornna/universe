package io.github.sweetcornna.universe.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.blueprint.BlueprintGeometry;
import io.github.sweetcornna.universe.core.mecha.DroneFleet;
import io.github.sweetcornna.universe.core.mecha.MechaEnergy;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Flyers;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.network.FlyersPayload;
import io.github.sweetcornna.universe.network.GhostsPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.tech.ExosuitData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 建造无人机的服务端（ADR-0023）：生存模式下放蓝图留下工地（{@link BuildSites}），穿着外骨骼胸甲的玩家身上的无人机
 * 一批一批飞去把虚影建起来，材料在放方块时从背包和货舱里扣。
 *
 * <p>每名玩家一队无人机（{@link DroneFleet}），只建自己的和同一队伍的人的工地。虚影每 {@link #SYNC_INTERVAL} 刻同步一次给附近的玩家：
 * 第一次发整个工地，之后只发还剩哪些格子。
 */
public final class Construction {
    private static final int SYNC_INTERVAL = 10;
    /** 工地离玩家多远以内同步虚影（水平距离，从工地边上算）。 */
    private static final double SYNC_RANGE = 160;
    /** 被占着、缺材料的格子隔多久再看。 */
    private static final int RECHECK = 100;
    /** 找不到活时歇多久。 */
    private static final int IDLE = 20;
    private static final int SUMMARY_INTERVAL = 20;
    /** 找第一个任务时最多看几格不合适的。 */
    private static final int ATTEMPTS = 64;

    private static final Map<UUID, Crew> CREWS = new HashMap<>();

    /** 一名玩家的无人机和同步状态。 */
    private static final class Crew {
        final DroneFleet fleet = new DroneFleet();
        int[] flyers = new int[0];
        long idleUntil;
        final Map<Item, Integer> reserved = new HashMap<>();
        /** 因为缺它而跳过了格子的材料；补上了就不等 RECHECK。 */
        final Set<Item> lacking = new HashSet<>();
        /** 发过的工地 → 发的时候的版本。 */
        final Map<Integer, Integer> known = new HashMap<>();
        @Nullable ResourceKey<Level> dimension;
        int left;
        int missing = -1;
    }

    private Construction() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("construction", () -> tick(event.getServer())));
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Crew crew = CREWS.remove(player.getUUID());
                if (crew != null) {
                    dock(player, crew);
                }
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                reset(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                reset(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                reset(player);
            }
        });
        gameBus.addListener(BlockEvent.EntityPlaceEvent.class, Construction::placedByHand);
        gameBus.addListener(ServerStoppedEvent.class, event -> CREWS.clear());
    }

    private static Crew crew(ServerPlayer player) {
        return CREWS.computeIfAbsent(player.getUUID(), id -> new Crew());
    }

    /** 进入维度、登录：客户端清掉所有虚影，接下来重新发。 */
    private static void reset(ServerPlayer player) {
        Crew crew = crew(player);
        crew.known.clear();
        PacketDistributor.sendToPlayer(player, new GhostsPayload(true, List.of(), List.of(), List.of()));
    }

    // ---- 放蓝图

    /**
     * 生存模式放蓝图：留下一个工地。
     *
     * @return 放下了（没超过"批量建造"的上限）
     */
    static boolean paste(ServerPlayer player, BlueprintData.Stored stored, BlockPos anchor, int turns) {
        ServerLevel level = player.level();
        int limit = Upgrades.buildLimit(Research.level(level, Upgrades.MASS_CONSTRUCTION));
        if (stored.blocks() > limit) {
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.over_limit", stored.blocks(), limit)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        List<Blueprints.Placement> placements = Blueprints.placements(stored, anchor, turns);
        if (placements.isEmpty()) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (Blueprints.Placement placement : placements) {
            minX = Math.min(minX, placement.pos().getX());
            minY = Math.min(minY, placement.pos().getY());
            minZ = Math.min(minZ, placement.pos().getZ());
        }
        BlockPos origin = new BlockPos(minX, minY, minZ);
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        int[] positions = new int[placements.size()];
        int[] states = new int[placements.size()];
        Map<Integer, CompoundTag> settings = new HashMap<>();
        for (int i = 0; i < placements.size(); i++) {
            Blueprints.Placement placement = placements.get(i);
            BlockPos rel = placement.pos().subtract(origin);
            positions[i] = BlueprintGeometry.pack(rel.getX(), rel.getY(), rel.getZ());
            states[i] = paletteIndex.computeIfAbsent(placement.state(), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            if (placement.settings() != null) {
                settings.put(i, placement.settings());
            }
        }
        BuildSites sites = BuildSites.get(level.getServer());
        BuildSite site = sites.add(player.getUUID(), level.dimension(), origin, palette, positions, states, settings);
        // 已经是这个样子的格子直接算建好
        for (int i = 0; i < site.size(); i++) {
            if (level.isLoaded(site.pos(i)) && level.getBlockState(site.pos(i)) == site.state(i)) {
                sites.finish(site, i, false);
            }
        }
        if (sites.site(site.id()) == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.blueprint.already_built"));
            return true;
        }
        player.sendSystemMessage(Component.translatable("message.universe.blueprint.site", site.left()));
        if (!Mecha.chest(player)) {
            player.sendSystemMessage(Component.translatable("message.universe.blueprint.need_chest").withStyle(ChatFormatting.YELLOW));
        }
        crew(player).idleUntil = 0;
        return true;
    }

    /** 机甲面板的"清空工地"：去掉自己在这个维度的所有工地。 */
    public static void cancel(ServerPlayer player) {
        BuildSites sites = BuildSites.get(player.level().getServer());
        int count = 0;
        for (BuildSite site : sites.in(player.level().dimension())) {
            if (site.owner().equals(player.getUUID())) {
                sites.remove(site);
                count++;
            }
        }
        Crew crew = crew(player);
        applyChanges(player, crew, crew.fleet.recall(new SitePlanner(player, crew, sites)));
        player.sendOverlayMessage(Component.translatable("message.universe.blueprint.cancelled", count));
    }

    /** 自己动手放的方块盖在虚影上：放的就是要的方块时这一格算建好。 */
    private static void placedByHand(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BuildSites sites = BuildSites.get(level.getServer());
        long[] ghost = sites.ghostAt(level.dimension(), event.getPos());
        if (ghost == null) {
            return;
        }
        BuildSite site = sites.site((int) ghost[0]);
        if (site != null && event.getPlacedBlock() == site.state((int) ghost[1])) {
            finish(level.getServer(), sites, site, (int) ghost[1], false);
        }
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        BuildSites sites = BuildSites.get(server);
        int now = server.getTickCount();
        if (now % RECHECK == 0) {
            for (BuildSite site : sites.all()) {
                site.skipped.clear();
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, sites, now);
            if (now % SYNC_INTERVAL == 0) {
                sync(player, sites);
            }
        }
    }

    private static void tickPlayer(ServerPlayer player, BuildSites sites, int now) {
        Crew crew = crew(player);
        ServerLevel level = player.level();
        if (crew.dimension != level.dimension()) {
            if (crew.dimension != null) {
                dock(player, crew);
            }
            crew.dimension = level.dimension();
        }
        boolean chest = Mecha.chest(player) || player.isCreative();
        int count = Upgrades.drones(Research.level(level, Upgrades.COMMUNICATION_CONTROL));
        int tasks = Upgrades.droneTasks(Research.level(level, Upgrades.COMMUNICATION_CONTROL));
        int speedMps = Upgrades.droneSpeed(Research.level(level, Upgrades.DRONE_ENGINE));
        double speed = speedMps / 20.0;
        if (now % SUMMARY_INTERVAL == 0) {
            refilled(player, crew, sites);
        }
        SitePlanner planner = new SitePlanner(player, crew, sites);
        if (!chest && crew.fleet.flying() > 0) {
            applyChanges(player, crew, crew.fleet.recall(planner));
        }
        if (crew.fleet.flying() == 0 && (!chest || player.isSpectator() || level.getGameTime() < crew.idleUntil)) {
            if (now % SUMMARY_INTERVAL == 0) {
                summary(player, crew, sites);
            }
            return;
        }
        boolean launch = chest && Mecha.powered(player) && !player.isSpectator() && level.getGameTime() >= crew.idleUntil;
        applyChanges(player, crew, crew.fleet.tick(player.getX(), player.getY() + 1.4, player.getZ(), count, speed, tasks, launch, planner));
        int flying = crew.fleet.flying();
        if (flying > 0) {
            Mecha.draw(player, flying * MechaEnergy.drone(speedMps));
        }
        if (now % SUMMARY_INTERVAL == 0) {
            summary(player, crew, sites);
        }
    }

    /** 缺的材料到了背包或货舱：把跳过的格子马上放出来，无人机不用等到下一轮。 */
    private static void refilled(ServerPlayer player, Crew crew, BuildSites sites) {
        if (crew.lacking.isEmpty()
                || !crew.lacking.removeIf(item -> available(player, item) - crew.reserved.getOrDefault(item, 0) > 0)) {
            return;
        }
        for (BuildSite site : sites.in(player.level().dimension())) {
            if (mayBuild(player, site)) {
                site.skipped.clear();
            }
        }
        crew.idleUntil = 0;
    }

    /** 玩家下线、换维度：没建的还回去，航段收掉。 */
    private static void dock(ServerPlayer player, Crew crew) {
        BuildSites sites = BuildSites.get(player.level().getServer());
        crew.fleet.dock(new SitePlanner(player, crew, sites));
        if (crew.dimension != null) {
            for (int i = 0; i < crew.flyers.length; i++) {
                if (crew.flyers[i] != 0) {
                    Flyers.land(crew.dimension, crew.flyers[i]);
                    crew.flyers[i] = 0;
                }
            }
        }
        crew.reserved.clear();
        crew.lacking.clear();
    }

    private static void applyChanges(ServerPlayer player, Crew crew, List<DroneFleet.Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        if (crew.flyers.length < crew.fleet.drones().size()) {
            crew.flyers = java.util.Arrays.copyOf(crew.flyers, crew.fleet.drones().size());
        }
        BuildSites sites = BuildSites.get(level.getServer());
        long now = level.getGameTime();
        float speed = Upgrades.droneSpeed(Research.level(level, Upgrades.DRONE_ENGINE)) / 20F;
        for (DroneFleet.Change change : changes) {
            int d = change.drone();
            if (change.state() == DroneFleet.State.DOCKED) {
                if (crew.flyers[d] != 0) {
                    Flyers.land(level.dimension(), crew.flyers[d]);
                    crew.flyers[d] = 0;
                }
                continue;
            }
            if (crew.flyers[d] == 0) {
                crew.flyers[d] = Flyers.nextId();
            }
            FlyersPayload.Flyer flyer = switch (change.state()) {
                case OUTBOUND -> {
                    DroneFleet.Task task = change.target();
                    BuildSite site = sites.site(task.site());
                    Item cost = site == null ? null : site.cost(task.index());
                    yield new FlyersPayload.Flyer(crew.flyers[d], FlyersPayload.DRONE, change.x(), change.y(), change.z(), now, speed, -1,
                            task.x() + 0.5, task.y() + 0.5, task.z() + 0.5, cost == null ? -1 : BuiltInRegistries.ITEM.getId(cost));
                }
                case BUILDING -> new FlyersPayload.Flyer(crew.flyers[d], FlyersPayload.DRONE, change.x(), change.y(), change.z(), now, 0, -1,
                        change.x(), change.y(), change.z(), -1);
                default -> new FlyersPayload.Flyer(crew.flyers[d], FlyersPayload.DRONE, change.x(), change.y(), change.z(), now, speed,
                        player.getId(), player.getX(), player.getY() + 1.4, player.getZ(), -1);
            };
            Flyers.fly(level, flyer);
        }
    }

    // ---- 选任务、建

    /** 能不能建这个工地：自己的，或者同一队伍的人的。 */
    private static boolean mayBuild(ServerPlayer player, BuildSite site) {
        if (site.owner().equals(player.getUUID())) {
            return true;
        }
        PlayerTeam team = player.getTeam();
        ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(site.owner());
        return team != null && owner != null && owner.getTeam() == team;
    }

    private enum Check {
        OK, DONE, SKIP
    }

    private static final class SitePlanner implements DroneFleet.Planner {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final Crew crew;
        private final BuildSites sites;
        private @Nullable List<BuildSite> mine;

        SitePlanner(ServerPlayer player, Crew crew, BuildSites sites) {
            this.player = player;
            this.level = player.level();
            this.crew = crew;
            this.sites = sites;
        }

        private List<BuildSite> mine() {
            if (this.mine == null) {
                List<BuildSite> list = new ArrayList<>();
                for (BuildSite site : this.sites.in(this.level.dimension())) {
                    if (mayBuild(this.player, site)) {
                        list.add(site);
                    }
                }
                this.mine = list;
            }
            return this.mine;
        }

        @Override
        public List<DroneFleet.Task> next(double x, double y, double z, int max) {
            DroneFleet.Task first = null;
            for (int attempt = 0; attempt < ATTEMPTS && first == null; attempt++) {
                long[] best = this.best(x, z);
                if (best == null) {
                    break;
                }
                BuildSite site = this.sites.site((int) best[0]);
                int index = (int) best[1];
                if (site != null && this.check(site, index) == Check.OK) {
                    BlockPos pos = site.pos(index);
                    first = new DroneFleet.Task(site.id(), index, pos.getX(), pos.getY(), pos.getZ());
                }
            }
            if (first == null) {
                this.crew.idleUntil = this.level.getGameTime() + IDLE;
                return List.of();
            }
            List<DroneFleet.Task> candidates = new ArrayList<>();
            candidates.add(first);
            List<DroneFleet.Task> neighbours = new ArrayList<>();
            int r = DroneFleet.BATCH_RADIUS;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        cursor.set(first.x() + dx, first.y() + dy, first.z() + dz);
                        long[] ghost = this.sites.ghostAt(this.level.dimension(), cursor);
                        if (ghost == null) {
                            continue;
                        }
                        BuildSite site = this.sites.site((int) ghost[0]);
                        int index = (int) ghost[1];
                        if (site == null || !site.isRemaining(index) || site.claimed.get(index) || site.skipped.get(index)
                                || !mayBuild(this.player, site) || this.check(site, index) != Check.OK) {
                            continue;
                        }
                        neighbours.add(new DroneFleet.Task(site.id(), index, cursor.getX(), cursor.getY(), cursor.getZ()));
                    }
                }
            }
            DroneFleet.Task origin = first;
            neighbours.sort(Comparator.comparingInt(t -> Math.abs(t.x() - origin.x()) + Math.abs(t.y() - origin.y()) * 2
                    + Math.abs(t.z() - origin.z())));
            candidates.addAll(neighbours);
            List<DroneFleet.Task> batch = new ArrayList<>();
            for (DroneFleet.Task task : DroneFleet.batch(candidates, max)) {
                BuildSite site = this.sites.site(task.site());
                if (site == null) {
                    continue;
                }
                Item cost = site.cost(task.index());
                if (cost != null && !this.player.isCreative()) {
                    if (available(this.player, cost) - this.crew.reserved.getOrDefault(cost, 0) <= 0) {
                        site.skipped.set(task.index());
                        this.crew.lacking.add(cost);
                        continue;
                    }
                    this.crew.reserved.merge(cost, 1, Integer::sum);
                }
                site.claimed.set(task.index());
                batch.add(task);
            }
            if (batch.isEmpty()) {
                this.crew.idleUntil = this.level.getGameTime() + IDLE;
            }
            return batch;
        }

        /** 离玩家最近、最低的一格还没认领的虚影（先比高度，再比水平距离）。 */
        private long @Nullable [] best(double x, double z) {
            long[] best = null;
            int bestY = Integer.MAX_VALUE;
            double bestDistance = Double.MAX_VALUE;
            double reach = DroneFleet.REACH * DroneFleet.REACH;
            for (BuildSite site : this.mine()) {
                if (this.sites.site(site.id()) != site) {
                    continue;
                }
                int[] bounds = site.bounds();
                double cx = Math.clamp(x, bounds[0], bounds[2] + 1);
                double cz = Math.clamp(z, bounds[1], bounds[3] + 1);
                if ((cx - x) * (cx - x) + (cz - z) * (cz - z) > reach) {
                    continue;
                }
                for (int i = site.remaining().nextSetBit(0); i >= 0; i = site.remaining().nextSetBit(i + 1)) {
                    if (site.claimed.get(i) || site.skipped.get(i)) {
                        continue;
                    }
                    BlockPos pos = site.pos(i);
                    double dx = pos.getX() + 0.5 - x;
                    double dz = pos.getZ() + 0.5 - z;
                    double distance = dx * dx + dz * dz;
                    if (distance > reach) {
                        continue;
                    }
                    if (pos.getY() < bestY || pos.getY() == bestY && distance < bestDistance) {
                        best = new long[] {site.id(), i};
                        bestY = pos.getY();
                        bestDistance = distance;
                    }
                }
            }
            return best;
        }

        private Check check(BuildSite site, int index) {
            BlockPos pos = site.pos(index);
            if (!this.level.isLoaded(pos) || !this.level.isInWorldBounds(pos)) {
                site.skipped.set(index);
                return Check.SKIP;
            }
            BlockState current = this.level.getBlockState(pos);
            BlockState target = site.state(index);
            if (current == target) {
                finish(this.level.getServer(), this.sites, site, index, false);
                return Check.DONE;
            }
            if (!current.canBeReplaced() || !this.level.isUnobstructed(target, pos, CollisionContext.empty())) {
                site.skipped.set(index);
                return Check.SKIP;
            }
            Item cost = site.cost(index);
            if (cost == null && !Blueprints.partnerPlaced(this.level, pos, target)) {
                site.skipped.set(index);
                return Check.SKIP;
            }
            if (cost != null && !this.player.isCreative() && available(this.player, cost) - this.crew.reserved.getOrDefault(cost, 0) <= 0) {
                site.skipped.set(index);
                this.crew.lacking.add(cost);
                return Check.SKIP;
            }
            return Check.OK;
        }

        @Override
        public boolean build(DroneFleet.Task task) {
            BuildSite site = this.sites.site(task.site());
            int index = task.index();
            if (site == null) {
                return false;
            }
            Item cost = site.cost(index);
            this.unreserve(cost);
            site.claimed.clear(index);
            if (!site.isRemaining(index)) {
                return false;
            }
            BlockPos pos = site.pos(index);
            BlockState target = site.state(index);
            BlockState current = this.level.getBlockState(pos);
            if (current == target) {
                finish(this.level.getServer(), this.sites, site, index, false);
                return true;
            }
            if (!current.canBeReplaced() || !this.level.isUnobstructed(target, pos, CollisionContext.empty())
                    || cost == null && !Blueprints.partnerPlaced(this.level, pos, target)
                    || cost != null && !this.player.isCreative() && !Blueprints.take(this.player, cost)) {
                site.skipped.set(index);
                return false;
            }
            this.level.setBlock(pos, target, Block.UPDATE_ALL);
            CompoundTag settings = site.settings(index);
            if (settings != null && this.level.getBlockEntity(pos) instanceof BlueprintSettings receiver) {
                receiver.applyBlueprintSettings(settings);
            }
            this.level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
            this.level.playSound(null, pos, target.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
            finish(this.level.getServer(), this.sites, site, index, true);
            return true;
        }

        @Override
        public void release(DroneFleet.Task task) {
            BuildSite site = this.sites.site(task.site());
            if (site != null) {
                site.claimed.clear(task.index());
                this.unreserve(site.cost(task.index()));
            }
        }

        private void unreserve(@Nullable Item cost) {
            if (cost != null) {
                this.crew.reserved.computeIfPresent(cost, (item, n) -> n > 1 ? n - 1 : null);
            }
        }
    }

    /** 一格建好；工地建完了告诉主人。 */
    private static void finish(MinecraftServer server, BuildSites sites, BuildSite site, int index, boolean byDrone) {
        if (sites.finish(site, index, byDrone)) {
            ServerPlayer owner = server.getPlayerList().getPlayer(site.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("message.universe.blueprint.done", site.placed()).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** 背包加货舱里有几个（不算蓝图本身）。 */
    public static int available(ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item) && !(stack.getItem() instanceof BlueprintItem)) {
                count += stack.getCount();
            }
        }
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        for (int i = 0; i < data.cargoSlots(); i++) {
            ItemStack stack = data.cargo().get(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // ---- HUD 和同步

    /** 自己能建的工地还剩几格，缺得最多的是哪种材料。 */
    private static void summary(ServerPlayer player, Crew crew, BuildSites sites) {
        int left = 0;
        Map<Item, Integer> needed = new HashMap<>();
        for (BuildSite site : sites.in(player.level().dimension())) {
            if (!mayBuild(player, site)) {
                continue;
            }
            left += site.left();
            for (int i = site.remaining().nextSetBit(0); i >= 0; i = site.remaining().nextSetBit(i + 1)) {
                Item cost = site.cost(i);
                if (cost != null) {
                    needed.merge(cost, 1, Integer::sum);
                }
            }
        }
        int missing = -1;
        int worst = 0;
        if (!player.isCreative()) {
            for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
                int shortfall = entry.getValue() - available(player, entry.getKey());
                if (shortfall > worst) {
                    worst = shortfall;
                    missing = BuiltInRegistries.ITEM.getId(entry.getKey());
                }
            }
        }
        crew.left = left;
        crew.missing = missing;
    }

    /** HUD：还剩几格。 */
    public static int left(ServerPlayer player) {
        Crew crew = CREWS.get(player.getUUID());
        return crew == null ? 0 : crew.left;
    }

    /** HUD：缺的材料（数字 ID），不缺时 -1。 */
    public static int missing(ServerPlayer player) {
        Crew crew = CREWS.get(player.getUUID());
        return crew == null ? -1 : crew.missing;
    }

    /** HUD：有几架在飞。 */
    public static int flying(ServerPlayer player) {
        Crew crew = CREWS.get(player.getUUID());
        return crew == null ? 0 : crew.fleet.flying();
    }

    private static void sync(ServerPlayer player, BuildSites sites) {
        Crew crew = crew(player);
        List<GhostsPayload.Site> full = new ArrayList<>();
        List<GhostsPayload.Progress> progress = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();
        Set<Integer> visible = new HashSet<>();
        double x = player.getX();
        double z = player.getZ();
        for (BuildSite site : sites.in(player.level().dimension())) {
            int[] bounds = site.bounds();
            double cx = Math.clamp(x, bounds[0], bounds[2] + 1);
            double cz = Math.clamp(z, bounds[1], bounds[3] + 1);
            if ((cx - x) * (cx - x) + (cz - z) * (cz - z) > SYNC_RANGE * SYNC_RANGE) {
                continue;
            }
            visible.add(site.id());
            Integer sent = crew.known.get(site.id());
            if (sent == null) {
                int[] palette = new int[site.palette().size()];
                for (int i = 0; i < palette.length; i++) {
                    palette[i] = Block.getId(site.palette().get(i));
                }
                full.add(new GhostsPayload.Site(site.id(), site.origin(), palette, site.positions(), site.states(), site.remaining().toLongArray()));
            } else if (sent != site.version) {
                progress.add(new GhostsPayload.Progress(site.id(), site.remaining().toLongArray()));
            }
            crew.known.put(site.id(), site.version);
        }
        Iterator<Integer> iterator = crew.known.keySet().iterator();
        while (iterator.hasNext()) {
            int id = iterator.next();
            if (!visible.contains(id)) {
                removed.add(id);
                iterator.remove();
            }
        }
        if (!full.isEmpty() || !progress.isEmpty() || !removed.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new GhostsPayload(false, full, progress, removed));
        }
    }
}
