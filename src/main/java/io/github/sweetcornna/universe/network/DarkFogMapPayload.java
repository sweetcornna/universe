package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：星图上的黑雾（ADR-0028 §八）。登录时发一次，以后有变化再发。
 *
 * @param hives 巢穴：在哪个恒星系、核心还在不在、等级、威胁、往外飞着的中继站
 * @param seeds 路上的种子：从哪到哪，发出时走了多少刻、一共要走多少刻（客户端按收到的时间往后推）
 */
public record DarkFogMapPayload(List<Hive> hives, List<Seed> seeds) implements CustomPacketPayload {
    public static final Type<DarkFogMapPayload> TYPE = new Type<>(Universe.id("dark_fog_map"));
    public static final StreamCodec<FriendlyByteBuf, DarkFogMapPayload> STREAM_CODEC = StreamCodec.of(DarkFogMapPayload::write,
            DarkFogMapPayload::read);
    /** 一次最多发多少条（防坏包）。 */
    private static final int MAX = 1024;

    public record Hive(int system, boolean alive, int level, float threat, int relays) {
    }

    public record Seed(int id, int from, int to, int elapsed, int total) {
    }

    /** 星图上看得出的变化：威胁按 5% 一档，种子只看从哪到哪（走到哪客户端自己推）。 */
    public int signature() {
        int hash = 1;
        for (Hive hive : this.hives) {
            hash = 31 * hash + Objects.hash(hive.system, hive.alive, hive.level, Math.round(hive.threat * 20), hive.relays);
        }
        for (Seed seed : this.seeds) {
            hash = 31 * hash + Objects.hash(seed.id, seed.from, seed.to, seed.total);
        }
        return hash;
    }

    private static void write(FriendlyByteBuf buf, DarkFogMapPayload payload) {
        buf.writeVarInt(payload.hives.size());
        for (Hive hive : payload.hives) {
            buf.writeVarInt(hive.system);
            buf.writeBoolean(hive.alive);
            buf.writeVarInt(hive.level);
            buf.writeFloat(hive.threat);
            buf.writeVarInt(hive.relays);
        }
        buf.writeVarInt(payload.seeds.size());
        for (Seed seed : payload.seeds) {
            buf.writeVarInt(seed.id);
            buf.writeVarInt(seed.from);
            buf.writeVarInt(seed.to);
            buf.writeVarInt(seed.elapsed);
            buf.writeVarInt(seed.total);
        }
    }

    private static DarkFogMapPayload read(FriendlyByteBuf buf) {
        int hiveCount = Math.min(MAX, buf.readVarInt());
        List<Hive> hives = new ArrayList<>(hiveCount);
        for (int i = 0; i < hiveCount; i++) {
            hives.add(new Hive(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readFloat(), buf.readVarInt()));
        }
        int seedCount = Math.min(MAX, buf.readVarInt());
        List<Seed> seeds = new ArrayList<>(seedCount);
        for (int i = 0; i < seedCount; i++) {
            seeds.add(new Seed(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new DarkFogMapPayload(hives, seeds);
    }

    @Override
    public Type<DarkFogMapPayload> type() {
        return TYPE;
    }
}
