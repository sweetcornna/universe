package io.github.sweetcornna.universe.logistics;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.BeltReceiver;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 物流站的方块实体（ADR-0015）：只存账本里的编号，物品格、载具、电量都在账本里。界面和自动化（传送带、分拣器、漏斗）
 * 读写的都是账本。
 *
 * <p>容器格子只是投放口：放进去的无人机、运输船、翘曲器每刻收进账本，界面上的按钮再把空闲的取出来。电池只用电网
 * 富余的电充（和蓄电器一样），不会跟设备抢电。
 */
public class StationBlockEntity extends MachineBlockEntity implements MachineMenu.ButtonTarget, BeltReceiver, BlueprintSettings, Owned {
    public static final int DRONE_SLOT = 0;
    public static final int VESSEL_SLOT = 1;
    public static final int WARPER_SLOT = 2;

    // ---- 界面数据
    /** 电量（百焦耳，拆两格）。 */
    public static final int ENERGY = BASE_DATA;
    /** 无人机：总数 | 空闲 << 8；运输船同样。 */
    public static final int DRONES = BASE_DATA + 2;
    public static final int VESSELS = BASE_DATA + 3;
    public static final int WARPERS = BASE_DATA + 4;
    public static final int RANGE = BASE_DATA + 5;
    /** 这一刻充进的电（焦耳/刻 × 10，拆两格）。 */
    public static final int CHARGE = BASE_DATA + 6;
    /** 每格：物品（注册表序号 + 1，拆两格）、数量、上限、供需（本地 | 远程 << 2）、在途（运来的）。 */
    public static final int ROWS = BASE_DATA + 8;
    public static final int ROW_SIZE = 6;
    public static final int DATA_COUNT = ROWS + 5 * ROW_SIZE;

    // ---- 按钮：每格 16 个编号
    public static final int SET_OR_DEPOSIT = 0;
    public static final int WITHDRAW = 1;
    public static final int CYCLE_LOCAL = 2;
    public static final int CYCLE_REMOTE = 3;
    public static final int MAX_MINUS_BIG = 4;
    public static final int MAX_MINUS = 5;
    public static final int MAX_PLUS = 6;
    public static final int MAX_PLUS_BIG = 7;
    public static final int CLEAR = 8;
    public static final int TAKE_DRONES = 100;
    public static final int TAKE_VESSELS = 101;
    public static final int TAKE_WARPERS = 102;
    public static final int RANGE_MINUS = 103;
    public static final int RANGE_PLUS = 104;
    // ---- 界面布局（服务端注册菜单和客户端画界面共用）
    public static final int ROWS_Y = 62;
    public static final int COLLECTOR_ROWS_Y = 44;
    public static final int ROW_HEIGHT = 21;
    private static final int BIG_STEP = 1000;
    private static final int STEP = 100;
    private static final int RANGE_STEP = 250;

    private final StationKind kind;
    private @Nullable UUID id;
    /** 从物品里带来的内容，建账本记录时恢复。 */
    private @Nullable StationContents pending;
    /** 拆掉时的快照：掉落物在方块移除之后才算，那时账本里已经没有这座站了。 */
    private @Nullable StationContents removed;
    private long charged;
    private final StationHandler handler = new StationHandler(this);
    /** 拆掉、读档时只认投放口，免得把账本的映射格当成真东西掉出来或存进去。 */
    private boolean portsOnly;
    /** 放下它的玩家：建账本记录时记成站的主人（控制面板远程改设置时认它）。 */
    private @Nullable UUID owner;

