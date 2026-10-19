package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端：探索车的驾驶员按下、松开攻击键，开关车载采矿激光（ADR-0017）。激光打哪儿由服务端按驾驶员的视线算；
 * 第三人称时镜头抬高了，瞄准线跟着抬（{@code Exocraft#aimOrigin}），所以还要告诉服务端用的是哪种视角。
 *
 * @param firing      按着攻击键
 * @param thirdPerson 用的是背后的第三人称视角
 */
public record ExocraftControlPayload(boolean firing, boolean thirdPerson) implements CustomPacketPayload {
    public static final Type<ExocraftControlPayload> TYPE = new Type<>(Universe.id("exocraft_control"));
    public static final StreamCodec<ByteBuf, ExocraftControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExocraftControlPayload::firing,
            ByteBufCodecs.BOOL, ExocraftControlPayload::thirdPerson,
            ExocraftControlPayload::new);

    @Override
    public Type<ExocraftControlPayload> type() {
        return TYPE;
    }
}
