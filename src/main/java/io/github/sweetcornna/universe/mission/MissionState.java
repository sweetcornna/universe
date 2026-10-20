package io.github.sweetcornna.universe.mission;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.core.mission.MissionKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 玩家的任务（ADR-0017）：手上的那个任务和今天做完的任务。存在玩家身上，死亡不丢，同步给本人画 HUD。
 *
 * @param active 接了的任务
 * @param done   今天交付过的任务（{@link Mission#id()}），同一天不能再接
 */
public record MissionState(Optional<Active> active, List<String> done) {
    public static final MissionState NONE = new MissionState(Optional.empty(), List.of());

    /**
     * 接了的任务。
     *
     * @param baseline 接任务时的统计（击落的海盗、打碎的小行星），清剿和采矿按涨了多少算
     * @param analyzed 测绘：接任务以后分析过的动植物
     * @param progress 现在的进度，服务端每秒更新一次
     */
    public record Active(Mission mission, long baseline, List<String> analyzed, int progress) {
        public boolean complete() {
            return this.progress >= this.mission.count();
        }

        Active withProgress(int progress) {
            return new Active(this.mission, this.baseline, this.analyzed, progress);
        }
    }

    public static final Codec<MissionKind> KIND_CODEC = Codec.STRING.comapFlatMap(MissionState::kind, MissionKind::key);
    public static final Codec<Mission.Bonus> BONUS_CODEC = Codec.STRING.comapFlatMap(MissionState::bonus, Mission.Bonus::key);

    public static final Codec<Mission> MISSION_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("system").forGetter(Mission::system),
            Codec.LONG.fieldOf("day").forGetter(Mission::day),
            Codec.INT.fieldOf("slot").forGetter(Mission::slot),
            KIND_CODEC.fieldOf("kind").forGetter(Mission::kind),
            Codec.STRING.optionalFieldOf("item", "").forGetter(Mission::item),
            Codec.INT.fieldOf("count").forGetter(Mission::count),
            Codec.LONG.fieldOf("reward").forGetter(Mission::reward),
            BONUS_CODEC.optionalFieldOf("bonus", Mission.Bonus.NONE).forGetter(Mission::bonus),
            Codec.INT.optionalFieldOf("bonus_count", 0).forGetter(Mission::bonusCount)
    ).apply(i, Mission::new));

    public static final Codec<Active> ACTIVE_CODEC = RecordCodecBuilder.create(i -> i.group(
            MISSION_CODEC.fieldOf("mission").forGetter(Active::mission),
            Codec.LONG.optionalFieldOf("baseline", 0L).forGetter(Active::baseline),
            Codec.STRING.listOf().optionalFieldOf("analyzed", List.of()).forGetter(Active::analyzed),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(Active::progress)
    ).apply(i, Active::new));

    public static final MapCodec<MissionState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ACTIVE_CODEC.optionalFieldOf("active").forGetter(MissionState::active),
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(MissionState::done)
    ).apply(i, MissionState::new));

    public static final StreamCodec<ByteBuf, MissionKind> KIND_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> MissionKind.values()[Math.floorMod(i, MissionKind.values().length)], MissionKind::ordinal);
    public static final StreamCodec<ByteBuf, Mission.Bonus> BONUS_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> Mission.Bonus.values()[Math.floorMod(i, Mission.Bonus.values().length)], Mission.Bonus::ordinal);

    public static final StreamCodec<ByteBuf, Mission> MISSION_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Mission::system,
            ByteBufCodecs.VAR_LONG, Mission::day,
            ByteBufCodecs.VAR_INT, Mission::slot,
            KIND_STREAM_CODEC, Mission::kind,
            ByteBufCodecs.stringUtf8(256), Mission::item,
            ByteBufCodecs.VAR_INT, Mission::count,
            ByteBufCodecs.VAR_LONG, Mission::reward,
            BONUS_STREAM_CODEC, Mission::bonus,
            ByteBufCodecs.VAR_INT, Mission::bonusCount,
            Mission::new);

    public static final StreamCodec<ByteBuf, Active> ACTIVE_STREAM_CODEC = StreamCodec.composite(
            MISSION_STREAM_CODEC, Active::mission,
            ByteBufCodecs.VAR_LONG, Active::baseline,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64)), Active::analyzed,
            ByteBufCodecs.VAR_INT, Active::progress,
            Active::new);

    public static final StreamCodec<ByteBuf, MissionState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ACTIVE_STREAM_CODEC), MissionState::active,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64)), MissionState::done,
            MissionState::new);

    private static DataResult<MissionKind> kind(String name) {
        try {
            return DataResult.success(MissionKind.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown mission kind " + name);
        }
    }

    private static DataResult<Mission.Bonus> bonus(String name) {
        try {
            return DataResult.success(Mission.Bonus.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown mission bonus " + name);
        }
    }

    MissionState withActive(Optional<Active> active) {
        return new MissionState(active, this.done);
    }
}
