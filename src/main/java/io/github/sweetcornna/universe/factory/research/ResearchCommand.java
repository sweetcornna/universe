package io.github.sweetcornna.universe.factory.research;

import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.Upgrade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * 调试命令 {@code /universe research}，开发和测试用。
 *
 * <ul>
 * <li>{@code grant <科技>|all}：直接完成科技（{@code all} 完成全部）
 * <li>{@code revoke <科技>|all}：收回科技（{@code all} 清空全部进度）
 * <li>{@code level <升级科技> <级数>}：直接设升级科技的级数
 * <li>{@code points <点数>}：给当前研究加点，不用矩阵
 * <li>{@code status}：完成了哪些、升级科技几级、正在研究什么
 * </ul>
 */
public final class ResearchCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TECH =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.universe.research.unknown", String.valueOf(id)));
    private static final SuggestionProvider<CommandSourceStack> TECHS = (context, builder) -> SharedSuggestionProvider.suggest(
            Research.TREE.techs().stream().map(Tech::id), builder);
    private static final SuggestionProvider<CommandSourceStack> UPGRADES = (context, builder) -> SharedSuggestionProvider.suggest(
            Research.TREE.upgrades().stream().map(Upgrade::id), builder);

    private ResearchCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("research")
                .then(Commands.literal("grant")
                        .then(Commands.literal("all").executes(context -> grantAll(context.getSource())))
                        .then(Commands.argument("tech", StringArgumentType.word()).suggests(TECHS)
                                .executes(context -> grant(context, tech(context)))))
                .then(Commands.literal("revoke")
                        .then(Commands.literal("all").executes(context -> revokeAll(context.getSource())))
                        .then(Commands.argument("tech", StringArgumentType.word()).suggests(TECHS)
                                .executes(context -> revoke(context, tech(context)))))
                .then(Commands.literal("level")
                        .then(Commands.argument("upgrade", StringArgumentType.word()).suggests(UPGRADES)
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(ResearchCommand::level))))
                .then(Commands.literal("points")
                        .then(Commands.argument("points", IntegerArgumentType.integer(1, 100_000))
                                .executes(ResearchCommand::points)))
                .then(Commands.literal("status").executes(context -> status(context.getSource())));
    }

    private static String tech(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "tech");
        if (Research.TREE.get(id).isEmpty()) {
            throw UNKNOWN_TECH.create(id);
        }
        return id;
    }

    private static int grant(CommandContext<CommandSourceStack> context, String id) {
        Research.complete(context.getSource().getServer(), id);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.research.granted", id), true);
        return 1;
    }

    private static int grantAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (Tech tech : Research.TREE.techs()) {
            if (!ResearchData.get(server).state().isDone(tech.id())) {
                Research.complete(server, tech.id());
                count++;
            }
        }
        int granted = count;
        source.sendSuccess(() -> Component.translatable("commands.universe.research.granted_all", granted), true);
        return granted;
    }

    private static int revoke(CommandContext<CommandSourceStack> context, String id) {
        MinecraftServer server = context.getSource().getServer();
        ResearchData.get(server).state().revoke(id);
        Research.changed(server);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.research.revoked", id), true);
        return 1;
    }

    private static int revokeAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ResearchData.get(server).state().clear();
        Research.changed(server);
        source.sendSuccess(() -> Component.translatable("commands.universe.research.revoked_all"), true);
        return 1;
    }

    private static int level(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "upgrade");
        Upgrade upgrade = Research.TREE.upgrade(id).orElseThrow(() -> UNKNOWN_TECH.create(id));
        int level = Math.min(IntegerArgumentType.getInteger(context, "level"), upgrade.maxLevel());
        MinecraftServer server = context.getSource().getServer();
        ResearchData.get(server).state().setLevel(id, level);
        Research.changed(server);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.research.level", id, level), true);
        return level;
    }

    private static int points(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        int points = IntegerArgumentType.getInteger(context, "points");
        int added = 0;
        while (added < points && ResearchData.get(server).state().current() != null) {
            Research.addPoint(server);
            added++;
        }
        int total = added;
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.research.points", total), true);
        return total;
    }

    private static int status(CommandSourceStack source) {
        ResearchState state = ResearchData.get(source.getServer()).state();
        Component none = Component.translatable("commands.universe.none");
        Component done = state.done().isEmpty() ? none : Component.literal(String.join(", ", state.done()));
        Component current = state.current() == null ? none : Component.literal(state.current() + " " + state.progress(state.current()) + "/"
                + state.pointsNeeded(Research.TREE, state.current()));
        String paused = state.progress().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(state.current()))
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
        String levels = state.levels().entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
        MutableComponent text = Component.translatable("commands.universe.research.status.done", done)
                .append("\n").append(Component.translatable("commands.universe.research.status.current", current));
        if (!paused.isEmpty()) {
            text.append("\n").append(Component.translatable("commands.universe.research.status.paused", paused));
        }
        if (!levels.isEmpty()) {
            text.append("\n").append(Component.translatable("commands.universe.research.status.levels", levels));
        }
        source.sendSuccess(() -> text, false);
        return state.done().size();
    }
}
