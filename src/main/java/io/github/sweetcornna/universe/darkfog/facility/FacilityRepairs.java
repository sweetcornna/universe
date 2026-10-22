package io.github.sweetcornna.universe.darkfog.facility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.darkfog.FacilityHealth;
import io.github.sweetcornna.universe.core.darkfog.RepairQueue;
import io.github.sweetcornna.universe.core.mecha.DroneFleet;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.darkfog.FacilityData;
import io.github.sweetcornna.universe.network.FacilityPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * 设施打掉以后（ADR-0028 §四）：拆成残影、按残影重建；建造无人机和战场分析基地的修理；把附近掉血的设施同步给客户端。
 */
public final class FacilityRepairs {
    /** 建造无人机的任务里"修"和"重建"用的工地编号（真的工地编号从 0 起）。 */
    public static final int REPAIR_SITE = -1;
    public static final int REBUILD_SITE = -2;
    /** 黑雾的掉落：生成时给掉落物打上这个标签，战场分析基地的"只捡黑雾掉落"认它。 */
    public static final String DARK_FOG_LOOT_TAG = "universe.dark_fog_loot";
    /** 每隔多少刻把附近掉血的设施整份发一次。 */
    private static final int SYNC_INTERVAL = 40;
    /** 发给多远以内的玩家。 */
    private static final double SYNC_RANGE = 48;
    private static final double UPDATE_RANGE = 64;

    /** 上一次整份发过、而且发的不是空的玩家（下次没有了也要发一份空的清掉）。 */
    private static final Set<UUID> TOLD = new HashSet<>();

