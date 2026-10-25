package io.github.sweetcornna.universe.darkfog.facility;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.darkfog.FacilityData;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 残影（ADR-0028 §四）：记着打掉之前的方块状态（含朝向）。物流站这类把东西存在物品上的设备，存的东西也留在这里，
 * 重建时还回去。
 */
public class FacilityGhostBlockEntity extends BlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();
    /** 物品上带走的组件（物流站的账本、带着东西走的设备的格子）；没有就是空的。 */
    private DataComponentPatch contents = DataComponentPatch.EMPTY;
    /** 物流站、采集器、配送器原来的主人（ADR-0033 §三）：谁重建的都还是他的。没有主人、旧档里的残影是空的。 */
    private @Nullable UUID owner;

    public FacilityGhostBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FACILITY_GHOST.get(), pos, state);
    }

    public BlockState original() {
        return this.original;
    }

    public DataComponentPatch contents() {
        return this.contents;
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    /** 两格高的设备：上半截也画、也挡着。 */
    public boolean tall() {
        return this.original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    void set(BlockState original, DataComponentPatch contents, @Nullable UUID owner) {
        this.original = original;
        this.contents = contents;
        this.owner = owner;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            FacilityData.get(serverLevel).addGhost(this.worldPosition.asLong());
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            FacilityData.get(serverLevel).removeGhost(this.worldPosition.asLong());
        }
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.original = input.read("Original", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        this.contents = input.read("Contents", DataComponentPatch.CODEC).orElse(DataComponentPatch.EMPTY);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Original", BlockState.CODEC, this.original);
        if (!this.contents.isEmpty()) {
            output.store("Contents", DataComponentPatch.CODEC, this.contents);
        }
        if (this.owner != null) {
            output.store("Owner", UUIDUtil.CODEC, this.owner);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 客户端只要原来的方块（画虚影），存的东西不发。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        BlockState.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), this.original)
                .ifSuccess(encoded -> tag.put("Original", encoded));
        return tag;
    }
}
