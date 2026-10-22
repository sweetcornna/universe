package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.ship.ShipPart;
import io.github.sweetcornna.universe.core.ship.ShipRating;
import io.github.sweetcornna.universe.core.ship.ShipShape;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 飞船上的全部方块，坐标相对原点格（见 {@link ShipShape}），方向是组装那一刻的世界方向。
 *
 * <p>方块实体（箱子、燃料舱……）的数据原样带着走，停船时放回去。客户端拿到方块状态，方块实体只拿到原版同步给
 * 客户端的那部分（告示牌的字、旗帜的图案），箱子里有什么不会发过去。
 *
 * <p>评级（ADR-0024）按部件和容器的格数算，技术读驾驶台方块实体里的摘要（{@link HelmBlockEntity#TECH}），客户端也算得出来。
 */
public final class ShipStructure {
    /**
     * 一个方块。{@code blockEntity} 在服务端是带 id 的完整方块实体数据；在客户端是
     * {@link BlockEntity#getUpdateTag} 给出的、只够画出来的那部分。
     */
    public record ShipBlock(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntity) {
    }

    /** 每种方块的容器有几格（按方块缓存，免得每次组装都新建方块实体）。 */
    private static final Map<Block, Integer> CONTAINER_SLOTS = new ConcurrentHashMap<>();

    private final List<ShipBlock> blocks;
    private final ShipShape shape;
    private final Map<ShipPart, Integer> counts;
    private final int cargoSlots;
    private final ShipRating rating;
    private final ShipStats baseStats;
    private final ShipTech tech;
    private final ShipStats stats;
    private final int helm;
    private final int[] seats;
    private final int[] thrusters;
    private final int[] engines;
    private final int[] cannons;
    private final int[] beams;

    public ShipStructure(List<ShipBlock> blocks) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("empty ship");
        }
        this.blocks = List.copyOf(blocks);
        int n = blocks.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] zs = new int[n];
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        int helmIndex = -1;
        List<Integer> seatList = new ArrayList<>();
        List<Integer> thrusterList = new ArrayList<>();
        List<Integer> engineList = new ArrayList<>();
        List<Integer> cannonList = new ArrayList<>();
        List<Integer> beamList = new ArrayList<>();
        int cargo = 0;
        for (int i = 0; i < n; i++) {
            ShipBlock block = blocks.get(i);
            xs[i] = block.x();
            ys[i] = block.y();
            zs[i] = block.z();
            ShipPart part = ShipParts.of(block.state());
            counts.merge(part, 1, Integer::sum);
            cargo += containerSlots(block.state());
            switch (part) {
                case HELM -> {
                    if (helmIndex < 0) {
                        helmIndex = i;
                    }
                }
                case SEAT -> seatList.add(i);
                case THRUSTER, THRUSTER_MK2 -> thrusterList.add(i);
                case IMPULSE_ENGINE, IMPULSE_ENGINE_MK2 -> engineList.add(i);
                case PHOTON_CANNON -> cannonList.add(i);
                case MINING_BEAM -> beamList.add(i);
                default -> {
                }
            }
        }
        this.shape = new ShipShape(xs, ys, zs);
        this.counts = Collections.unmodifiableMap(counts);
        this.cargoSlots = cargo;
        this.rating = ShipRating.of(counts, cargo);
        this.baseStats = ShipStats.of(counts);
        // 读到损坏的数据时也别崩：没有驾驶台就把第一个方块当驾驶位
        this.helm = Math.max(0, helmIndex);
        this.tech = helmIndex < 0 ? ShipTech.NONE
                : ShipTech.of(HelmBlockEntity.readSummary(blocks.get(helmIndex).blockEntity()), this.rating.slots());
        this.stats = this.baseStats.tuned(this.rating, this.tech.bonuses(), this.tech.drive());
        this.seats = seatList.stream().mapToInt(Integer::intValue).toArray();
        this.thrusters = thrusterList.stream().mapToInt(Integer::intValue).toArray();
        this.engines = engineList.stream().mapToInt(Integer::intValue).toArray();
        this.cannons = cannonList.stream().mapToInt(Integer::intValue).toArray();
        this.beams = beamList.stream().mapToInt(Integer::intValue).toArray();
    }

    public List<ShipBlock> blocks() {
        return this.blocks;
    }

    public ShipBlock block(int i) {
        return this.blocks.get(i);
    }

    public ShipShape shape() {
        return this.shape;
    }

    /** 加上评级的专长和技术以后的性能。 */
    public ShipStats stats() {
        return this.stats;
    }

    /** 只按部件算的性能（飞船面板对比用）。 */
    public ShipStats baseStats() {
        return this.baseStats;
    }

    public ShipRating rating() {
        return this.rating;
    }

    public ShipTech tech() {
        return this.tech;
    }

    /** 各种部件的个数。 */
    public Map<ShipPart, Integer> counts() {
        return this.counts;
    }

    /** 船上容器一共多少格。 */
    public int cargoSlots() {
        return this.cargoSlots;
    }

    /** 驾驶台换了数据（飞行中改了技术）：别的方块不变。 */
    public ShipStructure withHelmData(CompoundTag data) {
        List<ShipBlock> copy = new ArrayList<>(this.blocks);
        ShipBlock helm = copy.get(this.helm);
        copy.set(this.helm, new ShipBlock(helm.x(), helm.y(), helm.z(), helm.state(), data));
        return new ShipStructure(copy);
    }

    /** 这种方块的容器有几格；不是容器是 0。 */
    public static int containerSlots(BlockState state) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return 0;
        }
        return CONTAINER_SLOTS.computeIfAbsent(state.getBlock(), block -> {
            try {
                return entityBlock.newBlockEntity(BlockPos.ZERO, state) instanceof Container container ? container.getContainerSize() : 0;
            } catch (RuntimeException e) {
                // 别的模组的方块实体在没有世界时建不出来：当作不是容器
                return 0;
            }
        });
    }

    public ShipBlock helm() {
        return this.blocks.get(this.helm);
    }

    /** 驾驶台在组装时的朝向（船头）。 */
    public Direction helmFacing() {
        BlockState state = this.helm().state();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    public int seatCount() {
        return this.seats.length;
    }

    public ShipBlock seat(int i) {
        return this.blocks.get(this.seats[i]);
    }

    public int[] thrusters() {
        return this.thrusters;
    }

    public int[] engines() {
        return this.engines;
    }

    /** 光子炮（ADR-0017）。 */
    public int[] cannons() {
        return this.cannons;
    }

    /** 采矿光束。 */
    public int[] beams() {
        return this.beams;
    }

    // ---- 存档

    public void save(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("Blocks");
        for (ShipBlock block : this.blocks) {
            ValueOutput child = list.addChild();
            child.putInt("X", block.x());
            child.putInt("Y", block.y());
            child.putInt("Z", block.z());
            child.store("State", BlockState.CODEC, block.state());
            if (block.blockEntity() != null) {
                child.store("Data", CompoundTag.CODEC, block.blockEntity());
            }
        }
    }

    /** 读档；没有方块（数据损坏）时返回 {@code null}。 */
    public static @Nullable ShipStructure load(ValueInput input) {
        List<ShipBlock> blocks = new ArrayList<>();
        for (ValueInput child : input.childrenListOrEmpty("Blocks")) {
            BlockState state = child.read("State", BlockState.CODEC).orElse(null);
            if (state == null || state.isAir()) {
                continue;
            }
            blocks.add(new ShipBlock(child.getIntOr("X", 0), child.getIntOr("Y", 0), child.getIntOr("Z", 0), state,
                    child.read("Data", CompoundTag.CODEC).orElse(null)));
        }
        return blocks.isEmpty() ? null : new ShipStructure(blocks);
    }

    // ---- 网络：生成实体时发给客户端

    /** 写出方块；{@code structure} 为空时写出空表。 */
    public static void write(@Nullable ShipStructure structure, RegistryFriendlyByteBuf buf) {
        List<ShipBlock> blocks = structure == null ? List.of() : structure.blocks;
        buf.writeVarInt(blocks.size());
        for (ShipBlock block : blocks) {
            buf.writeVarInt(block.x());
            buf.writeVarInt(block.y());
            buf.writeVarInt(block.z());
            buf.writeVarInt(Block.getId(block.state()));
            buf.writeNbt(clientData(block, buf.registryAccess()));
        }
    }

    /** 方块实体里客户端该知道的部分（和原版区块同步发的一样）；没有方块实体时为空。 */
    private static @Nullable CompoundTag clientData(ShipBlock block, HolderLookup.Provider registries) {
        if (block.blockEntity() == null) {
            return null;
        }
        BlockEntity entity = BlockEntity.loadStatic(new BlockPos(block.x(), block.y(), block.z()), block.state(), block.blockEntity(), registries);
        return entity == null ? null : entity.getUpdateTag(registries);
    }

    public static @Nullable ShipStructure read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ShipBlock> blocks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            int z = buf.readVarInt();
            BlockState state = Block.stateById(buf.readVarInt());
            blocks.add(new ShipBlock(x, y, z, state, buf.readNbt()));
        }
        return blocks.isEmpty() ? null : new ShipStructure(blocks);
    }
}
