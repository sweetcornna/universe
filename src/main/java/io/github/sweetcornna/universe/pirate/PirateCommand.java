package io.github.sweetcornna.universe.pirate;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.core.ship.PirateRaid;
import io.github.sweetcornna.universe.entity.ship.ShipCargo;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /universe pirates [info|ambush|clear]}：调试海盗袭击（ADR-0017）。不带参数或 {@code info} 显示货值、遭遇的概率和离下次掷骰子还有多久；
 * {@code ambush} 立刻触发一场袭击（不管创造模式、离空间站多近）；{@code clear} 让所有海盗撤走、计时清零。
 */
public final class PirateCommand {
    private PirateCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("pirates")
                .executes(PirateCommand::info)
                .then(Commands.literal("info").executes(PirateCommand::info))
                .then(Commands.literal("ambush").executes(PirateCommand::ambush))
                .then(Commands.literal("clear").executes(context -> {
                    int n = Pirates.clear();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.pirates.cleared", n), true);
                    return n;
                }));
    }

    private static @Nullable ShipEntity ship(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || !SpaceDimensions.isSpace(player.level().dimension())) {
            return null;
        }
        return ship;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ShipEntity ship = ship(context);
        if (ship == null) {
            context.getSource().sendFailure(Component.translatable("commands.universe.pirates.not_piloting"));
            return 0;
        }
        double value = ShipCargo.value(ship, player.level().getServer());
        String chance = String.format(Locale.ROOT, "%.0f", PirateRaid.chance(value) * 100);
        String credits = Credits.format(Math.round(value));
        int[] timer = Pirates.timer(player);
        Component message = timer == null
                ? Component.translatable("commands.universe.pirates.info_idle", credits, chance)
                : Component.translatable("commands.universe.pirates.info", credits, chance, Math.max(0, (timer[1] - timer[0]) / 20));
        context.getSource().sendSuccess(() -> message, false);
        return (int) Math.round(value);
    }

    private static int ambush(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ShipEntity ship = ship(context);
        if (ship == null) {
            context.getSource().sendFailure(Component.translatable("commands.universe.pirates.not_piloting"));
            return 0;
        }
        if (Pirates.raiding(player)) {
            context.getSource().sendFailure(Component.translatable("commands.universe.pirates.busy"));
            return 0;
        }
        double value = ShipCargo.value(ship, player.level().getServer());
        int count = Pirates.start(player, ship, value, true);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.pirates.started", count, Credits.format(Math.round(value))), true);
        return count;
    }
}
