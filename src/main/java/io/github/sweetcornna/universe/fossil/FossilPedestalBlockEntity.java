package io.github.sweetcornna.universe.fossil;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 化石底座上放着的那块骨头（ADR-0034 §三），同步到客户端，渲染器画成立体的。 */
public class FossilPedestalBlockEntity extends BlockEntity {
    private @Nullable Bone bone;

    public FossilPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.FOSSIL_PEDESTAL.get(), pos, state);
    }

    public @Nullable Bone bone() {
        return this.bone;
    }

    /** 放上一块骨头（{@code null} 拿走），存档、同步。 */
    public void setBone(@Nullable Bone bone) {
        this.bone = bone;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && this.bone != null) {
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    new ItemStack(UniverseItems.BONES.get(this.bone).get()));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BoneFamily family = BoneFamily.byOrdinal(input.getIntOr("Family", -1));
        BonePart part = BonePart.byOrdinal(input.getIntOr("Part", -1));
        this.bone = family != null && part != null ? new Bone(family, part) : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.bone != null) {
            output.putInt("Family", this.bone.family().ordinal());
            output.putInt("Part", this.bone.part().ordinal());
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
