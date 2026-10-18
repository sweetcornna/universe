package io.github.sweetcornna.universe.factory;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.power.PowerDevice;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 工业设备的方块实体（ADR-0011）：物品栏、接入电网、"工作中"状态和界面数据。
 *
 * <p>物品栏对外是原版的 {@link WorldlyContainer}，NeoForge 的包装类把它变成物品能力，漏斗和其他模组的管道都能接：
 * 输入格只进不出，输出格只出不进。服务端加载时接入所在世界的电网，移除或区块卸载时退出。
 *
 * <p>电网每刻在方块实体之前结算（{@link LevelPower}），{@link #settle} 记下结果，{@link #tickMachine} 再按它干活。
 */
public abstract class MachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, PowerDevice {
    /** 界面数据里本类占用的格数，子类从这里往后排。 */
    public static final int BASE_DATA = 4;
    /** 停工多少刻后才熄灯，免得一卡一卡地闪。 */
    private static final int IDLE_GRACE = 10;

    private NonNullList<ItemStack> items;
    private final int[] allSlots;
    /** 上一刻的结算结果。 */
    protected double satisfaction;
    protected double load;
    protected long stored;
    private int idleTicks = IDLE_GRACE;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return MachineBlockEntity.this.dataValue(index);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return MachineBlockEntity.this.dataCount();
        }
    };

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.allSlots = new int[slots];
        for (int i = 0; i < slots; i++) {
            this.allSlots[i] = i;
        }
    }

    // ---- 电网

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            this.joinGrid(LevelPower.of(serverLevel));
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            LevelPower power = LevelPower.existing(serverLevel);
            if (power != null) {
                this.leaveGrid(power);
            }
        }
        super.setRemoved();
    }

    /** 接入电网。默认作为设备接入；电力感应塔改成作为塔接入。 */
    protected void joinGrid(LevelPower power) {
        power.addDevice(this.worldPosition, this);
    }

    protected void leaveGrid(LevelPower power) {
        power.removeDevice(this.worldPosition);
    }

    /** 有没有接上电网（有塔覆盖）。只在服务端有意义。 */
    public boolean connected() {
        if (this.level instanceof ServerLevel serverLevel) {
            LevelPower power = LevelPower.existing(serverLevel);
            return power != null && power.gridOf(this.worldPosition) != null;
        }
        return false;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        this.satisfaction = satisfaction;
        this.load = load;
        this.stored = stored;
    }

    /** 发电机：界面上显示输出和负荷。 */
    protected boolean generator() {
        return false;
    }

    /** 这一刻实际用的电（用电设备）或发的电（发电机），单位焦耳。界面上显示用。 */
    protected long powerFlow() {
        long demand = this.demand();
        return demand > 0 ? Math.round(demand * this.satisfaction) : Math.round(this.generation() * this.load);
    }

    // ---- tick

    final void serverTick() {
        boolean worked = this.tickMachine();
        this.idleTicks = worked ? 0 : Math.min(this.idleTicks + 1, IDLE_GRACE);
        boolean working = this.idleTicks < IDLE_GRACE;
        BlockState state = this.getBlockState();
        if (this.level != null && state.hasProperty(MachineBlock.WORKING) && state.getValue(MachineBlock.WORKING) != working) {
            this.level.setBlock(this.worldPosition, state.setValue(MachineBlock.WORKING, working), 3);
        }
    }

    /**
     * 每刻干一次活，在电网结算之后。
     *
     * @return 这一刻在工作（亮灯）
     */
    protected abstract boolean tickMachine();

    public boolean working() {
        return this.idleTicks < IDLE_GRACE;
    }

    // ---- 界面数据

    public ContainerData data() {
        return this.data;
    }

    protected int dataCount() {
        return BASE_DATA;
    }

    /**
     * 界面数据。原版同步时按 16 位传，大的数用 {@link MachineMenu#low}、{@link MachineMenu#high} 拆成两格。
     *
     * <ul>
     * <li>0：没接上电网时是 -1，否则是满足率（用电设备）或负荷（发电机）的千分数
     * <li>1、2：这一刻的功率（焦耳/刻 × 10）
     * <li>3：第 0 位是工作中，第 1 位表示是发电机（界面上显示负荷而不是满足率），第 2 位表示这一刻在要电
     * </ul>
     */
    protected int dataValue(int index) {
        return switch (index) {
            case 0 -> this.connected() ? (int) Math.round((this.demand() > 0 ? this.satisfaction : this.load) * 1000) : -1;
            case 1 -> MachineMenu.low(this.powerFlow() * 10);
            case 2 -> MachineMenu.high(this.powerFlow() * 10);
            case 3 -> (this.working() ? 1 : 0) | (this.generator() ? 2 : 0) | (this.demand() > 0 ? 4 : 0);
            default -> 0;
        };
    }

    // ---- 物品栏

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    /** 自动化（漏斗、分拣器）能放进去的格子。 */
    protected boolean isInput(int slot) {
        return false;
    }

    /** 自动化能拿出来的格子。 */
    protected boolean isOutput(int slot) {
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return this.allSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.isInput(slot) && this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return this.isOutput(slot);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.isInput(slot);
    }

    @Override
    protected Component getDefaultName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MachineMenu(this.menuType(), containerId, inventory, this, this.data, this.worldPosition);
    }

    /** 这台设备的界面。 */
    protected abstract MachineMenu.Type menuType();

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
