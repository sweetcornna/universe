package io.github.sweetcornna.universe.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 空间站的行情存在全局存档 {@code data/universe/trade.dat}（ADR-0017）：每座空间站的压价、存货和这一批模块卖掉了哪些，
 * 都记在最后结算的那一刻，读档后按时间惰性补上。
 */
public final class TradeData extends SavedData {
    /** 存档格式版本。改格式时加一，并在读取时迁移旧格式（技术架构 §3.10）。 */
    private static final int FORMAT = 1;

    /** 一座空间站存档时的样子。 */
    record Station(long tick, Map<String, Double> pressure, Map<String, Double> stock, long period, List<Integer> sold) {
        static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("tick").forGetter(Station::tick),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("pressure", Map.of()).forGetter(Station::pressure),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("stock", Map.of()).forGetter(Station::stock),
                Codec.LONG.optionalFieldOf("module_period", -1L).forGetter(Station::period),
                Codec.INT.listOf().optionalFieldOf("modules_sold", List.of()).forGetter(Station::sold)
        ).apply(i, Station::new));
    }

    private static final Codec<TradeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format").forGetter(data -> Optional.of(FORMAT)),
            Codec.unboundedMap(Codec.STRING, Station.CODEC).fieldOf("stations").forGetter(TradeData::snapshot)
    ).apply(i, (format, stations) -> {
        TradeData data = new TradeData();
        stations.forEach((key, station) -> {
            try {
                data.saved.put(Integer.parseInt(key), station);
            } catch (NumberFormatException e) {
                // 认不出的键跳过
            }
        });
        return data;
    }));

    public static final SavedDataType<TradeData> TYPE = new SavedDataType<>(Universe.id("trade"), TradeData::new, CODEC);

    private final Map<Integer, Station> saved = new HashMap<>();
    private final Map<Integer, StationMarket> live = new HashMap<>();

    private TradeData() {
    }

    public static TradeData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 恒星系空间站的行情，结算到 now。 */
    StationMarket market(MinecraftServer server, StarSystem system, long now) {
        StationMarket market = this.live.computeIfAbsent(system.index(), index -> StationMarket.create(server, system, this.saved.get(index), now));
        market.touch(server, now);
        return market;
    }

    /** 价钱表重算以后：把内存里的行情收回存档的样子，下次用到时按新价钱重建。 */
    void refresh() {
        this.live.forEach((index, market) -> this.saved.put(index, market.snapshot()));
        this.live.clear();
    }

    private Map<String, Station> snapshot() {
        Map<String, Station> out = new TreeMap<>();
        this.saved.forEach((index, station) -> out.put(String.valueOf(index), station));
        this.live.forEach((index, market) -> out.put(String.valueOf(index), market.snapshot()));
        return out;
    }
}
