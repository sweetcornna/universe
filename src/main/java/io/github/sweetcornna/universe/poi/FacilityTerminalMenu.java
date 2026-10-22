package io.github.sweetcornna.universe.poi;

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
 * 制造设施终端的界面（ADR-0026）：上面一排三个技术模块，下面是背包。拿走一个，另外两个就没了，这台终端对这个人用过了。
 * 模块只能拿、不能放回去。
 */
public class FacilityTerminalMenu extends AbstractContainerMenu {
    public static final int OFFERS = 3;
    public static final int WIDTH = 176;
    public static final int HEIGHT = 170;
    public static final int OFFER_Y = 40;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 88;
    private static final int INVENTORY_START = OFFERS;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final Container offers;
    private final Predicate<Player> valid;
    private final Runnable onChoose;
    private boolean chosen;

    /** 客户端：三个模块由菜单同步过来。 */
    public FacilityTerminalMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(OFFERS), player -> true, () -> {
        });
    }

    FacilityTerminalMenu(int id, Inventory inventory, Container offers, Predicate<Player> valid, Runnable onChoose) {
        super(UniverseMenus.FACILITY_TERMINAL.get(), id);
        this.offers = offers;
        this.valid = valid;
        this.onChoose = onChoose;
        for (int i = 0; i < OFFERS; i++) {
            this.addSlot(new OfferSlot(offers, i, offerX(i), OFFER_Y));
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

    /** 第几个模块格的横坐标：三格隔开一格排在中间。 */
    public static int offerX(int index) {
        return WIDTH / 2 - 8 + (index - 1) * 36;
    }

    /** 挑了一个：别的收走，记下用过。 */
    private void choose(int index) {
        if (this.chosen) {
            return;
        }
        this.chosen = true;
        for (int i = 0; i < OFFERS; i++) {
            if (i != index) {
                this.offers.setItem(i, ItemStack.EMPTY);
            }
        }
        this.onChoose.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && this.valid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (index >= INVENTORY_START || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
            return ItemStack.EMPTY;
        }
        slot.setByPlayer(ItemStack.EMPTY);
        slot.onTake(player, original);
        return ItemStack.EMPTY;
    }

    /** 模块格：只能拿。 */
    private final class OfferSlot extends Slot {
        OfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !FacilityTerminalMenu.this.chosen;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            FacilityTerminalMenu.this.choose(this.getContainerSlot());
        }
    }
}
