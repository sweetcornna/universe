package io.github.sweetcornna.universe.sentinel;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 发给玩家自己的守卫机读数，HUD 用（ADR-0014，ADR-0026 改成五级）。
 *
 * @param planet    当前星球的守卫强度（{@code SentinelLevel} 的序号），0 表示没有守卫
 * @param level     通缉级别 0–5
 * @param alert     警戒值占通缉门槛的比例（没被通缉时显示），取到 1%
 * @param escape    脱离进度 0–1，取到 1%
 * @param seen      正被敌对的守卫机看见
 * @param remaining 这一波还剩几架（还没打掉的加上还没赶到的）；-1 表示下一波正在赶来
 * @param dormant   这颗星球的守卫机还要撤离多少秒；0 表示没撤
 */
public record SentinelHud(int planet, int level, float alert, float escape, boolean seen, int remaining, int dormant) {
    public static final SentinelHud NONE = new SentinelHud(0, 0, 0, 0, false, 0, 0);
    public static final StreamCodec<ByteBuf, SentinelHud> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SentinelHud::planet,
            ByteBufCodecs.VAR_INT, SentinelHud::level,
            ByteBufCodecs.FLOAT, SentinelHud::alert,
            ByteBufCodecs.FLOAT, SentinelHud::escape,
            ByteBufCodecs.BOOL, SentinelHud::seen,
            ByteBufCodecs.VAR_INT, hud -> hud.remaining + 1,
            ByteBufCodecs.VAR_INT, SentinelHud::dormant,
            (planet, level, alert, escape, seen, remaining, dormant) -> new SentinelHud(planet, level, alert, escape, seen, remaining - 1, dormant));

    public static SentinelHud of(int planet, int level, double alertShare, float escape, boolean seen, int remaining, int dormant) {
        return new SentinelHud(planet, level, Math.round(Math.min(1, alertShare) * 100) / 100F, Math.round(escape * 100) / 100F, seen,
                remaining, dormant);
    }
}
