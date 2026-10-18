package io.github.sweetcornna.universe.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端：发现日志（ADR-0012）。进服时发整份，之后每有新发现或改名就发变了的那几条。
 *
 * @param full    true 表示整份，客户端先清空
 * @param entries 记录
 * @param reward  这次给收到的玩家发了几个勘探数据（客户端用来显示"新发现"）；0 表示只是同步
 */
public record DiscoveryPayload(boolean full, List<Entry> entries, int reward) implements CustomPacketPayload {
    public static final Type<DiscoveryPayload> TYPE = new Type<>(Universe.id("discoveries"));

    /** 线上的一条记录：东西用 {@link DiscoveryKey#id()}。 */
    public record Entry(String key, Optional<UUID> by, String byName, long day, Optional<String> name) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Entry::key,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Entry::by,
                ByteBufCodecs.stringUtf8(64), Entry::byName,
                ByteBufCodecs.VAR_LONG, Entry::day,
                ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(128)), Entry::name,
                Entry::new);

        public static Entry of(DiscoveryLog.Entry entry) {
            return new Entry(entry.key().id(), Optional.ofNullable(entry.discoverer()), entry.discovererName(), entry.day(),
                    Optional.ofNullable(entry.customName()));
        }

        public Optional<DiscoveryLog.Entry> entry() {
            return DiscoveryKey.parse(this.key).map(key -> new DiscoveryLog.Entry(key, this.by.orElse(null), this.byName, this.day,
                    this.name.orElse(null)));
        }
    }

    public static final StreamCodec<ByteBuf, DiscoveryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DiscoveryPayload::full,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(1 << 16)), DiscoveryPayload::entries,
            ByteBufCodecs.VAR_INT, DiscoveryPayload::reward,
            DiscoveryPayload::new);

    public static DiscoveryPayload of(boolean full, Iterable<DiscoveryLog.Entry> entries, int reward) {
        List<Entry> list = new ArrayList<>();
        for (DiscoveryLog.Entry entry : entries) {
            list.add(Entry.of(entry));
        }
        return new DiscoveryPayload(full, list, reward);
    }

    @Override
    public Type<DiscoveryPayload> type() {
        return TYPE;
    }
}
