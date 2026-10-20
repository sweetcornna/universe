package io.github.sweetcornna.universe.blueprint;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 蓝图（ADR-0018）。空白的：右键两个方块框出选区，潜行右键起名保存。画好的：右键对着方块放下（底面中心对着点的
 * 那一格），按旋转键转 90°，潜行右键清空回空白。画好的蓝图带附魔光效，好和空白的区分开。
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    /** 在方块自己的右键（比如打开设备界面）之前处理，这样对着设备也能框选区、放蓝图。 */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            // 潜行右键交给 use：保存或清空
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (stack.has(UniverseDataComponents.BLUEPRINT.get())) {
                Blueprints.place(serverPlayer, stack, anchor(context.getLevel(), context.getClickedPos(), context.getClickedFace()));
            } else {
                Blueprints.select(serverPlayer, stack, context.getClickedPos());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (stack.has(UniverseDataComponents.BLUEPRINT.get())) {
                stack.remove(UniverseDataComponents.BLUEPRINT.get());
                stack.remove(UniverseDataComponents.BLUEPRINT_ROTATION.get());
                serverPlayer.sendOverlayMessage(Component.translatable("message.universe.blueprint.cleared"));
            } else {
                Blueprints.requestSave(serverPlayer, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 放蓝图的那一格：点的方块能被替换（草、雪）就是它，否则是它朝点的那一面的邻格。 */
    public static BlockPos anchor(Level level, BlockPos clicked, Direction face) {
        return level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
    }

    /** 蓝图（已经转过的大小）的最小角：底面中心对着 anchor。 */
    public static BlockPos origin(BlockPos anchor, int[] rotatedSize) {
        return anchor.offset(-rotatedSize[0] / 2, 0, -rotatedSize[2] / 2);
    }

    @Override
    public Component getName(ItemStack stack) {
        BlueprintInfo info = stack.get(UniverseDataComponents.BLUEPRINT.get());
        return info == null ? super.getName(stack) : Component.translatable("item.universe.blueprint.named", info.name());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(UniverseDataComponents.BLUEPRINT.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        BlueprintInfo info = stack.get(UniverseDataComponents.BLUEPRINT.get());
        if (info == null) {
            BlueprintSelection selection = stack.get(UniverseDataComponents.BLUEPRINT_SELECTION.get());
            if (selection != null && selection.complete()) {
                int[] size = selection.size().orElseThrow();
                builder.accept(Component.translatable("tooltip.universe.blueprint.selection", size[0], size[1], size[2])
                        .withStyle(ChatFormatting.GRAY));
            } else if (selection != null && selection.first().isPresent()) {
                BlockPos first = selection.first().get();
                builder.accept(Component.translatable("tooltip.universe.blueprint.first", first.getX(), first.getY(), first.getZ())
                        .withStyle(ChatFormatting.GRAY));
            } else {
                builder.accept(Component.translatable("tooltip.universe.blueprint.blank").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        builder.accept(Component.translatable("tooltip.universe.blueprint.size", info.sizeX(), info.sizeY(), info.sizeZ(), info.blocks())
                .withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (BlueprintInfo.Material material : info.materials()) {
            if (shown == Blueprints.LISTED_MATERIALS) {
                builder.accept(Component.translatable("tooltip.universe.blueprint.more", info.materials().size() - shown)
                        .withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            builder.accept(Component.translatable("tooltip.universe.blueprint.material", new ItemStack(material.item()).getHoverName(),
                    material.count()).withStyle(ChatFormatting.DARK_GRAY));
            shown++;
        }
    }
}
