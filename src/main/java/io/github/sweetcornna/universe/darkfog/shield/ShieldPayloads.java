package io.github.sweetcornna.universe.darkfog.shield;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 行星护盾的数据包（ADR-0028 §八）：客户端按这些画穹顶。 */
public final class ShieldPayloads {
    private ShieldPayloads() {
    }

    /**
     * 一个展开着的穹顶。
     *
     * @param generator 发生器的位置（下半截）
     * @param radius    半径
     * @param charge    能量占"罩满"那条线的比例，低了穹顶变色
     */
    public record Dome(BlockPos generator, float radius, float charge) {
        public static final StreamCodec<ByteBuf, Dome> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Dome::generator,
                ByteBufCodecs.FLOAT, Dome::radius,
                ByteBufCodecs.FLOAT, Dome::charge,
                Dome::new);
    }

    /** 服务端 → 客户端：玩家所在维度里展开着的穹顶，整份替换。进维度时发一次，之后变了才发。 */
    public record Domes(List<Dome> domes) implements CustomPacketPayload {
        public static final Type<Domes> TYPE = new Type<>(Universe.id("shield_domes"));
        public static final StreamCodec<ByteBuf, Domes> STREAM_CODEC = StreamCodec.composite(
                Dome.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Domes::domes,
                Domes::new);

        @Override
        public Type<Domes> type() {
            return TYPE;
        }
    }

    /**
     * 服务端 → 客户端：穹顶挡下了一次伤害，在那里闪一下。
     *
     * @param generator 挡下的那台发生器
     * @param x         打在哪里
     * @param strength  挡下了多少伤害（闪得多亮）
     * @param collapsed 这一下把能量打光了，穹顶收起
     */
    public record Hit(BlockPos generator, double x, double y, double z, float strength, boolean collapsed) implements CustomPacketPayload {
        public static final Type<Hit> TYPE = new Type<>(Universe.id("shield_hit"));
        public static final StreamCodec<ByteBuf, Hit> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Hit::generator,
                ByteBufCodecs.DOUBLE, Hit::x,
                ByteBufCodecs.DOUBLE, Hit::y,
                ByteBufCodecs.DOUBLE, Hit::z,
                ByteBufCodecs.FLOAT, Hit::strength,
                ByteBufCodecs.BOOL, Hit::collapsed,
                Hit::new);

        @Override
        public Type<Hit> type() {
            return TYPE;
        }
    }
}
