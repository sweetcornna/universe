package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.Universe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 玩家所在星球的沙尘、火山灰、晶尘这类不落地的天气有多强（0–1）。
 *
 * <p>雨雪仍走原版的降雨强度包；这类天气不能算作下雨（会浇灭火、让实体变湿），所以单独同步。
 * 强度变化时发给这个维度里的所有玩家，进入维度、登录、重生时单独发一次。
 */
public record PlanetWeatherPayload(float intensity) implements CustomPacketPayload {
    public static final Type<PlanetWeatherPayload> TYPE = new Type<>(Universe.id("planet_weather"));
    public static final StreamCodec<ByteBuf, PlanetWeatherPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(PlanetWeatherPayload::new, PlanetWeatherPayload::intensity);

    @Override
    public Type<PlanetWeatherPayload> type() {
        return TYPE;
    }
}
