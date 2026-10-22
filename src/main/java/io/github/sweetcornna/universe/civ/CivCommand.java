package io.github.sweetcornna.universe.civ;

import java.util.Arrays;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /universe civ ...}：调试外星文明（ADR-0027）。
 *
 * <ul>
 *   <li>{@code status}：三个种族的声望、档和认识几个词，这里的主导种族。</li>
 *   <li>{@code standing <种族> <值>}：把声望设成这个值（档变了照常提示）。</li>
 *   <li>{@code teach <种族> [n]}：教 n 个词（默认 1）。</li>
 *   <li>{@code newday}：忘掉今天和谁说过话、请教过。</li>
 *   <li>{@code reset}：声望、词、知识石全部清空。</li>
 * </ul>
 */
public final class CivCommand {
    private static final SuggestionProvider<CommandSourceStack> RACES = (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(Race.values()).map(Race::id), builder);

    private CivCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("civ")
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("standing")
                                .then(Commands.argument("race", StringArgumentType.word()).suggests(RACES)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-20, 100))
                                                .executes(context -> standing(context, IntegerArgumentType.getInteger(context, "value"))))))
                        .then(Commands.literal("teach")
                                .then(Commands.argument("race", StringArgumentType.word()).suggests(RACES)
                                        .executes(context -> teach(context, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, Lexicon.SIZE))
                                                .executes(context -> teach(context, IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("newday").executes(context -> newDay(context.getSource())))
                        .then(Commands.literal("reset").executes(context -> reset(context.getSource())))));
    }

    private static Optional<Race> race(CommandContext<CommandSourceStack> context) {
        Optional<Race> race = Race.byId(StringArgumentType.getString(context, "race"));
        if (race.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.universe.civ.unknown_race", StringArgumentType.getString(context, "race")));
        }
        return race;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CivState state = Civ.state(player);
        for (Race race : Race.values()) {
            source.sendSuccess(() -> Component.translatable("commands.universe.civ.status", Civ.raceName(race), state.standing(race),
                    Civ.tierName(state.tier(race)), state.knownCount(race), Lexicon.SIZE).withColor(race.color()), false);
        }
        Civ.dominant(player.level()).ifPresent(race -> source.sendSuccess(
                () -> Component.translatable("commands.universe.civ.dominant", Civ.raceName(race)), false));
        return 1;
    }

    private static int standing(CommandContext<CommandSourceStack> context, int value) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Race> race = race(context);
        if (race.isEmpty()) {
            return 0;
        }
        Civ.changeStanding(player, race.get(), value - Civ.state(player).standing(race.get()));
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.civ.standing_set", Civ.raceName(race.get()), value), true);
        return value;
    }

    private static int teach(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Race> race = race(context);
        if (race.isEmpty()) {
            return 0;
        }
        int taught = 0;
        for (int i = 0; i < count; i++) {
            if (Civ.teachWord(player, race.get(), -1L, player.getRandom().nextLong()) < 0) {
                break;
            }
            taught++;
        }
        return taught;
    }

    private static int newDay(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Civ.state(player).clearDaily();
        Civ.sync(player);
        source.sendSuccess(() -> Component.translatable("commands.universe.civ.newday"), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.setData(UniverseAttachments.CIVILIZATION, new CivState());
        Civ.sync(player);
        source.sendSuccess(() -> Component.translatable("commands.universe.civ.reset"), true);
        return 1;
    }
}
