package io.github.sweetcornna.universe.fishing;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.fishing.FishingLog;
import io.github.sweetcornna.universe.core.fishing.FishingLog.Entry;
import io.github.sweetcornna.universe.core.fishing.FishTable;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 钓鱼图鉴的存档和同步格式（ADR-0034 §一）。按鱼的 ID 存，表里删掉的鱼读档时丢掉。 */
public final class FishingCodecs {
    private static final Codec<Entry> ENTRY = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("count").forGetter(Entry::count),
            Codec.DOUBLE.fieldOf("heaviest").forGetter(Entry::heaviest),
            Codec.STRING.optionalFieldOf("planet_zh", "").forGetter(Entry::planetZh),
            Codec.STRING.optionalFieldOf("planet_en", "").forGetter(Entry::planetEn)).apply(i, Entry::new));

    public static final MapCodec<FishingLog> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, ENTRY).optionalFieldOf("entries", Map.of()).forGetter(FishingLog::entries),
            Codec.INT.optionalFieldOf("released", 0).forGetter(FishingLog::released),
            Codec.INT.optionalFieldOf("release_casts", 0).forGetter(FishingLog::releaseCasts)).apply(i, FishingCodecs::known));

    private static final StreamCodec<ByteBuf, Entry> ENTRY_STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::count,
            ByteBufCodecs.DOUBLE, Entry::heaviest,
            ByteBufCodecs.STRING_UTF8, Entry::planetZh,
            ByteBufCodecs.STRING_UTF8, Entry::planetEn,
            Entry::new);

    public static final StreamCodec<ByteBuf, FishingLog> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, Entry, Map<String, Entry>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ENTRY_STREAM), FishingLog::entries,
            ByteBufCodecs.VAR_INT, FishingLog::released,
            ByteBufCodecs.VAR_INT, FishingLog::releaseCasts,
            FishingLog::new);

    private FishingCodecs() {
    }

    private static FishingLog known(Map<String, Entry> entries, int released, int releaseCasts) {
        Map<String, Entry> kept = new HashMap<>();
        entries.forEach((id, entry) -> {
            if (FishTable.byId(id).isPresent()) {
                kept.put(id, entry);
            }
        });
        return new FishingLog(kept, released, releaseCasts);
    }
}
