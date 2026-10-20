package io.github.sweetcornna.universe.network;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.mission.Mission;
import io.github.sweetcornna.universe.mission.MissionState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：打开任务板（ADR-0017）。
 *
 * @param offers 今天的任务
 * @param states 每个任务的状态：{@link #AVAILABLE}、{@link #ACTIVE}、{@link #DONE}
 * @param active 玩家手上的任务（可能是别的空间站发的）
 * @param have   手上是送货任务时，背包里有几件要送的东西
 */
public record OpenMissionBoardPayload(BlockPos pos, int system, List<Mission> offers, List<Integer> states,
        Optional<MissionState.Active> active, int have) implements CustomPacketPayload {
    public static final int AVAILABLE = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;
    public static final Type<OpenMissionBoardPayload> TYPE = new Type<>(Universe.id("open_mission_board"));
    public static final StreamCodec<ByteBuf, OpenMissionBoardPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenMissionBoardPayload::pos,
            ByteBufCodecs.VAR_INT, OpenMissionBoardPayload::system,
            MissionState.MISSION_STREAM_CODEC.apply(ByteBufCodecs.list(8)), OpenMissionBoardPayload::offers,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), OpenMissionBoardPayload::states,
            ByteBufCodecs.optional(MissionState.ACTIVE_STREAM_CODEC), OpenMissionBoardPayload::active,
            ByteBufCodecs.VAR_INT, OpenMissionBoardPayload::have,
            OpenMissionBoardPayload::new);

    @Override
    public Type<OpenMissionBoardPayload> type() {
        return TYPE;
    }
}
