package io.github.sweetcornna.universe.trade;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /universe credits [add|set <数量>]}：查看、加减、设定自己的信用点（ADR-0017），调试和创造模式用。 */
public final class CreditsCommand {
    private CreditsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("credits")
                .executes(CreditsCommand::show)
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Credits.add(player, LongArgumentType.getLong(context, "amount"));
                                    return show(context);
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    player.setData(UniverseAttachments.CREDITS, LongArgumentType.getLong(context, "amount"));
                                    return show(context);
                                })));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long credits = Credits.get(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.credits", Credits.format(credits)), false);
        return (int) Math.min(Integer.MAX_VALUE, credits);
    }
}
