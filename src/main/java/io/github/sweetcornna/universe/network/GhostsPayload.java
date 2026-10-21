package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：玩家附近的工地虚影（ADR-0023）。第一次发整个工地，之后只发还剩哪些格子。
 *
 * @param reset    先清掉客户端所有的工地（进入维度时）
 * @param sites    新看到的工地
 * @param progress 变了的工地还剩哪些格子
 * @param removed  建完了、取消了、离远了的工地
 */
public record GhostsPayload(boolean reset, List<Site> sites, List<Progress> progress, List<Integer> removed) implements CustomPacketPayload {
    public static final Type<GhostsPayload> TYPE = new Type<>(Universe.id("ghosts"));
    public static final StreamCodec<FriendlyByteBuf, GhostsPayload> STREAM_CODEC = StreamCodec.of(GhostsPayload::write, GhostsPayload::read);

    /**
     * 一个工地。
     *
     * @param palette   方块状态的数字 ID
     * @param positions 相对 origin 的位置（{@code BlueprintGeometry.pack}）
     * @param states    每格在 palette 里的下标
     * @param remaining 还没建的格子（{@link java.util.BitSet#toLongArray()}）
     */
    public record Site(int id, BlockPos origin, int[] palette, int[] positions, int[] states, long[] remaining) {
    }

    public record Progress(int id, long[] remaining) {
    }

    private static void write(FriendlyByteBuf buf, GhostsPayload payload) {
        buf.writeBoolean(payload.reset);
        buf.writeVarInt(payload.sites.size());
        for (Site site : payload.sites) {
            buf.writeVarInt(site.id);
            buf.writeBlockPos(site.origin);
            buf.writeVarIntArray(site.palette);
            buf.writeVarIntArray(site.positions);
            buf.writeVarIntArray(site.states);
            buf.writeLongArray(site.remaining);
        }
        buf.writeVarInt(payload.progress.size());
        for (Progress progress : payload.progress) {
            buf.writeVarInt(progress.id);
            buf.writeLongArray(progress.remaining);
        }
        buf.writeVarInt(payload.removed.size());
        for (int id : payload.removed) {
            buf.writeVarInt(id);
        }
    }

    private static GhostsPayload read(FriendlyByteBuf buf) {
        boolean reset = buf.readBoolean();
        int n = buf.readVarInt();
        List<Site> sites = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sites.add(new Site(buf.readVarInt(), buf.readBlockPos(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarIntArray(),
                    buf.readLongArray()));
        }
        int m = buf.readVarInt();
        List<Progress> progress = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            progress.add(new Progress(buf.readVarInt(), buf.readLongArray()));
        }
        int k = buf.readVarInt();
        List<Integer> removed = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            removed.add(buf.readVarInt());
        }
        return new GhostsPayload(reset, sites, progress, removed);
    }

    @Override
    public Type<GhostsPayload> type() {
        return TYPE;
    }
}