    public StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, StationKind kind) {
        super(type, pos, state, ports(kind));
        this.kind = kind;
    }

    /** 投放口的个数：行星物流站放无人机；星际物流站再加运输船和翘曲器；采集器没有。 */
    private static int ports(StationKind kind) {
        return switch (kind) {
            case PLANETARY -> 1;
            case INTERSTELLAR -> 3;
            case COLLECTOR -> 0;
        };
    }

    public static StationBlockEntity planetary(BlockPos pos, BlockState state) {
        return new StationBlockEntity(UniverseBlockEntities.PLANETARY_LOGISTICS_STATION.get(), pos, state, StationKind.PLANETARY);
    }

    public static StationBlockEntity interstellar(BlockPos pos, BlockState state) {
        return new StationBlockEntity(UniverseBlockEntities.INTERSTELLAR_LOGISTICS_STATION.get(), pos, state, StationKind.INTERSTELLAR);
    }

    public static StationBlockEntity collector(BlockPos pos, BlockState state) {
        return new StationBlockEntity(UniverseBlockEntities.ORBITAL_COLLECTOR.get(), pos, state, StationKind.COLLECTOR);
    }

    /** 蓝图只记每格的物品、上限和供需，不记存货、载具和电量。 */
    @Override
    public @Nullable CompoundTag blueprintSettings() {
        Station station = this.station();
        if (station == null) {
            return null;
        }
        StationContents settings = StationContents.of(station).settingsOnly();
        return StationContents.CODEC.encodeStart(NbtOps.INSTANCE, settings).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).orElse(null);
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        StationContents.CODEC.parse(NbtOps.INSTANCE, settings).result().ifPresent(contents -> this.pending = contents);
        this.setChanged();
    }

    public StationKind kind() {
        return this.kind;
    }

    /** 物流站的界面：投放口排在右上角，下面每格一行，最下面是背包；采集器没有投放口，也不显示背包。 */
    public static MachineMenu.Type layout(StationKind kind) {
        return switch (kind) {
            case PLANETARY -> new MachineMenu.Type(DATA_COUNT, 256, ROWS_Y + kind.slots() * ROW_HEIGHT + 13 + 82, port(228));
            case INTERSTELLAR -> new MachineMenu.Type(DATA_COUNT, 256, ROWS_Y + kind.slots() * ROW_HEIGHT + 13 + 82, port(164), port(196),
                    port(228));
            case COLLECTOR -> new MachineMenu.Type(DATA_COUNT, 256, COLLECTOR_ROWS_Y + kind.slots() * ROW_HEIGHT + 6);
        };
    }

    private static MachineMenu.SlotSpec port(int x) {
        return new MachineMenu.SlotSpec(x, 20, MachineMenu.Role.INPUT);
    }

    public StationHandler handler() {
        return this.handler;
    }

    // ---- 账本

    /** 账本里的这座站（服务端）；还没建或已经拆了时是 {@code null}。 */
    public @Nullable Station station() {
        if (this.id == null || !(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return Logistics.network(serverLevel.getServer()).get(this.id);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            this.ensureStation(serverLevel);
            BeltNetwork.of(this.level).outputChanged(this.worldPosition);
        }
    }

    /** 账本里没有这座站（新放下的、账本丢了的、或者编号和别的站撞了）就建一条。 */
    private void ensureStation(ServerLevel level) {
        LogisticsNetwork network = Logistics.network(level.getServer());
        Station existing = this.id == null ? null : network.get(this.id);
        if (existing != null && this.isHere(existing, level)) {
            return;
        }
        if (existing != null) {
            // 复制出来的方块带着别的站的编号
            this.id = null;
        }
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Station station = Logistics.create(level, this.worldPosition, this.kind, this.id);
        if (this.pending != null) {
            this.pending.applyTo(station);
            this.pending = null;
        }
        if (this.owner != null) {
            station.setOwner(this.owner);
        }
        this.setupNew(level, station);
        network.add(station);
        this.setChanged();
    }

    /** 账本里的这条是不是这个方块的（维度和位置对得上）。复制出来的方块（创造模式的选取、带数据的物品）带着别的站的编号。 */
    private boolean isHere(Station station, ServerLevel level) {
        return station.dimension().equals(level.dimension().identifier().toString()) && station.x() == this.worldPosition.getX()
                && station.y() == this.worldPosition.getY() && station.z() == this.worldPosition.getZ();
    }

    /** 账本里这座站的主人；账本里还没有（刚放下、编号是复制来的）时看方块实体上记的。 */
    @Override
    public Optional<UUID> owner() {
        Station station = this.station();
        if (station != null && this.level instanceof ServerLevel serverLevel && this.isHere(station, serverLevel)) {
            return station.owner();
        }
        return Optional.ofNullable(this.owner);
    }

    /**
     * 玩家放下了这座站（方块的 {@code setPlacedBy}）、贴蓝图建的、重建的：记成主人。账本里通常还没有这座站（{@link #onLoad} 在下一刻），
     * 有就直接改。编号是复制来的时候只记在方块实体上，不碰原来那座站，{@link #ensureStation} 换了新编号再记进去。
     */
    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        Station station = this.station();
        if (station != null && this.level instanceof ServerLevel serverLevel && this.isHere(station, serverLevel)) {
            station.setOwner(owner);
            LogisticsData.get(serverLevel.getServer()).setDirty();
        }
        this.setChanged();
    }

    /** 新建的站：子类（采集器）在这里设好产出。 */
    protected void setupNew(ServerLevel level, Station station) {
        if (this.kind == StationKind.COLLECTOR) {
            OrbitalCollectorBlock.setup(level, this.worldPosition, station);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            if (network != null) {
                network.outputChanged(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof ServerLevel serverLevel && this.id != null) {
            Station station = this.station();
            if (station != null) {
                this.removed = StationContents.of(station);
                Logistics.network(serverLevel.getServer()).remove(this.id);
            }
        }
        // 只掉投放口里的东西；账本里的货在掉落的物品里
        this.portsOnly = true;
        try {
            super.preRemoveSideEffects(pos, state);
        } finally {
            this.portsOnly = false;
        }
    }

    // ---- 电

    @Override
    public long chargeLimit() {
        Station station = this.station();
        return station == null ? 0 : station.chargeDemand();
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        Station station = this.station();
        this.charged = station != null && stored > 0 ? station.charge(stored) : 0;
    }

    @Override
    protected long powerFlow() {
        return this.charged;
    }

    @Override
    protected boolean tickMachine() {
        Station station = this.station();
        if (station == null) {
            if (this.level instanceof ServerLevel serverLevel) {
                this.ensureStation(serverLevel);
            }
            return false;
        }
        this.absorb(station);
        return station.idleDrones() < station.drones() || station.idleVessels() < station.vessels() || this.charged > 0;
    }

    /** 投放口里的载具和翘曲器收进账本。 */
    private void absorb(Station station) {
        if (this.kind == StationKind.COLLECTOR) {
            return;
        }
        ItemStack drones = this.getItem(DRONE_SLOT);
        if (!drones.isEmpty()) {
            drones.shrink(station.addDrones(drones.getCount()));
            this.setChanged();
        }
        if (this.kind == StationKind.INTERSTELLAR) {
            ItemStack vessels = this.getItem(VESSEL_SLOT);
            if (!vessels.isEmpty()) {
                vessels.shrink(station.addVessels(vessels.getCount()));
                this.setChanged();
            }
            ItemStack warpers = this.getItem(WARPER_SLOT);
            if (!warpers.isEmpty()) {
                int room = Station.MAX_WARPERS - station.warpers();
                int moved = Math.min(room, warpers.getCount());
                station.setWarpers(station.warpers() + moved);
                warpers.shrink(moved);
                this.setChanged();
            }
        }
    }

    // ---- 传送带直接送进来

    @Override
    public boolean acceptFromBelt(int cargo) {
        Station station = this.station();
        return station != null && this.handler.insertFromBelt(station, cargo);
    }

    // ---- 投放口

    @Override
    protected boolean isInput(int slot) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= this.portCount()) {
            // 进货口：这一格是这种物品，而且放得下整叠
            int row = this.inputRow(slot);
            Station station = this.station();
            if (row < 0 || station == null || !BeltNetwork.canCarry(stack)) {
                return false;
            }
            StationSlot ledger = station.slot(row);
            return !ledger.isEmpty() && ledger.item().equals(idOf(stack.getItem()))
                    && Math.min(ledger.max(), this.kind.slotCapacity()) - ledger.count() >= stack.getCount();
        }
        return switch (slot) {
            case DRONE_SLOT -> stack.is(UniverseItems.LOGISTICS_DRONE.get());
            case VESSEL_SLOT -> this.kind == StationKind.INTERSTELLAR && stack.is(UniverseItems.LOGISTICS_VESSEL.get());
            case WARPER_SLOT -> this.kind == StationKind.INTERSTELLAR && stack.is(UniverseItems.SPACE_WARPER.get());
            default -> false;
        };
    }

    /** 比较器读数：所有物品格的总存量占总容量的比例。 */
    public int analogSignal() {
        Station station = this.station();
        if (station == null) {
            return 0;
        }
        long count = 0;
        long capacity = 0;
        for (StationSlot slot : station.slots()) {
            if (!slot.isEmpty()) {
                count += slot.count();
                capacity += Math.max(1, Math.min(slot.max(), this.kind.slotCapacity()));
            }
        }
        return capacity == 0 || count == 0 ? 0 : 1 + (int) (count * 14 / capacity);
    }

    // ---- 原版漏斗和投掷器
    //
    // 它们只认原版容器，不问物品能力（StationHandler）。所以投放口后面再映射两组格子：每个物品格一个"进货口"，
    // 永远是空的，漏斗放进来就记进账本；一个"出货口"，显示能拿的数量（最多一组），漏斗拿走就从账本里扣。

    private int portCount() {
        return ports(this.kind);
    }

    /** 映射格对应的物品格；不是进货口时返回 -1。 */
    private int inputRow(int slot) {
        int row = slot - this.portCount();
        return row >= 0 && row < this.kind.slots() ? row : -1;
    }

    /** 映射格对应的物品格；不是出货口时返回 -1。 */
    private int outputRow(int slot) {
        int row = slot - this.portCount() - this.kind.slots();
        return row >= 0 && row < this.kind.slots() ? row : -1;
    }

    @Override
    public int getContainerSize() {
        return this.portsOnly || this.level == null || this.level.isClientSide() ? this.portCount()
                : this.portCount() + 2 * this.kind.slots();
    }

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        int[] slots = new int[this.getContainerSize()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < this.portCount()) {
            return super.getItem(slot);
        }
        int row = this.outputRow(slot);
        Station station = this.station();
        if (row < 0 || station == null || station.slot(row).isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = itemOf(station.slot(row).item());
        return BeltNetwork.stack(item, Math.min(station.slot(row).available(), item.getDefaultMaxStackSize()), station.slot(row).level());
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < this.portCount()) {
            return super.removeItem(slot, count);
        }
        int row = this.outputRow(slot);
        Station station = this.station();
        if (row < 0 || station == null || station.slot(row).isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = itemOf(station.slot(row).item());
        int level = station.slot(row).level();
        int taken = station.extract(row, Math.min(count, item.getDefaultMaxStackSize()), level);
        if (taken > 0) {
            this.setChanged();
        }
        return taken > 0 ? BeltNetwork.stack(item, taken, level) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot < this.portCount() ? super.removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < this.portCount()) {
            super.setItem(slot, stack);
            return;
        }
        Station station = this.station();
        if (station == null) {
            return;
        }
        int input = this.inputRow(slot);
        if (input >= 0 && !stack.isEmpty()) {
            station.insert(idOf(stack.getItem()), stack.getCount(), (long) stack.getCount() * BeltNetwork.level(stack));
            this.setChanged();
            return;
        }
        int output = this.outputRow(slot);
        if (output >= 0) {
            // 漏斗没拿成，把刚才拿走的放回来：按显示的数量差补上
            StationSlot ledger = station.slot(output);
            int shown = this.getItem(slot).getCount();
            int wanted = stack.isEmpty() || !idOf(stack.getItem()).equals(ledger.item()) ? 0 : stack.getCount();
            if (wanted != shown) {
                int level = wanted > shown ? BeltNetwork.level(stack) : ledger.level();
                long points = ledger.points() + (long) (wanted - shown) * level;
                station.restoreCount(output, ledger.count() + wanted - shown);
                station.restorePoints(output, points);
                this.setChanged();
            }
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.@Nullable Direction side) {
        if (slot < this.portCount()) {
            return this.canPlaceItem(slot, stack);
        }
        return this.inputRow(slot) >= 0 && this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return this.outputRow(slot) >= 0;
    }

    // ---- 界面

    @Override
    protected MachineMenu.Type menuType() {
        return switch (this.kind) {
            case PLANETARY -> UniverseMenus.PLANETARY_LOGISTICS_STATION;
            case INTERSTELLAR -> UniverseMenus.INTERSTELLAR_LOGISTICS_STATION;
            case COLLECTOR -> UniverseMenus.ORBITAL_COLLECTOR;
        };
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    @Override
    protected int dataValue(int index) {
        Station station = this.station();
        if (index < BASE_DATA || station == null) {
            return index < BASE_DATA ? super.dataValue(index) : 0;
        }
        long energy = station.energy() / 100;
        if (index >= ROWS) {
            int row = (index - ROWS) / ROW_SIZE;
            if (row >= this.kind.slots()) {
                return 0;
            }
            StationSlot slot = station.slot(row);
            int item = slot.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(itemOf(slot.item())) + 1;
            return switch ((index - ROWS) % ROW_SIZE) {
                case 0 -> MachineMenu.low(item);
                case 1 -> MachineMenu.high(item);
                case 2 -> slot.count();
                case 3 -> slot.max();
                case 4 -> slot.local().ordinal() | slot.remote().ordinal() << 2;
                // 采集器没有在途的货，这一格放每秒产量（千分之一）
                default -> this.kind == StationKind.COLLECTOR ? (int) Math.round(station.production(row) * 1000) : slot.incoming();
            };
        }
        return switch (index) {
            case ENERGY -> MachineMenu.low(energy);
            case ENERGY + 1 -> MachineMenu.high(energy);
            case DRONES -> station.drones() | station.idleDrones() << 8;
            case VESSELS -> station.vessels() | station.idleVessels() << 8;
            case WARPERS -> station.warpers();
            case RANGE -> station.droneRange();
            case CHARGE -> MachineMenu.low(this.charged * 10);
            case CHARGE + 1 -> MachineMenu.high(this.charged * 10);
            default -> 0;
        };
    }

    static Item itemOf(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
    }

    static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        Station station = this.station();
        if (station == null) {
            return false;
        }
        boolean changed = button >= TAKE_DRONES ? this.stationButton(player, station, button) : this.rowButton(player, station, button / 16,
                button % 16);
        if (changed) {
            this.setChanged();
        }
        return changed;
    }

    private boolean stationButton(ServerPlayer player, Station station, int button) {
        switch (button) {
            case TAKE_DRONES -> give(player, UniverseItems.LOGISTICS_DRONE.get(), station.removeDrones(station.idleDrones()));
            case TAKE_VESSELS -> give(player, UniverseItems.LOGISTICS_VESSEL.get(), station.removeVessels(station.idleVessels()));
            case TAKE_WARPERS -> {
                int warpers = station.warpers();
                station.setWarpers(0);
                give(player, UniverseItems.SPACE_WARPER.get(), warpers);
            }
            case RANGE_MINUS -> station.setDroneRange(station.droneRange() - RANGE_STEP);
            case RANGE_PLUS -> station.setDroneRange(station.droneRange() + RANGE_STEP);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void give(ServerPlayer player, Item item, int count) {
        give(player, item, count, 0);
    }

    private static void give(ServerPlayer player, Item item, int count, int level) {
        while (count > 0) {
            int n = Math.min(count, item.getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(BeltNetwork.stack(item, n, level), Prediction.SERVER_ONLY);
            count -= n;
        }
    }

    private boolean rowButton(ServerPlayer player, Station station, int row, int action) {
        if (row < 0 || row >= this.kind.slots() || this.kind == StationKind.COLLECTOR && action != WITHDRAW) {
            // 采集器的格子固定是氢和重氢，只能取货
            return false;
        }
        StationSlot slot = station.slot(row);
        switch (action) {
            case SET_OR_DEPOSIT -> {
                ItemStack carried = player.containerMenu.getCarried();
                if (carried.isEmpty()) {
                    return false;
                }
                String item = idOf(carried.getItem());
                if (slot.isEmpty() || slot.canChangeItem() && !slot.item().equals(item)) {
                    // 设定物品：同一种物品不能占两格
                    return carried.isComponentsPatchEmpty() && station.assign(row, item);
                }
                if (!slot.item().equals(item) || !BeltNetwork.canCarry(carried)) {
                    return false;
                }
                int added = station.insert(item, carried.getCount(), (long) carried.getCount() * BeltNetwork.level(carried));
                carried.shrink(added);
                return added > 0;
            }
            case WITHDRAW -> {
                if (slot.isEmpty()) {
                    return false;
                }
                Item item = itemOf(slot.item());
                int level = slot.level();
                int taken = station.extract(row, item.getDefaultMaxStackSize(), level);
                give(player, item, taken, level);
                return taken > 0;
            }
            default -> {
                return applySetting(station, row, action);
            }
        }
    }

    /** 不用玩家手上东西的那几个按钮：供需、上限、清掉。控制面板远程改设置也走这里（ADR-0033 §三）。返回真的改了没有。 */
    public static boolean applySetting(Station station, int row, int action) {
        if (row < 0 || row >= station.kind().slots() || station.kind() == StationKind.COLLECTOR) {
            return false;
        }
        StationSlot slot = station.slot(row);
        int capacity = station.kind().slotCapacity();
        int max = slot.max();
        switch (action) {
            case CYCLE_LOCAL -> slot.setLocal(slot.local().next());
            case CYCLE_REMOTE -> {
                if (!station.kind().remote()) {
                    return false;
                }
                slot.setRemote(slot.remote().next());
            }
            case MAX_MINUS_BIG -> slot.setMax(Math.max(0, slot.max() - BIG_STEP));
            case MAX_MINUS -> slot.setMax(Math.max(0, slot.max() - STEP));
            case MAX_PLUS -> slot.setMax(Math.min(capacity, slot.max() + STEP));
            case MAX_PLUS_BIG -> slot.setMax(Math.min(capacity, slot.max() + BIG_STEP));
            case CLEAR -> {
                return slot.setItem("");
            }
            default -> {
                return false;
            }
        }
        // 上限到头了再按不算改
        return action == CYCLE_LOCAL || action == CYCLE_REMOTE || slot.max() != max;
    }

    // ---- 物品组件（拆了带着内容）

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.pending = components.get(UniverseDataComponents.STATION_CONTENTS.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        Station station = this.station();
        StationContents contents = station != null ? StationContents.of(station) : this.removed;
        if (contents != null && !contents.isEmpty()) {
            components.set(UniverseDataComponents.STATION_CONTENTS.get(), contents);
        }
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        this.portsOnly = true;
        try {
            super.loadAdditional(input);
        } finally {
            this.portsOnly = false;
        }
        this.id = input.read("Station", UUIDUtil.CODEC).orElse(null);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.id != null) {
            output.store("Station", UUIDUtil.CODEC, this.id);
        }
        if (this.owner != null) {
            output.store("Owner", UUIDUtil.CODEC, this.owner);
        }
    }
}
