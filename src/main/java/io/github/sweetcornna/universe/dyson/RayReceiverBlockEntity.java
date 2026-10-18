package io.github.sweetcornna.universe.dyson;

import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.RayReceiver;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 射线接收站（ADR-0016）：把本恒星系戴森球的能量送进电网，或者在光子模式下产出临界光子，数值见 {@link RayReceiver}。
 * 星球上要恒星在地平线以上、头顶看得见天空；装着引力透镜时，有大气的星球不需要看见恒星；太空里一直能收。
 *
 * <p>发电模式下每刻向戴森球要"满负荷时要取的电 × 上一刻的负荷"，戴森球不够分时大家按比例少拿；电网用多少才从戴森球
 * 取多少（除以效率）。
 */
public class RayReceiverBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget {
    public static final int LENS_SLOT = 0;
    public static final int PHOTON_SLOT = 1;
    public static final int MODE = BASE_DATA;
    public static final int CONTINUITY = BASE_DATA + 1;
    public static final int LENS = BASE_DATA + 2;
    public static final int STATUS = BASE_DATA + 3;
    public static final int SHARE = BASE_DATA + 4;
    public static final int PHOTON = BASE_DATA + 5;
    public static final int DATA_COUNT = BASE_DATA + 6;
    public static final int BUTTON_MODE = 0;
    private static final int RECHECK = 20;

    public enum Status {
        RECEIVING, NO_SPHERE, NO_SUN, NO_SKY, NO_STAR, NO_GRID, FULL
    }

    private boolean photonMode;
    private double continuity;
    private int lensTicks;
    private double photonEnergy;
    private boolean sky;
    private boolean sun;
    private boolean receiving;
    private int recheck;
    private Status status = Status.NO_SPHERE;
    private double share = 1;
    /** 这一刻能发的电（焦），零头攒到下一刻。 */
    private long output;
    private double carry;

