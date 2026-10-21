package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.blueprint.BlueprintSettings;
import io.github.sweetcornna.universe.core.factory.BeltLine;
import io.github.sweetcornna.universe.core.factory.SorterTier;
import io.github.sweetcornna.universe.core.power.PowerDevice;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * 分拣器（ADR-0011）：从背后那格拿一个、转过去放到前面那格。两头可以是传送带，也可以是任何有物品能力的方块
 * （箱子、储物仓、设备）。只拿前面放得下的东西，所以给设备送料时自然只送配方要的原料；可以设一个过滤物品。
 *
 * <p>一个循环的刻数按档位（{@link SorterTier}，Mk.I 每秒 1.5 次）：前一半抓着东西转到前面，放下后后一半转回来。
 * 电力不足时按满足率变慢。几档共用这个方块实体，档位看方块。
 *
 * <p>从传送带上拿时整格拿走（叠着的几个一起，ADR-0022）；从容器拿时一次拿 {@link SorterTier#stack()} 个。手里的东西
 * 要么全放下，要么不放。
 */
public class SorterBlockEntity extends BlockEntity implements PowerDevice, BlueprintSettings {
    /** 前面的容器放不下时隔几刻再试。设备的原料格留着几次配方的量，晚几刻送到不影响产量。 */
    private static final int BLOCKED_RETRY = 4;
    private static final Codec<List<ItemStack>> HELD_CODEC = ItemStack.CODEC.listOf();

    private @Nullable Item filter;
    /** 手里抓着的东西：一般是一堆，从传送带上拿的点数分不匀时是两堆（{@link BeltNetwork#stacks}）。 */
    private List<ItemStack> held = List.of();
    /** 循环进度（刻）；-1 是停在背后等着抓。 */
    private double progress = -1;
    private double supplied;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> sourceCache;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> targetCache;
    private @Nullable Direction cachedFacing;
    /** 客户端：收到同步时的游戏刻，用来插值。 */
    private long syncedAt;
    private int retryIn;
    // 一次抓取里：前面的容器有没有拒收过，上一个问过的物品和答案（同一种只问一次）
    private boolean blocked;
    private int askedItem = BeltLine.EMPTY;
    private boolean askedFits;

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SORTER.get(), pos, state);
    }

    public @Nullable Item filter() {
        return this.filter;
    }

    @Override
    public @Nullable CompoundTag blueprintSettings() {
        if (this.filter == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Filter", BuiltInRegistries.ITEM.getKey(this.filter).toString());
        return tag;
    }

    @Override
    public void applyBlueprintSettings(CompoundTag settings) {
        Identifier id = Identifier.tryParse(settings.getStringOr("Filter", ""));
        this.setFilter(id == null ? null : BuiltInRegistries.ITEM.getValue(id));
        this.setChanged();
    }

    public void setFilter(@Nullable Item filter) {
        this.filter = filter == Items.AIR ? null : filter;
        this.setChanged();
        this.sync();
    }

    /** 客户端画在手上的样子。 */
    public ItemStack held() {
        return this.held.isEmpty() ? ItemStack.EMPTY : this.held.getFirst();
    }

    public SorterTier tier() {
        return this.getBlockState().getBlock() instanceof SorterBlock sorter ? sorter.tier() : SorterTier.MK1;
    }

    /** 客户端画手臂用：这一帧的循环进度（刻），停着时是 -1。按满电插值，抓着东西时最多转到前面。 */
    public double displayProgress(float partialTicks) {
        if (this.progress < 0 || this.level == null) {
            return -1;
        }
        double shown = this.progress + (this.level.getGameTime() - this.syncedAt) + partialTicks;
        SorterTier tier = this.tier();
        if (!this.held.isEmpty()) {
            return Math.min(shown, tier.halfCycle());
        }
        return shown >= tier.cycle() ? -1 : shown;
    }

    // ---- 电网

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            LevelPower.of(serverLevel).addDevice(this.worldPosition, this);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            LevelPower power = LevelPower.existing(serverLevel);
            if (power != null) {
                power.removeDevice(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public long demand() {
        return this.tier().power();
    }

    @Override
    public void settle(double satisfaction, double load, long stored) {
        this.supplied = satisfaction;
    }

    // ---- 每刻

    void serverTick() {
        double rate = this.supplied;
        this.supplied = 0;
        if (rate <= 0 || !(this.level instanceof ServerLevel)) {
            return;
        }
        if (this.progress < 0) {
            if (this.retryIn > 0) {
                this.retryIn--;
            } else {
                this.tryGrab();
            }
            return;
        }
        SorterTier tier = this.tier();
        if (!this.held.isEmpty()) {
            double next = this.progress + rate;
            if (next < tier.halfCycle()) {
                this.progress = next;
            } else if (this.drop(this.held)) {
                this.held = List.of();
                this.progress = next;
                this.setChanged();
                this.sync();
            } else {
                // 前面放不下，抓着等
                this.progress = tier.halfCycle();
            }
            return;
        }
        this.progress += rate;
        if (this.progress >= tier.cycle()) {
            this.progress = -1;
            if (!this.tryGrab()) {
                this.sync();
            }
        }
    }

    private boolean tryGrab() {
        List<ItemStack> grabbed = this.grab();
        if (grabbed.isEmpty()) {
            if (this.blocked) {
                this.retryIn = BLOCKED_RETRY;
            }
            return false;
        }
        this.held = grabbed;
        this.progress = 0;
        this.setChanged();
        this.sync();
        return true;
    }

    private Direction facing() {
        return this.getBlockState().getValue(SorterBlock.FACING);
    }

    private boolean matches(Item item) {
        return this.filter == null || this.filter == item;
    }

    /** 从背后拿前面放得下的东西。 */
    private List<ItemStack> grab() {
        this.blocked = false;
        this.askedItem = BeltLine.EMPTY;
        Direction facing = this.facing();
        BlockPos from = this.worldPosition.relative(facing.getOpposite());
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(from) != null) {
            int cargo = network.take(from, this::wants);
            return cargo == BeltLine.EMPTY ? List.of() : BeltNetwork.stacks(cargo);
        }
        ResourceHandler<ItemResource> source = this.source(facing);
        if (source == null) {
            return List.of();
        }
        int stack = Math.min(this.tier().stack(), this.tier().stack() > 1 && this.level instanceof ServerLevel serverLevel
                ? Upgrades.beltStack(Research.level(serverLevel, Upgrades.BELT_STACKING)) : 1);
        for (int index = 0; index < source.size(); index++) {
            ItemResource resource = source.getResource(index);
            if (resource.isEmpty() || !this.matches(resource.getItem())) {
                continue;
            }
            // 集装分拣器一次拿一叠；前面放不下一整叠就少拿几个
            int n = (int) Math.min(stack, source.getAmountAsLong(index));
            while (n > 0 && !this.canDrop(List.of(resource.toStack(n)))) {
                n--;
            }
            if (n <= 0) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = source.extract(index, resource, n, transaction);
                if (taken > 0) {
                    transaction.commit();
                    return List.of(resource.toStack(taken));
                }
            }
        }
        return List.of();
    }

    /** 传送带上的这格货物要不要抓。 */
    private boolean wants(int cargo) {
        if (cargo != this.askedItem) {
            this.askedItem = cargo;
            this.askedFits = this.matches(BeltNetwork.item(cargo)) && this.canDrop(BeltNetwork.stacks(cargo));
        }
        return this.askedFits;
    }

    /** 前面放不放得下这些（不真的放）。 */
    private boolean canDrop(List<ItemStack> stacks) {
        Direction facing = this.facing();
        BlockPos to = this.worldPosition.relative(facing);
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(to) != null) {
            return BeltNetwork.cargo(stacks) != BeltLine.EMPTY && network.canInsertAt(to, BeltNetwork.HALF);
        }
        ResourceHandler<ItemResource> target = this.target(facing);
        if (target == null) {
            this.blocked = true;
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            boolean fits = insertAll(target, stacks, transaction);
            this.blocked |= !fits;
            return fits;
        }
    }

    private boolean drop(List<ItemStack> stacks) {
        Direction facing = this.facing();
        BlockPos to = this.worldPosition.relative(facing);
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(to) != null) {
            int cargo = BeltNetwork.cargo(stacks);
            return cargo != BeltLine.EMPTY && network.insertAt(to, BeltNetwork.HALF, cargo);
        }
        ResourceHandler<ItemResource> target = this.target(facing);
        if (target == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (!insertAll(target, stacks, transaction)) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** 在事务里把这几堆全放进去；有一堆放不下就返回 false，调用的一方不提交。 */
    private static boolean insertAll(ResourceHandler<ItemResource> target, List<ItemStack> stacks, Transaction transaction) {
        for (ItemStack stack : stacks) {
            if (ResourceHandlerUtil.insertStacking(target, ItemResource.of(stack), stack.getCount(), transaction) != stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private @Nullable ResourceHandler<ItemResource> source(Direction facing) {
        this.refreshCaches(facing);
        return this.sourceCache == null ? null : this.sourceCache.getCapability();
    }

    private @Nullable ResourceHandler<ItemResource> target(Direction facing) {
        this.refreshCaches(facing);
        return this.targetCache == null ? null : this.targetCache.getCapability();
    }

    private void refreshCaches(Direction facing) {
        if (this.cachedFacing == facing || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.cachedFacing = facing;
        this.sourceCache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, serverLevel, this.worldPosition.relative(facing.getOpposite()), facing);
        this.targetCache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, serverLevel, this.worldPosition.relative(facing), facing.getOpposite());
    }

    /** 拆掉时手里抓着的东西掉在地上。 */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.held.isEmpty()) {
            for (ItemStack stack : this.held) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            }
            this.held = List.of();
        }
    }

    // ---- 同步和存档

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.filter = input.read("Filter", BuiltInRegistries.ITEM.byNameCodec()).filter(item -> item != Items.AIR).orElse(null);
        List<ItemStack> held = new ArrayList<>(input.read("HeldItems", HELD_CODEC).orElse(List.of()));
        // 旧档只抓着一个
        input.read("Held", ItemStack.OPTIONAL_CODEC).filter(stack -> !stack.isEmpty()).ifPresent(held::add);
        this.held = List.copyOf(held);
        this.progress = input.getDoubleOr("Progress", -1);
        if (this.level != null) {
            this.syncedAt = this.level.getGameTime();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        Optional.ofNullable(this.filter).ifPresent(item -> output.store("Filter", BuiltInRegistries.ITEM.byNameCodec(), item));
        if (!this.held.isEmpty()) {
            output.store("HeldItems", HELD_CODEC, this.held);
        }
        output.putDouble("Progress", this.progress);
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
