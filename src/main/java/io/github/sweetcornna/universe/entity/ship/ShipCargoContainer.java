package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.deepspace.ShipSides;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.deepspace.ShipTractor;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 把一艘船上所有箱子、木桶的格子拼成一个容器（ADR-0030 §六）：飞着的时候方块存在实体里（{@link ShipStructure}），
 * 所以先把方块实体都读出来，改完以后再写回船的结构。
 *
 * <p>按 {@link ShipStructure#blocks()} 的顺序排格子，同一艘船每次打开的顺序一样。读出来的方块实体留在
 * {@link Loaded} 里，{@link #setChanged()} 只把动过的那几个货柜换进船上现在的结构，别的方块（驾驶台的技术）不动。
 *
 * <p>船的结构是唯一的那一份：几个人同时开着货舱共用一个容器（{@link #forShip}）；船上的结构被别处换过
 * （另一个容器写回了、挖矿和战利品装了货、击毁丢了货、改了技术），下次读写前先按现在的结构重读，
 * 不会拿旧的那份写回去，也不会有人看着已经被拿走的东西。
 */
public final class ShipCargoContainer implements Container {
    private final ShipEntity ship;
    private final RegistryAccess registries;
    /** 读出货柜时船上的那份结构。 */
    private @Nullable ShipStructure source;
    /** 每个货柜：读出来的容器和它在结构的方块表里的下标。 */
    private List<Loaded> holds = List.of();
    /** 第 i 格在哪个货柜的哪一格：{@code holdOf[i]} 是货柜的下标，{@code innerOf[i]} 是货柜里的格号。 */
    private int[] holdOf = new int[0];
    private int[] innerOf = new int[0];

    private ShipCargoContainer(ShipEntity ship, RegistryAccess registries) {
        this.ship = ship;
        this.registries = registries;
        this.load(ship.structure());
    }

    /** 读出这艘船的货舱；船没有结构时格数为 0。 */
    public static ShipCargoContainer of(ShipEntity ship, RegistryAccess registries) {
        return new ShipCargoContainer(ship, registries);
    }

    /** 按 {@code structure} 读出所有货柜。 */
    private void load(@Nullable ShipStructure structure) {
        this.source = structure;
        List<ShipStructure.ShipBlock> blocks = structure == null ? List.of() : structure.blocks();
        List<Loaded> holds = new ArrayList<>();
        List<int[]> slots = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            ShipStructure.ShipBlock block = blocks.get(i);
            if (block.blockEntity() == null) {
                continue;
            }
            BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(),
                    this.registries);
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
        this.holds = holds;
        this.holdOf = holdOf;
        this.innerOf = innerOf;
    }

    /** 船上的结构被别处换过：先把这边还没写回去的改动写上去，再按现在的结构重读。 */
    private void refresh() {
        if (this.ship.structure() != this.source) {
            this.setChanged();
            this.load(this.ship.structure());
        }
    }

    /**
     * 这艘船的货舱容器：有人开着这艘船的货舱界面时用界面那一份（再开货舱的人、牵引光束、碎片都用它，ADR-0034 §四），
     * 没有就新读一份。
     */
    public static ShipCargoContainer forShip(ShipEntity ship, RegistryAccess registries) {
        if (ship.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof ShipCargoMenu menu && menu.cargo() instanceof ShipCargoContainer open && open.ship == ship) {
                    return open;
                }
            }
        }
        return of(ship, registries);
    }

    public ShipEntity ship() {
        return this.ship;
    }

    /**
     * 装东西：先叠到同样的东西上，再放进空格。{@code stack} 会被减掉装进去的数量，返回装进去了几件。
     * 改动要等 {@link #setChanged()} 才写回船上（一批装完再写一次）。
     */
    public int insert(ItemStack stack) {
        int before = stack.getCount();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < this.getContainerSize() && !stack.isEmpty(); i++) {
                if (!this.canPlaceItem(i, stack)) {
                    continue;
                }
                ItemStack there = this.getItem(i);
                int limit = Math.min(this.getMaxStackSize(stack), stack.getMaxStackSize());
                if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < limit) {
                    int move = Math.min(stack.getCount(), limit - there.getCount());
                    there.grow(move);
                    this.setItem(i, there);
                    stack.shrink(move);
                } else if (pass == 1 && there.isEmpty()) {
                    this.setItem(i, stack.split(Math.min(stack.getCount(), limit)));
                }
            }
        }
        return before - stack.getCount();
    }

    /**
     * 抛货（ADR-0034 §四）：第 {@code slot} 格的东西从船尾飘出去；{@code slot} 为 −1 时全部抛出。返回抛了几格。
     * 抛出去的东西一会儿之内牵引光束不吸回来。
     */
    public int jettison(int slot) {
        if (!(this.ship.level() instanceof ServerLevel level) || this.ship.structure() == null) {
            return 0;
        }
        ShipShape shape = this.ship.structure().shape();
        double[] center = {this.ship.getX(), this.ship.getY() + shape.height() / 2.0, this.ship.getZ()};
        boolean space = SpaceDimensions.isSpace(level.dimension());
        int from = slot < 0 ? 0 : slot;
        int to = slot < 0 ? this.getContainerSize() : Math.min(slot + 1, this.getContainerSize());
        int thrown = 0;
        for (int i = from; i < to; i++) {
            ItemStack stack = this.removeItemNoUpdate(i);
            if (stack.isEmpty()) {
                continue;
            }
            double[] at = ShipSides.jettison(center, this.ship.getYRot(), shape.radius(), thrown);
            ItemEntity item = new ItemEntity(level, at[0], at[1], at[2], stack, at[3], at[4], at[5]);
            item.setPickUpDelay(40);
            item.setNoGravity(space);
            ShipTractor.ignore(item, level.getGameTime() + ShipTractor.JETTISON_IGNORE);
            level.addFreshEntity(item);
            thrown++;
        }
        if (thrown > 0) {
            this.setChanged();
            double[] at = ShipSides.jettison(center, this.ship.getYRot(), shape.radius(), 0);
            level.playSound(null, at[0], at[1], at[2], DeepSpaceSounds.JETTISON.get(), SoundSource.NEUTRAL, 0.9F, thrown > 3 ? 0.85F : 1.0F);
        }
        return thrown;
    }

    @Override
    public int getContainerSize() {
        this.refresh();
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
        this.refresh();
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

    /**
     * 动过的货柜写回船的结构：船在飞还是停着都一样，结构是权威的那一份。写在船上现在那份结构上，只换动过的货柜
     * （飞着的船方块的位置和种类不变，下标对得上）。
     */
    @Override
    public void setChanged() {
        ShipStructure current = this.ship.structure();
        List<ShipStructure.ShipBlock> blocks = null;
        for (Loaded hold : this.holds) {
            if (!hold.dirty) {
                continue;
            }
            hold.dirty = false;
            // 船没了、或者结构换了样（数据被命令改过）就不写，免得写到别的方块上
            if (current == null || hold.index >= current.blocks().size()) {
                continue;
            }
            if (blocks == null) {
                blocks = new ArrayList<>(current.blocks());
            }
            ShipStructure.ShipBlock block = blocks.get(hold.index);
            blocks.set(hold.index, new ShipStructure.ShipBlock(block.x(), block.y(), block.z(), block.state(),
                    hold.entity.saveWithFullMetadata(this.registries)));
        }
        if (blocks != null) {
            ShipStructure written = new ShipStructure(blocks);
            this.ship.replaceStructure(written);
            // 只有这边写过：读出来的货柜和新结构一样，不用重读
            if (current == this.source) {
                this.source = written;
            }
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
