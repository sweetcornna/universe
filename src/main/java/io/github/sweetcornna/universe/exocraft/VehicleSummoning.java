package io.github.sweetcornna.universe.exocraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.vehicle.VehicleKind;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import io.github.sweetcornna.universe.network.VehiclePayloads;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 载具召唤台（ADR-0026）：右键列出你放在世界里的车（{@link VehicleRoster}），选一辆叫到台子旁边。
 * <ul>
 *   <li>同一个维度：直接挪过来。车所在的区块没加载就先加载（几秒内找到）。</li>
 *   <li>别的维度：那边的车拆成物品，在这边重新放下，燃料、货物、名字都在。</li>
 *   <li>潜艇只能叫到水里：台子旁边要有水。</li>
 * </ul>
 * 收成物品放在背包里的车不在名册上，叫不来。
 */
public final class VehicleSummoning {
    /** 离召唤台多远还能用（格）。 */
    static final double REACH = 8;
    /** 在台子周围多远以内找空地（格）。 */
    static final int SEARCH_RADIUS = 5;
    /** 区块没加载时最多等多少刻。 */
    static final int WAIT_TICKS = 200;

    /** 召唤的结果。 */
    public enum Result {
        /** 车到了。 */
        ARRIVED,
        /** 车所在的区块正在加载，过一会儿再试。 */
        LOADING,
        /** 名册上没有，或者那个维度、那辆车已经不在了。 */
        LOST,
        /** 台子旁边放不下。 */
        NO_ROOM,
        /** 潜艇：台子旁边没有水。 */
        NEED_WATER
    }

