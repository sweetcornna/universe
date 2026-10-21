package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.power.PowerDevice;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 串在传送带中间的一格（ADR-0022）：喷涂机、自动集装机、流速监测器。背后开过来的传送带把货物送进来
 * （{@link BeltReceiver}），处理以后放到正前方开出去的传送带上。缓冲几格，每刻最多放两格，Mk.III 传送带也不堵。
 *
 * <p>要电的子类按满足率干活；没电时货物照样流过去，只是不处理。
 */
public abstract class BeltPassBlockEntity extends BlockEntity implements BeltReceiver, PowerDevice {
    protected static final int BUFFER = 4;
    private static final int PER_TICK = 2;
    private static final Codec<List<Item>> ITEMS_CODEC = BuiltInRegistries.ITEM.byNameCodec().listOf();

    /** 处理好、等着放出去的货物。 */
    protected final IntArrayFIFOQueue output = new IntArrayFIFOQueue(BUFFER);
    /** 这一刻电网给的满足率。 */
    protected double supplied;
    /** 上一刻有没有电：传送带在方块实体之前推进，交货时用它判断。 */
    protected boolean lastPowered;

    protected BeltPassBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction facing() {
        return this.getBlockState().getValue(BeltPassBlock.FACING);
    }

    // ---- 登记

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            BeltNetwork.of(this.level).outputChanged(this.worldPosition);
            if (this.level instanceof ServerLevel serverLevel && this.power() > 0) {
                LevelPower.of(serverLevel).addDevice(this.worldPosition, this);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            if (network != null) {
                network.outputChanged(this.worldPosition);
            }
            if (this.level instanceof ServerLevel serverLevel && this.power() > 0) {
                LevelPower power = LevelPower.existing(serverLevel);
                if (power != null) {
                    power.removeDevice(this.worldPosition);
                }
            }
        }
        super.setRemoved();
    }

    // ---- 电

    /** 工作时的功率（焦耳/刻）；0 是不要电。 */
    protected long power() {
        return 0;
    }

    /** 这一刻要不要电。 */
    protected boolean wantsPower() {
        return true;
    }

    @Override
    public long demand() {
        return this.wantsPower() ? this.power() : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        this.supplied = this.wantsPower() ? satisfaction : 0;
    }

    /** 这一刻有没有电干活；不要电的设备总是有。 */
    protected boolean powered() {
        return this.power() <= 0 || this.supplied > 0;
    }

    // ---- 货物

    @Override
    public boolean acceptFromBelt(int cargo) {
        if (this.output.size() >= BUFFER) {
            return false;
        }
        this.output.enqueue(cargo);
        this.setChanged();
        return true;
    }

    /**
     * 放出去之前怎么处理；默认原样放。在方块实体自己的这一刻里调（电网已经结算过）。前面堵住时同一格货物下一刻会再处理一次，
     * 所以处理要做到再来一次也不变（喷过的不再喷）。
     */
    protected int process(int cargo) {
        return cargo;
    }

    /** 一格货物放上前面的传送带以后。 */
    protected void passed(int cargo) {
    }

    void serverTick() {
        this.lastPowered = this.powered();
        this.tick();
        this.pass();
        // 放完货再清：process 要看这一刻有没有电
        this.supplied = 0;
    }

    private void pass() {
        if (this.output.isEmpty() || this.level == null) {
            return;
        }
        BlockPos front = this.worldPosition.relative(this.facing());
        if (!this.outgoing(front)) {
            return;
        }
        BeltNetwork network = BeltNetwork.of(this.level);
        for (int moved = 0; moved < PER_TICK && !this.output.isEmpty(); moved++) {
            int cargo = this.process(this.output.dequeueInt());
            if (!network.insertAt(front, 0, cargo)) {
                this.output.enqueueFirst(cargo);
                break;
            }
            this.passed(cargo);
            this.setChanged();
        }
    }

    /** 每刻在放货之前调一次（集装机数等了多久）。 */
    protected void tick() {
    }

    /** 前面是不是一条朝前开的传送带。 */
    private boolean outgoing(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        return state.getBlock() instanceof BeltBlock && state.getValue(BeltBlock.FACING) == this.facing()
                && BeltNetwork.of(this.level).segment(pos) != null;
    }

    // ---- 拆掉和存档

    /** 拆掉时掉出来的东西：缓冲里的货物，子类再加自己存的。 */
    protected List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (int cargo : queue(this.output)) {
            out.addAll(BeltNetwork.stacks(cargo));
        }
        return out;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack stack : this.contents()) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
            this.output.clear();
        }
    }

    /** 队列里的货物（不改动队列）。 */
    protected static int[] queue(IntArrayFIFOQueue queue) {
        int[] out = new int[queue.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = queue.dequeueInt();
        }
        for (int cargo : out) {
            queue.enqueue(cargo);
        }
        return out;
    }

    /** 存几格货物：物品名一个列表，个数和点数另一个整数数组（{@link Cargo#withItem}）。 */
    protected static void saveCargo(ValueOutput output, String key, int[] cargo) {
        if (cargo.length == 0) {
            return;
        }
        List<Item> items = new ArrayList<>(cargo.length);
        int[] stacks = new int[cargo.length];
        for (int i = 0; i < cargo.length; i++) {
            items.add(BeltNetwork.item(cargo[i]));
            stacks[i] = Cargo.withItem(cargo[i], 0);
        }
        output.store(key, ITEMS_CODEC, items);
        output.putIntArray(key + "Stacks", stacks);
    }

    protected static int[] loadCargo(ValueInput input, String key) {
        List<Item> items = input.read(key, ITEMS_CODEC).orElse(List.of());
        int[] stacks = input.getIntArray(key + "Stacks").orElse(new int[0]);
        int[] out = new int[items.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = Cargo.withItem(i < stacks.length ? stacks[i] : 0, BeltNetwork.id(items.get(i)));
        }
        return out;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.output.clear();
        for (int cargo : loadCargo(input, "Output")) {
            if (this.output.size() < BUFFER) {
                this.output.enqueue(cargo);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveCargo(output, "Output", queue(this.output));
    }
}
