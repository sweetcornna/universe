package io.github.sweetcornna.universe.factory;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.power.PowerDevice;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
    /** 往外推产物的目标，按方向缓存。 */
    private final Map<Direction, BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>> outputs =
            new EnumMap<>(Direction.class);
    /** 原料格里攒下的增产点数，按格记；物品放在格子里时不带组件，同一种原料喷没喷过都叠在一起。 */
    private final Proliferation.Pool[] pools;
    /** 每格的池子对应的物品，格子里换了东西就清零。 */
    private final @Nullable Item[] poolItems;
    private final PoolJournal poolJournal = new PoolJournal();
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
        this.pools = new Proliferation.Pool[slots];
        this.poolItems = new Item[slots];
        for (int i = 0; i < slots; i++) {
            this.allSlots[i] = i;
            this.pools[i] = new Proliferation.Pool();
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
        // 电力统计按设备种类分开（ADR-0033 §四），每秒取一次样
        io.github.sweetcornna.universe.stats.ProductionLog.device(this);
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

    /**
     * 只改了进度、批数这类存档里的数，物品栏没动时用：标记区块要存盘，不像 {@link #setChanged()} 那样挨个通知六个邻居。
     * 比较器读的是物品栏装了多少，进度走一刻读数不变，所以每刻都要调的地方用它，物品栏真变了再 {@link #setChanged()}。
     */
    protected final void markUnsaved() {
        if (this.level != null) {
            this.level.blockEntityChanged(this.worldPosition);
        }
    }

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

    /**
     * 把一格里的东西推给某个方向的邻居（传送带、箱子、别的设备），用邻居的物品能力。
     *
     * @return 推出去的个数
     */
    protected int pushOutput(int slot, Direction side, int max) {
        ItemStack stack = this.getItem(slot);
        if (stack.isEmpty() || !(this.level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache = this.outputs.computeIfAbsent(side,
                direction -> BlockCapabilityCache.create(Capabilities.Item.BLOCK, serverLevel, this.worldPosition.relative(direction),
                        direction.getOpposite()));
        ResourceHandler<ItemResource> target = cache.getCapability();
        if (target == null) {
            return 0;
        }
        int inserted = ResourceHandlerUtil.insertStacking(target, ItemResource.of(stack), Math.min(max, stack.getCount()), null);
        if (inserted > 0) {
            stack.shrink(inserted);
            this.setChanged();
        }
        return inserted;
    }

    /**
     * 往产物格里加东西。
     *
     * @return 放不下的个数
     */
    protected int addOutput(int slot, ItemStack stack) {
        ItemStack current = this.getItem(slot);
        if (current.isEmpty()) {
            this.setItem(slot, stack.copy());
            return 0;
        }
        if (!ItemStack.isSameItemSameComponents(current, stack)) {
            return stack.getCount();
        }
        int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
        current.grow(moved);
        this.setChanged();
        return stack.getCount() - moved;
    }

    /** 产物格还能不能再放一个这种东西。 */
    protected boolean outputHasRoom(int slot, ItemStack stack) {
        ItemStack current = this.getItem(slot);
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack) && current.getCount() < current.getMaxStackSize();
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
        int[] points = input.getIntArray("InputPoints").orElse(new int[0]);
        for (int slot = 0; slot < this.pools.length; slot++) {
            ItemStack stack = this.items.get(slot);
            this.poolItems[slot] = stack.isEmpty() ? null : stack.getItem();
            this.pools[slot].set(stack.getCount(), slot < points.length ? points[slot] : 0);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        int[] points = new int[this.pools.length];
        boolean any = false;
        for (int slot = 0; slot < points.length; slot++) {
            points[slot] = (int) this.pool(slot).points();
            any |= points[slot] > 0;
        }
        if (any) {
            output.putIntArray("InputPoints", points);
        }
    }

    // ---- 增产点数（ADR-0022）

    /**
     * 这一格原料的点数池。先和格子里的数量对上：格子空了或换了物品就清零，数量被别处改了（玩家拿走、漏斗放进没喷过的）
     * 按 {@link Proliferation.Pool#reconcile} 对上。
     */
    protected Proliferation.Pool pool(int slot) {
        ItemStack stack = this.items.get(slot);
        Proliferation.Pool pool = this.pools[slot];
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (item != this.poolItems[slot]) {
            this.poolItems[slot] = item;
            pool.set(stack.getCount(), 0);
        } else {
            pool.reconcile(stack.getCount());
        }
        return pool;
    }

    /** 这一格原料的平均等级，不拿走。 */
    public int inputLevel(int slot) {
        Proliferation.Pool pool = this.pool(slot);
        return pool.count() == 0 ? 0 : (int) Math.min(Proliferation.MAX_LEVEL, pool.points() / pool.count());
    }

    /** 从原料格扣掉 {@code n} 个，返回它们的平均等级（点数按比例跟着拿走）。 */
    protected int consumeInput(int slot, int n) {
        int level = this.pool(slot).take(n);
        this.items.get(slot).shrink(n);
        return Math.max(0, level);
    }

    /**
     * 原料格里刚放进 {@code count} 个 {@code item}，点数一共 {@code points}。放之前要先调一次 {@link #pool}，让池子对上放之前的数量。
     */
    protected void receiveInput(int slot, Item item, int count, long points) {
        Proliferation.Pool pool = this.pools[slot];
        if (this.poolItems[slot] != item) {
            this.poolItems[slot] = item;
            pool.set(0, 0);
        }
        pool.addTotal(count, points);
    }

    /** 物品能力的事务开始改池子之前记一下，回滚时恢复。 */
    void snapshotPools(TransactionContext transaction) {
        this.poolJournal.updateSnapshots(transaction);
    }

    /** 玩家在界面里把喷过的物品放进原料格：去掉组件，点数记进池子。 */
    @Override
    public void setItem(int slot, ItemStack stack) {
        Integer level = stack.get(UniverseDataComponents.PROLIFERATION.get());
        if (level == null || !this.isInput(slot)) {
            super.setItem(slot, stack);
            return;
        }
        stack.remove(UniverseDataComponents.PROLIFERATION.get());
        super.setItem(slot, stack);
        this.poolItems[slot] = stack.isEmpty() ? null : stack.getItem();
        this.pools[slot].set(stack.getCount(), (long) stack.getCount() * level);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        this.pool(slot);
        ItemStack taken = super.removeItem(slot, count);
        this.pool(slot);
        return taken;
    }

    private record PoolSnapshot(long[] counts, long[] points, @Nullable Item[] items) {
    }

    private final class PoolJournal extends SnapshotJournal<PoolSnapshot> {
        @Override
        protected PoolSnapshot createSnapshot() {
            Proliferation.Pool[] pools = MachineBlockEntity.this.pools;
            long[] counts = new long[pools.length];
            long[] points = new long[pools.length];
            for (int i = 0; i < pools.length; i++) {
                counts[i] = pools[i].count();
                points[i] = pools[i].points();
            }
            return new PoolSnapshot(counts, points, MachineBlockEntity.this.poolItems.clone());
        }

        @Override
        protected void revertToSnapshot(PoolSnapshot snapshot) {
            Proliferation.Pool[] pools = MachineBlockEntity.this.pools;
            for (int i = 0; i < pools.length; i++) {
                pools[i].set(snapshot.counts()[i], snapshot.points()[i]);
            }
            System.arraycopy(snapshot.items(), 0, MachineBlockEntity.this.poolItems, 0, pools.length);
        }
    }
}
