package io.github.sweetcornna.universe.fishing;

import java.util.function.Consumer;

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
import net.minecraft.world.level.Level;

/**
 * 钓上来的漂流瓶和密封补给舱（ADR-0034 §一）：右键打开。漂流瓶标出这颗星球上附近的一个兴趣点，补给舱开出一份补给。
 */
public class FishingFindItem extends Item {
    public enum Kind {
        BOTTLE,
        CACHE
    }

    private final Kind kind;

    public FishingFindItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            boolean opened = switch (this.kind) {
                case BOTTLE -> Fishing.openBottle(serverPlayer);
                case CACHE -> {
                    Fishing.openCache(serverPlayer);
                    yield true;
                }
            };
            if (opened) {
                player.getItemInHand(hand).consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String key = this.kind == Kind.BOTTLE ? "tooltip.universe.message_bottle" : "tooltip.universe.sealed_cache";
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
