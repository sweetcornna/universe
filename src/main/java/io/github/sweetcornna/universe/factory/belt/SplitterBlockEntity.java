package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 分流器（ADR-0015）：四面都能接传送带。朝它开过来的传送带把物品送进来（{@link BeltReceiver}），它再轮流放到
 * 从它这里开出去的传送带上；哪边是进、哪边是出看传送带的朝向，不用设置。一边堵住了就跳过那边。
 */
public class SplitterBlockEntity extends BlockEntity implements BeltReceiver {
    /** 缓冲几个物品：够每刻放两个，Mk.III 传送带（每秒 30 个）汇进来也不堵。 */
    private static final int BUFFER = 4;
    private static final int PER_TICK = 2;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final ArrayDeque<Item> buffer = new ArrayDeque<>(BUFFER);
    /** 下一个先试的出口（轮流）。 */
    private int next;

    public SplitterBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SPLITTER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            BeltNetwork.of(this.level).outputChanged(this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            if (network != null) {
                network.outputChanged(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public boolean acceptFromBelt(Item item) {
        if (this.buffer.size() >= BUFFER) {
            return false;
        }
        this.buffer.addLast(item);
        this.setChanged();
        return true;
    }

    void serverTick() {
        if (this.buffer.isEmpty() || this.level == null) {
            return;
        }
        BeltNetwork network = BeltNetwork.of(this.level);
        for (int moved = 0; moved < PER_TICK && !this.buffer.isEmpty(); moved++) {
            int id = BeltNetwork.id(this.buffer.peekFirst());
            boolean placed = false;
            for (int k = 0; k < SIDES.length; k++) {
                int side = (this.next + k) % SIDES.length;
                BlockPos pos = this.worldPosition.relative(SIDES[side]);
                if (this.outgoing(pos, SIDES[side]) && network.insertAt(pos, 0, id)) {
                    this.buffer.pollFirst();
                    this.next = (side + 1) % SIDES.length;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                break;
            }
            this.setChanged();
        }
    }

    /** 这一边是不是一条从分流器开出去的传送带（朝向背对分流器）。 */
    private boolean outgoing(BlockPos pos, Direction side) {
        BlockState state = this.level.getBlockState(pos);
        return state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.FACING) == side
                && BeltNetwork.of(this.level).segment(pos) != null;
    }

    /** 拆掉时缓冲里的东西掉出来。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            for (Item item : this.buffer) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(item));
            }
            this.buffer.clear();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.buffer.clear();
        input.read("Buffer", BuiltInRegistries.ITEM.byNameCodec().listOf()).ifPresent(this.buffer::addAll);
        this.next = input.getIntOr("Next", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Item> items = new ArrayList<>(this.buffer);
        if (!items.isEmpty()) {
            output.store("Buffer", BuiltInRegistries.ITEM.byNameCodec().listOf(), items);
        }
        output.putInt("Next", this.next);
    }
}
