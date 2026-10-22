package io.github.sweetcornna.universe.network;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.mission.MissionState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 公会使者的数据包（ADR-0027 §六）。 */
public final class GuildEnvoyPayloads {
    private GuildEnvoyPayloads() {
    }

    /**
     * 服务端 → 客户端：使者的界面。对话里按"公会任务"时打开，每次接、交、领完再发一份刷新开着的界面。
     *
     * @param open     true 时打开界面；false 只刷新开着的、同一个使者的界面
     * @param entity   使者的实体 id
     * @param guild    {@code Guild} 的序号
     * @param standing 玩家在这个公会的声望
     * @param system   使者所在的恒星系（任务回这里交付）
     * @param offers   今天的任务；除名时为空
     * @param states   每个任务的状态，同任务板（{@link OpenMissionBoardPayload#AVAILABLE} 这些）
     * @param active   玩家手上的任务
     * @param have     手上是送货任务时背包里有几件
     * @param gift     能领的礼物是哪一档（{@code Standing.Tier} 的序号）；没有可领的为 -1
     */
    public record Open(boolean open, int entity, String nameZh, String nameEn, int guild, int standing, int system, List<Mission> offers,
            List<Integer> states, Optional<MissionState.Active> active, int have, int gift) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Universe.id("guild_envoy"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Open::open,
                ByteBufCodecs.VAR_INT, Open::entity,
                ByteBufCodecs.stringUtf8(64), Open::nameZh,
                ByteBufCodecs.stringUtf8(64), Open::nameEn,
                ByteBufCodecs.VAR_INT, Open::guild,
                ByteBufCodecs.VAR_INT, Open::standing,
                ByteBufCodecs.VAR_INT, Open::system,
                MissionState.MISSION_STREAM_CODEC.apply(ByteBufCodecs.list(4)), Open::offers,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4)), Open::states,
                ByteBufCodecs.optional(MissionState.ACTIVE_STREAM_CODEC), Open::active,
                ByteBufCodecs.VAR_INT, Open::have,
                ByteBufCodecs.VAR_INT, Open::gift,
                Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：在使者那儿接任务、放弃、交付、领礼物。
     *
     * @param slot 接第几个（只有接任务用）
     */
    public record Action(int entity, int action, int slot) implements CustomPacketPayload {
        public static final int ACCEPT = 0;
        public static final int ABANDON = 1;
        public static final int HAND_IN = 2;
        public static final int GIFT = 3;
        public static final Type<Action> TYPE = new Type<>(Universe.id("guild_envoy_action"));
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::entity,
                ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.VAR_INT, Action::slot,
                Action::new);

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }
}
