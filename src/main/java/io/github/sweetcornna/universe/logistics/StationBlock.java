package io.github.sweetcornna.universe.logistics;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 行星物流站和星际物流站（ADR-0015）：两格高，顶上是无人机和运输船的停机坪。拿着无人机、运输船或翘曲器右键
 * 直接装进去，空手右键打开界面。
 */
public class StationBlock extends TallMachineBlock {
    public StationBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        boolean drone = stack.is(UniverseItems.LOGISTICS_DRONE.get());
        boolean vessel = stack.is(UniverseItems.LOGISTICS_VESSEL.get());
        boolean warper = stack.is(UniverseItems.SPACE_WARPER.get());
        if (!drone && !vessel && !warper) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(this.machinePos(state, pos)) instanceof StationBlockEntity station)
                || !drone && station.kind() != StationKind.INTERSTELLAR) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Station ledger = station.station();
        if (ledger == null) {
            return InteractionResult.FAIL;
        }
        int added;
        if (drone) {
            added = ledger.addDrones(stack.getCount());
        } else if (vessel) {
            added = ledger.addVessels(stack.getCount());
        } else {
            added = Math.min(stack.getCount(), Station.MAX_WARPERS - ledger.warpers());
            ledger.setWarpers(ledger.warpers() + added);
        }
        if (added <= 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.station.full"));
            return InteractionResult.FAIL;
        }
        stack.consume(added, player);
        station.setChanged();
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        player.sendOverlayMessage(drone
                ? Component.translatable("message.universe.station.drones", ledger.drones(), ledger.kind().maxDrones())
                : vessel ? Component.translatable("message.universe.station.vessels", ledger.vessels(), ledger.kind().maxVessels())
                        : Component.translatable("message.universe.station.warpers", ledger.warpers(), Station.MAX_WARPERS));
        return InteractionResult.SUCCESS;
    }

    /** 比较器按账本里的存量出信号，不按投放口。 */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(this.machinePos(state, pos)) instanceof StationBlockEntity station ? station.analogSignal() : 0;
    }
}
