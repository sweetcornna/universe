package io.github.sweetcornna.universe.entity.ship;

import java.util.function.Predicate;

import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 飞船货舱的界面（ADR-0030 §六）：船上所有箱子、木桶的格子拼成一片，9 列一行，最多露出 6 行，再多就滚动。
 *
 * <p>服务端的容器是 {@link ShipCargoContainer}（直接读写船的结构），客户端是同样大小的 {@link SimpleContainer}，
 * 内容由菜单同步。{@code Slot} 的位置是 final 的，所以滚动不挪格子：界面上固定 9×6 个格子，每个格子按滚动的行数
 * 换算到货舱的第几格（{@link #index}），和原版创造模式的物品栏一个路子。船上一个货柜都没有时格数为 0，
 * 界面里写一行"船上没有货柜"。
 */
public class ShipCargoMenu extends AbstractContainerMenu {
    /** 一行 9 格。 */
    public static final int COLUMNS = 9;
    /** 最多同时露出几行，再多就滚动。 */
    public static final int VISIBLE_ROWS = 6;
    public static final int WIDTH = 194;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 22;
    /** 格子滚动条的宽度。 */
    public static final int SCROLLBAR_WIDTH = 12;

    private final Container cargo;
    private final int rows;
    private final int visibleRows;
    private final Predicate<Player> valid;
    /** 滚到第几行（0 是最上面一行）。 */
    private int scroll;

    /** 客户端：只拿到格数，格子里的东西由菜单同步。 */
    public ShipCargoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(buf.readVarInt()), player -> true);
    }

    ShipCargoMenu(int id, Inventory inventory, Container cargo, Predicate<Player> valid) {
        super(UniverseMenus.SHIP_CARGO.get(), id);
        this.cargo = cargo;
        this.valid = valid;
        this.rows = Math.ceilDiv(cargo.getContainerSize(), COLUMNS);
        this.visibleRows = Math.min(Math.max(this.rows, 1), VISIBLE_ROWS);
        for (int row = 0; row < this.visibleRows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new CargoSlot(cargo, row, column, GRID_X + column * 18, GRID_Y + row * 18));
            }
        }
        int inventoryY = GRID_Y + this.visibleRows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, GRID_X + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            this.addSlot(new Slot(inventory, column, GRID_X + column * 18, inventoryY + 58));
        }
    }

    /** 打开界面时发给客户端：货舱一共几格。 */
    static void writeOpenData(Container cargo, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(cargo.getContainerSize());
    }

    public int cargoSize() {
        return this.cargo.getContainerSize();
    }

    /** 货舱一共几行。 */
    public int rows() {
        return this.rows;
    }

    /** 界面上露出来的行数（至少 1 行，好画"没有货柜"）。 */
    public int visibleRows() {
        return this.visibleRows;
    }

    /** 界面上一共几个货舱格子。 */
    public int gridSlots() {
        return this.visibleRows * COLUMNS;
    }

    /** 要不要滚动条。 */
    public boolean scrollable() {
        return this.rows > VISIBLE_ROWS;
    }

    /** 最多能滚到第几行。 */
    public int maxScroll() {
        return Math.max(0, this.rows - VISIBLE_ROWS);
    }

    public int scroll() {
        return this.scroll;
    }

    /** 滚到第 row 行；超出范围的夹住。 */
    public void setScroll(int row) {
        this.scroll = Math.clamp(row, 0, this.maxScroll());
    }

    /**
     * 客户端滚了滚动条：按钮号就是要滚到的行。服务端也要跟着滚，不然两边的格子对着货舱里不同的格，
     * 拿东西会拿错一格。
     *
     * <p>负数是抛货（ADR-0034 §四）：{@link #JETTISON_ALL} 全部抛出，{@link #jettisonButton} 换算出来的是抛第几格。
     * 只有坐在船上的人能抛。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0) {
            if (this.cargo instanceof ShipCargoContainer ship && player.getVehicle() == ship.ship()) {
                return ship.jettison(id == JETTISON_ALL ? -1 : -2 - id) > 0;
            }
            return false;
        }
        this.setScroll(id);
        return true;
    }

    /** 抛货的按钮号：全部抛出。 */
    public static final int JETTISON_ALL = -1;

    /** 抛货的按钮号：抛货舱的第 {@code index} 格。 */
    public static int jettisonButton(int index) {
        return -2 - index;
    }

    /** 服务端是 {@link ShipCargoContainer}，客户端是同步用的 {@link SimpleContainer}。 */
    Container cargo() {
        return this.cargo;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && this.valid.test(player) && this.cargo.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int gridEnd = this.gridSlots();
        int inventoryEnd = gridEnd + 27;
        int end = this.slots.size();
        if (index < gridEnd) {
            if (!this.moveItemStackTo(stack, gridEnd, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.insertIntoCargo(stack)) {
            // 货舱满了（或者船上没有货柜）就在背包和快捷栏之间挪
            if (index < inventoryEnd) {
                if (!this.moveItemStackTo(stack, inventoryEnd, end, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, gridEnd, inventoryEnd, false)) {
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
     * Shift 点击往货舱里送：直接按货舱的格号走，滚动条外面的格子也收得到。先叠到同样的东西上，再放空格。
     * 返回有没有动过东西。
     */
    private boolean insertIntoCargo(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < this.cargo.getContainerSize() && !stack.isEmpty(); i++) {
                if (!this.cargo.canPlaceItem(i, stack)) {
                    continue;
                }
                ItemStack there = this.cargo.getItem(i);
                int limit = Math.min(this.cargo.getMaxStackSize(stack), stack.getMaxStackSize());
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < limit) {
                    int move = Math.min(stack.getCount(), limit - there.getCount());
                    there.grow(move);
                    this.cargo.setItem(i, there);
                    stack.shrink(move);
                    moved = true;
                } else if (pass == 1 && there.isEmpty()) {
                    this.cargo.setItem(i, stack.split(Math.min(stack.getCount(), limit)));
                    moved = true;
                }
            }
        }
        if (moved) {
            this.cargo.setChanged();
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 最后再写回一次：取走、放下时每次都写过了，这里管住拖拽之类不走 setChanged 的改动
        this.cargo.setChanged();
    }

    /**
     * 界面上的一个货舱格子：固定在第 row 行第 column 列，读写的是货舱里第 {@code (scroll + row) * 9 + column} 格。
     * 滚动以后同一个格子换去读另一格。货舱格数不够时（最后一行不满、船上没有货柜）这一格空着、点不到。
     */
    public class CargoSlot extends Slot {
        private final int row;
        private final int column;

        CargoSlot(Container container, int row, int column, int x, int y) {
            super(container, 0, x, y);
            this.row = row;
            this.column = column;
        }

        /** 这一格现在对着货舱的第几格；超出货舱范围时是 −1。 */
        public int index() {
            int index = (ShipCargoMenu.this.scroll + this.row) * COLUMNS + this.column;
            return index < ShipCargoMenu.this.cargo.getContainerSize() ? index : -1;
        }

        @Override
        public boolean isActive() {
            return this.index() >= 0;
        }

        @Override
        public ItemStack getItem() {
            int index = this.index();
            return index < 0 ? ItemStack.EMPTY : ShipCargoMenu.this.cargo.getItem(index);
        }

        @Override
        public boolean hasItem() {
            return !this.getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            int index = this.index();
            if (index >= 0) {
                ShipCargoMenu.this.cargo.setItem(index, stack);
                this.setChanged();
            }
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
            this.set(stack);
        }

        @Override
        public ItemStack remove(int count) {
            int index = this.index();
            if (index < 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = ShipCargoMenu.this.cargo.removeItem(index, count);
            if (!taken.isEmpty()) {
                this.setChanged();
            }
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            int index = this.index();
            return index >= 0 && ShipCargoMenu.this.cargo.canPlaceItem(index, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.index() >= 0;
        }

        @Override
        public int getMaxStackSize() {
            return ShipCargoMenu.this.cargo.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return ShipCargoMenu.this.cargo.getMaxStackSize(stack);
        }

        @Override
        public void setChanged() {
            ShipCargoMenu.this.cargo.setChanged();
        }
    }
}