    private FacilityRepairs() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlockEntity(Capabilities.Item.BLOCK,
                UniverseBlockEntities.BATTLEFIELD_ANALYSIS_BASE.get(), WorldlyContainerWrapper::new));
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
            if (server.getTickCount() % SYNC_INTERVAL == 0) {
                ServerPerf.time("facility_sync", () -> sync(server));
            }
        });
        // 只有黑雾能打掉设施：爆炸（苦力怕、TNT、玩家自己的爆破）炸不掉
        gameBus.addListener(ExplosionEvent.Detonate.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                event.getAffectedBlocks().removeIf(pos -> Facilities.isFacility(level.getBlockState(pos)));
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> TOLD.clear());
    }

    // ---- 打掉、重建

    /** 打掉一个设施：方块换成残影（不掉方块本身），里面的东西照拆方块的规矩掉出来；物品上带走的东西（物流站的账本）留在残影里。 */
    public static void destroy(ServerLevel level, BlockPos origin, BlockState state) {
        DataComponentPatch kept = DataComponentPatch.EMPTY;
        Item item = state.getBlock().asItem();
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (blockEntity != null && item != Items.AIR) {
            for (ItemStack drop : Block.getDrops(state, level, origin, blockEntity)) {
                if (drop.is(item) && !drop.getComponentsPatch().isEmpty()) {
                    kept = drop.getComponentsPatch();
                    break;
                }
            }
        }
        // 碎块的粒子和声音
        level.levelEvent(2001, origin, Block.getId(state));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.getX() + 0.5, origin.getY() + 0.8, origin.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        level.setBlock(origin, UniverseBlocks.FACILITY_GHOST.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(origin) instanceof FacilityGhostBlockEntity ghost) {
            ghost.set(state, kept);
            FacilityData.get(level).addGhost(origin.asLong());
        }
        healthChanged(level, origin, 1);
    }

    /**
     * 按残影把设施放回去：朝向照原来的，存在残影里的东西还回去。
     *
     * @param player 放的人（手动放回、无人机的主人）；战场分析基地放的是空
     * @return 放回去了；残影上面被挡住、有人站在里面时放不了
     */
    public static boolean rebuild(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost)) {
            return false;
        }
        BlockState state = ghost.original();
        if (state.isAir()) {
            level.removeBlock(pos, false);
            return false;
        }
        DataComponentPatch contents = ghost.contents();
        if (ghost.tall() && !level.getBlockState(pos.above()).canBeReplaced()) {
            return false;
        }
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        stack.applyComponents(contents);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && !contents.isEmpty()) {
            blockEntity.applyComponentsFromItemStack(stack);
            blockEntity.setChanged();
        }
        state.getBlock().setPlacedBy(level, pos, state, player, stack);
        FacilityData data = FacilityData.get(level);
        data.clearHealth(pos.asLong());
        data.removeGhost(pos.asLong());
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.1F);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.35, 0.35, 0.35, 0.01);
        return true;
    }

    /** 残影要的方块物品；不是残影时是空气。 */
    public static Item needed(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FacilityGhostBlockEntity ghost ? ghost.original().getBlock().asItem() : Items.AIR;
    }

    // ---- 战场分析基地

    /** 一次修理的结果。 */
    record Work(int repaired, int damaged) {
    }

    /** 范围内掉血最多的几个各修一点。 */
    static Work repairAround(ServerLevel level, BlockPos center, double range, float amount, int targets) {
        FacilityData data = FacilityData.get(level);
        if (!data.anyDamaged()) {
            return new Work(0, 0);
        }
        List<RepairQueue.Candidate> candidates = candidates(level, data, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, false);
        List<RepairQueue.Candidate> picked = RepairQueue.pick(candidates, range * range, targets);
        int repaired = 0;
        for (RepairQueue.Candidate candidate : picked) {
            BlockPos pos = BlockPos.of(candidate.key());
            if (Facilities.repair(level, pos, amount) > 0) {
                repaired++;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.05);
            }
        }
        int damaged = 0;
        for (RepairQueue.Candidate candidate : candidates) {
            if (candidate.distanceSq() <= range * range) {
                damaged++;
            }
        }
        return new Work(repaired, damaged);
    }

    /**
     * 范围内的残影，用储物格里的方块重建一个。
     *
     * @return 范围内的残影数，重建了一个时是负的（-1 - 剩下的数）
     */
    static int rebuildFrom(ServerLevel level, BlockPos center, double range, Container storage) {
        FacilityData data = FacilityData.get(level);
        int count = 0;
        BlockPos best = null;
        int bestSlot = -1;
        double bestDistance = range * range;
        for (long key : List.copyOf(data.ghosts())) {
            BlockPos pos = BlockPos.of(key);
            double distance = pos.distSqr(center);
            if (distance > range * range || !level.isLoaded(pos)) {
                continue;
            }
            count++;
            if (data.claimed(key) || distance > bestDistance) {
                continue;
            }
            Item item = needed(level, pos);
            int slot = find(storage, item);
            if (slot >= 0) {
                best = pos;
                bestSlot = slot;
                bestDistance = distance;
            }
        }
        if (best != null && rebuild(level, best, null)) {
            storage.removeItem(bestSlot, 1);
            return -1 - (count - 1);
        }
        return count;
    }

    private static int find(Container storage, Item item) {
        if (item == Items.AIR) {
            return -1;
        }
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (storage.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** 黑雾的掉落：那几种零件和矩阵，或者打了标签的掉落物（沙土之类）。 */
    public static boolean darkFogLoot(ItemEntity entity) {
        if (entity.entityTags().contains(DARK_FOG_LOOT_TAG)) {
            return true;
        }
        ItemStack stack = entity.getItem();
        return stack.is(UniverseItems.ENERGY_SHARD.get()) || stack.is(UniverseItems.SILICON_NEURON.get())
                || stack.is(UniverseItems.MATTER_RECOMBINATOR.get()) || stack.is(UniverseItems.NEGENTROPY_SINGULARITY.get())
                || stack.is(UniverseItems.CORE_ELEMENT.get()) || stack.is(UniverseItems.DARK_FOG_MATRIX.get());
    }

    // ---- 建造无人机（ADR-0023）：蓝图的工地都建完了，就顺手修附近掉血的设施、按残影重建

    /** 给一架无人机找活：先重建（背包或货舱里有那种方块），再修掉血最多的。认领下来。 */
    public static List<DroneFleet.Task> droneTasks(ServerPlayer player, double x, double y, double z) {
        ServerLevel level = player.level();
        FacilityData data = FacilityData.get(level);
        double reach = DroneFleet.REACH * DroneFleet.REACH;
        BlockPos ghost = null;
        double ghostDistance = reach;
        for (long key : data.ghosts()) {
            if (data.claimed(key)) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            double dx = pos.getX() + 0.5 - x;
            double dz = pos.getZ() + 0.5 - z;
            double distance = dx * dx + dz * dz;
            if (distance > ghostDistance || !level.isLoaded(pos)) {
                continue;
            }
            Item item = needed(level, pos);
            if (item != Items.AIR && (player.isCreative() || Construction.available(player, item) > 0)) {
                ghost = pos;
                ghostDistance = distance;
            }
        }
        if (ghost != null && data.claim(ghost.asLong())) {
            return List.of(new DroneFleet.Task(REBUILD_SITE, 0, ghost.getX(), ghost.getY(), ghost.getZ()));
        }
        if (!data.anyDamaged()) {
            return List.of();
        }
        List<RepairQueue.Candidate> candidates = candidates(level, data, x, y, z, true);
        for (RepairQueue.Candidate candidate : RepairQueue.pick(candidates, reach, candidates.size())) {
            if (data.claim(candidate.key())) {
                BlockPos pos = BlockPos.of(candidate.key());
                return List.of(new DroneFleet.Task(REPAIR_SITE, 0, pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return List.of();
    }

    /** 无人机到了：修一趟，或者从背包里拿一个方块重建。 */
    public static boolean droneBuild(ServerPlayer player, DroneFleet.Task task) {
        ServerLevel level = player.level();
        BlockPos pos = new BlockPos(task.x(), task.y(), task.z());
        FacilityData.get(level).release(pos.asLong());
        if (task.site() == REPAIR_SITE) {
            if (Facilities.repair(level, pos, FacilityHealth.DRONE_REPAIR) <= 0) {
                return false;
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.05);
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.25F, 1.6F);
            return true;
        }
        Item item = needed(level, pos);
        if (item == Items.AIR || !player.isCreative() && Construction.available(player, item) <= 0) {
            return false;
        }
        if (!rebuild(level, pos, player)) {
            return false;
        }
        if (!player.isCreative()) {
            Blueprints.take(player, item);
        }
        return true;
    }

    /** 没去成的任务还回去。 */
    public static void droneRelease(ServerLevel level, DroneFleet.Task task) {
        FacilityData.get(level).release(BlockPos.asLong(task.x(), task.y(), task.z()));
    }

    private static List<RepairQueue.Candidate> candidates(ServerLevel level, FacilityData data, double x, double y, double z, boolean horizontal) {
        List<RepairQueue.Candidate> candidates = new ArrayList<>();
        for (FacilityData.Entry entry : data.damaged()) {
            BlockPos pos = BlockPos.of(entry.pos());
            if (!level.isLoaded(pos) || data.claimed(entry.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!Facilities.isFacility(state)) {
                data.clearHealth(entry.pos());
                continue;
            }
            double dx = pos.getX() + 0.5 - x;
            double dy = horizontal ? 0 : pos.getY() + 0.5 - y;
            double dz = pos.getZ() + 0.5 - z;
            candidates.add(new RepairQueue.Candidate(entry.pos(), dx * dx + dy * dy + dz * dz,
                    FacilityHealth.ratio(entry.health(), Facilities.maxHealth(state))));
        }
        return candidates;
    }

    // ---- 同步

    /** 一个设施的耐久变了：告诉附近的人。 */
    public static void healthChanged(ServerLevel level, BlockPos origin, float ratio) {
        PacketDistributor.sendToPlayersNear(level, null, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, UPDATE_RANGE,
                new FacilityPayloads.Health(List.of(origin.asLong()), List.of(ratio), false));
    }

    /** 定时整份发一次附近掉血的设施（刚走近的人也能看到）。 */
    private static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            FacilityData data = FacilityData.get(level);
            List<Long> positions = new ArrayList<>();
            List<Float> ratios = new ArrayList<>();
            if (data.anyDamaged()) {
                for (FacilityData.Entry entry : data.damaged()) {
                    BlockPos pos = BlockPos.of(entry.pos());
                    if (pos.distToCenterSqr(player.position()) > SYNC_RANGE * SYNC_RANGE || !level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!Facilities.isFacility(state)) {
                        data.clearHealth(entry.pos());
                        continue;
                    }
                    positions.add(entry.pos());
                    ratios.add(FacilityHealth.ratio(entry.health(), Facilities.maxHealth(state)));
                }
            }
            boolean told = TOLD.contains(player.getUUID());
            if (positions.isEmpty() && !told) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new FacilityPayloads.Health(positions, ratios, true));
            if (positions.isEmpty()) {
                TOLD.remove(player.getUUID());
            } else {
                TOLD.add(player.getUUID());
            }
        }
    }
}
