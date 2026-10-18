package io.github.sweetcornna.universe.factory.belt;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.BeltLine;
import io.github.sweetcornna.universe.core.power.PowerDevice;
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
 * 分拣器 Mk.I（ADR-0011）：从背后那格拿一个、转过去放到前面那格。两头可以是传送带，也可以是任何有物品能力的方块
 * （箱子、储物仓、设备）。只拿前面放得下的东西，所以给设备送料时自然只送配方要的原料；可以设一个过滤物品。
 *
 * <p>一个循环 40/3 刻（每秒 1.5 次）：前一半抓着东西转到前面，放下后后一半转回来。电力不足时按满足率变慢。
 */
public class SorterBlockEntity extends BlockEntity implements PowerDevice {
    /** 戴森球计划的 18 kW。 */
    public static final long POWER = 1;
    /** 一次来回的刻数（每秒 1.5 次）。 */
    public static final double CYCLE = 40.0 / 3;
    public static final double HALF_CYCLE = CYCLE / 2;

    private @Nullable Item filter;
    private ItemStack held = ItemStack.EMPTY;
    /** 循环进度（刻）；-1 是停在背后等着抓。 */
    private double progress = -1;
    private double supplied;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> sourceCache;
    private @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> targetCache;
    private @Nullable Direction cachedFacing;
    /** 客户端：收到同步时的游戏刻，用来插值。 */
    private long syncedAt;

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SORTER.get(), pos, state);
    }

    public @Nullable Item filter() {
        return this.filter;
    }

    public void setFilter(@Nullable Item filter) {
        this.filter = filter == Items.AIR ? null : filter;
        this.setChanged();
        this.sync();
    }

    public ItemStack held() {
        return this.held;
    }

    /** 客户端画手臂用：这一帧的循环进度（刻），停着时是 -1。按满电插值，抓着东西时最多转到前面。 */
    public double displayProgress(float partialTicks) {
        if (this.progress < 0 || this.level == null) {
            return -1;
        }
        double shown = this.progress + (this.level.getGameTime() - this.syncedAt) + partialTicks;
        if (!this.held.isEmpty()) {
            return Math.min(shown, HALF_CYCLE);
        }
        return shown >= CYCLE ? -1 : shown;
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
        return POWER;
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
            this.tryGrab();
            return;
        }
        if (!this.held.isEmpty()) {
            double next = this.progress + rate;
            if (next < HALF_CYCLE) {
                this.progress = next;
            } else if (this.drop(this.held)) {
                this.held = ItemStack.EMPTY;
                this.progress = next;
                this.setChanged();
                this.sync();
            } else {
                // 前面放不下，抓着等
                this.progress = HALF_CYCLE;
            }
            return;
        }
        this.progress += rate;
        if (this.progress >= CYCLE) {
            this.progress = -1;
            if (!this.tryGrab()) {
                this.sync();
            }
        }
    }

    private boolean tryGrab() {
        ItemStack grabbed = this.grab();
        if (grabbed.isEmpty()) {
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

    /** 从背后拿一个前面放得下的东西。 */
    private ItemStack grab() {
        Direction facing = this.facing();
        BlockPos from = this.worldPosition.relative(facing.getOpposite());
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(from) != null) {
            int id = network.take(from, item -> this.matches(BeltNetwork.item(item)) && this.canDrop(new ItemStack(BeltNetwork.item(item))));
            return id == BeltLine.EMPTY ? ItemStack.EMPTY : new ItemStack(BeltNetwork.item(id));
        }
        ResourceHandler<ItemResource> source = this.source(facing);
        if (source == null) {
            return ItemStack.EMPTY;
        }
        for (int index = 0; index < source.size(); index++) {
            ItemResource resource = source.getResource(index);
            if (resource.isEmpty() || !this.matches(resource.getItem()) || !this.canDrop(resource.toStack(1))) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                if (source.extract(index, resource, 1, transaction) == 1) {
                    transaction.commit();
                    return resource.toStack(1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 前面放不放得下（不真的放）。 */
    private boolean canDrop(ItemStack stack) {
        Direction facing = this.facing();
        BlockPos to = this.worldPosition.relative(facing);
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(to) != null) {
            return BeltNetwork.canCarry(stack) && network.canInsertAt(to, BeltNetwork.HALF);
        }
        ResourceHandler<ItemResource> target = this.target(facing);
        if (target == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return ResourceHandlerUtil.insertStacking(target, ItemResource.of(stack), 1, transaction) == 1;
        }
    }

    private boolean drop(ItemStack stack) {
        Direction facing = this.facing();
        BlockPos to = this.worldPosition.relative(facing);
        BeltNetwork network = BeltNetwork.of(this.level);
        if (network.segment(to) != null) {
            return BeltNetwork.canCarry(stack) && network.insertAt(to, BeltNetwork.HALF, BeltNetwork.id(stack.getItem()));
        }
        ResourceHandler<ItemResource> target = this.target(facing);
        return target != null && ResourceHandlerUtil.insertStacking(target, ItemResource.of(stack), 1, null) == 1;
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
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.held);
            this.held = ItemStack.EMPTY;
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
        this.held = input.read("Held", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
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
            output.store("Held", ItemStack.OPTIONAL_CODEC, this.held);
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
