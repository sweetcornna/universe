package io.github.sweetcornna.universe.fossil;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 骨架展台的界面（ADR-0034 §三）：五格按骨架的样子摆（头骨在前、尾骨在后、四肢在下），每格只收对应部位的骨头；
 * 下面是背包。按钮 {@value #PACK} 封装：展台上的骨头封成一件化石展品放进背包，展台清空。
 */
public class SkeletonDisplayMenu extends AbstractContainerMenu {
    public static final int PACK = 0;
    public static final int WIDTH = 176;
    public static final int HEIGHT = 198;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 116;
    /** 五格的位置，按 {@link BonePart} 的顺序：头骨、颈椎、胸廓、四肢、尾骨。左半边摆成一副头朝左的骨架，右半边是立体的预览。 */
    public static final int[][] SLOTS = {{8, 31}, {27, 23}, {47, 27}, {47, 49}, {67, 19}};
    private static final int PARTS = BonePart.values().length;
    private static final int INVENTORY_END = PARTS + 36;

    private final Container display;

    /** 客户端：五格由菜单同步过来。 */
    public SkeletonDisplayMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(PARTS));
    }

    public SkeletonDisplayMenu(int id, Inventory inventory, Container display) {
        super(UniverseMenus.SKELETON_DISPLAY.get(), id);
        this.display = display;
        for (BonePart part : BonePart.values()) {
            int[] at = SLOTS[part.ordinal()];
            this.addSlot(new BoneSlot(display, part, at[0], at[1]));
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

    /** 五格里现在的骨架（界面算名字和价钱用）。 */
    public Skeleton skeleton() {
        Skeleton skeleton = Skeleton.EMPTY;
        for (int i = 0; i < PARTS; i++) {
            Bone bone = BoneItem.boneOf(this.slots.get(i).getItem());
            if (bone != null) {
                skeleton = skeleton.with(bone);
            }
        }
        return skeleton;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != PACK) {
            return false;
        }
        if (!player.level().isClientSide() && this.display instanceof SkeletonDisplayBlockEntity entity) {
            ItemStack exhibit = entity.pack();
            if (!exhibit.isEmpty()) {
                Fossils.give(player, exhibit);
                player.level().playSound(null, entity.getBlockPos(), FossilSounds.PACK.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.display.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PARTS) {
            if (!this.moveItemStackTo(stack, PARTS, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            Bone bone = BoneItem.boneOf(stack);
            if (bone == null || !this.moveItemStackTo(stack, bone.part().ordinal(), bone.part().ordinal() + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** 一个部位的格：只收这个部位的骨头，一格一块。 */
    public static final class BoneSlot extends Slot {
        private final BonePart part;

        BoneSlot(Container container, BonePart part, int x, int y) {
            super(container, part.ordinal(), x, y);
            this.part = part;
        }

        public BonePart part() {
            return this.part;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Bone bone = BoneItem.boneOf(stack);
            return bone != null && bone.part() == this.part;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Identifier getNoItemIcon() {
            return Universe.id("container/slot/bone_" + this.part.id());
        }
    }
}
