package io.github.sweetcornna.universe.fossil;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.sound.FossilSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 骨架展台上的骨架（ADR-0034 §三）：五格，每格只收对应部位的骨头，类群不限。展台自己就是界面里那五格的容器；
 * 骨架同步到客户端，渲染器照着它把骨头拼成立体的，五格都放上就在展台上方写出骨架的名字。
 */
public class SkeletonDisplayBlockEntity extends BlockEntity implements Container {
    private Skeleton skeleton = Skeleton.EMPTY;

    public SkeletonDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.SKELETON_DISPLAY.get(), pos, state);
    }

    public Skeleton skeleton() {
        return this.skeleton;
    }

    /** 整副换掉（摆回一件展品、封装以后清空）。 */
    public void setSkeleton(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.setChanged();
    }

    /** 放上一块骨头：这一格空着才放得上。刚好放满五格时响一声。 */
    public boolean insert(Bone bone) {
        if (this.skeleton.has(bone.part())) {
            return false;
        }
        this.skeleton = this.skeleton.with(bone);
        this.setChanged();
        if (this.skeleton.complete() && this.level != null && !this.level.isClientSide()) {
            this.level.playSound(null, this.worldPosition, FossilSounds.SKELETON_COMPLETE.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        }
        return true;
    }

    /** 封装成一件化石展品：展台清空，返回展品；空的展台返回空。 */
    public ItemStack pack() {
        if (this.skeleton.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack exhibit = FossilExhibitItem.of(this.skeleton);
        this.setSkeleton(Skeleton.EMPTY);
        return exhibit;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ---- 五格的容器

    @Override
    public int getContainerSize() {
        return BonePart.values().length;
    }

    @Override
    public boolean isEmpty() {
        return this.skeleton.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        BonePart part = BonePart.byOrdinal(slot);
        Bone bone = part == null ? null : this.skeleton.bone(part);
        return bone == null ? ItemStack.EMPTY : new ItemStack(UniverseItems.BONES.get(bone).get());
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = this.getItem(slot);
        if (!stack.isEmpty() && count > 0) {
            this.skeleton = this.skeleton.with(BonePart.byOrdinal(slot), null);
            this.setChanged();
        }
        return count > 0 ? stack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = this.getItem(slot);
        if (!stack.isEmpty()) {
            this.skeleton = this.skeleton.with(BonePart.byOrdinal(slot), null);
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        BonePart part = BonePart.byOrdinal(slot);
        if (part == null) {
            return;
        }
        Bone bone = BoneItem.boneOf(stack);
        if (stack.isEmpty() || bone == null || bone.part() != part) {
            this.skeleton = this.skeleton.with(part, null);
            this.setChanged();
        } else if (!this.insert(bone)) {
            this.skeleton = this.skeleton.with(bone);
            this.setChanged();
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        Bone bone = BoneItem.boneOf(stack);
        return bone != null && bone.part().ordinal() == slot;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.setSkeleton(Skeleton.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            for (Bone bone : this.skeleton.bones()) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        new ItemStack(UniverseItems.BONES.get(bone).get()));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.skeleton = input.read("Skeleton", FossilCodecs.SKELETON).orElse(Skeleton.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Skeleton", FossilCodecs.SKELETON, this.skeleton);
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