    /** 等区块加载的召唤。 */
    private record Pending(UUID player, ResourceKey<Level> level, BlockPos summoner, UUID vehicle, long deadline) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private VehicleSummoning() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
    }

    // ---- 列表

    /** 右键召唤台：把名册上你的车发给你的客户端，打开选车的界面。 */
    public static void open(ServerPlayer player, BlockPos summoner) {
        List<VehiclePayloads.Vehicle> list = new ArrayList<>();
        String here = player.level().dimension().identifier().toString();
        for (Map.Entry<UUID, VehicleRoster.Entry> entry : VehicleRoster.get(player.level().getServer()).vehicles(player.getUUID())) {
            VehicleRoster.Entry value = entry.getValue();
            int distance = value.dimension().equals(here) ? (int) Math.round(Math.sqrt(value.pos().distSqr(summoner))) : -1;
            list.add(new VehiclePayloads.Vehicle(entry.getKey(), value.kind(), value.name(), value.dimension(), distance));
        }
        // 同一个维度的在前、由近到远；别的维度的排在后面
        list.sort(Comparator.comparingInt((VehiclePayloads.Vehicle v) -> v.distance() < 0 ? 1 : 0)
                .thenComparingInt(VehiclePayloads.Vehicle::distance).thenComparing(VehiclePayloads.Vehicle::kind));
        PacketDistributor.sendToPlayer(player, new VehiclePayloads.Listing(summoner, list.size() > 64 ? list.subList(0, 64) : list));
    }

    /** 客户端选了一辆车。校验：人在召唤台旁边、那一格真是召唤台、车在他的名册上。 */
    public static void handleSummon(VehiclePayloads.Summon payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.level();
        BlockPos summoner = payload.summoner();
        if (!level.isLoaded(summoner) || !level.getBlockState(summoner).is(UniverseBlocks.VEHICLE_SUMMONER.get())
                || player.distanceToSqr(Vec3.atCenterOf(summoner)) > REACH * REACH) {
            return;
        }
        Result result = summon(level, summoner, player.getUUID(), payload.vehicle());
        if (result == Result.LOADING) {
            PENDING.removeIf(pending -> pending.player().equals(player.getUUID()));
            PENDING.add(new Pending(player.getUUID(), level.dimension(), summoner, payload.vehicle(), level.getGameTime() + WAIT_TICKS));
        }
        report(player, result);
    }

    private static void report(ServerPlayer player, Result result) {
        player.sendOverlayMessage(Component.translatable("message.universe.summoner." + result.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** 等区块加载的召唤：每刻再找一次，找到了就叫过来，超时就算丢了。 */
    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            ServerLevel level = server.getLevel(pending.level());
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (level == null || player == null) {
                it.remove();
                continue;
            }
            Result result = summon(level, pending.summoner(), pending.player(), pending.vehicle());
            if (result == Result.LOADING && level.getGameTime() < pending.deadline()) {
                continue;
            }
            it.remove();
            report(player, result == Result.LOADING ? Result.LOST : result);
        }
    }

    // ---- 叫车

    /**
     * 把 {@code owner} 名册上的一辆车叫到召唤台旁边。
     *
     * @param level    召唤台所在的维度
     * @param summoner 召唤台的位置
     */
    public static Result summon(ServerLevel level, BlockPos summoner, UUID owner, UUID vehicleId) {
        MinecraftServer server = level.getServer();
        VehicleRoster roster = VehicleRoster.get(server);
        VehicleRoster.Entry entry = roster.entry(owner, vehicleId);
        if (entry == null) {
            return Result.LOST;
        }
        Identifier dimension = Identifier.tryParse(entry.dimension());
        ServerLevel there = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        VehicleKind kind = VehicleKind.byId(entry.kind());
        if (there == null || kind == null) {
            roster.remove(owner, vehicleId);
            return Result.LOST;
        }
        if (!(there.getEntity(vehicleId) instanceof UniverseVehicle vehicle) || vehicle.isRemoved()) {
            // 车在没加载的区块里：加载那一片（传送门的票，过一阵自己失效），过几刻再找
            there.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(entry.pos()), 1);
            return Result.LOADING;
        }
        Vec3 spot = spot(level, summoner, vehicle.getType().getDimensions(), kind.aquatic(), vehicle);
        if (spot == null) {
            return kind.aquatic() ? Result.NEED_WATER : Result.NO_ROOM;
        }
        float yaw = facing(level, summoner).toYRot();
        flash(there, vehicle.position());
        if (there == level) {
            vehicle.ejectPassengers();
            vehicle.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, 0, false);
            vehicle.setDeltaMovement(Vec3.ZERO);
            roster.update(owner, vehicle);
        } else {
            // 别的维度：那边拆成物品，这边重新放下
            vehicle.ejectPassengers();
            ItemStack stack = vehicle.toItem();
            vehicle.discard();
            Entity created = vehicle.getType().create(level, EntitySpawnReason.TRIGGERED);
            if (!(created instanceof UniverseVehicle moved)) {
                return Result.LOST;
            }
            moved.loadFrom(stack);
            moved.setOwner(owner);
            moved.snapTo(spot.x, spot.y, spot.z, yaw, 0);
            level.addFreshEntity(moved);
            roster.update(owner, moved);
        }
        flash(level, spot);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.8F, 0.8F);
        return Result.ARRIVED;
    }

    private static void flash(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 60, 1.0, 0.8, 1.0, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 12, 0.8, 0.5, 0.8, 0.02);
    }

    /** 召唤台正面朝哪：车优先停在正面。 */
    private static Direction facing(ServerLevel level, BlockPos summoner) {
        BlockState state = level.getBlockState(summoner);
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /**
     * 台子旁边一块放得下这辆车的地方（车底的中心），找不到是 null。陆上的车要停在实地上（悬浮车停在水面上也行），
     * 潜艇要整个泡在水里。离台子正面两格的地方最先试，再往外找。
     */
    static @Nullable Vec3 spot(ServerLevel level, BlockPos summoner, EntityDimensions size, boolean aquatic, @Nullable Entity ignore) {
        Direction front = facing(level, summoner);
        Vec3 prefer = Vec3.atBottomCenterOf(summoner).add(front.getStepX() * 2.5, 0, front.getStepZ() * 2.5);
        List<Vec3> candidates = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 2; dy >= -3; dy--) {
                    candidates.add(Vec3.atBottomCenterOf(summoner.offset(dx, dy, dz)));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(prefer)));
        for (Vec3 at : candidates) {
            AABB box = size.makeBoundingBox(at);
            if (!level.noCollision(ignore, box) || box.intersects(new AABB(summoner))) {
                continue;
            }
            BlockPos feet = BlockPos.containing(at.x, at.y + 0.1, at.z);
            if (aquatic) {
                BlockPos middle = BlockPos.containing(at.x, at.y + size.height() * 0.6, at.z);
                if (AcidFluid.sea(level.getFluidState(feet)) && AcidFluid.sea(level.getFluidState(middle))) {
                    return at;
                }
                continue;
            }
            BlockPos below = feet.below();
            if (level.getFluidState(feet).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return at;
            }
        }
        return null;
    }
}
