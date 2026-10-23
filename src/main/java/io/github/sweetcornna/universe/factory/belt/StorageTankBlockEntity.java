package io.github.sweetcornna.universe.factory.belt;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.FluidUnits;
import io.github.sweetcornna.universe.factory.FluidItems;
import io.github.sweetcornna.universe.factory.fluid.FluidBridge;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 储液罐（ADR-0015）：装一种流体，最多 {@link #CAPACITY} 个。传送带开过来直接灌进去（{@link BeltReceiver}），分拣器、漏斗
 * 走物品能力存取；叠起来的罐子会把流体往下面那个罐子里放，和戴森球计划一样。拆掉时流体存在掉落的物品里。
 *
 * <p>别的模组的管道走流体能力（ADR-0030 §三），一个物品算 1000 mB；灌进来不满一个的零头记在 {@link #extra} 里，
 * 存档里有，掉落的物品里没有（拆掉时丢掉不到一个物品的零头）。
 */
public class StorageTankBlockEntity extends BlockEntity implements BeltReceiver {
    public static final int CAPACITY = 10000;
    /** 每刻往下面的罐子流多少。 */
    private static final int FLOW = 100;
    /** 内容变了以后，最快隔几刻同步一次客户端（画液面用）。 */
    private static final int SYNC_INTERVAL = 5;

    /** 罐子里的东西：哪种流体、多少个。 */
    public record Contents(Item fluid, int amount) {
        public static final Codec<Contents> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("fluid").forGetter(Contents::fluid),
                ExtraCodecs.intRange(1, CAPACITY).fieldOf("amount").forGetter(Contents::amount)
        ).apply(i, Contents::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Contents::fluid,
                ByteBufCodecs.VAR_INT, Contents::amount,
                Contents::new);
    }

    private @Nullable Item fluid;
    private int amount;
    /** 流体能力灌进来、不满一个物品的毫桶（0–999），算在 {@link #amount} 之外。 */
    private int extra;
    private final Handler handler = new Handler();
    private final FluidHandler fluidHandler = new FluidHandler();
    private final Journal journal = new Journal();
    private boolean syncPending;
    private long syncedAt;

    public StorageTankBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.STORAGE_TANK.get(), pos, state);
    }

    public @Nullable Item fluid() {
        return this.fluid;
    }

    public int amount() {
        return this.amount;
    }

    public ResourceHandler<ItemResource> handler() {
        return this.handler;
    }

    /** 对外的流体能力（ADR-0030 §三）。 */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluidHandler;
    }

    /** 不满一个物品的零头（毫桶）。 */
    public int extra() {
        return this.extra;
    }

    /** 还能装下几个这种东西（不是流体、或者罐里是别的流体就是 0）。 */
    public int room(Item item) {
        if (!FluidItems.isFluid(item) || (this.fluid != null && this.fluid != item)) {
            return 0;
        }
        // 零头占着最后一格
        return CAPACITY - this.amount - (this.extra > 0 ? 1 : 0);
    }

    /** 灌进去最多 {@code count} 个，返回实际灌了几个。 */
    public int fill(Item item, int count) {
        int added = Math.min(count, this.room(item));
        if (added > 0) {
            this.fluid = item;
            this.amount += added;
            this.changed();
        }
        return added;
    }

    /** 放出最多 {@code count} 个，返回实际放了几个。 */
    public int drain(int count) {
        int taken = Math.min(count, this.amount);
        if (taken > 0) {
            this.amount -= taken;
            if (this.amount == 0 && this.extra == 0) {
                this.fluid = null;
            }
            this.changed();
        }
        return taken;
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
    public boolean acceptFromBelt(int cargo) {
        // 储液罐不记增产点数
        Item item = BeltNetwork.item(cargo);
        int count = Cargo.count(cargo);
        return this.room(item) >= count && this.fill(item, count) == count;
    }

    void serverTick() {
        if (this.level == null) {
            return;
        }
        if (this.fluid != null && this.level.getBlockEntity(this.worldPosition.below()) instanceof StorageTankBlockEntity below) {
            Item flowing = this.fluid;
            int moved = below.fill(flowing, Math.min(FLOW, this.amount));
            if (moved > 0) {
                this.drain(moved);
            }
        }
        if (this.syncPending && this.level.getGameTime() - this.syncedAt >= SYNC_INTERVAL) {
            this.syncPending = false;
            this.syncedAt = this.level.getGameTime();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 满了多少（0–1），客户端画液面和比较器都用它。 */
    public float fillRatio() {
        return (float) FluidUnits.total(this.amount, this.extra) / FluidUnits.total(CAPACITY, 0);
    }

    private void changed() {
        this.setChanged();
        this.syncPending = true;
    }

    private void set(@Nullable Item fluid, int amount, int extra) {
        this.amount = fluid == null ? 0 : Math.max(0, Math.min(CAPACITY, amount));
        this.extra = fluid == null || this.amount >= CAPACITY ? 0 : Math.max(0, Math.min(FluidUnits.BUCKET - 1, extra));
        this.fluid = this.amount > 0 || this.extra > 0 ? fluid : null;
    }

    private void set(@Nullable Item fluid, int amount) {
        this.set(fluid, amount, 0);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Contents contents = input.read("Contents", Contents.CODEC).orElse(null);
        // 只剩零头时物品单独存（Contents 至少要一个）
        Item fluid = contents != null ? contents.fluid() : input.read("ExtraFluid", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        this.set(fluid, contents == null ? 0 : contents.amount(), input.getIntOr("ExtraMb", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.fluid != null && this.amount > 0) {
            output.store("Contents", Contents.CODEC, new Contents(this.fluid, this.amount));
        } else if (this.fluid != null) {
            output.store("ExtraFluid", BuiltInRegistries.ITEM.byNameCodec(), this.fluid);
        }
        if (this.extra > 0) {
            output.putInt("ExtraMb", this.extra);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Contents contents = components.get(UniverseDataComponents.TANK_CONTENTS.get());
        this.set(contents == null ? null : contents.fluid(), contents == null ? 0 : contents.amount());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // 只剩零头时物品上不记（Contents 至少一个）
        if (this.fluid != null && this.amount > 0) {
            components.set(UniverseDataComponents.TANK_CONTENTS.get(), new Contents(this.fluid, this.amount));
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Contents");
        output.discard("ExtraFluid");
        output.discard("ExtraMb");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    /** 对外的物品能力：一格，只收流体，同一时间只有一种。 */
    private final class Handler implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            Item current = StorageTankBlockEntity.this.fluid;
            return current == null ? ItemResource.EMPTY : ItemResource.of(current);
        }

        @Override
        public long getAmountAsLong(int index) {
            return StorageTankBlockEntity.this.amount;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return CAPACITY;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && resource.isComponentsPatchEmpty() && FluidItems.isFluid(resource.getItem());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (amount <= 0 || !this.isValid(index, resource)) {
                return 0;
            }
            int added = Math.min(amount, StorageTankBlockEntity.this.room(resource.getItem()));
            if (added > 0) {
                StorageTankBlockEntity.this.journal.updateSnapshots(transaction);
                StorageTankBlockEntity.this.fluid = resource.getItem();
                StorageTankBlockEntity.this.amount += added;
            }
            return added;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            StorageTankBlockEntity tank = StorageTankBlockEntity.this;
            if (amount <= 0 || tank.fluid == null || !resource.is(tank.fluid) || !resource.isComponentsPatchEmpty()) {
                return 0;
            }
            int taken = Math.min(amount, tank.amount);
            if (taken <= 0) {
                return 0;
            }
            tank.journal.updateSnapshots(transaction);
            tank.amount -= taken;
            if (tank.amount == 0 && tank.extra == 0) {
                tank.fluid = null;
            }
            return taken;
        }
    }

    /**
     * 对外的流体能力（ADR-0030 §三）：一格，按 {@link FluidBridge} 换成流体物品，一个算 1000 mB。不满一个的零头记在
     * {@link #extra} 里，攒够一个就并进去。
     */
    private final class FluidHandler implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            Item current = StorageTankBlockEntity.this.fluid;
            return current == null ? FluidResource.EMPTY : FluidBridge.toFluid(current);
        }

        @Override
        public long getAmountAsLong(int index) {
            StorageTankBlockEntity tank = StorageTankBlockEntity.this;
            return this.getResource(index).isEmpty() ? 0 : FluidUnits.total(tank.amount, tank.extra);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return FluidUnits.total(CAPACITY, 0);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return FluidBridge.toItem(resource).filter(FluidItems::isFluid).isPresent();
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            StorageTankBlockEntity tank = StorageTankBlockEntity.this;
            Item item = FluidBridge.toItem(resource).filter(FluidItems::isFluid).orElse(null);
            if (amount == 0 || item == null || (tank.fluid != null && tank.fluid != item)) {
                return 0;
            }
            long room = FluidUnits.total(CAPACITY, 0) - FluidUnits.total(tank.amount, tank.extra);
            int added = (int) Math.max(0, Math.min(amount, room));
            if (added > 0) {
                tank.journal.updateSnapshots(transaction);
                long total = FluidUnits.total(tank.amount, tank.extra) + added;
                tank.fluid = item;
                tank.amount = (int) FluidUnits.whole(total);
                tank.extra = FluidUnits.extra(total);
            }
            return added;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            StorageTankBlockEntity tank = StorageTankBlockEntity.this;
            if (amount == 0 || tank.fluid == null || !resource.equals(this.getResource(index))) {
                return 0;
            }
            long total = FluidUnits.total(tank.amount, tank.extra);
            int taken = (int) Math.min(amount, total);
            if (taken > 0) {
                tank.journal.updateSnapshots(transaction);
                total -= taken;
                tank.amount = (int) FluidUnits.whole(total);
                tank.extra = FluidUnits.extra(total);
                if (total == 0) {
                    tank.fluid = null;
                }
            }
            return taken;
        }
    }

    private record Snapshot(@Nullable Item fluid, int amount, int extra) {
    }

    private final class Journal extends SnapshotJournal<Snapshot> {
        @Override
        protected Snapshot createSnapshot() {
            return new Snapshot(StorageTankBlockEntity.this.fluid, StorageTankBlockEntity.this.amount, StorageTankBlockEntity.this.extra);
        }

        @Override
        protected void revertToSnapshot(Snapshot snapshot) {
            StorageTankBlockEntity.this.fluid = snapshot.fluid();
            StorageTankBlockEntity.this.amount = snapshot.amount();
            StorageTankBlockEntity.this.extra = snapshot.extra();
        }

        @Override
        protected void onRootCommit(Snapshot originalState) {
            StorageTankBlockEntity.this.changed();
        }
    }
}
