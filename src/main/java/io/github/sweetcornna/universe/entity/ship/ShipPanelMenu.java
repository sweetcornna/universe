package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.ship.ShipPart;
import io.github.sweetcornna.universe.core.ship.ShipRating;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.tech.DriveItem;
import io.github.sweetcornna.universe.tech.ListContainer;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 飞船面板（ADR-0024）：左边评级和各项数值，右边驾驶台的 {@link HelmBlockEntity#SLOTS} 格技术槽（档次给几格就开几格），
 * 下面是背包。
 *
 * <p>客户端拿到部件的个数和容器的格数，自己算评级；数值按技术槽里现在放着的东西实时算，算法和服务端组装时一样。
 * 档次降下来以后多出来的格子里还有东西时照样画出来，能拿走，但不生效。技术槽排成 3 × 2，挨着的同种模块和超频格
 * 的加成照 ADR-0034 §五 算，超频格是哪几格也从服务端发来。
 */
public class ShipPanelMenu extends AbstractContainerMenu {
    public static final int WIDTH = 236;
    public static final int HEIGHT = 222;
    public static final int TECH_X = 158;
    public static final int TECH_Y = 32;
    public static final int TECH_COLUMNS = 3;
    /** 左栏（评级和数值）的右边界。 */
    public static final int LEFT_COLUMN = 146;
    public static final int INVENTORY_X = 37;
    public static final int INVENTORY_Y = 140;
    public static final int INVENTORY_START = HelmBlockEntity.SLOTS;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    private final Map<ShipPart, Integer> counts;
    private final int cargoSlots;
    private final boolean flying;
    /** 驾驶台的超频格，按位记。 */
    private final int supercharged;
    private final ShipRating rating;
    private final ShipStats base;
    private final Predicate<Player> valid;

    /** 客户端：部件的个数和格数从服务端发来，格子里的东西由菜单同步。 */
    public ShipPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, readCounts(buf), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), new SimpleContainer(HelmBlockEntity.SLOTS),
                player -> true);
    }

    ShipPanelMenu(int id, Inventory inventory, Map<ShipPart, Integer> counts, int cargoSlots, boolean flying, int supercharged, Container tech,
            Predicate<Player> valid) {
        super(UniverseMenus.SHIP_PANEL.get(), id);
        this.counts = counts;
        this.cargoSlots = cargoSlots;
        this.flying = flying;
        this.supercharged = supercharged;
        this.rating = ShipRating.of(counts, cargoSlots);
        this.base = ShipStats.of(counts);
        this.valid = valid;
        for (int i = 0; i < HelmBlockEntity.SLOTS; i++) {
            this.addSlot(new TechSlot(tech, i, TECH_X + i % TECH_COLUMNS * 18, TECH_Y + i / TECH_COLUMNS * 18, this.rating.slots()));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
    }

    /** 打开界面时发给客户端：部件的个数、容器的格数、是不是在飞、超频格。 */
    static void writeOpenData(ShipStructure structure, boolean flying, int supercharged, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(structure.counts().size());
        structure.counts().forEach((part, count) -> {
            buf.writeVarInt(part.ordinal());
            buf.writeVarInt(count);
        });
        buf.writeVarInt(structure.cargoSlots());
        buf.writeBoolean(flying);
        buf.writeVarInt(supercharged);
    }

    private static Map<ShipPart, Integer> readCounts(RegistryFriendlyByteBuf buf) {
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            int part = buf.readVarInt();
            int count = buf.readVarInt();
            if (part >= 0 && part < ShipPart.values().length) {
                counts.put(ShipPart.values()[part], count);
            }
        }
        return counts;
    }

    public ShipRating rating() {
        return this.rating;
    }

    public int cargoSlots() {
        return this.cargoSlots;
    }

    /** 在飞（驾驶时打开）还是停着（潜行右键驾驶台打开）。 */
    public boolean flying() {
        return this.flying;
    }

    /** 驾驶台的超频格，按位记（{@link io.github.sweetcornna.universe.core.tech.TechGrid}）。 */
    public int supercharged() {
        return this.supercharged;
    }

    public Map<ShipPart, Integer> counts() {
        return this.counts;
    }

    /** 只按部件算的性能。 */
    public ShipStats baseStats() {
        return this.base;
    }

    /** 技术槽里现在的东西生效以后的技术。 */
    public ShipTech tech() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < HelmBlockEntity.SLOTS; i++) {
            items.add(this.slots.get(i).getItem());
        }
        return ShipTech.of(HelmBlockEntity.summary(items, this.supercharged), this.rating.slots());
    }

    /** 加上评级和技术以后的性能。 */
    public ShipStats stats() {
        ShipTech tech = this.tech();
        return this.base.tuned(this.rating, tech.bonuses(), tech.drive());
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && this.valid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (fits(stack)) {
            if (!this.moveItemStackTo(stack, 0, INVENTORY_START, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包和快捷栏之间挪
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

    /** 这个东西能不能装进飞船的技术槽：飞船模块和驱动。 */
    public static boolean fits(ItemStack stack) {
        return stack.getItem() instanceof TechModuleItem module && module.type().host() == ModuleHost.SHIP || stack.getItem() instanceof DriveItem;
    }

    /** 技术槽：一格一个，同一种模块最多三个，同一档驱动只装一台。 */
    public static class TechSlot extends Slot {
        private final int unlocked;

        TechSlot(Container container, int index, int x, int y, int unlocked) {
            super(container, index, x, y);
            this.unlocked = unlocked;
        }

        public boolean unlocked() {
            return this.getContainerSlot() < this.unlocked;
        }

        @Override
        public boolean isActive() {
            // 档次降下来以后，多出来的格子里还有东西就照样露出来，好拿走
            return this.unlocked() || this.hasItem();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!this.unlocked() || !fits(stack)) {
                return false;
            }
            for (int i = 0; i < this.container.getContainerSize(); i++) {
                if (i == this.getContainerSlot()) {
                    continue;
                }
                ItemStack other = this.container.getItem(i);
                if (stack.getItem() instanceof DriveItem drive && other.getItem() instanceof DriveItem there && there.tier() == drive.tier()) {
                    return false;
                }
            }
            return !(stack.getItem() instanceof TechModuleItem module) || this.sameType(module.type()) < ModuleType.MAX_STACK;
        }

        private int sameType(ModuleType type) {
            int count = 0;
            for (int i = 0; i < this.container.getContainerSize(); i++) {
                if (i != this.getContainerSlot() && this.container.getItem(i).getItem() instanceof TechModuleItem other && other.type() == type) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void set(ItemStack stack) {
            // 只在服务端随机（服务端的容器是 ListContainer），客户端等同步
            if (this.container instanceof ListContainer && !TechModuleItem.calibrated(stack)) {
                TechModuleItem.calibrate(stack, System.nanoTime() ^ stack.hashCode());
            }
            super.set(stack);
        }
    }
}
