package io.github.sweetcornna.universe.block.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * 着陆台（ADR-0024）：接电网，给停在台子上方 {@link #REACH} 格内的船慢慢加满燃料；也是召唤飞船时的落点。
 * 每个世界里的着陆台记在一张表里，召唤时不用扫方块。
 */
public class LandingPadBlockEntity extends MachineBlockEntity {
    /** 加燃料时每刻用的电（焦耳/刻，600 W）。 */
    public static final long POWER = 30;
    /** 一点燃料要的电（焦耳）：600 W 时 2000 点的燃料舱 80 多秒加满。 */
    public static final double JOULES_PER_FUEL = 25;
    /** 台子上方几格内的船算停在台上。 */
    public static final int REACH = 4;
    /** 隔几刻找一次台上的船。 */
    private static final int SCAN_INTERVAL = 10;
    /** 船慢到这个速度以下才加燃料（格/刻）。 */
    private static final double PARKED_SPEED = 0.2;
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PADS = new ConcurrentHashMap<>();

    /** 放下它的玩家；召唤时只停自己的台子，没有主人的谁都能停。 */
    private @Nullable UUID owner;
    private @Nullable ShipEntity ship;
    private boolean refueling;

    public LandingPadBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.LANDING_PAD.get(), pos, state, 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            PADS.computeIfAbsent(serverLevel.dimension(), key -> ConcurrentHashMap.newKeySet()).add(this.worldPosition.immutable());
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            Set<BlockPos> pads = PADS.get(serverLevel.dimension());
            if (pads != null) {
                pads.remove(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    /** 服务器关掉时清空。 */
    public static void clear() {
        PADS.clear();
    }

    /** 这个世界里离 {@code center} 不到 {@code radius} 格的着陆台，近的在前。 */
    public static List<BlockPos> near(ServerLevel level, BlockPos center, double radius) {
        Set<BlockPos> pads = PADS.get(level.dimension());
        if (pads == null) {
            return List.of();
        }
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : pads) {
            if (pos.distSqr(center) <= radius * radius) {
                found.add(pos);
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return found;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    /** 这名玩家的船能不能停：自己的台子或者没有主人的。 */
    public boolean usableBy(UUID player) {
        return this.owner == null || this.owner.equals(player);
    }

    /** 台子上方停着的船；没有为空。 */
    public @Nullable ShipEntity shipAbove() {
        if (this.level == null) {
            return null;
        }
        AABB column = new AABB(this.worldPosition).expandTowards(0, REACH, 0);
        List<ShipEntity> ships = this.level.getEntitiesOfClass(ShipEntity.class, column, ship -> !ship.isRemoved());
        return ships.isEmpty() ? null : ships.getFirst();
    }

    @Override
    public long demand() {
        return this.refueling ? POWER : 0;
    }

    @Override
    protected boolean tickMachine() {
        if (this.level == null) {
            return false;
        }
        if (this.ship == null || this.ship.isRemoved() || this.level.getGameTime() % SCAN_INTERVAL == 0) {
            this.ship = this.shipAbove();
        }
        ShipEntity ship = this.ship;
        boolean worked = false;
        if (this.refueling && ship != null && this.satisfaction > 0) {
            ship.refuel(POWER * this.satisfaction / JOULES_PER_FUEL);
            worked = true;
        }
        // 下一刻要不要电：电网在方块实体之前结算，按这一刻的情况报
        this.refueling = ship != null && ship.flight().speed() < PARKED_SPEED && ship.fuelServer() < ship.fuelCapacity() - 0.01;
        return worked;
    }

    @Override
    protected MachineMenu.Type menuType() {
        throw new UnsupportedOperationException("着陆台没有界面");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
    }
}
