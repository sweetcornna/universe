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
                context.getSource().sendSuccess(() -> Component.literal("任务 " + mission.id() + "：" + describe(mission)), false);
            }
        }
        MissionState state = Missions.state(player);
        context.getSource().sendSuccess(() -> Component.literal(state.active()
                .map(active -> "手上：" + active.mission().id() + " " + describe(active.mission()) + "，进度 " + active.progress())
                .orElse("手上没有任务") + "；今天做完 " + state.done().size() + " 个"), false);
        return 1;
    }

    private static String describe(Mission mission) {
        return mission.kind().key() + (mission.item().isEmpty() ? "" : " " + mission.item()) + " ×" + mission.count() + "，报酬 " + mission.reward()
                + (mission.bonus() == Mission.Bonus.NONE ? "" : "，另给 " + mission.bonus().key() + " ×" + mission.bonusCount());
    }

    private static int advance(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!Missions.advance(player, count)) {
            context.getSource().sendFailure(Component.literal("手上没有能推进度的任务（送货要真的带东西来）"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("进度 " + Missions.state(player).active().map(MissionState.Active::progress).orElse(0)),
                false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Missions.reset(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.literal("任务记录已清空"), false);
        return 1;
    }
}
