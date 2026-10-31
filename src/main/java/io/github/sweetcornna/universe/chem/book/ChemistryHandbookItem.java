package io.github.sweetcornna.universe.chem.book;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 化学手册（ADR-0037 §四）：右键打开，按类列出反应。界面在客户端，这里只留一个钩子，免得专用服务器加载客户端的类
 * （和 {@link io.github.sweetcornna.universe.guide.GuideItem} 一样）。
 */
public class ChemistryHandbookItem extends Item {
    /** 打开手册界面，客户端启动时设上。 */
    public static Runnable opener = () -> {
    };

    public ChemistryHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.run();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.chemistry_handbook").withStyle(ChatFormatting.GRAY));
    }
}
