package io.github.sweetcornna.universe.story;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.core.story.Milestone;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 调试命令 {@code /universe story}，开发和测试用，提示文字不做翻译。
 *
 * <ul>
 * <li>{@code status}：到过哪些剧情节点
 * <li>{@code reach <节点>}：直接到这个节点（发日志或放结局）
 * <li>{@code forget <节点>|all}：忘掉节点，好再触发一次
 * <li>{@code ending}：给自己放一遍结局画面
 * </ul>
 */
public final class StoryCommand {
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(id -> Component.literal("未知节点：" + id));
    private static final SuggestionProvider<CommandSourceStack> MILESTONES = (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(Milestone.values()).map(Milestone::id), builder);

    private StoryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("story")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("reach")
                        .then(Commands.argument("milestone", StringArgumentType.word()).suggests(MILESTONES)
                                .executes(context -> reach(context, milestone(context)))))
                .then(Commands.literal("forget")
                        .then(Commands.literal("all").executes(context -> forgetAll(context.getSource())))
                        .then(Commands.argument("milestone", StringArgumentType.word()).suggests(MILESTONES)
                                .executes(context -> forget(context, milestone(context)))))
                .then(Commands.literal("ending").executes(StoryCommand::ending));
    }

    private static Milestone milestone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "milestone");
        return Milestone.byId(id).orElseThrow(() -> UNKNOWN.create(id));
    }

    private static int status(CommandSourceStack source) {
        Map<String, Long> milestones = StoryData.get(source.getServer()).milestones();
        String text = milestones.isEmpty() ? "（无）" : milestones.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " @" + entry.getValue())
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("剧情节点：" + text), false);
        return milestones.size();
    }

    private static int reach(CommandContext<CommandSourceStack> context, Milestone milestone) {
        MinecraftServer server = context.getSource().getServer();
        boolean already = Story.reached(server, milestone);
        Story.reach(server, milestone);
        context.getSource().sendSuccess(() -> Component.literal(already ? milestone.id() + " 已经到过" : "到达 " + milestone.id()), true);
        return already ? 0 : 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context, Milestone milestone) {
        MinecraftServer server = context.getSource().getServer();
        StoryData.get(server).forget(milestone);
        Story.changed(server);
        context.getSource().sendSuccess(() -> Component.literal("忘掉了 " + milestone.id()), true);
        return 1;
    }

    private static int forgetAll(CommandSourceStack source) {
        StoryData.get(source.getServer()).clear();
        Story.changed(source.getServer());
        source.sendSuccess(() -> Component.literal("剧情进度已清空"), true);
        return 1;
    }

    private static int ending(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Story.showEnding(context.getSource().getServer(), List.of(context.getSource().getPlayerOrException()));
        return 1;
    }
}
