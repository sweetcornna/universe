package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 把一艘船上所有箱子、木桶的格子拼成一个容器（ADR-0030 §六）：飞着的时候方块存在实体里（{@link ShipStructure}），
 * 所以打开界面时一次把方块实体都读出来，玩家改完以后再整船写回去。
 *
 * <p>按 {@link ShipStructure#blocks()} 的顺序排格子，同一艘船每次打开的顺序一样。读出来的方块实体留在
 * {@link Loaded} 里，{@link #setChanged()} 把动过的那几个写回船的结构。
 */
public final class ShipCargoContainer implements Container {
    private final ShipEntity ship;
    private final RegistryAccess registries;
    private final List<ShipStructure.ShipBlock> blocks;
    /** 每个货柜：读出来的容器和它在 {@link #blocks} 里的下标。 */
    private final List<Loaded> holds;
    /** 第 i 格在哪个货柜的哪一格：{@code slots[i]} 是货柜的下标，{@code inner[i]} 是货柜里的格号。 */
    private final int[] holdOf;
    private final int[] innerOf;

    private ShipCargoContainer(ShipEntity ship, RegistryAccess registries, List<ShipStructure.ShipBlock> blocks, List<Loaded> holds,
            int[] holdOf, int[] innerOf) {
        this.ship = ship;
        this.registries = registries;
        this.blocks = blocks;
        this.holds = holds;
        this.holdOf = holdOf;
        this.innerOf = innerOf;
    }

    /** 读出这艘船的货舱；船没有结构时格数为 0。 */
    public static ShipCargoContainer of(ShipEntity ship, RegistryAccess registries) {
        ShipStructure structure = ship.structure();
        List<ShipStructure.ShipBlock> blocks = structure == null ? List.of() : new ArrayList<>(structure.blocks());
        List<Loaded> holds = new ArrayList<>();
        List<int[]> slots = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            ShipStructure.ShipBlock block = blocks.get(i);
            if (block.blockEntity() == null) {
                continue;
            }
            BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(),
                    registries);
            if (!(entity instanceof Container container)) {
                continue;
            }
            int hold = holds.size();
            holds.add(new Loaded(entity, container, i));
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                slots.add(new int[] {hold, slot});
            }
        }
        int[] holdOf = new int[slots.size()];
        int[] innerOf = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            holdOf[i] = slots.get(i)[0];
            innerOf[i] = slots.get(i)[1];
        }
        return new ShipCargoContainer(ship, registries, blocks, holds, holdOf, innerOf);
    }

    @Override
    public int getContainerSize() {
        return this.holdOf.length;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= this.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return this.hold(slot).container.getItem(this.innerOf[slot]);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= this.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.hold(slot).container.removeItem(this.innerOf[slot], count);
        if (!taken.isEmpty()) {
            this.hold(slot).dirty = true;
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= this.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.hold(slot).container.removeItemNoUpdate(this.innerOf[slot]);
        if (!taken.isEmpty()) {
            this.hold(slot).dirty = true;
        }
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.getContainerSize()) {
            return;
        }
        this.hold(slot).container.setItem(this.innerOf[slot], stack);
        this.hold(slot).dirty = true;
    }

    /** 各货柜里最小的那个上限：箱子、木桶都是 99，有上限小的货柜（比如漏斗）就跟着它。 */
    @Override
    public int getMaxStackSize() {
        int max = 99;
        for (Loaded hold : this.holds) {
            max = Math.min(max, hold.container.getMaxStackSize());
        }
        return max;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.getContainerSize()) {
            return false;
        }
        return this.hold(slot).container.canPlaceItem(this.innerOf[slot], stack);
    }

    /** 动过的货柜写回船的结构：船在飞还是停着都一样，结构是权威的那一份。 */
    @Override
    public void setChanged() {
        boolean changed = false;
        for (Loaded hold : this.holds) {
            if (!hold.dirty) {
                continue;
            }
            hold.dirty = false;
            ShipStructure.ShipBlock block = this.blocks.get(hold.index);
            this.blocks.set(hold.index, new ShipStructure.ShipBlock(block.x(), block.y(), block.z(), block.state(),
                    hold.entity.saveWithFullMetadata(this.registries)));
            changed = true;
        }
        if (changed) {
            this.ship.replaceStructure(new ShipStructure(this.blocks));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.ship.isAlive() && (player.getVehicle() == this.ship || player.distanceToSqr(this.ship) <= 64 * 64);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            this.setItem(i, ItemStack.EMPTY);
        }
        this.setChanged();
    }

    /** 读出来的一个货柜。 */
    private static final class Loaded {
        private final BlockEntity entity;
        private final Container container;
        private final int index;
        private boolean dirty;

        private Loaded(BlockEntity entity, Container container, int index) {
            this.entity = entity;
            this.container = container;
            this.index = index;
        }
    }

    private Loaded hold(int slot) {
        return this.holds.get(this.holdOf[slot]);
    }
}
