package io.github.sweetcornna.universe.encounter;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /universe encounter ...}：调试太空遭遇（ADR-0024）。
 *
 * <ul>
 *   <li>{@code derelict}、{@code distress}：在开着的船前方放一处遭遇（不管计时、创造模式）。</li>
 *   <li>{@code sentinels [n]}：太空守卫机追上来（默认按三星通缉的架数）。</li>
 *   <li>{@code visit [hazard]}：送到自己那处遭遇跟前（废弃货船直接进机库，或者站到漏电的线管底下）。</li>
 *   <li>{@code accept}、{@code credits}：替聊天栏里的按钮回船长的话。</li>
 *   <li>{@code status}：计时和正在进行的遭遇；{@code clear}：全部收场。</li>
 * </ul>
 */
public final class EncounterCommand {
    private EncounterCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("encounter")
                        .then(Commands.literal("derelict").executes(context -> place(context.getSource(), true)))
                        .then(Commands.literal("distress").executes(context -> place(context.getSource(), false)))
                        .then(Commands.literal("sentinels")
                                .executes(context -> sentinels(context.getSource(), Encounters.sentinels(Encounters.SENTINEL_WANTED)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 12))
                                        .executes(context -> sentinels(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("visit").executes(context -> visit(context.getSource(), false))
                                .then(Commands.literal("hazard").executes(context -> visit(context.getSource(), true))))
                        .then(Commands.literal("accept").executes(context -> answer(context.getSource(), true)))
                        .then(Commands.literal("credits").executes(context -> answer(context.getSource(), false)))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("clear").executes(context -> {
                            int n = SpaceEncounters.clear(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.encounter.cleared", n), true);
                            return n;
                        }))));
    }

    private static int place(CommandSourceStack source, boolean derelict) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || !SpaceDimensions.isSpace(player.level().dimension())) {
            source.sendFailure(Component.translatable("commands.universe.encounter.not_piloting"));
            return 0;
        }
        boolean placed = derelict ? SpaceEncounters.derelict(player, ship) : SpaceEncounters.distress(player, ship);
        if (!placed) {
            source.sendFailure(Component.translatable("commands.universe.encounter.no_room"));
            return 0;
        }
        return 1;
    }

    private static int sentinels(CommandSourceStack source, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!SpaceEncounters.pursue(player, count, true)) {
            source.sendFailure(Component.translatable("commands.universe.encounter.not_in_space"));
            return 0;
        }
        return count;
    }

    private static int visit(CommandSourceStack source, boolean hazard) throws CommandSyntaxException {
        if (!SpaceEncounters.visit(source.getPlayerOrException(), hazard)) {
            source.sendFailure(Component.translatable("commands.universe.encounter.none"));
            return 0;
        }
        return 1;
    }

    private static int answer(CommandSourceStack source, boolean freighter) throws CommandSyntaxException {
        return SpaceEncounters.answer(source.getPlayerOrException(), freighter) ? 1 : 0;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int[] timer = SpaceEncounters.timer(player);
        String clock = timer == null ? "-" : (timer[1] - timer[0]) / 20 + "s";
        String site = EncounterSites.get(source.getServer()).of(player.getUUID())
                .map(s -> s.kind().name().toLowerCase(Locale.ROOT) + " @ " + s.x() + " " + s.y() + " " + s.z())
                .orElse("-");
        String distress = SpaceEncounters.distressStatus(player);
        String pursuit = SpaceEncounters.pursuitStatus(player);
        source.sendSuccess(() -> Component.translatable("commands.universe.encounter.status", clock, site,
                distress == null ? "-" : distress, pursuit == null ? "-" : pursuit), false);
        return 1;
    }
}
