package io.github.sweetcornna.universe.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.Incubation;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.CreatureEggs.Egg;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 地上的生物卵（ADR-0026 §九）：记着卵和孵了几刻。孵够了就孵出幼崽，归卵上的主人；主人不在线时等他回来，
 * 免得孵出一只没人管的。卵上的颜色同步到客户端，方块按它染色。
 */
public class CreatureEggBlockEntity extends BlockEntity {
    /** 孵不出来（被堵住）时，隔多久再试。 */
    private static final int RETRY_TICKS = 100;

    private @Nullable Egg egg;
    private int ticks;
    private int retry;

    public CreatureEggBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.CREATURE_EGG.get(), pos, state);
    }

    /** 卵；没有记物种的空卵为 null，孵不出来。 */
    public @Nullable Egg egg() {
        return this.egg;
    }

    public int ticks() {
        return this.ticks;
    }

    /** 颜色，客户端染色用。 */
    public int tint() {
        return this.egg == null ? CreatureTraits.NO_TINT : this.egg.traits().tint();
    }

    /** 还没有主人的卵记到放下它的人名下。 */
    public void claim(UUID owner) {
        if (this.egg != null && this.egg.owner().isEmpty()) {
            this.egg = this.egg.withOwner(owner);
            this.setChanged();
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, CreatureEggBlockEntity entity) {
        if (entity.egg == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Incubation.hatched(entity.ticks)) {
            entity.ticks++;
            // 只是孵化的刻数，物品栏、比较器都不相干，标记存盘就够了
            level.blockEntityChanged(pos);
            int stage = Incubation.stage(entity.ticks);
            if (state.getValue(CreatureEggBlock.HATCH) != stage) {
                level.setBlock(pos, state.setValue(CreatureEggBlock.HATCH, stage), Block.UPDATE_CLIENTS);
                level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            }
            return;
        }
        if (entity.retry > 0) {
            entity.retry--;
            return;
        }
        entity.hatch(serverLevel, pos, state);
    }

    /** 孵出来：先拿掉方块（幼崽不和蛋壳挤在一起），孵不出来再放回去。 */
    private void hatch(ServerLevel level, BlockPos pos, BlockState state) {
        Egg egg = this.egg;
        if (egg == null) {
            return;
        }
        int ticks = this.ticks;
        level.removeBlock(pos, false);
        AlienCreature baby = AlienCreature.hatch(level, Vec3.atBottomCenterOf(pos), egg.home(), egg.species(), egg.traits(), egg.owner().orElse(null));
        if (baby == null) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CreatureEggBlockEntity restored) {
                restored.egg = egg;
                restored.ticks = ticks;
                restored.retry = RETRY_TICKS;
                restored.setChanged();
            }
            return;
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        // 主人不在线也照样孵，幼崽记在主人名下
        ServerPlayer owner = egg.owner().map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (owner != null) {
            owner.sendOverlayMessage(Component.translatable("message.universe.creature_egg.hatched", egg.speciesName()));
        }
    }

    // ---- 物品

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.egg = components.get(UniverseDataComponents.CREATURE_EGG.get());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.egg != null) {
            components.set(UniverseDataComponents.CREATURE_EGG.get(), this.egg);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Egg");
    }

    // ---- 存档和同步

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.egg = input.read("Egg", Egg.CODEC).orElse(null);
        this.ticks = input.getIntOr("Ticks", 0);
        // 客户端收到卵的颜色时方块可能已经画过了，重画一次
        if (this.level != null && this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Egg", Egg.CODEC, this.egg);
        output.putInt("Ticks", this.ticks);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    /** 给 GameTest 用：直接孵到只差一刻。 */
    public void skipToHatch() {
        this.ticks = Incubation.HATCH_TICKS - 1;
        this.setChanged();
    }
}
