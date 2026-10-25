package io.github.sweetcornna.universe.mission;

import java.util.OptionalInt;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.dyson.Dyson;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /universe mission info|advance <n>|reset}：看今天的任务和手上的任务、把清剿采矿测绘的进度往前推、清掉任务记录，开发和验收用。
 */
public final class MissionCommand {
    private MissionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mission")
                .then(Commands.literal("info").executes(MissionCommand::info))
                .then(Commands.literal("advance")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> advance(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("reset").executes(MissionCommand::reset));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OptionalInt system = Dyson.systemOf(context.getSource().getLevel().dimension());
        if (system.isPresent()) {
            for (Mission mission : Missions.offers(context.getSource().getServer(), system.getAsInt())) {
                context.getSource().sendSuccess(() -> Component.translatable("commands.universe.mission.offer", mission.id(), describe(mission)), false);
            }
        }
        MissionState state = Missions.state(player);
        int done = state.done().size();
        context.getSource().sendSuccess(() -> state.active()
                .map(active -> Component.translatable("commands.universe.mission.active", active.mission().id(), describe(active.mission()),
                        active.progress(), done))
                .orElse(Component.translatable("commands.universe.mission.idle", done)), false);
        return 1;
    }

    private static Component describe(Mission mission) {
        MutableComponent goal = Component.translatable("gui.universe.mission.kind." + mission.kind().key());
        if (!mission.item().isEmpty()) {
            goal.append(" " + mission.item());
        }
        return mission.bonus() == Mission.Bonus.NONE
                ? Component.translatable("commands.universe.mission.describe", goal, mission.count(), mission.reward())
                : Component.translatable("commands.universe.mission.describe.bonus", goal, mission.count(), mission.reward(),
                        Component.translatable("commands.universe.mission.bonus." + mission.bonus().key()), mission.bonusCount());
    }

    private static int advance(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!Missions.advance(player, count)) {
            context.getSource().sendFailure(Component.translatable("commands.universe.mission.advance.failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.mission.advance",
                Missions.state(player).active().map(MissionState.Active::progress).orElse(0)), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Missions.reset(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.mission.reset"), false);
        return 1;
    }
}
