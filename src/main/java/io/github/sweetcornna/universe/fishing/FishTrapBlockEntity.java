package io.github.sweetcornna.universe.fishing;

import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.Catch;
import io.github.sweetcornna.universe.core.fishing.CatchRoll;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.sound.FishingSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/**
 * 捕鱼笼（ADR-0034 §一）：第 0 格是饵，1–9 格是钓上来的东西。每秒看一次泡没泡在水里，进度攒满 3 分钟（有饵时 1 分半）
 * 按那里的水域进一样：只出普通和少见的鱼，杂物 15%；有饵时用掉一个，饵的效果照钓鱼器的算。9 格满了就停。
 */
public class FishTrapBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int BAIT_SLOT = 0;
    public static final int OUTPUT_SLOTS = 9;
    public static final int SLOTS = 1 + OUTPUT_SLOTS;
    /** 没有饵时多少刻进一条。 */
    public static final int CATCH_TICKS = 3 * 60 * 20;
    /** 界面上的数据：进度（刻）、状态、水域。 */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_HABITAT = 2;
    public static final int DATA_COUNT = 3;

    public enum Status {
        WORKING,
        /** 没整个泡在水里。 */
        DRY,
        /** 输出满了。 */
        FULL
    }

    private static final int[] TOP = {BAIT_SLOT};
    private static final int[] OUTPUTS = IntStream.rangeClosed(1, OUTPUT_SLOTS).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int progress;
    private Status status = Status.DRY;
    private int habitat = -1;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> FishTrapBlockEntity.this.progress;
                case DATA_STATUS -> FishTrapBlockEntity.this.status.ordinal();
                case DATA_HABITAT -> FishTrapBlockEntity.this.habitat;
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

    public FishTrapBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FISH_TRAP.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FishTrapBlockEntity trap) {
        if (level instanceof ServerLevel server && level.getGameTime() % 20 == Math.floorMod(pos.asLong(), 20)) {
            trap.second(server, pos, state);
        }
    }

    /** 每秒一次。 */
    private void second(ServerLevel level, BlockPos pos, BlockState state) {
        Fishing.Water water = submerged(level, pos, state) ? Fishing.water(level, pos).orElse(null) : null;
        this.habitat = water == null ? -1 : water.habitat().ordinal();
        if (water == null) {
            this.status = Status.DRY;
            return;
        }
        if (this.full()) {
            this.status = Status.FULL;
            return;
        }
        this.status = Status.WORKING;
        ItemStack bait = this.items.get(BAIT_SLOT);
        this.progress += bait.isEmpty() ? 20 : 40;
        if (this.progress >= CATCH_TICKS) {
            this.progress = 0;
            this.catchOne(level, pos, water, bait);
        }
        this.setChanged();
    }

    /** 自己含水，上面一格也是水。 */
    public static boolean submerged(Level level, BlockPos pos, BlockState state) {
        return state.getValue(FishTrapBlock.WATERLOGGED) && level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    private void catchOne(ServerLevel level, BlockPos pos, Fishing.Water water, ItemStack bait) {
        Bait kind = Fishing.baitKind(bait);
        Catch caught = CatchRoll.roll(Fishing.pool(level, water.habitat()), Fishing.situation(level, pos, water.depth()), kind, false,
                CatchRoll.Odds.TRAP, Rng.of(level.getRandom().nextLong()));
        if (!bait.isEmpty()) {
            bait.shrink(1);
        }
        this.insert(Fishing.stack(caught, level.getRandom()));
        level.playSound(null, pos, FishingSounds.TRAP.get(), SoundSource.BLOCKS, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /** 放进输出格，先叠到同样的堆上。 */
    private void insert(ItemStack stack) {
        for (int i = 1; i < SLOTS && !stack.isEmpty(); i++) {
            ItemStack slot = this.items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 1; i < SLOTS && !stack.isEmpty(); i++) {
            if (this.items.get(i).isEmpty()) {
                this.items.set(i, stack.split(stack.getMaxStackSize()));
            }
        }
    }

    /** 输出格都放满了（每一格都有东西而且叠满）。 */
    private boolean full() {
        for (int i = 1; i < SLOTS; i++) {
            ItemStack slot = this.items.get(i);
            if (slot.isEmpty() || slot.getCount() < slot.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    public ContainerData data() {
        return this.data;
    }

    public Status status() {
        return this.status;
    }

    public int progress() {
        return this.progress;
    }

    /** 测试用：直接把进度推到快满。 */
    public void setProgress(int progress) {
        this.progress = progress;
    }

    /** 物品能力：从上面放饵，从侧面和下面取东西。 */
    public ResourceHandler<ItemResource> handler(@Nullable Direction side) {
        return new WorldlyContainerWrapper(this, side);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : OUTPUTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == BAIT_SLOT && this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != BAIT_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == BAIT_SLOT && Fishing.isBait(stack);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected Component getDefaultName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FishTrapMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("progress", this.progress);
    }
}
