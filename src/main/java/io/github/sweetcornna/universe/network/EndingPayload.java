package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：放结局画面（ADR-0018），带上这个世界的统计。
 *
 * @param gameTime      主世界过了多少刻
 * @param systems       发现的恒星系
 * @param bodies        发现的天体
 * @param species       发现的物种（动物和植物）
 * @param layers        有结构的戴森球层数（所有恒星系加起来）
 * @param shells        铺满的球壳片数
 * @param dysonPower    所有戴森球现在的总功率（焦/刻）
 * @param techs         研究完的科技数
 * @param upgradeLevels 升级科技一共升了几级
 */
public record EndingPayload(long gameTime, int systems, int bodies, int species, int layers, int shells, double dysonPower, int techs,
        int upgradeLevels) implements CustomPacketPayload {
    public static final Type<EndingPayload> TYPE = new Type<>(Universe.id("ending"));
    public static final StreamCodec<ByteBuf, EndingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, EndingPayload::gameTime,
            ByteBufCodecs.VAR_INT, EndingPayload::systems,
            ByteBufCodecs.VAR_INT, EndingPayload::bodies,
            ByteBufCodecs.VAR_INT, EndingPayload::species,
            ByteBufCodecs.VAR_INT, EndingPayload::layers,
            ByteBufCodecs.VAR_INT, EndingPayload::shells,
            ByteBufCodecs.DOUBLE, EndingPayload::dysonPower,
            ByteBufCodecs.VAR_INT, EndingPayload::techs,
            ByteBufCodecs.VAR_INT, EndingPayload::upgradeLevels,
            EndingPayload::new);

    @Override
    public Type<EndingPayload> type() {
        return TYPE;
    }
}
