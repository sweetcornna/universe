package io.github.sweetcornna.universe.beacon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.core.beacon.MemoKeys;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.network.MemoPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 星球和恒星系的备忘（ADR-0033 §六），存在 {@code data/universe/memos.dat}，所有玩家共用。只有发现日志里有的星球、恒星系能写。
 */
public final class Memos extends SavedData {
    private static final Codec<Memos> CODEC = Codec.unboundedMap(Codec.STRING, MemoCodecs.CODEC).xmap(Memos::new, data -> data.memos);
    public static final SavedDataType<Memos> TYPE = new SavedDataType<>(Universe.id("memos"), Memos::new, CODEC);

    private final Map<String, Memo> memos = new HashMap<>();

    private Memos() {
    }

    private Memos(Map<String, Memo> memos) {
        memos.forEach((key, memo) -> {
            if (!memo.isEmpty()) {
                this.memos.put(key, memo);
            }
        });
    }

    public static Memos get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Memo memo(String key) {
        return this.memos.getOrDefault(key, Memo.EMPTY);
    }

    public void set(String key, Memo memo) {
        Memo clean = Memo.clean(memo.lines());
        Memo old = clean.isEmpty() ? this.memos.remove(key) : this.memos.put(key, clean);
        if (!clean.equals(old == null ? Memo.EMPTY : old)) {
            this.setDirty();
        }
    }

    /** 这个键能不能写：格式对，而且发现日志里有这颗星球（这个恒星系）。 */
    public static boolean writable(MinecraftServer server, String key) {
        Optional<DiscoveryKey> discovery = MemoKeys.discovery(key);
        return discovery.isPresent() && Discoveries.log(server).contains(discovery.get());
    }

    public static void handleOpen(MemoPayloads.Open payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && writable(player.level().getServer(), payload.key())) {
            PacketDistributor.sendToPlayer(player, new MemoPayloads.Content(payload.key(), get(player.level().getServer()).memo(payload.key())));
        }
    }

    public static void handleSave(MemoPayloads.Save payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator() && writable(player.level().getServer(), payload.key())) {
            get(player.level().getServer()).set(payload.key(), payload.memo());
        }
    }
}
