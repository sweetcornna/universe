package io.github.sweetcornna.universe.fishing;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.fishing.FishSpecies;
import io.github.sweetcornna.universe.core.fishing.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 一种鱼（ADR-0034 §一）：能叠，生吃给一点饱食度；对着水右键放生。名字按档次上色，说明里写水域、档次和个头。
 * 每条鱼多重不记在物品上（不然叠不起来），记在图鉴里。
 */
public class FishItem extends Item {
    private final FishSpecies species;

    public FishItem(FishSpecies species, Properties properties) {
        super(properties);
        this.species = species;
    }

    public FishSpecies species() {
        return this.species;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return this.species.rarity() == Rarity.COMMON ? name : name.copy().withColor(this.species.rarity().color() & 0xFFFFFF);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.BLOCK && Fishing.fishable(level.getFluidState(hit.getBlockPos()))) {
            if (player instanceof ServerPlayer serverPlayer) {
                Fishing.release(serverPlayer, player.getItemInHand(hand), hit.getBlockPos());
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FishSpecies s = this.species;
        builder.accept(Component.translatable("tooltip.universe.fish.kind", Component.translatable("fishing.universe.habitat." + s.habitat().key()),
                Component.translatable("fishing.universe.rarity." + s.rarity().key()),
                Component.translatable("fishing.universe.size." + s.size().key())).withColor(s.rarity().color() & 0xFFFFFF));
        if (s.conditional()) {
            builder.accept(FishText.conditions(s).withStyle(ChatFormatting.GRAY));
        }
        builder.accept(Component.translatable("tooltip.universe.fish.release").withStyle(ChatFormatting.DARK_AQUA));
    }
}
