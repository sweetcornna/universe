package io.github.sweetcornna.universe.network;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

/**
 * 星团种子和恒星系数量。客户端据此自己算出整个星团（技术架构 §3.1），用来给星球方块染色、画天空、显示星图。
 * 再带上守卫机强度（ADR-0028 §七）：强度 0 时星图上的星球都显示没有守卫机。游戏规则中途改了，下次进世界才同步。
 *
 * <p>在登录的配置阶段发送，保证客户端进入世界、开始渲染区块之前就知道星团。
 */
public record GalaxyInfoPayload(long galaxySeed, int systemCount, float sentinelStrength) implements CustomPacketPayload {
    public static final Type<GalaxyInfoPayload> TYPE = new Type<>(Universe.id("galaxy_info"));
    public static final StreamCodec<ByteBuf, GalaxyInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, GalaxyInfoPayload::galaxySeed,
            ByteBufCodecs.VAR_INT, GalaxyInfoPayload::systemCount,
            ByteBufCodecs.FLOAT, GalaxyInfoPayload::sentinelStrength,
            GalaxyInfoPayload::new);

    public static GalaxyInfoPayload of(MinecraftServer server) {
        return new GalaxyInfoPayload(Planets.galaxySeed(server), Planets.systemCount(server), (float) UniverseRules.sentinelStrength(server));
    }

    @Override
    public Type<GalaxyInfoPayload> type() {
        return TYPE;
    }

    /** 配置阶段的任务：发完就结束，不等客户端回应。 */
    public record SyncTask(ServerConfigurationPacketListener listener, MinecraftServer server) implements ICustomConfigurationTask {
        public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Universe.id("galaxy_info").toString());

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(GalaxyInfoPayload.of(this.server));
            this.listener.finishCurrentTask(TYPE);
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}