    public RayReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.RAY_RECEIVER.get(), pos, state, 2);
    }

    /** 客户端画光束用：在接收。 */
    public boolean receiving() {
        return this.receiving;
    }

    public boolean photonMode() {
        return this.photonMode;
    }

    private boolean lensActive() {
        return this.lensTicks > 0;
    }

    @Override
    public long generation() {
        return this.photonMode ? 0 : this.output;
    }

    @Override
    protected boolean generator() {
        return true;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        if (this.output > 0 && !this.photonMode) {
            this.sphere().ifPresent(sphere -> sphere.draw(this.output * load / RayReceiver.efficiency(this.continuity)));
        }
    }

    private java.util.Optional<DysonSphere> sphere() {
        if (!(this.level instanceof ServerLevel level)) {
            return java.util.Optional.empty();
        }
        OptionalInt system = Dyson.systemOf(level.dimension());
        return system.isPresent() ? java.util.Optional.of(Dyson.sphere(level.getServer(), system.getAsInt())) : java.util.Optional.empty();
    }

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        if (--this.recheck <= 0) {
            this.recheck = RECHECK;
            this.sky = DysonSight.sky(level, this.worldPosition.above());
            this.sun = DysonSight.sunElevation(level, this.worldPosition) > 0;
        }
        DysonSphere sphere = this.sphere().orElse(null);
        boolean lensAvailable = this.lensActive() || this.getItem(LENS_SLOT).is(UniverseItems.GRAVITON_LENS.get());
        // 装着透镜时，有大气的星球不需要看见恒星
        boolean sight = DysonSight.space(level) || lensAvailable && DysonSight.atmosphere(level) || this.sky && this.sun;
        boolean room = this.outputHasRoom(PHOTON_SLOT, new ItemStack(UniverseItems.CRITICAL_PHOTON.get()));
        if (sphere == null) {
            this.status = Status.NO_STAR;
        } else if (sphere.generation() <= 0) {
            this.status = Status.NO_SPHERE;
        } else if (!sight) {
            this.status = this.sky ? Status.NO_SUN : Status.NO_SKY;
        } else if (this.photonMode && !room) {
            this.status = Status.FULL;
        } else if (!this.photonMode && !this.connected()) {
            this.status = Status.NO_GRID;
        } else {
            this.status = Status.RECEIVING;
        }
        boolean receiving = this.status == Status.RECEIVING;
        this.continuity = RayReceiver.continuity(this.continuity, receiving);
        if (receiving && !this.lensActive() && this.getItem(LENS_SLOT).is(UniverseItems.GRAVITON_LENS.get())) {
            ProductionLog.consumed(level, UniverseItems.GRAVITON_LENS.get(), 1);
            this.getItem(LENS_SLOT).shrink(1);
            this.lensTicks = RayReceiver.LENS_TICKS;
            this.setChanged();
        }
        boolean lens = this.lensActive();
        if (receiving && lens) {
            this.lensTicks--;
        }
        this.share = sphere == null ? 0 : sphere.supplyFraction();
        double got = 0;
        if (!receiving) {
            this.output = 0;
            this.carry = 0;
        } else if (this.photonMode) {
            this.output = 0;
            double request = RayReceiver.photonRequest(this.continuity, lens);
            sphere.request(request);
            got = request * this.share;
            sphere.draw(got);
            this.photonEnergy += got;
            while (this.photonEnergy >= RayReceiver.PHOTON_ENERGY
                    && this.outputHasRoom(PHOTON_SLOT, new ItemStack(UniverseItems.CRITICAL_PHOTON.get()))) {
                this.photonEnergy -= RayReceiver.PHOTON_ENERGY;
                this.addOutput(PHOTON_SLOT, new ItemStack(UniverseItems.CRITICAL_PHOTON.get()));
                ProductionLog.produced(level, UniverseItems.CRITICAL_PHOTON.get(), 1);
            }
            this.setChanged();
        } else {
            // 上一刻没发电时按满负荷要，免得一直要不到
            double lastLoad = this.output > 0 ? this.load : 1;
            sphere.request(RayReceiver.powerRequest(this.continuity, lens) * lastLoad);
            double exact = RayReceiver.maxOutput(this.continuity, lens) * this.share + this.carry;
            this.output = (long) Math.floor(exact);
            this.carry = exact - this.output;
            got = this.output;
        }
        if (receiving != this.receiving) {
            this.receiving = receiving;
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
        return receiving && got > 0;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != BUTTON_MODE) {
            return false;
        }
        this.photonMode = !this.photonMode;
        this.output = 0;
        this.carry = 0;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
        return true;
    }

    @Override
    protected boolean isInput(int slot) {
        return slot == LENS_SLOT;
    }

    @Override
    protected boolean isOutput(int slot) {
        return slot == PHOTON_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == LENS_SLOT && stack.is(UniverseItems.GRAVITON_LENS.get());
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case MODE -> this.photonMode ? 1 : 0;
            case CONTINUITY -> (int) Math.round(this.continuity * 1000);
            case LENS -> (int) Math.round(this.lensTicks * 1000.0 / RayReceiver.LENS_TICKS);
            case STATUS -> this.status.ordinal();
            case SHARE -> (int) Math.round(this.share * 1000);
            case PHOTON -> (int) Math.round(Math.min(1, this.photonEnergy / RayReceiver.PHOTON_ENERGY) * 1000);
            default -> super.dataValue(index);
        };
    }

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.RAY_RECEIVER;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.photonMode = input.getBooleanOr("PhotonMode", false);
        this.continuity = input.getDoubleOr("Continuity", 0);
        this.lensTicks = input.getIntOr("LensTicks", 0);
        this.photonEnergy = input.getDoubleOr("PhotonEnergy", 0);
        this.receiving = input.getBooleanOr("Receiving", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("PhotonMode", this.photonMode);
        output.putDouble("Continuity", this.continuity);
        output.putInt("LensTicks", this.lensTicks);
        output.putDouble("PhotonEnergy", this.photonEnergy);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("PhotonMode", this.photonMode);
        tag.putBoolean("Receiving", this.receiving);
        return tag;
    }
}
