package io.github.sweetcornna.universe.factory.belt;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.factory.Proliferators;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/** 喷涂机（ADR-0022）：拿着增产剂右键塞进去，空手右键看还剩多少。 */
public class SprayCoaterBlock extends BeltPassBlock {
    public SprayCoaterBlock(Properties properties) {
        super(properties, UniverseBlockEntities.SPRAY_COATER, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(2, 9, 3, 14, 15, 13)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // 拿着别的东西照常用（放方块）
        if (Proliferators.tier(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SprayCoaterBlockEntity coater) {
            int added = coater.insert(stack);
            if (added > 0) {
                stack.consume(added, player);
            }
            status(player, coater);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SprayCoaterBlockEntity coater) {
            status(player, coater);
        }
        return InteractionResult.SUCCESS;
    }

    private static void status(Player player, SprayCoaterBlockEntity coater) {
        Proliferation.Tier tier = coater.tier();
        player.sendOverlayMessage(tier == null ? Component.translatable("message.universe.spray_coater.empty")
                : Component.translatable("message.universe.spray_coater.status", new ItemStack(Proliferators.item(tier)).getHoverName(),
                        coater.remaining()));
    }
}
