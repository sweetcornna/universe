package io.github.sweetcornna.universe.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.perf.PerfStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** {@code /universe perf start|stop|report}：分项计时（ADR-0019）。报告也写进服务端日志，日志里的文字固定用中文，验收脚本靠它找结果。 */
public final class PerfCommand {
    /** 报告最多列几项。 */
    private static final int LINES = 24;

    private PerfCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perf")
                .then(Commands.literal("start").executes(context -> {
                    ServerPerf.start(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.perf.started"), true);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(context -> {
                    ServerPerf.stop();
                    context.getSource().sendSuccess(() -> Component.translatable("commands.universe.perf.stopped"), true);
                    return 1;
                }))
                .then(Commands.literal("report").executes(context -> {
                    report(ServerPerf.stats()).forEach(line -> Universe.LOGGER.info("Perf: {}", line));
                    Component text = feedback(ServerPerf.stats());
                    context.getSource().sendSuccess(() -> text, false);
                    return (int) ServerPerf.stats().ticks();
                }));
    }

    static List<String> report(PerfStats stats) {
        List<String> lines = new ArrayList<>();
        if (stats.ticks() == 0) {
            lines.add(ServerPerf.running() ? "还没记到完整的一刻" : "没有记录，先用 /universe perf start");
            return lines;
        }
        double server = stats.serverMsPerTick();
        double total = stats.totalMsPerTick();
        lines.add(String.format(Locale.ROOT, "%d 刻，服务器每刻 %.2f ms，本模组合计 %.2f ms（%.0f%%）", stats.ticks(), server, total,
                server == 0 ? 0 : total / server * 100));
        List<PerfStats.Line> report = stats.report();
        for (int i = 0; i < Math.min(LINES, report.size()); i++) {
            PerfStats.Line line = report.get(i);
            lines.add(String.format(Locale.ROOT, "  %-28s %7.3f ms  %7.1f 次  %4.1f%%", line.section(), line.msPerTick(), line.callsPerTick(),
                    line.share() * 100));
        }
        if (report.size() > LINES) {
            lines.add("  ……另有 " + (report.size() - LINES) + " 项");
        }
        return lines;
    }

    /** 和 {@link #report} 同样的内容，发给执行命令的人，按客户端语言显示。 */
    private static Component feedback(PerfStats stats) {
        if (stats.ticks() == 0) {
            return Component.translatable(ServerPerf.running() ? "commands.universe.perf.no_tick" : "commands.universe.perf.no_data");
        }
        double server = stats.serverMsPerTick();
        double total = stats.totalMsPerTick();
        MutableComponent text = Component.translatable("commands.universe.perf.summary", stats.ticks(), String.format(Locale.ROOT, "%.2f", server),
                String.format(Locale.ROOT, "%.2f", total), String.format(Locale.ROOT, "%.0f", server == 0 ? 0 : total / server * 100));
        List<PerfStats.Line> report = stats.report();
        for (int i = 0; i < Math.min(LINES, report.size()); i++) {
            PerfStats.Line line = report.get(i);
            text.append("\n  ").append(Component.translatable("commands.universe.perf.line", String.format(Locale.ROOT, "%-28s", line.section()),
                    String.format(Locale.ROOT, "%7.3f", line.msPerTick()), String.format(Locale.ROOT, "%7.1f", line.callsPerTick()),
                    String.format(Locale.ROOT, "%4.1f", line.share() * 100)));
        }
        if (report.size() > LINES) {
            text.append("\n  ").append(Component.translatable("commands.universe.perf.more", report.size() - LINES));
        }
        return text;
    }
}
