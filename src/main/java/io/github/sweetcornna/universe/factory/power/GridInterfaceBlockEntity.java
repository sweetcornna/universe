package io.github.sweetcornna.universe.factory.power;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.power.GridInterface;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 电网接口（ADR-0030 §三）：放在感应塔的覆盖范围里接进电网，对外提供 NeoForge 的能量能力，1 J = 1 FE，每刻最多
 * 2250。输入模式收别的模组的电、当发电机送进电网；输出模式从电网取电，推给六个面上相邻的能量设备，也让别的模组来抽。
 * 逻辑在 {@link GridInterface}。
 */
public class GridInterfaceBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BlueprintSettings {
    /** 界面数据：模式（0 输入，1 输出）、缓冲（两格）、这一刻和外面走了多少（两格）、相邻的能量设备有几台。 */
    public static final int MODE = BASE_DATA;
    public static final int BUFFER = BASE_DATA + 1;
    public static final int EXTERNAL = BASE_DATA + 3;
    public static final int NEIGHBORS = BASE_DATA + 5;
    public static final int DATA_COUNT = BASE_DATA + 6;
    public static final int MODE_BUTTON = 0;
    /** 多久数一次相邻的能量设备。 */
    private static final int NEIGHBOR_INTERVAL = 20;

    private final GridInterface core = new GridInterface();
    private final Energy energy = new Energy();
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> neighbors = new EnumMap<>(Direction.class);
    /** 上一刻和电网走了多少（界面显示）。 */
    private long gridFlow;
    private int neighborCount;

    public GridInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.GRID_INTERFACE.get(), pos, state, 0);
    }

    public boolean output() {
        return this.core.output();
    }

    /** 换模式（界面按钮和游戏内测试用）。缓冲里的能量留着。 */
    public void setOutput(boolean output) {
        if (this.core.output() != output) {
            this.core.setOutput(output);
            this.setChanged();
        }
    }

    public long buffer() {
        return this.core.buffer();
    }

    /** 对外的能量能力。 */
    public EnergyHandler energyHandler() {
        return this.energy;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        if (button != MODE_BUTTON) {
            return false;
        }
        this.setOutput(!this.core.output());
        return true;
    }

    // ---- 电网

    @Override
    public long demand() {
        return this.core.demand();
    }

    @Override
    public long generation() {
        return this.core.generation();
    }

    @Override
    protected boolean generator() {
        return !this.core.output();
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        // 电网读完所有设备的数马上结算，中间缓冲不会变，这里重算一遍和报上去的一样
        long generation = this.core.generation();
        long demand = this.core.demand();
        super.settle(satisfaction, load, stored);
        this.gridFlow = this.core.settle(generation, demand, satisfaction, load);
        if (this.gridFlow > 0) {
            this.setChanged();
        }
    }

    @Override
    protected long powerFlow() {
        return this.gridFlow;
    }

    // ---- 每刻

    @Override
    protected boolean tickMachine() {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        long tick = level.getGameTime();
        if (tick % NEIGHBOR_INTERVAL == 0) {
            this.neighborCount = 0;
            for (Direction side : Direction.values()) {
                if (this.neighbor(level, side) != null) {
                    this.neighborCount++;
                }
            }
        }
        if (this.core.output() && this.core.buffer() > 0) {
            for (Direction side : Direction.values()) {
                EnergyHandler target = this.neighbor(level, side);
                if (target != null) {
                    EnergyHandlerUtil.move(this.energy, target, (int) this.core.extractable(tick), null);
                }
                if (this.core.extractable(tick) <= 0) {
                    break;
                }
            }
        }
        return this.gridFlow > 0 || this.core.external(tick) > 0;
    }

    private @Nullable EnergyHandler neighbor(ServerLevel level, Direction side) {
        BlockCapabilityCache<EnergyHandler, @Nullable Direction> cache = this.neighbors.computeIfAbsent(side,
                direction -> BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, this.worldPosition.relative(direction),
                        direction.getOpposite()));
        EnergyHandler handler = cache.getCapability();
        // 自己不算（两个接口挨着的时候各算各的）
        return handler == this.energy ? null : handler;
    }

    private long gameTime() {
        return this.level == null ? 0 : this.level.getGameTime();
    }

    // ---- 蓝图：只记模式

    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (!this.core.output()) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Output", true);
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        this.setOutput(settings.getBooleanOr("Output", false));
    }

    // ---- 界面和存档

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.GRID_INTERFACE;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        return switch (index) {
            case MODE -> this.core.output() ? 1 : 0;
            case BUFFER -> MachineMenu.low(this.core.buffer());
            case BUFFER + 1 -> MachineMenu.high(this.core.buffer());
            // 和功率一样按焦耳/刻 × 10 传
            case EXTERNAL -> MachineMenu.low(this.core.external(this.gameTime()) * 10);
            case EXTERNAL + 1 -> MachineMenu.high(this.core.external(this.gameTime()) * 10);
            case NEIGHBORS -> this.neighborCount;
            default -> super.dataValue(index);
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.core.setOutput(input.getBooleanOr("Output", false));
        this.core.setBuffer(input.getLongOr("Buffer", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Output", this.core.output());
        output.putLong("Buffer", this.core.buffer());
    }

    // ---- 能量能力

    /** NeoForge 的能量能力：只在服务端走，带事务。 */
    private final class Energy extends SnapshotJournal<long[]> implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return GridInterfaceBlockEntity.this.core.buffer();
        }

        @Override
        public long getCapacityAsLong() {
            return GridInterface.BUFFER;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            GridInterface core = GridInterfaceBlockEntity.this.core;
            long tick = GridInterfaceBlockEntity.this.gameTime();
            if (amount == 0 || !(GridInterfaceBlockEntity.this.level instanceof ServerLevel) || core.insertable(tick) <= 0) {
                return 0;
            }
            this.updateSnapshots(transaction);
            return (int) core.insert(amount, tick);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            GridInterface core = GridInterfaceBlockEntity.this.core;
            long tick = GridInterfaceBlockEntity.this.gameTime();
            if (amount == 0 || !(GridInterfaceBlockEntity.this.level instanceof ServerLevel) || core.extractable(tick) <= 0) {
                return 0;
            }
            this.updateSnapshots(transaction);
            return (int) core.extract(amount, tick);
        }

        @Override
        protected long[] createSnapshot() {
            return GridInterfaceBlockEntity.this.core.snapshot();
        }

        @Override
        protected void revertToSnapshot(long[] snapshot) {
            GridInterfaceBlockEntity.this.core.restore(snapshot);
        }

        @Override
        protected void onRootCommit(long[] originalState) {
            GridInterfaceBlockEntity.this.setChanged();
        }
    }
}
