package io.github.sweetcornna.universe.fishing;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 钓上东西的人：屏幕中间弹一张"钓上来了"的卡片（ADR-0034 §一）。
 *
 * @param species  鱼的编号；不是鱼时 -1，看 {@code item}
 * @param item     不是鱼时钓上来的物品 ID（杂物、漂流瓶、补给舱、技术模块）
 * @param weight   鱼多重（千克）
 * @param record   比图鉴里最重的还重
 * @param first    第一次钓到这种鱼
 */
public record FishCaughtPayload(int species, String item, float weight, boolean record, boolean first) implements CustomPacketPayload {
    public static final Type<FishCaughtPayload> TYPE = new Type<>(Universe.id("fish_caught"));
    public static final StreamCodec<ByteBuf, FishCaughtPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FishCaughtPayload::species,
            ByteBufCodecs.STRING_UTF8, FishCaughtPayload::item,
            ByteBufCodecs.FLOAT, FishCaughtPayload::weight,
            ByteBufCodecs.BOOL, FishCaughtPayload::record,
            ByteBufCodecs.BOOL, FishCaughtPayload::first,
            FishCaughtPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
