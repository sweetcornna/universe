package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 分流器（ADR-0015）：四面都能接传送带。朝它开过来的传送带把货物送进来（{@link BeltReceiver}），它再轮流放到
 * 从它这里开出去的传送带上；哪边是进、哪边是出看传送带的朝向，不用设置。一边堵住了就跳过那边。叠着的货物
 * 整格放出去（ADR-0022）。
 *
 * <p>可以设一个优先出口（ADR-0022）：没有过滤物品时货物先走优先出口，堵了再轮流走别的；设了过滤物品时，过滤物品只走
 * 优先出口，别的物品只走其他出口。没设优先出口时过滤物品不起作用。
 */
public class SplitterBlockEntity extends BlockEntity implements BeltReceiver {
    /** 缓冲几格货物：够每刻放两格，Mk.III 传送带（每秒 30 格）汇进来也不堵。 */
    private static final int BUFFER = 4;
    private static final int PER_TICK = 2;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Codec<List<Item>> ITEMS_CODEC = BuiltInRegistries.ITEM.byNameCodec().listOf();

    private final IntArrayFIFOQueue buffer = new IntArrayFIFOQueue(BUFFER);
    /** 下一个先试的出口（轮流）。 */
    private int next;
    private @Nullable Direction priority;
    private @Nullable Item filter;

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

    public @Nullable Direction priority() {
        return this.priority;
    }

    public @Nullable Item filter() {
        return this.filter;
    }

    /** 把这一边设成优先出口；已经是了就取消。返回现在的优先出口。 */
    public @Nullable Direction togglePriority(Direction side) {
        this.priority = this.priority == side ? null : side;
        this.changed();
        return this.priority;
    }

    public void setFilter(@Nullable Item filter) {
        this.filter = filter == Items.AIR ? null : filter;
        this.changed();
    }

    private void changed() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean acceptFromBelt(int cargo) {
        if (this.buffer.size() >= BUFFER) {
            return false;
        }
        this.buffer.enqueue(cargo);
        this.setChanged();
        return true;
    }

    void serverTick() {
        if (this.buffer.isEmpty() || this.level == null) {
            return;
        }
        BeltNetwork network = BeltNetwork.of(this.level);
        for (int moved = 0; moved < PER_TICK && !this.buffer.isEmpty(); moved++) {
            int cargo = this.buffer.firstInt();
            if (!this.place(network, cargo)) {
                break;
            }
            this.buffer.dequeueInt();
            this.setChanged();
        }
    }

    /** 按优先出口和过滤物品放出去一格。 */
    private boolean place(BeltNetwork network, int cargo) {
        Direction priority = this.priority;
        boolean filtered = priority != null && this.filter != null;
        boolean matches = filtered && BeltNetwork.item(cargo) == this.filter;
        if (priority != null && (!filtered || matches)) {
            BlockPos pos = this.worldPosition.relative(priority);
            if (this.outgoing(pos, priority) && network.insertAt(pos, 0, cargo)) {
                return true;
            }
            if (matches) {
                // 过滤物品只走优先出口，堵了就等着
                return false;
            }
        }
        for (int k = 0; k < SIDES.length; k++) {
            int side = (this.next + k) % SIDES.length;
            if (SIDES[side] == priority) {
                continue;
            }
            BlockPos pos = this.worldPosition.relative(SIDES[side]);
            if (this.outgoing(pos, SIDES[side]) && network.insertAt(pos, 0, cargo)) {
                this.next = (side + 1) % SIDES.length;
                return true;
            }
        }
        return false;
    }

    /** 这一边是不是一条从分流器开出去的传送带（朝向背对分流器）。 */
    private boolean outgoing(BlockPos pos, Direction side) {
        BlockState state = this.level.getBlockState(pos);
        return state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.FACING) == side
                && BeltNetwork.of(this.level).segment(pos) != null;
    }

    private int[] bufferContents() {
        int[] out = new int[this.buffer.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = this.buffer.dequeueInt();
        }
        for (int cargo : out) {
            this.buffer.enqueue(cargo);
        }
        return out;
    }

    /** 拆掉时缓冲里的东西掉出来。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            for (int cargo : this.bufferContents()) {
                for (ItemStack stack : BeltNetwork.stacks(cargo)) {
                    Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
            this.buffer.clear();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.buffer.clear();
        List<Item> items = input.read("Buffer", ITEMS_CODEC).orElse(List.of());
        // 叠了几个、增产点数（ADR-0022）；旧档没有
        int[] stacks = input.getIntArray("BufferStacks").orElse(new int[0]);
        for (int i = 0; i < items.size() && i < BUFFER; i++) {
            this.buffer.enqueue(Cargo.withItem(i < stacks.length ? stacks[i] : 0, BeltNetwork.id(items.get(i))));
        }
        this.next = input.getIntOr("Next", 0);
        int priority = input.getIntOr("Priority", -1);
        this.priority = priority >= 0 && priority < SIDES.length ? SIDES[priority] : null;
        this.filter = input.read("Filter", BuiltInRegistries.ITEM.byNameCodec()).filter(item -> item != Items.AIR).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        int[] cargo = this.bufferContents();
        if (cargo.length > 0) {
            List<Item> items = new ArrayList<>(cargo.length);
            int[] stacks = new int[cargo.length];
            for (int i = 0; i < cargo.length; i++) {
                items.add(BeltNetwork.item(cargo[i]));
                stacks[i] = Cargo.withItem(cargo[i], 0);
            }
            output.store("Buffer", ITEMS_CODEC, items);
            output.putIntArray("BufferStacks", stacks);
        }
        output.putInt("Next", this.next);
        output.putInt("Priority", this.priority == null ? -1 : List.of(SIDES).indexOf(this.priority));
        if (this.filter != null) {
            output.store("Filter", BuiltInRegistries.ITEM.byNameCodec(), this.filter);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
