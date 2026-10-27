package io.github.sweetcornna.universe.block;

import java.util.Optional;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 水培槽（ADR-0026）：拿着产物右键种下，熟了空手右键收获，潜行空手右键拔掉，没熟时右键看长势。没有界面。
 * 拿硝酸铵右键施肥（ADR-0036 §五）。
 */
public class HydroponicTrayBlock extends MachineBlock {
    /** 槽子十格高，植物立在液面上。 */
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 10.0);

    public HydroponicTrayBlock(Properties properties) {
        super(properties, UniverseBlockEntities.HYDROPONIC_TRAY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.is(UniverseItems.AMMONIUM_NITRATE.get())) {
            if (!(level.getBlockEntity(pos) instanceof HydroponicTrayBlockEntity tray)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (!level.isClientSide()) {
                if (tray.fertilize()) {
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    player.sendOverlayMessage(Component.translatable("message.universe.hydroponic_tray.fertilized", tray.fertilizer()));
                } else {
                    player.sendOverlayMessage(Component.translatable("message.universe.hydroponic_tray.fertilizer_full"));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (HydroponicTrayBlockEntity.farmable(stack).isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof HydroponicTrayBlockEntity tray) || tray.plant().isPresent()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && tray.sow(stack)) {
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof HydroponicTrayBlockEntity tray)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            ItemStack seed = tray.uproot();
            if (!seed.isEmpty()) {
                give(player, seed);
                give(player, tray.harvest());
                give(player, tray.harvestSeedlings());
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack crop = tray.harvest();
        if (!crop.isEmpty()) {
            give(player, crop);
            give(player, tray.harvestSeedlings());
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        Optional<ResourcePlant> plant = tray.plant();
        Component message;
        if (plant.isEmpty()) {
            message = Component.translatable("message.universe.hydroponic_tray.empty");
        } else if (!tray.connected()) {
            message = Component.translatable("message.universe.hydroponic_tray.no_power");
        } else {
            message = Component.translatable("message.universe.hydroponic_tray.growing",
                    Component.translatable("block.universe." + plant.get().id()), Math.round(tray.growth() * 100));
        }
        player.sendOverlayMessage(message);
        return InteractionResult.SUCCESS;
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }
}
