package io.github.sweetcornna.universe.factory.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.stats.ProductionLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 采矿机（ADR-0011）：开采正下方 5×5、往下 3 层里的矿脉节点，每个节点每 40 秒一个，产物从正面推出去。
 * 一片露头通常有 40 到 75 个节点，满电每分钟 60 到 110 个，够一两台电弧熔炉吃。
 * 范围里有几种矿时只采节点最多的那种（和戴森球计划一样，一台只出一种）。
 *
 * <p>高级采矿机（ADR-0015）也是这个类：范围 7×7，每个节点的速度翻倍，2.94 MW。
 */
public class MiningMachineBlockEntity extends MachineBlockEntity {
    /** 戴森球计划的 420 kW。 */
    public static final long POWER = 21;
    /** 每个节点多少刻出一个。 */
    public static final int TICKS_PER_ITEM = 800;
    public static final int RADIUS = 2;
    public static final int DEPTH = 3;
    /** 高级采矿机：戴森球计划的 2.94 MW，范围 7×7，速度两倍。 */
    public static final long ADVANCED_POWER = 147;
    public static final int ADVANCED_RADIUS = 3;
    public static final double ADVANCED_SPEED = 2;
    private static final int RESCAN = 100;
    public static final int OUTPUT = 0;

    public static final int NODES = BASE_DATA;
    public static final int ITEM = BASE_DATA + 1;
    public static final int REMAINING = BASE_DATA + 3;
    /** 范围的半径、速度（百分数），界面算产量用。 */
    public static final int AREA = BASE_DATA + 5;
    public static final int SPEED = BASE_DATA + 6;
    public static final int DATA_COUNT = BASE_DATA + 7;

    private final long power;
    private final int radius;
    private final double speed;

    private final List<BlockPos> nodes = new ArrayList<>();
    private @Nullable Item mined;
    private long remaining;
    private int scanTimer;
    private int cursor;
    private double progress;
    private boolean active;
    private double supplied;

    public MiningMachineBlockEntity(BlockPos pos, BlockState state) {
        this(UniverseBlockEntities.MINING_MACHINE.get(), pos, state, POWER, RADIUS, 1);
    }

    protected MiningMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, long power, int radius, double speed) {
        super(type, pos, state, 1);
        this.power = power;
        this.radius = radius;
        this.speed = speed;
    }

    public static MiningMachineBlockEntity advanced(BlockPos pos, BlockState state) {
        return new MiningMachineBlockEntity(UniverseBlockEntities.ADVANCED_MINING_MACHINE.get(), pos, state, ADVANCED_POWER, ADVANCED_RADIUS,
                ADVANCED_SPEED);
    }

    @Override
    public long demand() {
        return this.active ? this.power : 0;
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        super.settle(satisfaction, load, stored);
        this.supplied = this.active ? satisfaction : 0;
    }

    @Override
    protected boolean tickMachine() {
        double rate = this.supplied;
        this.supplied = 0;
        if (--this.scanTimer <= 0) {
            this.scan();
        }
        this.pushOutput(OUTPUT, this.getBlockState().getValue(MachineBlock.FACING), 4);
        if (this.mined == null || this.nodes.isEmpty() || !this.outputHasRoom(OUTPUT, new ItemStack(this.mined))) {
            this.active = false;
            return false;
        }
        this.active = true;
        if (rate <= 0) {
            return false;
        }
        this.progress += this.nodes.size() * rate * this.effectiveSpeed();
        while (this.progress >= TICKS_PER_ITEM && this.mined != null && !this.nodes.isEmpty()
                && this.outputHasRoom(OUTPUT, new ItemStack(this.mined))) {
            this.progress -= TICKS_PER_ITEM;
            this.mineOne();
        }
        this.setChanged();
        return true;
    }

    private void mineOne() {
        ServerLevel level = (ServerLevel) this.level;
        BlockPos pos = this.nodes.get(this.cursor++ % this.nodes.size());
        VeinNodes.Node node = VeinNodes.node(level.getBlockState(pos));
        if (node == null || node.item() != this.mined) {
            // 节点被人挖掉或换掉了
            this.scan();
            return;
        }
        // 矿脉利用：按概率才扣储量，平均下来每个矿扣 0.94^级数
        if (level.getRandom().nextDouble() < Upgrades.veinConsumption(Research.level(level, Upgrades.VEIN_UTILIZATION))) {
            if (VeinNodes.extract(level, pos, node)) {
                this.nodes.remove(pos);
            }
            this.remaining = Math.max(0, this.remaining - 1);
        }
        ProductionLog.produced(level, node.item(), 1);
        this.addOutput(OUTPUT, new ItemStack(node.item()));
    }

    /** 设备本身的速度乘上矿脉利用的加成。 */
    private double effectiveSpeed() {
        return this.level == null ? this.speed : this.speed * Upgrades.miningSpeed(Research.level(this.level, Upgrades.VEIN_UTILIZATION));
    }

    /** 找出范围里节点最多的那种矿。 */
    private void scan() {
        this.scanTimer = RESCAN;
        this.nodes.clear();
        this.remaining = 0;
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        Map<Item, List<BlockPos>> found = new LinkedHashMap<>();
        Map<BlockPos, VeinNodes.Node> nodeAt = new LinkedHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= DEPTH; dy++) {
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                for (int dz = -this.radius; dz <= this.radius; dz++) {
                    cursor.set(this.worldPosition.getX() + dx, this.worldPosition.getY() - dy, this.worldPosition.getZ() + dz);
                    VeinNodes.Node node = VeinNodes.node(level.getBlockState(cursor));
                    if (node != null) {
                        BlockPos pos = cursor.immutable();
                        found.computeIfAbsent(node.item(), item -> new ArrayList<>()).add(pos);
                        nodeAt.put(pos, node);
                    }
                }
            }
        }
        List<BlockPos> best = List.of();
        Item bestItem = null;
        for (Map.Entry<Item, List<BlockPos>> entry : found.entrySet()) {
            if (entry.getValue().size() > best.size()) {
                best = entry.getValue();
                bestItem = entry.getKey();
            }
        }
        this.mined = bestItem;
        this.nodes.addAll(best);
        for (BlockPos pos : best) {
            this.remaining += VeinNodes.remaining(level, pos, nodeAt.get(pos));
        }
    }

    // ---- 物品栏

    @Override
    protected boolean isOutput(int slot) {
        return slot == OUTPUT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    // ---- 界面

    @Override
    protected MachineMenu.Type menuType() {
        return UniverseMenus.MINING_MACHINE;
    }

    @Override
    protected int dataCount() {
        return DATA_COUNT;
    }

    /** 节点数、开采的物品（注册表序号 + 1，没有是 0，拆两格）、剩余储量（拆两格）、范围的边长、速度百分数。 */
    @Override
    protected int dataValue(int index) {
        int item = this.mined == null ? 0 : BuiltInRegistries.ITEM.getId(this.mined) + 1;
        long remaining = Math.min(this.remaining, 0xFFFFFFFFL);
        return switch (index) {
            case NODES -> this.nodes.size();
            case ITEM -> MachineMenu.low(item);
            case ITEM + 1 -> MachineMenu.high(item);
            case REMAINING -> MachineMenu.low(remaining);
            case REMAINING + 1 -> MachineMenu.high(remaining);
            case AREA -> this.radius * 2 + 1;
            case SPEED -> (int) Math.round(this.effectiveSpeed() * 100);
            default -> super.dataValue(index);
        };
    }

    // ---- 存档

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getDoubleOr("Progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("Progress", this.progress);
    }
}
