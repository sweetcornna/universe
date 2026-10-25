package io.github.sweetcornna.universe.fishing;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.ListContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 钓鱼器的饵格（ADR-0034 §一）：钓鱼模式下潜行右键打开，一格放鱼饵（能吃的东西、做好的菜、专门的饵），下面是背包。
 * 饵存在主手工具的数据组件上；那一格快捷栏锁住，免得把工具拿走。
 *
 * <p>服务端认准打开时的那一把工具：饵只写回它，那一格换成别的东西（哪怕是另一把工具）界面就失效，
 * 免得饵写到另一把上、原来那把还留着一份。
 */
public class BaitMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 150;
    public static final int BAIT_X = 26;
    public static final int BAIT_Y = 28;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 68;

    private final int toolSlot;
    /** 服务端：打开时主手的那一把工具；客户端为 {@code null}。 */
    private final @Nullable ItemStack tool;

    /** 客户端：只拿到工具在第几格，饵由菜单同步。 */
    public BaitMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt(), null, new SimpleContainer(1));
    }

    private BaitMenu(int id, Inventory inventory, int toolSlot, @Nullable ItemStack tool, Container bait) {
        super(UniverseMenus.BAIT.get(), id);
        this.toolSlot = toolSlot;
        this.tool = tool;
        this.addSlot(new Slot(bait, 0, BAIT_X, BAIT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Fishing.isBait(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = INVENTORY_X + column * 18;
            int y = INVENTORY_Y + 58;
            this.addSlot(column == toolSlot ? new Slot(inventory, column, x, y) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            } : new Slot(inventory, column, x, y));
        }
    }

    /** 服务端：直接读写主手工具的饵格。写回打开时的那一把，不是那一格里现在放着的。 */
    public static BaitMenu server(int id, Inventory inventory, int toolSlot) {
        ItemStack tool = inventory.getItem(toolSlot);
        NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
        items.set(0, Fishing.bait(tool));
        Container bait = new ListContainer(items, () -> Fishing.setBait(tool, items.get(0)));
        return new BaitMenu(id, inventory, toolSlot, tool, bait);
    }

    /** 饵格里现在放着的东西。 */
    public ItemStack bait() {
        return this.slots.getFirst().getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack held = player.getInventory().getItem(this.toolSlot);
        // 服务端要还是打开时那一把（扔掉以后原来那份数量是 0），客户端的物品会被同步换掉，只看是不是工具
        return player.isAlive() && (this.tool != null ? held == this.tool && !held.isEmpty() : held.getItem() instanceof MultitoolItem);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        // 数字键换格直接读写快捷栏，不经过锁住的那一格：按工具那一格的数字键会把工具换走
        if (input == ContainerInput.SWAP && buttonNum == this.toolSlot) {
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!Fishing.isBait(stack) || !this.moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
