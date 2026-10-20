package io.github.sweetcornna.universe.dyson;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 电磁轨道弹射器（ADR-0016）：每 60 刻把一张太阳帆射进选定的戴森云轨道，工作 90 焦/刻、待机 3 焦/刻（戴森球计划
 * 1.8 MW、60 kW）。星球上要看得见天空、恒星在地平线 5° 以上；太空里一直能射。
 */
public class EmRailEjectorBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    public static final long WORK_POWER = 90;
    public static final long IDLE_POWER = 3;
    public static final int SHOT_TICKS = 60;
    /** 恒星至少要高出地平线几度。 */
    public static final double MIN_ELEVATION = 5;
    public static final int SAIL_SLOT = 0;
    public static final int ORBIT = BASE_DATA;
    public static final int STATUS = BASE_DATA + 1;
    public static final int PROGRESS = BASE_DATA + 2;
    public static final int DATA_COUNT = BASE_DATA + 3;
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    private static final int RECHECK = 20;

    /** 界面上的状态。 */
    public enum Status {
        WORKING, NO_SAILS, NO_ORBIT, NO_SUN, NO_SKY, NO_STAR, NO_POWER
    }

    private int orbit = 1;
    private double progress;
    private Status status = Status.NO_SAILS;
    private boolean sight;
    private boolean ready;
    private int recheck;
    /** 最近一次发射的游戏刻，客户端据此画飞出去的帆。 */
    private long lastShot = Long.MIN_VALUE;

    public EmRailEjectorBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.EM_RAIL_EJECTOR.get(), pos, state, 1);
    }

    public long lastShot() {
        return this.lastShot;
    }

    /** 选定的戴森云轨道编号。 */
    public int orbit() {
        return this.orbit;
    }

    @Override
    public long demand() {
        return this.ready ? WORK_POWER : IDLE_POWER;
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        OptionalInt system = Dyson.systemOf(level.dimension());
        if (--this.recheck <= 0) {
            this.recheck = RECHECK;
            this.sight = DysonSight.sky(level, this.worldPosition.above())
                    && DysonSight.sunElevation(level, this.worldPosition) >= MIN_ELEVATION;
        }
        DysonSphere sphere = system.isPresent() ? Dyson.sphere(level.getServer(), system.getAsInt()) : null;
        boolean sails = this.getItem(SAIL_SLOT).is(UniverseItems.SOLAR_SAIL.get());
        if (sphere == null) {
            this.status = Status.NO_STAR;
        } else if (sphere.orbit(this.orbit) == null) {
            this.status = Status.NO_ORBIT;
        } else if (!sails) {
            this.status = Status.NO_SAILS;
        } else if (!DysonSight.sky(level, this.worldPosition.above())) {
            this.status = Status.NO_SKY;
        } else if (!this.sight) {
            this.status = Status.NO_SUN;
        } else if (!this.connected() || this.satisfaction <= 0) {
            this.status = Status.NO_POWER;
        } else {
            this.status = Status.WORKING;
        }
        boolean wasReady = this.ready;
        this.ready = sphere != null && sphere.orbit(this.orbit) != null && sails && this.sight;
        if (!this.ready) {
            if (wasReady) {
                this.progress = 0;
            }
            return false;
        }
        this.progress += this.satisfaction;
        if (this.progress >= SHOT_TICKS) {
            this.progress = 0;
            ItemStack sail = this.getItem(SAIL_SLOT);
            long lifetime = Upgrades.sailLifetime(Research.level(level, Upgrades.SAIL_LIFETIME));
            if (sphere.launchSail(this.orbit, level.getServer().overworld().getGameTime(), lifetime)) {
                ProductionLog.consumed(level, sail.getItem(), 1);
                sail.shrink(1);
                this.lastShot = level.getGameTime();
                this.setChanged();
                level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
                level.playSound(null, this.worldPosition, SoundEvents.BREEZE_SHOOT, SoundSource.BLOCKS, 0.6F, 1.5F);
            }
        }
        return this.satisfaction > 0;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != BUTTON_PREVIOUS && button != BUTTON_NEXT || !(this.level instanceof ServerLevel level)) {
            return false;
        }
        OptionalInt system = Dyson.systemOf(level.dimension());
        if (system.isEmpty()) {
            return false;
        }
        List<Integer> ids = new ArrayList<>();
        for (SwarmOrbit orbit : Dyson.sphere(level.getServer(), system.getAsInt()).orbits()) {
            ids.add(orbit.id());
        }
        if (ids.isEmpty()) {
            return false;
        }
        int at = ids.indexOf(this.orbit);
        if (at < 0) {
            this.orbit = ids.getFirst();
        } else {
            this.orbit = ids.get(Math.floorMod(at + (button == BUTTON_NEXT ? 1 : -1), ids.size()));
        }
        this.progress = 0;
        this.setChanged();
        level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        return true;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == SAIL_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SAIL_SLOT && stack.is(UniverseItems.SOLAR_SAIL.get());
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case ORBIT -> this.orbit;
            case STATUS -> this.status.ordinal();
            case PROGRESS -> (int) Math.round(this.progress / SHOT_TICKS * 1000);
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.EM_RAIL_EJECTOR;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.orbit = input.getIntOr("Orbit", 1);
        this.progress = input.getDoubleOr("Progress", 0);
        this.lastShot = input.getLongOr("LastShot", Long.MIN_VALUE);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Orbit", this.orbit);
        output.putDouble("Progress", this.progress);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Orbit", this.orbit);
        tag.putLong("LastShot", this.lastShot);
        return tag;
    }
}
