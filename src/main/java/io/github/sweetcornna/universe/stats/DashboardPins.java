package io.github.sweetcornna.universe.stats;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.core.stats.Dashboard.Kind;
import io.github.sweetcornna.universe.core.stats.Dashboard.Pin;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 仪表盘钉住的图怎么存、怎么发（ADR-0033 §四）。核心层不能依赖序列化库，这里转一道。存档里图的种类存名字，认不出的
 * （以后删掉的种类）读档时扔掉；网络上按序号发。
 */
public final class DashboardPins {
    private static final Codec<Pin> PIN = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("kind").forGetter(pin -> pin.kind().name().toLowerCase(Locale.ROOT)),
            Codec.STRING.optionalFieldOf("target", "").forGetter(Pin::target),
            Codec.STRING.optionalFieldOf("scope", "").forGetter(Pin::scope),
            Codec.INT.optionalFieldOf("range", 0).forGetter(Pin::range),
            Codec.FLOAT.optionalFieldOf("x", -1F).forGetter(Pin::x),
            Codec.FLOAT.optionalFieldOf("y", -1F).forGetter(Pin::y),
            Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(Pin::collapsed)
    ).apply(i, (kind, target, scope, range, x, y, collapsed) -> new Pin(kind(kind), target, scope, range, x, y, collapsed)));

    public static final MapCodec<List<Pin>> CODEC = PIN.listOf()
            .xmap(pins -> Dashboard.sanitize(pins.stream().filter(pin -> pin.kind() != null).toList()), pins -> pins)
            .fieldOf("pins");

    public static final StreamCodec<ByteBuf, Pin> PIN_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), Pin::kind,
            ByteBufCodecs.stringUtf8(256), Pin::target,
            ByteBufCodecs.stringUtf8(256), Pin::scope,
            ByteBufCodecs.VAR_INT, Pin::range,
            ByteBufCodecs.FLOAT, Pin::x,
            ByteBufCodecs.FLOAT, Pin::y,
            ByteBufCodecs.BOOL, Pin::collapsed,
            Pin::new);

    public static final StreamCodec<ByteBuf, List<Pin>> STREAM_CODEC = PIN_STREAM_CODEC.apply(ByteBufCodecs.list(Dashboard.MAX_PINS));

    private DashboardPins() {
    }

    private static @Nullable Kind kind(String name) {
        for (Kind kind : Kind.values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(name)) {
                return kind;
            }
        }
        return null;
    }
}
