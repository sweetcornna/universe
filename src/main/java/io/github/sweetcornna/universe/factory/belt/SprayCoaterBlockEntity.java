package io.github.sweetcornna.universe.factory.belt;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.Proliferators;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 喷涂机（ADR-0022）：串在传送带中间，经过的货物补到所存增产剂那一档的点数，每喷一个物品用一次。增产剂从侧面用分拣器、
 * 漏斗送进来，或者拿着右键塞进去；同一时间只存一档，用完了才换。
 */
public class SprayCoaterBlockEntity extends BeltPassBlockEntity {
    /** 戴森球计划的 90 kW（4.5 J/刻），向上取整。 */
    public static final long POWER = 5;
    /** 最多存几个增产剂。 */
    public static final int CAPACITY = 20;

    private final Stored stored = new Stored();
    /** 拆开的那个增产剂还剩几次。 */
    private int charges;
    private Proliferation.@Nullable Tier chargeTier;
    /** 一共喷了多少个物品（Jade 显示）。 */
    private long sprayed;

    public SprayCoaterBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SPRAY_COATER.get(), pos, state);
    }

    public ItemStacksResourceHandler handler() {
        return this.stored;
    }

    @Override
    protected long power() {
        return POWER;
    }

    /**
     * 装着增产剂就一直接电：电网在传送带送货之前结算，只在缓冲里有货时才要电的话，闲了一阵以后来的货会没电、不喷就过去。
     */
    @Override
    protected boolean wantsPower() {
        return this.charges > 0 || !this.stored.getResource(0).isEmpty();
    }

    /** 现在喷的是哪一档：剩的次数那一档，没有就是存着的那一档。 */
    public Proliferation.@Nullable Tier tier() {
        if (this.charges > 0 && this.chargeTier != null) {
            return this.chargeTier;
        }
        return Proliferators.tier(this.stored.getResource(0).getItem());
    }

    /** 还能喷几次：剩的加上存着的。 */
    public int remaining() {
        Proliferation.Tier tier = Proliferators.tier(this.stored.getResource(0).getItem());
        return this.charges + (tier == null ? 0 : (int) this.stored.getAmountAsLong(0) * tier.sprays());
    }

    public long sprayed() {
        return this.sprayed;
    }

    @Override
    protected int process(int cargo) {
        if (!this.powered()) {
            return cargo;
        }
        int count = Cargo.count(cargo);
        // 剩的次数不够这一格时拆一个新的；换档要等旧的用完
        while (this.charges < count) {
            ItemResource resource = this.stored.getResource(0);
            Proliferation.Tier tier = Proliferators.tier(resource.getItem());
            if (tier == null || this.charges > 0 && tier != this.chargeTier) {
                break;
            }
            this.stored.set(0, resource, (int) this.stored.getAmountAsLong(0) - 1);
            this.charges += tier.sprays();
            this.chargeTier = tier;
        }
        if (this.charges <= 0 || this.chargeTier == null) {
            return cargo;
        }
        int[] used = {0};
        int sprayed = Cargo.spray(cargo, this.chargeTier, this.charges, used);
        if (used[0] > 0) {
            this.charges -= used[0];
            this.sprayed += used[0];
            this.setChanged();
            this.sync();
        }
        return sprayed;
    }

    /** 玩家拿着增产剂右键：塞进去，返回塞了几个。 */
    public int insert(ItemStack stack) {
        int before = (int) this.stored.getAmountAsLong(0);
        if (!this.stored.isValid(0, ItemResource.of(stack))) {
            return 0;
        }
        ItemResource resource = ItemResource.of(stack);
        int added = Math.min(stack.getCount(), CAPACITY - before);
        if (added <= 0 || !this.stored.getResource(0).isEmpty() && !this.stored.getResource(0).equals(resource)) {
            return 0;
        }
        this.stored.set(0, resource, before + added);
        return added;
    }

    @Override
    protected List<ItemStack> contents() {
        List<ItemStack> out = super.contents();
        ItemResource resource = this.stored.getResource(0);
        if (!resource.isEmpty()) {
            out.add(resource.toStack((int) this.stored.getAmountAsLong(0)));
        }
        return out;
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide() && this.level.getGameTime() % 10 == 0) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Stored").ifPresent(this.stored::deserialize);
        this.charges = input.getIntOr("Charges", 0);
        int tier = input.getIntOr("ChargeTier", -1);
        this.chargeTier = tier >= 0 && tier < Proliferation.Tier.values().length ? Proliferation.Tier.values()[tier] : null;
        this.sprayed = input.getLongOr("Sprayed", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.stored.serialize(output.child("Stored"));
        output.putInt("Charges", this.charges);
        output.putInt("ChargeTier", this.chargeTier == null ? -1 : this.chargeTier.ordinal());
        output.putLong("Sprayed", this.sprayed);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    /** 存增产剂的一格：只收增产剂，同一时间只存一种；拿不出来（拆掉时掉出来）。 */
    private final class Stored extends ItemStacksResourceHandler {
        Stored() {
            super(1);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return Proliferators.tier(resource.getItem()) != null && resource.isComponentsPatchEmpty();
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return CAPACITY;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            SprayCoaterBlockEntity.this.setChanged();
        }
    }
}
