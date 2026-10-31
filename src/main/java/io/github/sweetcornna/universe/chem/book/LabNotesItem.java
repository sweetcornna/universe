package io.github.sweetcornna.universe.chem.book;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 实验记录（ADR-0037 §五）：写着一条反应（{@code universe:reaction} 组件）。右键读了，这条反应就全服发现了，和自由投料做成一批一样，
 * 读完这张用掉；已经发现的读了只提示一句，不用掉。物品名带上反应名，箱子里一眼看得出是哪条。
 */
public class LabNotesItem extends Item {
    public LabNotesItem(Properties properties) {
        super(properties);
    }

    /** 这张记录写的反应；没写或者写的反应不认识为 {@code null}。 */
    public static @Nullable Reaction reaction(ItemStack stack) {
        String name = stack.get(UniverseDataComponents.REACTION.get());
        return name == null ? null : Reaction.byName(name);
    }

    /** 一张写着这条反应的实验记录。 */
    public static ItemStack of(Item item, Reaction reaction) {
        ItemStack stack = new ItemStack(item);
        stack.set(UniverseDataComponents.REACTION.get(), reaction.name());
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Reaction reaction = reaction(stack);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (reaction == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.lab_notes.blank"));
            return InteractionResult.SUCCESS;
        }
        if (!ReactionBooks.discover(serverLevel.getServer(), reaction, true)) {
            player.sendOverlayMessage(Component.translatable("message.universe.lab_notes.known", Component.translatable("reaction.universe." + reaction.id())));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Reaction reaction = reaction(stack);
        return reaction == null ? super.getName(stack)
                : Component.translatable("item.universe.lab_notes.named", Component.translatable("reaction.universe." + reaction.id()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Reaction reaction = reaction(stack);
        if (reaction == null) {
            builder.accept(Component.translatable("tooltip.universe.lab_notes.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(Component.literal(reaction.equation()).withStyle(ChatFormatting.GRAY));
        boolean known = ReactionBooks.current().discovered(reaction);
        builder.accept(Component.translatable(known ? "tooltip.universe.lab_notes.known" : "tooltip.universe.lab_notes")
                .withStyle(known ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
    }
}
