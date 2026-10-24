package io.github.sweetcornna.universe.mecha;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.mecha.combat.MechaWeapon;
import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.core.mecha.fleet.Squadrons;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.mecha.combat.MechaCombat;
import io.github.sweetcornna.universe.mecha.fleet.FleetData;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem;
import io.github.sweetcornna.universe.mecha.fleet.MechaFleet;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 机甲面板（ADR-0023）：燃料格和背包是真的格子；能量核心的读数走 {@link ContainerData}；物流栏位是设置，
 * 存在 {@link MechaSettings} 里，按钮改（{@link #clickMenuButton}），上下限用 {@code MechaPayloads.Bounds} 改。
 *
 * <p>分三页（ADR-0033 §一、§二）："核心"是上面这些；"舰队"是五个编队的格子（真的格子，直接读写 {@link FleetData}，
 * 一次只显示选中的那个编队）和开关；"战斗"是护盾、三门武器的模式和弹药。页和选中的编队两边各记一份，客户端换的时候按按钮告诉服务端。
 */
public class MechaMenu extends AbstractContainerMenu {
    /** 物流栏位要研究的科技。 */
    public static final String LOGISTICS_TECH = "distribution_logistics";
    public static final int WIDTH = 248;
    public static final int HEIGHT = 242;
    public static final int FUEL_X = 8;
    public static final int FUEL_Y = 64;
    public static final int LOGISTICS_X = 130;
    public static final int LOGISTICS_Y = 30;
    public static final int LOGISTICS_COLUMNS = 6;
    public static final int INVENTORY_X = 43;
    public static final int INVENTORY_Y = 160;
    public static final int FLEET_X = 130;
    public static final int FLEET_Y = 34;

    public static final int PAGE_CORE = 0;
    public static final int PAGE_FLEET = 1;
    public static final int PAGE_COMBAT = 2;
    public static final int PAGES = 3;

    /**
     * 按钮：0–11 用手上的物品设物流栏位，20–31 清掉，40 切换自动补燃料，41 清空工地；50–52 换页，60–64 选编队，
     * 70–74 出动或召回，75–79 自动补员，80–84 攻击建筑，85–89 遇袭出动，90–92 换武器的模式。
     */
    public static final int SET_SLOT = 0;
    public static final int CLEAR_SLOT = 20;
    public static final int TOGGLE_REFUEL = 40;
    public static final int CANCEL_BUILD = 41;
    public static final int PAGE = 50;
    public static final int SELECT = 60;
    public static final int DEPLOY = 70;
    public static final int REPLENISH = 75;
    public static final int BUILDINGS = 80;
    public static final int AUTO_DEPLOY = 85;
    public static final int WEAPON_MODE = 90;

    public static final int ENERGY = 0;
    public static final int CAPACITY = 2;
    public static final int BURNING = 4;
    public static final int FUEL_POWER = 5;
    public static final int INFLOW = 6;
    public static final int OUTFLOW = 7;
    /** 位 0–4：编队开了；位 5–9：出动着。 */
    public static final int FLEET_STATE = 8;
    /** 位 0–4：自动补员；位 5–9：攻击建筑；位 10–14：遇袭出动。 */
    public static final int FLEET_TOGGLES = 9;
    /** 24 格各一个：被打掉的是哪种单位（{@link FleetUnitKind} 的序号 + 1，0 是没有）。 */
    public static final int PENDING = 10;
    /** 每门武器两个：装着的还剩几发，背包和货舱里还有几个弹药物品。 */
    public static final int AMMO = PENDING + Squadrons.TOTAL_SLOTS;
    public static final int DATA_COUNT = AMMO + MechaWeapon.values().length * 2;

    private static final int FUEL_SLOT = 0;
    private static final int INVENTORY_START = 1;
    private static final int INVENTORY_END = INVENTORY_START + 36;
    private static final int FLEET_START = INVENTORY_END;

    private final Player player;
    private final ContainerData data;
    private int page = PAGE_CORE;
    private int squadron;

    /** 客户端。 */
    public MechaMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainer(Squadrons.TOTAL_SLOTS), new SimpleContainerData(DATA_COUNT));
    }

    private MechaMenu(int id, Inventory inventory, Container fuel, Container fleet, ContainerData data) {
        super(UniverseMenus.MECHA.get(), id);
        this.player = inventory.player;
        this.data = data;
        this.addSlot(new Slot(fuel, 0, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.has(DataComponents.COOKING_FUEL);
            }

            @Override
            public boolean isActive() {
                return MechaMenu.this.page == PAGE_CORE;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
        for (int index = 0; index < Squadrons.TOTAL_SLOTS; index++) {
            this.addSlot(new FleetSlot(fleet, index));
        }
        this.addDataSlots(data);
    }

    /** 服务端：燃料格、编队格直接读写玩家的机甲数据。 */
    public static MechaMenu server(int id, Inventory inventory, ServerPlayer player) {
        MechaData mecha = player.getData(UniverseAttachments.MECHA);
        FleetData fleet = player.getData(UniverseAttachments.MECHA_FLEET);
        Container fuel = new FuelContainer(mecha);
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= FLEET_STATE) {
                    return fleetData(player, fleet, index);
                }
                long capacity = Mecha.capacity(player.level());
                return switch (index) {
                    case ENERGY -> MachineMenu.low((long) mecha.energy().energy());
                    case ENERGY + 1 -> MachineMenu.high((long) mecha.energy().energy());
                    case CAPACITY -> MachineMenu.low(capacity);
                    case CAPACITY + 1 -> MachineMenu.high(capacity);
                    case BURNING -> mecha.energy().burningTotal() <= 0 ? 0
                            : (int) Math.round(1000 * mecha.energy().burning() / mecha.energy().burningTotal());
                    case FUEL_POWER -> (int) Mecha.fuelPower(player.level());
                    case INFLOW -> (int) Math.round(mecha.inflow());
                    case OUTFLOW -> (int) Math.round(mecha.outflow());
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new MechaMenu(id, inventory, fuel, new FleetContainer(fleet), data);
    }

    private static int fleetData(ServerPlayer player, FleetData fleet, int index) {
        if (index == FLEET_STATE) {
            int bits = 0;
            for (int s = 0; s < Squadrons.COUNT; s++) {
                bits |= MechaFleet.open(player.level(), s) ? 1 << s : 0;
                bits |= MechaFleet.deployed(player, s) ? 1 << (s + Squadrons.COUNT) : 0;
            }
            return bits;
        }
        if (index == FLEET_TOGGLES) {
            int bits = 0;
            for (int s = 0; s < Squadrons.COUNT; s++) {
                bits |= fleet.replenish(s) ? 1 << s : 0;
                bits |= fleet.buildings(s) ? 1 << (s + Squadrons.COUNT) : 0;
                bits |= fleet.autoDeploy(s) ? 1 << (s + Squadrons.COUNT * 2) : 0;
            }
            return bits;
        }
        if (index < AMMO) {
            return FleetUnitKind.byId(fleet.pending(index - PENDING)).map(kind -> kind.ordinal() + 1).orElse(0);
        }
        int[] ammo = MechaCombat.ammoCount(player, MechaWeapon.values()[(index - AMMO) / 2]);
        return Math.min(Short.MAX_VALUE, ammo[(index - AMMO) % 2]);
    }

    public long energy() {
        return MachineMenu.join(this.data.get(ENERGY), this.data.get(ENERGY + 1));
    }

    public long capacity() {
        return MachineMenu.join(this.data.get(CAPACITY), this.data.get(CAPACITY + 1));
    }

    /** 正在烧的燃料还剩多少（0–1）。 */
    public float burning() {
        return this.data.get(BURNING) / 1000F;
    }

    public int fuelPower() {
        return this.data.get(FUEL_POWER);
    }

    public int inflow() {
        return this.data.get(INFLOW);
    }

    public int outflow() {
        return this.data.get(OUTFLOW);
    }

    public int page() {
        return this.page;
    }

    /** 选中的编队。 */
    public int squadron() {
        return this.squadron;
    }

    /** 客户端换页、选编队时先改自己这份，再按按钮告诉服务端。 */
    public void setPage(int page) {
        this.page = Math.clamp(page, 0, PAGES - 1);
    }

    public void setSquadron(int squadron) {
        this.squadron = Math.clamp(squadron, 0, Squadrons.COUNT - 1);
    }

    private int bits(int index) {
        return this.data.get(index) & 0xFFFF;
    }

    /** 这个编队研究出来了（研究和升级开了几个）。 */
    public boolean open(int squadron) {
        return (this.bits(FLEET_STATE) & 1 << squadron) != 0;
    }

    public boolean deployed(int squadron) {
        return (this.bits(FLEET_STATE) & 1 << (squadron + Squadrons.COUNT)) != 0;
    }

    public boolean replenish(int squadron) {
        return (this.bits(FLEET_TOGGLES) & 1 << squadron) != 0;
    }

    public boolean buildings(int squadron) {
        return (this.bits(FLEET_TOGGLES) & 1 << (squadron + Squadrons.COUNT)) != 0;
    }

    public boolean autoDeploy(int squadron) {
        return (this.bits(FLEET_TOGGLES) & 1 << (squadron + Squadrons.COUNT * 2)) != 0;
    }

    /** 这格被打掉的是什么，等着补；没有是 null。 */
    public @Nullable FleetUnitKind pending(int index) {
        int value = this.data.get(PENDING + index);
        return value <= 0 || value > FleetUnitKind.values().length ? null : FleetUnitKind.values()[value - 1];
    }

    /** {装着的还剩几发, 背包和货舱里的弹药物品}。 */
    public int[] ammo(MechaWeapon weapon) {
        return new int[] {this.data.get(AMMO + weapon.ordinal() * 2), this.data.get(AMMO + weapon.ordinal() * 2 + 1)};
    }

    /** 编队格（{@link Squadrons#flatIndex} 那一格）在这个菜单里是第几格。 */
    public static int fleetSlot(int index) {
        return FLEET_START + index;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= PAGE && id < PAGE + PAGES) {
            this.setPage(id - PAGE);
            return true;
        }
        if (id >= SELECT && id < SELECT + Squadrons.COUNT) {
            this.setSquadron(id - SELECT);
            return true;
        }
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        if (id >= DEPLOY && id < WEAPON_MODE + MechaWeapon.values().length) {
            return fleetButton(server, id);
        }
        MechaSettings settings = server.getData(UniverseAttachments.MECHA_SETTINGS);
        if (id >= SET_SLOT && id < SET_SLOT + MechaSettings.SLOTS) {
            if (!Mecha.logisticsUnlocked(server.level())) {
                return false;
            }
            ItemStack carried = this.getCarried();
            LogisticsSlot slot = carried.isEmpty() ? LogisticsSlot.EMPTY : LogisticsSlot.of(Mecha.id(carried), carried.getMaxStackSize());
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withSlot(id - SET_SLOT, slot));
            return true;
        }
        if (id >= CLEAR_SLOT && id < CLEAR_SLOT + MechaSettings.SLOTS) {
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withSlot(id - CLEAR_SLOT, LogisticsSlot.EMPTY));
            return true;
        }
        if (id == TOGGLE_REFUEL) {
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withAutoRefuel(!settings.autoRefuel()));
            return true;
        }
        if (id == CANCEL_BUILD) {
            Construction.cancel(server);
            return true;
        }
        return false;
    }

    private static boolean fleetButton(ServerPlayer player, int id) {
        if (id >= WEAPON_MODE) {
            MechaWeapon weapon = MechaWeapon.values()[id - WEAPON_MODE];
            MechaSettings settings = player.getData(UniverseAttachments.MECHA_SETTINGS);
            player.setData(UniverseAttachments.MECHA_SETTINGS, settings.withMode(weapon, settings.mode(weapon).next()));
            return true;
        }
        int squadron = (id - DEPLOY) % Squadrons.COUNT;
        FleetData fleet = player.getData(UniverseAttachments.MECHA_FLEET);
        switch ((id - DEPLOY) / Squadrons.COUNT) {
            case 0 -> {
                if (MechaFleet.deployed(player, squadron)) {
                    MechaFleet.recall(player, squadron, true);
                } else {
                    MechaFleet.deploy(player, squadron);
                }
            }
            case 1 -> fleet.setReplenish(squadron, !fleet.replenish(squadron));
            case 2 -> fleet.setBuildings(squadron, !fleet.buildings(squadron));
            default -> fleet.setAutoDeploy(squadron, !fleet.autoDeploy(squadron));
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int squadronStart = FLEET_START + Squadrons.flatIndex(this.squadron, 0);
        if (index == FUEL_SLOT || index >= FLEET_START) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.page == PAGE_FLEET && stack.getItem() instanceof FleetUnitItem) {
            // 舰队页：单位放进选中的编队（格子自己查放不放得下）
            if (!this.moveItemStackTo(stack, squadronStart, squadronStart + Squadrons.slots(this.squadron), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!(this.page == PAGE_CORE && stack.has(DataComponents.COOKING_FUEL)
                && this.moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false))) {
            int hotbar = INVENTORY_START + 27;
            if (index < hotbar ? !this.moveItemStackTo(stack, hotbar, INVENTORY_END, false)
                    : !this.moveItemStackTo(stack, INVENTORY_START, hotbar, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /**
     * 编队的一格：只放这个编队用的单位（地面编队放地面单位，太空舰队放舰船），一格一架；编队没开、出动着的时候不让动。
     * 只在舰队页、选中这个编队时显示。
     */
    private final class FleetSlot extends Slot {
        private final int squadron;

        FleetSlot(Container container, int index) {
            super(container, index, FLEET_X + Squadrons.unflatten(index)[1] * 18, FLEET_Y);
            this.squadron = Squadrons.unflatten(index)[0];
        }

        private boolean editable() {
            return MechaMenu.this.open(this.squadron) && !MechaMenu.this.deployed(this.squadron);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            FleetUnitKind kind = FleetUnitItem.kindOf(stack);
            return kind != null && kind.space() == Squadrons.space(this.squadron) && this.editable();
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.editable();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return MechaMenu.this.page == PAGE_FLEET && MechaMenu.this.squadron == this.squadron;
        }
    }

    /** 编队格：直接是 {@link FleetData#slots()}；手动放进去一架，这格就不再等补员。 */
    private static final class FleetContainer implements Container {
        private final FleetData data;

        FleetContainer(FleetData data) {
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return Squadrons.TOTAL_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return this.data.slots().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.data.slots().get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack stack = this.data.slots().get(slot);
            ItemStack taken = stack.split(count);
            if (stack.isEmpty()) {
                this.data.slots().set(slot, ItemStack.EMPTY);
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = this.data.slots().get(slot);
            this.data.slots().set(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.data.slots().set(slot, stack);
            if (!stack.isEmpty()) {
                this.data.setPending(slot, "");
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < Squadrons.TOTAL_SLOTS; i++) {
                this.data.slots().set(i, ItemStack.EMPTY);
            }
        }
    }

    /** 燃料格：直接是 {@link MechaData#fuel()}。 */
    private static final class FuelContainer implements Container {
        private final MechaData data;

        FuelContainer(MechaData data) {
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return this.data.fuel().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.data.fuel();
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = this.data.fuel().split(count);
            if (this.data.fuel().isEmpty()) {
                this.data.setFuel(ItemStack.EMPTY);
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = this.data.fuel();
            this.data.setFuel(ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.data.setFuel(stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            this.data.setFuel(ItemStack.EMPTY);
        }
    }
}
