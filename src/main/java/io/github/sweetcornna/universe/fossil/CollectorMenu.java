package io.github.sweetcornna.universe.fossil;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 化石收藏家的界面（ADR-0034 §三）：左边一格放化石展品，按"卖给他"拿信用点；右边三格放不要的骨头，选好想要的类群和部位，
 * 按"换"换一块。东西在界面里只是暂放，关掉时还回背包。买卖都在服务端（{@link FossilCollector}）判定。
 *
 * <p>按钮：{@value #SELL} 卖展品，{@value #SWAP} 换骨头，{@value #FAMILY_PREV}–{@value #PART_NEXT} 翻想要的类群、部位，
 * {@value #PICK} + 类群序号 × 5 + 部位序号直接选一块（开发脚本用）。
 */
public class CollectorMenu extends AbstractContainerMenu {
    public static final int SELL = 0;
    public static final int SWAP = 1;
    public static final int FAMILY_PREV = 2;
    public static final int FAMILY_NEXT = 3;
    public static final int PART_PREV = 4;
    public static final int PART_NEXT = 5;
    public static final int PICK = 100;
    public static final int WIDTH = 176;
    public static final int HEIGHT = 196;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 114;
    /** 展品格、三个换骨头的格。 */
    public static final int[] EXHIBIT_SLOT = {8, 30};
    public static final int[][] SWAP_SLOTS = {{94, 30}, {112, 30}, {130, 30}};
    static final int SLOTS = 1 + CollectorTrade.SWAP_COST;
    private static final int INVENTORY_END = SLOTS + 36;
    /** 离收藏家这么远以内才能交易。 */
    static final double REACH = 8;

    private final Container goods;
    private final @Nullable Entity npc;
    private final DataSlot family = DataSlot.standalone();
    private final DataSlot part = DataSlot.standalone();

    /** 客户端。 */
    public CollectorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, (Entity) null);
        buf.readVarInt();
    }

    public CollectorMenu(int id, Inventory inventory, @Nullable Entity npc) {
        super(UniverseMenus.FOSSIL_COLLECTOR.get(), id);
        this.npc = npc;
        this.goods = new SimpleContainer(SLOTS);
        this.addSlot(new Slot(this.goods, 0, EXHIBIT_SLOT[0], EXHIBIT_SLOT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FossilExhibitItem;
            }

            @Override
            public Identifier getNoItemIcon() {
                return Universe.id("container/slot/fossil_exhibit");
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < CollectorTrade.SWAP_COST; i++) {
            this.addSlot(new Slot(this.goods, 1 + i, SWAP_SLOTS[i][0], SWAP_SLOTS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return BoneItem.boneOf(stack) != null;
                }

                @Override
                public Identifier getNoItemIcon() {
                    return Universe.id("container/slot/bone_any");
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
        this.addDataSlot(this.family);
        this.addDataSlot(this.part);
    }

    /** 放在展品格里的骨架。 */
    public Skeleton exhibit() {
        return FossilExhibitItem.skeleton(this.goods.getItem(0));
    }

    public ItemStack exhibitStack() {
        return this.goods.getItem(0);
    }

    /** 三个换骨头的格里的骨头（空格是 null）。 */
    public List<Bone> offered() {
        List<Bone> bones = new ArrayList<>();
        for (int i = 1; i < SLOTS; i++) {
            bones.add(BoneItem.boneOf(this.goods.getItem(i)));
        }
        return bones;
    }

    /** 想换的那块。 */
    public Bone wanted() {
        BoneFamily f = BoneFamily.byOrdinal(Math.floorMod(this.family.get(), BoneFamily.values().length));
        BonePart p = BonePart.byOrdinal(Math.floorMod(this.part.get(), BonePart.values().length));
        return new Bone(f, p);
    }

    public CollectorTrade.Swap swapCheck() {
        return CollectorTrade.check(this.offered(), this.wanted());
    }

    /** 收走展品格和换骨头的格里的东西（交易成了以后）。 */
    void consumeExhibit() {
        this.goods.removeItem(0, 1);
    }

    void consumeOffered() {
        for (int i = 1; i < SLOTS; i++) {
            this.goods.removeItem(i, 1);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        int families = BoneFamily.values().length;
        int parts = BonePart.values().length;
        if (button >= PICK && button < PICK + families * parts) {
            this.family.set((button - PICK) / parts);
            this.part.set((button - PICK) % parts);
            return true;
        }
        switch (button) {
            case FAMILY_PREV -> this.family.set(Math.floorMod(this.family.get() - 1, families));
            case FAMILY_NEXT -> this.family.set(Math.floorMod(this.family.get() + 1, families));
            case PART_PREV -> this.part.set(Math.floorMod(this.part.get() - 1, parts));
            case PART_NEXT -> this.part.set(Math.floorMod(this.part.get() + 1, parts));
            case SELL -> FossilCollector.sell(player, this);
            case SWAP -> FossilCollector.swap(player, this);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.npc == null || this.npc.isAlive() && player.distanceToSqr(this.npc) < REACH * REACH;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            this.clearContainer(player, this.goods);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOTS) {
            if (!this.moveItemStackTo(stack, SLOTS, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof FossilExhibitItem) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (BoneItem.boneOf(stack) != null) {
            if (!this.moveItemStackTo(stack, 1, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
