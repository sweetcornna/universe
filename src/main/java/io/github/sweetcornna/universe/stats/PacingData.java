package io.github.sweetcornna.universe.stats;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.stats.Pacing;
import io.github.sweetcornna.universe.story.StoryData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 节奏记录（ADR-0039），存在 {@code data/universe/pacing.dat}：每个科技第一次研究完时主世界的游戏刻。剧情节点的时间
 * {@link StoryData} 本来就记着，报告时一起读。{@code /universe pacing} 把关键节点和平衡模型的目标放在一起列出来（{@link Pacing}）。
 */
public final class PacingData extends SavedData {
    private static final int FORMAT = 1;

    private static final Codec<PacingData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("techs", Map.of()).forGetter(data -> data.techs)
    ).apply(i, (format, techs) -> new PacingData(techs)));

    public static final SavedDataType<PacingData> TYPE = new SavedDataType<>(Universe.id("pacing"), PacingData::new, CODEC);

    private final Map<String, Long> techs = new HashMap<>();

    private PacingData() {
    }

    private PacingData(Map<String, Long> techs) {
        this.techs.putAll(techs);
    }

    public static PacingData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 一个科技研究完了：第一次时记下主世界的游戏刻（升级科技的后几级不再记）。 */
    public static void researched(MinecraftServer server, String tech) {
        PacingData data = get(server);
        if (data.techs.putIfAbsent(tech, server.overworld().getGameTime()) == null) {
            data.setDirty();
        }
    }

    /** 报告用的全部记录：{@code tech/<科技>} 和 {@code story/<节点>} → 游戏刻。 */
    public static Map<String, Long> reached(MinecraftServer server) {
        Map<String, Long> all = new HashMap<>();
        get(server).techs.forEach((tech, ticks) -> all.put("tech/" + tech, ticks));
        StoryData.get(server).milestones().forEach((milestone, ticks) -> all.put("story/" + milestone, ticks));
        return all;
    }

    // ---- 命令

    /** {@code /universe pacing}：关键节点、到的时候、每一段和模型比。 */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("pacing").executes(context -> {
            CommandSourceStack source = context.getSource();
            MinecraftServer server = source.getServer();
            source.sendSuccess(() -> Component.translatable("commands.universe.pacing.header",
                    hours(server.overworld().getGameTime() / Pacing.TICKS_PER_HOUR)), false);
            for (Pacing.Row row : Pacing.report(reached(server))) {
                Component name = Component.translatable(nameKey(row.checkpoint()));
                Component line;
                if (row.hours().isEmpty()) {
                    line = row.checkpoint().hasTarget()
                            ? Component.translatable("commands.universe.pacing.missing_target", name, hours(row.checkpoint().targetHours()))
                            : Component.translatable("commands.universe.pacing.missing", name);
                } else if (row.ratio().isPresent()) {
                    line = Component.translatable("commands.universe.pacing.segment", name, hours(row.hours().getAsDouble()),
                            hours(row.segmentHours().getAsDouble()), hours(row.targetSegmentHours().getAsDouble()),
                            String.format(Locale.ROOT, "%.2f", row.ratio().getAsDouble()));
                } else {
                    line = Component.translatable("commands.universe.pacing.row", name, hours(row.hours().getAsDouble()));
                }
                source.sendSuccess(() -> line, false);
            }
            return 1;
        });
    }

    /** 节点名字的语言键：{@code commands.universe.pacing.checkpoint.tech.warp_drive}。 */
    public static String nameKey(Pacing.Checkpoint checkpoint) {
        return "commands.universe.pacing.checkpoint." + checkpoint.key().replace('/', '.');
    }

    private static String hours(double hours) {
        return String.format(Locale.ROOT, "%.1f", hours);
    }
}
