package io.github.sweetcornna.universe.sentinel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.sentinel.Wanted;

/**
 * 玩家身上的警戒状态（ADR-0014）：在哪颗星球上、警戒值和通缉级别。存档，下线再上线还在；死亡后清零（不跟着复制）。
 * 换了星球也清零（{@link Sentinels} 发现维度变了就清）。
 */
public final class SentinelAlert {
    public static final MapCodec<SentinelAlert> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(alert -> alert.dimension),
            Codec.DOUBLE.fieldOf("alert").forGetter(alert -> alert.wanted.alert()),
            Codec.INT.fieldOf("level").forGetter(alert -> alert.wanted.level())
    ).apply(i, SentinelAlert::new));

    private String dimension;
    private final Wanted wanted;

    public SentinelAlert() {
        this("", 0, 0);
    }

    private SentinelAlert(String dimension, double alert, int level) {
        this.dimension = dimension;
        this.wanted = new Wanted(alert, level);
    }

    public Wanted wanted() {
        return this.wanted;
    }

    /** 记下当前所在的维度；换了维度就清零，返回 true。 */
    boolean moveTo(String dimension) {
        if (this.dimension.equals(dimension)) {
            return false;
        }
        boolean wasWanted = this.wanted.wanted();
        this.dimension = dimension;
        this.wanted.clear();
        return wasWanted;
    }
}
