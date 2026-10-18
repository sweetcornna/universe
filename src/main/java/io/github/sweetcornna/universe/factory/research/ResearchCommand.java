package io.github.sweetcornna.universe.factory.research;

import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 调试命令 {@code /universe research}，开发和测试用，提示文字不做翻译。
 *
 * <ul>
 * <li>{@code grant <科技>|all}：直接完成科技（{@code all} 完成全部）
 * <li>{@code revoke <科技>|all}：收回科技（{@code all} 清空全部进度）
 * <li>{@code status}：完成了哪些、正在研究什么
 * </ul>
 */
public final class ResearchCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TECH =
            new DynamicCommandExceptionType(id -> Component.literal("未知科技：" + id));
    private static final SuggestionProvider<CommandSourceStack> TECHS = (context, builder) -> SharedSuggestionProvider.suggest(
            Research.TREE.techs().stream().map(Tech::id), builder);

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
        context.getSource().sendSuccess(() -> Component.literal("已完成科技 " + id), true);
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
        source.sendSuccess(() -> Component.literal("已完成 " + granted + " 个科技"), true);
        return granted;
    }

    private static int revoke(CommandContext<CommandSourceStack> context, String id) {
        MinecraftServer server = context.getSource().getServer();
        ResearchData.get(server).state().revoke(id);
        Research.changed(server);
        context.getSource().sendSuccess(() -> Component.literal("已收回科技 " + id), true);
        return 1;
    }

    private static int revokeAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ResearchData.get(server).state().clear();
        Research.changed(server);
        source.sendSuccess(() -> Component.literal("已清空研究进度"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ResearchState state = ResearchData.get(source.getServer()).state();
        String done = state.done().isEmpty() ? "（无）" : String.join(", ", state.done());
        String current = state.current() == null ? "（无）" : state.current() + " " + state.progress(state.current()) + "/"
                + Research.TREE.get(state.current()).map(Tech::points).orElse(0);
        String paused = state.progress().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(state.current()))
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("已完成：" + done + "\n正在研究：" + current
                + (paused.isEmpty() ? "" : "\n暂停的：" + paused)), false);
        return state.done().size();
    }
}
